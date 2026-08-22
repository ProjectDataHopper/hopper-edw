# Cross-file relationships (AI)

How Data Hopper EDW artifacts compose a typical project.

```text
  .hsm (source model)
       │ publish tables / queries / JSON
       ▼
  Catalog FILE store
    hop/{project}/sources/*.json     type=DV_SOURCE
         layout → dvSource.fields[]
         location → physicalTable | physicalFile | physicalIcebergTable
       │  referenced by name as recordSource
       ▼
  .hdv  Raw Data Vault (hubs, links, sats, ref tables, linked tables)
       │ publish model tables
       ▼
  Catalog FILE store
    hop/{project}/models/{model}/*.json   type=DV_HUB|DV_LINK|DV_SATELLITE|…
         layout → physicalTable.fields[]
       │  dataVaultModelPath
       ▼
  .hbv  Business Vault  → catalog BV_TABLE (physicalTable.fields[])
  .hdm  Dimensional     → catalog DIM_TABLE / FACT_TABLE (physicalTable.fields[])

  hop/{project}/operations/*.json   type=PHYSICAL_TABLE (ops metrics; physicalTable.fields[])

  metadata/resource-definition-group/*.json
       lists paths to .hdv / .hbv / .hdm for validation & group update

  metadata/data-catalog/*.json
       points at catalog storage root

  metadata/rdbms/*.json          (Hop core) CRM / Vault / OPS connections
  metadata/execution-metrics-profile/*.json
  metadata/data-quality-rule-set/*.json
```

Layout is **never** a top-level `rowMetaXml` on write. Details: [metadata/catalog-record-definition.md](metadata/catalog-record-definition.md).

## Retail example map

| Artifact | Path (under `retail-example/`) |
|----------|--------------------------------|
| Source model | `models/source-tables-crm.hsm` |
| Raw DV | `models/retail-360.hdv` |
| BV SCD2 | `models/retail-360.hbv` → `dataVaultModelPath` → retail-360.hdv |
| Dimensional | `models/retail-f-orders.hdm`, `retail-conformed-dims.hdm`, … |
| RDG | `metadata/resource-definition-group/retail-sources.json` |
| Catalog connection | `metadata/data-catalog/local-catalog.json` |
| Connections | `metadata/rdbms/CRM.json`, `Vault.json`, `OPS.json` |

## Naming rules AIs often break

1. **Hub business keys** must map to source fields that exist on the hub’s record source(s). Composite BKs (`composite=Y`) need ordered `sourceFieldNames` (same part count across multi-source mappings for the same vault name).
2. **Satellite** `hub` (or `link`) name must match a table name already on the same `.hdv` canvas (or a linked hub). `parentKeySourceFields` length = hub hash-input part count (parts for composite hubs — not a composed BK column on the sat).
3. **Link** `hubNames` / `linkHubSources` must list participating hubs with BK source mappings (one source field per multipartite vault BK, or N part fields per composite vault BK).
4. **BV** tables map attributes from **DV satellites**, not directly from CRM unless designed that way.
5. **RDG** paths should use `${PROJECT_HOME}/models/…` and list models in intended update order within each layer (DV → BV → DM).

## Suggested edit order

1. Source tables / catalog sources  
2. `.hdv` hubs → satellites → links → reference tables  
3. `.hbv` SCD2/PIT  
4. `.hdm` dims then facts  
5. RDG + workflows  
