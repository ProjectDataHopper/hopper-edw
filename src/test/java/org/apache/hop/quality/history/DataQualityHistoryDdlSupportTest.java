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
package org.apache.hop.quality.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataQualityHistoryDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultPostgresDdlCreatesTablesWithoutSchema() {
    List<String> statements =
        DataQualityHistoryDdlSupport.buildCreateStatements(databaseMetaWithPluginId("POSTGRESQL"));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE SCHEMA")));
    assertContainsTable(statements, "quality_run");
    assertContainsTable(statements, "quality_profile_subject");
    assertContainsTable(statements, "quality_profile_field");
    assertContainsTable(statements, "quality_finding");
    assertContainsTable(statements, "quality_alert");
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE INDEX IF NOT EXISTS idx_quality_run_load_id")
                        && sql.contains("quality_run")
                        && sql.contains("(load_id)")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE TABLE IF NOT EXISTS quality_run")
                        && sql.contains("subjects_json")
                        && sql.contains("infra_errors_json")));
  }

  @Test
  void postgresDdlUsesCustomOperationsSchema() {
    List<String> statements =
        DataQualityHistoryDdlSupport.buildCreateStatements(
            databaseMetaWithPluginId("POSTGRESQL"), "retail_ops");

    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE SCHEMA IF NOT EXISTS retail_ops")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS retail_ops.quality_run")));
  }

  @Test
  void mysqlDdlUsesConnectionDefaultWithoutCreateDatabase() {
    List<String> statements =
        DataQualityHistoryDdlSupport.buildCreateStatements(databaseMetaWithPluginId("MYSQL"));

    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE TABLE IF NOT EXISTS quality_alert")
                        && sql.contains("disposition_failed")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE TABLE IF NOT EXISTS quality_run")
                        && sql.contains("TINYINT(1)")));
  }

  @Test
  void mysqlIndexesOmitIfNotExists() {
    List<String> statements =
        DataQualityHistoryDdlSupport.buildCreateStatements(databaseMetaWithPluginId("MYSQL"));

    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE INDEX idx_quality_run_load_id")
                        && !sql.contains("IF NOT EXISTS")));
    assertTrue(
        statements.stream()
            .noneMatch(
                sql ->
                    sql.contains("CREATE INDEX IF NOT EXISTS") && sql.contains("idx_quality_run")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE INDEX idx_quality_run_measured_at")
                        && sql.contains("measured_at DESC")));
    assertTrue(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.contains("CREATE INDEX idx_quality_profile_subject_lookup")
                        && sql.contains("captured_at DESC")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE INDEX idx_quality_finding_subject_rule")));
  }

  @Test
  void singlestoreUsesMysqlIndexDialectAndConnectionDefault() {
    List<String> statements =
        DataQualityHistoryDdlSupport.buildCreateStatements(databaseMetaWithPluginId("SINGLESTORE"));
    assertTrue(statements.stream().noneMatch(sql -> sql.contains("CREATE DATABASE")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS quality_run")));
    assertTrue(
        statements.stream()
            .noneMatch(sql -> sql.contains("CREATE INDEX IF NOT EXISTS idx_quality_run")));
    assertTrue(
        statements.stream()
            .anyMatch(sql -> sql.startsWith("CREATE INDEX idx_quality_run_load_id")));
  }

  @Test
  void resolveSchemaDefaultsToEmpty() {
    assertEquals("", DataQualityHistoryDdlSupport.resolveSchema(null));
    assertEquals("", DataQualityHistoryDdlSupport.resolveSchema("  "));
    assertEquals("custom", DataQualityHistoryDdlSupport.resolveSchema(" custom "));
  }

  @Test
  void resolvePhysicalSchemaKeepsExplicitNames() {
    assertEquals(
        "",
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(
            null, databaseMetaWithPluginId("MYSQL")));
    assertEquals(
        "dv_ops",
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(
            "dv_ops", databaseMetaWithPluginId("POSTGRESQL")));
    assertEquals(
        "custom",
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(
            "custom", databaseMetaWithPluginId("MYSQL")));
  }

  private static void assertContainsTable(List<String> statements, String table) {
    assertTrue(
        statements.stream().anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS " + table)),
        "expected table " + table);
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
