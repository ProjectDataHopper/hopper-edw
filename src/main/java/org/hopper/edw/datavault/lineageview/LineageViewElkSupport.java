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
package org.hopper.edw.datavault.lineageview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.layout.ElkGraphLayout;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.layout.ElkLayoutAlgorithm;
import org.hopper.edw.datavault.layout.ElkLayoutBox;
import org.hopper.edw.datavault.layout.ElkLayoutDirection;
import org.hopper.edw.datavault.layout.ElkLayoutEdge;
import org.hopper.edw.datavault.layout.ElkLayoutNode;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;

/** ELK layered RIGHT layout for a lineage graph. */
public final class LineageViewElkSupport {

  public static final int NODE_WIDTH = 180;
  public static final int NODE_HEIGHT = 66;

  private LineageViewElkSupport() {}

  public static Map<String, ElkLayoutBox> layout(LineageGraph graph) throws HopException {
    if (graph == null || graph.getNodesOrEmpty().isEmpty()) {
      return new LinkedHashMap<>();
    }
    List<ElkLayoutNode> nodes = new ArrayList<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (node == null || node.getId() == null) {
        continue;
      }
      String label = node.getName() != null ? node.getName() : node.getId();
      nodes.add(new ElkLayoutNode(node.getId(), label, NODE_WIDTH, NODE_HEIGHT, null));
    }
    List<ElkLayoutEdge> edges = new ArrayList<>();
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (edge == null || edge.getFromNodeId() == null || edge.getToNodeId() == null) {
        continue;
      }
      edges.add(new ElkLayoutEdge(edge.getFromNodeId(), edge.getToNodeId()));
    }
    ElkLayout layout = ElkLayout.createDefault();
    layout.setAlgorithm(ElkLayoutAlgorithm.LAYERED);
    layout.setDirection(ElkLayoutDirection.RIGHT);
    layout.setMinNodeWidth(NODE_WIDTH);
    layout.setNodeHeight(NODE_HEIGHT);
    return ElkGraphLayout.fromLayoutGraph("lineage-view", nodes, edges).layoutToBoxes(layout);
  }
}
