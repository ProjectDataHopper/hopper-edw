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
package org.hopper.edw.datavault.virtualization.dimensional;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.hop.core.util.Utils;

/**
 * Rel-to-SQL that emits the warehouse schema and table for a dimensional scan. Column names are
 * already the physical names. Must stay public: Calcite dispatches {@code visit} by reflection.
 */
public class DimensionalRelToSqlConverter extends RelToSqlConverter {

  public DimensionalRelToSqlConverter(SqlDialect dialect) {
    super(dialect);
  }

  @Override
  public Result visit(TableScan scan) {
    DimensionalCalciteTable calcite = scan.getTable().unwrap(DimensionalCalciteTable.class);
    if (calcite == null) {
      return super.visit(scan);
    }
    DimensionalJdbcTable table = calcite.getTable();
    List<String> names = new ArrayList<>();
    if (!Utils.isEmpty(table.physicalSchema())) {
      names.add(table.physicalSchema());
    }
    names.add(table.physicalTable());
    SqlIdentifier identifier = new SqlIdentifier(names, SqlParserPos.ZERO);
    return result(identifier, ImmutableList.of(Clause.FROM), scan, null);
  }
}
