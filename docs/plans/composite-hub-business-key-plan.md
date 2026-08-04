# Plan: Composite hub business keys (multi-source-field → one vault BK column)

**Status:** Implemented (metadata, hub/sat/link pipelines, validation, GUI, docs — see CHANGELOG Unreleased)  
**Goal:** Allow a hub to **store a single physical business-key column** that is **built by concatenating multiple source fields** (VaultSpeed-style composite BKs), while still hashing from those parts (or from the composed string with explicit rules). Satellites and links must **consume the hub’s logical BK model** and map source parts correctly without inventing parallel multipartite vault columns.

**Origin / motivating case (EDW reverse-engineering):**

| Layer | Example |
|-------|---------|
| Source (EXT) | `num_seq_bkcc_bk = 'IKL'`, `num_seq_bk = '12278170'` |
| VaultSpeed hub column | `burger_bk = 'IKL#12278170'` |
| VaultSpeed hash | `MD5(num_seq_bkcc_bk \|\| '#' \|\| num_seq_bk \|\| '#')` → `hub_burger_hkey` |
| Hop today (multipartite) | Physical columns `burger_bk_1`, `burger_bk_2` + hash from those names |
| Desired Hop | Physical column `burger_bk` (composite) + hash parity with VS |

**Related:**

- Hash formatting: `businessKeyDelimiter`, `hashContentSuffix`, `hashContentCasing`, `trimBusinessKeys`, `nullPlaceholder` on `DataVaultConfiguration` / `DvHashKeyMeta`
- Sat parent keys: `DvSatellite.parentKeySourceFields` (ordered source columns when names differ from hub BKs)
- Link hub mapping: `linkHubSources` / `businessKeySources`
- Reverse-engineering / prefer-ext work may emit multipartite BKs today as a **workaround**; this feature makes the **product** match composite hub DDL

---

## 1. Problem analysis

### 1.1 What VaultSpeed and many warehouses do

Composite business keys are common:

```text
source part A  +  delimiter  +  source part B  [+ trailing delimiter for hash only]
        ↓
   one hub column (e.g. burger_bk)
        ↓
   hub hash key (MD5 / SHA of parts or of composed string under fixed rules)
```

Important nuance: **stored BK string** and **hash input string** are not always identical.

| Artifact | Typical VS rule (example) |
|----------|---------------------------|
| Stored `burger_bk` | `part1 \|\| '#' \|\| part2` (no trailing `#`) |
| Hash input | `part1 \|\| '#' \|\| part2 \|\| '#'` (trailing `#`) |

Hop already supports delimiter / prefix / suffix / casing / null placeholder for **hashing**. It does **not** yet support “compose many source fields into **one target BK column**” in hub DDL and hub load pipelines.

### 1.2 What hop-data-vault does today

| Concept | Today | Consequence |
|---------|--------|-------------|
| Multiple `BusinessKey` on hub | Supported; **order matters for hash** | Each distinct BK **name** is a **physical column** on the hub table |
| Hash | `DvHashKey` over the list of BK field names in stream | Stream fields must exist with those names (or mapped earlier) |
| Hub load / DDL | Inserts all BK columns + hash + load-ts + record source | Multi-part ⇒ multi-column hub |
| Satellite parent identity | Defaults to hub BK **names** as source columns; optional `parentKeySourceFields` lists **source** column names in hub order | Assumes hub BK names align with sat feed or explicit source-part list |
| Link hub mapping | `businessKeySources`: hub BK name ↔ source field name | One source field per hub BK column |

**Observed gap (pilot `hub_burger_ami_ext_pilot.hdv`):**

- Model validates with multipartite BKs `burger_bk_1` / `burger_bk_2` mapped from EXT parts.
- Generated pipeline reading the **target** hub still expects `burger_bk_1` / `burger_bk_2`.
- Live/legacy vault table has composite `burger_bk` only → mismatch with dual-run / VS DDL.

### 1.3 Why “just use multipartite vault columns” is insufficient

1. **DDL parity** with existing VaultSpeed hubs (single composite column).  
2. **Downstream consumers** already join on `burger_bk`, not `burger_bk_1` + `burger_bk_2`.  
3. **Conceptual DV**: the business key is one identity string; parts are **source-side** composition, not necessarily multiple natural keys of equal stature.  
4. **Hash rules** may need trailing suffix **only for hash**, not for stored BK (VS). Multipartite columns alone do not express that split cleanly if both stored BKs and hash use the same field list without a “compose” step.

### 1.4 Goals (must-haves)

1. **Model:** Declare a hub business key that is **composite**: one vault column, N ordered source contributions.  
2. **Hash:** Compute hub hash from the **source parts** (or from composed value under explicit hash rules), compatible with existing delimiter/suffix/casing options.  
3. **DDL / hub load:** Target hub has **one** BK column (plus hkey, load-ts, record source).  
4. **Satellite:** Parent identity for sat load uses the hub’s **logical BK** (single composite name for matching/DDL where relevant) and maps **source parts** from the sat feed (ordered list / part mappings).  
5. **Link:** Link load maps source parts into each participating hub’s composite BK model (and link hash).  
6. **Validation / Check model:** Clear errors when part count, order, or source fields mismatch.  
7. **Backward compatible:** Existing multipartite (N vault columns) and single-field BKs keep working.

### 1.5 Non-goals (v1)

- Changing hash algorithm set (MD5/SHA*) or inventing a new hash transform.  
- Auto-discovery of composition rules from arbitrary SQL (reverse-engineering tools may set metadata; product need not parse STG procedures).  
- Forcing all hubs to composite; multipartite vault columns remain valid when intentionally modeled.  
- Full VaultSpeed feature parity for every edge case (exception streams, ghost records).  

---

## 2. Conceptual model

### 2.1 Roles of a hub business key

Separate three concerns that today are conflated:

| Concern | Meaning | Today | Desired |
|---------|---------|--------|---------|
| **Vault column** | Physical column on hub table | = `BusinessKey.name` | Still one name for composite BK |
| **Hash inputs** | Ordered values fed to `DvHashKey` | = list of BK names / stream fields | Ordered **parts** (source or intermediate stream fields) |
| **Source mapping** | How a record source supplies identity | One `sourceFieldName` per BK name per source | **N source fields → 1 vault BK** (per source) |

### 2.2 Composition rule

Define a **composition** used for the **stored** hub BK value:

```text
compose(parts) = join(delimiter, format(part_i))
```

Where `format` applies trim / null placeholder / optional casing **if** configured for composition (see open questions: share hash formatting vs separate “compose” settings).

**Default proposal for v1:**

- **Stored BK composition** uses `businessKeyDelimiter` (e.g. `#`) and `trimBusinessKeys`.  
- **Hash** continues to use existing `DvHashKey` rules (`delimiter`, `prefix`, `suffix`, `casing`, `nullPlaceholder`).  
- Model may set `hashUsesComposedBusinessKey=false` (default): hash over **parts** with hash suffix (VS trailing `#`).  
- Optional `hashUsesComposedBusinessKey=true`: hash over the **already composed** BK string only (simpler; may not match VS trailing-`#` without storing the trailing char).

### 2.3 Multi-source hubs

Same as today for multi-source: each record source maps its own part field names into the same logical composite BK:

| Hub vault BK | Source A parts | Source B parts |
|--------------|----------------|----------------|
| `burger_bk` | `num_seq_bkcc_bk`, `num_seq_bk` | `burgerid_bkcc_bk`, `burgerid_bk` |

Order of parts is **fixed on the hub** (part 1, part 2, …). Per-source mapping only supplies source field names for each part index.

---

## 3. Metadata design (`.hdv`)

### 3.1 Option A — Preferred: composite flag + ordered source parts on `BusinessKey`

Extend `BusinessKey` (or a nested structure) without multiplying vault columns:

```xml
<businessKeys>
  <name>burger_bk</name>
  <dataType>String</dataType>
  <length>21844</length>
  <!-- NEW: this BK is stored as one column; hash/load compose from parts -->
  <composite>Y</composite>
  <!-- NEW: ordered source field names for THIS record source (multi-source: repeat businessKeys with same name + different recordSourceName, each with its parts) -->
  <sourceFieldNames>
    <sourceFieldName>num_seq_bkcc_bk</sourceFieldName>
    <sourceFieldName>num_seq_bk</sourceFieldName>
  </sourceFieldNames>
  <!-- Legacy single field still supported when composite=N -->
  <sourceFieldName/>
  <recordSourceName>edw_ext.ami_indiv_klanten</recordSourceName>
</businessKeys>
```

**Distinct vault columns** = distinct `BusinessKey.name` values (unchanged).  
**Multi-source** = multiple entries with the **same** `name` and different `recordSourceName` (as today), each carrying its own `sourceFieldNames` list.

Alternatively, avoid repeating the full BK definition:

```xml
<businessKeys>
  <name>burger_bk</name>
  <composite>Y</composite>
  <partCount>2</partCount>  <!-- optional, derived from sourceFieldNames length -->
  ...
</businessKeys>
```

with part mappings only under a per-source structure (cleaner long-term; more dialog work).

### 3.2 Option B — Explicit `CompositeBusinessKey` type (heavier)

New metadata type with `vaultFieldName` + `parts[]`. Cleaner semantics, larger UI/API change. Recommend **Option A** for v1 (extends existing list).

### 3.3 Serialization / dual-read

- `composite` absent or `N` → current behavior.  
- `sourceFieldNames` empty and `sourceFieldName` set → single-field mapping.  
- If `composite=Y` and only `sourceFieldName` set → treat as one part (degenerate composite).  
- Dual-read multipartite models (`burger_bk_1`, `burger_bk_2` as separate BKs) remain valid; optional **migration action** “Collapse multipartite BKs into composite…” (phase 2).

### 3.4 Model configuration (optional knobs)

On `DataVaultConfiguration` or per-hub:

| Property | Purpose |
|----------|---------|
| `businessKeyDelimiter` | Shared delimiter for compose + hash (existing) |
| `composeBusinessKeysForStorage` | If true (default when any composite BK), hub load writes composed string |
| `hashUsesComposedBusinessKey` | Hash composed string vs raw parts (default **false** for VS parity) |
| `hashContentSuffix` | Existing; VS trailing `#` for hash only |

Document clearly: **storage composition** vs **hash composition**.

---

## 4. Pipeline generation

### 4.1 Hub update pipeline (core)

For each record source feeding the hub:

1. **Table Input / source graph** — select part columns (+ attrs if any).  
2. **Optional Select Values / rename** — keep stable stream names for parts (e.g. `__bk_part_1`, `__bk_part_2`) **or** keep source names.  
3. **Compose stored BK** (new small step or reuse **Concat Fields** / **Calculator**):  
   - Inputs: ordered part fields  
   - Output field: hub BK vault name (`burger_bk`)  
   - Delimiter / trim / null rules as configured  
4. **DvHashKey** — inputs:  
   - **Default (VS-friendly):** ordered part stream fields with existing delimiter + **suffix**  
   - **Alt:** single composed `burger_bk` field if `hashUsesComposedBusinessKey=true`  
5. **Merge / insert** into hub: columns = `hashKey`, **`burger_bk` (one)**, load date, record source — **not** `burger_bk_1` / `burger_bk_2`.

DDL generation for hub must emit **one** BK column when `composite=Y`.

### 4.2 Satellite update pipeline

Today: parent identity fields on the sat stream are matched by hub BK names or `parentKeySourceFields`.

**With composite hub BK:**

| Need | Behavior |
|------|----------|
| Hash of parent for sat row | Same as hub: hash from **parts** on the sat feed (order = hub part order) using hub/hash config |
| Optional parent BK column on sat | Usually sats store only `hub_*_hkey`; if any sat stores BK text, compose with same rules as hub |
| `parentKeySourceFields` | Remains the ordered list of **source part column names** on the sat feed (length = part count of composite hub BK, or sum of part counts if multiple hub BKs) |

**Validation:** If hub has one composite BK with 2 parts, sat must provide 2 source fields (via `parentKeySourceFields` or same names as internal part aliases). Do **not** require source columns named `burger_bk` unless the feed already has the composed string.

**Important:** When the sat feed only has parts (EXT), `parentKeySourceFields` = parts; when the feed already has composed `burger_bk` (STG), allow single-field parent key mapping with `composite` hash mode that splits? **v1: prefer parts on feed** (EXT-first). STG feeds with precomposed BK can map `parentKeySourceFields` = `[burger_bk]` only if hub is non-composite single-field.

### 4.3 Link update pipeline

For each hub role on a link:

1. Resolve hub’s logical BKs (including composite).  
2. For each composite BK, map **N source fields** (from `businessKeySources` expanded to multi-field or new `sourceFieldNames` list).  
3. Compose hub BK string if needed for stream/debug; **always** compute hub hkey from parts (default) before building link hash.  
4. Link hash over participating hub hkeys (+ dependent child keys) unchanged.

**Link metadata expansion:**

Today one `businessKeySource` per hub BK column. For composite:

```xml
<businessKeySources>
  <businessKeyField>burger_bk</businessKeyField>
  <sourceFieldNames>
    <sourceFieldName>num_seq_bkcc_bk</sourceFieldName>
    <sourceFieldName>num_seq_bk</sourceFieldName>
  </sourceFieldNames>
</businessKeySources>
```

Dual-read: multiple `businessKeySource` rows with same `businessKeyField` and ordered `sourceFieldName` — only if serialization cannot nest lists easily; prefer nested list for clarity.

### 4.4 Target table input / “read hub” paths

Any generated pipeline that **reads the hub table** (incremental compare, sat attach, debug) must select **`burger_bk`**, not multipartite columns.

Places to audit in code:

- Hub update merge keys / lookup  
- Satellite pipelines joining to hub  
- Link pipelines reading hub keys  
- Any “source from vault table” helpers  
- Check model / field mapping validation  

---

## 5. UI / dialogs

### 5.1 Hub dialog — Business keys tab

- Checkbox **Composite (single vault column)** per BK (or once if only one BK).  
- When composite: table of **ordered source fields** (add/remove/reorder) instead of single source field combo.  
- Show preview: `part1 + delim + part2 → sample` when possible.  
- Help text: “Hash may use a trailing suffix that is not stored in the BK column.”

### 5.2 Satellite dialog — Parent key source fields

- Already an ordered list; document that for composite hubs the list is **source parts in hub part order**, not the composite column name.  
- “Load from hub mapping” button: copy part field names from hub’s mapping for the sat’s record source when available.

### 5.3 Link dialog — Hub source mappings

- Per hub BK: either one source field or ordered multi-field editor when hub BK is composite.  
- Validate part count = hub composite part count.

### 5.4 Canvas / Check model messages

Replace ambiguous multipartite errors with:

- “Composite hub business key ‘burger_bk’ expects 2 source fields for source X; found 1.”  
- “Hash is configured with suffix ‘#’ (hash only); stored BK uses delimiter ‘#’ without suffix.”

---

## 6. Validation rules

| Rule | Severity |
|------|----------|
| `composite=Y` ⇒ at least one source field part | Error |
| All sources for same composite BK name must use same **part count** | Error |
| Part order is hub-global; only field **names** vary by source | Error if counts differ |
| Sat `parentKeySourceFields` length must equal total hub part count for parent hub | Error |
| Link mapping part count must equal hub composite part count per BK | Error |
| DDL target has only composite column names (no `*_bk_1`) when composite | Warning if live schema drift |
| `hashUsesComposedBusinessKey=true` and hash suffix non-empty | Warning (suffix may double-apply) |

---

## 7. Migration & coexistence

### 7.1 From multipartite vault columns (`burger_bk_1`, `burger_bk_2`)

Optional model refactor:

1. Detect consecutive BKs matching `*_bk_N` pattern with same record sources.  
2. Offer **Collapse to composite** → one `burger_bk` with `sourceFieldNames` = former source fields.  
3. User regenerates DDL / pipelines (breaking change for tables already loaded with multipartite columns).

### 7.2 From reverse-engineered EXT-first models

Generators (e.g. prefer-ext multipartite emission) should gain a flag:

- `--composite-hub-bks` (default true for VS parity) → emit composite `burger_bk` + part mappings  
- vs multipartite vault columns (current workaround)

### 7.3 Coexistence matrix

| Hub BK style | Hub DDL | Hash inputs | Sat parentKeySourceFields |
|--------------|---------|-------------|---------------------------|
| Single field | 1 col | that field | 1 source field or same name |
| Multipartite (today) | N cols | N fields | N source fields (names or mapped) |
| **Composite (this feature)** | **1 col** | **N parts** (default) | **N source part fields** |

---

## 8. Implementation plan

### Phase 0 — Spec freeze (½–1 day)

- Confirm Option A metadata XML.  
- Confirm default: hash over **parts** + existing suffix; storage compose without hash suffix.  
- List all pipeline builders that read hub BK field names.

### Phase 1 — Metadata + dual-read (1–2 days)

- Extend `BusinessKey` (and link mapping types) with `composite` + `sourceFieldNames`.  
- Serialize/deserialize; dual-read old models.  
- Unit tests for metadata round-trip.

### Phase 2 — Hub DDL + hub load pipelines (2–4 days)

- DDL: one column for composite BK.  
- Hub update: Concat/compose step + `DvHashKey` on parts.  
- Hub table input / merge use composite column only.  
- Integration test: load EXT-like feed → hub row `IKL#12278170` + expected hkey.

### Phase 3 — Satellite + link pipelines (2–4 days)

- Sat parent hkey from parts via hub hash rules.  
- Link multi-field `businessKeySources` for composite hub BKs.  
- Validation messages.  
- Integration tests for hub+sat and hub+link.

### Phase 4 — UI (2–3 days)

- Hub dialog composite editor.  
- Link dialog multi-field mapping.  
- Sat “load parts from hub” helper.  
- Docs + sample model (retail or small VS-like fixture).

### Phase 5 — Tooling / migration (optional, 1–2 days)

- Collapse multipartite action.  
- Notes for reverse-engineering generators.

**Rough total:** ~2–3 weeks calendar for one developer familiar with the plugin; smaller if UI is deferred after pipeline MVP.

---

## 9. Test plan

| Test | Expected |
|------|----------|
| Composite hub load from two string parts + `#` | Hub column = `IKL#12278170` |
| Hash with delimiter `#` and suffix `#` | Matches VS `UNHEX(MD5(...))` BINARY sample |
| Hash without suffix on composed-only mode | Documented different result |
| Multi-source hub: two sources, different part field names, same composite BK name | Both load into same hub column shape |
| Sat with `parentKeySourceFields` = two EXT parts | Sat validates; parent hkey matches hub |
| Link between two composite hubs | Link hkey stable; validates |
| Legacy multipartite model | Unchanged pipelines |
| Check model missing part | Clear error |

Parity fixture (from EDW pilot):

```text
num_seq_bkcc_bk = IKL
num_seq_bk      = 12278170
delimiter       = #
hash suffix     = #
stored BK       = IKL#12278170
hash input      = IKL#12278170#
```

---

## 10. Documentation deliverables

- User-facing: hub dialog help, “Composite business keys” section in feature overview / datavault hub docs.  
- AI schema: update `docs/ai-file-schemas/models/hdv.md` + XSD/sample.  
- Changelog entry when shipped.  
- Cross-link from reverse-engineering / prefer-ext notes: prefer this feature over emitting `*_bk_1` vault columns when targeting VS DDL.

---

## 11. Open questions

1. **Should composition casing match hash casing?** (VS often trims parts but does not uppercase for hash; hop default hash casing was UPPER historically.)  
2. **Null parts:** skip vs nullPlaceholder vs reject row?  
3. **More than one composite BK on one hub?** (rare; design should allow it.)  
4. **Dependent child keys on links** interacting with composite hub BKs?  
5. **Should stored BK ever include hash-only suffix?** (Recommend no.)  
6. **Binary / non-string parts:** cast to string before compose?  

---

## 12. Success criteria

1. A hub modeled with composite BK loads into a **single** physical BK column matching VS composition.  
2. Hub hash matches VS for the pilot recipe (parts + `#` + trailing `#`).  
3. Satellite and link pipelines **do not** require multipartite vault columns; they map **source parts** through the hub’s composite definition.  
4. Existing non-composite models remain valid without migration.  
5. Check model catches missing/wrong-order parts.

---

## 13. Suggested issue title / summary (for tracker)

**Title:** Composite hub business keys: multi-source-field composition into one vault BK column  

**Summary:** Support VaultSpeed-style hubs where multiple EXT/STG fields concatenate into one hub BK (e.g. `IKL` + `#` + `12278170` → `burger_bk`) while hashing uses ordered parts (optional trailing suffix). Update hub DDL/load, sat parent-key resolution, and link hub mappings so generated pipelines read/write the composite column—not multipartite `*_bk_1` / `*_bk_2` vault columns.

---

## 14. Appendix — Current vs desired stream shapes

### Hub load (desired)

```text
EXT row: num_seq_bkcc_bk, num_seq_bk, ...
    → compose → burger_bk = "IKL#12278170"
    → hash(parts + suffix) → hub_burger_hkey
    → insert hub(hub_burger_hkey, burger_bk, dv_load_ts, dv_record_source)
```

### Hub load (today multipartite workaround)

```text
EXT row: num_seq_bkcc_bk, num_seq_bk
    → rename/map → burger_bk_1, burger_bk_2
    → hash(burger_bk_1, burger_bk_2, …)
    → insert hub(hub_burger_hkey, burger_bk_1, burger_bk_2, …)   ← wrong for VS DDL
```

### Satellite (desired)

```text
EXT row: num_seq_bkcc_bk, num_seq_bk, attributes...
    → parentKeySourceFields = [num_seq_bkcc_bk, num_seq_bk]
    → hub hash from parts (same rules as hub)
    → insert sat(hub_burger_hkey, dv_load_ts, attrs, hashdiff...)
```
