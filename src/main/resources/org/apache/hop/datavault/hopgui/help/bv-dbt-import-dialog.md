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

# Import dbt models

Scan a **dbt-core** project (`dbt_project.yml` + `models/**/*.sql` + YAML + `macros/`) and create SQL business tables on the current Business Vault canvas.

## Steps

1. Browse to the dbt project folder (or any folder under it).
2. **Scan** lists models: name, path, materialization, description, issues.
3. Filter and select the models to import.
4. Choose destination:
   - **Current model** — add cards to this `.hbv`
   - **New .hbv file** — write a new model next to this file
   - **Split by first-level folder** — one `.hbv` per `models/staging`, `models/marts`, …
5. Optionally import `{% macro %}` files into a **Jinja macro library**.
6. **Import**. Authoring SQL stays Jinja; `ref()` / `source()` are not rewritten.

## Mapping

| dbt | Hop |
|-----|-----|
| `view` | VIEW |
| `table` | TABLE |
| `incremental` | TABLE (full refresh; warning) |
| `ephemeral` | VIEW (not inlined; warning) |
| snapshots / seeds / Python | skipped |

YAML descriptions and column notes are copied onto the table. Sources used in SQL are pre-declared on the Sources tab.

See `docs/dbt-import.adoc`.
