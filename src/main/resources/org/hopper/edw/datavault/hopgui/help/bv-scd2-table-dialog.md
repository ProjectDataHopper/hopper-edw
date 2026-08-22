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

# Business Vault SCD2 table

An SCD2 table in the Business Vault model (`.hbv`) materializes a **functional Type 2 timeline** from one or more raw Data Vault satellites: version rows with validity bounds (`valid from` / `valid to`), not a Type 1 overwrite table and not a PIT pointer table.

Use this dialog to name the target, choose satellite derivatives, set the functional timestamp and build mode, and map attributes for multi-satellite merges.

## What this table is for

- **Harmonize** multi-satellite history into one consumable timeline (for example Customer 360).
- Keep **organizational memory**: each business change becomes a new version with closed/open validity intervals.
- Feed **downstream** dimensional models, reports, and other BV objects with a clean SCD Type 2 grain.

Raw vault satellites remain insert-only (technical load history). SCD2 rewrites that history into **business-effective** intervals driven by the functional timestamp you configure.

## General

| Field | Purpose |
|-------|---------|
| **Name** | Logical name of the SCD2 table on the canvas and in the model. |
| **Physical table name** | Target table in the Business Vault database. |
| **Derivatives** | One or more DV satellite references. At least one satellite is required. |
| **Include hash key** | When enabled, the parent hub/link hash key is written to the BV table. |
| **Functional timestamp** | Column used as the timeline for validity bounds (see resolution order below). |
| **Build mode** | `FULL_REBUILD` (truncate and reload) or `INCREMENTAL` (watermark, append, close open rows). |
| **Incremental watermark field** | Optional BV column for `MAX(...)` watermark reads; defaults to the functional timestamp. |
| **Valid from / Valid to** | Output column names for SCD2 interval bounds (model defaults apply when empty). |

### Functional timestamp resolution

Priority order:

1. Per-satellite override in **Satellite configs**
2. Per-table **functional timestamp** on this dialog
3. Model-level functional timestamp in Business Vault configuration
4. Load-date fallback from Data Vault configuration (often `x_load_ts`)

The functional timestamp drives interval boundaries — not the technical load date unless you choose that column explicitly.

### Build mode

- **Full rebuild** — truncate and reload the BV table from satellite history.
- **Incremental** — read satellite deltas above a watermark, close the prior open version, append new versions. Prefer after an initial full load when history is large.

Use **Debug** or **Show build pipeline** on the canvas to inspect the generated Hop pipeline before workflow runs.

## Field mappings

Required when **two or more** satellites feed the table. Map each `(satellite, source field)` to a BV target column name. Validation checks parent hub/link consistency, source field existence, and unique target columns.

Single-satellite tables can pass attributes through under DV names unless you add explicit mappings for renaming or subsetting.

## Satellite configs

Optional per-satellite settings for multi-satellite merges:

- Functional timestamp override for that satellite
- Source indicator value when you need to tag which satellite contributed a version

## Type 1 vs Type 2 (important)

**This dialog builds pure SCD Type 2 only.** Every mapped business attribute is carried on version rows; history is not rewritten when a “current” value changes later.

| Need | Recommended approach |
|------|----------------------|
| Full history / as-of attributes | Keep them on this SCD2 table (Type 2). |
| Always show the **current** value (e.g. current sales rep on old facts) | Join a **current** object at mart or query time — open SCD2 row, latest satellite, or a BV SQL “current” table/view. Prefer **hybrid Type 1 + Type 2 in the dimensional model**, not by overwriting SCD2 history. |
| Mix Type 1 and Type 2 **in the same BV SCD2 table** | Not supported as a field mapping mode. Hybrid presentation belongs downstream or in a separate current-state object. |
| Type 1 / Type 2 modes **on a PIT table** | Wrong layer. PIT stores hub key + snapshot + satellite load-timestamp **pointers**; compose attributes (including always-current ones) when you join. |

**Why not Type 1 “update all history” on BV SCD2?**

1. As-of queries become silently wrong for columns that only *look* historical.
2. BV intermediate history should stay audit-friendly; back-propagating current values fights that role.
3. Mass updates across all versions are expensive and awkward with incremental watermarks.

Business Vault SCD2 is **consumable organizational memory**. Dimensional models own presentation rules such as hybrid slowly changing dimensions.

Longer guidance: project doc `docs/business-vault-scd2.adoc` (section *Type 1 vs Type 2 and hybrid attributes*).

## Tips

- Run **Check model** on the `.hbv` file before **Business Vault Update** or Debug.
- Confirm **Data Vault target** (read satellites) and **Business Vault target** (write SCD2) connections both resolve in project metadata.
- After changing the linked `.hdv` on disk, use **Reload DV model** on the Business Vault toolbar.
- External read-only satellites are skipped by Data Vault Update but remain valid SCD2 sources if the vault DB can read them.
- Multi-satellite example: `integration-tests/tests/multi-satellite-bv/customer-360.hbv`.

## Related docs

- `docs/business-vault-scd2.adoc` — generation, incremental mode, multi-satellite
- `docs/business-vault-pit.adoc` — PIT (technical as-of pointers; sibling of SCD2)
- `docs/business-vault-overview.adoc` — BV layer roles
- `docs/business-vault-update-action.adoc` — workflow action that runs SCD2 and PIT builds
