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

# Data Vault model settings

Open with **Edit model** on the Data Vault graph toolbar. The name and description at the top belong to this `.hdv` file. **Configuration** points at a shared project Data Vault configuration (recommended). Leave it empty only for a legacy model that still embeds its settings.

When a project configuration is selected, edit hashing, naming, and load options there — they apply to every model that references it. When the picker is empty, the tabs below edit the embedded copy.

If the project does not have a shared configuration yet, use **Tools → Configure EDW setup...** to create the standard catalog and configuration objects.

There is one target database per configuration. Table dialogs can still override a column name.

## General tab

Target database and hashing strategy.

- **Target database** — Hop connection where vault tables are created and loaded (DDL, quoting, generated pipelines). Required for Generate DDL, Debug, and Data Vault Update.
- **Data catalog connection** — Optional catalog used when resolving `DV_SOURCE` feeds for this model.
- **Hash algorithm** — Surrogate key algorithm (MD5, SHA-256, SHA-1, SHA-512).
- **Hash key data type** — Physical storage: HEX (default), STRING, or BINARY. BINARY needs Hop 2.19.0+ for correct sort order.
- **Hash content casing** — Upper, lower, or preserve business-key casing before hashing (UPPER is the usual Data Vault choice).
- **Business key delimiter** — Joins multipartite key parts for hashing and composed hub business-key strings (for example `||` or `#`).
- **Hash content prefix / suffix** — Optional text around the concatenated hash input. Supports variables. A trailing suffix is common for VaultSpeed-style recipes and is **not** written into a stored composed business-key column.
- **Null placeholder** — Substituted for NULL key parts during hashing and composed BK storage (default `^^`).
- **Trim business keys** — Strip leading/trailing whitespace before hashing and composition (on by default).
- **Hash composed business key** — When enabled, composite hub keys hash the stored composed column only. Default is off: hash ordered **source parts** (better VaultSpeed trailing-suffix parity). Satellites and links always hash from mapped source parts.

## Unknown records tab

Classic Data Vault "unknown" / ghost **sentinel row** in hubs and links (one shared row, not a per-key placeholder).

- **Generate unknown record** — Expect an unknown sentinel. Data Vault Update can insert it when **Ensure unknown and invalid records** is enabled.
- **Unknown business key value** — Stored BK; also used to compute the hub hash when the hash value field is empty.
- **Unknown hash key value** — Optional fixed hub hash. Supports variables and Hop hex expressions. Empty means hash the unknown business key.
- **Unknown link hash key value** — Optional fixed link hash. Empty means derive it from participating hub unknown hashes.
- **Unknown record source** — `RECORD_SOURCE` on those rows (default `UNKNOWN`).

## Invalid records tab

Sentinel row for malformed or rejected keys (same pattern as unknown).

- **Generate invalid record** — Expect an invalid sentinel; Update can insert missing rows.
- **Invalid business key / hash key / link hash key / record source** — Same roles as the Unknown tab (default record source `INVALID`; default invalid hash is all `FF` bytes for MD5-style keys).

## Orphan handling tab

What generated **link and satellite** loads do when a child feed arrives before its parent hub, or with a null business key. This is a model policy plus generated pipeline steps — not a new warehouse layer. Default **PASS** matches earlier releases (no extra runtime). Late-arriving *dimensions* (fact before dim member) are a separate dimensional setting.

- **Orphan handling policy**
  - **PASS** — Hash and load the child even if the hub row is missing (dangling hashes unless foreign keys are on).
  - **INFER** — Insert a placeholder hub row with the real business key and inferred record source; null keys use the unknown sentinel when that tab is enabled.
  - **SENTINEL** — Remap null or missing parents to the shared unknown ghost row (lossy for a known late key).
  - **QUARANTINE** — Do not load the child; write it to the quarantine table.
  - **FAIL** — Abort the pipeline.
- **Inferred record source** — Value stamped on placeholder hub rows (default `INFERRED`) so they are not confused with a master feed.
- **Store inferred flag** — When enabled, hub tables gain a boolean column. Off by default so existing DDL does not change.
- **Inferred flag field** — Name of that column (default `is_inferred`). Supports variables.
- **Quarantine table** — Shared table on the vault database (default `dv_orphan_quarantine`). Created on first Data Vault Update when any table uses QUARANTINE.

Link and satellite dialogs can override this policy (`INHERIT` keeps the model default). The hub dialog has **Allow inferred inserts** (allowed when unset; refused for external hubs).

For a *valid* business key seen first on a child feed, prefer **Also load parent hubs...** on the link or satellite, or **Also load parent hubs from child/link feeds** when generating a vault from a source model. That reuses the existing hub → link → satellite load order.

## Standard columns tab

Names and patterns for columns on every Data Vault table.

- **Load date field name** — Batch load timestamp (default `LOAD_DATE`). One value for the whole Update run.
- **Load end date field name** — Optional satellite end-date column when end-dating is used.
- **Record source field name** — Origin feed column (default `RECORD_SOURCE`). Hubs can override per table; hub satellites use the parent hub override when set.
- **Record source field length** — Character length (default 100). Supports variables.
- **Store load cycle ID** — Off by default. Adds an integer audit column; each Update allocates the next ID from a control table and stamps every row in the run (including sentinels).
- **Load cycle ID field name** — Default `LOAD_CYCLE_ID`. Supports variables.
- **Load cycle control table** — Counter table on the target database (default `dv_load_cycle`). Created on first allocate. The value is also published as `${DV_LOAD_CYCLE_ID}`.
- **Use load end date pattern** — Documents end-dating on satellites. Generated loads are still insert-only change detection.
- **Generate primary keys in DDL** — Off by default. CREATE TABLE only (not ALTER): hub hash; link hash; satellite parent hash + driving key (if any) + load date. Enabling foreign keys also adds PKs on parent tables.
- **Generate foreign keys in DDL** — Off by default. CREATE TABLE only. Skipped on engines without FKs (for example SingleStore). A child feed that is not a hub record source is the usual Data Vault pattern and is not a model-check error. With FKs on, a missing parent key can still fail at load time; use **Also load parent hubs...** or an orphan policy other than PASS if children can arrive first.

## Target loading tab

How generated pipelines write rows.

- **Target table batch size** — Table Output commit size (default 1000). Supports variables.
- **Target table parallel copies** — Parallel Table Output copies (default 1). In staging-file bulk mode, copies write separate shard files.
- **Target load mode** — Table Output, native bulk, or staging file (options depend on the target engine).
- **Bulk load staging folder / delimiter / enclosure / encoding** — Used when the mode is staging file. Folder default `${java.io.tmpdir}/dv2/bulk/`.
- **Bulk load requires local file** — Staging files must be on the local filesystem (needed for MySQL `LOAD DATA LOCAL INFILE`).
- **Sort rows in memory** — Sort Rows buffer before disk spill (default 1000000). Used where a merge key is computed in Hop. Hub updates use SQL `ORDER BY` only.
- **Execution logging level** — Log level for generated load pipelines (for example from Data Vault Update).
- **Create shard key on hash key (SingleStore)** — Enabled automatically when the target is SingleStore unless you turn it off. Appends `SHARD KEY` on the hash key in CREATE TABLE.
- **Include driving keys in shard key (SingleStore)** — For multi-active satellites, include the driving key with the parent hash in the shard key.

## Generated artifacts tab

Where generated update pipelines (and bulk-load workflows) are named and saved.

- **Generated artifact folder** — When set, `.hpl` files and bulk-load `.hwf` files are written here before execution (for example `${PROJECT_HOME}/generated-pipelines`). Empty means run in memory only.
- **Hub / link / satellite pipeline name prefix** — Defaults `hub-`, `link-`, `sat-`. Full pipeline name is prefix + table + `-` + source.
- **Bulk workflow name prefix** — Master workflow when target load mode is staging file (default `DV Bulk Update - ` + model name).

## Per-table overrides

Hashing and most names come from this dialog. Table editors still allow:

- **Hub** — Hash key field, record source field, allow inferred inserts, extra record sources.
- **Link** — Link hash key field, record source field, orphan policy override, **Also load parent hubs...**.
- **Satellite** — Uses the parent hash column; orphan policy override; **Also load parent hub...**.
