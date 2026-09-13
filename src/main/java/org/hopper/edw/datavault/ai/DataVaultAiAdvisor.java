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
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorInclusion;
import org.apache.hop.ai.advisor.AiAdvisorInclusionChoice;
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
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.hopgui.ai.EdwAiAdvisorGraphSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;

@AiAdvisorPlugin(
    id = DataVaultAiAdvisor.ID,
    name = "i18n::DataVaultAiAdvisor.Name",
    description = "i18n::DataVaultAiAdvisor.Description",
    image = "datavault-ai-help.svg",
    locations = {EdwAiAdvisorLocations.DATA_VAULT_GRAPH})
public class DataVaultAiAdvisor implements IAiAdvisor {

  public static final String ID = "data-vault-advisor";
  private static final Class<?> PKG = DataVaultAiAdvisor.class;

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "DataVaultAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.DATA_VAULT_GRAPH};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (DvAiScenario scenario : DvAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<AiAdvisorInclusion> listInclusions() {
    return List.of(
        inclusion(EdwAiInclusions.CHECKS, "Checks", false),
        catalogInclusion(),
        inclusion(EdwAiInclusions.XML, "Xml", false),
        inclusion(EdwAiInclusions.LOAD_RUN_METRICS, "LoadRunMetrics", false),
        inclusion(EdwAiInclusions.EXECUTION_INFO, "ExecutionInfo", false));
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(
        BaseMessages.getString(PKG, "DataVaultAiAdvisor.Sharing.Structure"),
        BaseMessages.getString(PKG, "DataVaultAiAdvisor.Sharing.SourceClassification"));
  }

  @Override
  public List<AiAdvisorInclusionChoice> listInclusionChoices(
      String inclusionId, AiAdvisorRequest request) {
    if (!EdwAiInclusions.CATALOG.equals(inclusionId)
        || !(request != null && request.getArtifact() instanceof DataVaultModel model)) {
      return List.of();
    }
    try {
      List<String> names =
          DvSourceCatalogService.listSourceNames(
              model, request.getVariables(), request.getMetadataProvider());
      List<AiAdvisorInclusionChoice> choices = new ArrayList<>();
      for (String name : names) {
        choices.add(new AiAdvisorInclusionChoice(name, name));
      }
      return choices;
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    DataVaultModel model = modelFrom(request);
    DvAiContextBundle context =
        DvAiContextBuilder.build(
            model, request.getMetadataProvider(), request.getVariables(), toDvRequest(request));
    String userPrompt =
        request.isFollowUp()
            ? DvAiAdvisorService.buildFollowUpUserPrompt(context)
            : DvAiAdvisorService.buildInitialUserPrompt(context);
    return new AiAdvisorPrompt(DvAiAdvisorService.buildSystemPrompt(context), userPrompt);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwAiProposalSupport.parseEdwResponse(raw);
  }

  @Override
  public String previewProposal(AiProposal proposal) {
    return DvAiProposalApplier.preview(EdwAiProposalSupport.fromAi(proposal));
  }

  @Override
  public List<AiProposalValidation> validateProposals(
      AiAdvisorRequest request, List<AiProposal> proposals) {
    DataVaultModel model =
        request != null && request.getArtifact() instanceof DataVaultModel m ? m : null;
    return EdwAiProposalSupport.toDvValidations(
        DvAiProposalValidator.validate(
            model,
            EdwAiProposalSupport.fromAi(proposals),
            request != null ? request.getMetadataProvider() : null,
            request != null ? request.getVariables() : null));
  }

  @Override
  public void applyProposals(AiAdvisorRequest request, List<AiProposal> selected)
      throws HopException {
    DataVaultModel model = modelFrom(request);
    EdwAiAdvisorGraphSupport.markUndoPoint(request);
    DvAiProposalApplier.apply(
        model,
        EdwAiProposalSupport.fromAi(selected),
        request.getMetadataProvider(),
        request.getVariables());
  }

  @Override
  public void afterApply(AiAdvisorRequest request, List<AiProposal> applied) {
    EdwAiAdvisorGraphSupport.afterApply(request);
  }

  private static DataVaultModel modelFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof DataVaultModel model)) {
      throw new HopException("No Data Vault model is bound to this session.");
    }
    return model;
  }

  private static DvAiRequest toDvRequest(AiAdvisorRequest request) {
    boolean followUp = request.isFollowUp();
    DvAiRequest.DvAiRequestBuilder builder =
        DvAiRequest.builder()
            .userPrompt(request.getUserPrompt())
            .scenario(DvAiScenario.resolve(request.getScenarioId()))
            .includeCheckResults(request.inclusionEnabled(EdwAiInclusions.CHECKS))
            .includeCatalogSources(request.inclusionEnabled(EdwAiInclusions.CATALOG))
            .includeModelXml(request.inclusionEnabled(EdwAiInclusions.XML) && !followUp)
            .includeLoadRunMetrics(request.inclusionEnabled(EdwAiInclusions.LOAD_RUN_METRICS))
            .includeExecutionInfo(request.inclusionEnabled(EdwAiInclusions.EXECUTION_INFO))
            .logsExcerpt(request.getLogExcerpt())
            .followUp(followUp);
    List<String> catalogIds = request.selectedInclusionIds(EdwAiInclusions.CATALOG);
    if (catalogIds != null) {
      builder.catalogSourceNames(catalogIds);
    }
    List<String> applied = request.getAppliedChangeSummaries();
    if (applied != null) {
      builder.appliedChangeSummaries(applied);
    }
    return builder.build();
  }

  private static AiAdvisorInclusion inclusion(String id, String keySuffix, boolean picker) {
    return new AiAdvisorInclusion(
        id,
        BaseMessages.getString(PKG, "DataVaultAiAdvisor.Inclusion." + keySuffix),
        false,
        BaseMessages.getString(PKG, "DataVaultAiAdvisor.Inclusion." + keySuffix + ".Tooltip"),
        BaseMessages.getString(PKG, "DataVaultAiAdvisor.Inclusion." + keySuffix + ".Summary"),
        picker,
        true);
  }

  private static AiAdvisorInclusion catalogInclusion() {
    return inclusion(EdwAiInclusions.CATALOG, "Catalog", true);
  }
}
