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

import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Counts persisted load-run metrics rows for workflow and integration verification. */
public final class LoadRunMetricsDatabaseAssertionSupport {

  public record LoadRunDatabaseCounts(
      long loadRunRows, long transformMetricRows, long insightRows) {}

  private LoadRunMetricsDatabaseAssertionSupport() {}

  public static boolean canConnect(DatabaseMeta databaseMeta, IVariables variables) {
    if (databaseMeta == null) {
      return false;
    }
    LoggingObject loggingObject = new LoggingObject(LoadRunMetricsDatabaseAssertionSupport.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      db.disconnect();
    }
  }

  public static LoadRunDatabaseCounts countRowsForRun(
      DatabaseMeta databaseMeta, String operationsSchema, String runId, IVariables variables)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(runId)) {
      return new LoadRunDatabaseCounts(0L, 0L, 0L);
    }
    String schema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            operationsSchema, databaseMeta);
    LoggingObject loggingObject = new LoggingObject(LoadRunMetricsDatabaseAssertionSupport.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      long loadRunRows =
          countRows(
              db,
              databaseMeta,
              variables,
              schema,
              LoadRunMetricsCatalogPublisher.TABLE_LOAD_RUN,
              runId);
      long transformRows =
          countRows(
              db,
              databaseMeta,
              variables,
              schema,
              LoadRunMetricsCatalogPublisher.TABLE_LOAD_TRANSFORM_METRIC,
              runId);
      long insightRows =
          countRows(
              db,
              databaseMeta,
              variables,
              schema,
              LoadRunMetricsCatalogPublisher.TABLE_LOAD_INSIGHT,
              runId);
      return new LoadRunDatabaseCounts(loadRunRows, transformRows, insightRows);
    } catch (Exception e) {
      throw new HopException("Unable to count load-run metrics rows for run " + runId, e);
    } finally {
      db.disconnect();
    }
  }

  private static long countRows(
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schema,
      String tableName,
      String runId)
      throws HopException {
    if (!db.checkTableExists(schema, tableName)) {
      return 0L;
    }
    String qualifiedTable =
        databaseMeta.getQuotedSchemaTableCombination(variables, schema, tableName);
    String sql =
        "SELECT COUNT(*) AS row_count FROM "
            + qualifiedTable
            + " WHERE run_id = "
            + sqlLiteral(variables, runId);
    var row = db.getOneRow(sql);
    if (row == null || row.getData() == null || row.getData().length == 0) {
      return 0L;
    }
    IRowMeta rowMeta = row.getRowMeta();
    if (rowMeta == null) {
      return 0L;
    }
    int index = rowMeta.indexOfValue("row_count");
    if (index < 0) {
      index = 0;
    }
    Object value = row.getData()[index];
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String sqlLiteral(IVariables variables, String value) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (resolved == null) {
      return "NULL";
    }
    return "'" + resolved.replace("'", "''") + "'";
  }
}
