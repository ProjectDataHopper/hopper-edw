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
package org.apache.hop.datavault.executionmap;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.pipeline.PipelineMeta;

/** Resolves statically generated pipelines from DV/BV/DM models during crawl. */
public final class ModelPipelineResolver {

  private ModelPipelineResolver() {}

  public static void resolveDataVaultModel(
      ExecutionMapContext context, String modelNodeId, DataVaultModel model) {
    if (context == null || model == null || Utils.isEmpty(modelNodeId)) {
      return;
    }
    if (!context.getOptions().isIncludeGeneratedPipelines()) {
      return;
    }
    try {
      List<IDvTable> tables =
          DvUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables());
      int generatedCount = 0;
      for (IDvTable table : tables) {
        if (table == null) {
          continue;
        }
        try {
          List<PipelineMeta> pipelines =
              table.generateUpdatePipelines(
                  context.getMetadataProvider(), context.getVariables(), model, new Date(), null);
          generatedCount += addGeneratedPipelines(context, modelNodeId, model.getName(), pipelines);
        } catch (Exception e) {
          context.addWarning(
              "Failed to generate pipelines for DV table '"
                  + table.getName()
                  + "': "
                  + e.getMessage());
        }
      }
      if (generatedCount > 0) {
        addOrchestratorNode(context, modelNodeId, model.getName());
      }
      resolveDmSourcePipelines(context, modelNodeId, model.getTables().stream().toList());
    } catch (Exception e) {
      context.addWarning("Failed to resolve DV generated pipelines: " + e.getMessage());
    }
  }

  public static void resolveBusinessVaultModel(
      ExecutionMapContext context,
      String modelNodeId,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel) {
    if (context == null || bvModel == null || Utils.isEmpty(modelNodeId)) {
      return;
    }
    if (!context.getOptions().isIncludeGeneratedPipelines()) {
      return;
    }
    try {
      List<IBvTable> tables =
          BusinessVaultUpdateExecutionSupport.orderTablesForPipelineExecution(
              bvModel.getTables(), bvModel, dvModel);
      int generatedCount = 0;
      for (IBvTable table : tables) {
        if (table == null) {
          continue;
        }
        try {
          List<PipelineMeta> pipelines =
              table.generateBuildPipelines(
                  context.getMetadataProvider(), context.getVariables(), bvModel, dvModel);
          generatedCount +=
              addGeneratedPipelines(context, modelNodeId, bvModel.getName(), pipelines);
        } catch (Exception e) {
          context.addWarning(
              "Failed to generate pipelines for BV table '"
                  + table.getName()
                  + "': "
                  + e.getMessage());
        }
      }
      if (generatedCount > 0) {
        addOrchestratorNode(context, modelNodeId, bvModel.getName());
      }
    } catch (Exception e) {
      context.addWarning("Failed to resolve BV generated pipelines: " + e.getMessage());
    }
  }

  public static void resolveDimensionalModel(
      ExecutionMapContext context, String modelNodeId, DimensionalModel dmModel) {
    if (context == null || dmModel == null || Utils.isEmpty(modelNodeId)) {
      return;
    }
    if (!context.getOptions().isIncludeGeneratedPipelines()) {
      return;
    }
    try {
      List<IDmTable> tables =
          DmUpdateExecutionSupport.orderTablesForPipelineExecution(dmModel.getTables());
      int generatedCount = 0;
      for (IDmTable table : tables) {
        if (table == null) {
          continue;
        }
        resolveDmTableSourcePipeline(context, modelNodeId, table.getSourceOrDefault());
        try {
          List<PipelineMeta> pipelines =
              table.generateUpdatePipelines(
                  context.getMetadataProvider(), context.getVariables(), dmModel, new Date());
          generatedCount +=
              addGeneratedPipelines(context, modelNodeId, dmModel.getName(), pipelines);
        } catch (Exception e) {
          context.addWarning(
              "Failed to generate pipelines for DM table '"
                  + table.getName()
                  + "': "
                  + e.getMessage());
        }
      }
      if (generatedCount > 0) {
        addOrchestratorNode(context, modelNodeId, dmModel.getName());
      }
    } catch (Exception e) {
      context.addWarning("Failed to resolve DM generated pipelines: " + e.getMessage());
    }
  }

  private static void resolveDmTableSourcePipeline(
      ExecutionMapContext context, String modelNodeId, DmSourceConfiguration source) {
    if (source == null || source.resolveSourceType() != DmSourceType.PIPELINE) {
      return;
    }
    String pipelineFile = source.resolveSourcePipelineFile(context.getVariables());
    if (Utils.isEmpty(pipelineFile)) {
      return;
    }
    String pipelineNodeId =
        ReferencedObjectResolver.resolvePipelineFile(context, modelNodeId, pipelineFile);
    if (!Utils.isEmpty(pipelineNodeId)) {
      context.addEdge(ExecutionMapEdgeType.PIPELINE_SOURCE, modelNodeId, pipelineNodeId, null);
    }
  }

  private static void resolveDmSourcePipelines(
      ExecutionMapContext context, String modelNodeId, List<?> tables) {
    // Reserved for future DM source refs on non-DM models.
  }

  private static int addGeneratedPipelines(
      ExecutionMapContext context,
      String modelNodeId,
      String modelName,
      List<PipelineMeta> pipelines)
      throws HopException {
    if (pipelines == null || pipelines.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (PipelineMeta pipelineMeta : pipelines) {
      if (pipelineMeta == null || Utils.isEmpty(pipelineMeta.getName())) {
        continue;
      }
      String syntheticPath = "generated://" + modelName + "/" + pipelineMeta.getName();
      ExecutionMapNode generatedNode = new ExecutionMapNode();
      generatedNode.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
      generatedNode.setName(pipelineMeta.getName());
      generatedNode.setPath(syntheticPath);
      generatedNode.setParentNodeId(modelNodeId);
      generatedNode.setSnapshotId(
          context.captureGeneratedPipelineSnapshot(
              syntheticPath, pipelineMeta.getXml(context.getVariables())));
      context.addNode(generatedNode);
      context.addEdge(
          ExecutionMapEdgeType.GENERATES,
          modelNodeId,
          generatedNode.getId(),
          pipelineMeta.getName());
      ModelDatasetResolver.linkGeneratedPipeline(
          context, modelNodeId, generatedNode.getId(), pipelineMeta);
      count++;
    }
    return count;
  }

  private static void addOrchestratorNode(
      ExecutionMapContext context, String modelNodeId, String modelName) {
    String orchestratorName = "DV Update Orchestrator - " + modelName;
    ExecutionMapNode orchestratorNode = new ExecutionMapNode();
    orchestratorNode.setNodeType(ExecutionMapNodeType.ORCHESTRATOR_PIPELINE);
    orchestratorNode.setName(orchestratorName);
    orchestratorNode.setPath("synthetic://" + orchestratorName);
    orchestratorNode.setParentNodeId(modelNodeId);
    context.addNode(orchestratorNode);
    context.addEdge(
        ExecutionMapEdgeType.EXECUTES, modelNodeId, orchestratorNode.getId(), orchestratorName);
  }
}
