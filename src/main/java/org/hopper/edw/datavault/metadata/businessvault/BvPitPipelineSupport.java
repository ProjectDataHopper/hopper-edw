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
import java.util.List;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.rowgenerator.GeneratorField;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.DvTargetLoadSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;

/** Generates PIT build pipelines from DV hub/satellite history. */
public final class BvPitPipelineSupport {

  private static final Class<?> PKG = BvPitPipelineSupport.class;

  private static final Point LOCATION_TABLE_INPUT = new Point(160, 160);
  private static final Point LOCATION_TABLE_OUTPUT = new Point(400, 160);
  private static final Point LOCATION_WATERMARK_PARAM = new Point(0, 160);

  /** Constant that feeds the PIT Table Input snapshot watermark {@code ?} parameter. */
  public static final String PARAM_SNAPSHOT_WATERMARK_TRANSFORM = "param_snapshot_watermark";

  static final String SNAPSHOT_WATERMARK_FIELD = "_snapshot_watermark";

  private BvPitPipelineSupport() {}

  public static PitBuildContext createContext(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvPitTable pitTable)
      throws HopException {
    if (metadataProvider == null || bvModel == null || dvModel == null || pitTable == null) {
      return null;
    }

    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DataVaultConfiguration dvConfig = dvModel.getConfigurationOrDefault();

    String sourceDbName = dvConfig.getTargetDatabase();
    if (Utils.isEmpty(sourceDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingDvTargetDatabase", pitTable.getName()));
    }
    DatabaseMeta sourceDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);

    String targetDbName = bvConfig.getTargetDatabase();
    if (Utils.isEmpty(targetDbName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingBvTargetDatabase", pitTable.getName()));
    }
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);

    DvHub hub = BvPitLayoutSupport.resolveHubDerivative(pitTable, dvModel);
    List<DvSatellite> satellites =
        BvPitLayoutSupport.resolveSatelliteDerivatives(pitTable, dvModel);
    List<BvSourceQuery> sourceQueries =
        BusinessVaultSourceQuerySupport.resolveSourceQueries(pitTable, bvModel);
    if (hub == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvPitPipelineSupport.Error.MissingHub", pitTable.getName()));
    }
    if (satellites.isEmpty() && sourceQueries.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MissingSatellite", pitTable.getName()));
    }
    if (satellites.size() + sourceQueries.size() > 1) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvPitPipelineSupport.Error.MultiSatelliteNotImplemented", pitTable.getName()));
    }
    if (!sourceQueries.isEmpty()
        && !BusinessVaultSourceQuerySupport.usesDvTargetConnection(
            sourceQueries.get(0), dvModel, variables)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvPitValidationSupport.Error.SourceQueryDifferentConnection",
              pitTable.getName(),
              sourceQueries.get(0).getName()));
    }

    DvSatellite satellite = satellites.isEmpty() ? null : satellites.get(0);
    BvSourceQuery sourceQuery = sourceQueries.isEmpty() ? null : sourceQueries.get(0);
    BvPitSnapshotSchedule schedule = pitTable.getSnapshotScheduleOrDefault();
    if (schedule.getCadence() != BvPitCadence.DAILY) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvPitPipelineSupport.Error.NonDailyCadenceNotImplemented",
              pitTable.getName(),
              schedule.getCadence().name()));
    }

    String bvTargetTableName =
        !Utils.isEmpty(pitTable.getTableName()) ? pitTable.getTableName() : pitTable.getName();
    String pipelineName = bvConfig.buildPitPipelineName(variables, bvTargetTableName);
    String hashKeyFieldName = BvPitLayoutSupport.resolveHubHashKeyFieldName(hub, variables);
    String snapshotDateField = BvPitLayoutSupport.resolveSnapshotDateField(pitTable, variables);
    String loadDateField =
        sourceQuery != null && !Utils.isEmpty(sourceQuery.getLoadDateField())
            ? variables.resolve(sourceQuery.getLoadDateField())
            : BvPitSnapshotSpineSupport.resolveLoadDateField(dvConfig, variables);
    String satellitePointerColumnName =
        sourceQuery != null
            ? BvPitLayoutSupport.resolveSourceQueryPointerColumnName(
                sourceQuery, schedule, variables)
            : BvPitLayoutSupport.resolveSatellitePointerColumnName(satellite, schedule, variables);
    String hubTableName = !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
    String satelliteTableName =
        sourceQuery != null
            ? (!Utils.isEmpty(sourceQuery.getTableName())
                ? sourceQuery.getTableName()
                : sourceQuery.getName())
            : BvPitLayoutSupport.resolveSatellitePhysicalName(satellite);

    return new PitBuildContext(
        pitTable,
        hub,
        satellite,
        schedule,
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
        bvTargetTableName,
        pipelineName,
        hashKeyFieldName,
        snapshotDateField,
        loadDateField,
        satellitePointerColumnName,
        hubTableName,
        satelliteTableName);
  }

  public static String buildPitTableInputSql(PitBuildContext ctx) throws HopException {
    if (ctx == null) {
      throw new HopException("PIT build context is required");
    }

    BvPitSnapshotAnchor anchor =
        ctx.schedule().getSnapshotAnchor() != null
            ? ctx.schedule().getSnapshotAnchor()
            : BvPitSnapshotAnchor.END_OF_PERIOD;

    StringBuilder sql = new StringBuilder("WITH ");
    boolean needsSatelliteLoadCte = needsEarliestSatelliteLoadCte(ctx.schedule());
    boolean needsHubLoadCte = needsEarliestHubLoadCte(ctx.schedule());

    if (needsSatelliteLoadCte) {
      sql.append("earliest_satellite_load AS (");
      if (ctx.satellite() == null) {
        sql.append("SELECT MIN(")
            .append(ctx.sourceDatabaseMeta().quoteField(ctx.loadDateField()))
            .append(") AS earliest_load FROM ")
            .append(satelliteRelation(ctx, "sat"));
      } else {
        sql.append(
            BvPitSnapshotSpineSupport.buildEarliestParticipatingSatelliteLoadSql(
                ctx.sourceDatabaseMeta(),
                ctx.variables(),
                List.of(ctx.satellite()),
                ctx.loadDateField()));
      }
      sql.append("), ");
    }
    if (needsHubLoadCte) {
      sql.append("earliest_hub_load AS (");
      sql.append(
          BvPitSnapshotSpineSupport.buildEarliestHubLoadSql(
              ctx.sourceDatabaseMeta(), ctx.variables(), ctx.hub(), ctx.loadDateField()));
      sql.append("), ");
    }

    sql.append("bounds AS (SELECT ");
    sql.append(buildStartDateExpression(ctx, needsSatelliteLoadCte, needsHubLoadCte));
    sql.append(" AS start_date, ");
    sql.append(buildEndDateExpression(ctx));
    sql.append(" AS end_date), ");

    sql.append(
        BvPitSnapshotSpineSupport.buildDynamicSnapshotSpineCte(
            ctx.sourceDatabaseMeta(), "snapshot_spine", "snapshot_date", "bounds", anchor));
    sql.append(", hub_keys AS (SELECT DISTINCT ");
    sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
    sql.append(" AS ");
    sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
    sql.append(" FROM ");
    sql.append(
        ctx.sourceDatabaseMeta()
            .getQuotedSchemaTableCombination(ctx.variables(), null, ctx.hubTableName()));
    sql.append(") ");

    if (BvPitSnapshotSpineSupport.resolveDialect(ctx.sourceDatabaseMeta())
        == BvPitSnapshotSpineSupport.PitSqlDialect.SINGLESTORE) {
      // SingleStore rejects correlated scalar subselects when the outer FROM is only CTEs
      // ("Scalar subselect where outer table is not a sharded table"). Use LEFT JOIN + GROUP BY.
      sql.append(buildSinglestorePitSelect(ctx));
    } else {
      sql.append("SELECT hk.");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName()));
      sql.append(", spine.");
      sql.append("snapshot_date");
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.snapshotDateField()));
      sql.append(", ");
      sql.append(buildSatellitePointerSubquery(ctx));
      sql.append(" AS ");
      sql.append(ctx.sourceDatabaseMeta().quoteField(ctx.satellitePointerColumnName()));
      sql.append(" FROM hub_keys hk CROSS JOIN snapshot_spine spine WHERE ");
      sql.append(buildIncrementalSnapshotFilter(ctx));
    }

    return sql.toString();
  }

  /**
   * SingleStore-safe final SELECT: join hub keys × spine to the satellite table and aggregate the
   * pointer load date instead of using a correlated scalar subselect.
   */
  private static String buildSinglestorePitSelect(PitBuildContext ctx) {
    String quotedHashKey = ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName());
    String quotedLoadDate = ctx.sourceDatabaseMeta().quoteField(ctx.loadDateField());
    String quotedSnapshotField = ctx.sourceDatabaseMeta().quoteField(ctx.snapshotDateField());
    String quotedPointer = ctx.sourceDatabaseMeta().quoteField(ctx.satellitePointerColumnName());
    String quotedSatelliteTable = satelliteRelation(ctx, "sat");
    String incremental = buildIncrementalSnapshotFilter(ctx);

    return "SELECT hk."
        + quotedHashKey
        + " AS "
        + quotedHashKey
        + ", spine.snapshot_date AS "
        + quotedSnapshotField
        + ", MAX(sat."
        + quotedLoadDate
        + ") AS "
        + quotedPointer
        + " FROM hub_keys hk "
        + "CROSS JOIN snapshot_spine spine "
        + "LEFT JOIN "
        + quotedSatelliteTable
        + " ON sat."
        + quotedHashKey
        + " = hk."
        + quotedHashKey
        + " AND sat."
        + quotedLoadDate
        + " <= spine.snapshot_date "
        + "WHERE "
        + incremental
        + " GROUP BY hk."
        + quotedHashKey
        + ", spine.snapshot_date";
  }

  public static PipelineMeta generatePipeline(PitBuildContext ctx) throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName());
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        pipelineMeta, ctx.bvModel(), "pit", ctx.pitTable().getName(), ctx.bvTargetTableName());

    TransformMeta watermarkParam =
        addSnapshotWatermarkParamConstant(ctx, pipelineMeta, LOCATION_WATERMARK_PARAM);
    TransformMeta tableInput = addTableInput(ctx, pipelineMeta, watermarkParam);
    if (tableInput != null) {
      GeneratedPipelineMetadataSupport.stampSourceRead(tableInput, ctx.sourceDbName());
    }
    TransformMeta writeTransform = addTableOutput(ctx, pipelineMeta, tableInput);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform,
          "pit",
          ctx.pitTable().getName(),
          ctx.bvTargetTableName(),
          ctx.targetDbName());
    }

    BvGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvPitTable pitTable)
      throws HopException {
    try {
      DbCache.clearAll();
      PitBuildContext ctx = createContext(metadataProvider, variables, bvModel, dvModel, pitTable);
      if (ctx == null) {
        return List.of();
      }
      return List.of(generatePipeline(ctx));
    } catch (Exception e) {
      throw new HopException(
          "Error generating PIT build pipeline for Business Vault table " + pitTable.getName(), e);
    }
  }

  /**
   * Generate Rows (limit 1) with the snapshot watermark. Must not use Constant alone — Constant
   * only enriches incoming rows and produces nothing without a predecessor.
   */
  private static TransformMeta addSnapshotWatermarkParamConstant(
      PitBuildContext ctx, PipelineMeta pipelineMeta, Point location) {
    RowGeneratorMeta rowGeneratorMeta = new RowGeneratorMeta();
    rowGeneratorMeta.setRowLimit("1");
    rowGeneratorMeta.setNeverEnding(false);
    rowGeneratorMeta
        .getFields()
        .add(
            new GeneratorField(
                SNAPSHOT_WATERMARK_FIELD,
                "Timestamp",
                "yyyy-MM-dd HH:mm:ss",
                -1,
                -1,
                null,
                null,
                null,
                resolveIncrementalSnapshotWatermarkValue(ctx),
                false));

    TransformMeta tm =
        new TransformMeta("RowGenerator", PARAM_SNAPSHOT_WATERMARK_TRANSFORM, rowGeneratorMeta);
    tm.setLocation(location);
    tm.setDistributes(false);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addTableInput(
      PitBuildContext ctx, PipelineMeta pipelineMeta, TransformMeta watermarkParam)
      throws HopException {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, buildPitTableInputSql(ctx));
    if (watermarkParam != null) {
      tableInputMeta.setLookup(watermarkParam.getName());
    }

    TransformMeta tm =
        new TransformMeta("TableInput", "read_" + ctx.bvTargetTableName(), tableInputMeta);
    tm.setLocation(LOCATION_TABLE_INPUT);
    pipelineMeta.addTransform(tm);
    if (watermarkParam != null) {
      watermarkParam.setDistributes(false);
      tableInputMeta.searchInfoAndTargetTransforms(pipelineMeta.getTransforms());
      pipelineMeta.addPipelineHop(new PipelineHopMeta(watermarkParam, tm));
    }
    return tm;
  }

  private static TransformMeta addTableOutput(
      PitBuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    IRowMeta targetLayout =
        BvPitLayoutSupport.buildTargetTableLayout(
            ctx.pitTable(), ctx.bvConfig(), ctx.dvModel(), ctx.variables());

    TransformMeta writePredecessor = predecessor;
    if (ctx.bvConfig() != null && ctx.bvConfig().isStoreLoadCycleId()) {
      writePredecessor =
          DvLoadCycleSupport.addConstantForLoadCycleId(
              pipelineMeta,
              predecessor,
              true,
              ctx.bvConfig().getLoadCycleIdField(),
              ctx.variables(),
              null,
              new Point(LOCATION_TABLE_OUTPUT.x - 50, LOCATION_TABLE_OUTPUT.y));
    }

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.bvConfig(),
            ctx.variables(),
            ctx.targetDatabaseMeta(),
            ctx.targetDbName(),
            ctx.bvTargetTableName(),
            ctx.pipelineName(),
            ctx.bvModel().getName(),
            LOCATION_TABLE_OUTPUT.x,
            LOCATION_TABLE_OUTPUT.y);

    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            writePredecessor,
            java.util.Collections.emptySet(),
            false);
    return result.transformMeta;
  }

  private static String satelliteRelation(PitBuildContext ctx, String alias) {
    BvSourceQuery sourceQuery =
        BusinessVaultSourceQuerySupport.resolveSourceQueries(ctx.pitTable(), ctx.bvModel()).stream()
            .findFirst()
            .orElse(null);
    if (sourceQuery != null) {
      if (sourceQuery.isSqlSource()) {
        return BvSourceQuerySqlSupport.wrapSqlAsSubquery(
            sourceQuery.getSqlQuery(), alias, ctx.sourceDatabaseMeta());
      }
      String table =
          BvSourceQuerySqlSupport.quotedTable(
              ctx.sourceDatabaseMeta(),
              ctx.variables(),
              sourceQuery.getSchemaName(),
              !Utils.isEmpty(sourceQuery.getTableName())
                  ? sourceQuery.getTableName()
                  : sourceQuery.getName());
      return table + " " + alias;
    }
    return ctx.sourceDatabaseMeta()
            .getQuotedSchemaTableCombination(ctx.variables(), null, ctx.satelliteTableName())
        + " "
        + alias;
  }

  private static String buildIncrementalSnapshotFilter(PitBuildContext ctx) {
    // Positional ? bound from param_snapshot_watermark Constant (dialect-neutral).
    return BvPitSnapshotSpineSupport.buildIncrementalSnapshotFilterSql("spine.snapshot_date");
  }

  /**
   * Queries the BV PIT table for {@code MAX(snapshot_date)} at pipeline generation time. Falls back
   * to the default sentinel when the table is missing or empty (null handling in Java keeps SQL
   * free of dialect-specific timestamp literals).
   */
  static String resolveIncrementalSnapshotWatermarkValue(PitBuildContext ctx) {
    if (ctx == null
        || ctx.targetDatabaseMeta() == null
        || Utils.isEmpty(ctx.targetDbName())
        || Utils.isEmpty(ctx.bvTargetTableName())) {
      return BvPitSnapshotSpineSupport.DEFAULT_INCREMENTAL_SENTINEL;
    }

    String sql =
        BvPitSnapshotSpineSupport.buildIncrementalSnapshotWatermarkSql(
            ctx.targetDatabaseMeta(),
            ctx.variables(),
            ctx.bvTargetTableName(),
            ctx.snapshotDateField());
    ILoggingObject loggingObject =
        new SimpleLoggingObject(
            BvPitPipelineSupport.class.getSimpleName()
                + ".resolveIncrementalSnapshotWatermarkValue",
            LoggingObjectType.GENERAL,
            null);
    try (Database db = new Database(loggingObject, ctx.variables(), ctx.targetDatabaseMeta())) {
      db.connect();
      RowMetaAndData row = db.getOneRow(sql);
      if (row == null
          || row.getData() == null
          || row.getData().length == 0
          || row.getData()[0] == null) {
        return BvPitSnapshotSpineSupport.DEFAULT_INCREMENTAL_SENTINEL;
      }
      Object value = row.getData()[0];
      if (value instanceof Timestamp timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
      }
      if (value instanceof java.util.Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
      }
      String text = String.valueOf(value);
      return Utils.isEmpty(text) ? BvPitSnapshotSpineSupport.DEFAULT_INCREMENTAL_SENTINEL : text;
    } catch (Exception e) {
      return BvPitSnapshotSpineSupport.DEFAULT_INCREMENTAL_SENTINEL;
    }
  }

  private static String buildSatellitePointerSubquery(PitBuildContext ctx) {
    String quotedHashKey = ctx.sourceDatabaseMeta().quoteField(ctx.hashKeyFieldName());
    String quotedLoadDate = ctx.sourceDatabaseMeta().quoteField(ctx.loadDateField());
    String satelliteAlias = "sat";
    String quotedSatelliteTable = satelliteRelation(ctx, satelliteAlias);
    return "(SELECT MAX("
        + satelliteAlias
        + "."
        + quotedLoadDate
        + ") FROM "
        + quotedSatelliteTable
        + " WHERE "
        + satelliteAlias
        + "."
        + quotedHashKey
        + " = hk."
        + quotedHashKey
        + " AND "
        + satelliteAlias
        + "."
        + quotedLoadDate
        + " <= spine.snapshot_date)";
  }

  private static boolean needsEarliestSatelliteLoadCte(BvPitSnapshotSchedule schedule) {
    BvPitRangeStart rangeStart =
        schedule.getRangeStart() != null
            ? schedule.getRangeStart()
            : BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
    return rangeStart == BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
  }

  private static boolean needsEarliestHubLoadCte(BvPitSnapshotSchedule schedule) {
    return schedule.getRangeStart() == BvPitRangeStart.EARLIEST_HUB_LOAD;
  }

  private static String buildStartDateExpression(
      PitBuildContext ctx, boolean needsSatelliteLoadCte, boolean needsHubLoadCte) {
    DatabaseMeta db = ctx.sourceDatabaseMeta();
    BvPitRangeStart rangeStart =
        ctx.schedule().getRangeStart() != null
            ? ctx.schedule().getRangeStart()
            : BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
    return switch (rangeStart) {
      case FIXED_DATE ->
          BvPitSnapshotSpineSupport.dateLiteral(
              db, resolveFixedDateLiteral(ctx.schedule().getRangeStartFixed(), ctx.variables()));
      case EARLIEST_PARTICIPATING_SATELLITE_LOAD ->
          needsSatelliteLoadCte
              ? "(SELECT "
                  + BvPitSnapshotSpineSupport.castToDateExpression(db, "earliest_load")
                  + " FROM earliest_satellite_load)"
              : BvPitSnapshotSpineSupport.currentDateExpression(db);
      case EARLIEST_HUB_LOAD ->
          needsHubLoadCte
              ? "(SELECT "
                  + BvPitSnapshotSpineSupport.castToDateExpression(db, "earliest_load")
                  + " FROM earliest_hub_load)"
              : BvPitSnapshotSpineSupport.currentDateExpression(db);
    };
  }

  private static String buildEndDateExpression(PitBuildContext ctx) {
    DatabaseMeta db = ctx.sourceDatabaseMeta();
    BvPitRangeEnd rangeEnd =
        ctx.schedule().getRangeEnd() != null
            ? ctx.schedule().getRangeEnd()
            : BvPitRangeEnd.NOW_MINUS_HORIZON;
    return switch (rangeEnd) {
      case NOW -> BvPitSnapshotSpineSupport.currentDateExpression(db);
      case NOW_MINUS_HORIZON ->
          BvPitSnapshotSpineSupport.currentDateMinusDaysExpression(
              db, ctx.schedule().getHorizonDays());
      case FIXED_DATE ->
          BvPitSnapshotSpineSupport.dateLiteral(
              db, resolveFixedDateLiteral(ctx.schedule().getRangeEndFixed(), ctx.variables()));
    };
  }

  private static String resolveFixedDateLiteral(String value, IVariables variables) {
    String resolved = variables != null ? variables.resolve(value) : value;
    if (Utils.isEmpty(resolved)) {
      return "1900-01-01";
    }
    String trimmed = resolved.trim();
    return trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
  }

  /** Resolved inputs for a generated PIT build pipeline. */
  public record PitBuildContext(
      BvPitTable pitTable,
      DvHub hub,
      DvSatellite satellite,
      BvPitSnapshotSchedule schedule,
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
      String bvTargetTableName,
      String pipelineName,
      String hashKeyFieldName,
      String snapshotDateField,
      String loadDateField,
      String satellitePointerColumnName,
      String hubTableName,
      String satelliteTableName) {}
}
