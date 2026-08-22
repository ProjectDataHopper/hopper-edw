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
package org.apache.hop.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.junit.jupiter.api.Test;

class ModelGeneratedArtifactOpenSupportTest {

  @Test
  void reloadPipelineForGuiOpenCanBeClearedBeforeOpeningInGui() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("generated-update");
    pipelineMeta.addTransform(new TransformMeta("Constant", "add_LOAD_DATE", new ConstantMeta()));
    assertTrue(pipelineMeta.hasChanged());

    PipelineMeta reloaded =
        ModelGeneratedArtifactOpenSupport.reloadPipelineForGuiOpen(
            pipelineMeta, null, new Variables());
    reloaded.clearChanged();
    assertFalse(reloaded.hasChanged());
  }

  @Test
  void generatedWorkflowCanBeClearedBeforeOpeningInGui() {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName("generated-update-workflow");
    workflowMeta.addAction(new ActionMeta(new ActionStart()));

    assertTrue(workflowMeta.hasChanged());

    workflowMeta.clearChanged();
    assertFalse(workflowMeta.hasChanged());
  }
}
