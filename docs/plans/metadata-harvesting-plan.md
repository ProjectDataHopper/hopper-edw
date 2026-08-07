# Plan: Metadata Harvesting as a Distinct EDW Update Phase (Issue #112)

## Problem statement

Today, looking up **live source metadata** is intertwined with the warehouse update path:

| When | What happens | What is stored |
|------|----------------|----------------|
| **Validate resource definitions** (`LIVE_SOURCE`) | Per-record live discovery + diff vs catalog/version | Ephemeral report (MD/HTML); **no durable harvest history** |
| **Check model / DV Update detailed type checking** | Live schema reads during model validation | Warnings only; catalog unchanged |
| **Refresh from source** (GUI / proposals) | Ad-hoc rediscovery | Optional **rewrite** of catalog contract |
| **Catalog versions** | Design-time **freeze of working-tree** contracts | Immutable tags under FILE catalog — **not** time-series of live systems |
| **Database Table Metadata** transform | One-shot discovery for import pipelines | Rows in a pipeline stream only |

[Issue #112](https://github.com/mattcasters/hop-data-vault/issues/112) asks for:

1. **Transparency** — treat “gather source metadata into the catalog world” as a first-class operational step, not a side effect of load.
2. **Scale** — thousands of record sources; a full pass over a catalog or a **Resource definition group** (all sources used by DV/BV/DM models).
3. **Durability** — after every run, **persist** discovered layouts and differences (missing/extra fields, width/type/PK/FK changes, missing tables) in a **database table or separate catalog store**.
4. **Efficiency** — harvest **per unique source database** (bulk table/column metadata) instead of N independent open-and-discover cycles.
5. **Early drift detection** — detect schema drift **without** running DV/BV/DM model checks or loads.
6. **Exploration UI** — browse drift over time by record source, source system (connection), type, etc.

This mirrors the architectural split already used for **data quality**: **measure** (observe) vs **disposition** (gate/alert) vs **load**.

---

## Current building blocks to reuse

Do **not** reinvent discovery or diffing.

| Component | Package / type | Role in harvesting |
|-----------|----------------|--------------------|
| `RecordDefinitionDiscoveryService` | `catalog.discovery` | Live fields for DATABASE / CSV / PARQUET / ICEBERG / COMPOSITE |
| `RecordDefinitionSchemaDiffSupport` | `catalog.discovery` | ADDED / REMOVED / CHANGED / PRIMARY_KEY_CHANGED |
| `SourceRecordValidationService` + `SourceUsageIndexBuilder` | `datavault.resourcedefinition` | Scope sources from a resource definition group |
| `ParallelValidationSupport` | same | Bounded concurrency (default 8, max 64) |
| `DatabaseTableMetadata` / `DatabaseTableMetadataSupport` | `catalog.transform.tablemetadata` | JDBC column + PK (+ FK) discovery, empty tables OK |
| `DvDatabaseSourceLiveSchemaSupport` | `datavault.metadata.database` | Model-check live path (should eventually prefer harvest cache) |
| `DataQualityHistoryPublisher` / `Reader` / `DdlSupport` | `quality.history` | **Pattern** for OPS tables, catalog publish of ops defs, GUI browser |
| `QualityHistoryBrowserDialog` | `catalog.hopgui.perspective` | **Pattern** for history UI |
| Catalog versions | `catalog.versioning` | Design-time baseline tags — complementary, not a substitute for harvest history |
| `ActionValidateResourceDefinitions` | workflow action | Existing **gate**; should consume harvest results later, not re-discover always |

---

## Design principles

### 1. Harvest is observation, not load

```text
[Harvest metadata]  →  persist snapshots + diffs
        ↓
[Optional gate]     →  fail/warn on drift vs catalog or baseline harvest
        ↓
[Quality measure/gate]  (content — already separate)
        ↓
[Update resource definition group / DV·BV·DM]
```

Harvest **never** writes business data and, by default, **does not rewrite** working-tree catalog contracts (same rule as length remediation: catalog is the contract; live source is measured against it).

### 2. Measure vs disposition (same as quality)

| Stage | Responsibility | Can fail the workflow? |
|-------|----------------|------------------------|
| **Harvest** | Discover live layouts; write history tables (and optional JSON reports) | Only on **infrastructure** failure (connection, unreadable required source), not on drift itself |
| **Schema gate / evaluate drift** | Policy on harvest (or re-run compare): fail on blocking drift kinds | Yes, depending on severity policy |
| **Catalog refresh** (optional, explicit) | Promote live snapshot → working catalog (or remediation package) | Separate action / GUI — never implicit in harvest |

### 3. Scope sources without coupling to model structure

Three scope modes (recommended):

| Scope | Meaning | Typical use |
|-------|---------|-------------|
| **Resource definition group** | All `DV_SOURCE` (and other feed types) referenced by the group’s models | Production EDW wave |
| **Catalog namespace / connection** | All sources under a FILE catalog connection (e.g. `hop/project/sources`) | Full inventory harvest |
| **Filter** | Record-source **group** tag, connection name, source type | Hourly CRM only |

Resource definition group remains the default for the retail-style orchestration chain.

### 4. Efficiency: connection-batched discovery

Today validation maps **one task per record definition** and rediscovers each table independently. For thousands of tables this wastes:

- connection open/close
- repeated `DatabaseMetaData` round-trips
- VPN latency

**Target harvest engine:**

1. Resolve harvest subjects → physical refs.
2. **Partition by** `(sourceType, databaseConnectionName)` (and file-root / Iceberg catalog where relevant).
3. For each database partition:
   - Open **one** connection.
   - Prefer **bulk** JDBC metadata for the set of schemas/tables needed (reuse `Database.getTableFieldsMeta` / PK / FK discovery paths already used by `DatabaseTableMetadataSupport`).
   - Emit a field list per table; map back to record definition keys.
4. For non-DB types, keep existing per-source discovery (CSV/Parquet sample, Iceberg, COMPOSITE via `.hsm` projection).
5. Diff each discovered layout against the **expected contract** (working catalog and/or catalog version tag).
6. Persist harvest run + subject snapshots + field rows + change events.

Parallelism should be primarily **across connections / source systems**, not across every table on the same DB (avoids connection storms).

### 5. GUI parity

No file-only or action-only capability. Required surfaces:

- Workflow **Harvest source metadata** action (dialog + i18n).
- Design-time control on **Resource definition group** (e.g. “Harvest sources…”).
- **Schema harvest history** browser (catalog perspective and/or group editor), analogous to quality history.
- Optional: model-check / schema gate option “use last harvest” to avoid double live hits in the same window.

---

## Storage options (evaluation)

### Option A — OPS relational history (recommended primary)

Mirror `quality_run` / `quality_finding`:

| Table | Purpose |
|-------|---------|
| `schema_harvest_run` | Run id, started/finished, group, scope, status, execution id, counts |
| `schema_harvest_subject` | Per record definition: connection, schema, table, source type, in_sync, error |
| `schema_harvest_field` | Discovered (and/or expected) field name, Hop type, length, precision, PK pos, native type |
| `schema_harvest_change` | Diff events: kind, field, expected vs actual detail |

**Pros:** time-series SQL, same OPS connection as metrics/quality, easy UI, retention jobs.  
**Cons:** needs DDL ensure; multi-dialect (reuse quality DDL patterns).

### Option B — FILE “harvest catalog” / JSON under storageDirectory

e.g. `catalog-harvests/<run-id>/…` next to `catalog-versions/`.

**Pros:** no OPS DB required for small projects.  
**Cons:** weak for large time-series, multi-engine reporting, concurrent CI; does not match ops analytics.

### Option C — Reuse catalog-versions tags

**Reject as primary store.** Tags are **design-time contracts**, not automatic live observations. Promoting a harvest to a version tag can be a **later optional step**.

### Option D — Separate metadata DB product (DataHub, Atlas, …)

Out of scope for MVP; OpenLineage/Marquez already covers job/dataset lineage, not Hop catalog contracts.

**Recommendation:** **Option A as primary**, optional Markdown/HTML report files (same style as schema gate), optional later export of a harvest snapshot into a catalog version tag.

---

## Conceptual data model (MVP)

```text
schema_harvest_run
  harvest_run_id (PK / UUID)
  started_at, finished_at
  resource_group_name
  catalog_connection
  expected_baseline   -- WORKING | VERSION:<tag>
  scope_json          -- filters
  status              -- SUCCESS | PARTIAL | FAILED
  subject_count, change_count, error_count
  hop_workflow_execution_id / DV_WORKFLOW_EXECUTION_ID (nullable)

schema_harvest_subject
  harvest_run_id, subject_key (namespace/name)
  source_type, database_meta_name, schema_name, table_name
  discovery_status  -- OK | UNAVAILABLE | ERROR
  in_sync (bool)
  change_count
  message

schema_harvest_field
  harvest_run_id, subject_key
  field_name, hop_type, length, precision
  primary_key_position
  source_data_type
  role  -- DISCOVERED | EXPECTED (or two columns side-by-side)

schema_harvest_change
  harvest_run_id, subject_key
  change_kind   -- FIELD_ADDED | FIELD_REMOVED | TYPE_CHANGED | LENGTH_CHANGED | PK_CHANGED | TABLE_MISSING | ...
  field_name
  expected_detail, actual_detail
  severity      -- derived policy or fixed mapping
```

Publish catalog **record definitions** for these OPS tables (same pattern as quality history) under `hop/{project}/operations/` so lineage/quality tooling can see them.

---

## Workflow and product placement

### Recommended production chain

```text
Harvest source metadata          ← NEW (durable observation)
  → Validate resource definitions  (gate; ideally reuse harvest or last run)
  → Measure / Evaluate source quality
  → Update resource definition group
  → (optional) Measure target quality ALERT_ONLY
```

Retail docs (`operations.adoc`, `update-resource-definition-group-action.adoc`) today start at the schema gate; harvest becomes the **first** explicit metadata step.

### Relationship to Validate resource definitions

| Concern | Harvest | Validate (schema gate) |
|---------|---------|------------------------|
| Live discovery | Primary | Can skip if harvest just ran / “use harvest run id” |
| Catalog vs version | Optional expected baseline | Already has compare modes |
| Target DB DDL | Out of scope | Keep on validate / model update |
| Downstream impact / lineage | Optional later | Already exists |
| Persistence | Always (when OPS configured) | Report files only |

Avoid duplicating full LIVE_SOURCE discovery twice in one night. Phase 2: gate accepts `HARVEST_RUN_ID` or “latest successful harvest for group”.

### Relationship to detailed type checking on DV Update

Long term: when a recent harvest exists for a subject, model check should prefer **harvested field list** over a new live JDBC hit. Config:

- default: use harvest if age &lt; N minutes / same execution id
- fallback: live discovery (current behavior)

This is an optimization phase after durable harvest works.

---

## UI surfaces (GUI parity)

### 1. Workflow action: **Harvest source metadata**

Dialog tabs (sketch):

- **Selection:** resource definition group **or** catalog connection + namespace; optional record-source group / connection filter; source types.
- **Baseline:** working catalog (default) vs catalog version tag (for change detection vs frozen contract).
- **Operations:** history database (default OPS / `SCHEMA_HARVEST_DATABASE`), schema; ensure tables; publish catalog defs.
- **Performance:** max concurrent **connections** (not tables); optional dry-run (discover + report, no DB write).
- **Reports:** optional MD/HTML path (summary of drift).

Action result: success/partial/fail; set variables e.g. `DV_SCHEMA_HARVEST_RUN_ID`, counts.

### 2. Resource definition group editor

Buttons next to Validate sources:

- **Harvest sources…** — same engine as the action (design-time run).
- **Browse harvest history…** — open history browser for this group.

### 3. Schema harvest history browser

Inspired by `QualityHistoryBrowserDialog`:

- Filter: group, connection, source type, date range, only-with-changes.
- Master: harvest runs.
- Detail: subjects with drift; expand to field changes.
- Actions: open record in catalog; jump to Validate with same baseline; optional “refresh catalog from this harvest” (explicit confirm).

### 4. Catalog perspective

On a `DV_SOURCE` detail panel: “Harvest history” (subject-level timeline), parallel to quality history.

---

## Efficiency design (detail)

### Database batch path

For subjects with the same Hop `DatabaseMeta`:

1. Group by resolved schema.
2. For each table in the group, call existing discovery helpers on a **shared** `Database` instance (single connect).
3. Optionally, if JDBC supports efficient `getColumns(catalog, schema, null, null)` for a schema with many needed tables, pre-load column maps once and slice per table — **measure** on real engines (Postgres, MySQL, SQL Server, SingleStore) before relying on bulk `getColumns` (dialect quirks).

### COMPOSITE / file / Iceberg

- COMPOSITE: rediscover from `.hsm` projection (no source DB table); still store snapshot + diff vs catalog.
- Files: existing sample discovery; harvest still records “observed” layout.
- Iceberg: existing metadata discovery.

### Caching within a workflow execution

In-memory `HarvestResultCache` keyed by physical ref + harvest_run_id, injectable into validation and model check for the same process (Docker Hop run). Cross-process reuse = OPS tables only.

---

## Phased delivery

### Phase 0 — Spec lock (this plan + acceptance criteria)

- Confirm OPS as primary store.
- Confirm harvest does not auto-update catalog.
- Confirm default scope = resource definition group.
- Document relationship to validate / quality / update.

### Phase 1 — Harvest engine + OPS persistence + workflow action

**Goal:** operators can harvest all sources for a group, store history, get a report, without loading the EDW.

Deliverables:

1. Domain types: `SchemaHarvestRun`, subject, field, change (immutable records).
2. `SchemaHarvestService`:
   - resolve subjects from RDG (reuse usage index / group model discovery);
   - connection-batched discovery;
   - diff vs working catalog (and optional version tag);
   - produce in-memory report.
3. `SchemaHarvestHistoryDdlSupport` + `SchemaHarvestHistoryPublisher` + `SchemaHarvestHistoryReader` (copy patterns from quality history).
4. `ActionHarvestSourceMetadata` + dialog + messages + Jandex discovery.
5. Unit tests: diff persistence, batch grouping, DDL for Postgres/MySQL/SQL Server dialects (as quality does).
6. Docs: `docs/metadata-harvesting.adoc`, feature-overview row, operations chain update.
7. Retail sample: optional step in `run-retail-update.hwf` (or a dedicated `run-retail-harvest.hwf`) writing to OPS.

**Out of Phase 1:** gate integration, model-check cache, rich multi-filter UI polish, FK-level change kinds if not free from existing discovery.

### Phase 2 — Schema gate integration + optional disposition action — **DONE**

1. `SchemaCompareMode.HARVEST_RUN` + `SchemaHarvestHistoryReader` + `HarvestBackedValidationSupport`.
2. `ActionValidateResourceDefinitions`: harvest run id / history DB fields; gate severity via existing policy.
3. Severity: REMOVED / PK → BLOCKING; ADDED / CHANGED → WARNING; unavailable → BLOCKING.
4. Retail `run-retail-update-models.hwf`: Harvest → Validate (HARVEST_RUN) → Quality → Update.

### Phase 3 — Exploration UI — **DONE**

1. Group browser: `SchemaHarvestHistoryBrowserDialog` (runs + subjects + filters).
2. Subject timeline: `SchemaHarvestSubjectHistoryDialog`.
3. Drill-down: `SchemaHarvestChangesDialog` (changes + EXPECTED/DISCOVERED fields).
4. Wiring: Resource definition group **Harvest history…**; catalog Quality tab **Schema harvest…**; Open in catalog via `DataCatalogPerspective.selectRecordDefinition`.

### Phase 4 — Model-check / load path efficiency — **DONE**

1. `SchemaHarvestModelCheckSupport` loads DISCOVERED fields → `DvModelCheckCache` keys.
2. `DvModelCheckOptions.preferHarvestForLiveFields` + harvest run/db settings.
3. Data Vault Update + Update resource definition group UI options (default on; fall back to live).
4. Parallel group checks load harvest once and pre-seed each worker cache.

### Phase 5 — Optional enhancements

- Promote harvest snapshot → catalog version tag.
- **PK/FK drift** — **DONE** (FK discovery + OPS `schema_harvest_fk` + catalog optional FK attrs; PK via existing PRIMARY_KEY_CHANGED).
- **Apply harvest → catalog FKs** — **DONE** (`SchemaHarvestCatalogApplySupport` + RDG **Apply harvest to catalog…**).
- **Generate / merge `.hsm` from harvest FKs** — **DONE** (`SchemaHarvestSourceModelGenerator` + RDG **Generate .hsm from harvest…**).
- Harvest of **target** EDW layouts (orthogonal; closer to target DDL checks).
- Retention purge action for old harvest runs.
- Marquez facet export of harvested schema (optional).
- Workflow action wrappers for apply-catalog / generate-hsm (GUI path is primary).

---

## Non-goals (MVP)

- Replacing catalog versions or git for model files.
- Auto-rewriting catalog contracts on every harvest.
- Harvesting non-source model internals (hub/sat structure is model check).
- Full enterprise metadata repository product.
- Reading row data (that is quality measure).

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Double work if harvest + LIVE_SOURCE both hit DB | Phase 2: gate reuses harvest; Phase 1 docs say “prefer harvest then validate with reports only for non-live axes” |
| OPS dialect DDL bugs | Reuse quality history DDL support patterns; multi-DB unit tests |
| Connection storms | Parallelize by connection, serial tables within connection by default |
| COMPOSITE “live” is model projection | Document; still harvest for contract consistency |
| Thousands of field rows per run | Index `(harvest_run_id, subject_key)`; optional “store fields only when changed” later |
| Confusion with catalog versions | UI copy: “Harvest = observed live; Version = frozen design contract” |

---

## Acceptance criteria (Phase 1)

1. Workflow action harvests all database sources referenced by a resource definition group without running DV/BV/DM update.
2. Each run inserts one `schema_harvest_run` and per-subject rows; field/change rows for diffs (and at least discovered fields for subjects with changes — full field snapshot preferred).
3. Diff kinds cover added/removed fields, type/length/precision changes, PK position changes, source unavailable.
4. Connection batching uses a single connection per database meta for the tables in that partition (verified by unit/integration test or logging).
5. Action does **not** modify working catalog or catalog versions.
6. GUI dialog exists; strings in `messages_*.properties` with proper escaping.
7. Lombok on new model/action classes; HopVFS only if writing report files.
8. Docs describe the distinct EDW update phase and recommended chain.
9. Unit tests green (`mvn test`); OPS DDL smoke on H2/Postgres-style as quality history.

---

## Implementation sketch (packages)

```text
org.apache.hop.catalog.harvest/
  SchemaHarvestModels.java          # records
  SchemaHarvestService.java         # orchestrate resolve → discover → diff
  SchemaHarvestSubjectResolver.java # RDG / namespace scope
  SchemaHarvestConnectionBatcher.java
  history/
    SchemaHarvestHistoryDdlSupport.java
    SchemaHarvestHistoryPublisher.java
    SchemaHarvestHistoryReader.java

org.apache.hop.datavault.workflow.actions.harvestmetadata/
  ActionHarvestSourceMetadata.java
  ActionHarvestSourceMetadataDialog.java

org.apache.hop.catalog.hopgui.perspective/
  SchemaHarvestHistoryBrowserDialog.java   # Phase 3
```

Prefer **catalog** package for harvest (source-agnostic observation); workflow action can live under `datavault.workflow.actions` for consistency with validate/update, or under catalog if we want symmetry with quality under `quality.workflow`. Recommendation: **engine in `catalog.harvest`**, action under `datavault.workflow.actions.harvestmetadata` next to validate (ops chain co-location).

---

## Alternatives considered

| Approach | Why not primary |
|----------|-----------------|
| Only enhance Validate to write history | Gate remains policy-heavy; harvest needs independent schedule and bulk efficiency |
| Only bulk import pipelines (Database Table Metadata + RDO) | No standard history model, no RDG scope, no first-class ops UI |
| Catalog-versions every night | Wrong abstraction (design tags vs ops observations); tag explosion |
| Store only diffs, no field snapshots | Harder to reconstruct “what did the source look like on date X” |

---

## Suggested PR breakdown

1. **PR1 — Models + harvest service + connection batching + unit tests** (no action yet; testable core).
2. **PR2 — OPS DDL/publisher/reader + Action + dialog + i18n + docs**.
3. **PR3 — Retail/integration wiring + report files**.
4. **PR4 — Gate integration (use harvest)**.
5. **PR5 — History browser UI**.
6. **PR6 — Model-check cache from harvest**.

---

## Open decisions (defaults recommended)

| Topic | Recommended default |
|-------|---------------------|
| Primary store | OPS tables (Option A) |
| Catalog mutation on harvest | Never (explicit refresh remains separate) |
| Default scope | Resource definition group |
| Expected baseline for diffs | Working catalog |
| Parallelism unit | Per database connection |
| Field storage | Full discovered field list per subject per run (simpler, space later) |
| Fail action on drift | No — only infrastructure failures; drift via gate |
| Package home for engine | `org.apache.hop.catalog.harvest` |

---

## Success picture

Operators treat metadata like a first-class warehouse concern:

1. **Nightly (or pre-wave):** Harvest — “What do sources look like now?” stored in OPS.
2. **Gate:** Compare harvest (or live) to catalog/version; block dangerous drift.
3. **Quality:** Content rules.
4. **Update:** Load EDW with confidence; model checks can reuse harvest for speed.
5. **Browse:** Schema drift timeline per CRM connection or per feed without opening models.

That is the distinct **metadata harvesting** phase of EDW update demanded by issue #112.
