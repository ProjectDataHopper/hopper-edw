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
package org.hopper.edw.datavault.ai.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.junit.jupiter.api.Test;

class EdwJourneyAiAdvisorTest {

  @Test
  void listsScenariosAndLocation() {
    EdwJourneyAiAdvisor advisor = new EdwJourneyAiAdvisor();
    assertEquals(EdwAiAdvisorLocations.EDW_JOURNEY, advisor.getLocations()[0]);
    assertTrue(
        advisor.listScenarios().stream()
            .anyMatch(s -> EdwJourneyAiScenario.WHAT_NEXT.getCode().equals(s.getId())));
  }

  @Test
  void buildPromptIncludesGroupAndEmptyLayers() throws Exception {
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "retail",
            "catalog",
            List.of(),
            List.of(),
            List.of(
                new EdwJourneySnapshot.ModelRef(
                    "models/retail-360.hdv",
                    "retail-360",
                    "DATA_VAULT_MODEL",
                    List.of("hub_customer"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("No source model in this group"));

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(snapshot);
    request.setUserPrompt("What should I do next?");
    request.setScenarioId(EdwJourneyAiScenario.WHAT_NEXT.getCode());

    AiAdvisorPrompt prompt = new EdwJourneyAiAdvisor().buildPrompt(request);
    assertTrue(prompt.getUserPrompt().contains("What should I do next?"));
    assertTrue(prompt.getUserPrompt().contains("retail-360"));
    assertTrue(prompt.getUserPrompt().contains("No source model"));
  }
}
