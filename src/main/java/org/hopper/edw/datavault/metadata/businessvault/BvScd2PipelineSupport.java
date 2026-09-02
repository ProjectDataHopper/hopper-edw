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
package org.hopper.edw.datavault.metadata.businessvault;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.analyticquery.AnalyticQueryMeta;
import org.apache.hop.pipeline.transforms.analyticquery.GroupField;
import org.apache.hop.pipeline.transforms.analyticquery.QueryField;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.groupby.Aggregation;
import org.apache.hop.pipeline.transforms.groupby.GroupByMeta;
import org.apache.hop.pipeline.transforms.groupby.GroupingField;
import org.apache.hop.pipeline.transforms.ifnull.Field;
import org.apache.hop.pipeline.transforms.ifnull.IfNullMeta;
import org.apache.hop.pipeline.transforms.mergejoin.MergeJoinMeta;
import org.apache.hop.pipeline.transforms.repeatfields.Repeat;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta;
import org.apache.hop.pipeline.transforms.repeatfields.RepeatFieldsMeta.RepeatType;
import org.apache.hop.pipeline.transforms.rowgenerator.GeneratorField;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.update.UpdateField;
import org.apache.hop.pipeline.transforms.update.UpdateKeyField;
import org.apache.hop.pipeline.transforms.update.UpdateLookupField;
import org.apache.hop.pipeline.transforms.update.UpdateMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.hopper.edw.datavault.expression.SqlExpressionException;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvDataTypeSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSourceFieldMappingSupport;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.DvSqlOrderBySupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.DvTargetLoadSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.HashAlgorithm;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeMeta;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeMetaFactory;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeSortKey;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMeta;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMetaFactory;

/**
 * Generates SCD2 build pipelines from DV satellite history using Analytic Query (LAG/LEAD validity
 * bounds), If Null sentinels, and Group By collapse for duplicate timestamps.
 */
public final class BvScd2PipelineSupport {

  private static final Class<?> PKG = BvScd2PipelineSupport.class;

  private static final Point LOCATION_START = new Point(160, 160);
  private static final int SPACING_WIDTH = 160;
  private static final int LEG_SPACING_HEIGHT = 96;
  static final String SOURCE_INDICATOR_FIELD = "_bv_source";
  static final String JOIN_HUB_BK_TRANSFORM = "join_hub_bk";
  public static final String BASELINE_SOURCE_INDICATOR = "BASELINE";
  static final String RECORD_SOURCE_CONCAT_SEPARATOR = ", ";
  public static final String DEFAULT_INCREMENTAL_SENTINEL = "1900-01-01 00:00:00";
  static final String INCREMENTAL_WATERMARK_FIELD = "_incremental_watermark";

  /** Table Input JDBC parameter field for the open-end sentinel (positional {@code ?}). */
  static final String OPEN_END_PARAM_FIELD = "_param_open_end";

  /** Standalone Constant that feeds satellite Table Input {@code ?} watermarks. */
  public static final String PARAM_WATERMARK_TRANSFORM = "param_incremental_watermark";

  /** Standalone Constant that feeds open-target / close-lookup Table Input {@code ?} params. */
  public static final String PARAM_OPEN_ROW_FILTER_TRANSFORM = "param_open_row_filter";

  static final String CLOSE_LOOKUP_VALID_FROM_FIELD = "_close_lookup_valid_from";
  static final String CLOSE_LOOKUP_READ_PREFIX = "read_open_close_lookup_";
  static final String JOIN_CLOSE_LOOKUP_VALID_FROM = "join_close_lookup_valid_from";
  private static final String REPEAT_FIELD_PREFIX = "_r_";

  private BvScd2PipelineSupport() {}

  /** Validates that DV and BV target database connections are configured and resolvable. */
  public static void validateTargetDatabases(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table) {
    if (remarks == null || scd2Table == null) {
      return;
    }
    BusinessVaultConfiguration bvConfig =
        bvModel != null ? bvModel.getConfigurationOrDefault() : new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig =
        dvModel != null ? dvModel.getConfigurationOrDefault() : new DataVaultConfiguration();

    if (Utils.isEmpty(dvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2PipelineSupport.CheckResult.MissingDvTargetDatabase",
                  scd2Table.getName()),
              scd2Table));
    } else if (metadataProvider != null) {
      try {
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), scd2Table));
      }
    }

    if (Utils.isEmpty(bvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvScd2PipelineSupport.CheckResult.MissingBvTargetDatabase",
                  scd2Table.getName()),
              scd2Table));
    } else if (metadataProvider != null) {
      try {
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), scd2Table));
      }
    }
  }

  public static PipelineMeta generatePipeline(Scd2BuildContext ctx) throws HopException {
    PipelineMeta pipelineMeta =
        ctx.isMultiSatellite()
            ? generateMultiSatellitePipeline(ctx)
            : generateSingleSatellitePipeline(ctx);
    applyPartitionParameters(pipelineMeta, ctx);
    return pipelineMeta;
  }

  private static PipelineMeta generateSingleSatellitePipeline(Scd2BuildContext ctx)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta, ctx.bvModel, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName);

    TransformMeta watermarkParam = null;
    TransformMeta openRowFilterParam = null;
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      watermarkParam =
          addIncrementalWatermarkParamConstant(
              ctx, pipelineMeta, new Point(LOCATION_START.x - SPACING_WIDTH, LOCATION_START.y));
      openRowFilterParam =
          addOpenRowFilterParamConstant(
              ctx,
              pipelineMeta,
              new Point(LOCATION_START.x - SPACING_WIDTH, LOCATION_START.y + LEG_SPACING_HEIGHT));
    }

    TransformMeta tableInput = addSatelliteTableInput(ctx, pipelineMeta, watermarkParam);
    if (tableInput != null) {
      GeneratedPipelineMetadataSupport.stampSourceRead(
          tableInput, ctx.legs.get(0).connectionName(ctx));
    }
    TransformMeta renamed =
        addSourceQueryHashKeyRename(ctx, ctx.legs.get(0), pipelineMeta, tableInput, LOCATION_START);
    TransformMeta legStream =
        injectRecordSourceConstantIfNeeded(
            ctx, ctx.legs.get(0), pipelineMeta, renamed, LOCATION_START);
    TransformMeta mergeInput = legStream;
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      TransformMeta baselineOutput =
          addIncrementalBaselineLeg(
              ctx,
              pipelineMeta,
              new Point(LOCATION_START.x, LOCATION_START.y + LEG_SPACING_HEIGHT),
              false,
              openRowFilterParam);
      mergeInput = addSortedSchemaMerge(ctx, pipelineMeta, List.of(legStream, baselineOutput));
    }
    TransformMeta analyticQuery = addAnalyticQuery(ctx, pipelineMeta, mergeInput);
    TransformMeta ifNull = addIfNull(ctx, pipelineMeta, analyticQuery);
    TransformMeta groupBy = addGroupBy(ctx, pipelineMeta, ifNull);
    TransformMeta calcInput = addHubBusinessKeyJoin(ctx, pipelineMeta, groupBy);
    TransformMeta calculated = addCalculations(ctx, pipelineMeta, calcInput);
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, calculated);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    }

    BvGeneratedPipelineSupport.applyScd2Layout(pipelineMeta);
    return pipelineMeta;
  }

  private static PipelineMeta generateMultiSatellitePipeline(Scd2BuildContext ctx)
      throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta, ctx.bvModel, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName);

    TransformMeta watermarkParam = null;
    TransformMeta openRowFilterParam = null;
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      watermarkParam =
          addIncrementalWatermarkParamConstant(
              ctx, pipelineMeta, new Point(LOCATION_START.x - SPACING_WIDTH, LOCATION_START.y));
      openRowFilterParam =
          addOpenRowFilterParamConstant(
              ctx,
              pipelineMeta,
              new Point(
                  LOCATION_START.x - SPACING_WIDTH,
                  LOCATION_START.y + ctx.legs.size() * LEG_SPACING_HEIGHT));
    }

    List<TransformMeta> legOutputs = new ArrayList<>();
    for (int legIndex = 0; legIndex < ctx.legs.size(); legIndex++) {
      SatelliteLeg leg = ctx.legs.get(legIndex);
      Point legLocation =
          new Point(LOCATION_START.x, LOCATION_START.y + legIndex * LEG_SPACING_HEIGHT);
      TransformMeta tableInput =
          addLegTableInput(ctx, leg, pipelineMeta, legLocation, watermarkParam);
      if (tableInput != null) {
        GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, leg.connectionName(ctx));
      }
      TransformMeta sourceConstant =
          addLegSourceIndicatorConstant(ctx, leg, pipelineMeta, tableInput, legLocation);
      legOutputs.add(addLegSelectValues(ctx, leg, pipelineMeta, sourceConstant, legLocation));
    }
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      Point baselineLocation =
          new Point(LOCATION_START.x, LOCATION_START.y + ctx.legs.size() * LEG_SPACING_HEIGHT);
      legOutputs.add(
          addIncrementalBaselineLeg(ctx, pipelineMeta, baselineLocation, true, openRowFilterParam));
    }

    TransformMeta sortedMerge = addSortedSchemaMerge(ctx, pipelineMeta, legOutputs);
    TransformMeta repeatFields = addRepeatFields(ctx, pipelineMeta, sortedMerge);
    TransformMeta postRepeatSelect = addPostRepeatSelectValues(ctx, pipelineMeta, repeatFields);
    TransformMeta analyticQuery = addAnalyticQuery(ctx, pipelineMeta, postRepeatSelect);
    TransformMeta ifNull = addIfNull(ctx, pipelineMeta, analyticQuery);
    TransformMeta groupBy = addGroupBy(ctx, pipelineMeta, ifNull);
    TransformMeta calcInput = addHubBusinessKeyJoin(ctx, pipelineMeta, groupBy);
    TransformMeta calculated = addCalculations(ctx, pipelineMeta, calcInput);
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, calculated);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    }

    BvGeneratedPipelineSupport.applyScd2Layout(pipelineMeta);
    return pipelineMeta;
  }

  public static Scd2BuildContext createContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    if (metadataProvider == null || bvModel == null || dvModel == null || scd2Table == null) {
      return null;
    }

    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(scd2Table, dvModel);
    List<BvSourceQuery> sourceQueries =
        BusinessVaultSourceQuerySupport.resolveSourceQueries(scd2Table, bvModel);
    if (satellites.isEmpty() && sourceQueries.isEmpty()) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " must hop to a Data Vault satellite or source query");
    }
    int inputCount = satellites.size() + sourceQueries.size();
    if (inputCount >= 2) {
      return createMultiSatelliteContext(
          metadataProvider, variables, bvModel, dvModel, scd2Table, satellites, sourceQueries);
    }
    if (!sourceQueries.isEmpty()) {
      return createSingleSourceQueryContext(
          metadataProvider, variables, bvModel, dvModel, scd2Table, sourceQueries.get(0));
    }
    return createSingleSatelliteContext(
        metadataProvider, variables, bvModel, dvModel, scd2Table, satellites.get(0));
  }

  private static Scd2BuildContext createSingleSatelliteContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      DvSatellite satellite)
      throws HopException {
    SharedScd2Resources resources =
        resolveSharedResources(metadataProvider, bvModel, dvModel, scd2Table, variables);

    String satelliteTableName =
        !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
    String pipelineName =
        resources.bvConfig.buildScd2PipelineName(
            variables, resources.bvTargetTableName, satellite.getName());

    String hashKeyFieldName =
        resolveSharedHashKeyFieldName(scd2Table, List.of(satellite), List.of(), dvModel, variables);
    String drivingKeyFieldName =
        satellite.hasDrivingKey() ? variables.resolve(satellite.getDrivingKey()) : null;
    List<String> attributeFieldNames = resolveAttributeFieldNames(satellite);

    SatelliteLeg leg =
        new SatelliteLeg(
            satellite,
            satelliteTableName,
            resolveSourceIndicatorValue(scd2Table, satellite, null, variables),
            resources.functionalTimestampField,
            List.of());

    return new Scd2BuildContext(
        scd2Table,
        List.of(leg),
        false,
        List.of(),
        bvModel,
        dvModel,
        resources.bvConfig,
        resources.dvConfig,
        metadataProvider,
        variables,
        resources.sourceDatabaseMeta,
        resources.sourceDbName,
        resources.targetDatabaseMeta,
        resources.targetDbName,
        satelliteTableName,
        resources.bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        drivingKeyFieldName,
        attributeFieldNames,
        resources.functionalTimestampField,
        resources.validFromField,
        resources.validToField,
        resources.recordSourceField,
        resources.openStartSentinel,
        resources.openEndSentinel,
        scd2Table.isIncludeHashKey(),
        resolveHubBkAttachment(scd2Table, dvModel, List.of(satellite), variables));
  }

  private static Scd2BuildContext createMultiSatelliteContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites)
      throws HopException {
    return createMultiSatelliteContext(
        metadataProvider, variables, bvModel, dvModel, scd2Table, satellites, List.of());
  }

  private static Scd2BuildContext createMultiSatelliteContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries)
      throws HopException {
    if (!hasFieldMappings(scd2Table)) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " references multiple satellites and requires explicit field mappings");
    }

    SharedScd2Resources resources =
        resolveSharedResources(metadataProvider, bvModel, dvModel, scd2Table, variables);
    String pipelineName =
        resources.bvConfig.buildScd2PipelineName(
            variables, resources.bvTargetTableName, scd2Table.getName());
    String hashKeyFieldName =
        resolveSharedHashKeyFieldName(scd2Table, satellites, sourceQueries, dvModel, variables);
    String drivingKeyFieldName = resolveSharedDrivingKeyFieldName(satellites, variables);
    List<String> mappedAttributeFieldNames = resolveMappedTargetFieldNames(scd2Table, variables);

    List<SatelliteLeg> legs = new ArrayList<>();
    for (DvSatellite satellite : satellites) {
      BvScd2SatelliteConfig satelliteConfig =
          BvScd2FieldMappingValidationSupport.findSatelliteConfig(
              scd2Table, satellite.getName(), variables);
      String satelliteTableName =
          !Utils.isEmpty(satellite.getTableName()) ? satellite.getTableName() : satellite.getName();
      legs.add(
          new SatelliteLeg(
              satellite,
              satelliteTableName,
              resolveSourceIndicatorValue(scd2Table, satellite, satelliteConfig, variables),
              resolveFunctionalTimestampFieldForSatellite(
                  scd2Table, satelliteConfig, resources.bvConfig, resources.dvConfig, variables),
              resolveFieldMappingsForSatellite(scd2Table, satellite.getName(), variables)));
    }
    for (BvSourceQuery sourceQuery : sourceQueries) {
      legs.add(
          createSourceQueryLeg(
              metadataProvider, variables, bvModel, dvModel, scd2Table, sourceQuery, resources));
    }
    String anchorTableName =
        !satellites.isEmpty() ? satellites.get(0).getName() : sourceQueries.get(0).getName();

    return new Scd2BuildContext(
        scd2Table,
        legs,
        true,
        mappedAttributeFieldNames,
        bvModel,
        dvModel,
        resources.bvConfig,
        resources.dvConfig,
        metadataProvider,
        variables,
        resources.sourceDatabaseMeta,
        resources.sourceDbName,
        resources.targetDatabaseMeta,
        resources.targetDbName,
        anchorTableName,
        resources.bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        drivingKeyFieldName,
        mappedAttributeFieldNames,
        resources.functionalTimestampField,
        resources.validFromField,
        resources.validToField,
        resources.recordSourceField,
        resources.openStartSentinel,
        resources.openEndSentinel,
        scd2Table.isIncludeHashKey(),
        resolveHubBkAttachment(scd2Table, dvModel, satellites, sourceQueries, variables));
  }

  private static Scd2BuildContext createSingleSourceQueryContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      BvSourceQuery sourceQuery)
      throws HopException {
    SharedScd2Resources resources =
        resolveSharedResources(metadataProvider, bvModel, dvModel, scd2Table, variables);
    SatelliteLeg leg =
        createSourceQueryLeg(
            metadataProvider, variables, bvModel, dvModel, scd2Table, sourceQuery, resources);
    String pipelineName =
        resources.bvConfig.buildScd2PipelineName(
            variables, resources.bvTargetTableName, sourceQuery.getName());
    String hashKeyFieldName =
        resolveSharedHashKeyFieldName(
            scd2Table, List.of(), List.of(sourceQuery), dvModel, variables);
    List<String> attributeFieldNames =
        BvSourceQuerySqlSupport.attributeFieldNames(sourceQuery, variables);
    return new Scd2BuildContext(
        scd2Table,
        List.of(leg),
        false,
        List.of(),
        bvModel,
        dvModel,
        resources.bvConfig,
        resources.dvConfig,
        metadataProvider,
        variables,
        leg.databaseMeta != null ? leg.databaseMeta : resources.sourceDatabaseMeta,
        !Utils.isEmpty(leg.connectionName) ? leg.connectionName : resources.sourceDbName,
        resources.targetDatabaseMeta,
        resources.targetDbName,
        leg.satelliteTableName,
        resources.bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        null,
        attributeFieldNames,
        leg.sourceFunctionalTimestampField,
        resources.validFromField,
        resources.validToField,
        resources.recordSourceField,
        resources.openStartSentinel,
        resources.openEndSentinel,
        scd2Table.isIncludeHashKey(),
        resolveHubBkAttachment(scd2Table, dvModel, List.of(), List.of(sourceQuery), variables));
  }

  private static SatelliteLeg createSourceQueryLeg(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      BvSourceQuery sourceQuery,
      SharedScd2Resources resources)
      throws HopException {
    BvScd2SatelliteConfig config =
        BvScd2FieldMappingValidationSupport.findSatelliteConfig(
            scd2Table, sourceQuery.getName(), variables);
    String connectionName =
        BusinessVaultSourceQuerySupport.resolveConnectionName(sourceQuery, dvModel, variables);
    DatabaseMeta databaseMeta =
        BusinessVaultSourceQuerySupport.loadConnection(
            sourceQuery, dvModel, metadataProvider, variables);
    if (databaseMeta == null) {
      databaseMeta = resources.sourceDatabaseMeta;
    }
    if (Utils.isEmpty(connectionName)) {
      connectionName = resources.sourceDbName;
    }
    String timestamp =
        resolveFunctionalTimestampFieldForSourceQuery(
            scd2Table, sourceQuery, config, resources.bvConfig, resources.dvConfig, variables);
    String tableName =
        !Utils.isEmpty(sourceQuery.getTableName())
            ? sourceQuery.getTableName()
            : sourceQuery.getName();
    String fromClause = BvSourceQuerySqlSupport.fromClause(databaseMeta, variables, sourceQuery);
    String hashKey =
        variables != null
            ? variables.resolve(sourceQuery.getHashKeyField())
            : sourceQuery.getHashKeyField();
    String indicator = resolveSourceIndicatorValue(scd2Table, null, config, variables);
    if (Utils.isEmpty(indicator)) {
      indicator = sourceQuery.getName();
    }
    return new SatelliteLeg(
        null,
        sourceQuery,
        tableName,
        indicator,
        timestamp,
        resolveFieldMappingsForSatellite(scd2Table, sourceQuery.getName(), variables),
        databaseMeta,
        connectionName,
        hashKey,
        fromClause);
  }

  static String resolveFunctionalTimestampFieldForSourceQuery(
      BvScd2Table scd2Table,
      BvSourceQuery sourceQuery,
      BvScd2SatelliteConfig config,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (config != null && !Utils.isEmpty(config.getFunctionalTimestampField())) {
      return variables.resolve(config.getFunctionalTimestampField());
    }
    if (sourceQuery != null && !Utils.isEmpty(sourceQuery.getFunctionalTimestampField())) {
      return variables.resolve(sourceQuery.getFunctionalTimestampField());
    }
    return resolveFunctionalTimestampField(scd2Table, bvConfig, dvConfig, variables);
  }

  static String resolveSharedHashKeyFieldName(
      BvScd2Table scd2Table,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      DataVaultModel dvModel,
      IVariables variables) {
    DvHub hub =
        BvScd2FieldMappingValidationSupport.resolveSharedParentHub(
            scd2Table, satellites, dvModel, variables);
    if (hub != null) {
      String hashKey =
          variables != null
              ? variables.resolve(hub.getHashKeyFieldName())
              : hub.getHashKeyFieldName();
      if (!Utils.isEmpty(hashKey)) {
        return hashKey;
      }
    }
    if (satellites != null && !satellites.isEmpty()) {
      return resolveHashKeyFieldName(satellites.get(0), dvModel, variables);
    }
    if (sourceQueries != null && !sourceQueries.isEmpty()) {
      BvSourceQuery first = sourceQueries.get(0);
      if (first != null) {
        String mapped = first.resolvedHubHashKeyField(variables);
        if (!Utils.isEmpty(mapped)) {
          return mapped;
        }
      }
    }
    return null;
  }

  private static HubBkAttachment resolveHubBkAttachment(
      BvScd2Table scd2Table,
      DataVaultModel dvModel,
      List<DvSatellite> satellites,
      List<BvSourceQuery> sourceQueries,
      IVariables variables)
      throws HopException {
    return resolveHubBkAttachment(scd2Table, dvModel, satellites, variables);
  }

  private static HubBkAttachment resolveHubBkAttachment(
      BvScd2Table scd2Table,
      DataVaultModel dvModel,
      List<DvSatellite> satellites,
      IVariables variables)
      throws HopException {
    if (scd2Table == null || !scd2Table.isIncludeHubBusinessKeys()) {
      return HubBkAttachment.none();
    }
    DvHub hub =
        BvScd2FieldMappingValidationSupport.resolveSharedParentHub(
            scd2Table, satellites, dvModel, variables);
    if (hub == null) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " cannot include hub business keys without a hub (set Parent hub on the SCD2 table, or hop hub-parent satellites)");
    }
    String tableName = !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
    List<String> fieldNames = new ArrayList<>();
    for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      fieldNames.add(variables.resolve(businessKey.getName()));
    }
    if (fieldNames.isEmpty()) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " cannot include hub business keys because hub "
              + hub.getName()
              + " has none");
    }
    return new HubBkAttachment(true, hub, tableName, fieldNames);
  }

  private static SharedScd2Resources resolveSharedResources(
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      IVariables variables)
      throws HopException {
    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();

    String sourceDbName = dvConfig.getTargetDatabase();
    if (Utils.isEmpty(sourceDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2PipelineSupport.Error.MissingDvTargetDatabase", scd2Table.getName()));
    }
    if (metadataProvider == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2PipelineSupport.Error.MissingMetadataProvider", scd2Table.getName()));
    }
    DatabaseMeta sourceDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);

    String targetDbName = bvConfig.getTargetDatabase();
    if (Utils.isEmpty(targetDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2PipelineSupport.Error.MissingBvTargetDatabase", scd2Table.getName()));
    }
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);

    String bvTargetTableName =
        !Utils.isEmpty(scd2Table.getTableName()) ? scd2Table.getTableName() : scd2Table.getName();
    String functionalTimestampField =
        resolveFunctionalTimestampField(scd2Table, bvConfig, dvConfig, variables);
    String validFromField = resolveValidFromField(scd2Table, bvConfig, variables);
    String validToField = resolveValidToField(scd2Table, bvConfig, variables);
    String recordSourceField = resolveRecordSourceField(dvConfig, variables);

    String openStartSentinel = bvConfig.getOpenStartSentinel();
    if (Utils.isEmpty(openStartSentinel)) {
      openStartSentinel = BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL;
    }
    openStartSentinel = variables.resolve(openStartSentinel);

    String openEndSentinel = bvConfig.getOpenEndSentinel();
    if (Utils.isEmpty(openEndSentinel)) {
      openEndSentinel = BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL;
    }
    openEndSentinel = variables.resolve(openEndSentinel);

    return new SharedScd2Resources(
        bvConfig,
        dvConfig,
        sourceDatabaseMeta,
        sourceDbName,
        targetDatabaseMeta,
        targetDbName,
        bvTargetTableName,
        functionalTimestampField,
        validFromField,
        validToField,
        recordSourceField,
        openStartSentinel,
        openEndSentinel);
  }

  static List<String> resolveMappedTargetFieldNames(BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    Set<String> names = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (!Utils.isEmpty(targetFieldName)) {
        names.add(targetFieldName);
      }
    }
    return new ArrayList<>(names);
  }

  private static List<BvScd2FieldMapping> resolveFieldMappingsForSatellite(
      BvScd2Table scd2Table, String satelliteName, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    List<BvScd2FieldMapping> mappings = new ArrayList<>();
    String resolvedSatelliteName = variables.resolve(satelliteName);
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping != null
          && resolvedSatelliteName.equals(variables.resolve(mapping.getSatelliteName()))) {
        mappings.add(mapping);
      }
    }
    return mappings;
  }

  private static String resolveSharedDrivingKeyFieldName(
      List<DvSatellite> satellites, IVariables variables) {
    for (DvSatellite satellite : satellites) {
      if (satellite != null && satellite.hasDrivingKey()) {
        return variables.resolve(satellite.getDrivingKey());
      }
    }
    return null;
  }

  public static IRowMeta buildTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    if (hasFieldMappings(scd2Table)) {
      return buildMappedTargetTableLayout(scd2Table, bvConfig, dvModel, variables);
    }
    return buildLegacyTargetTableLayout(
        scd2Table, bvConfig, dvModel, resolveSourceSatellite(scd2Table, dvModel), variables);
  }

  public static IRowMeta buildTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    if (hasFieldMappings(scd2Table)) {
      return buildTargetTableLayout(scd2Table, bvConfig, dvModel, variables);
    }
    return buildLegacyTargetTableLayout(scd2Table, bvConfig, dvModel, satellite, variables);
  }

  private static IRowMeta buildLegacyTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      DvSatellite satellite,
      IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    appendGrainFields(rowMeta, scd2Table, dvModel, List.of(satellite), variables, true);

    for (String attrName : resolveAttributeFieldNames(satellite)) {
      if (satellite.hasDrivingKey()
          && attrName.equals(variables.resolve(satellite.getDrivingKey()))) {
        continue;
      }
      IValueMeta attrMeta = findAttributeValueMeta(satellite, attrName);
      if (attrMeta != null) {
        rowMeta.addValueMeta(attrMeta);
      }
    }

    appendSourceAndValidityFields(rowMeta, scd2Table, bvConfig, dvModel, variables);
    applyCalculations(rowMeta, scd2Table, variables);
    appendLoadCycleField(rowMeta, bvConfig, variables);
    return rowMeta;
  }

  private static IRowMeta buildMappedTargetTableLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(scd2Table, dvModel);
    if (satellites.isEmpty()) {
      throw new HopException(
          "SCD2 table "
              + scd2Table.getName()
              + " must reference a Data Vault satellite derivative");
    }

    RowMeta rowMeta = new RowMeta();
    appendGrainFields(rowMeta, scd2Table, dvModel, satellites, variables, true);

    Set<String> addedTargets = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String satelliteName = variables.resolve(mapping.getSatelliteName());
      String sourceFieldName = variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)
          || !mapping.isIncludeInTarget()
          || !addedTargets.add(targetFieldName)) {
        continue;
      }

      DvSatellite satellite = findSatelliteByName(satellites, satelliteName);
      if (satellite == null) {
        continue;
      }
      IValueMeta sourceMeta = findAttributeValueMeta(satellite, sourceFieldName);
      if (sourceMeta == null) {
        continue;
      }
      rowMeta.addValueMeta(cloneValueMetaWithName(sourceMeta, targetFieldName));
    }

    appendSourceAndValidityFields(rowMeta, scd2Table, bvConfig, dvModel, variables);
    applyCalculations(rowMeta, scd2Table, variables);
    appendLoadCycleField(rowMeta, bvConfig, variables);
    return rowMeta;
  }

  public static IRowMeta buildCollapseRowLayout(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    RowMeta collapse = new RowMeta();
    if (hasFieldMappings(scd2Table)) {
      appendGrainFields(
          collapse,
          scd2Table,
          dvModel,
          BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(scd2Table, dvModel),
          variables,
          false);
      appendMappedAttributes(collapse, scd2Table, dvModel, variables);
    } else {
      DvSatellite satellite = resolveSourceSatellite(scd2Table, dvModel);
      appendGrainFields(collapse, scd2Table, dvModel, List.of(satellite), variables, false);
      for (String attrName : resolveAttributeFieldNames(satellite)) {
        if (satellite.hasDrivingKey()
            && attrName.equals(variables.resolve(satellite.getDrivingKey()))) {
          continue;
        }
        IValueMeta attrMeta = findAttributeValueMeta(satellite, attrName);
        if (attrMeta != null) {
          collapse.addValueMeta(attrMeta);
        }
      }
    }
    appendSourceAndValidityFields(collapse, scd2Table, bvConfig, dvModel, variables);
    return collapse;
  }

  private static void appendMappedAttributes(
      RowMeta rowMeta, BvScd2Table scd2Table, DataVaultModel dvModel, IVariables variables)
      throws HopException {
    List<DvSatellite> satellites =
        BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(scd2Table, dvModel);
    Set<String> addedTargets = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String satelliteName = variables.resolve(mapping.getSatelliteName());
      String sourceFieldName = variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)
          || !addedTargets.add(targetFieldName)
          || rowMeta.indexOfValue(targetFieldName) >= 0) {
        continue;
      }
      DvSatellite satellite = findSatelliteByName(satellites, satelliteName);
      if (satellite == null) {
        continue;
      }
      IValueMeta sourceMeta = findAttributeValueMeta(satellite, sourceFieldName);
      if (sourceMeta == null) {
        continue;
      }
      rowMeta.addValueMeta(cloneValueMetaWithName(sourceMeta, targetFieldName));
    }
  }

  private static void applyCalculations(
      IRowMeta rowMeta, BvScd2Table scd2Table, IVariables variables) throws HopException {
    if (scd2Table == null || !scd2Table.hasCalculations()) {
      return;
    }
    try {
      SqlExpressionProgram program =
          SqlExpressionProgram.compile(
              BvScd2CalculationValidationSupport.toSpecs(scd2Table.getCalculations(), variables),
              rowMeta,
              variables);
      IRowMeta withCalcs = program.getOutputRowMeta();
      rowMeta.clear();
      for (int i = 0; i < withCalcs.size(); i++) {
        rowMeta.addValueMeta(withCalcs.getValueMeta(i).clone());
      }
    } catch (SqlExpressionException e) {
      throw new HopException(
          "Unable to apply SCD2 calculations on table "
              + scd2Table.getName()
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private static void appendGrainFields(
      RowMeta rowMeta,
      BvScd2Table scd2Table,
      DataVaultModel dvModel,
      List<DvSatellite> satellites,
      IVariables variables,
      boolean forTargetTable)
      throws HopException {
    List<DvSatellite> satList = satellites != null ? satellites : List.of();

    if (scd2Table.isIncludeHashKey()) {
      String hashKeyName =
          resolveSharedHashKeyFieldName(scd2Table, satList, List.of(), dvModel, variables);
      if (!Utils.isEmpty(hashKeyName)) {
        rowMeta.addValueMeta(resolveHashKeyValueMeta(hashKeyName, dvModel));
      }
    }

    if (!forTargetTable || scd2Table.isLoadHubBusinessKeys()) {
      appendHubBusinessKeyFields(rowMeta, scd2Table, dvModel, satList, variables);
    }

    for (DvSatellite satellite : satList) {
      if (!satellite.hasDrivingKey()) {
        continue;
      }
      String drivingKeyName = variables.resolve(satellite.getDrivingKey());
      if (Utils.isEmpty(drivingKeyName) || rowMeta.indexOfValue(drivingKeyName) >= 0) {
        continue;
      }
      IValueMeta drivingKeyMeta = findAttributeValueMeta(satellite, drivingKeyName);
      if (drivingKeyMeta != null) {
        rowMeta.addValueMeta(drivingKeyMeta);
      }
    }
  }

  private static void appendSourceAndValidityFields(
      RowMeta rowMeta,
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultModel dvModel,
      IVariables variables) {
    rowMeta.addValueMeta(
        buildRecordSourceValueMeta(dvModel.getConfigurationOrDefault(), variables));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(
            resolveFunctionalTimestampField(
                scd2Table, bvConfig, dvModel.getConfigurationOrDefault(), variables)));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidFromField(scd2Table, bvConfig, variables)));
    rowMeta.addValueMeta(
        new ValueMetaTimestamp(resolveValidToField(scd2Table, bvConfig, variables)));
  }

  private static void appendLoadCycleField(
      IRowMeta rowMeta, BusinessVaultConfiguration bvConfig, IVariables variables) {
    if (bvConfig != null) {
      DvLoadCycleSupport.appendToLayout(
          rowMeta, bvConfig.isStoreLoadCycleId(), bvConfig.getLoadCycleIdField(), variables);
    }
  }

  static boolean hasFieldMappings(BvScd2Table scd2Table) {
    return scd2Table != null
        && scd2Table.getFieldMappings() != null
        && !scd2Table.getFieldMappings().isEmpty();
  }

  static List<String> resolveLoadedTargetFieldNames(BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    Set<String> names = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null || !mapping.isIncludeInTarget()) {
        continue;
      }
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (!Utils.isEmpty(targetFieldName)) {
        names.add(targetFieldName);
      }
    }
    return new ArrayList<>(names);
  }

  static List<String> resolveCalculationOnlyTargetFieldNames(
      BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return List.of();
    }
    Set<String> names = new LinkedHashSet<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null || mapping.isIncludeInTarget()) {
        continue;
      }
      String targetFieldName = variables.resolve(mapping.getTargetFieldName());
      if (!Utils.isEmpty(targetFieldName)) {
        names.add(targetFieldName);
      }
    }
    return new ArrayList<>(names);
  }

  private static void addStreamOnlyExcludeFields(Set<String> excludeFields, Scd2BuildContext ctx) {
    if (excludeFields == null || ctx == null) {
      return;
    }
    excludeFields.addAll(resolveCalculationOnlyTargetFieldNames(ctx.scd2Table, ctx.variables));
    if (ctx.includeHubBusinessKeys()
        && ctx.scd2Table != null
        && !ctx.scd2Table.isLoadHubBusinessKeys()) {
      excludeFields.addAll(ctx.hubBusinessKeyFieldNames());
    }
  }

  private static void appendHubBusinessKeyFields(
      RowMeta rowMeta,
      BvScd2Table scd2Table,
      DataVaultModel dvModel,
      List<DvSatellite> satellites,
      IVariables variables)
      throws HopException {
    if (scd2Table == null || !scd2Table.isIncludeHubBusinessKeys() || dvModel == null) {
      return;
    }
    DvHub hub =
        BvScd2FieldMappingValidationSupport.resolveSharedParentHub(
            scd2Table, satellites, dvModel, variables);
    if (hub == null) {
      return;
    }
    for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      String fieldName = variables.resolve(businessKey.getName());
      if (Utils.isEmpty(fieldName) || rowMeta.indexOfValue(fieldName) >= 0) {
        continue;
      }
      int typeId = DvDataTypeSupport.resolveHopTypeId(businessKey.getDataType(), null);
      if (typeId <= 0) {
        typeId = IValueMeta.TYPE_STRING;
      }
      try {
        IValueMeta valueMeta = ValueMetaFactory.createValueMeta(fieldName, typeId);
        valueMeta.setLength(Const.toInt(variables.resolve(businessKey.getLength()), -1));
        valueMeta.setPrecision(Const.toInt(variables.resolve(businessKey.getPrecision()), -1));
        rowMeta.addValueMeta(valueMeta);
      } catch (org.apache.hop.core.exception.HopPluginException e) {
        throw new HopException("Error creating value meta for hub business key " + fieldName, e);
      }
    }
  }

  public static String resolveFunctionalTimestampFieldForSatellite(
      BvScd2Table scd2Table,
      BvScd2SatelliteConfig satelliteConfig,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (satelliteConfig != null && !Utils.isEmpty(satelliteConfig.getFunctionalTimestampField())) {
      return variables.resolve(satelliteConfig.getFunctionalTimestampField());
    }
    return resolveFunctionalTimestampField(scd2Table, bvConfig, dvConfig, variables);
  }

  public static String resolveSourceIndicatorValue(
      BvScd2Table scd2Table,
      DvSatellite satellite,
      BvScd2SatelliteConfig satelliteConfig,
      IVariables variables) {
    if (satelliteConfig != null && !Utils.isEmpty(satelliteConfig.getSourceIndicatorValue())) {
      return variables.resolve(satelliteConfig.getSourceIndicatorValue());
    }
    return satellite != null ? variables.resolve(satellite.getName()) : null;
  }

  public static List<DvSatellite> resolveSourceSatellites(BvScd2Table table, DataVaultModel dvModel)
      throws HopException {
    return BvScd2FieldMappingValidationSupport.resolveSatelliteDerivatives(table, dvModel);
  }

  /**
   * True when this SCD2 satellite leg should {@code SELECT} the physical vault record-source
   * column. Multi-satellite pipelines never do: BV RS is {@link #SOURCE_INDICATOR_FIELD} renamed
   * after Repeat. A missing satellite defaults to omit so JDBC cannot request a column the table
   * may not have.
   */
  static boolean shouldSelectPhysicalRecordSource(Scd2BuildContext ctx, SatelliteLeg leg) {
    return ctx != null && !ctx.isMultiSatellite() && storesPhysicalRecordSource(leg);
  }

  static boolean storesPhysicalRecordSource(SatelliteLeg leg) {
    return leg != null
        && leg.satellite != null
        && DvSourceFieldMappingSupport.shouldStoreRecordSource(leg.satellite);
  }

  public static String resolveRecordSourceField(
      DataVaultConfiguration dvConfig, IVariables variables) {
    String rsFieldName = "RECORD_SOURCE";
    if (dvConfig != null && !Utils.isEmpty(dvConfig.getRecordSourceField())) {
      rsFieldName = dvConfig.getRecordSourceField();
    }
    rsFieldName = variables.resolve(rsFieldName);
    if (Utils.isEmpty(rsFieldName)) {
      rsFieldName = "RECORD_SOURCE";
    }
    return rsFieldName;
  }

  private static IValueMeta buildRecordSourceValueMeta(
      DataVaultConfiguration dvConfig, IVariables variables) {
    String rsFieldName = resolveRecordSourceField(dvConfig, variables);
    String lengthString =
        (dvConfig != null && !Utils.isEmpty(dvConfig.getRecordSourceFieldLength()))
            ? dvConfig.getRecordSourceFieldLength()
            : "100";
    int rsLength = Const.toInt(variables.resolve(lengthString), 100);
    IValueMeta rsMeta = new ValueMetaString(rsFieldName);
    rsMeta.setLength(rsLength);
    return rsMeta;
  }

  public static String buildSatelliteTableInputSql(Scd2BuildContext ctx) {
    if (ctx.isMultiSatellite()) {
      throw new IllegalStateException("Use buildLegTableInputSql for multi-satellite contexts");
    }
    return buildLegTableInputSql(ctx, ctx.legs.get(0));
  }

  /**
   * True when DV source and BV target use the same Hop connection name. Cross-connection
   * incremental SQL must never embed the other connection's tables in a single statement.
   */
  static boolean usesSharedTargetConnection(Scd2BuildContext ctx) {
    return ctx != null
        && !Utils.isEmpty(ctx.sourceDbName)
        && ctx.sourceDbName.equals(ctx.targetDbName);
  }

  static boolean legSharesTargetConnection(Scd2BuildContext ctx, SatelliteLeg leg) {
    return ctx != null
        && leg != null
        && !Utils.isEmpty(ctx.targetDbName)
        && ctx.targetDbName.equals(leg.connectionName(ctx));
  }

  static boolean hasLegSharingTargetConnection(Scd2BuildContext ctx) {
    if (ctx == null || ctx.legs == null) {
      return false;
    }
    for (SatelliteLeg leg : ctx.legs) {
      if (legSharesTargetConnection(ctx, leg)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Distinct hash keys from DV satellites that have rows after the watermark parameter. Each UNION
   * branch uses its own positional {@code ?} for the watermark — JDBC does not reuse bind values
   * across placeholders. The open-row filter parameter row must supply one watermark field per
   * branch ({@link #countDeltaHashKeyWatermarkPlaceholders}).
   *
   * <p>Only safe to embed in BV-connected SQL when {@link #usesSharedTargetConnection}.
   */
  static String buildDeltaHashKeysSubquerySql(Scd2BuildContext ctx) {
    List<String> unionBranches = new ArrayList<>();
    for (SatelliteLeg leg : ctx.legs) {
      if (!legSharesTargetConnection(ctx, leg)) {
        continue;
      }
      DatabaseMeta databaseMeta = leg.databaseMeta(ctx);
      String hashKeyColumn = databaseMeta.quoteField(leg.hashKeyField(ctx));
      String fromClause =
          !Utils.isEmpty(leg.fromClause)
              ? leg.fromClause
              : databaseMeta.getQuotedSchemaTableCombination(
                  ctx.variables, null, leg.satelliteTableName);
      String sourceTimestampField =
          ctx.isMultiSatellite()
              ? leg.sourceFunctionalTimestampField
              : ctx.functionalTimestampField;
      if (leg.isSourceQuery() && !Utils.isEmpty(leg.sourceFunctionalTimestampField)) {
        sourceTimestampField = leg.sourceFunctionalTimestampField;
      }
      String sourceTimestampColumn = databaseMeta.quoteField(sourceTimestampField);
      String filter = buildIncrementalSatelliteFilterSql(sourceTimestampColumn);
      unionBranches.add("SELECT " + hashKeyColumn + " FROM " + fromClause + " WHERE " + filter);
    }
    if (unionBranches.isEmpty()) {
      String hashKeyColumn = ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName);
      return "SELECT "
          + hashKeyColumn
          + " FROM "
          + ctx.sourceDatabaseMeta.getQuotedSchemaTableCombination(
              ctx.variables, null, ctx.satelliteTableName)
          + " WHERE 1 = 0";
    }
    return String.join(" UNION ", unionBranches);
  }

  /**
   * Number of watermark {@code ?} placeholders in {@link #buildDeltaHashKeysSubquerySql} (one per
   * satellite leg). Used to size the open-row filter parameter row.
   */
  static int countDeltaHashKeyWatermarkPlaceholders(Scd2BuildContext ctx) {
    if (ctx == null || ctx.legs == null || ctx.legs.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (SatelliteLeg leg : ctx.legs) {
      if (legSharesTargetConnection(ctx, leg)) {
        count++;
      }
    }
    return count;
  }

  public static String buildOpenTargetTableInputSql(Scd2BuildContext ctx) {
    List<String> selectFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    for (String attr : ctx.persistedAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      selectFields.add(ctx.targetDatabaseMeta.quoteField(attr));
    }
    selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.recordSourceField));
    selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.functionalTimestampField));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.bvTargetTableName));
    sql.append(" WHERE ");
    sql.append(ctx.targetDatabaseMeta.quoteField(ctx.validToField));
    // Positional ? bound from param_open_row_filter (open-end sentinel).
    sql.append(" = ?");
    // Delta hash-key pushdown references DV satellites — only valid on a shared connection.
    // One ? per UNION branch for the watermark (parameter row repeats the value).
    if (ctx.includeHashKey && hasLegSharingTargetConnection(ctx)) {
      sql.append(" AND ");
      sql.append(ctx.targetDatabaseMeta.quoteField(ctx.hashKeyFieldName));
      sql.append(" IN (");
      sql.append(buildDeltaHashKeysSubquerySql(ctx));
      sql.append(")");
    }
    appendScd2OrderBy(sql, ctx, ctx.targetDatabaseMeta, true, ctx.functionalTimestampField, null);
    return sql.toString();
  }

  public static String buildOpenTargetCloseLookupSql(Scd2BuildContext ctx) {
    List<String> selectFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(ctx.targetDatabaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    selectFields.add(
        ctx.targetDatabaseMeta.quoteField(ctx.validFromField)
            + " AS "
            + ctx.targetDatabaseMeta.quoteField(CLOSE_LOOKUP_VALID_FROM_FIELD));

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.bvTargetTableName));
    sql.append(" WHERE ");
    sql.append(ctx.targetDatabaseMeta.quoteField(ctx.validToField));
    sql.append(" = ?");
    // Delta hash-key pushdown references DV satellites — only valid on a shared connection.
    if (ctx.includeHashKey && hasLegSharingTargetConnection(ctx)) {
      sql.append(" AND ");
      sql.append(ctx.targetDatabaseMeta.quoteField(ctx.hashKeyFieldName));
      sql.append(" IN (");
      sql.append(buildDeltaHashKeysSubquerySql(ctx));
      sql.append(")");
    }
    appendScd2OrderBy(sql, ctx, ctx.targetDatabaseMeta, false, null, null);
    return sql.toString();
  }

  /**
   * Incremental satellite filter using a positional JDBC parameter for the watermark. Bound at
   * runtime from a preceding Constant / Get Variables transform — dialect-neutral.
   */
  public static String buildIncrementalSatelliteFilterSql(String sourceTimestampColumnRef) {
    return sourceTimestampColumnRef + " > ?";
  }

  /**
   * BV-only query used at pipeline generation to resolve the incremental watermark value. Returns
   * {@code MAX(watermark)} only; null/empty results fall back to the default sentinel in Java so
   * the SQL stays free of dialect-specific timestamp literals.
   */
  public static String buildIncrementalWatermarkSql(Scd2BuildContext ctx) {
    String watermarkField =
        ctx.scd2Table.resolveIncrementalWatermarkField(ctx.bvConfig, ctx.dvConfig, ctx.variables);
    String quotedTable =
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.bvTargetTableName);
    String quotedWatermarkField = ctx.targetDatabaseMeta.quoteField(watermarkField);
    return "SELECT MAX("
        + quotedWatermarkField
        + ") AS "
        + INCREMENTAL_WATERMARK_FIELD
        + " FROM "
        + quotedTable;
  }

  static String resolveIncrementalWatermarkValue(Scd2BuildContext ctx) {
    if (ctx == null || ctx.scd2Table == null || !ctx.scd2Table.isIncrementalBuild()) {
      return DEFAULT_INCREMENTAL_SENTINEL;
    }
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return DEFAULT_INCREMENTAL_SENTINEL;
    }

    String sql = buildIncrementalWatermarkSql(ctx);
    ILoggingObject loggingObject =
        new SimpleLoggingObject(
            BvScd2PipelineSupport.class.getSimpleName() + ".resolveIncrementalWatermarkValue",
            LoggingObjectType.GENERAL,
            null);
    try (Database db = new Database(loggingObject, ctx.variables, ctx.targetDatabaseMeta)) {
      db.connect();
      RowMetaAndData row = db.getOneRow(sql);
      if (row == null
          || row.getData() == null
          || row.getData().length == 0
          || row.getData()[0] == null) {
        return DEFAULT_INCREMENTAL_SENTINEL;
      }
      Object value = row.getData()[0];
      if (value instanceof Timestamp timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
      }
      if (value instanceof java.util.Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
      }
      String text = row.getString(INCREMENTAL_WATERMARK_FIELD, DEFAULT_INCREMENTAL_SENTINEL);
      return Utils.isEmpty(text) ? DEFAULT_INCREMENTAL_SENTINEL : text;
    } catch (Exception e) {
      return DEFAULT_INCREMENTAL_SENTINEL;
    }
  }

  static String buildLegTableInputSql(Scd2BuildContext ctx, SatelliteLeg leg) {
    DatabaseMeta databaseMeta = leg.databaseMeta(ctx);
    List<String> selectFields = new ArrayList<>();
    String outputHashKey = ctx.hashKeyFieldName;
    String sourceHashKey = leg.hashKeyField(ctx);
    String outputTimestamp =
        ctx.isMultiSatellite() ? leg.sourceFunctionalTimestampField : ctx.functionalTimestampField;
    String sourceTimestamp =
        !Utils.isEmpty(leg.sourceFunctionalTimestampField)
            ? leg.sourceFunctionalTimestampField
            : outputTimestamp;

    if (ctx.includeHashKey) {
      // Source-query hash keys stay as the physical column; rename happens in Select Values.
      selectFields.add(databaseMeta.quoteField(sourceHashKey));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(databaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    if (ctx.isMultiSatellite()) {
      for (BvScd2FieldMapping mapping : leg.fieldMappings) {
        if (mapping != null && !Utils.isEmpty(mapping.getSourceFieldName())) {
          selectFields.add(
              databaseMeta.quoteField(ctx.variables.resolve(mapping.getSourceFieldName())));
        }
      }
    } else if (leg.isSourceQuery()) {
      for (String attr :
          BvSourceQuerySqlSupport.attributeFieldNames(leg.sourceQuery, ctx.variables)) {
        if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
          continue;
        }
        selectFields.add(databaseMeta.quoteField(attr));
      }
    } else {
      for (String attr : ctx.attributeFieldNames) {
        if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
          continue;
        }
        selectFields.add(databaseMeta.quoteField(attr));
      }
    }
    // Multi-sat BV RS comes from _bv_source (post-repeat rename). Never read the physical
    // satellite column — VaultSpeed-style sats omit it and JDBC would fail.
    if (shouldSelectPhysicalRecordSource(ctx, leg)) {
      selectFields.add(databaseMeta.quoteField(ctx.recordSourceField));
    }
    selectFields.add(
        BvSourceQuerySqlSupport.selectExpression(databaseMeta, sourceTimestamp, outputTimestamp));

    String fromClause =
        !Utils.isEmpty(leg.fromClause)
            ? leg.fromClause
            : databaseMeta.getQuotedSchemaTableCombination(
                ctx.variables, null, leg.satelliteTableName);

    List<String> whereClauses = new ArrayList<>();
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      whereClauses.add(
          buildIncrementalSatelliteFilterSql(databaseMeta.quoteField(sourceTimestamp)));
    }
    if (isHashKeyPartitioned(ctx) && databaseMeta != null) {
      String quotedHashKey = databaseMeta.quoteField(sourceHashKey);
      String predicate =
          BvScd2HashPartitionSqlSupport.buildPredicate(
              databaseMeta,
              ctx.dvConfig != null ? ctx.dvConfig.resolveHashKeyDataType() : null,
              quotedHashKey);
      if (!Utils.isEmpty(predicate)) {
        whereClauses.add(predicate);
      }
    }

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(fromClause);
    if (!whereClauses.isEmpty()) {
      sql.append(" WHERE ");
      sql.append(String.join(" AND ", whereClauses));
    }
    appendScd2OrderBy(sql, ctx, databaseMeta, true, outputTimestamp, selectFields, sourceHashKey);

    return sql.toString();
  }

  static String buildHubTableInputSql(Scd2BuildContext ctx) {
    if (ctx == null || !ctx.includeHubBusinessKeys()) {
      return "";
    }
    List<String> selectFields = new ArrayList<>();
    selectFields.add(ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName));
    for (String hubBk : ctx.hubBusinessKeyFieldNames()) {
      selectFields.add(ctx.sourceDatabaseMeta.quoteField(hubBk));
    }
    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", selectFields));
    sql.append(" FROM ");
    sql.append(
        ctx.sourceDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.hubTableName));
    if (isHashKeyPartitioned(ctx) && ctx.sourceDatabaseMeta != null) {
      String quotedHashKey = ctx.sourceDatabaseMeta.quoteField(ctx.hashKeyFieldName);
      String predicate =
          BvScd2HashPartitionSqlSupport.buildPredicate(
              ctx.sourceDatabaseMeta,
              ctx.dvConfig != null ? ctx.dvConfig.resolveHashKeyDataType() : null,
              quotedHashKey);
      if (!Utils.isEmpty(predicate)) {
        sql.append(" WHERE ");
        sql.append(predicate);
      }
    }
    appendScd2OrderBy(sql, ctx, ctx.sourceDatabaseMeta, false, null, selectFields);
    return sql.toString();
  }

  /**
   * Hash-key (and optional driving-key / timestamp) ORDER BY matching {@link String#compareTo()}
   * for STRING/HEX keys so {@code SortedSchemaMerge} sees pre-sorted streams.
   */
  static void appendScd2OrderBy(
      StringBuilder sql,
      Scd2BuildContext ctx,
      DatabaseMeta databaseMeta,
      boolean includeTimestamp,
      String timestampField,
      List<String> selectFieldsFallback) {
    appendScd2OrderBy(
        sql,
        ctx,
        databaseMeta,
        includeTimestamp,
        timestampField,
        selectFieldsFallback,
        ctx.hashKeyFieldName);
  }

  static void appendScd2OrderBy(
      StringBuilder sql,
      Scd2BuildContext ctx,
      DatabaseMeta databaseMeta,
      boolean includeTimestamp,
      String timestampField,
      List<String> selectFieldsFallback,
      String hashKeyField) {
    sql.append(" ORDER BY ");
    List<String> terms = new ArrayList<>();
    if (ctx.includeHashKey) {
      terms.add(hashKeyOrderByTerm(databaseMeta, ctx, hashKeyField));
    } else if (ctx.hasDrivingKey()) {
      terms.add(databaseMeta.quoteField(ctx.drivingKeyFieldName));
    } else if (selectFieldsFallback != null && !selectFieldsFallback.isEmpty()) {
      terms.add(selectFieldsFallback.get(0));
    }
    if (ctx.hasDrivingKey() && ctx.includeHashKey) {
      terms.add(databaseMeta.quoteField(ctx.drivingKeyFieldName));
    }
    if (includeTimestamp && !Utils.isEmpty(timestampField)) {
      terms.add(databaseMeta.quoteField(timestampField));
    }
    sql.append(String.join(", ", terms));
  }

  static String hashKeyOrderByTerm(DatabaseMeta databaseMeta, Scd2BuildContext ctx) {
    return hashKeyOrderByTerm(databaseMeta, ctx, ctx.hashKeyFieldName);
  }

  static String hashKeyOrderByTerm(
      DatabaseMeta databaseMeta, Scd2BuildContext ctx, String hashKeyField) {
    String field = !Utils.isEmpty(hashKeyField) ? hashKeyField : ctx.hashKeyFieldName;
    String quoted = databaseMeta.quoteField(field);
    if (!hashKeyStoredAsString(ctx)) {
      return quoted;
    }
    return DvSqlOrderBySupport.javaStringCompareOrderExpression(databaseMeta, quoted);
  }

  static boolean hashKeyStoredAsString(Scd2BuildContext ctx) {
    HashKeyDataType type =
        ctx != null && ctx.dvConfig != null
            ? ctx.dvConfig.resolveHashKeyDataType()
            : HashKeyDataType.HEX;
    return type != HashKeyDataType.BINARY;
  }

  public static String resolveFunctionalTimestampField(
      BvScd2Table scd2Table,
      BusinessVaultConfiguration bvConfig,
      DataVaultConfiguration dvConfig,
      IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getFunctionalTimestampField())) {
      return variables.resolve(scd2Table.getFunctionalTimestampField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getFunctionalTimestampField())) {
      return variables.resolve(bvConfig.getFunctionalTimestampField());
    }
    if (dvConfig != null && !Utils.isEmpty(dvConfig.getLoadDateField())) {
      return variables.resolve(dvConfig.getLoadDateField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getLoadDateFieldFallback())) {
      return variables.resolve(bvConfig.getLoadDateFieldFallback());
    }
    return "LOAD_DATE";
  }

  public static String resolveValidFromField(
      BvScd2Table scd2Table, BusinessVaultConfiguration bvConfig, IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getValidFromField())) {
      return variables.resolve(scd2Table.getValidFromField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getValidFromField())) {
      return variables.resolve(bvConfig.getValidFromField());
    }
    return variables.resolve(BusinessVaultConfiguration.DEFAULT_VALID_FROM_FIELD);
  }

  public static String resolveValidToField(
      BvScd2Table scd2Table, BusinessVaultConfiguration bvConfig, IVariables variables) {
    if (scd2Table != null && !Utils.isEmpty(scd2Table.getValidToField())) {
      return variables.resolve(scd2Table.getValidToField());
    }
    if (bvConfig != null && !Utils.isEmpty(bvConfig.getValidToField())) {
      return variables.resolve(bvConfig.getValidToField());
    }
    return variables.resolve(BusinessVaultConfiguration.DEFAULT_VALID_TO_FIELD);
  }

  static DvSatellite resolveSourceSatellite(BvScd2Table table, DataVaultModel dvModel)
      throws HopException {
    for (BvDerivativeRef ref : table.getDerivatives()) {
      if (ref == null
          || ref.getDvTableType() != DvTableType.SATELLITE
          || Utils.isEmpty(ref.getDvTableName())) {
        continue;
      }
      IDvTable dvTable = dvModel.findTable(ref.getDvTableName());
      if (dvTable instanceof DvSatellite satellite) {
        return satellite;
      }
    }
    throw new HopException(
        "SCD2 table " + table.getName() + " must reference a Data Vault satellite derivative");
  }

  static String resolveHashKeyFieldName(
      DvSatellite satellite, DataVaultModel model, IVariables variables) {
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = model.findHub(satellite.getHubName());
      if (hub != null) {
        String hashKey = variables.resolve(hub.getHashKeyFieldName());
        if (!Utils.isEmpty(hashKey)) {
          return hashKey;
        }
        if (!Utils.isEmpty(hub.getBusinessKeys())) {
          return hub.getBusinessKeys().get(0).getName() + "_hk";
        }
      }
    } else if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = model.findLink(satellite.getLinkName());
      if (link != null) {
        String linkHash = link.resolveLinkHashKeyFieldName(variables);
        if (!Utils.isEmpty(linkHash)) {
          return linkHash;
        }
      }
    }
    return "hashkey";
  }

  static List<String> resolveAttributeFieldNames(DvSatellite satellite) {
    List<String> names = new ArrayList<>();
    if (satellite.getAttributes() == null) {
      return names;
    }
    for (SatelliteAttribute attr : satellite.getAttributes()) {
      if (attr != null && !Utils.isEmpty(attr.getName())) {
        names.add(attr.getName());
      }
    }
    return names;
  }

  static IValueMeta resolveHashKeyValueMeta(String hashKeyName, DataVaultModel dvModel) {
    DataVaultConfiguration config = dvModel.getConfigurationOrDefault();
    HashKeyDataType hdt = config.resolveHashKeyDataType();
    HashAlgorithm algo = config.resolveHashAlgorithm();
    if (algo == null) {
      algo = HashAlgorithm.MD5;
    }
    int digestBytes = algo.getDigestLength();

    if (hdt == HashKeyDataType.BINARY) {
      IValueMeta hashMeta = new ValueMetaBinary(hashKeyName);
      hashMeta.setLength(digestBytes);
      return hashMeta;
    }
    if (hdt == HashKeyDataType.HEX) {
      IValueMeta hashMeta = new ValueMetaString(hashKeyName);
      hashMeta.setLength(digestBytes * 2);
      return hashMeta;
    }
    int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
    IValueMeta hashMeta = new ValueMetaString(hashKeyName);
    hashMeta.setLength(stringMax);
    return hashMeta;
  }

  private static DvSatellite findSatelliteByName(List<DvSatellite> satellites, String name) {
    for (DvSatellite satellite : satellites) {
      if (satellite != null && name.equals(satellite.getName())) {
        return satellite;
      }
    }
    return null;
  }

  private static IValueMeta cloneValueMetaWithName(IValueMeta sourceMeta, String targetName)
      throws HopException {
    try {
      IValueMeta targetMeta = ValueMetaFactory.createValueMeta(targetName, sourceMeta.getType());
      targetMeta.setLength(sourceMeta.getLength());
      targetMeta.setPrecision(sourceMeta.getPrecision());
      targetMeta.setConversionMask(sourceMeta.getConversionMask());
      return targetMeta;
    } catch (org.apache.hop.core.exception.HopPluginException e) {
      throw new HopException("Error creating value meta for mapped field " + targetName, e);
    }
  }

  private static IValueMeta findAttributeValueMeta(DvSatellite satellite, String name)
      throws HopException {
    if (satellite.getAttributes() == null) {
      return null;
    }
    for (SatelliteAttribute attr : satellite.getAttributes()) {
      if (attr != null && name.equals(attr.getName())) {
        String dt = attr.getDataType();
        int typeId = IValueMeta.TYPE_STRING;
        if (!Utils.isEmpty(dt)) {
          typeId = ValueMetaFactory.getIdForValueMeta(dt);
          if (typeId <= 0) {
            typeId = IValueMeta.TYPE_STRING;
          }
        }
        try {
          IValueMeta vm = ValueMetaFactory.createValueMeta(name, typeId);
          vm.setLength(Const.toInt(attr.getLength(), -1));
          vm.setPrecision(Const.toInt(attr.getPrecision(), -1));
          return vm;
        } catch (org.apache.hop.core.exception.HopPluginException e) {
          throw new HopException("Error creating value meta for attribute " + name, e);
        }
      }
    }
    return null;
  }

  private static TransformMeta addSatelliteTableInput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta watermarkParam) {
    return addLegTableInput(ctx, ctx.legs.get(0), pipelineMeta, LOCATION_START, watermarkParam);
  }

  private static TransformMeta addLegTableInput(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      Point location,
      TransformMeta watermarkParam) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(leg.connectionName(ctx));
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildLegTableInputSql(ctx, leg));
    if (isHashKeyPartitioned(ctx)) {
      tableInputMeta.setVariableReplacementActive(true);
    }
    if (watermarkParam != null) {
      // Single ? for watermark — bound from param Constant info stream.
      tableInputMeta.setLookup(watermarkParam.getName());
    }

    TransformMeta tm = new TransformMeta("TableInput", "read_" + leg.sourceName(), tableInputMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    if (watermarkParam != null) {
      wireTableInputParameterStream(pipelineMeta, tableInputMeta, watermarkParam, tm);
    }
    return tm;
  }

  /**
   * Attaches parent-hub business keys with a LEFT OUTER Merge Join after SCD2 collapse, immediately
   * before SQL calculations. The historization chain (merge / repeat / analytic / collapse) is
   * unchanged, including incremental baseline.
   */
  private static TransformMeta addHubBusinessKeyJoin(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    if (predecessor == null || !ctx.includeHubBusinessKeys() || !ctx.includeHashKey) {
      return predecessor;
    }
    Point collapseLocation =
        predecessor.getLocation() != null ? predecessor.getLocation() : LOCATION_START;
    TransformMeta hubRead =
        addHubTableInput(
            ctx,
            pipelineMeta,
            new Point(collapseLocation.x, collapseLocation.y + LEG_SPACING_HEIGHT));

    MergeJoinMeta mergeJoinMeta = new MergeJoinMeta();
    mergeJoinMeta.setJoinType("LEFT OUTER");
    mergeJoinMeta.setLeftTransformName(predecessor.getName());
    mergeJoinMeta.setRightTransformName(hubRead.getName());
    mergeJoinMeta.getKeyFields1().add(ctx.hashKeyFieldName);
    mergeJoinMeta.getKeyFields2().add(ctx.hashKeyFieldName);

    TransformMeta tm = new TransformMeta("MergeJoin", JOIN_HUB_BK_TRANSFORM, mergeJoinMeta);
    tm.setLocation(collapseLocation.x + SPACING_WIDTH, collapseLocation.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(hubRead, tm));
    mergeJoinMeta.setParentTransformMeta(tm);
    mergeJoinMeta.searchInfoAndTargetTransforms(pipelineMeta.getTransforms());
    return tm;
  }

  private static TransformMeta addHubTableInput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, Point location) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildHubTableInputSql(ctx));
    if (isHashKeyPartitioned(ctx)) {
      tableInputMeta.setVariableReplacementActive(true);
    }
    TransformMeta tableInput =
        new TransformMeta("TableInput", "read_" + ctx.hubTableName, tableInputMeta);
    tableInput.setLocation(location);
    pipelineMeta.addTransform(tableInput);
    GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, ctx.sourceDbName);
    return tableInput;
  }

  private static TransformMeta addOpenTargetTableInput(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      Point location,
      TransformMeta openRowFilterParam) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.targetDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildOpenTargetTableInputSql(ctx));
    if (openRowFilterParam != null) {
      tableInputMeta.setLookup(openRowFilterParam.getName());
    }

    TransformMeta tm =
        new TransformMeta("TableInput", "read_open_" + ctx.bvTargetTableName, tableInputMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    if (openRowFilterParam != null) {
      wireTableInputParameterStream(pipelineMeta, tableInputMeta, openRowFilterParam, tm);
    }
    GeneratedPipelineMetadataSupport.stampTargetRead(
        tm, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    return tm;
  }

  private static TransformMeta addOpenTargetCloseLookupTableInput(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      Point location,
      TransformMeta openRowFilterParam) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.targetDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildOpenTargetCloseLookupSql(ctx));
    if (openRowFilterParam != null) {
      tableInputMeta.setLookup(openRowFilterParam.getName());
    }

    TransformMeta tm =
        new TransformMeta(
            "TableInput", CLOSE_LOOKUP_READ_PREFIX + ctx.bvTargetTableName, tableInputMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    if (openRowFilterParam != null) {
      wireTableInputParameterStream(pipelineMeta, tableInputMeta, openRowFilterParam, tm);
    }
    GeneratedPipelineMetadataSupport.stampTargetRead(
        tm, "scd2", ctx.scd2Table.getName(), ctx.bvTargetTableName, ctx.targetDbName);
    return tm;
  }

  /**
   * Wires a Table Input info stream so JDBC {@code ?} placeholders receive fields from {@code
   * paramSource} in field order. Copies to all targets when the param Constant fans out.
   */
  private static void wireTableInputParameterStream(
      PipelineMeta pipelineMeta,
      TableInputMeta tableInputMeta,
      TransformMeta paramSource,
      TransformMeta tableInput) {
    paramSource.setDistributes(false);
    tableInputMeta.searchInfoAndTargetTransforms(pipelineMeta.getTransforms());
    pipelineMeta.addPipelineHop(new PipelineHopMeta(paramSource, tableInput));
  }

  /**
   * Single Timestamp field used as the satellite Table Input watermark parameter ({@code ?}). Value
   * is resolved against the BV target at pipeline generation time.
   *
   * <p>Uses Generate Rows (not Constant): Constant only enriches incoming rows and emits nothing
   * when used as a pipeline start without a predecessor.
   */
  private static TransformMeta addIncrementalWatermarkParamConstant(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, Point location) {
    return addParameterRowGenerator(
        pipelineMeta,
        PARAM_WATERMARK_TRANSFORM,
        location,
        List.of(
            timestampGeneratorField(
                INCREMENTAL_WATERMARK_FIELD, resolveIncrementalWatermarkValue(ctx))));
  }

  /**
   * Parameter row for open-target / close-lookup Table Inputs.
   *
   * <ul>
   *   <li>Always: open-end sentinel ({@code valid_to = ?}) — first field
   *   <li>Same-connection only: one watermark field per UNION branch in the delta hash-key subquery
   *       ({@code … WHERE ts > ?}). JDBC requires a distinct bind value for each {@code ?}; the
   *       same watermark value is repeated for each leg.
   * </ul>
   */
  private static TransformMeta addOpenRowFilterParamConstant(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, Point location) {
    List<GeneratorField> fields = new ArrayList<>();
    fields.add(timestampGeneratorField(OPEN_END_PARAM_FIELD, ctx.openEndSentinel));
    if (ctx.includeHashKey && hasLegSharingTargetConnection(ctx)) {
      String watermark = resolveIncrementalWatermarkValue(ctx);
      int watermarkParams = countDeltaHashKeyWatermarkPlaceholders(ctx);
      for (int i = 0; i < watermarkParams; i++) {
        // Unique field names; Table Input binds by position to each ? in order.
        fields.add(timestampGeneratorField(INCREMENTAL_WATERMARK_FIELD + "_" + i, watermark));
      }
    }
    return addParameterRowGenerator(
        pipelineMeta, PARAM_OPEN_ROW_FILTER_TRANSFORM, location, fields);
  }

  private static final String TIMESTAMP_PARAM_FORMAT = "yyyy-MM-dd HH:mm:ss";

  private static GeneratorField timestampGeneratorField(String name, String value) {
    return new GeneratorField(
        name, "Timestamp", TIMESTAMP_PARAM_FORMAT, -1, -1, null, null, null, value, false);
  }

  /**
   * Generate Rows with limit 1 — a true start transform that feeds Table Input {@code ?} params.
   */
  private static TransformMeta addParameterRowGenerator(
      PipelineMeta pipelineMeta,
      String transformName,
      Point location,
      List<GeneratorField> fields) {
    RowGeneratorMeta rowGeneratorMeta = new RowGeneratorMeta();
    rowGeneratorMeta.setRowLimit("1");
    rowGeneratorMeta.setNeverEnding(false);
    rowGeneratorMeta.getFields().addAll(fields);

    TransformMeta tm = new TransformMeta("RowGenerator", transformName, rowGeneratorMeta);
    tm.setLocation(location);
    tm.setDistributes(false);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addIncrementalBaselineLeg(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      Point location,
      boolean multiSatelliteMerge,
      TransformMeta openRowFilterParam) {
    TransformMeta openRead =
        addOpenTargetTableInput(ctx, pipelineMeta, location, openRowFilterParam);
    if (!multiSatelliteMerge) {
      return openRead;
    }
    TransformMeta baselineConstant =
        addBaselineSourceIndicatorConstant(ctx, pipelineMeta, openRead, location);
    return addBaselineSelectValues(ctx, pipelineMeta, baselineConstant, location);
  }

  private static TransformMeta addBaselineSourceIndicatorConstant(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location) {
    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField indicatorField =
        new ConstantField(SOURCE_INDICATOR_FIELD, "String", BASELINE_SOURCE_INDICATOR);
    constantMeta.getFields().add(indicatorField);

    TransformMeta tm = new TransformMeta("Constant", "source_baseline", constantMeta);
    tm.setLocation(location.x + SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addBaselineSelectValues(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (ctx.includeHashKey) {
      selectFields.add(selectField(ctx.hashKeyFieldName, null));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(selectField(ctx.drivingKeyFieldName, null));
    }
    for (String attr : ctx.persistedAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      selectFields.add(selectField(attr, null));
    }
    selectFields.add(selectField(ctx.recordSourceField, null));
    selectFields.add(selectField(ctx.functionalTimestampField, null));
    selectFields.add(selectField(SOURCE_INDICATOR_FIELD, null));

    TransformMeta tm = new TransformMeta("SelectValues", "select_baseline", selectMeta);
    tm.setLocation(location.x + 2 * SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  /**
   * Renames a source-query hash key column to the SCD2/hub grain name after Table Input. Physical
   * SQL keeps the messy source column name.
   */
  private static TransformMeta addSourceQueryHashKeyRename(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location)
      throws HopException {
    if (predecessor == null
        || ctx == null
        || leg == null
        || !leg.isSourceQuery()
        || !ctx.includeHashKey) {
      return predecessor;
    }
    String sourceHashKey = leg.hashKeyField(ctx);
    String grainHashKey = ctx.hashKeyFieldName;
    if (Utils.isEmpty(sourceHashKey)
        || Utils.isEmpty(grainHashKey)
        || sourceHashKey.equals(grainHashKey)) {
      return predecessor;
    }
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    selectMeta.getSelectOption().getSelectFields().add(selectField(sourceHashKey, grainHashKey));
    TransformMeta tm =
        new TransformMeta("SelectValues", "rename_hk_" + leg.sourceName(), selectMeta);
    tm.setLocation(location.x + SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addLegSourceIndicatorConstant(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location) {
    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField indicatorField =
        new ConstantField(SOURCE_INDICATOR_FIELD, "String", leg.sourceIndicatorValue);
    constantMeta.getFields().add(indicatorField);

    TransformMeta tm = new TransformMeta("Constant", "source_" + leg.sourceName(), constantMeta);
    tm.setLocation(location.x + SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  /**
   * For single-satellite pipelines, inject a constant record-source field when the DV satellite
   * omits that column so Analytic Query / Group By / BV write still see {@code
   * ctx.recordSourceField}.
   */
  private static TransformMeta injectRecordSourceConstantIfNeeded(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location) {
    if (predecessor == null
        || leg == null
        || leg.satellite == null
        || storesPhysicalRecordSource(leg)) {
      return predecessor;
    }
    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta
        .getFields()
        .add(new ConstantField(ctx.recordSourceField, "String", leg.sourceIndicatorValue));
    TransformMeta tm =
        new TransformMeta("Constant", "record_source_" + leg.sourceName(), constantMeta);
    tm.setLocation(location.x + SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addLegSelectValues(
      Scd2BuildContext ctx,
      SatelliteLeg leg,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      Point location)
      throws HopException {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (ctx.includeHashKey) {
      selectFields.add(selectField(leg.hashKeyField(ctx), ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(selectField(ctx.drivingKeyFieldName, null));
    }
    for (BvScd2FieldMapping mapping : leg.fieldMappings) {
      if (mapping == null) {
        continue;
      }
      String sourceFieldName = ctx.variables.resolve(mapping.getSourceFieldName());
      String targetFieldName = ctx.variables.resolve(mapping.getTargetFieldName());
      selectFields.add(selectField(sourceFieldName, targetFieldName));
    }
    if (!leg.sourceFunctionalTimestampField.equals(ctx.functionalTimestampField)) {
      selectFields.add(
          selectField(leg.sourceFunctionalTimestampField, ctx.functionalTimestampField));
    } else {
      selectFields.add(selectField(ctx.functionalTimestampField, null));
    }
    selectFields.add(selectField(SOURCE_INDICATOR_FIELD, null));

    TransformMeta tm = new TransformMeta("SelectValues", "select_" + leg.sourceName(), selectMeta);
    tm.setLocation(location.x + 2 * SPACING_WIDTH, location.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static SelectField selectField(String name, String rename) {
    SelectField selectField = new SelectField();
    selectField.setName(name);
    if (!Utils.isEmpty(rename) && !rename.equals(name)) {
      selectField.setRename(rename);
    }
    return selectField;
  }

  private static TransformMeta addSortedSchemaMerge(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, List<TransformMeta> legOutputs) {
    List<String> inputTransformNames = new ArrayList<>();
    for (TransformMeta legOutput : legOutputs) {
      inputTransformNames.add(legOutput.getName());
    }

    List<SortedSchemaMergeSortKey> sortKeys = new ArrayList<>();
    sortKeys.add(new SortedSchemaMergeSortKey(ctx.hashKeyFieldName, true));
    if (ctx.hasDrivingKey()) {
      sortKeys.add(new SortedSchemaMergeSortKey(ctx.drivingKeyFieldName, true));
    }
    sortKeys.add(new SortedSchemaMergeSortKey(ctx.functionalTimestampField, true));

    SortedSchemaMergeMeta sortedSchemaMergeMeta =
        SortedSchemaMergeMetaFactory.create(inputTransformNames, sortKeys);

    TransformMeta tm =
        new TransformMeta("SortedSchemaMerge", "merge_sorted", sortedSchemaMergeMeta);
    tm.setLocation(LOCATION_START.x + 3 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    for (TransformMeta legOutput : legOutputs) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(legOutput, tm));
    }
    return tm;
  }

  private static TransformMeta addRepeatFields(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    RepeatFieldsMeta repeatFieldsMeta = new RepeatFieldsMeta();
    repeatFieldsMeta.getGroupFields().add(ctx.hashKeyFieldName);
    if (ctx.hasDrivingKey()) {
      repeatFieldsMeta.getGroupFields().add(ctx.drivingKeyFieldName);
    }

    for (BvScd2FieldMapping mapping : ctx.scd2Table.getFieldMappings()) {
      if (mapping == null) {
        continue;
      }
      String targetFieldName = ctx.variables.resolve(mapping.getTargetFieldName());
      String satelliteName = ctx.variables.resolve(mapping.getSatelliteName());
      if (Utils.isEmpty(targetFieldName) || Utils.isEmpty(satelliteName)) {
        continue;
      }
      SatelliteLeg leg = findLeg(ctx, satelliteName);
      if (leg == null) {
        continue;
      }
      Repeat repeat = new Repeat();
      repeat.setType(RepeatType.CurrentWhenIndicated);
      repeat.setSourceField(targetFieldName);
      repeat.setTargetField(repeatTargetFieldName(targetFieldName));
      repeat.setIndicatorFieldName(SOURCE_INDICATOR_FIELD);
      repeat.setIndicatorValue(leg.sourceIndicatorValue);
      repeatFieldsMeta.getRepeats().add(repeat);
    }
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      for (String targetFieldName : ctx.persistedAttributeFieldNames()) {
        if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(targetFieldName)) {
          continue;
        }
        Repeat baselineRepeat = new Repeat();
        baselineRepeat.setType(RepeatType.CurrentWhenIndicated);
        baselineRepeat.setSourceField(targetFieldName);
        baselineRepeat.setTargetField(repeatTargetFieldName(targetFieldName));
        baselineRepeat.setIndicatorFieldName(SOURCE_INDICATOR_FIELD);
        baselineRepeat.setIndicatorValue(BASELINE_SOURCE_INDICATOR);
        repeatFieldsMeta.getRepeats().add(baselineRepeat);
      }
    }

    TransformMeta tm = new TransformMeta("RepeatFields", "repeat_sparse", repeatFieldsMeta);
    tm.setLocation(LOCATION_START.x + 4 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addPostRepeatSelectValues(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (ctx.includeHashKey) {
      selectFields.add(selectField(ctx.hashKeyFieldName, null));
    }
    if (ctx.hasDrivingKey()) {
      selectFields.add(selectField(ctx.drivingKeyFieldName, null));
    }
    selectFields.add(selectField(ctx.functionalTimestampField, null));
    selectFields.add(selectField(SOURCE_INDICATOR_FIELD, ctx.recordSourceField));
    for (String attr : ctx.collapseAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      selectFields.add(selectField(repeatTargetFieldName(attr), attr));
    }

    TransformMeta tm = new TransformMeta("SelectValues", "select_repeated", selectMeta);
    tm.setLocation(LOCATION_START.x + 5 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static String repeatTargetFieldName(String fieldName) {
    return REPEAT_FIELD_PREFIX + fieldName;
  }

  private static SatelliteLeg findLeg(Scd2BuildContext ctx, String satelliteName) {
    if (ctx == null || Utils.isEmpty(satelliteName)) {
      return null;
    }
    for (SatelliteLeg leg : ctx.legs) {
      if (satelliteName.equals(leg.sourceName())) {
        return leg;
      }
    }
    return null;
  }

  private static TransformMeta addAnalyticQuery(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    AnalyticQueryMeta analyticQueryMeta = new AnalyticQueryMeta();
    List<GroupField> partitionFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      partitionFields.add(new GroupField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      partitionFields.add(new GroupField(ctx.drivingKeyFieldName));
    }
    analyticQueryMeta.setGroupFields(partitionFields);

    List<QueryField> queryFields = new ArrayList<>();
    queryFields.add(
        new QueryField(
            ctx.validFromField, ctx.functionalTimestampField, QueryField.AggregateType.LAG, 1));
    queryFields.add(
        new QueryField(
            ctx.validToField, ctx.functionalTimestampField, QueryField.AggregateType.LEAD, 1));
    analyticQueryMeta.setQueryFields(queryFields);

    String analyticTransformName =
        ctx.isMultiSatellite()
            ? "analytic_" + ctx.bvTargetTableName
            : "analytic_" + ctx.satelliteTableName;
    int analyticX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 6 * SPACING_WIDTH
            : LOCATION_START.x + SPACING_WIDTH;
    TransformMeta tm = new TransformMeta("AnalyticQuery", analyticTransformName, analyticQueryMeta);
    tm.setLocation(analyticX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addCalculations(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    if (ctx == null || ctx.scd2Table == null || !ctx.scd2Table.hasCalculations()) {
      return predecessor;
    }
    SqlExpressionMeta meta =
        SqlExpressionMetaFactory.create(
            BvScd2CalculationValidationSupport.toSpecs(
                ctx.scd2Table.getCalculations(), ctx.variables));
    String name = "calculate_" + ctx.bvTargetTableName;
    int x =
        predecessor.getLocation() != null
            ? predecessor.getLocation().x + SPACING_WIDTH
            : LOCATION_START.x;
    int y = predecessor.getLocation() != null ? predecessor.getLocation().y : LOCATION_START.y;
    TransformMeta tm = new TransformMeta("SqlExpression", name, meta);
    tm.setLocation(x, y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addGroupBy(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    GroupByMeta groupByMeta = new GroupByMeta();
    List<GroupingField> groupingFields = new ArrayList<>();

    if (ctx.includeHashKey) {
      groupingFields.add(new GroupingField(ctx.hashKeyFieldName));
    }
    if (ctx.hasDrivingKey()) {
      groupingFields.add(new GroupingField(ctx.drivingKeyFieldName));
    }
    for (String attr : ctx.collapseAttributeFieldNames()) {
      if (ctx.hasDrivingKey() && ctx.drivingKeyFieldName.equals(attr)) {
        continue;
      }
      groupingFields.add(new GroupingField(attr));
    }
    groupByMeta.setGroupingFields(groupingFields);

    List<Aggregation> aggregations = new ArrayList<>();

    Aggregation minAgg = new Aggregation();
    minAgg.setSubject(ctx.validFromField);
    minAgg.setField(ctx.validFromField);
    minAgg.setTypeLabel("MIN");
    aggregations.add(minAgg);

    Aggregation maxAgg = new Aggregation();
    maxAgg.setSubject(ctx.validToField);
    maxAgg.setField(ctx.validToField);
    maxAgg.setTypeLabel("MAX");
    aggregations.add(maxAgg);

    Aggregation rsAgg = new Aggregation();
    rsAgg.setSubject(ctx.recordSourceField);
    rsAgg.setField(ctx.recordSourceField);
    rsAgg.setTypeLabel("CONCAT_DISTINCT");
    rsAgg.setValue(RECORD_SOURCE_CONCAT_SEPARATOR);
    aggregations.add(rsAgg);

    Aggregation tsAgg = new Aggregation();
    tsAgg.setSubject(ctx.functionalTimestampField);
    tsAgg.setField(ctx.functionalTimestampField);
    tsAgg.setTypeLabel("MAX");
    aggregations.add(tsAgg);

    groupByMeta.setAggregations(aggregations);

    String collapseTransformName =
        ctx.isMultiSatellite()
            ? "collapse_" + ctx.bvTargetTableName
            : "collapse_" + ctx.satelliteTableName;
    int collapseX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 8 * SPACING_WIDTH
            : LOCATION_START.x + 3 * SPACING_WIDTH;
    TransformMeta tm = new TransformMeta("GroupBy", collapseTransformName, groupByMeta);
    tm.setLocation(collapseX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addIfNull(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    IfNullMeta ifNullMeta = new IfNullMeta();
    ifNullMeta.setSelectFields(true);

    Field validFromField = new Field();
    validFromField.setName(ctx.validFromField);
    validFromField.setValue(ctx.openStartSentinel);
    validFromField.setMask("yyyy-MM-dd HH:mm:ss");
    ifNullMeta.getFields().add(validFromField);

    Field validToField = new Field();
    validToField.setName(ctx.validToField);
    validToField.setValue(ctx.openEndSentinel);
    validToField.setMask("yyyy-MM-dd HH:mm:ss");
    ifNullMeta.getFields().add(validToField);

    int ifNullX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 7 * SPACING_WIDTH
            : LOCATION_START.x + 2 * SPACING_WIDTH;
    TransformMeta tm = new TransformMeta("IfNull", "sentinels", ifNullMeta);
    tm.setLocation(ifNullX, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addTableOutput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    TransformMeta withCycle = addConstantForLoadCycleId(ctx, pipelineMeta, predecessor);
    if (ctx.scd2Table != null && ctx.scd2Table.isIncrementalBuild()) {
      return addIncrementalWrites(ctx, pipelineMeta, withCycle);
    }
    return addFullRebuildTableOutput(ctx, pipelineMeta, withCycle);
  }

  private static TransformMeta addConstantForLoadCycleId(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (ctx.bvConfig == null || !ctx.bvConfig.isStoreLoadCycleId()) {
      return predecessor;
    }
    Point location = null;
    if (predecessor != null && predecessor.getLocation() != null) {
      location =
          new Point(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    }
    return DvLoadCycleSupport.addConstantForLoadCycleId(
        pipelineMeta,
        predecessor,
        true,
        ctx.bvConfig.getLoadCycleIdField(),
        ctx.variables,
        null,
        location);
  }

  private static TransformMeta addFullRebuildTableOutput(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        buildTargetTableLayout(ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    int tableOutputX =
        ctx.isMultiSatellite()
            ? LOCATION_START.x + 9 * SPACING_WIDTH
            : LOCATION_START.x + 4 * SPACING_WIDTH;

    boolean truncateTable = ctx.scd2Table == null || !ctx.scd2Table.isHashKeyPartitioned();
    Set<String> excludeFields = new LinkedHashSet<>();
    addStreamOnlyExcludeFields(excludeFields, ctx);
    DvTargetLoadSupport.TargetLoadResult result =
        addScd2TargetLoad(
            ctx,
            pipelineMeta,
            targetLayout,
            predecessor,
            tableOutputX,
            LOCATION_START.y,
            truncateTable,
            excludeFields);
    return result.transformMeta;
  }

  private static TransformMeta addIncrementalWrites(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        buildTargetTableLayout(ctx.scd2Table, ctx.bvConfig, ctx.dvModel, ctx.variables);
    Set<String> excludeFields = new LinkedHashSet<>();
    excludeFields.add(INCREMENTAL_WATERMARK_FIELD);
    excludeFields.add(CLOSE_LOOKUP_VALID_FROM_FIELD);
    addStreamOnlyExcludeFields(excludeFields, ctx);

    int x = predecessor.getLocation().x + SPACING_WIDTH;
    int y = predecessor.getLocation().y;

    TransformMeta watermarkConstant =
        addIncrementalWatermarkConstant(ctx, pipelineMeta, predecessor, new Point(x, y));
    x += SPACING_WIDTH;

    TransformMeta rowsToWrite =
        addFilterIncrementalRows(ctx, pipelineMeta, watermarkConstant, new Point(x, y));
    rowsToWrite.setDistributes(false);
    x += SPACING_WIDTH;

    DvTargetLoadSupport.TargetLoadResult writeResult =
        addScd2TargetLoad(ctx, pipelineMeta, targetLayout, rowsToWrite, x, y, false, excludeFields);
    TransformMeta writeTransform = writeResult.transformMeta;

    TransformMeta filterOpen =
        addFilterNewOpenRows(ctx, pipelineMeta, rowsToWrite, new Point(x, y + 40));
    TransformMeta openRowFilterParam = findTransform(pipelineMeta, PARAM_OPEN_ROW_FILTER_TRANSFORM);
    TransformMeta closeLookupRead =
        addOpenTargetCloseLookupTableInput(
            ctx, pipelineMeta, new Point(x - SPACING_WIDTH, y + 40), openRowFilterParam);
    TransformMeta joinCloseLookup =
        addJoinCloseLookupValidFrom(
            ctx, pipelineMeta, filterOpen, closeLookupRead, new Point(x + SPACING_WIDTH, y + 40));
    addCloseOpenVersion(
        ctx, pipelineMeta, joinCloseLookup, new Point(x + 2 * SPACING_WIDTH, y + 40));

    return writeTransform;
  }

  private static TransformMeta findTransform(PipelineMeta pipelineMeta, String name) {
    if (pipelineMeta == null || Utils.isEmpty(name)) {
      return null;
    }
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (name.equals(transformMeta.getName())) {
        return transformMeta;
      }
    }
    return null;
  }

  private static TransformMeta addJoinCloseLookupValidFrom(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta mainPredecessor,
      TransformMeta closeLookupRead,
      Point location) {
    if (closeLookupRead == null || mainPredecessor == null) {
      return mainPredecessor;
    }

    String joinKeyField =
        ctx.includeHashKey
            ? ctx.hashKeyFieldName
            : ctx.hasDrivingKey() ? ctx.drivingKeyFieldName : null;
    if (Utils.isEmpty(joinKeyField)) {
      return mainPredecessor;
    }

    MergeJoinMeta mergeJoinMeta = new MergeJoinMeta();
    mergeJoinMeta.setJoinType("INNER");
    mergeJoinMeta.setLeftTransformName(mainPredecessor.getName());
    mergeJoinMeta.setRightTransformName(closeLookupRead.getName());
    mergeJoinMeta.getKeyFields1().add(joinKeyField);
    mergeJoinMeta.getKeyFields2().add(joinKeyField);

    TransformMeta tm = new TransformMeta("MergeJoin", JOIN_CLOSE_LOOKUP_VALID_FROM, mergeJoinMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mainPredecessor, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(closeLookupRead, tm));
    mergeJoinMeta.setParentTransformMeta(tm);
    mergeJoinMeta.searchInfoAndTargetTransforms(pipelineMeta.getTransforms());
    return tm;
  }

  private static TransformMeta addIncrementalWatermarkConstant(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location) {
    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta
        .getFields()
        .add(
            new ConstantField(
                INCREMENTAL_WATERMARK_FIELD, "Timestamp", resolveIncrementalWatermarkValue(ctx)));

    TransformMeta tm =
        new TransformMeta("Constant", "set_" + INCREMENTAL_WATERMARK_FIELD, constantMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addFilterIncrementalRows(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location)
      throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition condition =
          new Condition(
              ctx.functionalTimestampField,
              Condition.Function.LARGER,
              INCREMENTAL_WATERMARK_FIELD,
              null);
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating incremental SCD2 watermark filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "filter_incremental_rows", filterRowsMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addFilterNewOpenRows(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location)
      throws HopException {
    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      Condition condition =
          new Condition(
              ctx.validToField,
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(
                  new ValueMetaTimestamp(ctx.validToField),
                  Timestamp.valueOf(ctx.openEndSentinel)));
      filterRowsMeta.getCompare().setCondition(condition);
    } catch (HopValueException e) {
      throw new HopException("Error creating incremental SCD2 open-row filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "filter_new_open_rows", filterRowsMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addCloseOpenVersion(
      Scd2BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point location) {
    UpdateMeta updateMeta = new UpdateMeta();
    updateMeta.setDefault();
    updateMeta.setConnection(ctx.targetDbName);
    updateMeta.setCommitSize(ctx.bvConfig.resolveTargetTableCommitSize(ctx.variables));
    updateMeta.setErrorIgnored(true);

    UpdateLookupField lookup = new UpdateLookupField();
    lookup.setSchemaName("");
    lookup.setTableName(ctx.bvTargetTableName);
    if (ctx.includeHashKey) {
      lookup
          .getLookupKeys()
          .add(new UpdateKeyField(ctx.hashKeyFieldName, ctx.hashKeyFieldName, "="));
    }
    if (ctx.hasDrivingKey()) {
      lookup
          .getLookupKeys()
          .add(new UpdateKeyField(ctx.drivingKeyFieldName, ctx.drivingKeyFieldName, "="));
    }
    lookup
        .getLookupKeys()
        .add(new UpdateKeyField(CLOSE_LOOKUP_VALID_FROM_FIELD, ctx.validFromField, "="));
    lookup.getUpdateFields().add(new UpdateField(ctx.validToField, ctx.validFromField));
    updateMeta.setLookupField(lookup);

    TransformMeta tm = new TransformMeta("Update", "close_" + ctx.bvTargetTableName, updateMeta);
    tm.setLocation(location);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static DvTargetLoadSupport.TargetLoadResult addScd2TargetLoad(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      int locationX,
      int locationY,
      boolean truncateTable)
      throws HopException {
    return addScd2TargetLoad(
        ctx,
        pipelineMeta,
        targetLayout,
        predecessor,
        locationX,
        locationY,
        truncateTable,
        Collections.emptySet());
  }

  private static DvTargetLoadSupport.TargetLoadResult addScd2TargetLoad(
      Scd2BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      int locationX,
      int locationY,
      boolean truncateTable,
      Set<String> excludeFields)
      throws HopException {
    String stagingFileInfix = null;
    if (isHashKeyPartitioned(ctx)
        && ctx.bvConfig != null
        && ctx.bvConfig.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE) {
      stagingFileInfix = BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_REF;
    }
    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.bvConfig,
            ctx.variables,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            ctx.bvTargetTableName,
            ctx.pipelineName,
            ctx.bvModel.getName(),
            locationX,
            locationY,
            stagingFileInfix);

    return DvTargetLoadSupport.addTargetLoad(
        targetCtx, pipelineMeta, targetLayout, predecessor, excludeFields, truncateTable);
  }

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    try {
      DbCache.clearAll();
      Scd2BuildContext ctx =
          createContext(metadataProvider, variables, bvModel, dvModel, scd2Table);
      if (ctx == null) {
        return List.of();
      }
      PipelineMeta scd2Pipeline = generatePipeline(ctx);
      if (!isHashKeyPartitioned(ctx)) {
        return List.of(scd2Pipeline);
      }
      PipelineMeta driverPipeline =
          BvScd2PartitionWorkflowSupport.buildDriverPipeline(ctx, scd2Pipeline);
      return List.of(scd2Pipeline, driverPipeline);
    } catch (Exception e) {
      throw new HopException(
          "Error generating SCD2 build pipeline for Business Vault table " + scd2Table.getName(),
          e);
    }
  }

  public static List<WorkflowMeta> generateBuildWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    if (scd2Table == null || !scd2Table.isHashKeyPartitioned()) {
      return List.of();
    }
    try {
      DbCache.clearAll();
      Scd2BuildContext ctx =
          createContext(metadataProvider, variables, bvModel, dvModel, scd2Table);
      if (ctx == null) {
        return List.of();
      }
      PipelineMeta scd2Pipeline = generatePipeline(ctx);
      PipelineMeta driverPipeline =
          BvScd2PartitionWorkflowSupport.buildDriverPipeline(ctx, scd2Pipeline);
      return List.of(
          BvScd2PartitionWorkflowSupport.buildWorkflow(ctx, driverPipeline, scd2Pipeline));
    } catch (Exception e) {
      throw new HopException(
          "Error generating SCD2 partition workflow for Business Vault table "
              + scd2Table.getName(),
          e);
    }
  }

  static boolean isHashKeyPartitioned(Scd2BuildContext ctx) {
    return ctx != null && ctx.scd2Table != null && ctx.scd2Table.isHashKeyPartitioned();
  }

  static void applyPartitionParameters(PipelineMeta pipelineMeta, Scd2BuildContext ctx)
      throws HopException {
    if (pipelineMeta == null || !isHashKeyPartitioned(ctx)) {
      return;
    }
    int count = ctx.scd2Table.getHashKeyPartitionCountOrDefault().getPartitionCount();
    pipelineMeta.addParameterDefinition(
        BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE,
        Integer.toString(count),
        "Hash-key partition count for this SCD2 full rebuild");
    pipelineMeta.addParameterDefinition(
        BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE,
        "0",
        "Zero-based hash-key partition number");
  }

  private static final class HubBkAttachment {
    final boolean enabled;
    final DvHub hub;
    final String tableName;
    final List<String> fieldNames;

    HubBkAttachment(boolean enabled, DvHub hub, String tableName, List<String> fieldNames) {
      this.enabled = enabled;
      this.hub = hub;
      this.tableName = tableName;
      this.fieldNames =
          fieldNames == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(fieldNames));
    }

    static HubBkAttachment none() {
      return new HubBkAttachment(false, null, null, List.of());
    }
  }

  /** Resolved inputs for one satellite or source-query branch in a generated SCD2 pipeline. */
  public static final class SatelliteLeg {
    final DvSatellite satellite;
    final BvSourceQuery sourceQuery;
    final String satelliteTableName;
    final String sourceIndicatorValue;
    final String sourceFunctionalTimestampField;
    final List<BvScd2FieldMapping> fieldMappings;
    final DatabaseMeta databaseMeta;
    final String connectionName;
    final String sourceHashKeyField;
    final String fromClause;

    SatelliteLeg(
        DvSatellite satellite,
        String satelliteTableName,
        String sourceIndicatorValue,
        String sourceFunctionalTimestampField,
        List<BvScd2FieldMapping> fieldMappings) {
      this(
          satellite,
          null,
          satelliteTableName,
          sourceIndicatorValue,
          sourceFunctionalTimestampField,
          fieldMappings,
          null,
          null,
          null,
          null);
    }

    SatelliteLeg(
        DvSatellite satellite,
        BvSourceQuery sourceQuery,
        String satelliteTableName,
        String sourceIndicatorValue,
        String sourceFunctionalTimestampField,
        List<BvScd2FieldMapping> fieldMappings,
        DatabaseMeta databaseMeta,
        String connectionName,
        String sourceHashKeyField,
        String fromClause) {
      this.satellite = satellite;
      this.sourceQuery = sourceQuery;
      this.satelliteTableName = satelliteTableName;
      this.sourceIndicatorValue = sourceIndicatorValue;
      this.sourceFunctionalTimestampField = sourceFunctionalTimestampField;
      this.fieldMappings =
          fieldMappings == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(fieldMappings));
      this.databaseMeta = databaseMeta;
      this.connectionName = connectionName;
      this.sourceHashKeyField = sourceHashKeyField;
      this.fromClause = fromClause;
    }

    boolean isSourceQuery() {
      return sourceQuery != null;
    }

    String sourceName() {
      if (sourceQuery != null && !Utils.isEmpty(sourceQuery.getName())) {
        return sourceQuery.getName();
      }
      return satellite != null ? satellite.getName() : satelliteTableName;
    }

    DatabaseMeta databaseMeta(Scd2BuildContext ctx) {
      return databaseMeta != null ? databaseMeta : ctx.sourceDatabaseMeta;
    }

    String connectionName(Scd2BuildContext ctx) {
      return !Utils.isEmpty(connectionName) ? connectionName : ctx.sourceDbName;
    }

    String hashKeyField(Scd2BuildContext ctx) {
      return !Utils.isEmpty(sourceHashKeyField) ? sourceHashKeyField : ctx.hashKeyFieldName;
    }
  }

  private static final class SharedScd2Resources {
    final BusinessVaultConfiguration bvConfig;
    final DataVaultConfiguration dvConfig;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String bvTargetTableName;
    final String functionalTimestampField;
    final String validFromField;
    final String validToField;
    final String recordSourceField;
    final String openStartSentinel;
    final String openEndSentinel;

    SharedScd2Resources(
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String bvTargetTableName,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel) {
      this.bvConfig = bvConfig;
      this.dvConfig = dvConfig;
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.bvTargetTableName = bvTargetTableName;
      this.functionalTimestampField = functionalTimestampField;
      this.validFromField = validFromField;
      this.validToField = validToField;
      this.recordSourceField = recordSourceField;
      this.openStartSentinel = openStartSentinel;
      this.openEndSentinel = openEndSentinel;
    }
  }

  /** Resolved inputs for a generated SCD2 build pipeline. */
  public static final class Scd2BuildContext {
    final BvScd2Table scd2Table;
    final List<SatelliteLeg> legs;
    final boolean multiSatellite;
    final List<String> mappedAttributeFieldNames;
    final BusinessVaultModel bvModel;
    final DataVaultModel dvModel;
    final BusinessVaultConfiguration bvConfig;
    final DataVaultConfiguration dvConfig;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;
    final DatabaseMeta sourceDatabaseMeta;
    final String sourceDbName;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String satelliteTableName;
    final String bvTargetTableName;
    final String pipelineName;
    final String hashKeyFieldName;
    final String drivingKeyFieldName;
    final List<String> attributeFieldNames;
    final String functionalTimestampField;
    final String validFromField;
    final String validToField;
    final String recordSourceField;
    final String openStartSentinel;
    final String openEndSentinel;
    final boolean includeHashKey;
    final boolean includeHubBusinessKeys;
    final DvHub parentHub;
    final String hubTableName;
    final List<String> hubBusinessKeyFieldNames;

    Scd2BuildContext(
        BvScd2Table scd2Table,
        List<SatelliteLeg> legs,
        boolean multiSatellite,
        List<String> mappedAttributeFieldNames,
        BusinessVaultModel bvModel,
        DataVaultModel dvModel,
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey) {
      this(
          scd2Table,
          legs,
          multiSatellite,
          mappedAttributeFieldNames,
          bvModel,
          dvModel,
          bvConfig,
          dvConfig,
          metadataProvider,
          variables,
          sourceDatabaseMeta,
          sourceDbName,
          targetDatabaseMeta,
          targetDbName,
          satelliteTableName,
          bvTargetTableName,
          pipelineName,
          hashKeyFieldName,
          drivingKeyFieldName,
          attributeFieldNames,
          functionalTimestampField,
          validFromField,
          validToField,
          recordSourceField,
          openStartSentinel,
          openEndSentinel,
          includeHashKey,
          HubBkAttachment.none());
    }

    Scd2BuildContext(
        BvScd2Table scd2Table,
        List<SatelliteLeg> legs,
        boolean multiSatellite,
        List<String> mappedAttributeFieldNames,
        BusinessVaultModel bvModel,
        DataVaultModel dvModel,
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey,
        HubBkAttachment hubBkAttachment) {
      this.scd2Table = scd2Table;
      this.legs = Collections.unmodifiableList(new ArrayList<>(legs));
      this.multiSatellite = multiSatellite;
      this.mappedAttributeFieldNames =
          mappedAttributeFieldNames == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(mappedAttributeFieldNames));
      this.bvModel = bvModel;
      this.dvModel = dvModel;
      this.bvConfig = bvConfig;
      this.dvConfig = dvConfig;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.sourceDatabaseMeta = sourceDatabaseMeta;
      this.sourceDbName = sourceDbName;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.satelliteTableName = satelliteTableName;
      this.bvTargetTableName = bvTargetTableName;
      this.pipelineName = pipelineName;
      this.hashKeyFieldName = hashKeyFieldName;
      this.drivingKeyFieldName = drivingKeyFieldName;
      this.attributeFieldNames = attributeFieldNames;
      this.functionalTimestampField = functionalTimestampField;
      this.validFromField = validFromField;
      this.validToField = validToField;
      this.recordSourceField = recordSourceField;
      this.openStartSentinel = openStartSentinel;
      this.openEndSentinel = openEndSentinel;
      this.includeHashKey = includeHashKey;
      HubBkAttachment attachment =
          hubBkAttachment != null ? hubBkAttachment : HubBkAttachment.none();
      this.includeHubBusinessKeys = attachment.enabled;
      this.parentHub = attachment.hub;
      this.hubTableName = attachment.tableName;
      this.hubBusinessKeyFieldNames = attachment.fieldNames;
    }

    /** Legacy test constructor for single-satellite contexts. */
    public Scd2BuildContext(
        BvScd2Table scd2Table,
        DvSatellite satellite,
        BusinessVaultModel bvModel,
        DataVaultModel dvModel,
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey) {
      this(
          scd2Table,
          List.of(
              new SatelliteLeg(
                  satellite,
                  satelliteTableName,
                  satellite.getName(),
                  functionalTimestampField,
                  List.of())),
          false,
          List.of(),
          bvModel,
          dvModel,
          bvConfig,
          dvConfig,
          metadataProvider,
          variables,
          sourceDatabaseMeta,
          sourceDbName,
          targetDatabaseMeta,
          targetDbName,
          satelliteTableName,
          bvTargetTableName,
          pipelineName,
          hashKeyFieldName,
          drivingKeyFieldName,
          attributeFieldNames,
          functionalTimestampField,
          validFromField,
          validToField,
          recordSourceField,
          openStartSentinel,
          openEndSentinel,
          includeHashKey,
          HubBkAttachment.none());
    }

    /** Test constructor that attaches hub business keys to a single-satellite context. */
    public Scd2BuildContext(
        BvScd2Table scd2Table,
        DvSatellite satellite,
        BusinessVaultModel bvModel,
        DataVaultModel dvModel,
        BusinessVaultConfiguration bvConfig,
        DataVaultConfiguration dvConfig,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DatabaseMeta sourceDatabaseMeta,
        String sourceDbName,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String satelliteTableName,
        String bvTargetTableName,
        String pipelineName,
        String hashKeyFieldName,
        String drivingKeyFieldName,
        List<String> attributeFieldNames,
        String functionalTimestampField,
        String validFromField,
        String validToField,
        String recordSourceField,
        String openStartSentinel,
        String openEndSentinel,
        boolean includeHashKey,
        DvHub parentHub,
        String hubTableName,
        List<String> hubBusinessKeyFieldNames) {
      this(
          scd2Table,
          List.of(
              new SatelliteLeg(
                  satellite,
                  satelliteTableName,
                  satellite.getName(),
                  functionalTimestampField,
                  List.of())),
          false,
          List.of(),
          bvModel,
          dvModel,
          bvConfig,
          dvConfig,
          metadataProvider,
          variables,
          sourceDatabaseMeta,
          sourceDbName,
          targetDatabaseMeta,
          targetDbName,
          satelliteTableName,
          bvTargetTableName,
          pipelineName,
          hashKeyFieldName,
          drivingKeyFieldName,
          attributeFieldNames,
          functionalTimestampField,
          validFromField,
          validToField,
          recordSourceField,
          openStartSentinel,
          openEndSentinel,
          includeHashKey,
          new HubBkAttachment(
              parentHub != null
                  && hubBusinessKeyFieldNames != null
                  && !hubBusinessKeyFieldNames.isEmpty(),
              parentHub,
              hubTableName,
              hubBusinessKeyFieldNames));
    }

    public DvSatellite getSatellite() {
      return legs.get(0).satellite;
    }

    boolean isMultiSatellite() {
      return multiSatellite;
    }

    List<String> collapseAttributeFieldNames() {
      return multiSatellite ? mappedAttributeFieldNames : attributeFieldNames;
    }

    List<String> persistedAttributeFieldNames() {
      if (hasFieldMappings(scd2Table)) {
        return resolveLoadedTargetFieldNames(scd2Table, variables);
      }
      return collapseAttributeFieldNames();
    }

    boolean includeHubBusinessKeys() {
      return includeHubBusinessKeys
          && hubBusinessKeyFieldNames != null
          && !hubBusinessKeyFieldNames.isEmpty();
    }

    List<String> hubBusinessKeyFieldNames() {
      return hubBusinessKeyFieldNames == null ? List.of() : hubBusinessKeyFieldNames;
    }

    boolean hasDrivingKey() {
      return !Utils.isEmpty(drivingKeyFieldName);
    }
  }
}
