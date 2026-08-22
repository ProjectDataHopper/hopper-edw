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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.layout.ElkLayout;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Column layout for action-less hub-and-spoke execution maps. */
public final class ExecutionMapHubSpokeLayout {

  private static final int TARGET_VERTICAL_SPACING = 32;

  private ExecutionMapHubSpokeLayout() {}

  /** Returns true when this document should use hub-and-spoke layout instead of ELK. */
  public static boolean canUseHubSpokeLayout(ExecutionMapDocument document) {
    if (document == null || document.getNodesOrEmpty().isEmpty()) {
      return false;
    }
    if (hasHopEdges(document)) {
      return false;
    }
    ExecutionMapNode hub = findHub(document);
    if (hub == null) {
      return false;
    }
    return !collectSpokeTargets(document, hub.getId()).isEmpty();
  }

  public static boolean applyIfNeeded(ExecutionMapDocument document) {
    if (!canUseHubSpokeLayout(document)) {
      return false;
    }
    ExecutionMapNode hub = findHub(document);
    Set<String> spokeIds = collectSpokeTargets(document, hub.getId());

    ElkLayout layout = ElkLayout.createForExecutionMap();
    int hubX = layout.getOriginX();
    int hubY = layout.getOriginY();
    hub.setLocation(hubX, hubY);

    int targetColumnX =
        hubX
            + ExecutionMapMetrics.NODE_WIDTH
            + ExecutionMapMetrics.HUB_GUTTER
            + ExecutionMapMetrics.BUS_LANE_WIDTH;
    List<ExecutionMapNode> spokes = new ArrayList<>();
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && spokeIds.contains(node.getId())) {
        spokes.add(node);
      }
    }
    spokes.sort(hubSpokeComparator());

    int targetY = hubY;
    for (ExecutionMapNode spoke : spokes) {
      spoke.setLocation(targetColumnX, targetY);
      targetY += ExecutionMapMetrics.NODE_HEIGHT + TARGET_VERTICAL_SPACING;
    }

    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || node.getLocation() == null) {
        continue;
      }
      if (hub.getId().equals(node.getId()) || spokeIds.contains(node.getId())) {
        continue;
      }
      node.setLocation(targetColumnX, targetY);
      targetY += ExecutionMapMetrics.NODE_HEIGHT + TARGET_VERTICAL_SPACING;
    }
    return true;
  }

  private static boolean hasHopEdges(ExecutionMapDocument document) {
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge != null && edge.getEdgeType() == ExecutionMapEdgeType.HOP) {
        return true;
      }
    }
    return false;
  }

  private static ExecutionMapNode findHub(ExecutionMapDocument document) {
    ExecutionMapNode fallback = null;
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      ExecutionMapNodeType type = node.getNodeType();
      if (type == ExecutionMapNodeType.ROOT_WORKFLOW
          || type == ExecutionMapNodeType.ROOT_PIPELINE) {
        return node;
      }
      if (fallback == null
          && (type == ExecutionMapNodeType.WORKFLOW || type == ExecutionMapNodeType.PIPELINE)) {
        fallback = node;
      }
    }
    return fallback;
  }

  private static Set<String> collectSpokeTargets(ExecutionMapDocument document, String hubId) {
    Set<String> spokeIds = new HashSet<>();
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || !hubId.equals(edge.getFromNodeId())) {
        continue;
      }
      if (isHubSpokeEdge(edge.getEdgeType()) && !Utils.isEmpty(edge.getToNodeId())) {
        spokeIds.add(edge.getToNodeId());
      }
    }
    return spokeIds;
  }

  private static boolean isHubSpokeEdge(ExecutionMapEdgeType edgeType) {
    return edgeType == ExecutionMapEdgeType.REFERENCES
        || edgeType == ExecutionMapEdgeType.GENERATES
        || edgeType == ExecutionMapEdgeType.MODEL_LINK;
  }

  private static Comparator<ExecutionMapNode> hubSpokeComparator() {
    return Comparator.comparing(
            ExecutionMapNode::getNodeType, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(
            node -> node.getName() != null ? node.getName() : "", String.CASE_INSENSITIVE_ORDER);
  }
}
