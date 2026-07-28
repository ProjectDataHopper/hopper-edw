# Changelog

All notable changes to the hop-datavault plugin are documented in this file.

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
