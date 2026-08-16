# Your first Data Vault model — video tutorial sources

Step-by-step tutorial video for building a retail Data Vault model with the Coach panel.

## Prerequisites

- `XAI_API_KEY` from [console.x.ai](https://console.x.ai)
- `ffmpeg` on `PATH` (used by MoviePy and pydub)

```bash
export XAI_API_KEY=your-key-here
```

## Build the video

Scene length is driven by xAI TTS audio plus a **1 second** pause after each narration block.

```bash
# Quick test: first 3 scenes only
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --limit 3

# Cache all TTS audio (no video encode)
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --audio-only

# Full build (TTS + video)
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --locale en

# Re-encode video from cached audio
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --video-only --locale en
```

Cached audio: `output/audio/xai-ursa/001-<scene-id>.mp3` and `output/timings-en.yaml`.

Output MP4: `output/your-first-data-vault-model-en.mp4`.

## Video settings

Edit `tutorial.video` in `manifest.yaml` for resolution and fps:

```yaml
video:
  width: 1920
  height: 1080
  fps: 30
```

Narration voice is set under `tutorial.tts.voice_id` (default: `ursa`). Override at build time with `--voice`.

## Hop project

The bundled Hop project under `hop-project/` matches the screenshots. Open `hop-project/retail-model.hdv` in Hop GUI to reproduce or update captures.

This video uses **catalog Import tables** and the coach panel, then a per-model Data Vault load. The current recommended **build order** is source model → publish to catalog → generate Data Vault → resource definition group → **Update resource definition group**: [Getting started — building an EDW](../../getting-started-edw.adoc). Pictures: [Architecture](../../architecture.adoc).

## Related documentation

- [Getting started — building an EDW](../../getting-started-edw.adoc)
- [Architecture](../../architecture.adoc)
- [Data Vault plugin guide](../../datavault-plugin.adoc)
- [Getting started — retail example](../../getting-started-retail.adoc)