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
# AdventureWorks sample: SQL Server source (bak restore) + Hop load into Postgres.
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "${SCRIPT_DIR}/hop-docker-lib.sh"

COMPOSE_FILE="${HOP_ADVENTUREWORKS_COMPOSE_FILE}"
COMPOSE_PROJECT="${AW_COMPOSE_PROJECT:-hop-adventureworks}"
AW_PROJECT_HOST="${REPO_ROOT}/adventureworks"
HOST_UID="${HOST_UID:-$(id -u)}"
HOST_GID="${HOST_GID:-$(id -g)}"
AW_CACHE_DIR="${AW_PROJECT_HOST}/.cache"
AW_BAK_NAME="AdventureWorks2025.bak"
AW_BAK_URL="${AW_BAK_URL:-https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/${AW_BAK_NAME}}"
AW_DB_NAME="${AW_DB_NAME:-AdventureWorks2025}"
AW_CONTAINER="${AW_CONTAINER:-hop-adventureworks-sqlserver}"
AW_SA_PASSWORD="${DB_PASSWORD:-Test_Password123}"
AW_SQL_HOST_PORT="${AW_SQL_HOST_PORT:-14333}"
AW_BAK_CONTAINER_PATH="/var/opt/mssql/backup/${AW_BAK_NAME}"
ACTION="${1:-up}"

aw_sqlcmd() {
  if docker exec "${AW_CONTAINER}" test -x /opt/mssql-tools18/bin/sqlcmd; then
    printf '%s\n' /opt/mssql-tools18/bin/sqlcmd
  elif docker exec "${AW_CONTAINER}" test -x /opt/mssql-tools/bin/sqlcmd; then
    printf '%s\n' /opt/mssql-tools/bin/sqlcmd
  else
    echo "sqlcmd not found in ${AW_CONTAINER}" >&2
    return 1
  fi
}

aw_sql() {
  sqlcmd_bin="$(aw_sqlcmd)"
  docker exec "${AW_CONTAINER}" "${sqlcmd_bin}" \
    -S localhost -U sa -P "${AW_SA_PASSWORD}" -C -b "$@"
}

wait_for_sqlserver() {
  max_attempts="${1:-60}"
  attempt=1
  while [ "${attempt}" -le "${max_attempts}" ]; do
    if docker exec "${AW_CONTAINER}" true >/dev/null 2>&1 \
      && aw_sql -Q "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  return 1
}

download_bak() {
  mkdir -p "${AW_CACHE_DIR}"
  bak_host="${AW_CACHE_DIR}/${AW_BAK_NAME}"
  if [ -f "${bak_host}" ] && [ -s "${bak_host}" ]; then
    echo "Using cached backup ${bak_host}"
    return 0
  fi
  echo "Downloading ${AW_BAK_URL}"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 -o "${bak_host}.partial" "${AW_BAK_URL}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${bak_host}.partial" "${AW_BAK_URL}"
  else
    echo "Need curl or wget to download ${AW_BAK_NAME}" >&2
    return 1
  fi
  mv "${bak_host}.partial" "${bak_host}"
  echo "Saved ${bak_host}"
}

copy_bak_into_container() {
  docker exec -u root "${AW_CONTAINER}" mkdir -p /var/opt/mssql/backup
  docker cp "${AW_CACHE_DIR}/${AW_BAK_NAME}" "${AW_CONTAINER}:${AW_BAK_CONTAINER_PATH}"
  docker exec -u root "${AW_CONTAINER}" chown mssql:mssql "${AW_BAK_CONTAINER_PATH}"
}

database_exists() {
  exists="$(
    aw_sql -h -1 -W -Q "SET NOCOUNT ON; SELECT DB_ID(N'${AW_DB_NAME}');" \
      | tr -d '[:space:]'
  )"
  case "${exists}" in
    NULL|"") return 1 ;;
    *) return 0 ;;
  esac
}

restore_bak() {
  download_bak
  copy_bak_into_container

  if database_exists && [ "${FORCE_RESTORE:-}" != "1" ]; then
    echo "Database ${AW_DB_NAME} already exists (set FORCE_RESTORE=1 to replace)."
    return 0
  fi

  echo "Restoring ${AW_BAK_NAME} as ${AW_DB_NAME}..."
  filelist="$(
    aw_sql -h -1 -W -s "|" -Q "SET NOCOUNT ON; RESTORE FILELISTONLY FROM DISK = N'${AW_BAK_CONTAINER_PATH}';"
  )"

  restore_sql="$(
    AW_DB_NAME="${AW_DB_NAME}" AW_BAK_CONTAINER_PATH="${AW_BAK_CONTAINER_PATH}" python3 - "${filelist}" <<'PY'
import os
import sys

raw = sys.argv[1]
db = os.environ["AW_DB_NAME"]
bak = os.environ["AW_BAK_CONTAINER_PATH"]
moves = []
seen = set()
for line in raw.splitlines():
    line = line.strip()
    if not line or line.startswith("(") or "rows affected" in line.lower():
        continue
    parts = [p.strip() for p in line.split("|")]
    if len(parts) < 3:
        continue
    logical, physical, ftype = parts[0], parts[1], parts[2]
    if not logical or logical.lower() == "logicalname":
        continue
    if logical in seen:
        continue
    seen.add(logical)
    base = os.path.basename(physical.replace("\\", "/")) or f"{logical}.mdf"
    if ftype.upper().startswith("L"):
        if not base.lower().endswith(".ldf"):
            base = f"{logical}.ldf"
    elif ftype.upper().startswith("S"):
        base = f"{logical}_fs"
    dest = f"/var/opt/mssql/data/{base}"
    moves.append(f"MOVE N'{logical}' TO N'{dest}'")
if not moves:
    sys.stderr.write("RESTORE FILELISTONLY returned no files\n")
    sys.exit(1)
sql = [
    "USE [master];",
    f"RESTORE DATABASE [{db}]",
    f"FROM DISK = N'{bak}'",
    "WITH REPLACE,",
    "    " + ",\n    ".join(moves) + ";",
]
print("\n".join(sql))
PY
  )"

  echo "${restore_sql}"
  tmp_sql="$(mktemp)"
  printf '%s\n' "${restore_sql}" > "${tmp_sql}"
  docker cp "${tmp_sql}" "${AW_CONTAINER}:/tmp/restore-aw.sql"
  docker exec -u root "${AW_CONTAINER}" chmod 644 /tmp/restore-aw.sql
  rm -f "${tmp_sql}"
  set +e
  aw_sql -i /tmp/restore-aw.sql
  restore_rc=$?
  set -e
  if [ "${restore_rc}" -ne 0 ]; then
    echo "RESTORE failed. SQL Server on Linux does not support FILESTREAM." >&2
    echo "If the backup has a FILESTREAM filegroup, use the OLTP install script fallback (see adventureworks/README.md)." >&2
    return "${restore_rc}"
  fi
  echo "Restored ${AW_DB_NAME}."
}

compose() {
  docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" "$@"
}

aw_status() {
  compose ps
  if docker exec "${AW_CONTAINER}" true >/dev/null 2>&1 && database_exists; then
    echo "SQL Server is up; database ${AW_DB_NAME} is present on localhost:${AW_SQL_HOST_PORT}."
  else
    echo "SQL Server container and/or ${AW_DB_NAME} is not ready."
  fi
}

ensure_load_prereqs() {
  "${SCRIPT_DIR}/run-postgres.sh" up
  if ! docker exec "${AW_CONTAINER}" true >/dev/null 2>&1 || ! wait_for_sqlserver 8; then
    echo "SQL Server is not ready; starting restore stack..."
    compose up -d
    if ! wait_for_sqlserver 60; then
      echo "SQL Server did not become ready. Run: $0 up" >&2
      compose logs db >&2 || true
      exit 1
    fi
    restore_bak
  fi
}

mssql_count() {
  aw_sql -d "${AW_DB_NAME}" -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM ${1};" \
    | tr -d '[:space:]'
}

pg_count() {
  PGPASSWORD="${DB_PASSWORD:-test}" psql -h "${DB_HOST:-localhost}" -p "${DB_PORT:-54320}" \
    -U "${DB_USER:-test}" -d "${DB_TARGET_NAME:-test_aw_edw}" -tAc "SELECT COUNT(*) FROM ${1};"
}

pg_distinct() {
  PGPASSWORD="${DB_PASSWORD:-test}" psql -h "${DB_HOST:-localhost}" -p "${DB_PORT:-54320}" \
    -U "${DB_USER:-test}" -d "${DB_TARGET_NAME:-test_aw_edw}" -tAc \
    "SELECT COUNT(DISTINCT ${2}) FROM ${1};"
}

spot_check_row() {
  label="$1"
  source_n="$2"
  target_n="$3"
  extra="${4:-0}"
  expected=$((source_n + extra))
  if [ "${target_n}" -eq "${expected}" ]; then
    printf '  OK  %-28s source=%s  target=%s\n' "${label}" "${source_n}" "${target_n}"
    return 0
  fi
  printf '  FAIL %-28s source=%s  target=%s (expected %s)\n' "${label}" "${source_n}" "${target_n}" "${expected}" >&2
  return 1
}

spot_check_counts() {
  echo "Comparing AdventureWorks2025 (SQL Server) to Vault test_aw_edw (Postgres)..."
  src_customer="$(mssql_count "Sales.Customer")"
  src_sod="$(mssql_count "Sales.SalesOrderDetail")"
  src_product="$(mssql_count "Production.Product")"
  src_pod="$(mssql_count "Purchasing.PurchaseOrderDetail")"
  hub_customer="$(pg_count hub_customer)"
  sat_sod="$(pg_distinct sat_salesorderdetail salesorderdetail_hk)"
  hub_product="$(pg_count hub_product)"
  sat_pod="$(pg_distinct sat_purchaseorderdetail purchaseorderdetail_hk)"
  fact_sales="$(pg_count f_sales_order_line)"
  fact_po="$(pg_count f_purchase_order_line)"
  failed=0
  # Hubs include unknown + invalid special records.
  spot_check_row "Customer / hub_customer" "${src_customer}" "${hub_customer}" 2 || failed=1
  spot_check_row "SalesOrderDetail / sat" "${src_sod}" "${sat_sod}" 0 || failed=1
  spot_check_row "SalesOrderDetail / fact" "${src_sod}" "${fact_sales}" 0 || failed=1
  spot_check_row "Product / hub_product" "${src_product}" "${hub_product}" 2 || failed=1
  spot_check_row "PurchaseOrderDetail / sat" "${src_pod}" "${sat_pod}" 0 || failed=1
  spot_check_row "PurchaseOrderDetail / fact" "${src_pod}" "${fact_po}" 0 || failed=1
  if [ "${failed}" -ne 0 ]; then
    echo "Spot-check failed." >&2
    return 1
  fi
  echo "Spot-check passed."
}

case "${ACTION}" in
  up)
    "${SCRIPT_DIR}/run-postgres.sh" up
    compose up -d
    echo "Waiting for SQL Server on localhost:${AW_SQL_HOST_PORT}..."
    if ! wait_for_sqlserver 60; then
      echo "SQL Server did not become ready in time." >&2
      compose logs db >&2 || true
      exit 1
    fi
    restore_bak
    aw_status
    ;;
  restore)
    compose up -d
    if ! wait_for_sqlserver 60; then
      echo "SQL Server did not become ready in time." >&2
      exit 1
    fi
    FORCE_RESTORE="${FORCE_RESTORE:-1}" restore_bak
    ;;
  down)
    compose down --remove-orphans
    ;;
  reset)
    compose down -v --remove-orphans
    ;;
  generate-models)
    if ! docker exec "${AW_CONTAINER}" true >/dev/null 2>&1 || ! wait_for_sqlserver 8; then
      echo "SQL Server is not ready; starting restore stack..."
      compose up -d
      if ! wait_for_sqlserver 60; then
        echo "SQL Server did not become ready. Run: $0 up" >&2
        compose logs db >&2 || true
        exit 1
      fi
      restore_bak
    fi
    python3 "${AW_PROJECT_HOST}/scripts/bootstrap-adventureworks-work.py" --project-home "${AW_PROJECT_HOST}"
    echo "Compiling hopper-edw (skip tests)..."
    (cd "${REPO_ROOT}" && mvn -q -DskipTests compile)
    ensure_hop_image "${HOP_COMPOSE_FILE}"
    echo "Generating adventureworks.hsm / adventureworks.hdv..."
    docker compose -f "${HOP_COMPOSE_FILE}" run --rm --no-deps \
      -e HOST_UID -e HOST_GID --entrypoint bash hop -c \
      'cd /opt/hop && mkdir -p /tmp/hop-config /tmp/hopper-edw-audit && ARCH=$(uname -m) && java -Xmx2g -DHOP_PLUGIN_BASE_FOLDERS=/opt/hop/plugins -DHOP_AUDIT_FOLDER=/tmp/hopper-edw-audit -DHOP_CONFIG_FOLDER=/tmp/hop-config -classpath "/workspace/target/classes:lib/core/*:lib/swt/linux/${ARCH}/*:/opt/hop/plugins/misc/hopper-edw/lib/*" org.hopper.edw.datavault.samples.AdventureWorksModelGenerator /workspace/adventureworks && chown "${HOST_UID:-0}:${HOST_GID:-0}" /workspace/adventureworks/models/adventureworks.hsm /workspace/adventureworks/models/adventureworks.hdv'
    ;;
  load)
    ensure_load_prereqs
    WORKFLOW="${2:-workflows/run-adventureworks-initial.hwf}"
    "${SCRIPT_DIR}/run-hop.sh" adventureworks "${WORKFLOW}"
    ;;
  update)
    ensure_load_prereqs
    "${SCRIPT_DIR}/run-hop.sh" adventureworks workflows/run-adventureworks-update.hwf
    ;;
  spot-check)
    ensure_load_prereqs
    spot_check_counts
    ;;
  status)
    aw_status
    ;;
  logs)
    compose logs -f db
    ;;
  *)
    echo "Usage: $0 [up|down|reset|restore|generate-models|load [workflow]|update|spot-check|status|logs]" >&2
    echo "  up              Start Postgres + SQL Server and restore AdventureWorks2025.bak" >&2
    echo "  restore         Re-restore the bak (FORCE_RESTORE=1 by default)" >&2
    echo "  generate-models Import SQL Server schemas to .hsm and generate .hdv" >&2
    echo "  load            Initial Hop load (drop vault, DV/BV/DM, report, HEM, docs)" >&2
    echo "  update          No-change incremental load (no drop; timings report)" >&2
    echo "  spot-check      Compare SQL Server source counts to Postgres vault/facts" >&2
    exit 1
    ;;
esac
