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

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "${SCRIPT_DIR}/hop-docker-lib.sh"

strip_carriage_returns() {
  printf '%s' "$1" | tr -d '\r'
}

DEFAULT_HOP_VERSION="$(
  strip_carriage_returns "$(
    grep '^ARG HOP_IMAGE_VERSION=' "${DOCKER_DIR}/Dockerfile" | cut -d= -f2
  )"
)"
NO_CACHE=""
HOP_VERSION="$(strip_carriage_returns "${HOP_IMAGE_VERSION:-}")"

usage() {
  echo "Usage: $0 [--no-cache] [--hop-version VERSION]" >&2
  echo "Rebuild ${HOP_IMAGE_NAME} (apache/hop + Data Hopper EDW plugin)." >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  --no-cache              Build without Docker layer cache" >&2
  echo "  --hop-version VERSION   Base apache/hop image tag (default: ${DEFAULT_HOP_VERSION})" >&2
  echo "" >&2
  echo "HOP_IMAGE_VERSION can also be set in the environment." >&2
  echo "Used by scripts/run-hop.sh and integration test runners." >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-cache)
      NO_CACHE="--no-cache"
      shift
      ;;
    --hop-version)
      if [ "$#" -lt 2 ]; then
        echo "Missing value for --hop-version" >&2
        usage
        exit 1
      fi
      HOP_VERSION="$(strip_carriage_returns "$2")"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [ -z "${HOP_VERSION}" ]; then
  HOP_VERSION="${DEFAULT_HOP_VERSION}"
fi

echo "=== Rebuilding ${HOP_IMAGE_NAME} ==="
echo "Base image: apache/hop:${HOP_VERSION}"
if [ -n "${NO_CACHE}" ]; then
  echo "Docker cache: disabled"
fi

docker compose -f "${HOP_COMPOSE_FILE}" build ${NO_CACHE} \
  --build-arg "HOP_IMAGE_VERSION=${HOP_VERSION}" hop

echo "Done. Image ready: ${HOP_IMAGE_NAME}"