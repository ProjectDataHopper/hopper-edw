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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Builds a single-connection SQL statement for a {@link SourceQuery}.
 *
 * <p>Uses {@link DatabaseMeta} quoting when provided; otherwise emits unquoted identifiers (unit
 * tests).
 */
public final class SourceQuerySqlGenerator {

  private SourceQuerySqlGenerator() {}

  public static String generate(
      SourceModel model, SourceQuery query, DatabaseMeta databaseMeta, IVariables variables)
      throws HopException {
    if (model == null || query == null) {
      throw new HopException("Source model and query are required for SQL generation");
    }
    if (!SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)) {
      throw new HopException(
          "Query '"
              + query.getName()
              + "' cannot generate single-connection SQL (mixed sources or connections)");
    }
    if (query.getColumns().isEmpty()) {
      throw new HopException("Query '" + query.getName() + "' has no projected columns");
    }

    Map<String, String> aliasByTable = assignAliases(model, query);
    String driving = query.getDrivingTableName().trim();
    SourceTable drivingTable = model.findTable(driving);
    if (drivingTable == null) {
      throw new HopException("Driving table '" + driving + "' not found");
    }

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ");
    boolean firstCol = true;
    Set<String> usedAliases = new LinkedHashSet<>();
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.getColumnName())) {
        continue;
      }
      String tableName = column.getTableName();
      if (Utils.isEmpty(tableName)) {
        throw new HopException("Projected column is missing table name");
      }
      String tableAlias = aliasByTable.get(tableName.trim());
      if (tableAlias == null) {
        throw new HopException(
            "Projected column table '" + tableName + "' is not a query participant");
      }
      String outAlias = uniqueOutputAlias(column.resolveAlias(), usedAliases);
      if (!firstCol) {
        sql.append(", ");
      }
      firstCol = false;
      sql.append(tableAlias)
          .append('.')
          .append(quoteField(databaseMeta, column.getColumnName().trim()));
      if (!outAlias.equalsIgnoreCase(column.getColumnName().trim())) {
        sql.append(" AS ").append(quoteField(databaseMeta, outAlias));
      } else {
        // Always alias for stable feed field names when multi-table.
        sql.append(" AS ").append(quoteField(databaseMeta, outAlias));
      }
    }
    if (firstCol) {
      throw new HopException("Query has no valid projected columns");
    }

    sql.append(" FROM ").append(qualifiedTable(databaseMeta, variables, drivingTable));
    sql.append(' ').append(aliasByTable.get(driving));

    Set<String> inScope = new LinkedHashSet<>();
    inScope.add(driving);

    for (SourceQueryJoin join : query.getJoins()) {
      if (join == null || Utils.isEmpty(join.getTableName())) {
        continue;
      }
      String rightTableName = join.getTableName().trim();
      SourceTable rightTable = model.findTable(rightTableName);
      if (rightTable == null) {
        throw new HopException("Join table '" + rightTableName + "' not found");
      }
      SourceQueryJoinKeyResolver.ResolvedJoinKeys keys =
          SourceQueryJoinKeyResolver.resolve(model, join, inScope);
      if (!keys.isValid()) {
        throw new HopException("Invalid join keys for table '" + rightTableName + "'");
      }

      String rightAlias = aliasByTable.get(rightTableName);
      sql.append(' ')
          .append(sqlJoinKeyword(join.resolveJoinType()))
          .append(' ')
          .append(qualifiedTable(databaseMeta, variables, rightTable))
          .append(' ')
          .append(rightAlias)
          .append(" ON ");
      for (int i = 0; i < keys.leftColumns().size(); i++) {
        if (i > 0) {
          sql.append(" AND ");
        }
        String leftTable = keys.leftTables().get(i);
        String leftAlias = aliasByTable.get(leftTable);
        if (leftAlias == null) {
          throw new HopException("Left join table '" + leftTable + "' is not in scope");
        }
        sql.append(leftAlias)
            .append('.')
            .append(quoteField(databaseMeta, keys.leftColumns().get(i)))
            .append(" = ")
            .append(rightAlias)
            .append('.')
            .append(quoteField(databaseMeta, keys.rightColumns().get(i)));
      }
      inScope.add(rightTableName);
    }

    if (!Utils.isEmpty(query.getWhereClause())) {
      String where = query.getWhereClause().trim();
      if (where.toUpperCase(Locale.ROOT).startsWith("WHERE ")) {
        where = where.substring(6).trim();
      }
      if (!where.isEmpty()) {
        sql.append(" WHERE ").append(where);
      }
    }

    return sql.toString();
  }

  static Map<String, String> assignAliases(SourceModel model, SourceQuery query) {
    Map<String, String> aliasByTable = new LinkedHashMap<>();
    List<String> participants = SourceQueryGenerationSupport.participantTableNames(query);
    Set<String> used = new LinkedHashSet<>();
    for (String tableName : participants) {
      String base = aliasBase(tableName);
      String alias = base;
      int suffix = 1;
      while (used.contains(alias)) {
        alias = base + suffix;
        suffix++;
      }
      used.add(alias);
      aliasByTable.put(tableName, alias);
    }
    return aliasByTable;
  }

  private static String aliasBase(String tableName) {
    String cleaned = tableName.replaceAll("[^A-Za-z0-9_]", "");
    if (cleaned.isEmpty()) {
      return "t";
    }
    if (cleaned.length() > 12) {
      cleaned = cleaned.substring(0, 12);
    }
    // SQL aliases shouldn't start with a digit.
    if (Character.isDigit(cleaned.charAt(0))) {
      cleaned = "t" + cleaned;
    }
    return cleaned.toLowerCase(Locale.ROOT);
  }

  private static String uniqueOutputAlias(String preferred, Set<String> used) {
    String base = Utils.isEmpty(preferred) ? "col" : preferred.trim();
    String candidate = base;
    int i = 2;
    while (used.contains(candidate.toLowerCase(Locale.ROOT))) {
      candidate = base + "_" + i;
      i++;
    }
    used.add(candidate.toLowerCase(Locale.ROOT));
    return candidate;
  }

  static String sqlJoinKeyword(SourceJoinType joinType) {
    return switch (joinType != null ? joinType : SourceJoinType.LEFT) {
      case INNER -> "INNER JOIN";
      case LEFT -> "LEFT OUTER JOIN";
      case RIGHT -> "RIGHT OUTER JOIN";
      case FULL -> "FULL OUTER JOIN";
    };
  }

  private static String qualifiedTable(
      DatabaseMeta databaseMeta, IVariables variables, SourceTable table) {
    String schema = table.getSchemaName();
    String tableName = table.getTableName();
    if (Utils.isEmpty(tableName)) {
      tableName = table.getName();
    }
    if (variables != null) {
      schema = variables.resolve(schema);
      tableName = variables.resolve(tableName);
    }
    if (databaseMeta != null) {
      return databaseMeta.getQuotedSchemaTableCombination(variables, schema, tableName);
    }
    if (!Utils.isEmpty(schema)) {
      return schema + "." + tableName;
    }
    return tableName;
  }

  private static String quoteField(DatabaseMeta databaseMeta, String field) {
    if (Utils.isEmpty(field)) {
      return field;
    }
    if (databaseMeta != null) {
      return databaseMeta.quoteField(field);
    }
    return field;
  }
}
