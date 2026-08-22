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

COMPOSE_FILE="${HOP_POSTGRES_LOCAL_COMPOSE_FILE}"
ACTION="${1:-up}"

case "${ACTION}" in
  up)
    docker compose -f "${COMPOSE_FILE}" up -d
    echo "Waiting for PostgreSQL on ${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}..."
    if wait_for_local_postgres 60; then
      ensure_local_postgres_retail_databases
      echo "PostgreSQL is ready (${LOCAL_POSTGRES_USER}@${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}/${LOCAL_POSTGRES_DB}; also test_source, test_edw, test_ops for retail-example)."
    else
      echo "PostgreSQL did not become ready in time." >&2
      docker compose -f "${COMPOSE_FILE}" logs db >&2 || true
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
    if local_postgres_ready; then
      echo "PostgreSQL is accepting connections on ${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}."
    else
      echo "PostgreSQL is not reachable on ${LOCAL_POSTGRES_HOST}:${LOCAL_POSTGRES_PORT}."
      exit 1
    fi
    ;;
  logs)
    docker compose -f "${COMPOSE_FILE}" logs -f db
    ;;
  *)
    echo "Usage: $0 [up|down|reset|status|logs]" >&2
    exit 1
    ;;
esac