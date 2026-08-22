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

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapViewFilterTest {

  @Test
  void visibleNodesIncludeParentAndDirectChildrenOnly() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");

    Set<String> visibleIds =
        ExecutionMapViewFilter.getVisibleNodes(document, focus).stream()
            .map(ExecutionMapNode::getId)
            .collect(Collectors.toSet());

    assertEquals(Set.of("root", "child", "sibling"), visibleIds);
    assertTrue(!visibleIds.contains("grandchild"));
  }

  @Test
  void visibleNodesIncludeRunRetailUpdateSharedReferences() {
    ExecutionMapDocument document = runRetailUpdateDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("run-retail-update");

    Set<String> visibleNames =
        ExecutionMapViewFilter.getVisibleNodes(document, focus).stream()
            .map(ExecutionMapNode::getName)
            .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            "run-retail-update",
            "write-load-control-context",
            "load-e2e-sources-to-crm",
            "update-retail-dv-bv-dm"),
        visibleNames);
  }

  @Test
  void visibleNodesIncludeSharedChildrenLinkedByContainsEdge() {
    ExecutionMapDocument document = sharedChildDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("second-parent");

    Set<String> visibleIds =
        ExecutionMapViewFilter.getVisibleNodes(document, focus).stream()
            .map(ExecutionMapNode::getId)
            .collect(Collectors.toSet());

    assertEquals(Set.of("second-parent", "shared-child"), visibleIds);
  }

  @Test
  void visibleEdgesExcludeContainsAndDistantEdges() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("root");

    var visible =
        ExecutionMapViewFilter.getVisibleEdges(document, focus).stream()
            .map(ExecutionMapEdge::getEdgeType)
            .collect(Collectors.toSet());

    assertTrue(visible.contains(ExecutionMapEdgeType.EXECUTES));
    assertTrue(!visible.contains(ExecutionMapEdgeType.CONTAINS));
    assertTrue(!visible.contains(ExecutionMapEdgeType.MODEL_LINK));
  }

  private static ExecutionMapDocument runRetailUpdateDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document
        .getNodesOrEmpty()
        .add(node("run-retail-initial", ExecutionMapNodeType.WORKFLOW, "root"));
    document.getNodesOrEmpty().add(node("update-6-times", ExecutionMapNodeType.PIPELINE, "root"));
    document
        .getNodesOrEmpty()
        .add(node("run-retail-update", ExecutionMapNodeType.WORKFLOW, "update-6-times"));
    document
        .getNodesOrEmpty()
        .add(
            node("write-load-control-context", ExecutionMapNodeType.PIPELINE, "run-retail-update"));
    document
        .getNodesOrEmpty()
        .add(node("load-e2e-sources-to-crm", ExecutionMapNodeType.PIPELINE, "run-retail-initial"));
    document
        .getNodesOrEmpty()
        .add(node("update-retail-dv-bv-dm", ExecutionMapNodeType.WORKFLOW, "run-retail-initial"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "run-retail-update", "load-e2e-sources-to-crm"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "run-retail-update", "update-retail-dv-bv-dm"));
    return document;
  }

  private static ExecutionMapDocument sharedChildDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document.getNodesOrEmpty().add(node("first-parent", ExecutionMapNodeType.WORKFLOW, "root"));
    document.getNodesOrEmpty().add(node("second-parent", ExecutionMapNodeType.WORKFLOW, "root"));
    document
        .getNodesOrEmpty()
        .add(node("shared-child", ExecutionMapNodeType.PIPELINE, "first-parent"));
    document
        .getEdgesOrEmpty()
        .add(edge(ExecutionMapEdgeType.CONTAINS, "second-parent", "shared-child"));
    return document;
  }

  private static ExecutionMapDocument sampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, null));
    document.getNodesOrEmpty().add(node("child", ExecutionMapNodeType.DV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("sibling", ExecutionMapNodeType.BV_UPDATE, "root"));
    document.getNodesOrEmpty().add(node("grandchild", ExecutionMapNodeType.PIPELINE, "child"));

    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.CONTAINS, "root", "child"));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.EXECUTES, "root", "child"));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.MODEL_LINK, "child", "grandchild"));
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

  private static ExecutionMapEdge edge(ExecutionMapEdgeType type, String from, String to) {
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setEdgeType(type);
    edge.setFromNodeId(from);
    edge.setToNodeId(to);
    return edge;
  }
}
