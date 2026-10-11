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

# EDW operational analytics (issue #195)

Business intelligence about the warehouse itself. One action reads the operational (OPS) database Hopper already fills and generates a source model, raw Data Vault, a small Business Vault, a dimensional model, a semantic layer, load workflows, and an EDW Journey group.

Issue: [ProjectDataHopper/hopper-edw#195](https://github.com/ProjectDataHopper/hopper-edw/issues/195).

## Status

Done in the working tree:

- `OpsAnalyticsCatalog` lists the 16 publisher tables, logical relationships, and six conformed-key queries. Column lists are checked against publisher DDL. `hop_executions` is optional and matches the retail bootstrap SQL.
- `OpsAnalyticsSourceModelBuilder` writes the `.hsm`. `quality_run` is drawn as `quality_run_hub` so the stock same-primary-key rule keeps it as the hub kernel ahead of `quality_alert`.
- `SourceToVaultOptions.singleParentCompositeKeyAsLink` defaults to false. When set, a composite key that is only partly covered by foreign keys becomes a non-historized link, and keyed source queries are hub parents. Generate Data Vault does not turn the flag on.
- `OpsAnalyticsVaultBuilder` classifies with that flag, applies the vault, and splits it into `ops_*.hdv` files. `lnk_load_pipeline_metric` keeps `linkSatelliteSources`.
- `OpsAnalyticsBusinessVaultBuilder` writes `bv_load_run_duration` and `bv_model_latest_load` as SQL views, plus bridge `bv_workflow_models`. Duration SQL follows the target plugin id (Postgres `EXTRACT(EPOCH)`, MySQL/SingleStore `TIMESTAMPDIFF`, SQL Server `DATEDIFF_BIG`, Snowflake `DATEDIFF`). Latest-load uses `ROW_NUMBER`, which the four main engines accept. The bridge joins `lnk_load_run_workflow_execution` to `lnk_load_run_model`. It does not treat `hop_executions.id` as `workflow_execution_id`. Model check against the split vault files reports no errors.
- `OpsAnalyticsDimensionalBuilder` publishes the vault and keeps twelve facts (`f_pipeline_load` through `f_harvest_change`), conformed dimensions `d_pipeline`, `d_transform`, `d_model`, `d_subject`, and `d_rule`, and `dim_date` with load, measured, harvest, and alerted roles. Repeating strings and run keys are degenerates. A link with one hub and a dependent child key is applied (insight grain). Semantic selections: `pipeline-duration-by-day`, `model-load-trend`, `quality-findings-by-severity`. Bus catalog `ops-analytics` has domains Load, Quality, and Harvest.
- `OpsAnalyticsProjectBuilder` saves configurations `ops-analytics-dv`, `ops-analytics-bv`, and `ops-analytics-dm`, the resource definition group (hubs and references before links, then the `.hbv` and `.hdm`), and workflows `ops-analytics-initial.hwf` and `ops-analytics-incremental.hwf`. The initial workflow creates target tables. The incremental workflow does not change structure. The group-update action has no truncate switch. Data Vault and Business Vault loads stay hash-diff. Dimensional facts insert on every run. Metrics publication is off so the analytics load does not write back into OPS.

- **Create operational analytics…** is on the EDW Journey toolbar and under **Tools**. The dialog uses one grouped widget panel. Replace is refused unless the checkbox is set, and then Hop asks again. Missing OPS tables are listed and generation still runs. The source model opens afterward. Help: `docs/help/ops-analytics-dialog.adoc`. Opened in Hop GUI on the retail project; OK was not submitted.

- Retail reference copy is `retail-example/models/ops-analytics/`. Source connection `OPS`, schema `dv_ops`. Configurations `ops-analytics-dv`, `ops-analytics-bv`, and `ops-analytics-dm` all target the retail `Vault` connection. `run-retail-initial.hwf` and `run-retail-update.hwf` call `ops-analytics-incremental.hwf` after `run-retail-update-models` succeeds. On analytics failure, action **Continue after analytics** (`SUCCESS`) clears the error and the same downstream actions still run. Initial continues to **Generate initial execution map** and **Export architecture**. Update continues to **Record retail load** and **Export architecture**. The database-setup workflows are not chained: they finish before the vault update that writes OPS rows. `RetailOpsAnalyticsChainTest` locks those hops and does not regenerate the models when the incremental workflow is already present.

User doc: `docs/ops-analytics.adoc` (also linked from the docs index, feature overview, retail getting-started, and `CHANGELOG.md` Unreleased). Screenshot of the dialog on the retail project: `docs/images/create-operational-analytics-dialog.png` (OPS, `dv_ops`, three targets `Vault`; Include hop_executions on; Replace off). OK was not submitted. A live Postgres load of the generated vault has not been run.

## Why this is not Import schema, then Generate Data Vault

OPS DDL declares primary keys and indexes. It does not declare foreign keys. `hop_executions` has no primary key in the retail bootstrap. `SourceToVaultClassifier` skips an isolated table and requires two foreign keys on the primary key before it emits a link. Dimensional publish is a draft. There is no generic Business Vault generator.

The feature is a generator profile on the existing engines.

## Source surface

Sixteen publisher tables in `dv_ops` (load metrics, workflow overview, quality history, schema harvest) plus optional `hop_executions`. `quality_alert` shares `quality_run_id` with `quality_run` and becomes `sat_quality_alert` on `hub_quality_run`.

Conformed keys that are not tables are source queries: `workflow_execution`, `pipeline`, `transform`, `model`, `subject`, `rule`. Their SQL is a quoted `UNION` of `SELECT DISTINCT` arms with `IS NOT NULL` filters, built with `DatabaseMeta` quoting when a connection is supplied.

`workflow_execution_id` is not treated as `hop_executions.id`. Execution rows, when included, are their own hub via the `parent_id` self relationship. The JSON column stays one string.

Object names follow `SourceToVaultNaming`. Examples locked by tests: `hub_load_run`, `hub_pipeline`, `hub_quality_run`, `lnk_load_pipeline_metric`, `lnk_load_insight` (dependent child key `insight_seq`), `lnk_quality_finding` (dependent child key `finding_seq`, hubs `hub_quality_run`, `hub_subject`, `hub_rule`).

## Remaining generator shape

Business Vault is one `.hbv`: `bv_load_run_duration` (`finished_at - started_at`), `bv_model_latest_load` (latest run per model by `started_at`), and `bv_workflow_models` (workflow-execution hub to model hub through the load-run links). No SCD2, identity map, or survivorship. `hop_executions` stays out of this bridge.

Dimensional publish runs on the generated vault, then `OpsAnalyticsDimensionalBuilder` adds `dim_date`, keeps the twelve operational facts, copies repeating strings to degenerate dimensions, and keeps conformed dimensions for pipeline, transform, model, subject, rule, and date. Named selections: `pipeline-duration-by-day`, `model-load-trend`, `quality-findings-by-severity`.

Workflows call Update resource definition group. The initial workflow creates the analytics tables. The incremental workflow leaves that structure alone. Re-reading OPS is the increment for Data Vault and Business Vault; there is no watermark in this issue. Dimensional facts insert on every run, including each retail update. Retail calls the incremental workflow after the model update in `run-retail-initial.hwf` and `run-retail-update.hwf`. An analytics failure hops to a Success action so the retail load still finishes.

The dialog is **Create operational analytics…** on the EDW Journey toolbar and the Tools menu. It asks for the OPS connection and schema, three target connections (default one connection, schemas `ops_dv`, `ops_bv`, `ops_dm`), a project folder, the group name, whether to include `hop_executions`, and whether to replace an existing folder. The generator ships in the plugin. Retail under `retail-example/models/ops-analytics/` is the reference copy. No sample zip.

## Verification so far

`OpsAnalyticsSourceModelBuilderTest`, `OpsAnalyticsVaultBuilderTest`, `OpsAnalyticsBusinessVaultBuilderTest`, `OpsAnalyticsDimensionalBuilderTest`, `OpsAnalyticsProjectBuilderTest`, `OpsAnalyticsDialogSupportTest`, `HelpTopicsTest`, `SourceToVaultClassifierTest`, and `RetailOpsAnalyticsChainTest`. Postgres load of the generated vault is still required before the retail slice is called done. The dialog has not been click-tested.
