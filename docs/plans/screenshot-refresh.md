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

# Screenshot refresh (issue 213)

The checklist is [screenshot-refresh.csv](screenshot-refresh.csv). Open it in a spreadsheet and sort by `session`, then `order`.

126 images. 88 are marked `recapture`, 37 are marked `review`, and 1 is marked `decide`. Eight review rows were added on 2026-10-20: the Data Vault source-modeling sequence and the DBeaver driver list.

Type `Y` in `done` when the file at `image_path` has been replaced. Type `drop` on the decide row if the image should go. Replacing the file updates every page in `used_in`. Captions stay as they are.

`file_to_load` is relative to the Hop project in `project`. An empty file means the surface is a menu, a new unsaved editor, or an external tool.

## Before the first shot

Restart Hop once on the current plugin. Keep one window size for the whole session and crop to the dialog or canvas named in `context`. Do not save models, metadata, or workflows while capturing. Do not click Create selected, Import, or OK on a dialog that would write a file, unless that row says the shot is the result of applying.

Rows whose `capture_notes` start with `needs_setup:` need a database, Marquez, a harvest, or a provoked schema change. Skip them on a click-through that only opens files.

`priority=review` can stay if the toolbar and icons in that shot still match the current GUI.

## S1 — retail-example, no database (51)

Open the retail-example project once. Walk the sheet in order. The source-model shots are all `models/source-tables-crm.hsm` (canvas, All customer info, `order_header`, the JSON card, `asn-package-lines`, masking, then Generate Data Vault into a **new** `.hdv` that you discard). Then `models/retail-360.hdv`, the dimensional models, `retail-sources`, `retail-bus`, `premodel-defaults`, `vault-target-mapping`, and source model service `crm`.

Workflow dialogs are on `workflows/run-retail-update-models.hwf` and `workflows/send-lineage-to-marquez.hwf`. Open the actions. Do not run them.

The execution-map file is `work/execution-maps/simulate-6-months.hem`. The workflow on disk is `workflows/simulate-n-months.hwf`. Crawl that workflow if the hem is missing. The hem is gitignored.

Configure EDW, the Hop Data Vault options, a new Jinja macro library, Create operational analytics, and a new Business Vault model (for Import dbt models) have no file to save.

## S2 — retail-example, one load (18)

Start Postgres and run `workflows/run-retail-initial.hwf` once (or the update workflow if the vault is already loaded).

While it runs, capture Open execution on the Data Vault Update action in `workflows/update-retail-dv-bv-dm.hwf`, and the resource-group load metrics dialog. The stored Open execution shot is the action named Data Vault Update Retry in the integration-tests large suite. The retail action shows the same menu. Do not run that large suite for this picture.

After the load, capture duration bars on `models/retail-360.hdv` and the workflow load-overview HTML under `work/reports` (summary, then the dimensional pipeline section).

The stored lineage shot is `models/f_orders-upstream.hlv` after `workflows/send-lineage-to-marquez.hwf`. The seed `f_orders` is expanded. The other nodes are compact.

Harvest history, the customer_id length change, the blocked validation, and the remediation dialogs are a second pass. They show a provoked drift (address_line1 / E2E-customer-contact), not the happy path. The generated workflow lands under `workflows/schema-remediation/`. The PASS and CRITICAL BLOCKED pictures are the HTML reports in a browser.

The two calculation-unit-test dialogs only need a new shot if you regenerate the test against a loaded `customer_360_bv`. The pipeline and the context menu are already in S1.

## S3 — integration-tests (33)

Open the integration-tests project once.

`tests/basic/vault1.hdv` covers the canvas and the Apache Hop website note. Edit model on that file only shows the configuration picker (`data-vault-vault1`). The hashing and load tabs are the Data Vault configuration editor for `data-vault-vault1` (General, Unknown records, Invalid records, Orphan handling, Standard columns, Target loading, Generated artifacts). Those shots are already in `docs/images/data-vault-model-configuration-editor-*.png` and marked done. The Data Vault Update action is `update vault1.hdv` on `tests/basic/update-vault1.hwf`. `vault1.hbv` is the SQL business table `sat_customer_hb_jinja`. The successful-run photo of `update-vault1.hwf` needs that workflow to have run.

Then the small fixture models: `customer-phone.hdv`, `link-satellite.hdv`, `load-end-date.hdv`, and metadata `data-vault-load-end-date` (PROJECT.md calls it load-end-date-config).

Customer 360 is `tests/multi-satellite-bv/customer-360.hdv`, `customer-360.hbv` (SCD2 General, Field mappings, Satellite settings on `customer_360_bv`), and `customer-360-external.hdv`. `retail-360.hbv` has a table with the same description. These shots follow the integration getting-started page, so use the integration-tests files.

Search Everywhere for `CRM-party-transfer-hrp` in this project, with `tests/hub-alias-role-playing/hub-alias-role-playing.hdv` open. That string is not in the retail model.

`tests/run-tests.hwf` is stored twice. Replace both `docs/images/workflow-run-tests-screenshot.png` and `integration-tests/images/workflow-run-tests-screenshot.png`.

## S4 — DBeaver, Marquez, Draw.io (11)

DBeaver is the Hop Source Model driver, a connection whose schema is `crm`, and a query of `crm.order_header`. The Hop side of that connection is source model service `crm`, already shot in S1.

Marquez comes after `workflows/send-lineage-to-marquez.hwf`: job lineage, column lineage into `customer_360_bv`, and dataset `E2E-customer-prefs`.

The four architecture pictures are Draw.io diagrams. Regenerate the retail architecture export (`work/drawio/retail-solution.drawio`, `retail-data.drawio`, `data-vault.drawio`, `dimensional.drawio`) and screenshot the export. A Hop dialog recapture will not update those icons.

## S5 — AI advisor (12)

Needs a configured provider. Rows 1–5 use `models/retail-360.hdv`. The link-satellite answer uses the prompt "Please explain what link satellites are and give an example." Leave those proposals unapplied. Skip the session and leave `done` empty when no provider is configured.

Rows 6–12 are already captured from `test/ai-modeling-test.hdv`: ask for a model of the selected E2E catalog sources, then apply hubs before the links and satellites that depend on them. The last frame is the canvas after apply.

## S0 — decide (1)

`resource-definition-group-validation-block-issue-details-and-proposals.png` is not referenced. The S2 validation and remediation shots cover that flow. Mark it `drop`, or name the page that should embed it before taking a new shot.

Four other images that no AsciiDoc page embeds are still on the list, because the component explorer or the transparency talk uses them: `data-vault-model-retail-360.png`, `source-model-sql-transform-dialog.png`, `source-model-sql-explain-plan-dialog.png`, and `business-vault-sql-table-dbt-reference-style.png`.
