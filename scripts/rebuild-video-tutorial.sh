#!/bin/sh
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
# Rebuild a narrated tutorial video from docs/video-tutorials/<name>/manifest.yaml
#
# Usage:
#   ./scripts/rebuild-video-tutorial.sh <tutorial> [--locale en] [-- ...]
#
# Examples:
#   ./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --limit 3
#   ./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --locale en
#   ./scripts/rebuild-video-tutorial.sh docs/video-tutorials/your-first-data-vault-model
#

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
TUTORIALS_DIR="${REPO_ROOT}/docs/video-tutorials"
VENV_DIR="${SCRIPT_DIR}/video/.venv"
BUILD_SCRIPT="${SCRIPT_DIR}/video/build_moviepy_video.py"
REQUIREMENTS="${SCRIPT_DIR}/video/requirements.txt"

usage() {
  echo "Usage: $0 <tutorial> [build options]" >&2
  echo "" >&2
  echo "  <tutorial>  Folder name under docs/video-tutorials/ or a path to that folder." >&2
  echo "" >&2
  echo "Build options (passed to build_moviepy_video.py):" >&2
  echo "  --locale <code>         Narration locale (default: en)" >&2
  echo "  --voice <id>            xAI voice_id (default: ursa)" >&2
  echo "  --pause-after-sec <n>   Silence after each speech block (default: 1.0)" >&2
  echo "  --audio-only            Cache xAI TTS audio + timings YAML (no video)" >&2
  echo "  --video-only            Build MP4 from cached audio (no TTS API calls)" >&2
  echo "  --force-audio           Regenerate all TTS even if narration unchanged" >&2
  echo "  --test-tts              One TTS call via Python, then exit" >&2
  echo "  --limit <n>             Process only the first N scenes" >&2
  echo "  --output <path>         Custom output MP4 path" >&2
  echo "" >&2
  echo "Requires XAI_API_KEY for TTS (https://console.x.ai)." >&2
  echo "Video builds require ffmpeg on PATH." >&2
  echo "" >&2
  echo "Available tutorials:" >&2
  list_tutorials >&2
}

list_tutorials() {
  found=0
  if [ -d "${TUTORIALS_DIR}" ]; then
    for dir in "${TUTORIALS_DIR}"/*; do
      [ -d "${dir}" ] || continue
      if [ -f "${dir}/manifest.yaml" ]; then
        echo "  $(basename "${dir}")"
        found=1
      fi
    done
  fi
  if [ "${found}" -eq 0 ]; then
    echo "  (none with manifest.yaml)"
  fi
}

resolve_tutorial_dir() {
  tutorial="$1"
  case "${tutorial}" in
    /*)
      printf '%s\n' "${tutorial}"
      ;;
    */*)
      printf '%s\n' "${REPO_ROOT}/${tutorial}"
      ;;
    *)
      printf '%s\n' "${TUTORIALS_DIR}/${tutorial}"
      ;;
  esac
}

ensure_venv() {
  if [ ! -x "${VENV_DIR}/bin/python" ]; then
    echo "Creating virtual environment at scripts/video/.venv ..."
    python3 -m venv "${VENV_DIR}"
  fi

  if ! "${VENV_DIR}/bin/python" -c "import moviepy, pydub, requests, yaml" >/dev/null 2>&1; then
    echo "Installing video build dependencies ..."
    "${VENV_DIR}/bin/pip" install -r "${REQUIREMENTS}"
  fi
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required tool not found on PATH: $1" >&2
    exit 1
  fi
}

if [ "$#" -lt 1 ]; then
  usage
  exit 1
fi

case "$1" in
  -h|--help|help)
    usage
    exit 0
    ;;
esac

TUTORIAL_DIR="$(resolve_tutorial_dir "$1")"
shift

MANIFEST="${TUTORIAL_DIR}/manifest.yaml"
if [ ! -f "${MANIFEST}" ]; then
  echo "Manifest not found: ${MANIFEST}" >&2
  echo "" >&2
  echo "Expected docs/video-tutorials/<name>/manifest.yaml" >&2
  list_tutorials >&2
  exit 1
fi

needs_ffmpeg=1
for arg in "$@"; do
  case "${arg}" in
    --audio-only)
      needs_ffmpeg=0
      break
      ;;
  esac
done

if [ "${needs_ffmpeg}" -eq 1 ]; then
  require_tool ffmpeg
fi

ensure_venv

if [ "${needs_ffmpeg}" -eq 1 ]; then
  echo "Rebuilding tutorial video from ${MANIFEST}"
else
  echo "Running xAI TTS audio pass from ${MANIFEST}"
fi
exec "${VENV_DIR}/bin/python" "${BUILD_SCRIPT}" "${MANIFEST}" "$@"