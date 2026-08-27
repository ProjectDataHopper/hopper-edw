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
package org.hopper.edw.datavault.impact;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.catalog.model.RecordDefinitionKey;

/** Immutable directed graph of source → DV → BV → DM dependencies. */
public final class ImpactGraph {

  private final Map<String, ImpactNode> nodesById;
  private final Map<String, List<ImpactEdge>> outgoing;

  public ImpactGraph(Map<String, ImpactNode> nodesById, Map<String, List<ImpactEdge>> outgoing) {
    this.nodesById = Map.copyOf(nodesById);
    Map<String, List<ImpactEdge>> edges = new LinkedHashMap<>();
    for (Map.Entry<String, List<ImpactEdge>> entry : outgoing.entrySet()) {
      edges.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    this.outgoing = Collections.unmodifiableMap(edges);
  }

  public static ImpactGraph empty() {
    return new ImpactGraph(Map.of(), Map.of());
  }

  public Collection<ImpactNode> nodes() {
    return nodesById.values();
  }

  public List<ImpactEdge> edges() {
    List<ImpactEdge> all = new ArrayList<>();
    for (List<ImpactEdge> list : outgoing.values()) {
      all.addAll(list);
    }
    return List.copyOf(all);
  }

  public ImpactNode node(String id) {
    return nodesById.get(id);
  }

  public List<ImpactEdge> outgoing(String nodeId) {
    return outgoing.getOrDefault(nodeId, List.of());
  }

  /**
   * Downstream nodes reachable from {@code start}, excluding the start node itself, in BFS order
   * with de-duplication.
   */
  public List<ImpactNode> downstream(ImpactNode start) {
    if (start == null || !nodesById.containsKey(start.id())) {
      return List.of();
    }
    List<ImpactNode> result = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    Queue<String> queue = new ArrayDeque<>();
    queue.add(start.id());
    visited.add(start.id());
    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (ImpactEdge edge : outgoing(current)) {
        if (visited.add(edge.toId())) {
          ImpactNode node = nodesById.get(edge.toId());
          if (node != null) {
            result.add(node);
            queue.add(edge.toId());
          }
        }
      }
    }
    return List.copyOf(result);
  }

  /**
   * Blast radius for a catalog source. When {@code fieldName} is set, starts from the source field
   * node (and still walks table-level edges from the source object). When empty, starts from the
   * source object only.
   */
  public List<ImpactNode> blastRadius(RecordDefinitionKey sourceKey, String fieldName) {
    if (sourceKey == null || Utils.isEmpty(sourceKey.getName())) {
      return List.of();
    }
    Set<ImpactNode> combined = new LinkedHashSet<>();
    ImpactNode objectNode = findSourceObject(sourceKey);
    if (!Utils.isEmpty(fieldName)) {
      ImpactNode fieldNode = findSourceField(sourceKey, fieldName);
      if (fieldNode != null) {
        combined.addAll(downstream(fieldNode));
      }
      // Also include table-level dependents of the source object (e.g. unmapped object edges).
      if (objectNode != null) {
        for (ImpactNode node : downstream(objectNode)) {
          if (node.kind() == ImpactNodeKind.DV_TABLE
              || node.kind() == ImpactNodeKind.BV_TABLE
              || node.kind() == ImpactNodeKind.DM_TABLE) {
            combined.add(node);
          }
        }
      }
    } else if (objectNode != null) {
      combined.addAll(downstream(objectNode));
    }
    return List.copyOf(combined);
  }

  /** Compact labels for reports, e.g. {@code hub_customer; sat_orders.email; customer_360_bv}. */
  public String formatBlastRadiusLabels(RecordDefinitionKey sourceKey, String fieldName) {
    List<ImpactNode> nodes = blastRadius(sourceKey, fieldName);
    if (nodes.isEmpty()) {
      return "";
    }
    // Prefer field-level and skip pure source nodes.
    List<String> labels =
        nodes.stream()
            .filter(
                n ->
                    n.kind() != ImpactNodeKind.SOURCE_OBJECT
                        && n.kind() != ImpactNodeKind.SOURCE_FIELD)
            .map(ImpactNode::displayLabel)
            .filter(label -> !Utils.isEmpty(label))
            .distinct()
            .collect(Collectors.toList());
    return String.join("; ", labels);
  }

  private ImpactNode findSourceObject(RecordDefinitionKey key) {
    for (ImpactNode node : nodesById.values()) {
      if (node.kind() == ImpactNodeKind.SOURCE_OBJECT && matchesSource(node, key)) {
        return node;
      }
    }
    return null;
  }

  private ImpactNode findSourceField(RecordDefinitionKey key, String fieldName) {
    for (ImpactNode node : nodesById.values()) {
      if (node.kind() == ImpactNodeKind.SOURCE_FIELD
          && matchesSource(node, key)
          && Objects.equals(normalize(fieldName), normalize(node.fieldName()))) {
        return node;
      }
    }
    return null;
  }

  private static boolean matchesSource(ImpactNode node, RecordDefinitionKey key) {
    if (key == null || node == null) {
      return false;
    }
    boolean nameMatch = Objects.equals(normalize(key.getName()), normalize(node.sourceName()));
    if (!nameMatch) {
      return false;
    }
    if (Utils.isEmpty(key.getNamespace()) || Utils.isEmpty(node.sourceNamespace())) {
      return true;
    }
    return Objects.equals(normalize(key.getNamespace()), normalize(node.sourceNamespace()));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
