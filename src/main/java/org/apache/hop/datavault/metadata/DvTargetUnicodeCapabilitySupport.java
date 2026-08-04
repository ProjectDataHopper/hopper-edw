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
package org.apache.hop.datavault.metadata;

import java.util.List;
import java.util.Locale;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.i18n.BaseMessages;

/**
 * Validates that a DV/BV/DM target database can store full Unicode in string columns — a baseline
 * requirement for an enterprise data warehouse.
 *
 * <ul>
 *   <li>SQL Server: database default collation ends with {@code _UTF8} (SQL Server 2019+ UTF-8
 *       collations for VARCHAR)
 *   <li>PostgreSQL: database encoding is UTF8
 *   <li>MySQL / MariaDB / SingleStore: schema character set is utf8mb4
 * </ul>
 */
public final class DvTargetUnicodeCapabilitySupport {

  private static final Class<?> PKG = DvTargetUnicodeCapabilitySupport.class;

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvTargetUnicodeCapability", LoggingObjectType.GENERAL, null);

  /** Outcome of a live (or offline) Unicode capability probe. */
  public enum Status {
    CAPABLE,
    NOT_CAPABLE,
    UNSUPPORTED_ENGINE,
    CONNECTION_FAILED,
    UNKNOWN
  }

  /** Result of probing a target database for Unicode string storage capability. */
  public record Assessment(Status status, String detail, String remediation) {

    public boolean isCapable() {
      return status == Status.CAPABLE || status == Status.UNSUPPORTED_ENGINE;
    }

    public boolean isHardFailure() {
      return status == Status.NOT_CAPABLE;
    }
  }

  private DvTargetUnicodeCapabilitySupport() {}

  /**
   * Connects to the target database and appends model-check remarks. Hard errors only when the
   * engine is proven non-Unicode-capable; connection failures are warnings so offline editing is
   * still possible.
   */
  public static void checkTargetUnicodeCapability(
      List<ICheckResult> remarks,
      DatabaseMeta targetDatabase,
      IVariables variables,
      String targetDatabaseName) {
    if (remarks == null) {
      return;
    }
    if (targetDatabase == null) {
      return;
    }
    Assessment assessment = assess(targetDatabase, variables);
    addRemark(remarks, assessment, targetDatabaseName, targetDatabase.getPluginId());
  }

  public static void addRemark(
      List<ICheckResult> remarks,
      Assessment assessment,
      String targetDatabaseName,
      String pluginId) {
    addRemark(remarks, assessment, targetDatabaseName, pluginId, isEnforcementEnabled());
  }

  /**
   * @param enforceHardFailure when false, {@link Status#NOT_CAPABLE} is reported as a warning so
   *     model check can continue (global plugin config).
   */
  public static void addRemark(
      List<ICheckResult> remarks,
      Assessment assessment,
      String targetDatabaseName,
      String pluginId,
      boolean enforceHardFailure) {
    if (remarks == null || assessment == null) {
      return;
    }
    String name = Const.NVL(targetDatabaseName, "");
    String engine = Const.NVL(pluginId, "");
    switch (assessment.status()) {
      case CAPABLE ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(
                      PKG,
                      "DvTargetUnicodeCapabilitySupport.CheckResult.Capable",
                      name,
                      Const.NVL(assessment.detail(), engine)),
                  null));
      case NOT_CAPABLE -> {
        int type =
            enforceHardFailure ? ICheckResult.TYPE_RESULT_ERROR : ICheckResult.TYPE_RESULT_WARNING;
        String messageKey =
            enforceHardFailure
                ? "DvTargetUnicodeCapabilitySupport.CheckResult.NotCapable"
                : "DvTargetUnicodeCapabilitySupport.CheckResult.NotCapableWarning";
        remarks.add(
            new CheckResult(
                type,
                BaseMessages.getString(
                    PKG,
                    messageKey,
                    name,
                    Const.NVL(assessment.detail(), ""),
                    Const.NVL(assessment.remediation(), "")),
                null));
      }
      case CONNECTION_FAILED ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DvTargetUnicodeCapabilitySupport.CheckResult.ConnectionFailed",
                      name,
                      Const.NVL(assessment.detail(), "")),
                  null));
      case UNKNOWN ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DvTargetUnicodeCapabilitySupport.CheckResult.Unknown",
                      name,
                      Const.NVL(assessment.detail(), "")),
                  null));
      case UNSUPPORTED_ENGINE ->
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(
                      PKG,
                      "DvTargetUnicodeCapabilitySupport.CheckResult.UnsupportedEngineSkipped",
                      name,
                      engine),
                  null));
      default -> {
        // no-op
      }
    }
  }

  /** Global plugin config: hard-fail Unicode target check when enabled (default true). */
  public static boolean isEnforcementEnabled() {
    try {
      return DataVaultConfigSingleton.getConfig().isEnforceTargetUnicodeCapability();
    } catch (Exception e) {
      return true;
    }
  }

  /** Live assessment: connects and queries engine-specific encoding/collation metadata. */
  public static Assessment assess(DatabaseMeta databaseMeta, IVariables variables) {
    if (databaseMeta == null) {
      return new Assessment(Status.UNKNOWN, "no database metadata", null);
    }
    String pluginId = databaseMeta.getPluginId();
    if (Utils.isEmpty(pluginId)) {
      return new Assessment(Status.UNKNOWN, "unknown database type", null);
    }

    if (!isUnicodeCheckSupported(databaseMeta)) {
      return new Assessment(
          Status.UNSUPPORTED_ENGINE, pluginId, "No automated Unicode check for this engine");
    }

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      String sql = buildCapabilityQuery(databaseMeta);
      if (Utils.isEmpty(sql)) {
        return new Assessment(Status.UNKNOWN, "no capability query for " + pluginId, null);
      }
      List<Object[]> rows = db.getRows(sql, 1);
      String value = firstCell(rows);
      return evaluate(databaseMeta, value);
    } catch (Exception e) {
      String message = e.getMessage();
      if (Utils.isEmpty(message) && e.getCause() != null) {
        message = e.getCause().getMessage();
      }
      return new Assessment(
          Status.CONNECTION_FAILED, Const.NVL(message, e.getClass().getSimpleName()), null);
    }
  }

  public static boolean isUnicodeCheckSupported(DatabaseMeta databaseMeta) {
    return DvSqlOrderBySupport.isSqlServer(databaseMeta)
        || DvSqlOrderBySupport.isPostgreSql(databaseMeta)
        || isMySqlFamily(databaseMeta);
  }

  public static boolean isMySqlFamily(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || "MARIADB".equalsIgnoreCase(pluginId);
  }

  static String buildCapabilityQuery(DatabaseMeta databaseMeta) {
    if (DvSqlOrderBySupport.isSqlServer(databaseMeta)) {
      return "SELECT CONVERT(varchar(128), DATABASEPROPERTYEX(DB_NAME(), 'Collation'))";
    }
    if (DvSqlOrderBySupport.isPostgreSql(databaseMeta)) {
      return "SELECT pg_encoding_to_char(encoding) FROM pg_database WHERE datname = current_database()";
    }
    if (isMySqlFamily(databaseMeta)) {
      return "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()";
    }
    return null;
  }

  /**
   * Pure evaluation of a probed encoding/collation value for unit tests without a live connection.
   */
  public static Assessment evaluate(DatabaseMeta databaseMeta, String probedValue) {
    if (DvSqlOrderBySupport.isSqlServer(databaseMeta)) {
      return evaluateSqlServer(probedValue);
    }
    if (DvSqlOrderBySupport.isPostgreSql(databaseMeta)) {
      return evaluatePostgreSql(probedValue);
    }
    if (isMySqlFamily(databaseMeta)) {
      return evaluateMySqlFamily(probedValue);
    }
    return new Assessment(
        Status.UNSUPPORTED_ENGINE, databaseMeta != null ? databaseMeta.getPluginId() : null, null);
  }

  static Assessment evaluateSqlServer(String collation) {
    if (Utils.isEmpty(collation)) {
      return new Assessment(
          Status.UNKNOWN,
          "empty database collation",
          "Create the vault database with a UTF-8 collation such as "
              + DvDdlSupport.SQL_SERVER_UTF8_COLLATION
              + " (SQL Server 2019+).");
    }
    String trimmed = collation.trim();
    if (isSqlServerUtf8Collation(trimmed)) {
      return new Assessment(Status.CAPABLE, "collation=" + trimmed, null);
    }
    return new Assessment(
        Status.NOT_CAPABLE,
        "collation=" + trimmed,
        "Recreate or migrate the target database with a UTF-8 collation such as "
            + DvDdlSupport.SQL_SERVER_UTF8_COLLATION
            + " (SQL Server 2019+). Example: CREATE DATABASE my_edw COLLATE "
            + DvDdlSupport.SQL_SERVER_UTF8_COLLATION
            + "; Generated VARCHAR columns also use this collation.");
  }

  static Assessment evaluatePostgreSql(String encoding) {
    if (Utils.isEmpty(encoding)) {
      return new Assessment(
          Status.UNKNOWN, "empty database encoding", "Create the database with ENCODING 'UTF8'.");
    }
    String normalized = encoding.trim().toUpperCase(Locale.ROOT);
    if ("UTF8".equals(normalized) || "UTF-8".equals(normalized)) {
      return new Assessment(Status.CAPABLE, "encoding=" + encoding.trim(), null);
    }
    return new Assessment(
        Status.NOT_CAPABLE,
        "encoding=" + encoding.trim(),
        "Recreate the target database with UTF-8 encoding, for example: CREATE DATABASE my_edw WITH ENCODING 'UTF8';");
  }

  static Assessment evaluateMySqlFamily(String charset) {
    if (Utils.isEmpty(charset)) {
      return new Assessment(
          Status.UNKNOWN,
          "empty schema character set",
          "Use CHARACTER SET utf8mb4 for the target schema.");
    }
    String normalized = charset.trim().toLowerCase(Locale.ROOT);
    if ("utf8mb4".equals(normalized)) {
      return new Assessment(Status.CAPABLE, "charset=" + charset.trim(), null);
    }
    return new Assessment(
        Status.NOT_CAPABLE,
        "charset=" + charset.trim(),
        "Alter or recreate the target schema with utf8mb4, for example: "
            + "CREATE DATABASE my_edw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
            + "(utf8/latin1 is not sufficient for full Unicode in an EDW.)");
  }

  /** True when a SQL Server collation name is a UTF-8 enabled collation. */
  public static boolean isSqlServerUtf8Collation(String collation) {
    if (Utils.isEmpty(collation)) {
      return false;
    }
    return collation.trim().toUpperCase(Locale.ROOT).endsWith("_UTF8");
  }

  private static String firstCell(List<Object[]> rows) {
    if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).length == 0) {
      return null;
    }
    Object value = rows.get(0)[0];
    return value != null ? Const.NVL(value.toString(), "").trim() : null;
  }
}
