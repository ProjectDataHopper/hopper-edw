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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.core.Result;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metrics.DvUpdateMetricsCollector;
import org.apache.hop.datavault.metrics.DvUpdateMetricsConstants;
import org.junit.jupiter.api.Test;

class DvPipelineOrchestratorSupportTest {

  @Test
  void sequenceFilenamesPreserveExecutionOrder() {
    assertEquals(
        "0001-hub-customer.hpl",
        DvPipelineOrchestratorSupport.buildStagedPipelineFilename("hub-customer", 1, true));
    assertEquals(
        "0002-link-order.hpl",
        DvPipelineOrchestratorSupport.buildStagedPipelineFilename("link-order", 2, true));
    assertEquals(
        "hub-customer.hpl",
        DvPipelineOrchestratorSupport.buildStagedPipelineFilename("hub-customer", 1, false));
  }

  @Test
  void initializeMetricsRunSetsRunScopedVariables() {
    Variables variables = new Variables();
    DvUpdateMetricsCollector.LoadRunPublishContext publishContext =
        DvUpdateMetricsCollector.LoadRunPublishContext.withDefaults(
            "local-catalog", "Vault", "update-retail-dv-bv-dm", "dm");

    String runId =
        DvPipelineOrchestratorSupport.initializeMetricsRun(
            variables, "retail-f-orders", publishContext);

    assertNotNull(runId);
    assertEquals(runId, variables.getVariable(DvUpdateMetricsConstants.VAR_RUN_ID));
    assertEquals("retail-f-orders", variables.getVariable(DvUpdateMetricsConstants.VAR_MODEL_NAME));
    assertEquals("dm", variables.getVariable(DvUpdateMetricsConstants.VAR_MODEL_TYPE));
    assertEquals(
        "update-retail-dv-bv-dm",
        variables.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_NAME));
    assertEquals("Vault", variables.getVariable(DvUpdateMetricsConstants.VAR_METRICS_DATABASE));
    assertEquals(
        "local-catalog",
        variables.getVariable(DvUpdateMetricsConstants.VAR_METRICS_CATALOG_CONNECTION));
  }

  @Test
  void initializeMetricsRunCopiesWorkflowExecutionIdFromInheritedVariables() {
    Variables parentWorkflow = new Variables();
    parentWorkflow.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, "root-exec-id");
    Variables variables = new Variables();
    variables.initializeFrom(parentWorkflow);
    DvUpdateMetricsCollector.LoadRunPublishContext publishContext =
        DvUpdateMetricsCollector.LoadRunPublishContext.withDefaults(
            "local-catalog", "Vault", "update-retail-dv-bv-dm", "dv");

    DvPipelineOrchestratorSupport.initializeMetricsRun(variables, "retail-360", publishContext);

    assertEquals(
        "root-exec-id", variables.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID));
  }

  @Test
  void mergeResultPropagatesFailureFlag() {
    Result target = new Result();
    target.setResult(true);

    Result source = new Result();
    source.setResult(false);
    source.setNrErrors(3);

    DvPipelineOrchestratorSupport.mergeResult(target, source);

    assertEquals(3, target.getNrErrors());
    assertFalse(target.getResult());
  }
}
