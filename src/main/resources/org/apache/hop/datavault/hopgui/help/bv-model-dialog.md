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

# Business Vault model settings

Open with **Edit model** on the Business Vault graph toolbar.

## Header

- **Name** / **Description** — model identity (name may sync with the `.hbv` filename).

## General tab

- **Target database** — connection where Business Vault tables and views are created and loaded.
- **Data catalog** — metadata catalog connection (`MetaSelectionLine`). Used for catalog publish and model-registry lookups. Business Vault Update falls back to this when the action leaves catalog empty.
- **SCD2 defaults** — functional timestamp, load-date fallback, valid-from / valid-to column names.

## Data Vault tables (canvas, not this dialog)

There is **no** single “linked Data Vault model file” setting. Reference hubs, links, and satellites from **one or more** `.hdv` models by placing canvas aliases:

- Context menu: **Add Hub / Satellite / Link reference**
- Pick a catalog-registered DV model or browse a `.hdv`
- Each alias stores an optional `referencedModelFilename` for multi-model sources

SCD2, PIT, SQL `ref()`, and Business Vault Update resolve DV tables from those aliases.

## Target loading / Generated artifacts tabs

Shared target-load mode (table output vs staging file), batch size, parallel copies, and generated pipeline / workflow name prefixes.

## Tips

- Use **Check model** on the graph before running Business Vault Update.
- Multi-step BV layers can also reference other `.hbv` files via SQL `ref()` or canvas **Business Vault references**.
