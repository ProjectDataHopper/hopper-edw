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

# Validate resource definitions — options

Shown when you click **Validate sources** on a **Resource definition group**. Choose the baseline and check axes **before** validation runs.

## Baseline (truth)

| Choice | When to use |
|--------|-------------|
| **Current working catalog** | After automatic source refresh / drift notification. The catalog is the contract of record. |
| **Catalog version tag** | Compare against a frozen snapshot under `catalog-versions/`. Snapshots are immutable. |

Validation **never** modifies the baseline. Catalog version snapshots are never rewritten by remediation either.

## What to check

| Axis | Meaning |
|------|---------|
| **Live source systems** | Physical source schema vs baseline contract |
| **Working catalog vs version** | Detect catalog field edits relative to a tag |
| **Target models** | DV/BV/DM attributes narrower than the baseline contract |
| **Target databases** | Physical EDW tables that need DDL to match models |
| **Downstream impact / lineage** | Blast radius annotations |

## Reports

Optionally write HTML and Markdown reports (same format as the CI schema gate) under a Hop VFS path.

## Related

- Results dialog after validation
- Remediation proposals (expand field lengths in models/schemas vs refuse longer live fields using a version baseline)
