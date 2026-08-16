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

# Target type mapping

A **Target type mapping** is a project-level policy for how Hop types become **native SQL types** in generated DDL. It is the inverse of a **Data type mapping** (source → Hop). Custom rules run **before** Hop dialect defaults. Unmatched columns keep today’s database-specific types.

See also: product guide `docs/target-type-mappings.adoc`.

## Identity

| Field | Meaning |
|-------|---------|
| **Name** | Unique metadata name (what you select on Record Definition DDL and update actions). |
| **Description** | Free-text documentation. |
| **Target database** | Optional Hop RDBMS connection this mapping is intended for. When **exactly one** mapping names a connection, DDL for that connection uses it automatically. |

## Rules

Rules are evaluated **top to bottom**. The **first matching enabled rule** wins. Put more specific rules first (for example String length 1 → `CHAR(1)` before String max 2000 → `VARCHAR({length})`).

All non-empty match criteria must succeed (AND). At least one match criterion is required.

| Column | Meaning |
|--------|---------|
| **Id / Name** | Optional labels. |
| **Match hop type** | `String`, `Integer`, `Timestamp`, … Empty = any type. |
| **Min / max length** | Inclusive Hop length bounds. Empty = unbounded. Variables allowed. |
| **Min / max precision** | Inclusive Hop precision bounds. |
| **Length absent** | Match only when Hop length is missing / −1. |
| **Match field name** | Optional glob/regex (`*_hk`, `flag*`). |
| **Target SQL type** | Native type template. |
| **Enabled** | Disabled rules are skipped. |

### Target SQL type

1. Hop variables are resolved (`${VARCHAR_TYPE}`).
2. `{length}` and `{precision}` (case-insensitive) are replaced from the Hop field.

If the template still contains `{length}` and the field has no length, the rule does **not** match (fall through).

Examples:

- `CHAR(1)`
- `NVARCHAR({length})`
- `timestamp(6) with time zone`

The template is **user-authored dialect text**. This plugin does not rewrite `NVARCHAR` to Postgres `VARCHAR`.

## Where it applies

- **Record Definition DDL** transform (optional selector)
- **Update resource definition group** and single-model DV / BV / DM Update (optional selector; group copies the name to children)
- Modeler SQL / schema gate / remediation: unique auto-match on the model target connection

SQL Server vault UTF-8 length×3 / `COLLATE` is **not** applied to a column that a rule already typed.

Rules apply to every column, including technical ones (`load_dts`, `record_source`). Exclude names with **Match field name** if needed.
