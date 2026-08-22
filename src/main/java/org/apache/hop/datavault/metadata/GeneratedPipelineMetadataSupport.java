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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmPipelineBuilderSupport;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Stamps logical model context onto generated pipeline and transform metadata attributes. */
public final class GeneratedPipelineMetadataSupport {

  private GeneratedPipelineMetadataSupport() {}

  public record PipelineContext(
      String modelType,
      String modelName,
      String modelFilename,
      String elementType,
      String elementName,
      String targetTable,
      String sourceName,
      String pipelineName) {}

  public record TransformContext(
      String logicalRole,
      String elementType,
      String elementName,
      String parentElementName,
      String physicalTable,
      String connection,
      String lookupCacheMode) {}

  public static void stampPipeline(PipelineMeta pipeline, PipelineContext ctx) {
    if (pipeline == null || ctx == null) {
      return;
    }
    setPipelineAttribute(pipeline, GeneratedPipelineMetadataConstants.MODEL_TYPE, ctx.modelType());
    setPipelineAttribute(pipeline, GeneratedPipelineMetadataConstants.MODEL_NAME, ctx.modelName());
    setPipelineAttribute(
        pipeline, GeneratedPipelineMetadataConstants.MODEL_FILENAME, ctx.modelFilename());
    setPipelineAttribute(
        pipeline, GeneratedPipelineMetadataConstants.ELEMENT_TYPE, ctx.elementType());
    setPipelineAttribute(
        pipeline, GeneratedPipelineMetadataConstants.ELEMENT_NAME, ctx.elementName());
    setPipelineAttribute(
        pipeline, GeneratedPipelineMetadataConstants.TARGET_TABLE, ctx.targetTable());
    setPipelineAttribute(
        pipeline, GeneratedPipelineMetadataConstants.SOURCE_NAME, ctx.sourceName());
    setPipelineAttribute(
        pipeline,
        GeneratedPipelineMetadataConstants.PIPELINE_NAME,
        Utils.isEmpty(ctx.pipelineName()) ? pipeline.getName() : ctx.pipelineName());
  }

  public static void stampTransform(TransformMeta transform, TransformContext ctx) {
    if (transform == null || ctx == null) {
      return;
    }
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.LOGICAL_ROLE, ctx.logicalRole());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.ELEMENT_TYPE, ctx.elementType());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.ELEMENT_NAME, ctx.elementName());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.PARENT_ELEMENT_NAME, ctx.parentElementName());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.PHYSICAL_TABLE, ctx.physicalTable());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.CONNECTION, ctx.connection());
    setTransformAttribute(
        transform, GeneratedPipelineMetadataConstants.LOOKUP_CACHE_MODE, ctx.lookupCacheMode());
  }

  public static void stampDvHubPipeline(
      PipelineMeta pipeline,
      DataVaultModel model,
      DvHub hub,
      String targetTable,
      String sourceName) {
    stampDvElementPipeline(
        pipeline, model, "hub", hub != null ? hub.getName() : null, targetTable, sourceName);
  }

  public static void stampDvSatellitePipeline(
      PipelineMeta pipeline,
      DataVaultModel model,
      DvSatellite satellite,
      String targetTable,
      String sourceName) {
    stampDvElementPipeline(
        pipeline,
        model,
        "satellite",
        satellite != null ? satellite.getName() : null,
        targetTable,
        sourceName);
  }

  public static void stampDvLinkPipeline(
      PipelineMeta pipeline,
      DataVaultModel model,
      DvLink link,
      String targetTable,
      String sourceName) {
    stampDvElementPipeline(
        pipeline, model, "link", link != null ? link.getName() : null, targetTable, sourceName);
  }

  public static void stampBvElementPipeline(
      PipelineMeta pipeline,
      BusinessVaultModel model,
      String elementType,
      String elementName,
      String targetTable) {
    if (pipeline == null) {
      return;
    }
    stampPipeline(
        pipeline,
        new PipelineContext(
            GeneratedPipelineMetadataConstants.MODEL_TYPE_BV,
            model != null ? model.getName() : null,
            model != null ? model.getFilename() : null,
            elementType,
            elementName,
            targetTable,
            "",
            pipeline.getName()));
  }

  public static void stampDvElementPipeline(
      PipelineMeta pipeline,
      DataVaultModel model,
      String elementType,
      String elementName,
      String targetTable,
      String sourceName) {
    if (pipeline == null) {
      return;
    }
    stampPipeline(
        pipeline,
        new PipelineContext(
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            model != null ? model.getName() : null,
            model != null ? model.getFilename() : null,
            elementType,
            elementName,
            targetTable,
            sourceName,
            pipeline.getName()));
  }

  public static void stampElementRole(
      TransformMeta transform,
      String elementType,
      String elementName,
      String targetTable,
      String connection,
      String logicalRole) {
    stampTransform(
        transform,
        new TransformContext(
            logicalRole, elementType, elementName, null, targetTable, connection, null));
  }

  public static void stampSort(
      TransformMeta transform, String elementType, String elementName, String targetTable) {
    stampElementRole(
        transform,
        elementType,
        elementName,
        targetTable,
        null,
        GeneratedPipelineMetadataConstants.ROLE_SORT);
  }

  public static void stampCdcMerge(
      TransformMeta transform,
      String elementType,
      String elementName,
      String targetTable,
      String connection) {
    stampElementRole(
        transform,
        elementType,
        elementName,
        targetTable,
        connection,
        GeneratedPipelineMetadataConstants.ROLE_CDC_MERGE);
  }

  public static void stampHashKey(
      TransformMeta transform, String elementType, String elementName, String targetTable) {
    stampElementRole(
        transform,
        elementType,
        elementName,
        targetTable,
        null,
        GeneratedPipelineMetadataConstants.ROLE_HASH_KEY);
  }

  public static void stampFilter(
      TransformMeta transform, String elementType, String elementName, String targetTable) {
    stampElementRole(
        transform,
        elementType,
        elementName,
        targetTable,
        null,
        GeneratedPipelineMetadataConstants.ROLE_FILTER);
  }

  public static void stampDmTablePipeline(
      PipelineMeta pipeline, DmPipelineBuilderSupport.BuildContext ctx) {
    if (pipeline == null || ctx == null || ctx.table == null) {
      return;
    }
    DmTableBase table = ctx.table;
    stampPipeline(
        pipeline,
        new PipelineContext(
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DM,
            ctx.model != null ? ctx.model.getName() : null,
            ctx.model != null ? ctx.model.getFilename() : null,
            mapDmElementType(table.getTableType()),
            table.getName(),
            ctx.targetTableName,
            "",
            ctx.pipelineName));
  }

  public static void stampSourceRead(TransformMeta transform, String connection) {
    stampTransform(
        transform,
        new TransformContext(
            GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ,
            null,
            null,
            null,
            null,
            connection,
            null));
  }

  public static void stampTargetRead(
      TransformMeta transform,
      String elementType,
      String elementName,
      String physicalTable,
      String connection) {
    stampTransform(
        transform,
        new TransformContext(
            GeneratedPipelineMetadataConstants.ROLE_TARGET_READ,
            elementType,
            elementName,
            null,
            physicalTable,
            connection,
            null));
  }

  public static void stampWriteTarget(
      TransformMeta transform,
      String elementType,
      String elementName,
      String physicalTable,
      String connection) {
    stampTransform(
        transform,
        new TransformContext(
            GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET,
            elementType,
            elementName,
            null,
            physicalTable,
            connection,
            null));
  }

  public static void stampDimensionLookup(
      TransformMeta transform,
      DmPipelineBuilderSupport.BuildContext ctx,
      DmDimension dimension,
      DmFactDimensionRole role) {
    if (transform == null || ctx == null || dimension == null || role == null) {
      return;
    }
    String dimName = dimension.getName();
    String physicalTable =
        !Utils.isEmpty(dimension.getTableName()) ? dimension.getTableName() : dimName;
    String cacheMode =
        role.isPreloadLookupCache()
            ? GeneratedPipelineMetadataConstants.LOOKUP_CACHE_PRELOAD
            : GeneratedPipelineMetadataConstants.LOOKUP_CACHE_DATABASE;
    stampTransform(
        transform,
        new TransformContext(
            GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP,
            "dimension",
            dimName,
            ctx.table != null ? ctx.table.getName() : null,
            physicalTable,
            ctx.targetDbName,
            cacheMode));
  }

  public static String getPipelineAttribute(PipelineMeta pipeline, String key) {
    if (pipeline == null || Utils.isEmpty(key)) {
      return null;
    }
    return pipeline.getAttribute(GeneratedPipelineMetadataConstants.NAMESPACE, key);
  }

  public static String getTransformAttribute(TransformMeta transform, String key) {
    if (transform == null || Utils.isEmpty(key)) {
      return null;
    }
    return transform.getAttribute(GeneratedPipelineMetadataConstants.NAMESPACE, key);
  }

  private static void setPipelineAttribute(PipelineMeta pipeline, String key, String value) {
    if (!Utils.isEmpty(value)) {
      pipeline.setAttribute(GeneratedPipelineMetadataConstants.NAMESPACE, key, value);
    }
  }

  private static void setTransformAttribute(TransformMeta transform, String key, String value) {
    if (!Utils.isEmpty(value)) {
      transform.setAttribute(GeneratedPipelineMetadataConstants.NAMESPACE, key, value);
    }
  }

  private static String mapDmElementType(DmTableType tableType) {
    if (tableType == null) {
      return "table";
    }
    return switch (tableType) {
      case DIMENSION -> "dimension";
      case DIMENSION_ALIAS -> "dimension_alias";
      case JUNK_DIMENSION -> "junk_dimension";
      case RANGE_DIMENSION -> "range_dimension";
      case FACT -> "fact";
      case FACTLESS_FACT -> "factless_fact";
      case PERIODIC_SNAPSHOT_FACT -> "periodic_snapshot_fact";
      case ACCUMULATING_SNAPSHOT_FACT -> "accumulating_snapshot_fact";
      case BRIDGE -> "bridge";
      case AGGREGATE_FACT -> "aggregate_fact";
    };
  }
}
