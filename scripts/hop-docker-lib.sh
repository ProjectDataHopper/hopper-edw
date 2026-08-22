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
# Shared helpers for running Apache Hop in Docker across Hop projects in this repo.
# Source after setting SCRIPT_DIR to the scripts/ directory.

: "${SCRIPT_DIR:?SCRIPT_DIR must be set before sourcing hop-docker-lib.sh}"

DOCKER_DIR="${SCRIPT_DIR}/docker"
HOP_IMAGE_NAME="docker-hop:latest"
HOP_COMPOSE_FILE="${DOCKER_DIR}/compose.hop.yml"
HOP_POSTGRES_LOCAL_COMPOSE_FILE="${DOCKER_DIR}/compose.postgres-local.yml"
HOP_SVG_COMPOSE_FILE="${DOCKER_DIR}/compose.svg.yml"
METRICS_COMPOSE_FILE="${HOP_COMPOSE_FILE}"
HOP_ENTRYPOINT_INIT="${HOP_ENTRYPOINT_INIT:-/opt/hop-datavault/entrypoint-init.sh}"
LOCAL_POSTGRES_HOST="${LOCAL_POSTGRES_HOST:-localhost}"
LOCAL_POSTGRES_PORT="${LOCAL_POSTGRES_PORT:-54320}"
LOCAL_POSTGRES_USER="${LOCAL_POSTGRES_USER:-test}"
LOCAL_POSTGRES_DB="${LOCAL_POSTGRES_DB:-test}"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
WORKSPACE_PREFIX="/workspace"

HOP_PROJECT_DIR="${HOP_PROJECT_DIR:-integration-tests}"
HOP_PROJECT_FOLDER="${HOP_PROJECT_FOLDER:-${WORKSPACE_PREFIX}/${HOP_PROJECT_DIR}}"
HOP_PROJECT_NAME="${HOP_PROJECT_NAME:-hop-data-vault}"
LOCAL_POSTGRES_ENV_FILE="${LOCAL_POSTGRES_ENV_FILE:-${HOP_PROJECT_FOLDER}/environments/local-docker-postgres.json}"

INTEGRATION_TESTS_DIR="${REPO_ROOT}/integration-tests"
METRICS_OVERVIEW_CSV="${INTEGRATION_TESTS_DIR}/metrics/metrics-overview.csv"
COLLECT_METRICS_PIPELINE="${WORKSPACE_PREFIX}/integration-tests/tests/shared/collect-metrics-results.hpl"

hop_project_host_dir() {
  printf '%s/%s\n' "${REPO_ROOT}" "${HOP_PROJECT_DIR}"
}

workflow_arg_to_container_path() {
  workflow_arg="${1:-tests/run-tests.hwf}"
  case "${workflow_arg}" in
    "${WORKSPACE_PREFIX}"/*)
      printf '%s\n' "${workflow_arg}"
      ;;
    /project/*)
      printf '%s/integration-tests%s\n' "${WORKSPACE_PREFIX}" "${workflow_arg#/project}"
      ;;
    /integration-tests/*)
      printf '%s%s\n' "${WORKSPACE_PREFIX}" "${workflow_arg}"
      ;;
    /*)
      printf '%s%s\n' "${HOP_PROJECT_FOLDER}" "${workflow_arg}"
      ;;
    *)
      printf '%s/%s\n' "${HOP_PROJECT_FOLDER}" "${workflow_arg}"
      ;;
  esac
}

local_postgres_ready() {
  if command -v pg_isready >/dev/null 2>&1; then
    pg_isready -h "${LOCAL_POSTGRES_HOST}" -p "${LOCAL_POSTGRES_PORT}" \
      -U "${LOCAL_POSTGRES_USER}" -d "${LOCAL_POSTGRES_DB}" >/dev/null 2>&1
    return $?
  fi

  if command -v nc >/dev/null 2>&1; then
    nc -z "${LOCAL_POSTGRES_HOST}" "${LOCAL_POSTGRES_PORT}" >/dev/null 2>&1
    return $?
  fi

  (echo >/dev/tcp/"${LOCAL_POSTGRES_HOST}"/"${LOCAL_POSTGRES_PORT}") >/dev/null 2>&1
}

wait_for_local_postgres() {
  max_attempts="${1:-30}"
  attempt=1
  while [ "${attempt}" -le "${max_attempts}" ]; do
    if local_postgres_ready; then
      return 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done
  return 1
}

LOCAL_POSTGRES_CONTAINER="${LOCAL_POSTGRES_CONTAINER:-hop-data-vault-postgres-local}"

# Creates retail-example databases missing from volumes initialized before init.sql was updated.
ensure_local_postgres_retail_databases() {
  if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${LOCAL_POSTGRES_CONTAINER}"; then
    return 0
  fi

  for db in test_source test_edw test_ops; do
    exists="$(
      docker exec "${LOCAL_POSTGRES_CONTAINER}" \
        psql -U "${LOCAL_POSTGRES_USER}" -p "${LOCAL_POSTGRES_PORT}" -d "${LOCAL_POSTGRES_DB}" -tAc \
        "SELECT 1 FROM pg_database WHERE datname = '${db}'" 2>/dev/null \
        | tr -d '[:space:]'
    )"
    if [ "${exists}" != "1" ]; then
      echo "Creating missing PostgreSQL database: ${db}"
      docker exec "${LOCAL_POSTGRES_CONTAINER}" \
        psql -U "${LOCAL_POSTGRES_USER}" -p "${LOCAL_POSTGRES_PORT}" -d "${LOCAL_POSTGRES_DB}" \
        -c "CREATE DATABASE ${db} OWNER ${LOCAL_POSTGRES_USER};" >/dev/null
    fi
  done
}

require_local_postgres() {
  if local_postgres_ready; then
    return 0
  fi
  echo "PostgreSQL is not reachable at ${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}." >&2
  echo "Start the local test database with: ./scripts/run-postgres.sh up" >&2
  return 1
}

strip_carriage_returns() {
  printf '%s' "$1" | tr -d '\r'
}

# Newest host-side plugin assembly under target/ (signal that Maven package ran).
latest_plugin_zip() {
  # shellcheck disable=SC2012
  ls -1t "${REPO_ROOT}"/target/hopper-edw-*.zip 2>/dev/null | head -n 1
}

# Epoch seconds for a file mtime (GNU or BSD stat).
file_mtime_epoch() {
  file="${1:?}"
  if stat -c %Y "${file}" >/dev/null 2>&1; then
    stat -c %Y "${file}"
  elif stat -f %m "${file}" >/dev/null 2>&1; then
    stat -f %m "${file}"
  else
    return 1
  fi
}

# Epoch seconds for a local Docker image Created timestamp.
docker_image_created_epoch() {
  image="${1:?}"
  created="$(docker image inspect "${image}" --format '{{.Created}}' 2>/dev/null)" || return 1
  if [ -z "${created}" ]; then
    return 1
  fi
  # GNU date (Linux / Git Bash); BusyBox date -d often works the same way.
  date -d "${created}" +%s 2>/dev/null || return 1
}

# True when a host plugin zip exists and is newer than docker-hop:latest.
hop_image_stale_vs_plugin_zip() {
  if [ "${HOP_IMAGE_SKIP_FRESHNESS:-}" = "1" ] || [ "${HOP_IMAGE_SKIP_FRESHNESS:-}" = "true" ]; then
    return 1
  fi
  if ! docker image inspect "${HOP_IMAGE_NAME}" >/dev/null 2>&1; then
    return 1
  fi
  plugin_zip="$(latest_plugin_zip)"
  if [ -z "${plugin_zip}" ] || [ ! -f "${plugin_zip}" ]; then
    return 1
  fi
  zip_epoch="$(file_mtime_epoch "${plugin_zip}")" || return 1
  image_epoch="$(docker_image_created_epoch "${HOP_IMAGE_NAME}")" || return 1
  if [ "${zip_epoch}" -gt "${image_epoch}" ]; then
    # Export paths for ensure_hop_image messaging (caller's subshell-safe via globals).
    HOP_STALE_PLUGIN_ZIP="${plugin_zip}"
    HOP_STALE_ZIP_EPOCH="${zip_epoch}"
    HOP_STALE_IMAGE_EPOCH="${image_epoch}"
    return 0
  fi
  return 1
}

format_epoch() {
  epoch="${1:?}"
  date -d "@${epoch}" '+%Y-%m-%d %H:%M:%S %z' 2>/dev/null || printf '%s\n' "${epoch}"
}

build_hop_image() {
  compose_file="${1:-${HOP_COMPOSE_FILE}}"
  hop_image_version="$(strip_carriage_returns "${HOP_IMAGE_VERSION:-}")"
  if [ -n "${hop_image_version}" ]; then
    docker compose -f "${compose_file}" build --build-arg "HOP_IMAGE_VERSION=${hop_image_version}" hop
  else
    docker compose -f "${compose_file}" build hop
  fi
}

# Ensure docker-hop:latest exists and is at least as new as target/hopper-edw-*.zip.
# Rebuilds when the image is missing or when a host plugin package is newer than the image
# (common after "mvn package" without "./scripts/rebuild-hop.sh").
# Set HOP_IMAGE_SKIP_FRESHNESS=1 to only build when the image is missing.
ensure_hop_image() {
  compose_file="${1:-${HOP_COMPOSE_FILE}}"
  need_build=0
  reason=""

  if ! docker image inspect "${HOP_IMAGE_NAME}" >/dev/null 2>&1; then
    need_build=1
    reason="image missing"
  elif hop_image_stale_vs_plugin_zip; then
    need_build=1
    zip_rel="${HOP_STALE_PLUGIN_ZIP#"${REPO_ROOT}"/}"
    reason="plugin package newer than image"
    echo "Hop image ${HOP_IMAGE_NAME} is older than the host plugin package:" >&2
    echo "  zip:   ${zip_rel}  ($(format_epoch "${HOP_STALE_ZIP_EPOCH}"))" >&2
    echo "  image: ${HOP_IMAGE_NAME}  ($(format_epoch "${HOP_STALE_IMAGE_EPOCH}"))" >&2
    echo "Rebuilding so Docker tests pick up the latest plugin (or run: ./scripts/rebuild-hop.sh)." >&2
  fi

  if [ "${need_build}" -eq 0 ]; then
    return 0
  fi

  echo "Building Hop docker image (${HOP_IMAGE_NAME}): ${reason}..."
  build_hop_image "${compose_file}"
}

host_to_workspace_path() {
  host_path="${1:?path required}"

  case "${host_path}" in
    "${WORKSPACE_PREFIX}"/*|"${WORKSPACE_PREFIX}")
      printf '%s\n' "${host_path}"
      return 0
      ;;
    /project/*)
      printf '%s/integration-tests%s\n' "${WORKSPACE_PREFIX}" "${host_path#/project}"
      return 0
      ;;
    /integration-tests/*)
      printf '%s%s\n' "${WORKSPACE_PREFIX}" "${host_path}"
      return 0
      ;;
  esac

  if [ "${host_path#/}" = "${host_path}" ]; then
    host_abs="$(CDPATH= cd -- "$(dirname -- "${host_path}")" && pwd)/$(basename -- "${host_path}")"
  else
    host_abs="${host_path}"
  fi

  case "${host_abs}" in
    "${REPO_ROOT}"/*)
      rel="${host_abs#"${REPO_ROOT}"/}"
      printf '%s/%s\n' "${WORKSPACE_PREFIX}" "${rel}"
      return 0
      ;;
    "${REPO_ROOT}")
      printf '%s\n' "${WORKSPACE_PREFIX}"
      return 0
      ;;
    *)
      echo "Path is outside repository (${host_abs}); cannot map into container." >&2
      return 1
      ;;
  esac
}

collect_svg_output_paths() {
  paths=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -o|--output|-t|--target-folder)
        if [ "$#" -lt 2 ]; then
          return 1
        fi
        paths="${paths}${paths:+ }$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  printf '%s' "${paths}"
}

translate_svg_command_parameters() {
  translated=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -f|--file|-o|--output|-s|--source-folder|-t|--target-folder|--project-home)
        if [ "$#" -lt 2 ]; then
          echo "Missing value for ${1}" >&2
          return 1
        fi
        opt="$1"
        val="$2"
        shift 2
        container_val="$(host_to_workspace_path "${val}")" || return 1
        translated="${translated} ${opt} ${container_val}"
        ;;
      --magnification)
        if [ "$#" -lt 2 ]; then
          echo "Missing value for --magnification" >&2
          return 1
        fi
        translated="${translated} $1 $2"
        shift 2
        ;;
      --no-notes|--recursive|--show-hash-keys)
        translated="${translated} $1"
        shift
        ;;
      *)
        translated="${translated} $1"
        shift
        ;;
    esac
  done
  printf '%s\n' "${translated# }"
}

reclaim_path_ownership() {
  target_path="${1:-}"
  if [ -z "${target_path}" ] || [ ! -e "${target_path}" ]; then
    return 0
  fi
  if [ -f "${HOP_SVG_COMPOSE_FILE:-}" ]; then
    container_path="$(host_to_workspace_path "${target_path}" 2>/dev/null)" || container_path=""
    if [ -n "${container_path}" ]; then
      docker compose -f "${HOP_SVG_COMPOSE_FILE}" run --rm --no-deps --entrypoint chown hop \
        -R "${HOST_UID}:${HOST_GID}" "${container_path}" >/dev/null 2>&1 || true
    fi
  fi
  chown -R "${HOST_UID}:${HOST_GID}" "${target_path}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${target_path}" 2>/dev/null \
    || true
}

run_hop_docker_command() {
  compose_file="${1:?compose file required}"
  hop_command="${2:?hop command required}"
  hop_command_parameters="${3:-}"

  ensure_hop_image "${compose_file}"

  set +e
  docker compose -f "${compose_file}" run --rm --no-deps \
    -e HOP_FILE_PATH= \
    -e HOP_COMMAND="${hop_command}" \
    -e HOP_COMMAND_PARAMETERS="${hop_command_parameters}" \
    -e HOP_RUN_PARAMETERS= \
    -e HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH="${HOP_ENTRYPOINT_INIT}" \
    -e HOP_PROJECT_DIR="${HOP_PROJECT_DIR}" \
    -e HOP_PROJECT_FOLDER="${HOP_PROJECT_FOLDER}" \
    -e HOP_PROJECT_NAME="${HOP_PROJECT_NAME}" \
    -e HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS="${LOCAL_POSTGRES_ENV_FILE}" \
    hop
  run_exit=$?
  set -e
  return "${run_exit}"
}

reclaim_rdbms_connection_ownership() {
  rdbms_metadata_dir="$(hop_project_host_dir)/metadata/rdbms"
  for conn in CRM.json Vault.json; do
    path="${rdbms_metadata_dir}/${conn}"
    if [ ! -e "${path}" ]; then
      continue
    fi
    chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || sudo chown "${HOST_UID}:${HOST_GID}" "${path}" 2>/dev/null \
      || true
  done
}

clear_metrics_json_files_for_db() {
  db="${1:-}"
  if [ -z "${db}" ]; then
    return 0
  fi
  metrics_dir="${INTEGRATION_TESTS_DIR}/metrics/${db}"
  mkdir -p "${metrics_dir}"
  find "${metrics_dir}" -maxdepth 1 -name '*.json' -type f -delete 2>/dev/null || true
}

reclaim_metrics_folder_ownership() {
  db="${1:-}"
  compose_file="${2:-${HOP_COMPOSE_FILE}}"
  if [ -z "${db}" ]; then
    return 0
  fi
  metrics_dir="${INTEGRATION_TESTS_DIR}/metrics/${db}"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  docker compose -f "${compose_file}" run --rm --no-deps --entrypoint chown hop \
    -R "${HOST_UID}:${HOST_GID}" "${WORKSPACE_PREFIX}/integration-tests/metrics/${db}" >/dev/null 2>&1 || true
  chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || true
}

reclaim_metrics_tree_ownership() {
  metrics_dir="${INTEGRATION_TESTS_DIR}/metrics"
  if [ ! -d "${metrics_dir}" ]; then
    return 0
  fi
  if [ -f "${HOP_COMPOSE_FILE}" ]; then
    docker compose -f "${HOP_COMPOSE_FILE}" run --rm --no-deps --entrypoint chown hop \
      -R "${HOST_UID}:${HOST_GID}" "${WORKSPACE_PREFIX}/integration-tests/metrics" >/dev/null 2>&1 || true
  fi
  chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${metrics_dir}" 2>/dev/null \
    || true
}

reclaim_vault_catalog_ownership() {
  compose_file="${1:-${HOP_COMPOSE_FILE}}"
  vault_catalog_dir="${INTEGRATION_TESTS_DIR}/vault-catalog"
  if [ ! -d "${vault_catalog_dir}" ]; then
    return 0
  fi
  docker compose -f "${compose_file}" run --rm --no-deps --entrypoint chown hop \
    -R "${HOST_UID}:${HOST_GID}" "${WORKSPACE_PREFIX}/integration-tests/vault-catalog" >/dev/null 2>&1 || true
  chown -R "${HOST_UID}:${HOST_GID}" "${vault_catalog_dir}" 2>/dev/null \
    || sudo chown -R "${HOST_UID}:${HOST_GID}" "${vault_catalog_dir}" 2>/dev/null \
    || true
}

collect_metrics_overview() {
  if [ "${HOP_PROJECT_DIR}" != "integration-tests" ]; then
    return 0
  fi
  if ! find "${INTEGRATION_TESTS_DIR}/metrics" -name '*.json' -print -quit 2>/dev/null | grep -q .; then
    echo "No metrics JSON files found; skipping metrics overview collection."
    return 0
  fi
  if [ ! -f "${METRICS_COMPOSE_FILE}" ]; then
    echo "Missing compose file for metrics collection: ${METRICS_COMPOSE_FILE}" >&2
    return 1
  fi

  echo "=== Collecting metrics overview ==="
  rm -f "${METRICS_OVERVIEW_CSV}"
  set +e
  docker compose -f "${METRICS_COMPOSE_FILE}" run --rm --no-deps \
    -e HOP_FILE_PATH="${COLLECT_METRICS_PIPELINE}" \
    -e HOP_RUN_PARAMETERS= \
    -e HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH="${HOP_ENTRYPOINT_INIT}" \
    -e HOP_PROJECT_DIR=integration-tests \
    -e HOP_PROJECT_FOLDER="${WORKSPACE_PREFIX}/integration-tests" \
    -e HOP_PROJECT_NAME=hop-data-vault \
    -e HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS="${WORKSPACE_PREFIX}/integration-tests/environments/local-docker-postgres.json" \
    hop
  COLLECT_EXIT=$?
  set -e
  reclaim_metrics_tree_ownership

  if [ "${COLLECT_EXIT}" -ne 0 ]; then
    echo "FAILED: metrics overview collection (exit code ${COLLECT_EXIT})" >&2
    return "${COLLECT_EXIT}"
  fi
  if [ ! -f "${METRICS_OVERVIEW_CSV}" ]; then
    echo "Metrics overview pipeline finished but ${METRICS_OVERVIEW_CSV} was not created." >&2
    return 1
  fi
  overview_rows="$(wc -l < "${METRICS_OVERVIEW_CSV}" | tr -d ' ')"
  if [ "${overview_rows}" -le 1 ]; then
    echo "Metrics overview CSV has no data rows (only ${overview_rows} line(s))." >&2
    return 1
  fi
  echo "Wrote ${METRICS_OVERVIEW_CSV} (${overview_rows} lines)"
  return 0
}

print_metrics_overview_table() {
  if [ ! -f "${METRICS_OVERVIEW_CSV}" ]; then
    return 0
  fi

  echo ""
  echo "=== Metrics overview ==="
  python3 - "${METRICS_OVERVIEW_CSV}" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
rows = list(csv.reader(path.open(encoding="utf-8")))
if not rows:
    sys.exit(0)

widths = [max(len(row[i]) for row in rows) for i in range(len(rows[0]))]
for row in rows:
    print("  ".join(row[i].ljust(widths[i]) for i in range(len(row))))

data_rows = max(len(rows) - 1, 0)
print("")
print(f"Rows: {data_rows}  File: {path}")
PY
}

run_hop_docker_short_lived() {
  compose_file="${1:?compose file required}"
  hop_file_path="${2:?hop file path required}"
  metrics_folder="${3:-${WORKSPACE_PREFIX}/integration-tests/metrics/local}"
  entrypoint_extension="${4:-}"

  ensure_hop_image "${compose_file}"

  set +e
  if [ -n "${entrypoint_extension}" ]; then
    docker compose -f "${compose_file}" run --rm \
      -e HOP_FILE_PATH="${hop_file_path}" \
      -e "HOP_RUN_PARAMETERS=METRICS_FOLDER=${metrics_folder}" \
      -e "HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH=${entrypoint_extension}" \
      -e HOP_PROJECT_DIR="${HOP_PROJECT_DIR}" \
      -e HOP_PROJECT_FOLDER="${HOP_PROJECT_FOLDER}" \
      -e HOP_PROJECT_NAME="${HOP_PROJECT_NAME}" \
      -e HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS="${LOCAL_POSTGRES_ENV_FILE}" \
      hop
  else
    docker compose -f "${compose_file}" run --rm \
      -e HOP_FILE_PATH="${hop_file_path}" \
      -e "HOP_RUN_PARAMETERS=METRICS_FOLDER=${metrics_folder}" \
      -e HOP_CUSTOM_ENTRYPOINT_EXTENSION_SHELL_FILE_PATH="${HOP_ENTRYPOINT_INIT}" \
      -e HOP_PROJECT_DIR="${HOP_PROJECT_DIR}" \
      -e HOP_PROJECT_FOLDER="${HOP_PROJECT_FOLDER}" \
      -e HOP_PROJECT_NAME="${HOP_PROJECT_NAME}" \
      -e HOP_ENVIRONMENT_CONFIG_FILE_NAME_PATHS="${LOCAL_POSTGRES_ENV_FILE}" \
      hop
  fi
  run_exit=$?
  set -e
  return "${run_exit}"
}