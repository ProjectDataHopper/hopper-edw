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
package org.hopper.edw.semantic.calcite;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * RelToSql that emits physical warehouse tables aliased with the semantic entity name (role-playing
 * dimensions share a physical table).
 */
public class SemanticRelToSqlConverter extends RelToSqlConverter {

  private final DatabaseMeta databaseMeta;
  private final IVariables variables;

  public SemanticRelToSqlConverter(
      SqlDialect dialect, DatabaseMeta databaseMeta, IVariables variables) {
    super(dialect);
    this.databaseMeta = databaseMeta;
    this.variables = variables;
  }

  @Override
  public Result visit(TableScan e) {
    SemanticTable table = e.getTable().unwrap(SemanticTable.class);
    if (table == null) {
      return super.visit(e);
    }
    SqlIdentifier physical = physicalIdentifier(table);
    SqlIdentifier alias = new SqlIdentifier(table.logicalName(), SqlParserPos.ZERO);
    SqlNode aliased = SqlStdOperatorTable.AS.createCall(SqlParserPos.ZERO, physical, alias);
    return result(aliased, ImmutableList.of(Clause.FROM), e, null);
  }

  private SqlIdentifier physicalIdentifier(SemanticTable table) {
    List<String> names = new ArrayList<>();
    String schema = null;
    if (table.getEntity().getBinding() != null) {
      schema = table.getEntity().getBinding().getSchemaName();
    }
    if (databaseMeta != null && Utils.isEmpty(schema)) {
      schema = databaseMeta.getPreferredSchemaName();
    }
    String physical = table.physicalTableName();
    if (variables != null) {
      if (!Utils.isEmpty(schema)) {
        schema = variables.resolve(schema);
      }
      if (!Utils.isEmpty(physical)) {
        physical = variables.resolve(physical);
      }
    }
    if (!Utils.isEmpty(schema)) {
      names.add(schema);
    }
    names.add(physical);
    return new SqlIdentifier(names, SqlParserPos.ZERO);
  }

  public static String convert(
      RelNode rel, SqlDialect dialect, DatabaseMeta databaseMeta, IVariables variables) {
    SemanticRelToSqlConverter converter =
        new SemanticRelToSqlConverter(dialect, databaseMeta, variables);
    SqlNode sqlNode = converter.visitRoot(rel).asStatement();
    return sqlNode.toSqlString(dialect).getSql();
  }
}
