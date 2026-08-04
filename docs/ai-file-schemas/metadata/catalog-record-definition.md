# Catalog record definition JSON (sources / targets)

**Not** under `metadata/`. Stored in the catalog FILE backend directory (see data-catalog `storageDirectory`), e.g.:

```text
hop/{project}/sources/CRM-customer.json
hop/{project}/models/…   (published vault tables, etc.)
```

**Schema:** [catalog-record-definition.schema.json](catalog-record-definition.schema.json)  
**Example excerpt:** [../samples/catalog-CRM-customer.excerpt.json](../samples/catalog-CRM-customer.excerpt.json)  
**Full sample:** `integration-tests/catalog-data/hop/integration-tests/sources/CRM-customer.json`

## Purpose

**System of record for source (and published target) schemas.** Data Vault models reference these by **name** (`CRM-customer`) as `recordSource` / `recordSources`.

## Critical fields

| Field | Role |
|-------|------|
| `namespace` | Catalog namespace path |
| `name` | Source name used in models |
| `type` | e.g. `DV_SOURCE` |
| `physicalTable` / `physicalFile` | Where data lives |
| `dvSource` | Delivery type, fields, sourceType DATABASE/CSV/… |
| `dvSource.fields[]` | name, sourceDataType, length, hopType |
| `rowMetaXml` | Hop row-meta XML (can be large) |
| `tags` | e.g. `FULL_SNAPSHOT` |

## Anti-patterns (highest value for Gemini)

1. **Inventing** source fields in `.hdv` that are not in this JSON / `.hsm`.  
2. Renaming `name` without updating every model `recordSource` reference.  
3. Editing `rowMetaXml` by hand instead of refreshing from DB/import when possible.  
4. Confusing catalog **namespace/name** with physical `schemaName.tableName`.  

## Related

- [data-catalog.adoc](../../data-catalog.adoc)  
- [datavault-source.adoc](../../datavault-source.adoc)  
