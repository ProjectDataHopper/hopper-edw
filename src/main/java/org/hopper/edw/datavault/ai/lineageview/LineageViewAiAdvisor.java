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
package org.hopper.edw.datavault.ai.lineageview;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorPlugin;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiAdvisorResponse;
import org.apache.hop.ai.advisor.AiAdvisorScenario;
import org.apache.hop.ai.advisor.IAiAdvisor;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.ai.EdwChatOnlyAdvisorSupport;
import org.hopper.edw.datavault.ai.HopAiPromptLoader;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;

@AiAdvisorPlugin(
    id = LineageViewAiAdvisor.ID,
    name = "i18n::LineageViewAiAdvisor.Name",
    description = "i18n::LineageViewAiAdvisor.Description",
    image = "datavault-ai-help.svg",
    locations = {EdwAiAdvisorLocations.LINEAGE_VIEW})
public class LineageViewAiAdvisor implements IAiAdvisor {

  public static final String ID = "lineage-view-advisor";
  private static final Class<?> PKG = LineageViewAiAdvisor.class;
  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/lineageview/";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "LineageViewAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.LINEAGE_VIEW};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (LineageViewAiScenario scenario : LineageViewAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(
        BaseMessages.getString(PKG, "LineageViewAiAdvisor.Sharing.Definition"),
        BaseMessages.getString(PKG, "LineageViewAiAdvisor.Sharing.SessionGraph"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    HopLineageViewDocument document = documentFrom(request);
    LineageViewAiScenario scenario = LineageViewAiScenario.resolve(request.getScenarioId());
    String system =
        HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble.txt")
            + "\n\n"
            + HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt");
    String user =
        LineageViewAiContextBuilder.userPrompt(
            request,
            LineageViewAiContextBuilder.definitionJson(document, request.getFocusNodeName()),
            LineageViewAiContextBuilder.sessionGraphJson(document, request));
    return new AiAdvisorPrompt(system, user);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwChatOnlyAdvisorSupport.parseAdviceOnly(raw);
  }

  private static HopLineageViewDocument documentFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof HopLineageViewDocument document)) {
      throw new HopException("No lineage view is bound to this session.");
    }
    return document;
  }
}
