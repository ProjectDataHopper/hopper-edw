# Reverse engineering: SQL package → source-to-target mapping via AI (Flash workflow)

**Related:** [dv-reverse-engineering-plan.md](dv-reverse-engineering-plan.md) (issue #107 — Layer B / Phase 3)  
**Audience:** Offline extraction of staging → raw Data Vault mappings from generated SQL packages (e.g. VaultSpeed → SingleStore), using a small LLM (e.g. Gemini Flash) with a limited token budget.  
**Compliance:** Prefer **SQL text only** (no production row data / PII). Structure of code, not content of tables.

---

## When to use this

- You already know (or have drafted) which hubs / links / satellites exist and how they relate.
- You have generated **load SQL packages** for those targets.
- You need **source-to-target field mappings**, not a full model graph from the LLM.
- Token budget is tight → one package (or one vault table) per call; JSON only, no essays.

The human (or later Hop importer) owns structure; the model only extracts bindings from DML.

---

## Aim for compact JSON — not prose and not full `.hdv`

With Flash + a tight token budget, the right intermediate artifact is a **small mapping document per vault table (or per SQL package)**.

That lines up with what hop-data-vault stores later:

| Hop concept | What the model should emit |
|-------------|----------------------------|
| Catalog `DV_SOURCE` | staging schema/table (or view) name |
| Hub BK / multi-source | `business_keys` / field maps with `role: business_key` |
| Satellite attributes | field maps with `role: attribute` |
| Link hub roles | `link_hubs[]` with per-hub key maps |
| Hash key | inputs only (`source_inputs`), not algorithm guesswork unless obvious |
| Technical columns | mark as `technical: true` and skip as business attributes |

Do **not** ask the model for a full model graph, DDL, or pipeline SQL.

| Format | Why skip (for this use) |
|--------|-------------------------|
| Free-form Markdown tables | Easy to read, hard to re-import, inconsistent |
| Full OpenLineage JSON | Too heavy; good later, not for Flash extraction |
| Full `.hdv` XML | Too many Hop-specific required fields; high error rate |
| dbt `sources.yml` only | Misses hash roles / technical columns / link hub maps |
| “Explain this package” essays | Burns budget without durable artifact |

Markdown tables are fine **for eyeballing**; store the **canonical artifact as JSON**.

---

## Format: `hop-dv-s2t-map/v1`

Keep it flat, stable field names, minimal nesting. Prefer **one file per package** or **one object per vault table** so you can retry cheaply.

### Example — satellite

```json
{
  "format": "hop-dv-s2t-map/v1",
  "source_package": "load_sat_customer_address.sql",
  "dialect": "singlestore",
  "confidence": "high",
  "notes": [],

  "source": {
    "kind": "staging_table",
    "schema": "stg",
    "name": "customer_address",
    "record_source_value": null
  },

  "target": {
    "type": "satellite",
    "logical_name": "sat_customer_address",
    "physical_name": "SAT_CUSTOMER_ADDRESS",
    "parent": {
      "type": "hub",
      "logical_name": "hub_customer",
      "physical_name": "HUB_CUSTOMER"
    }
  },

  "hash_keys": [
    {
      "target_column": "CUSTOMER_HK",
      "role": "parent_hash",
      "source_inputs": ["customer_id"],
      "expression": null
    }
  ],

  "field_maps": [
    {
      "target_column": "CUSTOMER_HK",
      "source_column": null,
      "transform": "HASH",
      "role": "parent_hash",
      "technical": true
    },
    {
      "target_column": "LOAD_DATE",
      "source_column": null,
      "transform": "STANDARD",
      "role": "load_date",
      "technical": true
    },
    {
      "target_column": "RECORD_SOURCE",
      "source_column": null,
      "transform": "STANDARD",
      "role": "record_source",
      "technical": true
    },
    {
      "target_column": "HASHDIFF",
      "source_column": null,
      "transform": "HASHDIFF",
      "role": "hashdiff",
      "technical": true,
      "source_inputs": ["street", "city", "postcode"]
    },
    {
      "target_column": "STREET",
      "source_column": "street",
      "transform": "IDENTITY",
      "role": "attribute",
      "technical": false
    },
    {
      "target_column": "CITY",
      "source_column": "city",
      "transform": "IDENTITY",
      "role": "attribute",
      "technical": false
    }
  ],

  "filters": [],
  "unresolved": []
}
```

### Hub variant (only the deltas that matter)

```json
{
  "target": {
    "type": "hub",
    "logical_name": "hub_customer",
    "physical_name": "HUB_CUSTOMER"
  },
  "field_maps": [
    {
      "target_column": "CUSTOMER_HK",
      "transform": "HASH",
      "role": "hash_key",
      "technical": true,
      "source_inputs": ["customer_id"]
    },
    {
      "target_column": "CUSTOMER_ID",
      "source_column": "customer_id",
      "transform": "IDENTITY",
      "role": "business_key",
      "technical": false
    }
  ]
}
```

### Link variant

```json
{
  "target": {
    "type": "link",
    "logical_name": "link_order_customer",
    "physical_name": "LNK_ORDER_CUSTOMER"
  },
  "link_hubs": [
    {
      "hub": "hub_order",
      "hash_column": "ORDER_HK",
      "source_keys": [
        { "hub_bk": "order_id", "source_column": "order_id" }
      ]
    },
    {
      "hub": "hub_customer",
      "hash_column": "CUSTOMER_HK",
      "source_keys": [
        { "hub_bk": "customer_id", "source_column": "cust_id" }
      ]
    }
  ]
}
```

The link shape maps almost 1:1 to Hop’s `BusinessKeySource` (`businessKeyField` / `sourceFieldName`) and link hub key mappings.

### Unresolved (prefer honest gaps over wrong guesses)

```json
"unresolved": [
  { "item": "tmp_delta_x", "reason": "intermediate temp; final source unclear" }
]
```

---

## Controlled vocabulary

| Field | Allowed values |
|-------|----------------|
| `format` | `hop-dv-s2t-map/v1` |
| `target.type` | `hub` \| `link` \| `satellite` |
| `transform` | `IDENTITY` \| `RENAME` \| `HASH` \| `HASHDIFF` \| `CONSTANT` \| `EXPRESSION` \| `STANDARD` \| `UNKNOWN` |
| `role` | `business_key` \| `attribute` \| `hash_key` \| `parent_hash` \| `link_hub_hash` \| `dependent_child_key` \| `load_date` \| `record_source` \| `hashdiff` \| `load_end_date` \| `other` |
| `confidence` | `high` \| `medium` \| `low` |

Keep enums locked so output stays mergeable and a future offline importer can stay dumb.

---

## Token-budget workflow

1. **You supply the skeleton** (known tables/relationships) so the model does not invent the graph:

```json
{
  "known": {
    "hubs": ["hub_customer"],
    "satellites": [
      { "name": "sat_customer_address", "parent": "hub_customer" }
    ],
    "links": []
  }
}
```

2. **One package at a time** (or strip to the main `INSERT`/`MERGE` + its `SELECT`). Do not dump whole subject-area folders in one prompt.

3. **Ask for JSON only** — no markdown commentary. Invalid/extra prose wastes tokens and breaks parsers.

4. **Pre-strip the SQL** before the model:
   - drop long comments, boilerplate grants, `CREATE PROCEDURE` wrappers if the core DML is clear
   - keep the final `INSERT … SELECT` / `MERGE` that writes the vault table
   - if a package is huge, feed only the block that targets the known physical table name

5. **Prefer column lists over prose explanations.** Flash is fine at `stg.col → SAT.COL`; it is wasteful at narrative.

6. **Optional second pass only on `unresolved` / `confidence: low`** — cheaper than regenerating everything.

---

## Minimal prompt shape (copy/adapt)

```text
You extract source-to-target mappings from Data Vault load SQL.

Known targets (do not invent others):
- hub_customer <- staging may feed BKs
- sat_customer_address parent hub_customer

Output ONLY valid JSON matching hop-dv-s2t-map/v1:
- source schema/table
- target type/name
- field_maps for every target column you can see
- transform/role from the allowed enums
- source_inputs for HASH/HASHDIFF
- unresolved[] for anything unclear

Do not invent columns not present in the SQL.
Do not include commentary.
```

Then paste the **trimmed** package.

---

## How this feeds hop-data-vault later

| JSON piece | Manual or future import into |
|------------|------------------------------|
| `source.schema` / `source.name` | Catalog `DV_SOURCE` / source modeler table |
| hub `business_key` maps | Hub keys + `BusinessKeySource` |
| sat `attribute` maps | Satellite attributes (+ same-name default when `IDENTITY`) |
| `link_hubs` | Link hub list + hub source key fields |
| `HASH` / `HASHDIFF` `source_inputs` | Documented BK/CDC composition (hash algorithm/config still manual) |
| `technical: true` | Skip as business attributes; seed standard column names in model config |

For a few known tables, apply the JSON by hand in the modeler in minutes. When Phase 3 of the reverse-eng plan lands (SQL package scanner / import load mappings), the same `hop-dv-s2t-map/v1` shape is a good target for an offline importer.

---

## Practical recommendation

Use **`hop-dv-s2t-map/v1` JSON, one vault target per file**, with:

- known relationship skeleton **you** provide  
- the model filling only `source`, `field_maps`, `hash_keys` / `link_hubs`, `filters`, `unresolved`  
- enums locked so output stays mergeable  

Optional follow-up: a tiny JSON Schema file (e.g. `hop-dv-s2t-map-v1.schema.json`) so prompts, validation, and a future importer share one contract.
