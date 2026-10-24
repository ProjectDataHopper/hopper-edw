<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Data Hopper EDW video series

Short videos for someone who already uses Apache Hop and is opening Data Hopper EDW. Issue [#224](https://github.com/ProjectDataHopper/hopper-edw/issues/224).

The renderer is adapted from the Apache Hop series ([apache/hop#7931](https://github.com/apache/hop/issues/7931)). Each video is a folder with a `manifest.yaml`, PNG stills, and short muted clips. `tools/video-series/render.py` speaks the narration, times every scene to the longest language, and writes a 1080p MP4. Generated files go under `output/` and are not committed.

English is the source. Further languages are added after the English cut has been heard. The publish set is `zh-CN`, `es-ES`, `pt-PT`, `fr-FR`, `de-DE`, and `it-IT`. Product words in `glossary.yaml` stay in English. Say **resource definition group** in full. Do not say RDG.

The voice for this series is `ara`. The Apache Hop series uses `orion`. Set `voice_id: ara` on every episode.

## Series

| Folder | Issue | Topic | State |
| --- | --- | --- | --- |
| `01-orientation` | 225 | What Data Hopper EDW is | English and six translations, voice `ara` |
| `02-installation` | 226 | Installation and configuration | Not started |
| `03-sources` | 227 | Sources | Not started |
| `04-source-models` | 228 | Source models | Not started |
| `05-data-vault` | 229 | Building a Data Vault | Not started |
| `06-resource-definition-group` | 230 | The resource definition group | Not started |
| `07-updating-a-group` | 231 | Updating a resource definition group | Not started |
| `08-business-vault` | 232 | Business Vault | Not started |
| `09-dimensional-model` | 233 | Dimensional model | Not started |
| `10-metrics-and-analytics` | 234 | Metrics and analytics operations | Not started |

Later videos may point at an earlier one in a single sentence. They do not re-teach it. The longer tutorials under `docs/video-tutorials/` stay.

## How a scene is timed

A scene is one spoken idea, about 20 to 40 seconds, which is 45 to 110 English words. The picture stays up for the longest voice-over among the languages you render, plus `padding_after_sec` (0.4 seconds). The 400 ms fade happens inside that pause. A clip shorter than the voice-over holds its last frame. A clip longer than the voice-over plays to the end. Set `trim_sec` on that scene when the recording should be cut.

## Capture rules

- Capture the Hop window as large as it will go. It does not have to be exactly 1920×1080. The renderer fits each picture into that frame. One Hop theme for the whole series. The sample is `retail-example`.
- PNG stills when the viewer should read the screen. Muted MP4 clips, 30 fps, only for something that moves.
- No secrets, personal paths, bookmarks, or notification popups.
- File names are the scene id (`images/what-it-is.png`).
- Do not paint the watermark. The renderer puts "Data Hopper" at the bottom right.

`render.py shotlist <video>` writes `output/shotlist.md`.

Scenes with `card_title` are title cards. `render.py cards <video>` draws them. The background is the Hop GUI dark-mode gray (`#242424`). Every title card places `docs/images/edw-logo.svg` at the top left and `docs/video-series/assets/logo_hop.svg` at the bottom right. Replace the PNG when you want a different card.

## Rebuild

From the repository root, with `ffmpeg` on `PATH` and the packages in `tools/video-series/requirements.txt`:

```bash
python3 tools/video-series/render.py self-test
python3 tools/video-series/render.py shotlist 01-orientation
python3 tools/video-series/render.py cards 01-orientation
python3 tools/video-series/render.py validate 01-orientation
```

Voice samples, after `XAI_API_KEY` is exported:

```bash
python3 tools/video-series/render.py voicesample --text "Data Hopper EDW builds an enterprise data warehouse from model files in Hop GUI. You describe the warehouse once. Hop generates the pipelines that load it."
```

English preview, then the publish render once every locale is in the manifest:

```bash
python3 tools/video-series/render.py render 01-orientation --locales en
python3 tools/video-series/render.py render 01-orientation --publish
```

`--silence` exercises the renderer without speech. `--audio-only` caches speech and writes timings. `--video-only` rebuilds the MP4 from that cache. `--limit 3` renders the first three scenes. `--force-audio` speaks again even when the text is unchanged.

## Add a video

Copy `01-orientation`, give it a new `video.id`, and fill `scenes`. Each scene needs an `id`, a `title`, a `visual`, a `visual_type` of `still` or `clip`, a `shot` line, and English narration of 45 to 110 words. Run `shotlist`, capture what is missing, then `validate` and `render`. Keep the claims in line with the guide named in the task.
