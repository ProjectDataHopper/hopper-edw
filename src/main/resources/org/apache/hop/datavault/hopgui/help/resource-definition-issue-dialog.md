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

# Remediation Proposals

Opened from **Remediation proposals...** on the Source validation results dialog (or by double-clicking an issue summary).

## Layout

1. **Issue header** — short severity / kind / field / message context
2. **Proposal summaries** (top ~30%) — read-only table with one row per proposal summary
3. **Selected proposal details** (bottom ~70%) — full summary, type, and untruncated details text (wrap + scroll)
4. **Footer** — **Apply proposal** and **Close** always visible at the bottom of the dialog

## Source schema drift (simple rules)

The catalog describes a source table. Validation may compare it to the live source. **Remediation never changes the catalog.**

| Proposal | What it does |
|----------|----------------|
| **Expand field length in target models and database schemas (using the catalog)** | Reads the field length from the catalog, expands mapped model attributes, and writes a SQL workflow for target tables. Catalog is left alone. |
| **Note: live is longer/shorter than the catalog** | Informational. If the live length is the intended truth, update the catalog yourself in the Data Catalog, then re-validate. |

Physical DDL is **not** run when you apply. Run the generated workflow per environment when ready.

Configure the root folder under **Configuration → Data Vault 2.0 → Schema remediation folder**.

## Tips

- Read the **Remediation result** dialog carefully — it lists every model/catalog change and the package path.
- Full issue text and navigation to source/target live on the parent validation results dialog.
