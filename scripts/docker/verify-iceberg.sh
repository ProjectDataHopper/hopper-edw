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
COMPOSE_FILE="${SCRIPT_DIR}/compose.iceberg.yml"

ICEBERG_NAMESPACE="${ICEBERG_NAMESPACE:-crm}"
ICEBERG_TABLE="${ICEBERG_TABLE:-customer}"

echo "=== Verifying Iceberg REST catalog ==="

docker compose -f "${COMPOSE_FILE}" up -d minio mc iceberg-rest

echo "Waiting for Iceberg REST catalog on localhost:8181..."
attempt=0
while [ "${attempt}" -lt 60 ]; do
  if curl -sf http://localhost:8181/v1/config >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 2
done

if [ "${attempt}" -ge 60 ]; then
  echo "Iceberg REST catalog did not become reachable on localhost:8181" >&2
  exit 1
fi

echo "REST catalog config endpoint is reachable"

docker compose -f "${COMPOSE_FILE}" run --rm --no-deps seed

echo "Checking namespace and table via REST API..."
namespaces_json="$(curl -sf "http://localhost:8181/v1/namespaces")"
printf '%s\n' "${namespaces_json}" | grep -q "\"${ICEBERG_NAMESPACE}\"" \
  || { echo "Namespace ${ICEBERG_NAMESPACE} not found in REST catalog" >&2; exit 1; }

tables_json="$(curl -sf "http://localhost:8181/v1/namespaces/${ICEBERG_NAMESPACE}/tables")"
printf '%s\n' "${tables_json}" | grep -q "\"${ICEBERG_TABLE}\"" \
  || { echo "Table ${ICEBERG_TABLE} not found in namespace ${ICEBERG_NAMESPACE}" >&2; exit 1; }

echo "Iceberg stack verification succeeded (${ICEBERG_NAMESPACE}.${ICEBERG_TABLE} is registered)"