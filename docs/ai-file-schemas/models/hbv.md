# `.hbv` — Business Vault model

**Root element:** `<business-vault-model>`  
**Java:** `org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel`  
**Schema:** [hbv.xsd](hbv.xsd) · **Excerpt:** [../samples/hbv-excerpt.xml](../samples/hbv-excerpt.xml)

## Purpose

Business Vault layer on top of a raw `.hdv`: SCD2 tables (single- or multi-satellite), PIT tables, SQL/business tables. Drives **Business Vault Update**.

## Critical link

```xml
<dataVaultModelPath>${PROJECT_HOME}/models/retail-360.hdv</dataVaultModelPath>
```

Must point at the parent raw vault model. Without it, DV references cannot resolve.

## Top-level structure

| Element | Role |
|---------|------|
| `name_sync_with_filename` | Y/N |
| `description` | Free text |
| `dataVaultModelPath` | Path to parent `.hdv` |
| `configuration` | Target DB, valid-from/to fields, sentinels, bulk options, pipeline name prefixes |
| `coaching` | Optional |
| `tables` / `table` | BV objects |

## Table types (examples)

| Code | Role |
|------|------|
| `SCD2` | History table from one or more DV satellites |
| `PIT` | Point-in-time snapshot helper |
| `BUSINESS_TABLE` | SQL or other BV table shape |
| `SOURCE_QUERY` | Satellite-shaped table/view or SQL that SCD2/PIT hop to (optional other connection) |

Inspect real retail samples for exact nested tags (`satellite_config`, `field_mapping`, `dv_reference`, …).

## SCD2 essentials

- Parent DV hub (via references)  
- One or more **satellite configs** (`satelliteName`, source indicator, field mappings `sourceFieldName` → `targetFieldName`)  
- Timeline fields from configuration (`validFromField`, `validToField`, open sentinels)
- Optional `hashKeyPartitionCount` (`NONE`, `4`, `8`, `16`) for large full rebuilds — SQL truncate once, then Table Output, Native bulk, or Staging file (one CSV set per partition)
- Optional `calculations` (`targetFieldName`, SQL `expression`) applied after collapse. Generate a Hop pipeline unit test from the table context action (SQL Expression linked to this `.hbv` table). Legacy `calculation_tests` / `collapse_tests` still run at Check model if present.  

## Anti-patterns

1. Mapping fields that do not exist on the DV satellite attributes.  
2. Pointing `dataVaultModelPath` at another `.hbv` or a missing file.  
3. Duplicating raw vault hash/load metadata as business attributes without need.  
4. Expecting BV load without a prior DV load of the source satellites.  

## Product docs

- [business-vault-overview.adoc](../../business-vault-overview.adoc)  
- [business-vault-scd2.adoc](../../business-vault-scd2.adoc)  
- [business-vault-pit.adoc](../../business-vault-pit.adoc)  
