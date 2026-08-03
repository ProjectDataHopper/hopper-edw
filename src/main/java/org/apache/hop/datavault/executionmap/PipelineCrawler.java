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

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Crawls a Hop pipeline and nested transform references. */
public final class PipelineCrawler {

  private PipelineCrawler() {}

  public static String crawlPipeline(
      ExecutionMapContext context,
      String pipelinePath,
      PipelineMeta loadedPipeline,
      boolean root,
      String parentNodeId) {
    if (context == null) {
      return null;
    }
    if (!context.getOptions().isFollowNestedPipelines()) {
      return addPipelineFileNode(context, pipelinePath, parentNodeId);
    }

    String resolvedPath =
        loadedPipeline != null
            ? context.resolvePath(loadedPipeline.getFilename())
            : context.resolvePath(pipelinePath);
    String existing = context.existingNodeIdForPath(resolvedPath);
    if (!Utils.isEmpty(existing)) {
      if (!Utils.isEmpty(parentNodeId)) {
        context.addEdge(ExecutionMapEdgeType.CONTAINS, parentNodeId, existing, null);
      }
      return existing;
    }
    if (!context.beginArtifactVisit(resolvedPath)) {
      context.addWarning("Skipped cyclic or deep pipeline reference: " + resolvedPath);
      return existing;
    }
    try {
      PipelineMeta pipelineMeta = loadedPipeline;
      if (pipelineMeta == null) {
        pipelineMeta =
            new PipelineMeta(resolvedPath, context.getMetadataProvider(), context.getVariables());
      }

      ExecutionMapNode pipelineNode = new ExecutionMapNode();
      pipelineNode.setNodeType(
          root ? ExecutionMapNodeType.ROOT_PIPELINE : ExecutionMapNodeType.PIPELINE);
      pipelineNode.setName(pipelineMeta.getName());
      pipelineNode.setPath(
          ExecutionMapPathSupport.toStoredPath(resolvedPath, context.getVariables()));
      pipelineNode.setParentNodeId(parentNodeId);
      pipelineNode.setSnapshotId(
          context.capturePipelineSnapshot(
              resolvedPath, pipelineMeta.getXml(context.getVariables())));
      context.addNode(pipelineNode);
      String pipelineNodeId = pipelineNode.getId();

      if (!Utils.isEmpty(parentNodeId)) {
        context.addEdge(ExecutionMapEdgeType.CONTAINS, parentNodeId, pipelineNodeId, null);
      }

      boolean includeTransforms = context.getOptions().isIncludePipelineTransforms();
      Map<String, String> transformNodeIds = new HashMap<>();
      for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
        if (transformMeta == null || Utils.isEmpty(transformMeta.getName())) {
          continue;
        }
        ITransformMeta transform = transformMeta.getTransform();
        String pluginId = transformMeta.getPluginId();
        String referenceFromId = pipelineNodeId;
        if (includeTransforms) {
          ExecutionMapNode transformNode = new ExecutionMapNode();
          transformNode.setNodeType(ExecutionMapContext.mapTransformPluginId(pluginId));
          transformNode.setName(transformMeta.getName());
          transformNode.setPluginId(pluginId);
          transformNode.setParentNodeId(pipelineNodeId);
          context.addNode(transformNode);
          transformNodeIds.put(transformMeta.getName(), transformNode.getId());
          context.addEdge(
              ExecutionMapEdgeType.CONTAINS, pipelineNodeId, transformNode.getId(), null);
          referenceFromId = transformNode.getId();
        }
        if (transform != null) {
          ReferencedObjectResolver.resolveTransform(context, referenceFromId, transform, pluginId);
        }
      }

      if (includeTransforms) {
        for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
          if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
            continue;
          }
          String fromId = transformNodeIds.get(hop.getFromTransform().getName());
          String toId = transformNodeIds.get(hop.getToTransform().getName());
          if (!Utils.isEmpty(fromId) && !Utils.isEmpty(toId)) {
            context.addEdge(ExecutionMapEdgeType.HOP, fromId, toId, null);
          }
        }
      }
      PipelineDatasetResolver.resolvePipeline(context, pipelineNodeId, pipelineMeta);
      return pipelineNodeId;
    } catch (HopException e) {
      context.addWarning("Failed to crawl pipeline " + resolvedPath + ": " + e.getMessage());
      return addPipelineFileNode(context, pipelinePath, parentNodeId);
    } finally {
      context.endArtifactVisit();
    }
  }

  private static String addPipelineFileNode(
      ExecutionMapContext context, String pipelinePath, String parentNodeId) {
    if (Utils.isEmpty(pipelinePath)) {
      return null;
    }
    String resolvedPath = context.resolvePath(pipelinePath);
    String existing = context.existingNodeIdForPath(resolvedPath);
    if (!Utils.isEmpty(existing)) {
      return existing;
    }
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.PIPELINE_FILE);
    node.setName(extractBaseName(resolvedPath));
    node.setPath(ExecutionMapPathSupport.toStoredPath(resolvedPath, context.getVariables()));
    node.setParentNodeId(parentNodeId);
    context.addNode(node);
    return node.getId();
  }

  private static String extractBaseName(String path) {
    if (Utils.isEmpty(path)) {
      return "pipeline";
    }
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    String base = slash >= 0 ? path.substring(slash + 1) : path;
    int dot = base.lastIndexOf('.');
    return dot > 0 ? base.substring(0, dot) : base;
  }
}
