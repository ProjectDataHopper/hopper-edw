# Cross-file relationships (AI)

How hop-data-vault artifacts compose a typical project.

```text
  .hsm (source model)
       │ publish tables / queries
       ▼
  Catalog FILE store
    hop/{project}/sources/*.json   (DV_SOURCE / COMPOSITE / …)
       │  referenced by name
       ▼
  .hdv  Raw Data Vault (hubs, links, sats, ref tables, linked tables)
       │  dataVaultModelPath
       ▼
  .hbv  Business Vault (SCD2, PIT, business tables)
       │
       ▼
  .hdm  Dimensional (dims, facts, bridges, aliases)

  metadata/resource-definition-group/*.json
       lists paths to .hdv / .hbv / .hdm for validation & group update

  metadata/data-catalog/*.json
       points at catalog storage root

  metadata/rdbms/*.json          (Hop core) CRM / Vault / OPS connections
  metadata/execution-metrics-profile/*.json
  metadata/data-quality-rule-set/*.json
```

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

1. **Hub business keys** must map to source fields that exist on the hub’s record source(s).
2. **Satellite** `hub` (or `link`) name must match a table name already on the same `.hdv` canvas (or a linked hub).
3. **Link** `hubNames` / `linkHubSources` must list participating hubs with BK source mappings.
4. **BV** tables map attributes from **DV satellites**, not directly from CRM unless designed that way.
5. **RDG** paths should use `${PROJECT_HOME}/models/…` and list models in intended update order within each layer (DV → BV → DM).

## Suggested edit order

1. Source tables / catalog sources  
2. `.hdv` hubs → satellites → links → reference tables  
3. `.hbv` SCD2/PIT  
4. `.hdm` dims then facts  
5. RDG + workflows  
