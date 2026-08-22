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
package org.apache.hop.datavault.ai.workflow;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.HopAiAdvisorEngine;
import org.apache.hop.datavault.ai.HopAiAdvisoryResponse;
import org.apache.hop.datavault.ai.HopAiConfig;
import org.apache.hop.datavault.ai.HopAiM2PromptSupport;
import org.apache.hop.datavault.ai.HopAiPromptLoader;
import org.apache.hop.datavault.ai.HopAiProposalParser;

public final class WorkflowAiAdvisorService {

  private static final String PROMPT_ROOT = "/org/apache/hop/datavault/ai/prompts/workflow/";

  private WorkflowAiAdvisorService() {}

  public static HopAiAdvisoryResponse advise(
      HopAiConfig config,
      IVariables variables,
      WorkflowAiContextBundle context,
      List<ChatMessage> conversationHistory)
      throws HopException {
    String userPrompt =
        context.isFollowUp() ? buildFollowUpUserPrompt(context) : buildInitialUserPrompt(context);
    HopAiAdvisorEngine.validateConfig(config);
    String raw =
        HopAiAdvisorEngine.adviseRaw(
            config, variables, buildSystemPrompt(context), userPrompt, conversationHistory);
    return HopAiProposalParser.parse(raw);
  }

  static String buildSystemPrompt(WorkflowAiContextBundle context) throws HopException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble-hop.txt")).append("\n\n");
    WorkflowAiScenario scenario =
        context.getScenario() != null ? context.getScenario() : WorkflowAiScenario.WORKFLOW_GENERAL;
    prompt.append(
        HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt"));
    prompt.append("\n\n").append(HopAiM2PromptSupport.buildM2Supplement());
    return prompt.toString();
  }

  public static String buildInitialUserPrompt(WorkflowAiContextBundle context) {
    return buildUserPromptBody(context, true);
  }

  public static String buildFollowUpUserPrompt(WorkflowAiContextBundle context) {
    return buildUserPromptBody(context, false);
  }

  private static String buildUserPromptBody(WorkflowAiContextBundle context, boolean includeFull) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(context.getUserPrompt()).append("\n\n");
    prompt
        .append("Workflow structure JSON:\n")
        .append(nullToEmpty(context.getStructureJson()))
        .append("\n\n");

    if (includeFull) {
      prompt
          .append("Workflow summary JSON:\n")
          .append(nullToEmpty(context.getSummaryJson()))
          .append("\n\n");
      if (!Utils.isEmpty(context.getActionCatalogJson())) {
        prompt
            .append("Available workflow action plugins JSON:\n")
            .append(context.getActionCatalogJson())
            .append("\n\n");
      }
      if (!Utils.isEmpty(context.getTopologyXml())) {
        prompt.append("Workflow topology XML:\n").append(context.getTopologyXml()).append("\n\n");
      }
      if (!Utils.isEmpty(context.getLogsExcerpt())) {
        prompt.append("Execution log excerpt:\n").append(context.getLogsExcerpt()).append("\n\n");
      }
    }

    if (!Utils.isEmpty(context.getFocusActionName())) {
      prompt.append("Focus action:\n").append(context.getFocusActionName()).append("\n\n");
    }

    if (!Utils.isEmpty(context.getCheckResultsJson())) {
      prompt
          .append("Workflow check results JSON:\n")
          .append(context.getCheckResultsJson())
          .append("\n\n");
    }

    appendAppliedSummaries(prompt, context.getAppliedChangeSummaries());
    return prompt.toString();
  }

  private static void appendAppliedSummaries(StringBuilder prompt, List<String> summaries) {
    if (summaries == null || summaries.isEmpty()) {
      return;
    }
    prompt.append("User applied these workflow changes since the previous turn:\n");
    for (String summary : summaries) {
      prompt.append("- ").append(summary).append('\n');
    }
    prompt.append('\n');
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
