# Marquez / OpenLineage lineage integration

> **Status:** Implemented for issue **#101** (released in **0.5.0**).  
> **User documentation:** [../openlineage-export.adoc](../openlineage-export.adoc)

## Summary

Export **model-derived** OpenLineage `RunEvent` documents from hop-data-vault:

- Table **and** column lineage from existing `DvModelLineageCollector` / `BvModelLineageCollector` / `DmModelLineageCollector`
- Workflow action **Export data lineage** (resource definition group scope)
- Destinations: **file folder** and/or **HTTP** (`POST …/api/v1/lineage`)
- Local Marquez: `./scripts/run-marquez.sh up`

This supersedes the earlier table-only “hook into Data Vault Update” MVP draft below.

## Implementation map

| Component | Location |
|-----------|----------|
| Snapshot → RunEvent mapper | `org.hopper.edw.datavault.openlineage.OpenLineageSnapshotMapper` |
| File writer | `OpenLineageFileWriter` |
| HTTP client | `OpenLineageHttpClient` |
| Orchestration | `OpenLineageExportService` |
| Ops enrichment | `OpsLineageEnricher` |
| Workflow action | `ActionExportDataLineage` |
| Marquez scripts | `scripts/run-marquez.sh`, `scripts/docker/compose.marquez.yml` |

Execution-map OpenLineage (`OpenLineageExportSupport`) remains for **pipeline-graph** table-level jobs; model export is the primary path for column lineage and Marquez.

## Historical MVP notes (obsolete)

The original draft proposed:

1. Table-only edges on DV Update only  
2. No dedicated export action  
3. No column facets  

Those items are replaced by the design above. Keep this file as a pointer so older doc links remain valid.
