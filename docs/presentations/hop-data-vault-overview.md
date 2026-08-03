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

# Hop Data Vault 2.0 Plugin

**Model-driven Data Vault and Business Vault on Apache Hop 2.19.0**

> For a feature checklist with documentation links, see [feature-overview.md](../feature-overview.md).  
> For an interactive standalone HTML presentation (architecture, detail pages, roadmap & open issues), open [hop-data-vault-features.html](hop-data-vault-features.html).

---

# The problem

Enterprise data warehouses built with hand-written pipelines drift from the model.

- Hashing rules change in one pipeline but not another
- Satellite CDC logic is copy-pasted across feeds
- Business Vault / SCD2 timelines are rebuilt in SQL or dbt without a shared contract
- Hybrid teams (Hop + dbt + SQL) cannot share one metadata picture

**Result:** slower delivery, inconsistent loads, and fragile consumption layers.

---

# The approach

**Model once. Generate loads and consumption layers.**

1. Define sources, hubs, links, and satellites in a visual model (`.hdv`)
2. Validate structure and types before anything runs
3. Generate DDL and insert-only load pipelines automatically
4. Build Business Vault SCD2 tables from satellite history (`.hbv`)
5. Run controlled batch updates from workflow actions

The model is the contract — not a diagram that diverges from production.

---

# Raw Data Vault layer

**Hubs** — core business entities (Customer, Product, Order)

**Links** — relationships between hubs

**Satellites** — descriptive history attached to hubs or links

**Insert-only loading** — new rows only when keys or attributes actually change; every batch shares one load timestamp.

Supports multi-active satellites, link satellites, load end dating, multi-source hubs, and sentinel rows (unknown / invalid).

---

# Business Vault layer

Raw vault stores **technical** history (when the warehouse loaded a change).

Consumption often needs **functional** history (when the source says a value was true).

The plugin generates **SCD Type 2** Business Vault tables:

- `valid_from` / `valid_to` (or your column names)
- Open-ended sentinels for current rows
- Single-satellite or **multi-satellite merge** (e.g. Customer 360 from four satellites)

Saved as `.hbv` files, linked to the underlying `.hdv` model.

---

# Hybrid warehouses

Not every raw vault table must be Hop-loaded.

**Integration modes** per hub, link, or satellite:

| Mode | Meaning |
|------|---------|
| **Hop managed** | Hop generates DDL and update pipelines (default) |
| **External read-only** | Table exists elsewhere (dbt, SQL, another ETL); Hop documents it for BV |
| **Custom pipelines** | Hop orchestrates your own `.hpl` files |

Business Vault generation reads **metadata** (table layout, hash keys, attributes) — not Hop-generated DV pipelines. External tables participate fully in BV and catalog publish.

---

# Model-driven operations

| Operation | Purpose |
|-----------|---------|
| **Check model** | Catch broken references and type mismatches early |
| **Generate DDL** | Preview CREATE/ALTER for the vault database |
| **Debug** | Open generated pipelines for inspection |
| **Data Vault Update** | Full raw vault batch load from a workflow |
| **Business Vault Update** | Rebuild BV tables from satellite history |

Same validation engine in the GUI and in workflow actions.

---

# Data catalog and lineage

- **Sources** live in the Hop data catalog (record definitions, field layouts, groups)
- Optional **publish** of target table layouts after DV or BV updates
- Foundation for downstream dimensional modeling and OpenLineage / Marquez export

Catalog + model = shared vocabulary between ingestion, vault, and consumption teams.

---

# AI advisory (optional accelerator)

**AI Help** on the DV model toolbar:

- Analyze catalog sources and suggest hubs, links, satellites
- Propose layout and type mappings with review-before-apply
- Troubleshoot validation and integration errors

Requires a configured LLM in Hop GUI → Configuration → AI Assistant. Human modelers remain in control.

---

# Quality and repeatability

The sample `integration-tests/` project includes:

- Golden-dataset unit tests per suite
- Docker-based test runner (PostgreSQL, MySQL, SingleStore)
- Metrics JSON per Data Vault / Business Vault update run
- Orchestrated workflow covering basic vault, multi-active, link satellite, load end date, status tracking, multi-source hub, and **multi-satellite Business Vault (Customer 360)**

CI-friendly: fail fast on model checks or DDL drift.

---

# Architecture (logical)

```
Sources (catalog / CRM)
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
  SCD2 · PIT · future bridges
        │
        ▼
  Dimensional marts (.hdm)
```

One metadata chain from staging to consumption.

---

# Who benefits

| Role | Value |
|------|-------|
| **Architects** | Governed hashing, naming, and load patterns; hybrid warehouse support |
| **Modelers** | Visual design, generated pipelines, BV without hand-written SQL |
| **Operations** | Workflow-driven batches, parallel execution, metrics, validation gates |
| **Analytics / BI** | Functional SCD2 timelines and standardized BV column names |

---

# Sample project highlights

Under `integration-tests/tests/`:

- **vault1** — classic hub / link / satellite
- **customer-phone** — multi-active satellite
- **customer-360** — four satellites merged into one BV SCD2 table
- **customer-360-external** — same BV model with **external read-only** DV satellites

![Customer 360 Data Vault model](../images/data-vault-model-customer-360.png)

![Customer 360 Business Vault model](../images/business-vault-model-customer-360.png)

Runnable via `integration-tests/run-tests.sh` or Hop GUI with the `hop-data-vault` project open. For a full-stack learning path, use `retail-example/` — see [getting-started-retail.adoc](../getting-started-retail.adoc).

![Retail 360 Data Vault model](../images/data-vault-model-retail-example.png)

---

# Integration modes (when to use which)

**Hop managed** — greenfield vault tables owned by Hop; full DDL + pipeline generation.

**External read-only** — dbt or another tool loads the raw vault; Hop models structure for BV, checks, and catalog. Canvas shows `(ext)`.

**Custom pipelines** — you maintain `.hpl` loaders; Hop runs them in the same orchestrator as generated pipelines. Canvas shows `(custom)`.

Choose per table — mixed models are supported.

![External read-only satellites](../images/data-vault-model-customer-360-external.png)

---

# Maturity and roadmap

**Available today**

- Raw DV modeling and loading
- Business Vault SCD2 (single and multi-satellite)
- PIT table type in BV model
- Dimensional modeler and Dimensional Update/Publish
- Execution maps (`.hem`)
- External / custom integration modes
- Data Catalog sources and resource definition validation
- AI advisory

**Planned (0.1.x and beyond)**

- BV naming rules engine
- ~~Marquez / OpenLineage lineage export~~ — shipped in 0.5.0
- ~~Reference tables, source modeler, project search, architecture export~~ — shipped in 0.6.0
- Hash-key ModPartitioner parallelism

---

# Getting started

| Audience | Start here |
|----------|------------|
| **Modelers** | [docs/getting-started-retail.adoc](../getting-started-retail.adoc) |
| **Advanced fixtures** | [docs/getting-started-integration-tests.adoc](../getting-started-integration-tests.adoc) |
| **Implementers** | [README.md](../../README.md) and [integration-tests/PROJECT.md](../../integration-tests/PROJECT.md) |
| **Reference** | [docs/README.md](../README.md) — full doc index |

Open `retail-example/` for the tutorial, or `integration-tests/` as **`hop-data-vault`** for regression suites. Configure **CRM** and **Vault** connections, install plugin **0.6.0** on Apache Hop **2.19.0** / recent **2.19.0-SNAPSHOT**.

---

# Summary

The Hop Data Vault plugin turns Data Vault 2.0 from a pile of pipelines into a **governed, model-driven platform**:

- Raw vault loads generated from `.hdv`
- Business vault SCD2 from `.hbv`
- External tables welcomed, not blocked
- Validation before execution
- Sample project proves it end-to-end

**Model the warehouse. Let Hop generate the mechanics.**