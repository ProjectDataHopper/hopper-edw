#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

# Start a local Marquez stack for OpenLineage export testing (issue #101).
# Usage: ./scripts/run-marquez.sh [up|down|reset|status|logs]
#
# After up:
#   API  http://localhost:5001  (POST /api/v1/lineage)
#   UI   http://localhost:3001
#   MARQUEZ_URL=http://localhost:5001

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker/compose.marquez.yml"
ACTION="${1:-up}"
MARQUEZ_API_URL="${MARQUEZ_API_URL:-http://localhost:5001}"
MARQUEZ_UI_URL="${MARQUEZ_UI_URL:-http://localhost:3001}"

wait_for_marquez_api() {
  attempts="${1:-60}"
  i=0
  while [ "$i" -lt "$attempts" ]; do
    if command -v curl >/dev/null 2>&1; then
      if curl -sf "${MARQUEZ_API_URL}/api/v1/namespaces" >/dev/null 2>&1; then
        return 0
      fi
    elif command -v wget >/dev/null 2>&1; then
      if wget -q -O /dev/null "${MARQUEZ_API_URL}/api/v1/namespaces" 2>/dev/null; then
        return 0
      fi
    else
      # No HTTP client — wait a fixed period after containers are up.
      sleep 15
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  return 1
}

case "${ACTION}" in
  up)
    docker compose -f "${COMPOSE_FILE}" up -d
    echo "Waiting for Marquez API on ${MARQUEZ_API_URL}..."
    if wait_for_marquez_api 90; then
      echo "Marquez is ready."
      echo "  API:  ${MARQUEZ_API_URL}/api/v1/lineage"
      echo "  UI:   ${MARQUEZ_UI_URL}"
      echo "  Export action HTTP URL: ${MARQUEZ_API_URL}/api/v1/lineage"
      echo "  Env:  MARQUEZ_URL=${MARQUEZ_API_URL}"
    else
      echo "Marquez API did not become ready in time." >&2
      docker compose -f "${COMPOSE_FILE}" logs marquez-api >&2 || true
      exit 1
    fi
    ;;
  down)
    docker compose -f "${COMPOSE_FILE}" down --remove-orphans
    ;;
  reset)
    docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans
    ;;
  status)
    docker compose -f "${COMPOSE_FILE}" ps
    if command -v curl >/dev/null 2>&1 && curl -sf "${MARQUEZ_API_URL}/api/v1/namespaces" >/dev/null 2>&1; then
      echo "Marquez API is accepting requests on ${MARQUEZ_API_URL}."
    else
      echo "Marquez API is not reachable on ${MARQUEZ_API_URL}."
      exit 1
    fi
    ;;
  logs)
    docker compose -f "${COMPOSE_FILE}" logs -f
    ;;
  *)
    echo "Usage: $0 [up|down|reset|status|logs]" >&2
    exit 1
    ;;
esac
