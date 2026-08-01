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
 *
 */

package org.apache.hop.datavault.architecture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Canonical, Hop-derived architecture graph used as intermediate model for Draw.io / ArchiMate
 * exporters. Models remain the source of truth; this graph is a projection.
 */
@Getter
@Setter
public class ArchitectureGraph {

  private String name;
  private String description;
  private ArchitectureViewType viewType = ArchitectureViewType.SOLUTION;
  /**
   * When true, Draw.io export uses node x/y coordinates (ELK freeform) instead of swimlanes.
   * When false, layered swimlane packing is used.
   */
  private boolean freeformLayout;
  /**
   * When true, Draw.io omits relationship edges (DATA inventory: tables only, no ER spider).
   */
  private boolean omitEdges;
  private final Map<String, ArchitectureNode> nodesById = new LinkedHashMap<>();
  private final List<ArchitectureEdge> edges = new ArrayList<>();

  public ArchitectureNode addNode(ArchitectureNode node) {
    if (node == null || node.getId() == null) {
      return node;
    }
    nodesById.putIfAbsent(node.getId(), node);
    return nodesById.get(node.getId());
  }

  public ArchitectureNode getOrCreateNode(
      String id, String name, ArchitectureNodeKind kind, ArchitectureLayer layer) {
    ArchitectureNode existing = nodesById.get(id);
    if (existing != null) {
      return existing;
    }
    ArchitectureNode node = new ArchitectureNode(id, name, kind);
    node.setLayer(layer);
    nodesById.put(id, node);
    return node;
  }

  public void addEdge(ArchitectureEdge edge) {
    if (edge == null
        || edge.getFromNodeId() == null
        || edge.getToNodeId() == null
        || !nodesById.containsKey(edge.getFromNodeId())
        || !nodesById.containsKey(edge.getToNodeId())) {
      return;
    }
    for (ArchitectureEdge existing : edges) {
      if (existing.getFromNodeId().equals(edge.getFromNodeId())
          && existing.getToNodeId().equals(edge.getToNodeId())
          && existing.getKind() == edge.getKind()) {
        return;
      }
    }
    if (edge.getId() == null) {
      edge.setId("e-" + edges.size() + "-" + edge.getFromNodeId() + "-" + edge.getToNodeId());
    }
    edges.add(edge);
  }

  public void addEdge(
      String fromId, String toId, ArchitectureEdgeKind kind, String label) {
    addEdge(new ArchitectureEdge(null, fromId, toId, kind, label));
  }

  public Collection<ArchitectureNode> getNodes() {
    return nodesById.values();
  }

  public ArchitectureNode findNode(String id) {
    return nodesById.get(id);
  }

  public int nodeCount() {
    return nodesById.size();
  }

  public int edgeCount() {
    return edges.size();
  }
}
