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
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonField;

/** Calcite table backed by a {@link SourceJson} extraction on a source model. */
@Getter
public class SourceModelJsonTable extends AbstractTable implements TranslatableTable {

  private final SourceJson sourceJson;

  public SourceModelJsonTable(SourceJson sourceJson) {
    this.sourceJson = sourceJson;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = new ArrayList<>();
    List<RelDataType> types = new ArrayList<>();
    for (SourceJsonField field : sourceJson.getFields()) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      names.add(field.getName());
      SourceColumn col = new SourceColumn(field.getName());
      col.setHopType(field.getHopType());
      if (field.getLength() > 0) {
        col.setLength(Integer.toString(field.getLength()));
      }
      if (field.getPrecision() >= 0) {
        col.setPrecision(Integer.toString(field.getPrecision()));
      }
      types.add(HopTypeSystem.toRelType(typeFactory, col));
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
    return sourceJson.getName();
  }

  public DvSourceType physicalType() {
    return DvSourceType.JSON;
  }
}
