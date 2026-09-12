<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Migrate hopper-edw AI advisors to Hop 2.20 `IAiAdvisor`

Internal implementation plan. Product docs stay in [ai-advisory.md](../ai-advisory.md) until this ships, then that page is rewritten around the Hop AI Assistant workbench.

**Prerequisite:** Apache Hop **2.20.0-SNAPSHOT** (or 2.20.0) that includes issue **#8330**:

- `IAiAdvisor` / `@AiAdvisorPlugin` in `hop-core`
- `HopGui.openAiAdvisorSession(AiAdvisorOpenRequest)`
- Generic inclusion picker (`picker` + `listInclusionChoices`)
- Session `attributes` and `inclusionSelections` round-trip
- `summarizeApplied` / `afterApply`
- Location-filtered advisor combo
- Third-party plugins **omit** `classLoaderGroup = "hop-ai"`
- `HopAiLegacyConfigMigrator` for old `hopAiConfig` API keys

Do **not** start this work on Hop 2.19.0. `IAiAdvisor` does not exist there.

Related Hop-side notes (SPI holes that 8330 closed): treat Hop `docs/hop-dev-manual/.../ai-advisor-plugins.adoc` as the contract. hopper-edw compiles against `hop-core` + `hop-ui` only — never `hop-tech-ai`.

---

## Goals

1. Bump this plugin to Hop **2.20.0-SNAPSHOT** (explicit pin decision).
2. Replace hopper-edw’s SWT advisor dialogs and LLM stack with Hop’s AI Assistant workbench.
3. Ship Data Vault, Business Vault, and dimensional modelers as `@AiAdvisorPlugin` implementations.
4. Delete pipeline/workflow AI Help from hopper-edw (Hop owns that surface in 2.20).
5. Delete hopper-edw `HopAiConfig` / Language Model Chat bundling; providers live in Hop metadata.
6. Leave a clear follow-up path for source model, lineage view, execution map, and EDW Journey advisors, plus richer BV/DM proposals.

## Non-goals (this migration)

- Implementing new BV/DM structural proposal types (PIT, bridge, fact, …) — follow-up after the port.
- Chat on `.hsm` / `.hlv` / `.hem` / Journey — follow-up, chat-only first.
- Changing Model Coach (mapping UI). It is not an advisor.
- Adding hopper-edw location ids to Hop `AiAdvisorLocations`.
- Depending on langchain4j or `plugins/tech/ai` at compile time.

---

## Pin bump (gate)

`hop.version` in `pom.xml` is **2.19.0**. [CLAUDE.md](../../CLAUDE.md) forbids bumping it without an explicit human decision.

When that decision is made, in the **same** change set as the first advisor PR or a dedicated pin PR:

| Place | Change |
|---|---|
| `pom.xml` `<hop.version>` | `2.20.0-SNAPSHOT` (then 2.20.0 at Hop GA) |
| Comment above it, CLAUDE.md pin table | Hop **2.20.0** required |
| `pom.xml` Hop snapshots repository | Re-enable if 2.20 is only on ASF snapshots |
| `src/assembly/assembly.xml` | Stop including `hop-transform-languagemodelchat` |
| `src/main/resources/dependencies.xml` | Drop `../../transforms/languagemodelchat` folders |
| `scripts/` Docker base image | `apache/hop:2.20.0` (or local image built from 2.20 SNAPSHOT) |
| README / docs that say “Requires Apache Hop 2.19.0” | 2.20.0 |

Resolve 2.20.0-SNAPSHOT either from ASF snapshots or `mvn install` of the local Hop `issue-8330` tree. Jenkins / Marketplace images must include `plugins/tech/ai`.

Until the pin moves, keep the current dialogs.

---

## Target architecture

```
Hop GUI graph (DV / BV / DM)
  └── @GuiToolbarElement / @GuiContextAction
          └── AiAdvisorOpenRequest  (hop-core)
          └── hopGui.openAiAdvisorSession(request)
                  └── no-op if plugins/tech/ai is absent

hopper-edw IAiAdvisor  (@AiAdvisorPlugin, no classLoaderGroup)
  └── listScenarios / listInclusions / listInclusionChoices
  └── buildPrompt  → existing *AiContextBuilder + prompt files
  └── parseResponse → existing *ProposalParser (adapted to AiProposal)
  └── validateProposals / applyProposals → existing validator/applier
  └── summarizeApplied / afterApply
```

Hop owns: workbench UI, chat, `AiProvider` metadata, `hopAiConfig` master switch, pipeline/workflow advisors.

hopper-edw owns: prompts, redacted model/catalog/metrics context, EDW proposal types, apply/undo on our graphs.

### Classloading

- `@AiAdvisorPlugin` — **omit** `classLoaderGroup`.
- Graph `@GuiPlugin` / toolbar / context — **omit** `classLoaderGroup`.
- Do not import `org.apache.hop.ai.engine.*` or langchain4j.
- Jandex in this plugin already indexes annotations; Hop 2.20 registers `AiAdvisorPluginType` at `HopEnvironment` init.

### Location ids (ours)

Do not add these to Hop. Same string on `@AiAdvisorPlugin(locations)` and `AiAdvisorOpenRequest.setLocation`.

| Constant | Value | Artifact |
|---|---|---|
| `DATA_VAULT_GRAPH` | `data-vault-graph` | `DataVaultModel` |
| `BUSINESS_VAULT_GRAPH` | `business-vault-graph` | `BusinessVaultModel` |
| `DIMENSIONAL_GRAPH` | `dimensional-graph` | `DimensionalModel` |
| (later) `SOURCE_MODEL_GRAPH` | `source-model-graph` | source model |
| (later) `LINEAGE_VIEW` | `lineage-view` | `.hlv` document |
| (later) `EXECUTION_MAP` | `execution-map` | `.hem` |
| (later) `EDW_JOURNEY` | `edw-journey` | journey snapshot |

Put them on `org.hopper.edw.datavault.ai.EdwAiAdvisorLocations`.

---

## What stays, what wraps, what goes

### Keep (adapt to `AiAdvisorRequest`)

Context builders, prompt loaders, prompt files, proposal validators/appliers, metrics/execution-info context, source-to-vault classification JSON.

| Package / files | Role after migration |
|---|---|
| `ai/DvAiContextBuilder.java` (+ bundle) | `buildPrompt` |
| `ai/DvAiPromptLoader.java`, `prompts/*.txt` | system prompts |
| `ai/DvAiProposalParser.java` | `parseResponse` (map to `AiProposal`) |
| `ai/DvAiProposalValidator.java`, `DvAiProposalApplier.java` | validate / apply |
| `ai/businessvault/*` except request/session if inlined | BV |
| `ai/dimensional/*` except request/session if inlined | DM |
| `ai/SourceToVaultAiContextBuilder.java` | DV baseline sharing |
| `metrics/MetricsAiContextBuilder.java`, `ExecutionInfoAiContextBuilder.java` | inclusions |

`DvAiRequest` / `BvAiRequest` / `DmAiRequest` can disappear once builders take `AiAdvisorRequest` (scenario id, inclusions, `selectedInclusionIds`, `isFollowUp()`, `appliedChangeSummaries`).

### New

| Class | Role |
|---|---|
| `EdwAiAdvisorLocations` | location id constants |
| `EdwAiAdvisorOpenSupport` | fill `AiAdvisorOpenRequest` + `openAiAdvisorSession` |
| `EdwAiAdvisorGraphSupport` | find open DV/BV/DM graph for artifact; undo + redraw |
| `EdwAiProposalSupport` | `DvAiProposal` ↔ `AiProposal` (type/risk as strings) |
| `DataVaultAiAdvisor` | `@AiAdvisorPlugin` id `data-vault-advisor` |
| `BusinessVaultAiAdvisor` | id `business-vault-advisor` |
| `DimensionalAiAdvisor` | id `dimensional-advisor` |

Optional: keep `DvAiProposal.Type` internally; adapters convert to `AiProposal.setType(enum.name())`.

### Delete (after DV/BV/DM work on the workbench)

**GUI / config / LLM**

- `hopgui/ai/DvAiAdvisorDialog.java`, `BvAiAdvisorDialog.java`, `DmAiAdvisorDialog.java`
- `hopgui/ai/PipelineAiAdvisorDialog.java`, `WorkflowAiAdvisorDialog.java`
- `hopgui/ai/PipelineAiGuiPlugin.java`, `WorkflowAiGuiPlugin.java`
- `hopgui/ai/DvAiCatalogSourceSelector.java` (replaced by `listInclusionChoices`)
- `hopgui/ai/DvAiProposalReviewDialog.java`, `HopAiProposalReviewDialog.java`, `ModelAiProposalReviewDialog.java` (Hop workbench review dialog)
- `hopgui/ai/HopAiTranscriptPanel.java` (Hop transcript)
- `config/HopAiConfigOptionPlugin.java` and its messages (collides with Hop’s same config key)
- `ai/HopAiConfig.java`, `HopAiConfigSingleton.java`, `HopAiProviderSettings*.java`, `DvAiProviderPreset.java`
- `ai/HopAiAdvisorEngine.java`, `HopAiLanguageModelFactory.java`, `DvAiLanguageModelFactory.java`
- `ai/DvAiAvailability.java` (do not Class.forName LMC from hopper-edw)
- `ai/HopAiConversationSession.java`, `DvAiConversationSession.java`, `HopAiTurn.java`, `DvAiTurn.java`

**Pipeline / workflow M2 duplicate**

- `ai/pipeline/**`, `ai/workflow/**`
- `ai/HopAiProposal*.java`, `HopAiAdvisoryResponse.java`, `HopAiM2PromptSupport.java`, `HopAiActionPluginSupport.java`, `HopAiTransformPluginSupport.java`
- Prompt trees `prompts/pipeline/`, `prompts/workflow/`, `prompts/hop-standards/`

**Tests** that only exist for the deleted stack (`HopAiAdvisorEngineTest`, `*LanguageModelFactory*`, `HopAiProviderSettings*`, `HopAiConversationSession*`, pipeline/workflow advisor tests). Keep context-builder and proposal validator/applier tests.

Do **not** delete Model Coach.

---

## Undo and graph refresh (easy to get wrong)

Hopper-edw undo is a **snapshot of the current model** (`ModelGraphSnapshotUndo.markChange`). It must run **before** mutations.

Hop calls:

1. `applyProposals` (UI thread)
2. `summarizeApplied` via `session.recordApplied`
3. `afterApply`
4. workbench `refreshBoundGraph()` — **Explorer pipeline/workflow only**; it will not see our graphs

So:

- **`applyProposals`:** resolve graph via `EdwAiAdvisorGraphSupport`, `markUndoPoint()`, then existing applier.
- **`afterApply`:** `setChanged()`, `redraw()`, enable undo toolbar.

`hopGui` is on `request.getAttributes().get("hopGui")` only at apply time (workbench injects it). Do not put SWT on `AiAdvisorOpenRequest.attributes`.

Graph lookup: walk Data Orchestration (and Explorer) tab handlers; match `graph.getModel() == request.getArtifact()` (identity, not name).

---

## Data Vault advisor (first port)

`DataVaultAiAdvisor` implements `IAiAdvisor`.

**Open session** from `HopGuiVaultGraph.openAiAdvisor` / `openAiAdvisorContext`:

```java
AiAdvisorOpenRequest request = new AiAdvisorOpenRequest();
request.setAdvisorPluginId(DataVaultAiAdvisor.ID);
request.setLocation(EdwAiAdvisorLocations.DATA_VAULT_GRAPH);
request.setAreaLabel("Data Vault");
request.setPreferFloatingWindow(true);
request.setArtifact(model);
request.setArtifactName(model.getName());
request.setArtifactKind("data-vault");
request.setTitle(model.getName());
request.setFocusNodeName(focusTableName); // from table context when available
hopGui.openAiAdvisorSession(request);
```

Keep toolbar id `HopGuiVaultGraph-ToolBar-10065-AI-Help` and icon `datavault-ai-help.svg`. Catch `HopException` from `openAiAdvisorSession` (missing tech-ai plugin is a silent no-op inside Hop; other failures should still show an error).

**Scenarios** — keep current enum codes as `AiAdvisorScenario` ids: `GENERAL`, `SOURCE_ANALYSIS`, `TYPE_MAPPING`, `DV_MODELING`, `HOP_INTEGRATION`, `ERROR_DIAGNOSIS`, `PERFORMANCE_TUNING`. Map id → existing prompt resource.

**Baseline sharing:** model outline; source-model classification JSON when sibling `.hsm` files exist.

**Inclusions** (`defaultSelected = false`):

| Id | Picker | `buildPrompt` |
|---|---|---|
| `checks` | no | model check JSON |
| `catalog` | **yes**, multi-select | record definitions for `selectedInclusionIds("catalog")` |
| `xml` | no | full model XML, first turn only |
| `load-run-metrics` | no | `MetricsAiContextBuilder` |
| `execution-info` | no | `ExecutionInfoAiContextBuilder` |
| `metadata` | workbench | honour if we also append Hop metadata; optional |

Performance-tuning scenario may still auto-attach metrics in `buildPrompt` when `scenarioId` is `PERFORMANCE_TUNING` (today’s context builder already does this).

**Catalog picker:** `listInclusionChoices("catalog", request)` lists record-definition names from the model’s catalog connection. **No SWT.** If the connection is missing or empty, return `List.of()`; the workbench unchecks the inclusion. Users set the catalog connection on the modeler (current selector’s “choose connection” dialog does not belong on `IAiAdvisor`).

**`parseResponse`:** keep ` ```dv_proposals ` in DV prompts, or switch prompts to `hop_proposals` and parse that fence. Either is valid; the workbench does not parse. Prefer **one fence** across DV/BV/DM (`hop_proposals` with EDW types) so we can drop `DvAiProposalParser` later. If prompts still say `dv_proposals`, parse both fences.

**`isAvailable()`:** `true`. Do not probe LMC. If tech-ai is absent, the open-session extension is a no-op.

---

## Business Vault and dimensional advisors

Same shell as DV.

| | BV | DM |
|---|---|---|
| Plugin id | `business-vault-advisor` | `dimensional-advisor` |
| Scenarios | `GENERAL`, `BV_MODELING`, `HOP_INTEGRATION`, `ERROR_DIAGNOSIS`, `PERFORMANCE_TUNING` | `GENERAL`, `DM_MODELING`, `HOP_INTEGRATION`, `ERROR_DIAGNOSIS`, `PERFORMANCE_TUNING` |
| Extra inclusion | `linked-dv` (checkbox; serialize linked DV outline) | none extra |
| Applyable types today | `ADD_MODEL_NOTE`, `SET_CONFIGURATION_PROPERTY`, `RENAME_TABLE` | same |

Toolbar methods on `HopGuiBusinessVaultGraph` / `HopGuiDimensionalModelGraph` switch to `openAiAdvisorSession`. Pass `focusNodeName` from table context when cheap.

Do not invent BV/DM structural types in the migration PR. Track them as follow-up (below).

---

## Pipeline / workflow AI

Hop 2.20 ships Pipeline AI Help and Workflow AI Help (`pipeline-graph` / `workflow-graph`). hopper-edw copies:

- Toolbar ids `HopGuiPipelineGraph-ToolBar-10046-ai-help` vs Hop `...-10048-ai-help` → **two buttons**
- Context action id `pipeline-graph-zzz-ai-help` → **collision**

Delete hopper-edw pipeline/workflow GUI plugins, services, prompts, and M2 helpers in the same release as the pin bump (or immediately after DV/BV/DM so we never ship 2.20 with duplicates).

User-facing: [ai-advisory.md](../ai-advisory.md) and [hop-ai-assistant-m2.md](hop-ai-assistant-m2.md) should point at Hop’s AI Assistant docs for pipelines/workflows, not at hopper-edw dialogs.

---

## Config and secrets

hopper-edw and Hop 2.20 both use `hopAiConfig` in `hop-config.json`.

- **Delete** `HopAiConfigOptionPlugin` so two Configuration tabs do not fight.
- Hop migrates old fields (`aiProviderPreset`, `aiApiKey`, `aiBaseUrl`, `aiModelName`, `aiTemperature`) to an `AiProvider` metadata object on first GUI start with a metadata provider, then strips secrets from `hop-config.json`.
- Document: enable AI under **Configuration → Plugins → AI Assistant**, create **Metadata → AI Provider**. Do not re-document preset tables in hopper-edw except as a pointer.

---

## Help, i18n, docs

- Remove or retarget HelpTopics entries for the five advisor dialogs (`DV_AI_ADVISOR`, …). Toolbar Help is Hop’s AI Assistant page plus [ai-advisory.md](../ai-advisory.md).
- Rewrite `docs/ai-advisory.md`: workbench (perspective / float / dock), Sharing line, catalog Select…, Hop providers. Drop hopper-edw config screenshots when they are stale.
- `docs/datavault-plugin.adoc` AI Help row: opens the Hop AI Assistant bound to the vault model.
- `docs/performance-tuning.md`: same workbench, Performance tuning scenario + load-run inclusion.
- CLAUDE.md / README Hop version.
- CHANGELOG: 2.20 pin, advisor migration, removal of pipeline/workflow AI from this plugin.

Keep i18n for advisor **names, scenarios, inclusions** on the new `*AiAdvisor` classes. Dialog-only keys can go.

---

## Tests

Unit tests (`mvn test`) are enough. This is not DDL/SQL/dialect work; do not require the four-engine matrix.

Must have:

- `DataVaultAiAdvisor` (and BV/DM): scenarios, inclusion ids, `listInclusionChoices` empty vs names, `buildPrompt` contains structure JSON and honours inclusions / follow-up / catalog ids (reuse context-builder tests).
- `parseResponse` still extracts EDW types.
- Validator/applier tests still pass through the `AiProposal` adapter.
- `EdwAiProposalSupport` round-trip.
- `summarizeApplied` default / override for `ADD_HUB`.
- Deleted stack: those tests deleted with the code.

Manual GUI (once): open `.hdv` AI Help → floating workbench, catalog Select…, apply `ADD_MODEL_NOTE`, Undo on the vault toolbar, remove `plugins/tech/ai` → toolbar no-op and hopper-edw still loads.

---

## Implementation order

| Phase | Scope | Done when |
|---|---|---|
| **0** | Hop pin 2.20.0-SNAPSHOT, Docker image, CLAUDE/README, stop bundling LMC | `mvn test` compiles against 2.20; plugin zip has no LMC jar |
| **1** | Locations, open support, graph lookup, proposal adapter | Helpers + tests, no user-visible change yet (dialogs still exist) |
| **2** | `DataVaultAiAdvisor` + vault toolbar/context | DV uses workbench; `DvAiAdvisorDialog` gone |
| **3** | BV + DM advisors + toolbars | All three modelers on workbench; their dialogs gone |
| **4** | Delete pipeline/workflow AI, `HopAiConfig*`, engines, transcript/review dialogs, dead tests | No duplicate AI Help on `.hpl`/`.hwf`; one AI config tab (Hop’s) |
| **5** | Docs, help topics, CHANGELOG, screenshots | `ai-advisory.md` describes the workbench |
| **6** (follow-up) | Source-model chat-only advisor | `.hsm` toolbar AI Help |
| **7** (follow-up) | Lineage view, execution map, Journey chat-only | explain-this-graph / what-next |
| **8** (follow-up) | Richer BV/DM proposals | add table / PIT / fact / bind source, etc. |

Phases 0–5 are the migration. 6–8 are “build more” and can land after.

Prefer **one PR for 0–5** if the pin bump is already agreed; otherwise pin PR then advisor PR. Do not ship 2.20 with both old dialogs and new advisors.

---

## Follow-up: more BV/DM and other surfaces

### Richer BV / DM proposals

Today BV/DM can only apply note / config / rename. After the port, add types in hopper-edw validators/appliers (no Hop API change):

- BV: add SCD2/PIT/bridge/business table, bind linked DV table, set SQL/Jinja, layout.
- DM: add dimension/fact/bridge/junk, bind source, layout.

Same `hop_proposals` review path.

### New chat-only advisors

| Surface | Location | Artifact | First slice |
|---|---|---|---|
| Source modeler | `source-model-graph` | source model | classify tables/queries, type mapping, generate-to-vault questions |
| Lineage view | `lineage-view` | `.hlv` document | explain graph, seed, OPS badges (read-only) |
| Execution map | `execution-map` | `.hem` | explain hops, runtime, diffs (read-only) |
| EDW Journey | `edw-journey` | snapshot / group | “what should I do next?” |

Toolbar on the existing graph/perspective; `preferFloatingWindow = true` for canvases. No proposals until there is a safe apply story.

### Focus node

Pass the clicked table/note name as `focusNodeName` from context menus (today most EDW AI Help opens unfocused).

---

## Acceptance (phases 0–5)

- Plugin builds and unit-tests against Hop 2.20.0-SNAPSHOT.
- `.hdv` / `.hbv` / `.hdm` **AI Help** opens the Hop workbench (floating), not a hopper-edw dialog.
- Scenarios and inclusions match today’s DV/BV/DM sets; catalog uses Select…; metrics/execution-info still optional.
- Structural DV proposals still review/apply; Undo on the vault toolbar restores the pre-apply snapshot.
- Follow-up turns see `ADD_HUB: …` (not `unknown change`).
- `.hpl` / `.hwf` have a **single** AI Help (Hop’s).
- Configuration has a **single** AI Assistant tab (Hop’s). Old `hopAiConfig` keys become an `AiProvider` via Hop’s migrator.
- `classLoaderGroup` is not `hop-ai` on hopper-edw advisors or graph plugins.
- Removing `plugins/tech/ai` makes AI Help a no-op; hopper-edw still loads.
- hopper-edw zip does not bundle Language Model Chat.

---

## Key source files (current)

Openers:

- `hopgui/file/vault/HopGuiVaultGraph.java` (`openAiAdvisor`, `openAiAdvisorContext`)
- `hopgui/file/businessvault/HopGuiBusinessVaultGraph.java`
- `hopgui/file/dimensional/HopGuiDimensionalModelGraph.java`
- `hopgui/ai/PipelineAiGuiPlugin.java`, `WorkflowAiGuiPlugin.java`

Contract to replace dialogs: `hopgui/ai/*AiAdvisorDialog.java`

Hop contract (read-only, other repo): `org.apache.hop.ai.advisor.IAiAdvisor` and `docs/hop-dev-manual/modules/ROOT/pages/ai-advisor-plugins.adoc`.
