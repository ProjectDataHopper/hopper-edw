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
package org.hopper.edw.datavault.lineageview.backend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/**
 * Shared clip / depth / hide-jobs / layer filter. The only place adapters' raw graphs are reduced.
 */
public final class LineageGraphOps {

  private LineageGraphOps() {}

  /**
   * Apply order: validate seed → clip direction → depth → hide-jobs → layer filter.
   *
   * @throws HopException {@link ILineageQueryService#SEED_NOT_FOUND} if the seed is missing
   */
  public static LineageGraph apply(LineageGraph graph, LineageQuery query) throws HopException {
    if (query == null) {
      throw new HopException("Lineage query is required");
    }
    if (graph == null
        || Utils.isEmpty(graph.getSeedNodeId())
        || graph.findNode(graph.getSeedNodeId()) == null) {
      throw new HopException(ILineageQueryService.SEED_NOT_FOUND + ": graph has no seed node");
    }
    LineageGraph clipped = clipDirection(graph, query.getDirection());
    LineageGraph depthLimited = applyDepth(clipped, query.getDepth());
    LineageGraph jobs = query.isIncludeJobs() ? depthLimited : hideJobs(depthLimited);
    return filterLayers(jobs, query.getLayerFilters());
  }

  static LineageGraph clipDirection(LineageGraph graph, LineageDirection direction) {
    LineageDirection dir = direction != null ? direction : LineageDirection.UPSTREAM;
    String seed = graph.getSeedNodeId();
    Set<String> kept = new LinkedHashSet<>();
    kept.add(seed);
    if (dir == LineageDirection.UPSTREAM || dir == LineageDirection.BOTH) {
      kept.addAll(walk(graph, seed, true));
    }
    if (dir == LineageDirection.DOWNSTREAM || dir == LineageDirection.BOTH) {
      kept.addAll(walk(graph, seed, false));
    }
    return subgraph(graph, kept, List.of());
  }

  static LineageGraph applyDepth(LineageGraph graph, int depth) {
    int limit = depth > 0 ? depth : 6;
    String seed = graph.getSeedNodeId();
    Map<String, Integer> distance = undirectedDistance(graph, seed);
    Set<String> kept = new LinkedHashSet<>();
    boolean clipped = false;
    for (LineageNode node : graph.getNodesOrEmpty()) {
      Integer d = distance.get(node.getId());
      if (d != null && d <= limit) {
        kept.add(node.getId());
      } else if (d != null) {
        clipped = true;
      }
    }
    List<LineageWarning> extra = new ArrayList<>();
    if (clipped) {
      extra.add(
          LineageWarning.builder()
              .code(LineageWarning.DEPTH_CLIPPED)
              .message("Dropped nodes farther than depth " + limit + " from the seed")
              .nodeId(seed)
              .build());
    }
    return subgraph(graph, kept, extra);
  }

  static LineageGraph hideJobs(LineageGraph graph) {
    Map<String, LineageNode> byId = index(graph);
    List<LineageEdge> next = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Set<String> dropJobs = new HashSet<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (node.getKind() != LineageNodeKind.JOB) {
        continue;
      }
      dropJobs.add(node.getId());
      List<LineageEdge> ins = new ArrayList<>();
      List<LineageEdge> outs = new ArrayList<>();
      for (LineageEdge edge : graph.getEdgesOrEmpty()) {
        if (node.getId().equals(edge.getToNodeId())) {
          LineageNode from = byId.get(edge.getFromNodeId());
          if (from != null && from.getKind() == LineageNodeKind.DATASET) {
            ins.add(edge);
          }
        }
        if (node.getId().equals(edge.getFromNodeId())) {
          LineageNode to = byId.get(edge.getToNodeId());
          if (to != null && to.getKind() == LineageNodeKind.DATASET) {
            outs.add(edge);
          }
        }
      }
      for (LineageEdge in : ins) {
        for (LineageEdge out : outs) {
          if (in.getFromNodeId().equals(out.getToNodeId())) {
            continue;
          }
          String key = in.getFromNodeId() + "\0" + out.getToNodeId() + "\0" + node.getId();
          if (!seen.add(key)) {
            continue;
          }
          next.add(
              LineageEdge.builder()
                  .fromNodeId(in.getFromNodeId())
                  .toNodeId(out.getToNodeId())
                  .viaJobId(node.getId())
                  .build());
        }
      }
    }
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (dropJobs.contains(edge.getFromNodeId()) || dropJobs.contains(edge.getToNodeId())) {
        continue;
      }
      String key =
          edge.getFromNodeId()
              + "\0"
              + edge.getToNodeId()
              + "\0"
              + String.valueOf(edge.getViaJobId());
      if (seen.add(key)) {
        next.add(edge);
      }
    }
    Set<String> kept = new LinkedHashSet<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (!dropJobs.contains(node.getId())) {
        kept.add(node.getId());
      }
    }
    return LineageGraph.builder()
        .nodes(filterNodes(graph, kept))
        .edges(List.copyOf(next))
        .seedNodeId(graph.getSeedNodeId())
        .warnings(graph.getWarnings())
        .build();
  }

  static LineageGraph filterLayers(LineageGraph graph, List<LineageGraphLayer> filters) {
    if (filters == null || filters.isEmpty()) {
      return graph;
    }
    Set<LineageGraphLayer> allow = new HashSet<>(filters);
    Set<String> drop = new LinkedHashSet<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (node.getLayer() == null || !allow.contains(node.getLayer())) {
        drop.add(node.getId());
      }
    }
    if (drop.isEmpty()) {
      return graph;
    }
    Set<String> kept = new LinkedHashSet<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (!drop.contains(node.getId())) {
        kept.add(node.getId());
      }
    }
    List<LineageWarning> extra = new ArrayList<>();
    extra.add(
        LineageWarning.builder()
            .code(LineageWarning.LAYER_DROPPED)
            .message("Dropped " + drop.size() + " node(s) not in the selected layers")
            .build());
    return subgraph(graph, kept, extra);
  }

  private static Set<String> walk(LineageGraph graph, String seed, boolean upstream) {
    Set<String> visited = new LinkedHashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(seed);
    visited.add(seed);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      for (LineageEdge edge : graph.getEdgesOrEmpty()) {
        String next;
        if (upstream && current.equals(edge.getToNodeId())) {
          next = edge.getFromNodeId();
        } else if (!upstream && current.equals(edge.getFromNodeId())) {
          next = edge.getToNodeId();
        } else {
          continue;
        }
        if (next != null && visited.add(next)) {
          queue.add(next);
        }
      }
    }
    return visited;
  }

  private static Map<String, Integer> undirectedDistance(LineageGraph graph, String seed) {
    Map<String, Integer> distance = new HashMap<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    distance.put(seed, 0);
    queue.add(seed);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      int d = distance.get(current);
      for (LineageEdge edge : graph.getEdgesOrEmpty()) {
        String other = null;
        if (current.equals(edge.getFromNodeId())) {
          other = edge.getToNodeId();
        } else if (current.equals(edge.getToNodeId())) {
          other = edge.getFromNodeId();
        }
        if (other != null && !distance.containsKey(other)) {
          distance.put(other, d + 1);
          queue.add(other);
        }
      }
    }
    return distance;
  }

  private static LineageGraph subgraph(
      LineageGraph graph, Set<String> kept, List<LineageWarning> extraWarnings) {
    List<LineageEdge> edges = new ArrayList<>();
    Set<String> edgeKeys = new HashSet<>();
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (kept.contains(edge.getFromNodeId()) && kept.contains(edge.getToNodeId())) {
        String key =
            edge.getFromNodeId()
                + "\0"
                + edge.getToNodeId()
                + "\0"
                + String.valueOf(edge.getViaJobId());
        if (edgeKeys.add(key)) {
          edges.add(edge);
        }
      }
    }
    List<LineageWarning> warnings = new ArrayList<>();
    if (graph.getWarnings() != null) {
      warnings.addAll(graph.getWarnings());
    }
    warnings.addAll(extraWarnings);
    return LineageGraph.builder()
        .nodes(filterNodes(graph, kept))
        .edges(List.copyOf(edges))
        .seedNodeId(graph.getSeedNodeId())
        .warnings(List.copyOf(warnings))
        .build();
  }

  private static List<LineageNode> filterNodes(LineageGraph graph, Set<String> kept) {
    List<LineageNode> nodes = new ArrayList<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (kept.contains(node.getId())) {
        nodes.add(node);
      }
    }
    return List.copyOf(nodes);
  }

  private static Map<String, LineageNode> index(LineageGraph graph) {
    Map<String, LineageNode> map = new LinkedHashMap<>();
    for (LineageNode node : graph.getNodesOrEmpty()) {
      map.put(node.getId(), node);
    }
    return map;
  }
}
