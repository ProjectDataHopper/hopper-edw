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
package org.hopper.edw.datavault.diagram.schema;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/** Builds an ER {@link SchemaDiagram} from a source model (tables + FK relationships). */
public final class SourceModelSchemaBuilder {

  private SourceModelSchemaBuilder() {}

  public static SchemaDiagram build(SourceModel model) {
    SchemaDiagram diagram = new SchemaDiagram();
    if (model == null) {
      return diagram;
    }
    diagram.setTitle(model.getName());
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      SchemaEntity entity = new SchemaEntity(table.getName(), SchemaStereotypes.TABLE);
      for (SourceColumn column : table.getColumns()) {
        if (column == null || Utils.isEmpty(column.getName())) {
          continue;
        }
        SchemaField field = new SchemaField(column.getName());
        if (!Utils.isEmpty(column.getSourceDataType())) {
          field.setType(column.getSourceDataType());
        }
        if (column.isPrimaryKey()) {
          field.setPrimaryKey(true);
        }
        entity.addField(field);
      }
      diagram.addEntity(entity);
    }
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null
          || Utils.isEmpty(relationship.getChildTableName())
          || Utils.isEmpty(relationship.getParentTableName())) {
        continue;
      }
      SchemaEntity child = diagram.findEntity(relationship.getChildTableName());
      if (child != null) {
        for (String column : relationship.getChildColumns()) {
          if (Utils.isEmpty(column)) {
            continue;
          }
          boolean found = false;
          for (SchemaField field : child.getFields()) {
            if (column.equals(field.getName())) {
              field.setForeignKey(true);
              found = true;
              break;
            }
          }
          if (!found) {
            child.addField(new SchemaField(column).fk());
          }
        }
      }
      SchemaRelation relation =
          new SchemaRelation(
              relationship.getChildTableName(),
              relationship.getParentTableName(),
              relationship.getName());
      relation.setFromCardinality(fromMultiplicity(relationship.resolveChildMultiplicity()));
      relation.setToCardinality(fromMultiplicity(relationship.resolveParentMultiplicity()));
      diagram.addRelation(relation);
    }
    return diagram;
  }

  private static SchemaCardinality fromMultiplicity(SourceRelationshipMultiplicity multiplicity) {
    if (multiplicity == null) {
      return SchemaCardinality.ONE_OR_MANY;
    }
    return switch (multiplicity) {
      case ONE -> SchemaCardinality.ONE;
      case ZERO_OR_ONE -> SchemaCardinality.ZERO_OR_ONE;
      case ONE_OR_MANY -> SchemaCardinality.ONE_OR_MANY;
      case ZERO_OR_MANY, UNKNOWN -> SchemaCardinality.ZERO_OR_MANY;
    };
  }
}
