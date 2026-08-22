# Plan: Snowflake as a fifth EDW engine

| Field | Value |
|-------|--------|
| **Document** | Snowflake support completion |
| **Date** | 2026-08-21 |
| **Status** | **In progress** — dialect + bulk-loader wiring and opt-in Docker profile exist; **not** claimed as a supported engine until a live emulator (or real account) run is green |
| **Audience** | Engineers finishing hop-datavault Snowflake support |
| **Hop pin** | **2.19.0** (do not bump) |
| **Related** | Hop Snowflake plugin (`SNOWFLAKE`, `SnowflakeBulkLoader`); LocalStack image `localstack/snowflake` |

This is an **internal completion plan**, not end-user documentation. Product docs stay under `docs/*.adoc` and must be updated only when a phase below is actually proven.

---

## 1. Goal

Treat **Snowflake** as a first-class Data Vault / Business Vault / dimensional **target** (and optional source) the same way PostgreSQL, MySQL, SingleStore, and SQL Server are treated:

- Model-driven **DDL** is correct for Snowflake types and identifier rules.
- Generated **load pipelines** run with Table Output (default) and **Snowflake bulk loader** (`PUT` + `COPY INTO`).
- Users operate this entirely from **Hop GUI** (connection dialog + existing Target load tab). No file-only flags.
- Maintainers can run an **opt-in** Docker suite against `localstack/snowflake`. The default four-engine matrix stays unchanged.

**Definition of done (product claim):**

1. Unit tests for dialect SQL and bulk-loader configuration stay green (`mvn test`).
2. A documented LocalStack (or real Snowflake) run completes **vault1** plus the shared DV suites that MySQL already runs (hub/sat/link, load-end-date, hash-key HEX, status-tracking, multi-source hub), with golden unit tests passing.
3. Native bulk loader is exercised on at least one hub/satellite load (dedicated workflow or a Snowflake profile with `targetLoadMode=NATIVE_BULK`).
4. User docs describe the connection recipe (warehouse, schema `PUBLIC`, quoting, HEX vs BINARY, bulk loader).
5. Remaining emulator gaps (especially `TRUNCATE`) are documented, not papered over.

Until (2) is true, do **not** list Snowflake next to the four engines as “tested” in README / feature-overview.

---

## 2. Constraints (do not violate)

| Constraint | Rule |
|------------|------|
| Hop version | Stay on **2.19.0**. Snowflake JDBC / bulk loader already ship in Hop. Do not vendor a copy of `SnowflakeDatabaseMeta`. |
| GUI parity | Connection = Hop **Snowflake** type. Load mode = existing **Target load** tab (`TABLE_OUTPUT` / `NATIVE_BULK`). Optional later: CLUSTER BY checkbox, enabled only when plugin id is `SNOWFLAKE`. |
| Default CI matrix | `ALL_DATABASES="postgres mysql singlestore sqlserver"`. Snowflake is **named opt-in** only. |
| LocalStack license | Image is **proprietary** and needs `LOCALSTACK_AUTH_TOKEN`. Never commit the token. Skip with exit 0 when unset. |
| Hash keys | Integration default remains **HEX** until BINARY columns round-trip (DDL, JDBC, SortRows, MergeRows) on a live engine. |
| Identifiers | Unquoted Snowflake names fold to **UPPERCASE**. Test profile must keep `QUOTE_ALL_FIELDS=Y`. |
| No Hop core patches in this repo | URL suffix, BINARY→VARIANT, TIMESTAMP_LTZ belong in **apache/hop**. Work around here. |

---

## 3. What is already implemented (do not redo)

Code and fixtures from the first implementation pass. Treat these as the baseline, not as “Snowflake is done.”

### 3.1 Hop 2.19.0 (upstream — reuse)

| Piece | Plugin id | Role |
|-------|-----------|------|
| Database type | `SNOWFLAKE` | JDBC `net.snowflake.client.jdbc.SnowflakeDriver`, warehouse GUI field, default port 443, `ssl=on` |
| Bulk load transform | `SnowflakeBulkLoader` | Local CSV → `PUT file://…` → `COPY INTO` |
| Warehouse action | Snowflake warehouse manager | Optional; warehouse on the JDBC URL is enough for loads |

### 3.2 Plugin dialect (this repo)

| Area | Behavior |
|------|----------|
| Engine helper | `DvDdlSupport.isSnowflake()` (`pluginId == SNOWFLAKE`) |
| BINARY DDL | Hop emits `VARIANT` for `TYPE_BINARY`. Plugin rewrites to `BINARY(n)` (or `BINARY` if length ≤ 0). JSON `VARIANT` is left alone (rewrite only when the Hop type is BINARY). |
| Timestamp / date DDL | Hop emits `TIMESTAMP_LTZ` for DATE and TIMESTAMP. Plugin rewrites TIMESTAMP → `TIMESTAMP_NTZ`, DATE → `DATE`. Statement-level `TIMESTAMP_LTZ` → `TIMESTAMP_NTZ` on ALTER/CREATE text. |
| Unicode | `DvTargetUnicodeCapabilitySupport` returns **CAPABLE** for Snowflake without a live probe (VARCHAR is UTF-8). |
| Hash-key ORDER BY | STRING/HEX: Hop SortRows (no Snowflake collation registry yet). BINARY: plain SQL `ORDER BY`. |
| BV SQL views | `CREATE OR REPLACE VIEW` |
| BV SQL tables | `CREATE OR REPLACE TABLE … AS` |
| PIT spine | `PitSqlDialect.SNOWFLAKE`: `GENERATOR` + `DATEADD`, `TIMESTAMP_NTZ` literals; ANSI `TIMESTAMP '…'` left unchanged |
| Metrics / overview DDL | Explicit `SNOWFLAKE` case uses the Postgres-shaped `CREATE TABLE IF NOT EXISTS` statements |
| Native bulk loader config | **User stage** (`LOCATION_TYPE_USER` / `@~/table`), schema from preferred schema or **`PUBLIC`**. CSV, abort on error, remove staged files. |

`STAGING_FILE` is intentionally **not** advertised for Snowflake (`DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId` returns null). Native bulk already is file + PUT + COPY.

### 3.3 Opt-in Docker profile (unproven live)

| Artifact | Role |
|----------|------|
| `scripts/docker/compose.snowflake.yml` | `localstack/snowflake:2026.07.0` + `docker-hop:latest`; network alias `snowflake.localhost.localstack.cloud` |
| `scripts/docker/snowflake-init.sql` | Warehouse + `TEST` database + `PUBLIC` (also mirrored in connection `SQL_CONNECT`) |
| `scripts/docker/Dockerfile` + `jdbc-versions.env` | Pins `net.snowflake:snowflake-jdbc:4.3.1` into `/opt/hop/lib/jdbc/` |
| `integration-tests/metadata/rdbms/profiles/snowflake/{CRM,Vault}.json` | `pluginId: SNOWFLAKE`, `QUOTE_ALL_FIELDS=Y`, `PREFERRED_SCHEMA_NAME=PUBLIC`, `manualUrl`, `ssl=off` |
| `integration-tests/environments/docker-snowflake.json` | Host/port/user/db/warehouse/`DB_TYPE=snowflake` |
| `integration-tests/run-tests-all-databases.sh` | Allows `snowflake`; **skips** if `LOCALSTACK_AUTH_TOKEN` is unset; not in default `ALL_DATABASES` |
| `tests/run-tests.hwf` | Multi-satellite BV still Postgres-only (Snowflake takes the skip path) |

Hop `SnowflakeDatabaseMeta.getURL()` always appends `.snowflakecomputing.com`. Emulator connections **must** use `manualUrl`, for example:

```text
jdbc:snowflake://snowflake.localhost.localstack.cloud:4566/?account=test&db=TEST&warehouse=COMPUTE_WH&schema=PUBLIC&ssl=off
```

### 3.4 Unit tests already covering the dialect

Run without Docker:

```bash
mvn test -Dtest=DvDdlSupportTest,DvTargetUnicodeCapabilitySupportTest,DvHashKeyOrderStrategySupportTest,DvBulkLoadCommandSupportTest,BvPitSnapshotSpineSupportTest,BvSqlViewPipelineSupportTest,LoadRunMetricsDdlSupportTest
```

These prove SQL shape and configuration. They do **not** prove JDBC, PUT/COPY, identifier quoting against a live engine, or golden datasets.

---

## 4. Why the work is not finished

| Gap | Why it blocks a “supported engine” claim |
|-----|------------------------------------------|
| **No live run** | LocalStack compose and profiles have never been executed against a token-backed emulator (or a real account). |
| **Hop URL / SSL / warehouse bootstrap** | First JDBC connect may fail if `db=TEST` is in the URL before `CREATE DATABASE`, or if `SQL_CONNECT` is not executed as multiple statements. Needs a spike, then possibly an init sidecar. |
| **`TRUNCATE TABLE`** | LocalStack feature coverage marks table TRUNCATE unsupported. Reference-table FULL_REPLACE uses Table Output truncate. Real Snowflake supports TRUNCATE; emulator may not. |
| **Collation suite always runs** | `tests/run-tests.hwf` hops **every** engine through `tests/sqlserver-collation/`. French `COLLATE` on CRM source is Postgres/SQL Server-specific and will fail on Snowflake unless gated. |
| **STRING/HEX sort vs Java** | No live probe of Snowflake VARCHAR order vs Hop SortRows. Using Hop SortRows is correct but slower; SQL `ORDER BY` is only safe after a probe. |
| **BINARY hash keys** | DDL rewrite exists; JDBC get/set bytes, SortRows, and MergeRows are unproven. Stay on HEX until then. |
| **Native bulk live path** | User-stage configuration is unit-tested only. `PUT` from the Hop container work directory + LocalStack PUT/COPY must be demonstrated. |
| **Docs vs code** | [performance-tuning.md](../performance-tuning.md) still says Snowflake bulk uses “Internal stage”. Feature-overview / getting-started do not yet describe Snowflake as an engine. |
| **Quality / harvest SQL** | `DataQualityHistoryDdlSupport` still “default = Postgres”. `DatabaseProfileCollector` Postgres regex (`~`) is not enabled for Snowflake (good) but `LENGTH` vs character length should be confirmed. |
| **Hop core defects** | BINARY→VARIANT and TIMESTAMP_LTZ remain wrong in Hop; URL suffix blocks LocalStack without `manualUrl`. |

---

## 5. Architecture (how Snowflake fits)

```
Hop GUI Snowflake connection
        │  warehouse, schema PUBLIC, QUOTE_ALL_FIELDS
        │  production: host = account.snowflakecomputing.com
        │  emulator:   manualUrl → localstack host:4566, ssl=off
        ▼
DV / BV / DM configuration  →  target database = Vault
        │
        ├─ DDL: DvDdlSupport (BINARY / TIMESTAMP_NTZ / DATE rewrite)
        ├─ Loads: Table Output  or  SnowflakeBulkLoader (user stage)
        └─ BV SQL / PIT: PitSqlDialect.SNOWFLAKE
```

Snowflake object model vs Hop `DatabaseMeta`:

| Snowflake | Hop field |
|-----------|-----------|
| Account | hostname (or `manualUrl`) |
| Warehouse (compute) | `warehouse` + URL `warehouse=` |
| Database | `databaseName` / URL `db=` |
| Schema | `PREFERRED_SCHEMA_NAME` (use `PUBLIC`) |
| Table | modeled physical name, **quoted** |

PK/FK clauses stay enabled (Snowflake accepts them as **unenforced** informational constraints). Do not treat Snowflake like SingleStore (no FKs). Clustering keys (`CLUSTER BY`) are a later optional analog of SingleStore shard keys — correctness does not depend on them.

---

## 6. Remaining work, by phase

### Phase A — Live connectivity spike (gates everything else)

**Goal:** one Hop 2.19 container talks to LocalStack (or a real trial account) and runs DDL + a few rows.

Checklist (record actual results in this plan or a short findings note; do not guess):

1. `LOCALSTACK_AUTH_TOKEN` set; `docker compose -f scripts/docker/compose.snowflake.yml up db` becomes healthy.
2. From the Hop container, JDBC `manualUrl` connects (SSL off, port 4566, host alias).
3. `CREATE WAREHOUSE` / `CREATE DATABASE TEST` / `CREATE SCHEMA PUBLIC` succeed (init SQL, `SQL_CONNECT`, or a dedicated init container).
4. Quoted `CREATE TABLE "hub_probe" ("customer_hk" VARCHAR(32), "x_load_ts" TIMESTAMP_NTZ)` round-trips: Table Output insert, Table Input read, names stay lowercase.
5. Hop `getDDL()` / plugin rewrite: BINARY column is `BINARY(16)` not `VARIANT`; load date is `TIMESTAMP_NTZ` not `TIMESTAMP_LTZ`.
6. `TRUNCATE TABLE` — succeed or fail. If fail, reference-table suite must be skipped or switched to `DELETE` on emulator only.
7. Snowflake bulk loader: user stage `PUT` + `COPY INTO` for a small CSV.
8. `ORDER BY` quoted VARCHAR vs Java `String.compareTo` for the dash-pair probes in `DvHashKeyOrderStrategySupport` (`0-10-…` vs `0-100-…`).

**Exit:** a written spike result: what LocalStack can and cannot do; whether `SQL_CONNECT` is enough or compose needs `db-init`; whether TRUNCATE is a skip.

If LocalStack is unavailable, run the same checklist against a real Snowflake account with a throwaway database (still do not add that path to default CI).

### Phase B — Integration-suite gating and bootstrap

Only after Phase A.

1. **Gate `string-collation`.** Today `run-tests.hwf` always runs `tests/sqlserver-collation/update-string-collation.hwf`. Add an EVAL (same pattern as `run multi-satellite BV?`) so it runs only when `DB_TYPE` is empty, `postgres`, or `sqlserver`. Snowflake (and MySQL/SingleStore) must skip it.
2. **Fix bootstrap** based on the spike: init sidecar vs `SQL_CONNECT` vs connecting without `db=` then `USE DATABASE`. Keep CRM and Vault on one database (`TEST`) like Postgres tests, unless two databases prove necessary.
3. **Reference table:** if emulator has no TRUNCATE, either skip `tests/reference-table/` when `DB_TYPE=snowflake`, or generate `DELETE FROM` instead of truncate for Snowflake Table Output on that path only (real Snowflake should keep TRUNCATE).
4. Rebuild `docker-hop:latest` so `snowflake-jdbc` is actually in the image (`Dockerfile` already copies it; image is stale until rebuild).
5. Run `./run-tests-all-databases.sh snowflake` and iterate until the MySQL-like suite set is green (not multi-satellite BV, not French collation).

Expected skip set (until investigated further):

| Suite | Snowflake |
|-------|-----------|
| `sqlserver-collation` | Skip (gate required) |
| `multi-satellite-bv*` | Skip (already Postgres-only) |
| `reference-table` | Skip on emulator if no TRUNCATE; run on real Snowflake |
| vault1, multi-active sat, link sat, driving-key, load-end-date, hash-key, status-tracking, multi-source hub, composite-hub-bk | Run |

### Phase C — Native bulk loader proof

1. Keep default integration config on **Table Output** (matches other engines).
2. Add a **Snowflake-only** workflow or environment overlay with `targetLoadMode=NATIVE_BULK` for `tests/basic/vault1` (or a single hub + satellite). Do not change committed `.hdv` defaults used by Postgres.
3. Confirm generated transform: location **user**, schema **PUBLIC**, work directory writable inside the Hop container, `PUT` paths use `file://`, `COPY INTO` loads rows, staged files removed.
4. Fix [performance-tuning.md](../performance-tuning.md): Snowflake row must say **user stage** (`@~/table`) + `COPY INTO`, not “Internal stage”, unless a later named-stage option is added.
5. If user stage is insufficient in production (named internal stage, S3 external stage), add a **GUI** field on DV/BV/DM Target load (stage name / location type) — not a sidecar XML file.

Optional later: `STAGING_FILE` mode that writes CSV then a workflow action with `PUT`/`COPY`. Not needed if native bulk works.

### Phase D — Dialect follow-ups (only if the live run shows them)

| Item | When to do it |
|------|----------------|
| Register Snowflake STRING/HEX `ORDER BY` (no COLLATE or a binary collation) + `hasStaticTrust` | Only if Phase A probe matches Java code-point order |
| `DataQualityHistoryDdlSupport` explicit `SNOWFLAKE` case | If Postgres-shaped DDL fails (BOOLEAN / `CREATE SCHEMA IF NOT EXISTS`) |
| `DatabaseProfileCollector` `LENGTH` / `CHAR_LENGTH` / `LEN` | If quality string-length rules mis-count on VARCHAR |
| Snowflake regex (`REGEXP_*`) for quality `REGEX` rules | Optional; today non-Postgres engines skip Postgres `~` |
| `SQL_CONNECT` split / warehouse auto-resume | If first statement fails because warehouse is suspended |
| Quoted vs unquoted information_schema | If catalog harvest / live schema import returns uppercase names despite `QUOTE_ALL_FIELDS` |

Do **not** silently map Snowflake PIT to Postgres `generate_series` (already a dedicated dialect). If LocalStack lacks `GENERATOR`/`SEQ4`, replace the spine with a digit cross-join (SingleStore-style) **after** proving the gap — keep real Snowflake on `GENERATOR`.

### Phase E — Product docs and “supported engine” wording

After a green live suite (Phase B), not before:

| Document | Change |
|----------|--------|
| [feature-overview.adoc](../feature-overview.adoc) | Mention Snowflake as an EDW target; note emulator tests are opt-in / LocalStack ≠ production |
| [getting-started-edw.adoc](../getting-started-edw.adoc) or a short `docs/snowflake.adoc` | Connection recipe: warehouse, database, schema `PUBLIC`, quote identifiers, HEX hash keys, Target load Table Output vs Native bulk |
| [datavault-configuration.adoc](../datavault-configuration.adoc) | Target load: Snowflake bulk loader uses user stage |
| [performance-tuning.md](../performance-tuning.md) | User stage, not internal stage |
| [PROJECT.md](../../integration-tests/PROJECT.md) / [SCRIPTS.md](../../integration-tests/SCRIPTS.md) | Update with spike findings (TRUNCATE, init) |
| [CLAUDE.md](../../CLAUDE.md) | Keep “opt-in, not default matrix” |
| README / presentations | Only once maintainers agree the live run counts |

GUI-facing copy: Target load combo already lists Native bulk when the Hop Snowflake plugin is installed. Document that in the configuration guide; no new metadata type.

### Phase F — Optional / later (not required for the first claim)

| Item | Notes |
|------|--------|
| Hop PR: skip `.snowflakecomputing.com` for localhost / `*.localstack.cloud` | Then emulator can drop `manualUrl`. Requires a deliberate `hop.version` bump here — **out of band**. |
| Hop PR: `TYPE_BINARY` → `BINARY(n)`, DATE/TIMESTAMP → `TIMESTAMP_NTZ` | Plugin rewrite can shrink once Hop 2.20+ is pinned. |
| `CLUSTER BY` on hash keys | GUI checkbox on DV configuration, enabled only for `SNOWFLAKE` (mirror SingleStore shard-key widgets). |
| Named internal stage / external S3 stage | GUI on Target load tab. |
| Real-account GitHub Action | Secrets; never LocalStack token in public logs. |
| Multi-satellite BV goldens on Snowflake | Same Postgres-oriented row-count issue as MySQL/SQL Server. |
| Snowpipe, key-pair/SSO, private link, Iceberg-on-Snowflake | Document only. |
| Adding Snowflake to `ALL_DATABASES` | **No**, unless LocalStack becomes free/open and TRUNCATE parity is real. |

---

## 7. GUI surfaces (parity checklist)

Users must be able to do all of this without editing JSON by hand (except LocalStack `manualUrl`, which is an emulator workaround until Hop URL generation is fixed):

| Task | Where |
|------|--------|
| Create CRM / Vault connections | Hop metadata **Snowflake** type: host/account, port, user, password, database, **warehouse**, preferred schema `PUBLIC`, quote all fields |
| Point models at Vault | Existing DV/BV/DM configuration **target database** |
| Choose Table Output vs bulk | Existing **Target load** tab — Native bulk appears when `SnowflakeBulkLoader` is installed |
| See generated DDL | Existing generate-SQL / modeler preview (must show `BINARY` / `TIMESTAMP_NTZ` after rewrite) |
| Optional CLUSTER BY (Phase F) | New checkbox on DV configuration, enabled iff `DvDdlSupport.isSnowflake(target)` |

Emulator-only: paste `manualUrl` on the connection (Hop already has this field). Document it; do not add a parallel “LocalStack mode” file.

---

## 8. Risk register

| Risk | Mitigation |
|------|------------|
| LocalStack ≠ Snowflake | Never claim production support from emulator green alone. One real-account smoke (vault1 + native bulk) on the release checklist. |
| Proprietary token | Opt-in runner; skip if unset; no default-matrix inclusion. |
| `TRUNCATE` missing | Spike first; skip or DELETE-only on emulator; keep TRUNCATE on real Snowflake. |
| Uppercase folding | `QUOTE_ALL_FIELDS=Y` on the Snowflake profile; golden CSVs stay lowercase. |
| BINARY → VARIANT if rewrite is skipped | HEX default; field-level rewrite only when Hop type is BINARY (not JSON). |
| First-connect chicken-and-egg (`db=TEST` missing) | Phase A; init sidecar if `SQL_CONNECT` never runs. |
| Bulk loader classloader | Keep reflection (`classLoaderGroup=snowflake`). Do not compile `hop-databases-snowflake` into the plugin jar (`provided`/test only if needed). |
| Collation suite on Snowflake | Phase B EVAL gate. |

---

## 9. Verification ladder

1. `mvn test` (or the focused list in §3.4) after any dialect/Java change.
2. `mvn spotless:apply` before commit.
3. Phase A spike notes checked in (this plan or `docs/plans/snowflake-spike-notes.md`).
4. `LOCALSTACK_AUTH_TOKEN=… ./run-tests-all-databases.sh snowflake` — vault1 + shared DV suites.
5. Native bulk overlay (Phase C).
6. Optional real Snowflake account: same Hop metadata, different environment file (host/account/warehouse/auth). Do not commit secrets.
7. Docs in Phase E only after (4) is green.

A Postgres-only green run is **not** evidence for Snowflake.

---

## 10. Suggested implementation order

```text
A  spike (connect, DDL, truncate, PUT/COPY, ORDER BY)
B  gate collation + bootstrap + run-tests-all-databases.sh snowflake
C  native bulk proof + performance-tuning wording
D  only the dialect follow-ups the spike proved necessary
E  user docs + “supported target” wording
F  Hop PRs / CLUSTER BY / real-account CI  (optional)
```

Do not start E or a README engine-list change before B is green. Do not add Snowflake to `ALL_DATABASES` in F.

---

## 11. Out of scope for completion of *this* plan

- Bumping `hop.version` off 2.19.0.
- Reimplementing JDBC or COPY in this plugin.
- Making LocalStack a required CI engine.
- Snowpipe, streams, tasks, dynamic tables, Iceberg-on-Snowflake as load targets.
- Collation-remediation suites (SQL Server / Postgres French source vs UTF-8 vault).
- Changing HEX as the plugin-wide hash default.

---

## 12. File map (for the next implementer)

**Already touched (baseline):**

- `src/main/java/org/hopper/edw/datavault/metadata/DvDdlSupport.java`
- `src/main/java/org/hopper/edw/datavault/metadata/DvBulkLoadTransformSupport.java`
- `src/main/java/org/hopper/edw/datavault/metadata/DvTargetUnicodeCapabilitySupport.java`
- `src/main/java/org/hopper/edw/datavault/metadata/businessvault/BvPitSnapshotSpineSupport.java`
- `src/main/java/org/hopper/edw/datavault/metadata/businessvault/BvSqlViewPipelineSupport.java`
- `src/main/java/org/hopper/edw/datavault/metrics/LoadRunMetricsDdlSupport.java`
- `src/main/java/org/hopper/edw/datavault/metrics/WorkflowLoadOverviewDdlSupport.java`
- `scripts/docker/compose.snowflake.yml`, `Dockerfile`, `jdbc-versions.env`, `snowflake-init.sql`
- `integration-tests/metadata/rdbms/profiles/snowflake/`
- `integration-tests/environments/docker-snowflake.json`
- `integration-tests/run-tests-all-databases.sh`, `tests/run-tests.hwf`

**Likely next edits:**

- `integration-tests/tests/run-tests.hwf` — EVAL before `string-collation`
- Compose / profile JSON — bootstrap from spike
- Optional skip for `reference-table` when `DB_TYPE=snowflake`
- `docs/performance-tuning.md`, then Phase E user docs
- Optional: `DvHashKeyOrderStrategySupport.candidateCollations` / `hasStaticTrust` after probe
- Optional: `org.hopper.edw.quality.history.DataQualityHistoryDdlSupport`
