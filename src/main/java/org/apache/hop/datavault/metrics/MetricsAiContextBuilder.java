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
package org.apache.hop.datavault.metrics;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiContextBuilder;
import org.apache.hop.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Loads recent load-run metrics and insights for AI performance advisory context. */
public final class MetricsAiContextBuilder {

  private static final int MAX_RUNS = 3;
  private static final int MAX_INSIGHTS_PER_RUN = 15;
  private static final int MAX_PIPELINES_PER_RUN = 20;
  private static final int MAX_TRANSFORMS_PER_RUN = 12;

  private static final Pattern PERFORMANCE_QUESTION =
      Pattern.compile(
          "\\b(performance|tuning|tune|slow|faster|optimize|optimi[sz]e|memory|sort|bulk|preload|"
              + "lookup|throughput|bottleneck|capacity|rows\\s+read|target\\s+read)\\b",
          Pattern.CASE_INSENSITIVE);

  private MetricsAiContextBuilder() {}

  public static boolean isPerformanceRelatedQuestion(String userPrompt) {
    return !Utils.isEmpty(userPrompt) && PERFORMANCE_QUESTION.matcher(userPrompt).find();
  }

  public static boolean shouldIncludeMetrics(
      boolean includeLoadRunMetrics, boolean performanceTuningScenario, String userPrompt) {
    if (includeLoadRunMetrics || performanceTuningScenario) {
      return true;
    }
    return isPerformanceRelatedQuestion(userPrompt);
  }

  public static String resolveMetricsDatabaseName(
      IHopMetadataProvider metadataProvider, IVariables variables) {
    if (metadataProvider == null) {
      return null;
    }
    try {
      List<String> profileNames =
          metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).listObjectNames();
      if (profileNames == null || profileNames.isEmpty()) {
        return null;
      }
      for (String profileName : profileNames) {
        ExecutionMetricsProfileMeta profile =
            metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).load(profileName);
        if (profile == null || !profile.isEnabled()) {
          continue;
        }
        String databaseName = resolveValue(profile.getTargetDatabaseConnection(), variables);
        if (!Utils.isEmpty(databaseName)) {
          return databaseName;
        }
      }
    } catch (Exception ignored) {
      // Metrics context is optional for AI advisory
    }
    return null;
  }

  public static String resolveOperationsSchema(
      IHopMetadataProvider metadataProvider, IVariables variables) {
    if (metadataProvider == null) {
      return LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME;
    }
    try {
      List<String> profileNames =
          metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).listObjectNames();
      if (profileNames == null) {
        return LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME;
      }
      for (String profileName : profileNames) {
        ExecutionMetricsProfileMeta profile =
            metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).load(profileName);
        if (profile == null || !profile.isEnabled()) {
          continue;
        }
        if (!Utils.isEmpty(profile.getTargetDatabaseConnection())) {
          return profile.getOperationsSchemaOrDefault();
        }
      }
    } catch (Exception ignored) {
      // Fall back to default schema
    }
    return LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME;
  }

  public static String buildMetricsContextJson(
      String modelName,
      String modelType,
      String metricsDatabaseName,
      String operationsSchema,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (metadataProvider == null
        || Utils.isEmpty(modelName)
        || Utils.isEmpty(metricsDatabaseName)) {
      return "";
    }
    try {
      String resolvedDatabase = resolveValue(metricsDatabaseName, variables);
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(resolvedDatabase);
      if (databaseMeta == null) {
        return "";
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              operationsSchema, databaseMeta);
      return queryMetricsJson(databaseMeta, schema, modelName, modelType, variables);
    } catch (Exception ignored) {
      return "";
    }
  }

  public static String buildMetricsContextForPrompt(
      String userPrompt,
      String modelName,
      String modelType,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    return buildMetricsContext(
        false, false, userPrompt, modelName, modelType, metadataProvider, variables);
  }

  public static String buildMetricsContext(
      boolean includeLoadRunMetrics,
      boolean performanceTuningScenario,
      String userPrompt,
      String modelName,
      String modelType,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (!shouldIncludeMetrics(includeLoadRunMetrics, performanceTuningScenario, userPrompt)) {
      return "";
    }
    String databaseName = resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName)) {
      return "";
    }
    String schema = resolveOperationsSchema(metadataProvider, variables);
    return buildMetricsContextJson(
        modelName, modelType, databaseName, schema, metadataProvider, variables);
  }

  private static String queryMetricsJson(
      DatabaseMeta databaseMeta,
      String schema,
      String modelName,
      String modelType,
      IVariables variables)
      throws HopException {
    LoggingObject loggingObject = new LoggingObject(MetricsAiContextBuilder.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
        return "";
      }

      String loadRunTable =
          databaseMeta.getQuotedSchemaTableCombination(
              variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
      String modelNameLiteral = sqlLiteral(variables, modelName);
      String modelTypeLiteral = sqlLiteral(variables, modelType);
      String runSql =
          "SELECT run_id, finished_at, success, error_count, workflow_name "
              + "FROM "
              + loadRunTable
              + " WHERE model_name = "
              + modelNameLiteral
              + " AND model_type = "
              + modelTypeLiteral
              + " ORDER BY finished_at DESC";

      db.setQueryLimit(MAX_RUNS);
      List<Object[]> runRows = db.getRows(runSql, MAX_RUNS);
      IRowMeta runMeta = db.getReturnRowMeta();
      if (runRows == null || runRows.isEmpty() || runMeta == null) {
        return "{\"runs\":[]}";
      }

      StringBuilder json = new StringBuilder();
      json.append("{\"modelName\":").append(DvAiContextBuilder.jsonString(modelName));
      json.append(",\"modelType\":").append(DvAiContextBuilder.jsonString(modelType));
      json.append(",\"insightRuleCatalog\":").append(LoadRunInsightRuleCatalog.toJson());
      json.append(",\"runs\":[");
      for (int i = 0; i < runRows.size(); i++) {
        if (i > 0) {
          json.append(',');
        }
        appendRunJson(json, runMeta, runRows.get(i), databaseMeta, schema, variables, db);
      }
      json.append("]}");
      return json.toString();
    } finally {
      db.disconnect();
    }
  }

  private static void appendRunJson(
      StringBuilder json,
      IRowMeta runMeta,
      Object[] runRow,
      DatabaseMeta databaseMeta,
      String schema,
      IVariables variables,
      Database db)
      throws HopException {
    String runId = stringValue(runMeta, runRow, "run_id");
    Date finishedAt = dateValue(runMeta, runRow, "finished_at");
    Boolean success = booleanValue(runMeta, runRow, "success");
    Long errorCount = longValue(runMeta, runRow, "error_count");
    String workflowName = stringValue(runMeta, runRow, "workflow_name");

    json.append("{\"runId\":").append(DvAiContextBuilder.jsonString(runId));
    json.append(",\"finishedAt\":").append(DvAiContextBuilder.jsonString(formatDate(finishedAt)));
    json.append(",\"success\":").append(success != null && success);
    json.append(",\"errorCount\":").append(errorCount != null ? errorCount : 0L);
    json.append(",\"workflowName\":").append(DvAiContextBuilder.jsonString(workflowName));
    json.append(",\"insights\":");
    appendInsightsJson(json, databaseMeta, schema, runId, variables, db);
    json.append(",\"pipelines\":");
    appendPipelineMetricsJson(json, databaseMeta, schema, runId, variables, db);
    json.append(",\"topTransforms\":");
    appendTopTransformMetricsJson(json, databaseMeta, schema, runId, variables, db);
    json.append('}');
  }

  private static void appendPipelineMetricsJson(
      StringBuilder json,
      DatabaseMeta databaseMeta,
      String schema,
      String runId,
      IVariables variables,
      Database db)
      throws HopException {
    if (Utils.isEmpty(runId)
        || !db.checkTableExists(
            schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC)) {
      json.append("[]");
      return;
    }
    String pipelineTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_PIPELINE_METRIC);
    String sql =
        "SELECT pipeline_name, element_type, element_name, source_rows_read, target_rows_read, target_rows_inserted, errors "
            + "FROM "
            + pipelineTable
            + " WHERE run_id = "
            + sqlLiteral(variables, runId)
            + " ORDER BY target_rows_inserted DESC, source_rows_read DESC";
    db.setQueryLimit(MAX_PIPELINES_PER_RUN);
    appendTabularJson(
        json,
        db.getRows(sql, MAX_PIPELINES_PER_RUN),
        db.getReturnRowMeta(),
        new String[] {
          "pipelineName",
          "elementType",
          "elementName",
          "sourceRowsRead",
          "targetRowsRead",
          "targetRowsInserted",
          "errors"
        },
        new String[] {
          "pipeline_name",
          "element_type",
          "element_name",
          "source_rows_read",
          "target_rows_read",
          "target_rows_inserted",
          "errors"
        });
  }

  private static void appendTopTransformMetricsJson(
      StringBuilder json,
      DatabaseMeta databaseMeta,
      String schema,
      String runId,
      IVariables variables,
      Database db)
      throws HopException {
    if (Utils.isEmpty(runId)
        || !db.checkTableExists(
            schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC)) {
      json.append("[]");
      return;
    }
    String transformTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC);
    String sql =
        "SELECT pipeline_name, transform_name, logical_role, element_name, rows_read, duration_ms, errors "
            + "FROM "
            + transformTable
            + " WHERE run_id = "
            + sqlLiteral(variables, runId)
            + " ORDER BY duration_ms DESC NULLS LAST, rows_read DESC";
    db.setQueryLimit(MAX_TRANSFORMS_PER_RUN);
    appendTabularJson(
        json,
        db.getRows(sql, MAX_TRANSFORMS_PER_RUN),
        db.getReturnRowMeta(),
        new String[] {
          "pipelineName",
          "transformName",
          "logicalRole",
          "elementName",
          "rowsRead",
          "durationMs",
          "errors"
        },
        new String[] {
          "pipeline_name",
          "transform_name",
          "logical_role",
          "element_name",
          "rows_read",
          "duration_ms",
          "errors"
        });
  }

  private static void appendTabularJson(
      StringBuilder json,
      List<Object[]> rows,
      IRowMeta rowMeta,
      String[] jsonFields,
      String[] dbFields)
      throws HopException {
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      json.append("[]");
      return;
    }
    json.append('[');
    for (int r = 0; r < rows.size(); r++) {
      if (r > 0) {
        json.append(',');
      }
      Object[] row = rows.get(r);
      json.append('{');
      for (int f = 0; f < jsonFields.length; f++) {
        if (f > 0) {
          json.append(',');
        }
        String dbField = dbFields[f];
        json.append('"').append(jsonFields[f]).append("\":");
        if (isNumericField(dbField)) {
          Long value = longValue(rowMeta, row, dbField);
          json.append(value != null ? value : 0L);
        } else {
          json.append(DvAiContextBuilder.jsonString(stringValue(rowMeta, row, dbField)));
        }
      }
      json.append('}');
    }
    json.append(']');
  }

  private static boolean isNumericField(String fieldName) {
    return fieldName.endsWith("_read")
        || fieldName.endsWith("_inserted")
        || fieldName.endsWith("_ms")
        || "errors".equals(fieldName)
        || fieldName.startsWith("rows_");
  }

  private static void appendInsightsJson(
      StringBuilder json,
      DatabaseMeta databaseMeta,
      String schema,
      String runId,
      IVariables variables,
      Database db)
      throws HopException {
    if (Utils.isEmpty(runId)
        || !db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT)) {
      json.append("[]");
      return;
    }

    String insightTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT);
    String runIdLiteral = sqlLiteral(variables, runId);
    String insightSql =
        "SELECT severity, code, message, element_name, related_element_name, metric_json "
            + "FROM "
            + insightTable
            + " WHERE run_id = "
            + runIdLiteral
            + LoadRunInsightSupport.sqlExcludeNoteSeverityClause()
            + " ORDER BY insight_seq";

    db.setQueryLimit(MAX_INSIGHTS_PER_RUN);
    List<Object[]> insightRows = db.getRows(insightSql, MAX_INSIGHTS_PER_RUN);
    IRowMeta insightMeta = db.getReturnRowMeta();
    if (insightRows == null || insightRows.isEmpty() || insightMeta == null) {
      json.append("[]");
      return;
    }

    json.append('[');
    for (int i = 0; i < insightRows.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      Object[] row = insightRows.get(i);
      json.append("{\"severity\":")
          .append(DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "severity")));
      json.append(",\"code\":")
          .append(DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "code")));
      json.append(",\"message\":")
          .append(DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "message")));
      json.append(",\"elementName\":")
          .append(DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "element_name")));
      json.append(",\"relatedElementName\":")
          .append(
              DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "related_element_name")));
      json.append(",\"metricJson\":")
          .append(DvAiContextBuilder.jsonString(stringValue(insightMeta, row, "metric_json")));
      json.append('}');
    }
    json.append(']');
  }

  private static String resolveValue(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
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

  private static String formatDate(Date value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.ROOT, "%tFT%<tT", value);
  }
}
