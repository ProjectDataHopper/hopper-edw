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
package org.hopper.edw.datavault.ai.pipeline;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.ai.HopAiAdvisorEngine;
import org.hopper.edw.datavault.ai.HopAiAdvisoryResponse;
import org.hopper.edw.datavault.ai.HopAiConfig;
import org.hopper.edw.datavault.ai.HopAiM2PromptSupport;
import org.hopper.edw.datavault.ai.HopAiPromptLoader;
import org.hopper.edw.datavault.ai.HopAiProposalParser;

public final class PipelineAiAdvisorService {

  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/pipeline/";

  private PipelineAiAdvisorService() {}

  public static HopAiAdvisoryResponse advise(
      HopAiConfig config,
      IVariables variables,
      PipelineAiContextBundle context,
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

  static String buildSystemPrompt(PipelineAiContextBundle context) throws HopException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble-hop.txt")).append("\n\n");
    PipelineAiScenario scenario =
        context.getScenario() != null ? context.getScenario() : PipelineAiScenario.PIPELINE_GENERAL;
    prompt.append(
        HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt"));
    prompt.append("\n\n").append(HopAiM2PromptSupport.buildM2Supplement());
    return prompt.toString();
  }

  public static String buildInitialUserPrompt(PipelineAiContextBundle context) {
    return buildUserPromptBody(context, true);
  }

  public static String buildFollowUpUserPrompt(PipelineAiContextBundle context) {
    return buildUserPromptBody(context, false);
  }

  private static String buildUserPromptBody(PipelineAiContextBundle context, boolean includeFull) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(context.getUserPrompt()).append("\n\n");
    prompt
        .append("Pipeline structure JSON:\n")
        .append(nullToEmpty(context.getStructureJson()))
        .append("\n\n");

    if (includeFull) {
      prompt
          .append("Pipeline summary JSON:\n")
          .append(nullToEmpty(context.getSummaryJson()))
          .append("\n\n");
      if (!Utils.isEmpty(context.getTransformCatalogJson())) {
        prompt
            .append("Available transform plugins JSON:\n")
            .append(context.getTransformCatalogJson())
            .append("\n\n");
      }
      if (!Utils.isEmpty(context.getTopologyXml())) {
        prompt.append("Pipeline topology XML:\n").append(context.getTopologyXml()).append("\n\n");
      }
      if (!Utils.isEmpty(context.getLogsExcerpt())) {
        prompt.append("Execution log excerpt:\n").append(context.getLogsExcerpt()).append("\n\n");
      }
    }

    if (!Utils.isEmpty(context.getFocusTransformName())) {
      prompt.append("Focus transform:\n").append(context.getFocusTransformName()).append("\n\n");
    }

    if (!Utils.isEmpty(context.getCheckResultsJson())) {
      prompt
          .append("Pipeline check results JSON:\n")
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
    prompt.append("User applied these pipeline changes since the previous turn:\n");
    for (String summary : summaries) {
      prompt.append("- ").append(summary).append('\n');
    }
    prompt.append('\n');
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
