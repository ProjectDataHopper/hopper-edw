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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class DvAiConversationSessionTest {

  @Test
  void recordsTurnAndBuildsHistoryWithoutProposalBlocks() {
    DvAiConversationSession session = new DvAiConversationSession();
    DvAiResponse response = new DvAiResponse();
    response.setRawResponse(
        """
        Advice here.

        ```dv_proposals
        {"proposals":[{"id":"1","type":"ADD_MODEL_NOTE","parameters":{"text":"note"}}]}
        ```
        """);
    response.setMarkdownAdvice("Advice here.");
    response.setProposals(List.of(new DvAiProposal()));

    session.recordTurn("First question", "User question:\nFirst question\n", response);

    assertEquals(1, session.getTurns().size());
    assertEquals("Advice here.", session.getTurns().get(0).getAssistantAdvice());
    assertEquals(2, session.buildLlmHistory().size());
    assertTrue(session.buildLlmHistory().get(0) instanceof UserMessage);
    assertTrue(session.buildLlmHistory().get(1) instanceof AiMessage);
    assertFalse(((AiMessage) session.buildLlmHistory().get(1)).text().contains("dv_proposals"));
  }

  @Test
  void truncatesHistoryToMaxTurns() {
    DvAiConversationSession session = new DvAiConversationSession();
    for (int i = 0; i < DvAiConversationSession.MAX_HISTORY_TURNS + 2; i++) {
      DvAiResponse response = new DvAiResponse();
      response.setMarkdownAdvice("answer " + i);
      session.recordTurn("q" + i, "prompt " + i, response);
    }
    assertEquals(DvAiConversationSession.MAX_HISTORY_TURNS * 2, session.buildLlmHistory().size());
  }

  @Test
  void recordsAppliedSummariesForFollowUp() {
    DvAiConversationSession session = new DvAiConversationSession();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.RENAME_TABLE);
    proposal.setParameters(java.util.Map.of("tableName", "Hub", "newName", "HUB_CUSTOMER"));

    session.recordApplied(0, List.of(proposal));

    assertEquals(1, session.consumePendingAppliedSummaries().size());
    assertTrue(session.consumePendingAppliedSummaries().isEmpty());
    assertEquals(
        "RENAME_TABLE: Hub -> HUB_CUSTOMER",
        DvAiConversationSession.formatAppliedSummary(proposal));
  }
}
