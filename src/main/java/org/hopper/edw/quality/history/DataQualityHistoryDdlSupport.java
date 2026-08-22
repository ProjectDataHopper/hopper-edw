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
package org.hopper.edw.quality.history;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;

/**
 * Creates the five quality history tables when missing. Does not depend on {@code
 * datavault.metrics}; MySQL/Postgres dialect switch is local to this class.
 *
 * <p>Empty operations schema means connection default database (unqualified tables). Explicit
 * schema names are honored for users who opt into isolation.
 */
public final class DataQualityHistoryDdlSupport {

  private static final String MYSQL_PLUGIN_ID = "MYSQL";
  private static final String SINGLESTORE_PLUGIN_ID = "SINGLESTORE";

  private DataQualityHistoryDdlSupport() {}

  public static void ensureTables(Database db, DatabaseMeta databaseMeta, ILogChannel log)
      throws HopException {
    ensureTables(db, databaseMeta, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME, log);
  }

  public static void ensureTables(
      Database db, DatabaseMeta databaseMeta, String operationsSchema, ILogChannel log)
      throws HopException {
    if (db == null || databaseMeta == null) {
      return;
    }
    String schema = resolvePhysicalSchema(operationsSchema, databaseMeta);
    if (allQualityTablesExist(db, schema)) {
      return;
    }

    String ddl = String.join(";\n", buildCreateStatements(databaseMeta, schema)) + ";";
    if (log != null) {
      log.logBasic(
          "Creating quality history tables in "
              + describeLocation(schema)
              + " on "
              + databaseMeta.getName());
    }
    db.execStatements(ddl);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta) {
    return buildCreateStatements(databaseMeta, DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME);
  }

  static List<String> buildCreateStatements(DatabaseMeta databaseMeta, String operationsSchema) {
    String schema = resolvePhysicalSchema(operationsSchema, databaseMeta);
    String pluginId =
        databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())
            ? databaseMeta.getPluginId().toUpperCase()
            : "";
    return switch (pluginId) {
      case MYSQL_PLUGIN_ID, SINGLESTORE_PLUGIN_ID -> mysqlStatements(schema);
      default -> postgresStatements(schema);
    };
  }

  static boolean allQualityTablesExist(Database db, String schema) throws HopException {
    return db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
        && db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT)
        && db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD)
        && db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)
        && db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_ALERT);
  }

  /** Blank → connection default; explicit names kept (opt-in isolation). */
  static String resolveSchema(String operationsSchema) {
    if (operationsSchema == null) {
      return DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;
    }
    return operationsSchema.trim();
  }

  /**
   * Physical schema/database qualifier for SQL. Empty means the connection default; no dialect
   * remapping of legacy names.
   */
  static String resolvePhysicalSchema(String operationsSchema, DatabaseMeta databaseMeta) {
    return resolveSchema(operationsSchema);
  }

  private static boolean isMysqlFamily(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return MYSQL_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || SINGLESTORE_PLUGIN_ID.equalsIgnoreCase(pluginId);
  }

  private static String qualify(String schema, String table) {
    if (Utils.isEmpty(schema)) {
      return table;
    }
    return schema + "." + table;
  }

  private static String describeLocation(String schema) {
    if (Utils.isEmpty(schema)) {
      return "connection default database";
    }
    return schema;
  }

  private static List<String> postgresStatements(String schema) {
    List<String> statements = new ArrayList<>();
    if (!Utils.isEmpty(schema)) {
      statements.add("CREATE SCHEMA IF NOT EXISTS " + schema);
    }
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id        VARCHAR(64)   NOT NULL,
          measured_at           TIMESTAMP     NULL,
          lifecycle             VARCHAR(16)   NULL,
          evaluation_mode       VARCHAR(32)   NULL,
          load_id               VARCHAR(64)   NULL,
          workflow_name         VARCHAR(255)  NULL,
          workflow_execution_id VARCHAR(64)   NULL,
          subject_count         BIGINT        NULL,
          finding_count         BIGINT        NULL,
          blocking_count        BIGINT        NULL,
          warning_count         BIGINT        NULL,
          info_count            BIGINT        NULL,
          infra_error_count     BIGINT        NULL,
          success               BOOLEAN       NULL,
          subjects_json         VARCHAR(4000) NULL,
          infra_errors_json     VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id  VARCHAR(64)  NOT NULL,
          subject_key     VARCHAR(512) NOT NULL,
          row_count       BIGINT       NULL,
          row_count_exact BOOLEAN      NULL,
          evaluation_mode VARCHAR(32)  NULL,
          lifecycle       VARCHAR(16)  NULL,
          captured_at     TIMESTAMP    NULL,
          PRIMARY KEY (quality_run_id, subject_key)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id       VARCHAR(64)   NOT NULL,
          subject_key          VARCHAR(512)  NOT NULL,
          field_name           VARCHAR(255)  NOT NULL,
          null_count           BIGINT        NULL,
          empty_string_count   BIGINT        NULL,
          non_null_count       BIGINT        NULL,
          exact_distinct_count BIGINT        NULL,
          distinct_truncated   BOOLEAN       NULL,
          min_value            VARCHAR(500)  NULL,
          max_value            VARCHAR(500)  NULL,
          min_string_length    BIGINT        NULL,
          max_string_length    BIGINT        NULL,
          top_values_json      VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id, subject_key, field_name)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id   VARCHAR(64)   NOT NULL,
          finding_seq      BIGINT        NOT NULL,
          subject_key      VARCHAR(512)  NULL,
          rule_id          VARCHAR(128)  NULL,
          rule_name        VARCHAR(255)  NULL,
          rule_type        VARCHAR(64)   NULL,
          severity         VARCHAR(16)   NULL,
          field_name       VARCHAR(255)  NULL,
          message          VARCHAR(2000) NULL,
          actual_summary   VARCHAR(1000) NULL,
          expected_summary VARCHAR(1000) NULL,
          metrics_json     VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id, finding_seq)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id     VARCHAR(64)   NOT NULL,
          alerted_at         TIMESTAMP     NULL,
          disposition_mode   VARCHAR(32)   NULL,
          disposition_failed BOOLEAN       NULL,
          summary            VARCHAR(2000) NULL,
          PRIMARY KEY (quality_run_id)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_ALERT)));
    statements.addAll(postgresIndexStatements(schema));
    return statements;
  }

  private static List<String> mysqlStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id        VARCHAR(64)   NOT NULL,
          measured_at           TIMESTAMP     NULL,
          lifecycle             VARCHAR(16)   NULL,
          evaluation_mode       VARCHAR(32)   NULL,
          load_id               VARCHAR(64)   NULL,
          workflow_name         VARCHAR(255)  NULL,
          workflow_execution_id VARCHAR(64)   NULL,
          subject_count         BIGINT        NULL,
          finding_count         BIGINT        NULL,
          blocking_count        BIGINT        NULL,
          warning_count         BIGINT        NULL,
          info_count            BIGINT        NULL,
          infra_error_count     BIGINT        NULL,
          success               TINYINT(1)    NULL,
          subjects_json         VARCHAR(4000) NULL,
          infra_errors_json     VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id  VARCHAR(64)  NOT NULL,
          subject_key     VARCHAR(512) NOT NULL,
          row_count       BIGINT       NULL,
          row_count_exact TINYINT(1)   NULL,
          evaluation_mode VARCHAR(32)  NULL,
          lifecycle       VARCHAR(16)  NULL,
          captured_at     TIMESTAMP    NULL,
          PRIMARY KEY (quality_run_id, subject_key)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id       VARCHAR(64)   NOT NULL,
          subject_key          VARCHAR(512)  NOT NULL,
          field_name           VARCHAR(255)  NOT NULL,
          null_count           BIGINT        NULL,
          empty_string_count   BIGINT        NULL,
          non_null_count       BIGINT        NULL,
          exact_distinct_count BIGINT        NULL,
          distinct_truncated   TINYINT(1)    NULL,
          min_value            VARCHAR(500)  NULL,
          max_value            VARCHAR(500)  NULL,
          min_string_length    BIGINT        NULL,
          max_string_length    BIGINT        NULL,
          top_values_json      VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id, subject_key, field_name)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_FIELD)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id   VARCHAR(64)   NOT NULL,
          finding_seq      BIGINT        NOT NULL,
          subject_key      VARCHAR(512)  NULL,
          rule_id          VARCHAR(128)  NULL,
          rule_name        VARCHAR(255)  NULL,
          rule_type        VARCHAR(64)   NULL,
          severity         VARCHAR(16)   NULL,
          field_name       VARCHAR(255)  NULL,
          message          VARCHAR(2000) NULL,
          actual_summary   VARCHAR(1000) NULL,
          expected_summary VARCHAR(1000) NULL,
          metrics_json     VARCHAR(4000) NULL,
          PRIMARY KEY (quality_run_id, finding_seq)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)));
    statements.add(
        """
        CREATE TABLE IF NOT EXISTS %s (
          quality_run_id     VARCHAR(64)   NOT NULL,
          alerted_at         TIMESTAMP     NULL,
          disposition_mode   VARCHAR(32)   NULL,
          disposition_failed TINYINT(1)    NULL,
          summary            VARCHAR(2000) NULL,
          PRIMARY KEY (quality_run_id)
        )"""
            .formatted(qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_ALERT)));
    // MySQL does not support CREATE INDEX IF NOT EXISTS. These run only on the first-create
    // path (when not all five tables exist), so plain CREATE INDEX is safe.
    statements.addAll(mysqlIndexStatements(schema));
    return statements;
  }

  /** Postgres/H2: IF NOT EXISTS + DESC on time columns per design §2.3. */
  private static List<String> postgresIndexStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_quality_run_load_id ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
            + " (load_id)");
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_quality_run_measured_at ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
            + " (measured_at DESC)");
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_quality_profile_subject_lookup ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT)
            + " (subject_key, lifecycle, captured_at DESC)");
    statements.add(
        "CREATE INDEX IF NOT EXISTS idx_quality_finding_subject_rule ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)
            + " (subject_key, rule_id)");
    return statements;
  }

  /** MySQL/SingleStore: plain CREATE INDEX (no IF NOT EXISTS — unsupported). */
  private static List<String> mysqlIndexStatements(String schema) {
    List<String> statements = new ArrayList<>();
    statements.add(
        "CREATE INDEX idx_quality_run_load_id ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
            + " (load_id)");
    statements.add(
        "CREATE INDEX idx_quality_run_measured_at ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
            + " (measured_at DESC)");
    statements.add(
        "CREATE INDEX idx_quality_profile_subject_lookup ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT)
            + " (subject_key, lifecycle, captured_at DESC)");
    statements.add(
        "CREATE INDEX idx_quality_finding_subject_rule ON "
            + qualify(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)
            + " (subject_key, rule_id)");
    return statements;
  }
}
