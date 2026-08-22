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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapFocusContextTest {

  @Test
  void resolvesRootWhenFocusIsUnset() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext();

    ExecutionMapNode resolved = focus.resolveFocusNode(document);

    assertNotNull(resolved);
    assertEquals("root", resolved.getId());
  }

  @Test
  void buildsBreadcrumbFromFocusedNodeToRoot() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext("child");

    var breadcrumb = focus.getBreadcrumb(document);

    assertEquals(2, breadcrumb.size());
    assertEquals("root", breadcrumb.get(0).getId());
    assertEquals("child", breadcrumb.get(1).getId());
  }

  @Test
  void drillIntoAndNavigateToUpdateFocus() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext();

    focus.drillInto("child");
    assertEquals("child", focus.getFocusNodeId());

    focus.navigateTo("root", document);
    assertEquals(null, focus.getFocusNodeId());

    focus.drillInto("child");
    focus.drillInto("grandchild");
    assertEquals("grandchild", focus.getFocusNodeId());

    focus.navigateTo("child", document);
    assertEquals("child", focus.getFocusNodeId());

    focus.navigateToRoot();
    assertEquals(null, focus.getFocusNodeId());
  }

  @Test
  void canDrillIntoWhenNodeHasSharedChildLinkedByContainsEdge() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(node("parent", "Parent", ExecutionMapNodeType.WORKFLOW, "root"));
    document
        .getNodesOrEmpty()
        .add(node("shared", "Shared", ExecutionMapNodeType.PIPELINE, "other-parent"));
    document.getEdgesOrEmpty().add(edge("parent", "shared"));

    ExecutionMapFocusContext focus = new ExecutionMapFocusContext();
    ExecutionMapNode parent = document.findNodeById("parent");

    assertTrue(focus.canDrillInto(parent, document));
  }

  @Test
  void canDrillIntoWhenNodeHasChildren() {
    ExecutionMapDocument document = sampleDocument();
    ExecutionMapFocusContext focus = new ExecutionMapFocusContext();

    ExecutionMapNode root = document.findNodeById("root");
    ExecutionMapNode child = document.findNodeById("child");
    ExecutionMapNode grandchild = document.findNodeById("grandchild");

    assertTrue(focus.canDrillInto(root, document));
    assertTrue(focus.canDrillInto(child, document));
    assertFalse(focus.canDrillInto(grandchild, document));
  }

  private static ExecutionMapDocument sampleDocument() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode root = node("root", "Root Workflow", ExecutionMapNodeType.ROOT_WORKFLOW, null);
    ExecutionMapNode child =
        node("child", "Child Model", ExecutionMapNodeType.DATA_VAULT_MODEL, "root");
    ExecutionMapNode grandchild =
        node("grandchild", "Grandchild Pipeline", ExecutionMapNodeType.PIPELINE, "child");
    document.getNodesOrEmpty().add(root);
    document.getNodesOrEmpty().add(child);
    document.getNodesOrEmpty().add(grandchild);
    return document;
  }

  private static ExecutionMapEdge edge(String fromId, String toId) {
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setEdgeType(ExecutionMapEdgeType.CONTAINS);
    edge.setFromNodeId(fromId);
    edge.setToNodeId(toId);
    return edge;
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
}
