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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * Optional load-cycle ID standard column: layout, pipeline constants, and durable allocation.
 *
 * <p>When enabled on a model configuration, every target table layout gains an integer audit column
 * and generated loads stamp the same cycle id for one model-update action. The next update
 * increments a single-row control table on the target database.
 */
public final class DvLoadCycleSupport {

  public static final String DEFAULT_FIELD_NAME = "LOAD_CYCLE_ID";
  public static final String DEFAULT_CONTROL_TABLE = "dv_load_cycle";
  public static final String VAR_LOAD_CYCLE_ID = "DV_LOAD_CYCLE_ID";
  public static final String COLUMN_CYCLE_ID = "cycle_id";

  private DvLoadCycleSupport() {}

  public static boolean isEnabled(boolean storeLoadCycleId) {
    return storeLoadCycleId;
  }

  public static String resolveFieldName(String configuredField, IVariables variables) {
    String name = variables != null ? variables.resolve(configuredField) : configuredField;
    if (Utils.isEmpty(name)) {
      return DEFAULT_FIELD_NAME;
    }
    return name.trim();
  }

  public static String resolveControlTableName(String configuredTable, IVariables variables) {
    String name = variables != null ? variables.resolve(configuredTable) : configuredTable;
    if (Utils.isEmpty(name)) {
      return DEFAULT_CONTROL_TABLE;
    }
    return name.trim();
  }

  /** Appends the load-cycle integer column when enabled and not already present. */
  public static void appendToLayout(
      IRowMeta rowMeta, boolean storeLoadCycleId, String fieldName, IVariables variables) {
    if (rowMeta == null || !storeLoadCycleId) {
      return;
    }
    String name = resolveFieldName(fieldName, variables);
    if (rowMeta.searchValueMeta(name) != null) {
      return;
    }
    IValueMeta meta = new ValueMetaInteger(name);
    meta.setLength(15);
    rowMeta.addValueMeta(meta);
  }

  /**
   * Resolves the cycle id from the workflow variable {@link #VAR_LOAD_CYCLE_ID}.
   *
   * @return parsed long, or null when empty / not a number
   */
  public static Long resolveCycleIdFromVariables(IVariables variables) {
    if (variables == null) {
      return null;
    }
    String raw = variables.getVariable(VAR_LOAD_CYCLE_ID);
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static void setCycleIdVariable(IVariables variables, long cycleId) {
    if (variables == null) {
      return;
    }
    variables.setVariable(VAR_LOAD_CYCLE_ID, Long.toString(cycleId));
  }

  public static void setCycleIdVariable(IWorkflowEngine<WorkflowMeta> workflow, long cycleId) {
    if (workflow == null) {
      return;
    }
    workflow.setVariable(VAR_LOAD_CYCLE_ID, Long.toString(cycleId));
  }

  /**
   * Ensures the control table exists, increments the counter, and returns the new cycle id.
   *
   * <p>Also sets {@link #VAR_LOAD_CYCLE_ID} on {@code variables}.
   */
  public static long allocateNext(
      DatabaseMeta databaseMeta,
      IVariables variables,
      ILoggingObject loggingObject,
      String controlTableName)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Cannot allocate a load cycle id without a target database.");
    }
    String table = resolveControlTableName(controlTableName, variables);
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();
      ensureControlTable(db, databaseMeta, variables, table);
      long next = incrementAndRead(db, databaseMeta, variables, table);
      setCycleIdVariable(variables, next);
      return next;
    }
  }

  /**
   * Allocates when {@code storeLoadCycleId} is true; otherwise returns null without touching the
   * database.
   */
  public static Long allocateIfEnabled(
      boolean storeLoadCycleId,
      DatabaseMeta databaseMeta,
      IVariables variables,
      ILoggingObject loggingObject,
      String controlTableName)
      throws HopException {
    if (!storeLoadCycleId) {
      return null;
    }
    return allocateNext(databaseMeta, variables, loggingObject, controlTableName);
  }

  static void ensureControlTable(
      Database db, DatabaseMeta databaseMeta, IVariables variables, String tableName)
      throws HopException {
    String schema = null;
    if (!db.checkTableExists(schema, tableName)) {
      String ddl = buildCreateControlTableSql(databaseMeta, variables, tableName);
      db.execStatements(ddl);
    }
    String qualified = databaseMeta.getQuotedSchemaTableCombination(variables, schema, tableName);
    String countSql = "SELECT COUNT(*) FROM " + qualified;
    RowMetaAndData countRow = db.getOneRow(countSql);
    long count = 0;
    if (countRow != null && countRow.getData() != null && countRow.getData().length > 0) {
      Object value = countRow.getData()[0];
      if (value instanceof Number number) {
        count = number.longValue();
      } else if (value != null) {
        count = Const.toLong(value.toString(), 0L);
      }
    }
    if (count <= 0) {
      db.execStatement("INSERT INTO " + qualified + " (" + COLUMN_CYCLE_ID + ") VALUES (0)");
    }
  }

  static String buildCreateControlTableSql(
      DatabaseMeta databaseMeta, IVariables variables, String tableName) {
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";
    String qualified = databaseMeta.getQuotedSchemaTableCombination(variables, null, tableName);
    return switch (pluginId) {
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID ->
          "CREATE TABLE IF NOT EXISTS "
              + qualified
              + " ("
              + COLUMN_CYCLE_ID
              + " BIGINT NOT NULL, updated_at TIMESTAMP NULL)";
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          "IF OBJECT_ID(N'"
              + tableName.replace("'", "''")
              + "', N'U') IS NULL CREATE TABLE "
              + qualified
              + " ("
              + COLUMN_CYCLE_ID
              + " BIGINT NOT NULL, updated_at DATETIME2 NULL)";
      default ->
          "CREATE TABLE IF NOT EXISTS "
              + qualified
              + " ("
              + COLUMN_CYCLE_ID
              + " BIGINT NOT NULL, updated_at TIMESTAMP NULL)";
    };
  }

  static long incrementAndRead(
      Database db, DatabaseMeta databaseMeta, IVariables variables, String tableName)
      throws HopException {
    String qualified = databaseMeta.getQuotedSchemaTableCombination(variables, null, tableName);
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";

    if (DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equals(pluginId)
        || DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equals(pluginId)) {
      db.execStatement(
          "UPDATE "
              + qualified
              + " SET "
              + COLUMN_CYCLE_ID
              + " = LAST_INSERT_ID("
              + COLUMN_CYCLE_ID
              + " + 1), updated_at = CURRENT_TIMESTAMP");
      RowMetaAndData row = db.getOneRow("SELECT LAST_INSERT_ID()");
      return requireLong(row, "MySQL/SingleStore load cycle allocation");
    }

    if (DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equals(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equals(pluginId)) {
      RowMetaAndData row =
          db.getOneRow(
              "UPDATE "
                  + qualified
                  + " WITH (UPDLOCK, ROWLOCK) SET "
                  + COLUMN_CYCLE_ID
                  + " = "
                  + COLUMN_CYCLE_ID
                  + " + 1, updated_at = SYSUTCDATETIME() OUTPUT INSERTED."
                  + COLUMN_CYCLE_ID);
      return requireLong(row, "SQL Server load cycle allocation");
    }

    // PostgreSQL and defaults: UPDATE ... RETURNING
    RowMetaAndData row =
        db.getOneRow(
            "UPDATE "
                + qualified
                + " SET "
                + COLUMN_CYCLE_ID
                + " = "
                + COLUMN_CYCLE_ID
                + " + 1, updated_at = CURRENT_TIMESTAMP RETURNING "
                + COLUMN_CYCLE_ID);
    return requireLong(row, "PostgreSQL load cycle allocation");
  }

  private static long requireLong(RowMetaAndData row, String context) throws HopException {
    if (row == null
        || row.getData() == null
        || row.getData().length == 0
        || row.getData()[0] == null) {
      throw new HopException("No cycle id returned from " + context);
    }
    Object value = row.getData()[0];
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString().trim());
    } catch (NumberFormatException e) {
      throw new HopException("Invalid cycle id from " + context + ": " + value, e);
    }
  }

  /**
   * Adds a Constant transform that stamps the load-cycle id when enabled and a value is available.
   *
   * @return predecessor unchanged when disabled or cycle id missing; otherwise the new Constant
   *     transform
   */
  public static TransformMeta addConstantForLoadCycleId(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      boolean storeLoadCycleId,
      String fieldName,
      IVariables variables,
      Long cycleId,
      Point location)
      throws HopException {
    if (pipelineMeta == null || predecessor == null || !storeLoadCycleId) {
      return predecessor;
    }
    Long resolved = cycleId != null ? cycleId : resolveCycleIdFromVariables(variables);
    if (resolved == null) {
      throw new HopException(
          "Load cycle id column is enabled but no cycle id is available. "
              + "Allocate a cycle before generating update pipelines (variable "
              + VAR_LOAD_CYCLE_ID
              + ").");
    }
    String name = resolveFieldName(fieldName, variables);
    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(name, "Integer", Long.toString(resolved));
    constantMeta.getFields().add(cf);

    TransformMeta tm = new TransformMeta("Constant", "add_" + name, constantMeta);
    if (location != null) {
      tm.setLocation(location);
    } else {
      Point pred = predecessor.getLocation();
      if (pred != null) {
        tm.setLocation(new Point(pred.x + 100, pred.y));
      }
    }
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  /** True when the layout column name is the configured load-cycle field. */
  public static boolean isLoadCycleColumn(
      boolean storeLoadCycleId, String fieldName, IVariables variables, String columnName) {
    if (!storeLoadCycleId || Utils.isEmpty(columnName)) {
      return false;
    }
    return resolveFieldName(fieldName, variables).equals(columnName);
  }
}
