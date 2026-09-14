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
package org.hopper.edw.datavault.ai.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceModelAiAdvisorTest {

  @Test
  void listsScenariosAndLocation() {
    SourceModelAiAdvisor advisor = new SourceModelAiAdvisor();
    assertEquals(EdwAiAdvisorLocations.SOURCE_MODEL_GRAPH, advisor.getLocations()[0]);
    assertTrue(
        advisor.listScenarios().stream()
            .anyMatch(s -> SourceModelAiScenario.SOURCE_ANALYSIS.getCode().equals(s.getId())));
    assertTrue(advisor.listBaselineSharing().stream().anyMatch(s -> s.contains("structure")));
  }

  @Test
  void buildPromptIncludesStructureAndClassification() throws Exception {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourceTable table = new SourceTable("customer");
    SourceColumn id = new SourceColumn("id");
    id.setPrimaryKeyPosition(1);
    table.getColumns().add(id);
    model.getTables().add(table);

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(model);
    request.setUserPrompt("Which tables should be hubs?");
    request.setScenarioId(SourceModelAiScenario.GENERATE_TO_VAULT.getCode());

    AiAdvisorPrompt prompt = new SourceModelAiAdvisor().buildPrompt(request);
    assertTrue(prompt.getSystemPrompt().contains("advisory chat only"));
    assertTrue(prompt.getUserPrompt().contains("Which tables should be hubs?"));
    assertTrue(prompt.getUserPrompt().contains("customer"));
    assertTrue(prompt.getUserPrompt().contains("classification JSON"));
  }

  @Test
  void parseResponseDropsProposalFences() {
    AiAdvisorResponse response =
        new SourceModelAiAdvisor()
            .parseResponse(
                """
                Use customer as a hub.

                ```hop_proposals
                {"proposals":[{"id":"1","type":"ADD_HUB"}]}
                ```
                """);
    assertTrue(response.getMarkdownAdvice().contains("Use customer as a hub"));
    assertTrue(response.getProposals().isEmpty());
    assertEquals(false, response.isProposalBlockPresent());
  }
}
