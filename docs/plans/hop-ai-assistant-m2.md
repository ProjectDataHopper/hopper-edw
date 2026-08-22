# Pipeline & Workflow AI Help — Milestone 2 (Structural Proposals)

Milestone 2 extends **Pipeline AI Help** and **Workflow AI Help** beyond advisory chat: the assistant can suggest concrete graph edits (add transforms, wire hops, reposition nodes) that you review and apply from the Hop GUI.

This document describes how M2 works, what proposal types are supported, how validation differs from prompt guidance, and where the bundled Apache Hop standards content comes from.

For shared AI configuration, provider setup, and entry points, see [Data Vault AI Help & Advisory](../ai-advisory.md).

---

## Overview

| Capability | M1 (advisory) | M2 (structural proposals) |
| :--- | :--- | :--- |
| Multi-turn chat | Yes | Yes |
| Check results, catalog, logs in context | Yes | Yes |
| LLM suggests topology edits | Text only | Optional `hop_proposals` JSON block |
| User review before apply | N/A | **Review N proposed change(s)** button per turn |
| GUI apply + undo | N/A | Yes (via standard Hop graph undo) |
| Plugin field/SQL/connection edits | N/A | **Not supported** (by design) |
| De Bertoli Hop standards enforcement | N/A | Prompt guidance only (not validator block/warn) |

M2 mirrors the Data Vault modeler pattern: advice text is shown in the transcript; machine-readable proposals are stripped from conversation history and stored separately for review.

---

## How to use structural proposals

1. Open a pipeline (`.hpl`) or workflow (`.hwf`) in the Hop GUI.
2. Open **AI Help** (toolbar, canvas context menu, or transform/action context menu).
3. Ask for a concrete change, for example:
   - *"Add a Filter Rows transform between Input and Output and wire it up."*
   - *"Insert a Dummy action after Start and connect them."*
4. When the response includes proposals, the transcript shows a **Review N proposed change(s)** button.
5. In the review dialog:
   - Check or uncheck individual proposals.
   - Read the preview pane for type, risk, and parameters.
   - Blocked proposals are unchecked and show the validation reason.
6. Click **Apply selected**. The graph updates and registers undo points; a system line confirms how many changes were applied.
7. On the next turn, applied-change summaries are sent back to the LLM so follow-ups reflect the updated graph.

**Undo:** Use the pipeline/workflow graph **Undo** toolbar action after applying. Each apply batch registers undo entries through Hop’s standard undo delegate.

---

## Proposal format (`hop_proposals`)

When the model suggests graph edits, it appends a fenced JSON block tagged `hop_proposals`:

````markdown
```hop_proposals
{
  "proposals": [
    {
      "id": "1",
      "description": "Add Filter Rows between Input and Output",
      "riskLevel": "LOW",
      "type": "ADD_TRANSFORM",
      "parameters": {
        "transformPluginId": "FilterRows",
        "name": "Filter bad rows",
        "locationX": "320",
        "locationY": "120"
      }
    }
  ]
}
```
````

Fields:

| Field | Required | Description |
| :--- | :--- | :--- |
| `id` | No | Stable identifier for the proposal (shown in preview) |
| `description` | Recommended | Human-readable summary in the review list |
| `riskLevel` | No | `LOW`, `MEDIUM`, or `HIGH` (default `MEDIUM`) |
| `type` | Yes | One of the supported proposal types (see tables below) |
| `parameters` | Yes | String key/value map; see per-type reference |

Malformed blocks are ignored; advisory text is still shown. The transcript displays a message when a block was present but could not be parsed.

---

## Supported proposal types

### Pipeline

| Type | Parameters | Effect |
| :--- | :--- | :--- |
| `ADD_TRANSFORM` | `transformPluginId`, `name`, `locationX`, `locationY` | Adds a transform with **plugin defaults** only |
| `DELETE_TRANSFORM` | `transformName` | Removes transform and all hops touching it |
| `RENAME_TRANSFORM` | `transformName`, `newName` | Renames a transform |
| `ADD_PIPELINE_HOP` | `fromTransform`, `toTransform`, `enabled` (Y/N, default Y) | Adds a hop between transforms |
| `DELETE_PIPELINE_HOP` | `fromTransform`, `toTransform` | Removes a hop |
| `SET_TRANSFORM_LOCATION` | `transformName`, `locationX`, `locationY` | Moves a transform on the canvas |
| `ADD_PIPELINE_NOTE` | `text`, `locationX`, `locationY`, optional `width`, `height` | Adds a canvas note |

### Workflow

| Type | Parameters | Effect |
| :--- | :--- | :--- |
| `ADD_ACTION` | `actionPluginId`, `name`, `locationX`, `locationY` | Adds an action with **plugin defaults** only |
| `DELETE_ACTION` | `actionName` | Removes action and all hops touching it |
| `RENAME_ACTION` | `actionName`, `newName` | Renames an action |
| `ADD_WORKFLOW_HOP` | `fromAction`, `toAction`, `unconditional` (Y/N), `evaluation` (Y/N, default Y) | Adds a hop between actions |
| `DELETE_WORKFLOW_HOP` | `fromAction`, `toAction` | Removes a hop |
| `SET_ACTION_LOCATION` | `actionName`, `locationX`, `locationY` | Moves an action on the canvas |
| `ADD_WORKFLOW_NOTE` | `text`, `locationX`, `locationY`, optional `width`, `height` | Adds a canvas note |

### Explicitly excluded

These are **not** accepted in M2 (validator blocks them; prompts instruct the model not to emit them):

- `SET_TRANSFORM_PROPERTY` / `SET_ACTION_PROPERTY`
- SQL, connection names, field mappings, or any plugin configuration dialog settings
- Metadata object creation outside the open graph

`ADD_TRANSFORM` / `ADD_ACTION` load plugins via `PluginRegistry` and call `setDefault()` on transform metadata only. You configure fields manually after apply.

---

## Validation vs standards guidance

**Topology validator (enforced in GUI)**

Before apply, proposals are validated against the **open** pipeline or workflow:

- Proposal type must match context (pipeline types on pipelines, workflow types on workflows).
- Names must be unique; hop endpoints must exist (including transforms/actions added earlier in the same proposal batch).
- `transformPluginId` / `actionPluginId` must exist in the plugin registry (and typically appear in the catalog JSON sent as context).
- Locations must be integer `locationX` / `locationY` where required.

Blocked proposals cannot be selected. Warnings (e.g. hop already exists) are shown but may still be applicable.

**De Bertoli Hop standards (prompt guidance only)**

Condensed extracts from the [Apache Hop Development Skill](https://github.com/DeBortoliWines/skill-apache-hop-development) are bundled under:

`src/main/resources/org/hopper/edw/datavault/ai/prompts/hop-standards/`

| Resource | Purpose |
| :--- | :--- |
| `standards-condensed.txt` | Naming, layout, and design conventions |
| `skeleton-reference.txt` | Pipeline/workflow skeleton patterns |
| `transform-type-index.json` | Transform plugin id → XML type hints |
| `hop-proposals-schema.txt` | Proposal type and parameter reference for the model |
| `preamble-hop-m2-supplement.txt` | M2 instructions header |
| `NOTICE-debortoli-hop-skill.txt` | Attribution and license notice |

These inform the system prompt; the GUI validator does **not** warn or block on De Bertoli naming rules. The model may follow them in prose and proposals, but you remain responsible for reviewing applied changes.

---

## System prompt assembly (M2)

For each pipeline or workflow advisory request, the system prompt includes:

1. Scenario-specific preamble (`preamble-hop.txt` + scenario file under `prompts/pipeline/` or `prompts/workflow/`)
2. M2 supplement from `HopAiM2PromptSupport` (standards, skeleton, schema, transform index)

User prompts still include structure JSON, optional catalog, check results, logs, and—on follow-up turns—summaries of changes you applied since the last message.

---

## Programmatic API (M2)

| Class | Role |
| :--- | :--- |
| [`HopAiProposalParser`](../../src/main/java/org/hopper/edw/datavault/ai/HopAiProposalParser.java) | Parses `hop_proposals` blocks from raw LLM text |
| [`HopAiM2PromptSupport`](../src/main/java/org/hopper/edw/datavault/ai/HopAiM2PromptSupport.java) | Loads bundled hop-standards resources into the system prompt |
| [`PipelineAiProposalValidator`](../src/main/java/org/hopper/edw/datavault/ai/pipeline/PipelineAiProposalValidator.java) | Topology validation for pipeline proposals |
| [`PipelineAiProposalApplier`](../src/main/java/org/hopper/edw/datavault/ai/pipeline/PipelineAiProposalApplier.java) | Applies validated pipeline proposals |
| [`WorkflowAiProposalValidator`](../src/main/java/org/hopper/edw/datavault/ai/workflow/WorkflowAiProposalValidator.java) | Topology validation for workflow proposals |
| [`WorkflowAiProposalApplier`](../src/main/java/org/hopper/edw/datavault/ai/workflow/WorkflowAiProposalApplier.java) | Applies validated workflow proposals |
| [`HopAiProposalReviewDialog`](../src/main/java/org/hopper/edw/datavault/hopgui/ai/HopAiProposalReviewDialog.java) | Shared SWT review UI |
| [`HopAiConversationSession`](../src/main/java/org/hopper/edw/datavault/ai/HopAiConversationSession.java) | Turn history, proposals, applied summaries |

Pipeline and workflow advisor services call `HopAiProposalParser.parse()` after the LLM returns.

---

## Attribution

Bundled hop-standards content is derived from the Apache Hop Development Skill by De Bortoli Wines Pty Limited. See:

- Classpath: `org/hopper/edw/datavault/ai/prompts/hop-standards/NOTICE-debortoli-hop-skill.txt`
- Project: [`NOTICE`](../../NOTICE)
- Full skill (maintained separately): `../skill-apache-hop-development/` relative to this repository

Only condensed extracts are bundled; the full skill remains in its upstream repository.

---

## Related documentation

- [Data Vault AI Help & Advisory](../ai-advisory.md) — configuration, scenarios, Data Vault structural proposals, privacy
- [Apache Hop Development Skill](https://github.com/DeBortoliWines/skill-apache-hop-development) — source standards and transform reference