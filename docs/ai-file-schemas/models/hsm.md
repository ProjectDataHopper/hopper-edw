# `.hsm` — Source model

**Root element:** `<source-model>`  
**Java:** `org.apache.hop.datavault.metadata.sourcemodel.SourceModel`  
**Schema:** [hsm.xsd](hsm.xsd) · **Excerpt:** [../samples/hsm-excerpt.xml](../samples/hsm-excerpt.xml)

## Purpose

Visual **source-side** model of CRM/staging tables, relationships, and multi-table **source queries**. Used to:

- Document source structure  
- Import/publish **catalog** feeds (`DV_SOURCE`, **COMPOSITE** multi-table queries)  
- Feed coaching / compose multi-table source into Data Vault  

Does **not** define vault hubs/sats (that is `.hdv`).

## Top-level structure

| Element | Role |
|---------|------|
| `name_sync_with_filename` | Y/N |
| `configuration` | `defaultDatabase`, `defaultSchema`, `catalogConnection` |
| `tables` / `table` | Physical source tables |
| `relationships` / `relationship` | PK/FK style relationships |
| `queries` / `query` | Multi-table source queries (when present) |
| `json-sources` / `json-source` | JSON extractions from a parent field (when present) |

## Table essentials

- `physicalType` (e.g. `DATABASE`)  
- `databaseName`, `schemaName`, `tableName`  
- `columns` / `column`: `name`, `sourceDataType`, `length`, `precision`, `hopType`, `primaryKeyPosition`  

## Relationship essentials

- `childTableName`, `parentTableName`  
- Child/parent column lists  
- Join type / cardinality  

## Query / COMPOSITE essentials

When present: joins, projection, WHERE, generation mode, optional published catalog feed name. Publishing creates a catalog COMPOSITE `DV_SOURCE` that `.hdv` hubs/sats can use as `recordSource`.

## JSON source essentials

When present under `json-sources` / `json-source`:

- `parentSourceKind` (`TABLE` / `QUERY` / `JSON`)
- `parentSourceName`, `jsonFieldName`
- `fields` / `field`: output name, JsonPath (`path`), optional pass-through parent field, Hop type, key position
- Publishing creates a catalog **JSON** `DV_SOURCE` pointing at the `.hsm` + JSON object name
- Published catalog layout is **`dvSource.fields[]`** (types/lengths/PK), not top-level `rowMetaXml` — see [catalog-record-definition.md](../metadata/catalog-record-definition.md)

Sample: [hsm-json-excerpt.xml](../samples/hsm-json-excerpt.xml).

## Anti-patterns

1. Putting vault hash keys or `x_load_ts` on source tables.  
2. Publishing without a valid `catalogConnection`.  
3. Assuming `.hsm` alone loads the vault — still need `.hdv` + Data Vault Update.  

## Product docs

- [source-modeler-overview.adoc](../../source-modeler-overview.adoc)  
- [data-catalog.adoc](../../data-catalog.adoc)  
- [catalog-record-definition.md](../metadata/catalog-record-definition.md) (catalog JSON after publish)  

