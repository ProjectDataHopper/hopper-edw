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

# Hop Data Vault plugin — feature overview

Apache Hop plugin for **model-driven Data Vault 2.0**, **Business Vault**, and **dimensional** loading. Version **0.6.0-SNAPSHOT** (latest release **0.5.0**) requires **Apache Hop 2.19.0** (or current **2.19.0-SNAPSHOT** until GA) and **Java 21**.

**Model once. Generate loads and consumption layers.** The visual models (`.hsm`, `.hdv`, `.hbv`, `.hdm`) are the contract between architects, modelers, and operations.

For a slide-style executive summary, see [presentations/hop-data-vault-overview.md](presentations/hop-data-vault-overview.md). Interactive HTML presentation (architecture diagram, detail pages, roadmap): [presentations/hop-data-vault-features.html](presentations/hop-data-vault-features.html). For DV concepts and canvas usage, see [datavault-plugin.adoc](datavault-plugin.adoc).

**Large teams / hundreds of tables:** how to split models, assign personas (modeler, admin, ops, BV/DM, analysts), use git and the data catalog, and orchestrate loads — see [enterprise-modeling-and-team-collaboration.adoc](enterprise-modeling-and-team-collaboration.adoc).

---

## Feature maturity

| Feature | Status | Documentation |
|---------|--------|---------------|
| Data Catalog + `DV_SOURCE` record definitions | Available | [data-catalog.adoc](data-catalog.adoc), [datavault-source.adoc](datavault-source.adoc) |
| Source modeler (`.hsm`) + multi-table queries / composite feeds | Available | [source-modeler-overview.adoc](source-modeler-overview.adoc) |
| Project / Search Everywhere for models and plugin metadata | Available (Hop 2.19) | [search.adoc](search.adoc) |
| Resource definition validation (issues, proposals, acknowledgements) | Available | [resource-definition-validation.adoc](resource-definition-validation.adoc) |
| Catalog version tags + schema impact simulation (CI/CD gate, blast radius) | Available | [resource-definition-validation.adoc](resource-definition-validation.adoc), [data-catalog.adoc](data-catalog.adoc) |
| Data quality measure + quality gate (content rules, persist, alerts) | Available (Phase 2) | [data-quality.adoc](data-quality.adoc) |
| Data Vault modeler (`.hdv`) | Available | [datavault-plugin.adoc](datavault-plugin.adoc) |
| Hub aliases / same hub twice on a link | Available | [dv-cross-model-references.adoc](dv-cross-model-references.adoc), [dv-link.adoc](dv-link.adoc) |
| Large-program / multi-team modeling guide | Available (docs) | [enterprise-modeling-and-team-collaboration.adoc](enterprise-modeling-and-team-collaboration.adoc) |
| Architecture export (Draw.io) | Available (SOLUTION + DATA inventory + aggregated DV/BV/DM ELK) | [architecture-export.adoc](architecture-export.adoc) |
| Model validation (Check model, type checking) | Available | [datavault-update-action.adoc](datavault-update-action.adoc) |
| Data Vault Update action | Available | [datavault-update-action.adoc](datavault-update-action.adoc) |
| Update resource definition group action | Available | [update-resource-definition-group-action.adoc](update-resource-definition-group-action.adoc) |
| Integration modes (managed / external / custom) | Available | [dv-integration-modes.adoc](dv-integration-modes.adoc) |
| Business Vault modeler (`.hbv`) | Available | [business-vault-overview.adoc](business-vault-overview.adoc) |
| BV SCD2 (single + multi-satellite merge) | Available | [business-vault-scd2.adoc](business-vault-scd2.adoc) |
| BV PIT tables | Available | [business-vault-pit.adoc](business-vault-pit.adoc) |
| BV SQL views / tables (`ref` / `source`) | Available | [business-vault-sql-view.adoc](business-vault-sql-view.adoc) (`vault1.hbv` + `retail-sql.hbv` samples) |
| Catalog model registry (DV/BV/DM index) | Available | Published on catalog publish; short `ref('model', 'table')` lookup |
| Source-to-target lineage (table + field, reason codes) | Available | [source-to-target-lineage.adoc](source-to-target-lineage.adoc) |
| Explainable DDL + lineage drift gate | Available | [source-to-target-lineage.adoc](source-to-target-lineage.adoc), [resource-definition-validation.adoc](resource-definition-validation.adoc) |
| Reverse lineage browser (source field → consumers) | Available | [source-to-target-lineage.adoc](source-to-target-lineage.adoc) |
| BV→BV canvas references (multi-step BV) | Available | Alias cards + SQL `ref()` to tables in another `.hbv` |
| Business Vault Update action | Available | [business-vault-update-action.adoc](business-vault-update-action.adoc) |
| Dimensional modeler (`.hdm`) | Available | [dimensional-modeler-overview.adoc](dimensional-modeler-overview.adoc) |
| Dimensional Update / Publish actions | Available | [dimensional-update-action.adoc](dimensional-update-action.adoc) |
| Execution maps (`.hem`) | Available | [execution-maps.adoc](execution-maps.adoc) |
| AI Help (model, pipeline, workflow) | Available | [ai-advisory.md](ai-advisory.md) |
| Record Definition Input transform | Available | [record-definition-input.adoc](record-definition-input.adoc) |
| Date Dimension Generator transform | Available | [date-dimension-generator.adoc](date-dimension-generator.adoc) |
| `hop svg` export | Available | [README.md](README.md#command-line-tools) |
| BV naming rules engine | Planned | [plans/bv-naming-rules-engine-plan.md](plans/bv-naming-rules-engine-plan.md) |
| Marquez / OpenLineage export | Available | [openlineage-export.adoc](openlineage-export.adoc) |
| Hash-key ModPartitioner parallelism | Planned | [plans/hash-key-partitioning-plan.md](plans/hash-key-partitioning-plan.md) |

---

## Architecture (logical)

```
Source model (.hsm)  ──►  multi-table queries
        │                       │
        │         catalog DV_SOURCE (single-table or COMPOSITE)
        ▼                       │
Sources (Data Catalog / CRM) ◄──┘
        │
        ▼
  Raw Data Vault (.hdv)
  hubs · links · satellites
        │
        ├── Hop-managed loads
        ├── External read-only tables
        └── Custom .hpl orchestration
        │
        ▼
  Business Vault (.hbv)
  SCD2 · PIT
        │
        ▼
  Dimensional marts (.hdm)
  dimensions · facts · bridges
```

---

## Major capabilities

### Data Catalog and sources

Record definitions of type **`DV_SOURCE`** describe logical feeds: record-source indicator, optional **group** for partial loads, and physical layout (database table, file types, or **composite** multi-table query). Definitions live under namespace `hop/{project}/sources` in the project's FILE catalog storage directory (retail: `work/edw-catalog/`) and open in the **Data Catalog** perspective.

Hubs, links, and satellites reference source **names**, not raw connection details — one stable vocabulary from catalog through models to generated pipelines.

### Source modeler (`.hsm`)

Visual **source-system** modeler: import tables with PK/FK, draw relationships, and compose **source queries** (joins + projections). Publish a query as a catalog **COMPOSITE** feed; Data Vault loads generate single-connection SQL or a Merge Join pipeline. Retail sample: `retail-example/models/source-tables-crm.hsm`. See [source-modeler-overview.adoc](source-modeler-overview.adoc).

![Source modeler — retail CRM tables with All customer info query SQL](images/source-modeler-retail-example-with-query-dialog-generated-sql.png)

### Project and metadata search (Hop 2.19)

Hop **Search Everywhere** / project search includes hop-data-vault models and metadata:

| Scope | Examples of matches |
|-------|---------------------|
| `.hsm` | Source tables, relationships, multi-table **query** names, WHERE text, published feed names |
| `.hdv` | Hub / link / satellite names, physical names, configuration, canvas notes |
| `.hbv` / `.hdm` | BV and dimensional table names, paths, configuration, notes |
| Resource definition group | Group name, catalog connection, listed `.hdv` / `.hbv` / `.hdm` paths |
| Data catalog, metrics profile, quality rule set | Connection paths, rule field names, metrics folder |

Open a model hit to open the file (or focus an open tab) and jump to the matching table or source query when the hit carries a component name. Open tabs search **unsaved** in-memory content. Full usage: [search.adoc](search.adoc).

### Raw Data Vault (`.hdv`)

Visual modeler for hubs, links, and satellites with embedded configuration (target database, hashing, sentinels, column names, pipeline options). Toolbar actions: **Edit model**, **Import sources**, **Check model**, **Generate DDL**, **Debug**, optional **AI Help**.

**Data Vault Update** workflow action validates (optionally), generates DDL, stages update pipelines, and runs them in parallel with a shared load timestamp.

### Model validation and schema gate

**Check model** in the GUI and model checks in update actions share one engine: structural rules plus optional **detailed data type checking** against live source schemas.

Catalog **resource definition validation** adds feed-level checks with remediation proposals, acknowledgements, and **downstream impact** (Source→DV→BV→DM). From a **Resource definition group** you can **Tag catalog version**, **List catalog versions**, and **Validate sources** (options dialog for baseline + axes). Length remediation expands models and multi-layer DDL (DV/BV/DM) **from the catalog length** without rewriting the catalog. Retail sample package: `workflows/schema-remediation/accept-address_line1/` (see [resource-definition-validation.adoc](resource-definition-validation.adoc)).

The **Validate resource definitions** workflow action is the **CI/CD schema gate**:

* Compare modes: live source vs catalog, working tree vs tagged baseline, or version vs version
* Failure severity (`FAIL_ON_BLOCKING`, `FAIL_ON_WARNINGS`, `WARN_ONLY`)
* Markdown/HTML reports (for example under `work/reports/`) with blast-radius tables

See [resource-definition-validation.adoc](resource-definition-validation.adoc) and [data-catalog.adoc](data-catalog.adoc#catalog-versions).

![Validate resource definitions action dialog](images/validate-resource-definitions-action-dialog.png)

### Business Vault (`.hbv`)

Linked to a `.hdv` model. Defines **SCD2** consumption tables (single- or multi-satellite merge) and **PIT** helpers. **Business Vault Update** validates, optionally publishes target layouts to the catalog, generates SCD2 build pipelines, and orchestrates parallel execution.

### Dimensional modeler (`.hdm`)

Kimball star/snowflake modeling: dimensions, facts, junk dimensions, bridges, snapshot facts, and aggregates. **Dimensional Publish** drafts a `.hdm` from a `.hdv`; **Dimensional Update** loads the mart. Shares canvas interaction patterns with the DV modeler.

### Execution maps (`.hem`)

Crawl a root workflow or model and persist a graph of workflows, models, generated pipelines, and source datasets. Open `.hem` files in Hop GUI for execution and lineage views. The retail example includes maps for its main update workflow and the six-month simulation.

![Execution map in Hop GUI — simulate-6-months](images/execution-map-in-hop-gui-simulate-six-months.png)

### AI Help

Optional LLM advisory on Data Vault models, pipelines, and workflows — scenarios, context inclusions, review-before-apply proposals. Configure under Hop GUI → **Configuration → AI Assistant**.

The **Performance tuning** scenario can include load-run metrics and propose model configuration changes (parallel copies, preload lookup cache) after review:

![AI advisor — performance tuning recommendations](images/ai-advisor-offering-performance-tuning-advice.png)

### Pipeline transforms

- **Record Definition Input** — stream catalog record definitions (or fields) as pipeline rows.
- **Date Dimension Generator** — populate standard calendar dimension attributes.

### Operations

Docker-based runners (`scripts/run-hop.sh`, `run-postgres.sh`), parallel pipeline orchestration, **record source group** partial loads, catalog-backed load-run metrics, workflow load overview reports, and per-table duration bars on model graphs.

**Preferred batch shape:** **Update resource definition group** runs every DV/BV/DM model listed on a resource definition group (layer order DV → BV → DM) and can manage Begin/End-style vault update metrics in one action. See [update-resource-definition-group-action.adoc](update-resource-definition-group-action.adoc), [operations.adoc](operations.adoc), and [performance-tuning.md](performance-tuning.md).

![Update resource definition group action dialog](images/update-resource-definition-group-action-dialog.png)

![Workflow load overview — per-model summary](images/workflow-load-overview-summary-models.png)

![Retail 360 model with load duration overview](images/data-vault-retail-360-model-with-duration-metrics.png)

---

## Sample projects

| Project | Use for |
|---------|---------|
| **[retail-example](../retail-example/)** | Learning path — CSV → CRM → DV → BV → DM, initial and monthly updates |
| **[integration-tests](../integration-tests/)** | CI/regression — Customer 360, multi-active, external read-only, golden datasets |

![Retail 360 Data Vault model](images/data-vault-model-retail-example.png)

**Recommended path:** [getting-started-retail.adoc](getting-started-retail.adoc) → reference adoc → [getting-started-integration-tests.adoc](getting-started-integration-tests.adoc) for advanced fixtures.

---

## 0.1.x focus

The transition from 0.0.x to 0.1.x emphasizes:

- Documentation completeness and accurate catalog-first workflows
- Validation UX (model checks + catalog issue handling, schema gates, catalog versions)
- Retail tutorial as the primary onboarding path
- Hardening existing modelers and actions rather than large new subsystems