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
package org.hopper.edw.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorInclusion;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorScenario;
import org.apache.hop.ai.advisor.AiProposal;
import org.apache.hop.ai.advisor.AiProposalValidation;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.junit.jupiter.api.Test;

class DataVaultAiAdvisorTest {

  private final DataVaultAiAdvisor advisor = new DataVaultAiAdvisor();

  @Test
  void listsDvScenariosAndCatalogPicker() {
    List<String> scenarioIds =
        advisor.listScenarios().stream().map(AiAdvisorScenario::getId).toList();
    assertTrue(scenarioIds.contains(DvAiScenario.GENERAL.getCode()));
    assertTrue(scenarioIds.contains(DvAiScenario.DV_MODELING.getCode()));
    assertTrue(scenarioIds.contains(DvAiScenario.PERFORMANCE_TUNING.getCode()));

    List<AiAdvisorInclusion> inclusions = advisor.listInclusions();
    assertTrue(inclusions.stream().anyMatch(i -> EdwAiInclusions.CHECKS.equals(i.getId())));
    AiAdvisorInclusion catalog =
        inclusions.stream()
            .filter(i -> EdwAiInclusions.CATALOG.equals(i.getId()))
            .findFirst()
            .orElseThrow();
    assertTrue(catalog.isPicker());
    assertTrue(catalog.isMultiSelect());
    assertFalse(catalog.isDefaultSelected());
  }

  @Test
  void listInclusionChoicesEmptyWithoutCatalog() {
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(new DataVaultModel());
    assertTrue(advisor.listInclusionChoices(EdwAiInclusions.CATALOG, request).isEmpty());
    assertTrue(advisor.listInclusionChoices("other", request).isEmpty());
  }

  @Test
  void buildPromptIncludesQuestionAndStructure() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("demo");
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(model);
    request.setUserPrompt("Suggest a hub for customer");
    request.setScenarioId(DvAiScenario.GENERAL.getCode());

    AiAdvisorPrompt prompt = advisor.buildPrompt(request);

    assertTrue(
        prompt.getSystemPrompt().contains("Data Vault") || prompt.getSystemPrompt().length() > 20);
    assertTrue(prompt.getUserPrompt().contains("Suggest a hub for customer"));
    assertTrue(prompt.getUserPrompt().contains("Model structure JSON"));
  }

  @Test
  void parseResponseExtractsEdwTypes() {
    String raw =
        """
        Done.

        ```dv_proposals
        {"proposals":[{"id":"1","type":"ADD_HUB","description":"Customer hub","parameters":{"tableName":"HUB_CUSTOMER"}}]}
        ```
        """;
    assertEquals(1, advisor.parseResponse(raw).getProposals().size());
    assertEquals("ADD_HUB", advisor.parseResponse(raw).getProposals().get(0).getType());
  }

  @Test
  void validateNoteAndSummarizeAddHub() {
    DataVaultModel model = new DataVaultModel();
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(model);

    AiProposal note = new AiProposal();
    note.setId("n1");
    note.setType("ADD_MODEL_NOTE");
    note.setDescription("Add a note");
    note.setParameters(java.util.Map.of("text", "hello"));

    List<AiProposalValidation> ok = advisor.validateProposals(request, List.of(note));
    assertEquals(1, ok.size());
    assertFalse(ok.get(0).isBlocked());

    AiProposal hub = new AiProposal();
    hub.setType("ADD_HUB");
    hub.setDescription("Customer hub");
    assertEquals("ADD_HUB: Customer hub", advisor.summarizeApplied(hub));
  }
}
