# `.hdv` — Raw Data Vault model

**Root element:** `<data-vault-model>`  
**Java:** `org.apache.hop.datavault.metadata.DataVaultModel`  
**Schema:** [hdv.xsd](hdv.xsd) · **Excerpt:** [../samples/hdv-excerpt.xml](../samples/hdv-excerpt.xml)

## Purpose

Visual **raw Data Vault 2.0** model: hubs, links, satellites, physical **reference tables**, and **linked tables** (pointers / hub aliases). Drives:

- Check model / DDL generation  
- **Data Vault Update** load pipelines  
- Catalog publish of vault tables  
- Parent for Business Vault (`.hbv`) via path reference  

## What it is not

- Not a source schema (use `.hsm` + catalog)  
- Not SCD2 history merge (use `.hbv`)  
- Not a star schema (use `.hdm`)  

## Top-level structure

| Element | Role |
|---------|------|
| `name_sync_with_filename` | Y/N — keep model name aligned with file basename |
| `description` | Free text |
| `configuration` | Target DB, hash algorithm/type, load/record-source fields, bulk options, PK/FK flags |
| `coaching` | Optional coach-panel sources |
| `tables` / `table` | Polymorphic vault objects |
| `notes` / `note` | Canvas documentation only |

## Table types (`tableType`)

| Code | Class | Loadable | Notes |
|------|-------|----------|-------|
| `HUB` | `DvHub` | Yes | Business keys + record sources |
| `LINK` | `DvLink` | Yes | Participating hubs + source mappings |
| `SATELLITE` | `DvSatellite` | Yes | Parent hub/link + attributes + record source |
| `REFERENCE` | `DvReferenceTable` | Yes | Natural keys + attributes; FULL_REPLACE style loads |
| `LINKED_TABLE` | `DvLinkedTable` | No | Pointer or hub alias (`referencedTableName`, optional `referencedModelFilename`, optional role `hashKeyFieldName`) |
| `TABLE_REFERENCE` | legacy | No | Dual-read only → rewrite as `LINKED_TABLE` |

## Common fields on every table

- `tableName`, `description`, `tableType`  
- `xloc`, `yloc` (canvas)  
- `integrationMode` (e.g. Hop managed vs external/custom)  
- Optional custom pipeline paths  

### Hub essentials

- `businessKeys` (name, dataType, length; dual-read `sourceFieldName` and/or ordered `sourceFieldNames`; optional `composite` Y/N; optional `recordSourceName`)  
- **Multipartite:** several `businessKeys` with different `name` → several physical columns; hash order = first-seen names  
- **Composite (single vault column):** `composite=Y`, one vault `name`, ordered `sourceFieldNames` (or multi-source rows with same `name` + different `recordSourceName`, same part count)  
- `hashKeyFieldName`  
- `recordSources` / `recordSource` (catalog source names)  
- Configuration: `businessKeyDelimiter`, `hashContentSuffix`, `hashUsesComposedBusinessKey` (default N: hash parts for composite BKs)  

### Satellite essentials

- `hub` or `link` parent name  
- `recordSource` catalog name  
- `attributes` (name/type/length; CDC include flags)  
- Optional `parentKeySourceFields` (ordered source columns = hub hash-input parts: multipartite vault names or composite source parts — **not** a stored composed BK)  
- Optional driving key, status tracking  

### Link essentials

- `hubNames`, `linkHubSources` with `businessKeySources` (`businessKeyField` + `sourceFieldName` and/or `sourceFieldNames` for composite parts)  
- Optional dependent child keys, link satellites  

### Reference table essentials

- `naturalKeys`, `attributes`, `recordSources`, load mode  

### Linked table essentials

- `referencedTableName`  
- `referencedModelFilename` (empty/same model for hub alias)  
- `referencedTableType`  
- Optional `hashKeyFieldName` for role-playing alias  

## Anti-patterns (for Gemini)

1. Inventing `x_load_ts` / hash columns as satellite attributes — those come from **configuration** standard columns.  
2. Putting CRM columns on a hub that are not business keys — use a satellite.  
3. Using `REFERENCE` for a cross-model hub pointer — use `LINKED_TABLE`.  
4. Omitting `recordSource` / `recordSources` so loads cannot resolve catalog feeds.  
5. Hardcoding SQL Server-only collations or dialects in the model XML (not stored here; loads use connections).  
6. Modeling VaultSpeed-style composed BKs as multipartite vault columns (`burger_bk_1`, `burger_bk_2`) when the target DDL has a single `burger_bk` — use `composite=Y` + `sourceFieldNames` instead.  
7. Putting a composed hub BK column on satellites or links — sats/links map **source parts** and store only parent/hub **hash keys**.  
8. Confusing hub BK `composite` with catalog **COMPOSITE** multi-table source feeds (`.hsm` queries).  

## Product docs

- [datavault-plugin.adoc](../../datavault-plugin.adoc)  
- [dv-hub.adoc](../../dv-hub.adoc), [dv-link.adoc](../../dv-link.adoc), [dv-satellite.adoc](../../dv-satellite.adoc)  
- [dv-cross-model-references.adoc](../../dv-cross-model-references.adoc)  
