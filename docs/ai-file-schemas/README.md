# AI file-schema pack (models + metadata)

Portable context for external AIs (e.g. Google Gemini) that edit Data Hopper EDW project files.

This pack describes **structure** (XSD / JSON Schema) and **purpose** (Markdown) for:

| File | Root / location | Pack |
|------|-----------------|------|
| `.hdv` | `<data-vault-model>` | [models/hdv.md](models/hdv.md), [models/hdv.xsd](models/hdv.xsd) |
| `.hbv` | `<business-vault-model>` | [models/hbv.md](models/hbv.md), [models/hbv.xsd](models/hbv.xsd) |
| `.hdm` | `<dimensional-model>` | [models/hdm.md](models/hdm.md), [models/hdm.xsd](models/hdm.xsd) |
| `.hsm` | `<source-model>` | [models/hsm.md](models/hsm.md), [models/hsm.xsd](models/hsm.xsd) |
| Project metadata JSON | `metadata/<type>/*.json` | [metadata/](metadata/) |
| Catalog record definitions | catalog FILE store (e.g. `…/sources/*.json`, `…/models/…/*.json`) | [metadata/catalog-record-definition.md](metadata/catalog-record-definition.md) + [schema](metadata/catalog-record-definition.schema.json) |

**Catalog layout (important):** column lists live on **`dvSource.fields`** (`DV_SOURCE`) or **`physicalTable.fields`** (published targets / ops). Do **not** write top-level `rowMetaXml`.

Start with **[conventions.md](conventions.md)** and **[cross-references.md](cross-references.md)**.

## How to use with Gemini (or similar)

1. Attach or paste **`conventions.md`** every session that edits models or catalog.
2. For a specific edit, also attach the matching **`models/*.md` + schema** (or **`metadata/*.md` + schema**).
3. Prefer **editing an existing sample** from `retail-example/` or `integration-tests/` over inventing a file from scratch.
4. **Do not invent source columns** — read the catalog source JSON (or `.hsm` columns) first.
5. After structural edits, remind the human to open the file in Hop GUI and run **Check model**.

### Minimal attach set by task

| Task | Attach |
|------|--------|
| Edit raw vault | conventions + hdv.md + hdv.xsd + excerpt |
| Edit BV SCD2 | conventions + hbv.md + hdv.md (parent) + cross-references |
| Edit source model / composite feed | conventions + hsm.md + catalog-record-definition.md |
| Wire RDG / group update | resource-definition-group.md + cross-references |
| Add catalog source | catalog-record-definition.md + schema + `samples/catalog-CRM-customer.excerpt.json` |
| Edit published hub/sat catalog row | catalog-record-definition.md (`physicalTable.fields`) + `samples/catalog-hub-customer.excerpt.json` |

## Schema style

- **XSD / JSON Schema are intentionally relaxed** (many optional elements, `xs:any` / `additionalProperties` where Hop evolves).
- Purpose markdown and **anti-patterns** matter more for LLM quality than strict validation.
- Source of truth for serialization remains the Java `@HopMetadataProperty` classes (noted in each file).

## Samples

Short excerpts live under [samples/](samples/). Full real models:

- `integration-tests/tests/basic/vault1.hdv`
- `retail-example/models/retail-360.hdv`, `retail-360.hbv`, `source-tables-crm.hsm`, `retail-f-orders.hdm`
- `integration-tests/catalog-data/hop/integration-tests/sources/CRM-customer.json`
- `retail-example/metadata/resource-definition-group/retail-sources.json`

## Product docs (human-oriented)

Full feature docs: [docs/README.md](../README.md). This pack does not replace them.
