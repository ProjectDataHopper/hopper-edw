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
package org.hopper.edw.datavault.executionmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.executionmap.ExecutionMapLineageEdge.DatasetRef;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Extracts table-level lineage jobs from execution map documents. */
public final class ExecutionMapLineageCollector {

  private static final String DEFAULT_JOB_NAMESPACE = "hop-data-vault";

  private ExecutionMapLineageCollector() {}

  public static List<ExecutionMapLineageEdge> collect(ExecutionMapDocument document) {
    if (document == null) {
      return List.of();
    }
    Map<String, ExecutionMapNode> nodeById = new HashMap<>();
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && !Utils.isEmpty(node.getId())) {
        nodeById.put(node.getId(), node);
      }
    }

    String jobNamespace = resolveJobNamespace(document);
    Map<String, ExecutionMapLineageEdge> jobs = new LinkedHashMap<>();

    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || !isJobNode(node)) {
        continue;
      }
      String key = jobKey(node);
      jobs.computeIfAbsent(
          key,
          ignored -> new ExecutionMapLineageEdge(jobNamespace, node.getName(), node.getPath()));
    }

    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == null) {
        continue;
      }
      if (edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM) {
        ExecutionMapNode dataset = nodeById.get(edge.getFromNodeId());
        ExecutionMapNode job = nodeById.get(edge.getToNodeId());
        if (dataset != null && job != null && isJobNode(job)) {
          ExecutionMapLineageEdge lineageEdge = jobs.get(jobKey(job));
          if (lineageEdge != null) {
            lineageEdge.getInputs().add(toDatasetRef(dataset));
          }
        }
      } else if (edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO) {
        ExecutionMapNode job = nodeById.get(edge.getFromNodeId());
        ExecutionMapNode dataset = nodeById.get(edge.getToNodeId());
        if (dataset != null && job != null && isJobNode(job)) {
          ExecutionMapLineageEdge lineageEdge = jobs.get(jobKey(job));
          if (lineageEdge != null) {
            lineageEdge.getOutputs().add(toDatasetRef(dataset));
          }
        }
      }
    }

    List<ExecutionMapLineageEdge> result = new ArrayList<>();
    for (ExecutionMapLineageEdge edge : jobs.values()) {
      if (!edge.getInputs().isEmpty() || !edge.getOutputs().isEmpty()) {
        result.add(edge);
      }
    }
    return result;
  }

  private static boolean isJobNode(ExecutionMapNode node) {
    if (node.getNodeType() == null) {
      return false;
    }
    return switch (node.getNodeType()) {
      case PIPELINE, ROOT_PIPELINE, GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE -> true;
      default -> false;
    };
  }

  private static String jobKey(ExecutionMapNode node) {
    if (!Utils.isEmpty(node.getPath())) {
      return node.getPath();
    }
    if (!Utils.isEmpty(node.getName())) {
      return node.getName();
    }
    return node.getId();
  }

  private static DatasetRef toDatasetRef(ExecutionMapNode datasetNode) {
    String namespace = datasetNode.getProperty("datasetNamespace");
    String name = datasetNode.getProperty("datasetName");
    if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
      String path = datasetNode.getPath();
      if (!Utils.isEmpty(path) && path.startsWith(DatasetNodeSupport.DATASET_PATH_PREFIX)) {
        String remainder = path.substring(DatasetNodeSupport.DATASET_PATH_PREFIX.length());
        int slash = remainder.indexOf('/');
        if (slash > 0) {
          namespace = remainder.substring(0, slash);
          name = remainder.substring(slash + 1);
        }
      }
    }
    if (Utils.isEmpty(namespace)) {
      namespace = "default";
    }
    if (Utils.isEmpty(name) && !Utils.isEmpty(datasetNode.getName())) {
      name = datasetNode.getName();
    }
    return new DatasetRef(namespace, name, datasetNode.getProperty("datasetKind"));
  }

  private static String resolveJobNamespace(ExecutionMapDocument document) {
    if (!Utils.isEmpty(document.getHopProject())) {
      return DEFAULT_JOB_NAMESPACE + "/" + document.getHopProject();
    }
    return DEFAULT_JOB_NAMESPACE;
  }
}
