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
package org.hopper.edw.semantic.bind;

import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticEntityRole;
import org.hopper.edw.semantic.model.SemanticFormatSupport;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticPhysicalBinding;
import org.hopper.edw.semantic.model.SemanticRelationship;

/** Builds a consumption semantic model from a Kimball `.hdm`. */
public final class HdmSemanticSeed {

  private HdmSemanticSeed() {}

  public static SemanticModel seed(
      DimensionalModel dimensionalModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (dimensionalModel == null) {
      throw new HopException("Dimensional model is required to seed a semantic layer");
    }
    SemanticModel model = new SemanticModel();
    model.setName(Const.NVL(dimensionalModel.getName(), "semantic"));
    model.setDescription(dimensionalModel.getDescription());
    model.setDimensionalModelFilename(dimensionalModel.getFilename());
    DimensionalConfiguration config = dimensionalModel.getConfigurationOrDefault();
    if (config != null) {
      model.setTargetDatabase(config.getTargetDatabase());
    }

    for (IDmTable table : dimensionalModel.getTables()) {
      if (table == null || table.getTableType() == DmTableType.RANGE_DIMENSION) {
        continue;
      }
      SemanticEntity entity = toEntity(dimensionalModel, table, variables, metadataProvider);
      if (entity != null) {
        model.getEntities().add(entity);
      }
    }

    for (IDmTable table : dimensionalModel.getTables()) {
      if (!(table instanceof IDmFactLikeTable fact)) {
        continue;
      }
      addFactRelationships(model, dimensionalModel, fact, variables, metadataProvider);
    }
    model.clearChanged();
    return model;
  }

  private static SemanticEntity toEntity(
      DimensionalModel dimensionalModel,
      IDmTable table,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    SemanticEntity entity = new SemanticEntity();
    entity.setName(table.getName());
    entity.setDescription(table.getDescription());
    entity.setRole(roleOf(table));
    entity.setBinding(
        SemanticPhysicalBinding.hdm(
            dimensionalModel.getFilename(), table.getName(), physicalName(table)));

    IRowMeta layout = layoutOrNull(table, dimensionalModel, variables, metadataProvider);

    if (table instanceof IDmFactLikeTable fact) {
      for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
        if (measure == null || Utils.isEmpty(measure.getFieldName())) {
          continue;
        }
        SemanticMeasure semanticMeasure =
            new SemanticMeasure(measure.getFieldName(), measure.getFieldName());
        semanticMeasure.setHeader(humanize(measure.getFieldName()));
        semanticMeasure.setAdditive(measure.isAdditive());
        semanticMeasure.setAggregationMethod(
            measure.isAdditive() ? AggregationMethod.SUM : AggregationMethod.COUNT);
        int hopType = hopType(layout, measure.getFieldName());
        semanticMeasure.setFormatMask(SemanticFormatSupport.defaultMask(hopType));
        if (measure.getDocumentation() != null) {
          semanticMeasure.setDescription(measure.getDocumentation().getDescription());
        }
        entity.getMeasures().add(semanticMeasure);
      }
      for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
        if (degenerate == null || Utils.isEmpty(degenerate.getFieldName())) {
          continue;
        }
        entity
            .getAttributes()
            .add(
                attribute(
                    degenerate.getFieldName(),
                    layout,
                    degenerate.getDocumentation() == null
                        ? null
                        : degenerate.getDocumentation().getDescription()));
      }
      for (DmFactRangeDimensionRole rangeRole : fact.getRangeDimensionRolesOrEmpty()) {
        if (rangeRole == null || Utils.isEmpty(rangeRole.getTargetFieldName())) {
          continue;
        }
        entity.getAttributes().add(attribute(rangeRole.getTargetFieldName(), layout, null));
      }
      return entity;
    }

    if (table instanceof DmDimension dimension) {
      addDimensionFields(entity, dimension, layout);
      return entity;
    }
    if (table instanceof DmDimensionAlias alias) {
      DmDimension target =
          DmDimensionResolutionSupport.resolveAliasTarget(
              dimensionalModel, alias, variables, metadataProvider);
      if (target != null) {
        entity.getBinding().setPhysicalTableName(physicalName(target));
        addDimensionFields(
            entity, target, layoutOrNull(target, dimensionalModel, variables, metadataProvider));
      }
      return entity;
    }
    if (table instanceof DmJunkDimension junk) {
      for (DmNaturalKeyField keyField : junk.getKeyFieldsOrEmpty()) {
        if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
          continue;
        }
        entity.getAttributes().add(attribute(keyField.getFieldName(), layout, null));
      }
      return entity;
    }
    return entity;
  }

  private static void addDimensionFields(
      SemanticEntity entity, DmDimension dimension, IRowMeta layout) {
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      if (naturalKey == null || Utils.isEmpty(naturalKey.getFieldName())) {
        continue;
      }
      entity.getAttributes().add(attribute(naturalKey.getFieldName(), layout, null));
    }
    for (DmDimensionAttribute dimAttribute : dimension.getAttributesOrEmpty()) {
      if (dimAttribute == null || Utils.isEmpty(dimAttribute.getFieldName())) {
        continue;
      }
      String description =
          dimAttribute.getDocumentation() != null
              ? dimAttribute.getDocumentation().getDescription()
              : null;
      entity.getAttributes().add(attribute(dimAttribute.getFieldName(), layout, description));
    }
  }

  private static void addFactRelationships(
      SemanticModel model,
      DimensionalModel dimensionalModel,
      IDmFactLikeTable fact,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    DimensionalConfiguration config = dimensionalModel.getConfigurationOrDefault();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      String toEntity = resolve(role.getDimensionTableName(), variables);
      DmDimension dimension =
          DmDimensionResolutionSupport.resolveDimension(
              dimensionalModel, toEntity, variables, metadataProvider);
      String toField =
          dimension != null
              ? DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables)
              : null;
      if (Utils.isEmpty(toField) && dimension != null) {
        toField = resolve(dimension.getSurrogateKeyField(), variables);
      }
      if (Utils.isEmpty(toField)
          && dimension != null
          && !dimension.getNaturalKeysOrEmpty().isEmpty()) {
        toField = dimension.getNaturalKeysOrEmpty().get(0).getFieldName();
      }
      addRelationship(
          model,
          fact.getName() + "_" + toEntity,
          fact.getName(),
          toEntity,
          resolve(role.getForeignKeyColumn(), variables),
          toField,
          toEntity);
    }
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getJunkDimensionTableName())) {
        continue;
      }
      String toEntity = resolve(role.getJunkDimensionTableName(), variables);
      IDmTable junkTable = dimensionalModel.findTable(toEntity);
      String toField = null;
      if (junkTable instanceof DmJunkDimension junk) {
        toField = DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junk, config, variables);
      }
      addRelationship(
          model,
          fact.getName() + "_" + toEntity,
          fact.getName(),
          toEntity,
          resolve(role.getForeignKeyColumn(), variables),
          toField,
          toEntity);
    }
  }

  private static void addRelationship(
      SemanticModel model,
      String name,
      String fromEntity,
      String toEntity,
      String fromField,
      String toField,
      String roleName) {
    if (Utils.isEmpty(fromField) || Utils.isEmpty(toField) || model.findEntity(toEntity) == null) {
      return;
    }
    SemanticRelationship relationship = new SemanticRelationship();
    relationship.setName(name);
    relationship.setFromEntity(fromEntity);
    relationship.setToEntity(toEntity);
    relationship.setFromField(fromField);
    relationship.setToField(toField);
    relationship.setRoleName(roleName);
    model.getRelationships().add(relationship);
  }

  private static SemanticAttribute attribute(
      String fieldName, IRowMeta layout, String description) {
    SemanticAttribute attribute = new SemanticAttribute(fieldName, fieldName);
    attribute.setHeader(humanize(fieldName));
    attribute.setDescription(description);
    attribute.setFormatMask(SemanticFormatSupport.defaultMask(hopType(layout, fieldName)));
    return attribute;
  }

  private static SemanticEntityRole roleOf(IDmTable table) {
    DmTableType type = table.getTableType();
    if (type == null) {
      return SemanticEntityRole.DIMENSION;
    }
    return switch (type) {
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          SemanticEntityRole.FACT;
      case BRIDGE -> SemanticEntityRole.BRIDGE;
      default -> SemanticEntityRole.DIMENSION;
    };
  }

  private static IRowMeta layoutOrNull(
      IDmTable table,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      return table.getTargetTableLayout(metadataProvider, variables, model);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static int hopType(IRowMeta layout, String fieldName) {
    if (layout == null || Utils.isEmpty(fieldName)) {
      return IValueMeta.TYPE_NONE;
    }
    int index = layout.indexOfValue(fieldName);
    if (index < 0) {
      return IValueMeta.TYPE_NONE;
    }
    return layout.getValueMeta(index).getType();
  }

  private static String physicalName(IDmTable table) {
    return Const.NVL(table.getTableName(), Const.NVL(table.getName(), ""));
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value) || variables == null) {
      return value;
    }
    return variables.resolve(value);
  }

  private static String humanize(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return "";
    }
    String spaced = fieldName.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }
}
