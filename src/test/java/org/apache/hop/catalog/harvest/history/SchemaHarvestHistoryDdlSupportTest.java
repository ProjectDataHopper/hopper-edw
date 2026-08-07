/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.catalog.harvest.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SchemaHarvestHistoryDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultPostgresDdlCreatesTablesWithoutSchema() {
    List<String> statements =
        SchemaHarvestHistoryDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId("POSTGRESQL"));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE SCHEMA")));
    assertContainsTable(statements, "schema_harvest_run");
    assertContainsTable(statements, "schema_harvest_subject");
    assertContainsTable(statements, "schema_harvest_field");
    assertContainsTable(statements, "schema_harvest_change");
    assertContainsTable(statements, "schema_harvest_fk");
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE INDEX IF NOT EXISTS idx_schema_harvest_run_group")
                        && sql.contains("resource_group_name")));
  }

  @Test
  void postgresDdlUsesCustomOperationsSchema() {
    List<String> statements =
        SchemaHarvestHistoryDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId("POSTGRESQL"), "retail_ops");

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE SCHEMA IF NOT EXISTS retail_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql -> sql.contains("CREATE TABLE IF NOT EXISTS retail_ops.schema_harvest_run")));
  }

  @Test
  void mysqlDdlUsesConnectionDefaultWithoutCreateDatabase() {
    List<String> statements =
        SchemaHarvestHistoryDdlSupport.buildCreateStatements(databaseMetaWithPluginId("MYSQL"));
    assertTrue(statements.stream().noneMatch(sql -> sql.toUpperCase().contains("CREATE DATABASE")));
    assertContainsTable(statements, "schema_harvest_run");
  }

  @Test
  void resolveSchemaTrimsAndDefaults() {
    assertEquals("", SchemaHarvestHistoryDdlSupport.resolveSchema(null));
    assertEquals("", SchemaHarvestHistoryDdlSupport.resolveSchema("  "));
    assertEquals("custom", SchemaHarvestHistoryDdlSupport.resolveSchema(" custom "));
  }

  private static void assertContainsTable(List<String> statements, String table) {
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains(table)),
        "Expected table " + table + " in DDL");
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
