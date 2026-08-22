# EDW Journey perspective — internal design note

Phase 1 ships a new `IHopPerspective` (`360-EdwJourneyPerspective`) that maps one **resource definition group** as a canonical journey tree. Full product rationale is in the session plan; this note exists so later PRs do not reopen “perspective vs file”.

## Locked decisions

* **Perspective, not a file type.** Same singleton shell as the Data Catalog. Do not add `.hej` / persisted journey XML (that is `.hem`).
* **Scope = resource definition group.** Combo at the top. Do not root the tree at a workflow.
* **Canonical stages in operate order**, always present when empty. Model-file grain; table names as unexpanded children.
* **Deep-link** into existing editors. Do not embed canvases or run harvest/validate/load from the perspective (except opening the existing Validate sources / harvest history dialogs).
* **Headless snapshot + tree builder** are unit-tested without SWT.
* Phase 2: OPS last-run overlay (harvest, quality, load overview, problems) — shipped on the same node ids.
* Phase 3: empty-stage create (new HSM/HDV/HBV/HDM, add to group, generate DV from HSM, catalog import, quality rule set, Configure EDW setup). Optional operate-workflow wizard remains later.

## Packages

`org.apache.hop.datavault.hopgui.perspective.journey`

Reuse: `RecordOriginNavigationSupport`, `ResourceDefinitionGroupResolver`, `SourceUsageIndexBuilder`, harvest/validation GUI supports, `ModelSearchOpenSupport`.

Do not rebuild on `MetadataChanged`. Refresh on activation, toolbar Refresh, project switch, and after explicit create.
