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

# Optional Marquez smoke for Hop Lineage View (issue #79, PR 8).
# Not part of mvn test. Not part of run-tests-all-databases.sh.
#
# Usage: ./scripts/smoke-lineage-view.sh [--export] [--base-url URL] [--node-id NODE]
#
# Prerequisites:
#   ./scripts/run-marquez.sh up
#   An export into that Marquez (or pass --export to run send-lineage-to-marquez.hwf)
#
# Checks GET /api/v1/lineage for a retail seed, then follow-up run facets for hop_export.
# Does not treat latestRun.durationMs as load telemetry.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${MARQUEZ_BASE_URL:-http://localhost:5001}"
DO_EXPORT=0
NODE_ID=""

usage() {
  echo "Usage: $0 [--export] [--base-url URL] [--node-id NODE]" >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --export)
      DO_EXPORT=1
      shift
      ;;
    --base-url)
      [ "$#" -ge 2 ] || usage
      BASE_URL="$2"
      shift 2
      ;;
    --node-id)
      [ "$#" -ge 2 ] || usage
      NODE_ID="$2"
      shift 2
      ;;
    -h | --help)
      usage
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

# Strip a pasted ${MARQUEZ_API} the same way MarquezUrls.normalizeBaseUrl does.
normalize_base_url() {
  url="$1"
  url="${url%/}"
  case "$url" in
    */api/v1-beta/lineage) url="${url%/api/v1-beta/lineage}" ;;
    */api/v1/lineage) url="${url%/api/v1/lineage}" ;;
    */api/v1-beta) url="${url%/api/v1-beta}" ;;
    */api/v1) url="${url%/api/v1}" ;;
  esac
  url="${url%/}"
  printf '%s' "$url"
}

BASE_URL="$(normalize_base_url "${BASE_URL}")"
LINEAGE_URL="${BASE_URL}/api/v1/lineage"
NAMESPACES_URL="${BASE_URL}/api/v1/namespaces"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required." >&2
  exit 1
fi

echo "Marquez base URL: ${BASE_URL}"
echo "Note: export ≠ load. Marquez latestRun.durationMs is the last export, not the last load."

if ! curl -sf "${NAMESPACES_URL}" >/dev/null 2>&1; then
  echo "Marquez API is not reachable at ${NAMESPACES_URL}." >&2
  echo "Start it with: ${SCRIPT_DIR}/run-marquez.sh up" >&2
  exit 1
fi

if [ "${DO_EXPORT}" -eq 1 ]; then
  echo "Exporting retail lineage to Marquez..."
  "${SCRIPT_DIR}/run-hop.sh" retail-example workflows/send-lineage-to-marquez.hwf
fi

encode_node_id() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

fetch_lineage() {
  node="$1"
  encoded="$(encode_node_id "${node}")"
  curl -sf "${LINEAGE_URL}?nodeId=${encoded}&depth=6" || true
}

graph_ok() {
  python3 -c '
import json, sys
raw = sys.stdin.read()
if not raw.strip():
    sys.exit(1)
try:
    body = json.loads(raw)
except json.JSONDecodeError:
    sys.exit(1)
graph = body.get("graph")
if not isinstance(graph, list) or not graph:
    sys.exit(1)
print(raw)
'
}

inspect_graph() {
  python3 -c '
import json, sys

seed = sys.argv[1]
body = json.load(sys.stdin)
graph = body.get("graph") or []
print("graph_nodes=%d" % len(graph))

by_id = {}
for item in graph:
    node_id = item.get("id")
    if node_id:
        by_id[node_id] = item

def run_of(item):
    if not item:
        return None, None
    latest = (item.get("data") or {}).get("latestRun") or {}
    rid = latest.get("id")
    return (str(rid) if rid else None), latest.get("durationMs")

run_id, duration_ms = run_of(by_id.get(seed))
if not run_id:
    seed_item = by_id.get(seed) or {}
    neighbor_ids = []
    for edge in seed_item.get("inEdges") or []:
        neighbor_ids.append(edge.get("origin"))
    for edge in seed_item.get("outEdges") or []:
        neighbor_ids.append(edge.get("destination"))
    for nid in neighbor_ids:
        item = by_id.get(nid) or {}
        if str(item.get("type") or "").upper() != "JOB":
            continue
        run_id, duration_ms = run_of(item)
        if run_id:
            break
if not run_id:
    for item in graph:
        if str(item.get("type") or "").upper() != "JOB":
            continue
        run_id, duration_ms = run_of(item)
        if run_id:
            break

hop_export = False
for item in graph:
    facets = (item.get("data") or {}).get("facets") or {}
    if "hop_export" in facets:
        hop_export = True
        break

print("latest_run_id=%s" % (run_id or ""))
print("graph_has_hop_export=%s" % ("yes" if hop_export else "no"))
if duration_ms is not None:
    print("latestRun.durationMs=%s (export wall time — ignored)" % duration_ms)
' "$1"
}

CANDIDATES=""
if [ -n "${NODE_ID}" ]; then
  CANDIDATES="${NODE_ID}"
else
  CANDIDATES="dataset:retail-dataset:f_orders
dataset:retail-dataset:f_order_lines
job:retail-job/retail-example:dm/retail-f-orders/f_orders
job:retail-job/retail-example:dm/retail-f-order-lines/f_order_lines"
fi

GRAPH_JSON=""
USED_NODE=""
for node in ${CANDIDATES}; do
  echo "GET ${LINEAGE_URL}?nodeId=${node}&depth=6"
  raw="$(fetch_lineage "${node}")"
  if checked="$(printf '%s' "${raw}" | graph_ok)"; then
    GRAPH_JSON="${checked}"
    USED_NODE="${node}"
    break
  fi
  echo "  no graph for ${node}"
done

if [ -z "${GRAPH_JSON}" ]; then
  echo "No lineage graph for the retail seeds." >&2
  echo "Export first:" >&2
  echo "  ${SCRIPT_DIR}/run-hop.sh retail-example workflows/send-lineage-to-marquez.hwf" >&2
  echo "or re-run with --export." >&2
  exit 1
fi

echo "Seed found: ${USED_NODE}"
INSPECT="$(printf '%s' "${GRAPH_JSON}" | inspect_graph "${USED_NODE}")"
echo "${INSPECT}"

RUN_ID="$(printf '%s\n' "${INSPECT}" | sed -n 's/^latest_run_id=//p')"
if [ -z "${RUN_ID}" ]; then
  echo "Graph has no latestRun.id; cannot fetch hop_export follow-up." >&2
  exit 1
fi

FACETS_URL="${BASE_URL}/api/v1/jobs/runs/${RUN_ID}/facets?type=run"
echo "GET ${FACETS_URL}"
FACETS_JSON="$(curl -sf "${FACETS_URL}" || true)"
if [ -z "${FACETS_JSON}" ]; then
  echo "Run-facet request failed." >&2
  exit 1
fi

python3 -c '
import json, sys
body = json.loads(sys.argv[1])
facets = body.get("facets") or {}
if "hop_export" not in facets:
    print("Run facets are missing hop_export. Re-export with a plugin that stamps hop identity facets.", file=sys.stderr)
    sys.exit(1)
export = facets.get("hop_export") or {}
print("hop_export.modelLayer=%s" % (export.get("modelLayer") or ""))
print("hop_export.modelName=%s" % (export.get("modelName") or ""))
print("hop_export.logicalName=%s" % (export.get("logicalName") or ""))
if "hop_ops" in facets:
    print("hop_ops present (export-time snapshot; OPS overlay prefers the Hop OPS database)")
print("OK")
' "${FACETS_JSON}"
