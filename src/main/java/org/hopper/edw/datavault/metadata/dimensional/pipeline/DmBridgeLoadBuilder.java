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
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateKeyField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateLookupField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;

/** Generates bridge table load pipelines using Insert/Update on dimension key combinations. */
public final class DmBridgeLoadBuilder {

  private DmBridgeLoadBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmBridge bridge)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, bridge);
    if (ctx == null) {
      throw new HopException("Unable to create build context for bridge " + bridge.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    TransformMeta sourceTransform = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    TransformMeta writeTransform = addInsertUpdate(ctx, pipelineMeta, sourceTransform, bridge);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform, "bridge", bridge.getName(), ctx.targetTableName, ctx.targetDbName);
    }

    DmGeneratedPipelineSupport.applyLayout(pipelineMeta);
    return pipelineMeta;
  }

  private static TransformMeta addInsertUpdate(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmBridge bridge) {
    InsertUpdateMeta insertUpdateMeta = new InsertUpdateMeta();
    insertUpdateMeta.setConnection(ctx.targetDbName);
    insertUpdateMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    InsertUpdateLookupField lookup = new InsertUpdateLookupField();
    lookup.setTableName(ctx.targetTableName);

    List<InsertUpdateKeyField> keys = new ArrayList<>();
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null || Utils.isEmpty(ref.getForeignKeyColumn())) {
        continue;
      }
      String fkColumn = ref.getForeignKeyColumn();
      if (ctx.variables != null) {
        fkColumn = ctx.variables.resolve(fkColumn);
      }
      keys.add(new InsertUpdateKeyField(fkColumn, fkColumn, "="));
    }
    lookup.setLookupKeys(keys);

    List<InsertUpdateValue> values = new ArrayList<>();
    String weightField = bridge.getWeightField();
    if (ctx.variables != null) {
      weightField = ctx.variables.resolve(weightField);
    }
    if (!Utils.isEmpty(weightField)) {
      values.add(new InsertUpdateValue(weightField, weightField, true));
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
