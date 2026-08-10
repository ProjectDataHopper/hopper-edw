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
package org.apache.hop.datavault.virtualization.generate;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.virtualization.calcite.SourceModelTable;

/**
 * RelToSql that emits physical schema/table identifiers for {@link SourceModelTable} scans.
 *
 * <p>Must be a public top-level class: Calcite dispatches {@code visit(...)} via reflection.
 */
public class PhysicalRelToSqlConverter extends RelToSqlConverter {

  private final IVariables variables;

  public PhysicalRelToSqlConverter(
      SqlDialect dialect, DatabaseMeta databaseMeta, IVariables variables) {
    super(dialect);
    this.variables = variables;
  }

  @Override
  public Result visit(TableScan e) {
    SourceModelTable smt = e.getTable().unwrap(SourceModelTable.class);
    if (smt == null) {
      return super.visit(e);
    }
    SqlIdentifier identifier = physicalIdentifier(smt);
    return result(identifier, ImmutableList.of(Clause.FROM), e, null);
  }

  private SqlIdentifier physicalIdentifier(SourceModelTable smt) {
    List<String> names = new ArrayList<>();
    String schema = smt.getSourceTable().getSchemaName();
    String table = smt.getSourceTable().getTableName();
    if (Utils.isEmpty(table)) {
      table = smt.logicalName();
    }
    if (variables != null) {
      if (!Utils.isEmpty(schema)) {
        schema = variables.resolve(schema);
      }
      table = variables.resolve(table);
    }
    if (!Utils.isEmpty(schema)) {
      names.add(schema);
    }
    names.add(table);
    return new SqlIdentifier(names, SqlParserPos.ZERO);
  }

  /** Unused helper kept for debugging; prefer visitRoot on the converter. */
  public static String convert(
      RelNode rel, SqlDialect dialect, DatabaseMeta databaseMeta, IVariables variables) {
    PhysicalRelToSqlConverter converter =
        new PhysicalRelToSqlConverter(dialect, databaseMeta, variables);
    return converter.visitRoot(rel).asStatement().toSqlString(dialect).getSql();
  }
}
