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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Builds Table Input FROM clauses for {@link BvSourceQuery}: quoted table/view, or user SQL wrapped
 * as a subquery so the plugin can add WHERE / ORDER BY outside the inner statement.
 */
public final class BvSourceQuerySqlSupport {

  public static final String DEFAULT_ALIAS = "src";

  private BvSourceQuerySqlSupport() {}

  /** Copies Hop query metadata into a persisted source-query column (type, length, precision). */
  public static BvSourceQueryColumn fromValueMeta(IValueMeta valueMeta) {
    if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
      return null;
    }
    BvSourceQueryColumn column = new BvSourceQueryColumn(valueMeta.getName());
    column.setDataType(ValueMetaFactory.getValueMetaName(valueMeta.getType()));
    if (valueMeta.getLength() >= 0) {
      column.setLength(Integer.toString(valueMeta.getLength()));
    }
    if (valueMeta.getPrecision() >= 0) {
      column.setPrecision(Integer.toString(valueMeta.getPrecision()));
    }
    return column;
  }

  public static String stripTrailingSemicolon(String sql) {
    if (sql == null) {
      return null;
    }
    String trimmed = sql.trim();
    while (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  public static String wrapSqlAsSubquery(String sql, String alias) {
    String inner = stripTrailingSemicolon(sql);
    if (Utils.isEmpty(inner)) {
      return inner;
    }
    String quotedAlias = sanitizeAlias(alias);
    return "(" + inner + ") " + quotedAlias;
  }

  public static String quotedTable(
      DatabaseMeta databaseMeta, IVariables variables, String schemaName, String tableName) {
    if (databaseMeta == null || Utils.isEmpty(tableName)) {
      return tableName;
    }
    return databaseMeta.getQuotedSchemaTableCombination(variables, schemaName, tableName);
  }

  /**
   * FROM clause for a source query. TABLE mode is a quoted schema.table. SQL mode is always {@code
   * ( sql ) alias} so inner WITH/ORDER BY cannot collide with the outer clauses.
   */
  public static String fromClause(
      DatabaseMeta databaseMeta, IVariables variables, BvSourceQuery sourceQuery) {
    return fromClause(databaseMeta, variables, sourceQuery, DEFAULT_ALIAS);
  }

  public static String fromClause(
      DatabaseMeta databaseMeta, IVariables variables, BvSourceQuery sourceQuery, String alias) {
    if (sourceQuery == null) {
      return null;
    }
    if (sourceQuery.isSqlSource()) {
      return wrapSqlAsSubquery(sourceQuery.getSqlQuery(), alias);
    }
    String tableName =
        !Utils.isEmpty(sourceQuery.getTableName())
            ? sourceQuery.getTableName()
            : sourceQuery.getName();
    return quotedTable(databaseMeta, variables, sourceQuery.getSchemaName(), tableName);
  }

  public static String previewSql(
      DatabaseMeta databaseMeta, IVariables variables, BvSourceQuery sourceQuery) {
    if (sourceQuery == null) {
      return null;
    }
    if (sourceQuery.isSqlSource()) {
      return stripTrailingSemicolon(sourceQuery.getSqlQuery());
    }
    String from = fromClause(databaseMeta, variables, sourceQuery);
    if (Utils.isEmpty(from)) {
      return null;
    }
    return "SELECT * FROM " + from;
  }

  public static String selectExpression(
      DatabaseMeta databaseMeta, String sourceField, String outputField) {
    if (Utils.isEmpty(sourceField)) {
      return sourceField;
    }
    String quotedSource = databaseMeta != null ? databaseMeta.quoteField(sourceField) : sourceField;
    if (Utils.isEmpty(outputField) || sourceField.equals(outputField)) {
      return quotedSource;
    }
    String quotedOutput = databaseMeta != null ? databaseMeta.quoteField(outputField) : outputField;
    return quotedSource + " AS " + quotedOutput;
  }

  public static String buildSelect(
      List<String> selectExpressions,
      String fromClause,
      List<String> whereClauses,
      List<String> orderBy) {
    StringBuilder sql = new StringBuilder("SELECT ");
    if (selectExpressions == null || selectExpressions.isEmpty()) {
      sql.append("*");
    } else {
      sql.append(String.join(", ", selectExpressions));
    }
    sql.append(" FROM ");
    sql.append(fromClause);
    if (whereClauses != null && !whereClauses.isEmpty()) {
      sql.append(" WHERE ");
      sql.append(String.join(" AND ", whereClauses));
    }
    if (orderBy != null && !orderBy.isEmpty()) {
      sql.append(" ORDER BY ");
      sql.append(String.join(", ", orderBy));
    }
    return sql.toString();
  }

  public static List<String> attributeFieldNames(BvSourceQuery sourceQuery, IVariables variables) {
    List<String> names = new ArrayList<>();
    if (sourceQuery == null) {
      return names;
    }
    String hashKey = resolve(sourceQuery.getHashKeyField(), variables);
    String functionalTs = resolve(sourceQuery.getFunctionalTimestampField(), variables);
    String loadDate = resolve(sourceQuery.getLoadDateField(), variables);
    for (String name : sourceQuery.columnNames()) {
      if (name.equalsIgnoreCase(hashKey)
          || name.equalsIgnoreCase(functionalTs)
          || name.equalsIgnoreCase(loadDate)) {
        continue;
      }
      names.add(name);
    }
    return names;
  }

  private static String sanitizeAlias(String alias) {
    if (Utils.isEmpty(alias)) {
      return DEFAULT_ALIAS;
    }
    return alias.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
