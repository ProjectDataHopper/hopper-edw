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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapTwoLevelLayoutTest {

  @Test
  void placesParentLeftOfChildren() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");
    List<ExecutionMapNode> visible = ExecutionMapViewFilter.getVisibleNodes(document, focus);
    var metrics = ExecutionMapTwoLevelLayout.defaultCardMetrics(visible);

    ExecutionMapTwoLevelLayout.layout(document, focus, metrics);

    ExecutionMapNode parent = document.findNodeById("root");
    ExecutionMapNode childA = document.findNodeById("child-a");
    ExecutionMapNode childB = document.findNodeById("child-b");

    assertTrue(parent.getLocation().x < childA.getLocation().x);
    assertTrue(parent.getLocation().x < childB.getLocation().x);
    assertTrue(childA.getLocation().y < childB.getLocation().y);
  }

  @Test
  void modelFocusPlacesSourcePipelineTargetInRows() {
    ExecutionMapDocument document = modelPipelineDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("model");
    List<ExecutionMapNode> visible = ExecutionMapViewFilter.getVisibleNodes(document, focus);
    var metrics = ExecutionMapTwoLevelLayout.defaultCardMetrics(visible);

    ExecutionMapTwoLevelLayout.layout(document, focus, metrics);

    ExecutionMapNode model = document.findNodeById("model");
    ExecutionMapNode pipeA = document.findNodeById("pipe-a");
    ExecutionMapNode pipeB = document.findNodeById("pipe-b");
    ExecutionMapNode srcA = document.findNodeById("src-a");
    ExecutionMapNode tgtA = document.findNodeById("tgt-a");
    ExecutionMapNode srcB = document.findNodeById("src-b");
    ExecutionMapNode tgtB = document.findNodeById("tgt-b");

    assertTrue(model.getLocation().x < srcA.getLocation().x);
    assertTrue(srcA.getLocation().x < pipeA.getLocation().x);
    assertTrue(pipeA.getLocation().x < tgtA.getLocation().x);
    assertTrue(srcB.getLocation().x < pipeB.getLocation().x);
    assertTrue(pipeB.getLocation().x < tgtB.getLocation().x);
    assertTrue(pipeB.getLocation().y > pipeA.getLocation().y);
  }

  @Test
  void modelFocusPlacesOrphanCatalogDatasetsBelowPipelineRows() {
    ExecutionMapDocument document = modelPipelineDocumentWithOrphan();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("model");
    List<ExecutionMapNode> visible = ExecutionMapViewFilter.getVisibleNodes(document, focus);
    var metrics = ExecutionMapTwoLevelLayout.defaultCardMetrics(visible);

    ExecutionMapTwoLevelLayout.layout(document, focus, metrics);

    ExecutionMapNode pipe = document.findNodeById("pipe-a");
    ExecutionMapNode orphan = document.findNodeById("orphan");

    assertTrue(orphan.getLocation().y > pipe.getLocation().y);
  }

  private static ExecutionMapDocument sampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document.getNodesOrEmpty().add(node("child-a", ExecutionMapNodeType.DV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("child-b", ExecutionMapNodeType.BV_UPDATE, "root"));
    return document;
  }

  private static ExecutionMapDocument modelPipelineDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document
        .getNodesOrEmpty()
        .add(node("model", "retail-360", ExecutionMapNodeType.DATA_VAULT_MODEL, null));
    document
        .getNodesOrEmpty()
        .add(node("pipe-a", "hub-a", ExecutionMapNodeType.GENERATED_PIPELINE, "model"));
    document
        .getNodesOrEmpty()
        .add(node("pipe-b", "hub-b", ExecutionMapNodeType.GENERATED_PIPELINE, "model"));
    document
        .getNodesOrEmpty()
        .add(node("src-a", "CRM::customer", ExecutionMapNodeType.SOURCE_DATASET, "model"));
    document
        .getNodesOrEmpty()
        .add(node("tgt-a", "Vault::hub_a", ExecutionMapNodeType.TARGET_DATASET, "model"));
    document
        .getNodesOrEmpty()
        .add(node("src-b", "CRM::product", ExecutionMapNodeType.SOURCE_DATASET, "model"));
    document
        .getNodesOrEmpty()
        .add(node("tgt-b", "Vault::hub_b", ExecutionMapNodeType.TARGET_DATASET, "model"));

    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.READS_FROM, "src-a", "pipe-a", "source"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.READS_FROM, "tgt-a", "pipe-a", "target"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.READS_FROM, "src-b", "pipe-b", "source"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.WRITES_TO, "pipe-b", "tgt-b", "target"));
    return document;
  }

  private static ExecutionMapDocument modelPipelineDocumentWithOrphan() {
    ExecutionMapDocument document = modelPipelineDocument();
    document
        .getNodesOrEmpty()
        .add(
            node(
                "orphan",
                "hop/retail-example/models/retail-360::hub_customer",
                ExecutionMapNodeType.TARGET_DATASET,
                "model"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "model", "orphan", "hub_customer"));
    return document;
  }

  private static ExecutionMapNode node(String id, ExecutionMapNodeType type, String parentId) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setId(id);
    node.setName(id);
    node.setNodeType(type);
    node.setParentNodeId(parentId);
    return node;
  }

  private static ExecutionMapNode node(
      String id, String name, ExecutionMapNodeType type, String parentId) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setId(id);
    node.setName(name);
    node.setNodeType(type);
    node.setParentNodeId(parentId);
    return node;
  }

  private static ExecutionMapEdge edge(
      ExecutionMapEdgeType type, String fromId, String toId, String label) {
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setEdgeType(type);
    edge.setFromNodeId(fromId);
    edge.setToNodeId(toId);
    edge.setLabel(label);
    return edge;
  }
}
