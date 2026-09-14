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
package org.hopper.edw.datavault.ai.executionmap;

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
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;

@AiAdvisorPlugin(
    id = ExecutionMapAiAdvisor.ID,
    name = "i18n::ExecutionMapAiAdvisor.Name",
    description = "i18n::ExecutionMapAiAdvisor.Description",
    image = "datavault-ai-help.svg",
    locations = {EdwAiAdvisorLocations.EXECUTION_MAP})
public class ExecutionMapAiAdvisor implements IAiAdvisor {

  public static final String ID = "execution-map-advisor";
  private static final Class<?> PKG = ExecutionMapAiAdvisor.class;
  private static final String PROMPT_ROOT = "/org/hopper/edw/datavault/ai/prompts/executionmap/";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getName() {
    return BaseMessages.getString(PKG, "ExecutionMapAiAdvisor.Name");
  }

  @Override
  public String[] getLocations() {
    return new String[] {EdwAiAdvisorLocations.EXECUTION_MAP};
  }

  @Override
  public List<AiAdvisorScenario> listScenarios() {
    List<AiAdvisorScenario> scenarios = new ArrayList<>();
    for (ExecutionMapAiScenario scenario : ExecutionMapAiScenario.values()) {
      scenarios.add(
          new AiAdvisorScenario(
              scenario.getCode(), scenario.getDescription(), scenario.getDescription()));
    }
    return scenarios;
  }

  @Override
  public List<String> listBaselineSharing() {
    return List.of(BaseMessages.getString(PKG, "ExecutionMapAiAdvisor.Sharing.Structure"));
  }

  @Override
  public AiAdvisorPrompt buildPrompt(AiAdvisorRequest request) throws HopException {
    ExecutionMapDocument document = documentFrom(request);
    ExecutionMapAiScenario scenario = ExecutionMapAiScenario.resolve(request.getScenarioId());
    String system =
        HopAiPromptLoader.loadResource(PROMPT_ROOT, "preamble.txt")
            + "\n\n"
            + HopAiPromptLoader.loadResource(PROMPT_ROOT, scenario.getPromptResource() + ".txt");
    String user =
        ExecutionMapAiContextBuilder.userPrompt(
            request,
            ExecutionMapAiContextBuilder.structureJson(document, request.getFocusNodeName()));
    return new AiAdvisorPrompt(system, user);
  }

  @Override
  public AiAdvisorResponse parseResponse(String raw) {
    return EdwChatOnlyAdvisorSupport.parseAdviceOnly(raw);
  }

  private static ExecutionMapDocument documentFrom(AiAdvisorRequest request) throws HopException {
    if (request == null || !(request.getArtifact() instanceof ExecutionMapDocument document)) {
      throw new HopException("No execution map is bound to this session.");
    }
    return document;
  }
}
