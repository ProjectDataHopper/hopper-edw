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
COMPOSE_FILE="${SCRIPT_DIR}/../scripts/docker/compose.iceberg.yml"
ACTION="${1:-verify}"

case "${ACTION}" in
  up)
    docker compose -f "${COMPOSE_FILE}" up -d minio mc iceberg-rest db
    "${SCRIPT_DIR}/docker/verify-iceberg.sh"
    ;;
  verify)
    "${SCRIPT_DIR}/docker/verify-iceberg.sh"
    ;;
  test)
    export HOST_UID="${HOST_UID:-$(id -u)}"
    export HOST_GID="${HOST_GID:-$(id -g)}"
    mkdir -p "${SCRIPT_DIR}/metrics/iceberg"
    docker compose -f "${COMPOSE_FILE}" up --build --abort-on-container-exit --exit-code-from hop hop
    ;;
  down)
    docker compose -f "${COMPOSE_FILE}" down --remove-orphans
    ;;
  logs)
    docker compose -f "${COMPOSE_FILE}" logs -f minio mc iceberg-rest seed
    ;;
  *)
    echo "Usage: $0 [up|verify|test|down|logs]" >&2
    exit 1
    ;;
esac