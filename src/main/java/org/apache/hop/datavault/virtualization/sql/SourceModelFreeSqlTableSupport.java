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
package org.apache.hop.datavault.virtualization.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Resolves logical source-model table names referenced by free SQL (FROM / JOIN), without full
 * planning. Used for canvas composition edges and participant lists.
 */
public final class SourceModelFreeSqlTableSupport {

  private SourceModelFreeSqlTableSupport() {}

  /**
   * Logical names that free SQL may reference as tables: DATABASE tables, named source queries
   * (virtual tables), Source JSON, and Source Pipeline feeds — in stable canvas order.
   */
  public static List<String> queryableObjectNames(SourceModel model) {
    List<String> modelNames = new ArrayList<>();
    if (model == null) {
      return modelNames;
    }
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        modelNames.add(table.getName());
      }
    }
    for (var query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      modelNames.add(query.getName());
      if (!Utils.isEmpty(query.getPublishedCatalogName())
          && query.getName() != null
          && !query.getPublishedCatalogName().trim().equalsIgnoreCase(query.getName().trim())) {
        modelNames.add(query.getPublishedCatalogName().trim());
      }
    }
    for (var json : model.getJsonSources()) {
      if (json != null && !Utils.isEmpty(json.getName())) {
        modelNames.add(json.getName());
      }
    }
    for (var pipeline : model.getPipelineSources()) {
      if (pipeline != null && !Utils.isEmpty(pipeline.getName())) {
        modelNames.add(pipeline.getName());
      }
    }
    return modelNames;
  }

  /**
   * Builds a starter {@code SELECT … FROM t1 [, t2 …]} fragment listing all selected logical names
   * (comma-separated FROM — user adds joins).
   */
  public static String insertTablesSqlSnippet(List<String> names) {
    if (names == null || names.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT *\nFROM ");
    for (int i = 0; i < names.size(); i++) {
      if (i > 0) {
        sb.append("\n  /* JOIN */ ");
      }
      sb.append(names.get(i));
    }
    sb.append('\n');
    return sb.toString();
  }

  /**
   * Returns model table names referenced by {@code freeSql}, in first-seen order. Names match
   * canvas {@link SourceTable#getName()} (case-insensitive match against the model).
   *
   * <p>On parse failure returns an empty list (callers may fall back to visual driving/joins).
   */
  public static List<String> referencedTableNames(SourceModel model, String freeSql) {
    Set<String> ordered = new LinkedHashSet<>();
    if (model == null || Utils.isEmpty(freeSql)) {
      return List.of();
    }
    List<String> modelNames = queryableObjectNames(model);
    if (modelNames.isEmpty()) {
      return List.of();
    }

    try {
      SqlParser.Config parserConfig =
          SqlParser.config()
              .withCaseSensitive(false)
              .withQuotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED)
              .withUnquotedCasing(org.apache.calcite.avatica.util.Casing.UNCHANGED);
      SqlNode root = SqlParser.create(freeSql.trim(), parserConfig).parseQuery();
      Set<String> raw = new LinkedHashSet<>();
      root.accept(new TableRefCollector(raw));
      for (String rawName : raw) {
        String resolved = resolveModelTableName(modelNames, rawName);
        if (resolved != null) {
          ordered.add(resolved);
        }
      }
    } catch (SqlParseException | RuntimeException e) {
      // Best-effort for GUI; invalid SQL should not break painting.
      return List.of();
    }
    return new ArrayList<>(ordered);
  }

  private static String resolveModelTableName(List<String> modelNames, String rawName) {
    if (Utils.isEmpty(rawName)) {
      return null;
    }
    // SqlIdentifier may be schema.table — use last component.
    String simple = rawName;
    int dot = rawName.lastIndexOf('.');
    if (dot >= 0 && dot < rawName.length() - 1) {
      simple = rawName.substring(dot + 1);
    }
    String want = simple.trim();
    for (String modelName : modelNames) {
      if (modelName.equalsIgnoreCase(want)) {
        return modelName;
      }
    }
    // Also try full dotted form if model names ever include schema.
    for (String modelName : modelNames) {
      if (modelName.equalsIgnoreCase(rawName.trim())) {
        return modelName;
      }
    }
    return null;
  }

  /**
   * Collects table identifiers from FROM/JOIN only (not column refs).
   *
   * <p>Walks SELECT FROM and JOIN left/right; skips ON conditions and SELECT lists.
   */
  private static final class TableRefCollector extends SqlBasicVisitor<Void> {
    private final Set<String> tables;

    TableRefCollector(Set<String> tables) {
      this.tables = tables;
    }

    @Override
    public Void visit(SqlCall call) {
      if (call instanceof SqlSelect select) {
        if (select.getFrom() != null) {
          collectFrom(select.getFrom());
        }
        // Recurse into WITH / subqueries in FROM only via collectFrom.
        return null;
      }
      return super.visit(call);
    }

    private void collectFrom(SqlNode from) {
      if (from == null) {
        return;
      }
      if (from instanceof SqlJoin join) {
        collectFrom(join.getLeft());
        collectFrom(join.getRight());
        return;
      }
      if (from.getKind() == SqlKind.AS && from instanceof SqlCall asCall) {
        // table AS alias — first operand is the table (or subquery)
        if (asCall.operandCount() >= 1) {
          collectFrom(asCall.operand(0));
        }
        return;
      }
      if (from instanceof SqlSelect nested) {
        // Subquery in FROM: recurse for nested tables
        if (nested.getFrom() != null) {
          collectFrom(nested.getFrom());
        }
        return;
      }
      if (from instanceof SqlIdentifier id) {
        tables.add(id.toString());
        // Also store simple last name for matching.
        List<String> names = id.names;
        if (names != null && !names.isEmpty()) {
          tables.add(names.get(names.size() - 1));
        }
      }
    }
  }
}
