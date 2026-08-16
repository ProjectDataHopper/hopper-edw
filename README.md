<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing,
software distributed under this License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Hop Data Vault 2.0 Plugin

![EDW](docs/images/edw-logo.svg)

Apache Hop plugin for **Data Vault 2.0**, **Business Vault**, and **dimensional** modeling, validation, and model-driven loading. Version **0.10.0-SNAPSHOT** (latest release **0.9.0**) requires **Apache Hop 2.19.0** (or a **recent 2.19.0-SNAPSHOT** until GA) and **Java 21**.

**Hop 2.19 is required.** Until the GA client is on Maven Central, build Hop from source or download a recent CI snapshot of **hop-client**:

- https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/

Hop **2.18.x** (including 2.18.1) is **not** supported for this release.

**Model once. Generate loads and consumption layers.** Sources live in the Hop **Data Catalog**; visual **`.hsm`**, **`.hdv`**, **`.hbv`**, and **`.hdm`** models drive workflow actions, optional **execution maps** (`.hem`), and **Hop Lineage Views** (`.hlv`).

## Tutorials

* [Hop Data Vault Tutorial - 1 - Creating your first data vault model](https://youtu.be/YRUwPdDyNDE)
* [Hop Data Vault Tutorial - 2 - Updating your data vault](https://www.youtube.com/watch?v=k64kxMmyA4U)

## Features

Full capability list with maturity labels: **[docs/feature-overview.adoc](docs/feature-overview.adoc)**  
Release notes: **[CHANGELOG.md](CHANGELOG.md)**

Highlights:

- **Data Catalog** — `DV_SOURCE` record definitions under `hop/{project}/sources`; catalog validation with proposals and acknowledgements
- **Catalog versions + schema gate** — tag source contracts, **Validate resource definitions** CI action, impact blast radius, Markdown/HTML reports
- **Source-to-target lineage** — field/table lineage with reason codes, Lineage tab, explainable DDL, catalog publish, drift gate, reverse browser ([docs](docs/source-to-target-lineage.adoc))
- **Hop Lineage View** — authorable `.hlv` over Marquez, an export folder, or local models; **Show lineage** from a table; OPS load-time overlay (not Marquez export duration) ([docs](docs/hop-lineage-view.adoc))
- **OpenLineage / Marquez export** — **Export data lineage** workflow action (folder + HTTP), physical location facets, dimension-alias symlinks ([docs](docs/openlineage-export.adoc))
- **Raw Data Vault** — `.hdv` modeler, Check model, Data Vault Update action, hybrid integration modes
- **Business Vault** — `.hbv` SCD2 (single and multi-satellite), PIT tables, Business Vault Update action
- **Dimensional modeler** — `.hdm` Kimball loads, Dimensional Publish/Update actions
- **Execution maps** — crawl workflows and models into `.hem` execution graphs
- **AI Help** — optional LLM advisory on models, pipelines, and workflows

![Edit Data Vault Model](docs/images/data-vault-model-dialog.png)

![Data Vault Update action](docs/images/action-data-vault-update.png)

![Customer 360 Business Vault model](docs/images/business-vault-model-customer-360.png)

## Documentation

Full index: **[docs/README.md](docs/README.md)**

`mvn package` also builds an HTML copy of the user guide into `target/generated-docs/` and ships it in the plugin zip at `plugins/misc/datavault/docs/index.html`. Architecture diagrams are committed SVGs generated from PlantUML (`docs/diagrams/`).

| Audience | Document |
|----------|----------|
| Everyone (start here) | [`docs/architecture.adoc`](docs/architecture.adoc) then [`docs/getting-started-edw.adoc`](docs/getting-started-edw.adoc) |
| Feature list | [`docs/feature-overview.adoc`](docs/feature-overview.adoc) |
| Tour the sample | [`docs/getting-started-retail.adoc`](docs/getting-started-retail.adoc) |
| Advanced fixtures | [`docs/getting-started-integration-tests.adoc`](docs/getting-started-integration-tests.adoc) |
| Managers / architects | [`docs/presentations/hop-data-vault-overview.md`](docs/presentations/hop-data-vault-overview.md) |

**Data Vault reference** (AsciiDoc under `docs/`):

| Document | Topic |
|----------|--------|
| [`docs/datavault-plugin.adoc`](docs/datavault-plugin.adoc) | Plugin overview, visual editor, workflows |
| [`docs/datavault-configuration.adoc`](docs/datavault-configuration.adoc) | Shared Data Vault configuration metadata |
| [`docs/dv-integration-modes.adoc`](docs/dv-integration-modes.adoc) | Hop managed / external / custom pipelines |
| [`docs/dv-hub.adoc`](docs/dv-hub.adoc) / [`dv-link.adoc`](docs/dv-link.adoc) / [`dv-satellite.adoc`](docs/dv-satellite.adoc) | Table metadata |
| [`docs/datavault-update-action.adoc`](docs/datavault-update-action.adoc) | Data Vault Update action |

**Business Vault reference:**

| Document | Topic |
|----------|--------|
| [`docs/business-vault-overview.adoc`](docs/business-vault-overview.adoc) | `.hbv` modeler and table types |
| [`docs/business-vault-scd2.adoc`](docs/business-vault-scd2.adoc) | SCD2 and multi-satellite merge |
| [`docs/business-vault-configuration.adoc`](docs/business-vault-configuration.adoc) | Embedded `.hbv` configuration |
| [`docs/business-vault-update-action.adoc`](docs/business-vault-update-action.adoc) | Business Vault Update action |

Also: [`docs/source-to-target-lineage.adoc`](docs/source-to-target-lineage.adoc), [`docs/hop-lineage-view.adoc`](docs/hop-lineage-view.adoc), [`docs/openlineage-export.adoc`](docs/openlineage-export.adoc), [`docs/resource-definition-validation.adoc`](docs/resource-definition-validation.adoc), [`docs/ai-advisory.md`](docs/ai-advisory.md), [`docs/datavault-source.adoc`](docs/datavault-source.adoc), [`docs/datavault-source-database.adoc`](docs/datavault-source-database.adoc), [`docs/record-definition-input.adoc`](docs/record-definition-input.adoc), [`docs/date-dimension-generator.adoc`](docs/date-dimension-generator.adoc).

Screenshots are in [`docs/images/`](docs/images/).

## Repository layout

| Folder | Purpose |
|--------|---------|
| [`integration-tests/`](integration-tests/) | Plugin regression suites and golden-dataset tests — see [integration-tests/PROJECT.md](integration-tests/PROJECT.md) |
| [`retail-example/`](retail-example/) | Full-stack retail demo (CSV → DV → BV → DM) — see [retail-example/README.md](retail-example/README.md) |
| [`scripts/`](scripts/) | Shared Docker runners (`run-hop.sh`, `run-postgres.sh`) and retail data generators |

### Integration tests

Register `integration-tests/` as Hop project **`hop-data-vault`**. Configure **`CRM`** and **`Vault`** database connections, install the plugin, then:

```bash
./scripts/run-postgres.sh up
./scripts/run-hop.sh integration-tests tests/run-tests.hwf
```

Or from `integration-tests/`: `./run-tests.sh`

### Retail example demo

![Retail 360 Data Vault model](docs/images/data-vault-model-retail-example.png)

Register `retail-example/` as Hop project **`retail-example`**, then:

```bash
./scripts/run-postgres.sh up
./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf
./scripts/run-hop.sh retail-example workflows/run-retail-update.hwf   # repeat for monthly batches
```

## Building

```bash
mvn clean package
```

Artifacts:

- `target/hop-datavault-0.10.0-SNAPSHOT.jar`
- `target/hop-datavault-0.10.0-SNAPSHOT.zip` (ready-to-unzip plugin layout)

Published release artifacts for **0.9.0**:

- **GitHub:** [v0.9.0 release](https://github.com/mattcasters/hop-data-vault/releases/tag/v0.9.0) — [hop-datavault-0.9.0.zip](https://github.com/mattcasters/hop-data-vault/releases/download/v0.9.0/hop-datavault-0.9.0.zip)
- **Nexus (Marketplace):** [hop-datavault-0.9.0.zip](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.9.0/hop-datavault-0.9.0.zip) (`org.apache.hop:hop-datavault:0.9.0`)

## Installation (external plugin)

1. Install **Apache Hop 2.19.0** or a **recent 2.19.0-SNAPSHOT** client ([hop-client snapshots](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)).
2. Unzip the assembly zip into your Hop installation, or manually copy the jar to:
   ```
   $HOP_HOME/plugins/misc/datavault/hop-datavault-0.10.0-SNAPSHOT.jar
   ```
3. Restart Hop GUI.
4. New metadata types appear under **Metadata → Data Vault**. **Data Vault Update**, **Business Vault Update**, **Validate resource definitions**, **Export data lineage**, and **Update resource definition group** actions are available in workflows. `.hsm`, `.hdv`, `.hbv`, `.hdm`, and `.hlv` files open in Explorer tabs.

### Hop Marketplace

Continuous Jenkins builds publish the latest SNAPSHOT zip to the Data Hopper community Maven repository. On **Apache Hop 2.19.0+**, import the shareable repository definition ([`hop-marketplace-repo.yaml`](hop-marketplace-repo.yaml)), then query and install.

**1. Import the repository** (from GitHub `main`, or a local clone of this file):

```bash
./hop marketplace repo import \
  https://raw.githubusercontent.com/mattcasters/hop-data-vault/refs/heads/main/hop-marketplace-repo.yaml
```

```text
Updated repository 'data-hopper-community' → https://repository.data-hopper.com/repository/hop-community-plugins/ (browse enabled)
```

**2. Query** available plugins (filter for this one):

```bash
./hop marketplace query | grep vault
```

```text
| hop-datavault             | 0.9.0           | Community     | data-hopper-community |           | 2026-08-16 | Data Vault 2.0, Business Vault, and dimensional model... |
```

**3. Install** the plugin (latest release or continuous SNAPSHOT when published):

```bash
./hop marketplace install hop-datavault
```

```text
Resolved hop-datavault → org.apache.hop:hop-datavault:0.9.0 (prefer repo 'data-hopper-community')
… Marketplace - Downloading org.apache.hop:hop-datavault:0.9.0 from https://repository.data-hopper.com/repository/hop-community-plugins/…
… Marketplace - Installed org.apache.hop:hop-datavault:0.9.0. Restart Hop to load the plugin.
Plugin org.apache.hop:hop-datavault:0.9.0 installed under $HOP_HOME from repo 'data-hopper-community'. Restart Hop to load it.
```

You can also use **Tools → Marketplace…** in Hop GUI: import the repository on the **Repositories** tab, then install from the **Plugins** tab.

**Restart Hop** after install so the plugin registry reloads. This plugin requires **Hop 2.19.0** or a **recent 2.19.0-SNAPSHOT** build ([hop-client snapshots](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)).

## Usage

Recommended build order (details: [`docs/getting-started-edw.adoc`](docs/getting-started-edw.adoc), pictures: [`docs/architecture.adoc`](docs/architecture.adoc)):

1. **Tools → Configure EDW setup...** — FILE catalog (`local-catalog`) and shared source / DV / BV / DM configurations.
2. Create a **source model** (`.hsm`), **Import schema**, then toolbar **Push to catalog** (`hop/{project}/sources`).
3. **Generate Data Vault…** from the `.hsm`. **Check model** on the `.hdv`.
4. Create a **Resource definition group**, list the `.hdv` (BV/DM can wait), set the default catalog connection.
5. Workflow **Update resource definition group**: update target structure, **Publish target tables to catalog**.
6. Add `.hbv` then `.hdm` to the **same** group and update again (layer order is always DV → BV → DM).

Single-model **Data Vault Update** / **Business Vault Update** / **Dimensional Update** still exist; prefer the group action once you have a group.

For multi-active satellites, set **`drivingKey`** (vault column) and **`drivingKeySourceField`** (source column). For scheduled partial loads, tag sources with **`group`** and set **`recordSourceGroup`** on the update action.

For load end dating, enable **`useLoadEndDate`** in the model configuration and set **`loadEndDateField`** (e.g. `x_load_end_ts`). Current satellite rows are those where the end-date column is null:

```sql
SELECT * FROM sat_customer WHERE x_load_end_ts IS NULL
```

## Common Data Vault 2.0 options included

- Hashing: MD5 / SHA1 / SHA256 / SHA512
- HEX (default), String, or Binary hash keys (Binary sorting fixed in Hop 2.19.0 — [issue 7346](https://github.com/apache/hop/issues/7346))
- Execution information to a database (e.g. OPS `hop_executions`) via Hop run configuration / execution info location (Hop 2.19.0+)
- Trimming + casing normalization
- Delimiter + null placeholder
- Unknown and invalid sentinel record handling
- Column naming conventions (load timestamp, record source)
- Hashdiff vs **load end date** satellite patterns
- Multi-active satellites via driving keys
- Record source groups for partial model updates

## Roadmap / releases

**Shipped in 0.9.0:** shared model configuration and **Configure EDW setup** (#126); generate a Data Vault from a source model (#125); optional orphan handling (#77); Hop Lineage View (`.hlv`, #79) including Hop Web; first-time **architecture** / **getting-started EDW** docs and plugin HTML; CSV satellite hash and SQL Server live-schema fixes. See [CHANGELOG.md](CHANGELOG.md).

**Shipped in 0.8.0:** **Free SQL** over source models (Calcite, #117) + **Source model SQL** transform + Hop Server **`jdbc:hop-hsm:`** thin client for DBeaver; **pipeline sources** on `.hsm` (#116); **Source JSON** / shipment path (#114); **metadata harvesting** (#112); RDG load-overview reporting.

**Shipped in 0.7.0:** optional **load cycle ID** (#111); **composite hub business keys** (VaultSpeed-style multi-part → one BK column); optional satellite **record-source column** omit; AI file-schema pack; catalog/model-check and GUI polish (MODELS export, Open in catalog, resource-group editor performance).

**Shipped in 0.6.0:** **Reference tables** (#110); **linked tables** rename (`TABLE_REFERENCE` → `LINKED_TABLE`); source modeler (`.hsm`) and composite feeds (#105); project search (#106); Draw.io architecture export (#104); hub aliases (#103); cross-engine ORDER BY COLLATE fix (#108); Hop **2.19.0** requirement.

**Shipped in 0.5.0:** OpenLineage / Marquez export (**Export data lineage** action, physical `dataSource` / `hop_location` facets, dimension-alias symlinks); optional primary and foreign keys in model DDL; resource-definition validation with catalog-safe multi-layer length remediation and catalog version tags UI; portable model/execution-map paths; dark-mode note fills.

**Shipped in 0.4.0:** source-to-target lineage (table + field, reason codes), Lineage tab and reverse browser, explainable DDL, catalog lineage publish, lineage drift gate; transactional link dependent child keys; separate DV/BV target databases for incremental SCD2/PIT; SQL Server multi-byte VARCHAR expansion; pipeline wall-clock metrics.

**Shipped in 0.3.0 preview:** catalog version tags, schema impact simulation, **Validate resource definitions** CI/CD gate (compare modes, failure severity, Markdown/HTML reports, downstream impact), retail `work/` runtime tree, schema-gate docs and screenshots.

**Also shipped (0.2.x line):** dimensional modeler, execution maps, catalog-first sources, data quality rules and gates, multi-DB integration hardening, incremental Business Vault SCD2, primary-key import/detection, SQL Server / Unicode EDW hardening.

**Planned:** BV naming rules engine, additional source types, automated execution with dependency resolution.