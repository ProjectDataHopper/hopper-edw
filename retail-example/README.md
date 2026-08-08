<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Retail Example Hop Project

End-to-end retail data vault example demonstrating initial and incremental loads across Data Vault, Business Vault, and dimensional models.

![Retail 360 Data Vault model](../docs/images/data-vault-model-retail-example.png)

## Documentation

- [docs/getting-started-retail.adoc](../docs/getting-started-retail.adoc) — primary tutorial
- [docs/source-modeler-overview.adoc](../docs/source-modeler-overview.adoc) — source modeler (`.hsm`) and composite feeds
- [docs/feature-overview.md](../docs/feature-overview.md) — all plugin capabilities
- [docs/README.md](../docs/README.md) — full documentation index

DV source catalog entries use namespace **`hop/retail-example/sources`**.

### Source model sample

`models/source-tables-crm.hsm` maps CRM landing tables (PK/FK relationships) and includes:

- Query **All customer info** (hub + address + contact + demo + prefs). Publish as composite catalog feed `feed_customer_enriched` when experimenting with multi-table satellite loads.
- Table **order_shipment_event** — Kafka-style consumer landing (UUID `message_id`, JSON `payload`, `kafka_timestamp`, `topic`, `partition`, `offset`). Populated by `scripts/generate-retail-data.py` during initial and update waves with **order shipping tracking** events (label created → picked up → in transit → delivered, …).
- Source JSON **order_shipment_tracking** — flattens `payload` into columns (`order_id`, `status`, `carrier`, location, …) plus pass-through Kafka metadata. Published as catalog feed **`feed_order_shipment_tracking`** (type `JSON`).
- Data Vault **`retail-360.hdv`** loads that feed into **`hub_order_shipment`**, **`lnk_order_shipment`**, and **`sat_order_shipment`** (JSON source pipeline: record-source constant, hub sort/distinct for CDC).

## Prerequisites

- Docker with Compose v2 (used by `../scripts/run-hop.sh`)
- Local PostgreSQL on port **54320** — start with `../scripts/run-postgres.sh`
- Python 3 (stdlib only) for data generation scripts

After upgrading to the two-database layout, reset the local Postgres volume once:

```sh
./scripts/run-postgres.sh reset
./scripts/run-postgres.sh up
```

## Database layout

Retail uses **two PostgreSQL databases** on the same local instance (port 54320):

| Database | Hop connection | Role |
|----------|----------------|------|
| `test_source` | **CRM** | E2E landing tables (source system) |
| `test_edw` | **Vault** | Data Vault, Business Vault, dimensional marts, load control, DM staging views |

Environment variables in `environments/local-docker-postgres.json`:

- `DB_SOURCE_NAME` → CRM (`test_source`)
- `DB_TARGET_NAME` → Vault (`test_edw`)

Most DV record sources are **database-backed** catalog entries (`physicalTable` on CRM). The shipment feed is a **JSON** catalog source over `order_shipment_event.payload`. CSV files under `files/` are still generated for debugging; `load-e2e-sources-to-crm.hpl` loads them into CRM before each DV update.

## Project layout

```
retail-example/
├── project-config.json
├── environments/local-docker-postgres.json
├── metadata/                  # CRM, Vault, local-catalog, rule sets, run configurations
│   └── data-quality-rule-set/ # retail-source-quality + retail-target-quality libraries
├── fixtures/
│   └── schema-gate-baseline/  # Seed for catalog-versions tag v1.0.0 (copied into work/)
├── pipelines/                 # create-source-tables, load-e2e-sources-to-crm
├── files/                     # Generated CSV source files (mostly gitignored)
├── models/                    # TRACKED .hsm / .hdv / .hbv / .hdm
├── sql/                       # drop-source / drop-target, load control, staging views
├── scripts/                   # generate data, bootstrap work/, catalog sources
├── work/                      # GITIGNORED runtime tree (created on first run)
│   ├── edw-catalog/           # FILE data catalog (sources, published models, versions)
│   ├── reports/               # Schema gate + load overview MD/HTML
│   ├── execution-maps/        # Generated .hem files
│   └── metrics/               # Per-run metrics JSON
└── workflows/
    ├── run-retail-initial.hwf
    ├── run-retail-update.hwf
    └── simulate-n-months.hwf
```

`local-catalog` points at `${PROJECT_HOME}/work/edw-catalog`. Initial setup runs
`scripts/bootstrap-retail-work.py` (E2E sources + schema-gate baseline copy).

Shared Python helpers live in `../scripts/end-to-end/` (repo root, not inside this project).

## Run instructions

From the repository root:

```sh
# Start PostgreSQL (once)
./scripts/run-postgres.sh up

# Initial load: drop CRM + EDW, create sources, generate data, load CRM, DV + BV + DM
./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf

# Incremental load: read control, generate update wave, load CRM, DV + BV + DM, advance control
./scripts/run-hop.sh retail-example workflows/run-retail-update.hwf
```

## Schema validation gate

`workflows/run-retail-update-models.hwf` starts with **Validate resource definitions** on group `retail-sources`:

- Compare mode **`WORKING_VS_VERSION`** against baseline tag **`v1.0.0`** (catalog field length/type drift)
- Includes downstream impact (hubs / sats / BV / DM)
- **Check target databases** always; **Target tables are created automatically** is parameterized:
  - **Initial** (`run-retail-initial` → `EXPECT_AUTOMATIC_TARGET_TABLE_CREATION=Y`): missing-table CREATE is not reported (expected; vault update creates tables)
  - **Update** (`run-retail-update` → `N`, default): missing/altered targets stay WARNINGs and fail the gate
- Writes `work/reports/retail-schema-validation.md` and `.html`

After the vault wave, **Update resource definition group** also writes load-overview reports when metrics are enabled:

- Initial: `work/reports/retail-dv-initial-report.md` / `.html` (`REPORT_BASE_NAME`)
- Update: `work/reports/retail-dv-update-report.md` / `.html`

Baseline **`v1.0.0`** is seeded from `fixtures/schema-gate-baseline/` into
`work/edw-catalog/catalog-versions/` by `bootstrap-retail-work.py`. Refresh the
live baseline with **Tag catalog version** on the resource definition group when
source contracts change intentionally (or re-bootstrap with `--force-baseline`
after fixture updates). Use **`LIVE_SOURCE`** only when you want
to compare the expected contract to physical CRM columns (not catalog-only
metadata edits).

When reading validation results, use the **Axis / Compared / Found / Why it matters**
lines on each issue: a baseline version gap is not the same as “target tables
missing before first load” (`TARGET_DDL_REQUIRED`). See
[docs/resource-definition-validation.adoc](../docs/resource-definition-validation.adoc).

Screenshots and full parameter reference:
[docs/resource-definition-validation.adoc](../docs/resource-definition-validation.adoc)
(catalog versions: [docs/data-catalog.adoc](../docs/data-catalog.adoc)).

## Data quality (measure + gate)

Retail binds source content rules from Hop metadata **`retail-source-quality`** onto the `E2E-*` catalog sources (via `generate-catalog-sources.py`), and target rules from **`retail-target-quality`** onto published vault tables (`hub_customer`, `sat_customer_demo`, `hub_order`).

Both **initial** and **update** workflows:

1. **Measure source data quality** after CRM is loaded (and after schema validation on initial), with `persistHistory` to OPS/`dv_ops`
2. **Evaluate source quality gate** with `FAIL_ON_BLOCKING` — blocks vault update on bad extracts
3. After vault update completes: **Measure target data quality (post)** (`POST_UPDATE`, persist to OPS) on the three published tables + **Alert on target quality (post)** with `ALERT_ONLY` (`alertSinks=log,ops_table`, `remeasureIfNoPriorReport=N`)

Operations catalog stubs for quality history tables are published under
`work/edw-catalog/hop/retail-example/operations/` at runtime.

See [docs/data-quality.adoc](../docs/data-quality.adoc) for rule types and action details.

## Data generation

CSV files are written to `files/` by `scripts/generate-retail-data.py` (workflows) — kept in sync with `../scripts/end-to-end/generate-retail-data.py`:

| Mode | Description |
|------|-------------|
| `initial` | Full snapshot wave (`*_initial.csv`) |
| `update` | Incremental wave for the current `retail_load_control.progress_date` |

Default scale: 10,000 customers, 1,000 products, 100,000 orders.

**Order lines** are generated at grain `(order_id, product_id, line_number)`. The same product can appear on multiple lines of one order; the first order in each wave is forced to do so (with different unit prices) so **transactional link** behaviour (`lnk_order_line` + dependent child key `line_number` in `models/retail-360.hdv`) is easy to verify.

CSV wave selection uses Hop variable **`RETAIL_CSV_WAVE`** (for example `initial` or
`2024-01`). `generate-retail-data.py` writes `work/retail-csv-wave.properties`; the
workflows load it with **Set variables** before `load-e2e-sources-to-crm.hpl`, which
reads files as `${PROJECT_HOME}/files/<table>_${RETAIL_CSV_WAVE}.csv`. The pipeline
XML is stable and is not rewritten on each run.