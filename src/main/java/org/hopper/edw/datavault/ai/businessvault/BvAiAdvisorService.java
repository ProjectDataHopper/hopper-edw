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
package org.hopper.edw.datavault.ai.businessvault;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transforms.languagemodelchat.LanguageModelChatMeta;
import org.apache.hop.pipeline.transforms.languagemodelchat.internals.LanguageModelFacade;
import org.hopper.edw.datavault.ai.DvAiLanguageModelFactory;
import org.hopper.edw.datavault.ai.DvAiProposalParser;
import org.hopper.edw.datavault.ai.DvAiResponse;
import org.hopper.edw.datavault.ai.HopAiAdvisorEngine;
import org.hopper.edw.datavault.ai.HopAiConfig;

/** Calls the configured LLM and parses Business Vault advisory responses. */
public final class BvAiAdvisorService {

  private BvAiAdvisorService() {}

  public static DvAiResponse advise(
      HopAiConfig config,
      IVariables variables,
      BvAiContextBundle context,
      List<ChatMessage> history)
      throws HopException {
    HopAiAdvisorEngine.validateConfig(config);

    LanguageModelChatMeta meta = DvAiLanguageModelFactory.fromConfig(config, variables);
    LanguageModelFacade facade = new LanguageModelFacade(variables, meta);

    String userPrompt =
        context.isFollowUp() ? buildFollowUpUserPrompt(context) : buildInitialUserPrompt(context);

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(new SystemMessage(buildSystemPrompt(context)));
    if (history != null) {
      messages.addAll(history);
    }
    messages.add(new UserMessage(userPrompt));

    try {
      Response<AiMessage> response = facade.generate(messages);
      String text = response != null && response.content() != null ? response.content().text() : "";
      return DvAiProposalParser.parse(text);
    } catch (Exception e) {
      throw new HopException("AI advisory request failed: " + e.getMessage(), e);
    }
  }

  static String buildSystemPrompt(BvAiContextBundle context) throws HopException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(BvAiPromptLoader.loadPreamble()).append("\n\n");
    prompt.append(BvAiPromptLoader.loadScenarioPrompt(context.getScenario()));
    return prompt.toString();
  }

  public static String buildInitialUserPrompt(BvAiContextBundle context) {
    return buildUserPromptBody(context, true);
  }

  public static String buildFollowUpUserPrompt(BvAiContextBundle context) {
    return buildUserPromptBody(context, false);
  }

  private static String buildUserPromptBody(BvAiContextBundle context, boolean includeFullContext) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(context.getUserPrompt()).append("\n\n");
    prompt
        .append("Business Vault model structure JSON:\n")
        .append(nullToEmpty(context.getModelStructureJson()))
        .append("\n\n");

    if (includeFullContext) {
      prompt
          .append("Business Vault model summary JSON:\n")
          .append(nullToEmpty(context.getModelSummaryJson()))
          .append("\n\n");
      if (!Utils.isEmpty(context.getLinkedDvModelStructureJson())) {
        prompt
            .append("Linked Data Vault model structure JSON:\n")
            .append(context.getLinkedDvModelStructureJson())
            .append("\n\n");
      }
      if (!Utils.isEmpty(context.getHopMetadataJson())) {
        prompt.append("Hop metadata JSON:\n").append(context.getHopMetadataJson()).append("\n\n");
      }
      if (!Utils.isEmpty(context.getModelXml())) {
        prompt.append("Business Vault model XML:\n").append(context.getModelXml()).append("\n\n");
      }
      if (!Utils.isEmpty(context.getLogsExcerpt())) {
        prompt.append("Log excerpt:\n").append(context.getLogsExcerpt()).append("\n\n");
      }
    }

    if (!Utils.isEmpty(context.getCheckResultsJson())) {
      prompt
          .append("Model check results JSON:\n")
          .append(context.getCheckResultsJson())
          .append("\n\n");
    }
    if (!Utils.isEmpty(context.getLoadRunMetricsJson())) {
      prompt
          .append("Recent load-run metrics and insights JSON:\n")
          .append(context.getLoadRunMetricsJson())
          .append("\n\n");
    }
    if (!Utils.isEmpty(context.getExecutionInfoJson())) {
      prompt
          .append("Recent execution logs and transform metrics JSON:\n")
          .append(context.getExecutionInfoJson())
          .append("\n\n");
    }

    appendAppliedSummaries(prompt, context.getAppliedChangeSummaries());
    return prompt.toString();
  }

  private static void appendAppliedSummaries(StringBuilder prompt, List<String> summaries) {
    if (summaries == null || summaries.isEmpty()) {
      return;
    }
    prompt.append("User applied these model changes since the previous turn:\n");
    for (String summary : summaries) {
      prompt.append("- ").append(summary).append('\n');
    }
    prompt.append('\n');
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
