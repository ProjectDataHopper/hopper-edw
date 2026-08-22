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

# SQL business table

Author a SQL query that materialises as a **view** or **table** in the Business Vault database.

## Materialization

- **View** — `CREATE OR REPLACE VIEW name AS query`
- **Table** — `CREATE OR REPLACE TABLE name AS query`

## Schema

Optional target schema for `CREATE VIEW|TABLE` and for `{{ this }}`. Leave empty to use the Business Vault connection default.

## Templates (dbt style)

- `{{ ref('object') }}` — BV table in this model or DV table in the linked vault model
- `{{ ref('model', 'object') }}` — object in another model file. `model` may be a relative path (e.g. `../models/retail-360` or another `.hbv` for multi-step BV layers), with optional extension, a catalog registry basename, or a path under `${PROJECT_HOME}`
- `{{ source('source', 'table') }}` — must be declared on the **Sources** tab
- `{% set %}` / `{% if %}` / `{% for %}` and project macros from a **Jinja macro library** (Metadata perspective)
- `{{ var('name') }}` — Hop variable of that name, else a library default
- `{{ config(...) }}` — ignored at render (set materialization and schema on this dialog)
- `{{ this }}` — quoted name of this table
- `is_incremental()` — always false (full refresh)

Use **Parse / sync refs** after editing SQL — it resolves paths, fills the **References** tab (including resolved model path), and can place **DV** and external **BV** aliases on the canvas. You can also place BV references from the canvas context menu (**Add Business Vault reference**). **Preview SQL** runs a row preview of the resolved query against the BV target database. The **Generated SQL** tab shows the full `CREATE OR REPLACE VIEW|TABLE` statement and refreshes when you open that tab.

See `docs/business-vault-sql-view.adoc` for full documentation.

Samples: `integration-tests/tests/basic/vault1.hbv` (`sat_customer_hb_v`, `satb_customer_hb`) and `retail-example/models/retail-sql.hbv` (`satb_product_hb`).
