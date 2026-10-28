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
import java.util.LinkedHashSet;
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
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.security.EdwWebAuthorization;
import org.hopper.edw.datavault.hopgui.ai.EdwAiAdvisorGraphSupport;
import org.hopper.edw.datavault.hopgui.ai.EdwAiBoundModel;
import org.hopper.edw.datavault.hopgui.ai.EdwAiBoundModel.CatalogCoachSource;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;

@AiAdvisorPlugin(
    id = DataVaultAiAdvisor.ID,
    name = "i18n::DataVaultAiAdvisor.Name",
    description = "i18n::DataVaultAiAdvisor.Description",
    image = "ai-provider.svg",
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
    if (!EdwAiInclusions.CATALOG.equals(inclusionId) || request == null) {
      return List.of();
    }
    DataVaultModel bound = EdwAiBoundModel.bind(request, DataVaultModel.class);
    Object model = bound != null ? bound : request.getArtifact();
    if (bound == null && !EdwAiBoundModel.isForeignModel(model, DataVaultModel.class)) {
      return List.of();
    }
    List<String> coachNames = new ArrayList<>();
    for (CatalogCoachSource source : EdwAiBoundModel.coachCatalogSources(model)) {
      if (source != null && !Utils.isEmpty(source.recordName())) {
        coachNames.add(source.recordName());
      }
    }
    List<String> catalogNames = List.of();
    try {
      String connection =
          EdwAiBoundModel.catalogConnectionName(
              model, DvSourceCatalogService.DEFAULT_SOURCE_CATALOG_CONNECTION);
      catalogNames =
          DvSourceCatalogService.listSourceNames(
              connection, request.getVariables(), request.getMetadataProvider());
    } catch (Exception e) {
      if (HopLogStore.isInitialized()) {
        LogChannel.GENERAL.logError("Could not list catalog sources for the AI advisor", e);
      }
    }
    return catalogChoices(coachNames, catalogNames);
  }

  /**
   * Coach record names first, labeled so the Select dialog shows where they came from, then the
   * other catalog sources. Duplicate names are listed once.
   */
  static List<AiAdvisorInclusionChoice> catalogChoices(
      List<String> coachNames, List<String> catalogNames) {
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    List<AiAdvisorInclusionChoice> choices = new ArrayList<>();
    if (coachNames != null) {
      for (String name : coachNames) {
        if (Utils.isEmpty(name) || !seen.add(name)) {
          continue;
        }
        choices.add(
            new AiAdvisorInclusionChoice(
                name,
                BaseMessages.getString(
                    PKG, "DataVaultAiAdvisor.Inclusion.Catalog.CoachLabel", name)));
      }
    }
    if (catalogNames != null) {
      for (String name : catalogNames) {
        if (Utils.isEmpty(name) || !seen.add(name)) {
          continue;
        }
        choices.add(new AiAdvisorInclusionChoice(name, name));
      }
    }
    return choices;
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    Object artifact = request == null ? null : request.getArtifact();
    if (EdwAiBoundModel.isForeignModel(artifact, DataVaultModel.class)) {
      return promptFromStructure(request, EdwAiBoundModel.structureOf(artifact));
    }
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
    List<DvAiProposal> parsed = EdwAiProposalSupport.fromAi(proposals);
    try {
      return EdwAiProposalSupport.toDvValidations(
          DvAiProposalValidator.validate(
              modelFor(request),
              parsed,
              request != null ? request.getMetadataProvider() : null,
              request != null ? request.getVariables() : null));
    } catch (Exception e) {
      String message = e.getMessage();
      if (Utils.isEmpty(message)) {
        message = "Could not validate proposals";
      }
      List<DvAiProposalValidator.ValidationResult> blocked = new ArrayList<>();
      for (DvAiProposal proposal : parsed) {
        blocked.add(
            new DvAiProposalValidator.ValidationResult(
                proposal, DvAiProposalValidator.Status.BLOCKED, message));
      }
      return EdwAiProposalSupport.toDvValidations(blocked);
    }
  }

  @Override
  public void applyProposals(AiAdvisorRequest request, List<AiProposal> selected)
      throws HopException {
    if (!EdwWebAuthorization.canEditModels()) {
      throw new HopException(EdwWebAuthorization.MODEL_EDIT_DENIED);
    }
    DataVaultModel model = modelFor(request);
    if (model == null) {
      throw new HopException(EdwAiBoundModel.unbound("Data Vault model", request));
    }
    EdwAiAdvisorGraphSupport.markUndoPoint(request);
    DvAiProposalApplier.apply(
        model,
        EdwAiProposalSupport.fromAi(selected),
        request.getMetadataProvider(),
        request.getVariables());
    EdwAiBoundModel.writeBack(model, request);
  }

  @Override
  public void afterApply(AiAdvisorRequest request, List<AiProposal> applied) {
    EdwAiAdvisorGraphSupport.afterApply(request);
  }

  private static AiAdvisorPrompt promptFromStructure(AiAdvisorRequest request, String structure)
      throws HopException {
    DvAiRequest dvRequest = toDvRequest(request);
    DvAiScenario scenario =
        dvRequest.getScenario() != null ? dvRequest.getScenario() : DvAiScenario.GENERAL;
    String recordDefinitionsJson = "";
    if (dvRequest.isIncludeCatalogSources()) {
      recordDefinitionsJson =
          DvAiContextBuilder.serializeCatalogSources(
              request.getArtifact(),
              request.getMetadataProvider(),
              request.getVariables(),
              dvRequest);
    }
    DvAiContextBundle context =
        DvAiContextBundle.builder()
            .scenario(scenario)
            .userPrompt(dvRequest.getUserPrompt())
            .modelStructureJson(structure)
            .modelSummaryJson("{}")
            .recordDefinitionsJson(recordDefinitionsJson)
            .followUp(dvRequest.isFollowUp())
            .appliedChangeSummaries(
                dvRequest.getAppliedChangeSummaries() != null
                    ? dvRequest.getAppliedChangeSummaries()
                    : List.of())
            .build();
    String userPrompt =
        request.isFollowUp()
            ? DvAiAdvisorService.buildFollowUpUserPrompt(context)
            : DvAiAdvisorService.buildInitialUserPrompt(context);
    return new AiAdvisorPrompt(DvAiAdvisorService.buildSystemPrompt(context), userPrompt);
  }

  private static DataVaultModel modelFrom(AiAdvisorRequest request) throws HopException {
    DataVaultModel model = modelFor(request);
    if (model == null) {
      throw new HopException(EdwAiBoundModel.unbound("Data Vault model", request));
    }
    return model;
  }

  /**
   * The open model, including a same-loader copy when the modeler loaded it. An empty model is
   * still the model: its named Data Vault configuration stays attached so catalog checks use that
   * connection.
   */
  private static DataVaultModel modelFor(AiAdvisorRequest request) throws HopException {
    DataVaultModel model = EdwAiBoundModel.bind(request, DataVaultModel.class);
    if (model == null) {
      Object artifact = request == null ? null : request.getArtifact();
      if (EdwAiBoundModel.isForeignModel(artifact, DataVaultModel.class)) {
        model = EdwAiBoundModel.copy(artifact, DataVaultModel.class, request.getMetadataProvider());
      }
    }
    if (model != null && request != null) {
      ModelConfigurationResolver.attach(model, request.getMetadataProvider());
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
