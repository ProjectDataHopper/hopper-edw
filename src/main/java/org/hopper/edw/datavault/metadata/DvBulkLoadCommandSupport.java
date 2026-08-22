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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.workflow.action.IAction;

/** Builds bulk-load workflow actions for staged delimited files. */
public final class DvBulkLoadCommandSupport {

  public static final String MYSQL_BULK_LOAD_ACTION_ID = "MYSQL_BULK_LOAD";
  public static final String MSSQL_BULK_LOAD_ACTION_ID = "MSSQL_BULK_LOAD";
  public static final String SQL_ACTION_ID = "SQL";
  public static final String PIPELINE_ACTION_ID = "PIPELINE";

  private DvBulkLoadCommandSupport() {}

  /** Returns the workflow action plugin id used to load staged files for the target database. */
  public static String resolveStagingBulkActionPluginId(DatabaseMeta targetDatabase) {
    if (targetDatabase == null || Utils.isEmpty(targetDatabase.getPluginId())) {
      return null;
    }
    return switch (targetDatabase.getPluginId()) {
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID ->
          MYSQL_BULK_LOAD_ACTION_ID;
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          MSSQL_BULK_LOAD_ACTION_ID;
      case DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID ->
          DvBulkLoadPluginSupport.isTransformPluginAvailable(
                  DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)
              ? PIPELINE_ACTION_ID
              : null;
      default -> null;
    };
  }

  public static IAction createStagingBulkLoadAction(
      DatabaseMeta targetDatabase,
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String targetDbName,
      String targetTableName,
      List<String> columnNames,
      String stagedFilePath,
      int copyIndex)
      throws HopException {
    String actionPluginId = resolveStagingBulkActionPluginId(targetDatabase);
    if (Utils.isEmpty(actionPluginId)) {
      throw new HopException(
          "No staged bulk-load workflow action is available for database type "
              + (targetDatabase != null ? targetDatabase.getPluginId() : ""));
    }
    String actionName = "bulk_load_" + targetTableName + "_" + copyIndex;
    IAction action = DvBulkLoadActionSupport.newConfiguredAction(actionPluginId, actionName);
    if (MYSQL_BULK_LOAD_ACTION_ID.equals(actionPluginId)) {
      configureMysqlBulkLoadAction(
          action, config, variables, targetDbName, targetTableName, stagedFilePath);
    } else if (MSSQL_BULK_LOAD_ACTION_ID.equals(actionPluginId)) {
      configureMssqlBulkLoadAction(
          action, config, variables, targetDbName, targetTableName, stagedFilePath);
    } else {
      throw new HopException("Unsupported staging bulk-load action: " + actionPluginId);
    }
    return action;
  }

  private static void configureMssqlBulkLoadAction(
      IAction action,
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String targetDbName,
      String targetTableName,
      String stagedFilePath)
      throws HopException {
    DvBulkLoadActionSupport.invoke(action, "setConnection", String.class, targetDbName);
    DvBulkLoadActionSupport.invoke(action, "setTableName", String.class, targetTableName);
    DvBulkLoadActionSupport.invoke(action, "setSchemaName", String.class, "");
    DvBulkLoadActionSupport.invoke(action, "setFileName", String.class, stagedFilePath);
    DvBulkLoadActionSupport.invoke(action, "setDataFileType", String.class, "char");
    DvBulkLoadActionSupport.invoke(
        action, "setFieldTerminator", String.class, config.resolveBulkLoadDelimiter(variables));
    DvBulkLoadActionSupport.invoke(action, "setLineTerminated", String.class, "\\n");
    DvBulkLoadActionSupport.invoke(action, "setCodePage", String.class, "65001");
    DvBulkLoadActionSupport.invoke(action, "setStartFile", int.class, 2);
    DvBulkLoadActionSupport.invoke(action, "setKeepNulls", boolean.class, true);
    DvBulkLoadActionSupport.invoke(action, "setTruncate", boolean.class, false);
  }

  private static void configureMysqlBulkLoadAction(
      IAction action,
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String targetDbName,
      String targetTableName,
      String stagedFilePath)
      throws HopException {
    DvBulkLoadActionSupport.invoke(action, "setConnection", String.class, targetDbName);
    DvBulkLoadActionSupport.invoke(action, "setTableName", String.class, targetTableName);
    DvBulkLoadActionSupport.invoke(action, "setSchemaName", String.class, "");
    DvBulkLoadActionSupport.invoke(action, "setFileName", String.class, stagedFilePath);
    DvBulkLoadActionSupport.invoke(
        action, "setSeparator", String.class, config.resolveBulkLoadDelimiter(variables));
    DvBulkLoadActionSupport.invoke(
        action, "setEnclosed", String.class, config.resolveBulkLoadEnclosure(variables));
    DvBulkLoadActionSupport.invoke(action, "setEscaped", String.class, "\\");
    DvBulkLoadActionSupport.invoke(action, "setIgnoreLines", String.class, "1");
    DvBulkLoadActionSupport.invoke(action, "setReplaceData", boolean.class, false);
    DvBulkLoadActionSupport.invoke(action, "setLocalInFile", boolean.class, true);
  }
}
