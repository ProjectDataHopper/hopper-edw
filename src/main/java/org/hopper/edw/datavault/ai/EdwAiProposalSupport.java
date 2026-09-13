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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.ai.advisor.AiProposal;
import org.apache.hop.ai.advisor.AiProposalParser;
import org.apache.hop.ai.advisor.AiProposalValidation;
import org.apache.hop.core.util.Utils;

/** Converts hopper-edw {@link DvAiProposal} values to Hop {@link AiProposal} and back. */
public final class EdwAiProposalSupport {

  private EdwAiProposalSupport() {}

  public static AiProposal toAi(DvAiProposal source) {
    if (source == null) {
      return null;
    }
    AiProposal proposal = new AiProposal();
    proposal.setId(source.getId());
    proposal.setDescription(source.getDescription());
    if (source.getRiskLevel() != null) {
      proposal.setRiskLevel(source.getRiskLevel().name());
    }
    if (source.getType() != null) {
      proposal.setType(source.getType().name());
    }
    if (source.getParameters() != null) {
      proposal.setParameters(new LinkedHashMap<>(source.getParameters()));
    }
    return proposal;
  }

  public static DvAiProposal fromAi(AiProposal source) {
    if (source == null) {
      return null;
    }
    DvAiProposal proposal = new DvAiProposal();
    proposal.setId(source.getId());
    proposal.setDescription(source.getDescription());
    proposal.setRiskLevel(parseRisk(source.getRiskLevel()));
    proposal.setType(parseType(source.getType()));
    if (source.getParameters() != null) {
      proposal.setParameters(new LinkedHashMap<>(source.getParameters()));
    }
    return proposal;
  }

  public static List<DvAiProposal> fromAi(List<AiProposal> sources) {
    List<DvAiProposal> out = new ArrayList<>();
    if (sources == null) {
      return out;
    }
    for (AiProposal source : sources) {
      DvAiProposal converted = fromAi(source);
      if (converted != null) {
        out.add(converted);
      }
    }
    return out;
  }

  public static AiAdvisorResponse parseEdwResponse(String raw) {
    AiAdvisorResponse hop = AiProposalParser.parse(raw);
    DvAiResponse dv = DvAiProposalParser.parse(raw);
    AiAdvisorResponse response = new AiAdvisorResponse();
    response.setRawResponse(raw);
    String advice = hop.getMarkdownAdvice();
    if (DvAiProposalParser.hasProposalBlock(advice)) {
      advice = DvAiProposalParser.stripProposalBlocks(advice);
    }
    response.setMarkdownAdvice(advice);
    List<AiProposal> proposals = new ArrayList<>();
    if (hop.getProposals() != null) {
      proposals.addAll(hop.getProposals());
    }
    if (dv.getProposals() != null) {
      for (DvAiProposal proposal : dv.getProposals()) {
        proposals.add(toAi(proposal));
      }
    }
    response.setProposals(proposals);
    response.setProposalBlockPresent(
        hop.isProposalBlockPresent() || DvAiProposalParser.hasProposalBlock(raw));
    return response;
  }

  public static AiProposalValidation toValidation(
      String proposalId, Enum<?> status, String message) {
    AiProposalValidation validation = new AiProposalValidation();
    validation.setProposalId(proposalId);
    boolean blocked = status != null && "BLOCKED".equals(status.name());
    validation.setBlocked(blocked);
    if (blocked) {
      validation.setReason(message);
    } else if (status != null && "WARNING".equals(status.name()) && !Utils.isEmpty(message)) {
      validation.setWarning(message);
    }
    return validation;
  }

  public static List<AiProposalValidation> toDvValidations(
      List<DvAiProposalValidator.ValidationResult> results) {
    List<AiProposalValidation> out = new ArrayList<>();
    if (results == null) {
      return out;
    }
    for (DvAiProposalValidator.ValidationResult result : results) {
      String id = result.getProposal() != null ? result.getProposal().getId() : null;
      out.add(toValidation(id, result.getStatus(), result.getMessage()));
    }
    return out;
  }

  public static List<AiProposalValidation> toBvValidations(
      List<org.hopper.edw.datavault.ai.businessvault.BvAiProposalValidator.ValidationResult>
          results) {
    List<AiProposalValidation> out = new ArrayList<>();
    if (results == null) {
      return out;
    }
    for (org.hopper.edw.datavault.ai.businessvault.BvAiProposalValidator.ValidationResult result :
        results) {
      String id = result.getProposal() != null ? result.getProposal().getId() : null;
      out.add(toValidation(id, result.getStatus(), result.getMessage()));
    }
    return out;
  }

  public static List<AiProposalValidation> toDmValidations(
      List<org.hopper.edw.datavault.ai.dimensional.DmAiProposalValidator.ValidationResult>
          results) {
    List<AiProposalValidation> out = new ArrayList<>();
    if (results == null) {
      return out;
    }
    for (org.hopper.edw.datavault.ai.dimensional.DmAiProposalValidator.ValidationResult result :
        results) {
      String id = result.getProposal() != null ? result.getProposal().getId() : null;
      out.add(toValidation(id, result.getStatus(), result.getMessage()));
    }
    return out;
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

  private static DvAiProposal.Type parseType(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    try {
      return DvAiProposal.Type.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
