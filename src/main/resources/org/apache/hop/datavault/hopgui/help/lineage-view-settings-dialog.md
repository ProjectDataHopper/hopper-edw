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

# Lineage view settings

A **Hop Lineage View** (`.hlv`) stores *what to look at*, not the lineage graph. The tab name is the file name (without `.hlv`) after save — there is no separate name field.

## Seed

- **MODEL_TABLE** — start from a Hop table (layer + model + logical name). Job and dataset ids are recomputed from the selected backend on each refresh.
- **DATASET** / **JOB** — start from an OpenLineage identity already in Marquez or an export folder.

Direction defaults to **upstream** (end of the chain). Depth defaults to **6**.

## Filters

- **Include jobs** — show load jobs between datasets. Off concatenates dataset-to-dataset edges.
- **Layers** — SOURCE / DV / BV / DM chips. All selected means no filter.
- **Overlay OPS load times** — reserved for duration badges (does not change the structure query).

Pick a **lineage backend** metadata object (Marquez, export folder, or local models). If exactly one backend is enabled, File → New pre-selects it. Cancel on File → New does not open a tab.

Refresh queries the backend in the background. Hover a node name to underline it; click the name to update the details pane (Markdown, with **View as HTML**). Click the rest of the card for **Open model**, **Open in catalog**, **Show update pipeline** (DV/DM), or **Show build pipeline** (BV SCD2/PIT). Toolbar **Export SVG** writes the current graph.

When **Overlay OPS load times** is on, cards show last load duration from the Hop OPS database (and the recent average when it differs). If OPS has no row, a stale `hop_ops` value from the last lineage export may appear, labeled **export**. Marquez `latestRun.durationMs` is never used.

From a Data Vault, Business Vault, or dimensional table, **Show lineage** opens an unsaved view seeded on that table. If exactly one lineage backend is enabled, it is used; otherwise you pick one.
