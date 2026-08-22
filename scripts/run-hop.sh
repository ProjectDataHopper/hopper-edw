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
. "${SCRIPT_DIR}/hop-docker-lib.sh"

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <hop-project-dir> <workflow> [extra workflow args ignored]" >&2
  echo "Examples:" >&2
  echo "  $0 integration-tests tests/run-tests.hwf" >&2
  echo "  $0 retail-example workflows/run-retail-initial.hwf" >&2
  exit 1
fi

export HOP_PROJECT_DIR="$1"
WORKFLOW_ARG="$2"

case "${HOP_PROJECT_DIR}" in
  integration-tests)
    export HOP_PROJECT_NAME="${HOP_PROJECT_NAME:-hop-data-vault}"
    ;;
  retail-example)
    export HOP_PROJECT_NAME="${HOP_PROJECT_NAME:-retail-example}"
    ;;
  *)
    export HOP_PROJECT_NAME="${HOP_PROJECT_NAME:-${HOP_PROJECT_DIR}}"
    ;;
esac

export HOP_PROJECT_FOLDER="${WORKSPACE_PREFIX}/${HOP_PROJECT_DIR}"
export LOCAL_POSTGRES_ENV_FILE="${HOP_PROJECT_FOLDER}/environments/local-docker-postgres.json"

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

HOP_FILE_PATH="$(workflow_arg_to_container_path "${WORKFLOW_ARG}")"
COLLECT_METRICS="${COLLECT_METRICS:-Y}"

if [ "${HOP_PROJECT_DIR}" = "integration-tests" ]; then
  export METRICS_FOLDER="${METRICS_FOLDER:-${WORKSPACE_PREFIX}/integration-tests/metrics/local}"
  mkdir -p "${INTEGRATION_TESTS_DIR}/metrics/local"
else
  COLLECT_METRICS=N
  export METRICS_FOLDER="${METRICS_FOLDER:-${HOP_PROJECT_FOLDER}/metrics}"
  mkdir -p "$(hop_project_host_dir)/metrics" 2>/dev/null || true
fi

echo "=== Running ${HOP_PROJECT_DIR}/${WORKFLOW_ARG} in Hop docker container ==="
echo "Project folder: ${HOP_PROJECT_FOLDER}"
echo "Environment: ${HOP_ENVIRONMENT_NAME:-local-docker-postgres} (${LOCAL_POSTGRES_ENV_FILE})"
if [ "${HOP_PROJECT_DIR}" = "retail-example" ]; then
  echo "Databases: CRM=test_source, Vault=test_edw, OPS=test_ops (${LOCAL_POSTGRES_USER}@${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT})"
else
  echo "Database: ${LOCAL_POSTGRES_USER}@${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}/${LOCAL_POSTGRES_DB}"
fi
require_local_postgres
if [ "${HOP_PROJECT_DIR}" = "retail-example" ]; then
  ensure_local_postgres_retail_databases
fi

EXIT_CODE=0
run_hop_docker_short_lived "${HOP_COMPOSE_FILE}" "${HOP_FILE_PATH}" "${METRICS_FOLDER}" \
  || EXIT_CODE=$?

if [ "${HOP_PROJECT_DIR}" = "integration-tests" ]; then
  reclaim_metrics_tree_ownership
  reclaim_vault_catalog_ownership
  if [ "${COLLECT_METRICS}" = "Y" ]; then
    collect_metrics_overview || EXIT_CODE=1
    print_metrics_overview_table
  fi
fi

exit "${EXIT_CODE}"