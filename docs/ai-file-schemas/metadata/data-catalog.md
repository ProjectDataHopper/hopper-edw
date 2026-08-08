# `metadata/data-catalog` — Data catalog connection

**Java:** `org.apache.hop.catalog.metadata.DataCatalogMeta`  
**Schema:** [data-catalog.schema.json](data-catalog.schema.json)  
**Example:** `retail-example/metadata/data-catalog/local-catalog.json`

## Purpose

Names a **catalog connection** used by models and actions (import tables, publish sources, validation). Does **not** store individual source tables — those live in catalog storage as record definition JSON.

## Typical FILE backend

```json
{
  "name": "local-catalog",
  "description": "…",
  "enabled": true,
  "catalog": {
    "FILE": {
      "pluginId": "FILE",
      "storageDirectory": "${PROJECT_HOME}/work/edw-catalog"
    }
  }
}
```

Models reference this via configuration `dataCatalogConnection` = `local-catalog`.

## Anti-patterns

- Pointing `storageDirectory` at a non-writable or absolute host path.  
- Creating catalog **sources** as files under `metadata/data-catalog/` — wrong place.  
- Putting source/target column lists in top-level `rowMetaXml` — use structured fields on the record definition (see [catalog-record-definition.md](catalog-record-definition.md)).  

