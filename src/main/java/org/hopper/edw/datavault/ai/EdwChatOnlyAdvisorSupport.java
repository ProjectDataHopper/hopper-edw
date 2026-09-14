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

import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorInclusion;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Shared helpers for chat-only hopper-edw advisors (no apply / no proposals). */
public final class EdwChatOnlyAdvisorSupport {

  public static final String ATTR_SESSION_GRAPH_JSON = "edw.sessionGraphJson";
  public static final String ATTR_OPS_JSON = "edw.opsJson";

  private EdwChatOnlyAdvisorSupport() {}

  public static void requireQuestion(AiAdvisorRequest request) throws HopException {
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }
  }

  public static AiAdvisorResponse parseAdviceOnly(String raw) {
    AiAdvisorResponse response = new AiAdvisorResponse();
    response.setRawResponse(raw);
    String advice = raw != null ? raw : "";
    advice = stripFence(advice, "hop_proposals");
    advice = stripFence(advice, "dv_proposals");
    response.setMarkdownAdvice(advice.trim());
    response.setProposals(List.of());
    response.setProposalBlockPresent(false);
    return response;
  }

  public static AiAdvisorInclusion inclusion(
      Class<?> pkg, String messagePrefix, String id, String keySuffix, boolean defaultSelected) {
    return new AiAdvisorInclusion(
        id,
        BaseMessages.getString(pkg, messagePrefix + ".Inclusion." + keySuffix),
        defaultSelected,
        BaseMessages.getString(pkg, messagePrefix + ".Inclusion." + keySuffix + ".Tooltip"),
        BaseMessages.getString(pkg, messagePrefix + ".Inclusion." + keySuffix + ".Summary"));
  }

  public static String serializeCheckResults(List<ICheckResult> results) {
    StringBuilder json = new StringBuilder();
    json.append("{\"results\":[");
    List<ICheckResult> safe = results != null ? results : List.of();
    for (int i = 0; i < safe.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      ICheckResult result = safe.get(i);
      String type =
          result.getType() == ICheckResult.TYPE_RESULT_ERROR
              ? "ERROR"
              : result.getType() == ICheckResult.TYPE_RESULT_OK
                  ? "OK"
                  : result.getType() == ICheckResult.TYPE_RESULT_WARNING ? "WARNING" : "INFO";
      json.append("{\"type\":").append(DvAiContextBuilder.jsonString(type));
      json.append(",\"text\":").append(DvAiContextBuilder.jsonString(result.getText()));
      json.append('}');
    }
    json.append("]}");
    return json.toString();
  }

  public static String attributeString(AiAdvisorRequest request, String key) {
    if (request == null || request.getAttributes() == null || Utils.isEmpty(key)) {
      return "";
    }
    Object value = request.getAttributes().get(key);
    return value instanceof String text ? text : "";
  }

  private static String stripFence(String text, String tag) {
    if (text == null || tag == null) {
      return "";
    }
    String open = "```" + tag;
    int start = indexOfIgnoreCase(text, open);
    if (start < 0) {
      return text;
    }
    int fenceEnd = text.indexOf('\n', start);
    if (fenceEnd < 0) {
      return text.substring(0, start).trim();
    }
    int close = text.indexOf("```", fenceEnd + 1);
    if (close < 0) {
      return text.substring(0, start).trim();
    }
    return (text.substring(0, start) + text.substring(close + 3)).trim();
  }

  private static int indexOfIgnoreCase(String text, String needle) {
    return text.toLowerCase().indexOf(needle.toLowerCase());
  }
}
