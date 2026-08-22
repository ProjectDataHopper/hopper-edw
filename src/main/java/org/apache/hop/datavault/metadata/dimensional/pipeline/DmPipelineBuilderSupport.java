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
package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvLoadCycleSupport;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDateGeneratorConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourcePipelineSupport;
import org.apache.hop.datavault.metadata.dimensional.DmSourceRecordDefinitionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/** Shared helpers for generated dimensional load pipelines. */
public final class DmPipelineBuilderSupport {

  public static final int SPACING_WIDTH = 200;
  public static final Point LOCATION_START = new Point(100, 100);
  public static final String PREVIOUS_VERSION_FIELD = "dm_prev_version";
  public static final String PREVIOUS_VERSION_NUM_FIELD = "dm_prev_version_num";
  public static final String NEW_VERSION_FIELD_SUFFIX = "_new";

  private DmPipelineBuilderSupport() {}

  /**
   * Stream field carrying the incremented SCD2 version (avoids colliding with the merge branch).
   */
  public static String scd2NewVersionFieldName(String versionField) {
    if (Utils.isEmpty(versionField)) {
      return NEW_VERSION_FIELD_SUFFIX.substring(1);
    }
    return versionField + NEW_VERSION_FIELD_SUFFIX;
  }

  public static final class BuildContext {
    public final DmTableBase table;
    public final DimensionalModel model;
    public final DimensionalConfiguration config;
    public final DmSourceConfiguration source;
    public final IHopMetadataProvider metadataProvider;
    public final IVariables variables;
    public final DatabaseMeta targetDatabaseMeta;
    public final String targetDbName;
    public final String sourceDbName;
    public final String targetTableName;
    public final String pipelineName;

    private BuildContext(
        DmTableBase table,
        DimensionalModel model,
        DimensionalConfiguration config,
        DmSourceConfiguration source,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String sourceDbName,
        String targetTableName,
        String pipelineName) {
      this.table = table;
      this.model = model;
      this.config = config;
      this.source = source;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.sourceDbName = sourceDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
    }

    public static BuildContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DimensionalModel model,
        DmTableBase table)
        throws HopException {
      if (metadataProvider == null || model == null || table == null) {
        return null;
      }

      DimensionalConfiguration config = model.getConfigurationOrDefault();
      DmSourceConfiguration source = table.getSourceOrDefault();
      if (source.isSqlSource()) {
        String sourceSql = source.resolveSourceSql(variables);
        if (Utils.isEmpty(sourceSql)) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires source SQL for pipeline generation");
        }
      } else if (source.isPipelineSource()) {
        if (Utils.isEmpty(source.resolveSourcePipelineFile(variables))) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires a source pipeline file for pipeline generation");
        }
        if (Utils.isEmpty(source.resolveSourcePipelineTransform(variables))) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires a source pipeline transform for pipeline generation");
        }
      } else if (source.isRecordDefinitionSource()) {
        if (Utils.isEmpty(source.resolveSourceRecordNamespace(variables))
            || Utils.isEmpty(source.resolveSourceRecordName(variables))) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires a record definition namespace and name for pipeline generation");
        }
        DmSourceRecordDefinitionSupport.resolveCatalogConnection(
            config, source, variables, metadataProvider);
      } else if (source.isDateGeneratorSource()) {
        DmDateGeneratorConfiguration generator =
            source.getDateGenerator() != null
                ? source.getDateGenerator()
                : DmDateGeneratorConfiguration.createDefault();
        boolean hasField = false;
        for (var field : generator.getFieldsOrEmpty()) {
          if (field != null && !Utils.isEmpty(field.getName())) {
            hasField = true;
            break;
          }
        }
        if (!hasField) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires date generator output fields for pipeline generation");
        }
        if (Utils.isEmpty(generator.getStartDate()) || Utils.isEmpty(generator.getEndDate())) {
          throw new HopException(
              "Dimensional table "
                  + table.getName()
                  + " requires date generator start and end dates for pipeline generation");
        }
      } else {
        throw new HopException(
            "Dimensional table " + table.getName() + " has an unsupported source type");
      }

      DatabaseMeta targetDatabaseMeta =
          DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
      if (targetDatabaseMeta == null) {
        throw new HopException(
            "Dimensional model target database is not configured for table " + table.getName());
      }

      String targetDbName = config.getTargetDatabase();
      String sourceDbName = source.resolveSourceConnection(config, variables);
      if (Utils.isEmpty(sourceDbName)) {
        sourceDbName = targetDbName;
      }

      String targetTableName =
          !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
      String pipelineName = resolvePipelineName(config, variables, table, targetTableName);

      return new BuildContext(
          table,
          model,
          config,
          source,
          metadataProvider,
          variables,
          targetDatabaseMeta,
          targetDbName,
          sourceDbName,
          targetTableName,
          pipelineName);
    }
  }

  public static String resolveFactDimensionLookupDateField(BuildContext ctx) {
    if (ctx == null || ctx.table == null) {
      return null;
    }
    String lookupDateField = ctx.table.getDimensionLookupDateField();
    if (ctx.variables != null) {
      lookupDateField = ctx.variables.resolve(lookupDateField);
    }
    return lookupDateField;
  }

  public static TransformMeta addSourceInput(BuildContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    TransformMeta sourceTransform;
    if (ctx.source.isPipelineSource()) {
      sourceTransform = addSourceMetaInject(ctx, pipelineMeta);
    } else if (ctx.source.isRecordDefinitionSource()) {
      sourceTransform = addSourceRecordDefinition(ctx, pipelineMeta);
    } else if (ctx.source.isDateGeneratorSource()) {
      sourceTransform = addSourceDateGenerator(ctx, pipelineMeta);
    } else {
      sourceTransform = addSourceTableInput(ctx, pipelineMeta);
    }
    if (sourceTransform != null) {
      GeneratedPipelineMetadataSupport.stampSourceRead(sourceTransform, ctx.sourceDbName);
    }
    return sourceTransform;
  }

  public static TransformMeta addSourceDateGenerator(BuildContext ctx, PipelineMeta pipelineMeta) {
    DmDateGeneratorConfiguration config =
        ctx.source.getDateGenerator() != null
            ? ctx.source.getDateGenerator()
            : DmDateGeneratorConfiguration.createDefault();
    String loadDateField =
        ctx.config != null ? ctx.config.resolveLoadDateField(ctx.variables) : null;
    DateDimensionGeneratorMeta generatorMeta = config.toTransformMeta(loadDateField);
    TransformMeta tm =
        new TransformMeta("DateDimensionGenerator", "source_" + ctx.targetTableName, generatorMeta);
    tm.setLocation(LOCATION_START.x, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  public static TransformMeta addSourceRecordDefinition(BuildContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    return DmSourceRecordDefinitionSupport.appendSourceInput(
        ctx.source,
        ctx.config,
        pipelineMeta,
        LOCATION_START,
        "source_" + ctx.targetTableName,
        ctx.variables,
        ctx.metadataProvider);
  }

  public static TransformMeta addSourceTableInput(BuildContext ctx, PipelineMeta pipelineMeta) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(ctx.sourceDbName);
    DvSqlSupport.assignDisplaySql(tableInputMeta, ctx.source.resolveSourceSql(ctx.variables));

    TransformMeta tm =
        new TransformMeta("TableInput", "source_" + ctx.targetTableName, tableInputMeta);
    tm.setLocation(LOCATION_START.x, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  public static TransformMeta addSourceMetaInject(BuildContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    MetaInjectMeta metaInjectMeta =
        DmSourcePipelineSupport.buildMetaInjectMeta(
            ctx.source, ctx.variables, ctx.metadataProvider);
    TransformMeta tm =
        new TransformMeta("MetaInject", "source_" + ctx.targetTableName, metaInjectMeta);
    tm.setLocation(LOCATION_START.x, LOCATION_START.y);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  public static TransformMeta addTableOutput(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate)
      throws HopException {
    return addTableOutput(
        ctx, pipelineMeta, targetLayout, predecessor, truncate, Collections.emptySet());
  }

  public static TransformMeta addTableOutput(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate,
      Set<String> excludeFields)
      throws HopException {
    return addTargetLoad(ctx, pipelineMeta, targetLayout, predecessor, truncate, excludeFields);
  }

  public static TransformMeta addTargetLoad(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate)
      throws HopException {
    return addTargetLoad(
        ctx, pipelineMeta, targetLayout, predecessor, truncate, Collections.emptySet());
  }

  public static TransformMeta addTargetLoad(
      BuildContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      boolean truncate,
      Set<String> excludeFields)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    TransformMeta writePredecessor = addLoadCycleConstantIfEnabled(ctx, pipelineMeta, predecessor);

    DvTargetLoadSupport.TargetLoadContext targetCtx =
        new DvTargetLoadSupport.TargetLoadContext(
            ctx.config,
            ctx.variables,
            ctx.targetDatabaseMeta,
            ctx.targetDbName,
            ctx.targetTableName,
            ctx.pipelineName,
            ctx.model.getName(),
            writePredecessor.getLocation().x + SPACING_WIDTH,
            writePredecessor.getLocation().y);

    Set<String> resolvedExcludeFields =
        excludeFields != null ? excludeFields : Collections.emptySet();
    DvTargetLoadSupport.TargetLoadResult result =
        DvTargetLoadSupport.addTargetLoad(
            targetCtx,
            pipelineMeta,
            targetLayout,
            writePredecessor,
            resolvedExcludeFields,
            truncate);
    if (result != null && result.transformMeta != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          result.transformMeta,
          GeneratedPipelineMetadataSupport.getPipelineAttribute(
              pipelineMeta, GeneratedPipelineMetadataConstants.ELEMENT_TYPE),
          ctx.table != null ? ctx.table.getName() : null,
          ctx.targetTableName,
          ctx.targetDbName);
    }
    return result.transformMeta;
  }

  public static TransformMeta addLoadCycleConstantIfEnabled(
      BuildContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) throws HopException {
    if (ctx == null
        || ctx.config == null
        || !ctx.config.isStoreLoadCycleId()
        || predecessor == null) {
      return predecessor;
    }
    Point location =
        predecessor.getLocation() != null
            ? new Point(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y)
            : null;
    return DvLoadCycleSupport.addConstantForLoadCycleId(
        pipelineMeta,
        predecessor,
        true,
        ctx.config.getLoadCycleIdField(),
        ctx.variables,
        null,
        location);
  }

  public static TransformMeta addDummyTransform(
      PipelineMeta pipelineMeta, TransformMeta predecessor, String name, int x, int y) {
    if (predecessor == null) {
      return null;
    }
    TransformMeta tm = new TransformMeta("Dummy", name, new DummyMeta());
    tm.setLocation(x, y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static String resolvePipelineName(
      DimensionalConfiguration config,
      IVariables variables,
      DmTableBase table,
      String targetTableName) {
    return switch (table.getTableType()) {
      case DIMENSION -> config.buildDimensionPipelineName(variables, targetTableName);
      case DIMENSION_ALIAS -> config.buildDimensionPipelineName(variables, targetTableName);
      case JUNK_DIMENSION -> config.buildJunkDimensionPipelineName(variables, targetTableName);
      case RANGE_DIMENSION -> targetTableName;
      case BRIDGE -> config.buildBridgePipelineName(variables, targetTableName);
      case FACT,
              FACTLESS_FACT,
              PERIODIC_SNAPSHOT_FACT,
              ACCUMULATING_SNAPSHOT_FACT,
              AGGREGATE_FACT ->
          config.buildFactPipelineName(variables, targetTableName);
    };
  }

  public static List<String> naturalKeyFieldNamesFromList(
      List<org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField> naturalKeys,
      IVariables variables) {
    List<String> names = new ArrayList<>();
    if (naturalKeys == null) {
      return names;
    }
    for (var key : naturalKeys) {
      if (key == null || Utils.isEmpty(key.getFieldName())) {
        continue;
      }
      String field = key.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }

  public static List<String> naturalKeyFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var key : dimension.getNaturalKeysOrEmpty()) {
      if (key == null || Utils.isEmpty(key.getFieldName())) {
        continue;
      }
      String field = key.getFieldName();
      if (variables != null) {
        field = variables.resolve(field);
      }
      names.add(field);
    }
    return names;
  }

  public static List<String> type1AttributeFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      if (attribute.getScdUpdatePolicy()
          == org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy.TYPE2) {
        continue;
      }
      String field = DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, variables);
      if (!Utils.isEmpty(field)) {
        names.add(field);
      }
    }
    return names;
  }

  public static List<String> scd2AttributeFieldNames(
      org.apache.hop.datavault.metadata.dimensional.DmDimension dimension, IVariables variables) {
    List<String> names = new ArrayList<>();
    for (var attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      String field = DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, variables);
      if (!Utils.isEmpty(field)) {
        names.add(field);
      }
    }
    return names;
  }

  /** Field names and order required on both Merge Rows inputs for SCD2 dimensions. */
  public static List<String> scd2MergeRowFieldNames(BuildContext ctx, DmDimension dimension) {
    List<String> fields = new ArrayList<>();
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String surrogateField =
          DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, ctx.config, ctx.variables);
      if (!Utils.isEmpty(surrogateField)) {
        fields.add(surrogateField);
      }
    }
    fields.addAll(scd2AttributeFieldNames(dimension, ctx.variables));
    fields.addAll(naturalKeyFieldNames(dimension, ctx.variables));
    fields.add(ctx.config.resolveVersionField(ctx.variables));
    fields.add(ctx.config.resolveDateFromField(ctx.variables));
    fields.add(ctx.config.resolveDateToField(ctx.variables));
    return fields;
  }

  /** Sort keys for SCD2 source collapse: natural keys, attributes, then optional effective date. */
  public static List<String> scd2CollapseSortFieldNames(BuildContext ctx, DmDimension dimension) {
    List<String> fields = new ArrayList<>();
    fields.addAll(naturalKeyFieldNames(dimension, ctx.variables));
    fields.addAll(scd2AttributeFieldNames(dimension, ctx.variables));
    String effectiveSource = dimension.resolveEffectiveDateSourceField(ctx.variables);
    if (!Utils.isEmpty(effectiveSource) && !fields.contains(effectiveSource)) {
      fields.add(effectiveSource);
    }
    return fields;
  }

  /** Fields aggregated with LAST_INCL_NULL when collapsing duplicate natural keys. */
  public static List<String> scd2CollapseAggregateFieldNames(
      BuildContext ctx, DmDimension dimension) {
    List<String> fields = new ArrayList<>();
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String surrogateSource =
          DmSurrogateKeySupport.resolveSurrogateKeySourceField(
              dimension, ctx.config, ctx.variables);
      if (!Utils.isEmpty(surrogateSource)) {
        fields.add(surrogateSource);
      }
    }
    for (String attribute : scd2AttributeFieldNames(dimension, ctx.variables)) {
      if (!fields.contains(attribute)) {
        fields.add(attribute);
      }
    }
    String effectiveSource = dimension.resolveEffectiveDateSourceField(ctx.variables);
    if (!Utils.isEmpty(effectiveSource) && !fields.contains(effectiveSource)) {
      fields.add(effectiveSource);
    }
    return fields;
  }

  /** Source stream column name on the compare branch for a merge-layout target field. */
  public static String scd2MergeCompareSourceFieldName(
      BuildContext ctx, DmDimension dimension, String targetFieldName) {
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        == DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      String surrogateTarget =
          DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, ctx.config, ctx.variables);
      if (!Utils.isEmpty(surrogateTarget) && surrogateTarget.equals(targetFieldName)) {
        return DmSurrogateKeySupport.resolveSurrogateKeySourceField(
            dimension, ctx.config, ctx.variables);
      }
    }
    return targetFieldName;
  }
}
