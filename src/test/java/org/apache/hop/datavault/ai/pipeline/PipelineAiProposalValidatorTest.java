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
package org.apache.hop.datavault.ai.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.datavault.ai.HopAiProposal;
import org.apache.hop.datavault.ai.HopAiProposalValidation;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineAiProposalValidatorTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void validatesAddTransformAndHop() {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta input = new TransformMeta("TableInput", "Input", null);
    TransformMeta output = new TransformMeta("TableOutput", "Output", null);
    input.setLocation(100, 100);
    output.setLocation(300, 100);
    pipelineMeta.addTransform(input);
    pipelineMeta.addTransform(output);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(input, output));

    HopAiProposal addTransform =
        proposal(
            HopAiProposal.Type.ADD_TRANSFORM,
            Map.of(
                "transformPluginId", "Dummy",
                "name", "Check",
                "locationX", "200",
                "locationY", "100"));
    HopAiProposal addHop =
        proposal(
            HopAiProposal.Type.ADD_PIPELINE_HOP,
            Map.of("fromTransform", "Input", "toTransform", "Check"));
    HopAiProposal duplicateName =
        proposal(
            HopAiProposal.Type.ADD_TRANSFORM,
            Map.of(
                "transformPluginId", "Dummy",
                "name", "Input",
                "locationX", "50",
                "locationY", "50"));

    List<HopAiProposalValidation.Result> results =
        PipelineAiProposalValidator.validate(
            pipelineMeta, List.of(addTransform, addHop, duplicateName));

    assertEquals(HopAiProposalValidation.Status.OK, results.get(0).getStatus());
    assertEquals(HopAiProposalValidation.Status.OK, results.get(1).getStatus());
    assertEquals(HopAiProposalValidation.Status.BLOCKED, results.get(2).getStatus());
  }

  private static HopAiProposal proposal(HopAiProposal.Type type, Map<String, String> parameters) {
    HopAiProposal proposal = new HopAiProposal();
    proposal.setType(type);
    proposal.setDescription(type.name());
    proposal.setParameters(parameters);
    return proposal;
  }
}
