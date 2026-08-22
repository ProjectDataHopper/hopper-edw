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
package org.hopper.edw.datavault.ai.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.Test;

class PipelineAiContextBuilderTest {

  @Test
  void serializeStructureIncludesTransformsHopsAndFocus() {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("demo-pipeline");

    TransformMeta input = new TransformMeta("TableInput", "Input", null);
    TransformMeta output = new TransformMeta("TableOutput", "Output", null);
    pipelineMeta.addTransform(input);
    pipelineMeta.addTransform(output);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(input, output));

    String json = PipelineAiContextBuilder.serializeStructure(pipelineMeta, "Input");

    assertTrue(json.contains("\"name\":\"Input\""));
    assertTrue(json.contains("\"name\":\"Output\""));
    assertTrue(json.contains("\"from\":\"Input\""));
    assertTrue(json.contains("\"to\":\"Output\""));
    assertTrue(json.contains("\"focusTransform\":\"Input\""));
  }

  @Test
  void buildOmitsLogsWhenExecutionLogDisabled() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("demo");

    PipelineAiRequest request =
        PipelineAiRequest.builder()
            .userPrompt("Why did this fail?")
            .includeExecutionLog(false)
            .logsExcerpt("ERROR: row rejected")
            .includeCheckResults(false)
            .includeTransformCatalog(false)
            .includeTopologyXml(false)
            .build();

    var bundle = PipelineAiContextBuilder.build(pipelineMeta, null, new Variables(), request);

    assertTrue(bundle.getLogsExcerpt().isEmpty());
  }

  @Test
  void buildIncludesLogsWhenExecutionLogEnabled() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("demo");

    PipelineAiRequest request =
        PipelineAiRequest.builder()
            .userPrompt("Why did this fail?")
            .includeExecutionLog(true)
            .logsExcerpt("ERROR: row rejected")
            .includeCheckResults(false)
            .includeTransformCatalog(false)
            .includeTopologyXml(false)
            .build();

    var bundle = PipelineAiContextBuilder.build(pipelineMeta, null, new Variables(), request);

    assertTrue(bundle.getLogsExcerpt().contains("ERROR: row rejected"));
  }

  @Test
  void followUpPromptOmitsFullCatalogContext() {
    PipelineAiContextBundle context =
        PipelineAiContextBundle.builder()
            .scenario(PipelineAiScenario.PIPELINE_GENERAL)
            .userPrompt("What next?")
            .structureJson("{\"transforms\":[]}")
            .summaryJson("{\"name\":\"demo\"}")
            .transformCatalogJson("{\"transforms\":[]}")
            .checkResultsJson("{\"results\":[]}")
            .followUp(true)
            .build();

    String prompt = PipelineAiAdvisorService.buildFollowUpUserPrompt(context);

    assertTrue(prompt.contains("What next?"));
    assertTrue(prompt.contains("Pipeline structure JSON"));
    assertFalse(prompt.contains("Pipeline summary JSON"));
    assertFalse(prompt.contains("Available transform plugins JSON"));
  }
}
