# Project metadata JSON (AI)

Hop stores project metadata as JSON under:

```text
${PROJECT_HOME}/metadata/<metadata-key>/<name>.json
```

## Plugin-owned types (schemas in this folder)

| Folder key | Purpose doc | JSON Schema |
|------------|-------------|-------------|
| `data-catalog` | [data-catalog.md](data-catalog.md) | [data-catalog.schema.json](data-catalog.schema.json) |
| `resource-definition-group` | [resource-definition-group.md](resource-definition-group.md) | [resource-definition-group.schema.json](resource-definition-group.schema.json) |
| `execution-metrics-profile` | [execution-metrics-profile.md](execution-metrics-profile.md) | [execution-metrics-profile.schema.json](execution-metrics-profile.schema.json) |
| `data-quality-rule-set` | [data-quality-rule-set.md](data-quality-rule-set.md) | [data-quality-rule-set.schema.json](data-quality-rule-set.schema.json) |

## Catalog FILE store (not under metadata/)

Record definitions (sources/targets) live in the **catalog storage directory** configured by the data-catalog connection (e.g. `${PROJECT_HOME}/work/edw-catalog` or integration-tests `catalog-data/`).

See [catalog-record-definition.md](catalog-record-definition.md) and [catalog-record-definition.schema.json](catalog-record-definition.schema.json).

## Hop core types (no full schema here)

Documented only for orientation — use Hop samples:

| Folder | Example | Role |
|--------|---------|------|
| `rdbms` | `metadata/rdbms/CRM.json` | Database connections |
| `pipeline-run-configuration` | `local.json` | Pipeline engine settings |
| `workflow-run-configuration` | `local.json` | Workflow engine settings |
| `execution-info-location` | `tmp-executions.json` | Where Hop stores execution info |

## Rules for AI

1. Do not invent new metadata folder names — match `@HopMetadata(key=…)` / existing folders.  
2. Keep `name` equal to the filename stem when Hop expects it.  
3. RDG model paths must exist and use `${PROJECT_HOME}` when possible.  
