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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;

/** Filters execution map nodes and edges to the current 2-level focus view. */
public final class ExecutionMapViewFilter {

  private ExecutionMapViewFilter() {}

  public static Set<String> directChildNodeIds(ExecutionMapDocument document, String parentId) {
    Set<String> childIds = new LinkedHashSet<>();
    if (document == null || Utils.isEmpty(parentId)) {
      return childIds;
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && !Utils.isEmpty(node.getId()) && parentId.equals(node.getParentNodeId())) {
        childIds.add(node.getId());
      }
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null
          || edge.getEdgeType() != ExecutionMapEdgeType.CONTAINS
          || !parentId.equals(edge.getFromNodeId())
          || Utils.isEmpty(edge.getToNodeId())) {
        continue;
      }
      childIds.add(edge.getToNodeId());
    }
    return childIds;
  }

  public static List<ExecutionMapNode> getVisibleNodes(
      ExecutionMapDocument document, ExecutionMapFocusContext focus) {
    List<ExecutionMapNode> visible = new ArrayList<>();
    if (document == null) {
      return visible;
    }
    ExecutionMapFocusContext resolvedFocus = focus != null ? focus : new ExecutionMapFocusContext();
    ExecutionMapNode parent = resolvedFocus.resolveFocusNode(document);
    if (parent == null) {
      return visible;
    }
    visible.add(parent);
    for (String childId : directChildNodeIds(document, parent.getId())) {
      ExecutionMapNode child = document.findNodeById(childId);
      if (child != null) {
        visible.add(child);
      }
    }
    return visible;
  }

  public static List<ExecutionMapEdge> getVisibleEdges(
      ExecutionMapDocument document, ExecutionMapFocusContext focus) {
    List<ExecutionMapEdge> visible = new ArrayList<>();
    if (document == null) {
      return visible;
    }
    Set<String> visibleIds = new HashSet<>();
    for (ExecutionMapNode node : getVisibleNodes(document, focus)) {
      if (node != null && !Utils.isEmpty(node.getId())) {
        visibleIds.add(node.getId());
      }
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS) {
        continue;
      }
      if (visibleIds.contains(edge.getFromNodeId()) && visibleIds.contains(edge.getToNodeId())) {
        visible.add(edge);
      }
    }
    return visible;
  }
}
