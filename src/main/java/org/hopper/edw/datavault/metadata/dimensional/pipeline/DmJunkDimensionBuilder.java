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
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkHashCodeStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.transform.junkdimension.JunkDimensionMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.combinationlookup.CFields;
import org.apache.hop.pipeline.transforms.combinationlookup.KeyField;
import org.apache.hop.pipeline.transforms.combinationlookup.ReturnFields;

/** Generates junk dimension pipelines using the project-local JunkDimension transform. */
public final class DmJunkDimensionBuilder {

  private DmJunkDimensionBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmJunkDimension junkDimension)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(
            metadataProvider, variables, model, junkDimension);
    if (ctx == null) {
      throw new HopException(
          "Unable to create build context for junk dimension " + junkDimension.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    addJunkDimension(ctx, pipelineMeta, sourceTransform, junkDimension, null);

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  public static TransformMeta addJunkDimension(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmJunkDimension junkDimension,
      DmFactJunkDimensionRole factRole)
      throws HopException {
    JunkDimensionMeta lookupMeta = configureMeta(ctx, junkDimension, factRole);

    String transformName =
        factRole != null
            ? "junk_" + resolve(ctx, factRole.getForeignKeyColumn())
            : "junk_" + ctx.targetTableName;
    TransformMeta tm = new TransformMeta("JunkDimension", transformName, lookupMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  static JunkDimensionMeta configureMeta(
      DmPipelineBuilderSupport.BuildContext ctx,
      DmJunkDimension junkDimension,
      DmFactJunkDimensionRole factRole) {
    JunkDimensionMeta lookupMeta = new JunkDimensionMeta();
    lookupMeta.setConnectionName(ctx.targetDbName);
    lookupMeta.setTableName(ctx.targetTableName);
    lookupMeta.setReplaceFields(factRole != null);
    lookupMeta.setCommitSize(
        Integer.parseInt(ctx.config.resolveTargetTableCommitSize(ctx.variables)));

    DmJunkHashCodeStrategy hashStrategy = junkDimension.getHashCodeStrategyOrDefault();
    lookupMeta.setHashCodeStrategy(hashStrategy.getCode());
    lookupMeta.setUseHash(hashStrategy.usesHashColumn());
    lookupMeta.setHashField(resolveHashCodeField(junkDimension, ctx.config, ctx.variables));
    lookupMeta.setUseSurrogateKeyAsHashCodeField(junkDimension.isUseSurrogateKeyAsHashCodeField());

    DmJunkSurrogateKeyStrategy surrogateStrategy =
        DmSurrogateKeySupport.resolveJunkStrategy(junkDimension);
    lookupMeta.setJunkSurrogateKeyStrategy(surrogateStrategy.getCode());
    lookupMeta.setSurrogateKeySourceField(
        resolve(
            ctx,
            DmSurrogateKeySupport.resolveJunkSurrogateKeySourceField(
                junkDimension, ctx.config, ctx.variables)));

    String technicalKeyField =
        DmSurrogateKeySupport.resolveJunkSurrogateKeyField(
            junkDimension, ctx.config, ctx.variables);
    String outputField = technicalKeyField;
    if (factRole != null && !Utils.isEmpty(factRole.getForeignKeyColumn())) {
      outputField = resolve(ctx, factRole.getForeignKeyColumn());
    }
    lookupMeta.setTechnicalKeyOutputField(outputField);

    CFields fields = new CFields();
    List<KeyField> keyFields = new ArrayList<>();
    for (DmNaturalKeyField keyField : junkDimension.getKeyFieldsOrEmpty()) {
      if (keyField == null || Utils.isEmpty(keyField.getFieldName())) {
        continue;
      }
      String field = resolve(ctx, keyField.getFieldName());
      keyFields.add(new KeyField(field, field));
    }
    fields.setKeyFields(keyFields);

    ReturnFields returnFields = new ReturnFields();
    returnFields.setTechnicalKeyField(technicalKeyField);
    returnFields.setUseAutoIncrement(
        surrogateStrategy == DmJunkSurrogateKeyStrategy.AUTO_INCREMENT);
    returnFields.setTechKeyCreation(resolveTechKeyCreation(surrogateStrategy));
    returnFields.setLastUpdateField(ctx.config.resolveLoadDateField(ctx.variables));
    fields.setReturnFields(returnFields);
    lookupMeta.setFields(fields);
    return lookupMeta;
  }

  private static String resolveTechKeyCreation(DmJunkSurrogateKeyStrategy strategy) {
    if (strategy == DmJunkSurrogateKeyStrategy.AUTO_INCREMENT) {
      return JunkDimensionMeta.CREATION_METHOD_AUTOINC;
    }
    return JunkDimensionMeta.CREATION_METHOD_TABLEMAX;
  }

  private static String resolveHashCodeField(
      DmJunkDimension junkDimension,
      DimensionalConfiguration config,
      org.apache.hop.core.variables.IVariables variables) {
    return DmJunkDimensionSupport.resolveJunkHashCodeField(junkDimension, config, variables);
  }

  private static String resolve(DmPipelineBuilderSupport.BuildContext ctx, String value) {
    return resolve(ctx.variables, value);
  }

  private static String resolve(org.apache.hop.core.variables.IVariables variables, String value) {
    if (variables != null && value != null) {
      return variables.resolve(value);
    }
    return value;
  }
}
