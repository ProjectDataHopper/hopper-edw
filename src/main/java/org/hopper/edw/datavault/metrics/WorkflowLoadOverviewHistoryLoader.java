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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Lists recent workflow load overviews from OPS for the group-update metrics dialog. */
public final class WorkflowLoadOverviewHistoryLoader {

  public static final int DEFAULT_LIMIT = 20;
  public static final int QUERY_LIMIT = 50;

  private WorkflowLoadOverviewHistoryLoader() {}

  public record OverviewHistoryRow(
      String overviewId,
      String workflowExecutionId,
      String rootWorkflowName,
      String metricsWorkflowName,
      Date finishedAt,
      Long durationMs,
      Long modelCount,
      Long pipelineCount,
      Long totalSourceRowsRead,
      Long totalTargetRowsInserted,
      Long totalErrors,
      Boolean success) {}

  public record HistoryList(
      List<OverviewHistoryRow> rows, boolean workflowFilterMissed, String emptyReason) {
    public HistoryList {
      rows = rows != null ? List.copyOf(rows) : List.of();
    }

    public static HistoryList empty(String reason) {
      return new HistoryList(List.of(), false, reason);
    }
  }

  public static WorkflowLoadOverviewReport loadDetail(
      IHopMetadataProvider metadataProvider, IVariables variables, OverviewHistoryRow row) {
    if (row == null || Utils.isEmpty(row.workflowExecutionId()) || metadataProvider == null) {
      return null;
    }
    String databaseName =
        MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName)) {
      return null;
    }
    try {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(databaseName));
      if (databaseMeta == null) {
        return null;
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables),
              databaseMeta);
      return WorkflowLoadOverviewLoader.load(
          databaseMeta,
          schema,
          row.workflowExecutionId(),
          row.rootWorkflowName(),
          null,
          variables,
          true,
          true,
          WorkflowLoadOverviewReport.DEFAULT_MAX_PIPELINES_PER_MODEL);
    } catch (Exception e) {
      return null;
    }
  }

  public static HistoryList listRecent(
      IHopMetadataProvider metadataProvider, IVariables variables, String workflowName) {
    String databaseName =
        MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName)) {
      return HistoryList.empty("not-configured");
    }
    try {
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(databaseName));
      if (databaseMeta == null) {
        return HistoryList.empty("not-configured");
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables),
              databaseMeta);
      LoggingObject loggingObject = new LoggingObject(WorkflowLoadOverviewHistoryLoader.class);
      Database db = new Database(loggingObject, variables, databaseMeta);
      db.connect();
      try {
        if (!db.checkTableExists(
            schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW)) {
          return HistoryList.empty("tables-missing");
        }
        String overviewTable =
            databaseMeta.getQuotedSchemaTableCombination(
                db, schema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW);
        String sql =
            "SELECT overview_id, workflow_execution_id, root_workflow_name, metrics_workflow_name,"
                + " finished_at, duration_ms, model_count, pipeline_count, total_source_rows_read,"
                + " total_target_rows_inserted, total_errors, success FROM "
                + overviewTable
                + " ORDER BY finished_at DESC";
        db.setQueryLimit(QUERY_LIMIT);
        List<Object[]> rows = db.getRows(sql, QUERY_LIMIT);
        IRowMeta rowMeta = db.getReturnRowMeta();
        List<OverviewHistoryRow> parsed = parseRows(rowMeta, rows);
        return assembleList(parsed, workflowName, DEFAULT_LIMIT);
      } finally {
        db.disconnect();
      }
    } catch (Exception e) {
      return HistoryList.empty("load-failed");
    }
  }

  static HistoryList assembleList(List<OverviewHistoryRow> rows, String workflowName, int limit) {
    int cap = Math.max(1, limit);
    if (rows == null || rows.isEmpty()) {
      return HistoryList.empty("no-rows");
    }
    if (Utils.isEmpty(workflowName)) {
      return new HistoryList(first(rows, cap), false, null);
    }
    List<OverviewHistoryRow> matching = new ArrayList<>();
    for (OverviewHistoryRow row : rows) {
      if (row == null) {
        continue;
      }
      if (workflowName.equalsIgnoreCase(row.rootWorkflowName())
          || workflowName.equalsIgnoreCase(row.metricsWorkflowName())) {
        matching.add(row);
        if (matching.size() >= cap) {
          break;
        }
      }
    }
    if (!matching.isEmpty()) {
      return new HistoryList(matching, false, null);
    }
    return new HistoryList(first(rows, cap), true, null);
  }

  static List<OverviewHistoryRow> parseRows(IRowMeta rowMeta, List<Object[]> rows) {
    if (rowMeta == null || rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<OverviewHistoryRow> parsed = new ArrayList<>();
    for (Object[] row : rows) {
      if (row == null) {
        continue;
      }
      parsed.add(
          new OverviewHistoryRow(
              stringVal(rowMeta, row, "overview_id"),
              stringVal(rowMeta, row, "workflow_execution_id"),
              stringVal(rowMeta, row, "root_workflow_name"),
              stringVal(rowMeta, row, "metrics_workflow_name"),
              dateVal(rowMeta, row, "finished_at"),
              longVal(rowMeta, row, "duration_ms"),
              longVal(rowMeta, row, "model_count"),
              longVal(rowMeta, row, "pipeline_count"),
              longVal(rowMeta, row, "total_source_rows_read"),
              longVal(rowMeta, row, "total_target_rows_inserted"),
              longVal(rowMeta, row, "total_errors"),
              boolVal(rowMeta, row, "success")));
    }
    return parsed;
  }

  private static List<OverviewHistoryRow> first(List<OverviewHistoryRow> rows, int limit) {
    if (rows.size() <= limit) {
      return List.copyOf(rows);
    }
    return List.copyOf(rows.subList(0, limit));
  }

  private static String stringVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = indexOf(rowMeta, field);
      if (index < 0) {
        return null;
      }
      return rowMeta.getValueMeta(index).getString(row[index]);
    } catch (Exception e) {
      return null;
    }
  }

  private static Long longVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = indexOf(rowMeta, field);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value instanceof Number n) {
        return n.longValue();
      }
      String text = rowMeta.getValueMeta(index).getString(value);
      if (Utils.isEmpty(text)) {
        return null;
      }
      return Long.parseLong(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static Boolean boolVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = indexOf(rowMeta, field);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value instanceof Boolean b) {
        return b;
      }
      String text = rowMeta.getValueMeta(index).getString(value);
      return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static Date dateVal(IRowMeta rowMeta, Object[] row, String field) {
    try {
      int index = indexOf(rowMeta, field);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value instanceof Date date) {
        return date;
      }
      return rowMeta.getValueMeta(index).getDate(value);
    } catch (Exception e) {
      return null;
    }
  }

  private static int indexOf(IRowMeta rowMeta, String field) {
    int index = rowMeta.indexOfValue(field);
    if (index >= 0) {
      return index;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      if (rowMeta.getValueMeta(i).getName() != null
          && rowMeta.getValueMeta(i).getName().equalsIgnoreCase(field)) {
        return i;
      }
    }
    return -1;
  }
}
