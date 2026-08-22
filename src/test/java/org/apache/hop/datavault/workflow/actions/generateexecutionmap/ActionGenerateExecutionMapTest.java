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
package org.apache.hop.datavault.workflow.actions.generateexecutionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.executionmap.CrawlOptions;
import org.apache.hop.i18n.BaseMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActionGenerateExecutionMapTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesConfiguredRootArtifactPath() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    assertEquals(
        "/workspace/retail-example/workflows/run-retail-update.hwf",
        ActionGenerateExecutionMap.resolveRootArtifactPath(
            "${PROJECT_HOME}/workflows/run-retail-update.hwf", null, variables));
  }

  @Test
  void buildsCrawlOptionsMatchingDialogDefaults() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();

    CrawlOptions options = action.toCrawlOptions();

    assertTrue(options.isIncludeGeneratedPipelines());
    assertTrue(options.isIncludeDatasetNodes());
    assertTrue(options.isCaptureSnapshots());
    assertTrue(options.isFollowNestedWorkflows());
    assertTrue(options.isFollowNestedPipelines());
    assertEquals(false, options.isIncludeWorkflowActions());
    assertEquals(false, options.isIncludePipelineTransforms());
  }

  @Test
  void failsWhenRootArtifactMissingAndNoParentWorkflow() {
    assertThrows(
        HopException.class,
        () -> ActionGenerateExecutionMap.resolveRootArtifactPath(null, null, new Variables()));
  }

  @Test
  void exposesExecutionMapReferencedObjectDescription() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();

    assertEquals("Execution Map", action.getReferencedObjectDescriptions()[0]);
    assertEquals(
        BaseMessages.getString(
            ActionGenerateExecutionMap.class,
            "ActionGenerateExecutionMap.ReferencedObject.Description"),
        action.getReferencedObjectDescriptions()[0]);
  }

  @Test
  void enablesReferencedObjectWhenOutputPathResolvable() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();
    action.setRootArtifactFilename("/workspace/retail-example/workflows/run-retail-update.hwf");

    assertTrue(action.isReferencedObjectEnabled()[0]);
  }

  @Test
  void enablesReferencedObjectWhenOutputHemFileConfigured() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();
    action.setOutputHemFile("/tmp/custom-map.hem");

    assertTrue(action.isReferencedObjectEnabled()[0]);
  }

  @Test
  void disablesReferencedObjectWhenOutputPathNotResolvable() {
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();

    assertFalse(action.isReferencedObjectEnabled()[0]);
  }

  @Test
  void supportsDrillDown() {
    assertTrue(new ActionGenerateExecutionMap().supportsDrillDown());
  }

  @Test
  void resolvesReferencedExecutionMapFromConfiguredOutputFile() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");
    ActionGenerateExecutionMap action = new ActionGenerateExecutionMap();
    action.setOutputHemFile("${PROJECT_HOME}/execution-maps/run-retail-initial.hem");

    assertEquals(
        "/workspace/retail-example/execution-maps/run-retail-initial.hem",
        action.resolveReferencedExecutionMapPath(variables));
  }

  @Test
  void resolvesRootArtifactFromParentWorkflowMetaWhenEngineUnavailable() throws HopException {
    Variables variables = new Variables();
    org.apache.hop.workflow.WorkflowMeta workflowMeta = new org.apache.hop.workflow.WorkflowMeta();
    workflowMeta.setFilename("/workspace/retail-example/workflows/run-retail-initial.hwf");

    assertEquals(
        "/workspace/retail-example/workflows/run-retail-initial.hwf",
        ActionGenerateExecutionMap.resolveRootArtifactPath(null, null, workflowMeta, variables));
  }
}
