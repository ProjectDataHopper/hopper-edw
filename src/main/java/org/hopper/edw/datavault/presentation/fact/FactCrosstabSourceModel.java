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
package org.hopper.edw.datavault.presentation.fact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
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
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.semantic.model.SemanticAttribute;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticRelationship;

/**
 * Fact table plus related dimensions/junk tables that can supply crosstab columns.
 *
 * <p>Range-dimension roles contribute the fact's band-label column; they are not join sources.
 */
public final class FactCrosstabSourceModel {

  private final IDmFactLikeTable fact;
  private final List<FactCrosstabSourceTable> tables;

  private FactCrosstabSourceModel(IDmFactLikeTable fact, List<FactCrosstabSourceTable> tables) {
    this.fact = fact;
    this.tables = tables;
  }

  public IDmFactLikeTable getFact() {
    return fact;
  }

  public List<FactCrosstabSourceTable> getTables() {
    return tables;
  }

  public FactCrosstabSourceTable findTable(String logicalName) {
    if (logicalName == null) {
      return null;
    }
    for (FactCrosstabSourceTable table : tables) {
      if (table != null && logicalName.equals(table.getLogicalName())) {
        return table;
      }
    }
    return null;
  }

  public FactCrosstabSourceTable factTable() {
    for (FactCrosstabSourceTable table : tables) {
      if (table != null && table.isFact()) {
        return table;
      }
    }
    return tables.isEmpty() ? null : tables.get(0);
  }

  public static FactCrosstabSourceModel build(
      DimensionalModel model,
      IDmFactLikeTable fact,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || fact == null) {
      throw new HopException("Fact crosstab requires a dimensional model and a fact table");
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    List<FactCrosstabSourceTable> tables = new ArrayList<>();
    tables.add(buildFactTable(model, fact, config, variables, metadataProvider));

    Set<String> usedLogical = new LinkedHashSet<>();
    usedLogical.add(fact.getName());

    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      String logicalName = resolve(role.getDimensionTableName(), variables);
      if (Utils.isEmpty(logicalName) || !usedLogical.add(logicalName)) {
        continue;
      }
      FactCrosstabSourceTable sourceTable =
          buildDimensionTable(model, fact, role, logicalName, config, variables, metadataProvider);
      if (sourceTable != null) {
        tables.add(sourceTable);
      }
    }

    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getJunkDimensionTableName())) {
        continue;
      }
      String logicalName = resolve(role.getJunkDimensionTableName(), variables);
      if (Utils.isEmpty(logicalName) || !usedLogical.add(logicalName)) {
        continue;
      }
      FactCrosstabSourceTable sourceTable =
          buildJunkTable(model, role, logicalName, config, variables, metadataProvider);
      if (sourceTable != null) {
        tables.add(sourceTable);
      }
    }

    return new FactCrosstabSourceModel(fact, tables);
  }

  /**
   * Crosstab field list from a semantic fact entity and its relationships. Physical join keys and
   * table names come from the `.hsl`; hop types and canvas table types are filled from the bound
   * `.hdm` when present.
   */
  public static FactCrosstabSourceModel fromSemantic(
      SemanticModel semantic,
      SemanticEntity factEntity,
      DimensionalModel dimensionalModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (semantic == null || factEntity == null) {
      throw new HopException("Fact crosstab requires a semantic layer and a fact entity");
    }
    IDmFactLikeTable fact = resolveFactTable(dimensionalModel, factEntity);
    if (fact == null) {
      throw new HopException(
          "Semantic fact '"
              + factEntity.getName()
              + "' is not a fact table on the bound dimensional model");
    }
    List<FactCrosstabSourceTable> tables = new ArrayList<>();
    tables.add(tableFromEntity(factEntity, dimensionalModel, variables, metadataProvider, null));

    Set<String> usedLogical = new LinkedHashSet<>();
    usedLogical.add(factEntity.getName());
    for (SemanticRelationship relationship : semantic.relationshipsFrom(factEntity.getName())) {
      if (relationship == null || Utils.isEmpty(relationship.getToEntity())) {
        continue;
      }
      if (!usedLogical.add(relationship.getToEntity())) {
        continue;
      }
      SemanticEntity related = semantic.findEntity(relationship.getToEntity());
      if (related == null) {
        continue;
      }
      tables.add(
          tableFromEntity(related, dimensionalModel, variables, metadataProvider, relationship));
    }
    return new FactCrosstabSourceModel(fact, tables);
  }

  private static FactCrosstabSourceTable tableFromEntity(
      SemanticEntity entity,
      DimensionalModel dimensionalModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SemanticRelationship joinFromFact) {
    FactCrosstabSourceTable table = new FactCrosstabSourceTable();
    table.setLogicalName(entity.getName());
    table.setPhysicalTableName(entity.resolvePhysicalTableName());
    table.setJoinAlias(FactCrosstabSqlBuilder.sanitizeAlias(entity.getName()));
    IDmTable canvasTable =
        dimensionalModel != null ? dimensionalModel.findTable(entity.getName()) : null;
    table.setTableType(tableTypeOf(entity, canvasTable));
    if (joinFromFact != null) {
      table.setFactForeignKey(joinFromFact.getFromField());
      table.setDimensionKeyColumn(joinFromFact.getToField());
    }
    IRowMeta layout =
        canvasTable != null
            ? layoutOrNull(canvasTable, dimensionalModel, variables, metadataProvider)
            : null;
    Set<String> added = new LinkedHashSet<>();
    if (entity.getAttributes() != null) {
      for (SemanticAttribute attribute : entity.getAttributes()) {
        if (attribute == null || Utils.isEmpty(attribute.getName())) {
          continue;
        }
        String fieldName = attribute.resolvePhysicalColumn();
        if (Utils.isEmpty(fieldName) || !added.add(fieldName)) {
          continue;
        }
        String header =
            !Utils.isEmpty(attribute.getHeader())
                ? attribute.getHeader()
                : FactCrosstabLabels.humanize(attribute.getName());
        table
            .getColumns()
            .add(
                new FactCrosstabSourceColumn(
                    fieldName,
                    header,
                    FactCrosstabSourceColumn.Kind.ATTRIBUTE,
                    hopType(layout, fieldName),
                    false));
      }
    }
    if (entity.getMeasures() != null) {
      for (SemanticMeasure measure : entity.getMeasures()) {
        if (measure == null || Utils.isEmpty(measure.getName())) {
          continue;
        }
        String fieldName = measure.resolvePhysicalColumn();
        if (Utils.isEmpty(fieldName) || !added.add(fieldName)) {
          continue;
        }
        String header =
            !Utils.isEmpty(measure.getHeader())
                ? measure.getHeader()
                : FactCrosstabLabels.humanize(measure.getName());
        table
            .getColumns()
            .add(
                new FactCrosstabSourceColumn(
                    fieldName,
                    header,
                    FactCrosstabSourceColumn.Kind.MEASURE,
                    hopType(layout, fieldName),
                    measure.isAdditive()));
      }
    }
    return table;
  }

  private static IDmFactLikeTable resolveFactTable(
      DimensionalModel dimensionalModel, SemanticEntity factEntity) {
    if (dimensionalModel == null || factEntity == null) {
      return null;
    }
    IDmTable table = dimensionalModel.findTable(factEntity.getName());
    if (table instanceof IDmFactLikeTable fact) {
      return fact;
    }
    if (factEntity.getBinding() != null && !Utils.isEmpty(factEntity.getBinding().getTableName())) {
      table = dimensionalModel.findTable(factEntity.getBinding().getTableName());
      if (table instanceof IDmFactLikeTable fact) {
        return fact;
      }
    }
    return null;
  }

  private static DmTableType tableTypeOf(SemanticEntity entity, IDmTable canvasTable) {
    if (canvasTable != null && canvasTable.getTableType() != null) {
      return canvasTable.getTableType();
    }
    if (entity == null) {
      return DmTableType.DIMENSION;
    }
    return switch (entity.resolveRole()) {
      case FACT -> DmTableType.FACT;
      case BRIDGE -> DmTableType.BRIDGE;
      default -> DmTableType.DIMENSION;
    };
  }

  private static FactCrosstabSourceTable buildFactTable(
      DimensionalModel model,
      IDmFactLikeTable fact,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    FactCrosstabSourceTable table = new FactCrosstabSourceTable();
    table.setLogicalName(fact.getName());
    table.setPhysicalTableName(physicalName(fact));
    table.setJoinAlias(FactCrosstabSqlBuilder.sanitizeAlias(fact.getName()));
    table.setTableType(fact.getTableType() != null ? fact.getTableType() : DmTableType.FACT);
    IRowMeta layout = layoutOrNull(fact, model, variables, metadataProvider);
    Set<String> added = new LinkedHashSet<>();

    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      if (measure == null || Utils.isEmpty(measure.getFieldName())) {
        continue;
      }
      addColumn(
          table,
          added,
          measure.getFieldName(),
          FactCrosstabSourceColumn.Kind.MEASURE,
          layout,
          measure.isAdditive());
    }
    for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
      if (degenerate == null || Utils.isEmpty(degenerate.getFieldName())) {
        continue;
      }
      addColumn(
          table,
          added,
          degenerate.getFieldName(),
          FactCrosstabSourceColumn.Kind.DEGENERATE,
          layout,
          false);
    }
    for (DmFactRangeDimensionRole rangeRole : fact.getRangeDimensionRolesOrEmpty()) {
      if (rangeRole == null || Utils.isEmpty(rangeRole.getTargetFieldName())) {
        continue;
      }
      addColumn(
          table,
          added,
          rangeRole.getTargetFieldName(),
          FactCrosstabSourceColumn.Kind.RANGE,
          layout,
          false);
    }
    return table;
  }

  private static FactCrosstabSourceTable buildDimensionTable(
      DimensionalModel model,
      IDmFactLikeTable fact,
      DmFactDimensionRole role,
      String logicalName,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    IDmTable canvasTable = model.findTable(logicalName);
    DmDimension dimension =
        DmDimensionResolutionSupport.resolveDimension(
            model, logicalName, variables, metadataProvider);
    if (dimension == null) {
      return null;
    }
    FactCrosstabSourceTable table = new FactCrosstabSourceTable();
    table.setLogicalName(logicalName);
    table.setPhysicalTableName(physicalName(dimension));
    table.setJoinAlias(FactCrosstabSqlBuilder.sanitizeAlias(logicalName));
    table.setTableType(
        canvasTable instanceof DmDimensionAlias
            ? DmTableType.DIMENSION_ALIAS
            : DmTableType.DIMENSION);
    table.setFactForeignKey(resolve(role.getForeignKeyColumn(), variables));
    String sk = DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
    if (Utils.isEmpty(sk)) {
      sk = resolve(dimension.getSurrogateKeyField(), variables);
    }
    if (Utils.isEmpty(sk) && !dimension.getNaturalKeysOrEmpty().isEmpty()) {
      sk = resolve(dimension.getNaturalKeysOrEmpty().get(0).getFieldName(), variables);
    }
    table.setDimensionKeyColumn(sk);

    IRowMeta layout = layoutOrNull(dimension, model, variables, metadataProvider);
    Set<String> added = new LinkedHashSet<>();
    Set<String> technical = technicalColumns(config, variables);
    if (!Utils.isEmpty(sk)) {
      technical.add(sk.toLowerCase(Locale.ROOT));
    }
    for (DmNaturalKeyField naturalKey : dimension.getNaturalKeysOrEmpty()) {
      if (naturalKey == null || Utils.isEmpty(naturalKey.getFieldName())) {
        continue;
      }
      addColumnIfNotTechnical(
          table,
          added,
          technical,
          naturalKey.getFieldName(),
          FactCrosstabSourceColumn.Kind.NATURAL_KEY,
          layout);
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      addColumnIfNotTechnical(
          table,
          added,
          technical,
          attribute.getFieldName(),
          FactCrosstabSourceColumn.Kind.ATTRIBUTE,
          layout);
    }
    return table;
  }

  private static FactCrosstabSourceTable buildJunkTable(
      DimensionalModel model,
      DmFactJunkDimensionRole role,
      String logicalName,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    DmJunkDimension junk =
        DmJunkDimensionSupport.resolveJunkDimension(model, logicalName, variables);
    if (junk == null) {
      return null;
    }
    FactCrosstabSourceTable table = new FactCrosstabSourceTable();
    table.setLogicalName(logicalName);
    table.setPhysicalTableName(physicalName(junk));
    table.setJoinAlias(FactCrosstabSqlBuilder.sanitizeAlias(logicalName));
    table.setTableType(DmTableType.JUNK_DIMENSION);
    table.setFactForeignKey(resolve(role.getForeignKeyColumn(), variables));
    table.setDimensionKeyColumn(
        DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junk, config, variables));
    IRowMeta layout = layoutOrNull(junk, model, variables, metadataProvider);
    Set<String> added = new LinkedHashSet<>();
    Set<String> technical = technicalColumns(config, variables);
    if (!Utils.isEmpty(table.getDimensionKeyColumn())) {
      technical.add(table.getDimensionKeyColumn().toLowerCase(Locale.ROOT));
    }
    for (DmNaturalKeyField keyField : junk.getKeyFieldsOrEmpty()) {
      if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
        continue;
      }
      addColumnIfNotTechnical(
          table,
          added,
          technical,
          keyField.getFieldName(),
          FactCrosstabSourceColumn.Kind.ATTRIBUTE,
          layout);
    }
    return table;
  }

  private static void addColumnIfNotTechnical(
      FactCrosstabSourceTable table,
      Set<String> added,
      Set<String> technical,
      String fieldName,
      FactCrosstabSourceColumn.Kind kind,
      IRowMeta layout) {
    if (technical.contains(fieldName.toLowerCase(Locale.ROOT))) {
      return;
    }
    addColumn(table, added, fieldName, kind, layout, false);
  }

  private static void addColumn(
      FactCrosstabSourceTable table,
      Set<String> added,
      String fieldName,
      FactCrosstabSourceColumn.Kind kind,
      IRowMeta layout,
      boolean additiveMeasure) {
    if (!added.add(fieldName)) {
      return;
    }
    int valueType = IValueMeta.TYPE_NONE;
    if (layout != null) {
      int index = layout.indexOfValue(fieldName);
      if (index >= 0) {
        valueType = layout.getValueMeta(index).getType();
      }
    }
    table
        .getColumns()
        .add(
            new FactCrosstabSourceColumn(
                fieldName,
                FactCrosstabLabels.humanize(fieldName),
                kind,
                valueType,
                additiveMeasure));
  }

  private static Set<String> technicalColumns(
      DimensionalConfiguration config, IVariables variables) {
    Set<String> names = new LinkedHashSet<>();
    addTechnical(names, config.resolveDimKeyField(variables));
    addTechnical(names, config.resolveVersionField(variables));
    addTechnical(names, config.resolveDateFromField(variables));
    addTechnical(names, config.resolveDateToField(variables));
    addTechnical(names, config.resolveLoadDateField(variables));
    addTechnical(names, config.resolveCurrentFlagField(variables));
    return names;
  }

  private static void addTechnical(Set<String> names, String field) {
    if (!Utils.isEmpty(field)) {
      names.add(field.toLowerCase(Locale.ROOT));
    }
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

  static String physicalName(IDmTable table) {
    if (table == null) {
      return "";
    }
    return Const.NVL(table.getTableName(), Const.NVL(table.getName(), ""));
  }

  static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
