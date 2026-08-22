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
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmLayoutSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;

/** Builds Dimension Lookup transforms for fact FK resolution and hybrid / Type 3 dimensions. */
public final class DmDimensionLookupBuilder {

  private DmDimensionLookupBuilder() {}

  public static PipelineMeta generateHybridDimensionPipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmDimension dimension)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, dimension);
    if (ctx == null) {
      throw new HopException(
          "Unable to create build context for hybrid dimension " + dimension.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    TransformMeta lookupTransform =
        addHybridDimensionLookup(ctx, pipelineMeta, sourceTransform, dimension);
    if (lookupTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          lookupTransform, "dimension", dimension.getName(), ctx.targetTableName, ctx.targetDbName);
    }

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  public static TransformMeta addFactDimensionLookup(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension,
      DmFactDimensionRole role) {
    if (predecessor == null || dimension == null || role == null) {
      return null;
    }

    DimensionLookupMeta lookupMeta = new DimensionLookupMeta();
    lookupMeta.setConnection(ctx.targetDbName);
    lookupMeta.setTableName(
        !Utils.isEmpty(dimension.getTableName()) ? dimension.getTableName() : dimension.getName());
    lookupMeta.setUpdate(false);
    lookupMeta.setPreloadingCache(role.isPreloadLookupCache());
    lookupMeta.setFields(buildLookupOnlyFields(ctx, dimension, role));
    configureTechnicalKeySource(lookupMeta, dimension, ctx);

    String transformName = DmFactDimensionJoinBuilder.resolveLookupTransformName(role, dimension);
    TransformMeta tm = new TransformMeta("DimensionLookup", transformName, lookupMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    GeneratedPipelineMetadataSupport.stampDimensionLookup(tm, ctx, dimension, role);
    return tm;
  }

  private static TransformMeta addHybridDimensionLookup(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    DimensionLookupMeta lookupMeta = new DimensionLookupMeta();
    lookupMeta.setConnection(ctx.targetDbName);
    lookupMeta.setTableName(ctx.targetTableName);
    lookupMeta.setUpdate(true);
    lookupMeta.setFields(buildHybridFields(ctx, dimension));
    configureTechnicalKeySource(lookupMeta, dimension, ctx);
    lookupMeta.setCommitSize(
        Integer.parseInt(ctx.config.resolveTargetTableCommitSize(ctx.variables)));

    TransformMeta tm =
        new TransformMeta("DimensionLookup", "dim_update_" + ctx.targetTableName, lookupMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static DimensionLookupMeta.DLFields buildLookupOnlyFields(
      DmPipelineBuilderSupport.BuildContext ctx, DmDimension dimension, DmFactDimensionRole role) {
    DimensionLookupMeta.DLFields dlFields = new DimensionLookupMeta.DLFields();
    dlFields.setKeys(DmFactDimensionJoinBuilder.buildFactLookupKeys(dimension, role, ctx));

    DimensionalConfiguration config = ctx.config;
    String dimKeyField =
        DmLayoutSupport.resolveDimensionLookupKeyField(dimension, config, ctx.variables);
    String fkColumn =
        DmLayoutSupport.defaultFactForeignKeyColumn(dimension, role, config, ctx.variables);

    DimensionLookupMeta.DLReturn returns = new DimensionLookupMeta.DLReturn();
    returns.setKeyField(dimKeyField);
    returns.setKeyRename(fkColumn);
    applySurrogateKeyCreation(returns, dimension);
    dlFields.setReturns(returns);
    configureFactLookupDate(dlFields, ctx, dimension);
    return dlFields;
  }

  private static void configureFactLookupDate(
      DimensionLookupMeta.DLFields dlFields,
      DmPipelineBuilderSupport.BuildContext ctx,
      DmDimension dimension) {
    if (ctx == null || ctx.table == null || dimension == null) {
      return;
    }
    String lookupDateField = DmPipelineBuilderSupport.resolveFactDimensionLookupDateField(ctx);
    if (Utils.isEmpty(lookupDateField)
        || !DmLayoutSupport.dimensionUsesEffectivityLookup(dimension)) {
      return;
    }
    DimensionLookupMeta.DLDate date = new DimensionLookupMeta.DLDate();
    date.setName(lookupDateField);
    date.setFrom(ctx.config.resolveDateFromField(ctx.variables));
    date.setTo(ctx.config.resolveDateToField(ctx.variables));
    dlFields.setDate(date);
  }

  private static DimensionLookupMeta.DLFields buildHybridFields(
      DmPipelineBuilderSupport.BuildContext ctx, DmDimension dimension) {
    DimensionLookupMeta.DLFields dlFields = new DimensionLookupMeta.DLFields();
    dlFields.setKeys(buildNaturalKeys(dimension, ctx));

    DimensionalConfiguration config = ctx.config;
    DimensionLookupMeta.DLDate date = new DimensionLookupMeta.DLDate();
    date.setName(config.resolveLoadDateField(ctx.variables));
    date.setFrom(config.resolveDateFromField(ctx.variables));
    date.setTo(config.resolveDateToField(ctx.variables));
    dlFields.setDate(date);

    DimensionLookupMeta.DLReturn returns = new DimensionLookupMeta.DLReturn();
    returns.setKeyField(
        DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, ctx.variables));
    returns.setVersionField(config.resolveVersionField(ctx.variables));
    applySurrogateKeyCreation(returns, dimension);
    dlFields.setReturns(returns);

    List<DimensionLookupMeta.DLField> attributeFields = new ArrayList<>();
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      DmScdUpdatePolicy policy = DmDimensionLoadStrategySupport.resolveEffectivePolicy(attribute);
      if (policy == DmScdUpdatePolicy.TYPE3_PREVIOUS) {
        DimensionLookupMeta.DLField previousField = new DimensionLookupMeta.DLField();
        previousField.setLookup(DmLayoutSupport.resolvePreviousFieldName(attribute, ctx.variables));
        previousField.setUpdate(DimensionLookupMeta.DimensionUpdateType.LAST_VERSION.getCode());
        attributeFields.add(previousField);
        continue;
      }
      String sourceField =
          DmDimensionLoadStrategySupport.resolveSourceFieldName(attribute, ctx.variables);
      String targetField =
          DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, ctx.variables);
      DimensionLookupMeta.DLField dlField = new DimensionLookupMeta.DLField();
      dlField.setName(sourceField);
      dlField.setLookup(targetField);
      dlField.setUpdate(resolveUpdateType(attribute).getCode());
      attributeFields.add(dlField);
    }

    DimensionLookupMeta.DLField currentFlag = new DimensionLookupMeta.DLField();
    currentFlag.setLookup(config.resolveCurrentFlagField(ctx.variables));
    currentFlag.setUpdate(DimensionLookupMeta.DimensionUpdateType.LAST_VERSION.getCode());
    attributeFields.add(currentFlag);

    dlFields.setFields(attributeFields);
    return dlFields;
  }

  private static List<DimensionLookupMeta.DLKey> buildNaturalKeys(
      DmDimension dimension, DmPipelineBuilderSupport.BuildContext ctx) {
    List<DimensionLookupMeta.DLKey> keys = new ArrayList<>();
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      DimensionLookupMeta.DLKey key = new DimensionLookupMeta.DLKey();
      key.setName(naturalKey);
      key.setLookup(naturalKey);
      keys.add(key);
    }
    return keys;
  }

  private static void applySurrogateKeyCreation(
      DimensionLookupMeta.DLReturn returns, DmDimension dimension) {
    DmSurrogateKeyStrategy strategy = DmSurrogateKeySupport.resolveStrategy(dimension);
    returns.setCreationMethod(strategy.toDimensionLookupCreationMethod());
  }

  private static void configureTechnicalKeySource(
      DimensionLookupMeta lookupMeta,
      DmDimension dimension,
      DmPipelineBuilderSupport.BuildContext ctx) {
    if (DmSurrogateKeySupport.resolveStrategy(dimension)
        != DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      return;
    }
    lookupMeta.setTkSourceField(
        DmSurrogateKeySupport.resolveSurrogateKeySourceField(dimension, ctx.config, ctx.variables));
  }

  private static DimensionLookupMeta.DimensionUpdateType resolveUpdateType(
      DmDimensionAttribute attribute) {
    DmScdUpdatePolicy policy = DmDimensionLoadStrategySupport.resolveEffectivePolicy(attribute);
    return switch (policy) {
      case TYPE1, TYPE3_CURRENT -> DimensionLookupMeta.DimensionUpdateType.UPDATE;
      case TYPE1_PUNCH_THROUGH -> DimensionLookupMeta.DimensionUpdateType.PUNCH_THROUGH;
      case TYPE2 -> DimensionLookupMeta.DimensionUpdateType.INSERT;
      case TYPE3_PREVIOUS -> DimensionLookupMeta.DimensionUpdateType.LAST_VERSION;
    };
  }
}
