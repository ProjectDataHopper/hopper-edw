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
package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engines.local.LocalWorkflowEngine;
import org.junit.jupiter.api.Test;

class VaultUpdateExecutionSupportTest {

  private LocalWorkflowEngine parentWorkflow() {
    return new LocalWorkflowEngine(new WorkflowMeta());
  }

  @Test
  void beginExecutionAssignsExecutionIdAndStartedAtOnParentWorkflow() {
    LocalWorkflowEngine parentWorkflow = parentWorkflow();
    String executionId =
        VaultUpdateExecutionSupport.beginExecution(parentWorkflow, null, false, false);

    assertNotNull(executionId);
    assertEquals(
        executionId,
        parentWorkflow.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID));
    assertNotNull(
        parentWorkflow.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT));
    assertNotNull(VaultUpdateExecutionSupport.resolveStartedAt(parentWorkflow, null));
  }

  @Test
  void beginExecutionCanReuseExistingExecutionId() {
    LocalWorkflowEngine parentWorkflow = parentWorkflow();
    parentWorkflow.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, "existing-id");

    String executionId =
        VaultUpdateExecutionSupport.beginExecution(parentWorkflow, null, true, false);

    assertEquals("existing-id", executionId);
  }

  @Test
  void formatStartedAtRoundTripsThroughResolveStartedAt() {
    Date startedAt = new Date(1_700_000_000_000L);
    String formatted = VaultUpdateExecutionSupport.formatStartedAt(startedAt);

    Variables variables = new Variables();
    variables.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT, formatted);

    Date resolved = VaultUpdateExecutionSupport.resolveStartedAt(variables, null);
    assertNotNull(resolved);
    assertEquals(startedAt.getTime() / 1000, resolved.getTime() / 1000);
  }

  @Test
  void resolveExecutionIdReturnsNullWhenUnset() {
    assertNull(VaultUpdateExecutionSupport.resolveExecutionId(new Variables(), null));
  }

  @Test
  void resolveStartedAtReturnsNullForInvalidValue() {
    Variables variables = new Variables();
    variables.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT, "not-a-date");
    assertNull(VaultUpdateExecutionSupport.resolveStartedAt(variables, null));
  }

  @Test
  void beginExecutionOverwritesExistingIdByDefault() {
    LocalWorkflowEngine parentWorkflow = parentWorkflow();
    parentWorkflow.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, "existing-id");

    String executionId =
        VaultUpdateExecutionSupport.beginExecution(parentWorkflow, null, false, false);

    assertNotNull(executionId);
    assertFalse("existing-id".equals(executionId));
  }

  @Test
  void beginExecutionCanUseWorkflowLogChannelId() {
    LocalWorkflowEngine parentWorkflow = parentWorkflow();
    String logChannelId = parentWorkflow.getLogChannelId();

    String executionId =
        VaultUpdateExecutionSupport.beginExecution(parentWorkflow, null, false, true);

    assertEquals(logChannelId, executionId);
    assertEquals(
        logChannelId,
        parentWorkflow.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID));
  }

  @Test
  void resolveExecutionIdFallsBackToWorkflowLogChannelId() {
    assertEquals(
        "workflow-log-1",
        VaultUpdateExecutionSupport.resolveExecutionId(
            new Variables(), null, true, "workflow-log-1"));
  }

  @Test
  void resolveExecutionIdPrefersVariableOverWorkflowLogChannelId() {
    Variables variables = new Variables();
    variables.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, "from-variable");

    assertEquals(
        "from-variable",
        VaultUpdateExecutionSupport.resolveExecutionId(variables, null, true, "workflow-log-1"));
  }

  @Test
  void childWorkflowVariablesInheritParentExecutionId() {
    LocalWorkflowEngine parentWorkflow = parentWorkflow();
    VaultUpdateExecutionSupport.beginExecution(parentWorkflow, null, false, false);

    Variables childWorkflow = new Variables();
    childWorkflow.initializeFrom(parentWorkflow);

    assertEquals(
        parentWorkflow.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID),
        VaultUpdateExecutionSupport.resolveExecutionId(childWorkflow, null));
  }
}
