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

# Data type mapping profile

A **Data type mapping** is a project-level pre-modeling policy. You attach one or more profiles to source model cards (table, query, JSON, pipeline). On **publish**, the **effective** field layout (types, lengths, conversion masks, renames) is written to the catalog. Generated DV/BV/DM loads inject a Select Values transform so runtime matches that contract.

See also: product guide `docs/data-type-mappings.adoc`.

## Identity

| Field | Meaning |
|-------|---------|
| **Name** | Unique metadata name (what you select on the source dialog). |
| **Description** | Free-text documentation for the profile. |

## Scope (optional)

Scope is **advisory**: used for bulk-suggest and mismatch warnings. When a profile is **explicitly attached** to a source, it still applies even if scope would not match.

### Source kinds

Multi-select list. Choose **zero or more** of:

`DATABASE`, `CSV`, `PARQUET`, `ICEBERG`, `JSON`, `PIPELINE`, `COMPOSITE`

- **None selected** → profile applies to any kind when attached.
- **One or more** → hints that the profile is intended for those feed types.

### Patterns

Each pattern field matches **one string** (connection name, schema, file path / topic, catalog namespace). They are **not** comma-separated lists.

| Pattern | Typical value |
|---------|----------------|
| **Database name pattern** | Hop RDBMS connection name |
| **Schema name pattern** | Database schema |
| **Path / topic pattern** | File path, Iceberg table path, Kafka topic |
| **Catalog namespace pattern** | Catalog namespace (e.g. `hop/retail-example/sources`) |

**Syntax (one pattern per field):**

| Input | Meaning |
|-------|---------|
| *(empty)* | Matches everything |
| `CRM` | Exact match (case-insensitive) |
| `CRM*` or `*landing*` | Glob: `*` = any characters, `?` = one character |
| `^crm_.*` or patterns with `(` / `[` | Treated as a Java regular expression |

Hop variables are allowed in pattern fields (e.g. `${PROJECT_NAME}*`).

## Rules table

Rules are evaluated **top to bottom**. Within a profile, the **first matching rule** wins for each field. When several profiles are attached to a source, later profiles **overlay** attributes (attribute-level merge). Per-source field overrides win last.

### Match columns (when does a rule apply?)

All non-empty match criteria must succeed (AND). At least one match criterion should be set.

| Column | Meaning |
|--------|---------|
| **Id** | Stable rule id (optional; auto-derived from Name if empty). |
| **Name** | Human label for the rule. |
| **Match hop type** | Hop type name (`String`, `Integer`, …). Empty = any type. |
| **Match source type** | Pattern against native/SQL type (`VARCHAR`, `LONGTEXT`, `TEXT*`, …). Same pattern syntax as scope. |
| **Match field name** | Pattern against the physical field name (`*_at`, `CUST_*`, …). |
| **Length absent** | `Y` = only fields with missing / empty / negative length. |

### Target columns (what to set)

Empty target cells leave that attribute unchanged (for multi-profile layering).

| Column | Meaning |
|--------|---------|
| **Target hop type** | Resulting Hop type after mapping. |
| **Target length** | e.g. `2000` for bounded strings (avoids TEXT/LONGTEXT). |
| **Target precision** | Scale for numeric types. |
| **Target name** | Rename the field (empty = keep name). |
| **Conversion mask** | Hop conversion mask (required for safe String→Date/Timestamp). Examples: `yyyy-MM-dd`, `yyyy-MM-dd HH:mm:ss`, `#.#`. |
| **Decimal** / **Grouping** | Number parsing symbols (e.g. `.` and `,`). |
| **Locale** | Date locale (e.g. `en_US`). |
| **Time zone** | Date/time zone (e.g. `UTC`, `Europe/Brussels`). |
| **Enabled** | `N` to disable the rule without deleting it. |

### Safety tips

- **String → Date/Timestamp** without a conversion mask is a validation **error**.
- Prefer explicit lengths on strings used as satellite attributes so DDL does not collapse to LOB types.
- Renames change the **effective** catalog field name; the physical stream name is kept as `sourceStreamName` for load pipelines.

## Typical example

1. Rule: Match hop type `String`, Length absent `Y` → Target hop type `String`, Target length `2000`.
2. Rule: Match field name `*_at`, Match hop type `String` → Target hop type `Timestamp`, Conversion mask `yyyy-MM-dd HH:mm:ss`, Time zone `UTC`.

Attach the profile on a source model card (**Data type mapping** tab → Select / Add), then publish to the catalog.
