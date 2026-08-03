# Changelog

All notable changes to the hop-datavault plugin are documented in this file.

## Unreleased

### Reference tables (#110)

- First-class **Reference table** object on the raw Data Vault canvas (`tableType=REFERENCE`, `DvReferenceTable`): natural-key code/catalog tables with DV load audit columns (no hub hash keys or satellite hashdiff)
- GUI: add/edit dialog, icon, help topic; load mode **FULL_REPLACE** (delete+insert) with database and CSV source pipeline builders
- Catalog type, lineage, DDL, Data Vault Update execution, and multi-source workflow wiring
- Integration suite `tests/reference-table/` (golden ref_country two-wave load) on main and SQL Server runners
- Design notes: [docs/plans/dv-reference-tables-plan.md](docs/plans/dv-reference-tables-plan.md)

### Linked tables (rename from “table reference”)

- Canvas pointers / hub aliases are now **linked tables** (`tableType=LINKED_TABLE`, class `DvLinkedTable`) so they are not confused with physical **Reference tables** (`REFERENCE` / `DvReferenceTable`)
- User language: **Add Linked Hub / Linked Link / Linked Satellite**; canvas badge `(linked)` (aliases stay `(alias)`)
- Dual-read: existing `.hdv` with `TABLE_REFERENCE` still load; save rewrites as `LINKED_TABLE`
- Docs: [dv-cross-model-references.adoc](docs/dv-cross-model-references.adoc)

### SQL Server string length model-check vs vault DDL (issue #91 follow-up)

- Model lengths remain **characters** (e.g. 50); vault SQL Server DDL still creates UTF-8 **`VARCHAR(n×3)`** (e.g. 150)
- Field-mapping overflow check now uses `DvDdlSupport.effectiveStringCapacity` so capacity matches vault DDL (no more false “source 150 exceeds target 50”)
- Catalog/CRM staging CREATE no longer applies vault UTF-8 length expansion (only vault/EDW paths do)
- Docs: [datavault-plugin.adoc](docs/datavault-plugin.adoc), [dv-satellite.adoc](docs/dv-satellite.adoc)

### Docker test image freshness

- `ensure_hop_image` rebuilds `docker-hop:latest` when `target/hop-datavault-*.zip` is newer than the image (avoids stale plugin after `mvn package` without `./scripts/rebuild-hop.sh`)

### Hop 2.19.0-SNAPSHOT resolution on CI

- Apache snapshots repository uses `updatePolicy=always` so agents re-check ASF Nexus for newer Hop SNAPSHOTs (avoids same-day stale `hop-ui` / missing `SwtGc#getNativeGc`)
- `ModelGraphSvgIconCache` resolves `getNativeGc` reflectively so compile still succeeds if an older hop-ui is on the classpath

### Cross-engine ORDER BY COLLATE (#108)

- Do not apply a SQL Server bridge collation (e.g. `French_CI_AS`) on PostgreSQL `ORDER BY` (or Postgres ICU/locale names on SQL Server) when source and target engines differ
- Same-engine remediation (SQL Server↔SQL Server, PostgreSQL↔PostgreSQL) is unchanged
- Fixes hub/satellite/link update SQL that failed with `collation "French_CI_AS" for encoding "UTF8" does not exist` on Postgres targets fed from SQL Server sources

### Project and metadata search (#106)

- Opt `.hsm` / `.hdv` / `.hbv` / `.hdm` into Hop 2.19 project search via `CAPABILITY_SEARCH` and `createSearchable()`
- Content analysers for Data Vault, Business Vault, dimensional, and source models (table/query names, notes, configuration, high-value fields)
- Content analysers for plugin metadata: resource definition group (model paths), data catalog connection, execution metrics profile, data quality rule set
- Open search results navigate to the model and select/edit the matching table or source query when available
- Open tabs search the in-memory model (unsaved edits)
- Docs: [search.adoc](docs/search.adoc), feature overview matrix + plugin/operations usage

### Required Apache Hop version

- Development and runtime target is **Apache Hop 2.19.0** (use **2.19.0-SNAPSHOT** until the GA release ships)
- Enables database-backed execution information (e.g. OPS `hop_executions`), BINARY hash key sorting ([apache/hop#7346](https://github.com/apache/hop/issues/7346)), and Hop Marketplace install for this plugin

### Satellite parent key source fields (DV2 independent feeds)

- Hub owns logical business keys and hash order; hub satellites only optionally list **ordered source field names** on the sat feed (`parentKeySourceFields`) that supply those values
- Empty list (normal case): source columns have the same names as the hub business keys
- Non-empty list: same length as distinct hub BKs, zipped by position (no hub-BK name mapping on the satellite)
- Satellite dialog tab **Parent key source fields**; **Load hub key names** fills the same-name default
- Removed model-check warning that required the satellite record source to be listed on the parent hub
- Hub Record sources / Keys remain for **hub loads only**
- Docs: [dv-satellite.adoc](docs/dv-satellite.adoc), [dv-hub.adoc](docs/dv-hub.adoc)

### Source modeler (`.hsm`) and composite feeds (#105)

- New visual **source modeler** (`.hsm`): tables, PK/FK import, relationships, multi-table **source queries**, notes, ELK layout, undo/clipboard parity with other modelers
- Query builder: joins (relationship or explicit keys), projection, WHERE, generation mode AUTO/SQL/PIPELINE, SQL preview and row preview
- **COMPOSITE** catalog `DV_SOURCE`: publish queries with field layout + pointer to `.hsm` query; optional cached SQL
- Hub / link / satellite pipeline builders consume composite sources (single-connection SQL subquery or Merge Join pipeline injection)
- DV canvas action **Compose multi-table source…**; source-model toolbar/context **Publish to catalog**
- Retail sample: `retail-example/models/source-tables-crm.hsm` (query **All customer info** → `feed_customer_enriched`)
- Docs and screenshot: [source-modeler-overview.adoc](docs/source-modeler-overview.adoc); feature overview + getting-started snippet

### Update resource definition group action

- Workflow action **Update resource definition group** runs every DV / BV / DM model listed on a resource definition group (layer order DV → BV → DM; list order within each layer)
- Tabbed dialog: Selection, Run, Operations, Data catalog, Metrics, Reports
- Optional **Manage vault update metrics (Begin/End)** assigns `DV_WORKFLOW_EXECUTION_ID` and publishes a workflow load overview without separate Begin/End actions
- Open referenced models from the workflow canvas with project-relative labels (no `${PROJECT_HOME}/` prefix in the menu text)
- Retail: `run-retail-update.hwf` / `run-retail-update-models.hwf` use group `retail-sources`
- Docs: [update-resource-definition-group-action.adoc](docs/update-resource-definition-group-action.adoc), [operations.adoc](docs/operations.adoc)

### Architecture export to Draw.io (#104)

- Derived **SOLUTION** architecture (workflows / capabilities / model file refs only — no dataset table dump), **DATA inventory**, and **aggregated MODEL** layer diagrams
- MODEL export unions all `.hdv` → `data-vault.drawio`, all `.hbv` → `business-vault.drawio`, all `.hdm` → `dimensional.drawio` (ELK layout, shared tables deduped)
- SOLUTION swimlanes stay architectural; MODEL freeform uses ELK; default inventory file is `data-inventory.drawio` (not concatenated model names)
- Paths in exports are project-relative or basenames (e.g. `models/retail-360.hdv`), not host absolute paths
- CLI / action: `hop architecture-export` (`--also-data`, `--also-models`)
- Docs: [architecture-export.adoc](docs/architecture-export.adoc)

### Hub aliases — same physical hub twice on a link (#103)

- Same-model **hub aliases** (`LINKED_TABLE` / legacy `TABLE_REFERENCE` with optional role `hashKeyFieldName`) so a link can participate one physical hub more than once with distinct source mappings and link columns (primary/secondary rep, from/to location)
- Canvas action **Add Hub alias**; link dialog lists hub aliases as participating hubs
- Link DDL, load pipelines, special records, and optional FKs use role hash columns
- Docs: [dv-cross-model-references.adoc](docs/dv-cross-model-references.adoc), [dv-link.adoc](docs/dv-link.adoc)
- Recommended modeling shape: **natural hub + alias** for extra roles (not two aliases with an orphan physical hub)
- Integration: `tests/hub-alias-role-playing/`; retail sample: `hub_sales_rep` + `hub_secondary_rep` + `lnk_order_rep` with `sales_rep_initial.csv` / `order_rep_initial.csv`

## [0.5.0] — 2026-07-30

Requires **Apache Hop 2.18.1** and **Java 21**.

### OpenLineage / Marquez lineage export (#101)

- New workflow action **Export data lineage** emits model-derived OpenLineage COMPLETE events for DV/BV/DM tables (table + optional column lineage)
- Destinations: folder (one JSON file per table + summary) and/or HTTP POST to OpenLineage endpoints (Marquez, Collibra OL-compatible)
- Configurable **job** and **dataset** namespaces
- Physical location facets: standard `dataSource` plus `hop_location` (database connection/schema/table, CSV/Parquet folder+mask, Iceberg catalog location)
- Dimension aliases (role-playing dims) keep logical dataset identity and **symlink** to the shared physical dimension (e.g. `d_shipping_date` → `d_date`)
- Marquez-safe `dataSource.uri` encoding (spaces / unresolved `${variables}`) so failed POSTs no longer abort mid-export when **Fail on HTTP error** is enabled
- Optional operational enrichment from `load_pipeline_metric`
- Local stack: `./scripts/run-marquez.sh` and `scripts/docker/compose.marquez.yml` (API :5001, UI :3001)
- Retail sample: `send-lineage-to-marquez.hwf`, env vars `MARQUEZ_API` / `MARQUEZ_NAMESPACE_*`
- Docs and screenshots: [docs/openlineage-export.adoc](docs/openlineage-export.adoc)

### Optional primary and foreign keys in model DDL (#92)

- Two **optional, default-off** model configuration checkboxes on DV (`.hdv`), BV (`.hbv`), and DM (`.hdm`): **Generate primary keys in DDL** and **Generate foreign keys in DDL**
- CREATE TABLE only (no ALTER retrofit of constraints on existing tables)
- DV PK rules: hub/link hash key; satellite parent hash + multi-active driving key + load date
- DV FK rules: link → hubs, satellite/STS → parent hub or link
- BV PK for SCD2 grain + `valid_from` and PIT hash + snapshot date; FK to DV only when BV and DV share the same target database
- DM PK for dimension/junk surrogate keys and bridge composites; FK for fact/bridge roles and dimension outriggers (facts have no PK in this release)
- Foreign keys are skipped on SingleStore (and any engine treated as non-FK-capable); primary keys still apply when enabled
- Enabling foreign keys also emits primary keys on parent tables that are referenced
- Multi-source hubs/links: serial workflow generation so sources for the same table no longer race under parallel bulk load
- Link CDC target ordering for STRING/HEX hash keys matches Hop SortRows (collation-safe automatic strategy)
- Integration: drop scripts not blocked by BV views on shared Vault DB

### Resource definition validation and catalog-safe remediation (#83)

- Design-time **Validate sources** options dialog (baseline, check axes, optional report path)
- Validation results master-detail; remediation via **Remediation proposals…**
- Length remediation expands DV satellite attributes, BV SCD2 mapped columns, and DM SQL-sourced columns **from the catalog field length** (catalog never rewritten)
- Multi-table remediation package: SQL script + Hop workflow under the schema-remediation folder
- BV-mediated SQL lineage for free-form DM sources
- Read-only **Versions** tree in the Data Catalog perspective
- Docs and screenshots: [docs/resource-definition-validation.adoc](docs/resource-definition-validation.adoc)

### Portability / UX

- Issue #98: portable model and execution-map paths for cross-host projects
- Dark-mode note fills (dark orange / dark gray)
- Overview presentation and docs refresh

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
