/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmAccumulatingSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateKeyField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateLookupField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;

/** Generates accumulating snapshot fact pipelines with dimension lookups and Insert/Update. */
public final class DmAccumulatingSnapshotLoadBuilder {

  private DmAccumulatingSnapshotLoadBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmAccumulatingSnapshotFact fact)
      throws HopException {
    DmPipelineBuilderSupport.BuildContext ctx =
        DmPipelineBuilderSupport.BuildContext.create(metadataProvider, variables, model, fact);
    if (ctx == null) {
      throw new HopException(
          "Unable to create build context for accumulating snapshot " + fact.getName());
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(ctx.pipelineName);
    GeneratedPipelineMetadataSupport.stampDmTablePipeline(pipelineMeta, ctx);

    TransformMeta predecessor = DmPipelineBuilderSupport.addSourceInput(ctx, pipelineMeta);
    try {
      predecessor =
          DmNumberRangeBuilder.wireRangeDimensionRoles(
              ctx, pipelineMeta, predecessor, model, fact.getRangeDimensionRolesOrEmpty());
      predecessor =
          DmFactJunkDimensionJoinBuilder.wireJunkDimensionRoles(
              ctx, pipelineMeta, predecessor, model, fact.getJunkDimensionRolesOrEmpty());
      predecessor =
          DmFactDimensionJoinBuilder.wireDimensionRoles(
              ctx,
              pipelineMeta,
              predecessor,
              model,
              metadataProvider,
              fact.getDimensionRolesOrEmpty());
    } catch (HopException e) {
      throw new HopException("Accumulating snapshot " + fact.getName() + ": " + e.getMessage(), e);
    }

    TransformMeta writeTransform = addInsertUpdate(ctx, pipelineMeta, predecessor, fact);
    if (writeTransform != null) {
      GeneratedPipelineMetadataSupport.stampWriteTarget(
          writeTransform,
          "accumulating_snapshot_fact",
          fact.getName(),
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
      DmAccumulatingSnapshotFact fact) {
    InsertUpdateMeta insertUpdateMeta = new InsertUpdateMeta();
    insertUpdateMeta.setConnection(ctx.targetDbName);
    insertUpdateMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

    InsertUpdateLookupField lookup = new InsertUpdateLookupField();
    lookup.setTableName(ctx.targetTableName);

    List<InsertUpdateKeyField> keys = new ArrayList<>();
    for (String grainKey :
        DmPipelineBuilderSupport.naturalKeyFieldNamesFromList(
            fact.getGrainKeysOrEmpty(), ctx.variables)) {
      keys.add(new InsertUpdateKeyField(grainKey, grainKey, "="));
    }
    lookup.setLookupKeys(keys);

    List<InsertUpdateValue> values = new ArrayList<>();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getForeignKeyColumn())) {
        continue;
      }
      String fkColumn = role.getForeignKeyColumn();
      if (ctx.variables != null) {
        fkColumn = ctx.variables.resolve(fkColumn);
      }
      values.add(new InsertUpdateValue(fkColumn, fkColumn, true));
    }
    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      if (measure == null || Utils.isEmpty(measure.getFieldName())) {
        continue;
      }
      String field = measure.getFieldName();
      if (ctx.variables != null) {
        field = ctx.variables.resolve(field);
      }
      values.add(new InsertUpdateValue(field, field, true));
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
