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
package org.apache.hop.datavault.architecture;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/**
 * Builds a SOLUTION-oriented {@link ArchitectureGraph} from an execution map: workflows,
 * capabilities (actions), and model file references. Table/dataset grain is left to DATA inventory
 * and MODEL (ELK) exports.
 */
public final class ArchitectureGraphFromExecutionMap {

  private ArchitectureGraphFromExecutionMap() {}

  public static ArchitectureGraph build(ExecutionMapDocument map) {
    ArchitectureGraph graph = new ArchitectureGraph();
    graph.setViewType(ArchitectureViewType.SOLUTION);
    graph.setFreeformLayout(false);
    graph.setOmitEdges(false);
    if (map == null) {
      graph.setName("empty");
      return graph;
    }
    String root =
        !Utils.isEmpty(map.getRootArtifactPath())
            ? map.getRootArtifactPath()
            : !Utils.isEmpty(map.getName()) ? map.getName() : "execution-map";
    graph.setName(stripExtension(basename(root)));
    graph.setDescription(
        "Solution architecture (workflows, capabilities, model files) from " + basename(root));

    for (ExecutionMapNode node : map.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      if (skipNode(node.getNodeType())) {
        continue;
      }
      ArchitectureNode arch = mapNode(node);
      graph.addNode(arch);
    }

    for (ExecutionMapEdge edge : map.getEdgesOrEmpty()) {
      if (edge == null) {
        continue;
      }
      if (graph.findNode(edge.getFromNodeId()) == null
          || graph.findNode(edge.getToNodeId()) == null) {
        continue;
      }
      ArchitectureNode from = graph.findNode(edge.getFromNodeId());
      ArchitectureNode to = graph.findNode(edge.getToNodeId());
      String label = architectureEdgeLabel(edge, from, to);
      graph.addEdge(
          edge.getFromNodeId(), edge.getToNodeId(), mapEdgeKind(edge.getEdgeType()), label);
    }
    return graph;
  }

  /**
   * Architecture overview keeps process structure only. Datasets and pipeline files clutter the
   * diagram and belong in inventory / model exports.
   */
  private static boolean skipNode(ExecutionMapNodeType type) {
    if (type == null) {
      return true;
    }
    return switch (type) {
      case PIPELINE_TRANSFORM,
              MAPPING,
              META_INJECT,
              SOURCE_DATASET,
              TARGET_DATASET,
              PIPELINE,
              ROOT_PIPELINE,
              PIPELINE_FILE,
              GENERATED_PIPELINE,
              ORCHESTRATOR_PIPELINE,
              PIPELINE_EXECUTOR,
              WORKFLOW_EXECUTOR ->
          true;
      default -> false;
    };
  }

  private static ArchitectureNode mapNode(ExecutionMapNode node) {
    ArchitectureNode arch = new ArchitectureNode();
    arch.setId(node.getId());
    arch.setPath(node.getPath());
    arch.setDetailType(node.getNodeType() != null ? node.getNodeType().name() : null);
    if (!Utils.isEmpty(node.getPluginId())) {
      arch.property("pluginId", node.getPluginId());
    }

    ExecutionMapNodeType type = node.getNodeType();
    if (type == null) {
      arch.setName(displayName(node));
      arch.setKind(ArchitectureNodeKind.OTHER);
      arch.setLayer(ArchitectureLayer.OTHER);
      return arch;
    }

    switch (type) {
      case ROOT_WORKFLOW, WORKFLOW, BULK_MASTER_WORKFLOW -> {
        arch.setKind(ArchitectureNodeKind.WORKFLOW);
        arch.setLayer(ArchitectureLayer.ORCHESTRATION);
        arch.setName(displayName(node));
      }
      case WORKFLOW_ACTION -> {
        arch.setKind(ArchitectureNodeKind.CAPABILITY);
        arch.setLayer(layerForPlugin(node.getPluginId()));
        arch.setName(displayName(node));
      }
      case DV_UPDATE -> {
        arch.setKind(ArchitectureNodeKind.CAPABILITY);
        arch.setLayer(ArchitectureLayer.DATA_VAULT);
        arch.setName(labelCapability(displayName(node), "Data Vault Update"));
      }
      case BV_UPDATE -> {
        arch.setKind(ArchitectureNodeKind.CAPABILITY);
        arch.setLayer(ArchitectureLayer.BUSINESS_VAULT);
        arch.setName(labelCapability(displayName(node), "Business Vault Update"));
      }
      case DM_UPDATE, DM_PUBLISH -> {
        arch.setKind(ArchitectureNodeKind.CAPABILITY);
        arch.setLayer(ArchitectureLayer.DIMENSIONAL);
        arch.setName(labelCapability(displayName(node), "Dimensional Update"));
      }
      case DATA_VAULT_MODEL -> {
        arch.setKind(ArchitectureNodeKind.MODEL);
        arch.setLayer(ArchitectureLayer.DATA_VAULT);
        arch.setName(modelDisplayName(node));
      }
      case BUSINESS_VAULT_MODEL -> {
        arch.setKind(ArchitectureNodeKind.MODEL);
        arch.setLayer(ArchitectureLayer.BUSINESS_VAULT);
        arch.setName(modelDisplayName(node));
      }
      case DIMENSIONAL_MODEL -> {
        arch.setKind(ArchitectureNodeKind.MODEL);
        arch.setLayer(ArchitectureLayer.DIMENSIONAL);
        arch.setName(modelDisplayName(node));
      }
      default -> {
        arch.setKind(ArchitectureNodeKind.OTHER);
        arch.setLayer(ArchitectureLayer.OTHER);
        arch.setName(displayName(node));
      }
    }
    return arch;
  }

  /**
   * Prefer short human labels. Paths become basenames (e.g. {@code retail-360.hdv}); free-text
   * action names are kept.
   */
  private static String displayName(ExecutionMapNode node) {
    String name = node.getName();
    if (!Utils.isEmpty(name) && !ArchitecturePathSupport.looksLikeFilesystemPath(name)) {
      return name;
    }
    if (!Utils.isEmpty(node.getPath())) {
      return basename(node.getPath());
    }
    if (!Utils.isEmpty(name)) {
      return basename(name);
    }
    return node.getId();
  }

  /** Model nodes: show file basename (e.g. {@code retail-360.hdv}). */
  private static String modelDisplayName(ExecutionMapNode node) {
    if (!Utils.isEmpty(node.getPath())) {
      return basename(node.getPath());
    }
    if (!Utils.isEmpty(node.getName())) {
      return basename(node.getName());
    }
    return node.getId();
  }

  /**
   * Edge labels for architecture: prefer project-relative model path when linking to a model;
   * otherwise short free text (not absolute paths).
   */
  private static String architectureEdgeLabel(
      ExecutionMapEdge edge, ArchitectureNode from, ArchitectureNode to) {
    // Pointing at a model: show the model path (relative once portableized)
    if (to != null && to.getKind() == ArchitectureNodeKind.MODEL) {
      if (!Utils.isEmpty(to.getPath())) {
        return to.getPath();
      }
      return to.getName();
    }
    if (from != null && from.getKind() == ArchitectureNodeKind.MODEL) {
      if (!Utils.isEmpty(from.getPath())) {
        return from.getPath();
      }
      return from.getName();
    }
    String label = edge.getLabel();
    if (Utils.isEmpty(label)) {
      return null;
    }
    if (ArchitecturePathSupport.looksLikeFilesystemPath(label)) {
      return basename(label);
    }
    return label;
  }

  private static ArchitectureLayer layerForPlugin(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return ArchitectureLayer.ORCHESTRATION;
    }
    String id = pluginId.toUpperCase();
    if (id.contains("VALIDATE") || id.contains("RESOURCE_DEFINITION") || id.contains("SCHEMA")) {
      return ArchitectureLayer.CONTROL;
    }
    if (id.contains("QUALITY") || id.contains("DATA_QUALITY")) {
      return ArchitectureLayer.CONTROL;
    }
    if (id.contains("DATA_VAULT") || id.contains("DATAVAULT")) {
      return ArchitectureLayer.DATA_VAULT;
    }
    if (id.contains("BUSINESS_VAULT") || id.contains("BUSINESSVAULT")) {
      return ArchitectureLayer.BUSINESS_VAULT;
    }
    if (id.contains("DIMENSIONAL")) {
      return ArchitectureLayer.DIMENSIONAL;
    }
    if (id.contains("CATALOG") || id.contains("LINEAGE") || id.contains("EXECUTION_MAP")) {
      return ArchitectureLayer.OPS;
    }
    if (id.contains("BEGIN_VAULT") || id.contains("END_VAULT") || id.contains("METRICS")) {
      return ArchitectureLayer.OPS;
    }
    return ArchitectureLayer.ORCHESTRATION;
  }

  private static ArchitectureEdgeKind mapEdgeKind(ExecutionMapEdgeType type) {
    if (type == null) {
      return ArchitectureEdgeKind.OTHER;
    }
    return switch (type) {
      case EXECUTES, HOP -> ArchitectureEdgeKind.EXECUTES;
      case REFERENCES, MODEL_LINK -> ArchitectureEdgeKind.REFERENCES;
      case CONTAINS, GENERATES -> ArchitectureEdgeKind.CONTAINS;
      case READS_FROM, PIPELINE_SOURCE -> ArchitectureEdgeKind.READS;
      case WRITES_TO -> ArchitectureEdgeKind.WRITES;
      default -> ArchitectureEdgeKind.OTHER;
    };
  }

  private static String labelCapability(String name, String fallback) {
    return !Utils.isEmpty(name) ? name : fallback;
  }

  static String basename(String path) {
    if (path == null) {
      return "architecture";
    }
    String n = path.replace('\\', '/');
    // strip dataset:// style if ever present
    int scheme = n.indexOf("://");
    if (scheme > 0) {
      n = n.substring(scheme + 3);
    }
    int slash = n.lastIndexOf('/');
    if (slash >= 0 && slash < n.length() - 1) {
      n = n.substring(slash + 1);
    }
    return n;
  }

  private static String stripExtension(String name) {
    if (name == null) {
      return "architecture";
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      return name.substring(0, dot);
    }
    return name;
  }
}
