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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Detects collation / Unicode-vs-ANSI differences on ORDER BY columns (SQL Server and PostgreSQL)
 * and resolves a bridge collation for generated {@code COLLATE} clauses.
 *
 * <p><b>Cross-engine (issue #108):</b> a SQL Server collation name such as {@code French_CI_AS}
 * must never appear in PostgreSQL {@code ORDER BY} (and vice versa). When source and target use
 * different database engines, no SQL bridge {@code COLLATE} is emitted — those names are not
 * portable. Same-engine remediation (SQL Server↔SQL Server, PostgreSQL↔PostgreSQL) is unchanged.
 */
public final class DvSqlOrderByCollationSupport {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvSqlOrderByCollation", LoggingObjectType.GENERAL, null);

  private DvSqlOrderByCollationSupport() {}

  /** Live SQL column type and collation metadata for one table column. */
  public record ColumnSqlMeta(String columnName, String typeName, String collationName) {

    public String normalizedType() {
      return DvSqlPhysicalTypeValidationSupport.normalizeSqlTypeName(typeName);
    }

    public String normalizedCollation() {
      if (Utils.isEmpty(collationName)) {
        return null;
      }
      return collationName.trim();
    }
  }

  /**
   * Per pipeline-generation / model-check session holding preloaded column metadata. Local to one
   * call stack; not shared across threads.
   */
  public static final class Session {
    private final Map<String, ColumnSqlMeta> sourceColumns;
    private final Map<String, ColumnSqlMeta> targetColumns;
    private final String sourceDbDefaultCollation;
    private final String targetDbDefaultCollation;
    private final String sourcePluginId;
    private final String targetPluginId;

    /** Compatibility constructor without engine ids (tests / callers that do not track engines). */
    public Session(
        Map<String, ColumnSqlMeta> sourceColumns,
        Map<String, ColumnSqlMeta> targetColumns,
        String sourceDbDefaultCollation,
        String targetDbDefaultCollation) {
      this(
          sourceColumns,
          targetColumns,
          sourceDbDefaultCollation,
          targetDbDefaultCollation,
          null,
          null);
    }

    public Session(
        Map<String, ColumnSqlMeta> sourceColumns,
        Map<String, ColumnSqlMeta> targetColumns,
        String sourceDbDefaultCollation,
        String targetDbDefaultCollation,
        String sourcePluginId,
        String targetPluginId) {
      this.sourceColumns =
          sourceColumns != null
              ? Collections.unmodifiableMap(new HashMap<>(sourceColumns))
              : Map.of();
      this.targetColumns =
          targetColumns != null
              ? Collections.unmodifiableMap(new HashMap<>(targetColumns))
              : Map.of();
      this.sourceDbDefaultCollation = sourceDbDefaultCollation;
      this.targetDbDefaultCollation = targetDbDefaultCollation;
      this.sourcePluginId = sourcePluginId;
      this.targetPluginId = targetPluginId;
    }

    public static Session empty() {
      return new Session(Map.of(), Map.of(), null, null, null, null);
    }

    public Map<String, ColumnSqlMeta> sourceColumns() {
      return sourceColumns;
    }

    public Map<String, ColumnSqlMeta> targetColumns() {
      return targetColumns;
    }

    public String sourceDbDefaultCollation() {
      return sourceDbDefaultCollation;
    }

    public String targetDbDefaultCollation() {
      return targetDbDefaultCollation;
    }

    public String sourcePluginId() {
      return sourcePluginId;
    }

    public String targetPluginId() {
      return targetPluginId;
    }

    /**
     * False when source and target are known to be different collation-capable engines (e.g. SQL
     * Server vs PostgreSQL). Unknown plugin ids are treated as not cross-engine so callers can
     * still apply a per-engine compatibility filter on the resolved name.
     */
    public boolean sameCollationEngineFamily() {
      DvSqlOrderBySupport.CollationEngineFamily sourceFamily =
          DvSqlOrderBySupport.collationEngineFamily(sourcePluginId);
      DvSqlOrderBySupport.CollationEngineFamily targetFamily =
          DvSqlOrderBySupport.collationEngineFamily(targetPluginId);
      if (sourceFamily == DvSqlOrderBySupport.CollationEngineFamily.UNKNOWN
          || targetFamily == DvSqlOrderBySupport.CollationEngineFamily.UNKNOWN) {
        return true;
      }
      return sourceFamily == targetFamily;
    }

    public ColumnSqlMeta sourceColumn(String name) {
      return lookup(sourceColumns, name);
    }

    public ColumnSqlMeta targetColumn(String name) {
      return lookup(targetColumns, name);
    }

    private static ColumnSqlMeta lookup(Map<String, ColumnSqlMeta> map, String name) {
      if (map == null || Utils.isEmpty(name)) {
        return null;
      }
      ColumnSqlMeta direct = map.get(name);
      if (direct != null) {
        return direct;
      }
      for (Map.Entry<String, ColumnSqlMeta> entry : map.entrySet()) {
        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }
  }

  /**
   * True when source/target ORDER BY columns may sort differently (collation and/or Unicode vs ANSI
   * string type).
   */
  public static boolean isOrderByRisk(ColumnSqlMeta source, ColumnSqlMeta target) {
    if (source == null && target == null) {
      return false;
    }
    String sourceType = source != null ? source.normalizedType() : null;
    String targetType = target != null ? target.normalizedType() : null;
    if (DvSqlPhysicalTypeValidationSupport.isSortSensitiveStringTypeMismatch(
        sourceType, targetType)) {
      return true;
    }
    String sourceCollation = source != null ? source.normalizedCollation() : null;
    String targetCollation = target != null ? target.normalizedCollation() : null;
    if (!Utils.isEmpty(sourceCollation)
        && !Utils.isEmpty(targetCollation)
        && !sourceCollation.equalsIgnoreCase(targetCollation)) {
      return true;
    }
    return false;
  }

  /**
   * Resolves the bridge collation to apply on an ORDER BY expression when a sort risk is present.
   * Prefer source column collation so both merge legs sort with source semantics. Returns null when
   * there is no risk, no collation name is available, or source/target engines differ (issue #108).
   */
  public static String resolveBridgeCollation(
      ColumnSqlMeta source,
      ColumnSqlMeta target,
      String sourceDbDefaultCollation,
      String targetDbDefaultCollation) {
    return resolveBridgeCollation(
        source, target, sourceDbDefaultCollation, targetDbDefaultCollation, null);
  }

  /**
   * @param session optional session; when present and source/target engines differ, returns null
   *     (collation names are not portable across engines)
   */
  public static String resolveBridgeCollation(
      ColumnSqlMeta source,
      ColumnSqlMeta target,
      String sourceDbDefaultCollation,
      String targetDbDefaultCollation,
      Session session) {
    if (session != null && !session.sameCollationEngineFamily()) {
      return null;
    }
    if (!isOrderByRisk(source, target)) {
      return null;
    }
    if (source != null && !Utils.isEmpty(source.normalizedCollation())) {
      return source.normalizedCollation();
    }
    if (target != null && !Utils.isEmpty(target.normalizedCollation())) {
      return target.normalizedCollation();
    }
    if (!Utils.isEmpty(sourceDbDefaultCollation)) {
      return sourceDbDefaultCollation.trim();
    }
    if (!Utils.isEmpty(targetDbDefaultCollation)) {
      return targetDbDefaultCollation.trim();
    }
    return null;
  }

  /**
   * True when {@code collation} is safe to use in {@code ORDER BY … COLLATE} on {@code
   * databaseMeta}. SQL Server and PostgreSQL collation name spaces are disjoint (issue #108).
   */
  public static boolean isCollationCompatibleWithEngine(
      DatabaseMeta databaseMeta, String collation) {
    if (databaseMeta == null || Utils.isEmpty(collation)) {
      return false;
    }
    String name = stripCollationQuotes(collation);
    if (Utils.isEmpty(name)) {
      return false;
    }
    if (DvSqlOrderBySupport.isSqlServer(databaseMeta)) {
      return looksLikeSqlServerCollation(name) && !looksLikePostgreSqlCollation(name);
    }
    if (DvSqlOrderBySupport.isPostgreSql(databaseMeta)) {
      return looksLikePostgreSqlCollation(name) && !looksLikeSqlServerCollation(name);
    }
    return false;
  }

  static String stripCollationQuotes(String collation) {
    if (Utils.isEmpty(collation)) {
      return collation;
    }
    String trimmed = collation.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
    }
    return trimmed;
  }

  /**
   * Heuristic for SQL Server collation identifiers ({@code French_CI_AS}, {@code
   * Latin1_General_100_CI_AS_SC_UTF8}, {@code SQL_Latin1_General_CP1_CI_AS}).
   *
   * <p>Does not treat bare locale names ({@code en_US}) as SQL Server — those are PostgreSQL-style.
   */
  static boolean looksLikeSqlServerCollation(String collation) {
    if (Utils.isEmpty(collation)) {
      return false;
    }
    String u = collation.toUpperCase(Locale.ROOT);
    // Clear PostgreSQL / ICU forms are not SQL Server collations.
    if (u.contains("-X-ICU") || collation.contains(".") || "C".equals(u) || "POSIX".equals(u)) {
      return false;
    }
    // Windows / SQL Server collations carry CI/CS/BIN (or well-known families). Avoid matching
    // PostgreSQL locales like en_US that only have an underscore.
    return u.contains("_CI_")
        || u.contains("_CS_")
        || u.contains("_BIN")
        || u.startsWith("SQL_")
        || u.contains("LATIN1_GENERAL")
        || u.startsWith("FRENCH_")
        || u.endsWith("_UTF8")
        || u.contains("_SC_")
        || u.endsWith("_CI_AS")
        || u.endsWith("_CS_AS")
        || u.endsWith("_CI_AI")
        || u.endsWith("_CS_AI");
  }

  /**
   * Heuristic for PostgreSQL collation names ({@code fr-FR-x-icu}, {@code en_US.utf8}, {@code C}).
   */
  static boolean looksLikePostgreSqlCollation(String collation) {
    if (Utils.isEmpty(collation)) {
      return false;
    }
    String u = collation.toUpperCase(Locale.ROOT);
    if ("C".equals(u) || "POSIX".equals(u) || "DEFAULT".equals(u) || "UNICODE".equals(u)) {
      return true;
    }
    if (u.contains("-X-ICU") || u.contains("X-ICU") || collation.contains(".")) {
      return true;
    }
    // BCP-47 / ICU style with hyphens but not SQL Server CI/CS tokens.
    if (collation.contains("-")
        && !u.contains("_CI_")
        && !u.contains("_CS_")
        && !u.contains("_BIN")) {
      return true;
    }
    // Locale-style without provider: en_US (no .utf8) — not SQL Server if no _CI_/_CS_/_BIN.
    if (collation.contains("_")
        && !u.contains("_CI_")
        && !u.contains("_CS_")
        && !u.contains("_BIN")
        && !u.startsWith("SQL_")
        && !u.contains("LATIN1_GENERAL")
        && !u.startsWith("FRENCH_")) {
      return true;
    }
    return false;
  }

  /**
   * Loads column type/collation maps for source and target tables when the connections support
   * ORDER BY COLLATE remediation (SQL Server, PostgreSQL). Failures return an empty session (never
   * throws for connectivity issues).
   */
  public static Session loadSession(
      DatabaseMeta sourceDatabaseMeta,
      String sourceSchema,
      String sourceTable,
      DatabaseMeta targetDatabaseMeta,
      String targetSchema,
      String targetTable,
      IVariables variables) {
    Map<String, ColumnSqlMeta> sourceColumns = Map.of();
    Map<String, ColumnSqlMeta> targetColumns = Map.of();
    String sourceDefault = null;
    String targetDefault = null;

    if (DvSqlOrderBySupport.isCollationOrderBySupported(sourceDatabaseMeta)
        && !Utils.isEmpty(sourceTable)) {
      sourceColumns = loadColumnMetaMap(sourceDatabaseMeta, variables, sourceSchema, sourceTable);
      sourceDefault = loadDatabaseDefaultCollation(sourceDatabaseMeta, variables);
    }
    if (DvSqlOrderBySupport.isCollationOrderBySupported(targetDatabaseMeta)
        && !Utils.isEmpty(targetTable)) {
      targetColumns = loadColumnMetaMap(targetDatabaseMeta, variables, targetSchema, targetTable);
      targetDefault = loadDatabaseDefaultCollation(targetDatabaseMeta, variables);
    }
    return new Session(
        sourceColumns,
        targetColumns,
        sourceDefault,
        targetDefault,
        pluginId(sourceDatabaseMeta),
        pluginId(targetDatabaseMeta));
  }

  private static String pluginId(DatabaseMeta databaseMeta) {
    return databaseMeta != null ? databaseMeta.getPluginId() : null;
  }

  /**
   * Loads column metadata for a collation-aware table. Returns empty map on failure or unsupported
   * engine.
   */
  public static Map<String, ColumnSqlMeta> loadColumnMetaMap(
      DatabaseMeta databaseMeta, IVariables variables, String schema, String table) {
    if (!DvSqlOrderBySupport.isCollationOrderBySupported(databaseMeta) || Utils.isEmpty(table)) {
      return Map.of();
    }
    String resolvedSchema = resolve(variables, schema);
    String resolvedTable = resolve(variables, table);
    if (Utils.isEmpty(resolvedTable)) {
      return Map.of();
    }
    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      String sql = buildColumnMetaQuery(resolvedSchema, resolvedTable);
      List<Object[]> rows = db.getRows(sql, 0);
      return parseColumnMetaRows(rows);
    } catch (Exception e) {
      return Map.of();
    }
  }

  public static String loadDatabaseDefaultCollation(
      DatabaseMeta databaseMeta, IVariables variables) {
    if (!DvSqlOrderBySupport.isCollationOrderBySupported(databaseMeta)) {
      return null;
    }
    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      String sql = buildDatabaseDefaultCollationQuery(databaseMeta);
      if (Utils.isEmpty(sql)) {
        return null;
      }
      List<Object[]> rows = db.getRows(sql, 1);
      if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).length == 0) {
        return null;
      }
      Object value = rows.get(0)[0];
      return value != null ? Const.NVL(value.toString(), "").trim() : null;
    } catch (Exception e) {
      return null;
    }
  }

  static String buildDatabaseDefaultCollationQuery(DatabaseMeta databaseMeta) {
    if (DvSqlOrderBySupport.isSqlServer(databaseMeta)) {
      return "SELECT CONVERT(varchar(128), DATABASEPROPERTYEX(DB_NAME(), 'Collation'))";
    }
    if (DvSqlOrderBySupport.isPostgreSql(databaseMeta)) {
      return "SELECT datcollate FROM pg_database WHERE datname = current_database()";
    }
    return null;
  }

  static String buildColumnMetaQuery(String schema, String table) {
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT COLUMN_NAME, DATA_TYPE, COLLATION_NAME ");
    sql.append("FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '");
    sql.append(escapeSqlLiteral(table));
    sql.append("'");
    if (!Utils.isEmpty(schema)) {
      sql.append(" AND TABLE_SCHEMA = '");
      sql.append(escapeSqlLiteral(schema));
      sql.append("'");
    }
    return sql.toString();
  }

  static Map<String, ColumnSqlMeta> parseColumnMetaRows(List<Object[]> rows) {
    if (rows == null || rows.isEmpty()) {
      return Map.of();
    }
    Map<String, ColumnSqlMeta> map = new HashMap<>();
    for (Object[] row : rows) {
      if (row == null || row.length < 1 || row[0] == null) {
        continue;
      }
      String name = row[0].toString();
      String type = row.length > 1 && row[1] != null ? row[1].toString() : null;
      String collation = row.length > 2 && row[2] != null ? row[2].toString() : null;
      if (!Utils.isEmpty(name)) {
        map.put(name, new ColumnSqlMeta(name, type, collation));
      }
    }
    return map;
  }

  static String escapeSqlLiteral(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("'", "''");
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  /** Case-insensitive key normalize for maps built from live meta. */
  public static String normalizeKey(String name) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    return name.trim();
  }

  static String describeRisk(ColumnSqlMeta source, ColumnSqlMeta target) {
    if (!isOrderByRisk(source, target)) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    String st = source != null ? source.normalizedType() : "?";
    String tt = target != null ? target.normalizedType() : "?";
    String sc = source != null ? Const.NVL(source.normalizedCollation(), "?") : "?";
    String tc = target != null ? Const.NVL(target.normalizedCollation(), "?") : "?";
    sb.append("source type=")
        .append(st)
        .append(" collation=")
        .append(sc)
        .append(", target type=")
        .append(tt)
        .append(" collation=")
        .append(tc);
    return sb.toString().toLowerCase(Locale.ROOT);
  }
}
