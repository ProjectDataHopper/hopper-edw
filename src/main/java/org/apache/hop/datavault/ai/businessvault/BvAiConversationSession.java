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
package org.apache.hop.datavault.ai.businessvault;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.ai.DvAiProposalParser;
import org.apache.hop.datavault.ai.DvAiResponse;
import org.apache.hop.datavault.ai.DvAiTurn;

/** Multi-turn Business Vault AI advisory session state. */
public class BvAiConversationSession {

  public static final int MAX_HISTORY_TURNS = 6;

  private BvAiScenario scenario;
  private final List<DvAiTurn> turns = new ArrayList<>();
  private final List<String> pendingAppliedSummaries = new ArrayList<>();
  private int modelRevision;

  public BvAiScenario getScenario() {
    return scenario;
  }

  public void setScenario(BvAiScenario scenario) {
    this.scenario = scenario;
  }

  public List<DvAiTurn> getTurns() {
    return turns;
  }

  public boolean isEmpty() {
    return turns.isEmpty();
  }

  public void clear() {
    turns.clear();
    pendingAppliedSummaries.clear();
    modelRevision = 0;
  }

  public List<String> consumePendingAppliedSummaries() {
    List<String> copy = List.copyOf(pendingAppliedSummaries);
    pendingAppliedSummaries.clear();
    return copy;
  }

  public void recordTurn(String userPrompt, String llmUserMessage, DvAiResponse response) {
    DvAiTurn turn = new DvAiTurn();
    turn.setUserPrompt(userPrompt);
    turn.setLlmUserMessage(llmUserMessage);
    String raw = response != null ? response.getRawResponse() : null;
    turn.setProposalBlockPresent(DvAiProposalParser.hasProposalBlock(raw));
    turn.setProposalsDropped(
        turn.isProposalBlockPresent()
            && (response == null
                || response.getProposals() == null
                || response.getProposals().isEmpty()));
    turn.setAssistantAdvice(
        response != null && response.getMarkdownAdvice() != null
            ? response.getMarkdownAdvice()
            : "");
    if (response != null && response.getProposals() != null) {
      turn.getProposals().addAll(response.getProposals());
    }
    turns.add(turn);
  }

  public void recordApplied(int turnIndex, List<DvAiProposal> applied) {
    if (applied == null || applied.isEmpty()) {
      return;
    }
    List<String> summaries = new ArrayList<>();
    for (DvAiProposal proposal : applied) {
      summaries.add(formatAppliedSummary(proposal));
    }
    pendingAppliedSummaries.addAll(summaries);
    if (turnIndex >= 0 && turnIndex < turns.size()) {
      turns.get(turnIndex).getAppliedSummaries().addAll(summaries);
    }
    modelRevision++;
  }

  public List<ChatMessage> buildLlmHistory() {
    List<ChatMessage> history = new ArrayList<>();
    int start = Math.max(0, turns.size() - MAX_HISTORY_TURNS);
    for (int i = start; i < turns.size(); i++) {
      DvAiTurn turn = turns.get(i);
      if (!Utils.isEmpty(turn.getLlmUserMessage())) {
        history.add(new UserMessage(turn.getLlmUserMessage()));
      }
      if (!Utils.isEmpty(turn.getAssistantAdvice())) {
        history.add(new AiMessage(turn.getAssistantAdvice()));
      }
    }
    return history;
  }

  static String formatAppliedSummary(DvAiProposal proposal) {
    if (proposal == null || proposal.getType() == null) {
      return "unknown change";
    }
    return switch (proposal.getType()) {
      case ADD_MODEL_NOTE ->
          "ADD_MODEL_NOTE: "
              + truncate(proposal.parameter("text") != null ? proposal.parameter("text") : "", 120);
      case SET_CONFIGURATION_PROPERTY ->
          "SET_CONFIGURATION_PROPERTY: "
              + proposal.parameter("propertyName")
              + "="
              + proposal.parameter("value");
      case RENAME_TABLE ->
          "RENAME_TABLE: "
              + proposal.parameter("tableName")
              + " -> "
              + proposal.parameter("newName");
      default -> proposal.getType().name();
    };
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    if (value.length() <= max) {
      return value;
    }
    return value.substring(0, max) + "...";
  }
}
