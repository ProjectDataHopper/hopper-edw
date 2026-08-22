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
package org.hopper.edw.datavault.virtualization.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.hop.core.Condition;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSourceSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceJsonPipelineGenerator;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQueryPipelineGenerator;
import org.hopper.edw.datavault.virtualization.calcite.HopTypeSystem;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelJsonTable;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelPipelineTable;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelQueryTable;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelTable;
import org.hopper.edw.datavault.virtualization.plan.PushdownClassifier;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlException;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlOptions;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.hopper.edw.datavault.virtualization.sql.SupportedSqlFeatures;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculationType;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMeta;
import org.apache.hop.pipeline.transforms.calculator.CalculatorMetaFunction;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/**
 * Converts a Calcite {@link RelNode} plan into a Hop {@link PipelineMeta}, preferring full SQL
 * pushdown when all scans share one DATABASE connection.
 */
public final class RelToPipelineGenerator {

  private RelToPipelineGenerator() {}

  public static SourceModelSqlPlan generate(
      RelNode rel,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceModelSqlOptions options)
      throws SourceModelSqlException {
    if (rel == null) {
      throw new SourceModelSqlException("RelNode plan is required");
    }
    SourceModelSqlOptions opts = options != null ? options : SourceModelSqlOptions.defaults();
    PushdownClassifier.Classification classification = PushdownClassifier.classify(rel);
    List<String> warnings = new ArrayList<>();

    try {
      if (opts.isPreferFullPushdown() && classification.fullDatabasePushdown()) {
        return generateFullPushdown(
            rel, classification, variables, metadataProvider, opts, warnings);
      }
      warnings.addAll(classification.reasons());
      return generateResidual(rel, model, variables, metadataProvider, opts, warnings);
    } catch (SourceModelSqlException e) {
      throw e;
    } catch (Exception e) {
      throw new SourceModelSqlException(
          "Failed to generate pipeline from SQL plan: " + e.getMessage(), e);
    }
  }

  private static SourceModelSqlPlan generateFullPushdown(
      RelNode rel,
      PushdownClassifier.Classification classification,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceModelSqlOptions opts,
      List<String> warnings)
      throws HopException {
    String connectionName = classification.sharedDatabaseName();
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new SourceModelSqlException("Database connection '" + connectionName + "' not found");
    }

    String sql = DialectSqlSupport.relToSql(rel, databaseMeta, variables);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(opts.getPipelineName());
    pipelineMeta.setMetadataProvider(metadataProvider);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql);
    if (opts.getPreviewRowLimit() > 0) {
      tableInputMeta.setRowLimit(Integer.toString(opts.getPreviewRowLimit()));
    }
    TransformMeta source = new TransformMeta("TableInput", "SQL source", tableInputMeta);
    source.setLocation(100, 100);
    pipelineMeta.addTransform(source);

    finalizePipelineMeta(pipelineMeta);

    IRowMeta rowMeta = HopTypeSystem.toRowMeta(rel.getRowType());
    return new SourceModelSqlPlan(
        pipelineMeta, source.getName(), rowMeta, List.of(sql), List.of(), warnings, true);
  }

  private static SourceModelSqlPlan generateResidual(
      RelNode rel,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceModelSqlOptions opts,
      List<String> warnings)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(opts.getPipelineName());
    pipelineMeta.setMetadataProvider(metadataProvider);

    List<String> pushdownSql = new ArrayList<>();
    List<String> residualOps = new ArrayList<>();
    GenerationContext ctx =
        new GenerationContext(
            pipelineMeta,
            model,
            variables,
            metadataProvider,
            opts,
            pushdownSql,
            residualOps,
            warnings);

    TransformMeta output = convert(rel, ctx, new Point(100, 100));
    finalizePipelineMeta(pipelineMeta);
    IRowMeta rowMeta = HopTypeSystem.toRowMeta(rel.getRowType());
    return new SourceModelSqlPlan(
        pipelineMeta, output.getName(), rowMeta, pushdownSql, residualOps, warnings, false);
  }

  /**
   * Wire parent pipeline/transform references and resolve INFO streams (MergeJoin left/right).
   * Programmatic pipelines never go through {@link PipelineMeta#lookupReferencesAfterLoading()}.
   */
  public static void finalizePipelineMeta(PipelineMeta pipelineMeta) {
    if (pipelineMeta == null) {
      return;
    }
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta == null) {
        continue;
      }
      transformMeta.setParentPipelineMeta(pipelineMeta);
      if (transformMeta.getTransform() != null) {
        transformMeta.getTransform().setParentTransformMeta(transformMeta);
        transformMeta.getTransform().searchInfoAndTargetTransforms(pipelineMeta.getTransforms());
      }
    }
  }

  private static TransformMeta convert(RelNode node, GenerationContext ctx, Point location)
      throws HopException {
    if (node instanceof TableScan scan) {
      return convertTableScan(scan, ctx, location);
    }
    if (node instanceof Project project) {
      TransformMeta input = convert(project.getInput(), ctx, new Point(location.x, location.y));
      return convertProject(project, input, ctx, new Point(location.x + 200, location.y));
    }
    if (node instanceof Filter filter) {
      TransformMeta input = convert(filter.getInput(), ctx, new Point(location.x, location.y));
      return convertFilter(filter, input, ctx, new Point(location.x + 200, location.y));
    }
    if (node instanceof Join join) {
      TransformMeta left = convert(join.getLeft(), ctx, new Point(location.x, location.y));
      TransformMeta right = convert(join.getRight(), ctx, new Point(location.x, location.y + 200));
      return convertJoin(join, left, right, ctx, new Point(location.x + 250, location.y + 100));
    }
    if (node instanceof Sort sort) {
      TransformMeta input = convert(sort.getInput(), ctx, new Point(location.x, location.y));
      return convertSort(sort, input, ctx, new Point(location.x + 200, location.y));
    }
    if (node instanceof Aggregate aggregate) {
      TransformMeta input = convert(aggregate.getInput(), ctx, new Point(location.x, location.y));
      return convertAggregate(aggregate, input, ctx, new Point(location.x + 200, location.y));
    }
    // Unwrap single-input operators we do not recognise (e.g. LogicalValues shouldn't appear).
    if (node.getInputs().size() == 1) {
      ctx.warnings.add("Skipping unsupported operator " + node.getRelTypeName());
      return convert(node.getInput(0), ctx, location);
    }
    throw new SourceModelSqlException(
        "Unsupported relational operator: "
            + node.getRelTypeName()
            + ". "
            + SupportedSqlFeatures.SUMMARY);
  }

  private static TransformMeta convertTableScan(
      TableScan scan, GenerationContext ctx, Point location) throws HopException {
    SourceModelQueryTable queryTable = scan.getTable().unwrap(SourceModelQueryTable.class);
    if (queryTable != null) {
      return convertQueryScan(queryTable, ctx, location);
    }
    SourceModelJsonTable jsonTable = scan.getTable().unwrap(SourceModelJsonTable.class);
    if (jsonTable != null) {
      return convertJsonScan(jsonTable, ctx, location);
    }
    SourceModelPipelineTable pipelineTable = scan.getTable().unwrap(SourceModelPipelineTable.class);
    if (pipelineTable != null) {
      return convertPipelineScan(pipelineTable, ctx, location);
    }
    SourceModelTable smt = scan.getTable().unwrap(SourceModelTable.class);
    if (smt == null) {
      throw new SourceModelSqlException("Table scan has no known source-model table");
    }
    if (smt.physicalType() != DvSourceType.DATABASE) {
      throw new SourceModelSqlException(
          "Residual free SQL path only supports DATABASE / JSON / PIPELINE tables (got "
              + smt.physicalType()
              + " for '"
              + smt.logicalName()
              + "')");
    }
    if (Utils.isEmpty(smt.databaseName())) {
      throw new SourceModelSqlException(
          "Table '" + smt.logicalName() + "' has no database connection");
    }
    DatabaseMeta databaseMeta =
        ctx.metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(
                ctx.variables != null
                    ? ctx.variables.resolve(smt.databaseName())
                    : smt.databaseName());
    if (databaseMeta == null) {
      throw new SourceModelSqlException(
          "Database connection '" + smt.databaseName() + "' not found");
    }

    String schema = smt.getSourceTable().getSchemaName();
    String table = smt.getSourceTable().getTableName();
    if (Utils.isEmpty(table)) {
      table = smt.logicalName();
    }
    if (ctx.variables != null) {
      if (!Utils.isEmpty(schema)) {
        schema = ctx.variables.resolve(schema);
      }
      table = ctx.variables.resolve(table);
    }

    // Select all columns with logical field names matching the scan row type.
    StringBuilder sql = new StringBuilder("SELECT ");
    List<RelDataTypeField> fields = scan.getRowType().getFieldList();
    if (fields.isEmpty()) {
      sql.append('*');
    } else {
      for (int i = 0; i < fields.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        String col = fields.get(i).getName();
        sql.append(databaseMeta.quoteField(col));
        // Keep output name = Calcite field name for residual operators.
        sql.append(" AS ").append(databaseMeta.quoteField(col));
      }
    }
    sql.append(" FROM ")
        .append(databaseMeta.getQuotedSchemaTableCombination(ctx.variables, schema, table));

    String sqlText = sql.toString();
    ctx.pushdownSql.add(sqlText);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sqlText);
    if (ctx.options.getPreviewRowLimit() > 0) {
      tableInputMeta.setRowLimit(Integer.toString(ctx.options.getPreviewRowLimit()));
    }
    String transformName = uniqueName(ctx, "Read " + smt.logicalName());
    TransformMeta transform = new TransformMeta("TableInput", transformName, tableInputMeta);
    transform.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(transform);
    ctx.residualOps.add("TableScan " + smt.logicalName() + " → Table Input");
    return transform;
  }

  private static TransformMeta convertQueryScan(
      SourceModelQueryTable queryTable, GenerationContext ctx, Point location) throws HopException {
    if (ctx.model == null) {
      throw new SourceModelSqlException("Source model is required to expand named Source Query");
    }
    SourceQuery query = queryTable.getSourceQuery();
    PipelineMeta subgraph =
        SourceQueryPipelineGenerator.generate(
            ctx.model, query, ctx.variables, ctx.metadataProvider);
    PipelineSubgraphMerger.MergedSubgraph merged =
        PipelineSubgraphMerger.merge(
            ctx.pipelineMeta, subgraph, location, base -> uniqueName(ctx, base));
    ctx.residualOps.add("TableScan Query " + queryTable.logicalName() + " → Source query graph");
    return merged.outputTransform();
  }

  private static TransformMeta convertJsonScan(
      SourceModelJsonTable jsonTable, GenerationContext ctx, Point location) throws HopException {
    if (ctx.model == null) {
      throw new SourceModelSqlException("Source model is required to expand JSON table scans");
    }
    SourceJson jsonSource = jsonTable.getSourceJson();
    PipelineMeta subgraph =
        SourceJsonPipelineGenerator.generate(
            ctx.model, jsonSource, ctx.variables, ctx.metadataProvider);
    PipelineSubgraphMerger.MergedSubgraph merged =
        PipelineSubgraphMerger.merge(
            ctx.pipelineMeta, subgraph, location, base -> uniqueName(ctx, base));
    ctx.residualOps.add("TableScan JSON " + jsonTable.logicalName() + " → Json pipeline");
    return merged.outputTransform();
  }

  private static TransformMeta convertPipelineScan(
      SourceModelPipelineTable pipelineTable, GenerationContext ctx, Point location)
      throws HopException {
    SourcePipeline sourcePipeline = pipelineTable.getSourcePipeline();
    DvPipelineSource dvSource = new DvPipelineSource();
    dvSource.setPipelineFilename(sourcePipeline.getPipelineFilename());
    dvSource.setOutputTransformName(sourcePipeline.getOutputTransformName());
    dvSource.setPipelineRunConfiguration(sourcePipeline.getPipelineRunConfiguration());
    List<SourceField> fields = new ArrayList<>();
    for (var col : sourcePipeline.getFields()) {
      if (col == null || Utils.isEmpty(col.getName())) {
        continue;
      }
      SourceField field = new SourceField(col.getName());
      field.setHopType(col.getHopType());
      field.setLength(col.getLength());
      field.setPrecision(col.getPrecision());
      fields.add(field);
    }
    dvSource.setFields(fields);

    MetaInjectMeta metaInjectMeta =
        DvPipelineSourceSupport.buildMetaInjectMeta(dvSource, ctx.variables, ctx.metadataProvider);
    String name = uniqueName(ctx, "Pipeline " + pipelineTable.logicalName());
    TransformMeta transform = new TransformMeta("MetaInject", name, metaInjectMeta);
    transform.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(transform);
    ctx.residualOps.add("TableScan PIPELINE " + pipelineTable.logicalName() + " → MetaInject");
    return transform;
  }

  private static TransformMeta convertAggregate(
      Aggregate aggregate, TransformMeta input, GenerationContext ctx, Point location)
      throws SourceModelSqlException {
    List<String> inputNames = aggregate.getInput().getRowType().getFieldNames();
    List<String> outputNames = aggregate.getRowType().getFieldNames();
    GroupByMeta groupByMeta = new GroupByMeta();
    groupByMeta.setAlwaysGivingBackOneRow(true);
    List<GroupingField> groupingFields = new ArrayList<>();
    ImmutableBitSet groupSet = aggregate.getGroupSet();
    int outIndex = 0;
    for (int bit : groupSet) {
      GroupingField gf = new GroupingField(inputNames.get(bit));
      groupingFields.add(gf);
      outIndex++;
    }
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();
    for (AggregateCall call : aggregate.getAggCallList()) {
      String outName =
          outIndex < outputNames.size()
              ? outputNames.get(outIndex)
              : (call.getName() != null ? call.getName() : "agg" + outIndex);
      outIndex++;
      int hopType = mapAggregateType(call);
      String subject = null;
      if (!call.getArgList().isEmpty()) {
        subject = inputNames.get(call.getArgList().get(0));
      } else if (hopType == Aggregation.TYPE_GROUP_COUNT_ANY) {
        subject = !inputNames.isEmpty() ? inputNames.get(0) : null;
      }
      Aggregation aggregation = new Aggregation();
      aggregation.setField(outName);
      aggregation.setSubject(subject);
      aggregation.setType(hopType);
      aggregations.add(aggregation);
    }
    groupByMeta.setAggregations(aggregations);

    // GroupBy requires sorted input on group keys for streaming behaviour when passAllRows is
    // false.
    TransformMeta stream = input;
    if (!groupingFields.isEmpty()) {
      List<String> sortKeys = groupingFields.stream().map(GroupingField::getName).toList();
      stream = addSort(ctx, input, sortKeys, new Point(location.x - 80, location.y));
    }

    String name = uniqueName(ctx, "GroupBy");
    TransformMeta transform = new TransformMeta("GroupBy", name, groupByMeta);
    transform.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(transform);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(stream, transform));
    ctx.residualOps.add("Aggregate → Group By (" + aggregations.size() + " agg(s))");
    return transform;
  }

  private static int mapAggregateType(AggregateCall call) throws SourceModelSqlException {
    SqlKind kind = call.getAggregation().getKind();
    return switch (kind) {
      case COUNT -> {
        if (call.isDistinct()) {
          yield Aggregation.TYPE_GROUP_COUNT_DISTINCT;
        }
        if (call.getArgList().isEmpty()) {
          yield Aggregation.TYPE_GROUP_COUNT_ANY;
        }
        yield Aggregation.TYPE_GROUP_COUNT_ALL;
      }
      case SUM -> Aggregation.TYPE_GROUP_SUM;
      case AVG -> Aggregation.TYPE_GROUP_AVERAGE;
      case MIN -> Aggregation.TYPE_GROUP_MIN;
      case MAX -> Aggregation.TYPE_GROUP_MAX;
      default ->
          throw new SourceModelSqlException(
              "Unsupported aggregate " + kind + ". " + SupportedSqlFeatures.SUMMARY);
    };
  }

  private static TransformMeta convertProject(
      Project project, TransformMeta input, GenerationContext ctx, Point location)
      throws SourceModelSqlException {
    List<RexNode> projects = project.getProjects();
    List<String> fieldNames = project.getRowType().getFieldNames();
    List<String> inputNames = project.getInput().getRowType().getFieldNames();

    boolean allSimpleRefs = projects.stream().allMatch(e -> e instanceof RexInputRef);
    if (allSimpleRefs) {
      return convertSimpleProject(project, input, ctx, location, projects, fieldNames, inputNames);
    }
    return convertExpressionProject(
        project, input, ctx, location, projects, fieldNames, inputNames);
  }

  private static TransformMeta convertSimpleProject(
      Project project,
      TransformMeta input,
      GenerationContext ctx,
      Point location,
      List<RexNode> projects,
      List<String> fieldNames,
      List<String> inputNames) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = new ArrayList<>();
    for (int i = 0; i < projects.size(); i++) {
      RexInputRef ref = (RexInputRef) projects.get(i);
      String inputName = inputNames.get(ref.getIndex());
      String outputName = fieldNames.get(i);
      SelectField field = new SelectField();
      field.setName(inputName);
      if (!inputName.equals(outputName)) {
        field.setRename(outputName);
      }
      selectFields.add(field);
    }
    boolean identity =
        selectFields.size() == inputNames.size()
            && selectFields.stream()
                .allMatch(f -> Utils.isEmpty(f.getRename()) || f.getRename().equals(f.getName()));
    if (identity) {
      boolean orderMatch = true;
      for (int i = 0; i < selectFields.size(); i++) {
        if (!selectFields.get(i).getName().equals(inputNames.get(i))) {
          orderMatch = false;
          break;
        }
      }
      if (orderMatch) {
        return input;
      }
    }

    selectMeta.getSelectOption().setSelectFields(selectFields);
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    String name = uniqueName(ctx, "Project");
    TransformMeta select = new TransformMeta("SelectValues", name, selectMeta);
    select.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(select);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(input, select));
    ctx.residualOps.add("Project → Select Values");
    return select;
  }

  private static TransformMeta convertExpressionProject(
      Project project,
      TransformMeta input,
      GenerationContext ctx,
      Point location,
      List<RexNode> projects,
      List<String> fieldNames,
      List<String> inputNames)
      throws SourceModelSqlException {
    CalculatorMeta calculatorMeta = new CalculatorMeta();
    List<CalculatorMetaFunction> functions = new ArrayList<>();
    List<SelectField> finalSelect = new ArrayList<>();
    int tempSeq = 0;

    for (int i = 0; i < projects.size(); i++) {
      RexNode expr = projects.get(i);
      String outputName = fieldNames.get(i);
      if (expr instanceof RexInputRef ref) {
        SelectField field = new SelectField();
        field.setName(inputNames.get(ref.getIndex()));
        if (!inputNames.get(ref.getIndex()).equals(outputName)) {
          field.setRename(outputName);
        }
        finalSelect.add(field);
        continue;
      }
      String produced =
          emitExpression(expr, inputNames, functions, "expr_" + (tempSeq++), outputName);
      SelectField field = new SelectField();
      field.setName(produced);
      if (!produced.equals(outputName)) {
        field.setRename(outputName);
      }
      finalSelect.add(field);
    }

    TransformMeta stream = input;
    if (!functions.isEmpty()) {
      calculatorMeta.setFunctions(functions);
      String calcName = uniqueName(ctx, "Calculate");
      TransformMeta calc = new TransformMeta("Calculator", calcName, calculatorMeta);
      calc.setLocation(location.x, location.y);
      ctx.pipelineMeta.addTransform(calc);
      ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(input, calc));
      ctx.residualOps.add("Project expressions → Calculator (" + functions.size() + ")");
      stream = calc;
      location = new Point(location.x + 160, location.y);
    }

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectFields(finalSelect);
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    String name = uniqueName(ctx, "Project");
    TransformMeta select = new TransformMeta("SelectValues", name, selectMeta);
    select.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(select);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(stream, select));
    ctx.residualOps.add("Project → Select Values");
    return select;
  }

  /**
   * Emits Calculator steps for a residual expression. Returns the output field name produced.
   * Supports column refs, literals, + - * /, and COALESCE/NVL of two args.
   */
  private static String emitExpression(
      RexNode expr,
      List<String> inputNames,
      List<CalculatorMetaFunction> functions,
      String tempBase,
      String preferredName)
      throws SourceModelSqlException {
    if (expr instanceof RexInputRef ref) {
      return inputNames.get(ref.getIndex());
    }
    if (expr instanceof RexLiteral lit) {
      String out = preferredName != null ? preferredName : tempBase;
      String value = literalAsString(lit);
      functions.add(
          new CalculatorMetaFunction(
              out,
              CalculationType.CONSTANT,
              value,
              null,
              null,
              ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING),
              -1,
              -1,
              null,
              null,
              null,
              null,
              false));
      return out;
    }
    if (expr instanceof RexCall call) {
      SqlKind kind = call.getKind();
      if (kind == SqlKind.COALESCE || kind == SqlKind.NVL) {
        if (call.getOperands().size() < 2) {
          throw new SourceModelSqlException("COALESCE/NVL needs two arguments");
        }
        String a =
            emitExpression(call.getOperands().get(0), inputNames, functions, tempBase + "_a", null);
        String b =
            emitExpression(call.getOperands().get(1), inputNames, functions, tempBase + "_b", null);
        String out = preferredName != null ? preferredName : tempBase;
        functions.add(
            new CalculatorMetaFunction(
                out,
                CalculationType.NVL,
                a,
                b,
                null,
                ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING),
                -1,
                -1,
                null,
                null,
                null,
                null,
                false));
        return out;
      }
      CalculationType calcType =
          switch (kind) {
            case PLUS -> CalculationType.ADD;
            case MINUS -> CalculationType.SUBTRACT;
            case TIMES -> CalculationType.MULTIPLY;
            case DIVIDE -> CalculationType.DIVIDE;
            default -> null;
          };
      if (calcType != null && call.getOperands().size() == 2) {
        String a =
            emitExpression(call.getOperands().get(0), inputNames, functions, tempBase + "_a", null);
        String b =
            emitExpression(call.getOperands().get(1), inputNames, functions, tempBase + "_b", null);
        String out = preferredName != null ? preferredName : tempBase;
        functions.add(
            new CalculatorMetaFunction(
                out,
                calcType,
                a,
                b,
                null,
                ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_NUMBER),
                -1,
                -1,
                null,
                null,
                null,
                null,
                false));
        return out;
      }
      if (kind == SqlKind.CAST && call.getOperands().size() == 1) {
        // Treat CAST as pass-through of the inner expression for residual mode.
        return emitExpression(
            call.getOperands().get(0), inputNames, functions, tempBase, preferredName);
      }
    }
    throw new SourceModelSqlException(
        "Unsupported residual expression: " + expr + ". " + SupportedSqlFeatures.SUMMARY);
  }

  private static String literalAsString(RexLiteral lit) {
    Object value = lit.getValue2();
    if (value instanceof org.apache.calcite.util.NlsString nls) {
      return nls.getValue();
    }
    return value != null ? String.valueOf(value) : "";
  }

  private static TransformMeta convertFilter(
      Filter filter, TransformMeta input, GenerationContext ctx, Point location)
      throws SourceModelSqlException {
    Condition condition =
        rexToCondition(filter.getCondition(), filter.getInput().getRowType().getFieldNames());
    FilterRowsMeta filterMeta = new FilterRowsMeta();
    filterMeta.setCondition(condition);
    String name = uniqueName(ctx, "Filter");
    TransformMeta transform = new TransformMeta("FilterRows", name, filterMeta);
    transform.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(transform);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(input, transform));
    ctx.residualOps.add("Filter → Filter Rows");
    return transform;
  }

  private static TransformMeta convertJoin(
      Join join,
      TransformMeta leftInput,
      TransformMeta rightInput,
      GenerationContext ctx,
      Point location)
      throws SourceModelSqlException {
    JoinKeys keys = extractEquiJoinKeys(join);
    List<String> leftFieldNames = join.getLeft().getRowType().getFieldNames();
    List<String> rightFieldNames = join.getRight().getRowType().getFieldNames();

    List<String> leftKeys = new ArrayList<>();
    List<String> rightKeys = new ArrayList<>();
    for (int i = 0; i < keys.leftIndexes.size(); i++) {
      leftKeys.add(leftFieldNames.get(keys.leftIndexes.get(i)));
      rightKeys.add(rightFieldNames.get(keys.rightIndexes.get(i)));
    }

    TransformMeta leftSorted =
        addSort(ctx, leftInput, leftKeys, new Point(location.x - 100, location.y - 50));
    TransformMeta rightSorted =
        addSort(ctx, rightInput, rightKeys, new Point(location.x - 100, location.y + 50));

    MergeJoinMeta mergeMeta = new MergeJoinMeta();
    mergeMeta.setJoinType(toMergeJoinType(join.getJoinType()));
    mergeMeta.setKeyFields1(leftKeys);
    mergeMeta.setKeyFields2(rightKeys);
    mergeMeta.setLeftTransformName(leftSorted.getName());
    mergeMeta.setRightTransformName(rightSorted.getName());
    String name = uniqueName(ctx, "Join");
    TransformMeta merge = new TransformMeta("MergeJoin", name, mergeMeta);
    merge.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(merge);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(leftSorted, merge));
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(rightSorted, merge));
    // Resolve INFO streams immediately so partial inspect/explain is consistent; finalize also
    // runs.
    mergeMeta.setParentTransformMeta(merge);
    mergeMeta.searchInfoAndTargetTransforms(ctx.pipelineMeta.getTransforms());
    ctx.residualOps.add(
        "Join (" + join.getJoinType() + ") → Sort + Merge Join on " + leftKeys + " = " + rightKeys);
    return merge;
  }

  private static TransformMeta convertSort(
      Sort sort, TransformMeta input, GenerationContext ctx, Point location)
      throws SourceModelSqlException {
    if (sort.getCollation().getFieldCollations().isEmpty()
        && (sort.fetch != null || sort.offset != null)) {
      // LIMIT-only: apply row limit on a dummy identity path — warn and pass through.
      if (sort.fetch != null) {
        ctx.warnings.add(
            "LIMIT/FETCH on residual path is not enforced by a dedicated transform in phase A; "
                + "use preview row limit or full pushdown.");
      }
      if (sort.offset != null) {
        ctx.warnings.add("OFFSET is not supported on residual path in phase A.");
      }
      return input;
    }

    List<String> fieldNames = sort.getInput().getRowType().getFieldNames();
    List<SortRowsField> fields = new ArrayList<>();
    for (RelFieldCollation collation : sort.getCollation().getFieldCollations()) {
      SortRowsField field = new SortRowsField();
      field.setFieldName(fieldNames.get(collation.getFieldIndex()));
      field.setAscending(collation.getDirection() != RelFieldCollation.Direction.DESCENDING);
      field.setCaseSensitive(true);
      fields.add(field);
    }
    SortRowsMeta sortMeta = new SortRowsMeta();
    sortMeta.setSortFields(fields);
    String name = uniqueName(ctx, "Sort");
    TransformMeta transform = new TransformMeta("SortRows", name, sortMeta);
    transform.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(transform);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(input, transform));
    ctx.residualOps.add("Sort → Sort Rows");
    if (sort.fetch != null || sort.offset != null) {
      ctx.warnings.add(
          "LIMIT/OFFSET with ORDER BY on residual path: sort applied; limit not fully enforced.");
    }
    return transform;
  }

  private static TransformMeta addSort(
      GenerationContext ctx, TransformMeta from, List<String> keys, Point location) {
    SortRowsMeta sortMeta = new SortRowsMeta();
    List<SortRowsField> fields = new ArrayList<>();
    for (String key : keys) {
      SortRowsField field = new SortRowsField();
      field.setFieldName(key);
      field.setAscending(true);
      field.setCaseSensitive(true);
      fields.add(field);
    }
    sortMeta.setSortFields(fields);
    String name = uniqueName(ctx, "Sort " + from.getName());
    TransformMeta sort = new TransformMeta("SortRows", name, sortMeta);
    sort.setLocation(location.x, location.y);
    ctx.pipelineMeta.addTransform(sort);
    ctx.pipelineMeta.addPipelineHop(new PipelineHopMeta(from, sort));
    return sort;
  }

  private static String toMergeJoinType(JoinRelType type) {
    if (type == null) {
      return "INNER";
    }
    return switch (type) {
      case INNER -> "INNER";
      case LEFT -> "LEFT OUTER";
      case RIGHT -> "RIGHT OUTER";
      case FULL -> "FULL OUTER";
      default -> "INNER";
    };
  }

  private static JoinKeys extractEquiJoinKeys(Join join) throws SourceModelSqlException {
    RexNode condition = join.getCondition();
    JoinKeys keys = new JoinKeys();
    collectEquiKeys(condition, join.getLeft().getRowType().getFieldCount(), keys);
    if (keys.leftIndexes.isEmpty()) {
      throw new SourceModelSqlException(
          "Only equi-joins (AND of = comparisons) are supported in free SQL residual path. "
              + SupportedSqlFeatures.SUMMARY);
    }
    return keys;
  }

  private static void collectEquiKeys(RexNode node, int leftFieldCount, JoinKeys keys)
      throws SourceModelSqlException {
    if (node == null) {
      return;
    }
    if (node.isA(SqlKind.AND) && node instanceof RexCall call) {
      for (RexNode operand : call.getOperands()) {
        collectEquiKeys(operand, leftFieldCount, keys);
      }
      return;
    }
    if (node.isA(SqlKind.EQUALS)
        && node instanceof RexCall call
        && call.getOperands().size() == 2) {
      RexNode a = call.getOperands().get(0);
      RexNode b = call.getOperands().get(1);
      if (a instanceof RexInputRef left && b instanceof RexInputRef right) {
        int li = left.getIndex();
        int ri = right.getIndex();
        if (li < leftFieldCount && ri >= leftFieldCount) {
          keys.leftIndexes.add(li);
          keys.rightIndexes.add(ri - leftFieldCount);
          return;
        }
        if (ri < leftFieldCount && li >= leftFieldCount) {
          keys.leftIndexes.add(ri);
          keys.rightIndexes.add(li - leftFieldCount);
          return;
        }
      }
    }
    throw new SourceModelSqlException(
        "Unsupported join condition (need equi-join only): "
            + node
            + ". "
            + SupportedSqlFeatures.SUMMARY);
  }

  private static Condition rexToCondition(RexNode node, List<String> fieldNames)
      throws SourceModelSqlException {
    try {
      return rexToConditionInternal(node, fieldNames);
    } catch (SourceModelSqlException e) {
      throw e;
    } catch (Exception e) {
      throw new SourceModelSqlException(
          "Failed to convert filter to Hop condition: " + e.getMessage(), e);
    }
  }

  private static Condition rexToConditionInternal(RexNode node, List<String> fieldNames)
      throws Exception {
    if (node == null) {
      return new Condition();
    }
    if (node.isA(SqlKind.AND) && node instanceof RexCall call) {
      Condition root = null;
      for (RexNode operand : call.getOperands()) {
        Condition child = rexToConditionInternal(operand, fieldNames);
        if (root == null) {
          root = child;
        } else {
          child.setOperator(Condition.Operator.AND);
          root.addCondition(child);
        }
      }
      return root != null ? root : new Condition();
    }
    if (node.isA(SqlKind.OR)) {
      throw new SourceModelSqlException(
          "OR conditions in residual Filter Rows are not supported in phase A; "
              + "prefer full pushdown (single database) for complex WHERE. "
              + SupportedSqlFeatures.SUMMARY);
    }
    if (node instanceof RexCall call) {
      return comparisonToCondition(call, fieldNames);
    }
    throw new SourceModelSqlException(
        "Unsupported filter expression: " + node + ". " + SupportedSqlFeatures.SUMMARY);
  }

  private static Condition comparisonToCondition(RexCall call, List<String> fieldNames)
      throws Exception {
    SqlKind kind = call.getKind();
    if (kind == SqlKind.IS_NULL || kind == SqlKind.IS_NOT_NULL) {
      RexNode op = call.getOperands().get(0);
      if (!(op instanceof RexInputRef ref)) {
        throw new SourceModelSqlException("IS NULL requires a column reference");
      }
      String field = fieldNames.get(ref.getIndex());
      return new Condition(
          field,
          kind == SqlKind.IS_NULL ? Condition.Function.NULL : Condition.Function.NOT_NULL,
          null,
          null);
    }

    if (call.getOperands().size() != 2) {
      throw new SourceModelSqlException("Unsupported comparison: " + call);
    }
    RexNode left = call.getOperands().get(0);
    RexNode right = call.getOperands().get(1);

    // Prefer column on the left.
    if (!(left instanceof RexInputRef)
        && right instanceof RexInputRef
        && left instanceof RexLiteral) {
      RexNode tmp = left;
      left = right;
      right = tmp;
      kind = flipComparison(kind);
    }
    if (!(left instanceof RexInputRef ref)) {
      throw new SourceModelSqlException(
          "Filter left side must be a column in residual mode: " + call);
    }
    String field = fieldNames.get(ref.getIndex());
    Condition.Function function =
        switch (kind) {
          case EQUALS -> Condition.Function.EQUAL;
          case NOT_EQUALS -> Condition.Function.NOT_EQUAL;
          case LESS_THAN -> Condition.Function.SMALLER;
          case LESS_THAN_OR_EQUAL -> Condition.Function.SMALLER_EQUAL;
          case GREATER_THAN -> Condition.Function.LARGER;
          case GREATER_THAN_OR_EQUAL -> Condition.Function.LARGER_EQUAL;
          case LIKE -> Condition.Function.LIKE;
          default ->
              throw new SourceModelSqlException(
                  "Unsupported comparison operator " + kind + ". " + SupportedSqlFeatures.SUMMARY);
        };

    if (right instanceof RexInputRef rightRef) {
      return new Condition(field, function, fieldNames.get(rightRef.getIndex()), null);
    }
    if (right instanceof RexLiteral lit) {
      Object value = lit.getValue2();
      String stringValue = value != null ? String.valueOf(value) : null;
      if (value instanceof org.apache.calcite.util.NlsString nls) {
        stringValue = nls.getValue();
      }
      ValueMetaAndData vmad = new ValueMetaAndData("constant", stringValue);
      return new Condition(field, function, null, vmad);
    }
    throw new SourceModelSqlException(
        "Filter right side must be a column or literal in residual mode: " + call);
  }

  private static SqlKind flipComparison(SqlKind kind) {
    return switch (kind) {
      case LESS_THAN -> SqlKind.GREATER_THAN;
      case LESS_THAN_OR_EQUAL -> SqlKind.GREATER_THAN_OR_EQUAL;
      case GREATER_THAN -> SqlKind.LESS_THAN;
      case GREATER_THAN_OR_EQUAL -> SqlKind.LESS_THAN_OR_EQUAL;
      default -> kind;
    };
  }

  private static String uniqueName(GenerationContext ctx, String base) {
    String name = base;
    int i = 2;
    while (ctx.usedNames.containsKey(name.toLowerCase(Locale.ROOT))) {
      name = base + " " + i++;
    }
    ctx.usedNames.put(name.toLowerCase(Locale.ROOT), Boolean.TRUE);
    return name;
  }

  private static final class JoinKeys {
    final List<Integer> leftIndexes = new ArrayList<>();
    final List<Integer> rightIndexes = new ArrayList<>();
  }

  private static final class GenerationContext {
    final PipelineMeta pipelineMeta;
    final SourceModel model;
    final IVariables variables;
    final IHopMetadataProvider metadataProvider;
    final SourceModelSqlOptions options;
    final List<String> pushdownSql;
    final List<String> residualOps;
    final List<String> warnings;
    final Map<String, Boolean> usedNames = new LinkedHashMap<>();

    GenerationContext(
        PipelineMeta pipelineMeta,
        SourceModel model,
        IVariables variables,
        IHopMetadataProvider metadataProvider,
        SourceModelSqlOptions options,
        List<String> pushdownSql,
        List<String> residualOps,
        List<String> warnings) {
      this.pipelineMeta = pipelineMeta;
      this.model = model;
      this.variables = variables;
      this.metadataProvider = metadataProvider;
      this.options = options;
      this.pushdownSql = pushdownSql;
      this.residualOps = residualOps;
      this.warnings = warnings;
    }
  }
}
