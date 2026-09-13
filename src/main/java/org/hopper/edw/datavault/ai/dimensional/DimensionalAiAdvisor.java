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
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;

@AiAdvisorPlugin(
    id = DimensionalAiAdvisor.ID,
    name = "i18n::DimensionalAiAdvisor.Name",
    description = "i18n::DimensionalAiAdvisor.Description",
    image = "datavault-ai-help.svg",
    locations = {EdwAiAdvisorLocations.DIMENSIONAL_GRAPH})
public class DimensionalAiAdvisor implements IAiAdvisor {

  public static final String ID = "dimensional-advisor";
  private static final Class<?> PKG = DimensionalAiAdvisor.class;

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "DimensionalAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.DIMENSIONAL_GRAPH};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (DmAiScenario scenario : DmAiScenario.values()) {
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
        inclusion(EdwAiInclusions.XML, "Xml"),
        inclusion(EdwAiInclusions.LOAD_RUN_METRICS, "LoadRunMetrics"),
        inclusion(EdwAiInclusions.EXECUTION_INFO, "ExecutionInfo"));
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(BaseMessages.getString(PKG, "DimensionalAiAdvisor.Sharing.Structure"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    DimensionalModel model = modelFrom(request);
    DmAiContextBundle context =
        DmAiContextBuilder.build(
            model, request.getMetadataProvider(), request.getVariables(), toDmRequest(request));
    String userPrompt =
        request.isFollowUp()
            ? DmAiAdvisorService.buildFollowUpUserPrompt(context)
            : DmAiAdvisorService.buildInitialUserPrompt(context);
    return new AiAdvisorPrompt(DmAiAdvisorService.buildSystemPrompt(context), userPrompt);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwAiProposalSupport.parseEdwResponse(raw);
  }

  @Override
  public String previewProposal(AiProposal proposal) {
    return DmAiProposalApplier.preview(EdwAiProposalSupport.fromAi(proposal));
  }

  @Override
  public List<AiProposalValidation> validateProposals(
      AiAdvisorRequest request, List<AiProposal> proposals) {
    DimensionalModel model =
        request != null && request.getArtifact() instanceof DimensionalModel m ? m : null;
    return EdwAiProposalSupport.toDmValidations(
        DmAiProposalValidator.validate(
            model,
            EdwAiProposalSupport.fromAi(proposals),
            request != null ? request.getMetadataProvider() : null,
            request != null ? request.getVariables() : null));
  }

  @Override
  public void applyProposals(AiAdvisorRequest request, List<AiProposal> selected)
      throws HopException {
    DimensionalModel model = modelFrom(request);
    EdwAiAdvisorGraphSupport.markUndoPoint(request);
    DmAiProposalApplier.apply(
        model,
        EdwAiProposalSupport.fromAi(selected),
        request.getMetadataProvider(),
        request.getVariables());
  }

  @Override
  public void afterApply(AiAdvisorRequest request, List<AiProposal> applied) {
    EdwAiAdvisorGraphSupport.afterApply(request);
  }

  private static DimensionalModel modelFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof DimensionalModel model)) {
      throw new HopException("No dimensional model is bound to this session.");
    }
    return model;
  }

  private static DmAiRequest toDmRequest(AiAdvisorRequest request) {
    boolean followUp = request.isFollowUp();
    DmAiRequest.DmAiRequestBuilder builder =
        DmAiRequest.builder()
            .userPrompt(request.getUserPrompt())
            .scenario(DmAiScenario.resolve(request.getScenarioId()))
            .includeCheckResults(request.inclusionEnabled(EdwAiInclusions.CHECKS))
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
        BaseMessages.getString(PKG, "DimensionalAiAdvisor.Inclusion." + keySuffix),
        false,
        BaseMessages.getString(PKG, "DimensionalAiAdvisor.Inclusion." + keySuffix + ".Tooltip"),
        BaseMessages.getString(PKG, "DimensionalAiAdvisor.Inclusion." + keySuffix + ".Summary"));
  }
}
