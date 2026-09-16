# `metadata/business-process-catalog` — Business process catalog

**Java:** `org.hopper.edw.datavault.metadata.busmatrix.BusinessProcessCatalogMeta`  
**Schema:** [business-process-catalog.schema.json](business-process-catalog.schema.json)  
**User guide:** [../../bus-matrix.adoc](../../bus-matrix.adoc)  
**Example:** `retail-example/metadata/business-process-catalog/retail-bus.json`

## Purpose

Named library of **business domains** and **process levels 1–3** used to tag facts for the Kimball bus matrix.

## Key fields

Hop list keys are singular: `domain`, `level1`, `level2`, `level3`. Each term has `name`, optional `description`, and `parentName` (domain for L1, L1 for L2, L2 for L3).

A resource definition group may set `businessProcessCatalog` to this metadata name.

## Anti-patterns

- Duplicate term names with different parents.  
- Level 2/3 rows whose `parentName` does not exist on the previous level.
