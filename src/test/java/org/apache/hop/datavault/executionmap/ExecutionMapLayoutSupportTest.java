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
package org.apache.hop.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapLayoutSupportTest {

  @Test
  void layoutOrdersFlowNodesLeftToRightInsideContainers() throws Exception {
    ExecutionMapDocument document = buildSampleDocument();

    ExecutionMapLayoutSupport.layout(document);

    ExecutionMapNode actionA = document.findNodeById("action-a");
    ExecutionMapNode actionB = document.findNodeById("action-b");
    ExecutionMapNode transformA = document.findNodeById("transform-a");
    ExecutionMapNode transformB = document.findNodeById("transform-b");
    ExecutionMapNode workflow = document.findNodeById("workflow");
    ExecutionMapNode pipeline = document.findNodeById("pipeline");
    ExecutionMapNode dataset = document.findNodeById("dataset");

    assertTrue(
        actionA.getLocation().x < actionB.getLocation().x, "action flow should run left-to-right");
    assertTrue(
        transformA.getLocation().x < transformB.getLocation().x,
        "pipeline flow should run left-to-right");
    assertTrue(
        actionA.getLocation().x >= workflow.getLocation().x,
        "actions should stay within workflow container");
    assertTrue(
        actionB.getLocation().y >= workflow.getLocation().y,
        "actions should stay within workflow container");
    assertTrue(
        transformA.getLocation().x >= pipeline.getLocation().x,
        "transforms should stay within pipeline container");
    assertTrue(
        dataset.getLocation().x >= 0 && dataset.getLocation().y >= 0,
        "dataset node should receive a layout position");
  }

  @Test
  void hubSpokeLayoutPlacesTargetsInRightColumnWithGutter() throws Exception {
    ExecutionMapDocument document = buildHubSpokeDocument();

    ExecutionMapLayoutSupport.layout(document);

    ExecutionMapNode hub = document.findNodeById("hub");
    ExecutionMapNode dm = document.findNodeById("dm-1");
    ExecutionMapNode dv = document.findNodeById("dv");

    int expectedTargetX =
        hub.getLocation().x
            + ExecutionMapMetrics.NODE_WIDTH
            + ExecutionMapMetrics.HUB_GUTTER
            + ExecutionMapMetrics.BUS_LANE_WIDTH;
    assertEquals(expectedTargetX, dm.getLocation().x);
    assertEquals(expectedTargetX, dv.getLocation().x);
    assertTrue(dm.getLocation().y != dv.getLocation().y, "spokes should be vertically separated");
    assertTrue(
        expectedTargetX - (hub.getLocation().x + ExecutionMapMetrics.NODE_WIDTH) >= 64,
        "gutter between hub and targets should be at least 64px");
  }

  @Test
  void layoutSucceedsWhenOnlyNonLayoutEdgesArePresent() throws Exception {
    ExecutionMapDocument document = buildSampleDocument();
    document
        .getEdgesOrEmpty()
        .removeIf(edge -> ExecutionMapLayoutOptions.DEFAULT.usesEdgeForLayout(edge.getEdgeType()));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.REFERENCES, "action-b", "pipeline", "ref"));

    ExecutionMapLayoutSupport.layout(document, ExecutionMapLayoutOptions.DEFAULT);

    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      assertTrue(node.getLocation().x >= 0, "node " + node.getId() + " should have x position");
      assertTrue(node.getLocation().y >= 0, "node " + node.getId() + " should have y position");
    }
  }

  private static ExecutionMapDocument buildHubSpokeDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setRootArtifactPath("workflows/update.hwf");
    document.getNodesOrEmpty().add(node("hub", "update", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document
        .getNodesOrEmpty()
        .add(node("dm-1", "retail-f-orders", ExecutionMapNodeType.DIMENSIONAL_MODEL, null));
    document
        .getNodesOrEmpty()
        .add(node("dv", "retail-360", ExecutionMapNodeType.DATA_VAULT_MODEL, null));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.REFERENCES, "hub", "dm-1", "dm"));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.REFERENCES, "hub", "dv", "dv"));
    return document;
  }

  private static ExecutionMapDocument buildSampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setRootArtifactPath("workflows/sample.hwf");

    document
        .getNodesOrEmpty()
        .add(node("workflow", "Sample workflow", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document
        .getNodesOrEmpty()
        .add(node("action-a", "Action A", ExecutionMapNodeType.WORKFLOW_ACTION, "workflow"));
    document
        .getNodesOrEmpty()
        .add(node("action-b", "Action B", ExecutionMapNodeType.WORKFLOW_ACTION, "workflow"));
    document
        .getNodesOrEmpty()
        .add(node("pipeline", "Sample pipeline", ExecutionMapNodeType.PIPELINE, "action-b"));
    document
        .getNodesOrEmpty()
        .add(
            node(
                "transform-a", "Transform A", ExecutionMapNodeType.PIPELINE_TRANSFORM, "pipeline"));
    document
        .getNodesOrEmpty()
        .add(
            node(
                "transform-b", "Transform B", ExecutionMapNodeType.PIPELINE_TRANSFORM, "pipeline"));
    document
        .getNodesOrEmpty()
        .add(node("dataset", "orders", ExecutionMapNodeType.SOURCE_DATASET, "transform-b"));

    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.HOP, "action-a", "action-b", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.HOP, "transform-a", "transform-b", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "workflow", "action-a", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "workflow", "action-b", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "action-b", "pipeline", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "pipeline", "transform-a", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "pipeline", "transform-b", null));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.READS_FROM, "transform-b", "dataset", null));
    return document;
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
