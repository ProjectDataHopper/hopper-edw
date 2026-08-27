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
package org.hopper.edw.datavault.metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.execution.Execution;
import org.apache.hop.execution.ExecutionState;
import org.apache.hop.execution.ExecutionStateComponentMetrics;
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.execution.IExecutionInfoLocation;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;

/** Loads recent execution logs and transform metrics from Hop Execution Information Locations. */
public final class ExecutionInfoAiContextBuilder {

  private static final int MAX_PIPELINES = 25;
  private static final int MAX_TRANSFORMS_PER_PIPELINE = 30;
  private static final int MAX_LOG_EXCERPT_CHARS = 4000;

  private ExecutionInfoAiContextBuilder() {}

  public static boolean shouldIncludeExecutionInfo(
      boolean includeExecutionInfo, boolean performanceTuningScenario, String userPrompt) {
    if (includeExecutionInfo || performanceTuningScenario) {
      return true;
    }
    return MetricsAiContextBuilder.isPerformanceRelatedQuestion(userPrompt);
  }

  public static String buildExecutionInfoContext(
      boolean includeExecutionInfo,
      boolean performanceTuningScenario,
      String userPrompt,
      String modelName,
      String modelType,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (!shouldIncludeExecutionInfo(includeExecutionInfo, performanceTuningScenario, userPrompt)) {
      return "";
    }
    String databaseName =
        MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName)) {
      return "";
    }
    String schema = MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables);
    try {
      LatestLoadRun latestRun =
          queryLatestLoadRun(
              databaseName, schema, modelName, modelType, variables, metadataProvider);
      if (latestRun == null
          || Utils.isEmpty(latestRun.logChannelId())
          || Utils.isEmpty(latestRun.pipelineRunConfiguration())) {
        return "";
      }
      return buildFromExecutionInfoLocation(latestRun, variables, metadataProvider);
    } catch (Exception ignored) {
      return "";
    }
  }

  private record LatestLoadRun(
      String runId,
      String logChannelId,
      String pipelineRunConfiguration,
      Date finishedAt,
      boolean success) {}

  private static LatestLoadRun queryLatestLoadRun(
      String metricsDatabaseName,
      String operationsSchema,
      String modelName,
      String modelType,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(metricsDatabaseName);
    if (databaseMeta == null) {
      return null;
    }
    String schema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            operationsSchema, databaseMeta);
    LoggingObject loggingObject = new LoggingObject(ExecutionInfoAiContextBuilder.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
        return null;
      }
      if (!db.checkColumnExists(
          schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN, "pipeline_run_configuration")) {
        return null;
      }

      String loadRunTable =
          databaseMeta.getQuotedSchemaTableCombination(
              variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
      String sql =
          "SELECT run_id, log_channel_id, pipeline_run_configuration, finished_at, success FROM "
              + loadRunTable
              + " WHERE model_name = "
              + sqlLiteral(variables, modelName)
              + " AND model_type = "
              + sqlLiteral(variables, modelType)
              + " ORDER BY finished_at DESC";

      db.setQueryLimit(1);
      List<Object[]> rows = db.getRows(sql, 1);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return null;
      }
      Object[] row = rows.getFirst();
      return new LatestLoadRun(
          stringValue(rowMeta, row, "run_id"),
          stringValue(rowMeta, row, "log_channel_id"),
          stringValue(rowMeta, row, "pipeline_run_configuration"),
          dateValue(rowMeta, row, "finished_at"),
          Boolean.TRUE.equals(booleanValue(rowMeta, row, "success")));
    } finally {
      db.disconnect();
    }
  }

  private static String buildFromExecutionInfoLocation(
      LatestLoadRun latestRun, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    ExecutionInfoLocationSupport.ResolvedExecutionInfoLocation resolved =
        ExecutionInfoLocationSupport.resolveInitializedLocation(
            latestRun.pipelineRunConfiguration(), variables, metadataProvider);
    if (resolved == null || resolved.location() == null) {
      return "";
    }

    IExecutionInfoLocation location = resolved.location();
    try {
      List<PipelineExecutionSnapshot> pipelines =
          collectPipelineSnapshots(location, latestRun.logChannelId());
      if (pipelines.isEmpty()) {
        return "";
      }

      StringBuilder json = new StringBuilder();
      json.append('{');
      json.append("\"runId\":").append(DvAiContextBuilder.jsonString(latestRun.runId()));
      json.append(",\"rootLogChannelId\":")
          .append(DvAiContextBuilder.jsonString(latestRun.logChannelId()));
      json.append(",\"pipelineRunConfiguration\":")
          .append(DvAiContextBuilder.jsonString(resolved.pipelineRunConfiguration()));
      json.append(",\"executionInfoLocation\":")
          .append(DvAiContextBuilder.jsonString(resolved.executionInfoLocationName()));
      json.append(",\"finishedAt\":")
          .append(DvAiContextBuilder.jsonString(formatDate(latestRun.finishedAt())));
      json.append(",\"success\":").append(latestRun.success());
      json.append(",\"pipelines\":[");
      for (int i = 0; i < pipelines.size(); i++) {
        if (i > 0) {
          json.append(',');
        }
        appendPipelineSnapshot(json, pipelines.get(i));
      }
      json.append("]}");
      return json.toString();
    } finally {
      try {
        location.close();
      } catch (Exception ignored) {
        // Best-effort cleanup
      }
    }
  }

  private record PipelineExecutionSnapshot(
      String logChannelId,
      String name,
      boolean failed,
      String logExcerpt,
      List<TransformSnapshot> transforms) {}

  private record TransformSnapshot(String name, String copyNr, Map<String, Long> metrics) {}

  private static List<PipelineExecutionSnapshot> collectPipelineSnapshots(
      IExecutionInfoLocation location, String rootLogChannelId) throws HopException {
    List<PipelineExecutionSnapshot> snapshots = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(rootLogChannelId);

    while (!queue.isEmpty() && snapshots.size() < MAX_PIPELINES) {
      String executionId = queue.removeFirst();
      if (!visited.add(executionId)) {
        continue;
      }

      Execution execution = location.getExecution(executionId);
      if (execution != null && execution.getExecutionType() == ExecutionType.Pipeline) {
        snapshots.add(buildPipelineSnapshot(location, execution));
      }

      List<Execution> children = location.findExecutions(executionId);
      if (children != null) {
        for (Execution child : children) {
          if (child != null && !Utils.isEmpty(child.getId())) {
            queue.addLast(child.getId());
          }
        }
      }
    }
    return snapshots;
  }

  private static PipelineExecutionSnapshot buildPipelineSnapshot(
      IExecutionInfoLocation location, Execution execution) throws HopException {
    String executionId = execution.getId();
    ExecutionState state = location.getExecutionState(executionId, false);
    boolean failed = state != null && state.isFailed();
    String logExcerpt = loadLogExcerpt(location, executionId, failed);
    List<TransformSnapshot> transforms = collectTransformSnapshots(state);
    return new PipelineExecutionSnapshot(
        executionId, execution.getName(), failed, logExcerpt, transforms);
  }

  private static String loadLogExcerpt(
      IExecutionInfoLocation location, String executionId, boolean failed) throws HopException {
    String loggingText = location.getExecutionStateLoggingText(executionId, MAX_LOG_EXCERPT_CHARS);
    if (Utils.isEmpty(loggingText)) {
      return "";
    }
    if (loggingText.length() <= MAX_LOG_EXCERPT_CHARS) {
      return loggingText;
    }
    if (failed) {
      return loggingText.substring(loggingText.length() - MAX_LOG_EXCERPT_CHARS);
    }
    return loggingText.substring(0, MAX_LOG_EXCERPT_CHARS);
  }

  private static List<TransformSnapshot> collectTransformSnapshots(ExecutionState state) {
    if (state == null || state.getMetrics() == null || state.getMetrics().isEmpty()) {
      return List.of();
    }
    List<TransformSnapshot> transforms = new ArrayList<>();
    for (ExecutionStateComponentMetrics componentMetrics : state.getMetrics()) {
      if (componentMetrics == null || Utils.isEmpty(componentMetrics.getComponentName())) {
        continue;
      }
      transforms.add(
          new TransformSnapshot(
              componentMetrics.getComponentName(),
              componentMetrics.getComponentCopy(),
              componentMetrics.getMetrics()));
      if (transforms.size() >= MAX_TRANSFORMS_PER_PIPELINE) {
        break;
      }
    }
    return transforms;
  }

  private static void appendPipelineSnapshot(
      StringBuilder json, PipelineExecutionSnapshot pipeline) {
    json.append("{\"logChannelId\":")
        .append(DvAiContextBuilder.jsonString(pipeline.logChannelId()));
    json.append(",\"name\":").append(DvAiContextBuilder.jsonString(pipeline.name()));
    json.append(",\"failed\":").append(pipeline.failed());
    json.append(",\"logExcerpt\":").append(DvAiContextBuilder.jsonString(pipeline.logExcerpt()));
    json.append(",\"transforms\":[");
    for (int i = 0; i < pipeline.transforms().size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      appendTransformSnapshot(json, pipeline.transforms().get(i));
    }
    json.append("]}");
  }

  private static void appendTransformSnapshot(StringBuilder json, TransformSnapshot transform) {
    json.append("{\"name\":").append(DvAiContextBuilder.jsonString(transform.name()));
    json.append(",\"copyNr\":").append(DvAiContextBuilder.jsonString(transform.copyNr()));
    json.append(",\"metrics\":{");
    boolean first = true;
    if (transform.metrics() != null) {
      for (Map.Entry<String, Long> entry : transform.metrics().entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        if (!isUsefulMetric(entry.getKey())) {
          continue;
        }
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append('"')
            .append(escapeJsonField(entry.getKey()))
            .append("\":")
            .append(entry.getValue());
      }
    }
    json.append("}}");
  }

  private static boolean isUsefulMetric(String metricName) {
    return Pipeline.METRIC_NAME_READ.equals(metricName)
        || Pipeline.METRIC_NAME_WRITTEN.equals(metricName)
        || Pipeline.METRIC_NAME_OUTPUT.equals(metricName)
        || Pipeline.METRIC_NAME_UPDATED.equals(metricName)
        || Pipeline.METRIC_NAME_REJECTED.equals(metricName)
        || Pipeline.METRIC_NAME_ERROR.equals(metricName)
        || Pipeline.METRIC_NAME_INPUT.equals(metricName)
        || "duration".equals(metricName)
        || metricName.endsWith("_ms");
  }

  private static String escapeJsonField(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String sqlLiteral(IVariables variables, String value) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (resolved == null) {
      return "NULL";
    }
    return "'" + resolved.replace("'", "''") + "'";
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

  private static String formatDate(Date value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.ROOT, "%tFT%<tT", value);
  }
}
