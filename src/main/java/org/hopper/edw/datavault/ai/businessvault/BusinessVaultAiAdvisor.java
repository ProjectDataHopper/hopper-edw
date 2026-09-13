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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorInclusion;
import org.apache.hop.ai.advisor.AiAdvisorPlugin;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.ai.advisor.AiAdvisorScenario;
import org.apache.hop.ai.advisor.AiProposal;
import org.apache.hop.ai.advisor.AiProposalValidation;
import org.apache.hop.ai.advisor.IAiAdvisor;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.ai.EdwAiInclusions;
import org.hopper.edw.datavault.ai.EdwAiProposalSupport;
import org.hopper.edw.datavault.hopgui.ai.EdwAiAdvisorGraphSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;

@AiAdvisorPlugin(
    id = BusinessVaultAiAdvisor.ID,
    name = "i18n::BusinessVaultAiAdvisor.Name",
    description = "i18n::BusinessVaultAiAdvisor.Description",
    image = "datavault-ai-help.svg",
    locations = {EdwAiAdvisorLocations.BUSINESS_VAULT_GRAPH})
public class BusinessVaultAiAdvisor implements IAiAdvisor {

  public static final String ID = "business-vault-advisor";
  private static final Class<?> PKG = BusinessVaultAiAdvisor.class;

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "BusinessVaultAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.BUSINESS_VAULT_GRAPH};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (BvAiScenario scenario : BvAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<AiAdvisorInclusion> listInclusions() {
    return List.of(
        inclusion(EdwAiInclusions.CHECKS, "Checks"),
        inclusion(EdwAiInclusions.LINKED_DV, "LinkedDv"),
        inclusion(EdwAiInclusions.XML, "Xml"),
        inclusion(EdwAiInclusions.LOAD_RUN_METRICS, "LoadRunMetrics"),
        inclusion(EdwAiInclusions.EXECUTION_INFO, "ExecutionInfo"));
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(BaseMessages.getString(PKG, "BusinessVaultAiAdvisor.Sharing.Structure"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    BusinessVaultModel model = modelFrom(request);
    BvAiContextBundle context =
        BvAiContextBuilder.build(
            model, request.getMetadataProvider(), request.getVariables(), toBvRequest(request));
    String userPrompt =
        request.isFollowUp()
            ? BvAiAdvisorService.buildFollowUpUserPrompt(context)
            : BvAiAdvisorService.buildInitialUserPrompt(context);
    return new AiAdvisorPrompt(BvAiAdvisorService.buildSystemPrompt(context), userPrompt);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwAiProposalSupport.parseEdwResponse(raw);
  }

  @Override
  public String previewProposal(AiProposal proposal) {
    return BvAiProposalApplier.preview(EdwAiProposalSupport.fromAi(proposal));
  }

  @Override
  public List<AiProposalValidation> validateProposals(
      AiAdvisorRequest request, List<AiProposal> proposals) {
    BusinessVaultModel model =
        request != null && request.getArtifact() instanceof BusinessVaultModel m ? m : null;
    return EdwAiProposalSupport.toBvValidations(
        BvAiProposalValidator.validate(
            model,
            EdwAiProposalSupport.fromAi(proposals),
            request != null ? request.getMetadataProvider() : null,
            request != null ? request.getVariables() : null));
  }

  @Override
  public void applyProposals(AiAdvisorRequest request, List<AiProposal> selected)
      throws HopException {
    BusinessVaultModel model = modelFrom(request);
    EdwAiAdvisorGraphSupport.markUndoPoint(request);
    BvAiProposalApplier.apply(
        model,
        EdwAiProposalSupport.fromAi(selected),
        request.getMetadataProvider(),
        request.getVariables());
  }

  @Override
  public void afterApply(AiAdvisorRequest request, List<AiProposal> applied) {
    EdwAiAdvisorGraphSupport.afterApply(request);
  }

  private static BusinessVaultModel modelFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof BusinessVaultModel model)) {
      throw new HopException("No Business Vault model is bound to this session.");
    }
    return model;
  }

  private static BvAiRequest toBvRequest(AiAdvisorRequest request) {
    boolean followUp = request.isFollowUp();
    BvAiRequest.BvAiRequestBuilder builder =
        BvAiRequest.builder()
            .userPrompt(request.getUserPrompt())
            .scenario(BvAiScenario.resolve(request.getScenarioId()))
            .includeCheckResults(request.inclusionEnabled(EdwAiInclusions.CHECKS))
            .includeLinkedDvModel(request.inclusionEnabled(EdwAiInclusions.LINKED_DV))
            .includeModelXml(request.inclusionEnabled(EdwAiInclusions.XML) && !followUp)
            .includeLoadRunMetrics(request.inclusionEnabled(EdwAiInclusions.LOAD_RUN_METRICS))
            .includeExecutionInfo(request.inclusionEnabled(EdwAiInclusions.EXECUTION_INFO))
            .logsExcerpt(request.getLogExcerpt())
            .followUp(followUp);
    List<String> applied = request.getAppliedChangeSummaries();
    if (applied != null) {
      builder.appliedChangeSummaries(applied);
    }
    return builder.build();
  }

  private static AiAdvisorInclusion inclusion(String id, String keySuffix) {
    return new AiAdvisorInclusion(
        id,
        BaseMessages.getString(PKG, "BusinessVaultAiAdvisor.Inclusion." + keySuffix),
        false,
        BaseMessages.getString(PKG, "BusinessVaultAiAdvisor.Inclusion." + keySuffix + ".Tooltip"),
        BaseMessages.getString(PKG, "BusinessVaultAiAdvisor.Inclusion." + keySuffix + ".Summary"));
  }
}
