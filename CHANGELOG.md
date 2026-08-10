# Changelog

All notable changes to the hop-datavault plugin are documented in this file.

## Unreleased

### Hop Web modelers (#119)

- Render `.hsm` / `.hdv` / `.hbv` / `.hdm` / `.hem` graphs under **Hop Web** via the Hop SVG canvas stack (`CanvasSvgFacade.publishSnapshot`, requires Hop with [apache/hop#7873](https://github.com/apache/hop/issues/7873) / [#7874](https://github.com/apache/hop/pull/7874) and related web client fixes)
- Shared web shell (`HopGuiModelGraphBase`): register canvas, zoom handler, hover, drag arming on mouse-down, final position apply on mouse-up, `nodes`/`notes` maps for client previews
- Client polish (with Hop `canvas-svg.js` / `canvas-zoom.js`): card- and note-sized drag ghosts, note resize handles/outline, pan wireframe that undims on release, wheel zoom through the SVG overlay, active-tab SVG rebind (link follow / tab switch)
- Source model minimap content; BV/DV reference link navigation rebinds the shared SVG client to the target graph
- Execution map breadcrumb / drill zoom-fit uses the **focused** subgraph size (not full-document maximum)
- Skip load-duration chart pane under RAP (unsupported `ScrolledComposite.addPaintListener`); coach canvas DnD desktop-only
- Source model SVG export (`.hsm`) via `SvgExportService` / `SourceModelSvgPainter`
- Docs: [hop-web-modelers.md](docs/hop-web-modelers.md)

### Hop database type: Apache Hop Source Model

- Register **`@DatabaseMetaPlugin`** type **Apache Hop Source Model** (`HOPSOURCEMODEL`) so Hop connection metadata can use the thin hop-hsm JDBC driver against Hop Server source model services
- Ship `hop-hsm-jdbc` in the plugin zip (`plugins/misc/datavault/lib/`) so Table Input / Explore DB can load `org.apache.hop.hsm.jdbc.HopHsmJdbcDriver` without a separate install

## [0.8.0] — 2026-08-10

Requires **Apache Hop 2.19.0** (or a **recent 2.19.0-SNAPSHOT** until GA) and **Java 21**.

**Hop runtime:** Hop **2.19.0** is not yet published to Maven Central. To use this plugin you must either **build Hop from source** (branch/tag aligned with 2.19) or **download a recent CI snapshot** of the Hop client:

- [Apache Hop 2.19.0-SNAPSHOT (hop-client)](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)

**Downloads:** [GitHub release zip](https://github.com/mattcasters/hop-data-vault/releases/download/v0.8.0/hop-datavault-0.8.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.8.0/hop-datavault-0.8.0.zip) (`org.apache.hop:hop-datavault:0.8.0`)

### Free SQL over source models + Hop Server JDBC (#117 / #115)

- **Apache Calcite** free SQL against `.hsm` logical names: full database pushdown when safe; residual Sort / Merge Join / Filter / Group By / Calculator / MetaInject / JSON subgraphs when mixed
- **Free SQL** generation mode on source queries: Validate, Explain plan, Preview, Insert tables…, View generated pipeline
- Named source queries, Source JSON, and Source Pipeline feeds as SQL tables; schema-qualified names (e.g. `crm.order_header`) for DBeaver
- **Source model SQL** pipeline transform (runtime free SQL against a `.hsm`)
- **Source model service** metadata: public name → server-side `.hsm` path (no client filesystem exposure)
- Hop Server servlet **`/hop/sourceModelData`** and thin zero-dependency client module **`hop-hsm-jdbc`** for DBeaver (`jdbc:hop-hsm://{username}:{password}@{host}:{port}/{database}`)
- Docs and screenshots: [source-modeler-overview.adoc](docs/source-modeler-overview.adoc), [hop-hsm-jdbc/README.md](hop-hsm-jdbc/README.md), [plans/source-model-sql-virtualization-plan.md](docs/plans/source-model-sql-virtualization-plan.md)

### Source modeler: pipeline sources + Record Definition Input (data) (#116)

- Rename catalog **metadata** transforms: **Get Record Definition Names** / **Catalog Record Definition Metadata Output** (plugin ids unchanged)
- New **Record Definition Input** (`RecordDefinitionDataInput`): read actual data rows from a catalog record definition (same path as catalog Preview data)
- **Source pipeline** cards on `.hsm`: pipeline file, output transform, declared fields, import catalog lineage refs from Record Definition Input transforms (0..n), open pipeline, canvas drag/select/hover-edit like other cards, relationships, push-to-catalog as `PIPELINE`
- Catalog / DV load: `DvSourceType.PIPELINE` + MetaInject hub/link/sat builders using declared field contracts
- **Origin / Go to origin**: pipeline (and JSON/composite) publish stores `.hsm` provenance; catalog navigation opens the source model and selects the card (`SOURCE_MODEL`)
- **Schema harvest / gate**: rediscover pipeline feeds from the source-model field projection (or live transform) instead of failing as unavailable
- **Relationship lifecycle**: drop edges on endpoint rename; prune dangling relationships on `.hsm` load
- **Link dialog**: **Suggest mappings…** matches a selected catalog feed’s fields to participating hub business keys (empty maps only)
- Preview pipeline sources activates pipeline parameter defaults (e.g. `RETAIL_CSV_WAVE`) like design-time preview
- **Retail**: ASN XML waves + `pipelines/parse-asn-xml.hpl` + pipeline source **asn-package-lines** → `hub_package_line` / `sat_package_line` / `lnk_package_line` on `retail-360.hdv`
- Docs: [record-definition-data-input.adoc](docs/record-definition-data-input.adoc), [source-modeler-overview.adoc](docs/source-modeler-overview.adoc), [dv-link.adoc](docs/dv-link.adoc), [datavault-source.adoc](docs/datavault-source.adoc), [getting-started-retail.adoc](docs/getting-started-retail.adoc), screenshots `docs/images/source-modeler-pipeline-source-*.png`

### Source modeler: JSON feeds, validation, and retail shipment path (#114)

- **Source JSON** on `.hsm`: flatten a parent table/query JSON column into a catalog **`JSON`** `DV_SOURCE`; sample/propose fields, preview, publish; relationship endpoints include tables, queries, and JSON sources
- **Validate** source tables / queries / JSON extractions / relationships from dialogs (live schema compare, length noise reduced for non-string types)
- **JSON hub/sat/link loads**: inject static record-source indicator (or rename source-indicator field); hub sources **sort + distinct** identity fields so MergeRowsPlus CDC works on incremental loads (same as CSV/DB)
- **Link hash key**: `resolveLinkHashKeyFieldName()` defaults blank names to `name_LK`; validation ERROR only when unresolvable; generation never uses a raw empty field
- **Catalog field layout**: structured `dvSource.fields` / `physicalTable.fields` is authoritative (no dual-write of layout as `rowMetaXml`); type import prefers source data type / effective Hop type
- **Retail**: Kafka-style `order_shipment_event` + Source JSON `order_shipment_tracking` → `feed_order_shipment_tracking` wired into `retail-360.hdv` (`hub_order_shipment`, `lnk_order_shipment`, `sat_order_shipment`); data generator and load pipeline updated
- Docs: [source-modeler-overview.adoc](docs/source-modeler-overview.adoc), [getting-started-retail.adoc](docs/getting-started-retail.adoc), [datavault-source.adoc](docs/datavault-source.adoc), [retail-example/README.md](retail-example/README.md)

### Update resource definition group: load-overview reports

- Propagate `DV_WORKFLOW_EXECUTION_ID` / started-at onto the action and programmatic child DV/BV/DM updates so `load_run.workflow_execution_id` is set and **Markdown/HTML load overview** reports write after the wave
- Clearer **ModelFailed** log detail (summarize child errors that were previously buried earlier in the log)
- Docs: [update-resource-definition-group-action.adoc](docs/update-resource-definition-group-action.adoc)

### Metadata harvesting (#112)

- **Harvest source metadata** workflow action: connection-batched live discovery, OPS history (`schema_harvest_run` / `subject` / `field` / `fk` / `change`), Markdown reports, variable `DV_SCHEMA_HARVEST_RUN_ID`
- Schema gate compare mode **`HARVEST_RUN`** reuses harvest without rediscovering sources; retail `run-retail-update-models.hwf` chain is harvest → validate → quality → update
- Harvest history GUI (resource definition group + catalog subject timeline) with changes / field snapshot drill-down
- Model-check / load path can prefer last harvest DISCOVERED layouts for detailed type checking
- PK drift and FK inventory (optional catalog FK contract; live-only FKs are INFO until applied)
- **Apply harvest to catalog…** and **Generate .hsm from harvest…** on the resource definition group editor
- Integer family equivalence for source field metadata (avoids false length drift across SMALLINT/INTEGER/BIGINT)
- Docs: [metadata-harvesting.adoc](docs/metadata-harvesting.adoc), screenshots under `docs/images/`

## [0.7.0] — 2026-08-05

Requires **Apache Hop 2.19.0** (or a **recent 2.19.0-SNAPSHOT** until GA) and **Java 21**.

**Hop runtime:** Hop **2.19.0** is not yet published to Maven Central. To use this plugin you must either **build Hop from source** (branch/tag aligned with 2.19) or **download a recent CI snapshot** of the Hop client:

- [Apache Hop 2.19.0-SNAPSHOT (hop-client)](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)

**Downloads:** [GitHub release zip](https://github.com/mattcasters/hop-data-vault/releases/download/v0.7.0/hop-datavault-0.7.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.7.0/hop-datavault-0.7.0.zip) (`org.apache.hop:hop-datavault:0.7.0`)

### Optional load cycle ID (#111)

- Optional **Store load cycle ID** on DV / BV / dimensional model configuration (default off)
- When enabled, every managed target table layout includes an integer audit column (default `LOAD_CYCLE_ID`)
- Each Data Vault / Business Vault / Dimensional Update allocates the next ID from a durable control table on the model target database (default `dv_load_cycle`) and stamps that value on all rows written in the run
- Workflow variable `DV_LOAD_CYCLE_ID` is set for the allocated value; generated pipelines inject a Constant integer field
- Docs: [datavault-configuration.adoc](docs/datavault-configuration.adoc)

### Composite hub business keys (multi-source-field → one vault BK column)

- Model a hub business key as **one physical vault column** composed from ordered **source parts** (`composite=Y` + `sourceFieldNames`), with dual-read of legacy multipartite vault columns and single-field mappings
- Hub DDL/load: compose stored BK with business key delimiter / trim / null placeholder; default **hash over parts** (optional hash content suffix is hash-only — VaultSpeed-style trailing `#` parity); optional **Hash composed business key** config flag
- Satellites and links map ordered source parts for parent/hub hash calculation only — they do **not** store the composed BK column
- Check model: part counts, multi-source consistency, link/sat parent-key length
- GUI: hub Keys composite column + multi-part source fields; satellite parent-key defaults/hints; link hub source mapping multi-part rows
- Integration suite `tests/composite-hub-bk/`: EXT two-part feed → `hub_burger` (`IKL#12278170`) + VS-style hash (`parts + #` + trailing `#`), satellite parent parts; wired into `run-tests.hwf` / SQL Server orchestrator
- Bundle `hop-transform-concatfields` in the plugin assembly (required for generated compose steps)
- Docs: [dv-hub.adoc](docs/dv-hub.adoc#composite-hub-business-keys), [dv-satellite.adoc](docs/dv-satellite.adoc), [dv-link.adoc](docs/dv-link.adoc), [datavault-configuration.adoc](docs/datavault-configuration.adoc), [feature-overview.md](docs/feature-overview.md); AI schema notes in [docs/ai-file-schemas/models/hdv.md](docs/ai-file-schemas/models/hdv.md)

### Optional satellite record-source column (VaultSpeed)

- Per-satellite **Store record source indicator** (default on) — uncheck to omit the physical source-indicator column from satellite (and STS) DDL and loads
- Model check no longer requires a feed source indicator when the satellite does not store the column; catalog feed binding remains required
- Business Vault SCD2 injects a constant record-source value (leg indicator / satellite name) when reading such satellites
- GUI checkbox on the satellite General tab; docs: [dv-satellite.adoc](docs/dv-satellite.adoc)

### AI file-schema pack for external agents

- `docs/ai-file-schemas/`: purpose markdown + relaxed XSD for `.hdv` / `.hbv` / `.hdm` / `.hsm`, JSON Schema for plugin metadata and catalog record definitions, samples and cross-reference guide (for Gemini and other external AIs)

### Catalog, model check, and GUI polish

- Catalog and data type validation refinements; SingleStore false type/length rediscovery diffs reduced
- Model check: database connection caching and progress bar
- Defer lineage and catalog source loads when opening model table dialogs
- **Open in catalog** action on the satellite record-source field
- Resource definition group editor performance with large model lists
- **MODELS** architecture export from resource definition groups
- Unicode EDW target checks globally configurable; binary fields shown as hex in plugin Show Rows previews
- Coach and metrics panels closed by default in modelers
- Record Definition Output improvements (physical table mapping + docs)
- Link table performance improvements

## [0.6.0] — 2026-08-03

Requires **Apache Hop 2.19.0** (or a **recent 2.19.0-SNAPSHOT** until GA) and **Java 21**.

**Hop runtime:** Hop **2.19.0** is not yet published to Maven Central. To use this plugin you must either **build Hop from source** (branch/tag aligned with 2.19) or **download a recent CI snapshot** of the Hop client:

- [Apache Hop 2.19.0-SNAPSHOT (hop-client)](https://repository.apache.org/content/groups/snapshots/org/apache/hop/hop-client/2.19.0-SNAPSHOT/)

Older Hop **2.18.x** (including 2.18.1) is **not** supported for 0.6.0.

**Downloads:** [GitHub release zip](https://github.com/mattcasters/hop-data-vault/releases/download/v0.6.0/hop-datavault-0.6.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.6.0/hop-datavault-0.6.0.zip) (`org.apache.hop:hop-datavault:0.6.0`)

### Metadata note — linked tables vs reference tables

In **0.6.0**, canvas **cross-model pointers** and **hub aliases** are stored as **linked tables**:

| Concept | `tableType` (saved) | Java type | Meaning |
|---------|---------------------|-----------|---------|
| **Linked table** | `LINKED_TABLE` | `DvLinkedTable` | Pointer / hub alias into another table or model (UI: *Add Linked Hub / Link / Satellite*) |
| **Reference table** | `REFERENCE` | `DvReferenceTable` | Physical vault **code/catalog** table (`ref_*`), natural keys + load audit, **no** hub hash |

**Compatibility:** existing `.hdv` files that used `TABLE_REFERENCE` still **load** (dual-read). On the next **save**, they are rewritten as `LINKED_TABLE`. This rename avoids confusion with physical **Reference tables** (#110). Docs: [dv-cross-model-references.adoc](docs/dv-cross-model-references.adoc).

### Reference tables (#110)

- First-class **Reference table** object on the raw Data Vault canvas: natural-key code/catalog tables with DV load audit columns (no hub hash keys or satellite hashdiff)
- GUI: add/edit dialog, icon, help topic; load mode **FULL_REPLACE** (delete+insert) with database and CSV source pipeline builders
- Catalog type, lineage, DDL, Data Vault Update execution, and multi-source workflow wiring
- Integration suite `tests/reference-table/` (golden `ref_country` two-wave load)
- Design notes: [docs/plans/dv-reference-tables-plan.md](docs/plans/dv-reference-tables-plan.md)

### Linked tables (rename from “table reference”)

- Canvas pointers / hub aliases are **linked tables** (`LINKED_TABLE` / `DvLinkedTable`)
- User language: **Add Linked Hub / Linked Link / Linked Satellite**; canvas badge `(linked)` (aliases stay `(alias)`)
- Dual-read of legacy `TABLE_REFERENCE`; save rewrites as `LINKED_TABLE`

### Cross-engine ORDER BY COLLATE (#108)

- Do not apply a SQL Server bridge collation (e.g. `French_CI_AS`) on PostgreSQL `ORDER BY` (or Postgres ICU/locale names on SQL Server) when source and target engines differ
- Same-engine remediation (SQL Server↔SQL Server, PostgreSQL↔PostgreSQL) is unchanged
- Fixes hub/satellite/link update SQL that failed with `collation "French_CI_AS" for encoding "UTF8" does not exist` on Postgres targets fed from SQL Server sources

### Source modeler (`.hsm`) and composite feeds (#105)

- New visual **source modeler** (`.hsm`): tables, PK/FK import, relationships, multi-table **source queries**, notes, ELK layout, undo/clipboard parity with other modelers
- Query builder: joins (relationship or explicit keys), projection, WHERE, generation mode AUTO/SQL/PIPELINE, SQL preview and row preview
- **COMPOSITE** catalog `DV_SOURCE`: publish queries with field layout + pointer to `.hsm` query; optional cached SQL
- Hub / link / satellite pipeline builders consume composite sources (single-connection SQL subquery or Merge Join pipeline injection)
- DV canvas action **Compose multi-table source…**; source-model toolbar/context **Publish to catalog**
- Retail sample: `retail-example/models/source-tables-crm.hsm` (query **All customer info** → `feed_customer_enriched`)
- Docs: [source-modeler-overview.adoc](docs/source-modeler-overview.adoc)

### Project and metadata search (#106)

- Opt `.hsm` / `.hdv` / `.hbv` / `.hdm` into Hop 2.19 project search via `CAPABILITY_SEARCH` and `createSearchable()`
- Content analysers for models and plugin metadata (resource definition group, catalog connection, execution metrics, quality rule set)
- Open search results navigate to the model and select/edit the matching table or source query when available
- Docs: [search.adoc](docs/search.adoc)

### Architecture export to Draw.io (#104)

- Derived **SOLUTION**, **DATA inventory**, and aggregated **MODEL** layer diagrams
- MODEL export unions all `.hdv` → `data-vault.drawio`, all `.hbv` → `business-vault.drawio`, all `.hdm` → `dimensional.drawio`
- CLI / action: `hop architecture-export` (`--also-data`, `--also-models`)
- Docs: [architecture-export.adoc](docs/architecture-export.adoc)

### Hub aliases — same physical hub twice on a link (#103)

- Same-model **hub aliases** (linked table with optional role `hashKeyFieldName`) so a link can participate one physical hub more than once
- Canvas **Add Hub alias**; link dialog, DDL, load pipelines, special records, optional FKs
- Integration: `tests/hub-alias-role-playing/`; retail sample `hub_sales_rep` + `hub_secondary_rep` + `lnk_order_rep`

### Satellite parent key source fields (DV2 independent feeds)

- Hub owns logical business keys and hash order; hub satellites may list **ordered source field names** (`parentKeySourceFields`) when names differ from hub BKs
- Satellite dialog tab **Parent key source fields**
- Docs: [dv-satellite.adoc](docs/dv-satellite.adoc), [dv-hub.adoc](docs/dv-hub.adoc)

### Update resource definition group action

- Workflow action **Update resource definition group** runs every DV / BV / DM model on a resource definition group
- Optional **Manage vault update metrics (Begin/End)**; open referenced models from the canvas
- Docs: [update-resource-definition-group-action.adoc](docs/update-resource-definition-group-action.adoc)

### SQL Server string length / Unicode (issue #91 follow-up)

- Model lengths remain **characters**; vault SQL Server DDL still creates UTF-8 **`VARCHAR(n×3)`**
- Field-mapping overflow uses `effectiveStringCapacity` (capacity matches vault DDL)
- Catalog/CRM staging CREATE does **not** apply vault UTF-8 length expansion

### Hop 2.19 platform requirements

- Development and runtime target is **Apache Hop 2.19.0** (use **2.19.0-SNAPSHOT** until GA)
- Enables database-backed execution information (e.g. OPS `hop_executions`), BINARY hash key sorting ([apache/hop#7346](https://github.com/apache/hop/issues/7346)), and Hop Marketplace install
- Markdown notes on DV/BV/DM model canvases (Hop 2.19 note API)
- Apache snapshots repository `updatePolicy=always` for reliable CI resolution of Hop SNAPSHOTs

### Stability and tooling

- Canvas SVG icon cache / SWT handle-leak hardening (`ModelGraphSvgIconCache`; reflective `SwtGc#getNativeGc` for mixed hop-ui snapshots)
- FILE catalog mkdir when `PROJECT_HOME` is unresolved
- Docker test image freshness: rebuild `docker-hop:latest` when `target/hop-datavault-*.zip` is newer than the image

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
