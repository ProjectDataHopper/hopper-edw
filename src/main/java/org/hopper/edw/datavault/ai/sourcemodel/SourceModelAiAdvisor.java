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
package org.hopper.edw.datavault.ai.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorInclusion;
import org.apache.hop.ai.advisor.AiAdvisorPlugin;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.ai.advisor.AiAdvisorScenario;
import org.apache.hop.ai.advisor.IAiAdvisor;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.ai.EdwAiInclusions;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.ai.HopAiPromptLoader;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

@AiAdvisorPlugin(
    id = SourceModelAiAdvisor.ID,
    name = "i18n::SourceModelAiAdvisor.Name",
    description = "i18n::SourceModelAiAdvisor.Description",
    image = "ai-provider.svg",
    locations = {EdwAiAdvisorLocations.SOURCE_MODEL_GRAPH})
public class SourceModelAiAdvisor implements IAiAdvisor {

  public static final String ID = "source-model-advisor";
  private static final Class<?> PKG = SourceModelAiAdvisor.class;
  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/sourcemodel/";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "SourceModelAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.SOURCE_MODEL_GRAPH};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (SourceModelAiScenario scenario : SourceModelAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<AiAdvisorInclusion> listInclusions() {
    return List.of(
        EdwChatOnlyAdvisorSupport.inclusion(
            PKG, "SourceModelAiAdvisor", EdwAiInclusions.CHECKS, "Checks", false));
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(
        BaseMessages.getString(PKG, "SourceModelAiAdvisor.Sharing.Structure"),
        BaseMessages.getString(PKG, "SourceModelAiAdvisor.Sharing.Classification"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    SourceModel model = modelFrom(request);
    SourceModelAiScenario scenario = SourceModelAiScenario.resolve(request.getScenarioId());
    String system =
        HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble.txt")
            + "\n\n"
            + HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt");
    String user =
        SourceModelAiContextBuilder.userPrompt(
            request,
            SourceModelAiContextBuilder.structureJson(model, request.getFocusNodeName()),
            SourceModelAiContextBuilder.classificationJson(model),
            SourceModelAiContextBuilder.checkResultsJson(
                model, request.getMetadataProvider(), request.getVariables(), request));
    return new AiAdvisorPrompt(system, user);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwChatOnlyAdvisorSupport.parseAdviceOnly(raw);
  }

  private static SourceModel modelFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof SourceModel model)) {
      throw new HopException("No source model is bound to this session.");
    }
    return model;
  }
}
