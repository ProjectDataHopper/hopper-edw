# Changelog

All notable changes to Data Hopper EDW (formerly Data Hopper EDW) are documented in this file.

## Unreleased

### Product identity: Data Hopper EDW

- Repository home: [ProjectDataHopper/hopper-edw](https://github.com/ProjectDataHopper/hopper-edw) (transferred from `ProjectDataHopper/hopper-edw`; old GitHub URLs redirect)
- Product name: **Data Hopper EDW** — Apache Hop plugins to build an Enterprise Data Warehouse, not a Data Vault-only tool
- Maven coordinates: `org.projectdatahopper.hop:hopper-edw` (was `org.apache.hop:hop-datavault`). Marketplace: `./hop marketplace install hopper-edw`
- Assembly zip: `target/hopper-edw-*.zip`. Unzip layout stays `plugins/misc/datavault/`
- License remains Apache License 2.0; copyright **i-Bridge bv**. Not an Apache Software Foundation release
- **0.9.0 cutover:** uninstall `$HOP_HOME/plugins/misc/datavault` from the old `hop-datavault` artifact, re-import [`hop-marketplace-repo.yaml`](hop-marketplace-repo.yaml), then install `hopper-edw`. Release 0.9.0 remains at `org.apache.hop:hop-datavault` on Nexus

### Jinja macros and dbt-core import (issue #72)

- Business Vault SQL tables render `{% set %}` / `{% if %}` / `{% for %}` and project macros via sandboxed Jinjava (whitespace matches dbt-core: `trim_blocks` / `lstrip_blocks` off)
- Plugin zip unpacks Jinjava runtime jars into Hop `lib/core` (`jinjava`, `immutables-exceptions`, `algebra`, `big-math`, `java-ipv6`, `jackson-dataformat-yaml`) so Hop GUI's application classloader can load `ExtendedSyntaxBuilder` and `FromYamlFilter`
- Jinja render also sets the plugin thread context classloader as a fallback when only the plugin folder is copied
- New **Jinja macro library** metadata (editor + test-render); `var()` prefers Hop variables
- **Import dbt models** on the BV canvas (scan `dbt_project.yml` + SQL + YAML + macros)
- Workflow action **Import dbt project** for CI re-import
- One-arg `ref()` also resolves sibling `.hbv` files; optional table schema and column notes
- Integration fixture: `sat_customer_hb_jinja` in `vault1.hbv`
- Guide: [docs/dbt-import.adoc](docs/dbt-import.adoc)

### Target type mappings (issue #127)

- New project metadata **Target type mapping**: ordered Hop-type + length/precision rules emit native SQL types (`CHAR(1)`, `NVARCHAR({length})`, `timestamp(6) with time zone`) **before** Hop dialect DDL
- Select the mapping on **Record Definition DDL**, **Update resource definition group**, and single-model DV / BV / DM Update; unique auto-match when exactly one mapping names the target connection
- `{length}` / `{precision}` placeholders and Hop `'${variables}'`; SQL Server UTF-8 rewrite is skipped for rule-matched columns
- Retail sample: `vault-target-mapping` (String length 1 → `CHAR({length})`) selected on `run-retail-update-models.hwf`
- Editor screenshots: Rules and Preview tabs
- Guide: [docs/target-type-mappings.adoc](docs/target-type-mappings.adoc)

### Jenkins / Maven docs rewrite

- Generated-docs `.adoc` href rewrite runs in Java (`RewriteAdocHrefs`) so `mvn verify` no longer requires `python3` on CI agents

## [0.9.0] — 2026-08-16

Requires **Apache Hop 2.19.0** and **Java 21**.

**Downloads:** [GitHub release zip](https://github.com/ProjectDataHopper/hopper-edw/releases/download/v0.9.0/hop-datavault-0.9.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.9.0/hop-datavault-0.9.0.zip) (`org.apache.hop:hop-datavault:0.9.0`)

### SQL Server live source schema

- JDBC catalog lookup now resolves `${DB_NAME}` (and similar) before `DatabaseMetaData.getColumns`. SQL Server treats catalog as a real database name and failed model check with `Database '${DB_NAME}' does not exist`.

### CSV satellite hash keys

- `DvHashKey` now hashes the compatible (unformatted) integer/number string, so file sources that apply catalog `Integer` length no longer produce padded keys such as ` 000001001` while the hub hashes `1001`
- Satellite catalog mappings include parent-key hash inputs, not only attributes

### EDW logo and in-GUI documentation

- Navy/cyan **EDW** wordmark (`edw-logo.svg`) in Hop’s logo palette
- Main toolbar button and **Help → EDW documentation** open the plugin-shipped `docs/index.html`

- Converted [docs/feature-overview.adoc](docs/feature-overview.adoc) so the plugin HTML set includes `feature-overview.html`

### Offline HTML docs and architecture SVGs

- Architecture pictures are committed SVGs under `docs/images/diagrams/`, generated from PlantUML (`docs/diagrams/`, Smetana layout)
- User-facing `.adoc` files no longer use Markdown fences or live Mermaid listings
- `mvn package` writes `target/generated-docs/` and includes `plugins/misc/datavault/docs/` in the plugin zip

### First-time EDW architecture and getting-started guide

- [docs/architecture.adoc](docs/architecture.adoc) — catalog as contract bus, resource definition group spine, build vs run, four controls
- [docs/getting-started-edw.adoc](docs/getting-started-edw.adoc) — build-from-scratch order: Configure EDW → `.hsm` → catalog → `.hdv` → resource definition group → Update RDG → same group for BV/DM
- [docs/resource-definition-group.adoc](docs/resource-definition-group.adoc) — first-class RDG page (was only implied by validation / update docs)
- Retail tutorial reframed as a **tour** of the finished sample; README Usage and plugin “Typical workflow” follow the golden path

### Shared model configuration metadata (issue #126)

- Four project metadata types: Source Model, Data Vault, Business Vault, and Dimensional configuration
- Models reference a named configuration (`configurationName`); older embedded `<configuration>` blocks still load
- Edit model dialogs pick a shared configuration or extract an embedded copy; settings live in the Metadata perspective
- New-project dialog offers a local catalog plus the four standard configuration elements
- **Tools → Configure EDW setup...** reopens that dialog after it was dismissed
- retail-example and integration-tests now use shared configuration objects instead of duplicating settings in every model file
- Guide: [docs/datavault-configuration.adoc](docs/datavault-configuration.adoc)

### Optional Data Vault orphan handling (issue #77)

- Model **Orphan handling** policy (`PASS` default, plus `INFER` / `SENTINEL` / `QUARANTINE` / `FAIL`) with link/satellite overrides and hub **Allow inferred inserts**
- **Also load parent hubs...** on link/satellite dialogs and optional source-to-vault seed so child feeds can populate parent hubs
- Generated load fragments for non-`PASS` policies; Data Vault Update creates the quarantine table when needed
- Model check does **not** treat a child/link source that is absent from the parent hub as an error (`PASS` is the usual hub-from-parent / link-from-child pattern)
- Guide: [docs/datavault-configuration.adoc](docs/datavault-configuration.adoc), plan [docs/plans/orphan-prevention-plan.md](docs/plans/orphan-prevention-plan.md)

### Generate Data Vault from a source model (issue #125)

- Source modeler **Generate Data Vault…** and vault modeler **Generate from source model…** classify selected `.hsm` tables (PKs + relationships) and add hubs, satellites, and links after a review screen
- Same-PK clusters become one hub; junction/transaction tables become links (optional dependent child keys and link satellites); leftover FKs become binary links
- Existing vault hubs are reused when business keys or catalog feeds already match; catalog publish is optional
- Queries, JSON extractions, and pipeline cards classify with the same grain rules; multi-FK feeds can emit one n-ary transactional link
- Lookup / code tables can become **reference tables**; self-FKs become a hub alias plus a hierarchy link
- Coach panel **Generate DV** and unmapped-source insight; AI Help includes sibling `.hsm` classification JSON
- Guide: [docs/generating-data-vault-from-source-model.adoc](docs/generating-data-vault-from-source-model.adoc)

### Hop Web lineage view (issue #79, plan PR 12)

- `.hlv` uses the same experimental Hop Web SVG canvas stack as `.hsm` / `.hdv` / `.hbv` / `.hdm` / `.hem` (not part of #119)
- Details sash and Markdown pane are constructed under RAP; settings dialog fields scroll so the wizard fits a browser viewport
- Empty / loading / error text is painted in the SVG snapshot, not only the status label
- Card context waits for an unmoved mouse-up and snapshots click coordinates so RAP does not reuse a request-scoped `Event` after facet follow-up
- Lineage cards are not user-draggable; Marquez / folder / OPS URLs resolve in the Hop Web server JVM

### Hop Lineage View show pipeline

- **Show update/build pipeline** uses the opened model graph (not the active tab) and starts generation after the context menu finishes, so the validation progress dialog is not cancelled by the tab switch
- Dimension aliases (for example `dm/retail-f-inventory/d_product`) follow the referenced dimension and open that update pipeline instead of staying on the consuming fact model

### Hop Lineage View details pane

- Right-hand node details are Markdown, rendered with the same viewer as dialog Help, plus **View as HTML**

### Hop Lineage View name hover

- Node names underline on mouse-over; clicking the name updates the details pane only. The context dialog opens from a click on the rest of the card, same as the other modelers

### Hop Lineage View card labels

- Long job/dataset names use the card width instead of a 22-character cap, and overflow keeps the tail (`…/retail-f-order-lines/d_product`)

### Hop Lineage View connectors

- Lineage edges clip to card borders and show a small filled square at each attachment so lines that pass under other cards stay readable

### Hop Lineage View docs and retail sample (issue #79, PR 8)

- User guide: [docs/hop-lineage-view.adoc](docs/hop-lineage-view.adoc) — export is not a load, `${MARQUEZ_BASE_URL}` vs `${MARQUEZ_API}`, backends, Show lineage, OPS overlay
- Retail sample view `retail-example/models/f_orders-upstream.hlv`; optional `./scripts/smoke-lineage-view.sh` (not in `mvn test` or the DB matrix)
- Environment variable `${MARQUEZ_BASE_URL}` (`http://localhost:5001`) alongside existing `${MARQUEZ_API}`

### Hop Lineage View file type (issue #79, PR 4)

- Authorable `.hlv` view definition (File → New wizard, Explorer tab, save, search)
- Canvas refresh queries the selected lineage backend in the background and lays out an upstream table graph
- Toolbar zoom, SVG export of the session graph, and a read-only details pane for the selected node
- Tab name follows the `.hlv` filename; Marquez seeds fall back to search when the stored namespace does not match export (`retail-job` / `retail-dataset`)

### Hop Lineage View navigation (issue #79, PR 5)

- Click a lineage node to open the Hop model/table, catalog record, or generated update/build pipeline when `hop_export` / `hop_location` are present
- Marquez nodes fetch missing facets in the background on select before the context menu is shown

### Hop Lineage View OPS overlay (issue #79, PR 6)

- Optional last-load badges on lineage cards from the Hop OPS database (`dv` / `bv` / `dm`), with a stale export-time `hop_ops` fallback
- Never treats Marquez `latestRun.durationMs` as load telemetry

### Hop Lineage View from model tables (issue #79, PR 7)

- **Show lineage** on DV / BV / DM table context menus opens an unsaved upstream `.hlv` tab for that table
- Uses the single enabled lineage backend, or asks when several are enabled; passes the open model as `extraSnapshots` for Local-models

### Hop Lineage View query SPI (issue #79, PR 2–3)

- Headless `ILineageQueryService` with graph DTOs and `LineageGraphOps` (direction clip, depth, hide-jobs, layer filter)
- Adapters: Marquez 0.50, export-folder JSON, and local model collectors (`extraSnapshots` override)
- `@HopMetadata` **Lineage backend** (`lineage-backend`) with Marquez / folder / local-models editor and Test connection
- `latestRun.durationMs` is never treated as load telemetry

### OpenLineage hop identity facets (issue #79, PR 1)

- `hop_export` now includes `projectKey`, `resourceGroup`, `catalogConnection`, `physicalTableName`, and `targetDatabase` so a future Hop Lineage View can deep-link to models and catalog records
- `hop_location` now includes `catalogKey` and `catalogConnection` on source datasets
- Export stamps `resourceGroup` and a fallback `catalogConnection` onto each lineage snapshot before mapping
- Design: [docs/plans/hop-lineage-view-plan.md](docs/plans/hop-lineage-view-plan.md)

### Catalog data type mappings on hub/link loads

- `apply data type mappings` no longer rewrites catalog columns that the generated source stream does not read (for example source `load_date` on a hub that only selects business keys)
- Source-indicator mappings use the vault alias (`record_source AS x_record_source`) so Select Values does not look up a physical name that left the stream
- Link loads map only hub keys / dependent child keys / the record-source alias — not satellite attributes on the same catalog feed (`stock_qty`, `quantity`, `order_status`)
- If the generated stream layout cannot be determined, mappings are skipped instead of applying the full catalog
- Select Values metadata is emitted only when a field actually needs length, precision, conversion, or rename — a hop type alone is not enough

### Date generator load timestamp

- Date Dimension Generator token `@now` / `@load_ts` emits the transform init timestamp (same value on every calendar row); empty mask on a `Timestamp` field is treated as `@now`
- **Add load timestamp** on the transform dialog and dimensional Source tab appends that field (`load_dt`, or the model's load timestamp column)
- Type 1 date-dimension loads auto-inject the model load timestamp when the generator field table does not already include it, so generated calendars satisfy the required source `x_load_ts` / `load_dt` mapping

### Hop Web table context dialog (#123)

- Left-click on a table or card in the Source / Data Vault / Business Vault / Dimensional modelers again opens the context dialog under **Hop Web**. Icon-drag was armed on mouse-down (RAP has no move-while-held), so mouse-up treated every click as a completed drag.

### Data type mappings (#113)

- New project metadata type **Data type mapping** (`data-type-mapping`): ordered rules with match criteria (Hop type, source SQL type pattern, field name pattern, length absent) and target type/length/conversion options
- Resolver with attribute-level multi-profile merge; per-source field overrides win last
- Design-time validation for dangerous conversions (e.g. String→Date/Timestamp without conversion mask), rename collisions, unknown overrides, PK type changes, length narrowing
- `Select Values` builder (`DataTypeMappingPipelineSupport`) for load-pipeline injection
- HSM entities (`SourceTable`, `SourceQuery`, `SourceJson`, `SourcePipeline`) store profile refs + field overrides
- Source table dialog **Data type mapping** tab: attach profiles, field overrides, effective-fields preview
- **Catalog publish** writes effective fields + conversion block + optional `sourceStreamName` (rename)
- **DV load pipelines** inject `apply data type mappings` Select Values from catalog layout after source read (hub/link/sat/reference)
- File source coerce also applies length when present; full conversion via catalog injection
- **Data type mapping** tab on source **query / JSON / pipeline** dialogs (shared with table)
- Profile field actions: **Add** / **Select** / **Edit** Data type mapping metadata from the tab
- Source model **default data type mappings** (Edit model) applied on schema import and new cards
- Docs: [data-type-mappings.adoc](docs/data-type-mappings.adoc); retail example profile `premodel-defaults`
- Plan: [docs/plans/data-type-mappings-plan.md](docs/plans/data-type-mappings-plan.md)

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

Requires **Apache Hop 2.19.0** and **Java 21**.

**Downloads:** [GitHub release zip](https://github.com/ProjectDataHopper/hopper-edw/releases/download/v0.8.0/hop-datavault-0.8.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.8.0/hop-datavault-0.8.0.zip) (`org.apache.hop:hop-datavault:0.8.0`)

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

Requires **Apache Hop 2.19.0** and **Java 21**.

**Downloads:** [GitHub release zip](https://github.com/ProjectDataHopper/hopper-edw/releases/download/v0.7.0/hop-datavault-0.7.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.7.0/hop-datavault-0.7.0.zip) (`org.apache.hop:hop-datavault:0.7.0`)

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
- Docs: [dv-hub.adoc](docs/dv-hub.adoc#composite-hub-business-keys), [dv-satellite.adoc](docs/dv-satellite.adoc), [dv-link.adoc](docs/dv-link.adoc), [datavault-configuration.adoc](docs/datavault-configuration.adoc), [feature-overview.adoc](docs/feature-overview.adoc); AI schema notes in [docs/ai-file-schemas/models/hdv.md](docs/ai-file-schemas/models/hdv.md)

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

Requires **Apache Hop 2.19.0** and **Java 21**.

Older Hop **2.18.x** (including 2.18.1) is **not** supported for 0.6.0.

**Downloads:** [GitHub release zip](https://github.com/ProjectDataHopper/hopper-edw/releases/download/v0.6.0/hop-datavault-0.6.0.zip) · [Data Hopper Nexus](https://repository.data-hopper.com/repository/hop-community-plugins/org/apache/hop/hop-datavault/0.6.0/hop-datavault-0.6.0.zip) (`org.apache.hop:hop-datavault:0.6.0`)

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

- Development and runtime target is **Apache Hop 2.19.0**
- Enables database-backed execution information (e.g. OPS `hop_executions`), BINARY hash key sorting ([apache/hop#7346](https://github.com/apache/hop/issues/7346)), and Hop Marketplace install
- Markdown notes on DV/BV/DM model canvases (Hop 2.19 note API)
- Apache snapshots repository `updatePolicy=always` for reliable CI resolution of Hop SNAPSHOTs

### Stability and tooling

- Canvas SVG icon cache / SWT handle-leak hardening (`ModelGraphSvgIconCache`; reflective `SwtGc#getNativeGc` for mixed hop-ui snapshots)
- FILE catalog mkdir when `PROJECT_HOME` is unresolved
- Docker test image freshness: rebuild `docker-hop:latest` when `target/hopper-edw-*.zip` is newer than the image

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
