/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.edw.datavault.opsanalytics;

import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metrics.LoadRunMetricsCatalogPublisher;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewDdlSupport;
import org.hopper.edw.quality.history.DataQualityHistoryPublisher;

/**
 * Owned OPS tables, logical relationships, and conformed-key queries for operational analytics.
 *
 * <p>Column lists mirror the publisher DDL. {@code quality_run} is drawn as {@code quality_run_hub}
 * so the stock same-primary-key rule keeps it as the hub kernel ahead of {@code quality_alert}.
 * Relationships are logical; the generator does not alter the OPS database.
 */
public final class OpsAnalyticsCatalog {

  /** Canvas name for {@code quality_run}. The physical table name stays {@code quality_run}. */
  public static final String QUALITY_RUN_CARD = "quality_run_hub";

  public static final String TABLE_EXECUTION = "hop_executions";

  public static final String QUERY_WORKFLOW_EXECUTION = "workflow_execution";
  public static final String QUERY_PIPELINE = "pipeline";
  public static final String QUERY_TRANSFORM = "transform";
  public static final String QUERY_MODEL = "model";
  public static final String QUERY_SUBJECT = "subject";
  public static final String QUERY_RULE = "rule";

  private OpsAnalyticsCatalog() {}

  public static List<String> publisherTableNames() {
    return List.of(
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN,
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC,
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC,
        LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT,
        WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW,
        WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL,
        DataQualityHistoryPublisher.TABLE_QUALITY_RUN,
        DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT,
        DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD,
        DataQualityHistoryPublisher.TABLE_QUALITY_FINDING,
        DataQualityHistoryPublisher.TABLE_QUALITY_ALERT,
        SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN,
        SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT,
        SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD,
        SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE,
        SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK);
  }

  public static List<OpsAnalyticsTable> tables(boolean includeExecutions) {
    List<OpsAnalyticsTable> tables = new ArrayList<>();
    tables.add(table(LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN, loadRunColumns()));
    tables.add(
        table(LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC, pipelineMetricColumns()));
    tables.add(
        table(
            LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC, transformMetricColumns()));
    tables.add(table(LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT, insightColumns()));
    tables.add(
        table(
            WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW, overviewColumns()));
    tables.add(
        table(
            WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL,
            overviewModelColumns()));
    tables.add(qualityRun());
    tables.add(table(DataQualityHistoryPublisher.TABLE_QUALITY_ALERT, qualityAlertColumns()));
    tables.add(
        table(
            DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT,
            qualitySubjectColumns()));
    tables.add(
        table(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD, qualityFieldColumns()));
    tables.add(table(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, qualityFindingColumns()));
    tables.add(table(SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN, harvestRunColumns()));
    tables.add(
        table(SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT, harvestSubjectColumns()));
    tables.add(table(SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD, harvestFieldColumns()));
    tables.add(table(SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE, harvestChangeColumns()));
    tables.add(table(SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK, harvestFkColumns()));
    if (includeExecutions) {
      tables.add(executionTable());
    }
    return List.copyOf(tables);
  }

  public static List<OpsAnalyticsQuery> queries() {
    return List.of(
        new OpsAnalyticsQuery(
            QUERY_WORKFLOW_EXECUTION,
            List.of(OpsAnalyticsColumn.keyStr("workflow_execution_id", 64, 1)),
            List.of(
                branch(LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN, "workflow_execution_id"),
                branch(DataQualityHistoryPublisher.TABLE_QUALITY_RUN, "workflow_execution_id"),
                branch(SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN, "workflow_execution_id"),
                branch(
                    WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW,
                    "workflow_execution_id"))),
        new OpsAnalyticsQuery(
            QUERY_PIPELINE,
            List.of(OpsAnalyticsColumn.keyStr("pipeline_name", 255, 1)),
            List.of(
                branch(
                    LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC, "pipeline_name"),
                branch(
                    LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC, "pipeline_name"))),
        new OpsAnalyticsQuery(
            QUERY_TRANSFORM,
            List.of(
                OpsAnalyticsColumn.keyStr("pipeline_name", 255, 1),
                OpsAnalyticsColumn.keyStr("transform_name", 255, 2)),
            List.of(
                branch(
                    LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC,
                    "pipeline_name",
                    "transform_name"))),
        new OpsAnalyticsQuery(
            QUERY_MODEL,
            List.of(
                OpsAnalyticsColumn.keyStr("model_type", 16, 1),
                OpsAnalyticsColumn.keyStr("model_name", 255, 2)),
            List.of(
                branch(LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN, "model_type", "model_name"),
                branch(
                    WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL,
                    "model_type",
                    "model_name"))),
        new OpsAnalyticsQuery(
            QUERY_SUBJECT,
            List.of(OpsAnalyticsColumn.keyStr("subject_key", 512, 1)),
            List.of(
                branch(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT, "subject_key"),
                branch(DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD, "subject_key"),
                branch(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, "subject_key"),
                branch(SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT, "subject_key"),
                branch(SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD, "subject_key"),
                branch(SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE, "subject_key"),
                branch(SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK, "subject_key"))),
        new OpsAnalyticsQuery(
            QUERY_RULE,
            List.of(OpsAnalyticsColumn.keyStr("rule_id", 128, 1)),
            List.of(branch(DataQualityHistoryPublisher.TABLE_QUALITY_FINDING, "rule_id"))));
  }

  public static List<OpsAnalyticsRelationship> relationships(boolean includeExecutions) {
    List<OpsAnalyticsRelationship> relationships = new ArrayList<>();
    String loadRun = LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN;
    String pipelineMetric = LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC;
    String transformMetric = LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC;
    String insight = LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT;
    String overview = WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW;
    String overviewModel = WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL;
    String qualitySubject = DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT;
    String qualityField = DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD;
    String finding = DataQualityHistoryPublisher.TABLE_QUALITY_FINDING;
    String harvestRun = SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN;
    String harvestSubject = SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT;
    String harvestField = SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD;
    String harvestChange = SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE;
    String harvestFk = SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK;

    relationships.add(tableFk(pipelineMetric, "run_id", loadRun, "run_id"));
    relationships.add(queryFk(pipelineMetric, "pipeline_name", QUERY_PIPELINE, "pipeline_name"));
    relationships.add(tableFk(transformMetric, "run_id", loadRun, "run_id"));
    relationships.add(
        queryFk(
            transformMetric,
            List.of("pipeline_name", "transform_name"),
            QUERY_TRANSFORM,
            List.of("pipeline_name", "transform_name")));
    relationships.add(tableFk(insight, "run_id", loadRun, "run_id"));
    relationships.add(tableFk(overviewModel, "overview_id", overview, "overview_id"));
    relationships.add(tableFk(overviewModel, "load_run_id", loadRun, "run_id"));
    relationships.add(
        queryFk(
            overviewModel,
            List.of("model_type", "model_name"),
            QUERY_MODEL,
            List.of("model_type", "model_name")));
    relationships.add(
        tableFk(
            DataQualityHistoryPublisher.TABLE_QUALITY_ALERT,
            "quality_run_id",
            QUALITY_RUN_CARD,
            "quality_run_id"));
    relationships.add(tableFk(qualitySubject, "quality_run_id", QUALITY_RUN_CARD, "quality_run_id"));
    relationships.add(queryFk(qualitySubject, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(tableFk(qualityField, "quality_run_id", QUALITY_RUN_CARD, "quality_run_id"));
    relationships.add(queryFk(qualityField, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(tableFk(finding, "quality_run_id", QUALITY_RUN_CARD, "quality_run_id"));
    relationships.add(queryFk(finding, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(queryFk(finding, "rule_id", QUERY_RULE, "rule_id"));
    relationships.add(tableFk(harvestSubject, "harvest_run_id", harvestRun, "harvest_run_id"));
    relationships.add(queryFk(harvestSubject, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(tableFk(harvestField, "harvest_run_id", harvestRun, "harvest_run_id"));
    relationships.add(queryFk(harvestField, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(tableFk(harvestChange, "harvest_run_id", harvestRun, "harvest_run_id"));
    relationships.add(queryFk(harvestChange, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(tableFk(harvestFk, "harvest_run_id", harvestRun, "harvest_run_id"));
    relationships.add(queryFk(harvestFk, "subject_key", QUERY_SUBJECT, "subject_key"));
    relationships.add(
        queryFk(
            QUERY_TRANSFORM,
            SourceEndpointKind.QUERY,
            "pipeline_name",
            QUERY_PIPELINE,
            "pipeline_name"));
    relationships.add(
        queryFk(loadRun, "workflow_execution_id", QUERY_WORKFLOW_EXECUTION, "workflow_execution_id"));
    relationships.add(
        queryFk(
            QUALITY_RUN_CARD,
            "workflow_execution_id",
            QUERY_WORKFLOW_EXECUTION,
            "workflow_execution_id"));
    relationships.add(
        queryFk(
            harvestRun, "workflow_execution_id", QUERY_WORKFLOW_EXECUTION, "workflow_execution_id"));
    relationships.add(
        queryFk(
            overview, "workflow_execution_id", QUERY_WORKFLOW_EXECUTION, "workflow_execution_id"));
    relationships.add(
        queryFk(
            loadRun,
            List.of("model_type", "model_name"),
            QUERY_MODEL,
            List.of("model_type", "model_name")));
    if (includeExecutions) {
      relationships.add(tableFk(TABLE_EXECUTION, "parent_id", TABLE_EXECUTION, "id"));
    }
    return List.copyOf(relationships);
  }

  private static OpsAnalyticsTable table(String name, List<OpsAnalyticsColumn> columns) {
    return new OpsAnalyticsTable(name, name, false, columns);
  }

  private static OpsAnalyticsTable qualityRun() {
    return new OpsAnalyticsTable(
        QUALITY_RUN_CARD,
        DataQualityHistoryPublisher.TABLE_QUALITY_RUN,
        false,
        qualityRunColumns());
  }

  private static OpsAnalyticsTable executionTable() {
    return new OpsAnalyticsTable(TABLE_EXECUTION, TABLE_EXECUTION, true, executionColumns());
  }

  private static OpsAnalyticsQuery.Branch branch(String table, String... columns) {
    return new OpsAnalyticsQuery.Branch(table, List.of(columns));
  }

  private static OpsAnalyticsRelationship tableFk(
      String child, String childColumn, String parent, String parentColumn) {
    return new OpsAnalyticsRelationship(
        child,
        SourceEndpointKind.TABLE,
        List.of(childColumn),
        parent,
        SourceEndpointKind.TABLE,
        List.of(parentColumn));
  }

  private static OpsAnalyticsRelationship queryFk(
      String child, String childColumn, String parent, String parentColumn) {
    return queryFk(
        child, SourceEndpointKind.TABLE, childColumn, parent, parentColumn);
  }

  private static OpsAnalyticsRelationship queryFk(
      String child,
      SourceEndpointKind childKind,
      String childColumn,
      String parent,
      String parentColumn) {
    return new OpsAnalyticsRelationship(
        child,
        childKind,
        List.of(childColumn),
        parent,
        SourceEndpointKind.QUERY,
        List.of(parentColumn));
  }

  private static OpsAnalyticsRelationship queryFk(
      String child, List<String> childColumns, String parent, List<String> parentColumns) {
    return new OpsAnalyticsRelationship(
        child,
        SourceEndpointKind.TABLE,
        childColumns,
        parent,
        SourceEndpointKind.QUERY,
        parentColumns);
  }

  private static List<OpsAnalyticsColumn> loadRunColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("run_id", 64, 1),
        OpsAnalyticsColumn.date("started_at"),
        OpsAnalyticsColumn.date("finished_at"),
        OpsAnalyticsColumn.str("model_type", 16),
        OpsAnalyticsColumn.str("model_name", 255),
        OpsAnalyticsColumn.str("workflow_name", 255),
        OpsAnalyticsColumn.str("workflow_execution_id", 64),
        OpsAnalyticsColumn.str("log_channel_id", 64),
        OpsAnalyticsColumn.str("pipeline_run_configuration", 255),
        OpsAnalyticsColumn.bool("success"),
        OpsAnalyticsColumn.num("error_count"));
  }

  private static List<OpsAnalyticsColumn> pipelineMetricColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("pipeline_name", 255, 2),
        OpsAnalyticsColumn.str("element_type", 64),
        OpsAnalyticsColumn.str("element_name", 255),
        OpsAnalyticsColumn.str("source_name", 255),
        OpsAnalyticsColumn.num("source_rows_read"),
        OpsAnalyticsColumn.num("target_rows_read"),
        OpsAnalyticsColumn.num("target_rows_inserted"),
        OpsAnalyticsColumn.num("errors"),
        OpsAnalyticsColumn.date("execution_start_date"),
        OpsAnalyticsColumn.date("execution_end_date"),
        OpsAnalyticsColumn.num("duration_ms"));
  }

  private static List<OpsAnalyticsColumn> transformMetricColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("pipeline_name", 255, 2),
        OpsAnalyticsColumn.keyStr("transform_name", 255, 3),
        OpsAnalyticsColumn.str("logical_role", 64),
        OpsAnalyticsColumn.str("element_type", 64),
        OpsAnalyticsColumn.str("element_name", 255),
        OpsAnalyticsColumn.str("parent_element_name", 255),
        OpsAnalyticsColumn.num("rows_read"),
        OpsAnalyticsColumn.num("rows_written"),
        OpsAnalyticsColumn.num("rows_updated"),
        OpsAnalyticsColumn.num("rows_rejected"),
        OpsAnalyticsColumn.num("errors"),
        OpsAnalyticsColumn.num("duration_ms"));
  }

  private static List<OpsAnalyticsColumn> insightColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("run_id", 64, 1),
        OpsAnalyticsColumn.keyNum("insight_seq", 2),
        OpsAnalyticsColumn.str("severity", 16),
        OpsAnalyticsColumn.str("code", 64),
        OpsAnalyticsColumn.str("message", 2000),
        OpsAnalyticsColumn.str("element_name", 255),
        OpsAnalyticsColumn.str("related_element_name", 255),
        OpsAnalyticsColumn.str("metric_json", 4000));
  }

  private static List<OpsAnalyticsColumn> overviewColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("overview_id", 64, 1),
        OpsAnalyticsColumn.str("workflow_execution_id", 64),
        OpsAnalyticsColumn.str("root_workflow_name", 255),
        OpsAnalyticsColumn.str("metrics_workflow_name", 255),
        OpsAnalyticsColumn.date("started_at"),
        OpsAnalyticsColumn.date("finished_at"),
        OpsAnalyticsColumn.num("duration_ms"),
        OpsAnalyticsColumn.num("model_count"),
        OpsAnalyticsColumn.num("pipeline_count"),
        OpsAnalyticsColumn.num("insight_count"),
        OpsAnalyticsColumn.num("total_source_rows_read"),
        OpsAnalyticsColumn.num("total_target_rows_inserted"),
        OpsAnalyticsColumn.num("total_errors"),
        OpsAnalyticsColumn.bool("success"));
  }

  private static List<OpsAnalyticsColumn> overviewModelColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("overview_id", 64, 1),
        OpsAnalyticsColumn.keyNum("sequence_no", 2),
        OpsAnalyticsColumn.str("load_run_id", 64),
        OpsAnalyticsColumn.str("model_type", 16),
        OpsAnalyticsColumn.str("model_name", 255),
        OpsAnalyticsColumn.num("pipeline_count"),
        OpsAnalyticsColumn.num("source_rows_read"),
        OpsAnalyticsColumn.num("target_rows_read"),
        OpsAnalyticsColumn.num("target_rows_inserted"),
        OpsAnalyticsColumn.num("errors"),
        OpsAnalyticsColumn.num("duration_ms"),
        OpsAnalyticsColumn.num("insight_count"),
        OpsAnalyticsColumn.bool("success"));
  }

  private static List<OpsAnalyticsColumn> qualityRunColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("quality_run_id", 64, 1),
        OpsAnalyticsColumn.date("measured_at"),
        OpsAnalyticsColumn.str("lifecycle", 16),
        OpsAnalyticsColumn.str("evaluation_mode", 32),
        OpsAnalyticsColumn.str("load_id", 64),
        OpsAnalyticsColumn.str("workflow_name", 255),
        OpsAnalyticsColumn.str("workflow_execution_id", 64),
        OpsAnalyticsColumn.num("subject_count"),
        OpsAnalyticsColumn.num("finding_count"),
        OpsAnalyticsColumn.num("blocking_count"),
        OpsAnalyticsColumn.num("warning_count"),
        OpsAnalyticsColumn.num("info_count"),
        OpsAnalyticsColumn.num("infra_error_count"),
        OpsAnalyticsColumn.bool("success"),
        OpsAnalyticsColumn.str("subjects_json", 4000),
        OpsAnalyticsColumn.str("infra_errors_json", 4000));
  }

  private static List<OpsAnalyticsColumn> qualityAlertColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("quality_run_id", 64, 1),
        OpsAnalyticsColumn.date("alerted_at"),
        OpsAnalyticsColumn.str("disposition_mode", 32),
        OpsAnalyticsColumn.bool("disposition_failed"),
        OpsAnalyticsColumn.str("summary", 2000));
  }

  private static List<OpsAnalyticsColumn> qualitySubjectColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("quality_run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("subject_key", 512, 2),
        OpsAnalyticsColumn.num("row_count"),
        OpsAnalyticsColumn.bool("row_count_exact"),
        OpsAnalyticsColumn.str("evaluation_mode", 32),
        OpsAnalyticsColumn.str("lifecycle", 16),
        OpsAnalyticsColumn.date("captured_at"));
  }

  private static List<OpsAnalyticsColumn> qualityFieldColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("quality_run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("subject_key", 512, 2),
        OpsAnalyticsColumn.keyStr("field_name", 255, 3),
        OpsAnalyticsColumn.num("null_count"),
        OpsAnalyticsColumn.num("empty_string_count"),
        OpsAnalyticsColumn.num("non_null_count"),
        OpsAnalyticsColumn.num("exact_distinct_count"),
        OpsAnalyticsColumn.bool("distinct_truncated"),
        OpsAnalyticsColumn.str("min_value", 500),
        OpsAnalyticsColumn.str("max_value", 500),
        OpsAnalyticsColumn.num("min_string_length"),
        OpsAnalyticsColumn.num("max_string_length"),
        OpsAnalyticsColumn.str("top_values_json", 4000));
  }

  private static List<OpsAnalyticsColumn> qualityFindingColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("quality_run_id", 64, 1),
        OpsAnalyticsColumn.keyNum("finding_seq", 2),
        OpsAnalyticsColumn.str("subject_key", 512),
        OpsAnalyticsColumn.str("rule_id", 128),
        OpsAnalyticsColumn.str("rule_name", 255),
        OpsAnalyticsColumn.str("rule_type", 64),
        OpsAnalyticsColumn.str("severity", 16),
        OpsAnalyticsColumn.str("field_name", 255),
        OpsAnalyticsColumn.str("message", 2000),
        OpsAnalyticsColumn.str("actual_summary", 1000),
        OpsAnalyticsColumn.str("expected_summary", 1000),
        OpsAnalyticsColumn.str("metrics_json", 4000));
  }

  private static List<OpsAnalyticsColumn> harvestRunColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("harvest_run_id", 64, 1),
        OpsAnalyticsColumn.date("started_at"),
        OpsAnalyticsColumn.date("finished_at"),
        OpsAnalyticsColumn.str("resource_group_name", 255),
        OpsAnalyticsColumn.str("catalog_connection", 255),
        OpsAnalyticsColumn.str("expected_baseline", 128),
        OpsAnalyticsColumn.str("status", 16),
        OpsAnalyticsColumn.num("subject_count"),
        OpsAnalyticsColumn.num("change_count"),
        OpsAnalyticsColumn.num("error_count"),
        OpsAnalyticsColumn.num("subjects_with_changes"),
        OpsAnalyticsColumn.str("workflow_name", 255),
        OpsAnalyticsColumn.str("workflow_execution_id", 64),
        OpsAnalyticsColumn.str("scope_summary", 2000));
  }

  private static List<OpsAnalyticsColumn> harvestSubjectColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("harvest_run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("subject_key", 512, 2),
        OpsAnalyticsColumn.str("catalog_connection", 255),
        OpsAnalyticsColumn.str("source_type", 32),
        OpsAnalyticsColumn.str("database_meta_name", 255),
        OpsAnalyticsColumn.str("schema_name", 255),
        OpsAnalyticsColumn.str("table_name", 255),
        OpsAnalyticsColumn.str("discovery_status", 32),
        OpsAnalyticsColumn.bool("in_sync"),
        OpsAnalyticsColumn.num("change_count"),
        OpsAnalyticsColumn.str("message", 4000));
  }

  private static List<OpsAnalyticsColumn> harvestFieldColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("harvest_run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("subject_key", 512, 2),
        OpsAnalyticsColumn.keyStr("field_role", 16, 3),
        OpsAnalyticsColumn.keyStr("field_name", 255, 4),
        OpsAnalyticsColumn.str("hop_type", 64),
        OpsAnalyticsColumn.str("field_length", 64),
        OpsAnalyticsColumn.str("field_precision", 64),
        OpsAnalyticsColumn.num("primary_key_position"),
        OpsAnalyticsColumn.str("source_data_type", 128));
  }

  private static List<OpsAnalyticsColumn> harvestChangeColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("harvest_run_id", 64, 1),
        OpsAnalyticsColumn.keyNum("change_seq", 2),
        OpsAnalyticsColumn.str("subject_key", 512),
        OpsAnalyticsColumn.str("change_kind", 64),
        OpsAnalyticsColumn.str("field_name", 255),
        OpsAnalyticsColumn.str("expected_detail", 2000),
        OpsAnalyticsColumn.str("actual_detail", 2000),
        OpsAnalyticsColumn.str("severity", 16));
  }

  private static List<OpsAnalyticsColumn> harvestFkColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("harvest_run_id", 64, 1),
        OpsAnalyticsColumn.keyStr("subject_key", 512, 2),
        OpsAnalyticsColumn.keyStr("field_role", 16, 3),
        OpsAnalyticsColumn.keyStr("constraint_name", 255, 4),
        OpsAnalyticsColumn.str("child_schema", 255),
        OpsAnalyticsColumn.str("child_table", 255),
        OpsAnalyticsColumn.str("child_columns", 2000),
        OpsAnalyticsColumn.str("parent_schema", 255),
        OpsAnalyticsColumn.str("parent_table", 255),
        OpsAnalyticsColumn.str("parent_columns", 2000));
  }

  private static List<OpsAnalyticsColumn> executionColumns() {
    return List.of(
        OpsAnalyticsColumn.keyStr("id", 100, 1),
        OpsAnalyticsColumn.str("name", 1024),
        OpsAnalyticsColumn.str("execution_type", 32),
        OpsAnalyticsColumn.str("parent_id", 100),
        OpsAnalyticsColumn.date("registration_date"),
        OpsAnalyticsColumn.date("execution_start_date"),
        OpsAnalyticsColumn.date("execution_end_date"),
        OpsAnalyticsColumn.bool("failed"),
        OpsAnalyticsColumn.str("status_description", 128),
        OpsAnalyticsColumn.num("duration_ms"),
        OpsAnalyticsColumn.str("json", 0));
  }
}
