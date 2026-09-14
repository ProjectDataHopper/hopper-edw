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
package org.hopper.edw.datavault.ai.lineageview;

import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.diagram.exporter.EdwOpenDiagramGraphSupport;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;
import org.hopper.edw.datavault.lineageview.backend.HopOpsFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;

/** Compact lineage-view JSON for chat-only AI Help. */
public final class LineageViewAiContextBuilder {

  private static final int MAX_NODES = 80;

  private LineageViewAiContextBuilder() {}

  public static String definitionJson(HopLineageViewDocument document, String focusNodeName) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":")
        .append(DvAiContextBuilder.jsonString(document != null ? document.getName() : null));
    json.append(",\"backend\":")
        .append(DvAiContextBuilder.jsonString(document != null ? document.getBackendName() : null));
    json.append(",\"seedKind\":")
        .append(
            DvAiContextBuilder.jsonString(
                document != null && document.getSeedKind() != null
                    ? document.getSeedKind().name()
                    : null));
    json.append(",\"modelName\":")
        .append(DvAiContextBuilder.jsonString(document != null ? document.getModelName() : null));
    json.append(",\"logicalTable\":")
        .append(
            DvAiContextBuilder.jsonString(document != null ? document.getLogicalTable() : null));
    json.append(",\"direction\":")
        .append(
            DvAiContextBuilder.jsonString(
                document != null && document.getDirection() != null
                    ? document.getDirection().name()
                    : null));
    json.append(",\"depth\":").append(document != null ? document.getDepth() : 0);
    json.append(",\"includeOpsOverlay\":")
        .append(document != null && document.isIncludeOpsOverlay());
    json.append(",\"focusNode\":").append(DvAiContextBuilder.jsonString(focusNodeName));
    json.append('}');
    return json.toString();
  }

  public static String sessionGraphJson(HopLineageViewDocument document, AiAdvisorRequest request) {
    LineageGraph graph = liveGraph(document);
    if (graph == null) {
      String cached =
          EdwChatOnlyAdvisorSupport.attributeString(
              request, EdwChatOnlyAdvisorSupport.ATTR_SESSION_GRAPH_JSON);
      return cached;
    }
    return sessionGraphJson(graph);
  }

  public static String sessionGraphJson(LineageGraph graph) {
    if (graph == null) {
      return "";
    }
    StringBuilder json = new StringBuilder();
    json.append("{\"seedNodeId\":").append(DvAiContextBuilder.jsonString(graph.getSeedNodeId()));
    json.append(",\"nodeCount\":").append(graph.getNodesOrEmpty().size());
    json.append(",\"edgeCount\":").append(graph.getEdgesOrEmpty().size());
    json.append(",\"nodes\":[");
    int written = 0;
    for (LineageNode node : graph.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      if (written >= MAX_NODES) {
        break;
      }
      if (written > 0) {
        json.append(',');
      }
      written++;
      json.append("{\"id\":").append(DvAiContextBuilder.jsonString(node.getId()));
      json.append(",\"kind\":")
          .append(
              DvAiContextBuilder.jsonString(node.getKind() != null ? node.getKind().name() : null));
      json.append(",\"name\":").append(DvAiContextBuilder.jsonString(node.getName()));
      json.append(",\"layer\":")
          .append(
              DvAiContextBuilder.jsonString(
                  node.getLayer() != null ? node.getLayer().name() : null));
      HopOpsFacet ops = node.getHopOps();
      if (ops != null) {
        json.append(",\"ops\":{\"lastSuccessAt\":")
            .append(DvAiContextBuilder.jsonString(ops.getLastSuccessAt()));
        json.append(",\"durationMs\":").append(ops.getDurationMs());
        json.append(",\"pipelineName\":")
            .append(DvAiContextBuilder.jsonString(ops.getPipelineName()));
        json.append('}');
      }
      json.append('}');
    }
    json.append("],\"edges\":[");
    boolean first = true;
    int edgeCount = 0;
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (edge == null || edgeCount >= MAX_NODES) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      edgeCount++;
      json.append("{\"from\":").append(DvAiContextBuilder.jsonString(edge.getFromNodeId()));
      json.append(",\"to\":").append(DvAiContextBuilder.jsonString(edge.getToNodeId()));
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  public static String userPrompt(
      AiAdvisorRequest request, String definitionJson, String sessionGraphJson)
      throws HopException {
    EdwChatOnlyAdvisorSupport.requireQuestion(request);
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(request.getUserPrompt()).append("\n\n");
    if (!Utils.isEmpty(request.getFocusNodeName())) {
      prompt.append("Focus node: ").append(request.getFocusNodeName()).append("\n\n");
    }
    prompt.append("Lineage view definition JSON:\n").append(definitionJson).append("\n\n");
    if (!Utils.isEmpty(sessionGraphJson)) {
      prompt.append("Session graph JSON:\n").append(sessionGraphJson).append("\n\n");
    } else {
      prompt.append(
          "Session graph: not available (open the Lineage View tab and refresh to load the live graph).\n\n");
    }
    return prompt.toString();
  }

  private static LineageGraph liveGraph(HopLineageViewDocument document) {
    try {
      HopGuiLineageViewGraph graph = EdwOpenDiagramGraphSupport.findLineageView(document);
      return graph != null ? graph.getSessionGraph() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }
}
