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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.lineageview.backend.marquez.MarquezFacetParser;

/**
 * Builds a {@link LineageGraph} from OpenLineage RunEvent objects (file-folder and local-models).
 */
public final class OpenLineageEventGraphBuilder {

  private OpenLineageEventGraphBuilder() {}

  /** Dataset seed first, then job. Returns null if neither identity exists in the event set. */
  public static String resolveSeed(LineageQuery query, List<ObjectNode> events) {
    if (query == null) {
      return null;
    }
    LineageGraph index = build(events, null);
    if (query.getDataset() != null && query.getDataset().isComplete()) {
      String id = query.getDataset().toNodeId(LineageNodeKind.DATASET);
      if (index.findNode(id) != null) {
        return id;
      }
    }
    if (query.getJob() != null && query.getJob().isComplete()) {
      String id = query.getJob().toNodeId(LineageNodeKind.JOB);
      if (index.findNode(id) != null) {
        return id;
      }
    }
    return null;
  }

  public static LineageGraph build(List<ObjectNode> events, String seedNodeId) {
    Map<String, LineageNode> nodes = new LinkedHashMap<>();
    List<LineageEdge> edges = new ArrayList<>();
    Set<String> edgeKeys = new LinkedHashSet<>();
    if (events != null) {
      for (ObjectNode event : events) {
        addEvent(event, nodes, edges, edgeKeys);
      }
    }
    return LineageGraph.builder()
        .nodes(List.copyOf(nodes.values()))
        .edges(List.copyOf(edges))
        .seedNodeId(seedNodeId)
        .warnings(List.of())
        .build();
  }

  private static void addEvent(
      ObjectNode event,
      Map<String, LineageNode> nodes,
      List<LineageEdge> edges,
      Set<String> edgeKeys) {
    if (event == null) {
      return;
    }
    JsonNode job = event.path("job");
    String jobNs = text(job, "namespace");
    String jobName = text(job, "name");
    if (Utils.isEmpty(jobNs) || Utils.isEmpty(jobName)) {
      return;
    }
    String jobId = "job:" + jobNs + ":" + jobName;
    JsonNode runFacets = event.path("run").path("facets");
    HopExportFacet hopExport = MarquezFacetParser.hopExport(runFacets);
    HopOpsFacet hopOps = MarquezFacetParser.hopOps(runFacets);
    String eventTime = text(event, "eventTime");
    List<LineageWarning> warnings = new ArrayList<>();
    LineageGraphLayer layer =
        LineageLayerSupport.infer(LineageNodeKind.JOB, jobName, hopExport, null, warnings, jobId);
    nodes.put(
        jobId,
        LineageNode.builder()
            .id(jobId)
            .kind(LineageNodeKind.JOB)
            .namespace(jobNs)
            .name(jobName)
            .layer(layer)
            .hopExport(hopExport)
            .hopOps(hopOps)
            .lastExportedAt(eventTime)
            .warnings(List.copyOf(warnings))
            .build());

    addDatasets(event.path("inputs"), nodes, hopExport);
    addDatasets(event.path("outputs"), nodes, hopExport);
    link(event.path("inputs"), jobId, true, edges, edgeKeys);
    link(event.path("outputs"), jobId, false, edges, edgeKeys);
  }

  private static void addDatasets(
      JsonNode array, Map<String, LineageNode> nodes, HopExportFacet jobExport) {
    if (array == null || !array.isArray()) {
      return;
    }
    for (JsonNode dataset : array) {
      String ns = text(dataset, "namespace");
      String name = text(dataset, "name");
      if (Utils.isEmpty(ns) || Utils.isEmpty(name)) {
        continue;
      }
      String id = "dataset:" + ns + ":" + name;
      if (nodes.containsKey(id)) {
        continue;
      }
      JsonNode facets = dataset.path("facets");
      HopLocationFacet location = MarquezFacetParser.hopLocation(facets);
      List<String> fields = new ArrayList<>();
      JsonNode schemaFields = facets.path("schema").path("fields");
      if (schemaFields.isArray()) {
        for (JsonNode field : schemaFields) {
          String fieldName = text(field, "name");
          if (!Utils.isEmpty(fieldName)) {
            fields.add(fieldName);
          }
        }
      }
      List<LineageWarning> warnings = new ArrayList<>();
      LineageGraphLayer layer =
          LineageLayerSupport.infer(
              LineageNodeKind.DATASET, name, jobExport, location, warnings, id);
      nodes.put(
          id,
          LineageNode.builder()
              .id(id)
              .kind(LineageNodeKind.DATASET)
              .namespace(ns)
              .name(name)
              .layer(layer)
              .hopLocation(location)
              .schemaFieldNames(List.copyOf(fields))
              .warnings(List.copyOf(warnings))
              .build());
    }
  }

  private static void link(
      JsonNode array, String jobId, boolean inputs, List<LineageEdge> edges, Set<String> edgeKeys) {
    if (array == null || !array.isArray()) {
      return;
    }
    for (JsonNode dataset : array) {
      String ns = text(dataset, "namespace");
      String name = text(dataset, "name");
      if (Utils.isEmpty(ns) || Utils.isEmpty(name)) {
        continue;
      }
      String datasetId = "dataset:" + ns + ":" + name;
      String from = inputs ? datasetId : jobId;
      String to = inputs ? jobId : datasetId;
      String key = from + "\0" + to;
      if (edgeKeys.add(key)) {
        edges.add(LineageEdge.builder().fromNodeId(from).toNodeId(to).build());
      }
    }
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.isObject() || Utils.isEmpty(field) || !node.has(field)) {
      return null;
    }
    String value = node.get(field).asText(null);
    return Utils.isEmpty(value) ? null : value;
  }
}
