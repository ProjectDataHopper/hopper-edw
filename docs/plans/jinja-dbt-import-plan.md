# Plan: Jinja macros + dbt-core importer for Business Vault SQL (Issue #72)

**Issue:** [#72 — Support dbt-style macros in the SQL Business Vault Table](https://github.com/ProjectDataHopper/hopper-edw/issues/72)  
**Branch:** `issue-72`  
**Goal of this plan:** implement the *basics* of a Hop alternative for teams leaving dbt — not a full dbt-core clone.

---

## 1. Problem and product intent

A customer project has **1000+ dbt-core models**: parameterized SQL with Jinja (`{% %}`, `{{ }}`, macros), `ref()` / `source()`, and YAML metadata (descriptions, materialization, columns). At runtime dbt creates views and tables. Those teams want a path onto **Hop as a real ETL engine**, with Business Vault SQL objects (`.hbv`) as the landing zone.

Issue #72 asks for three things:

1. Consider **HubSpot Jinjava** (Apache-2.0).
2. **Register macros in Hop metadata**.
3. An **importer** from dbt-core (`.sql` + `.yml` / `.yaml`) into `.hbv`, plus the imported macro metadata.

### 1.1 What already exists (do not reinvent)

Business Vault SQL tables (`BvBusinessTable`) already implement the *dbt-shaped* consumption layer:

| Capability | Today |
|------------|--------|
| Authoring SQL on the BV canvas | `HopGuiBvBusinessTableDialog` |
| `{{ ref('object') }}` / `{{ ref('model','object') }}` / `{{ source('s','t') }}` | Regex parser `BvSqlTemplateParser` — **single-quoted literals only** |
| Source declarations | `BvSqlSource` (name, database, schema, table, description) |
| Materialize VIEW or TABLE | `BvSqlViewPipelineSupport` + Exec SQL pipeline |
| Dependency order | `BvSqlDependencySupport` topological sort in BV Update |
| Cross-model refs | Catalog registry + filesystem path (`BvSqlRefResolver`, `BvSqlModelPathSupport`) |
| Canvas aliases | DV / BV reference cards from parse/sync |
| Check model | Residual `{{` / `}}` after known macros is an **ERROR** |
| GUI | Context menu **Add SQL view / table**; Preview SQL; Generated SQL tab |

Docs already say the quiet part: *“Full Jinja, multi-file dbt projects, and incremental merge strategies are out of scope.”* ([`docs/business-vault-sql-view.adoc`](docs/business-vault-sql-view.adoc))

### 1.2 Why the current parser is not enough

Real dbt models routinely contain:

```sql
{{ config(materialized='table', alias='customer_360') }}

{% set methods = ['card', 'cash'] %}
{% if var('include_inactive', false) %}
  -- ...
{% endif %}

select
  {{ cents_to_dollars('amount') }},
  {% for m in methods %}
    sum(case when method = '{{ m }}' then amount end) as {{ m }}_amt{% if not loop.last %},{% endif %}
  {% endfor %}
from {{ ref('stg_payments') }}
```

`BvSqlTemplateParser` cannot see `ref()` inside `{% if %}`, cannot expand `{% for %}`, rejects leftover `{{`, and has no user macros. Residual braces are treated as malformed. That is the migration blocker.

---

## 2. What “basics” means (in scope vs out)

This is a **migration on-ramp**, not “dbt inside Hop.”

### 2.1 In scope (this work)

1. **Sandboxed Jinja render** of authoring SQL via Jinjava.
2. **dbt-shaped builtins** that feed the existing BV graph: `ref()`, `source()`, `config()` (no-op at render; captured at import), `var()`, `this`, `is_incremental() → false`, `adapter.quote()`.
3. **Jinja Macro Library** Hop metadata (project-level, GUI editor, test-render).
4. **Wire render** into check model, parse/sync refs, Preview SQL, Generated SQL, and BV Update materialization.
5. **dbt-core importer** (GUI on the BV canvas): walk a project, read `.sql` + schema YAML + `macros/*.sql` + `sources`, create `BvBusinessTable`s (and optional extra `.hbv` files), import macros, show a compatibility report.
6. **Provenance + descriptions**: table description, optional column descriptions, optional target schema, original dbt path.
7. **Keep existing simple `ref`/`source` tables working** with no file format break.

### 2.2 Explicitly out of scope (report, do not fake)

| dbt feature | Basics behavior |
|-------------|-----------------|
| Incremental merge / `is_incremental()` true | Import as **TABLE** (full refresh); `is_incremental()` is always false; warn |
| Ephemeral models | Import as **VIEW** (dbt inlines; we will not); warn |
| Snapshots, seeds, analyses, Python models | Skip; list in report |
| Tests / `data_tests` / exposures / metrics / semantic layer | Skip (later: quality rules) |
| `dbt_packages` / `adapter.dispatch` / `dbt_utils.*` | Do not execute; warn on calls; optional best-effort copy of *project* macros only |
| `run_query`, `graph`, `flags`, `execute` flag, `exceptions.warn` | Fail loudly with a clear “not supported” message |
| Relation objects (`ref().identifier`, `.include(...)`) | `ref`/`source` return a **quoted name string** only |
| Per-model `database=` (other connection) | Warn; use BV target database |
| Export `.hbv` → dbt (`to-and-from`) | Deferred. Issue #72 mentions it; **import is the basics path** |
| Workflow action for headless re-import | Deferred (GUI first; action is a follow-up) |
| Iceberg / non-CREATE VIEW engines | Unchanged (already documented) |

Honesty for a 1000-model estate: models that are “SQL + `ref`/`source` + `{% set %}` / `{% if %}` / `{% for %}` + project macros + `var()` + `config()`” should import and run. Models that are “incremental + `dbt_utils` + `run_query` + adapter” will land as cards with a **check-model error** until rewritten or until a later compatibility layer exists.

---

## 3. Alternatives considered

### 3.1 Template engine

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **Jinjava 2.8.4** (HubSpot, APL2) | Issue #72’s suggestion; real `{% %}` / macros / filters; Java 17+; 2.8.3+ has ForTag + restricted-class sandbox fixes (CVE-2025-59340, CVE-2026-25526) | Not Python Jinja2; not dbt-jinja; Jackson/Guava/JSoup transitives; must sandbox | **Choose** |
| Grow the regex parser | No new deps; tiny | Cannot do loops, macros, dynamic `ref()` | Reject as the engine |
| Pebble / Freemarker | Mature Java | Not Jinja; migration would rewrite every model | Reject |
| Shell out to Python dbt/Jinja2 | Perfect compatibility | Not acceptable in a Hop plugin (runtime, packaging, GUI) | Reject |

### 3.2 Where macros live

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| One Hop metadata object **per macro** | Fine-grained | 50–200 items flood Metadata perspective | Reject |
| **Jinja Macro Library** (list of macros), like `DataQualityRuleSetMeta` | Matches existing metadata UX; one library per imported project/package | Slightly more editor work | **Choose** |
| Keep `macros/*.sql` on disk only | Familiar to dbt users | Bypasses Hop GUI / metadata; violates project GUI-parity rule | Reject as sole store |

Libraries are **project metadata**. A BV model (or BV configuration) can list which libraries are in scope; default is **all enabled libraries** in the metadata provider.

### 3.3 How `ref()` / `source()` are collected

Regex-before-render misses `{{ ref(var) }}` and branch-dependent refs. dbt records dependencies **during Jinja execution**.

**Choose:** render with intercepting functions that (a) record `ref`/`source` and (b) return the dialect-quoted physical name. One pass for both check/sync and materialization.

Keep `BvSqlTemplateParser` as a **fast path** when the SQL contains only the three known macros (no `{%`, no other `{{`). Existing unit tests and `vault1.hbv` stay on that path.

### 3.4 1000 models on one canvas

One `.hbv` with 1000 cards is unusable. dbt model names are **globally unique** in a project, so one-arg `ref('stg_customers')` can resolve across files if the resolver searches sibling `.hbv` files.

**Choose:**

- Importer default: **split by first-level folder under `models/`** when the selection is large (threshold ~80), else import into the **current** `.hbv`.
- User can override (current model / new single file / split).
- After import, run **ELK layout** (`BusinessVaultElkLayout` already exists).
- Extend **one-arg `ref()`** to search other `.hbv` files in the same directory and `${PROJECT_HOME}/models/` (plus existing catalog registry). Do **not** rewrite authoring SQL.

### 3.5 `config()` and materialization

Do not implement dbt’s config-merge object.

- **Import:** parse `{{ config(...) }}` (and YAML / `dbt_project.yml` `+materialized`) into `BvBusinessTable.materialization`, `tableName` (alias), optional `schemaName`.
- **Render:** `config()` returns empty string so it does not leak into SQL.

---

## 4. Target architecture

```
dbt project (dbt_project.yml, models/**/*.sql, **/*.{yml,yaml}, macros/**/*.sql)
        │
        ▼
 DbtProjectParser  ──►  DbtImportService  ──►  .hbv BvBusinessTable(s)
        │                      │                    + JinjaMacroLibraryMeta
        │                      ▼
        │               Import report (GUI)
        │
        ▼
Authoring SQL (Jinja kept as written)
        │
        ▼
 BvSqlJinjaSupport.render(...)          ← Jinjava sandbox
        │  builtins: ref, source, config, var, this, is_incremental, adapter.quote
        │  macros: all in-scope JinjaMacroLibraryMeta entries (global names)
        │  vars: Hop IVariables + imported dbt vars
        │
        ├─ collected BvSqlRef / BvSqlSource usages
        └─ rendered SQL (macros expanded; ref/source already quoted)
                │
                ▼
 BvSqlViewPipelineSupport  ──►  CREATE VIEW|TABLE … AS <rendered>
```

Existing `BvSqlRefResolver` / dependency sort / Exec SQL pipelines stay the materialization spine. Jinja is a **front-end** to that spine.

### 4.1 Render pipeline (replaces “regex then rewrite” when Jinja is present)

```
sqlQuery
  → if simple-dbt-only: BvSqlTemplateParser + BvSqlRefResolver.resolveSql  (today)
  → else:
       BvSqlJinjaSupport.render(table, model, vars, metadata, db)
         • load in-scope macro libraries
         • register builtins
         • Jinjava.render()
         • fail on template errors (no silent leftover {{ )
       sync sqlRefs from collected ref() calls
       rendered SQL already has quoted names → skip regex rewrite
```

`BvSqlRefResolver.resolveSql` becomes the single entry point (Preview, Generated SQL, BV Update, check model). Internally it chooses fast-path vs Jinja.

### 4.2 Builtin semantics

| Function | Render result | Side effect |
|----------|---------------|-------------|
| `ref('object')` / `ref('model','object')` | Dialect-quoted physical name via existing resolver | Append `BvSqlRef` |
| `source('name','table')` | Quoted `schema.table` from declared `BvSqlSource`, or from imported project sources | Record usage; check-model still requires a declaration (importer pre-fills) |
| `config(...)` | `""` | None at render |
| `var('x')` / `var('x', default)` | Hop variable `x` if set, else imported library/project vars, else default; missing without default → render error | |
| `this` | Quoted target `schema.table` of the current business table | |
| `is_incremental()` | `false` | |
| `adapter.quote(name)` | `DatabaseMeta.quoteField(name)` (or quote table identifier) | |
| `exceptions.raise_compiler_error(msg)` | Throws `HopException` | |
| Unknown `adapter.*`, `run_query`, `graph` | Render error naming the unsupported call | |

`var()` maps to Hop variables so `${START_DATE}` and `{{ var('START_DATE') }}` can share one project config. Imported `dbt_project.yml` `vars:` become defaults on the library (or a small `vars` map on the library), **overridden** by Hop variables of the same name.

### 4.3 Sandbox (non-negotiable)

Jinjava has a history of sandbox escapes. Basics must:

- Pin **`com.hubspot.jinjava:jinjava:2.8.4`** (or newer patched line). Do not drop below 2.8.3.
- Exclude Jinjava’s Jackson; keep this plugin’s Jackson **2.21.1**.
- Disable `FileResourceLocator` (removed as default since Jinjava 2.2.0 — do not re-enable).
- Do **not** register a resource locator that reads arbitrary VFS paths from template `include`. Macro bodies come from metadata only.
- `failOnUnknownTokens = true`; max render size / max recursion set to tight defaults.
- Restrict EL to registered functions + Jinja builtins; no `getClass`, no interpreter exposure.
- Unit-test that `{{ ''.class }}` / Java method probes fail.

Bundle Jinjava + required transitives (Guava, JSoup, shaded JUEL, etc. as needed) in `src/assembly/assembly.xml` under `plugins/misc/hopper-edw/lib`. Attribute Apache-2.0 in `NOTICE`.

### 4.4 YAML

No YAML library is in this repo today. Add **`jackson-dataformat-yaml` 2.21.1** (aligned with existing `jackson-databind`). Use it only for dbt YAML / `dbt_project.yml`. Do not introduce a second YAML stack.

---

## 5. Data model

### 5.1 `JinjaMacroLibraryMeta` (new `@HopMetadata`)

Mirror `DataQualityRuleSetMeta` / `DataTypeMappingMeta`:

- `key = "jinja-macro-library"`
- Fields: `description`, `packageName` (optional, e.g. project name from `dbt_project.yml`), `enabled`, `vars` (list of name/value defaults), `macros[]`
- Each `JinjaMacroDefinition`: `name`, `description`, `jinjaSource` (full `{% macro name(...) %}...{% endmacro %}` or body + signature), `originPath` (portable dbt relative path)

Registration: `HopEnvironmentAfterInit` extension point, same pattern as `RegisterDataTypeMappingMetadataExtensionPoint`.

Searchable analyser for Hop 2.19 project search.

### 5.2 `BvBusinessTable` additions (backward compatible)

| Field | Purpose |
|-------|---------|
| `schemaName` | Optional target schema (from dbt `schema` / `+schema`); used when quoting CREATE and `this` |
| `originDbtPath` | Portable relative path (`models/marts/customers.sql`) for re-import / report |
| `columnNotes` (`name` + `description`) | YAML column descriptions; documentation only (layout is still SQL-defined) |

No new `BvSqlReferenceStyle`. Style `DBT` now means “dbt + Jinja.” Existing `.hbv` files unchanged.

### 5.3 Optional scope pointer

`BusinessVaultConfiguration` (or the model) may list `jinjaMacroLibraryNames`. Empty = all enabled libraries. Keep this small so 10 `.hbv` files share one imported library.

### 5.4 Lineage

`BvModelLineageCollector` today treats SQL tables as generic. After render, attach `sqlRefs` / sources as `TableSourceRef`s so Hop Lineage View sees dbt-imported dependencies. Do this in the engine PR once refs are collected from Jinja.

---

## 6. dbt importer

### 6.1 Parse (headless, unit-testable)

New package `org.hopper.edw.datavault.dbt`:

| Class | Role |
|-------|------|
| `DbtProjectParser` | Read `dbt_project.yml` via HopVfs: `name`, `model-paths`, `macro-paths`, `vars`, folder `+materialized` / `+schema` |
| `DbtSchemaYamlParser` | Merge all `*.yml` / `*.yaml` under model paths: `models[]`, `sources[]`, column descriptions |
| `DbtSqlModelReader` | Pair `*.sql` with same-basename YAML + folder `schema.yml`; strip/capture `config()`; keep full SQL as authoring text |
| `DbtMacroFileParser` | Split `macros/**/*.sql` into named `{% macro %}` units |
| `DbtImportService` | Pure apply: options → `BusinessVaultModel` mutation(s) + `JinjaMacroLibraryMeta` + `DbtImportReport` |

**I/O via `HopVfs` only.** Recurse with VFS file selectors, not `java.nio.file` (unless VFS cannot list; prefer VFS).

Supported YAML shapes:

- dbt v2 `models: - name: … description: … config: … columns:`
- `sources: - name: … schema: … database: … tables: - name: …`
- Folder-level `schema.yml` listing many models
- Adjacent `customers.yml` next to `customers.sql`

`dbt_project.yml` folder config is applied first, then YAML `config`, then in-SQL `{{ config() }}` (SQL wins).

Materialization map:

| dbt `materialized` | Hop | Report |
|--------------------|-----|--------|
| `view` / unset | VIEW | |
| `table` / `materialized_view` | TABLE / VIEW | warn on materialized_view |
| `incremental` | TABLE | warn: full refresh only |
| `ephemeral` | VIEW | warn: not inlined |
| other | skip or VIEW | error/warn |

### 6.2 GUI (required)

Toolbar button on `HopGuiBusinessVaultGraph` (same family as Check model / ELK layout) plus canvas context action **Import dbt models**.

`HopGuiDbtImportDialog`:

1. Folder field (VFS) — must contain `dbt_project.yml` (or user points at `models/` and we walk up).
2. Scan (busy cursor / progress). Fill a `TableView`: checkbox, model name, relative path, materialization, description, issue summary.
3. Filter box; **Select all** / **Select folder**.
4. Options:
   - Destination: current `.hbv` / new `.hbv` / split by first-level `models/` folder
   - Import macros into library named `[project]-macros` (create or replace)
   - Import `sources:` onto each table that uses them (and a shared note / unused-source warnings stay as today)
   - Conflict: skip / replace existing table of same name
5. **Import** applies via undo snapshot (`graph.markUndoPoint` / `runUndoableModelChange`), ELK layout (rect packing default for large sets), `setChanged()`, report dialog.

When splitting: write additional `.hbv` files next to the current model (or a user-chosen folder) with `HopVfs.getOutputStream`, same BV configuration name as the current model, then offer to open them.

Help topic: `docs` + `src/main/resources/org/hopper/edw/datavault/hopgui/help/bv-dbt-import-dialog.md`.

### 6.3 Macro editor GUI

`JinjaMacroLibraryMetaEditor` (`MetadataEditor`):

- Name, description, package, enabled
- Vars table (name / default)
- Macros table + detail: name, origin path, styled text for Jinja source
- **Test render** with a sample snippet `{{ macro_name(...) }}` and dummy `ref`/`source` (quoted placeholders)

### 6.4 Business table dialog

- Keep authoring SQL as Jinja (do not compile away on import).
- Generated SQL tab already shows CREATE; it must go through the new `resolveSql`.
- Check model: replace “residual `{{` after regex” with “Jinja render failed” / “unsupported builtin” when the Jinja path is used. Fast-path keeps today’s residual-brace rule.
- Optional small **Columns** tab listing imported `columnNotes` (read-only-ish, editable descriptions). Needed so YAML descriptions are not file-only.

---

## 7. Resolver change for split imports

In `BvSqlRefResolver.resolveOneArgRef`, after current BV + DV alias + linked DV:

1. Catalog registry (already exists for two-arg; use for one-arg object scan of registered BV models).
2. Sibling `.hbv` in the referring file’s directory.
3. `${PROJECT_HOME}/models/*.hbv`.

Cache loads per resolve-session (check model / update) so 1000 refs do not re-parse the same file 1000 times. `BvSqlModelPathSupport` already loads models; add a short-lived cache keyed by resolved path.

Name collisions across files: ERROR on check model (dbt would also forbid duplicate model names).

---

## 8. Package / file map (expected)

```
src/main/java/org/hopper/edw/datavault/jinja/
  BvSqlJinjaSupport.java
  JinjaSandboxFactory.java
  DbtJinjaBuiltins.java          // ref, source, config, var, this, …
  JinjaMacroLibraryLoader.java

src/main/java/org/hopper/edw/datavault/metadata/jinja/
  JinjaMacroLibraryMeta.java
  JinjaMacroDefinition.java
  JinjaMacroVar.java
  JinjaMacroLibraryMetaEditor.java
  JinjaMacroLibrarySearchableAnalyser.java
  xp/RegisterJinjaMacroLibraryMetadataExtensionPoint.java

src/main/java/org/hopper/edw/datavault/dbt/
  DbtProjectParser.java
  DbtSchemaYamlParser.java
  DbtSqlModelReader.java
  DbtMacroFileParser.java
  DbtImportService.java
  DbtImportOptions.java
  DbtImportReport.java
  DbtModelDraft.java

src/main/java/org/hopper/edw/datavault/hopgui/file/businessvault/
  HopGuiDbtImportDialog.java
  HopGuiDbtImportSupport.java
  (+ toolbar/context on HopGuiBusinessVaultGraph)

src/test/resources/org/hopper/edw/datavault/dbt/jaffle-mini/
  dbt_project.yml, models/…, macros/…, models/schema.yml
```

Touch existing:

- `BvSqlRefResolver`, `BvSqlValidationSupport`, `BvSqlViewPipelineSupport`
- `BvBusinessTable`, maybe `BusinessVaultConfiguration`
- `HopGuiBvBusinessTableDialog`
- `BvModelLineageCollector`
- `pom.xml`, `src/assembly/assembly.xml`, `NOTICE`
- i18n: `messages_en_US.properties` (BV graph, dialogs, metadata, check results) — escape `=` / `:` / `'${VAR}'`
- Docs: `docs/business-vault-sql-view.adoc`, new `docs/dbt-import.adoc`, `docs/feature-overview.adoc`, `docs/plans/README.md`, help markdown
- SVG icon for toolbar / metadata (simple, consistent with existing)

---

## 9. Testing strategy

### 9.1 Unit (required every PR)

- **Jinja engine:** `{% set %}`, `{% if %}`, `{% for %}` + `loop.last`, comments `{# #}`, whitespace `{%- -%}`, user macro call, `var` + Hop override, `this`, `config()` stripped, `ref`/`source` collection with non-literal args (`{% set t = 'sat_x' %}{{ ref(t) }}`).
- **Sandbox:** Java introspection attempts fail.
- **Fast path:** existing `BvSqlTemplateParserTest` / `BvSqlRefResolverTest` / `BvSqlValidationSupportTest` remain green.
- **Validation:** residual braces on simple SQL still error; Jinja SQL with `{% for %}` does not.
- **dbt parser:** `jaffle-mini` fixture — model names, descriptions, sources, macros, `+materialized`, incremental warning, ephemeral warning, skip snapshot.
- **Import service:** apply into an in-memory `BusinessVaultModel`; conflict skip/replace; split grouping.

### 9.2 Integration

Jinja that compiles to the same SQL as today’s fixtures does **not** change dialect/DDL. A **Postgres-only** golden is enough for the first Jinja SQL table (e.g. a `{% for %}` column list over `sat_customer`). Full `run-tests-all-databases.sh` is **not** required unless CREATE script generation changes.

If `schemaName` affects quoting, add one quoted-schema assertion per dialect in **unit** tests (`BvSqlViewPipelineSupportTest` already dialect-switches), not a 4-engine Docker run, unless quoting is wrong in practice.

### 9.3 GUI

No automated SWT tests (repo convention). Manual check: import dialog, macro editor test-render, business table Generated SQL, Check model.

---

## 10. Documentation and product copy

- New user doc `docs/dbt-import.adoc`: what imports, what warns, what will not run, how macros and `var()` map to Hop.
- Update `docs/business-vault-sql-view.adoc`: Jinja is in scope for `{% %}` / macros; keep incremental/packages out; remove the blanket “Full Jinja … out of scope” sentence and replace with a precise compatibility table.
- `docs/feature-overview.adoc`: two rows — Jinja macros (metadata) and dbt importer (preview/available).
- Dialog help + metadata `documentationUrl`.
- Do not claim dbt-core compatibility.

---

## 11. Key decisions

1. **Jinjava 2.8.4+**, sandboxed, Jackson excluded — real Jinja without a Python runtime.
2. **Macros live in a Hop metadata library**, edited in GUI; importer fills the library from `macros/`.
3. **Authoring SQL stays Jinja**; render at check / preview / materialize. Import does not compile away templates.
4. **`ref`/`source` are Jinja functions** that record deps and return quoted names; regex remains the simple fast path.
5. **Not a dbt runtime.** Incremental, packages, `run_query`, Relation methods, snapshots, tests, and export are out of basics.
6. **Importer is GUI-first** on the BV canvas; headless action later.
7. **Split large imports by folder**; extend one-arg `ref()` across sibling `.hbv` files instead of rewriting SQL.
8. **Ephemeral → VIEW, incremental → TABLE**, both warned. Better a runnable object than a silent skip.
9. **Optional `schemaName` on SQL business tables** so dbt custom schemas are not dropped.
10. **Issue #72 “to-and-from”:** **from** dbt is this work; **to** dbt is a later exporter.

---

## 12. PR plan (incremental, each mergeable)

### PR 1 — Jinja engine + metadata library + BV render hook

**Title:** Issue #72: sandboxed Jinja render and Jinja Macro Library metadata  

**Files:** `jinja/*`, `metadata/jinja/*`, `pom.xml`, `assembly.xml`, `NOTICE`, `BvSqlRefResolver`, `BvSqlValidationSupport`, `BvBusinessTable` (`schemaName` only if CREATE needs it here), messages, unit tests, help/docs slice for macros + template language.

**Depends on:** nothing.

**Done when:** a SQL business table with `{% for %}` + a library macro + `{{ ref('sat_customer') }}` check-models and produces CREATE SQL; simple existing tables unchanged; sandbox tests pass.

### PR 2 — Project-wide one-arg `ref()` + lineage from collected refs

**Title:** Issue #72: resolve one-arg ref() across sibling Business Vault models  

**Files:** `BvSqlRefResolver`, `BvSqlModelPathSupport` (cache), `BvModelLineageCollector`, tests.

**Depends on:** PR 1 (uses collected refs from render).

**Done when:** `ref('other_sql_table')` resolves if that table lives in `./other.hbv` or `${PROJECT_HOME}/models/other.hbv`.

### PR 3 — dbt project parser + import service (no GUI)

**Title:** Issue #72: parse dbt-core projects into Business Vault drafts  

**Files:** `dbt/*`, `jaffle-mini` fixtures, tests, `columnNotes` / `originDbtPath` on `BvBusinessTable`.

**Depends on:** PR 1 (macro library types).

**Done when:** parser+service can build tables + library + report from the fixture without SWT.

### PR 4 — Importer GUI + dialog help

**Title:** Issue #72: Import dbt models into the Business Vault canvas  

**Files:** `HopGuiDbtImportDialog`, toolbar/context on `HopGuiBusinessVaultGraph`, i18n, help markdown, ELK layout call, `docs/dbt-import.adoc`, feature-overview.

**Depends on:** PR 3 (and PR 2 so split imports resolve).

**Done when:** from an open `.hbv`, a user can pick a dbt project, preview 1000 rows in a filterable table, import a subset, get macros in metadata, and see a report. No file-only path.

### PR 5 — Docs polish + optional Postgres Jinja fixture

**Title:** Issue #72: document Jinja/dbt import and add a Jinja BV SQL fixture  

**Files:** `docs/business-vault-sql-view.adoc`, retail or integration-tests one Jinja SQL table (Postgres), `docs/plans/README.md`.

**Depends on:** PR 1 (engine) and ideally PR 4 (so docs match GUI).

---

## 13. Implementation notes (easy to violate)

- **GUI parity:** every user-facing capability (macros, import, column descriptions) has a dialog or metadata editor.
- **Lombok** on new model/meta classes; no hand-rolled getters.
- **HopVfs** for all dbt file reads/writes.
- **i18n:** properties properly escaped; `'${PROJECT_HOME}'` if shown.
- **ASF headers** on new `.java`; Spotless before commit.
- **Jandex:** new `@HopMetadata` / `@GuiPlugin` / `@ExtensionPoint` must be compiled so the index picks them up — do not bypass the Jandex plugin.
- **Do not** vendor dbt or call `dbt parse`.
- **Do not** bump Java or Hop version.
- **Do not** edit golden CSVs for this feature unless a new IT assertion is intentional.

---

## 14. Open questions (defaults if unanswered)

These have recommended defaults so work can start; confirm only if a customer constraint disagrees.

1. **Incremental models:** import as full-refresh TABLE (recommended) vs skip?
2. **Ephemeral models:** import as VIEW (recommended) vs skip vs attempt CTE inlining (later)?
3. **Split threshold:** ~80 tables (recommended) vs always current model vs always split?
4. **Export to dbt:** confirm deferred?
5. **Optional follow-up:** workflow action `Import dbt project` for CI re-import — after GUI lands?

---

## 15. Suggested verification ladder (when implementing)

1. `mvn test` after each PR.
2. Manual GUI: macro editor test-render; import `jaffle-mini`; Check model; Generated SQL; BV Update against local Postgres if convenient.
3. Existing `vault1.hbv` SQL tables still check and materialize (fast path).
4. Full multi-database IT only if CREATE/quoting behavior changes.
