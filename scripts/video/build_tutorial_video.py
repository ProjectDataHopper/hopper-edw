#!/usr/bin/env python3
"""Build a tutorial video from a YAML manifest, screenshots, and TTS narration."""
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
import asyncio
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

import yaml

try:
    import edge_tts
except ImportError:
    edge_tts = None  # type: ignore[assignment]

BuildMode = Literal["full", "audio", "video", "estimate"]

# FFmpeg 6.x xfade transition names (see: ffmpeg -h filter=xfade)
XFADE_TRANSITIONS = frozenset(
    {
        "fade",
        "wipeleft",
        "wiperight",
        "wipeup",
        "wipedown",
        "slideleft",
        "slideright",
        "slideup",
        "slidedown",
        "circlecrop",
        "rectcrop",
        "distance",
        "fadeblack",
        "fadewhite",
        "radial",
        "smoothleft",
        "smoothright",
        "smoothup",
        "smoothdown",
        "circleopen",
        "circleclose",
        "vertopen",
        "vertclose",
        "horzopen",
        "horzclose",
        "dissolve",
        "pixelize",
        "diagtl",
        "diagtr",
        "diagbl",
        "diagbr",
        "hlslice",
        "hrslice",
        "vuslice",
        "vdslice",
        "hblur",
        "fadegrays",
        "wipetl",
        "wipetr",
        "wipebl",
        "wipebr",
        "squeezeh",
        "squeezev",
        "zoomin",
        "fadefast",
        "fadeslow",
        "hlwind",
        "hrwind",
        "vuwind",
        "vdwind",
        "coverleft",
        "coverright",
        "coverup",
        "coverdown",
        "revealleft",
        "revealright",
        "revealup",
        "revealdown",
    }
)

KEN_BURNS_ALIASES = {"subtle": "smooth"}


@dataclass
class SceneTiming:
    scene_id: str
    narration: str
    narration_hash: str
    words: int
    audio_sec: float
    scene_sec: float
    audio_path: Path
    audio_file: str
    image: str


@dataclass
class BuildContext:
    manifest_path: Path
    tutorial_dir: Path
    output_dir: Path
    locale: str
    voice: str
    width: int
    height: int
    fps: int
    padding_after_sec: float
    transition_name: str
    transition_sec: float
    ken_burns: str
    scenes: list[dict[str, Any]]


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f"Required tool not found on PATH: {name}")
    return path


def run_command(cmd: list[str], *, quiet: bool = False) -> None:
    if not quiet:
        print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)


def probe_duration_seconds(path: Path) -> float:
    ffprobe = require_tool("ffprobe")
    result = subprocess.run(
        [
            ffprobe,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "json",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    data = json.loads(result.stdout)
    return float(data["format"]["duration"])


def normalize_narration(text: str) -> str:
    return " ".join(text.split())


def narration_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def word_count(text: str) -> int:
    return len(text.split())


def normalize_ken_burns(value: str) -> str:
    normalized = KEN_BURNS_ALIASES.get(value, value)
    if normalized not in {"none", "smooth"}:
        raise SystemExit(
            f"Unsupported ken_burns value '{value}'. Use none, smooth, or subtle."
        )
    return normalized


def normalize_transition(value: str) -> str:
    if value in {"none", "cut"}:
        return "none"
    if value == "crossfade":
        return "fade"
    if value not in XFADE_TRANSITIONS:
        supported = ", ".join(sorted(XFADE_TRANSITIONS | {"none", "cut"}))
        raise SystemExit(
            f"Unsupported default_transition value '{value}'. Supported: {supported}"
        )
    return value


def scene_hold_after_sec(scene: dict[str, Any]) -> float:
    if "hold_after_sec" in scene:
        return float(scene["hold_after_sec"])
    # Deprecated: min_duration_sec was a scene-length floor, not extra hold.
    return 0.0


def compute_scene_duration(
    audio_sec: float, *, padding_after_sec: float, hold_after_sec: float
) -> float:
    return audio_sec + padding_after_sec + hold_after_sec


def timings_path(output_dir: Path, locale: str) -> Path:
    return output_dir / f"timings-{locale}.yaml"


def audio_cache_path(output_dir: Path, locale: str, scene_id: str) -> Path:
    return output_dir / "audio" / locale / f"{scene_id}.mp3"


def build_video_filter(
    *,
    width: int,
    height: int,
    fps: int,
    frames: int,
    ken_burns: str,
) -> str:
    scale_pad = (
        f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
        f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color=black"
    )
    if ken_burns == "none":
        return f"{scale_pad},format=yuv420p"

    work_w = width * 2
    work_h = height * 2
    zoom_end = 0.04
    return (
        f"{scale_pad},"
        f"scale={work_w}:{work_h},"
        f"zoompan=z='1+{zoom_end}*on/{frames}':"
        f"x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':"
        f"d={frames}:s={width}x{height}:fps={fps},"
        f"format=yuv420p"
    )


async def synthesize_edge_tts(text: str, voice: str, output_path: Path) -> None:
    if edge_tts is None:
        raise SystemExit(
            "edge-tts is not installed. Run: pip install -r scripts/video/requirements.txt"
        )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(str(output_path))


def build_scene_video(
    image_path: Path,
    audio_path: Path,
    output_path: Path,
    *,
    width: int,
    height: int,
    fps: int,
    duration_sec: float,
    ken_burns: str,
) -> None:
    ffmpeg = require_tool("ffmpeg")
    frames = max(1, int(round(duration_sec * fps)))
    video_filter = build_video_filter(
        width=width,
        height=height,
        fps=fps,
        frames=frames,
        ken_burns=normalize_ken_burns(ken_burns),
    )

    run_command(
        [
            ffmpeg,
            "-y",
            "-loop",
            "1",
            "-i",
            str(image_path),
            "-i",
            str(audio_path),
            "-t",
            f"{duration_sec:.3f}",
            "-vf",
            video_filter,
            "-r",
            str(fps),
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "20",
            "-pix_fmt",
            "yuv420p",
            "-af",
            f"apad=whole_dur={duration_sec:.3f}",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            str(output_path),
        ]
    )


def concat_with_hard_cut(scene_paths: list[Path], output_path: Path) -> None:
    ffmpeg = require_tool("ffmpeg")

    if len(scene_paths) == 1:
        shutil.copy2(scene_paths[0], output_path)
        return

    concat_list = output_path.with_suffix(".concat.txt")
    try:
        lines = [f"file '{path.resolve()}'" for path in scene_paths]
        concat_list.write_text("\n".join(lines) + "\n", encoding="utf-8")
        run_command(
            [
                ffmpeg,
                "-y",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                str(concat_list),
                "-c",
                "copy",
                str(output_path),
            ]
        )
    finally:
        concat_list.unlink(missing_ok=True)


def concat_with_xfade(
    scene_paths: list[Path],
    scene_durations: list[float],
    output_path: Path,
    *,
    transition: str,
    transition_sec: float,
    fps: int,
) -> None:
    ffmpeg = require_tool("ffmpeg")

    if len(scene_paths) == 1:
        shutil.copy2(scene_paths[0], output_path)
        return

    actual_durations = [probe_duration_seconds(path) for path in scene_paths]
    if len(actual_durations) != len(scene_durations):
        raise SystemExit("Scene path and duration lists are out of sync")

    inputs: list[str] = []
    for scene in scene_paths:
        inputs.extend(["-i", str(scene)])

    filter_parts: list[str] = []
    audio_parts: list[str] = []

    current_label = "[0:v]"
    accumulated = actual_durations[0]

    for index in range(1, len(scene_paths)):
        next_label = f"[v{index}]" if index < len(scene_paths) - 1 else "[vout]"
        offset = max(0.0, accumulated - transition_sec)
        filter_parts.append(
            f"{current_label}[{index}:v]xfade=transition={transition}:"
            f"duration={transition_sec:.3f}:offset={offset:.3f}{next_label}"
        )
        current_label = next_label
        accumulated += actual_durations[index] - transition_sec

    for index in range(len(scene_paths)):
        audio_parts.append(f"[{index}:a]")

    audio_filter = "".join(audio_parts) + f"concat=n={len(scene_paths)}:v=0:a=1[aout]"
    filter_complex = ";".join(filter_parts + [audio_filter])

    run_command(
        [
            ffmpeg,
            "-y",
            *inputs,
            "-filter_complex",
            filter_complex,
            "-map",
            "[vout]",
            "-map",
            "[aout]",
            "-r",
            str(fps),
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "20",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            str(output_path),
        ]
    )


def concat_scenes(
    scene_paths: list[Path],
    scene_durations: list[float],
    output_path: Path,
    *,
    transition: str,
    transition_sec: float,
    fps: int,
) -> None:
    if transition == "none":
        concat_with_hard_cut(scene_paths, output_path)
        return
    concat_with_xfade(
        scene_paths,
        scene_durations,
        output_path,
        transition=transition,
        transition_sec=transition_sec,
        fps=fps,
    )


def write_srt(entries: list[tuple[float, float, str]], output_path: Path) -> None:
    def format_timestamp(seconds: float) -> str:
        millis = int(round(seconds * 1000))
        hours, rem = divmod(millis, 3_600_000)
        minutes, rem = divmod(rem, 60_000)
        secs, ms = divmod(rem, 1000)
        return f"{hours:02d}:{minutes:02d}:{secs:02d},{ms:03d}"

    lines: list[str] = []
    for index, (start, end, text) in enumerate(entries, start=1):
        lines.append(str(index))
        lines.append(f"{format_timestamp(start)} --> {format_timestamp(end)}")
        lines.append(text)
        lines.append("")

    output_path.write_text("\n".join(lines), encoding="utf-8")


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def resolve_voice(manifest: dict[str, Any], locale: str) -> str:
    tts = manifest.get("tutorial", {}).get("tts", {})
    voices = tts.get("voices", {})
    return voices.get(locale, "en-US-JennyNeural")


def load_build_context(manifest_path: Path, locale: str) -> BuildContext:
    manifest = load_manifest(manifest_path)
    tutorial = manifest["tutorial"]
    video_cfg = tutorial["video"]
    transition_name = normalize_transition(
        video_cfg.get("default_transition", "fade")
    )
    transition_ms = int(video_cfg.get("transition_ms", 700))
    tutorial_dir = manifest_path.parent
    output_dir = tutorial_dir / "output"
    output_dir.mkdir(parents=True, exist_ok=True)

    return BuildContext(
        manifest_path=manifest_path,
        tutorial_dir=tutorial_dir,
        output_dir=output_dir,
        locale=locale,
        voice=resolve_voice(manifest, locale),
        width=int(video_cfg.get("width", 1920)),
        height=int(video_cfg.get("height", 1080)),
        fps=int(video_cfg.get("fps", 30)),
        padding_after_sec=float(video_cfg.get("padding_after_sec", 0.5)),
        transition_name=transition_name,
        transition_sec=0.0 if transition_name == "none" else transition_ms / 1000.0,
        ken_burns=normalize_ken_burns(video_cfg.get("ken_burns", "smooth")),
        scenes=manifest["scenes"],
    )


def write_timings_file(
    ctx: BuildContext, timings: list[SceneTiming], path: Path
) -> None:
    payload = {
        "locale": ctx.locale,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "voice": ctx.voice,
        "padding_after_sec": ctx.padding_after_sec,
        "scenes": [
            {
                "id": timing.scene_id,
                "image": timing.image,
                "narration_hash": timing.narration_hash,
                "words": timing.words,
                "audio_sec": round(timing.audio_sec, 3),
                "scene_sec": round(timing.scene_sec, 3),
                "audio_file": timing.audio_file,
            }
            for timing in timings
        ],
    }
    with path.open("w", encoding="utf-8") as handle:
        yaml.safe_dump(payload, handle, sort_keys=False)


def load_timings_file(path: Path, ctx: BuildContext) -> list[SceneTiming]:
    if not path.exists():
        raise SystemExit(
            f"Timings file not found: {path}\n"
            "Run with --audio-only or --estimate-durations first."
        )

    with path.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle)

    if data.get("locale") != ctx.locale:
        raise SystemExit(
            f"Timings locale '{data.get('locale')}' does not match --locale {ctx.locale}"
        )

    timings: list[SceneTiming] = []
    for entry in data["scenes"]:
        audio_path = ctx.output_dir / entry["audio_file"]
        if not audio_path.exists():
            raise SystemExit(f"Missing cached audio: {audio_path}")

        timings.append(
            SceneTiming(
                scene_id=entry["id"],
                narration="",
                narration_hash=entry.get("narration_hash", ""),
                words=int(entry.get("words", 0)),
                audio_sec=float(entry["audio_sec"]),
                scene_sec=float(entry["scene_sec"]),
                audio_path=audio_path,
                audio_file=entry["audio_file"],
                image=entry["image"],
            )
        )
    return timings


def print_duration_table(timings: list[SceneTiming]) -> None:
    print()
    print(f"{'scene_id':<36} {'audio':>7} {'scene':>7} {'words':>6}")
    print("-" * 60)
    total_audio = 0.0
    total_scene = 0.0
    total_words = 0
    for timing in timings:
        print(
            f"{timing.scene_id:<36} "
            f"{timing.audio_sec:7.1f} "
            f"{timing.scene_sec:7.1f} "
            f"{timing.words:6d}"
        )
        total_audio += timing.audio_sec
        total_scene += timing.scene_sec
        total_words += timing.words
    print("-" * 60)
    print(
        f"{'TOTAL':<36} {total_audio:7.1f} {total_scene:7.1f} {total_words:6d}"
    )
    overlap_note = ""
    print(f"\nScene duration total: {total_scene:.1f}s{overlap_note}")


def export_kdenlive_assets(
    ctx: BuildContext, timings: list[SceneTiming], export_dir: Path
) -> Path:
    """Prepare numbered image + audio pairs for manual import in Kdenlive."""
    export_dir.mkdir(parents=True, exist_ok=True)
    rows: list[str] = ["index,scene_id,image,audio,scene_sec,narration"]

    for index, timing in enumerate(timings, start=1):
        prefix = f"{index:03d}-{timing.scene_id}"
        image_src = ctx.tutorial_dir / timing.image
        if not image_src.exists():
            raise SystemExit(f"Missing image for scene '{timing.scene_id}': {image_src}")

        image_dst = export_dir / f"{prefix}.png"
        audio_dst = export_dir / f"{prefix}.mp3"
        text_dst = export_dir / f"{prefix}.txt"

        shutil.copy2(image_src, image_dst)
        shutil.copy2(timing.audio_path, audio_dst)
        text_dst.write_text(timing.narration + "\n", encoding="utf-8")

        narration_csv = timing.narration.replace('"', '""')
        rows.append(
            f'{index},{timing.scene_id},{image_dst.name},{audio_dst.name},'
            f'{timing.scene_sec:.3f},"{narration_csv}"'
        )

    (export_dir / "import-order.csv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    (export_dir / "README.txt").write_text(
        "\n".join(
            [
                "Kdenlive import bundle",
                "",
                "Files are numbered in playback order: 001-scene-id.png + .mp3 + .txt",
                "",
                "Suggested Kdenlive workflow:",
                "1. Project → Add Clip: multi-select all .mp3 files (sort by name)",
                "2. Drag clips to timeline in numeric order",
                "3. For each clip, add matching .png on video track above",
                "4. Set image clip duration to match audio (scene_sec in import-order.csv)",
                "5. Add transitions in Kdenlive",
                "",
                "import-order.csv lists scene_sec (audio + padding) per scene.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return export_dir


def enrich_timings_with_narration(
    ctx: BuildContext, timings: list[SceneTiming]
) -> list[SceneTiming]:
    narration_by_id = {
        scene["id"]: normalize_narration(scene["narration"][ctx.locale])
        for scene in ctx.scenes
        if ctx.locale in scene.get("narration", {})
    }
    return [
        SceneTiming(
            scene_id=timing.scene_id,
            narration=narration_by_id.get(timing.scene_id, timing.narration),
            narration_hash=timing.narration_hash,
            words=timing.words,
            audio_sec=timing.audio_sec,
            scene_sec=timing.scene_sec,
            audio_path=timing.audio_path,
            audio_file=timing.audio_file,
            image=timing.image,
        )
        for timing in timings
    ]


def cached_scene_hash(path: Path, scene_id: str) -> str | None:
    if not path.exists():
        return None
    with path.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    for entry in data.get("scenes", []):
        if entry.get("id") == scene_id:
            return entry.get("narration_hash")
    return None


async def build_audio_pass(
    ctx: BuildContext, *, force_audio: bool = False
) -> list[SceneTiming]:
    timings_path_file = timings_path(ctx.output_dir, ctx.locale)
    timings: list[SceneTiming] = []

    for index, scene in enumerate(ctx.scenes, start=1):
        scene_id = scene["id"]
        narration_map = scene.get("narration", {})
        if ctx.locale not in narration_map:
            raise SystemExit(
                f"Scene '{scene_id}' has no narration for locale '{ctx.locale}'"
            )

        narration = normalize_narration(narration_map[ctx.locale])
        text_hash = narration_hash(narration)
        audio_path = audio_cache_path(ctx.output_dir, ctx.locale, scene_id)
        audio_file = str(audio_path.relative_to(ctx.output_dir))
        hold_after = scene_hold_after_sec(scene)

        reuse = (
            not force_audio
            and audio_path.exists()
            and cached_scene_hash(timings_path_file, scene_id) == text_hash
        )

        if reuse:
            print(f"Using cached audio {index}/{len(ctx.scenes)}: {scene_id}")
        else:
            print(f"Synthesizing scene {index}/{len(ctx.scenes)}: {scene_id}")
            await synthesize_edge_tts(narration, ctx.voice, audio_path)

        audio_sec = probe_duration_seconds(audio_path)
        scene_sec = compute_scene_duration(
            audio_sec,
            padding_after_sec=ctx.padding_after_sec,
            hold_after_sec=hold_after,
        )

        timings.append(
            SceneTiming(
                scene_id=scene_id,
                narration=narration,
                narration_hash=text_hash,
                words=word_count(narration),
                audio_sec=audio_sec,
                scene_sec=scene_sec,
                audio_path=audio_path,
                audio_file=audio_file,
                image=scene["image"],
            )
        )

    write_timings_file(ctx, timings, timings_path_file)
    print(f"Wrote timings: {timings_path_file}")
    return timings


def build_video_pass(
    ctx: BuildContext,
    timings: list[SceneTiming],
    output_path: Path,
    *,
    write_captions: bool,
    keep_temp: bool,
) -> Path:
    temp_dir = Path(tempfile.mkdtemp(prefix="hop-dv-video-"))
    scene_videos: list[Path] = []
    scene_durations: list[float] = []
    caption_entries: list[tuple[float, float, str]] = []
    timeline = 0.0

    try:
        for index, timing in enumerate(timings, start=1):
            image_path = ctx.tutorial_dir / timing.image
            if not image_path.exists():
                raise SystemExit(
                    f"Missing image for scene '{timing.scene_id}': {image_path}"
                )

            video_path = temp_dir / f"scene_{index:02d}_{timing.scene_id}.mp4"
            print(f"Encoding scene {index}/{len(timings)}: {timing.scene_id}")
            build_scene_video(
                image_path,
                timing.audio_path,
                video_path,
                width=ctx.width,
                height=ctx.height,
                fps=ctx.fps,
                duration_sec=timing.scene_sec,
                ken_burns=ctx.ken_burns,
            )

            scene_videos.append(video_path)
            scene_durations.append(timing.scene_sec)
            if timing.narration:
                caption_entries.append(
                    (timeline, timeline + timing.audio_sec, timing.narration)
                )
            timeline += timing.scene_sec - (
                ctx.transition_sec if index < len(timings) else 0.0
            )

        if ctx.transition_name == "none":
            print("Concatenating scenes with hard cuts...")
        else:
            print(
                f"Concatenating scenes with '{ctx.transition_name}' transitions..."
            )
        concat_scenes(
            scene_videos,
            scene_durations,
            output_path,
            transition=ctx.transition_name,
            transition_sec=ctx.transition_sec,
            fps=ctx.fps,
        )

        if write_captions and caption_entries:
            srt_path = output_path.with_suffix(".srt")
            write_srt(caption_entries, srt_path)
            print(f"Wrote captions: {srt_path}")

        print(f"Wrote video: {output_path}")
        return output_path
    finally:
        if keep_temp:
            print(f"Kept temp dir: {temp_dir}")
        else:
            shutil.rmtree(temp_dir, ignore_errors=True)


async def resolve_timings(
    ctx: BuildContext, *, mode: BuildMode, force_audio: bool
) -> list[SceneTiming]:
    if mode == "video":
        timings = load_timings_file(timings_path(ctx.output_dir, ctx.locale), ctx)
        return enrich_timings_with_narration(ctx, timings)
    return await build_audio_pass(ctx, force_audio=force_audio)


async def run_build(
    manifest_path: Path,
    *,
    locale: str,
    output_path: Path | None,
    mode: BuildMode,
    write_captions: bool,
    keep_temp: bool,
    force_audio: bool,
    export_kdenlive: bool,
) -> Path | None:
    ctx = load_build_context(manifest_path, locale)
    tutorial_id = load_manifest(manifest_path)["tutorial"]["id"]

    if output_path is None and mode in {"full", "video"}:
        output_path = ctx.output_dir / f"{tutorial_id}-{locale}.mp4"
    elif output_path is not None:
        output_path.parent.mkdir(parents=True, exist_ok=True)

    timings = await resolve_timings(ctx, mode=mode, force_audio=force_audio)

    if mode == "estimate":
        print_duration_table(timings)

    if export_kdenlive:
        export_dir = export_kdenlive_assets(
            ctx, timings, ctx.output_dir / "kdenlive" / locale
        )
        print(f"Wrote Kdenlive bundle: {export_dir}")

    if mode == "estimate":
        return None
    if mode == "audio":
        print(
            f"Cached {len(timings)} audio files under {ctx.output_dir / 'audio'}"
        )
        return None

    assert output_path is not None
    return build_video_pass(
        ctx,
        timings,
        output_path,
        write_captions=write_captions,
        keep_temp=keep_temp,
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Path to tutorial manifest.yaml")
    parser.add_argument(
        "--locale",
        default="en",
        help="Locale key under scene narration (default: en)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Output MP4 path (default: <tutorial-dir>/output/<id>-<locale>.mp4)",
    )
    parser.add_argument(
        "--srt",
        action="store_true",
        help="Also write an SRT caption file next to the MP4",
    )
    parser.add_argument(
        "--keep-temp",
        action="store_true",
        help="Keep intermediate scene files for debugging",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--estimate-durations",
        action="store_true",
        help="Generate TTS only, print per-scene durations, write timings YAML",
    )
    mode.add_argument(
        "--audio-only",
        action="store_true",
        help="Generate and cache TTS audio + timings YAML (no video encode)",
    )
    mode.add_argument(
        "--video-only",
        action="store_true",
        help="Build video from cached audio (skip TTS)",
    )
    parser.add_argument(
        "--force-audio",
        action="store_true",
        help="Regenerate all TTS audio even when cached narration is unchanged",
    )
    parser.add_argument(
        "--export-kdenlive",
        action="store_true",
        help="Write numbered PNG+MP3 pairs under output/kdenlive/<locale>/",
    )
    return parser.parse_args(argv)


def resolve_mode(args: argparse.Namespace) -> BuildMode:
    if args.estimate_durations:
        return "estimate"
    if args.audio_only:
        return "audio"
    if args.video_only:
        return "video"
    return "full"


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if not args.manifest.exists():
        raise SystemExit(f"Manifest not found: {args.manifest}")

    mode = resolve_mode(args)
    if mode in {"full", "video"}:
        require_tool("ffmpeg")
        require_tool("ffprobe")

    asyncio.run(
        run_build(
            args.manifest,
            locale=args.locale,
            output_path=args.output,
            mode=mode,
            write_captions=args.srt,
            keep_temp=args.keep_temp,
            force_audio=args.force_audio,
            export_kdenlive=args.export_kdenlive,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())