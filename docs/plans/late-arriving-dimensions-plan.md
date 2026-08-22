# Plan: Late-arriving dimensions / inferred members (Issue #109)

**Issue:** [#109 — Late arriving dimensions](https://github.com/ProjectDataHopper/hopper-edw/issues/109)  
**Status:** Deferred — design only; implement later  
**Goal:** When a fact references a natural key that is not yet in the dimension, create a **stable surrogate-key skeleton (inferred member)**, load the fact, and later **promote** that row when real dimension attributes arrive—without breaking SCD1 / SCD2 / hybrid semantics.

**Related reading:** [Resolving Late Arriving Dimensions (Medium)](https://medium.com/dataseries/resolving-late-arriving-dimensions-c0ebc9f818c3) (also called early-arriving facts).

---

## 1. Problem

Kimball marts assume dimensions load before facts. Real feeds often deliver **facts first** with a natural key that does not exist in the dimension yet (master data lag, multi-system CDC, incomplete reference data).

Today in hop-data-vault, fact FK resolution is **lookup-only**:

- `DmDimensionLookupBuilder.addFactDimensionLookup` → Hop `DimensionLookup` with `update=false`
- On miss, Hop returns `notFoundTk` (typically `0`); **no dimension insert**
- Hash-key passthrough roles (`skipDimensionLookup`) never touch the dimension table

Without model-driven support, users either lose facts, hand-build pipelines, or point at a generic unknown key without a real per-NK member.

### Classic approaches (context)

| Approach | Behavior | MVP choice |
|----------|----------|------------|
| Never process fact | Drop / fail until dim exists | Out of scope as primary |
| Park and retry | Stage unmatched facts; redrive later | Out of scope for v1 |
| **Inferred members** | Insert skeleton dim row + load fact; promote later | **In scope** |

---

## 2. Why SCD work dominates

Dimension **loads** already branch by strategy (`DmDimensionLoadStrategySupport`):

| Strategy | Builder | Promote-inferred risk |
|----------|---------|------------------------|
| Pure Type 1 | `DmInsertUpdateBuilder` | Low: NK match overwrites attributes; clear `is_inferred` |
| Pure Type 2 | `DmScd2DimensionBuilder` (MergeRowsPlus) | **High:** attribute fill-in looks like Type 2 → **new version / new SK** while facts still point at inferred SK |
| Hybrid / Type 3 | `DmDimensionLookupBuilder` (`update=true`) | Medium: per-attribute INSERT vs UPDATE; placeholders must not force a false Type 2 |

**Kimball rule to encode:** filling an incomplete inferred member is a **Type 1 in-place update of the same surrogate key**, not a Type 2 version. Real business changes after promotion follow normal SCD rules.

That single rule drives most of the implementation effort.

---

## 3. Non-goals (MVP and near-term)

- Park-and-retry fact staging / automatic redrive
- Late-arriving facts that require **rewriting historical fact FKs** after dim version backdating
- Full SCD2 **effective-date backfill** (fact in the past needing a historical dim version that never existed)
- Inferred members for junk, range, date-generator, or pure hash-passthrough roles (unless trivial later)
- Changing Hop core `DimensionLookup` unless composition of existing transforms is blocked

---

## 4. Architecture decisions

### 4.1 Two paths, two jobs

| Path | Responsibility |
|------|----------------|
| **Fact load** | Lookup by natural key (and effectivity date for versioned dims). On miss only: **insert skeleton**, return new SK as fact FK. **Never** overwrite a real dimension from the fact stream. |
| **Dimension load** | Normal SCD load. On NK match to an **inferred** row: **promote in place** (fill attributes, clear flag, keep SK). Afterwards behave as today. |

Do **not** flip fact lookups to hybrid `update=true` with full attributes: the fact stream usually lacks complete dimension columns and would corrupt or re-version the dim.

### 4.2 Where policy lives

- **Primary:** on `DmDimension` (owns inferred semantics, DDL flag, promote rules)
- **Override:** optional on `DmFactDimensionRole` (`STRICT` forces no create-on-miss for that role)
- **Default:** `STRICT` (today’s behavior) for backward compatibility

### 4.3 Technical shape of an inferred row

- Natural key(s) from the fact stream
- New surrogate key (same strategy as the dimension—document/validate supported strategies)
- Descriptive attributes: null or configurable placeholders
- Optional but recommended `is_inferred` flag (configurable name; default `is_inferred`)
- Versioned dims: open effectivity window matching `DimensionalConfiguration` sentinels (`date_from` / `date_to`, `version=1`, `is_current=true`)

### 4.4 SCD promotion matrix

| When real dim arrives | Desired behavior |
|----------------------|------------------|
| Pure Type 1 | Insert/Update on NK → attributes + `is_inferred=false` |
| Pure Type 2 | If current row for NK is inferred → **in-place UPDATE** (keep SK/version window); else existing MergeRowsPlus Type 2 |
| Hybrid | Promote without a Type 2 `INSERT` solely because placeholders → real values; clear flag |

### 4.5 Fact create-on-miss pipeline shape

Preferred composition (no Hop core change):

1. Existing lookup-only `DimensionLookup` (`update=false`)
2. Branch on “SK is notFound / null / 0” (and NK present)
3. Build skeleton row (Constants / SelectValues for placeholders + `is_inferred=true` + technical columns)
4. Insert skeleton (`TableOutput` or insert-oriented path)
5. Re-lookup (or return generated SK) so fact FK is real

Spike risks: concurrent same-NK inserts, `USE_SOURCE_FIELD` SK strategy, versioned lookup dates, preload cache after mid-pipeline insert.

---

## 5. Implementation phases

### Phase 0 — Spec lock + spike

- Optionally refresh GitHub #109 with sharpened acceptance criteria
- Spike Postgres Type1 path: fact-before-dim → inferred insert → promote, SK stable
- Spike SCD2 promote-in-place insertion point in `DmScd2DimensionBuilder`
- Decide v1 surrogate strategies (recommend auto-inc + table-maximum first)

### Phase 1 — Metadata + DDL + validation (no runtime change yet)

**Model**

- `DmDimension`: `lateArrivingPolicy` (`STRICT` \| `INFERRED`), `inferredFlagField`, optional placeholder mode
- `DmFactDimensionRole`: optional override
- `DimensionalConfiguration`: default inferred flag column name

**Layout / DDL / validation / GUI**

- `DmLayoutSupport`: add flag column when policy is `INFERRED`
- Validation: NK required; no passthrough/date-truncate + inferred; supported SK strategies; versioned layout checks
- `HopGuiDmTableDialog`: dimension policy + optional role override; i18n

**Tests:** serialize/deserialize `.hdm`, layout, validation messages.

### Phase 2 — Fact path: create inferred member on miss

- `DmFactDimensionJoinBuilder` / `DmDimensionLookupBuilder`
- New helper e.g. `DmInferredMemberFactJoinBuilder` / `DmInferredMemberSupport`
- Unit: graph shape for STRICT vs INFERRED
- Integration: fact-only load creates skeleton + fact; second fact reuses SK

### Phase 3 — Dimension path: promote inferred (SCD matrix)

| Sub-phase | Work |
|-----------|------|
| **3a Pure Type 1** | Ensure flag cleared on match; new real inserts not inferred |
| **3b Hybrid** | Prefer **promote Update pre-step** (`UPDATE … WHERE nk=? AND is_inferred`) then existing hybrid graph; avoids false Type 2 |
| **3c Pure Type 2** | Before MergeRows change detection: inferred current row → in-place Update; only non-inferred enter Type 2 split |

### Phase 4 — Docs, fixtures, polish

- `dimensional-modeler-overview.adoc`, `dimensional-update-action.adoc`, feature-overview
- Integration or retail-style fixture optional
- AI schemas / search if they index new properties
- Clarify: inferred helps across **batches** when dim feed is incomplete, not only within one Dimensional Update ordering

---

## 6. Suggested PR breakdown

| PR | Scope | Risk |
|----|--------|------|
| PR1 | Metadata, config, DDL layout, validation, GUI (STRICT default) | Low |
| PR2 | Fact-path inferred insert + Type1 unit/integration | Medium |
| PR3 | Type1 + hybrid promotion | Medium |
| PR4 | Pure Type2 promote-in-place | Higher |
| PR5 | Docs, fixtures, issue closeout | Low |

Useful early ship: **PR1 + PR2 + Type1 promote**, with hybrid/SCD2 following.

---

## 7. Effort realism

| Area | Share |
|------|-------|
| Metadata + GUI + validation | ~15% |
| Fact create-on-miss + races + SK strategies | ~25% |
| Type1 promote | ~10% |
| Hybrid promote without false Type2 | ~15% |
| Pure Type2 promote-in-place | ~25% |
| Tests, docs, multi-DB edges | ~10% |

---

## 8. Key files (expected)

**Metadata / layout / validation**

- `DmDimension.java`, `DmFactDimensionRole.java`, `DimensionalConfiguration.java`
- `DmLayoutSupport.java`, `DmValidationSupport.java`, `DmFactDimensionJoinValidationSupport.java`
- `DmSurrogateKeySupport.java`

**Pipeline generation**

- `pipeline/DmDimensionLookupBuilder.java`
- `pipeline/DmFactDimensionJoinBuilder.java`
- `pipeline/DmFactLikeLoadBuilder.java`
- `pipeline/DmInsertUpdateBuilder.java`
- `pipeline/DmScd2DimensionBuilder.java`
- New: `pipeline/DmInferredMemberSupport.java` (name flexible)

**GUI / i18n**

- `hopgui/file/dimensional/HopGuiDmTableDialog.java`
- dimensional `messages_*.properties`

**Tests**

- Patterns: `DmBasicStarPipelineTest`, `DmCatalogPipelineTest`
- New inferred fact-join + SCD promote tests; Postgres integration fixture

---

## 9. Open decisions (resolve in Phase 0)

1. v1 SCD coverage: Type1-only vs Type1+hybrid vs all three before claiming the feature complete
2. Inferred flag required when policy=`INFERRED` (recommended: yes)
3. Placeholders: null-only vs literals (`N/A`)
4. Always real SK (recommended) vs shared unknown member (`0` / `-1`)
5. Multi-worker fact loads: require unique NK on dimension for race safety?
6. Effectivity: inferred rows always open-ended current so versioned fact lookups hit them

---

## 10. Acceptance criteria (when implemented)

1. STRICT: pipelines and runtime match current behavior
2. INFERRED + Type1: fact-before-dim → skeleton + fact FK; later dim load fills attributes, clears flag, **same SK**
3. INFERRED + Type2: promotion does **not** allocate a new SK; later real Type2 changes still version
4. INFERRED + hybrid: promotion does not create a spurious Type2 version from placeholder fill-in alone
5. Full GUI on `.hdm`; save/reload; validation for unsupported combos
6. Unit tests for generators; at least Postgres integration for fact-before-dim + promote
7. Docs describe policy, SCD promotion rule, and out-of-scope items

---

## 11. Verification ladder (when implementing)

1. `mvn test`
2. Targeted Postgres integration for the inferred scenario
3. Multi-DB if boolean/DDL portability is in play: `integration-tests/run-tests-all-databases.sh`
4. Manual Hop GUI: set policy, generate/debug pipeline, inspect insert branch

---

## 12. Recommendation

Implement later in **PR slices**, lead with **Type1 end-to-end** (metadata → fact insert → promote), then **shared promote-Update pre-step** for hybrid, then **SCD2 branch** in `DmScd2DimensionBuilder`. That matches existing load strategies (`PURE_TYPE1` / `PURE_TYPE2` / `DIMENSION_LOOKUP`) and isolates the riskiest SCD2 change.
