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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowLoadOverviewDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultDdlCreatesUnqualifiedWorkflowOverviewTables() {
    List<String> statements =
        WorkflowLoadOverviewDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID),
            LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME);

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS workflow_load_overview")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql -> sql.contains("CREATE TABLE IF NOT EXISTS workflow_load_overview_model")));
    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE SCHEMA")));
  }

  @Test
  void mysqlDdlCreatesUnqualifiedOverviewTables() {
    List<String> statements =
        WorkflowLoadOverviewDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID),
            LoadRunMetricsCatalogPublisher.DEFAULT_SCHEMA_NAME);

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS workflow_load_overview")));
    assertTrue(statements.stream().anyMatch(sql -> sql.contains("TINYINT(1)")));
  }

  @Test
  void customSchemaQualifiesOverviewTables() {
    List<String> statements =
        WorkflowLoadOverviewDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID), "ops");

    assertTrue(
        statements.stream()
            .anyMatch(
                sql -> sql.contains("CREATE TABLE IF NOT EXISTS ops.workflow_load_overview")));
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName("OPS");
    return databaseMeta;
  }
}
