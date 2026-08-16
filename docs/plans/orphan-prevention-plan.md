<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Plan: Optional orphan handling for Data Vault (#77), related to #109

**Issue:** [#77 — Smart "Ghost Record" & Early-Arriving Fact Automations](https://github.com/mattcasters/hop-data-vault/issues/77)  
**Related:** [#109 — Late arriving dimensions](https://github.com/mattcasters/hop-data-vault/issues/109) — design at [late-arriving-dimensions-plan.md](late-arriving-dimensions-plan.md)  
**Status:** Implemented (opt-in, default `PASS`) — evaluation remains the design record

This document is both the **evaluation** of the “orphan prevention layer” idea and the **implementation plan** to support it optionally.

---

## 0. Where the idea actually lives

There is **no separate design note** from last month beyond:

- GitHub **#77** (opened 2026-07-14, same minute-batch as #78 / #79 / #80). Body is a short product pitch: intercept RI failures, quarantine or route to an “Error Satellite”, auto-generate a missing hub placeholder, stop 2 AM crashes when a child arrives before a parent.
- The presentation line in `docs/presentations/hop-data-vault-features.html`: “Built-in orphan prevention, quarantine / error satellite, auto placeholder hubs.”
- Earlier (2026-06-18) this repo already shipped **static unknown/invalid sentinel rows** (`DataVaultConfiguration` Unknown/Invalid tabs + `DvSpecialRecordSupport` + Data Vault Update “Ensure special records”). That is the *ghost record* half of #77, and it is **not** wired into generated load streams.

#109 (2026-08-03) is the **dimensional** sibling: inferred members when a fact NK is missing from the dimension.

---

## 1. Evaluation

### 1.1 The problem is real; the write-up mixes four different tools

Both #77 and #109 are the same operational situation: **source A arrives before source B, so we must load incomplete identity**.

| Situation | Incomplete unit | Child | Today if parent missing |
|-----------|-----------------|-------|-------------------------|
| Raw DV (#77) | Hub identity (BK + hash) | Link or satellite | Link/sat still hashes the BK and inserts. No parent lookup. With optional FKs **on**, insert can fail. With FKs **off** (default), you get a **dangling hash** (silent orphan). |
| Kimball DM (#109) | Dimension member (SK + NK + attributes) | Fact | `DimensionLookup` `update=false` → `notFoundTk` (typically 0). No dim insert. |

#77’s text then folds four distinct DV patterns into one “layer”:

| Pattern | Correct use | Wrong use | Product today |
|---------|-------------|-----------|---------------|
| **Shared ghost / unknown sentinel** (`UNKNOWN`, often −1 / fixed hash) | **Null or absent** business key | A *known* NK that just has not arrived yet (collapses every missing parent into one member; promotion impossible) | **Shipped as static rows.** Config + `ensureSpecialRecords`. Load pipelines do **not** remap null BKs onto that row. Nulls are hashed with the null placeholder (`^^`) and become a *different* real-looking key. |
| **Shared invalid sentinel** | **Malformed** key | Late-arriving valid key | Same as above: row exists, stream does not route to it. |
| **Per-NK placeholder hub** | Valid BK seen first on a child feed | Null BK | **Not automatic.** Manual today: add the child feed as a **second hub record source** (multi-source hubs already work; hub phase runs before link/sat). Source-to-vault (#125) only attaches the *parent table* as the hub source. |
| **Quarantine / error satellite** | Hard reject: do not load the child | Valid early-arriving identity | **Not built.** DQ measure/gate can fail a run; source-model profiler already *counts* `childOrphanCount` / `childNullKeyCount` but does not change loads. |

So the idea is directionally right and the 2 AM story is real **once FKs are enabled**. It is **not** one mechanism, and it is **not** a new warehouse layer between source and vault.

### 1.2 Verdict on the original idea

**Keep the goal. Do not implement the pitch literally.**

| Claim in #77 | Verdict |
|--------------|---------|
| “Built-in Orphan Prevention Layers” as a new physical layer | **Reject as architecture.** No new file type, no extra canvas between `.hsm` and `.hdv`. Policy + generated pipeline fragments on existing hubs/links/sats. |
| Auto placeholder hubs for missing parents | **Accept, optional.** This is standard DV 2.0: a valid BK seen on a child *is* a hub insert. Prefer modeling-time “seed hub from child”; add runtime infer as a safety net. |
| Shared ghost (−1) for late parents | **Reject for valid NKs.** Same conclusion as #109. Ghosts stay for null/malformed keys only. |
| Quarantine / error satellite | **Accept as an alternate policy**, not as the default and not as the same action as infer. Infer *loads* the child; quarantine *does not*. |
| Stop pipelines crashing at 2 AM | **Partially true.** Default is no FKs → no crash, silent orphans. Crash happens when `generateForeignKeys` is on. The worse production bug today is the silent dangling hash, not the crash. |
| “Early-arriving fact automations” in the #77 title | **Belongs to #109**, not this work. Do not implement dimensional inferred members under #77. |

### 1.3 Relation to #109 (keep separate)

Same family, **do not unify** into one cross-layer feature.

| | #77 raw vault | #109 dimensional |
|--|---------------|------------------|
| Identity | Hash of business key(s) | Surrogate key + natural key |
| Placeholder | Hub row (BK + hash + load date + record source). No attributes required. | Dim row (SK + NK + empty/placeholder attrs + `is_inferred`) |
| “Promote later” | **No-op for identity.** Later master feed is just another hub source (same hash) plus a satellite. | **Hard.** Fill attributes in place; must not allocate a new SK; SCD2/hybrid must not version on placeholder fill-in. |
| Dominant cost | Stream routing, races under PK/FK, record-source semantics | SCD promotion matrix (~50% of #109 effort) |
| Shared ghost | Already configured | Explicitly out of #109 MVP |

#77 is **simpler** than #109. Implement it as its own optional DV feature. Cross-link docs. Reuse the *policy shape* from #109 (`STRICT` / off by default, GUI, generate pipelines, tests) — not the SCD machinery.

### 1.4 What already exists (reuse, do not reimplement)

- **Multi-source hubs** + serial multi-source workflows (`DvMultiSourceUpdateWorkflowSupport`). Adding a child feed as a hub source and mapping BK parts is the textbook DV fix for early-arriving children in the **same batch** (hub phase before link/sat).
- **Load order** `REF → HUB → LINK → SAT` (`DvUpdateExecutionSupport.FreePipelineBuckets`). Parallelism is inside a phase.
- **Unknown / invalid sentinel rows** (`DataVaultConfiguration` tabs, `SpecialRecordKind`, `DvSpecialRecordSupport`, Update action checkbox).
- **Optional PK/FK DDL** (default off). When FKs are on, missing parents fail inserts — the only current “crash” path.
- **Source-to-vault** creates hubs from parent tables only; junction/transaction tables become links. It does **not** seed parent hubs from child FKs.
- **Source relationship profiler** already measures orphans and null child keys (design-time, not load-time).
- **Data quality** measure/gate (`NOT_NULL`, `SQL_ASSERTION`, …) can block a run; it is not per-row routing.

### 1.5 The important product gap (beyond “add another hub source”)

Even a careful modeler who multi-sources hubs still has:

1. **Null / empty BK on the child** — hashed via `^^`, **not** pointed at the UNKNOWN sentinel.
2. **Malformed BK** — not pointed at INVALID.
3. **Cross-batch child-only keys** when the modeler *refuses* to list the child as a hub source (they do not want that `RECORD_SOURCE` on the hub).
4. **External / read-only hubs** — cannot infer-insert.
5. **Wanted reject path** — keep the vault clean; park the child.

That is what an optional **orphan handling policy** is for. Multi-source hub seeding is 80% of the “valid BK, child first” cases and should be the first slice.

---

## 2. Recommended product shape

Name in the GUI: **Orphan handling** (not “orphan prevention layer”).  
Default everywhere: **`PASS`** — today’s behavior, including silent dangling hashes when FKs are off.

### 2.1 Two complementary mechanisms

```
┌─────────────────────────────────────────────────────────────┐
│  A. Model assist (cheap, same-batch + most cross-batch)     │
│     “Also load this hub from the link/sat source”           │
│     → extra hub record source + BK mapping                  │
│     → existing hub pipeline in HUB phase                    │
└─────────────────────────────────────────────────────────────┘
                              +
┌─────────────────────────────────────────────────────────────┐
│  B. Runtime policy on link / satellite loads (opt-in)       │
│     after BK/hash calc:                                     │
│       null/empty  → SENTINEL | QUARANTINE | FAIL | PASS     │
│       present BK  → lookup parent (optional)                │
│                     miss → INFER | QUARANTINE | FAIL | PASS │
└─────────────────────────────────────────────────────────────┘
```

A does **not** need a new pipeline pattern. B is new generated graph fragments.

### 2.2 Policy enum

`DvOrphanPolicy` (name flexible):

| Value | Null / empty BK | Valid BK, parent missing | Load the child? |
|-------|-----------------|--------------------------|-----------------|
| **`PASS`** (default) | Hash as today (`^^`) | Insert link/sat anyway | Yes |
| **`INFER`** | Use SENTINEL if unknown-row enabled, else QUARANTINE | Insert placeholder hub (real BK + same hash), then child | Yes |
| **`SENTINEL`** | Remap hashes/BKs to existing UNKNOWN (or INVALID if marked malformed) | Remap to UNKNOWN (shared ghost — **only when the user chose this**) | Yes |
| **`QUARANTINE`** | Write quarantine, drop from main | Write quarantine, drop from main | **No** |
| **`FAIL`** | Abort pipeline / action | Abort | No |

`INFER` for a **valid** NK must **never** write the shared UNKNOWN key. `SENTINEL` is the explicit “collapse to ghost” choice.

Malformed detection (non-empty but unparseable / fails a simple rule) can map to INVALID under `SENTINEL` / `INFER`. v1 can treat only null/blank as sentinel-unknown and leave richer “invalid” to a follow-up or to existing DQ.

### 2.3 Where policy lives (GUI required)

| Place | Role |
|-------|------|
| `DataVaultConfiguration` | New **Orphan handling** tab (or extend Unknown/Invalid). Model default policy + inferred record-source value + optional `is_inferred` flag name + quarantine table name. |
| `DvLink` / per participating hub (or `DvLinkHubSource`) | Optional override per parent role. |
| `DvSatellite` | Optional override for the parent hub or link. |
| `DvHub` | Whether this hub **allows** infer-insert; default yes for managed hubs, **no** for external/read-only (`DvIntegrationMode`). |
| Source-to-vault options | Checkbox: **Seed parent hubs from child/link feeds** (default off, to keep #125 behavior stable). |

No file-only config. i18n in existing `messages_*.properties`. Lombok on new types.

### 2.4 Physical artifacts

**Placeholder hub row (INFER)**

- Same layout as a normal hub insert: hash key, business key(s), `LOAD_DATE`, `RECORD_SOURCE`.
- `RECORD_SOURCE`: configurable, default `INFERRED` (must **not** look like the master feed).
- Optional boolean `is_inferred` (off by default so existing DDL does not change). When on, `DvHub` layout + DDL grow one flag column; later real hub load from a modeled source can clear it (Type-1 style update on the hub row — hubs are insert-only today, so clearing the flag is a **small, explicit update path**, not a sat).
- Hash **must** be `hash(BK)` with the same recipe as a later real load, or the “placeholder” is a second identity. That is the whole point.

**Quarantine (v1)**

- One shared table on the vault (or ops) database, e.g. `dv_orphan_quarantine`, created on first use (same spirit as `dv_load_cycle` / quality history).
- Columns: load date, load cycle id (if enabled), model name, child table, parent table, policy, reason (`NULL_KEY` / `MISSING_PARENT` / `EXTERNAL_PARENT`), BK parts, computed hashes, record source, optional payload JSON or first-N source fields.
- **Not** a new canvas object in v1.

**Error satellite (later, not v1)**

- Optional per-parent satellite on the canvas if users want quarantined rows as DV history. Higher modeling cost; defer.

### 2.5 Runtime pipeline fragment (policy ≠ PASS)

Preferred composition — **no Hop core change**:

1. Existing source → BK compose → `DvHashKey` (hub hashes, then link hash).
2. **Filter / Switch** on “BK missing” vs “BK present”.
3. Missing → `SENTINEL` remap (Constants / SelectValues to unknown BK+hash) **or** quarantine Table Output **or** Abort.
4. Present + `INFER` / `QUARANTINE` / `FAIL`: **Database Lookup** (or Stream Lookup after Table Input of parent hub hashes) on parent hash key.
5. Hit → continue existing merge/insert.
6. Miss + `INFER`: `TableOutput` / insert-ignore into hub (same key layout as `DvHub.generateUpdatePipelines`), then continue. Dedup same-NK in-batch (`UniqueRows` / hash set) before insert.
7. Miss + `QUARANTINE` / `FAIL`: as named.

Spike risks (same family as #109): concurrent same-BK infer under PK, bulk-load + FK, `USE` of BINARY hash keys, composite BK parts, hub aliases / role-playing, linked-table (cross-model) parents, external hubs.

When **all** participating hubs of a link already list this source, `INFER` in the *link* pipeline is redundant for same-batch — A already ran. Runtime infer still helps if that hub source was skipped (record-source group) or the key was unexpected.

### 2.6 Non-goals (v1)

- New warehouse layer / file type / extra model between source and vault.
- Dimensional inferred members (that is #109).
- Per-table error satellites on the canvas.
- Park-and-retry redrive of quarantined rows (store them; redrive is a later action).
- Rewriting historical link hashes after a key correction.
- Infer into **external / custom** hubs.
- Changing Hop core.
- Making `INFER` or FKs the default.
- Treating `SENTINEL` as the infer path for valid NKs.

---

## 3. Implementation phases

### Phase 0 — Spec lock (no product change)

- Refresh GitHub #77: split “ghost sentinel routing” vs “per-NK infer” vs “quarantine”; point early-arriving *facts* at #109.
- Spike Postgres: order lines before customers, FKs on, `INFER` → hub row + link row, same hash as a later customer load.
- Spike null `customer_id` → UNKNOWN sentinel (not `hash(^^)`).
- Decide v1 quarantine location (recommend shared table on vault target).

### Phase 1 — Model assist only (largest value / lowest risk)

- Link / satellite dialog: **“Also load parent hub(s) from this source”** (creates/updates hub `recordSources` + `BusinessKey` / source-part rows; does not invent BK names).
- Source-to-vault option `seedParentHubsFromChildFeeds` (off by default).
- Model check: do **not** treat “link/sat source is not a hub record source” as a defect under `PASS` — that is the usual hub-from-parent / link-from-child pattern (retail, integration fixtures). Misconfigured policies still error (`INFER` on a refused hub, `SENTINEL` without unknown rows, `QUARANTINE` without a table).
- Tests: apply/dialog serialization; source-to-vault flag; existing multi-source hub pipelines unchanged when the user does not opt in.

No new runtime graph. Reuses hub-before-link.

### Phase 2 — Metadata + GUI for runtime policy (still `PASS` everywhere)

- `DvOrphanPolicy` + fields on `DataVaultConfiguration`, `DvLink` (per hub role), `DvSatellite`, allow-flag on `DvHub`.
- New Orphan handling tab; i18n; `.hdv` save/reload.
- Validation: `INFER` forbidden on external hubs; `SENTINEL` requires `generateUnknownRecord`; `QUARANTINE` requires a table name. `PASS` with FKs enabled is valid (parent feeds are expected to load first).
- Optional `is_inferred` layout only when a hub is infer-enabled and the flag is configured.
- Unit tests for serialize + check messages.
- **Default remains `PASS` — zero runtime change.**

### Phase 3 — Runtime: sentinel routing + infer + fail

- Shared helper e.g. `DvOrphanHandlingSupport` used from `DvLink` / `DvSatellite` pipeline generators (all source types: DB, CSV, Parquet, composite, pipeline, JSON).
- Wire null/empty BK → UNKNOWN (and optional INVALID) when policy is `INFER` or `SENTINEL`.
- Wire lookup + hub insert-on-miss for `INFER`.
- Wire Abort for `FAIL`.
- Unit tests: graph shape per policy.
- Integration (Postgres first): child-before-parent with FKs on; second batch parent load reuses same hash; null BK lands on UNKNOWN.

### Phase 4 — Quarantine table + metrics

- DDL for shared quarantine table; write path from the reject stream.
- Counts in Data Vault Update log / load-run metrics (quarantined rows, inferred hubs).
- Docs: `datavault-configuration.adoc`, `dv-hub.adoc`, `dv-link.adoc`, `dv-satellite.adoc`, `datavault-update-action.adoc`, `feature-overview.adoc`.
- Optional DQ `SQL_ASSERTION` example: inferred or quarantine count.

### Phase 5 (later, not required to close #77)

- Canvas error satellite.
- Quarantine redrive action.
- Richer invalid detection.
- #109 inferred dimensions (separate plan).

---

## 4. Suggested PR breakdown

| PR | Scope | Risk |
|----|--------|------|
| PR1 | Phase 1 model assist + check warnings + source-to-vault flag | Low |
| PR2 | Phase 2 metadata/GUI/validation (`PASS` default) | Low |
| PR3 | Phase 3 sentinel remap + Type/hub infer + FAIL (Postgres integration) | Medium |
| PR4 | Phase 4 quarantine + metrics + docs + issue closeout | Medium |
| PR5 | Multi-DB if boolean/DDL/quarantine SQL is dialect-sensitive | Medium |

Useful early ship: **PR1 alone** already fixes the common “orders before customers” case without new load semantics.

---

## 5. Key files (expected)

**Metadata / validation / layout**

- `DataVaultConfiguration.java` (new tab + defaults)
- New: `DvOrphanPolicy.java`
- `DvHub.java`, `DvLink.java` (`DvLinkHubSource` / `HubSourceKeyField`), `DvSatellite.java`
- `DvIntegrationSupport.java` (block infer on external)
- Source-to-vault: `SourceToVaultOptions.java`, `SourceToVaultApplySupport.java`, `SourceToVaultClassifier.java` (only if seeding is proposed at classify time)
- `DvSpecialRecordSupport.java` / `SpecialRecordKind.java` (reuse hashes/values)

**Pipeline generation**

- New: `DvOrphanHandlingSupport.java`
- `DvLink.generateUpdatePipelines`, `DvSatellite.generateUpdatePipelines`
- All `*SourcePipelineBuilder` paths only as needed to expose BK fields before the new fragment
- `ActionDataVaultUpdate.java` (log inferred/quarantine totals; create quarantine table)

**GUI / i18n**

- `HopGuiDataVaultModelDialog` (orphan tab — same pattern as Unknown/Invalid)
- `DvLinkDialog`, `DvSatellite` dialog, hub dialog allow-flag
- `org/apache/hop/datavault/metadata/messages/messages_*.properties`
- `hopgui/file/vault/messages/messages_*.properties`

**Tests / fixtures**

- Serialize/check tests next to existing hub/link tests
- New pipeline-shape tests (`PASS` vs `INFER` vs `SENTINEL`)
- Integration: child-before-parent + null BK (Postgres; full matrix if quarantine/boolean DDL is involved)

---

## 6. Open decisions (resolve in Phase 0)

1. Quarantine v1: shared table on vault target (recommended) vs ops DB vs per-table error sat.
2. `is_inferred` column required for `INFER`? Recommended: **optional**, default off; `RECORD_SOURCE=INFERRED` is enough for v1.
3. Lookup style: Database Lookup each row vs cached Table Input + Stream Lookup (better for large hubs).
4. Infer record source: always `INFERRED` vs child’s record source (recommended: dedicated value).
5. Same-batch: if Phase 1 already seeded the hub, skip runtime lookup? Recommended: still run lookup when policy is `INFER` (cheap correctness when a hub source was skipped).
6. `SENTINEL` on *valid but missing* NK: expose it (user asked for ghosts) but document it as lossy. Recommended default for “I want placeholders” is `INFER`, not `SENTINEL`.

---

## 7. Acceptance criteria (when implemented)

1. `PASS`: pipelines and data match current behavior.
2. Phase 1: user can, in the GUI, attach a link/sat source to parent hubs; next Update inserts those BKs in the hub phase before the link.
3. `INFER`: child-before-parent with a **valid** NK creates a hub row whose hash equals a later master-feed load; link/sat loads; FKs (if on) do not fail.
4. `INFER` / `SENTINEL`: null/empty BK uses the configured UNKNOWN sentinel, **not** `hash(^^)`.
5. `QUARANTINE`: child row is not in the link/sat; it is in the quarantine table; run succeeds.
6. `FAIL`: run errors; no dangling child insert.
7. External hub + `INFER`: model check error; no insert attempt.
8. Everything is on `.hdv` dialogs; save/reload; i18n.
9. Unit tests for generators; at least Postgres integration for infer + sentinel.
10. Docs distinguish ghost sentinel vs per-NK infer vs #109 inferred dimensions.

---

## 8. Verification ladder (when implementing)

1. `mvn test` / `mvn spotless:apply`
2. Targeted Postgres integration for child-before-parent + null BK
3. `integration-tests/run-tests-all-databases.sh` if quarantine/boolean/FK DDL is in play
4. Manual Hop GUI: set policy, seed hub from child, Debug pipeline, inspect infer/quarantine branch

---

## 9. Recommendation

- **Evaluate as: good problem, mixed prescription.** Implement **optional orphan handling**, not a new layer.
- **Lead with Phase 1 (seed hub from child)** — this is the Data Vault 2.0-correct fix and reuses machinery you already have.
- **Then** wire sentinels into the *stream* (they are unused today) and add `INFER` / `QUARANTINE` / `FAIL`.
- **Do not** use shared −1/UNKNOWN for valid early-arriving keys.
- **Do not** fold #109 into this work.
- **Default off** so existing models and golden suites stay stable.

Effort (after Phase 1): metadata/GUI ~20%; sentinel routing ~15%; infer insert + races + aliases/composite ~35%; quarantine/metrics ~15%; tests/docs/multi-DB ~15%. Phase 1 itself is a small, shippable slice.
