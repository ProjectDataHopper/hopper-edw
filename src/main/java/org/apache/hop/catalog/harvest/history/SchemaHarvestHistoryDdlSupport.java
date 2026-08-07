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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;

/**
 * Creates schema harvest history tables when missing. MySQL/SingleStore vs Postgres-style DDL (same
 * approach as quality history).
 */
public final class SchemaHarvestHistoryDdlSupport {

  private static final String MYSQL_PLUGIN_ID = "MYSQL";
  private static final String SINGLESTORE_PLUGIN_ID = "SINGLESTORE";

  private SchemaHarvestHistoryDdlSupport() {}

  public static void ensureTables(
      Database db, DatabaseMeta databaseMeta, String operationsSchema, ILogChannel log)
      throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String schema = resolveSchema(operationsSchema);
    if (allTablesExist(db, schema)) {
      return;
    }
    String ddl = String.join(";\n", buildCreateStatements(databaseMeta, schema)) + ";";
    if (log != null) {
      log.logBasic(
          "Creating schema harvest history tables in "
              + (Utils.isEmpty(schema) ? "connection default database" : schema)
              + " on "
              + databaseMeta.getName());
    }
    db.execStatements(ddl);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta) {
    return buildCreateStatements(databaseMeta, SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta, String operationsSchema) {
    String schema = resolveSchema(operationsSchema);
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";
    return switch (pluginId) {
      case MYSQL_PLUGIN_ID, SINGLESTORE_PLUGIN_ID -> mysqlStatements(schema);
      default -> postgresStatements(schema);
    };
  }

  static boolean allTablesExist(Database db, String schema) throws HopException {
    return db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN)
        && db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT)
        && db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD)
        && db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE)
        && db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK);
  }

  static String resolveSchema(String operationsSchema) {
    if (operationsSchema == null) {
      return SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME;
    }
    return operationsSchema.trim();
  }

  private static String qualify(String schema, String table) {
    if (Utils.isEmpty(schema)) {
      return table;
    }
    return schema + "." + table;
  }

  private static List<String> postgresStatements(String schema) {
    List<String> statements = new ArrayList<>();
    if (!Utils.isEmpty(schema)) {
      statements.add("CREATE SCHEMA IF NOT EXISTS " + schema);
    }
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id          VARCHAR(64)   NOT NULL,
          started_at              TIMESTAMP     NULL,
          finished_at             TIMESTAMP     NULL,
          resource_group_name     VARCHAR(255)  NULL,
          catalog_connection      VARCHAR(255)  NULL,
          expected_baseline       VARCHAR(128)  NULL,
          status                  VARCHAR(16)   NULL,
          subject_count           BIGINT        NULL,
          change_count            BIGINT        NULL,
          error_count             BIGINT        NULL,
          subjects_with_changes   BIGINT        NULL,
          workflow_name           VARCHAR(255)  NULL,
          workflow_execution_id   VARCHAR(64)   NULL,
          scope_summary           VARCHAR(2000) NULL,
          PRIMARY KEY (harvest_run_id)
        )"""
            .formatted(qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id       VARCHAR(64)   NOT NULL,
          subject_key          VARCHAR(512)  NOT NULL,
          catalog_connection   VARCHAR(255)  NULL,
          source_type          VARCHAR(32)   NULL,
          database_meta_name   VARCHAR(255)  NULL,
          schema_name          VARCHAR(255)  NULL,
          table_name           VARCHAR(255)  NULL,
          discovery_status     VARCHAR(32)   NULL,
          in_sync              BOOLEAN       NULL,
          change_count         BIGINT        NULL,
          message              VARCHAR(4000) NULL,
          PRIMARY KEY (harvest_run_id, subject_key)
        )"""
            .formatted(qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id         VARCHAR(64)  NOT NULL,
          subject_key            VARCHAR(512) NOT NULL,
          field_role             VARCHAR(16)  NOT NULL,
          field_name             VARCHAR(255) NOT NULL,
          hop_type               VARCHAR(64)  NULL,
          field_length           VARCHAR(64)  NULL,
          field_precision        VARCHAR(64)  NULL,
          primary_key_position   BIGINT       NULL,
          source_data_type       VARCHAR(128) NULL,
          PRIMARY KEY (harvest_run_id, subject_key, field_role, field_name)
        )"""
            .formatted(qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id    VARCHAR(64)   NOT NULL,
          change_seq        BIGINT        NOT NULL,
          subject_key       VARCHAR(512)  NULL,
          change_kind       VARCHAR(64)   NULL,
          field_name        VARCHAR(255)  NULL,
          expected_detail   VARCHAR(2000) NULL,
          actual_detail     VARCHAR(2000) NULL,
          severity          VARCHAR(16)   NULL,
          PRIMARY KEY (harvest_run_id, change_seq)
        )"""
            .formatted(qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id     VARCHAR(64)  NOT NULL,
          subject_key        VARCHAR(512) NOT NULL,
          field_role         VARCHAR(16)  NOT NULL,
          constraint_name    VARCHAR(255) NOT NULL,
          child_schema       VARCHAR(255) NULL,
          child_table        VARCHAR(255) NULL,
          child_columns      VARCHAR(2000) NULL,
          parent_schema      VARCHAR(255) NULL,
          parent_table       VARCHAR(255) NULL,
          parent_columns     VARCHAR(2000) NULL,
          PRIMARY KEY (harvest_run_id, subject_key, field_role, constraint_name)
        )"""
            .formatted(qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK)));
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_schema_harvest_run_group ON "
            + qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN)
            + " (resource_group_name)");
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_schema_harvest_subject_db ON "
            + qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT)
            + " (database_meta_name)");
    return statements;
  }

  private static List<String> mysqlStatements(String schema) {
    // Empty schema = connection default database; no CREATE DATABASE.
    List<String> statements = new ArrayList<>();
    String runTable = qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN);
    String subjectTable = qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT);
    String fieldTable = qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD);
    String changeTable = qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE);
    String fkTable = qualify(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK);
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id          VARCHAR(64)   NOT NULL,
          started_at              DATETIME      NULL,
          finished_at             DATETIME      NULL,
          resource_group_name     VARCHAR(255)  NULL,
          catalog_connection      VARCHAR(255)  NULL,
          expected_baseline       VARCHAR(128)  NULL,
          status                  VARCHAR(16)   NULL,
          subject_count           BIGINT        NULL,
          change_count            BIGINT        NULL,
          error_count             BIGINT        NULL,
          subjects_with_changes   BIGINT        NULL,
          workflow_name           VARCHAR(255)  NULL,
          workflow_execution_id   VARCHAR(64)   NULL,
          scope_summary           VARCHAR(2000) NULL,
          PRIMARY KEY (harvest_run_id)
        )"""
            .formatted(runTable));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id       VARCHAR(64)   NOT NULL,
          subject_key          VARCHAR(512)  NOT NULL,
          catalog_connection   VARCHAR(255)  NULL,
          source_type          VARCHAR(32)   NULL,
          database_meta_name   VARCHAR(255)  NULL,
          schema_name          VARCHAR(255)  NULL,
          table_name           VARCHAR(255)  NULL,
          discovery_status     VARCHAR(32)   NULL,
          in_sync              TINYINT(1)    NULL,
          change_count         BIGINT        NULL,
          message              VARCHAR(4000) NULL,
          PRIMARY KEY (harvest_run_id, subject_key)
        )"""
            .formatted(subjectTable));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id         VARCHAR(64)  NOT NULL,
          subject_key            VARCHAR(512) NOT NULL,
          field_role             VARCHAR(16)  NOT NULL,
          field_name             VARCHAR(255) NOT NULL,
          hop_type               VARCHAR(64)  NULL,
          field_length           VARCHAR(64)  NULL,
          field_precision        VARCHAR(64)  NULL,
          primary_key_position   BIGINT       NULL,
          source_data_type       VARCHAR(128) NULL,
          PRIMARY KEY (harvest_run_id, subject_key, field_role, field_name)
        )"""
            .formatted(fieldTable));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id    VARCHAR(64)   NOT NULL,
          change_seq        BIGINT        NOT NULL,
          subject_key       VARCHAR(512)  NULL,
          change_kind       VARCHAR(64)   NULL,
          field_name        VARCHAR(255)  NULL,
          expected_detail   VARCHAR(2000) NULL,
          actual_detail     VARCHAR(2000) NULL,
          severity          VARCHAR(16)   NULL,
          PRIMARY KEY (harvest_run_id, change_seq)
        )"""
            .formatted(changeTable));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          harvest_run_id     VARCHAR(64)  NOT NULL,
          subject_key        VARCHAR(512) NOT NULL,
          field_role         VARCHAR(16)  NOT NULL,
          constraint_name    VARCHAR(255) NOT NULL,
          child_schema       VARCHAR(255) NULL,
          child_table        VARCHAR(255) NULL,
          child_columns      VARCHAR(2000) NULL,
          parent_schema      VARCHAR(255) NULL,
          parent_table       VARCHAR(255) NULL,
          parent_columns     VARCHAR(2000) NULL,
          PRIMARY KEY (harvest_run_id, subject_key, field_role, constraint_name)
        )"""
            .formatted(fkTable));
    return statements;
  }
}
