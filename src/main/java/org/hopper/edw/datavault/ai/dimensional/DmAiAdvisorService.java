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
package org.hopper.edw.datavault.ai.dimensional;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/** Assembles dimensional-model system and user prompts for the Hop AI Assistant workbench. */
public final class DmAiAdvisorService {

  private DmAiAdvisorService() {}

  public static String buildSystemPrompt(DmAiContextBundle context) throws HopException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(DmAiPromptLoader.loadPreamble()).append("\n\n");
    prompt.append(DmAiPromptLoader.loadScenarioPrompt(context.getScenario()));
    return prompt.toString();
  }

  public static String buildInitialUserPrompt(DmAiContextBundle context) {
    return buildUserPromptBody(context, true);
  }

  public static String buildFollowUpUserPrompt(DmAiContextBundle context) {
    return buildUserPromptBody(context, false);
  }

  private static String buildUserPromptBody(DmAiContextBundle context, boolean includeFullContext) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("User question:\n").append(context.getUserPrompt()).append("\n\n");
    prompt
        .append("Dimensional model structure JSON:\n")
        .append(nullToEmpty(context.getModelStructureJson()))
        .append("\n\n");

    if (includeFullContext) {
      prompt
          .append("Dimensional model summary JSON:\n")
          .append(nullToEmpty(context.getModelSummaryJson()))
          .append("\n\n");
      if (!Utils.isEmpty(context.getHopMetadataJson())) {
        prompt.append("Hop metadata JSON:\n").append(context.getHopMetadataJson()).append("\n\n");
      }
      if (!Utils.isEmpty(context.getModelXml())) {
        prompt.append("Dimensional model XML:\n").append(context.getModelXml()).append("\n\n");
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
