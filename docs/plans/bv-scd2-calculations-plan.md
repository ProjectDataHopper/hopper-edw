# Issue 150 — BV SCD2 calculations

**Issue:** [#150 — BV SCD2 Calculations](https://github.com/ProjectDataHopper/hopper-edw/issues/150)  
**Branch:** `issue-150`  
**Goal:** let modelers attach deterministic, database-independent SQL field expressions to Business Vault SCD2 tables (the CASE / COALESCE / CAST work that currently lives in copy-pasted dbt-core SQL), execute them in generated pipelines via a reusable Calcite-backed Hop transform, and test them at column level and at post-collapse SCD2 row level without a warehouse round-trip.

---

## 1. Why this exists

A large deployment maintains **SCD2-like SQL in dbt-core**: historization boilerplate plus per-column `CASE` / `COALESCE` / nested `CASE` / `CAST`. That SQL is:

- Repeated per entity (error-prone when satellites, grain, or validity columns change).
- Hard to test (dbt tests are scarce; a wrong `CASE` is discovered in production).
- Coupled to one SQL dialect (`:> VARCHAR(n)` style casts appear in the issue examples).

Data Hopper already does the mechanical SCD2 job in `.hbv`:

| Already shipped | Gap (#150) |
|-----------------|------------|
| Multi-satellite merge, Repeat Fields, Analytic Query, sentinels, Group By collapse | No place to put `CASE WHEN deleted='Y' THEN NULL ELSE date END` |
| 1:1 `BvScd2FieldMapping` (satellite + source → target) | No multi-input derived columns |
| Incremental / partitioned rebuilds | Expressions would have to be hand-edited into generated pipelines |
| dbt importer (#72) lands models as **SQL business tables** | Imported SCD2 SQL stays opaque SQL; historization is not the generated SCD2 pipeline |
| Calcite for source-model Free SQL | Residual `CASE` is explicitly **not** supported (`SupportedSqlFeatures`) |
| Hop Formula / Calculator / Janino | Wrong syntax for dbt SQL authors; Calculator residual CASE is unimplemented |

Issue 150 is **not** “run dbt snapshots in Hop”. It is: **keep generated SCD2 for versions, lift the SELECT-list expressions into the model, and make them testable.**

Typical expressions from the issue:

```sql
CASE WHEN sourceXDeletedFlag = 'Y' THEN NULL ELSE sourceXDate END AS xDate

CASE WHEN (conditionA OR conditionB) AND conditionC THEN field END AS someField

COALESCE(someCode, CASE WHEN sourceYDeletedFlag = 'Y' THEN NULL ELSE otherCode END) AS some_code

CASE WHEN sourceZDeletedFlag = 'Y' THEN NULL
     ELSE CASE WHEN field = 0 THEN 'N' WHEN field = 1 THEN 'Y' END
     END  -- plus CAST to VARCHAR(4)

COALESCE(fieldA, CAST('Default value' AS VARCHAR(720))) AS someDescription
```

Constraint from the issue (accepted): expressions are **deterministic** in the input fields. Same mapped values ⇒ same output. They must **not** drive new SCD2 versions.

---

## 2. Recommended product shape

### 2.1 Two layers on the SCD2 table

Keep field mappings as they are. Add a **Calculations** list (not a fourth column on the mapping table).

```text
Satellites  --map-->  collapsed SCD2 row (grain + mapped attrs + validity)
                         |
                         |  Calculations (SQL scalars, after Group By)
                         v
                    target row (+ calculated columns)
```

- **Mappings** still pull satellite attributes onto the merged timeline. They remain the **Group By keys** (versioning).
- **Calculations** are `(targetField, sqlExpression)` applied **after** `collapse_*` Group By, **before** load-cycle constant / Table Output / incremental write.
- Calculation **target names are new columns**. They must not collide with mapping target names, hash key, driving key, validity, record source, or functional timestamp. Recompute-from-raw on incremental requires the raw mapped attributes to stay on the target.

This matches the issue’s test-builder seam: *“data right after the satellites are collapsed with a group by transform, before these calculations/expressions have been applied.”*

### 2.2 Why not overwrite mapped columns

Incremental SCD2 already reads **open BV rows** as a baseline and merges them with satellite deltas. Versioning and Repeat Fields operate on **mapped attributes**. If a calculation overwrote `xDate` in place:

- The baseline would hold the *calculated* value.
- Re-running `CASE` on that value would double-apply (or lose the deleted-flag input if it was not stored).

So v1 **persists both** raw mapped attributes and calculated columns. Teams that do not want flags on the consumption table add a BV SQL view (already exists) that selects the calculated columns. A later `includeInTarget=false` on mappings is full-rebuild-only and is **out of v1**.

### 2.3 Engine: Calcite parse + allow-listed evaluator + Hop transform

Issue 150 asks to consider a **new transform** that parses SQL clause expressions with Apache Calcite, also useful for `jdbc:hop-hsm:`.

**Choose that.** Shared library, three callers:

1. Generated BV SCD2 pipelines (`BvScd2PipelineSupport`).
2. Standalone pipeline use (palette).
3. Source-model residual `Project` (replace today’s Calculator-only residual path for `CASE` / nested `COALESCE` / real `CAST`).

Do **not** push expressions into satellite `TableInput` SQL (dialect-specific, runs too early, breaks incremental JDBC bind protocol).

---

## 3. Alternatives considered

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **SQL Expression transform + Calcite** (shared evaluator) | SQL syntax for dbt authors; DB-independent; reusable for Free SQL / JDBC; unit-testable without a warehouse | New plugin transform; must allow-list functions | **Choose** |
| Extend Hop **Calculator** residual emitter | Already used in `RelToPipelineGenerator` | No `CASE`; 2-arg `NVL` only; CAST is pass-through; not authorable as SQL | Reject as the SCD2 surface |
| Hop **Formula** (libformula) | Existing transform | Excel-like syntax; not the dbt SQL the estate already has | Reject |
| **User Defined Java** / Janino | Fast | Java, not SQL; GUI-hostile for modelers | Reject |
| Put `CASE` in BV **SQL business tables** (#72) | Already works | Historization stays hand-written; the maintenance problem remains | Keep as escape hatch, not the solution |
| Generate dialect SQL inside Table Input | Simple for one engine | Violates “database independent”; wrong pipeline stage | Reject |
| Fourth column on `BvScd2FieldMapping` | Tiny metadata change | Expressions are multi-input, not 1:1 satellite-field maps | Reject |
| Auto-extract `SELECT` list from imported dbt SCD2 models | Tempting for 1000-model estates | Fragile (Jinja, macros, window/self-join SCD2); wrong first slice | **Out of scope** (docs + AI advisor later) |

### 3.1 How to execute Calcite (sub-choice)

| Option | Verdict |
|--------|---------|
| Wrap each row as a Calcite table and run Enumerable | Heavy per row; hard to sandbox `NOW()` |
| **Parse/validate with Calcite, evaluate allow-listed `RexNode`s in Java** | **Choose** — determinism is the allow-list; fast; testable |
| Compile via Janino/`RexToLixTranslator` | Faster later; more classloader risk (Calcite already in plugin). Defer unless profiling says so |

Parse wrapper (same unquoted case-insensitive config as `SourceModelRelPlanner`):

```sql
SELECT (<expression>) AS _out FROM hop_row
```

`hop_row` is a one-row Calcite schema built from the incoming Hop `IRowMeta` via existing `HopTypeSystem`.

---

## 4. SQL language (v1 allow-list)

**In:**

- Column refs (mapped attributes, grain, validity, record source, functional timestamp, earlier calculations in list order)
- Literals (string / numeric / boolean / `NULL`)
- `CASE WHEN … THEN … [WHEN …] [ELSE …] END` (searched `CASE`; simple `CASE expr WHEN` as well)
- `COALESCE` / `NVL` (N-arg)
- `CAST(x AS type)` and a thin preprocessor for issue dialect `x :> TYPE` and Postgres `x::TYPE` → `CAST(x AS TYPE)`
- Comparisons, `IS NULL` / `IS NOT NULL`, `AND` / `OR` / `NOT`, parentheses
- Arithmetic `+ - * /`
- `NULLIF`, `TRIM`, `UPPER`, `LOWER`, `SUBSTRING`/`SUBSTR`, `CONCAT` / `||`
- Hop variables resolved **once** at compile (`'${VAR}'` in the expression string via `IVariables.resolve`) — treated as constants

**Out (Check model ERROR):**

- Non-deterministic: `NOW`, `CURRENT_TIMESTAMP`, `CURRENT_DATE`, `CURRENT_TIME`, `RANDOM`, `RAND`, `UUID`, `USER`, `SESSION_USER`
- Subqueries, aggregates, windows, `OVER`, `JOIN`, DML
- `adapter.dispatch` / dbt macros / Jinja (`{{` leftover)
- Database-specific functions (`NVL2`, `DECODE`, `IFF`, `TO_CHAR`, …)

Unknown identifiers and type errors fail at **Check model** and at transform `init`, not at first data row.

**Types for DDL:** Calcite validated `RelDataType` → Hop `IValueMeta` (`HopTypeSystem.toHopType`) including `VARCHAR(n)` length. Optional per-calculation Hop type/length override in the dialog. Target type mappings apply at DDL time as they do today (`getTargetTableLayout`).

**NULL:** SQL three-valued logic for `AND`/`OR`; `COALESCE` skips nulls.

**Order:** calculations evaluate in dialog order; later expressions may reference earlier output names.

---

## 5. New transform: SQL Expression

Mirror `SortedSchemaMerge` / `MergeRowsPlus` packaging (Jandex `@Transform`, plugin classloader, help topic).

| Item | Value |
|------|--------|
| Id | `SqlExpression` |
| Package | `org.hopper.edw.datavault.transform.sqlexpression` |
| Category | Scripting (next to Formula; also used outside DV) |
| Meta fields | `List<SqlExpressionField>`: `fieldName`, `expression`, optional `hopTypeName`, `length`, `precision` |
| Runtime | Compile once in `init` / first row; evaluate per row; support error handling (bad row → error hop) |
| `getFields` | Append or replace listed output fields with inferred/override types |

**GUI (user rule):** `@GuiPlugin` + `@GuiWidgetElement` grouped `BOXES`, and `GuiCompositeWidgets.registerExtraGroup(...)` for the expression `TableView` (annotations cannot express a multi-column SQL editor). Buttons: Validate (parse all), Test selected (opens the small column tester — see §7). Help topic `sql-expression-dialog`.

**Do not** hand-layout FormAttachments for the shell-vs-button-bar problem.

Shared engine (no SWT):

```text
org.hopper.edw.datavault.expression
  SqlExpressionCompiler   // parse, preprocess :: and :>, validate, RexNode
  SqlExpressionEvaluator  // per-row Object[] / Hop row
  SqlExpressionAllowList  // SqlKind + operator names
  SqlCastRewrite          // :>  and  ::
```

The transform is a thin Hop wrapper around that engine.

---

## 6. Wire into BV SCD2

### 6.1 Metadata

`BvScd2Table`:

```java
@HopMetadataProperty(key = "calculation", groupKey = "calculations")
private List<BvScd2Calculation> calculations;
```

`BvScd2Calculation`: `targetFieldName`, `expression`, optional type override (`hopType`, `length`, `precision`), optional `description`.

Existing `.hbv` files stay valid (`calculations` absent ⇒ no extra transform).

### 6.2 Pipeline insertion

Today (single- and multi-sat):

```text
… → AnalyticQuery → IfNull(sentinels) → GroupBy(collapse) → [load cycle] → TableOutput / incremental
```

After:

```text
… → GroupBy(collapse) → SqlExpression(calculations) → [SelectValues only if needed] → [load cycle] → writes
```

- Skip the transform when the list is empty (zero behavior change).
- Hash-key partitioned full rebuild: expressions live **inside** the per-partition pipeline (after that partition’s Group By). No extra wrapper work.
- Incremental: recompute calculations on every collapsed row (including rows that mixed baseline + delta). Determinism ⇒ unchanged raw attrs produce unchanged calculated cols. Close-open-version `Update` is unchanged (matches hash + open `valid_to`).
- Incremental baseline SQL already selects `collapseAttributeFieldNames()` (raw mapped attrs). Calculated columns on the target are **not** required on the baseline stream; they are recomputed. (If Table Input `SELECT *` were used it would still be fine; explicit column lists should **not** select calculated columns into grouping.)

`buildTargetTableLayout` / `buildMappedTargetTableLayout`: after mapped attributes, append calculation fields (inferred or override types), then control fields (validity, record source, load cycle). Catalog publish follows this layout automatically.

### 6.3 Dialog (`HopGuiBvScd2TableDialog`)

Add a **Calculations** tab (after Field mappings / Satellite settings):

| Column | Notes |
|--------|--------|
| Target field | Unique among mappings + calculations + technical cols |
| SQL expression | Multi-line friendly; combo of available field names as insert help |
| Type | Blank = infer; else Hop type |
| Length / precision | For `String` / `BigNumber` |
| Description | Optional |

Buttons: Add / Delete / **Validate expressions** / **Test…** (column-level tester).

Keep the existing custom tab folder; do not rewrite the whole SCD2 dialog to `GuiCompositeWidgets` in this issue.

### 6.4 Check model (`BvScd2FieldMappingValidationSupport` or new `BvScd2CalculationValidationSupport`)

- Compile each expression against the **post-collapse** row meta (grain + mapped attrs + validity + record source + functional ts + prior calculations).
- Duplicate / empty names, unknown columns, forbidden functions, parse errors → `TYPE_RESULT_ERROR`.
- Column-level tests attached to the table → run in-process; failure → error (see §7).

### 6.5 Lineage / impact / search

- New `LineageReasonCode.BV_SCD2_CALCULATION`.
- `FieldTransform.DERIVED` contributions: every identifier in the expression that resolves to a mapped field or prior calculation.
- `ImpactGraphBuilder`: calculation target depends on those inputs.
- Search analyser: index expression text and target names.
- AI file schema `docs/ai-file-schemas/models/hbv.md` + sample excerpt.

---

## 7. Test builder (the scarce-tests problem)

Two levels, both **in the GUI**, both **without a database**.

### 7.1 Column-level (required in v1)

On the Calculations tab (or a nested Test dialog):

- One or more `BvScd2CalculationTest` rows stored **on the SCD2 table** in the `.hbv` (git-friendly, travels with the model).
- Each test: `name`, map of **input field → value**, map of **expected calculated field → value**.
- Run = `SqlExpressionEvaluator` on a synthetic Hop row. No pipeline, no JDBC.
- **Check model** and dialog **Test** run the same suite.

This is the unit test the issue asks for at field level. It is how you lock `CASE WHEN deleted='Y'` without loading four satellites.

### 7.2 Post-collapse (BV-level) tests (required in v1)

The issue’s “test-builder … data set … right after group by”:

- `BvScd2CollapseTest`: `name`, plus either inline rows or a **HopVfs CSV path** (large cases).
- Input schema = post-collapse layout (same as transform input).
- Expected output = those rows after calculations (calculated columns required; other columns compared when present).
- Dialog: spreadsheet-like `TableView` (Data Grid style) with **Load CSV** / **Save CSV** via `HopVfs`.
- Run in-process through the same evaluator (N rows), not a generated pipeline. Faster and stable when pipeline names change.

Optional later (not v1): emit a tiny pipeline (Data Grid → SqlExpression → golden DataSet) and register a Hop Pipeline Unit Test. That needs the testing plugin in the EDW project; skip until someone asks.

### 7.3 What we will not do in v1

- Golden-dataset tests of the **full** SCD2 historization path still stay in `integration-tests/` (existing Customer 360 pattern). Calculations add a **new** small suite, not a rewrite of satellite collapse goldens.
- No separate Hop metadata type for tests (would break GUI-parity and scatter tests away from the table).

---

## 8. Source-model SQL / JDBC (same engine, later PR)

`RelToPipelineGenerator.emitExpression` today: `+ - * /`, 2-arg `COALESCE`/`NVL`, CAST pass-through; **throws on `CASE`**.

After the transform exists:

- Residual `Project` with unsupported Calculator shapes → emit **one `SqlExpression`** with the original SQL (or the Rex-printed SQL) per output field, then Select Values.
- Update `SupportedSqlFeatures.SUMMARY` to include residual `CASE` / `CAST` / N-arg `COALESCE`.
- JDBC (`jdbc:hop-hsm:`) and Source model SQL transform get CASE “for free” because they already execute generated residual pipelines.

Keep this in a **follow-up PR** so SCD2 + tests can land without re-qualifying every Free SQL fixture in the same change.

---

## 9. dbt-core migration (docs, not an importer rewrite)

#72 already imports `.sql` as `BvBusinessTable`. That remains the on-ramp for arbitrary SQL.

For **SCD2-like** dbt models, the documented path becomes:

1. Ensure raw satellites exist on `.hdv` (Hop-managed or **External read-only**).
2. Create a BV SCD2 table: derivatives + field mappings (including deleted flags and other CASE inputs).
3. Copy SELECT-list expressions onto the **Calculations** tab; rewrite `:>` / `::` if the preprocessor misses a variant; replace `ref()` of the satellite with mapped field names.
4. Add column-level tests from a few known source rows (the tests the dbt project lacked).
5. Retire the dbt SCD2 model; optional BV SQL view for a “pretty” column subset.

Explicitly **out:** parsing dbt snapshot strategies, `dbt_scd2_plus`, or `incremental_strategy='scd2'` into this feature.

---

## 10. Files / packages (expected)

| Area | Files |
|------|--------|
| Engine | `src/main/java/org/hopper/edw/datavault/expression/*` |
| Transform | `.../transform/sqlexpression/SqlExpression{Meta,Data,,Dialog}.java` + messages + SVG |
| SCD2 metadata | `BvScd2Calculation.java`, `BvScd2CalculationTest.java`, `BvScd2CollapseTest.java`, fields on `BvScd2Table` |
| Validation | `BvScd2CalculationValidationSupport.java` |
| Pipeline | `BvScd2PipelineSupport` after `addGroupBy` |
| Dialog | `HopGuiBvScd2TableDialog` Calculations + Tests |
| Lineage/impact | `BvModelLineageCollector`, `LineageReasonFactory`, `ImpactGraphBuilder` |
| Help/docs | `docs/help/sql-expression-dialog.adoc`, `bv-scd2-table-dialog.adoc`, `business-vault-scd2.adoc`, `feature-overview.adoc`, `dbt-import.adoc` (migration paragraph), `CHANGELOG.md` |
| Layout | `BvGeneratedPipelineSupport.applyScd2Layout` (new node in the chain) |
| i18n | `messages_en_US.properties` for transform + SCD2 dialog + check results |

I/O: tests CSV via **HopVfs** only. No `java.io.File` except if Hop APIs force it.

---

## 11. Testing strategy

### Unit (always)

- `SqlCastRewriteTest` — `:>`, `::`, do not rewrite inside quotes
- `SqlExpressionAllowListTest` — reject `NOW()`, subqueries
- `SqlExpressionEvaluatorTest` — **every issue example**, nested CASE, COALESCE vs NULL, CAST VARCHAR length, 3-valued AND/OR, prior-calculation reference, Hop variable constant
- `SqlExpressionMetaGetFieldsTest` — inferred types
- `BvScd2PipelineSupportTest` — empty calculations ⇒ no transform; non-empty ⇒ `SqlExpression` immediately after `GroupBy`; target layout includes calc columns; incremental still has Update close path
- `BvScd2CalculationValidationSupportTest` — collisions, unknown field, forbidden fn
- `BvScd2CalculationTestRunnerTest` — column + collapse tests pass/fail
- Lineage test: `DERIVED` + `BV_SCD2_CALCULATION`
- XML round-trip of `.hbv` calculations + tests (`XmlMetadataUtil`, same pattern as existing SCD2 tests)

### Integration

New fixture under `integration-tests/tests/` (e.g. `scd2-calculations/`):

- One satellite (or reuse vault1 customer) with a deleted flag + date
- SCD2 calculations matching issue example 1 and 5
- Golden current-state CSV for calculated columns
- Column-level tests in the `.hbv` so Check model exercises them in CI

Because the evaluator is engine-independent, **Postgres is enough to prove expression values**. DDL for `VARCHAR(n)` from CAST **does** touch dialects — run the new suite through `./run-tests-all-databases.sh` before calling the DDL/layout slice done (project rule). Do not churn existing Customer 360 goldens unless a mapping is added there.

### Transform smoke

Local engine pipeline: Data Grid → SqlExpression → Dummy; JUnit already covers evaluator; optional `Pipeline.prepareExecution` smoke in unit tests (pattern used by other transform tests).

---

## 12. Key decisions

1. **Calculations are a new list on `BvScd2Table`, not mapping columns** — expressions are multi-input.
2. **Run after Group By collapse** — versions stay driven by mapped attributes; matches the requested test seam.
3. **Do not overwrite mapped target names in v1** — incremental baseline + recompute stay correct; both raw and calculated columns persist.
4. **Calcite parse + allow-listed Java evaluator** — SQL for authors, determinism by construction, no `NOW()`.
5. **New `SqlExpression` transform** — GUI-first, reusable for residual Free SQL / JDBC.
6. **Tests live on the SCD2 table** — column-level + post-collapse datasets; run in-process; Check model executes column tests.
7. **No dbt-SCD2 importer** — document the migration; #72 SQL tables remain the dump-SQL path.
8. **Preprocessor for `:>` and `::`** — issue examples are not standard CAST; Calcite default parser will not accept them.
9. **Residual CASE for source-model SQL is a follow-up PR** on the same engine, not a blocker for BV SCD2.

---

## 13. Out of scope

- Hybrid Type 1 columns on BV SCD2 (already rejected in `business-vault-scd2.adoc`)
- Using calculation outputs as Group By keys / version drivers
- `includeInTarget=false` working columns (incremental-unsafe without satellite re-read)
- Auto-converting imported dbt snapshot/SCD2 models into `.hbv` SCD2 + calculations
- Jinja inside calculation expressions
- Compiling Rex to Janino
- Hop Pipeline Unit Test metadata / testing plugin
- Dimensional-model hybrid SCD expressions (mart layer; separate issue if needed)
- Changing Hop core Formula/Calculator

---

## 14. Implementation order (PRs)

Independently reviewable slices. Each PR: `mvn spotless:apply`, unit tests, license headers.

### PR 1 — Expression engine + `SqlExpression` transform

- Engine, allow-list, cast rewrite, evaluator unit tests (all issue SQL examples)
- Transform Meta/Data/runtime + GuiCompositeWidgets dialog + help + icon + i18n
- No SCD2 wiring yet (palette-only)

**Done when:** Data Grid → SqlExpression unit/smoke works; forbidden functions fail at init.

### PR 2 — SCD2 metadata, dialog, pipeline, layout, lineage

- `BvScd2Calculation` on `.hbv`
- Calculations tab
- Insert transform after Group By; target layout + catalog types
- Check model validation
- Lineage / impact / search / AI schema
- Pipeline unit tests (`BvScd2PipelineSupportTest`)

**Done when:** Debug/Show build pipeline on a sample SCD2 table shows `SqlExpression` after `collapse_*`; empty calculations unchanged.

### PR 3 — Test builder

- Column-level tests + post-collapse tests on the table
- Dialog Test UI (inline grid + CSV via HopVfs)
- Check model runs column-level tests
- Docs: how to replace untested dbt CASE with locked tests

**Done when:** Check model fails a wrong expected value without touching a database.

### PR 4 — Integration fixture + docs + CHANGELOG

- `integration-tests` suite with deleted-flag CASE + CAST length
- Full DB matrix for that suite (DDL)
- `business-vault-scd2.adoc`, feature overview, dbt-import migration notes, dialog help

### PR 5 — Residual CASE in Free SQL / JDBC (follow-up)

- `RelToPipelineGenerator` emits `SqlExpression` for residual CASE/CAST/N-arg COALESCE
- Update `SupportedSqlFeatures`; extend `SourceModelSqlPhaseBTest` / JDBC tests
- **Status:** done on `issue-150`

---

## 15. Risks

| Risk | Mitigation |
|------|------------|
| Calcite `CASE` type inference vs Hop String/Date | Override types in the dialog; tests for mixed THEN/ELSE |
| `:>` rewrite false positives | Quote-aware scanner; always also accept standard `CAST` |
| Incremental double-apply | Never overwrite mapped names; always recompute from raw mapped attrs |
| Extra versions when a deleted flag changes but CASE output does not | Accepted: versioning is on mappings; document it |
| Performance on large SCD2 | Compile once; Java evaluator; Janino only if measured |
| Classloader (Calcite + Janino already in plugin) | Transform uses existing Calcite pin `1.40.0`; no new deps |
| Scope creep into dbt snapshot import | Keep importer docs as “SQL table vs SCD2+calculations” |

---

## 16. Suggested first implementation notes

- Reuse `HopTypeSystem` and `SourceModelRelPlanner` parser casing (`UNCHANGED`, case-insensitive).
- Do not call `RelToPipelineGenerator` from the SCD2 path (that engine plans `SELECT` queries, not per-row scalars).
- `applyScd2Layout` will need to treat `SqlExpression` as a node in the main chain (same as Group By).
- For the SCD2 dialog expression column, a `ColumnInfo.COLUMN_TYPE_TEXT` with a larger editor is enough; a full SQL editor widget is unnecessary.
