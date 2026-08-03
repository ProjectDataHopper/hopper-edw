/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.ai.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.dummy.ActionDummy;
import org.junit.jupiter.api.Test;

class WorkflowAiContextBuilderTest {

  @Test
  void serializeStructureIncludesActionsHopsAndFocus() {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName("demo-workflow");

    ActionMeta start = new ActionMeta(new ActionDummy());
    start.setName("Start");
    ActionMeta end = new ActionMeta(new ActionDummy());
    end.setName("End");
    workflowMeta.addAction(start);
    workflowMeta.addAction(end);
    workflowMeta.addWorkflowHop(new WorkflowHopMeta(start, end));

    String json = WorkflowAiContextBuilder.serializeStructure(workflowMeta, "Start");

    assertTrue(json.contains("\"name\":\"Start\""));
    assertTrue(json.contains("\"name\":\"End\""));
    assertTrue(json.contains("\"from\":\"Start\""));
    assertTrue(json.contains("\"to\":\"End\""));
    assertTrue(json.contains("\"focusAction\":\"Start\""));
  }

  @Test
  void buildOmitsLogsWhenExecutionLogDisabled() throws Exception {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName("demo");

    WorkflowAiRequest request =
        WorkflowAiRequest.builder()
            .userPrompt("Why did this fail?")
            .includeExecutionLog(false)
            .logsExcerpt("ERROR: action failed")
            .includeCheckResults(false)
            .includeActionCatalog(false)
            .includeTopologyXml(false)
            .build();

    var bundle = WorkflowAiContextBuilder.build(workflowMeta, null, new Variables(), request);

    assertTrue(bundle.getLogsExcerpt().isEmpty());
  }

  @Test
  void followUpPromptOmitsFullCatalogContext() {
    WorkflowAiContextBundle context =
        WorkflowAiContextBundle.builder()
            .scenario(WorkflowAiScenario.WORKFLOW_GENERAL)
            .userPrompt("What next?")
            .structureJson("{\"actions\":[]}")
            .summaryJson("{\"name\":\"demo\"}")
            .actionCatalogJson("{\"actions\":[]}")
            .checkResultsJson("{\"results\":[]}")
            .followUp(true)
            .build();

    String prompt = WorkflowAiAdvisorService.buildFollowUpUserPrompt(context);

    assertTrue(prompt.contains("What next?"));
    assertTrue(prompt.contains("Workflow structure JSON"));
    assertFalse(prompt.contains("Workflow summary JSON"));
    assertFalse(prompt.contains("Available workflow action plugins JSON"));
  }
}
