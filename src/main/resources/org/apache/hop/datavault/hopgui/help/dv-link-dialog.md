<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Link editor

A **Link** represents a relationship between two or more **Hubs** — for example *Customer placed Order*, *Product on Order line*, or *Employee assigned to Department*. The link table stores the participating hub hash keys, optional **dependent child keys** (transactional grain), a surrogate **link hash key**, the record source, and the load date.

Links are insert-only: a generated pipeline reads relationship rows from a source, computes hub and link hash keys, and inserts only combinations that do not already exist in the target link table.

## What the link table contains

| Column | Meaning |
|--------|---------|
| Link hash key | Surrogate key for the relationship (hash of participating hub hashes, plus dependent child key values when configured) |
| Hub hash key(s) | One column per participating hub, in the order listed on the **Options** tab |
| Dependent child key(s) | Optional identity columns for transactional links (e.g. `line_number`) — stored on the link and included in the hash |
| Record source | Which feed supplied the row (from the catalog source configuration) |
| Load date | Batch load timestamp |

If the relationship itself has descriptive attributes that change over time (contract terms, quantity, status, …), attach a **link satellite** and map its source on the **Satellite sources** tab.

## Three layers of configuration

Link loading is easier to reason about when you separate four concerns:

1. **Structure** (Options + Driving keys + Dependent child keys) — which hubs participate, transactional identity columns, target table names, hash key column names.
2. **Link hub sources** — which catalog **DV_SOURCE** feeds the link table, and how each source column maps to each hub's business keys.
3. **Link satellite sources** — which catalog feed supplies link-satellite attributes, and how source columns map to satellite fields.
4. **Custom pipelines** (optional) — when integration mode is **Custom pipelines**.

The **Hub sources** tab loads the link itself. The **Satellite sources** tab loads descriptive attributes on top of an existing link. They are configured independently even when both read the same physical table.

## Options tab

| Field | Description |
|-------|-------------|
| Integration mode | **Hop managed** (default), **External read-only**, or **Custom pipelines** |
| Physical table name | Target table in the vault database; defaults to the link name |
| Link hash key field name | Surrogate key column (e.g. `lnk_order_line_hk`). Empty defaults to link name + `_LK` |
| Record source field name | Optional per-link override for the record source **column** name in the link table |
| Has descriptive attributes | Documentation flag when you plan a link satellite; does not change link loading |
| Participating hubs | At least two hubs. **Order matters** — it defines hash-key column order and link hash calculation |
| Participating link satellites | Link satellites that describe this relationship (parent link is set on each satellite) |

Define hubs before adding them to a link. Use **Check model** / **Validate** to catch missing hubs, empty hub-source mappings, or type mismatches.

## Driving keys tab

Use **driving keys** when the same hub appears more than once in one link under different roles — for example a route with *from location* and *to location*, both referencing a Location hub.

| Concept | Where you set it |
|---------|------------------|
| Driving key **name** | **Driving keys** tab (target column names, e.g. `FROM_LOCATION_HK`) |
| Driving key **source column** | **Hub sources** → edit hub mapping → driving key source fields |

Driving keys disambiguate **roles**. They are not the same as dependent child keys (see next section).

## Dependent child keys tab

Use **dependent child keys** for **transactional** (non-historized) link grain: the same hub combination can appear more than once, and each occurrence must get its own link hash.

Classic retail case: order lines. The same product can appear twice on one order (different unit price or promotion). Without a dependent child key, both lines collapse to one `lnk_order_line_hk`.

| Column in the table | Description |
|---------------------|-------------|
| Target column name | Column stored on the link table and used as the post-rename field name (e.g. `line_number`) |
| Source field name | Column in the link record source; when empty, defaults to the target column name |
| Data type / length / precision | Used for DDL on the link table (e.g. Integer, length 9) |
| Description | Optional business description |

Rules:

* Zero rows = **standard link** (distinct hub combinations only).
* One or more rows = those values are **included in the link hash after the hub hashes** (list order) and **materialized** on the link table.
* Source fields must exist on each configured **Hub sources** catalog feed (validated by **Validate** / Check model).

| Feature | Purpose |
|---------|---------|
| **Driving keys** | Same hub type more than once with different roles (from / to) |
| **Dependent child keys** | Same hubs, repeated events or line items (`line_number`, transaction id, measurement timestamp) |

**Screenshot (docs):** retail `lnk_order_line` with dependent child key `line_number` is illustrated in the product docs as `docs/images/data-vault-link-table-with-dependent-child-keys.png` (Edit Link dialog, **Dependent child keys** tab).

## Hub sources tab

Each row is one **catalog DV_SOURCE** that supplies relationship rows for this link. Hop generates **one update pipeline per hub source**.

For every source you must:

1. Select the **Data Vault Source** (catalog feed).
2. Click **Edit hub source mappings…** and, for **each participating hub**, map source columns to that hub's business keys.

See the **Link hub source** help (opened from that sub-dialog) for the detailed mapping workflow.

Typical pattern: an order-line table feeds `lnk_order_line`. The source has `order_id` and `product_id` columns. You map `order_id` → `hub_order.order_id` and `product_id` → `hub_product.product_id` even when column names match — explicit mappings are required for every hub.

Dependent child key **source field names** must also be present on that feed (they are selected automatically from the link definition; you do not re-map them per hub).

## Satellite sources tab

When a link has descriptive attributes, create a **link satellite** (parent link set on the satellite's General tab) and list it under **Participating link satellites** on the Options tab.

Each row on **Satellite sources** is one catalog **DV_SOURCE** feeding those link satellites. Click **Edit satellite source mappings…** to map source columns to satellite attribute fields (and multi-active driving keys only when the satellite itself is multi-active).

For transactional links with a dependent child key on the link, line/event identity usually lives on the **link**. The satellite then holds measures only (quantity, price, …) under a unique parent link hash — multi-active driving keys on the satellite are often unnecessary.

The link hub source and link satellite source can point at the **same** catalog feed (common for bridge tables that carry both keys and attributes) or at different feeds.

## How link loading works

For each entry on **Hub sources**, the generated pipeline:

1. Reads mapped business key columns, driving keys (if any), and dependent child key source fields from the source.
2. Computes a hash key for each participating hub from those business key values.
3. Computes the link hash key from the hub hash keys **plus** dependent child key values (in configured order).
4. Renames dependent child source fields to target column names for storage.
5. Compares against the existing link table on the link hash key.
6. Inserts only new link rows, with load date and record source.

The record source **value** written to each row comes from the catalog source (fixed indicator or indicator field), not from a field on the link dialog.

## Worked example: order line link (transactional)

`lnk_order_line` connects `hub_order` and `hub_product` from source `E2E-order-line`:

| Hub | Hub business key | Source column |
|-----|------------------|---------------|
| hub_order | order_id | order_id |
| hub_product | product_id | product_id |

**Dependent child key** (this dialog, **Dependent child keys** tab):

| Target column | Source field | Type |
|---------------|--------------|------|
| line_number | line_number | Integer |

Link hash ≈ hash(order_hk, product_hk, line_number). Same product on two lines of one order → **two** link rows.

Link satellite `sat_lnk_order_line` (quantity, unit_price, discount_pct) uses the same source `E2E-order-line` with attribute mappings on the **Satellite sources** tab. Parent hash is the event-level `lnk_order_line_hk` — no multi-active `line_number` driving key on the satellite.

## Tips

- Configure catalog **DV_SOURCE** feeds in the Data Catalog perspective before binding link sources.
- Map **every** participating hub for **every** hub source — partial mappings fail at runtime.
- Keep hub order consistent; it affects physical column order and hashing.
- Use **driving keys** for role-playing; use **dependent child keys** for repeated events / line items.
- Run **Validate** / **Check model** before updates to validate source field names exist in the catalog feed and types match.
- For link satellites, set the parent link on the satellite editor, then wire **Satellite sources** here.
