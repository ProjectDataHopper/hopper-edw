# Shared conventions (AI)

Hop Data Vault project files are **Hop metadata** serializations, not free-form XML/JSON.

## Boolean values

Hop XML uses **`Y` / `N`** (not `true`/`false`) for booleans, e.g. `<name_sync_with_filename>Y</name_sync_with_filename>`.

JSON metadata often uses real JSON booleans (`true`/`false`). Follow the sample for that type.

## Paths

- Prefer **`${PROJECT_HOME}/…`** for model paths inside projects.
- Never commit absolute host paths (`/home/…`, `C:\…`).
- Business Vault models link raw vault via `dataVaultModelPath` (e.g. `${PROJECT_HOME}/models/retail-360.hdv`).

## Lists in XML

Hop wraps list items with a **group** element when `@HopMetadataProperty(groupKey=…)` is set:

```xml
<tables>
  <table>…</table>
  <table>…</table>
</tables>
<recordSources>
  <recordSource>CRM-customer</recordSource>
</recordSources>
```

Item tag is often singular; group is plural.

## Polymorphic tables

Model canvases store a **single** `tables/table` list. Discriminator:

| Format | Discriminator | Values (examples) |
|--------|---------------|-------------------|
| `.hdv` | `<tableType>` | `HUB`, `LINK`, `SATELLITE`, `REFERENCE`, `LINKED_TABLE` |
| `.hbv` | `<tableType>` | `SCD2`, `PIT`, `BUSINESS_TABLE`, … |
| `.hdm` | `<tableType>` | `DIMENSION`, `FACT`, `JUNK_DIMENSION`, `BRIDGE`, `DIMENSION_ALIAS`, `RANGE_DIMENSION`, … |
| `.hsm` | `<physicalType>` on tables | `DATABASE` (and others as added) |

### Linked table vs Reference table (0.6.0+)

| Concept | `tableType` | Meaning |
|---------|-------------|---------|
| **Linked table** | `LINKED_TABLE` | Canvas pointer / hub alias (not a load target by itself) |
| **Reference table** | `REFERENCE` | Physical vault code/catalog table (`ref_*`) |
| Legacy pointer | `TABLE_REFERENCE` | Dual-read only; rewrite to `LINKED_TABLE` on save |

**Do not** invent load pipelines for `LINKED_TABLE`. **Do not** use `REFERENCE` for cross-model pointers.

## Catalog is the contract

- Source field names, types, and lengths for DV loads come from **catalog** `DV_SOURCE` (or COMPOSITE) records and/or `.hsm`.
- Models **reference** sources by name (`CRM-customer`), they do not redefine the full source schema.
- If a field is missing, update the catalog / source model first.

## Canvas layout

`xloc` / `yloc` are GUI coordinates only; safe to leave or copy from neighbors. Do not use them for load logic.

## ASF license headers

Many project XML/JSON files include Apache license comments. Preserve them when editing existing files.

## Integration / execution

- Loads are driven by workflow actions (Data Vault Update, Business Vault Update, Dimensional Update, Update resource definition group), not by the model file alone.
- Optional PK/FK DDL flags live in model configuration (`generatePrimaryKeys`, `generateForeignKeys`).
