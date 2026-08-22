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

# Hub editor

A **Hub** stores the business key(s) that identify a core concept (Customer, Order, Product, …) plus a surrogate hash key.

## Three meanings of "record source"

| Concept | Where you set it | Example |
|---------|------------------|---------|
| **Catalog feed** | Record sources tab | `CRM-customer` (a DV_SOURCE in the Data Catalog) |
| **Value in the vault row** | Catalog source → source indicator or indicator field | `CRM` or a column from the feed |
| **Target column name** | Model config, or per-hub override below | `RECORD_SOURCE` or `src_invoice` |

The **Record sources** tab lists **catalog feeds**, not table columns.

## Options tab

- **Integration mode** — Hop managed (default), external read-only, or custom pipelines.
- **Physical table name** — Target table; defaults to the hub name.
- **Hash key field name** — Surrogate key column (e.g. `customer_hk`). Satellites and links reference this.
- **Record source field name** — Optional per-hub override for the record source **column** name in the hub table. Hub satellites inherit this name when loading.

## Keys tab

- **Business keys** — Natural keys in hub column order (composite keys supported).
- **Source field name** — Physical column in a feed when it differs from the hub column name.
- **Source system** — Which catalog feed this key mapping belongs to. Must appear on the Record sources tab.
- **Load from source** — Fills keys from the first feed on the Record sources tab (run once per feed for multi-source hubs).

## Record sources tab

List every **catalog DV_SOURCE** that supplies business keys for this hub. One hub box can have many feeds; use **Source system** on the Keys tab to map different column names per feed.

Use **Check model** on the toolbar to validate empty record sources, missing catalog indicators, and key mappings before running pipelines.