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
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.hopper.edw.datavault.virtualization.calcite.HopTypeSystem;

/** Calcite table for one logical dimensional JDBC table. */
@Getter
public class DimensionalCalciteTable extends AbstractTable implements TranslatableTable {

  private final DimensionalJdbcTable table;

  public DimensionalCalciteTable(DimensionalJdbcTable table) {
    this.table = table;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = new ArrayList<>();
    List<RelDataType> types = new ArrayList<>();
    for (DimensionalJdbcColumn column : table.columns()) {
      names.add(column.name());
      types.add(relType(typeFactory, column));
    }
    if (names.isEmpty()) {
      names.add("dummy");
      types.add(typeFactory.createSqlType(SqlTypeName.VARCHAR, 1));
    }
    return typeFactory.createStructType(types, names);
  }

  private static RelDataType relType(RelDataTypeFactory typeFactory, DimensionalJdbcColumn column) {
    try {
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(column.name(), column.hopType());
      return HopTypeSystem.toRelType(typeFactory, valueMeta);
    } catch (Exception e) {
      return typeFactory.createTypeWithNullability(
          typeFactory.createSqlType(SqlTypeName.VARCHAR, 1024), true);
    }
  }

  @Override
  public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
    return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
  }
}
