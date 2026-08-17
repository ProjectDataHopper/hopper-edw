<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Hop Web support for modelers

Status: **available (experimental)** — issue [#119](https://github.com/mattcasters/hop-data-vault/issues/119).

Under **Hop Web** (RAP), the six model file graphs (the original five plus Hop Lineage View) render through the same SVG canvas stack as pipelines and workflows, with plugin-specific painters and client interaction polish.

## File types

| Extension | Graph |
|-----------|--------|
| `.hsm` | Source modeler |
| `.hdv` | Data Vault modeler |
| `.hbv` | Business Vault modeler |
| `.hdm` | Dimensional modeler |
| `.hem` | Execution map viewer |
| `.hlv` | Hop Lineage View (live session graph; experimental, same bar as `.hem`) |

## What works

| Area | Behaviour |
|------|-----------|
| Open / create / save | Same file types and explorer tabs as desktop |
| Paint | Server `SvgGc` → SVG snapshot → client overlay above the RAP canvas |
| Pan | Middle button or Ctrl+drag: dimmed SVG + **wireframe** of cards/notes; full paint on mouse-up |
| Zoom | Toolbar combo / buttons; **mouse wheel** via Hop Web zoom handler (SVG host does not capture wheel events) |
| Drag tables/cards | Client **card-sized** blue outline ghosts while dragging; server applies final positions on mouse-up (RAP has no move-while-held) |
| Drag notes | Full note-size outline from top-left (not a cursor square); same final apply on mouse-up |
| Note resize | Edge/corner cursors and 8 handles; live outline while resizing |
| Multi-select | Lasso; drag previews for all selected cards/notes |
| Relationships | Rubber-band hop line on the SVG effects layer (from card center when sizes are published) |
| Minimap | Bottom-right navigation viewport with miniature cards (including **source model** content) |
| Tab switch | SVG + zoom rebind so the active tab paints without an extra click |
| BV/DV reference links | Follow name-click navigation; shared SVG client stays on the **target** tab (no snap-back to the previous model) |
| Execution map breadcrumb | Drill / breadcrumb **zoom-fit uses the focused subgraph** size (not the full-document maximum) |
| Lineage view details | Same right-hand Markdown sash as desktop (`StyledText`); **View as HTML** still works |
| Lineage view empty/error | Painted in the SVG snapshot (not only the status `Label`) |
| Dark mode | SVG render uses `SvgGc` dark theme + `NotePadStyle` |
| Edit | Double-click / name-click dialogs, **left-click context dialogs** on tables/cards, toolbars (standard SWT/RAP dialogs) |
| Hover | Name underline via Hop Web hover remote object |

## Hop core requirement

Plugin code calls:

- `CanvasSvgFacade.registerCanvas` / `publishSnapshot` / `ensureInteractionHandler`
- `IWebCanvasGraph` (hover + area owners)

These APIs land in Apache Hop via [issue #7873](https://github.com/apache/hop/issues/7873) / [PR #7874](https://github.com/apache/hop/pull/7874). Use **Apache Hop 2.19.0**, which includes that SPI **and** the related Hop Web client fixes (SVG effects-layer previews, zoom wheel hit-testing, active-canvas-only rebind).

### Hop Web client behaviour (core)

The shared client scripts (`canvas-svg.js`, `canvas-zoom.js`, explorer tab hooks) also provide:

- Drag/resize/pan previews on an **effects layer above** the SVG (pipeline `canvas.js` paint sits under the SVG and is not visible for model graphs)
- **Single SVG + zoom remote** per UI session: only the **active** canvas rebinds the client after paint (avoids a background paint stealing the overlay after link navigation)
- Zoom wheel attached to the RAP canvas by widget id; SVG host uses `pointer-events: none` so the wheel reaches the canvas

## Desktop vs web

| Concern | Desktop | Hop Web |
|---------|---------|---------|
| Paint | `SwtGc` on SWT `Canvas` | Server `SvgGc` → SVG snapshot → client overlay |
| Drag while button held | Server mouse-move | Client outline; server applies final position on mouse-up |
| Left-click vs drag | 3px threshold on move-while-held | Mouse-down arms a client drag ghost; mouse-up with no movement is a click (table context dialog) |
| Load duration chart | Optional right pane | Not constructed (RAP lacks `ScrolledComposite.addPaintListener`) |
| Coach palette DnD | DropTarget on canvas | Disabled (use toolbar / dialogs) |

## Architecture (plugin)

```
HopGuiModelGraphBase
  ├── setupWebCanvas()          register + zoom + empty nodes/notes/hops
  ├── paint → *CanvasSvgRenderer → SvgGc painters
  ├── applyWebCanvasRender()    area owners, setNodes/setNotes, publishSnapshot
  └── armWeb*DragModes()        mode=drag|resize|hop for client ghosts

ModelGraphWebCanvasData         RAP-safe setData for nodes/notes/hops/mode
*ModelCanvasSvgRenderer         Source / DV / BV / DM / EM / HLV wrappers
SourceModelSvgPainter           headless .hsm SVG export
LineageViewCanvasSvgRenderer    live .hlv session graph (not a persisted snapshot)
```

Painters already target `IGc`. Interactive web path uses the `*ModelCanvasSvgRenderer` wrappers, collects `AreaOwner` hit regions, and publishes via `CanvasSvgFacade.publishSnapshot`.

## Run locally

1. Install Hop with the canvas SPI and current web client scripts (`core` / `engine` / `ui` / `rap` as needed).
2. Build this plugin: `mvn clean package -DskipTests`
3. Unzip `target/hop-datavault-*-SNAPSHOT.zip` into the Hop Web home (not only `plugins/misc/datavault/`) so Jinjava jars land in `lib/core`.
4. Start Hop Web (for example `docker/run-hop-web-local.sh --quick` from a Hop checkout that mounts the plugin).
5. Create or open a model under the explorer.

## Known limitations

- Continuous “live” card motion while the button is held is client-outline only; the authoritative model updates on mouse-up (RAP).
- Load-duration overview and coach drag-from-palette remain desktop-first.
- Lineage view cards are **not** user-draggable (ELK owns layout). Click is select / context only.
- Marquez, export-folder, and OPS queries run in the **Hop Web server JVM**. `${MARQUEZ_BASE_URL}=http://localhost:5001` is localhost inside that process (or container), not the browser. Use a URL or folder the server can reach, or the **Local models** backend / **Show lineage**.
- Requires the Hop SPI and client fixes above; older Hop Web builds will not compile or run this plugin version correctly.

## Related

- Feature matrix: [feature-overview.adoc](feature-overview.adoc)
- Changelog: [../CHANGELOG.md](../CHANGELOG.md)
- Hop SPI: [apache/hop#7873](https://github.com/apache/hop/issues/7873), [PR #7874](https://github.com/apache/hop/pull/7874)
