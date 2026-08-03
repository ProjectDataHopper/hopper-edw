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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.util.Utils;

/** Parses advisory text and extracts dv_proposals JSON blocks. */
public final class DvAiProposalParser {

  private static final Pattern PROPOSAL_BLOCK =
      Pattern.compile("```dv_proposals\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  private DvAiProposalParser() {}

  public static boolean hasProposalBlock(String rawResponse) {
    return !Utils.isEmpty(rawResponse) && PROPOSAL_BLOCK.matcher(rawResponse).find();
  }

  /** Removes dv_proposals fenced blocks from advisory text (for conversation history). */
  public static String stripProposalBlocks(String rawResponse) {
    if (Utils.isEmpty(rawResponse)) {
      return "";
    }
    String advice = rawResponse;
    Matcher matcher = PROPOSAL_BLOCK.matcher(rawResponse);
    while (matcher.find()) {
      advice = advice.replace(matcher.group(0), "").trim();
    }
    return advice.trim();
  }

  public static DvAiResponse parse(String rawResponse) {
    DvAiResponse response = new DvAiResponse();
    response.setRawResponse(rawResponse);
    if (Utils.isEmpty(rawResponse)) {
      response.setMarkdownAdvice("");
      return response;
    }

    String advice = rawResponse;
    List<DvAiProposal> proposals = new ArrayList<>();
    Matcher matcher = PROPOSAL_BLOCK.matcher(rawResponse);
    while (matcher.find()) {
      advice = advice.replace(matcher.group(0), "").trim();
      proposals.addAll(parseProposalJson(matcher.group(1)));
    }
    response.setMarkdownAdvice(advice.trim());
    response.setProposals(proposals);
    return response;
  }

  private static List<DvAiProposal> parseProposalJson(String jsonText) {
    List<DvAiProposal> proposals = new ArrayList<>();
    if (Utils.isEmpty(jsonText)) {
      return proposals;
    }
    try {
      ObjectMapper mapper = HopJson.newMapper();
      JsonNode root = mapper.readTree(jsonText.trim());
      JsonNode array = root.path("proposals");
      if (!array.isArray()) {
        return proposals;
      }
      for (JsonNode node : array) {
        DvAiProposal proposal = toProposal(node);
        if (proposal != null) {
          proposals.add(proposal);
        }
      }
    } catch (Exception ignored) {
      // Ignore malformed proposal blocks; advice text is still shown
    }
    return proposals;
  }

  private static DvAiProposal toProposal(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String typeValue = node.path("type").asText(null);
    if (Utils.isEmpty(typeValue)) {
      return null;
    }
    DvAiProposal proposal = new DvAiProposal();
    proposal.setId(node.path("id").asText(null));
    proposal.setDescription(node.path("description").asText(""));
    proposal.setRiskLevel(parseRisk(node.path("riskLevel").asText("MEDIUM")));
    try {
      proposal.setType(DvAiProposal.Type.valueOf(typeValue.trim().toUpperCase()));
    } catch (IllegalArgumentException e) {
      return null;
    }
    JsonNode parameters = node.path("parameters");
    if (parameters.isObject()) {
      Map<String, String> map = new LinkedHashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = parameters.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        map.put(entry.getKey(), entry.getValue().asText(""));
      }
      proposal.setParameters(map);
    }
    return proposal;
  }

  private static DvAiProposal.RiskLevel parseRisk(String value) {
    if (Utils.isEmpty(value)) {
      return DvAiProposal.RiskLevel.MEDIUM;
    }
    try {
      return DvAiProposal.RiskLevel.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return DvAiProposal.RiskLevel.MEDIUM;
    }
  }
}
