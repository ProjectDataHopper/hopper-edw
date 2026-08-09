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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Mutable crawl state shared by workflow and pipeline crawlers. */
public final class ExecutionMapContext {

  private final ExecutionMapDocument document;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final CrawlOptions options;
  private final Set<String> visitedArtifactPaths = new HashSet<>();
  private final Map<String, String> nodeIdByArtifactPath = new HashMap<>();
  private final Map<String, String> snapshotIdByArtifactPath = new HashMap<>();
  private final List<String> warnings = new ArrayList<>();
  private int currentDepth;

  public ExecutionMapContext(
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      CrawlOptions options) {
    this.document = document;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.options = options != null ? options : CrawlOptions.builder().build();
  }

  public ExecutionMapDocument getDocument() {
    return document;
  }

  public IVariables getVariables() {
    return variables;
  }

  public IHopMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  public CrawlOptions getOptions() {
    return options;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void addWarning(String warning) {
    if (!Utils.isEmpty(warning)) {
      warnings.add(warning);
    }
  }

  public String resolvePath(String path) {
    if (Utils.isEmpty(path) || variables == null) {
      return path;
    }
    try {
      return HopVfs.normalize(variables.resolve(path));
    } catch (Exception e) {
      return variables.resolve(path);
    }
  }

  public boolean beginArtifactVisit(String resolvedPath) {
    if (Utils.isEmpty(resolvedPath)) {
      return false;
    }
    if (visitedArtifactPaths.contains(resolvedPath)) {
      return false;
    }
    if (currentDepth >= options.getMaxDepth()) {
      addWarning("Maximum crawl depth reached at: " + resolvedPath);
      return false;
    }
    visitedArtifactPaths.add(resolvedPath);
    currentDepth++;
    return true;
  }

  public void endArtifactVisit() {
    if (currentDepth > 0) {
      currentDepth--;
    }
  }

  public String existingNodeIdForPath(String resolvedPath) {
    return nodeIdByArtifactPath.get(resolvedPath);
  }

  public ExecutionMapNode addNode(ExecutionMapNode node) {
    if (node == null) {
      return null;
    }
    if (Utils.isEmpty(node.getId())) {
      node.setId(UUID.randomUUID().toString());
    }
    document.getNodesOrEmpty().add(node);
    if (!Utils.isEmpty(node.getPath())) {
      nodeIdByArtifactPath.putIfAbsent(resolvePath(node.getPath()), node.getId());
    }
    return node;
  }

  public ExecutionMapEdge addEdge(
      ExecutionMapEdgeType edgeType, String fromNodeId, String toNodeId, String label) {
    if (Utils.isEmpty(fromNodeId) || Utils.isEmpty(toNodeId)) {
      return null;
    }
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setId(UUID.randomUUID().toString());
    edge.setEdgeType(edgeType);
    edge.setFromNodeId(fromNodeId);
    edge.setToNodeId(toNodeId);
    edge.setLabel(label);
    document.getEdgesOrEmpty().add(edge);
    return edge;
  }

  public ExecutionMapEdge addEdgeIfAbsent(
      ExecutionMapEdgeType edgeType, String fromNodeId, String toNodeId, String label) {
    if (Utils.isEmpty(fromNodeId) || Utils.isEmpty(toNodeId)) {
      return null;
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() != edgeType) {
        continue;
      }
      if (fromNodeId.equals(edge.getFromNodeId()) && toNodeId.equals(edge.getToNodeId())) {
        return edge;
      }
    }
    return addEdge(edgeType, fromNodeId, toNodeId, label);
  }

  public String captureSnapshot(
      ExecutionMapArtifactType artifactType, String sourcePath, String xml) {
    if (!options.isCaptureSnapshots() || Utils.isEmpty(xml)) {
      return null;
    }
    String resolvedPath = Utils.isEmpty(sourcePath) ? null : resolvePath(sourcePath);
    String dedupeKey = resolvedPath != null ? resolvedPath : artifactType.name() + ":" + sourcePath;
    String existing = snapshotIdByArtifactPath.get(dedupeKey);
    if (existing != null) {
      return existing;
    }
    try {
      ExecutionMapArtifactSnapshot snapshot = new ExecutionMapArtifactSnapshot();
      snapshot.setId(UUID.randomUUID().toString());
      snapshot.setArtifactType(artifactType);
      snapshot.setSourcePath(
          ExecutionMapPathSupport.toStoredPath(
              resolvedPath != null ? resolvedPath : sourcePath, variables));
      snapshot.setCapturedAt(new Date());
      snapshot.setXmlGzipBase64(ArtifactSnapshotSupport.encodeXml(xml));
      document.getSnapshotsOrEmpty().add(snapshot);
      snapshotIdByArtifactPath.put(dedupeKey, snapshot.getId());
      return snapshot.getId();
    } catch (HopException e) {
      addWarning("Failed to capture snapshot for " + sourcePath + ": " + e.getMessage());
      return null;
    }
  }

  public String capturePipelineSnapshot(String sourcePath, String xml) {
    return captureSnapshot(ExecutionMapArtifactType.PIPELINE, sourcePath, xml);
  }

  public String captureWorkflowSnapshot(String sourcePath, String xml) {
    return captureSnapshot(ExecutionMapArtifactType.WORKFLOW, sourcePath, xml);
  }

  public String captureGeneratedPipelineSnapshot(String syntheticPath, String xml) {
    return captureSnapshot(ExecutionMapArtifactType.GENERATED_PIPELINE, syntheticPath, xml);
  }

  public static String resolveProjectKey(IVariables variables) {
    if (variables != null) {
      String projectHome = variables.resolve("${PROJECT_HOME}");
      if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
        Path path = Path.of(projectHome).getFileName();
        if (path != null) {
          return path.toString();
        }
      }
    }
    return "project";
  }

  public static ExecutionMapNodeType mapActionPluginId(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return ExecutionMapNodeType.WORKFLOW_ACTION;
    }
    return switch (pluginId) {
      case "DATA_VAULT_UPDATE" -> ExecutionMapNodeType.DV_UPDATE;
      case "BUSINESS_VAULT_UPDATE" -> ExecutionMapNodeType.BV_UPDATE;
      case "DIMENSIONAL_UPDATE" -> ExecutionMapNodeType.DM_UPDATE;
      case "DIMENSIONAL_PUBLISH" -> ExecutionMapNodeType.DM_PUBLISH;
      case "UPDATE_RESOURCE_DEFINITION_GROUP" -> ExecutionMapNodeType.RDG_UPDATE;
      default -> ExecutionMapNodeType.WORKFLOW_ACTION;
    };
  }

  public static boolean isWorkflowActionLayerNodeType(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return false;
    }
    return switch (nodeType) {
      case WORKFLOW_ACTION, DV_UPDATE, BV_UPDATE, DM_UPDATE, DM_PUBLISH, RDG_UPDATE -> true;
      default -> false;
    };
  }

  public static boolean isPipelineTransformLayerNodeType(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return false;
    }
    return switch (nodeType) {
      case PIPELINE_TRANSFORM, PIPELINE_EXECUTOR, WORKFLOW_EXECUTOR, MAPPING, META_INJECT -> true;
      default -> false;
    };
  }

  public static ExecutionMapNodeType mapTransformPluginId(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return ExecutionMapNodeType.PIPELINE_TRANSFORM;
    }
    String normalized = pluginId.toLowerCase();
    if (normalized.contains("pipelineexecutor")) {
      return ExecutionMapNodeType.PIPELINE_EXECUTOR;
    }
    if (normalized.contains("workflowexecutor")) {
      return ExecutionMapNodeType.WORKFLOW_EXECUTOR;
    }
    if (normalized.contains("mapping")) {
      return ExecutionMapNodeType.MAPPING;
    }
    if (normalized.contains("metainject")) {
      return ExecutionMapNodeType.META_INJECT;
    }
    return ExecutionMapNodeType.PIPELINE_TRANSFORM;
  }
}
