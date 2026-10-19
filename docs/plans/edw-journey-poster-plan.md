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

# EDW Journey poster and engineer panels (issue #209)

The Journey right pane is a vertical sash. The top is the architecture poster (`docs/images/diagrams/edw-overview.svg`) with hit rectangles taken from `docs/presentations/edw-component-explorer.html`. The bottom is a Hopper presentation when the group or a poster block is selected, and the existing details pane when another tree node is selected.

Status order is error, then warning, then ok. Informational blocks (`documentation`, `clients`, `hop-jdbc`, `edw-journey`, `presentations`, `coach`) and a missing optional layer stay unmarked. An unavailable OPS connection does not mark `ops` as an error.

Panels are in-memory presentations (`HSimplePresentation`, `HInMemoryRowsConnector`). History is the last 14 harvest, quality, and load-overview runs. A finding in 3 of the last 5 quality runs is chronic.

Presentation clicks use `OPEN_LINK` and the `hop-edw` scheme:

```text
hop-edw://open?layer=dv&file=${PROJECT_HOME}/models/vault.hdv&element=hub_customer&verb=select
hop-edw://act?name=validate-sources
hop-edw://panel?id=GATE_DESK
```

`{field}` in an Open link is replaced from the clicked row. Table body cells carry every source field, including columns that are not displayed. Verbs that have a GUI method today: `select`, `edit`, `browse` (source), `pipeline` (dimensional), `presentation` (open the `.hsl`). Group actions open the existing dialogs. Generate Data Vault opens the review dialog and does not apply by itself.

The talk explorer HTML is left as the offline pancard companion.
