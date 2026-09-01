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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoadRunMetricsDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultDdlUsesConnectionDefaultWithoutCreateSchema() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE SCHEMA")));
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("pipeline_run_configuration")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("workflow_execution_id")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_insight")));
    assertTrue(
        statements.stream().noneMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dv_ops.")));
    assertPipelineMetricTimingColumns(statements);
  }

  @Test
  void postgresPipelineMetricDdlIncludesTimingColumns() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID));
    assertPipelineMetricTimingColumns(statements);
  }

  @Test
  void snowflakePipelineMetricDdlMatchesPostgresShape() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID));
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
    assertPipelineMetricTimingColumns(statements);
  }

  @Test
  void mysqlPipelineMetricDdlIncludesTimingColumns() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID));
    assertPipelineMetricTimingColumns(statements);
  }

  @Test
  void mssqlPipelineMetricDdlIncludesTimingColumns() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID));
    String pipelineDdl =
        statements.stream()
            .filter(sql -> sql.contains("load_pipeline_metric"))
            .findFirst()
            .orElse("");
    assertTrue(pipelineDdl.contains("execution_start_date"));
    assertTrue(pipelineDdl.contains("execution_end_date"));
    assertTrue(pipelineDdl.contains("duration_ms"));
    assertTrue(pipelineDdl.contains("DATETIME2"));
  }

  private static void assertPipelineMetricTimingColumns(List<String> statements) {
    String pipelineDdl =
        statements.stream()
            .filter(sql -> sql.contains("load_pipeline_metric"))
            .findFirst()
            .orElse("");
    assertFalse(pipelineDdl.isEmpty(), "expected load_pipeline_metric CREATE statement");
    assertTrue(pipelineDdl.contains("execution_start_date"), pipelineDdl);
    assertTrue(pipelineDdl.contains("execution_end_date"), pipelineDdl);
    assertTrue(pipelineDdl.contains("duration_ms"), pipelineDdl);
  }

  @Test
  void previewSqlIncludesMetricsAndOverviewTables() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildPreviewStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID), "dv_ops");
    String sql =
        LoadRunMetricsDdlSupport.buildPreviewSql(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID), "dv_ops");

    assertTrue(statements.stream().anyMatch(s -> s.contains("CREATE SCHEMA IF NOT EXISTS dv_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS dv_ops.load_pipeline_metric")));
    assertTrue(
        statements.stream()
            .anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS dv_ops.workflow_load_overview")));
    assertTrue(sql.contains("dv_ops.load_pipeline_metric"));
    assertTrue(sql.contains("dv_ops.workflow_load_overview_model"));
  }

  @Test
  void postgresDdlUsesCustomOperationsSchema() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID),
            "retail_ops");

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE SCHEMA IF NOT EXISTS retail_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS retail_ops.load_run")));
  }

  @Test
  void mysqlDdlUsesConnectionDefaultWithoutCreateDatabase() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID));

    assertTrue(
        statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")),
        "MySQL should not create a separate ops database");
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("TINYINT(1)")));
  }

  @Test
  void singlestoreDdlUsesConnectionDefaultLikeMysql() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS load_run")));
  }

  @Test
  void mysqlCustomSchemaStillQualifiesTables() {
    List<String> statements =
        LoadRunMetricsDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID), "custom_ops");

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS custom_ops.load_run")));
    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
  }

  @Test
  void resolvePhysicalOperationsSchemaKeepsExplicitAndEmptyDefault() {
    DatabaseMeta mysql = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);
    DatabaseMeta postgres =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    assertEquals("", LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(null, mysql));
    assertEquals(
        "", LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("  ", postgres));
    assertEquals(
        "dv_ops",
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("dv_ops", postgres));
    assertEquals(
        "custom_ops",
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema("custom_ops", mysql));
    assertTrue(LoadRunMetricsCatalogPublisher.isMysqlFamily(mysql));
    assertFalse(LoadRunMetricsCatalogPublisher.isMysqlFamily(postgres));
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName("test-db");
    return databaseMeta;
  }
}
