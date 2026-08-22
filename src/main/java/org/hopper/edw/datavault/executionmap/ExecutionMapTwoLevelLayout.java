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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Lays out a focused parent node and its direct children in two columns. */
public final class ExecutionMapTwoLevelLayout {

  private ExecutionMapTwoLevelLayout() {}

  public static void layout(
      ExecutionMapDocument document,
      ExecutionMapFocusContext focus,
      Map<String, ExecutionMapNodeCardMetrics> cardMetricsById) {
    if (document == null) {
      return;
    }
    List<ExecutionMapNode> visible = ExecutionMapViewFilter.getVisibleNodes(document, focus);
    if (visible.isEmpty()) {
      return;
    }
    ExecutionMapNode parent = visible.get(0);
    List<ExecutionMapNode> children = new ArrayList<>(visible.subList(1, visible.size()));

    if (DatasetNodeSupport.isModelNodeType(parent.getNodeType())) {
      ExecutionMapModelPipelineLayout.layout(document, parent, children, cardMetricsById);
      return;
    }

    children.sort(nodeComparator());

    ElkLayout defaults = ElkLayout.createForExecutionMap();
    int originX = defaults.getOriginX();
    int originY = defaults.getOriginY();
    int gutter = ExecutionMapMetrics.HUB_GUTTER + ExecutionMapMetrics.BUS_LANE_WIDTH;

    int parentWidth = cardWidth(parent, cardMetricsById);
    int parentHeight = cardHeight(parent, cardMetricsById);

    int childColumnX = originX + parentWidth + gutter;
    int totalChildHeight = 0;
    List<Integer> childHeights = new ArrayList<>();
    for (ExecutionMapNode child : children) {
      int height = cardHeight(child, cardMetricsById);
      childHeights.add(height);
      totalChildHeight += height;
      if (!children.isEmpty()) {
        totalChildHeight += ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
      }
    }
    if (!children.isEmpty()) {
      totalChildHeight -= ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
    }

    int parentY = originY;
    if (!children.isEmpty()) {
      parentY = originY + Math.max(0, (totalChildHeight - parentHeight) / 2);
    }
    parent.setLocation(originX, parentY);

    int childY = originY;
    for (int i = 0; i < children.size(); i++) {
      ExecutionMapNode child = children.get(i);
      child.setLocation(childColumnX, childY);
      childY += childHeights.get(i) + ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
    }
  }

  public static Map<String, ExecutionMapNodeCardMetrics> defaultCardMetrics(
      List<ExecutionMapNode> nodes) {
    Map<String, ExecutionMapNodeCardMetrics> metrics = new HashMap<>();
    if (nodes == null) {
      return metrics;
    }
    for (ExecutionMapNode node : nodes) {
      if (node != null && !Utils.isEmpty(node.getId())) {
        metrics.put(node.getId(), ExecutionMapNodeCardMetrics.defaultFor(node));
      }
    }
    return metrics;
  }

  private static int cardWidth(
      ExecutionMapNode node, Map<String, ExecutionMapNodeCardMetrics> metrics) {
    ExecutionMapNodeCardMetrics card = metrics != null ? metrics.get(node.getId()) : null;
    return card != null ? card.width() : ExecutionMapMetrics.MIN_NODE_WIDTH;
  }

  private static int cardHeight(
      ExecutionMapNode node, Map<String, ExecutionMapNodeCardMetrics> metrics) {
    ExecutionMapNodeCardMetrics card = metrics != null ? metrics.get(node.getId()) : null;
    return card != null ? card.height() : ExecutionMapMetrics.MIN_NODE_HEIGHT;
  }

  private static Comparator<ExecutionMapNode> nodeComparator() {
    return Comparator.comparing(
            ExecutionMapNode::getNodeType, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(
            node -> node.getName() != null ? node.getName() : "", String.CASE_INSENSITIVE_ORDER);
  }
}
