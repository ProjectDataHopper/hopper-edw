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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticRelationship;

/** Calcite table for one semantic entity (logical name, physical binding). */
@Getter
public class SemanticTable extends AbstractTable implements TranslatableTable {

  private final SemanticModel model;
  private final SemanticEntity entity;

  public SemanticTable(SemanticModel model, SemanticEntity entity) {
    this.model = model;
    this.entity = entity;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = new ArrayList<>();
    List<RelDataType> types = new ArrayList<>();
    Set<String> added = new LinkedHashSet<>();
    if (entity.getAttributes() != null) {
      for (SemanticAttribute attribute : entity.getAttributes()) {
        addColumn(
            names,
            types,
            added,
            attribute == null ? null : attribute.resolvePhysicalColumn(),
            typeFactory);
      }
    }
    if (entity.getMeasures() != null) {
      for (SemanticMeasure measure : entity.getMeasures()) {
        addColumn(
            names,
            types,
            added,
            measure == null ? null : measure.resolvePhysicalColumn(),
            typeFactory,
            true);
      }
    }
    if (model != null && model.getRelationships() != null) {
      for (SemanticRelationship relationship : model.getRelationships()) {
        if (relationship == null) {
          continue;
        }
        if (entity.getName() != null && entity.getName().equals(relationship.getFromEntity())) {
          addColumn(names, types, added, relationship.getFromField(), typeFactory);
        }
        if (entity.getName() != null && entity.getName().equals(relationship.getToEntity())) {
          addColumn(names, types, added, relationship.getToField(), typeFactory);
        }
      }
    }
    if (names.isEmpty()) {
      names.add("dummy");
      types.add(typeFactory.createSqlType(SqlTypeName.VARCHAR, 1));
    }
    return typeFactory.createStructType(types, names);
  }

  private static void addColumn(
      List<String> names,
      List<RelDataType> types,
      Set<String> added,
      String column,
      RelDataTypeFactory typeFactory) {
    addColumn(names, types, added, column, typeFactory, false);
  }

  private static void addColumn(
      List<String> names,
      List<RelDataType> types,
      Set<String> added,
      String column,
      RelDataTypeFactory typeFactory,
      boolean numeric) {
    if (Utils.isEmpty(column) || !added.add(column)) {
      return;
    }
    names.add(column);
    if (numeric) {
      types.add(typeFactory.createSqlType(SqlTypeName.DECIMAL, 38, 10));
    } else {
      types.add(typeFactory.createSqlType(SqlTypeName.VARCHAR, 1024));
    }
  }

  @Override
  public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
    return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
  }

  public String logicalName() {
    return entity.getName();
  }

  public String physicalTableName() {
    return entity.resolvePhysicalTableName();
  }
}
