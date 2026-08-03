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

import java.util.List;
import org.junit.jupiter.api.Test;

class HopAiConversationSessionTest {

  @Test
  void recordsProposalsAndAppliedSummaries() {
    HopAiConversationSession session = new HopAiConversationSession();
    HopAiAdvisoryResponse response =
        HopAiProposalParser.parse(
            """
        Advice only.

        ```hop_proposals
        {"proposals":[{"type":"ADD_TRANSFORM","parameters":{"name":"X","transformPluginId":"Dummy","locationX":"1","locationY":"2"}}]}
        ```
        """);

    session.recordTurn("add dummy", "llm message", response);

    assertEquals(1, session.getTurns().size());
    assertEquals(1, session.getTurns().get(0).getProposals().size());
    assertFalse(session.getTurns().get(0).isProposalsDropped());

    HopAiProposal applied = session.getTurns().get(0).getProposals().get(0);
    session.recordApplied(0, List.of(applied));

    assertEquals(1, session.getGraphRevision());
    assertEquals(1, session.consumePendingAppliedSummaries().size());
    assertTrue(session.consumePendingAppliedSummaries().isEmpty());
  }
}
