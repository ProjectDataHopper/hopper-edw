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
package org.hopper.edw.datavault.ai.executionmap;

import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.DvAiContextBuilder;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Compact execution-map JSON for chat-only AI Help. */
public final class ExecutionMapAiContextBuilder {

  private static final int MAX_NODES = 120;

  private ExecutionMapAiContextBuilder() {}

  public static String structureJson(ExecutionMapDocument document, String focusNodeName) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":")
        .append(DvAiContextBuilder.jsonString(document != null ? document.getName() : null));
    json.append(",\"rootArtifactPath\":")
        .append(
            DvAiContextBuilder.jsonString(
                document != null ? document.getRootArtifactPath() : null));
    json.append(",\"focusNode\":").append(DvAiContextBuilder.jsonString(focusNodeName));
    json.append(",\"nodes\":[");
    int written = 0;
    if (document != null) {
      for (ExecutionMapNode node : document.getNodesOrEmpty()) {
        if (node == null || Utils.isEmpty(node.getId()) || skip(node.getNodeType())) {
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
        json.append(",\"name\":").append(DvAiContextBuilder.jsonString(node.getName()));
        json.append(",\"type\":")
            .append(
                DvAiContextBuilder.jsonString(
                    node.getNodeType() != null ? node.getNodeType().name() : null));
        json.append(",\"path\":").append(DvAiContextBuilder.jsonString(node.getPath()));
        json.append('}');
      }
    }
    json.append("],\"edges\":[");
    boolean first = true;
    if (document != null) {
      for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
        if (edge == null) {
          continue;
        }
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append("{\"from\":").append(DvAiContextBuilder.jsonString(edge.getFromNodeId()));
        json.append(",\"to\":").append(DvAiContextBuilder.jsonString(edge.getToNodeId()));
        json.append(",\"type\":")
            .append(
                DvAiContextBuilder.jsonString(
                    edge.getEdgeType() != null ? edge.getEdgeType().name() : null));
        json.append('}');
      }
    }
    json.append("]}");
    return json.toString();
  }

  public static String userPrompt(AiAdvisorRequest request, String structureJson)
      throws HopException {
    EdwChatOnlyAdvisorSupport.requireQuestion(request);
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(request.getUserPrompt()).append("\n\n");
    if (!Utils.isEmpty(request.getFocusNodeName())) {
      prompt.append("Focus node: ").append(request.getFocusNodeName()).append("\n\n");
    }
    prompt.append("Execution map structure JSON:\n").append(structureJson).append("\n\n");
    return prompt.toString();
  }

  private static boolean skip(ExecutionMapNodeType type) {
    if (type == null) {
      return true;
    }
    return switch (type) {
      case PIPELINE_TRANSFORM,
              MAPPING,
              META_INJECT,
              PIPELINE,
              ROOT_PIPELINE,
              PIPELINE_FILE,
              GENERATED_PIPELINE,
              ORCHESTRATOR_PIPELINE,
              PIPELINE_EXECUTOR,
              WORKFLOW_EXECUTOR ->
          true;
      default -> false;
    };
  }
}
