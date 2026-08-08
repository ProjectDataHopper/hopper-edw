# Catalog record definition JSON (sources / targets)

**Not** under `metadata/`. Stored in the catalog FILE backend directory (see data-catalog `storageDirectory`), e.g.:

```text
hop/{project}/sources/CRM-customer.json
hop/{project}/models/{modelName}/hub_customer.json
hop/{project}/operations/load_run.json
```

**Schema:** [catalog-record-definition.schema.json](catalog-record-definition.schema.json)  
**Samples:**

| Kind | Excerpt |
|------|---------|
| `DV_SOURCE` | [../samples/catalog-CRM-customer.excerpt.json](../samples/catalog-CRM-customer.excerpt.json) |
| `DV_HUB` (target layout) | [../samples/catalog-hub-customer.excerpt.json](../samples/catalog-hub-customer.excerpt.json) |

**Full samples:** `integration-tests/catalog-data/…`, `retail-example/fixtures/unit-catalog/…`

**Java:** `RecordDefinition` / `RecordDefinitionDocument`, layout API `DvSourceFieldSupport`

## Purpose

**System of record for source and published target schemas.** Data Vault models reference sources by **name** (`CRM-customer`) as `recordSource` / `recordSources`. Published hubs/sats/dims and ops tables store their **physical column layout** in the same document shape with a different layout home.

---

## Schema change log (layout)

### Breaking contract change (current)

| Before | After |
|--------|--------|
| Top-level **`rowMetaXml`** held a serialized Hop `IRowMeta` for almost every record type | **`rowMetaXml` is not written** |
| Meaning of the blob was ambiguous (source contract vs target table vs ops table) | Layout lives on the **element that owns the columns** |
| Dual store for `DV_SOURCE`: `dvSource.fields` **and** `rowMetaXml` often drifted | Single store per type |

### Where `fields[]` lives now

| `type` | Authoritative layout | Location-only refs |
|--------|----------------------|--------------------|
| `DV_SOURCE` | **`dvSource.fields[]`** | `physicalTable` / `physicalFile` / `physicalIcebergTable` (no contract columns) |
| `DV_HUB`, `DV_LINK`, `DV_SATELLITE`, `DV_REFERENCE` | **`physicalTable.fields[]`** | connection / schema / table name on same object |
| `BV_TABLE`, `DIM_TABLE`, `FACT_TABLE` | **`physicalTable.fields[]`** | same |
| `PHYSICAL_TABLE`, `VIEW` | **`physicalTable.fields[]`** | ops tables (`load_run`, quality history, …) |
| `DV_MODEL`, `BV_MODEL`, `DM_MODEL` | *(none)* | index / registry entries only |

### Field object (`CatalogSourceField`)

Shared by `dvSource.fields[]` and `physicalTable.fields[]` (JSON Schema: `#/definitions/catalogField`):

| Property | Role |
|----------|------|
| `name` | Column / stream name (**required**) |
| `sourceDataType` | Hop name and/or SQL label (`Integer`, `int4`, `DATETIME(6)`, …) |
| `hopType` | Hop type id (`2` String, `5` Integer, `9` Timestamp, …). **Do not leave `0` when known.** |
| `length` / `precision` | Strings; empty when N/A |
| `primaryKeyPosition` | 1-based PK order; `0` if not a key |
| `fk*` / `inputOptions` | Optional (sources / harvest / CSV) |

Hop `IRowMeta` used by pipelines is **derived in memory** from these lists. It is **not** a second persisted store.

### Legacy `rowMetaXml`

- **Read:** still accepted. If the authoritative structured list is empty, XML is migrated into `dvSource.fields` or `physicalTable.fields` on load.
- **Write:** always omitted (`null` / property absent).
- **AI rule:** never invent or reintroduce `rowMetaXml`. Never treat it as the layout source of truth.

---

## Critical document fields

| Field | Role |
|-------|------|
| `namespace` | Catalog namespace path |
| `name` | Logical name (source name or table element name) |
| `type` | Record type code (see table above) |
| `physicalTable` | RDBMS location; **plus** `fields[]` for targets/ops |
| `physicalFile` / `physicalIcebergTable` | File/Iceberg location for some `DV_SOURCE`s |
| `dvSource` | Source binding + **`fields[]` for `DV_SOURCE` only** |
| `tags` | e.g. `FULL_SNAPSHOT`, `DV HUB` |
| `origin` | Provenance |
| `qualityRules` | Optional DQ bindings |

---

## Examples

### `DV_SOURCE` — contract on `dvSource.fields`

```json
{
  "name": "CRM-customer",
  "type": "DV_SOURCE",
  "physicalTable": {
    "databaseMetaName": "CRM",
    "schemaName": "",
    "tableName": "customer"
  },
  "dvSource": {
    "sourceType": "DATABASE",
    "deliveryType": "FULL_SNAPSHOT",
    "fields": [
      {
        "name": "customer_id",
        "sourceDataType": "Integer",
        "length": "9",
        "hopType": 5,
        "primaryKeyPosition": 1
      }
    ]
  }
}
```

`physicalTable` has **no** `fields` here — only location.

### Published hub — layout on `physicalTable.fields`

```json
{
  "name": "hub_customer",
  "type": "DV_HUB",
  "physicalTable": {
    "databaseMetaName": "Vault",
    "tableName": "hub_customer",
    "fields": [
      { "name": "customer_hk", "sourceDataType": "String", "length": "63", "hopType": 2 },
      { "name": "customer_id", "sourceDataType": "Integer", "length": "9", "hopType": 5 }
    ]
  }
}
```

No `dvSource.fields` for vault targets.

---

## Runtime / plugin API (for tools)

| Operation | API |
|-----------|-----|
| Read layout | `DvSourceFieldSupport.sourceFieldsFromDefinition(definition)` |
| Write layout | `DvSourceFieldSupport.applyLayoutToDefinition(definition, fields, variables)` |
| Load migrate + align | `synchronizeLayoutAfterLoad` (via `RecordDefinitionDocument.toRecordDefinition`) |
| Persist | `prepareForPersistence` then document **without** `rowMetaXml` |

Hub **Import keys**, satellite **Load from source**, catalog DDL, and the catalog field grid all use the same read path.

---

## Anti-patterns (highest value for Gemini)

1. **Inventing** source fields in `.hdv` that are not in this JSON / `.hsm`.  
2. Renaming `name` without updating every model `recordSource` reference.  
3. **Reintroducing `rowMetaXml`** or editing XML as the layout.  
4. Putting **source** contract columns on `physicalTable.fields` for a `DV_SOURCE`.  
5. Putting **target** columns only on a top-level or `dvSource` list for a hub/sat.  
6. Writing `hopType: 0` while `sourceDataType` has the real type (import then shows String).  
7. Confusing catalog **namespace/name** with physical `schemaName.tableName`.  

---

## Related

- [data-catalog.adoc](../../data-catalog.adoc)  
- [datavault-source.adoc](../../datavault-source.adoc)  
- [conventions.md](../conventions.md)  
- [cross-references.md](../cross-references.md)  
