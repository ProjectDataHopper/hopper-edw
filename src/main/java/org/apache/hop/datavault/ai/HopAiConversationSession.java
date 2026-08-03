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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;

/** Multi-turn advisory session for pipelines and workflows. */
public class HopAiConversationSession {

  public static final int MAX_HISTORY_TURNS = 6;

  private final List<HopAiTurn> turns = new ArrayList<>();
  private final List<String> pendingAppliedSummaries = new ArrayList<>();
  private int graphRevision;

  public List<HopAiTurn> getTurns() {
    return turns;
  }

  public boolean isEmpty() {
    return turns.isEmpty();
  }

  public int getGraphRevision() {
    return graphRevision;
  }

  public void bumpGraphRevision() {
    graphRevision++;
  }

  public void clear() {
    turns.clear();
    pendingAppliedSummaries.clear();
    graphRevision = 0;
  }

  public List<String> consumePendingAppliedSummaries() {
    List<String> copy = List.copyOf(pendingAppliedSummaries);
    pendingAppliedSummaries.clear();
    return copy;
  }

  public void recordTurn(String userPrompt, String llmUserMessage, HopAiAdvisoryResponse response) {
    HopAiTurn turn = new HopAiTurn();
    turn.setUserPrompt(userPrompt);
    turn.setLlmUserMessage(llmUserMessage);
    String raw = response != null ? response.getRawResponse() : null;
    turn.setProposalBlockPresent(HopAiProposalParser.hasProposalBlock(raw));
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

  public void recordApplied(int turnIndex, List<HopAiProposal> applied) {
    if (applied == null || applied.isEmpty()) {
      return;
    }
    List<String> summaries = new ArrayList<>();
    for (HopAiProposal proposal : applied) {
      summaries.add(formatAppliedSummary(proposal));
    }
    pendingAppliedSummaries.addAll(summaries);
    if (turnIndex >= 0 && turnIndex < turns.size()) {
      turns.get(turnIndex).getAppliedSummaries().addAll(summaries);
    }
    bumpGraphRevision();
  }

  public List<ChatMessage> buildLlmHistory() {
    List<ChatMessage> history = new ArrayList<>();
    int start = Math.max(0, turns.size() - MAX_HISTORY_TURNS);
    for (int i = start; i < turns.size(); i++) {
      HopAiTurn turn = turns.get(i);
      if (!Utils.isEmpty(turn.getLlmUserMessage())) {
        history.add(new UserMessage(turn.getLlmUserMessage()));
      }
      if (!Utils.isEmpty(turn.getAssistantAdvice())) {
        history.add(new AiMessage(turn.getAssistantAdvice()));
      }
    }
    return history;
  }

  static String formatAppliedSummary(HopAiProposal proposal) {
    if (proposal == null || proposal.getType() == null) {
      return "unknown change";
    }
    return switch (proposal.getType()) {
      case ADD_TRANSFORM ->
          "ADD_TRANSFORM: "
              + proposal.parameter("name")
              + " ("
              + proposal.parameter("transformPluginId")
              + ")";
      case DELETE_TRANSFORM -> "DELETE_TRANSFORM: " + proposal.parameter("transformName");
      case RENAME_TRANSFORM ->
          "RENAME_TRANSFORM: "
              + proposal.parameter("transformName")
              + " -> "
              + proposal.parameter("newName");
      case ADD_PIPELINE_HOP ->
          "ADD_PIPELINE_HOP: "
              + proposal.parameter("fromTransform")
              + " -> "
              + proposal.parameter("toTransform");
      case DELETE_PIPELINE_HOP ->
          "DELETE_PIPELINE_HOP: "
              + proposal.parameter("fromTransform")
              + " -> "
              + proposal.parameter("toTransform");
      case SET_TRANSFORM_LOCATION ->
          "SET_TRANSFORM_LOCATION: "
              + proposal.parameter("transformName")
              + " @ "
              + proposal.parameter("locationX")
              + ","
              + proposal.parameter("locationY");
      case ADD_PIPELINE_NOTE ->
          "ADD_PIPELINE_NOTE: "
              + truncate(proposal.parameter("text") != null ? proposal.parameter("text") : "", 120);
      case ADD_ACTION ->
          "ADD_ACTION: "
              + proposal.parameter("name")
              + " ("
              + proposal.parameter("actionPluginId")
              + ")";
      case DELETE_ACTION -> "DELETE_ACTION: " + proposal.parameter("actionName");
      case RENAME_ACTION ->
          "RENAME_ACTION: "
              + proposal.parameter("actionName")
              + " -> "
              + proposal.parameter("newName");
      case ADD_WORKFLOW_HOP ->
          "ADD_WORKFLOW_HOP: "
              + proposal.parameter("fromAction")
              + " -> "
              + proposal.parameter("toAction");
      case DELETE_WORKFLOW_HOP ->
          "DELETE_WORKFLOW_HOP: "
              + proposal.parameter("fromAction")
              + " -> "
              + proposal.parameter("toAction");
      case SET_ACTION_LOCATION ->
          "SET_ACTION_LOCATION: "
              + proposal.parameter("actionName")
              + " @ "
              + proposal.parameter("locationX")
              + ","
              + proposal.parameter("locationY");
      case ADD_WORKFLOW_NOTE ->
          "ADD_WORKFLOW_NOTE: "
              + truncate(proposal.parameter("text") != null ? proposal.parameter("text") : "", 120);
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
