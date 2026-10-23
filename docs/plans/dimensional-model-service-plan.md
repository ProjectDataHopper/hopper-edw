# Proposal: Dimensional model service (issue #223)

Design note for a Hop Server JDBC schema over dimensional models, with field masking and a query audit table. The service, masking policy, and audit table described here are implemented. Issue: [ProjectDataHopper/hopper-edw#223](https://github.com/ProjectDataHopper/hopper-edw/issues/223) (PII in dimensional models).

## Problem

At the customer, the warehouse is PII. Developers and analysts are not allowed to query it, and non-production databases are not allowed to hold a copy. Those environments then have no representative volumes, distributions, or join shapes. Report and dashboard work in DBeaver, Oracle Analytics, and MicroStrategy is guesswork.

The data has to stay in production. What leaves the trusted network is a masked projection, through a JDBC endpoint those tools already know how to use. Counts, sums, and joins still run on the real warehouse, so volume and logical-consistency questions stay accurate. Columns flagged with a Hop masking pattern never reach the client in the clear.

## What already exists

The Hop Server virtual database is the thin driver `jdbc:hop-hsm:` (`hop-hsm-jdbc`, zero dependencies) talking to servlet `/hop/sourceModelData`.

- Each **Source model service** (metadata category **Servers and Web Services**) is one JDBC schema. The name is public. The `.hsm` path stays on the server.
- Actions are `schemas`, `tables`, `columns`, `query`, and `ping`. Columns already carry a `pk` flag, and the thin driver exposes `getPrimaryKeys`. `getImportedKeys` returns empty. Prepared-statement `setXxx` throws.
- Free SQL is planned with Calcite. A single-database source query is pushed down. Source masking cards run **Mask fields** inside that plan. Hidden cards are omitted.
- A dimensional model (`.hdm`) already has one target connection, physical table names, surrogate and natural keys, attributes, measures, degenerate dimensions, and fact-to-dimension roles. Those tables are loaded in the warehouse. There is no JDBC surface over them, and fields have no masking pattern.
- The semantic layer (`.hsl`) is a Hop presentation API. BI tools bring their own semantic model and want tables, columns, and foreign keys. This service does not publish `.hsl` files.

The URL `jdbc:hop-hsm:` stays. Plugin ids and the servlet path stay. CLAUDE.md forbids renaming them. One driver registration in DBeaver, OAS, and MicroStrategy covers every schema.

## Deployment

```
DBeaver / Oracle Analytics / MicroStrategy
  (non-prod machines, no warehouse password)
        │  jdbc:hop-hsm://analyst:***@hop-server:8888/retail
        ▼
Hop Server in the trusted network
  schema crm     → Source model service     → source-tables-crm.hsm
  schema retail  → Dimensional model service → retail-f-orders.hdm
        │  warehouse credential stays on the server
        ▼
Production dimensional database
        │  SELECT pushed down
        ▼
Mask fields on flagged columns only
        │  masked rows to the client
        └── one audit row (who, when, SQL, status, row count)
```

Non-prod databases stay empty of real rows. The developer login is a Hop Server user. The warehouse login is only on the server. The same virtual database lists source schemas and dimensional schemas together, which is what “source and dimensional models alike” means.

Cleartext exists in Hop Server memory for the page being masked, and in the warehouse. It is not written to the audit table, the Hop log, or the JDBC response. The Mask fields map table (when a pattern uses database storage) and the audit table are operational stores. They are not granted to the analyst login.

## Dimensional model service

New metadata type `dimensional-model-service`, category `HopMetadataCategory.SERVERS`, same place as Source model service. GUI editor follows `SourceModelServiceEditor` (name, help button, grouped options). Lombok on the metadata class. Strings in the package `messages_en_US.properties`, with variables quoted as `'${VAR}'`.

| Group | Fields |
|---|---|
| General | Enabled. Server-side `.hdm` path (variables allowed, never sent to the client). Description. Default row limit (10 000) and hard max (100 000), same resolution rules as `SourceModelService.resolveRowLimit`. Query timeout seconds (default 60, 0 = driver default). Allow schema metadata. |
| Access | Allowed users. Empty means any authenticated Hop Server user who already has `run.execute` (the permission the current servlet requires). A non-empty list is matched against `request.getRemoteUser()`. |
| Masking | Policy: **Safe** (default) or **Display**. See below. |
| Auditing | Enabled. Audit connection (Hop database metadata). Optional schema. Table name, default `edw_dimensional_query_audit`. Store query text verbatim (default off). Button **Create audit table**. |

A model check reports an error when this service’s name equals an enabled Source model service name. The servlet returns 409 if both still exist at runtime. Disabled services are omitted from `schemas`.

The editor is the Hop GUI surface for the endpoint. External tools only consume JDBC.

## Schemas the client sees

`action=schemas` returns enabled source services and enabled dimensional services as one schema list. `tables`, `columns`, and `query` resolve the schema name to one service and dispatch.

A dimensional schema exposes modeled tables only:

- Dimensions, junk dimensions, facts, factless facts, bridges.
- Conformed dimension aliases, under the alias name, physically resolved with `DmDimensionResolutionSupport` to the referenced table.
- Columns are the modeled ones: surrogate key, natural keys, attributes, Type 3 previous column, SCD `date_from` / `date_to` when the dimension has them, measures, degenerate dimensions, and fact role foreign-key columns.

`SELECT *` is expanded to that column list before pushdown. Physical columns that are not on the model (load bookkeeping the canvas does not list) are not in the result.

Role-playing dimensions stay one table. Two fact roles against `d_date` are two foreign keys (`order_date_key`, `ship_date_key`) to the same primary key. That is the star OAS and MicroStrategy can import.

JDBC metadata to add, source schemas returning an empty key set so today’s behavior holds:

- `getPrimaryKeys` already works from the `pk` flag on `columns`. Dimensional columns set `pk` on the surrogate (or the natural key when the table has no surrogate).
- New action `importedKeys`. `getImportedKeys` / `getExportedKeys` / `getCrossReference` on the thin driver call it. Rows come from fact dimension roles: FK column, parent schema, parent table, parent PK column, key sequence, and a generated FK name. Two roles to one date dimension produce two imported-key rows.

The thin jar stays zero-dependency. An older jar still runs queries. Key discovery and bound parameters need the jar built with this change.

## Query execution

Dimensional SQL is not the source-model residual planner. The tables already share one target database (the dimensional configuration’s target connection and its preferred schema).

1. Accept a single read-only statement: `SELECT` or `WITH … SELECT`. Reject a second statement, DML, DDL, and `CALL`.
2. Calcite parses and validates against a schema built from logical canvas names and modeled columns.
3. Apply the masking policy to the validated tree.
4. Rewrite logical table and column identifiers to the physical schema, table, and column, quoted for the target dialect.
5. Bind client parameters on a warehouse `PreparedStatement`. Do not concatenate parameter values into SQL.
6. Enforce the service row limit and timeout on that statement.
7. When the projection contains a masked column, stream the result through **Mask fields** with the named `org.apache.hop.pipeline.transforms.maskfields.MaskingPattern`. Unmasked queries return the pushdown result directly.
8. Write one audit row, then return rows. Fail closed when auditing is on and the insert fails: the client gets an error and no result set.

Supported SQL in the first build is what Calcite can validate and the target dialect can run after identifier rewrite: joins, filters, `GROUP BY`, aggregates, `ORDER BY`, `LIMIT` / `FETCH`, and CTEs. Expressions that do not touch a masked column are pushed with the rest of the statement. There is no residual Sort / Merge Join path for dimensional queries, because every table of one service is on one connection.

Bound parameters are part of this slice. OAS and MicroStrategy prepare statements. The thin driver today throws on `setXxx`. It will keep `?` in the SQL and POST the values with the query (form body, not the URL). The audit log stores the SQL with placeholders and a parameter count. Parameter values are not stored.

### Masking policy

A field is masked when its optional pattern name is set. The name is a Hop **Masking pattern** metadata item, the same object source masking cards use. The pattern definition is not copied onto the model.

Patterns can be set on dimension attributes, natural keys, fact measures, and degenerate dimensions. The Type 3 previous column inherits the attribute’s pattern. Surrogate keys and fact foreign keys cannot carry a pattern. A model check says so, because masking a join key makes the star unusable and does not hide the attribute on the other side.

| Policy | Behavior |
|---|---|
| **Safe** (default) | A masked column may appear only as a bare select item (`customer_name`, `d.email`). The planner rejects it inside `WHERE`, `JOIN`, `GROUP BY`, `HAVING`, `ORDER BY`, `DISTINCT` on that column, aggregates (`MIN`, `MAX`, `COUNT(DISTINCT …)`), and any function, `CAST`, `CASE`, or concatenation. `COUNT(*)` and aggregates of unmasked measures still push down. |
| **Display** | The rewritten statement is pushed as written, including predicates and aggregates on masked columns. Mask fields then rewrites flagged output cells. Existence checks and `MIN` / `MAX` still run on cleartext inside the warehouse. |

Safe is the customer default. It answers “how many orders, what is the sum, what does the report layout look like” and it blocks the usual probes (`WHERE email = '…'`, `ORDER BY salary DESC`, `SUBSTRING(email, 1, 3)`). Display is the behavior described in the issue text, available when a named analyst must filter on a sensitive column and the grid should still be masked. The service editor is where that choice is made.

Missing or unloadable patterns fail the query. The column is not returned in the clear. `SET_NULL` and `SET_EMPTY` are valid masks. A model check warns when the chosen pattern stores tokens in memory, because those tokens change every execution and a dashboard grouped by a masked name will not be stable. Database storage on the pattern is the setting that keeps the same source value mapped to the same token across reports. That map table stays in the trusted zone.

### Where the flag is edited

`HopGuiDmTableDialog` field grids, through `HopGuiDmFieldTableSupport` and `HopGuiDmFieldEditorDialog`:

- Dimension attributes, natural keys.
- Fact measures, degenerate dimensions.

Add a **Masking pattern** column (combo of `MaskingPattern` metadata names, blank = not masked) and the same open-or-edit pattern action the source masking dialog already uses. The canvas field list shows the pattern name when one is set, so a modeler can see which columns a JDBC client will receive masked.

Table context action **Preview masked rows** runs `SELECT` of the modeled columns through the same executor and opens `ShowRowsDialog`. That is the in-Hop check that the JDBC client will not see cleartext. Direct **Preview target table** stays a warehouse preview for people who already operate the model.

## Audit table

One table, one row per attempt, including rejected and failed queries. A probe that the policy rejects is still an event.

Button **Create audit table** on the service editor runs DDL on the chosen connection. The runtime does not create the table as a side effect of a user query.

Columns:

| Column | Contents |
|---|---|
| `audit_id` | Surrogate, generated by the database. |
| `event_time` | Server timestamp. |
| `principal` | Authenticated Hop Server user. |
| `service_name` | JDBC schema name. |
| `client_info` | Optional JDBC client property, truncated. |
| `remote_addr` | Caller address. |
| `sql_text` | Client SQL. With verbatim storage off, string, date, and numeric literals are replaced with `?`. |
| `sql_rewritten` | Physical SQL sent to the warehouse, literals redacted the same way. Empty when the planner rejects the statement before rewrite. |
| `parameter_count` | Bound parameters. Values are never stored. |
| `literal_count` | Literals seen before redaction. |
| `status` | `OK`, `REJECTED`, or `ERROR`. |
| `status_message` | Planner or database message, truncated. No cell values. |
| `row_count` | Rows returned. Zero for a rejection. |
| `duration_ms` | Server-side duration. |
| `masked_columns` | Logical columns that Mask fields rewrote, comma-separated. |

Verbatim SQL is off by default because the statement the analyst typed can itself contain PII (`WHERE email = 'alice@example.com'`). Turning it on is an explicit choice on a service whose audit connection is already locked down.

When auditing is enabled and the insert fails, the query does not return rows. A warning in the metadata check tells the editor when the table is missing. Source model services do not write this table. Adding the same block there can be a follow-up. This proposal keeps auditing on the dimensional service, which is the extra option asked for.

## Build order

These are implementation slices for a later go-ahead. This note does not build the service.

1. **Service and pushdown.** Metadata, editor, help topic, schema union, name collision, `SELECT` rewrite, row limit, timeout, modeled column list, primary keys, imported keys, prepared-statement binding. Unit tests on H2 for rewrite, star expansion, and two date roles. Identifier quoting checks for Postgres and SQL Server.
2. **Masking.** Pattern property and dialog column on attributes, natural keys, measures, and degenerate dimensions. Safe and Display policies. Mask fields on the result stream. Preview masked rows. Tests: bare column is masked, `WHERE` on a masked column is rejected under Safe, `SUM` of an unmasked measure is pushed, `SELECT *` does not add unmodeled columns, a missing pattern fails closed.
3. **Audit and access.** Audit fields, create-table button, redaction, fail-closed insert, allowed users. Test the row shape, redaction, and a down audit connection.

A Postgres smoke through the servlet against a small `.hdm` is enough to prove the path. The four-engine integration matrix is not required for a first merge. Quoting of physical identifiers is covered by unit tests per dialect. Full-matrix runs matter later if dialect-specific SQL generation grows beyond identifier rewrite.

## Documentation

- This note: `docs/plans/dimensional-model-service-plan.md`, indexed from `docs/plans/README.md`.
- When the feature is built: a section in `docs/dimensional-modeler-overview.adoc` (service, masking column, safe vs display, audit table, the DBeaver / OAS / MicroStrategy URL). Reuse the existing source-model JDBC screenshots as the driver setup. Add one service-editor screenshot when the GUI exists. Help topic for the metadata editor, wired like `HelpTopics.SOURCE_MODEL_SERVICE`. Changelog bullet under Unreleased at implementation time, not with this proposal.

## Out of scope

- Publishing raw vault or business vault models as schemas. The same service shape could do that later.
- Publishing `.hsl` presentations over JDBC.
- Row-level or user-specific filters (“this analyst sees one region”).
- A second JDBC URL or a rename of `jdbc:hop-hsm:`.
- Loading a masked copy into non-prod. The production query is the point.
- Warehouse dynamic-masking features. Patterns stay Hop masking patterns so the rule is the same on every engine and matches source masking.
- Auditing source-model service traffic.
- Treating the JDBC endpoint as a substitute for warehouse grants. Users who also have a direct warehouse login bypass the mask. The deployment gives those users only the Hop Server login.

## Risks

- Safe mode will reject some report SQL that wraps a sensitive column in `CAST` or `COALESCE`. The fix on the model side is to expose the column bare and let the BI tool format it, or to switch that service to Display with eyes open.
- Consistent tokens depend on the pattern’s database map. A memory pattern looks masked and is still unstable. The model check warns. It does not block.
- Hop Server logs and a heap dump on that host can still contain a page of cleartext. Row limits bound the page. Query execution must not log row data.
- `run.execute` is a broad Hop Server permission. The allowed-users list is what narrows a service when several Hop users share the server.
