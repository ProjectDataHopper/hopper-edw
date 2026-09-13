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

import java.util.Map;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.ai.advisor.AiProposal;
import org.junit.jupiter.api.Test;

class EdwAiProposalSupportTest {

  @Test
  void roundTripsTypeRiskAndParameters() {
    DvAiProposal source = new DvAiProposal();
    source.setId("1");
    source.setDescription("Add customer hub");
    source.setType(DvAiProposal.Type.ADD_HUB);
    source.setRiskLevel(DvAiProposal.RiskLevel.LOW);
    source.setParameters(Map.of("tableName", "HUB_CUSTOMER"));

    AiProposal ai = EdwAiProposalSupport.toAi(source);
    DvAiProposal back = EdwAiProposalSupport.fromAi(ai);

    assertEquals("1", back.getId());
    assertEquals(DvAiProposal.Type.ADD_HUB, back.getType());
    assertEquals(DvAiProposal.RiskLevel.LOW, back.getRiskLevel());
    assertEquals("HUB_CUSTOMER", back.parameter("tableName"));
  }

  @Test
  void parseEdwResponseReadsDvAndHopFences() {
    String raw =
        """
        Advice text.

        ```dv_proposals
        {"proposals":[{"id":"dv","type":"ADD_MODEL_NOTE","description":"Note","parameters":{"text":"hello"}}]}
        ```

        ```hop_proposals
        {"proposals":[{"id":"hop","type":"RENAME_TABLE","description":"Rename","parameters":{"oldName":"A","newName":"B"}}]}
        ```
        """;

    AiAdvisorResponse response = EdwAiProposalSupport.parseEdwResponse(raw);

    assertTrue(response.getMarkdownAdvice().contains("Advice text"));
    assertFalse(response.getMarkdownAdvice().contains("dv_proposals"));
    assertFalse(response.getMarkdownAdvice().contains("hop_proposals"));
    assertTrue(response.isProposalBlockPresent());
    assertEquals(2, response.getProposals().size());
  }
}
