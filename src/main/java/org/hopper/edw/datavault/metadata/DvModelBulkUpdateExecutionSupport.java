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
package org.hopper.edw.datavault.metadata;

import java.util.List;
import org.apache.hop.core.Result;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.hopper.edw.datavault.metrics.DvUpdateMetricsCollector;
import org.hopper.edw.datavault.metrics.LoadRunMetricsPipelineSupport;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRunContext;

/** Shared pipeline orchestrator and staging-file workflow execution for model update actions. */
public final class DvModelBulkUpdateExecutionSupport {

  private static final Class<?> PKG = DvModelBulkUpdateExecutionSupport.class;

  private DvModelBulkUpdateExecutionSupport() {}

  public record ExecutionOutcome(boolean success, int totalErrors) {}

  public static ExecutionOutcome executeOrchestratorUpdate(
      Result result,
      String modelName,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      LogLevel pipelineLogLevel,
      String pipelineStagingFolder,
      String parallelPipelineCopies,
      String metricsOutputFolder,
      boolean success,
      int totalErrors,
      IVariables variables,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return executeOrchestratorUpdate(
        result,
        modelName,
        allPipelineMetas,
        realRunConfig,
        pipelineLogLevel,
        pipelineStagingFolder,
        parallelPipelineCopies,
        metricsOutputFolder,
        null,
        success,
        totalErrors,
        variables,
        parent,
        parentWorkflow,
        metadataProvider);
  }

  public static ExecutionOutcome executeOrchestratorUpdate(
      Result result,
      String modelName,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      LogLevel pipelineLogLevel,
      String pipelineStagingFolder,
      String parallelPipelineCopies,
      String metricsOutputFolder,
      org.hopper.edw.datavault.metrics.DvUpdateMetricsCollector.LoadRunPublishContext
          metricsPublishContext,
      boolean success,
      int totalErrors,
      IVariables variables,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    LogChannel log = new LogChannel(parent);
    metricsPublishContext = enrichPublishContext(metricsPublishContext, realRunConfig);
    String stagingFolder =
        variables.resolve(
            DvPipelineOrchestratorSupport.resolveStagingFolder(
                pipelineStagingFolder, variables, modelName));
    int parallelCopies =
        DvPipelineOrchestratorSupport.resolveParallelCopies(parallelPipelineCopies, variables);

    log.logBasic(
        BaseMessages.getString(
            PKG,
            "DvModelBulkUpdateExecutionSupport.Log.StagingPipelines",
            stagingFolder,
            allPipelineMetas.size()));
    log.logBasic(
        BaseMessages.getString(
            PKG, "DvModelBulkUpdateExecutionSupport.Log.ParallelCopies", parallelCopies));

    try {
      DvPipelineOrchestratorSupport.prepareStagingFolder(stagingFolder, variables);
      if (LoadRunMetricsPipelineSupport.isMetricsCollectionEnabled(
          metricsOutputFolder, metricsPublishContext)) {
        LoadRunMetricsPipelineSupport.enableTransformPerformanceCapture(allPipelineMetas);
      }
      DvPipelineOrchestratorSupport.stagePipelines(
          stagingFolder, variables, allPipelineMetas, true);

      PipelineMeta orchestrator =
          DvPipelineOrchestratorSupport.buildOrchestratorPipeline(
              stagingFolder, realRunConfig, parallelCopies, modelName);

      log.logBasic(
          BaseMessages.getString(
              PKG,
              "DvModelBulkUpdateExecutionSupport.Log.RunningOrchestrator",
              orchestrator.getName(),
              realRunConfig));

      Result orchestratorResult =
          DvPipelineOrchestratorSupport.runOrchestrator(
              orchestrator,
              realRunConfig,
              pipelineLogLevel != null ? pipelineLogLevel : parent.getLogLevel(),
              metricsOutputFolder,
              metricsPublishContext,
              stagingFolder,
              null,
              parent,
              parentWorkflow,
              variables,
              metadataProvider);

      DvPipelineOrchestratorSupport.mergeResult(result, orchestratorResult);

      if (orchestratorResult.getNrErrors() > 0 || !orchestratorResult.getResult()) {
        log.logError(
            BaseMessages.getString(
                PKG, "DvModelBulkUpdateExecutionSupport.Error.OrchestratorFailed"));
        success = false;
        totalErrors += orchestratorResult.getNrErrors();
      }
    } finally {
      try {
        DvPipelineOrchestratorSupport.cleanupStagingFolder(stagingFolder, variables);
        log.logBasic(
            BaseMessages.getString(
                PKG, "DvModelBulkUpdateExecutionSupport.Log.StagingCleanup", stagingFolder));
      } catch (HopException e) {
        log.logError(
            BaseMessages.getString(
                PKG, "DvModelBulkUpdateExecutionSupport.Error.StagingCleanupFailed", stagingFolder),
            e);
      }
    }
    return new ExecutionOutcome(success, totalErrors);
  }

  public static ExecutionOutcome executeStagingFileUpdate(
      Result result,
      String modelName,
      IDvTargetLoadConfiguration pipelineConfig,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      String pipelineStagingFolder,
      DatabaseMeta targetDatabase,
      String targetDbName,
      boolean success,
      int totalErrors,
      IVariables variables,
      ILoggingObject parent,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return executeStagingFileUpdate(
        result,
        modelName,
        pipelineConfig,
        allPipelineMetas,
        realRunConfig,
        realWorkflowRunConfig,
        pipelineLogLevel,
        pipelineStagingFolder,
        targetDatabase,
        targetDbName,
        null,
        null,
        null,
        null,
        success,
        totalErrors,
        variables,
        parent,
        metadataProvider);
  }

  public static ExecutionOutcome executeStagingFileUpdate(
      Result result,
      String modelName,
      IDvTargetLoadConfiguration pipelineConfig,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      String pipelineStagingFolder,
      DatabaseMeta targetDatabase,
      String targetDbName,
      String metricsOutputFolder,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      boolean success,
      int totalErrors,
      IVariables variables,
      ILoggingObject parent,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return executeStagingFileUpdate(
        result,
        modelName,
        pipelineConfig,
        allPipelineMetas,
        realRunConfig,
        realWorkflowRunConfig,
        pipelineLogLevel,
        pipelineStagingFolder,
        targetDatabase,
        targetDbName,
        metricsOutputFolder,
        metricsPublishContext,
        null,
        null,
        success,
        totalErrors,
        variables,
        parent,
        metadataProvider);
  }

  public static ExecutionOutcome executeStagingFileUpdate(
      Result result,
      String modelName,
      IDvTargetLoadConfiguration pipelineConfig,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      String pipelineStagingFolder,
      DatabaseMeta targetDatabase,
      String targetDbName,
      String metricsOutputFolder,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      String modelFilename,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      boolean success,
      int totalErrors,
      IVariables variables,
      ILoggingObject parent,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    LogChannel log = new LogChannel(parent);
    metricsPublishContext = enrichPublishContext(metricsPublishContext, realRunConfig);
    if (targetDatabase == null || Utils.isEmpty(targetDbName)) {
      log.logError(
          BaseMessages.getString(PKG, "DvModelBulkUpdateExecutionSupport.Error.NoTargetDatabase"));
      return new ExecutionOutcome(false, totalErrors + 1);
    }

    String pipelineStagingFolderResolved =
        variables.resolve(
            DvPipelineOrchestratorSupport.resolveStagingFolder(
                pipelineStagingFolder, variables, modelName));
    String bulkStagingFolderResolved =
        variables.resolve(pipelineConfig.resolveBulkLoadStagingFolder(variables, modelName));

    log.logBasic(
        BaseMessages.getString(
            PKG,
            "DvModelBulkUpdateExecutionSupport.Log.StagingPipelines",
            pipelineStagingFolderResolved,
            allPipelineMetas.size()));
    log.logBasic(
        BaseMessages.getString(
            PKG,
            "DvModelBulkUpdateExecutionSupport.Log.StagingBulkDataFolder",
            bulkStagingFolderResolved));

    boolean metricsCollectionEnabled =
        LoadRunMetricsPipelineSupport.isMetricsCollectionEnabled(
            metricsOutputFolder, metricsPublishContext);
    String metricsRunId =
        DvPipelineOrchestratorSupport.initializeMetricsRun(
            variables, modelName, metricsPublishContext);

    try {
      DvPipelineOrchestratorSupport.prepareStagingFolder(pipelineStagingFolderResolved, variables);
      if (metricsCollectionEnabled) {
        LoadRunMetricsPipelineSupport.enableTransformPerformanceCapture(allPipelineMetas);
      }
      DvPipelineOrchestratorSupport.stagePipelines(
          pipelineStagingFolderResolved, variables, allPipelineMetas, true);
      DvUpdateWorkflowSupport.prepareBulkStagingFolder(bulkStagingFolderResolved, variables);

      List<DvUpdateWorkflowSupport.DvStagingLoadDescriptor> descriptors =
          DvUpdateWorkflowSupport.buildStagingDescriptors(
              pipelineConfig, variables, modelName, targetDatabase, targetDbName, allPipelineMetas);

      WorkflowMeta masterWorkflow =
          DvUpdateWorkflowSupport.buildMasterWorkflow(
              descriptors, pipelineConfig, variables, realRunConfig, modelName);

      String savedWorkflowFile =
          DvGeneratedPipelineSupport.saveWorkflowBeforeExecution(
              pipelineConfig, variables, masterWorkflow);
      if (!Utils.isEmpty(savedWorkflowFile)) {
        log.logBasic(
            BaseMessages.getString(
                PKG,
                "DvModelBulkUpdateExecutionSupport.Log.SavedGeneratedWorkflow",
                masterWorkflow.getName(),
                savedWorkflowFile));
      }

      log.logBasic(
          BaseMessages.getString(
              PKG,
              "DvModelBulkUpdateExecutionSupport.Log.RunningBulkWorkflow",
              masterWorkflow.getName(),
              descriptors.size()));

      UpdateRunLiveRunContext liveContext =
          UpdateRunLiveRunContext.from(
              metricsRunId,
              modelName,
              modelFilename,
              pipelineStagingFolderResolved + " | " + bulkStagingFolderResolved,
              parent,
              parentWorkflow,
              pipelineLogLevel != null ? pipelineLogLevel : parent.getLogLevel());

      DvUpdateWorkflowSupport.MasterWorkflowRunOutcome workflowOutcome =
          DvUpdateWorkflowSupport.runMasterWorkflowWithLogChannel(
              masterWorkflow,
              realWorkflowRunConfig,
              pipelineLogLevel != null ? pipelineLogLevel : parent.getLogLevel(),
              parent,
              liveContext,
              variables,
              metadataProvider);
      Result workflowResult = workflowOutcome.result();

      DvPipelineOrchestratorSupport.mergeResult(result, workflowResult);

      DvPipelineOrchestratorSupport.finalizeMetricsRun(
          log,
          metricsRunId,
          modelName,
          pipelineLogLevel != null ? pipelineLogLevel : parent.getLogLevel(),
          metricsOutputFolder,
          workflowOutcome.workflowLogChannelId(),
          variables,
          metadataProvider,
          metricsPublishContext,
          workflowResult);

      if (workflowResult.getNrErrors() > 0 || !workflowResult.getResult()) {
        log.logError(
            BaseMessages.getString(
                PKG, "DvModelBulkUpdateExecutionSupport.Error.BulkWorkflowFailed"));
        success = false;
        totalErrors += workflowResult.getNrErrors();
      }
    } finally {
      try {
        DvPipelineOrchestratorSupport.cleanupStagingFolder(
            pipelineStagingFolderResolved, variables);
        log.logBasic(
            BaseMessages.getString(
                PKG,
                "DvModelBulkUpdateExecutionSupport.Log.StagingCleanup",
                pipelineStagingFolderResolved));
      } catch (HopException e) {
        log.logError(
            BaseMessages.getString(
                PKG,
                "DvModelBulkUpdateExecutionSupport.Error.StagingCleanupFailed",
                pipelineStagingFolderResolved),
            e);
      }
      try {
        DvUpdateWorkflowSupport.cleanupBulkStagingFolder(bulkStagingFolderResolved, variables);
        log.logBasic(
            BaseMessages.getString(
                PKG,
                "DvModelBulkUpdateExecutionSupport.Log.BulkStagingCleanup",
                bulkStagingFolderResolved));
      } catch (HopException e) {
        log.logError(
            BaseMessages.getString(
                PKG,
                "DvModelBulkUpdateExecutionSupport.Error.BulkStagingCleanupFailed",
                bulkStagingFolderResolved),
            e);
      }
    }
    return new ExecutionOutcome(success, totalErrors);
  }

  private static DvUpdateMetricsCollector.LoadRunPublishContext enrichPublishContext(
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      String pipelineRunConfiguration) {
    if (metricsPublishContext == null || Utils.isEmpty(pipelineRunConfiguration)) {
      return metricsPublishContext;
    }
    return metricsPublishContext.withPipelineRunConfiguration(pipelineRunConfiguration);
  }
}
