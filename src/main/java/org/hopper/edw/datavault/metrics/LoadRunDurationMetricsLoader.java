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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Loads recent per-table load durations from the operations metrics catalog. */
public final class LoadRunDurationMetricsLoader {

  public static final int DEFAULT_MAX_RUNS = 20;

  private LoadRunDurationMetricsLoader() {}

  public static LoadRunDurationSnapshot load(
      String modelName,
      String modelType,
      List<String> tableNames,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    return load(modelName, modelType, tableNames, metadataProvider, variables, DEFAULT_MAX_RUNS);
  }

  public static LoadRunDurationSnapshot load(
      String modelName,
      String modelType,
      List<String> tableNames,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      int maxRuns) {
    List<String> orderedTables = normalizeTableNames(tableNames);
    if (orderedTables.isEmpty()) {
      return LoadRunDurationSnapshot.builder()
          .status(LoadRunDurationSnapshot.Status.NO_TABLES)
          .build();
    }

    String databaseName =
        MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, variables);
    if (Utils.isEmpty(databaseName) || metadataProvider == null) {
      return LoadRunDurationSnapshot.builder()
          .status(LoadRunDurationSnapshot.Status.NO_DATABASE)
          .tableNames(orderedTables)
          .build();
    }

    try {
      String resolvedDatabase = resolveValue(databaseName, variables);
      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(resolvedDatabase);
      if (databaseMeta == null) {
        return LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.NO_DATABASE)
            .tableNames(orderedTables)
            .build();
      }
      String schema =
          LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
              MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, variables),
              databaseMeta);
      return querySnapshot(
          databaseMeta, schema, modelName, modelType, orderedTables, variables, maxRuns);
    } catch (Exception e) {
      return LoadRunDurationSnapshot.builder()
          .status(LoadRunDurationSnapshot.Status.ERROR)
          .message(e.getMessage())
          .tableNames(orderedTables)
          .build();
    }
  }

  static LoadRunDurationSnapshot assembleSnapshot(
      List<String> tableNames, List<LoadRunDurationRun> runs, List<DurationMetricRow> metricRows) {
    List<String> orderedTables = normalizeTableNames(tableNames);
    List<LoadRunDurationRun> orderedRuns = runs != null ? runs : Collections.emptyList();
    if (orderedRuns.isEmpty()) {
      return LoadRunDurationSnapshot.builder()
          .status(LoadRunDurationSnapshot.Status.NO_RUNS)
          .tableNames(List.copyOf(orderedTables))
          .runs(List.copyOf(orderedRuns))
          .build();
    }

    Map<String, Integer> runIndexById = new LinkedHashMap<>();
    for (int i = 0; i < orderedRuns.size(); i++) {
      runIndexById.put(orderedRuns.get(i).getRunId(), i);
    }

    Map<String, long[]> durationsByElement = new HashMap<>();
    for (String tableName : orderedTables) {
      durationsByElement.put(tableName, new long[orderedRuns.size()]);
    }

    if (metricRows != null) {
      for (DurationMetricRow row : metricRows) {
        if (row == null || Utils.isEmpty(row.runId()) || Utils.isEmpty(row.elementName())) {
          continue;
        }
        long[] durations = durationsByElement.get(row.elementName());
        if (durations == null) {
          continue;
        }
        Integer runIndex = runIndexById.get(row.runId());
        if (runIndex == null || runIndex >= durations.length) {
          continue;
        }
        durations[runIndex] = row.durationMs();
      }
    }

    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.LOADED)
            .tableNames(List.copyOf(orderedTables))
            .runs(List.copyOf(orderedRuns))
            .durationsByElement(durationsByElement)
            .build();

    return LoadRunDurationSnapshot.builder()
        .status(snapshot.getStatus())
        .tableNames(snapshot.getTableNames())
        .runs(snapshot.getRuns())
        .durationsByElement(snapshot.getDurationsByElement())
        .maxDurationMs(snapshot.resolveDisplayMaxDurationMs())
        .build();
  }

  private static LoadRunDurationSnapshot querySnapshot(
      DatabaseMeta databaseMeta,
      String schema,
      String modelName,
      String modelType,
      List<String> tableNames,
      IVariables variables,
      int maxRuns)
      throws HopException {
    LoggingObject loggingObject = new LoggingObject(LoadRunDurationMetricsLoader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN)) {
        return LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.NO_RUNS)
            .tableNames(tableNames)
            .build();
      }

      List<LoadRunDurationRun> runs =
          queryRecentRuns(db, databaseMeta, schema, modelName, modelType, variables, maxRuns);
      if (runs.isEmpty()) {
        return LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.NO_RUNS)
            .tableNames(tableNames)
            .build();
      }

      List<DurationMetricRow> metricRows =
          queryDurationMetrics(db, databaseMeta, schema, modelName, modelType, runs, variables);
      return assembleSnapshot(tableNames, runs, metricRows);
    } finally {
      db.disconnect();
    }
  }

  private static List<LoadRunDurationRun> queryRecentRuns(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      String modelName,
      String modelType,
      IVariables variables,
      int maxRuns)
      throws HopException {
    String loadRunTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);
    String sql =
        "SELECT run_id, finished_at, success FROM "
            + loadRunTable
            + " WHERE model_name = "
            + sqlLiteral(variables, modelName)
            + " AND model_type = "
            + sqlLiteral(variables, modelType)
            + " ORDER BY finished_at DESC";

    db.setQueryLimit(maxRuns);
    List<Object[]> rows = db.getRows(sql, maxRuns);
    IRowMeta rowMeta = db.getReturnRowMeta();
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      return Collections.emptyList();
    }

    List<LoadRunDurationRun> runs = new ArrayList<>(rows.size());
    for (int i = rows.size() - 1; i >= 0; i--) {
      Object[] row = rows.get(i);
      runs.add(
          LoadRunDurationRun.builder()
              .runId(stringValue(rowMeta, row, "run_id"))
              .finishedAt(dateValue(rowMeta, row, "finished_at"))
              .success(Boolean.TRUE.equals(booleanValue(rowMeta, row, "success")))
              .build());
    }
    return runs;
  }

  private static List<DurationMetricRow> queryDurationMetrics(
      Database db,
      DatabaseMeta databaseMeta,
      String schema,
      String modelName,
      String modelType,
      List<LoadRunDurationRun> runs,
      IVariables variables)
      throws HopException {
    if (!db.checkTableExists(schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC)) {
      return Collections.emptyList();
    }

    String transformTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC);
    String loadRunTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, schema, LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN);

    String runIdInClause = buildRunIdInClause(variables, runs);
    String sql =
        "SELECT r.run_id, t.element_name, SUM(COALESCE(t.duration_ms, 0)) AS duration_ms "
            + "FROM "
            + transformTable
            + " t JOIN "
            + loadRunTable
            + " r ON r.run_id = t.run_id "
            + "WHERE r.model_name = "
            + sqlLiteral(variables, modelName)
            + " AND r.model_type = "
            + sqlLiteral(variables, modelType)
            + " AND r.run_id IN ("
            + runIdInClause
            + ") "
            + "GROUP BY r.run_id, t.element_name";

    int rowLimit = Math.max(1, runs.size()) * 256;
    List<Object[]> rows = db.getRows(sql, rowLimit);
    IRowMeta rowMeta = db.getReturnRowMeta();
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      return Collections.emptyList();
    }

    List<DurationMetricRow> metrics = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      Long durationMs = longValue(rowMeta, row, "duration_ms");
      metrics.add(
          new DurationMetricRow(
              stringValue(rowMeta, row, "run_id"),
              stringValue(rowMeta, row, "element_name"),
              durationMs != null ? durationMs : 0L));
    }
    return metrics;
  }

  static String buildRunIdInClause(IVariables variables, List<LoadRunDurationRun> runs) {
    StringBuilder clause = new StringBuilder();
    for (int i = 0; i < runs.size(); i++) {
      if (i > 0) {
        clause.append(',');
      }
      clause.append(sqlLiteral(variables, runs.get(i).getRunId()));
    }
    return clause.toString();
  }

  private static List<String> normalizeTableNames(List<String> tableNames) {
    if (tableNames == null || tableNames.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> normalized = new ArrayList<>();
    for (String tableName : tableNames) {
      if (!Utils.isEmpty(tableName)) {
        normalized.add(tableName);
      }
    }
    return normalized;
  }

  private static String resolveValue(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  static String sqlLiteral(IVariables variables, String value) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (resolved == null) {
      return "NULL";
    }
    return "'" + resolved.replace("'", "''") + "'";
  }

  static String stringValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length || row[index] == null) {
      return "";
    }
    return row[index].toString();
  }

  static Date dateValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    int index = rowMeta.indexOfValue(fieldName);
    if (index < 0 || row == null || index >= row.length) {
      return null;
    }
    Object value = row[index];
    return value instanceof Date date ? date : null;
  }

  static Boolean booleanValue(IRowMeta rowMeta, Object[] row, String fieldName) {
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

  static Long longValue(IRowMeta rowMeta, Object[] row, String fieldName) {
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

  /** Raw query row used when assembling a {@link LoadRunDurationSnapshot}. */
  public record DurationMetricRow(String runId, String elementName, long durationMs) {}
}
