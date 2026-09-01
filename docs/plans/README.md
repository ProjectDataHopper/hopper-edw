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

# Internal design notes

Planning and implementation documents for features in progress. These are **not** end-user documentation.

For product documentation, start at [../README.md](../README.md) or [../feature-overview.adoc](../feature-overview.adoc).

| Plan | Topic |
|------|--------|
| [hop-lineage-view-plan.md](hop-lineage-view-plan.md) | Issue #79 — Hop Lineage View (`.hlv`) over OpenLineage backends |
| [edw-journey-perspective-plan.md](edw-journey-perspective-plan.md) | EDW Journey perspective (canonical tree over a resource definition group) |
| [data-type-mappings-plan.md](data-type-mappings-plan.md) | Issue #113 — project data type mappings / pre-modeling sources |
| [target-type-mappings-plan.md](target-type-mappings-plan.md) | Issue #127 — Hop type → native SQL type preferences for DDL |
| [snowflake-support-plan.md](snowflake-support-plan.md) | Snowflake as a fifth EDW engine (dialect done; live LocalStack/real-account completion) |
| [jinja-dbt-import-plan.md](jinja-dbt-import-plan.md) | Issue #72 — Jinja macros + dbt-core importer for BV SQL |
| [bv-scd2-calculations-plan.md](bv-scd2-calculations-plan.md) | Issue #150 — BV SCD2 SQL calculations + tests |
| [late-arriving-dimensions-plan.md](late-arriving-dimensions-plan.md) | Issue #109 — late-arriving dimensions / inferred members (deferred) |
| [orphan-prevention-plan.md](orphan-prevention-plan.md) | Issue #77 — optional DV orphan handling / placeholder hubs |
| (session plan / issue #112) | Metadata harvesting as distinct EDW phase — product doc: [../metadata-harvesting.adoc](../metadata-harvesting.adoc) |
| [source-to-vault-generation-plan.md](source-to-vault-generation-plan.md) | Issue #125 — generate hubs/links/sats from a source model |