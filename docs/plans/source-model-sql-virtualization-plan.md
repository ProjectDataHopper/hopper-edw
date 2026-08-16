# Plan: SQL-driven data virtualization over Source Models (Issue #117)

**Issue:** [#117 — SQL parser for Hop source models](https://github.com/mattcasters/hop-data-vault/issues/117)  
**Related:** [#115 — Free Query source](https://github.com/mattcasters/hop-data-vault/issues/115) (natural first GUI consumer)  
**Historical parallel:** Pentaho / Kettle JDBC driver (query any transform step for Mondrian / Metadata) — incomplete “data virtualisation”; this plan aims for a **relationship-aware**, **pushdown-capable** virtualisation layer on `.hsm`.

---

## 1. Problem and product intent

### 1.1 What exists today

The source modeler (`.hsm`) already captures:

| Asset | Role |
|-------|------|
| **Source tables** | Physical entities (DB tables primarily; catalog types also include CSV/Parquet/Iceberg) + columns, PK |
| **Relationships** | PK/FK (or manual) edges with join type + crow’s-foot multiplicity |
| **Source queries** | Visual multi-table projection → **SQL** (`Table Input`) or **Merge Join pipeline** |
| **Source JSON / pipeline** | Nested extraction and MetaInject contracts |
| **Catalog publish** | `COMPOSITE` / `JSON` / `PIPELINE` / `DATABASE` feeds for DV loads |

Generation path (load-time and preview):

```
SourceQuery ──► SourceQuerySqlGenerator      (same connection)
            └──► SourceQueryPipelineGenerator (Sort + MergeJoin + SelectValues)
```

That is **static** composition: joins and projection are designed in the query builder. There is **no** general SQL surface that says:

```sql
SELECT c.customer_id, a.city, o.order_date
FROM customer c
JOIN address a ON …
JOIN order_header o ON …
WHERE o.order_date >= DATE '2024-01-01'
ORDER BY o.order_date
LIMIT 100
```

…and gets an executable Hop pipeline with **as much work as possible pushed to the underlying engines**.

### 1.2 Issue #117 requirements (must)

1. **Apache Calcite** for SQL parse / validation / planning.
2. After planning, **dynamically generate** a Hop pipeline whose sources match referenced `.hsm` objects.
3. **Push down** filters, sorts, projections, and group-bys to the database **where safe**.
4. When join/filter/sort cannot run in one DB (mixed connections, JSON, files, pipelines), fall back to **pipeline operators** (Merge Join, Sort, Filter, Group By, Select Values).
5. **Limit** functions/expressions in early iterations (explicit allow-list).

### 1.3 Broader virtualisation vision (this plan’s north star)

Treat a **Source Model as a virtual schema**:

```
External SQL / Free Query / BI tool
        │
        ▼
  Virtual SQL layer (Calcite + Hop)
        │  schema = .hsm tables, queries, JSON, pipelines
        │  PK/FK metadata informs join defaults & cost later
        ▼
  Optimized RelNode plan
        │
   ┌────┴────┐
   │         │
 DB pushdown  Residual Hop graph
 (Table Input  (MergeJoin, Sort,
  + dialect     Filter, GroupBy, …)
  SQL)
        │
        ▼
  PipelineMeta  ──execute──► rows
```

Compared to the old Kettle JDBC driver:

| Old Kettle JDBC | This design |
|-----------------|-------------|
| Schema = transformation steps | Schema = **`.hsm` entities** (tables + composed feeds) |
| Little relationship knowledge | **PK/FK graph** available for join inference, validation, coach |
| Little pushdown | **Calcite + dialect RelToSql** per connection |
| GUI = pick a step | GUI = free SQL on source model + optional transform + later JDBC |

### 1.4 Non-goals (v1–v2)

- Full ANSI / dialect SQL parity or arbitrary UDFs.
- Replacing Business Vault SQL views (`.hbv` materialisation) — different layer.
- Auto-generating entire DV models from free SQL.
- Shipping **only** a library/API with no Hop GUI surface (project rule).
- Day-one Mondrian/Avatica production BI deployment (Phase D optional stretch).
- Inventing a new file extension; stay on `.hsm` + catalog.

### 1.5 Relationship to #115 (Free Query)

#115 asks for free-form SQL in the Source Query dialog for special cases. **Implement #115 as the first product surface of #117**, not as a parallel string-passthrough that bypasses planning:

- Free SQL is **parsed and validated** against the model.
- Generation uses the **same** Calcite → pipeline engine as ad-hoc SQL.
- Visual join builder remains for the common case; free SQL is an alternate **generation mode** on `SourceQuery`.

---

## 2. Architecture

### 2.1 New package layout

Prefer a dedicated virtualisation package (not further bloating `sourcemodel.generate`):

```
org.apache.hop.datavault.virtualization/
  sql/
    SourceModelSqlEngine.java          # parse → plan → generate entry
    SourceModelSqlOptions.java         # limits, row limit, dialect prefs
    SourceModelSqlException.java
    SupportedSqlFeatures.java          # allow-list documentation + checks
  calcite/
    SourceModelSchema.java             # Calcite Schema
    SourceModelSchemaFactory.java      # optional model.json / JDBC later
    SourceModelTable.java              # AbstractTable / TranslatableTable
    SourceModelViewTable.java          # optional: named SourceQuery as view
    HopTypeSystem.java                 # hopType ↔ RelDataType
    SourceModelSqlParser.java          # SqlParser config (quoting, casing)
  plan/
    SourceModelRelPlanner.java         # FrameworkConfig, Hep/Volcano programs
    PushdownClassifier.java            # same-connection components
    PushdownFragment.java              # tables + residual Rel subtree
  generate/
    RelToPipelineGenerator.java        # RelNode → PipelineMeta
    TableScanSourceFactory.java        # TableInput / file / JSON / MetaInject
    ResidualOperatorFactory.java       # Filter, Sort, Join, Project, Aggregate, Limit
    DialectSqlSupport.java             # DatabaseMeta → Calcite SqlDialect + RelToSql
  execute/
    SourceModelSqlExecutor.java        # local engine run for preview
  jdbc/                                # Phase D only
    HopSourceModelJdbcDriver.java
    …
```

GUI / product surfaces:

```
hopgui/file/sourcemodel/
  HopGuiSourceQueryDialog.java         # Free SQL tab/mode
  HopGuiSourceModelSqlDialog.java      # model-level “SQL query…” (optional v1)

transform/sourcemodelsql/              # Phase C
  SourceModelSqlMeta / Data / Dialog

metadata/sourcemodel/
  SourceQueryGenerationMode            # add FREE_SQL (or FREE)
```

### 2.2 Calcite dependency

| Item | Choice |
|------|--------|
| Artifact | `org.apache.calcite:calcite-core` (+ `calcite-linq4j` transitive) |
| License | Apache-2.0 (compatible) |
| Packaging | Bundle in plugin zip via `assembly.xml` (same pattern as ELK / CommonMark / Iceberg) |
| Scope | **compile + runtime** inside the plugin classloader (Hop does not ship Calcite today) |
| Version pin | Explicit property in `pom.xml` (e.g. recent 1.3x stable); do not float |

Watch classloader conflicts (Guava, Janino, Avatica). Prefer Calcite’s shaded usage patterns and verify Hop GUI / unit tests after packaging.

### 2.3 Schema mapping (`.hsm` → Calcite)

**Default schema name:** model name (or `source`).

| `.hsm` object | Calcite object | Scan implementation |
|---------------|----------------|---------------------|
| `SourceTable` (DATABASE) | Table | `TableInput` with `SELECT col… FROM schema.table` (+ pushed predicates later) |
| `SourceTable` (CSV/PARQUET/ICEBERG when wired) | Table | Existing Hop file/Iceberg input transforms |
| `SourceQuery` (published/named) | **View** (optional) | Expand to underlying plan or inject generated composite pipeline |
| `SourceJson` | Table (virtual) | Reuse `SourceJsonPipelineGenerator` subgraph |
| `SourcePipeline` | Table (virtual) | MetaInject / pipeline executor subgraph |

**Identifiers:**

- Prefer **canvas / logical names** as SQL table names (`customer`, `feed_customer_enriched`).
- Physical `schema.table` remains inside generators, not necessarily in user SQL (unless we expose dual names later).
- Case policy: Calcite unquoted identifiers → uppercase by default; configure **unquoted matching case-insensitive** against model names for Hop UX.

**Column types:** `SourceColumn.hopType` + length/precision → Calcite `RelDataType` via `HopTypeSystem`.

**Relationships:** not required for Calcite parse, but used for:

- Validation hints (“join has no FK edge”).
- Future cost model / join order suggestions.
- Optional natural-join / USING sugar (later).

### 2.4 Planning pipeline

```
SQL string
  → SqlParser (Calcite, limited conformance)
  → validate against SourceModelSchema
  → SqlToRelConverter → RelNode
  → HepProgram:
       - Project/Filter pushdown (Calcite standard rules)
       - Join commute/associate (subset)
       - Custom: mark JDBC-pushable subtrees
  → PushdownClassifier
  → RelToPipelineGenerator
```

**Supported SQL (Phase A allow-list):**

| Feature | Phase A | Phase B |
|---------|---------|---------|
| `SELECT` column list / `*` | yes | |
| Table aliases | yes | |
| `INNER` / `LEFT` join | yes | `RIGHT` / `FULL` (pipeline only) |
| `WHERE` (comparisons, `AND`/`OR`, `IS NULL`, `IN` list, `BETWEEN`) | yes | |
| `ORDER BY` | yes | |
| `LIMIT` / `FETCH` | yes | |
| `GROUP BY` + `COUNT/SUM/MIN/MAX/AVG` | no | yes |
| Scalar expressions / `CASE` | very limited or reject | Calculator subset |
| Subqueries / CTEs | no | maybe non-correlated |
| Window functions | no | later |
| DML | never (read-only virtualisation) | |

Reject with clear messages listing supported features (coach-friendly).

### 2.5 Pushdown classifier (core design)

**Goal:** maximise work in source engines without incorrect semantics.

1. **Partition** `TableScan` nodes by **capability key**:
   - DATABASE: `(connectionName)` — same Hop `DatabaseMeta` name.
   - Non-DB: each scan is its own fragment (or same file connection if we later group).
2. For each same-connection **DATABASE** component, collect the largest Rel subtree that:
   - Only references tables in that component,
   - Only uses operators the dialect can express (project, filter, join, sort, aggregate, limit),
   - Does not depend on residual columns from other fragments.
3. Translate that subtree with **Calcite `RelToSqlConverter`** + dialect mapped from Hop `DatabaseMeta` (Postgres, MySQL, MSSQL, …). Emit one **`TableInput`** transform with that SQL.
4. **Residual** operators (cross-connection join, JSON join, unsupported functions) become Hop transforms:
   - Join → `SortRows` on keys + `MergeJoin` (reuse patterns from `SourceQueryPipelineGenerator`)
   - Filter → `FilterRows` (limited conditions) or Calculator + Filter
   - Project → `SelectValues` (+ Calculator when needed)
   - Sort / Limit → `SortRows` / `MemoryGroupBy` or sample-limit pattern already used in previews
   - Aggregate → `GroupBy`

**Important:** when the **entire** query is single-connection and fully pushable, output is a **one-transform** pipeline (Table Input only) — same quality as today’s SQL mode, but from free SQL.

**Dialect mapping:** start with a small table Hop plugin id → Calcite `SqlDialect` (Postgres, MySQL, MSSQL, H2, generic ANSI). Prefer pushdown that is **correct** over aggressive; fall back to residual when unsure.

### 2.6 Generation output contract

```java
public record SourceModelSqlPlan(
    PipelineMeta pipelineMeta,
    String outputTransformName,
    IRowMeta outputRowMeta,
    List<String> pushdownSqlFragments,  // for Explain / dialog
    List<String> residualOperators,     // human-readable
    List<String> warnings)
```

Consumers:

| Consumer | Use |
|----------|-----|
| Source Query dialog (FREE_SQL) | Generate / Preview / Explain |
| Model-level SQL dialog | Ad-hoc exploration |
| Catalog composite / free SQL feed | DV Update injects pipeline |
| `SourceModelSql` transform (Phase C) | Runtime in arbitrary pipelines |
| JDBC (Phase D) | Execute via local engine, stream rows |

Reuse existing preview patterns (`LocalPipelineEngine`, `ShowRowsDialog` / `PipelinePreviewProgressDialog`).

### 2.7 Integration with existing generators

Do **not** delete `SourceQuerySqlGenerator` / `SourceQueryPipelineGenerator` in Phase A.

| Mode | Path |
|------|------|
| Visual query + AUTO/SQL/PIPELINE | Keep current generators (battle-tested for DV loads) |
| FREE_SQL mode | New Calcite engine |
| Long-term | Optionally reimplement visual query as “structured Rel builder → same generator” for one code path |

Migration strategy: after FREE_SQL is proven, consider translating visual `SourceQuery` into a Rel tree so pushdown rules are shared — **Phase B+**, not a rewrite of load pipelines on day one.

---

## 3. Product / GUI surfaces (mandatory parity)

### 3.1 Phase A — Free SQL on Source Query (closes #115 + core #117)

In `HopGuiSourceQueryDialog`:

1. Generation mode: **Automatic | SQL | Pipeline | Free SQL**.
2. Free SQL tab/editor:
   - Multi-line SQL (existing SQL styling if available).
   - Tables list helper (model participants).
   - **Validate** (Calcite parse + schema check).
   - **Explain** (pushdown SQL fragments + residual operator list).
   - **Generate pipeline** (optional open in Hop GUI).
   - **Preview data** (row limit) — works for mixed sources, not SQL-only.
3. Persist SQL text on `SourceQuery` (`freeSql` metadata property).
4. Publish to catalog still as **COMPOSITE** (or keep name; payload stores free SQL + provenance). Loads call `SourceModelSqlEngine` when mode is FREE_SQL.

i18n: all labels in `messages_*.properties` with proper quoting for `${…}` if any.

### 3.2 Phase A optional — model toolbar “SQL query…”

Canvas-level dialog (no need to create a query card): write SQL → preview → optional “Save as source query”. Improves exploration UX; still GUI-first.

### 3.3 Phase C — Transform: Source Model SQL Input

Hop transform so any pipeline can read:

- Source model file (VFS path)
- SQL (or reference named free query)
- Row limit / variables

Dialog: browse `.hsm`, SQL editor, preview, explain. Satisfies GUI parity for runtime virtualisation without requiring JDBC.

### 3.4 Phase D — JDBC / Avatica (Kettle nostalgia, optional)

Expose `jdbc:hop-hsm:…` (or Calcite model) for BI tools:

- Schema = loaded `.hsm`
- `executeQuery` → plan → run pipeline → `ResultSet`
- Security, connection pooling, and write-back: out of scope
- Document for users; not required to close #117

---

## 4. Catalog and load-path impact

### 4.1 Composite source extension

Extend `DvCompositeSource` / resolver:

- Detect free SQL mode from live `.hsm` query.
- Prefer live model + `SourceModelSqlEngine` over cached SQL.
- Cache: optional last-generated pushdown SQL **or** original free SQL (prefer original free SQL + engine version note).

### 4.2 DV Update

`DvCompositeSourcePipelineBuilder`:

- If FREE_SQL → inject `RelToPipelineGenerator` output (then existing field mapping / record-source injection).
- Preserve hub sort/distinct CDC behaviour after the virtualised source graph.

### 4.3 Schema harvest / validation

Resource definition rediscovery:

- Free SQL feeds: field list from Calcite validated row type (or last successful plan), not from JDBC table metadata.
- Validation: parse free SQL; report missing tables/columns as blocking issues with proposals.

---

## 5. Implementation phases (PR plan)

### Phase A — Foundation + Free SQL (MVP for #117 / #115)

**Goal:** Parse free SQL against a DATABASE-centric `.hsm`, generate correct pipelines with same-connection pushdown and residual Merge Join.

| PR | Scope |
|----|--------|
| **A1** | Maven: Calcite pin, assembly packaging, smoke classloader test |
| **A2** | `SourceModelSchema` + type system + parse/validate unit tests (retail CRM fixture) |
| **A3** | `PushdownClassifier` + `RelToPipelineGenerator` for Project/Filter/Join/Sort/Limit + TableScan(DATABASE) |
| **A4** | `SourceQuery` FREE_SQL mode + dialog Validate/Explain/Preview; persistence |
| **A5** | Composite publish + DV load path for free SQL; unit tests; docs sketch |

**Exit criteria:**

- SQL against `retail-example/models/source-tables-crm.hsm` (multi-table join + where + order + limit) previews correct rows on Postgres.
- Same-connection query produces a single Table Input (assert via plan inspect).
- Cross-connection fixture (if available) or mocked dual-DB unit test produces Merge Join residual.
- Unsupported SQL fails with readable error.

### Phase B — Operators + more source kinds — **implemented**

| PR | Scope | Status |
|----|--------|--------|
| **B1** | Aggregates → GroupBy residual; full pushdown via RelToSql | Done |
| **B2** | SourceJson / SourcePipeline as Calcite tables + residual expansion | Done |
| **B3** | Residual Calculator for `+ - * /`, COALESCE/NVL, CAST pass-through; CASE via full pushdown | Done (residual CASE deferred) |
| **B4** | Unify visual query → Rel builder (optional) | Deferred |

### Phase C — Runtime transform + polish — **mostly implemented**

| PR | Scope | Status |
|----|--------|--------|
| **C1** | `SourceModelSql` transform + dialog + docs | Done |
| **C2** | Coach / help: SQL cheat sheet against current model | Deferred |
| **C3** | Integration tests (Postgres first; full matrix if SQL dialects in pushdown change) | Unit tests done; Docker IT optional follow-up |

### Phase D — External JDBC (optional epic) — **implemented**

| PR | Scope | Status |
|----|--------|--------|
| **D1** | Local JDBC driver `jdbc:hop-hsm:file=…` + docs | Done — in-plugin `HopSourceModelJdbcDriver` |
| **D2** | Read-only ResultSet + DatabaseMetaData | Done — materialised rows |
| **D3** | Hop Server servlet + named metadata + **thin client jar** | Done — `SourceModelDataServlet` (`/hop/sourceModelData`), `SourceModelService` = JDBC schema, module `hop-hsm-jdbc` (zero deps). DBeaver URL template: `jdbc:hop-hsm://{username}:{password}@{host}:{port}/{database}` (`{database}` = service name). Docs + screenshots in `docs/source-modeler-overview.adoc` |
| **Polish** | Named SourceQuery views; Free SQL **Insert tables…** | Done |

**Out of scope / deferred:** true Avatica BI server, connection pooling, bound prepared parameters, multi-statement, write-back, true streaming cancellation.

---

## 6. Detailed design notes for implementers

### 6.1 Reuse first

| Existing code | Reuse for |
|---------------|-----------|
| `SourceQueryPipelineGenerator` | Sort + MergeJoin + SelectValues patterns |
| `SourceQuerySqlGenerator` | Quoting / qualified table names / alias style (or replace with RelToSql for free SQL only) |
| `SourceJsonPipelineGenerator` | JSON table scans |
| `DvPipelineSourceSupport` / MetaInject | Pipeline source scans |
| `SourceQueryPreviewSupport` / `SourceJsonPreviewSupport` | Local execution + row collect |
| `ShowRowsDialog` | Result display |
| Retail `.hsm` + CRM Docker | Golden path tests |

### 6.2 Expression safety

Phase A: only column refs and predicates Calcite can push or `FilterRows` can express.  
Reject: user-defined functions, Hop variable substitution inside identifiers (allow `${VAR}` only in **connection** metadata via Hop variables space, not raw SQL injection into free SQL without validation).

Variables: resolve Hop variables **before** parse for connection-bound fragments only if we explicitly document `/*+ hop */` — simpler rule for v1: **free SQL is literal after variable substitution on the whole string** (Hop `variables.resolve(sql)`), then parse.

### 6.3 Security / ops

- Read-only.
- Preview row limits mandatory in GUI.
- No multi-statement SQL.
- Execution uses project metadata provider + variables (same trust model as previewing a source table today).

### 6.4 Testing strategy

| Layer | Tests |
|-------|-------|
| Unit | Parse/validate; type mapping; pushdown classification; Rel→pipeline shape (transform counts/types); SQL fragment snapshots |
| Fixture | Load `source-tables-crm.hsm` from disk (already used in tests) |
| Integration | Postgres preview/load for free SQL composite feed; dialect pushdown for MySQL/MSSQL when RelToSql dialects land |
| Negative | Unsupported syntax; unknown table; ambiguous column |

Do **not** edit golden CSVs without intentional behaviour change + suite re-run.

### 6.5 Documentation

- `docs/source-modeler-overview.adoc` — Free SQL + virtualisation section  
- `docs/plans/source-model-sql-virtualization-plan.md` — copy of this plan (repo-facing)  
- `docs/feature-overview.adoc` — maturity row  
- `CHANGELOG.md` when shipping  
- AI file schemas: free SQL field on query if serialized in `.hsm`

### 6.6 Coding conventions (project rules)

- Lombok on new classes; ASF headers; Spotless.  
- Hop VFS for loading `.hsm`.  
- GUI for every user-facing capability.  
- No Hop-core patches in this repo.  
- JDK 21 / Hop 2.19.0 pins unchanged.

---

## 7. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Calcite size / classloader conflicts | Bundle carefully; isolated smoke test in plugin zip; pin versions |
| Dialect SQL incorrect | Start conservative; generic ANSI + Postgres first; residual fallback |
| Merge Join requires sorted inputs | Always insert SortRows on join keys (existing pattern) |
| Visual vs free SQL dual paths drift | Share pushdown classifier; later unify on Rel |
| Scope explosion (full SQL, JDBC, BI) | Hard phase gates; Phase A closes #117 MVP |
| Performance of naive residual joins | Document; Phase B+ cost stats from relationship profiler row counts |

---

## 8. Success metrics

1. **MVP:** Free SQL in Source Query dialog validates, explains, previews, and publishes a loadable composite feed.  
2. **Pushdown:** Single-DB queries show pushdown SQL in Explain and a minimal pipeline.  
3. **Heterogeneous:** Join DB table + JSON (or dual connection) runs via residual Merge Join.  
4. **UX:** Errors name the unsupported construct and point to supported subset.  
5. **Parity:** No feature that exists only as a hidden API.

---

## 9. Recommended first implementation slice

If implementing immediately, start with **A1–A4** only:

1. Add Calcite dependency + assembly.  
2. Schema over **DATABASE** `SourceTable`s only.  
3. Support `SELECT … FROM t [JOIN …] WHERE … ORDER BY … LIMIT n`.  
4. Full pushdown when one connection; else Table Input per table + Sort + MergeJoin + Filter + SelectValues.  
5. Wire Free SQL into Source Query dialog with Validate / Explain / Preview.

That is already a credible “data virtualisation” step beyond the old Kettle JDBC driver — because the **source model relationships and multi-engine generation** are first-class — without boiling the ocean.

---

## 10. Open decisions (defaults if unstated)

| Decision | Default recommendation |
|----------|------------------------|
| Bundle Free Query (#115) into this work? | **Yes** — same engine |
| JDBC driver in MVP? | **No** — Phase D |
| Replace visual SQL generator with Calcite? | **No** in Phase A |
| Named SourceQueries as SQL views? | **Phase B** (nice for `FROM feed_customer_enriched`) |
| Catalog type for free SQL | Keep **COMPOSITE** with mode flag / free SQL field |
| Calcite planner | HepProgram subset first; Volcano only if needed |

---

## 11. Summary

Issue #117 is the right moment to turn the Source Modeler from a **static feed designer** into a **virtual schema** with Calcite planning and Hop execution. The existing SQL / Merge Join generators and retail `.hsm` fixtures are strong foundations. Ship in phases: **parse + pushdown + free SQL GUI** first, then richer operators and source kinds, then a runtime transform, and only later a Kettle-style JDBC façade for external tools.
