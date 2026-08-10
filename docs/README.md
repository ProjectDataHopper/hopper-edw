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

# Hop Data Vault documentation index

Documentation for the **hop-datavault** plugin (development **0.9.0-SNAPSHOT**, latest release **0.8.0**). Requires **Apache Hop 2.19.0** or a recent **2.19.0-SNAPSHOT** ([hop-client snapshots](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)). Highlights include **Free SQL / hop-hsm JDBC**, **source modeler** (`.hsm` with JSON and pipeline sources), **metadata harvesting**, optional **load cycle IDs**, **composite hub business keys**, **OpenLineage / Marquez export**, **source-to-target lineage**, catalog version tags, schema validation, multi-DB hardening, and Business Vault incremental loading. See [CHANGELOG.md](../CHANGELOG.md).

**New here?** Read [feature-overview.md](feature-overview.md), then follow [getting-started-retail.adoc](getting-started-retail.adoc).

## Start here

| Document | Description |
|----------|-------------|
| [feature-overview.md](feature-overview.md) | Major plugin capabilities, maturity, and links to deep dives |
| [hop-web-modelers.md](hop-web-modelers.md) | **Hop Web** modelers (`.hsm`/`.hdv`/`.hbv`/`.hdm`/`.hem`): SVG canvas, interactions, Hop SPI |
| [getting-started-retail.adoc](getting-started-retail.adoc) | Primary tutorial: retail-example (DV → BV → dimensional) |
| [getting-started-integration-tests.adoc](getting-started-integration-tests.adoc) | Reference walkthrough: Customer 360 and integration test fixtures |
| [search.adoc](search.adoc) | **Search Everywhere**: models (`.hsm`/`.hdv`/`.hbv`/`.hdm`) and plugin metadata |
| [ai-file-schemas/README.md](ai-file-schemas/README.md) | **AI context pack**: XSD/JSON Schema + purpose markdown for models and metadata (Gemini/external AIs) |

## Managers and architects

| Document | Description |
|----------|-------------|
| [enterprise-modeling-and-team-collaboration.adoc](enterprise-modeling-and-team-collaboration.adoc) | **Large programs:** multi-file models, personas, git/catalog, load order, ops and analyst discovery |
| [architecture-export.adoc](architecture-export.adoc) | Export SOLUTION architecture, DATA inventory, and aggregated DV/BV/DM ELK Draw.io diagrams |
| [presentations/hop-data-vault-overview.md](presentations/hop-data-vault-overview.md) | High-level slide deck: goals, architecture, hybrid warehouses |

## Data Catalog and sources

| Document | Description |
|----------|-------------|
| [data-catalog.adoc](data-catalog.adoc) | Local catalog setup, namespaces, refresh, **catalog version tags** |
| [source-modeler-overview.adoc](source-modeler-overview.adoc) | **Source modeler (`.hsm`)**: PK/FK import, multi-table queries, Free SQL, Source model SQL transform, **Hop Server JDBC / DBeaver** (`jdbc:hop-hsm:`) |
| [source-to-target-lineage.adoc](source-to-target-lineage.adoc) | Field/table lineage, Lineage tab, explainable DDL, catalog publish, drift gate, reverse browser |
| [datavault-source.adoc](datavault-source.adoc) | `DV_SOURCE` record definitions (database, file, **composite**) |
| [datavault-source-database.adoc](datavault-source-database.adoc) | Database-backed source fields |
| [resource-definition-validation.adoc](resource-definition-validation.adoc) | Schema gate action, GUI validate, impact, proposals, HTML/MD reports, DTAP |
| [metadata-harvesting.adoc](metadata-harvesting.adoc) | **Metadata harvest** as a distinct EDW phase: OPS history, `HARVEST_RUN` gate, GUI, apply FKs / generate `.hsm` |
| [data-quality.adoc](data-quality.adoc) | Content quality measure, gate, history, alert sinks (Phase 2) |
| [record-definition-input.adoc](record-definition-input.adoc) | Pipeline transform: **Get Record Definition Names** (catalog definition list/metadata) |
| [record-definition-data-input.adoc](record-definition-data-input.adoc) | Pipeline transform: read **data rows** from a catalog record definition |
| [record-definition-output.adoc](record-definition-output.adoc) | Pipeline transform: write/discover/migrate catalog definitions |
| [database-table-metadata.adoc](database-table-metadata.adoc) | Pipeline transform: Hop-typed table columns + PK/FK (empty tables OK) |

## Data Vault reference (AsciiDoc)

| Document | Description |
|----------|-------------|
| [datavault-plugin.adoc](datavault-plugin.adoc) | Plugin overview, visual editor, workflows |
| [datavault-configuration.adoc](datavault-configuration.adoc) | Embedded `.hdv` configuration |
| [dv-hub.adoc](dv-hub.adoc) | Hub metadata |
| [dv-link.adoc](dv-link.adoc) | Link metadata |
| [dv-satellite.adoc](dv-satellite.adoc) | Satellite metadata |
| [dv-integration-modes.adoc](dv-integration-modes.adoc) | Hop managed / external / custom pipelines |
| [datavault-update-action.adoc](datavault-update-action.adoc) | Data Vault Update workflow action |

## Business Vault reference (AsciiDoc)

| Document | Description |
|----------|-------------|
| [business-vault-overview.adoc](business-vault-overview.adoc) | `.hbv` modeler and table types |
| [business-vault-scd2.adoc](business-vault-scd2.adoc) | SCD2 generation, multi-satellite mappings |
| [business-vault-pit.adoc](business-vault-pit.adoc) | PIT snapshot schedule, layout, pipelines |
| [business-vault-sql-view.adoc](business-vault-sql-view.adoc) | SQL business tables: view/table materialization, `ref` / `source` |
| [business-vault-configuration.adoc](business-vault-configuration.adoc) | Embedded `.hbv` configuration |
| [business-vault-update-action.adoc](business-vault-update-action.adoc) | Business Vault Update workflow action |

## Dimensional modeling

| Document | Description |
|----------|-------------|
| [dimensional-modeler-overview.adoc](dimensional-modeler-overview.adoc) | `.hdm` modeler, Kimball table types |
| [dimensional-update-action.adoc](dimensional-update-action.adoc) | Dimensional Update and Publish actions |
| [date-dimension-generator.adoc](date-dimension-generator.adoc) | Generate `dim_date` rows |

## Operations and tooling

| Document | Description |
|----------|-------------|
| [operations.adoc](operations.adoc) | Docker runners, batch orchestration, partial loads, load overview |
| [update-resource-definition-group-action.adoc](update-resource-definition-group-action.adoc) | Preferred multi-model update action (group-scoped) |
| [search.adoc](search.adoc) | Project search for models and plugin metadata (Hop 2.19) |
| [execution-maps.adoc](execution-maps.adoc) | `.hem` execution and lineage graphs |
| [performance-tuning.md](performance-tuning.md) | Sort memory and pipeline tuning |
| [ai-advisory.md](ai-advisory.md) | AI Help setup and usage |

## Sample Hop projects

| Folder | Document | Role |
|--------|----------|------|
| [../retail-example/](../retail-example/) | [../retail-example/README.md](../retail-example/README.md) | **Learn** — full-stack retail demo |
| [../integration-tests/](../integration-tests/) | [../integration-tests/PROJECT.md](../integration-tests/PROJECT.md) | **Reference / CI** — regression suites |
| [../scripts/](../scripts/) | [../scripts/README.md](../scripts/README.md) | Shared Docker runners |

## Command-line tools

### `hop svg`

Export pipelines (`.hpl`), workflows (`.hwf`), Data Vault models (`.hdv`), Business Vault models (`.hbv`), dimensional models (`.hdm`), and execution maps (`.hem`) to SVG.

**Docker (no local Hop install):** from `integration-tests/`:

```bash
cd integration-tests
./run-svg.sh -f tests/multi-satellite-bv/customer-360.hdv \
             -o ../docs/images/customer-360.svg --no-notes
```

**Local Hop** with the plugin installed:

```bash
hop svg -f integration-tests/tests/multi-satellite-bv/customer-360.hdv \
        -o docs/images/customer-360-generated.svg --no-notes
hop svg -s integration-tests/tests/multi-satellite-bv -t /tmp/svg-out -r
hop svg -f integration-tests/tests/basic/load1.hpl -o /tmp/load1.svg
```

Options: `--no-notes`, `--magnification`, `--show-hash-keys` (`.hdv` only), `--project-home`.

## Internal design notes

See [plans/](plans/) — not part of the end-user documentation path.

Screenshots: [images/](images/)