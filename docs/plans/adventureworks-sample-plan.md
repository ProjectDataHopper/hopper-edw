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

# AdventureWorks sample / demo (issue #180)

GitHub: https://github.com/ProjectDataHopper/hopper-edw/issues/180
Bak URL (comment): https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/AdventureWorks2025.bak

## Goal

Add a new Hop project at repo-root `adventureworks/` that restores Microsoft’s AdventureWorks OLTP sample into a SQL Server Docker container, models it through Data Hopper EDW (source → raw Data Vault 2.0 → Business Vault → dimensional), loads a **PostgreSQL** target, and produces:

- A workflow load-overview report with timings
- An execution map (`.hem`)
- Project documentation that includes a Kimball bus matrix

This is a **schema-complexity demo and cross-engine benchmark**, not a volume stress test. AdventureWorks 2025 is a ~50 MB OLTP backup (~70 tables, five business schemas). Row counts are modest (`Sales.SalesOrderDetail` is on the order of 100k rows). Volume tuning already lives in `integration-tests/files/large/`. Treat timings as “how long does a realistic multi-schema SQL Server → Postgres EDW load take on this machine,” not as a TPC-style result.

Retail-example stays the small tutorial. AdventureWorks is the “known industry schema” showcase: import from SQL Server, generate a raw vault, then a designed BV/DM that you can open in Hop GUI.

## Issue checklist → product mapping

| Issue item | How we do it |
|---|---|
| Download recent `.bak` | Script; do **not** commit the bak. Pin `AdventureWorks2025.bak`. |
| Import bak into SQL Server Docker | Dedicated compose + restore init. Image: `mcr.microsoft.com/mssql/server:2025-latest` (2025 bak / compatibility 170). |
| Build a source model | Import PK/FK per schema into one committed `.hsm`. |
| Build Data Vault 2.0 | **Generate Data Vault** from the `.hsm`, then review/fix (especially Customer = Person **or** Store). Commit `.hdv`. |
| Build BV | Hand-authored `.hbv` (no auto-generator). Customer 360, Product 360, employee PIT. |
| Build DM | Hand-authored `.hdm` files (conformed dims + 2 facts). Do **not** ship Dimensional Publish’s hub→dim dump as the mart. |
| Resource definition group | Metadata `adventureworks` listing DV/BV/DM in load order. |
| New `adventureworks/` project | Sibling of `retail-example/`. |
| Workflow to update target Postgres | **Update resource definition group** with native bulk load, metrics on. |
| Update report with timings | Same load-overview MD/HTML path as retail (`REPORT_BASE_NAME`). |
| Generate `.hem` | **Generate execution map** action on the root workflow. |
| Project documentation with bus matrix | **Generate project documentation** action; bus matrix comes from the group’s `.hdm` files + business process catalog. |

## Non-goals (v1)

- Not part of `integration-tests/run-tests-all-databases.sh` or Jenkins’ default matrix (download, SQL Server 2025 image, and wall-clock are too heavy).
- No incremental “month wave” like retail (AdventureWorks is a static snapshot). One initial load is the demo; a second run is a no-change incremental for timings only.
- No Marquez / `.hlv` in v1 (can follow retail later).
- No data-quality rule library required for the first load.
- Do not replace or rewrite retail-example.

## Recommended modeling scope

**Source + raw vault: full OLTP** (minus FILESTREAM-only / unusable types).

Import schemas: `Person`, `HumanResources`, `Production`, `Purchasing`, `Sales`. Skip `dbo` system objects. If FILESTREAM restore drops document/photo tables, omit `Production.Document`, `Production.ProductPhoto`, `Production.ProductDocument`.

**Business Vault + dimensional: designed Kimball slice**, not “every hub becomes a dimension.”

That is the demo people recognize (classic AdventureWorksDW processes) without a 70-dimension bus matrix.

| Layer | v1 objects |
|---|---|
| BV | `adventureworks.hbv`: `bv_customer_360` (SCD2 over Person + Customer + Store + email), `bv_product_360` (Product + subcategory + category), `bv_employee` (PIT / current from Employee hierarchy) |
| DM conformed | `adventureworks-conformed-dims.hdm`: `d_date` (date generator), `d_customer`, `d_product`, `d_employee`, `d_sales_territory`, `d_vendor` |
| DM facts | `adventureworks-f-sales.hdm` (`f_sales_order_line`, grain = sales order + line number), `adventureworks-f-purchasing.hdm` (`f_purchase_order_line`) |
| Bus catalog | Domain Adventure Works Cycles; L1 Sales / Purchasing; L3 Sales order lines / Purchase order lines |

Deferred: work orders, inventory snapshot, internet vs reseller split, credit card / currency junk dims.

## Architecture

```
SQL Server 2025 Docker          Local Postgres 16 (port 54320)
AdventureWorks2025              test_aw_edw  (Vault DV/BV/DM)
  Person / HR / Production      test_aw_ops  (OPS metrics)  — do not reuse test_edw
  Purchasing / Sales
        │                              ▲
        │ JDBC MSSQLNATIVE             │ native bulk loader
        └──────── Hop (host net) ──────┘
```

Hop already runs with `network_mode: host` (`scripts/docker/compose.hop.yml`), so a published SQL Server port on localhost is reachable as `localhost`.

**Do not load into `test_edw`.** That is retail’s vault. New databases keep the samples independent.

### Connections

| Hop connection | Engine | Role |
|---|---|---|
| `AdventureWorks` | `MSSQLNATIVE` | Source (encrypt=false, trustServerCertificate=true, same extras as integration-tests SQL Server profile) |
| `Vault` | `POSTGRESQL` | DV/BV/DM target (`test_aw_edw`) |
| `OPS` | `POSTGRESQL` | Load-run metrics (`test_aw_ops`) |
| `local-catalog` | FILE | `${PROJECT_HOME}/work/edw-catalog` |

Environment file `adventureworks/environments/local-docker.json`:

- `DB_SOURCE_HOST=localhost`, `DB_SOURCE_PORT=14333` (non-default to avoid a host SQL Server on 1433)
- `DB_SOURCE_USER=sa`, `DB_SOURCE_PASSWORD=Test_Password123` (complexity rules)
- `DB_SOURCE_NAME=AdventureWorks2025`
- `DB_HOST=localhost`, `DB_PORT=54320`, `DB_TARGET_NAME=test_aw_edw`, `DB_OPS_NAME=test_aw_ops`

## Project layout

```
adventureworks/
├── README.md
├── project-config.json
├── environments/local-docker.json
├── metadata/          # connections, configs, RDG, bus catalog, metrics profile
├── models/            # TRACKED .hsm / .hdv / .hbv / .hdm
├── workflows/
│   └── run-adventureworks-initial.hwf
├── sql/               # drop-target, OPS metrics bootstrap
└── work/              # GITIGNORED: catalog, reports, hem, documentation, metrics
```

Scripts live with the other Docker runners:

- `scripts/docker/compose.adventureworks.yml` — SQL Server 2025 + restore sidecar
- `scripts/run-adventureworks.sh` — `up | down | restore | load | status`

`.gitignore`: `adventureworks/work/`, `adventureworks/.cache/` (downloaded bak).

RAT: new `.sh` files need Apache headers. `.hsm`/`.hdv`/`.hbv`/`.hdm`/`.json`/`.hwf` are already excluded or headered like retail.

## Infrastructure: download and restore

`scripts/run-adventureworks.sh up`:

1. `./scripts/run-postgres.sh up` (creates retail DBs today; extend `ensure_local_postgres_*` to also create `test_aw_edw` and `test_aw_ops`).
2. Start `compose.adventureworks.yml`: `mcr.microsoft.com/mssql/server:2025-latest`, Developer, EULA, 4G memory cap, host port **14333→1433**, volume for data + a bind for `/var/opt/mssql/backup`.
3. Download bak to `adventureworks/.cache/AdventureWorks2025.bak` if missing (curl/wget, fail on HTTP error).
4. `RESTORE FILELISTONLY` then `RESTORE DATABASE AdventureWorks2025 ... WITH MOVE` of every logical file to `/var/opt/mssql/data/...`, `REPLACE`.

**FILESTREAM risk:** SQL Server on Linux does not support FILESTREAM. Classic AdventureWorks uses it for document/photo filegroups. Microsoft’s own Linux restore example only MOVEs `.mdf`/`.ldf`. Implementation order:

1. Try restore of the 2025 bak (it may no longer carry a FILESTREAM filegroup; bak is ~50 MB).
2. If restore fails on FILESTREAM, `RESTORE ... WITH MOVE` of ROW_FILE files only is not valid; fallback is the OLTP **install script** (`AdventureWorks-oltp-install-script.zip`) with FILESTREAM/documents skipped, still targeting database name `AdventureWorks2025`.
3. Document whichever path worked. Do not require Windows SQL Server.

Restore is **not** a Hop workflow (no bak-restore action). The shell script is the GUI-adjacent bootstrap, same role as `run-postgres.sh` for retail.

## Source model

Toolbar **Import schema** is GUI-only; the engine `DatabaseSchemaImportSupport.importTables` is headless but takes **one schema at a time**.

Author once against a live restore (Hop GUI):

1. New `models/adventureworks.hsm`, configuration `source-model`, catalog `local-catalog`, default database `AdventureWorks`.
2. Import each schema separately (`Person`, `HumanResources`, `Production`, `Purchasing`, `Sales`) with **Publish to catalog**. Use `sourceNamePrefix` = schema name if logical names collide.
3. Confirm FK edges across schemas (e.g. `Sales.Customer` → `Person.Person` / `Sales.Store`). Draw any missing relationships.
4. Data type mapping profile for SQL Server types Hop will not load cleanly into Postgres:

   | Native type | Handling |
   |---|---|
   | `uniqueidentifier` | String 36 |
   | `money` / `smallmoney` | Number / BigNumber |
   | `xml` | String (length cap) |
   | `hierarchyid` (`Employee.OrganizationNode`) | String, or exclude from satellite |
   | `geography` (`Address.SpatialLocation`) | String WKT, or exclude |
   | `varbinary` / FILESTREAM | Exclude from vault attributes |

5. **Check model**. Commit the `.hsm`.

One file is enough (~70 tables). If the canvas is unusable, split per schema later; v1 stays one model so Generate Data Vault sees cross-schema FKs.

## Data Vault 2.0

On the `.hsm`: **Generate Data Vault…** with defaults (`createFkLinks`, hub satellites, reference tables, hierarchy links). Apply into `models/adventureworks.hdv`.

Review before commit (this is the value of a known schema):

- Lookup/code tables → `ref_*` (UnitMeasure, ShipMethod, PhoneNumberType, AddressType, Department, Shift, …).
- `HumanResources.Employee` self-FK → hierarchy link + hub alias.
- **Customer grain:** `Sales.Customer` is either a person or a store. After generate, expect `hub_customer` plus links to `hub_person` / `hub_store`. Do not collapse Person and Customer into one hub.
- `SalesOrderHeader` / `SalesOrderDetail` → hub + link with dependent child key `LineNumber`.
- Same pattern for purchase orders.
- Bill of materials / work orders: keep in raw vault; not required in BV/DM v1.

Configuration: target `Vault`, hash STRING/MD5 like retail, **Native bulk loader**, `targetTableParallelCopies` 2–4, `sortRowsSize` 1_000_000. **Check model** must be clean. Commit.

Catalog namespace: `hop/adventureworks/sources`.

## Resource definition group

Metadata `adventureworks`:

- Catalog `local-catalog`
- Business process catalog `adventureworks-bus`
- DV: `adventureworks.hdv`
- BV: `adventureworks.hbv` (add when authored)
- DM: conformed dims first, then sales, then purchasing (list order = load order)

EDW Journey should open this group like retail.

## Workflow (the runnable demo)

`workflows/run-adventureworks-initial.hwf`, run via:

```bash
./scripts/run-adventureworks.sh load
# wraps: ./scripts/run-hop.sh adventureworks workflows/run-adventureworks-initial.hwf
```

Extend `run-hop.sh` so `adventureworks` gets `HOP_PROJECT_NAME=adventureworks` and the new environment file (today only `integration-tests` and `retail-example` are special-cased).

Action graph (mirror retail, skip CSV generation):

1. Start
2. Wait for `AdventureWorks` + `Vault` + `OPS`
3. Bootstrap `work/` (Python, copy empty FILE catalog)
4. SQL: drop vault schemas/tables (idempotent initial)
5. Create OPS metrics tables if missing
6. **Update resource definition group** `adventureworks`
   - Include DV, BV, DM
   - Update target structure on
   - Publish targets to catalog
   - Metrics + load-overview → `work/reports/adventureworks-initial-report.{md,html}`
   - `PIPELINE_COPIES` parameter (default 3)
7. **Generate execution map** → `work/execution-maps/run-adventureworks-initial.hem`
8. **Generate project documentation** → `work/documentation/` (includes Bus matrices page)

Success criteria: workflow green; report shows per-model durations; `index.html` has a bus matrix; `.hem` opens in Hop GUI.

No golden CSV compare in v1. Optional smoke: SQL counts on `hub_customer`, `lnk_sales_order_detail` vs source `Sales.Customer` / `Sales.SalesOrderDetail`.

## Docker / Hop wiring

- `compose.adventureworks.yml` is **source-only**. Vault stays on the existing local Postgres compose.
- `run-adventureworks.sh load` requires both stacks healthy, then `run-hop.sh`.
- Do not add SQL Server to `compose.hop.yml`.
- Memory: SQL Server 4G + Postgres + Hop JVM. Document ~8 GB RAM recommended.

## Documentation (user-facing)

- `adventureworks/README.md` — prerequisites, `up` / `load`, GUI open path, what is in/out of scope.
- `docs/getting-started-adventureworks.adoc` — tour (like retail), not a from-scratch build guide.
- Index links from `docs/README.md`, `docs/feature-overview.adoc`, root `README.md`.
- `docs/plans/adventureworks-sample-plan.md` — copy of this plan for in-repo design notes.
- Screenshots only if we capture them during implementation (canvas, report, bus matrix). No placeholder images.

## Risks and first spikes

Do these **before** committing models (spike, throwaway restore):

1. **Restore** 2025 bak into `2025-latest` Linux container. Decide bak vs install-script. **Done:** restore succeeds (data+log only, no FILESTREAM filegroup). 71 tables. Hop `CHECK_DB_CONNECTIONS` green for AdventureWorks + Vault + OPS.
2. **Table Input** of `Person.Address` and `HumanResources.Employee` into a dummy Postgres table — prove `geography` / `hierarchyid` / `xml` handling.
3. **Import schema** all five schemas; count tables and FK edges; confirm cross-schema FKs.
4. **Generate Data Vault** and inspect Customer / SalesOrder* proposals before polishing names.

If spike 2 fails, add a source-model data type mapping and/or exclude those columns from satellites rather than blocking the sample.

## Plugin code we might need

Prefer **no engine changes**. Likely small glue only:

- `run-hop.sh` / `hop-docker-lib.sh` / `postgres-local-init.sql` for the new DBs and project name.
- `.gitignore`.
- Optional: if import UX cannot do five schemas without pain, a tiny maintainer script that calls `DatabaseSchemaImportSupport` in a loop (not required if GUI import is acceptable once).

Do **not** add a bak-restore workflow action.

## Verification

1. `./scripts/run-adventureworks.sh up` restores `AdventureWorks2025` (or documented fallback).
2. Open `adventureworks.hsm` / `.hdv` / `.hbv` / `.hdm` in Hop GUI; Check model clean.
3. `./scripts/run-adventureworks.sh load` completes.
4. Load-overview HTML lists DV/BV/DM with durations and insert counts.
5. `work/documentation/index.html` includes a bus-matrix page for group `adventureworks`.
6. `work/execution-maps/run-adventureworks-initial.hem` opens.
7. Spot-check: vault row counts vs SQL Server source for customer and sales order lines.
8. `mvn test` unchanged; do not run the four-engine IT matrix for this sample.

## PR slices

Independently reviewable; each should leave the tree buildable.

1. **Infra + empty Hop project** — compose, restore script, postgres DBs, `project-config.json`, connections, README stub. Proof: restore + Hop can `SELECT 1` from both engines.
2. **Source model + raw vault + RDG + DV-only load** — committed `.hsm`/`.hdv`, group with DV only, workflow that loads vault + timings report + HEM. Proof: DV load green.
3. **BV + DM + bus matrix + project documentation** — `.hbv`/`.hdm`, bus catalog, full group update, documentation action, getting-started doc. Proof: bus matrix page + fact row counts.
4. **Incremental timings + CHANGELOG + spot-check** — no-drop update workflow, source vs vault counts, CHANGELOG. Proof: second run inserts 0 new hub rows; `spot-check` matches Customer / SalesOrderDetail / Product / PurchaseOrderDetail (sats by distinct hash key).

## Key decisions

- **Committed models**, generated once in GUI, not regenerated on every `load` (same as retail; GUI-first).
- **SQL Server 2025 container** for the 2025 bak the issue named.
- **Postgres target on new databases** so retail is untouched.
- **Full OLTP in HSM/HDV; designed BV/DM slice** so the bus matrix is readable.
- **Optional runner, not CI matrix.**
- **Load-overview reports are the benchmark artifact.**
