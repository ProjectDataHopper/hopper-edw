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
import java.util.Date;
import java.util.List;
import org.apache.hop.core.Condition;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculationType;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMetaFunction;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateField;
import org.apache.hop.pipeline.transforms.update.UpdateKeyField;
import org.apache.hop.pipeline.transforms.update.UpdateLookupField;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;

/** Generates SCD Type 2 dimension pipelines using the proven satellite MergeRowsPlus pattern. */
public final class DmScd2DimensionBuilder {

  private static final Point LOCATION_TARGET = new Point(100, 280);
  private static final int MERGE_INTEGER_FIELD_LENGTH = 9;

  private DmScd2DimensionBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmDimension dimension,
      Date loadTimestamp)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, dimension);
    if (ctx == null) {
      throw new HopException("Unable to create build context for dimension " + dimension.getName());
    }
    if (loadTimestamp == null) {
      loadTimestamp = new Date();
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    TransformMeta sortTransform = addSortForCollapse(ctx, pipelineMeta, sourceTransform, dimension);
    TransformMeta collapseTransform =
        addCollapseSourcePerNaturalKey(ctx, pipelineMeta, sortTransform, dimension);
    TransformMeta compareTransform =
        addEffectiveDates(ctx, pipelineMeta, loadTimestamp, collapseTransform, dimension);
    TransformMeta targetTransform = addTargetTableInput(ctx, pipelineMeta, dimension);
    if (targetTransform != null) {
      GeneratedPipelineMetadataSupport.stampTargetRead(
          targetTransform, "dimension", dimension.getName(), ctx.targetTableName, ctx.targetDbName);
    }
    TransformMeta mergeTransform =
        addMergeRows(ctx, pipelineMeta, compareTransform, targetTransform, dimension);
    if (mergeTransform != null) {
      GeneratedPipelineMetadataSupport.stampCdcMerge(
          mergeTransform, "dimension", dimension.getName(), ctx.targetTableName, ctx.targetDbName);
    }
    TransformMeta filterTransform = addFilterNewOrChanged(pipelineMeta, mergeTransform);
    TransformMeta versionedFilterTransform =
        addFilterNeedsVersionedUpdate(ctx, pipelineMeta, filterTransform);
    TransformMeta inPlaceFilterTransform =
        addFilterNeedsInPlaceUpdate(ctx, pipelineMeta, filterTransform);
    TransformMeta versionTransform =
        addVersionCalculator(ctx, pipelineMeta, versionedFilterTransform);
    TransformMeta controlFieldsTransform =
        addCurrentFlagConstant(ctx, pipelineMeta, versionTransform, loadTimestamp, "");
    IRowMeta targetLayout = dimension.getTargetTableLayout(metadataProvider, variables, model);
    TransformMeta tableOutputTransform =
        addScd2TableOutput(ctx, pipelineMeta, targetLayout, controlFieldsTransform);
    if (tableOutputTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          tableOutputTransform,
          "dimension",
          dimension.getName(),
          ctx.targetTableName,
          ctx.targetDbName);
    }
    TransformMeta filterPreviousTransform =
        addFilterHasPreviousVersion(pipelineMeta, tableOutputTransform);
    addClosePreviousVersion(ctx, pipelineMeta, filterPreviousTransform, dimension);
    addInPlaceScd2Update(ctx, pipelineMeta, inPlaceFilterTransform, dimension, loadTimestamp);

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  private static TransformMeta addSortForCollapse(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    for (String sortFieldName :
        DmPipelineBuilderSupport.scd2CollapseSortFieldNames(ctx, dimension)) {
      SortRowsField sortField = new SortRowsField();
      sortField.setFieldName(sortFieldName);
      sortField.setAscending(true);
      sortRowsMeta.getSortFields().add(sortField);
    }

    TransformMeta tm = new TransformMeta("SortRows", "sort_" + ctx.targetTableName, sortRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addCollapseSourcePerNaturalKey(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    GroupByMeta groupByMeta = new GroupByMeta();
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      groupByMeta.getGroupingFields().add(new GroupingField(naturalKey));
    }

    for (String aggregateField :
        DmPipelineBuilderSupport.scd2CollapseAggregateFieldNames(ctx, dimension)) {
      Aggregation aggregation = new Aggregation();
      aggregation.setSubject(aggregateField);
      aggregation.setField(aggregateField);
      aggregation.setTypeLabel("LAST_INCL_NULL");
      groupByMeta.getAggregations().add(aggregation);
    }

    TransformMeta tm = new TransformMeta("GroupBy", "collapse_" + ctx.targetTableName, groupByMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addEffectiveDates(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      Date loadTimestamp,
      TransformMeta predecessor,
      DmDimension dimension)
      throws HopException {
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    String openEnd = resolveOpenEndSentinel(ctx);
    String effectiveSource = dimension.resolveEffectiveDateSourceField(ctx.variables);
    boolean useSourceEffectiveDate = !Utils.isEmpty(effectiveSource);

    ValueMetaDate loadDateMeta = new ValueMetaDate("ld");
    loadDateMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String loadDateString = loadDateMeta.getString(loadTimestamp);

    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta.getFields().add(new ConstantField(dateToField, "String", openEnd));

    String versionField = ctx.config.resolveVersionField(ctx.variables);
    ConstantField versionConstant = new ConstantField(versionField, "Integer", "0");
    versionConstant.setFieldLength(MERGE_INTEGER_FIELD_LENGTH);
    constantMeta.getFields().add(versionConstant);

    if (!useSourceEffectiveDate) {
      ConstantField fromField = new ConstantField(dateFromField, "Date", loadDateString);
      fromField.setFieldFormat(loadDateMeta.getConversionMask());
      constantMeta.getFields().add(fromField);
    }

    ConstantField loadField = new ConstantField(loadDateField, "Date", loadDateString);
    loadField.setFieldFormat(loadDateMeta.getConversionMask());
    constantMeta.getFields().add(loadField);
    appendLoadCycleConstantField(ctx, constantMeta);

    TransformMeta constantTransform =
        new TransformMeta("Constant", "add_effective_dates", constantMeta);
    constantTransform.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(constantTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, constantTransform));

    TransformMeta current = constantTransform;
    if (useSourceEffectiveDate && !effectiveSource.equals(dateFromField)) {
      SelectValuesMeta selectMeta = new SelectValuesMeta();
      selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
      SelectField rename = new SelectField();
      rename.setName(effectiveSource);
      rename.setRename(dateFromField);
      selectMeta.getSelectOption().getSelectFields().add(rename);

      TransformMeta selectTransform =
          new TransformMeta(
              "SelectValues", "map_" + ctx.targetTableName + "_effective_date", selectMeta);
      selectTransform.setLocation(
          current.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
          current.getLocation().y);
      pipelineMeta.addTransform(selectTransform);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(current, selectTransform));
      current = selectTransform;
    }
    return current;
  }

  private static String resolveOpenEndSentinel(DmPipelineBuilderSupport.BuildContext ctx) {
    String openEnd = ctx.config.getOpenEndSentinel();
    if (ctx.variables != null) {
      openEnd = ctx.variables.resolve(openEnd);
    }
    return openEnd;
  }

  private static TransformMeta addTargetTableInput(
      DmPipelineBuilderSupport.BuildContext ctx, PipelineMeta pipelineMeta, DmDimension dimension) {
    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String openEnd = ctx.config.getOpenEndSentinel();
    if (ctx.variables != null) {
      openEnd = ctx.variables.resolve(openEnd);
    }

    List<String> selectFields = new ArrayList<>();
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String surrogateField =
          DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, ctx.config, ctx.variables);
      if (!Utils.isEmpty(surrogateField)) {
        selectFields.add(ctx.targetDatabaseMeta.quoteField(surrogateField));
      }
    }
    for (String attribute :
        DmPipelineBuilderSupport.scd2AttributeFieldNames(dimension, ctx.variables)) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(attribute));
    }
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(naturalKey));
    }
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    String versionField = ctx.config.resolveVersionField(ctx.variables);
    selectFields.add(ctx.targetDatabaseMeta.quoteField(versionField));
    selectFields.add(ctx.targetDatabaseMeta.quoteField(dateFromField));
    selectFields.add(ctx.targetDatabaseMeta.quoteField(dateToField));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    sql.append(" WHERE ");
    sql.append(ctx.targetDatabaseMeta.quoteField(dateToField));
    sql.append(" = '");
    sql.append(openEnd.replace("'", "''"));
    sql.append("'");

    List<String> orderByFields = new ArrayList<>();
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      orderByFields.add(ctx.targetDatabaseMeta.quoteField(naturalKey));
    }
    if (!orderByFields.isEmpty()) {
      sql.append(" ORDER BY ");
      sql.append(String.join(", ", orderByFields));
    }

    DvSqlSupport.assignDisplaySql(targetTableInputMeta, sql.toString());

    TransformMeta tm =
        new TransformMeta("TableInput", "target_" + ctx.targetTableName, targetTableInputMeta);
    tm.setLocation(LOCATION_TARGET.x, LOCATION_TARGET.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addMergeRows(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform,
      DmDimension dimension) {
    TransformMeta compareForMerge =
        addMergeCompareSelectValues(ctx, pipelineMeta, compareTransform, dimension);
    TransformMeta referenceForMerge =
        addMergeReferenceSelectValues(ctx, pipelineMeta, referenceTransform, dimension);

    MergeRowsPlusMeta mergeRowsMeta = new MergeRowsPlusMeta();
    mergeRowsMeta.setReferenceTransform(referenceForMerge.getName());
    mergeRowsMeta.setCompareTransform(compareForMerge.getName());
    mergeRowsMeta.setFlagField("flag");
    mergeRowsMeta.setKeyFields(
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables));
    mergeRowsMeta
        .getValueFields()
        .addAll(DmPipelineBuilderSupport.scd2AttributeFieldNames(dimension, ctx.variables));

    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    String versionField = ctx.config.resolveVersionField(ctx.variables);
    mergeRowsMeta
        .getPassThroughFields()
        .add(
            new PassThroughField(
                dateFromField, DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD, true));
    mergeRowsMeta
        .getPassThroughFields()
        .add(
            new PassThroughField(
                versionField, DmPipelineBuilderSupport.PREVIOUS_VERSION_NUM_FIELD, true));
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String surrogateField =
          DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, ctx.config, ctx.variables);
      if (!Utils.isEmpty(surrogateField)) {
        mergeRowsMeta
            .getPassThroughFields()
            .add(new PassThroughField(surrogateField, surrogateField, true));
      }
    }

    TransformMeta tm = new TransformMeta("MergeRowsPlus", "merge_diff", mergeRowsMeta);
    tm.setLocation(
        compareForMerge.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        compareForMerge.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceForMerge, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareForMerge, tm));
    return tm;
  }

  private static TransformMeta addMergeCompareSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    for (String targetField : DmPipelineBuilderSupport.scd2MergeRowFieldNames(ctx, dimension)) {
      String sourceField =
          DmPipelineBuilderSupport.scd2MergeCompareSourceFieldName(ctx, dimension, targetField);
      SelectField selectField = new SelectField();
      selectField.setName(sourceField);
      if (!sourceField.equals(targetField)) {
        selectField.setRename(targetField);
      }
      selectFields.add(selectField);
    }
    addScd2MergeMetadataChanges(ctx, selectMeta.getSelectOption().getMeta());

    TransformMeta tm = new TransformMeta("SelectValues", "select_merge_compare", selectMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addMergeReferenceSelectValues(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    for (String field : DmPipelineBuilderSupport.scd2MergeRowFieldNames(ctx, dimension)) {
      SelectField selectField = new SelectField();
      selectField.setName(field);
      selectFields.add(selectField);
    }
    addScd2MergeMetadataChanges(ctx, selectMeta.getSelectOption().getMeta());

    TransformMeta tm = new TransformMeta("SelectValues", "select_merge_reference", selectMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static void addScd2MergeMetadataChanges(
      DmPipelineBuilderSupport.BuildContext ctx, List<SelectMetadataChange> metaChanges) {
    String versionField = ctx.config.resolveVersionField(ctx.variables);
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);
    String dateToField = ctx.config.resolveDateToField(ctx.variables);

    SelectMetadataChange versionChange = new SelectMetadataChange();
    versionChange.setName(versionField);
    versionChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_INTEGER));
    versionChange.setLength(MERGE_INTEGER_FIELD_LENGTH);
    metaChanges.add(versionChange);

    SelectMetadataChange dateFromChange = new SelectMetadataChange();
    dateFromChange.setName(dateFromField);
    dateFromChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_TIMESTAMP));
    metaChanges.add(dateFromChange);

    SelectMetadataChange dateToChange = new SelectMetadataChange();
    dateToChange.setName(dateToField);
    dateToChange.setType(ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_TIMESTAMP));
    dateToChange.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    metaChanges.add(dateToChange);
  }

  private static TransformMeta addFilterNewOrChanged(
      PipelineMeta pipelineMeta, TransformMeta mergeTransform) throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition newCondition =
          new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), "new"));
      Condition changedCondition =
          new Condition(
              "flag",
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("changed"), "changed"));
      changedCondition.setOperator(Condition.Operator.OR);

      Condition condition = new Condition();
      condition.addCondition(newCondition);
      condition.addCondition(changedCondition);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating SCD2 filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "new_or_changed", filterRowsMeta);
    tm.setLocation(
        mergeTransform.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        mergeTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));
    return tm;
  }

  private static TransformMeta addFilterNeedsVersionedUpdate(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor)
      throws HopException {
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition previousMissing =
          new Condition(
              DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD, Condition.Function.NULL, null, null);
      Condition effectiveDateAdvanced =
          new Condition(
              dateFromField,
              Condition.Function.LARGER,
              DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD,
              null);
      effectiveDateAdvanced.setOperator(Condition.Operator.OR);

      Condition condition = new Condition();
      condition.addCondition(previousMissing);
      condition.addCondition(effectiveDateAdvanced);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating SCD2 versioned-update filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "needs_versioned_update", filterRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y - 40);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addFilterNeedsInPlaceUpdate(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor)
      throws HopException {
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition previousPresent =
          new Condition(
              DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD,
              Condition.Function.NOT_NULL,
              null,
              null);
      Condition effectiveDateNotAdvanced =
          new Condition(
              dateFromField,
              Condition.Function.SMALLER_EQUAL,
              DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD,
              null);
      effectiveDateNotAdvanced.setOperator(Condition.Operator.AND);

      Condition condition = new Condition();
      condition.addCondition(previousPresent);
      condition.addCondition(effectiveDateNotAdvanced);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating SCD2 in-place-update filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "needs_inplace_update", filterRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y + 40);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addFilterHasPreviousVersion(
      PipelineMeta pipelineMeta, TransformMeta predecessor) throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    Condition condition =
        new Condition(
            DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD,
            Condition.Function.NOT_NULL,
            null,
            null);
    filterRowsMeta.getCompare().setCondition(condition);

    TransformMeta tm = new TransformMeta("FilterRows", "has_previous_version", filterRowsMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addScd2TableOutput(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    String versionField = ctx.config.resolveVersionField(ctx.variables);
    String newVersionField = DmPipelineBuilderSupport.scd2NewVersionFieldName(versionField);

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection(ctx.targetDbName);
    tableOutputMeta.setTableName(ctx.targetTableName);
    tableOutputMeta.setSpecifyFields(true);
    tableOutputMeta.setTruncateTable(false);
    tableOutputMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    if (targetLayout != null) {
      for (IValueMeta valueMeta : targetLayout.getValueMetaList()) {
        String columnName = valueMeta.getName();
        if (columnName.equals(versionField)) {
          tableOutputMeta.getFields().add(new TableOutputField(columnName, newVersionField));
        } else {
          tableOutputMeta.getFields().add(new TableOutputField(columnName, columnName));
        }
      }
    }

    TransformMeta tm =
        new TransformMeta("TableOutput", "write_to_" + ctx.targetTableName, tableOutputMeta);
    tm.setCopiesString(ctx.config.resolveTargetTableParallelCopies(ctx.variables));
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addVersionCalculator(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor) {
    if (predecessor == null) {
      return null;
    }

    String versionField = ctx.config.resolveVersionField(ctx.variables);
    String tempZero = "_dm_version_zero";
    String tempBase = "_dm_version_base";
    String constOne = "_dm_version_one";
    String integerType = IValueMeta.getTypeDescription(IValueMeta.TYPE_INTEGER);

    CalculatorMeta calculatorMeta = new CalculatorMeta();
    List<CalculatorMetaFunction> functions = new ArrayList<>();
    functions.add(
        calculatorFunction(tempZero, CalculationType.CONSTANT, "0", null, integerType, true));
    functions.add(
        calculatorFunction(
            tempBase,
            CalculationType.NVL,
            DmPipelineBuilderSupport.PREVIOUS_VERSION_NUM_FIELD,
            tempZero,
            integerType,
            true));
    functions.add(
        calculatorFunction(constOne, CalculationType.CONSTANT, "1", null, integerType, true));
    functions.add(
        calculatorFunction(
            DmPipelineBuilderSupport.scd2NewVersionFieldName(versionField),
            CalculationType.ADD,
            tempBase,
            constOne,
            integerType,
            false));
    calculatorMeta.setFunctions(functions);

    TransformMeta tm =
        new TransformMeta("Calculator", "calc_" + ctx.targetTableName + "_version", calculatorMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addCurrentFlagConstant(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Date loadTimestamp,
      String nameSuffix)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    String currentFlagField = ctx.config.resolveCurrentFlagField(ctx.variables);
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    ValueMetaDate loadDateMeta = new ValueMetaDate("ld");
    loadDateMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String loadDateString = loadDateMeta.getString(loadTimestamp);

    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta.getFields().add(new ConstantField(currentFlagField, "Boolean", "Y"));
    ConstantField loadField = new ConstantField(loadDateField, "Date", loadDateString);
    loadField.setFieldFormat(loadDateMeta.getConversionMask());
    constantMeta.getFields().add(loadField);
    appendLoadCycleConstantField(ctx, constantMeta);

    TransformMeta tm =
        new TransformMeta(
            "Constant", "set_" + ctx.targetTableName + "_current" + nameSuffix, constantMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static void appendLoadCycleConstantField(
      DmPipelineBuilderSupport.BuildContext ctx, ConstantMeta constantMeta) throws HopException {
    if (ctx == null
        || ctx.config == null
        || !ctx.config.isStoreLoadCycleId()
        || constantMeta == null) {
      return;
    }
    Long cycleId =
        org.hopper.edw.datavault.metadata.DvLoadCycleSupport.resolveCycleIdFromVariables(
            ctx.variables);
    if (cycleId == null) {
      throw new HopException(
          "Load cycle id column is enabled but no cycle id is available (variable "
              + org.hopper.edw.datavault.metadata.DvLoadCycleSupport.VAR_LOAD_CYCLE_ID
              + ").");
    }
    String fieldName =
        org.hopper.edw.datavault.metadata.DvLoadCycleSupport.resolveFieldName(
            ctx.config.getLoadCycleIdField(), ctx.variables);
    constantMeta.getFields().add(new ConstantField(fieldName, "Integer", Long.toString(cycleId)));
  }

  private static CalculatorMetaFunction calculatorFunction(
      String fieldName,
      CalculationType calcType,
      String fieldA,
      String fieldB,
      String valueType,
      boolean removedFromResult) {
    CalculatorMetaFunction function = new CalculatorMetaFunction();
    function.setFieldName(fieldName);
    function.setCalcType(calcType);
    function.setFieldA(fieldA);
    if (!Utils.isEmpty(fieldB)) {
      function.setFieldB(fieldB);
    }
    function.setValueType(valueType);
    function.setRemovedFromResult(removedFromResult);
    return function;
  }

  private static TransformMeta addClosePreviousVersion(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    if (predecessor == null) {
      return null;
    }

    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String dateFromField = ctx.config.resolveDateFromField(ctx.variables);

    UpdateMeta updateMeta = new UpdateMeta();
    updateMeta.setDefault();
    updateMeta.setConnection(ctx.targetDbName);
    updateMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    UpdateLookupField lookup = new UpdateLookupField();
    lookup.setSchemaName("");
    lookup.setTableName(ctx.targetTableName);
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      lookup.getLookupKeys().add(new UpdateKeyField(naturalKey, naturalKey, "="));
    }
    lookup
        .getLookupKeys()
        .add(
            new UpdateKeyField(
                DmPipelineBuilderSupport.PREVIOUS_VERSION_FIELD, dateFromField, "="));
    lookup.getUpdateFields().add(new UpdateField(dateToField, dateFromField));
    updateMeta.setLookupField(lookup);

    TransformMeta tm = new TransformMeta("Update", "close_" + ctx.targetTableName, updateMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addInPlaceScd2Update(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      Date loadTimestamp)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    TransformMeta controlFieldsTransform =
        addCurrentFlagConstant(ctx, pipelineMeta, predecessor, loadTimestamp, "_inplace");

    String dateToField = ctx.config.resolveDateToField(ctx.variables);
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    String currentFlagField = ctx.config.resolveCurrentFlagField(ctx.variables);

    UpdateMeta updateMeta = new UpdateMeta();
    updateMeta.setDefault();
    updateMeta.setConnection(ctx.targetDbName);
    updateMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    UpdateLookupField lookup = new UpdateLookupField();
    lookup.setSchemaName("");
    lookup.setTableName(ctx.targetTableName);
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      lookup.getLookupKeys().add(new UpdateKeyField(naturalKey, naturalKey, "="));
    }
    lookup.getLookupKeys().add(new UpdateKeyField(dateToField, dateToField, "="));
    for (String attribute :
        DmPipelineBuilderSupport.scd2AttributeFieldNames(dimension, ctx.variables)) {
      lookup.getUpdateFields().add(new UpdateField(attribute, attribute));
    }
    lookup.getUpdateFields().add(new UpdateField(loadDateField, loadDateField));
    lookup.getUpdateFields().add(new UpdateField(currentFlagField, currentFlagField));
    updateMeta.setLookupField(lookup);

    TransformMeta tm =
        new TransformMeta("Update", "update_" + ctx.targetTableName + "_inplace", updateMeta);
    tm.setLocation(
        controlFieldsTransform.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        controlFieldsTransform.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(controlFieldsTransform, tm));
    return tm;
  }
}
