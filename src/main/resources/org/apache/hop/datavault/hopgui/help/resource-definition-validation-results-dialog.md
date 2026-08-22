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

# Resource Definition Validation Results

Shows source validation issues for a **Resource definition group** after **Validate sources** (and the options dialog that chooses baseline + axes).

## Layout

1. **Status banner** — `PASS` / `WARNINGS` / `CRITICAL BLOCKED`, issue counts, and the **baseline + checks** that were run
2. **Issue summaries** (top table) — compact rows: Severity, Record definition, Field, Kind, Proposals count
3. **Selected issue details** (bottom pane) — full message, downstream impact, and model usages
4. **Footer** — show acknowledged issues, re-validate (re-opens options), tag catalog version, close

Select a summary row to load its details. Double-click a row (or use **Remediation proposals...**) to apply fixes.

## Detail actions

| Button | Purpose |
|--------|---------|
| **Open source in catalog** | Jump to the source record definition in the Data Catalog perspective |
| **Open target table** | Open the selected model usage (hub, satellite, BV, or DM table) |
| **Remediation proposals...** | Open proposals and apply a fix |
| **Acknowledge...** | Accept a warning with a required comment |
| **Revoke acknowledgement** | Clear a previous acknowledgement |

## Tips

- Long issue text and blast-radius details stay in the **bottom** pane so the summary list stays readable.
- Use **Show acknowledged issues** to review accepted warnings.
- Run **Re-validate** after applying a proposal or editing the catalog.
