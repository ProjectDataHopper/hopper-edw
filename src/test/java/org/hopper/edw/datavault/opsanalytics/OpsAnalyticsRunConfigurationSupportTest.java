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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.execution.ExecutionInfoLocation;
import org.apache.hop.execution.caching.CachingFileExecutionInfoLocation;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.apache.hop.workflow.engines.local.LocalWorkflowRunConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpsAnalyticsRunConfigurationSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void defaultsHaveNoDataProfileAndASeparateExecutionLocation() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    OpsAnalyticsRequest request = OpsAnalyticsRequest.defaults();

    OpsAnalyticsRunConfigurationSupport.ensure(request, provider);

    PipelineRunConfiguration pipeline =
        provider
            .getSerializer(PipelineRunConfiguration.class)
            .load(OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION);
    WorkflowRunConfiguration workflow =
        provider
            .getSerializer(WorkflowRunConfiguration.class)
            .load(OpsAnalyticsRunConfigurationSupport.WORKFLOW_RUN_CONFIGURATION);
    ExecutionInfoLocation location =
        provider
            .getSerializer(ExecutionInfoLocation.class)
            .load(OpsAnalyticsRunConfigurationSupport.EXECUTION_INFO_LOCATION);

    assertTrue(pipeline.getEngineRunConfiguration() instanceof LocalPipelineRunConfiguration);
    assertNull(pipeline.getExecutionDataProfileName());
    assertFalse(pipeline.isDefaultSelection());
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.EXECUTION_INFO_LOCATION,
        pipeline.getExecutionInfoLocationName());
    assertTrue(workflow.getEngineRunConfiguration() instanceof LocalWorkflowRunConfiguration);
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.EXECUTION_INFO_LOCATION,
        workflow.getExecutionInfoLocationName());
    assertTrue(location.getExecutionInfoLocation() instanceof CachingFileExecutionInfoLocation);
    assertEquals(
        OpsAnalyticsRunConfigurationSupport.EXECUTION_INFO_FOLDER,
        ((CachingFileExecutionInfoLocation) location.getExecutionInfoLocation()).getRootFolder());
  }

  @Test
  void existingRunConfigurationIsLeftAlone() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    PipelineRunConfiguration existing = new PipelineRunConfiguration();
    existing.setName(OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION);
    existing.setExecutionDataProfileName("first-last");
    existing.setExecutionInfoLocationName("db-executions");
    existing.setEngineRunConfiguration(new LocalPipelineRunConfiguration());
    provider.getSerializer(PipelineRunConfiguration.class).save(existing);

    OpsAnalyticsRunConfigurationSupport.ensure(OpsAnalyticsRequest.defaults(), provider);

    PipelineRunConfiguration loaded =
        provider
            .getSerializer(PipelineRunConfiguration.class)
            .load(OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION);
    assertEquals("first-last", loaded.getExecutionDataProfileName());
    assertEquals("db-executions", loaded.getExecutionInfoLocationName());
  }
}
