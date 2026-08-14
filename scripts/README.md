<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Shared scripts

Docker runners and retail data generators used by `integration-tests/` and `retail-example/`.

## Runners

```bash
./scripts/run-postgres.sh up
./scripts/run-marquez.sh up   # OpenLineage / Marquez (API :5001, UI :3001)
./scripts/run-hop.sh <hop-project-dir> <workflow>
./scripts/smoke-lineage-view.sh   # optional Marquez GET /lineage + hop_export check
```

`${MARQUEZ_API}` (`http://localhost:5001/api/v1/lineage`) is the **Export data lineage** POST URL. `${MARQUEZ_BASE_URL}` (`http://localhost:5001`) is the Marquez host root for a **lineage backend**. See [docs/hop-lineage-view.adoc](../docs/hop-lineage-view.adoc). `smoke-lineage-view.sh` is not part of `mvn test` or `run-tests-all-databases.sh`.

| Project folder | Example |
|----------------|---------|
| `integration-tests` | `./scripts/run-hop.sh integration-tests tests/run-tests.hwf` |
| `retail-example` | `./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf` |

Thin wrappers also exist in `integration-tests/run-tests.sh` and `integration-tests/run-postgres.sh`.

## Retail data generators

`scripts/end-to-end/` — Python helpers invoked from `retail-example` workflows:

- `generate-retail-data.py` — initial snapshot or period update CSVs
- `activate-source-wave.py` — switch catalog file masks
- `generate-catalog-sources.py` — (re)create E2E catalog JSON entries

## Video tutorials

Rebuild a narrated tutorial MP4 from `docs/video-tutorials/<name>/` using xAI TTS and MoviePy:

```bash
export XAI_API_KEY=your-key-here

# Quick test (first 3 scenes)
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --limit 3

# Full build
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --locale en

# Video only from cached TTS
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --video-only --locale en
```

The script creates `scripts/video/.venv` on first run and installs `moviepy`, `pydub`, `requests`, and `PyYAML`. Video builds require `ffmpeg` on `PATH`.

Each scene lasts for the spoken audio plus a 1 second pause (configurable with `--pause-after-sec`). See `docs/video-tutorials/your-first-data-vault-model/README.md`.

Lower-level entry point: `scripts/video/build_moviepy_video.py`.