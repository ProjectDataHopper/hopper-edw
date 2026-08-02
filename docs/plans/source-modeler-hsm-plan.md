# Plan: Hop Source Modeler (`.hsm`) — Issue #105

**Issue:** [Create a modeler for record sources](https://github.com/mattcasters/hop-data-vault/issues/105)  
**Goal:** A fourth visual modeler that captures source-system structure (tables, PK/FK, multi-table queries) so DV designers can efficiently feed hubs, links, and satellites—including the common case of joining small lookup/key-value tables into one satellite load.

---

## 1. Problem analysis

### 1.1 What customers (e.g. VaultSpeed users) already do

VaultSpeed-style source modelers treat the **source system as a first-class model**:

1. Import schema (tables, columns, primary keys, foreign keys).
2. Draw / refine an ER-style source graph.
3. Define **source queries** that join several physical tables (lookups, reference codes, sparse attributes).
4. Map those composed feeds onto hubs / links / satellites.
5. Generate efficient SQL (or ETL) to materialize the feed for the vault load.

The pain this solves is real in retail/CRM sources: product master + product type lookup, customer + address type codes, order line + status dictionary. Today each physical table tends to become its own `DV_SOURCE` and often its own satellite—even when a single satellite with a join would be cleaner and cheaper.

### 1.2 What hop-data-vault does today

| Layer | Capability | Gap for multi-table sources |
|-------|------------|------------------------------|
| **Catalog `DV_SOURCE`** | One logical feed → one physical table/file (DB, CSV, Parquet, Iceberg) | No join graph; no multi-table projection |
| **PK on fields** | `SourceField.primaryKeyPosition` + JDBC PK discovery | FK **not** discovered or stored on sources |
| **`ForeignKeySpec`** | Only for **target** DV/BV/DM DDL | Not source-side relationships |
| **Hub/Link/Sat pipeline builders** | Generate `SELECT … FROM single_table` via `DvDatabase*SourcePipelineBuilder` | `appendFrom` is always one schema.table |
| **DM staging SQL** | Free-form `DmSourceConfiguration.sourceSql` | Manual SQL, not visual; not reusable for DV |
| **Modelers** | `.hdv` / `.hbv` / `.hdm` + shared `HopGuiModelGraphBase` | No source ER modeler |
| **Coach / mapping plans** | Source→vault UX patterns (workbench, coverage matrix) | Assume 1:1 catalog feeds; no join designer |

Architecture today:

```
Catalog DV_SOURCE (1 table/file each)
        │
        ▼
  Raw Data Vault (.hdv)  → generated single-table SQL
        ▼
  Business Vault (.hbv) → Dimensional (.hdm)
```

Desired architecture:

```
Source Model (.hsm)  ──import PK/FK──► tables + relationships
        │
        │  Source queries (joins / projections)
        ▼
Catalog DV_SOURCE (single-table OR composed feed)
        │
        ▼
  Raw Data Vault (.hdv)  → SQL Table Input OR Merge-Join pipeline
        ▼
  BV / DM (unchanged)
```

### 1.3 Issue #105 requirements (must-haves)

1. **`.hsm` file type** with full GUI parity to the other three modelers (canvas, dialogs, undo/clipboard, ELK, notes, open/save via Hop VFS).
2. **Importer** using **PK and FK** JDBC schema metadata to seed the model automatically.
3. **Query composition**: select a driving table + related tables (e.g. key/value lookups) into one logical feed for a satellite (or hub/link).
4. **Generation strategy**:
   - All participants are DB tables on the **same connection** → generate SQL for a **Table Input** transform.
   - Mixed sources (files + tables, or multi-connection) → generate a **pipeline** with **Merge Join** (and sorts as needed).
5. **Cool query builder** that also integrates into the **Data Vault modeler** (not a dead-end file-only feature).

### 1.4 Non-goals (v1)

- Replacing the Data Catalog or removing single-table `DV_SOURCE`.
- Auto-generating a complete `.hdv` from the source ER (AI/coach can help later; not required for #105).
- Reverse-engineering undocumented FKs with ML (name-heuristic assist is optional later).
- CDC graph modeling / change-data capture orchestration (existing delivery types stay on the published feed).
- Shipping only API/config with no Hop GUI surface (project rule: GUI parity).

---

## 2. Product design

### 2.1 Mental model

An `.hsm` file is the **source-system map** for one subject area (or one source system):

| Concept | Canvas role | Purpose |
|---------|-------------|---------|
| **Source table** | Node / card | Physical entity (DB table, later file) with columns, PK, optional catalog link |
| **Relationship** | Edge | FK-derived or manual join (child→parent columns, join type, cardinality) |
| **Source query** | Named logical feed (node or dedicated list + highlight) | Multi-table projection that becomes a loadable feed for DV |
| **Note** | Sticky | Same note pads as other modelers |

**Source query** is the product’s “dream” object: “satellite *sat_product* should read `product ⟕ product_type` with these columns.”

### 2.2 Relationship to the catalog

**Recommended contract (keeps organisational memory clean):**

1. **Physical tables** in `.hsm` can:
   - **Link** to an existing catalog `DV_SOURCE`, or
   - Be **imported** and optionally **published/updated** into the catalog as single-table `DV_SOURCE`s (same path as today’s “Import sources”).
2. **Source queries** publish as catalog feeds with a **new source kind** (see §3.3), e.g. `COMPOSITE` / `SOURCE_QUERY`, so hubs/links/sats keep binding by **name**—no special-casing in dialogs beyond choosing the feed.
3. FK graph lives primarily in **`.hsm`** (versioned with the model). Optional catalog enrichment (FK metadata on fields) is a follow-up for reverse lineage / impact, not a v1 blocker.

This mirrors how BV references DV and DM can stage from SQL/pipeline/catalog without polluting the vault ontology.

### 2.3 Query builder UX (two entry points)

**A. Inside `.hsm` (primary)**  
Toolbar / context: **New source query** → pick driving table → add related tables via existing edges (or define ad-hoc joins) → project columns → preview → save → **Publish to catalog**.

**B. Inside `.hdv` (integration)**  
From Hub / Link / Satellite dialog (Record sources / default source): **Compose multi-table source…** opens the same query-builder dialog scoped to a chosen `.hsm` (or creates one). On OK: writes/updates the query in the `.hsm`, publishes feed, binds the vault table to the new feed name.

Shared dialog class so power is not file-only (GUI parity rule).

### 2.4 Generation decision tree

```
SourceQuery.resolveParticipants()
  │
  ├─ all DATABASE, same connection, no file/iceberg
  │     → SqlGenerationMode.SINGLE_QUERY
  │     → Table Input with generated SELECT … JOIN …
  │
  └─ otherwise (mixed types, multi-connection, or force-pipeline flag)
        → PipelineGenerationMode.MERGE_JOIN
        → pipeline: inputs → (Sort) → Merge Join chain → Select Values → output
```

Preview uses the same path (limited rows). Check-model validates join completeness, column uniqueness, and that every projected field resolves.

### 2.5 Example (customer dream case)

Source tables:

- `product` (PK `product_id`, FK `type_id` → `product_type`)
- `product_type` (PK `type_id`, attrs `type_code`, `type_name`)

Source query `feed_product_enriched`:

```sql
SELECT p.product_id, p.sku, p.name, t.type_code, t.type_name, …
FROM product p
LEFT JOIN product_type t ON p.type_id = t.type_id
```

Satellite `sat_product` binds to catalog feed `feed_product_enriched` and loads **one** satellite instead of two.

---

## 3. Technical design

### 3.1 Package layout (mirror existing modelers)

```
org.apache.hop.datavault.metadata.sourcemodel/
  SourceModel.java
  SourceModelConfiguration.java
  SourceTable.java                 # canvas node
  SourceColumn.java                # field + PK position + types
  SourceRelationship.java          # edge + join columns
  SourceJoinType.java              # INNER, LEFT, …
  SourceQuery.java                 # composed feed
  SourceQueryJoin.java             # join step in a query
  SourceQueryColumn.java           # projected column + alias
  SourceQueryGenerationMode.java   # AUTO | SQL | PIPELINE
  SourceModelLoadSupport.java
  SourceModelCheckSupport.java
  import/
    DatabaseSchemaImportSupport.java   # tables + PK + FK
    DatabaseForeignKeyDiscoverySupport.java
    ImportSourceSchemaOptionsDialog.java
  generate/
    SourceQuerySqlGenerator.java
    SourceQueryPipelineGenerator.java
    SourceQueryPreviewSupport.java
  publish/
    SourceQueryCatalogPublisher.java   # → DV_SOURCE COMPOSITE
  pipeline/  (if needed by generators)

org.apache.hop.datavault.hopgui.file.sourcemodel/
  HopSourceModelFileType.java          # .hsm
  HopGuiSourceModelGraph.java
  SourceModelPainter.java
  SourceModelSvgPainter.java
  HopGuiSourceModelDialog.java
  HopGuiSourceTableDialog.java
  HopGuiSourceRelationshipDialog.java
  HopGuiSourceQueryDialog.java         # query builder
  HopGuiSourceQueryBuilderDialog.java  # reusable from DV
  delegates/ (clipboard, undo)
  SourceElkLayout.java (or under layout/)

org.apache.hop.datavault.metadata/
  (extend) DvSourceType + IDvSource factory for COMPOSITE
  (extend) hub/link/sat pipeline builders to consume composite sources
```

Resources:

- `src/main/resources/source-model.svg` (file icon)
- `messages/messages_en_US.properties` under new packages
- Help markdown under `hopgui/help/` for dialogs

### 3.2 Core metadata (sketch)

```java
// SourceModel — HopMetadataBase + IChanged + IHasFilename + IUndo (like DataVaultModel)
@HopMetadataProperty String description;
@HopMetadataProperty SourceModelConfiguration configuration;
@HopMetadataProperty List<SourceTable> tables;
@HopMetadataProperty List<SourceRelationship> relationships;
@HopMetadataProperty List<SourceQuery> queries;
@HopMetadataProperty List<DvNote> notes;

// SourceTable
String name;                 // logical name on canvas
Point location;
String catalogSourceName;    // optional link to DV_SOURCE
String databaseName, schemaName, tableName; // physical when DB
DvSourceType physicalType;   // DATABASE | CSV | … (v1: DATABASE first)
List<SourceColumn> columns;
// SourceColumn: name, hopType, length, precision, primaryKeyPosition, description

// SourceRelationship
String name;
String childTableName, parentTableName;
List<String> childColumns, parentColumns; // parallel lists
SourceJoinType defaultJoinType; // LEFT for lookups
String cardinality; // optional display

// SourceQuery
String name;                 // becomes catalog feed name when published
String description;
String drivingTableName;
List<SourceQueryJoin> joins; // ordered: table + relationship ref or explicit columns + join type
List<SourceQueryColumn> columns; // table.column → alias
String whereClause;          // optional, advanced
SourceQueryGenerationMode generationMode; // AUTO default
String publishedCatalogName; // last publish target
```

Serialization: XML via `XmlMetadataUtil` + `ModelXmlWriteSupport`, file tag e.g. `source-model`, extension **`.hsm`**. Load/save exclusively through **HopVfs** (project rule).

### 3.3 New composite source type (DV integration)

Extend `DvSourceType` with e.g. **`COMPOSITE`** (or `SOURCE_QUERY`):

```java
public class DvCompositeSource extends DvSourceBase {
  String sourceModelFilename;  // ${PROJECT_HOME}/models/crm.hsm
  String sourceQueryName;      // feed_product_enriched
  // optional denormalized cache:
  String generatedSql;
  String sourcePipelineFile;   // if forced pipeline
  List<SourceField> fields;    // projected layout (required for mapping)
}
```

**Pipeline builder change (critical path):**

- `DvSourcePipelineBuilderFactory` / hub-link-sat builders:
  - If `DATABASE` → existing path.
  - If `COMPOSITE` + SQL mode → Table Input with `SourceQuerySqlGenerator` SQL (or stored SQL).
  - If `COMPOSITE` + pipeline mode → inject / MetaInject / copy steps from generated query pipeline (prefer: generate SQL/pipeline at **DV Update** time from live `.hsm`, fall back to cached SQL if model missing—with check-model warning).

Prefer **resolve-from-`.hsm` at generation time** so query edits do not require re-publish of SQL text; catalog stores identity + field layout + pointers.

### 3.4 Schema importer (PK/FK)

Build on:

- `DatabasePrimaryKeyDiscoverySupport` (exists)
- New `DatabaseForeignKeyDiscoverySupport` using JDBC `DatabaseMetaData.getImportedKeys` (and optionally `getExportedKeys` for UI)

Import flow (GUI dialog, like `ImportDatabaseTablesOptionsDialog`):

1. Choose connection + schema filter + table multi-select.
2. For each table: columns, types, PK positions.
3. For each table: imported FKs → `SourceRelationship` edges (dedupe).
4. Auto-layout via ELK.
5. Optional: **also create/update catalog `DV_SOURCE`** for each table (checkbox; default on for greenfield).

When DB has no FKs (common): still import tables + PKs; user draws relationships manually. Optional later: name-heuristic FK suggestions (`*_id` → parent PK).

### 3.5 GUI shell (parity checklist)

Reuse `HopGuiModelGraphBase` patterns from vault/dimensional/business vault:

| Capability | Pattern to copy |
|------------|-----------------|
| File type plugin | `HopVaultFileType` / `HopDimensionalFileType` |
| Graph + toolbar | `HopGuiVaultGraph` (slimmed) |
| Cards + edges | Table cards with column list, PK badges, FK edge labels |
| Dialogs | Per-object dialogs + model settings dialog |
| Undo / clipboard | Snapshot undo + paste support |
| ELK layout | `VaultElkLayout` analogue |
| Notes | `DvNote` + existing note pad support |
| Check model | Issues list → jump to object |
| SVG export | Painter sibling (optional phase 2 if heavy) |
| Coach panel | Register sources from `.hsm` queries as coaching sources (phase 5) |

Toolbar (minimum):

- Edit model
- Import schema (PK/FK)
- New table / New relationship / New source query
- Check model
- Publish queries to catalog
- Preview query
- Layout (ELK)
- Help

### 3.6 SQL generator (same connection)

Responsibilities of `SourceQuerySqlGenerator`:

- Qualify tables with schema; dialect-safe quoting via `DatabaseMeta`.
- Aliases for each table instance (`p`, `t` or `product`, `product_type`).
- JOIN clauses from `SourceQueryJoin` / relationships.
- SELECT list with aliases; detect duplicate output names.
- Optional WHERE (validated as non-executable string store only; preview executes).
- No `SELECT *` in published feeds (explicit projections for stable vault mappings).

### 3.7 Pipeline generator (mixed / multi-input)

`SourceQueryPipelineGenerator`:

- One input transform per leaf physical source (Table Input / CSV / Parquet / Iceberg using existing builders).
- Sort on join keys when Merge Join requires sorted input.
- Chain Merge Joins left-to-right in join order.
- Select Values / rename to projected aliases.
- Persist under model work folder similar to generated DV pipelines (e.g. `work/generated/source-queries/…`).

### 3.8 Validation (`Check model`)

- Orphan relationships (missing table).
- Join column count/type mismatches (when types known).
- Source query: missing driving table, incomplete joins, empty projection.
- Name uniqueness (tables, queries).
- Published feed field drift vs query projection.
- Referenced catalog sources missing.
- Generation mode AUTO resolves; warn if forced SQL but participants not all same DB.

### 3.9 Lineage, impact, execution maps (incremental)

| Area | v1 | Later |
|------|----|-------|
| Source→DV lineage | Composite source fields contribute like single-table | Edge-level table.column → sat.attr with join reason codes |
| Impact graph | Query name as dataset | Expand to physical tables |
| Execution maps | Treat composite feed as source dataset | Nested expand into join pipeline |
| Architecture export | Optional SOURCE layer | Full ER export |

Do not block #105 on full lineage parity; design metadata so collectors can hang off `SourceQuery` later.

### 3.10 Testing strategy

| Level | What |
|-------|------|
| Unit | Serialize/deserialize `.hsm`; FK discovery mocks; SQL generator golden strings (Postgres quote style); check-model rules; composite source factory |
| Integration | Postgres: import schema with FKs from a small fixture schema; generate SQL; load one satellite via composed feed; golden row counts |
| Multi-DB | SQL dialect quoting in generator when join SQL is involved—**full matrix before calling SQL generation “done”** (project rule) |
| Retail | Optional `models/retail-crm.hsm` demonstrating product+type lookup → one sat |

---

## 4. Implementation phases (PR plan)

Phased so each PR is shippable and reviewable. Order respects dependencies.

### PR1 — Foundation: metadata + `.hsm` file type (vertical thin slice)

**Deliverables**

- `SourceModel` + nested types (tables, relationships, queries—minimal fields).
- `HopSourceModelFileType` (`.hsm`), load/save via HopVfs + XmlMetadataUtil.
- Minimal graph: open empty model, add/move table nodes, save, reopen.
- Icons + i18n stubs.
- Unit tests: XML round-trip.

**Exit criteria:** Create/open/save `.hsm` in Hop GUI without crashes.

### PR2 — Schema importer (PK + FK) ✅

**Deliverables**

- `DatabaseForeignKeyDiscoverySupport` + `DiscoveredForeignKey`.
- `DatabaseSchemaImportSupport` / `ImportSourceSchemaOptionsDialog` / `HopGuiSourceModelImportSupport`.
- Toolbar + canvas context **Import schema**; grid auto-layout; optional catalog publish.
- Unit tests for FK model helpers and import pure functions.

**Exit criteria:** Import a DB schema with FKs → visible ER edges on canvas.

### PR3 — Full canvas GUI parity ✅

**Deliverables**

- Table / relationship / model dialogs.
- Edge drawing & hit-testing; column PK badges on cards.
- Undo, clipboard, notes, ELK layout, check model (structure only).
- Left-click context menus; relationship drag (middle / shift+left).

**Exit criteria:** Manual modeling feels like `.hdv`/`.hdm` for structure (no generation yet).

### PR4 — Query builder + SQL/pipeline generation + preview

**Deliverables**

- Source query dialog (visual join selection + column projection + WHERE).
- `SourceQuerySqlGenerator` + `SourceQueryPipelineGenerator` + preview.
- Check-model rules for queries.
- Unit/integration tests for SQL generation.

**Exit criteria:** Define multi-table query, preview rows, inspect generated SQL/pipeline.

### PR5 — Catalog publish + Data Vault integration

**Deliverables**

- `DvSourceType.COMPOSITE` + `DvCompositeSource` + factory.
- `SourceQueryCatalogPublisher` (fields + pointer to `.hsm` query).
- Hub/Link/Satellite pipeline builders consume composite sources.
- **Query builder entry** from DV hub/sat/link dialogs.
- Model check: resolve composite sources; field mapping validation.
- Update actions generate correct loads end-to-end.

**Exit criteria:** Satellite load uses composed feed (join) without hand-written SQL in the vault model.

### PR6 — Sample, docs, hardening — **done**

**Deliverables**

- Retail fixture `.hsm` (`retail-example/models/source-tables-crm.hsm`) with multi-table query **All customer info** → publish name `feed_customer_enriched`.
- Docs: `docs/source-modeler-overview.adoc`, feature-overview row, getting-started Chapter 2b, catalog/source/architecture cross-links.
- Unit tests: dialect quoting (`SourceQuerySqlGeneratorQuotingTest`) + retail fixture load/SQL (`RetailSourceModelFixtureTest`).
- Optional/thin: architecture-export docs note composite feeds in DATA inventory; coach still uses published catalog feeds as `RECORD_DEFINITION` (no separate SOURCE_QUERY coach type yet).

**Exit criteria:** Tutorial path and CI cover the dream scenario; issue #105 closable.

---

## 5. Alignment with existing plans & principles

| Principle / plan | How this plan respects it |
|------------------|---------------------------|
| GUI parity | Full `.hsm` modeler + query builder also from DV dialogs |
| Lombok / HopVfs / i18n | Same as other modelers |
| Catalog-first sources | Composed feeds publish as named `DV_SOURCE`; vault tables still bind by name |
| Clean vault ontology | Joins live in `.hsm`, not cluttered into hub/sat definitions |
| [source-to-data-vault-mapping-plan.md](docs/plans/source-to-data-vault-mapping-plan.md) | Source modeler supplies better multi-table feeds; mapping workbench remains complementary (field→vault), not replaced |
| Coach panel | Later phase can drop source queries into the coach tree as curated sources |
| No file-only features | Generation and import always have GUI surfaces |

---

## 6. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Scope explosion (second full modeler ~3k+ graph LOC) | Strict PR phases; ship import+ER before query generation |
| SQL dialect differences | Centralize quoting on `DatabaseMeta`; multi-DB integration tests before “done” |
| Stale published feeds | Prefer resolve-from-`.hsm` at pipeline generation; check-model field drift warnings |
| Cycles / multi-path FKs in join UI | Require explicit join path in `SourceQueryJoin` list; no automatic shortest-path magic in v1 |
| Mixed file+DB performance | Document Merge Join cost; allow force-SQL only when valid; optional materialization later |
| Naming collision with catalog | Query publish dialog validates uniqueness in namespace |

---

## 7. Recommended defaults (open decisions locked for implementation)

Unless you override these:

1. **Extension:** `.hsm` — Hop Source Model.
2. **v1 physical type on canvas:** DATABASE tables; file nodes can be stubbed later (pipeline path already designed).
3. **Composite source type name:** `COMPOSITE` with pointer `(sourceModelFilename, sourceQueryName)`.
4. **Default join type for imported FKs:** `LEFT` (lookup-friendly); user can switch to `INNER`.
5. **Catalog publish of single tables on import:** default **on** for new projects; off when tables already exist in catalog (match by connection+schema+table).
6. **Generation at DV Update:** re-resolve from `.hsm` when file present; else use last published SQL/fields.
7. **No auto-DV generation from ER in v1** — only feed composition + bind; coach/AI remains separate.

---

## 8. Effort sketch (order of magnitude)

| Phase | Rough effort |
|-------|----------------|
| PR1 Foundation | S–M |
| PR2 Importer PK/FK | M |
| PR3 Full GUI | L (largest GUI slice) |
| PR4 Query builder + generators | L |
| PR5 DV integration | M–L |
| PR6 Docs/samples/CI | S–M |

Comparable in total work to standing up **dimensional modeler** (metadata + graph + generators + update path), not a weekend feature.

---

## 9. Success criteria for “dream #105”

1. User imports a source schema with PKs/FKs into an `.hsm` and sees an ER graph in Hop GUI.
2. User builds a source query joining a fact-like table with a key/value lookup.
3. That query publishes as a catalog feed and binds to a satellite (or hub/link).
4. **Data Vault Update** loads the satellite via generated **multi-table SQL** (same DB) without hand-maintained SQL in the vault model.
5. Mixed source types generate a **Merge Join pipeline** instead of invalid SQL.
6. The same query builder is reachable from the DV modeler dialogs.
7. Unit + at least Postgres integration coverage; SQL quoting verified on the multi-DB matrix before release notes claim multi-engine support.

---

## 10. Suggested first implementation step

Start **PR1** immediately after plan approval: scaffold `metadata.sourcemodel` + `HopSourceModelFileType` + empty graph, with a failing unit test for XML round-trip of a two-table one-FK model. That freezes the serialization contract before GUI bulk lands.
