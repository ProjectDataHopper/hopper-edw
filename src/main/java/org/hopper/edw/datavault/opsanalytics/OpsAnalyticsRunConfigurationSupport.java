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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.execution.ExecutionInfoLocation;
import org.apache.hop.execution.caching.CachingFileExecutionInfoLocation;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.apache.hop.workflow.engines.local.LocalWorkflowRunConfiguration;

/**
 * Run configurations used to update operational models. They do not sample rows, and execution
 * information goes to a file location rather than the OPS executions table.
 */
public final class OpsAnalyticsRunConfigurationSupport {

  public static final String PIPELINE_RUN_CONFIGURATION = "ops-analytics-pipeline";
  public static final String WORKFLOW_RUN_CONFIGURATION = "ops-analytics-workflow";
  public static final String EXECUTION_INFO_LOCATION = "ops-analytics-executions";
  public static final String EXECUTION_INFO_FOLDER =
      "${PROJECT_HOME}/work/ops-analytics-executions";

  private OpsAnalyticsRunConfigurationSupport() {}

  /**
   * Fills blank names with the defaults and creates any of those metadata objects that are not
   * already in the project. An existing run configuration is left unchanged.
   */
  public static void ensure(OpsAnalyticsRequest request, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null) {
      throw new HopException("A metadata provider is required");
    }
    String pipelineName =
        name(
            request == null ? null : request.getPipelineRunConfiguration(),
            PIPELINE_RUN_CONFIGURATION);
    String workflowName =
        name(
            request == null ? null : request.getWorkflowRunConfiguration(),
            WORKFLOW_RUN_CONFIGURATION);
    if (request != null) {
      request.setPipelineRunConfiguration(pipelineName);
      request.setWorkflowRunConfiguration(workflowName);
    }

    boolean pipelineMissing =
        metadataProvider.getSerializer(PipelineRunConfiguration.class).load(pipelineName) == null;
    boolean workflowMissing =
        metadataProvider.getSerializer(WorkflowRunConfiguration.class).load(workflowName) == null;
    if (pipelineMissing || workflowMissing) {
      ensureLocation(metadataProvider);
    }
    if (pipelineMissing) {
      metadataProvider
          .getSerializer(PipelineRunConfiguration.class)
          .save(pipelineConfiguration(pipelineName));
    }
    if (workflowMissing) {
      metadataProvider
          .getSerializer(WorkflowRunConfiguration.class)
          .save(workflowConfiguration(workflowName));
    }
  }

  private static void ensureLocation(IHopMetadataProvider metadataProvider) throws HopException {
    if (metadataProvider.getSerializer(ExecutionInfoLocation.class).load(EXECUTION_INFO_LOCATION)
        != null) {
      return;
    }
    CachingFileExecutionInfoLocation files = new CachingFileExecutionInfoLocation();
    files.setRootFolder(EXECUTION_INFO_FOLDER);
    files.setCreateParentFolder(true);
    ExecutionInfoLocation location = new ExecutionInfoLocation();
    location.setName(EXECUTION_INFO_LOCATION);
    location.setDescription(
        "File execution information for operational analytics loads. Not the OPS executions table.");
    location.setExecutionInfoLocation(files);
    metadataProvider.getSerializer(ExecutionInfoLocation.class).save(location);
  }

  private static PipelineRunConfiguration pipelineConfiguration(String name) {
    PipelineRunConfiguration configuration = new PipelineRunConfiguration();
    configuration.setName(name);
    configuration.setDescription(
        "Local pipeline engine for operational analytics updates. No data profile, so row samples are not stored.");
    configuration.setEngineRunConfiguration(new LocalPipelineRunConfiguration());
    configuration.setExecutionInfoLocationName(EXECUTION_INFO_LOCATION);
    configuration.setExecutionDataProfileName(null);
    configuration.setDefaultSelection(false);
    return configuration;
  }

  private static WorkflowRunConfiguration workflowConfiguration(String name) {
    WorkflowRunConfiguration configuration = new WorkflowRunConfiguration();
    configuration.setName(name);
    configuration.setDescription(
        "Local workflow engine for operational analytics updates. Execution information is not written to the OPS executions table.");
    configuration.setEngineRunConfiguration(new LocalWorkflowRunConfiguration());
    configuration.setExecutionInfoLocationName(EXECUTION_INFO_LOCATION);
    configuration.setDefaultSelection(false);
    return configuration;
  }

  private static String name(String requested, String fallback) {
    return Utils.isEmpty(requested) ? fallback : requested.trim();
  }
}
