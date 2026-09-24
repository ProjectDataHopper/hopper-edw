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
package org.hopper.edw.datavault.presentation.fact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabQuery.SelectedColumn;

/** Builds the star join for a {@link FactCrosstabSpec}, aggregated at the selected grain. */
public final class FactCrosstabSqlBuilder {

  /** Preview cap for the editor SQL button (database explorer). */
  public static final int DEBUG_ROW_LIMIT = 1000;

  private FactCrosstabSqlBuilder() {}

  public static FactCrosstabQuery build(
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sourceModel,
      DatabaseMeta databaseMeta,
      IVariables variables)
      throws HopException {
    if (spec == null || spec.isEmpty()) {
      throw new HopException("Select at least one column for the crosstab");
    }
    if (sourceModel == null || sourceModel.factTable() == null) {
      throw new HopException("Fact crosstab source model is missing");
    }
    if (databaseMeta == null) {
      throw new HopException("Target database connection is required to generate SQL");
    }
    IVariables vars = variables != null ? variables : new Variables();

    List<FactCrosstabField> selectedFields = spec.allFields();
    Set<String> neededLogical = new LinkedHashSet<>();
    for (FactCrosstabField field : selectedFields) {
      if (field != null && !Utils.isEmpty(field.getTableName())) {
        neededLogical.add(field.getTableName());
      }
    }

    FactCrosstabSourceTable factTable = sourceModel.factTable();
    List<FactCrosstabSourceTable> joinTables = new ArrayList<>();
    for (String logicalName : neededLogical) {
      FactCrosstabSourceTable table = sourceModel.findTable(logicalName);
      if (table == null) {
        throw new HopException("Unknown crosstab source table: " + logicalName);
      }
      if (!table.isFact()) {
        joinTables.add(table);
      }
    }

    List<SelectedColumn> selected = new ArrayList<>();
    Set<String> usedAliases = new LinkedHashSet<>();
    List<String> selectList = new ArrayList<>();
    List<String> groupBy = new ArrayList<>();
    Set<String> grouped = new LinkedHashSet<>();
    for (FactCrosstabField field : selectedFields) {
      FactCrosstabSourceTable table = sourceModel.findTable(field.getTableName());
      if (table == null) {
        throw new HopException("Unknown crosstab source table: " + field.getTableName());
      }
      if (table.findColumn(field.getColumnName()) == null) {
        throw new HopException(
            "Column '"
                + field.getColumnName()
                + "' is not on table '"
                + field.getTableName()
                + "'");
      }
      String resultAlias = uniqueAlias(field, usedAliases);
      usedAliases.add(resultAlias);
      String qualified = qualifiedColumn(databaseMeta, table.getJoinAlias(), field.getColumnName());
      if (spec.getFacts().contains(field)) {
        AggregationMethod method =
            field.getAggregation() != null ? field.getAggregation() : AggregationMethod.SUM;
        String weightAlias = null;
        switch (method) {
          case COUNT -> selectList.add("NULLIF(COUNT(" + qualified + "), 0) AS " + resultAlias);
          case AVERAGE -> {
            selectList.add("SUM(" + qualified + ") AS " + resultAlias);
            weightAlias = uniqueWeightAlias(resultAlias, usedAliases);
            usedAliases.add(weightAlias);
            selectList.add("COUNT(" + qualified + ") AS " + weightAlias);
          }
          case SUM -> selectList.add("SUM(" + qualified + ") AS " + resultAlias);
          default -> selectList.add("SUM(" + qualified + ") AS " + resultAlias);
        }
        selected.add(new SelectedColumn(field.copy(), resultAlias, weightAlias));
      } else {
        selectList.add(qualified + " AS " + resultAlias);
        selected.add(new SelectedColumn(field.copy(), resultAlias));
        if (grouped.add(qualified)) {
          groupBy.add(qualified);
        }
      }
    }

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT").append(System.lineSeparator());
    for (int i = 0; i < selectList.size(); i++) {
      sql.append("  ").append(selectList.get(i));
      if (i < selectList.size() - 1) {
        sql.append(',');
      }
      sql.append(System.lineSeparator());
    }
    sql.append("FROM ")
        .append(tableAs(databaseMeta, vars, factTable))
        .append(System.lineSeparator());
    for (FactCrosstabSourceTable joinTable : joinTables) {
      if (Utils.isEmpty(joinTable.getFactForeignKey())
          || Utils.isEmpty(joinTable.getDimensionKeyColumn())) {
        throw new HopException(
            "Cannot join dimension '"
                + joinTable.getLogicalName()
                + "': fact foreign key or dimension key is missing");
      }
      sql.append("JOIN ")
          .append(tableAs(databaseMeta, vars, joinTable))
          .append(" ON ")
          .append(
              qualifiedColumn(
                  databaseMeta, factTable.getJoinAlias(), joinTable.getFactForeignKey()))
          .append(" = ")
          .append(
              qualifiedColumn(
                  databaseMeta, joinTable.getJoinAlias(), joinTable.getDimensionKeyColumn()))
          .append(System.lineSeparator());
    }
    if (!groupBy.isEmpty()) {
      sql.append("GROUP BY").append(System.lineSeparator());
      for (int i = 0; i < groupBy.size(); i++) {
        sql.append("  ").append(groupBy.get(i));
        if (i < groupBy.size() - 1) {
          sql.append(',');
        }
        sql.append(System.lineSeparator());
      }
    }
    return new FactCrosstabQuery(sql.toString().trim(), selected);
  }

  /**
   * Caps a SELECT using the target dialect: {@code LIMIT} at the end (Postgres, MySQL, …) or {@code
   * TOP} after {@code SELECT} (SQL Server). Falls back to {@code LIMIT n} when the dialect offers
   * neither.
   */
  public static String applyRowLimit(String sql, DatabaseMeta databaseMeta, int limit) {
    if (Utils.isEmpty(sql) || limit <= 0) {
      return sql;
    }
    String prefix =
        databaseMeta != null ? Const.NVL(databaseMeta.getLimitClausePrefix(limit), "") : "";
    String suffix = databaseMeta != null ? Const.NVL(databaseMeta.getLimitClause(limit), "") : "";
    if (prefix.isBlank() && suffix.isBlank()) {
      suffix = " LIMIT " + limit;
    }
    String result = sql.trim();
    if (!prefix.isBlank()) {
      if (result.regionMatches(true, 0, "SELECT", 0, 6)) {
        result = result.substring(0, 6) + prefix + result.substring(6);
      } else {
        result = "SELECT" + prefix + " * FROM (" + result + ") preview";
      }
    }
    if (!suffix.isBlank()) {
      result = result + suffix;
    }
    return result;
  }

  public static String sanitizeAlias(String name) {
    if (Utils.isEmpty(name)) {
      return "col";
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (Character.isLetterOrDigit(ch) || ch == '_') {
        builder.append(Character.toLowerCase(ch));
      } else {
        builder.append('_');
      }
    }
    String alias = builder.toString().replaceAll("_+", "_");
    alias = alias.replaceAll("^_+", "").replaceAll("_+$", "");
    if (alias.isEmpty() || !Character.isLetter(alias.charAt(0))) {
      alias = "t_" + alias;
    }
    return alias;
  }

  private static String uniqueWeightAlias(String resultAlias, Set<String> used) {
    String candidate = resultAlias + "_n";
    int n = 2;
    while (used.contains(candidate)) {
      candidate = resultAlias + "_n" + n;
      n++;
    }
    return candidate;
  }

  private static String uniqueAlias(FactCrosstabField field, Set<String> used) {
    String columnAlias = sanitizeAlias(field.getColumnName());
    if (!used.contains(columnAlias)) {
      return columnAlias;
    }
    String tablePrefixed = sanitizeAlias(field.getTableName() + "_" + field.getColumnName());
    String candidate = tablePrefixed;
    int n = 2;
    while (used.contains(candidate)) {
      candidate = tablePrefixed + "_" + n;
      n++;
    }
    return candidate;
  }

  private static String tableAs(
      DatabaseMeta databaseMeta, IVariables variables, FactCrosstabSourceTable table) {
    String quoted =
        databaseMeta.getQuotedSchemaTableCombination(variables, null, table.getPhysicalTableName());
    return quoted + " AS " + quoteIdent(databaseMeta, table.getJoinAlias());
  }

  private static String qualifiedColumn(
      DatabaseMeta databaseMeta, String joinAlias, String columnName) {
    return quoteIdent(databaseMeta, joinAlias) + "." + quoteIdent(databaseMeta, columnName);
  }

  private static String quoteIdent(DatabaseMeta databaseMeta, String ident) {
    if (Utils.isEmpty(ident)) {
      return ident;
    }
    String quoted = databaseMeta.quoteField(ident);
    return quoted == null ? ident : quoted;
  }
}
