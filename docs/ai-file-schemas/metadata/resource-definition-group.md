# `metadata/resource-definition-group` — Resource definition group

**Java:** `org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta`  
**Schema:** [resource-definition-group.schema.json](resource-definition-group.schema.json)  
**Example:** `retail-example/metadata/resource-definition-group/retail-sources.json`

## Purpose

Lists model files for:

- **Validate resource definitions** (schema/catalog gates)  
- **Update resource definition group** (run DV → BV → DM models in order)  

## Key fields

| Field | Role |
|-------|------|
| `name` | Metadata name |
| `dataCatalogConnection` | Catalog connection name |
| `data_vault_model` | Array of `.hdv` paths |
| `business_vault_model` | Array of `.hbv` paths |
| `dimensional_model` | Array of `.hdm` paths |
| `detailedDataTypeChecking` | Stricter live type checks |
| `previewRowLimit` | Preview size for validation |

**Order within each array** is update order for the group action. Layer order is always DV then BV then DM.

## Anti-patterns

- Listing the same model twice.  
- Host absolute paths instead of `${PROJECT_HOME}/models/…`.  
- Putting catalog source JSON paths in the model arrays.  
