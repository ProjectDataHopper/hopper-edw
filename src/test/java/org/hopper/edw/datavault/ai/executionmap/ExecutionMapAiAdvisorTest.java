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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.hopper.edw.datavault.ai.EdwAiAdvisorLocations;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapAiAdvisorTest {

  @Test
  void listsScenariosAndLocation() {
    ExecutionMapAiAdvisor advisor = new ExecutionMapAiAdvisor();
    assertEquals(EdwAiAdvisorLocations.EXECUTION_MAP, advisor.getLocations()[0]);
    assertTrue(
        advisor.listScenarios().stream()
            .anyMatch(s -> ExecutionMapAiScenario.EXPLAIN_MAP.getCode().equals(s.getId())));
  }

  @Test
  void buildPromptIncludesWorkflowNodesAndSkipsPipelineInternals() throws Exception {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setName("run-update");
    document.setRootArtifactPath("${PROJECT_HOME}/workflows/run-update.hwf");

    ExecutionMapNode root = new ExecutionMapNode();
    root.setId("wf-root");
    root.setName("run-update");
    root.setNodeType(ExecutionMapNodeType.ROOT_WORKFLOW);
    document.getNodesOrEmpty().add(root);

    ExecutionMapNode transform = new ExecutionMapNode();
    transform.setId("tr-1");
    transform.setName("hidden transform");
    transform.setNodeType(ExecutionMapNodeType.PIPELINE_TRANSFORM);
    document.getNodesOrEmpty().add(transform);

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(document);
    request.setUserPrompt("What does this map run?");

    AiAdvisorPrompt prompt = new ExecutionMapAiAdvisor().buildPrompt(request);
    assertTrue(prompt.getUserPrompt().contains("run-update"));
    assertFalse(prompt.getUserPrompt().contains("hidden transform"));
  }
}
