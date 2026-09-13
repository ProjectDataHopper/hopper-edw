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
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

/** Builds a Kimball star/snowflake {@link SchemaDiagram} from a dimensional model. */
public final class DimensionalSchemaBuilder {

  private DimensionalSchemaBuilder() {}

  public static SchemaDiagram build(DimensionalModel model) {
    SchemaDiagram diagram = new SchemaDiagram();
    if (model == null) {
      return diagram;
    }
    diagram.setTitle(model.getName());
    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      diagram.addEntity(entityFor(table));
    }
    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      addRelations(diagram, table);
    }
    return diagram;
  }

  private static SchemaEntity entityFor(IDmTable table) {
    String stereotype = stereotypeFor(table);
    SchemaEntity entity = new SchemaEntity(table.getName(), stereotype);
    entity.setGrain(table.getGrain());
    if (table instanceof DmDimension dimension) {
      if (!Utils.isEmpty(dimension.getSurrogateKeyField())) {
        entity.addField(new SchemaField(dimension.getSurrogateKeyField()).pk());
      }
      for (DmNaturalKeyField key : dimension.getNaturalKeysOrEmpty()) {
        if (key != null && !Utils.isEmpty(key.getFieldName())) {
          entity.addField(new SchemaField(key.getFieldName()).pk());
        }
      }
      for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
        if (attribute != null && !Utils.isEmpty(attribute.getFieldName())) {
          entity.addField(new SchemaField(attribute.getFieldName()));
        }
      }
      for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
        if (outrigger != null && !Utils.isEmpty(outrigger.getForeignKeyColumn())) {
          entity.addField(new SchemaField(outrigger.getForeignKeyColumn()).fk());
        }
      }
    } else if (table instanceof DmJunkDimension junk) {
      if (!Utils.isEmpty(junk.getSurrogateKeyField())) {
        entity.addField(new SchemaField(junk.getSurrogateKeyField()).pk());
      }
      for (DmNaturalKeyField key : junk.getKeyFieldsOrEmpty()) {
        if (key != null && !Utils.isEmpty(key.getFieldName())) {
          entity.addField(new SchemaField(key.getFieldName()).pk());
        }
      }
    } else if (table instanceof DmBridge bridge) {
      for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
        if (ref != null && !Utils.isEmpty(ref.getForeignKeyColumn())) {
          entity.addField(new SchemaField(ref.getForeignKeyColumn()).fk());
        }
      }
      if (!Utils.isEmpty(bridge.getWeightField())) {
        entity.addField(new SchemaField(bridge.getWeightField()));
      }
    } else if (table instanceof IDmFactLikeTable fact) {
      for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
        String fk = roleFk(role);
        if (!Utils.isEmpty(fk)) {
          entity.addField(new SchemaField(fk).fk());
        }
      }
      for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
        if (role != null && !Utils.isEmpty(role.getForeignKeyColumn())) {
          entity.addField(new SchemaField(role.getForeignKeyColumn()).fk());
        }
      }
      for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
        if (role != null && !Utils.isEmpty(role.getTargetFieldName())) {
          entity.addField(new SchemaField(role.getTargetFieldName()).fk());
        }
      }
      for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
        if (degenerate != null && !Utils.isEmpty(degenerate.getFieldName())) {
          entity.addField(new SchemaField(degenerate.getFieldName()).pk());
        }
      }
      for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
        if (measure != null && !Utils.isEmpty(measure.getFieldName())) {
          entity.addField(new SchemaField(measure.getFieldName()));
        }
      }
    } else if (table instanceof DmDimensionAlias alias) {
      if (!Utils.isEmpty(alias.getReferencedDimensionName())) {
        entity.addField(new SchemaField(alias.getReferencedDimensionName()).fk());
      }
    }
    return entity;
  }

  private static void addRelations(SchemaDiagram diagram, IDmTable table) {
    if (table instanceof IDmFactLikeTable fact) {
      for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
          continue;
        }
        String label =
            !Utils.isEmpty(role.getRoleName()) ? role.getRoleName() : role.getDimensionTableName();
        addManyToOne(diagram, table.getName(), role.getDimensionTableName(), label);
      }
      for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getJunkDimensionTableName())) {
          continue;
        }
        addManyToOne(diagram, table.getName(), role.getJunkDimensionTableName(), "junk");
      }
      for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
        if (role == null || Utils.isEmpty(role.getRangeDimensionTableName())) {
          continue;
        }
        addManyToOne(diagram, table.getName(), role.getRangeDimensionTableName(), "range");
      }
    }
    if (table instanceof DmDimension dimension) {
      for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
        if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
          continue;
        }
        addManyToOne(diagram, table.getName(), outrigger.getDimensionTableName(), "outrigger");
      }
    }
    if (table instanceof DmBridge bridge) {
      for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
        if (ref == null || Utils.isEmpty(ref.getDimensionTableName())) {
          continue;
        }
        addManyToOne(diagram, table.getName(), ref.getDimensionTableName(), "bridge");
      }
    }
    if (table instanceof DmDimensionAlias alias
        && !Utils.isEmpty(alias.getReferencedDimensionName())) {
      addManyToOne(diagram, table.getName(), alias.getReferencedDimensionName(), "alias");
    }
  }

  private static void addManyToOne(SchemaDiagram diagram, String from, String to, String label) {
    SchemaRelation relation = new SchemaRelation(from, to, label);
    relation.setFromCardinality(SchemaCardinality.ONE_OR_MANY);
    relation.setToCardinality(SchemaCardinality.ONE);
    diagram.addRelation(relation);
  }

  private static String roleFk(DmFactDimensionRole role) {
    if (role == null) {
      return null;
    }
    if (!Utils.isEmpty(role.getForeignKeyColumn())) {
      return role.getForeignKeyColumn();
    }
    return role.getDimensionTableName();
  }

  private static String stereotypeFor(IDmTable table) {
    if (table instanceof DmBridge) {
      return SchemaStereotypes.BRIDGE;
    }
    if (table instanceof IDmFactLikeTable) {
      return SchemaStereotypes.FACT;
    }
    return SchemaStereotypes.DIMENSION;
  }
}
