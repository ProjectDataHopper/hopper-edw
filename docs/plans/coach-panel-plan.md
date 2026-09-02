# Generic Coach Panel for DV / BV / DM Modelers

> Implementation plan for the shared coach panel: curated coaching sources in model XML, panel visibility in AuditManager, tree + contextual toolbar, drag-to-canvas table creation, and unified source-to-target mapping entry points.

Supersedes the conceptual Coach panel section in [source-to-data-vault-mapping-plan.md](source-to-data-vault-mapping-plan.md).

## Summary

| Concern | Storage / mechanism |
|---------|---------------------|
| Curated coaching sources | `ModelCoachingConfiguration.coachingSources` on `.hdv` / `.hbv` / `.hdm` |
| Panel open/closed | `ModelCoachPanelAuditSupport` via `AuditManager` (default visible) |
| Derived sources (DM SQL/pipeline, BV derivatives) | Runtime only via `ICoachingModelAdapter` |
| Mapping entry | `GenericSourceTargetMappingDialog` seeds metadata then opens existing table editors |

## Architecture

- **Metadata:** `org.hopper.edw.datavault.metadata.coaching`
- **GUI:** `org.hopper.edw.datavault.hopgui.coaching`
- **Audit:** `ModelCoachPanelAuditSupport` (mirrors `ModelLoadDurationPaneAuditSupport`)
- **Shell:** nested sash in `HopGuiModelGraphBase` — coach (left), canvas + durations (right)

## Modeler adapters

| Modeler | Persisted sources (`+`) | Auto-derived tree entries |
|---------|---------------------------|---------------------------|
| DV | Catalog `RECORD_DEFINITION` | — |
| BV | Optional record definitions | DV derivatives from `derivatives[]` / canvas refs |
| DM | Catalog `RECORD_DEFINITION` | SQL + PIPELINE from table `DmSourceConfiguration` |

## UI flows

1. **Add sources (`+`)** — `AddCoachingSourcesDialog`: catalog gate, filter box, multi-select `DV_SOURCE`, embedded import via `DataCatalogImportMenu`.
2. **Tree** — filter box, then source node → target children (mapped tables) → insight children (validation gaps).
3. **Drag source → canvas** — `CoachingTableTypeDialog` → create table → `GenericSourceTargetMappingDialog`.
4. **Map to target** — toolbar on selected source; opens mapping dialog then native table editor.

## Phased delivery

### Phase A (implemented)

- Coaching metadata on all three model types
- `ModelCoachPanel` + audit persistence + toolbar toggle
- DV/BV/DM adapters and tree population
- Add-sources dialog and import bridge

### Phase B (follow-up)

- Flattened link mapping UI inside `GenericSourceTargetMappingDialog`
- BV SCD2 and DM dimension/fact mapping sections
- `MappingSuggestionService` (Suggest mappings)
- Generalize `DataCatalogImportContext` for DM/BV catalog-only import

## Locked decisions

- BV auto sources: **DV derivatives only**
- Coach panel position: **left** of canvas
- Panel visibility: **AuditManager**, default **on** for new/unknown files
- No wizard flows