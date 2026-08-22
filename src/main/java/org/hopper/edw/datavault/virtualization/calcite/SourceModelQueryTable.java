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
package org.hopper.edw.datavault.virtualization.calcite;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;

/**
 * Calcite table backed by a named {@link SourceQuery} (visual or free SQL) so outer free SQL can
 * use {@code FROM feed_name}.
 */
@Getter
public class SourceModelQueryTable extends AbstractTable implements TranslatableTable {

  private final SourceQuery sourceQuery;

  public SourceModelQueryTable(SourceQuery sourceQuery) {
    this.sourceQuery = sourceQuery;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = new ArrayList<>();
    List<RelDataType> types = new ArrayList<>();
    for (SourceQueryColumn column : sourceQuery.getColumns()) {
      if (column == null || Utils.isEmpty(column.getColumnName())) {
        continue;
      }
      String out = column.resolveAlias();
      names.add(out);
      // Unknown hop type → VARCHAR; visual projections rarely store hop types.
      types.add(
          typeFactory.createTypeWithNullability(
              typeFactory.createSqlType(org.apache.calcite.sql.type.SqlTypeName.VARCHAR, 1024),
              true));
    }
    if (names.isEmpty()
        && sourceQuery.resolveGenerationMode() == SourceQueryGenerationMode.FREE_SQL
        && !Utils.isEmpty(sourceQuery.getFreeSql())) {
      // Free SQL without a projection list: expose a placeholder until plan-time types apply.
      names.add("column0");
      types.add(
          typeFactory.createTypeWithNullability(
              typeFactory.createSqlType(org.apache.calcite.sql.type.SqlTypeName.VARCHAR, 1024),
              true));
    }
    if (names.isEmpty()) {
      names.add("dummy");
      types.add(typeFactory.createSqlType(org.apache.calcite.sql.type.SqlTypeName.VARCHAR, 1));
    }
    return typeFactory.createStructType(types, names);
  }

  @Override
  public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
    return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
  }

  public String logicalName() {
    return sourceQuery.getName();
  }
}
