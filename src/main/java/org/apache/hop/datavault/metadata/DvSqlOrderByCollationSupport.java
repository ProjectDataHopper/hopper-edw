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

    public Session(
        Map<String, ColumnSqlMeta> sourceColumns,
        Map<String, ColumnSqlMeta> targetColumns,
        String sourceDbDefaultCollation,
        String targetDbDefaultCollation) {
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
    }

    public static Session empty() {
      return new Session(Map.of(), Map.of(), null, null);
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
   * there is no risk or no collation name is available.
   */
  public static String resolveBridgeCollation(
      ColumnSqlMeta source,
      ColumnSqlMeta target,
      String sourceDbDefaultCollation,
      String targetDbDefaultCollation) {
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
    return new Session(sourceColumns, targetColumns, sourceDefault, targetDefault);
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
