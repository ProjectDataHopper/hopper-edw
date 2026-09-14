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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class LineageViewAiAdvisorTest {

  @Test
  void listsScenariosAndLocation() {
    LineageViewAiAdvisor advisor = new LineageViewAiAdvisor();
    assertEquals(EdwAiAdvisorLocations.LINEAGE_VIEW, advisor.getLocations()[0]);
    assertTrue(
        advisor.listScenarios().stream()
            .anyMatch(s -> LineageViewAiScenario.EXPLAIN_GRAPH.getCode().equals(s.getId())));
  }

  @Test
  void buildPromptIncludesDefinitionAndCachedGraph() throws Exception {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("orders-lineage");
    document.setBackendName("local-models");
    document.setModelName("retail-360");

    LineageGraph graph =
        LineageGraph.builder()
            .seedNodeId("dataset:hop:f_orders")
            .nodes(
                java.util.List.of(
                    LineageNode.builder()
                        .id("dataset:hop:f_orders")
                        .kind(LineageNodeKind.DATASET)
                        .name("f_orders")
                        .build()))
            .build();

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(document);
    request.setUserPrompt("Explain this graph");
    request
        .getAttributes()
        .put(
            EdwChatOnlyAdvisorSupport.ATTR_SESSION_GRAPH_JSON,
            LineageViewAiContextBuilder.sessionGraphJson(graph));

    AiAdvisorPrompt prompt = new LineageViewAiAdvisor().buildPrompt(request);
    assertTrue(prompt.getUserPrompt().contains("Explain this graph"));
    assertTrue(prompt.getUserPrompt().contains("retail-360"));
    assertTrue(prompt.getUserPrompt().contains("f_orders"));
  }
}
