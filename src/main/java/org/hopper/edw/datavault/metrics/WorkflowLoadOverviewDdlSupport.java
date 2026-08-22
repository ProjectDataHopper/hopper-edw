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
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;

/** Creates workflow load overview tables in the operations schema. */
public final class WorkflowLoadOverviewDdlSupport {

  public static final String TABLE_WORKFLOW_LOAD_OVERVIEW = "workflow_load_overview";
  public static final String TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL = "workflow_load_overview_model";

  private WorkflowLoadOverviewDdlSupport() {}

  public static void ensureOverviewTables(
      Database db, DatabaseMeta databaseMeta, String operationsSchema, ILogChannel log)
      throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String schema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            operationsSchema, databaseMeta);
    if (db.checkTableExists(schema, TABLE_WORKFLOW_LOAD_OVERVIEW)
        && db.checkTableExists(schema, TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL)) {
      return;
    }
    String ddl = String.join(";\n", buildCreateStatements(databaseMeta, schema)) + ";";
    if (log != null) {
      log.logBasic(
          "Creating workflow load overview tables in "
              + LoadRunMetricsCatalogPublisher.describeOperationsLocation(schema)
              + " on "
              + databaseMeta.getName());
    }
    db.execStatements(ddl);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta, String operationsSchema) {
    String schema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            operationsSchema, databaseMeta);
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";
    return switch (pluginId) {
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID ->
          mysqlStatements(schema);
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          mssqlStatements(schema);
      case DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID -> postgresStatements(schema);
      default -> postgresStatements(schema);
    };
  }

  private static List<String> postgresStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          overview_id              VARCHAR(64)   NOT NULL,
          workflow_execution_id    VARCHAR(64)   NULL,
          root_workflow_name       VARCHAR(255)  NULL,
          metrics_workflow_name    VARCHAR(255)  NULL,
          started_at               TIMESTAMP     NULL,
          finished_at              TIMESTAMP     NULL,
          duration_ms              BIGINT        NULL,
          model_count              BIGINT        NULL,
          pipeline_count           BIGINT        NULL,
          insight_count            BIGINT        NULL,
          total_source_rows_read   BIGINT        NULL,
          total_target_rows_inserted BIGINT      NULL,
          total_errors             BIGINT        NULL,
          success                  BOOLEAN       NULL,
          PRIMARY KEY (overview_id)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          overview_id              VARCHAR(64)   NOT NULL,
          sequence_no              BIGINT        NOT NULL,
          load_run_id              VARCHAR(64)   NULL,
          model_type               VARCHAR(16)   NULL,
          model_name               VARCHAR(255)  NULL,
          pipeline_count           BIGINT        NULL,
          source_rows_read         BIGINT        NULL,
          target_rows_read         BIGINT        NULL,
          target_rows_inserted     BIGINT        NULL,
          errors                   BIGINT        NULL,
          duration_ms              BIGINT        NULL,
          insight_count            BIGINT        NULL,
          success                  BOOLEAN       NULL,
          PRIMARY KEY (overview_id, sequence_no)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL)));
    return statements;
  }

  private static List<String> mssqlStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        """
        CREATE TABLE %s (
          overview_id              VARCHAR(64)   NOT NULL,
          workflow_execution_id    VARCHAR(64)   NULL,
          root_workflow_name       VARCHAR(255)  NULL,
          metrics_workflow_name    VARCHAR(255)  NULL,
          started_at               DATETIME2     NULL,
          finished_at              DATETIME2     NULL,
          duration_ms              BIGINT        NULL,
          model_count              BIGINT        NULL,
          pipeline_count           BIGINT        NULL,
          insight_count            BIGINT        NULL,
          total_source_rows_read   BIGINT        NULL,
          total_target_rows_inserted BIGINT      NULL,
          total_errors             BIGINT        NULL,
          success                  BIT           NULL,
          PRIMARY KEY (overview_id)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW)));
    statements.add(
        """
        CREATE TABLE %s (
          overview_id              VARCHAR(64)   NOT NULL,
          sequence_no              BIGINT        NOT NULL,
          load_run_id              VARCHAR(64)   NULL,
          model_type               VARCHAR(16)   NULL,
          model_name               VARCHAR(255)  NULL,
          pipeline_count           BIGINT        NULL,
          source_rows_read         BIGINT        NULL,
          target_rows_read         BIGINT        NULL,
          target_rows_inserted     BIGINT        NULL,
          errors                   BIGINT        NULL,
          duration_ms              BIGINT        NULL,
          insight_count            BIGINT        NULL,
          success                  BIT           NULL,
          PRIMARY KEY (overview_id, sequence_no)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL)));
    return statements;
  }

  private static List<String> mysqlStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          overview_id              VARCHAR(64)   NOT NULL,
          workflow_execution_id    VARCHAR(64)   NULL,
          root_workflow_name       VARCHAR(255)  NULL,
          metrics_workflow_name    VARCHAR(255)  NULL,
          started_at               TIMESTAMP     NULL,
          finished_at              TIMESTAMP     NULL,
          duration_ms              BIGINT        NULL,
          model_count              BIGINT        NULL,
          pipeline_count           BIGINT        NULL,
          insight_count            BIGINT        NULL,
          total_source_rows_read   BIGINT        NULL,
          total_target_rows_inserted BIGINT      NULL,
          total_errors             BIGINT        NULL,
          success                  TINYINT(1)    NULL,
          PRIMARY KEY (overview_id)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          overview_id              VARCHAR(64)   NOT NULL,
          sequence_no              BIGINT        NOT NULL,
          load_run_id              VARCHAR(64)   NULL,
          model_type               VARCHAR(16)   NULL,
          model_name               VARCHAR(255)  NULL,
          pipeline_count           BIGINT        NULL,
          source_rows_read         BIGINT        NULL,
          target_rows_read         BIGINT        NULL,
          target_rows_inserted     BIGINT        NULL,
          errors                   BIGINT        NULL,
          duration_ms              BIGINT        NULL,
          insight_count            BIGINT        NULL,
          success                  TINYINT(1)    NULL,
          PRIMARY KEY (overview_id, sequence_no)
        )"""
            .formatted(qualify(schema, TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL)));
    return statements;
  }

  private static String qualify(String schema, String table) {
    return LoadRunMetricsCatalogPublisher.qualifyOperationsTable(schema, table);
  }
}
