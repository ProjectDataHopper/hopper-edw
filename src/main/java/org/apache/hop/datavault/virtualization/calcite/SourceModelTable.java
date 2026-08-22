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
package org.apache.hop.datavault.virtualization.calcite;

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
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/** Calcite table backed by a {@link SourceTable} on a source model. */
@Getter
public class SourceModelTable extends AbstractTable implements TranslatableTable {

  private final SourceTable sourceTable;

  public SourceModelTable(SourceTable sourceTable) {
    this.sourceTable = sourceTable;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = new ArrayList<>();
    List<RelDataType> types = new ArrayList<>();
    for (SourceColumn column : sourceTable.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      names.add(column.getName());
      types.add(HopTypeSystem.toRelType(typeFactory, column));
    }
    if (names.isEmpty()) {
      // Allow empty tables to exist for validation errors to be clearer elsewhere.
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
    return sourceTable.getName();
  }

  public DvSourceType physicalType() {
    return sourceTable.resolvePhysicalType();
  }

  public String databaseName() {
    return sourceTable.getDatabaseName();
  }
}
