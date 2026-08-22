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
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateKeyField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateLookupField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;

/** Generates SCD Type 1 dimension load pipelines using Insert/Update. */
public final class DmInsertUpdateBuilder {

  private DmInsertUpdateBuilder() {}

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

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    sourceTransform =
        DmPipelineBuilderSupport.addLoadCycleConstantIfEnabled(ctx, pipelineMeta, sourceTransform);
    TransformMeta insertUpdateTransform =
        addInsertUpdate(ctx, pipelineMeta, sourceTransform, dimension);
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

  private static TransformMeta addInsertUpdate(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmDimension dimension) {
    InsertUpdateMeta insertUpdateMeta = new InsertUpdateMeta();
    insertUpdateMeta.setConnection(ctx.targetDbName);
    insertUpdateMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    InsertUpdateLookupField lookup = new InsertUpdateLookupField();
    lookup.setTableName(ctx.targetTableName);

    List<InsertUpdateKeyField> keys = new ArrayList<>();
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      keys.add(new InsertUpdateKeyField(naturalKey, naturalKey, "="));
    }
    lookup.setLookupKeys(keys);

    List<InsertUpdateValue> values = new ArrayList<>();
    for (String naturalKey :
        DmPipelineBuilderSupport.naturalKeyFieldNames(dimension, ctx.variables)) {
      values.add(new InsertUpdateValue(naturalKey, naturalKey, false));
    }
    for (String attribute :
        DmPipelineBuilderSupport.type1AttributeFieldNames(dimension, ctx.variables)) {
      values.add(new InsertUpdateValue(attribute, attribute, true));
    }
    String loadDateField = ctx.config.resolveLoadDateField(ctx.variables);
    values.add(new InsertUpdateValue(loadDateField, loadDateField, true));
    if (ctx.config.isStoreLoadCycleId()) {
      String cycleField =
          org.apache.hop.datavault.metadata.DvLoadCycleSupport.resolveFieldName(
              ctx.config.getLoadCycleIdField(), ctx.variables);
      values.add(new InsertUpdateValue(cycleField, cycleField, true));
    }
    lookup.setValueFields(values);

    insertUpdateMeta.setInsertUpdateLookupField(lookup);

    TransformMeta tm =
        new TransformMeta("InsertUpdate", "upsert_" + ctx.targetTableName, insertUpdateMeta);
    tm.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }
}
