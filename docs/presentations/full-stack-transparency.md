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

# Full-stack transparency

20-minute talk from [issue #190](https://github.com/ProjectDataHopper/hopper-edw/issues/190). Slides: [full-stack-transparency.pptx](full-stack-transparency.pptx).

Audience already knows that Data Vault splits one business concept into hubs, links, and satellites, and that Business Vault and dimensional layers sit above that. If the room already lives in Hop, skip the layer reminder on slide 2 and give the time to the demo.

Spoken time is about 15 minutes. The demo is 4 minutes. Leave a minute of air.

Say these three lines out loud. They are also on the slides.

- If you can't see it, you can't own it.
- Best tool for each job gives you the best parts. The engineer still needs the whole picture.
- Looking, deciding, and loading are three different acts.

The bullets in the issue (harvest, gates, JDBC, Gantt, and the rest) stay in these notes. They do not each become a slide.

## 1 — Title (0:20)

**On slide.** Full-stack transparency. Design principles for a Data Vault you can own. *If you can't see it, you can't own it.*

This is a design talk. Twenty minutes, then one customer. I am going to stay off the feature list.

## 2 — One concept, many pieces (1:10)

**On slide.** A customer does not stay a customer table. The failure is not the modeling technique. Schema, load logic, lineage, operations, quality.

Take a customer. In a Data Vault that idea becomes a hub, several satellites, a link, then a business-vault table with its own timeline, then a dimension someone queries.

That spread is the point of the method. It is also why these warehouses get hard to live with. The description of the pieces ends up somewhere else: a modeler, a catalog, pipelines generated last quarter, a monitor, a quality report.

Five kinds of information have to stay with the object. The schema. The load logic. Lineage in both directions. The operational record. The quality rules and the results.

## 3 — The principle (1:00)

**On slide.** View, examine, and edit. Every layer. Every object. Every kind of metadata. *If you can't see it, you can't own it.*

A data engineer has to be able to view, examine, and edit everything about the object they are standing on. Source, raw vault, business vault, dimensional.

The word that usually gets dropped is edit. Many catalogs and lineage products will show you the problem. Fixing it happens in another window, against another copy of the truth. Seeing and fixing have to be the same place.

## 4 — Best tool for each job (1:20)

**On slide.** Optimizes: depth, specialists, one category at a time. Costs: split context, read-only islands, integration as the project. *Best tool for each job gives you the best parts. The engineer still needs the whole picture.*

The reflex is to buy the best tool for each job. A modeler, a lineage product, a quality tool, a scheduler, the database console, and an ETL tool beside them. Each one can be excellent. That approach is good at depth, at people who live in one product, and at purchasing one category at a time.

What it costs the engineer is the whole picture. One hub's truth is split across tools that do not share a name or a version. You switch windows to answer why a value changed. The model, the tables, and the load drift apart. Lineage becomes a place you can look and not type. A large share of the project becomes the wiring between the tools.

Data Vault multiplies that cost, because the concept was already split on purpose.

## 5 — The position (1:00)

**On slide.** One coherent place to view and edit. Specialist tools plug into shared metadata. When depth and visibility conflict, keep visibility and editability.

This is not a rejection of those tools. Three rules.

One coherent place to view and edit.

Specialist tools stay welcome, as consumers and producers of shared metadata. They are not the only home for a fact the engineer needs.

When there is a trade-off, keep visibility and editability, even when that means less depth in one category.

The metadata stays open. Files, a catalog, exports, ordinary formats. A stronger modeler or a stronger lineage store should be able to attach. It should not become the only door.

## 6 — One picture (1:20)

**On slide.** Landing, source model, catalog, raw vault, business vault, dimensional. One group lists every model. Harvest observes. Publishing is a deliberate write.

Landing systems stay outside. You model them. You publish a contract into the catalog. Raw vault, business vault, and dimensional models bind to catalog names, not to a connection string copied into a pipeline.

The catalog is the bus, not a fifth product you go and visit. Harvest writes history. It does not rewrite the contract. Publishing a target layout is a deliberate write after a load.

One resource definition group lists the models. Business vault and the dimensional layer join that same group. They do not start a second context.

Do not read the file types aloud. The picture is the list.

## 7 — Generated, not hidden (1:00)

**On slide.** The model is the contract. Open the pipeline. Read the DDL. See the layout published back.

Generation is allowed only because the result stays inspectable. From the object you open the pipeline that loads it, read the DDL, and see the layout published back to the catalog. The same checks run on the canvas and in the workflow.

A black box would leave the previous picture intact and break the principle. Edit is doing real work here.

## 8 — The ecosystem keeps moving (0:50)

**On slide.** How do you notice without silently rewriting the contract? Sources keep changing. Downstream objects already depend on them.

In a large organization the sources do not hold still. Columns move, lengths change, feeds appear, and the hubs and marts already depend on yesterday's shape.

Detection is the easy part. The design question is how you notice without silently rewriting the contract everyone is loading against.

## 9 — Three different acts (1:20)

**On slide.** Harvest observes. The schema gate decides whether this wave may load. The quality gate decides whether the rows are acceptable. The update loads, then publishes. A deliberate write is not a side effect of looking.

Looking, deciding, and loading are three different acts.

Harvest stores what the live source looks like now. It does not change the model.

The schema gate decides whether this wave may load, against the catalog contract, a version tag, the harvest, the target tables. Failing the wave is a policy.

The quality gate decides whether the rows are acceptable. Structure and content are different questions.

The update loads, and then publishes the layouts back.

In the notes, not on the slide: schema validation against the catalog, downstream lineage, remediation proposals, reports, the schema-drift gate, the data-quality gate. They sit under those four jobs. They do not get their own products.

One rule to say: if a column grew, length remediation expands the model and the target from the catalog length. It does not edit the catalog. If the live source is the new truth, that is a separate write you choose.

## 10 — The happy path will be wrong (0:50)

**On slide.** The day the source is a join, a document, or a file with no types. A side pipeline is how the object stops being owned.

A model-driven loader is fine until the source is a join, a JSON document, a stream, or a file with no types and no lengths.

The tempting fix is a pipeline written beside the model. It works. The special case is no longer something you can open from the model, and the object stops being owned.

## 11 — Complexity stays at the source (1:20)

**On slide.** Query. JSON. Pipeline. Typing rule. JDBC. One generated vault load.

The special case stays inside the source model. A query. A JSON extraction. A pipeline card. A typing rule for a source that brought no types. A JDBC view of that same model, when another tool needs to query it.

Those are still objects. You view them and edit them where the rest of the source lives. The vault load that follows is the same generated shape as the ordinary table.

Do not configure any of these. If a sentence starts to explain the driver URL, stop. The point is where complexity is allowed to live.

## 12 — Silence is not a status (0:50)

**On slide.** Long. Parallel. Remote. A drawing with no last duration does not answer what happened.

Once this is running, the chain is long, often parallel, and almost always on another machine. Failure looks like silence.

A lineage drawing that cannot say how long the last run took, or that a copy has stalled, does not answer what happened.

## 13 — The canvas is the operations surface (1:10)

**On slide.** Metrics beside the model. Durations on the lineage. Status while it runs. A report when it is done. Journey opens these surfaces. It is not a second modeler.

Operational facts sit on the object you already have open.

A Gantt or a performance chart is for one question: what was concurrent? Stall detection, the metrics profile, and execution maps are the same idea at smaller print. Leave them unspoken unless someone asks.

The journey perspective lists sources, controls, the three layers, the workflows, and the reports, and it opens the surface you already use.

## 14 — Demo card (0:20, then 4:00 off the slides)

**On slide.** This customer changed. Where do I look, and what am I allowed to change?

Read the sentence. Leave the slides.

### Before you walk on

- Hop GUI, project `retail-example`.
- Postgres is up. One retail update has already finished, so OPS has `load_run` rows.
- An execution metrics profile is on, so the duration pane on `retail-360.hdv` is populated.
- Do not start a load.

### Clicks

Checked against `retail-example/models/source-tables-crm.hsm`, `retail-360.hdv` (`sat_customer_address`, record source `E2E-customer-address`, parent `hub_customer`), and the vault-graph action **Show update pipeline** (click the icon body, not the name). Do one live pass on the presentation machine before November. This session did not open Hop.

1. **0:30.** Sidebar **EDW Journey** (after Data Catalog). Group `retail-sources`. Point at **1. Sources**, **2. Controls**, **3. Raw Data Vault**. Do not expand the tree.
2. **0:40.** Double-click `source-tables-crm.hsm`. If Journey does not list it, open `models/source-tables-crm.hsm`. Click the `customer_address` card. `customer_id` is the key. Do not open the query **All customer info**.
3. **1:10.** Back to Journey. Double-click `retail-360.hdv`. Click the body of `sat_customer_address`. **Show update pipeline**. The generated pipeline opens. Point at one transform so it is obviously an ordinary pipeline. Close it.
4. **0:50.** Double-click the name `sat_customer_address`. Open the **Lineage** tab. Parent hub `hub_customer`, feed `E2E-customer-address`. Say that downstream continues into Business Vault and the dimension. Do not open them.
5. **0:30.** Close the dialog. The right-hand pane of the same canvas is the load-duration overview. If it is empty, return to Journey and read the last-run line on the model node. Stop.

Out, even if you are ahead: SCD2, facts, the semantic layer, masking, AI help.

### If Hop or Postgres misbehaves

Narrate the same five steps over stills. Use these, in this order, if you do not have fresher shots from rehearsal:

- `docs/images/source-modeler-retail-example-with-query-dialog-generated-sql.png` (the source model; ignore the query dialog)
- `docs/images/data-vault-model-retail-example.png`
- `docs/images/data-vault-satellite-dialog-lineage-tab.png` (`sat_customer_address`)
- `docs/images/data-vault-retail-360-model-with-duration-metrics.png`

There is no committed Journey screenshot. Capture one during the live rehearsal if you want it on the fallback.

## 15 — Three lines (1:00)

**On slide.** The three lines. Leave-behind: the architecture page.

If you can't see it, you can't own it.

Best tool for each job gives you the best parts. The engineer still needs the whole picture.

Looking, deciding, and loading are three different acts.

The page to take away is the architecture page. Stop. Do not preview a roadmap.

## If someone asks

- **Semantic layer.** Consumption metadata is another editable surface on the warehouse. It is not a separate BI catalog. Then stop.
- **AI help.** Proposals are reviewed before they are applied. Same edit rule. Then stop.
- **Why not the best lineage product?** It can sit on the open metadata. It cannot be the only place the engineer is allowed to look.

## Left out of this pass

Issue #190 also asks for a design-principles page. That page does not exist yet. Slides 3, 5, 7, 9, and 11 can be lifted into it later. The older decks `hop-data-vault-overview.md` and `hop-data-vault-features.html` stay as they are.
