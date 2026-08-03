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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvMultiSourceUpdateWorkflowSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void singlePipelineProducesNoWorkflow() throws HopException {
    PipelineMeta only = namedPipeline("hub-customer_src1");
    List<WorkflowMeta> workflows =
        DvMultiSourceUpdateWorkflowSupport.buildSerialWorkflowsIfMultiSource(
            "hub-customer-multi-source", List.of(only), stubFactory());
    assertTrue(workflows.isEmpty());
  }

  @Test
  void multiPipelineProducesSerialWorkflow() throws HopException {
    PipelineMeta p1 = namedPipeline("hub-customer_src1");
    PipelineMeta p2 = namedPipeline("hub-customer_src2");
    PipelineMeta p3 = namedPipeline("hub-customer_src3");

    List<WorkflowMeta> workflows =
        DvMultiSourceUpdateWorkflowSupport.buildSerialWorkflowsIfMultiSource(
            "hub-customer-multi-source", List.of(p1, p2, p3), stubFactory());

    assertEquals(1, workflows.size());
    WorkflowMeta workflow = workflows.get(0);
    assertEquals("hub-customer-multi-source", workflow.getName());

    // Start + 3 pipeline actions
    assertEquals(4, workflow.getActions().size());
    assertEquals(3, workflow.nrWorkflowHops());

    Set<String> referenced =
        DvMultiSourceUpdateWorkflowSupport.collectReferencedPipelineFilenames(workflow);
    assertEquals(3, referenced.size());
    assertTrue(referenced.contains("hub-customer_src1.hpl"));
    assertTrue(referenced.contains("hub-customer_src2.hpl"));
    assertTrue(referenced.contains("hub-customer_src3.hpl"));

    ActionMeta start = workflow.findAction("Start");
    assertTrue(start != null);
  }

  @Test
  void updateArtifactsExcludesNestedFromFreeList() throws HopException {
    PipelineMeta p1 = namedPipeline("hub-customer_src1");
    PipelineMeta p2 = namedPipeline("hub-customer_src2");
    PipelineMeta sat = namedPipeline("sat-customer-demo");

    WorkflowMeta workflow =
        DvMultiSourceUpdateWorkflowSupport.buildSerialSourceWorkflow(
            "hub-customer-multi-source", List.of(p1, p2), null, stubFactory());

    DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts artifacts =
        DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts.of(
            List.of(p1, p2, sat), List.of(workflow));

    assertEquals(1, artifacts.freePipelines().size());
    assertEquals("sat-customer-demo", artifacts.freePipelines().get(0).getName());
    assertEquals(2, artifacts.nestedPipelines().size());
    assertEquals(1, artifacts.multiSourceWorkflows().size());
  }

  @Test
  void updateArtifactsAllFreeWhenNoWorkflows() {
    PipelineMeta p1 = namedPipeline("sat-a");
    DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts artifacts =
        DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts.of(List.of(p1), List.of());
    assertEquals(1, artifacts.freePipelines().size());
    assertTrue(artifacts.nestedPipelines().isEmpty());
    assertTrue(artifacts.multiSourceWorkflows().isEmpty());
  }

  private static PipelineMeta namedPipeline(String name) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);
    return pipelineMeta;
  }

  private static DvMultiSourceUpdateWorkflowSupport.PipelineActionFactory stubFactory() {
    return StubPipelineAction::new;
  }

  /** Minimal PIPELINE action stub for unit tests without the action plugin on the classpath. */
  private static final class StubPipelineAction extends ActionBase implements IAction {
    private String filename;

    StubPipelineAction(String name) {
      super(name, "");
      setPluginId(DvMultiSourceUpdateWorkflowSupport.PIPELINE_ACTION_ID);
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    @Override
    public org.apache.hop.core.Result execute(org.apache.hop.core.Result prevResult, int nr) {
      return prevResult != null ? prevResult : new org.apache.hop.core.Result();
    }
  }
}
