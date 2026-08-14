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
 */
package org.apache.hop.datavault.lineageview.backend.marquez;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.lineageview.backend.HopExportFacet;
import org.apache.hop.datavault.lineageview.backend.HopLocationFacet;
import org.apache.hop.datavault.lineageview.backend.HopOpsFacet;
import org.apache.hop.datavault.lineageview.backend.LineageEdge;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageGraphLayer;
import org.apache.hop.datavault.lineageview.backend.LineageLayerSupport;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.apache.hop.datavault.lineageview.backend.LineageWarning;
import org.apache.hop.datavault.lineageview.backend.OpenLineageRef;

/** Maps Marquez 0.50 {@code GET /api/v1/lineage} JSON onto {@link LineageGraph}. */
public final class MarquezLineageGraphParser {

  private MarquezLineageGraphParser() {}

  public static LineageGraph parse(JsonNode root, String seedNodeId) {
    Map<String, LineageNode> nodes = new LinkedHashMap<>();
    List<LineageEdge> edges = new ArrayList<>();
    Set<String> edgeKeys = new LinkedHashSet<>();
    if (root != null && root.path("graph").isArray()) {
      for (JsonNode item : root.path("graph")) {
        LineageNode node = parseNode(item);
        if (node == null) {
          continue;
        }
        nodes.putIfAbsent(node.getId(), node);
        addEdges(item.path("inEdges"), edges, edgeKeys);
        addEdges(item.path("outEdges"), edges, edgeKeys);
      }
    }
    return LineageGraph.builder()
        .nodes(List.copyOf(nodes.values()))
        .edges(List.copyOf(edges))
        .seedNodeId(seedNodeId)
        .warnings(List.of())
        .build();
  }

  static LineageNode parseNode(JsonNode item) {
    if (item == null || !item.isObject()) {
      return null;
    }
    String id = text(item, "id");
    LineageNodeKind kind = kindOf(item.path("type").asText(null), id);
    if (Utils.isEmpty(id) || kind == null) {
      return null;
    }
    JsonNode data = item.path("data");
    OpenLineageRef ref = OpenLineageRef.fromNodeId(id);
    String namespace = text(data, "namespace");
    String name = text(data, "name");
    if (Utils.isEmpty(namespace) && ref != null) {
      namespace = ref.getNamespace();
    }
    if (Utils.isEmpty(name) && ref != null) {
      name = ref.getName();
    }
    JsonNode facets = data.path("facets");
    if (!facets.isObject()) {
      facets = data.path("latestRun").path("facets");
    }
    HopExportFacet hopExport = MarquezFacetParser.hopExport(facets);
    HopLocationFacet hopLocation = MarquezFacetParser.hopLocation(facets);
    // hop_ops only from hop_ops facet — never latestRun.durationMs
    HopOpsFacet hopOps = MarquezFacetParser.hopOps(facets);
    List<LineageWarning> warnings = new ArrayList<>();
    LineageGraphLayer layer =
        LineageLayerSupport.infer(kind, name, hopExport, hopLocation, warnings, id);
    JsonNode latestRun = data.path("latestRun");
    String latestRunId = text(latestRun, "id");
    String lastExportedAt = text(latestRun, "endedAt");
    if (Utils.isEmpty(lastExportedAt)) {
      lastExportedAt = text(latestRun, "startedAt");
    }
    return LineageNode.builder()
        .id(id)
        .kind(kind)
        .namespace(namespace)
        .name(name)
        .layer(layer)
        .hopExport(hopExport)
        .hopLocation(hopLocation)
        .hopOps(hopOps)
        .schemaFieldNames(schemaNames(data))
        .lastExportedAt(lastExportedAt)
        .latestRunId(latestRunId)
        .warnings(List.copyOf(warnings))
        .build();
  }

  private static void addEdges(JsonNode array, List<LineageEdge> edges, Set<String> keys) {
    if (array == null || !array.isArray()) {
      return;
    }
    for (JsonNode edge : array) {
      String from = text(edge, "origin");
      String to = text(edge, "destination");
      if (Utils.isEmpty(from) || Utils.isEmpty(to)) {
        continue;
      }
      String key = from + "\0" + to;
      if (!keys.add(key)) {
        continue;
      }
      edges.add(LineageEdge.builder().fromNodeId(from).toNodeId(to).build());
    }
  }

  private static List<String> schemaNames(JsonNode data) {
    List<String> names = new ArrayList<>();
    JsonNode fields = data.path("fields");
    if (!fields.isArray()) {
      fields = data.path("facets").path("schema").path("fields");
    }
    if (fields.isArray()) {
      for (JsonNode field : fields) {
        String name = text(field, "name");
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
    }
    return List.copyOf(names);
  }

  private static LineageNodeKind kindOf(String type, String id) {
    if ("JOB".equalsIgnoreCase(type) || (id != null && id.startsWith("job:"))) {
      return LineageNodeKind.JOB;
    }
    if ("DATASET".equalsIgnoreCase(type) || (id != null && id.startsWith("dataset:"))) {
      return LineageNodeKind.DATASET;
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.isObject() || Utils.isEmpty(field) || !node.has(field)) {
      return null;
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText(null);
    return Utils.isEmpty(text) ? null : text;
  }
}
