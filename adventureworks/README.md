<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# AdventureWorks sample (issue #180)

Microsoft **AdventureWorks** OLTP restored into SQL Server Docker, modeled with Data Hopper EDW, loaded into **PostgreSQL**. This is a schema-complexity demo and cross-engine timing run — not a volume stress test (see `integration-tests/files/large/` for that).

Design notes: [docs/plans/adventureworks-sample-plan.md](../docs/plans/adventureworks-sample-plan.md).

## What you get

| Piece | Role |
|-------|------|
| SQL Server 2025 (`localhost:14333`) | Source: restored `AdventureWorks2025.bak` |
| Postgres `test_aw_edw` (port **54320**) | Data Vault / Business Vault / dimensional target (**not** retail's `test_edw`) |
| Postgres `test_aw_ops` | Load-run metrics / Hop executions |
| Hop project `adventureworks/` | Connections, models, resource definition group, workflows |

~8 GB RAM recommended (SQL Server 4G + Postgres + Hop).

## Prerequisites

- Docker with Compose v2
- Python 3 (stdlib only)
- Plugin zip built (`mvn package`) so `docker-hop:latest` can include it

## Run

From the repository root:

```sh
# Start Postgres (Vault/OPS) + SQL Server, download and restore the bak
./scripts/run-adventureworks.sh up

# Verify Hop can reach both engines
./scripts/run-adventureworks.sh load
```

Generate (or refresh) the committed source model and raw Data Vault from the live SQL Server:

```sh
./scripts/run-adventureworks.sh generate-models
```

That writes `models/adventureworks.hsm` (63 tables, 86 FK edges including a drawn `SalesOrderDetail.ProductID` → `Product` edge) and `models/adventureworks.hdv` (raw vault from Generate Data Vault), and publishes catalog feeds under `work/edw-catalog/hop/adventureworks/sources/`.

Load the vault into Postgres (timings report, `.hem`, project documentation):

```sh
./scripts/run-adventureworks.sh load
```

That runs `workflows/run-adventureworks-initial.hwf` (Data Vault, Business Vault, then dimensional models on group `adventureworks`). Target FK constraints are off (`generateForeignKeys=false`) because some generated constraint names collide after Postgres’s 63-character identifier limit.

`generate-models` also draws `SalesOrderDetail.ProductID` → `Product` so the raw vault has `lnk_salesorderdetail_product` (AdventureWorks only FKs that column as part of `SpecialOfferProduct`).

A representative full load on this machine: workflow wall clock **3m 16s**, including Data Vault (~2.2M rows), Business Vault (`bv_customer_scd2` 19 820, `bv_product_scd2` 504, `pit_employee` 20 779 plus 360 views), conformed dimensions (`d_customer` 19 820, `d_product` 504, `d_date` 1 826), and facts **`f_sales_order_line` 121 317** / **`f_purchase_order_line` 8 845**. Reports land in `work/reports/adventureworks-initial-report.{md,html}`. The execution map is `work/execution-maps/run-adventureworks-initial.hem` (453 nodes). Open `work/documentation/index.html` for project docs, including the Kimball bus matrix at `work/documentation/bus-matrices/adventureworks.html`. Connection check only: `./scripts/run-adventureworks.sh load workflows/run-adventureworks-check-db.hwf`.

Tour: [docs/getting-started-adventureworks.adoc](../docs/getting-started-adventureworks.adoc).

No-change incremental (does **not** drop the vault). Hubs insert **0**; insert-only satellites may add new versions for the new load date; TYPE1 dimensions insert **0**; facts are truncated then reloaded (fact pipelines insert without truncate). A representative update on this machine: workflow **2m 52s**, report `work/reports/adventureworks-update-report.html`.

```sh
./scripts/run-adventureworks.sh update
```

Compare SQL Server source counts to Postgres (`hub_customer` is source + 2 special records):

```sh
./scripts/run-adventureworks.sh spot-check
```

Re-restore the bak (replaces the database):

```sh
./scripts/run-adventureworks.sh restore
```

Stop SQL Server only (Postgres is shared with retail-example):

```sh
./scripts/run-adventureworks.sh down
```

## Hop GUI

1. Register this folder as a Hop project named **`adventureworks`**.
2. Environment: **`local-docker`** (`environments/local-docker.json`).
3. Connections: **AdventureWorks** (SQL Server), **Vault**, **OPS**.
4. After restore, **Import schema** on a new `.hsm` from connection AdventureWorks (schemas `Person`, `HumanResources`, `Production`, `Purchasing`, `Sales`).

## Restore notes

`AdventureWorks2025.bak` (~48 MB) restores on Linux SQL Server 2025 with only the data and log files (`AdventureWorks` / `AdventureWorks_log`). No FILESTREAM filegroup in this backup.

The restored database has **71** user tables (`Person` 13, `HumanResources` 6, `Production` 25, `Purchasing` 5, `Sales` 19, `dbo` 3). Skip `dbo` when importing the source model. Largest fact-like table: `Sales.SalesOrderDetail` (121 317 rows).

SQL Server-specific types present: `uniqueidentifier`, `money`/`smallmoney`, `xml`, `hierarchyid` (`Employee.OrganizationNode`, `Document.DocumentNode`), `geography` (`Address.SpatialLocation`), `varbinary` (photos/documents). Profile `sqlserver-types` maps these; omit photo/document binaries from satellites when generating the vault.

The bak is **not** committed. It is cached under gitignored `adventureworks/.cache/`. If a future bak adds FILESTREAM, SQL Server on Linux will fail the restore; fallback is Microsoft's [OLTP install script](https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/AdventureWorks-oltp-install-script.zip) with documents skipped.

## Layout

```
adventureworks/
├── environments/local-docker.json
├── metadata/          # connections, EDW configs, resource definition group
├── models/            # committed .hsm / .hdv / .hbv / .hdm
├── workflows/
├── sql/
├── scripts/bootstrap-adventureworks-work.py
└── work/              # gitignored catalog, reports, HEMs, documentation
```
