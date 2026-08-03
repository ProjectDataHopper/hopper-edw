/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Loads correlated load-run metrics for one workflow execution id. */
public final class WorkflowLoadOverviewLoader {

  private WorkflowLoadOverviewLoader() {}

  public static WorkflowLoadOverviewReport load(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String workflowExecutionId,
      String rootWorkflowName,
      Date startedAt,
      IVariables variables,
      boolean includePipelineDetail,
      boolean includeInsights,
      int maxPipelinesPerModel)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(workflowExecutionId)) {
      return null;
    }
    String schema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            operationsSchema, databaseMeta);
    LoggingObject loggingObject = new LoggingObject(WorkflowLoadOverviewLoader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
        return null;
      }
      List<LoadRunRow> runs =
          queryLoadRuns(db, databaseMeta, schema, workflowExecutionId, variables);
      if (runs.isEmpty()) {
        return null;
      }
      runs.sort(
          Comparator.comparingInt(WorkflowLoadOverviewLoader::modelTypeOrder)
              .thenComparing(LoadRunRow::finishedAt));

      Map<String, Long> durationByRunId =
          queryDurationByRunId(db, databaseMeta, schema, runs, variables);
      Map<String, Map<String, Long>> pipelineDurationByRunId =
          includePipelineDetail
              ? queryPipelineDurationByRunId(db, databaseMeta, schema, runs, variables)
              : Map.of();
      Map<String, List<WorkflowLoadOverviewReport.PipelineEntry>> pipelinesByRunId =
          includePipelineDetail
              ? queryPipelinesByRunId(
                  db,
                  databaseMeta,
                  schema,
                  runs,
                  variables,
                  maxPipelinesPerModel,
                  pipelineDurationByRunId)
              : Map.of();
      Map<String, List<WorkflowLoadOverviewReport.InsightEntry>> insightsByRunId =
          includeInsights
              ? queryInsightsByRunId(db, databaseMeta, schema, runs, variables)
              : Map.of();

      return assembleReport(
          workflowExecutionId,
          rootWorkflowName,
          startedAt,
          runs,
          durationByRunId,
          pipelinesByRunId,
          insightsByRunId);
    } finally {
      db.disconnect();
    }
  }

  static WorkflowLoadOverviewReport assembleReport(
      String workflowExecutionId,
      String rootWorkflowName,
      Date startedAt,
      List<LoadRunRow> runs,
      Map<String, Long> durationByRunId,
      Map<String, List<WorkflowLoadOverviewReport.PipelineEntry>> pipelinesByRunId,
      Map<String, List<WorkflowLoadOverviewReport.InsightEntry>> insightsByRunId) {
    if (runs == null || runs.isEmpty()) {
      return null;
    }

    List<LoadRunRow> orderedRuns = new ArrayList<>(runs);
    orderedRuns.sort(
        Comparator.comparingInt(WorkflowLoadOverviewLoader::modelTypeOrder)
            .thenComparing(
                LoadRunRow::finishedAt, Comparator.nullsLast(Comparator.naturalOrder())));

    WorkflowLoadOverviewReport.WorkflowLoadOverviewReportBuilder builder =
        WorkflowLoadOverviewReport.builder()
            .overviewId(UUID.randomUUID().toString())
            .workflowExecutionId(workflowExecutionId)
            .rootWorkflowName(rootWorkflowName)
            .startedAt(startedAt);

    String metricsWorkflowName = null;
    Date earliestFinished = null;
    Date latestFinished = null;
    long totalDurationMs = 0;
    int totalPipelines = 0;
    int totalInsights = 0;
    long totalSourceRowsRead = 0;
    long totalTargetRowsInserted = 0;
    long totalErrors = 0;
    boolean allSuccess = true;

    int sequence = 1;
    for (LoadRunRow run : orderedRuns) {
      if (metricsWorkflowName == null && !Utils.isEmpty(run.workflowName())) {
        metricsWorkflowName = run.workflowName();
      }
      if (run.finishedAt() != null) {
        if (earliestFinished == null || run.finishedAt().before(earliestFinished)) {
          earliestFinished = run.finishedAt();
        }
        if (latestFinished == null || run.finishedAt().after(latestFinished)) {
          latestFinished = run.finishedAt();
        }
      }

      List<WorkflowLoadOverviewReport.PipelineEntry> pipelines =
          pipelinesByRunId != null
              ? pipelinesByRunId.getOrDefault(run.runId(), List.of())
              : List.of();
      List<WorkflowLoadOverviewReport.InsightEntry> insights =
          insightsByRunId != null
              ? insightsByRunId.getOrDefault(run.runId(), List.of())
              : List.of();

      long sourceRowsRead = 0;
      long targetRowsRead = 0;
      long targetRowsInserted = 0;
      long errors = 0;
      for (WorkflowLoadOverviewReport.PipelineEntry pipeline : pipelines) {
        sourceRowsRead += pipeline.getSourceRowsRead();
        targetRowsRead += pipeline.getTargetRowsRead();
        targetRowsInserted += pipeline.getTargetRowsInserted();
        errors += pipeline.getErrors();
      }
      if (run.errorCount() != null && run.errorCount() > errors) {
        errors = run.errorCount();
      }

      long durationMs =
          durationByRunId != null ? durationByRunId.getOrDefault(run.runId(), 0L) : 0L;
      boolean success = run.success() == null || run.success();
      if (!success) {
        allSuccess = false;
      }

      totalDurationMs += durationMs;
      totalPipelines += pipelines.size();
      totalInsights += insights.size();
      totalSourceRowsRead += sourceRowsRead;
      totalTargetRowsInserted += targetRowsInserted;
      totalErrors += errors;

      builder.model(
          WorkflowLoadOverviewReport.ModelEntry.builder()
              .sequenceNo(sequence++)
              .loadRunId(run.runId())
              .modelType(run.modelType())
              .modelName(run.modelName())
              .pipelineCount(pipelines.size())
              .sourceRowsRead(sourceRowsRead)
              .targetRowsRead(targetRowsRead)
              .targetRowsInserted(targetRowsInserted)
              .errors(errors)
              .durationMs(durationMs)
              .insightCount(insights.size())
              .success(success)
              .startedAt(resolveModelStartedAt(run.startedAt(), run.finishedAt(), durationMs))
              .finishedAt(run.finishedAt())
              .pipelines(pipelines)
              .insights(insights)
              .build());
    }

    Date effectiveStartedAt = startedAt != null ? startedAt : earliestFinished;
    Date effectiveFinishedAt = latestFinished != null ? latestFinished : new Date();
    return builder
        .metricsWorkflowName(metricsWorkflowName)
        .finishedAt(effectiveFinishedAt)
        .durationMs(
            resolveWorkflowDurationMs(effectiveStartedAt, effectiveFinishedAt, totalDurationMs))
        .modelCount(orderedRuns.size())
        .pipelineCount(totalPipelines)
        .insightCount(totalInsights)
        .totalSourceRowsRead(totalSourceRowsRead)
        .totalTargetRowsInserted(totalTargetRowsInserted)
        .totalErrors(totalErrors)
        .success(allSuccess)
        .startedAt(effectiveStartedAt)
        .build();
  }

  private static List<LoadRunRow> queryLoadRuns(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      String workflowExecutionId,
      IVariables variables)
      throws HopException {
    String loadRunTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    String sql =
        "SELECT run_id, started_at, finished_at, model_type, model_name, workflow_name, success, error_count "
            + "FROM "
            + loadRunTable
            + " WHERE workflow_execution_id = "
            + sqlLiteral(variables, workflowExecutionId)
            + " ORDER BY finished_at";
    List<Object[]> rows = db.getRows(sql, 256);
    IRowMeta rowMeta = db.getReturnRowMeta();
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      return List.of();
    }
    List<LoadRunRow> runs = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      runs.add(
          new LoadRunRow(
              stringValue(rowMeta, row, "run_id"),
              dateValue(rowMeta, row, "started_at"),
              dateValue(rowMeta, row, "finished_at"),
              stringValue(rowMeta, row, "model_type"),
              stringValue(rowMeta, row, "model_name"),
              stringValue(rowMeta, row, "workflow_name"),
              booleanValue(rowMeta, row, "success"),
              longValue(rowMeta, row, "error_count")));
    }
    return runs;
  }

  private static Map<String, Long> queryDurationByRunId(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      List<LoadRunRow> runs,
      IVariables variables)
      throws HopException {
    if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC)) {
      return Map.of();
    }
    String inClause = buildRunIdInClause(variables, runs);
    if (Utils.isEmpty(inClause)) {
      return Map.of();
    }
    String transformTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC);
    String sql =
        "SELECT run_id, SUM(COALESCE(duration_ms, 0)) AS duration_ms FROM "
            + transformTable
            + " WHERE run_id IN ("
            + inClause
            + ") GROUP BY run_id";
    List<Object[]> rows = db.getRows(sql, 256);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, Long> durations = new HashMap<>();
    if (rows == null || rowMeta == null) {
      return durations;
    }
    for (Object[] row : rows) {
      String runId = stringValue(rowMeta, row, "run_id");
      Long duration = longValue(rowMeta, row, "duration_ms");
      if (!Utils.isEmpty(runId) && duration != null) {
        durations.put(runId, duration);
      }
    }
    return durations;
  }

  /**
   * Pipeline wall-clock durations: prefer {@code load_pipeline_metric.duration_ms} when present
   * (issue #87). Fall back to summed transform durations for older OPS rows without the column.
   */
  private static Map<String, Map<String, Long>> queryPipelineDurationByRunId(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      List<LoadRunRow> runs,
      IVariables variables)
      throws HopException {
    String inClause = buildRunIdInClause(variables, runs);
    if (Utils.isEmpty(inClause)) {
      return Map.of();
    }
    Map<String, Map<String, Long>> durationsByRunId = new HashMap<>();
    if (db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC)
        && db.checkColumnExists(
            schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC, "duration_ms")) {
      String pipelineTable =
          databaseMeta.getQuotedSchemaTableCombination(
              variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC);
      String sql =
          "SELECT run_id, pipeline_name, duration_ms FROM "
              + pipelineTable
              + " WHERE run_id IN ("
              + inClause
              + ")";
      putPipelineDurations(db.getRows(sql, 1024), db.getReturnRowMeta(), durationsByRunId, false);
    }
    if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC)) {
      return durationsByRunId;
    }
    String transformTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC);
    String fallbackSql =
        "SELECT run_id, pipeline_name, SUM(COALESCE(duration_ms, 0)) AS duration_ms FROM "
            + transformTable
            + " WHERE run_id IN ("
            + inClause
            + ") GROUP BY run_id, pipeline_name";
    // Only fill gaps (missing pipeline or null/zero pipeline duration with transform evidence).
    putPipelineDurations(
        db.getRows(fallbackSql, 1024), db.getReturnRowMeta(), durationsByRunId, true);
    return durationsByRunId;
  }

  private static void putPipelineDurations(
      List<Object[]> rows,
      IRowMeta rowMeta,
      Map<String, Map<String, Long>> durationsByRunId,
      boolean onlyIfAbsent)
      throws HopException {
    if (rows == null || rowMeta == null) {
      return;
    }
    for (Object[] row : rows) {
      String runId = stringValue(rowMeta, row, "run_id");
      String pipelineName = stringValue(rowMeta, row, "pipeline_name");
      Long duration = longValue(rowMeta, row, "duration_ms");
      if (Utils.isEmpty(runId) || Utils.isEmpty(pipelineName) || duration == null) {
        continue;
      }
      Map<String, Long> byPipeline = durationsByRunId.computeIfAbsent(runId, id -> new HashMap<>());
      if (onlyIfAbsent && byPipeline.containsKey(pipelineName)) {
        continue;
      }
      byPipeline.put(pipelineName, duration);
    }
  }

  private static Map<String, List<WorkflowLoadOverviewReport.PipelineEntry>> queryPipelinesByRunId(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      List<LoadRunRow> runs,
      IVariables variables,
      int maxPipelinesPerModel,
      Map<String, Map<String, Long>> pipelineDurationByRunId)
      throws HopException {
    if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC)) {
      return Map.of();
    }
    String inClause = buildRunIdInClause(variables, runs);
    if (Utils.isEmpty(inClause)) {
      return Map.of();
    }
    String pipelineTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC);
    String sql =
        "SELECT run_id, pipeline_name, element_type, element_name, source_name, source_rows_read, target_rows_read, target_rows_inserted, errors "
            + "FROM "
            + pipelineTable
            + " WHERE run_id IN ("
            + inClause
            + ") ORDER BY run_id, target_rows_inserted DESC, source_rows_read DESC";
    List<Object[]> rows =
        db.getRows(sql, Math.max(256, runs.size() * Math.max(1, maxPipelinesPerModel)));
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, List<WorkflowLoadOverviewReport.PipelineEntry>> pipelinesByRunId = new HashMap<>();
    if (rows == null || rowMeta == null) {
      return pipelinesByRunId;
    }
    Map<String, Integer> counts = new HashMap<>();
    for (Object[] row : rows) {
      String runId = stringValue(rowMeta, row, "run_id");
      if (Utils.isEmpty(runId)) {
        continue;
      }
      int count = counts.getOrDefault(runId, 0);
      if (count >= maxPipelinesPerModel) {
        continue;
      }
      counts.put(runId, count + 1);
      String pipelineName = stringValue(rowMeta, row, "pipeline_name");
      long pipelineDurationMs =
          pipelineDurationByRunId != null
              ? pipelineDurationByRunId.getOrDefault(runId, Map.of()).getOrDefault(pipelineName, 0L)
              : 0L;
      pipelinesByRunId
          .computeIfAbsent(runId, id -> new ArrayList<>())
          .add(
              WorkflowLoadOverviewReport.PipelineEntry.builder()
                  .pipelineName(pipelineName)
                  .elementType(stringValue(rowMeta, row, "element_type"))
                  .elementName(stringValue(rowMeta, row, "element_name"))
                  .sourceName(stringValue(rowMeta, row, "source_name"))
                  .sourceRowsRead(nullToZero(longValue(rowMeta, row, "source_rows_read")))
                  .targetRowsRead(nullToZero(longValue(rowMeta, row, "target_rows_read")))
                  .targetRowsInserted(nullToZero(longValue(rowMeta, row, "target_rows_inserted")))
                  .errors(nullToZero(longValue(rowMeta, row, "errors")))
                  .durationMs(pipelineDurationMs)
                  .build());
    }
    return pipelinesByRunId;
  }

  private static Map<String, List<WorkflowLoadOverviewReport.InsightEntry>> queryInsightsByRunId(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      List<LoadRunRow> runs,
      IVariables variables)
      throws HopException {
    if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT)) {
      return Map.of();
    }
    String inClause = buildRunIdInClause(variables, runs);
    if (Utils.isEmpty(inClause)) {
      return Map.of();
    }
    String insightTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT);
    String sql =
        "SELECT run_id, severity, code, message, element_name, related_element_name "
            + "FROM "
            + insightTable
            + " WHERE run_id IN ("
            + inClause
            + ")"
            + LoadRunInsightSupport.sqlExcludeNoteSeverityClause()
            + " ORDER BY run_id, insight_seq";
    List<Object[]> rows = db.getRows(sql, 1024);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, List<WorkflowLoadOverviewReport.InsightEntry>> insightsByRunId = new HashMap<>();
    if (rows == null || rowMeta == null) {
      return insightsByRunId;
    }
    for (Object[] row : rows) {
      String runId = stringValue(rowMeta, row, "run_id");
      if (Utils.isEmpty(runId)) {
        continue;
      }
      insightsByRunId
          .computeIfAbsent(runId, id -> new ArrayList<>())
          .add(
              WorkflowLoadOverviewReport.InsightEntry.builder()
                  .severity(stringValue(rowMeta, row, "severity"))
                  .code(stringValue(rowMeta, row, "code"))
                  .message(stringValue(rowMeta, row, "message"))
                  .elementName(stringValue(rowMeta, row, "element_name"))
                  .relatedElementName(stringValue(rowMeta, row, "related_element_name"))
                  .build());
    }
    return insightsByRunId;
  }

  private static String buildRunIdInClause(IVariables variables, List<LoadRunRow> runs) {
    if (runs == null || runs.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (LoadRunRow run : runs) {
      if (Utils.isEmpty(run.runId())) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(',');
      }
      builder.append(sqlLiteral(variables, run.runId()));
    }
    return builder.toString();
  }

  private static Date resolveModelStartedAt(Date startedAt, Date finishedAt, long durationMs) {
    if (startedAt != null
        && finishedAt != null
        && startedAt.before(finishedAt)
        && !sameInstant(startedAt, finishedAt)) {
      return startedAt;
    }
    if (finishedAt != null && durationMs > 0) {
      return new Date(finishedAt.getTime() - durationMs);
    }
    return startedAt;
  }

  private static long resolveWorkflowDurationMs(
      Date startedAt, Date finishedAt, long summedTransformDurationMs) {
    if (startedAt != null && finishedAt != null && finishedAt.after(startedAt)) {
      return Math.max(0L, finishedAt.getTime() - startedAt.getTime());
    }
    return Math.max(0L, summedTransformDurationMs);
  }

  private static boolean sameInstant(Date left, Date right) {
    return left != null && right != null && left.getTime() == right.getTime();
  }

  static int modelTypeOrder(LoadRunRow run) {
    if (run == null || Utils.isEmpty(run.modelType())) {
      return 99;
    }
    return switch (run.modelType().toLowerCase()) {
      case "dv" -> 0;
      case "bv" -> 1;
      case "dm" -> 2;
      default -> 3;
    };
  }

  private static String sqlLiteral(IVariables variables, String value) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (resolved == null) {
      return "NULL";
    }
    return "'" + resolved.replace("'", "''") + "'";
  }

  private static long nullToZero(Long value) {
    return value != null ? value : 0L;
  }

  private static String stringValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length || row[index] == null) {
      return "";
    }
    return row[index].toString();
  }

  private static Date dateValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length) {
      return null;
    }
    Object value = row[index];
    return value instanceof Date date ? date : null;
  }

  private static Boolean booleanValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length || row[index] == null) {
      return null;
    }
    Object value = row[index];
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof Number number) {
      return number.longValue() != 0L;
    }
    return Boolean.parseBoolean(value.toString());
  }

  private static Long longValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length || row[index] == null) {
      return null;
    }
    Object value = row[index];
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  record LoadRunRow(
      String runId,
      Date startedAt,
      Date finishedAt,
      String modelType,
      String modelName,
      String workflowName,
      Boolean success,
      Long errorCount) {}
}
