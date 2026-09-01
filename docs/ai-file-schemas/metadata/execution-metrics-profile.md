# `metadata/execution-metrics-profile` — Execution metrics profile

**Java:** `org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta`  
**Schema:** [execution-metrics-profile.schema.json](execution-metrics-profile.schema.json)  
**Example:** `retail-example/metadata/execution-metrics-profile/retail-execution-metrics.json`

## Purpose

Configures **load-run metrics** collection/publication for Data Vault / BV / DM update workflows (JSON files, optional OPS database tables, catalog definitions).

## Key fields

| Field | Role |
|-------|------|
| `name` | Profile name referenced from update actions |
| `enabled` | Master switch |
| `metricsOutputFolder` | e.g. `${PROJECT_HOME}/work/metrics` |
| `targetDatabaseConnection` | Often OPS |
| `operationsSchema` | e.g. `dv_ops` (blank = connection default) |
| `autoCreateTables` | Create missing OPS tables on first publish |
| `dataCatalogConnection` | Catalog for publishing metric record defs |
| Threshold fields | Alert/tuning heuristics |

GUI: **Generate SQL** on the metadata editor emits dialect-specific `CREATE` statements for `load_run`, `load_pipeline_metric`, and related tables.

## Anti-patterns

- Hardcoding `/tmp` only without project-relative metrics folder for shared demos.  
- Enabling DB publish without OPS connection / schema.  
