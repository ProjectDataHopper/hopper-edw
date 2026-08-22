#!/usr/bin/env python3
"""Build a tutorial video from manifest screenshots and xAI TTS narration."""
#
# Copyright 2026 i-Bridge bv
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
from __future__ import annotations

import argparse
import hashlib
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import requests
import yaml
from moviepy import (
    AudioFileClip,
    ColorClip,
    CompositeVideoClip,
    ImageClip,
    concatenate_videoclips,
)
from pydub import AudioSegment

XAI_TTS_URL = "https://api.x.ai/v1/tts"
MAX_RETRIES = 5
NON_RETRYABLE_STATUS = {400, 401, 403, 404, 405, 415, 422}


@dataclass
class Scene:
    index: int
    scene_id: str
    image: str
    narration: str
    narration_hash: str


@dataclass
class BuildContext:
    manifest_path: Path
    tutorial_dir: Path
    output_dir: Path
    tutorial_id: str
    locale: str
    voice: str
    width: int
    height: int
    fps: int
    pause_after_sec: float
    scenes: list[Scene]


def narration_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def normalize_narration(text: str) -> str:
    return " ".join(text.split())


def resolve_voice(manifest: dict[str, Any], override: str | None) -> str:
    if override:
        return override
    tts = manifest.get("tutorial", {}).get("tts", {})
    return tts.get("voice_id", tts.get("voices", {}).get("en", "ursa"))


def audio_cache_dir(output_dir: Path, voice: str) -> Path:
    return output_dir / "audio" / f"xai-{voice}"


def raw_audio_path(cache_dir: Path, scene: Scene) -> Path:
    return cache_dir / f"{scene.index:03d}-{scene.scene_id}.mp3"


def timings_path(output_dir: Path, locale: str) -> Path:
    return output_dir / f"timings-{locale}.yaml"


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def load_build_context(
    manifest_path: Path,
    *,
    locale: str,
    voice: str | None,
    pause_after_sec: float,
    limit: int | None,
) -> BuildContext:
    manifest = load_manifest(manifest_path)
    tutorial = manifest["tutorial"]
    video_cfg = tutorial["video"]
    tutorial_dir = manifest_path.parent
    output_dir = tutorial_dir / "output"
    output_dir.mkdir(parents=True, exist_ok=True)

    scenes: list[Scene] = []
    for index, entry in enumerate(manifest["scenes"], start=1):
        narration_raw = entry["narration"][locale]
        narration = normalize_narration(narration_raw)
        scenes.append(
            Scene(
                index=index,
                scene_id=entry["id"],
                image=entry["image"],
                narration=narration,
                narration_hash=narration_hash(narration),
            )
        )
        if limit is not None and index >= limit:
            break

    return BuildContext(
        manifest_path=manifest_path,
        tutorial_dir=tutorial_dir,
        output_dir=output_dir,
        tutorial_id=tutorial["id"],
        locale=locale,
        voice=resolve_voice(manifest, voice),
        width=int(video_cfg.get("width", 1920)),
        height=int(video_cfg.get("height", 1080)),
        fps=int(video_cfg.get("fps", 30)),
        pause_after_sec=pause_after_sec,
        scenes=scenes,
    )


def write_timings_file(ctx: BuildContext, timings: list[dict[str, Any]]) -> None:
    payload = {
        "locale": ctx.locale,
        "provider": "xai",
        "voice": ctx.voice,
        "pause_after_sec": ctx.pause_after_sec,
        "scenes": timings,
    }
    path = timings_path(ctx.output_dir, ctx.locale)
    with path.open("w", encoding="utf-8") as handle:
        yaml.safe_dump(payload, handle, sort_keys=False)


def load_timings_hashes(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle) or {}
    return {
        entry["id"]: entry.get("narration_hash", "")
        for entry in data.get("scenes", [])
    }


def require_xai_api_key() -> str:
    api_key = os.environ.get("XAI_API_KEY", "")
    api_key = api_key.strip().strip('"').strip("'")
    api_key = api_key.replace("\n", "").replace("\r", "")
    if not api_key:
        raise SystemExit(
            "XAI_API_KEY is not set. Export your key from https://console.x.ai"
        )
    return api_key


def xai_request_headers(api_key: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "*/*",
        "User-Agent": "hop-data-vault-tts/1.0",
    }


def is_probable_audio(content: bytes, content_type: str) -> bool:
    lowered = content_type.lower()
    if lowered.startswith("audio/"):
        return True
    if len(content) >= 3 and content[:3] == b"ID3":
        return True
    if len(content) >= 2 and content[0] == 0xFF and (content[1] & 0xE0) == 0xE0:
        return True
    return False


def format_tts_error(response: requests.Response) -> str:
    body = response.text.strip()
    if not body:
        return f"HTTP {response.status_code} (empty body)"
    return f"HTTP {response.status_code}: {body[:500]}"


def tts_permission_hint(status_code: int) -> str:
    if status_code == 401:
        return (
            "Check that XAI_API_KEY is set and valid "
            "(https://console.x.ai/team/default/api-keys)."
        )
    if status_code == 403:
        return (
            "Your API key authenticated but is not allowed to use TTS. "
            "In the xAI console, confirm your team has Voice/TTS access and "
            "billing enabled, or create a new key with the right permissions."
        )
    return ""


def synthesize_xai_tts(
    text: str,
    *,
    voice: str,
    output_path: Path,
    api_key: str,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "text": text,
        "voice_id": voice,
        "language": "en",
    }
    headers = xai_request_headers(api_key)

    last_error: str | None = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = requests.post(
                XAI_TTS_URL,
                headers=headers,
                json=payload,
                timeout=120,
            )
            if response.status_code in NON_RETRYABLE_STATUS:
                detail = format_tts_error(response)
                hint = tts_permission_hint(response.status_code)
                message = f"xAI TTS failed: {detail}"
                if hint:
                    message = f"{message}\n{hint}"
                raise SystemExit(message)
            if response.status_code == 429 or response.status_code >= 500:
                last_error = format_tts_error(response)
            elif not response.ok:
                last_error = format_tts_error(response)
            elif not is_probable_audio(
                response.content, response.headers.get("Content-Type", "")
            ):
                last_error = (
                    "Response was not audio "
                    f"(Content-Type: {response.headers.get('Content-Type', 'unknown')}, "
                    f"{len(response.content)} bytes): "
                    f"{response.text[:200]}"
                )
            else:
                output_path.write_bytes(response.content)
                return
        except requests.RequestException as exc:
            last_error = str(exc)

        if attempt == MAX_RETRIES:
            break
        sleep_sec = min(2**attempt, 30)
        print(
            f"TTS retry {attempt}/{MAX_RETRIES} for {output_path.name} "
            f"in {sleep_sec}s ({last_error})",
            file=sys.stderr,
        )
        time.sleep(sleep_sec)

    raise SystemExit(f"xAI TTS failed after {MAX_RETRIES} attempts: {last_error}")


def padded_audio_path(cache_dir: Path, scene: Scene) -> Path:
    return cache_dir / f"{scene.index:03d}-{scene.scene_id}-padded.wav"


def build_padded_audio(
    raw_mp3: Path,
    output_wav: Path,
    *,
    pause_after_sec: float,
) -> float:
    speech = AudioSegment.from_file(raw_mp3)
    pause_ms = int(round(pause_after_sec * 1000))
    padded = speech + AudioSegment.silent(duration=pause_ms)
    output_wav.parent.mkdir(parents=True, exist_ok=True)
    padded.export(output_wav, format="wav")
    return len(padded) / 1000.0


def fit_image_on_canvas(
    image_path: Path,
    *,
    width: int,
    height: int,
    duration_sec: float,
) -> CompositeVideoClip:
    background = ColorClip(
        size=(width, height),
        color=(0, 0, 0),
        duration=duration_sec,
    )
    image = ImageClip(str(image_path), duration=duration_sec)
    img_w, img_h = image.size
    scale = min(width / img_w, height / img_h)
    fitted = image.resized(
        new_size=(max(1, int(img_w * scale)), max(1, int(img_h * scale)))
    )
    return CompositeVideoClip(
        [background, fitted.with_position("center")],
        size=(width, height),
    )


def build_scene_clip(
    ctx: BuildContext,
    scene: Scene,
    padded_wav: Path,
    duration_sec: float,
) -> CompositeVideoClip:
    image_path = ctx.tutorial_dir / scene.image
    if not image_path.exists():
        raise SystemExit(f"Image not found for scene {scene.scene_id}: {image_path}")

    visual = fit_image_on_canvas(
        image_path,
        width=ctx.width,
        height=ctx.height,
        duration_sec=duration_sec,
    )
    audio = AudioFileClip(str(padded_wav))
    return visual.with_audio(audio)


def ensure_scene_audio(
    ctx: BuildContext,
    scene: Scene,
    *,
    api_key: str | None,
    force_audio: bool,
    cached_hashes: dict[str, str],
) -> tuple[Path, float, float]:
    cache_dir = audio_cache_dir(ctx.output_dir, ctx.voice)
    raw_path = raw_audio_path(cache_dir, scene)
    padded_path = padded_audio_path(cache_dir, scene)

    needs_tts = force_audio or not raw_path.exists()
    if not needs_tts and cached_hashes.get(scene.scene_id) != scene.narration_hash:
        needs_tts = True

    if needs_tts:
        if api_key is None:
            raise SystemExit(
                f"Missing cached audio for scene {scene.scene_id}: {raw_path}\n"
                "Run without --video-only first, or set XAI_API_KEY."
            )
        print(f"TTS {scene.index:03d}/{len(ctx.scenes)} {scene.scene_id}")
        synthesize_xai_tts(
            scene.narration,
            voice=ctx.voice,
            output_path=raw_path,
            api_key=api_key,
        )

    speech = AudioSegment.from_file(raw_path)
    audio_sec = len(speech) / 1000.0
    scene_sec = build_padded_audio(
        raw_path,
        padded_path,
        pause_after_sec=ctx.pause_after_sec,
    )
    return raw_path, audio_sec, scene_sec


def run_test_tts(*, voice: str) -> None:
    api_key = require_xai_api_key()
    prefix = api_key[:8] if len(api_key) >= 8 else api_key[:4]
    print(f"XAI_API_KEY loaded ({len(api_key)} chars, starts with {prefix}...)")
    output_path = Path("/tmp/xai-tts-script-test.mp3")
    synthesize_xai_tts(
        "Test.",
        voice=voice,
        output_path=output_path,
        api_key=api_key,
    )
    print(f"TTS OK — wrote {output_path} ({output_path.stat().st_size} bytes)")


def build_video(
    ctx: BuildContext,
    *,
    audio_only: bool,
    video_only: bool,
    force_audio: bool,
    output_path: Path | None,
) -> None:
    api_key = None if video_only else require_xai_api_key()
    cached_hashes = load_timings_hashes(timings_path(ctx.output_dir, ctx.locale))

    timings: list[dict[str, Any]] = []
    scene_clips: list[CompositeVideoClip] = []

    for scene in ctx.scenes:
        raw_path, audio_sec, scene_sec = ensure_scene_audio(
            ctx,
            scene,
            api_key=api_key,
            force_audio=force_audio,
            cached_hashes=cached_hashes,
        )
        timings.append(
            {
                "id": scene.scene_id,
                "index": scene.index,
                "image": scene.image,
                "narration_hash": scene.narration_hash,
                "audio_sec": round(audio_sec, 3),
                "scene_sec": round(scene_sec, 3),
                "audio_file": str(raw_path.relative_to(ctx.output_dir)),
            }
        )
        print(
            f"Scene {scene.index:03d} {scene.scene_id}: "
            f"audio={audio_sec:.2f}s scene={scene_sec:.2f}s"
        )

        if not audio_only:
            padded_path = padded_audio_path(
                audio_cache_dir(ctx.output_dir, ctx.voice), scene
            )
            scene_clips.append(
                build_scene_clip(ctx, scene, padded_path, scene_sec)
            )

    write_timings_file(ctx, timings)

    if audio_only:
        print(f"Cached {len(timings)} audio files under {audio_cache_dir(ctx.output_dir, ctx.voice)}")
        return

    if not scene_clips:
        raise SystemExit("No scenes to render.")

    final = concatenate_videoclips(scene_clips, method="compose")
    destination = output_path or (
        ctx.output_dir / f"{ctx.tutorial_id}-{ctx.locale}.mp4"
    )
    print(f"Writing {destination}")
    final.write_videofile(
        str(destination),
        fps=ctx.fps,
        codec="libx264",
        audio_codec="aac",
        logger=None,
    )

    for clip in scene_clips:
        clip.close()
    final.close()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Path to tutorial manifest.yaml")
    parser.add_argument(
        "--locale",
        default="en",
        help="Locale key under scene narration (default: en)",
    )
    parser.add_argument(
        "--voice",
        default=None,
        help="xAI voice_id (default: manifest tts.voice_id or ursa)",
    )
    parser.add_argument(
        "--pause-after-sec",
        type=float,
        default=1.0,
        help="Silence appended after each speech block (default: 1.0)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Output MP4 path (default: <tutorial-dir>/output/<id>-<locale>.mp4)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Process only the first N scenes (for testing)",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--audio-only",
        action="store_true",
        help="Generate and cache TTS audio only (no video encode)",
    )
    mode.add_argument(
        "--video-only",
        action="store_true",
        help="Build video from cached audio (skip TTS API calls)",
    )
    parser.add_argument(
        "--force-audio",
        action="store_true",
        help="Regenerate all TTS audio even when cached narration is unchanged",
    )
    parser.add_argument(
        "--test-tts",
        action="store_true",
        help="Call xAI TTS once and exit (verifies key + same code path as the build)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if not args.manifest.exists():
        raise SystemExit(f"Manifest not found: {args.manifest}")

    ctx = load_build_context(
        args.manifest,
        locale=args.locale,
        voice=args.voice,
        pause_after_sec=args.pause_after_sec,
        limit=args.limit,
    )
    if args.test_tts:
        run_test_tts(voice=ctx.voice)
        return 0
    build_video(
        ctx,
        audio_only=args.audio_only,
        video_only=args.video_only,
        force_audio=args.force_audio,
        output_path=args.output,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())