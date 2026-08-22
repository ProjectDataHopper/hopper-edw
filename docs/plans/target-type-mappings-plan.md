# Plan: Target type mappings for DDL (Issue #127)

**Issue:** [#127 — Allow preferences for target data type and DDL](https://github.com/ProjectDataHopper/hopper-edw/issues/127)

**Goal:** Let users declare reusable, first-match rules that map **Hop types** (plus length/precision) to **native SQL types** used in generated DDL. Custom rules run **before** Hop’s dialect `getFieldDefinition`. Unmatched columns keep today’s database-specific defaults.

This is the **inverse** of issue #113 (`Data type mapping`: source → Hop). Do **not** extend that metadata type.

---

## 1. Problem

Hop already has good control over **incoming** types (String / Integer / Date / Timestamp, length, precision) via **Data type mapping**. Physical DDL still comes only from Hop dialect rules:

| Hop type | Typical Postgres (`PostgreSqlDatabaseMeta.getFieldDefinition`) |
|----------|------------------------------------------------------------------|
| String(1) | `VARCHAR(1)` |
| String(n) | `VARCHAR(n)` |
| String unbounded | `TEXT` |
| Integer length &lt; 5 | `SMALLINT` |
| Timestamp / Date | `TIMESTAMP` (no time zone, no fractional seconds) |

Users want project policy such as:

- String length 1 → `CHAR(1)`
- String length ≤ 2000 → `NVARCHAR({length})` (or `VARCHAR({length})` on Postgres)
- Integer length ≤ 2 → `BYTE`
- Integer length ≤ 3 → `SMALLINT`
- Timestamp → `timestamp(6) with time zone`

Surfaces named in the issue:

- Pipeline transform **Record Definition DDL** (`RecordDefinitionDdl` → `CatalogTableDdlSupport` → `DvDdlSupport.buildCreateTableStatement`)
- Workflow action **Update resource definition group** (delegates to DV / BV / DM update → `IDvTable.generateUpdateDdl` → `DvDdlSupport.getTargetTableDdl`)

The same `DvDdlSupport` choke point is also used by:

- Single-model Data Vault / Business Vault / Dimensional Update
- Modeler “generate SQL” (`HopGuiVaultGraph`)
- Schema-gate target layout checks (`TargetSchemaValidationSupport`)
- Remediation DDL (`RemediationProposalApplySupport`)

Any solution that only patches the two dialogs will make those other paths emit different types. The engine must be shared.

---

## 2. What already exists (leverage)

| Asset | Use |
|-------|-----|
| `DataTypeMappingMeta` + editor + `Register*MetadataExtensionPoint` | Template for a **new** `@HopMetadata` type (do not reuse rules: different match, different output) |
| `DvDdlSupport.getFieldDefinition` | CREATE field lines; add rule application **before** `databaseMeta.getFieldDefinition` |
| `DvDdlSupport.getTargetTableDdl` | CREATE (our builder) vs ALTER (`db.getDDL()` → Hop core) |
| `CatalogTableDdlSupport` | Record Definition DDL; catalog/CRM staging (`applySqlServerUtf8EdwPolicy=false`) |
| `@GuiWidgetElement(type = METADATA)` on update actions | Operations / DDL tab selector |
| `DvSqlPhysicalTypeValidationSupport.resolveTargetSqlType` | Must use the same resolved SQL type as DDL |
| SQL Server UTF-8 rewrite in `DvDdlSupport` | Must **not** rewrite a column that a custom rule already typed |

Hop core `Database.getDDL` / `getAddColumnStatement` / `getModifyColumnStatement` call `IDatabase.getFieldDefinition` internally. We cannot change Hop. ALTER must be handled in this plugin (see §5.3).

---

## 3. Product design

### 3.1 Mental model

```
Hop IValueMeta (type, length, precision, name)
        │
        ▼
Target type mapping rules (first match wins)
        │ match → emit user SQL with ${vars} and {length}/{precision}
        │ no match → Hop dialect getFieldDefinition
        ▼
Optional SQL Server UTF-8 enrich (only if no rule matched)
        ▼
CREATE / ALTER / schema-check expected type
```

**Data type mapping (#113)** answers: “what Hop type is this source field?”  
**Target type mapping (#127)** answers: “what native column type do we CREATE/ALTER for this Hop type?”

### 3.2 New project metadata: `TargetTypeMappingMeta`

Package: `org.apache.hop.datavault.metadata.targettypemapping`

```
@HopMetadata(key = "target-type-mapping",
             name = "Target type mapping",
             image = "target-type-mapping.svg")
TargetTypeMappingMeta
  description
  targetDatabase          // optional DatabaseMeta name (issue: "Target db connection: vault")
  rules: List<TargetTypeMappingRule>   // ordered; first match wins
```

**`TargetTypeMappingRule`**

| Field | Role |
|-------|------|
| `enabled` | default true |
| `id` / `name` | optional labels for search / preview |
| `matchHopType` | Hop type name (`String`, `Integer`, `Timestamp`, …). Empty = any type (warn if no other criteria). |
| `matchMinLength` / `matchMaxLength` | Inclusive bounds; `TextVar`; empty = unbounded on that side |
| `matchMinPrecision` / `matchMaxPrecision` | Same for precision |
| `matchLengthAbsent` | Match only when length is missing / −1 |
| `matchFieldNamePattern` | Optional glob/regex (reuse `DataTypeMappingPatternSupport`) so technical columns can be excluded |
| `targetSqlType` | Native type template, e.g. `CHAR(1)`, `NVARCHAR({length})`, `timestamp(6) with time zone`. `TextVar`. |

Rules with no match criteria are invalid (same as #113).

Target SQL is **user-authored dialect text**. We do not translate `NVARCHAR` → Postgres `VARCHAR`. A soft **warning** is enough if the mapping’s `targetDatabase` dialect obviously cannot contain tokens like `NVARCHAR` / `BYTE` (optional; do not block save).

### 3.3 Placeholders and variables

Resolve in this order:

1. `IVariables.resolve(targetSqlType)` — `${VARCHAR_TYPE}`, etc.
2. Replace `{length}` and `{precision}` (case-insensitive) from the **Hop** `IValueMeta` (not from match bounds).

Rules:

- If the template still contains `{length}` and `valueMeta.getLength() < 0`, the rule **does not match** (fall through).
- Same for `{precision}` when precision is absent / negative and the token is present.
- Literal `CHAR(1)` (no placeholder) is valid — matches the issue examples.
- Do not invent extra tokens (`{len}`, `{scale}`) in v1.

### 3.4 Matching

A field matches a rule when **all** specified criteria match:

- Hop type (if set) equals `ValueMetaFactory.getValueMetaName(type)` (case-insensitive)
- Length in `[min, max]` when those bounds are set (after variable resolve; numeric)
- Precision in `[min, max]` when set
- `matchLengthAbsent` if requested
- Field name pattern if set

**First enabled matching rule wins.** More specific rules must be listed first (document this; e.g. String 1..1 before String max 2000).

Rules apply to **every** column including hash keys, `load_dts`, `record_source`. Users who need exceptions use `matchFieldNamePattern` (e.g. exclude `*_hk`). Hash keys are typically Binary, so String/Integer/Timestamp rules will not hit them.

### 3.5 Binding / resolution order

Do **not** invent a file-only config. Every binding is a GUI field.

**Resolution** (`TargetTypeMappingSupport.resolve`):

1. **Explicit name** on the current consumer (variable-resolved). Missing metadata name → error at DDL time.
2. Else **unique auto-match**: exactly one saved mapping whose `targetDatabase` equals the actual target `DatabaseMeta` name (case-insensitive). This is what makes the issue’s “Target db connection: vault” field operational for modeler SQL, schema gate, and updates without selecting the mapping on every action.
3. Else **Hop dialect defaults** (today’s behaviour).

If two mappings share the same `targetDatabase`, auto-match is skipped and a **warning** is logged / shown on check: select one explicitly.

**v1 GUI selectors (explicit name, optional):**

| Surface | Where | Why |
|---------|-------|-----|
| **Target type mapping** editor | Metadata perspective | Create/edit rules |
| **Record Definition DDL** | Target or DDL tab, `MetaSelectionLine` | Issue request; catalog/CRM tables have no model config |
| **Update resource definition group** | Operations tab, `@GuiWidgetElement(METADATA)` next to “Update target database structure” | Issue request |
| **Data Vault / Business Vault / Dimensional Update** | DDL tab, same widget | Group action **configures child actions**; also GUI parity for single-model updates |

Copy the group action’s mapping name onto child DV/BV/DM actions in `configureDataVaultUpdate` / `configureBusinessVaultUpdate` / `configureDimensionalUpdate`.

**v1 non-goals (do not add unless needed later):**

- Field on `ResourceDefinitionGroupMeta` (auto-match covers the common “one mapping per vault connection” case)
- Field on `DataVaultConfiguration` / BV / DM configuration (same reason; add later if auto-match proves too weak)
- Stacking multiple mappings
- Per-column overrides on `.hdv` / `.hbv` / `.hdm` tables

Absent mapping / empty selector / no unique auto-match = **100% backward compatible**.

---

## 4. Metadata model and GUI

### 4.1 Classes

```
org.apache.hop.datavault.metadata.targettypemapping/
  TargetTypeMappingMeta
  TargetTypeMappingRule
  TargetTypeMappingResolver          // match + placeholder substitution
  TargetTypeMappingSupport           // load by name, auto-match, resolve context
  TargetTypeMappingContext           // mapping + variables + which rule matched (for UTF-8 skip)
  TargetTypeMappingValidationSupport
  TargetTypeMappingMetaEditor
  TargetTypeMappingMetaSearchableAnalyser
  xp/RegisterTargetTypeMappingMetadataExtensionPoint
```

Mirror #113: `@HopMetadata`, Lombok `@Getter`/`@Setter`, Jandex via annotations, `HopEnvironmentAfterInit` registration.

### 4.2 Editor (GUI parity)

Mirror `DataTypeMappingMetaEditor` (simpler: no Scope tab).

- Name, description
- **Target database** (`MetaSelectionLine<DatabaseMeta>` / combo of connections)
- **Rules** `TableView`: Enabled, Hop type combo, min/max length, min/max precision, length absent, field pattern, target SQL type (`TextVar`)
- Add / duplicate / move up / move down / Validate
- **Preview** (same editor): sample Hop type + length + precision + field name → resolved SQL (or “dialect default”). Uses the mapping’s target connection for fallback.
- Help topic (`HelpTopics.TARGET_TYPE_MAPPING`) describing placeholders, first-match, UTF-8 interaction, and the difference from Data type mapping

i18n: `src/main/resources/org/apache/hop/datavault/metadata/targettypemapping/messages/messages_en_US.properties`  
Escape `=` / `:` / spaces; wrap `'${VARIABLE}'` and `'{length}'` as in existing bundles.

Icon: new `src/main/resources/target-type-mapping.svg` (same visual family as `data-type-mapping.svg`).

### 4.3 Record Definition DDL

Add to `RecordDefinitionDdlMeta`:

```
@HopMetadataProperty(key = "target_type_mapping")
String targetTypeMappingName;  // optional, variables allowed
```

Dialog: `MetaSelectionLine<TargetTypeMappingMeta>` on the Target (or DDL) tab.  
`clone()` must copy the field.  
`RecordDefinitionDdl` / `CatalogTableDdlSupport.applyTableDdl` / `generateCreateTableDdl` take the resolved context.

### 4.4 Update actions

Add `targetTypeMapping` (`GuiElementType.METADATA`, `metadata = TargetTypeMappingMeta.class`, `variables = true`) on:

- `ActionUpdateResourceDefinitionGroup` — Operations tab, order ~`0605` (between structure update and fail-if-DDL)
- `ActionDataVaultUpdate`, `ActionBusinessVaultUpdate`, `ActionDimensionalUpdate` — DDL tab

Copy-constructors / clone tests must include the field.

Child configuration in the group action sets the same name on each child.

---

## 5. Engine: apply rules in DDL

### 5.1 Context object

```
TargetTypeMappingContext
  mapping: TargetTypeMappingMeta | null
  variables: IVariables
```

`DvDdlSupport.getFieldDefinition(databaseMeta, valueMeta, applyUtf8, context)`:

1. If `context` has a mapping, ask `TargetTypeMappingResolver.resolveSqlType(valueMeta, ruleSet, variables)`.
2. On hit: return Hop-shaped definition = quoted field name + `' '` + resolved type (same `addFieldname` / `addCr` behaviour as today’s 4-arg `getFieldDefinition`). **Do not** run SQL Server UTF-8 length×3 / `COLLATE` on that column.
3. On miss: existing `databaseMeta.getFieldDefinition` + optional UTF-8 enrich.

Keep existing overloads; default `context = null` (current behaviour).

Thread `context` through:

- `buildCreateTableStatement` (all overloads that emit field lines)
- `getTargetTableDdl` / `getCreateTableDdl`
- `CatalogTableDdlSupport.generateCreateTableDdl` / `buildTableDdlScript` / `applyTableDdl`
- `DvTableBase.generateTargetTableDdl` (and BV / DM table bases)
- `RemediationProposalApplySupport.generateDdlWithForcedFieldLength`

### 5.2 How tables obtain context

`DvTableBase.generateUpdateDdl` already has `IHopMetadataProvider`, `IVariables`, model config (`targetDatabase`).

```
String explicit = firstNonEmpty(
    variables.resolve(actionProvidedName),   // see below
    null);
TargetTypeMappingContext ctx =
    TargetTypeMappingSupport.resolve(explicit, targetDatabaseMeta, provider, variables);
```

**Action → table channel** without exploding `IDvTable` in v1:

- Documented variable `DATAVAULT_TARGET_TYPE_MAPPING` (optional).
- Update actions, when their widget is non-empty, set that variable on the action’s variable space **before** `generateUpdateDdl`.
- Group action copies the name onto children; children set the same variable.
- Modeler / schema gate leave it unset → auto-match or Hop default.

Alternatively (slightly cleaner, a few more signatures): add an overload `generateUpdateDdl(..., String targetTypeMappingName)` used only by the update actions. Prefer the **overload** if the call sites stay small; use the variable only as a user-facing escape hatch (issue asked for variables on the mapping **name** field via `TextVar` / `METADATA` + variables).

Recommended: **overload + explicit action field**. Do not rely on a hidden variable as the only channel.

### 5.3 ALTER (required, not a follow-up)

`getTargetTableDdl` today:

- Table missing → often `CREATE` via `buildCreateTableStatement` (rules apply once threaded).
- Table exists → `db.getDDL()` which compares **Hop** field definitions. A `CHAR(1)` preference would **never** ALTER an existing `VARCHAR(1)`, and new ADD COLUMN lines would use Hop types.

When a mapping context is present (or always, if we implement our own ALTER helper and use it whenever context ≠ null):

1. Read current columns with `db.getTableFields(tableName)`.
2. **ADD** missing columns: start from `databaseMeta.getAddColumnStatement(...)`, replace the Hop type token (`getFieldDefinition(..., addFieldname=false)`) with our resolved type. Drop-column statements stay Hop’s (no type).
3. **MODIFY** when normalized **physical** current type ≠ normalized **desired** type:
   - Desired = resolved rule type or Hop fallback.
   - Current = `originalColumnTypeName` + length/precision from JDBC, normalized (`CHAR(1)` vs `character(1)` vs `bpchar`). **Do not** compare Hop’s re-interpretation of the current column (JDBC `CHAR(1)` becomes Hop String(1) → `VARCHAR(1)`, which would always look like drift).
4. Build MODIFY via Hop’s `getModifyColumnStatement` and the same type-token replacement (Postgres modify embeds `getFieldDefinition` several times — replace the **type token**, not the whole `name TYPE` string, so tmp-column names still work).

When context is null, keep calling `db.getDDL()` unchanged.

Idempotence: a second update after CREATE with the same mapping must emit **no** ALTER.

### 5.4 SQL Server UTF-8 policy

- Rule matched → emit the user’s type verbatim (`NVARCHAR({length})` is skipped by the existing ANSI-only regex; `VARCHAR({length})` must also skip enrich so we do not surprise with `×3` + `COLLATE`).
- No rule → existing vault/EDW enrich (`applySqlServerUtf8EdwPolicy=true`). Catalog/CRM path stays `false`.

### 5.5 Validation / expected types

`DvSqlPhysicalTypeValidationSupport.resolveTargetSqlType` must call `DvDdlSupport.getFieldDefinition(..., context)` (or the resolver) so sort-sensitive checks see `NVARCHAR` vs `VARCHAR` the same way DDL does.

Schema-gate `generateUpdateDdl` picks up the mapping via auto-match or the action variable/overload — expected CREATE/ALTER then matches what Update will run.

### 5.6 Missing / invalid mapping

- Explicit name set but metadata missing → **fail DDL** with a clear message (do not silently fall back).
- Mapping has zero enabled rules → warning; fall back to Hop.
- Unparseable min/max after variable resolve → treat that bound as unset and warn (or fail the rule); prefer **fail the rule** (skip it) so we never apply a half-resolved range.

---

## 6. Validation (design-time)

`TargetTypeMappingValidationSupport`:

| Severity | Condition |
|----------|-----------|
| ERROR | Empty metadata name; enabled rule with no match criteria; empty `targetSqlType` |
| ERROR | Unknown `matchHopType` |
| ERROR | min length/precision &gt; max when both set (after resolving literals; skip if still `${var}`) |
| WARNING | `targetDatabase` empty (auto-match will never pick this mapping) |
| WARNING | Two mappings in the project share the same `targetDatabase` |
| WARNING | `{length}` in template but rule also sets `matchLengthAbsent` (can never match) |
| WARNING | Very broad rule (type-only Timestamp / all Strings) — informational |

Editor Validate + metadata save checks. Update action / Record Definition DDL `check()`: if name is set, metadata must exist.

---

## 7. Testing

### 7.1 Unit (required)

- Resolver: issue examples (String 1→CHAR(1), String max 2000→NVARCHAR({length}), Integer 2→BYTE, Integer 3→SMALLINT, Timestamp→timestamptz); first-match order; length absent; `{length}` with length −1 skips; `${VAR}` then `{length}`; field-name exclude; disabled rules.
- `DvDdlSupport.buildCreateTableStatement` with context vs without (Postgres + SQL Server fixtures already used in `DvDdlSupportTest`).
- SQL Server: matched `NVARCHAR(10)` is **not** expanded / collated; unmatched `String(10)` still is.
- ALTER helper: ADD uses custom type; existing physical CHAR(1) vs desired CHAR(1) → no MODIFY; VARCHAR(1) vs desired CHAR(1) → MODIFY.
- `CatalogTableDdlSupport.generateCreateTableDdl` honors context.
- Action clone tests + `RecordDefinitionDdlMeta.clone()` copy the new field.
- Auto-match: one mapping for `Vault` selected; two mappings → none; explicit name wins.

Do **not** edit existing integration golden CSVs.

### 7.2 Integration (DDL-sensitive — full matrix before done)

New **opt-in** fixture (new metadata file + small table or isolated suite), **not** wired into retail/default goldens:

- Mapping attached / auto-matched for the test vault connection.
- Assert generated CREATE contains `CHAR(1)` / `TIMESTAMP(6) WITH TIME ZONE` (or engine-appropriate literals you choose for the fixture).
- Run via `integration-tests/run-tests-all-databases.sh` because type tokens differ by engine.

While iterating: `mvn test` + Postgres `./run-tests.sh` for the new suite only.

### 7.3 GUI

No SWT automation required for v1. Manual smoke: create mapping, preview, select on Record Definition DDL and Update resource definition group.

---

## 8. Documentation

- New `docs/target-type-mappings.adoc` — concepts, rule table, placeholders, resolution order, UTF-8 note, worked examples from the issue, contrast with `docs/data-type-mappings.adoc`.
- Link from `docs/data-type-mappings.adoc`, `docs/feature-overview.adoc`, `docs/update-resource-definition-group-action.adoc` (Operations), `docs/datavault-update-action.adoc`, `docs/datavault-configuration.adoc` (brief), `docs/README.md`.
- New `docs/record-definition-ddl.adoc` (transform is currently only mentioned in passing) **or** a section under catalog transform docs — include the mapping selector.
- `docs/ai-file-schemas/metadata/target-type-mapping.md` (+ list in that README).
- `docs/plans/target-type-mappings-plan.md` — copy of this plan after approval.
- Help markdown under `src/main/resources/org/apache/hop/datavault/hopgui/help/`.
- `CHANGELOG.md` Unreleased.

Do **not** add a mapping to `retail-example` or `integration-tests` project metadata unless the new suite needs it — auto-match would change existing vault DDL goldens.

---

## 9. Implementation phases

### Phase A — Metadata + resolver (no behaviour change)

1. `TargetTypeMappingMeta` / `Rule` / `Resolver` / `ValidationSupport` / editor / search analyser / register XP / i18n / icon.
2. Unit tests for resolver + validation.

**Exit:** Can define `postgres-target-type-rules` in Metadata perspective and resolve the issue’s sample fields in tests.

### Phase B — DDL engine

1. `TargetTypeMappingSupport.resolve` (explicit + auto-match).
2. Thread `TargetTypeMappingContext` through `DvDdlSupport` CREATE path.
3. ALTER helper when context present; physical-type comparison.
4. `CatalogTableDdlSupport` + table bases + remediation + physical-type validation.
5. Unit tests for CREATE/ALTER/UTF-8.

**Exit:** Programmatic CREATE/ALTER uses rules; no context → identical to current SQL.

### Phase C — Consumer GUI + wiring

1. Record Definition DDL field + dialog + transform runtime.
2. Group update + three single-model update actions (widget, clone, child copy).
3. `generateUpdateDdl` overload (or equivalent) from actions.
4. Clone / meta tests.

**Exit:** Both issue surfaces can select a mapping; group update children inherit it; modeler/schema gate auto-match when exactly one mapping targets the vault connection.

### Phase D — Docs + isolated integration fixture

1. Docs + help + CHANGELOG + AI schema notes.
2. New integration suite; full DB matrix for that suite.

---

## 10. Key decisions

| Topic | Decision | Why |
|-------|----------|-----|
| Separate metadata from #113 | New `target-type-mapping` | Different direction (Hop → SQL), different match/output |
| Output | Raw SQL type template, not a Hop type | Issue examples are dialect tokens |
| Rule order | First match wins | Same as source mappings; user puts CHAR(1) before VARCHAR({length}) |
| Variables | `${…}` via `IVariables` then `{length}` / `{precision}` | Issue requirement |
| Binding | Explicit selector on Record DDL + update actions; unique auto-match by `targetDatabase` | Issue’s “Target db connection” field; keeps modeler/schema gate consistent without extra model-config fields |
| Unmatched columns | Hop dialect unchanged | Backward compatible |
| SQL Server UTF-8 | Skip when a rule matched | User owns the type string |
| ALTER | Implement in plugin; compare **physical** current vs desired | Hop `db.getDDL()` cannot see custom types; JDBC→Hop would false-diff CHAR vs VARCHAR |
| Technical columns | Rules apply; exclude with field-name pattern | Timestamp→timestamptz on `load_dts` is desirable |
| Retail / existing goldens | No default mapping in those projects | Auto-match would rewrite vault DDL |
| Hop core | No patches | Pin stays 2.19.0 |

---

## 11. Out of scope (v1)

- Changing Hop `IDatabase.getFieldDefinition` or JDBC drivers
- Inferring rules from an existing physical schema
- Per-table / per-field overrides on models
- Multiple mappings stacked on one consumer
- Rewriting source Data type mapping (#113)
- Shipping org-standard mappings that alter retail or current integration goldens

---

## 12. Success criteria

1. Architect can create a named **Target type mapping** in Hop Metadata (full GUI).
2. Issue examples resolve correctly with `{length}` / `{precision}` and `${variables}`.
3. **Record Definition DDL** and **Update resource definition group** can select the mapping; group children inherit it.
4. Unselected + no unique auto-match → identical DDL to today.
5. CREATE and ALTER (ADD/MODIFY) use the same resolved types; second run is a no-op.
6. SQL Server UTF-8 policy does not mutate a rule-provided type.
7. Feature is operable only through GUI + existing metadata files (no side-channel config).
8. `mvn test` green; new DDL fixture proven on the full database matrix before the change is treated as done.

---

## 13. Suggested implementation order (after leaving plan mode)

1. Phase A in `…/metadata/targettypemapping/`.
2. Phase B in `DvDdlSupport` + `CatalogTableDdlSupport` + table DDL entry points.
3. Phase C widgets and action/transform wiring.
4. Phase D docs + isolated integration tests.
5. `mvn spotless:apply` and `mvn test`; then `integration-tests/run-tests-all-databases.sh` for the new fixture.
