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
package org.hopper.edw.datavault.ai.journey;

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
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;

@AiAdvisorPlugin(
    id = EdwJourneyAiAdvisor.ID,
    name = "i18n::EdwJourneyAiAdvisor.Name",
    description = "i18n::EdwJourneyAiAdvisor.Description",
    image = "ai-provider.svg",
    locations = {EdwAiAdvisorLocations.EDW_JOURNEY})
public class EdwJourneyAiAdvisor implements IAiAdvisor {

  public static final String ID = "edw-journey-advisor";
  private static final Class<?> PKG = EdwJourneyAiAdvisor.class;
  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/journey/";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "EdwJourneyAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.EDW_JOURNEY};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (EdwJourneyAiScenario scenario : EdwJourneyAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(
        BaseMessages.getString(PKG, "EdwJourneyAiAdvisor.Sharing.Snapshot"),
        BaseMessages.getString(PKG, "EdwJourneyAiAdvisor.Sharing.Ops"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    EdwJourneySnapshot snapshot = EdwJourneyAiContextBuilder.snapshotFrom(request);
    EdwJourneyAiScenario scenario = EdwJourneyAiScenario.resolve(request.getScenarioId());
    String system =
        HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble.txt")
            + "\n\n"
            + HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt");
    String user =
        EdwJourneyAiContextBuilder.userPrompt(
            request,
            EdwJourneyAiContextBuilder.snapshotJson(snapshot, request.getFocusNodeName()),
            EdwJourneyAiContextBuilder.opsJson(request));
    return new AiAdvisorPrompt(system, user);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwChatOnlyAdvisorSupport.parseAdviceOnly(raw);
  }
}
