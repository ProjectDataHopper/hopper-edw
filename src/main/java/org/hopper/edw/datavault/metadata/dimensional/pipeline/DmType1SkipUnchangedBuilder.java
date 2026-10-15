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
package org.hopper.edw.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Condition;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.stream.IStream;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.MergeRowsMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;

/**
 * Type 1 load that compares the source with the dimension table and sends only {@code new} and
 * {@code changed} rows to Insert/Update.
 */
public final class DmType1SkipUnchangedBuilder {

  static final String FLAG_FIELD = "flag";
  private static final Point LOCATION_TARGET = new Point(100, 360);

  private DmType1SkipUnchangedBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmDimension dimension)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, dimension);
    if (ctx == null) {
      throw new HopException("Unable to create build context for dimension " + dimension.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    List<String> naturalKeys =
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables);
    List<String> valueFields = compareValueFields(ctx, dimension, naturalKeys);
    Set<String> excluded = auditFieldNames(ctx, dimension);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    TransformMeta compareSelect =
        addCompareSelect(ctx, pipelineMeta, sourceTransform, dimension, naturalKeys, excluded);
    TransformMeta compareSort =
        addSort(
            pipelineMeta,
            compareSelect,
            naturalKeys,
            "sort_source_" + ctx.targetTableName);

    TransformMeta targetTransform = addTargetTableInput(ctx, pipelineMeta, naturalKeys, valueFields);
    GeneratedPipelineMetadataSupport.stampTargetRead(
        targetTransform, "dimension", dimension.getName(), ctx.targetTableName, ctx.targetDbName);
    TransformMeta referenceSelect =
        addReferenceSelect(pipelineMeta, targetTransform, naturalKeys, valueFields);
    TransformMeta referenceSort =
        addSort(
            pipelineMeta,
            referenceSelect,
            naturalKeys,
            "sort_target_" + ctx.targetTableName);

    TransformMeta mergeTransform =
        addMergeRows(
            pipelineMeta, compareSort, referenceSort, naturalKeys, valueFields, ctx.targetTableName);
    GeneratedPipelineMetadataSupport.stampCdcMerge(
        mergeTransform, "dimension", dimension.getName(), ctx.targetTableName, ctx.targetDbName);
    TransformMeta filterTransform = addFilterNewOrChanged(pipelineMeta, mergeTransform);
    TransformMeta writePredecessor =
        DmPipelineBuilderSupport.addLoadCycleConstantIfEnabled(ctx, pipelineMeta, filterTransform);
    TransformMeta insertUpdateTransform =
        DmInsertUpdateBuilder.addInsertUpdate(ctx, pipelineMeta, writePredecessor, dimension);
    if (insertUpdateTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          insertUpdateTransform,
          "dimension",
          dimension.getName(),
          ctx.targetTableName,
          ctx.targetDbName);
    }

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  /**
   * Type 1 attributes that decide a row changed. The load timestamp is excluded: a date generator
   * stamps it with {@code @now}, so comparing it would mark every calendar row changed.
   */
  static List<String> compareValueFields(
      DmPipelineBuilderSupport.BuildContext ctx,
      DmDimension dimension,
      List<String> naturalKeys) {
    Set<String> skip = auditFieldNames(ctx, dimension);
    List<String> values = new ArrayList<>();
    for (String attribute : DmPipelineBuilderSupport.type1AttributeFieldNames(dimension, ctx.variables)) {
      if (Utils.isEmpty(attribute) || naturalKeys.contains(attribute) || skip.contains(attribute)) {
        continue;
      }
      values.add(attribute);
    }
    return values;
  }

  private static Set<String> auditFieldNames(
      DmPipelineBuilderSupport.BuildContext ctx, DmDimension dimension) {
    Set<String> names = new LinkedHashSet<>();
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    if (!Utils.isEmpty(loadDateField)) {
      names.add(loadDateField);
    }
    if (ctx.config.isStoreLoadCycleId()) {
      String cycleField =
          DvLoadCycleSupport.resolveFieldName(ctx.config.getLoadCycleIdField(), ctx.variables);
      if (!Utils.isEmpty(cycleField)) {
        names.add(cycleField);
      }
    }
    String surrogateField =
        DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, ctx.config, ctx.variables);
    if (!Utils.isEmpty(surrogateField)) {
      names.add(surrogateField);
    }
    return names;
  }

  private static TransformMeta addCompareSelect(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      List<String> naturalKeys,
      Set<String> excluded)
      throws HopException {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    Set<String> selected = new LinkedHashSet<>();
    for (String naturalKey : naturalKeys) {
      addSelectField(selectFields, naturalKey, naturalKey);
      selected.add(naturalKey);
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      String target =
          DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, ctx.variables);
      if (Utils.isEmpty(target) || naturalKeys.contains(target) || excluded.contains(target)) {
        continue;
      }
      String source =
          DmDimensionLoadStrategySupport.resolveSourceFieldName(attribute, ctx.variables);
      addSelectField(selectFields, source, target);
      selected.add(target);
    }
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    if (!Utils.isEmpty(loadDateField) && !selected.contains(loadDateField)) {
      addSelectField(selectFields, loadDateField, loadDateField);
    }

    return addSelectTransform(pipelineMeta, predecessor, "select_merge_compare", selectMeta);
  }

  private static TransformMeta addReferenceSelect(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> naturalKeys,
      List<String> valueFields) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    for (String naturalKey : naturalKeys) {
      addSelectField(selectFields, naturalKey, naturalKey);
    }
    for (String valueField : valueFields) {
      addSelectField(selectFields, valueField, valueField);
    }
    return addSelectTransform(pipelineMeta, predecessor, "select_merge_reference", selectMeta);
  }

  private static void addSelectField(List<SelectField> fields, String sourceName, String targetName) {
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    SelectField selectField = new SelectField();
    selectField.setName(sourceName);
    if (!sourceName.equals(targetName)) {
      selectField.setRename(targetName);
    }
    fields.add(selectField);
  }

  private static TransformMeta addSelectTransform(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String name,
      SelectValuesMeta selectMeta) {
    TransformMeta transform = new TransformMeta("SelectValues", name, selectMeta);
    transform.setLocation(nextLocation(predecessor));
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, transform));
    return transform;
  }

  private static TransformMeta addTargetTableInput(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      List<String> naturalKeys,
      List<String> valueFields) {
    List<String> columns = new ArrayList<>();
    for (String naturalKey : naturalKeys) {
      columns.add(ctx.targetDatabaseMeta.quoteField(naturalKey));
    }
    for (String valueField : valueFields) {
      columns.add(ctx.targetDatabaseMeta.quoteField(valueField));
    }

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.targetDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql.toString());

    TransformMeta transform =
        new TransformMeta("TableInput", "target_" + ctx.targetTableName, tableInputMeta);
    transform.setLocation(LOCATION_TARGET.x, LOCATION_TARGET.y);
    pipelineMeta.addTransform(transform);
    return transform;
  }

  private static TransformMeta addSort(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> naturalKeys,
      String name) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    for (String naturalKey : naturalKeys) {
      SortRowsField sortField = new SortRowsField();
      sortField.setFieldName(naturalKey);
      sortField.setAscending(true);
      sortRowsMeta.getSortFields().add(sortField);
    }
    TransformMeta transform = new TransformMeta("SortRows", name, sortRowsMeta);
    transform.setLocation(nextLocation(predecessor));
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, transform));
    return transform;
  }

  private static TransformMeta addMergeRows(
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform,
      List<String> naturalKeys,
      List<String> valueFields,
      String targetTableName) {
    MergeRowsMeta mergeRowsMeta = new MergeRowsMeta();
    mergeRowsMeta.setReferenceTransform(referenceTransform.getName());
    mergeRowsMeta.setCompareTransform(compareTransform.getName());
    mergeRowsMeta.setFlagField(FLAG_FIELD);
    mergeRowsMeta.setAlignInputLayouts(true);
    mergeRowsMeta.setKeyFields(new ArrayList<>(naturalKeys));
    mergeRowsMeta.setValueFields(new ArrayList<>(valueFields));
    List<IStream> infoStreams = mergeRowsMeta.getTransformIOMeta().getInfoStreams();
    infoStreams.get(0).setSubject(referenceTransform.getName());
    infoStreams.get(1).setSubject(compareTransform.getName());

    TransformMeta transform =
        new TransformMeta("MergeRows", "merge_diff_" + targetTableName, mergeRowsMeta);
    transform.setLocation(nextLocation(compareTransform));
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceTransform, transform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareTransform, transform));
    return transform;
  }

  private static TransformMeta addFilterNewOrChanged(
      PipelineMeta pipelineMeta, TransformMeta mergeTransform) throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    if (filterRowsMeta.getCompare() == null) {
      filterRowsMeta.setDefault();
    }
    try {
      Condition newCondition =
          new Condition(
              FLAG_FIELD,
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), "new"));
      Condition changedCondition =
          new Condition(
              FLAG_FIELD,
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("changed"), "changed"));
      changedCondition.setOperator(Condition.Operator.OR);
      Condition condition = new Condition();
      condition.addCondition(newCondition);
      condition.addCondition(changedCondition);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating Type 1 new-or-changed filter", e);
    }

    TransformMeta transform = new TransformMeta("FilterRows", "new or changed", filterRowsMeta);
    transform.setLocation(nextLocation(mergeTransform));
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, transform));
    return transform;
  }

  private static Point nextLocation(TransformMeta predecessor) {
    if (predecessor == null || predecessor.getLocation() == null) {
      return new Point(DmPipelineBuilderSupport.LOCATION_START.x, DmPipelineBuilderSupport.LOCATION_START.y);
    }
    return new Point(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
  }
}
