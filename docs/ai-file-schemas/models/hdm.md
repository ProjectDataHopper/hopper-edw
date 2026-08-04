# `.hdm` — Dimensional model

**Root element:** `<dimensional-model>`  
**Java:** `org.apache.hop.datavault.metadata.dimensional.DimensionalModel`  
**Schema:** [hdm.xsd](hdm.xsd) · **Excerpt:** [../samples/hdm-excerpt.xml](../samples/hdm-excerpt.xml)

## Purpose

Kimball-style dimensional model: dimensions, facts, junk dimensions, bridges, dimension aliases (role-playing). Drives **Dimensional Update** / publish actions.

## Top-level structure

| Element | Role |
|---------|------|
| `name_sync_with_filename` | Y/N |
| `description` | Free text |
| `configuration` | Target DB, dim key / version / date range / current flag fields, bulk options |
| `tables` / `table` | Dimensional objects |

## Table types (examples)

| Code | Role |
|------|------|
| `DIMENSION` | Conformed or local dimension |
| `DIMENSION_ALIAS` | Role-playing pointer to a shared dimension (`referencedDimensionName`, model file) |
| `FACT` | Fact with dimension roles / degenerate keys |
| `JUNK_DIMENSION` | Combined low-cardinality flags |
| `BRIDGE` | Many-to-many helper |
| `RANGE_DIMENSION` | Range / band dimension |

## Fact essentials

- Grain description (in description or naming)  
- `dimension_role` entries: `dimensionTableName` / `referencedDimensionName`, `foreignKeyColumn`, source fields, lookup flags  
- Measure / key fields as defined in the sample  

## Anti-patterns

1. Embedding a full second copy of `dim_date` instead of `DIMENSION_ALIAS` for order_date / ship_date roles.  
2. Fact FKs that do not match dimension key configuration (`dimKeyField`).  
3. Loading facts before conformed dimensions exist.  

## Product docs

- [dimensional-modeler-overview.adoc](../../dimensional-modeler-overview.adoc)  
- [dimensional-update-action.adoc](../../dimensional-update-action.adoc)  
