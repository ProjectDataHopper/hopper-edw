# Changelog

All notable changes to the hop-datavault plugin are documented in this file.

## [Unreleased] — 0.5.0-SNAPSHOT

### OpenLineage / Marquez lineage export (#101)

- New workflow action **Export data lineage** emits model-derived OpenLineage COMPLETE events for DV/BV/DM tables (optional columnLineage + schema facets)
- Destinations: folder (one JSON file per table + summary) and/or HTTP POST to OpenLineage endpoints (Marquez, Collibra OL-compatible)
- Optional operational enrichment from `load_pipeline_metric`
- Local stack: `./scripts/run-marquez.sh` and `scripts/docker/compose.marquez.yml` (API :5001, UI :3001)
- Docs: [docs/openlineage-export.adoc](docs/openlineage-export.adoc)

### Optional primary and foreign keys in model DDL (#92)

- Two **optional, default-off** model configuration checkboxes on DV (`.hdv`), BV (`.hbv`), and DM (`.hdm`): **Generate primary keys in DDL** and **Generate foreign keys in DDL**
- CREATE TABLE only (no ALTER retrofit of constraints on existing tables)
- DV PK rules: hub/link hash key; satellite parent hash + multi-active driving key + load date
- DV FK rules: link → hubs, satellite/STS → parent hub or link
- BV PK for SCD2 grain + `valid_from` and PIT hash + snapshot date; FK to DV only when BV and DV share the same target database
- DM PK for dimension/junk surrogate keys and bridge composites; FK for fact/bridge roles and dimension outriggers (facts have no PK in this release)
- Foreign keys are skipped on SingleStore (and any engine treated as non-FK-capable); primary keys still apply when enabled
- Enabling foreign keys also emits primary keys on parent tables that are referenced
- Multi-source hubs/links: `generateUpdateWorkflows()` emits a serial workflow (Start → source pipelines in series) so sources for the same table no longer race under parallel Pipeline Executor / bulk load; free pipelines stay parallel
- DV Update partitions multi-source units vs free pipelines; Debug opens multi-source workflows
- Link updates: source SQL uses `SELECT DISTINCT` on relationship keys (was plain `SELECT`); Unique Rows on link hash before CDC merge so duplicate LHKs cannot bulk-load twice under PRIMARY KEY
- **Root cause fix for `lnk_order_pkey` / bulk COPY failures:** link CDC target ordering for STRING/HEX hash keys must match Hop SortRows. Linguistic DB collations (not only PostgreSQL) can reverse decimal-dash keys (e.g. `0-100-…` vs `0-10-…`), desynchronizing MergeRows so existing LHKs are flagged `new`. **Automatic strategy** (`DvHashKeyOrderStrategySupport`): SQL `ORDER BY … COLLATE` with a hop-compatible binary/`C` collation when certain (static trust + optional live probe for PostgreSQL `"C"`, SQL Server `BIN2`, MySQL/SingleStore `utf8mb4_bin`, …); otherwise Hop SortRows on the target leg. Source stream always SortRows after `DvHashKey`. BINARY hash keys use plain SQL `ORDER BY`. No user option — correctness is the plugin's job

### Resource definition validation and catalog-safe remediation (#83)

- Design-time **Validate sources** opens an options dialog (baseline: working catalog or version tag; check axes for live sources, version drift, target models, target databases; optional report path)
- Validation results dialog is master-detail with baseline/axes banner; remediation via explicit **Remediation proposals…** (double-click still works)
- Length remediation expands DV satellite attributes, BV SCD2 mapped columns, and DM SQL-sourced columns **from the catalog field length** — the catalog is never rewritten on this path
- Multi-table remediation package: SQL script + Hop workflow (one SQL action per target table) under the configured schema-remediation folder
- BV-mediated SQL lineage for free-form DM sources (e.g. `d_customer.cust_address` via `customer_360_bv`)
- Read-only **Versions** tree in the Data Catalog perspective for tagged catalog snapshots
- Assembly packages `hop-action-sql` / success action so generated remediation workflows can run SQL actions
- Retail sample package: `retail-example/workflows/schema-remediation/accept-address_line1/`
- Docs and screenshots: [docs/resource-definition-validation.adoc](docs/resource-definition-validation.adoc)

## [0.4.0] — 2026-07-29

Requires **Apache Hop 2.18.1** and **Java 21**.

### Source-to-target lineage (#97)

- Derive table- and field-level lineage from DV (`.hdv`), BV (`.hbv`), and DM (`.hdm`) models with structured reason codes
- **Lineage** tab on hub, link, satellite, BV SCD2, and dimensional table dialogs; **Lineage…** viewer for flat dialogs
- Explainable DDL: whenever update actions generate structure changes (including **Fail if DDL is needed**), log which mappings force the delta; Generate DDL shows the same explanation in the GUI
- Publish lineage sibling records under `hop/{project}/lineage/{dv|bv|dm}/{model}/` when models publish to the Data Catalog
- Lineage drift gate: **Validate resource definitions** compares current models to catalog lineage baselines (blocking renames without `USER_EXPLICIT_NAME`)
- Reverse lineage browser from **Resource definition group** (**Browse lineage…**): filter by source feed/field, multi-hop paths, open consumer model elements
- Fix Explorer multi-pane open for `.hdv` / `.hbv` / `.hdm` / `.hem` (public `getTabFolder()` API)
- Lineage tab layout: height scales with global zoom; outer margins
- Reverse browser: open correct model after `TableView` column sort

Documentation: [docs/source-to-target-lineage.adoc](docs/source-to-target-lineage.adoc)

### Data Vault / Business Vault / dimensional

- Issue #90: transactional links via dependent child keys
- Issue #81: separate DV and BV databases for incremental SCD2 and PIT
- Issue #91: expand SQL Server UTF-8 `VARCHAR` lengths for multi-byte data
- Issue #87: pipeline wall-clock timing on `load_pipeline_metric`
- Issue #85: document pure Type 2 BV SCD2 and hybrid Type 1/2 guidance

### Operations / build

- Jenkins/catalog fixture build fixes
- Reduced init logging

### Notes

- Catalog version tags and the **Validate resource definitions** schema gate remain as shipped in the **0.3.0** preview; this release adds **lineage** on top of that foundation.

## [0.3.0] — 2026-07-14

Preview release. See GitHub release notes for catalog version tags, schema impact simulation, and the CI/CD schema validation gate.

## [0.2.0] — 2026-07-12

Preview release. Dimensional modeler, execution maps, catalog-first sources, data quality foundations, multi-DB hardening.
