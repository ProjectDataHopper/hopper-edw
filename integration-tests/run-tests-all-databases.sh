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
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SCRIPTS_DIR="$(CDPATH= cd -- "${SCRIPT_DIR}/../scripts" && pwd)"
SCRIPT_DIR="${SCRIPTS_DIR}"
. "${SCRIPTS_DIR}/hop-docker-lib.sh"
HOP_PROJECT_DIR=integration-tests
HOP_PROJECT_FOLDER="${WORKSPACE_PREFIX}/integration-tests"
export HOP_PROJECT_DIR HOP_PROJECT_FOLDER
INTEGRATION_TESTS_HOST="${SCRIPTS_DIR}/../integration-tests"

RDBMS_METADATA_DIR="${INTEGRATION_TESTS_HOST}/metadata/rdbms"
BACKUP_DIR=""

# Default matrix (must match scripts/docker/compose.<name>.yml).
ALL_DATABASES="postgres mysql singlestore sqlserver"
# Opt-in engines: allowed by name, not run when the script is invoked with no arguments.
OPT_IN_DATABASES="snowflake"

is_allowed_database() {
  candidate="$1"
  for allowed in ${ALL_DATABASES} ${OPT_IN_DATABASES}; do
    if [ "${candidate}" = "${allowed}" ]; then
      return 0
    fi
  done
  return 1
}

usage() {
  echo "Usage: $(basename "$0") [engine]..." >&2
  echo "  Default engines: ${ALL_DATABASES}" >&2
  echo "  Opt-in engines: ${OPT_IN_DATABASES} (requires LOCALSTACK_AUTH_TOKEN)" >&2
  echo "  No arguments: run the suite against the default engines." >&2
  echo "  One or more arguments: each must be a known engine name." >&2
  echo "  Example: $(basename "$0") postgres" >&2
  echo "  Example: $(basename "$0") postgres mysql" >&2
  echo "  Example: LOCALSTACK_AUTH_TOKEN=... $(basename "$0") snowflake" >&2
}

if [ "$#" -eq 0 ]; then
  DATABASES="${ALL_DATABASES}"
else
  DATABASES=""
  for arg in "$@"; do
    if ! is_allowed_database "${arg}"; then
      echo "Unknown database profile '${arg}'." >&2
      echo "Allowed: ${ALL_DATABASES} ${OPT_IN_DATABASES}" >&2
      usage
      exit 1
    fi
    DATABASES="${DATABASES} ${arg}"
  done
  # trim leading space
  DATABASES="${DATABASES# }"
fi

FAIL=0
METRICS_FAIL=0
FAILED_ENGINES=""
PASSED_ENGINES=""
SKIPPED_ENGINES=""
ACTIVE_COMPOSE=""

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

backup_rdbms_connections() {
  if [ -n "${BACKUP_DIR}" ]; then
    return 0
  fi
  for conn in CRM.json Vault.json; do
    if [ ! -f "${RDBMS_METADATA_DIR}/${conn}" ]; then
      echo "Missing RDBMS connection metadata: ${RDBMS_METADATA_DIR}/${conn}" >&2
      exit 1
    fi
  done
  BACKUP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hop-data-vault-rdbms-backup.XXXXXX")"
  cp "${RDBMS_METADATA_DIR}/CRM.json" "${BACKUP_DIR}/CRM.json"
  cp "${RDBMS_METADATA_DIR}/Vault.json" "${BACKUP_DIR}/Vault.json"
  echo "Backed up local CRM/Vault connections to ${BACKUP_DIR}"
}

restore_rdbms_connections() {
  if [ -z "${BACKUP_DIR}" ] || [ ! -d "${BACKUP_DIR}" ]; then
    return 0
  fi
  reclaim_rdbms_connection_ownership
  cp "${BACKUP_DIR}/CRM.json" "${RDBMS_METADATA_DIR}/CRM.json"
  cp "${BACKUP_DIR}/Vault.json" "${RDBMS_METADATA_DIR}/Vault.json"
  rm -rf "${BACKUP_DIR}"
  BACKUP_DIR=""
  echo "Restored local CRM/Vault connections"
}

cleanup() {
  if [ -n "${ACTIVE_COMPOSE}" ]; then
    docker compose -f "${ACTIVE_COMPOSE}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  restore_rdbms_connections
}

trap cleanup INT TERM EXIT

ensure_hop_image "${HOP_COMPOSE_FILE}"
backup_rdbms_connections

for db in ${DATABASES}; do
  if [ "${db}" = "snowflake" ] && [ -z "${LOCALSTACK_AUTH_TOKEN:-}" ]; then
    echo "=== Skipping snowflake: LOCALSTACK_AUTH_TOKEN is not set ==="
    echo "LocalStack for Snowflake is proprietary and opt-in. Export LOCALSTACK_AUTH_TOKEN to run."
    SKIPPED_ENGINES="${SKIPPED_ENGINES} ${db}"
    continue
  fi

  COMPOSE_FILE="${DOCKER_DIR}/compose.${db}.yml"
  if [ ! -f "${COMPOSE_FILE}" ]; then
    echo "Missing compose file for allowed profile '${db}': ${COMPOSE_FILE}" >&2
    FAIL=1
    continue
  fi

  echo "=== Running test suite against ${db} ==="
  ACTIVE_COMPOSE="${COMPOSE_FILE}"

  echo "Clearing previous metrics JSON in metrics/${db}/"
  clear_metrics_json_files_for_db "${db}"
  export METRICS_FOLDER="/workspace/integration-tests/metrics/${db}"

  set +e
  docker compose -f "${COMPOSE_FILE}" up --abort-on-container-exit --exit-code-from hop hop
  EXIT_CODE=$?
  set -e

  docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans
  reclaim_rdbms_connection_ownership
  reclaim_metrics_folder_ownership "${db}" "${COMPOSE_FILE}"
  reclaim_vault_catalog_ownership "${COMPOSE_FILE}"
  hop_workflow="tests/run-tests.hwf"
  if [ "${db}" = "sqlserver" ]; then
    hop_workflow="tests/run-tests-sqlserver.hwf"
  fi
  if [ "${EXIT_CODE}" -eq 0 ]; then
    write_hop_run_metrics "${INTEGRATION_TESTS_HOST}/metrics/${db}" true 0 "${hop_workflow}"
  else
    write_hop_run_metrics "${INTEGRATION_TESTS_HOST}/metrics/${db}" false "${EXIT_CODE}" "${hop_workflow}"
  fi

  # Banner is easy to miss in docker noise if we only print a single short line.
  echo ""
  echo "================================================================"
  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo " RESULT: ${db}  FAILED  (docker/hop exit code ${EXIT_CODE})"
    echo " A non-zero hop exit means a suite inside tests/run-tests.hwf"
    echo " (or run-tests-sqlserver.hwf) failed. Search the log above for"
    echo " 'ERROR', 'Finished with errors', or the last workflow name."
    echo "================================================================"
    FAIL=1
    FAILED_ENGINES="${FAILED_ENGINES} ${db}(exit=${EXIT_CODE})"
  else
    echo " RESULT: ${db}  PASSED  (exit code 0)"
    echo "================================================================"
    PASSED_ENGINES="${PASSED_ENGINES} ${db}"
  fi
  echo ""
done

ACTIVE_COMPOSE=""
restore_rdbms_connections
trap - INT TERM EXIT

echo ""
echo "=== Collecting cross-engine metrics overview (post-run) ==="
if ! collect_metrics_overview; then
  METRICS_FAIL=1
  FAIL=1
fi
print_metrics_overview_table

echo ""
echo "=== Multi-database summary ==="
if [ -n "${PASSED_ENGINES}" ]; then
  echo "Passed engines:${PASSED_ENGINES}"
else
  echo "Passed engines: (none)"
fi
if [ -n "${SKIPPED_ENGINES}" ]; then
  echo "Skipped engines:${SKIPPED_ENGINES}"
fi
if [ -n "${FAILED_ENGINES}" ]; then
  echo "Failed engines:${FAILED_ENGINES}" >&2
else
  echo "Failed engines: (none)"
fi
if [ "${METRICS_FAIL}" -ne 0 ]; then
  echo "Metrics overview: FAILED (see messages above; this is separate from per-engine hop runs)" >&2
else
  echo "Metrics overview: ok (or skipped)"
fi

if [ "${FAIL}" -ne 0 ]; then
  echo "" >&2
  if [ -n "${FAILED_ENGINES}" ] && [ "${METRICS_FAIL}" -ne 0 ]; then
    echo "One or more database engines failed, and metrics overview collection failed." >&2
  elif [ -n "${FAILED_ENGINES}" ]; then
    echo "One or more database engines failed (hop non-zero exit). See RESULT banners above." >&2
  elif [ "${METRICS_FAIL}" -ne 0 ]; then
    echo "All engines reported PASSED, but metrics overview collection failed." >&2
  else
    echo "One or more database test runs failed." >&2
  fi
  exit 1
fi

echo ""
if [ -z "${PASSED_ENGINES}" ] && [ -n "${SKIPPED_ENGINES}" ]; then
  echo "No database engines ran (all requested engines were skipped)."
else
  echo "All database test runs passed."
fi