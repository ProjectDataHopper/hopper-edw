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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HopAiProposalParserTest {

  @Test
  void parsesAdviceAndHopProposalsBlock() {
    String raw =
        """
        ## Suggestion
        Add a Filter Rows transform after the input.

        ```hop_proposals
        {
          "proposals": [
            {
              "id": "1",
              "description": "Add filter step",
              "riskLevel": "LOW",
              "type": "ADD_TRANSFORM",
              "parameters": {
                "transformPluginId": "FilterRows",
                "name": "Filter bad rows",
                "locationX": "320",
                "locationY": "120"
              }
            }
          ]
        }
        ```
        """;

    HopAiAdvisoryResponse response = HopAiProposalParser.parse(raw);

    assertTrue(response.getMarkdownAdvice().contains("Add a Filter Rows transform"));
    assertEquals(1, response.getProposals().size());
    assertEquals(HopAiProposal.Type.ADD_TRANSFORM, response.getProposals().get(0).getType());
    assertEquals("FilterRows", response.getProposals().get(0).parameter("transformPluginId"));
  }
}
