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

# Jinja macro library

A **Jinja macro library** is project metadata: named macros and default `var()` values used when Business Vault SQL is rendered.

## When to use

SQL business tables that need more than `{{ ref() }}` / `{{ source() }}` — loops, `{% set %}`, `{% if %}`, and reusable `{% macro %}` helpers.

## Fields

- **Name** — metadata object name (for example `retail-macros`)
- **Package name** — optional dbt package / project hint
- **Enabled** — disabled libraries are ignored at render time
- **Variables** — defaults for `{{ var('name') }}` when no Hop variable of that name is set (Hop variables win)
- **Macros** — name, description, optional origin path, and Jinja source

Prefer a full block:

```
{% macro cents_to_dollars(col) -%}
  (({{ col }}) / 100.0)
{%- endmacro %}
```

**Test render** expands a snippet against the macros and vars in this editor (with dummy `ref` / `source` names).

## Scope

Business Vault configuration can list library names (comma-separated). Empty means every enabled library.

See `docs/business-vault-sql-view.adoc`.
