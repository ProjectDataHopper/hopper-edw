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

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.fileinput.FileTypeFilter;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metrics.DvUpdateMetricsCollector;
import org.apache.hop.datavault.metrics.DvUpdateMetricsConstants;
import org.apache.hop.datavault.metrics.LoadRunPublishSummary;
import org.apache.hop.datavault.metrics.VaultUpdateExecutionSupport;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveMonitor;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveRunContext;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.getfilenames.FileItem;
import org.apache.hop.pipeline.transforms.getfilenames.FilterItem;
import org.apache.hop.pipeline.transforms.getfilenames.GetFileNamesMeta;
import org.apache.hop.pipeline.transforms.pipelineexecutor.PipelineExecutorMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * Stages generated DV update pipelines and runs them through a Get File Names → Pipeline Executor
 * orchestrator for round-robin parallel execution.
 */
public final class DvPipelineOrchestratorSupport {

  public static final String DEFAULT_STAGING_PREFIX = "${java.io.tmpdir}/dv2/";
  public static final String HPL_FILE_MASK = ".*\\.hpl$";

  private DvPipelineOrchestratorSupport() {}

  /** Resolves the staging folder, defaulting to {@code ${java.io.tmpdir}/dv2/<model-name>/}. */
  public static String resolveStagingFolder(
      String configuredFolder, IVariables variables, String modelName) {
    String folder = configuredFolder;
    if (variables != null) {
      folder = variables.resolve(folder);
    }
    if (Utils.isEmpty(folder)) {
      folder = DEFAULT_STAGING_PREFIX + sanitizeModelName(modelName) + "/";
    }
    return ensureTrailingSlash(folder);
  }

  /** Resolves parallel pipeline executor copies (minimum 1). */
  public static int resolveParallelCopies(String copiesSetting, IVariables variables)
      throws HopException {
    return DvIntegerSettingValidationSupport.requirePositiveInteger(
        copiesSetting,
        variables,
        "1",
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.ParallelPipelineCopies"));
  }

  /** Creates the staging folder and removes any previously staged {@code .hpl} files. */
  public static void prepareStagingFolder(String folder, IVariables variables) throws HopException {
    try {
      FileObject stagingFolder = HopVfs.getFileObject(folder, variables);
      if (stagingFolder.exists()) {
        FileObject[] children = stagingFolder.getChildren();
        if (children != null) {
          for (FileObject child : children) {
            if (child != null
                && child.getType().hasContent()
                && child.getName().getBaseName().endsWith(PipelineMeta.PIPELINE_EXTENSION)) {
              child.delete();
            }
          }
        }
      } else {
        stagingFolder.createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to prepare pipeline staging folder " + folder, e);
    }
  }

  /** Writes each generated pipeline to the staging folder. */
  public static void stagePipelines(
      String folder, IVariables variables, List<PipelineMeta> pipelines) throws HopException {
    stagePipelines(folder, variables, pipelines, false);
  }

  /**
   * Writes each generated pipeline to the staging folder.
   *
   * @param sequenceFilenames when {@code true}, prefixes each filename with a zero-padded sequence
   *     ({@code 0001-}, {@code 0002-}, …) so Get File Names picks up pipelines in list order
   */
  public static void stagePipelines(
      String folder, IVariables variables, List<PipelineMeta> pipelines, boolean sequenceFilenames)
      throws HopException {
    if (pipelines == null || pipelines.isEmpty()) {
      return;
    }
    int sequence = 1;
    for (PipelineMeta pipelineMeta : pipelines) {
      if (pipelineMeta == null) {
        continue;
      }
      String pipelineFilename =
          appendPath(
              folder,
              buildStagedPipelineFilename(pipelineMeta.getName(), sequence++, sequenceFilenames));
      pipelineMeta.setFilename(pipelineFilename);
      writePipeline(variables, pipelineMeta, pipelineFilename);
    }
  }

  /**
   * Builds a pipeline that lists staged {@code .hpl} files and executes them via Pipeline Executor.
   */
  public static PipelineMeta buildOrchestratorPipeline(
      String stagingFolder, String runConfiguration, int parallelCopies, String modelName) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("DV Update Orchestrator - " + modelName);

    GetFileNamesMeta getFileNamesMeta = new GetFileNamesMeta();
    getFileNamesMeta.setDefault();
    getFileNamesMeta.getFilterItemList().clear();
    getFileNamesMeta.getFilterItemList().add(new FilterItem(FileTypeFilter.ONLY_FILES.toString()));

    List<FileItem> filesList = new ArrayList<>();
    filesList.add(new FileItem(stagingFolder, HPL_FILE_MASK, "", "N", "N"));
    getFileNamesMeta.setFilesList(filesList);

    TransformMeta getFileNamesTransform = new TransformMeta("Get pipeline files", getFileNamesMeta);
    getFileNamesTransform.setLocation(100, 100);
    pipelineMeta.addTransform(getFileNamesTransform);

    PipelineExecutorMeta executorMeta = new PipelineExecutorMeta();
    executorMeta.setDefault();
    executorMeta.setFilenameInField(true);
    executorMeta.setFilenameField("filename");
    executorMeta.setRunConfigurationName(runConfiguration);
    executorMeta.setInheritingAllVariables(true);

    TransformMeta executorTransform = new TransformMeta("Execute update pipelines", executorMeta);
    executorTransform.setLocation(300, 100);
    executorTransform.setCopiesString(String.valueOf(Math.max(1, parallelCopies)));
    pipelineMeta.addTransform(executorTransform);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(getFileNamesTransform, executorTransform));

    return pipelineMeta;
  }

  /** Runs the orchestrator pipeline and returns its result. */
  public static Result runOrchestrator(
      PipelineMeta orchestrator,
      String runConfiguration,
      LogLevel logLevel,
      String metricsOutputFolder,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return runOrchestrator(
        orchestrator,
        runConfiguration,
        logLevel,
        metricsOutputFolder,
        null,
        parent,
        parentWorkflow,
        variables,
        metadataProvider);
  }

  public static Result runOrchestrator(
      PipelineMeta orchestrator,
      String runConfiguration,
      LogLevel logLevel,
      String metricsOutputFolder,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return runOrchestrator(
        orchestrator,
        runConfiguration,
        logLevel,
        metricsOutputFolder,
        metricsPublishContext,
        null,
        null,
        parent,
        parentWorkflow,
        variables,
        metadataProvider);
  }

  public static Result runOrchestrator(
      PipelineMeta orchestrator,
      String runConfiguration,
      LogLevel logLevel,
      String metricsOutputFolder,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      String stagingFolder,
      String modelFilename,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String modelName = resolveOrchestratorModelName(orchestrator);
    String metricsRunId = initializeMetricsRun(variables, modelName, metricsPublishContext);

    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(
            variables, runConfiguration, metadataProvider, orchestrator);

    if (logLevel != null) {
      engine.setLogLevel(logLevel);
    }
    engine.setParent(parent);
    engine.setParentWorkflow(parentWorkflow);

    UpdateRunLiveRunContext liveContext =
        UpdateRunLiveRunContext.from(
            metricsRunId,
            modelName,
            modelFilename,
            stagingFolder,
            parent,
            parentWorkflow,
            logLevel != null ? logLevel : parent != null ? parent.getLogLevel() : null);
    UpdateRunLiveMonitor monitor = UpdateRunLiveMonitor.start(liveContext, engine);
    try {
      engine.execute();
      engine.waitUntilFinished();
    } finally {
      if (monitor != null) {
        monitor.close();
      }
    }

    Result orchestratorResult = engine.getResult();
    if (orchestratorResult == null) {
      orchestratorResult = new Result();
    }
    finalizeMetricsRun(
        engine.getLogChannel(),
        metricsRunId,
        modelName,
        logLevel,
        metricsOutputFolder,
        engine.getLogChannelId(),
        variables,
        metadataProvider,
        metricsPublishContext,
        orchestratorResult);
    applyChildPipelineFailures(engine, orchestratorResult);
    return orchestratorResult;
  }

  /**
   * Assigns run-scoped variables used by {@link
   * org.apache.hop.datavault.metrics.DvUpdatePipelineCompletedExtensionPoint}.
   */
  public static String initializeMetricsRun(
      IVariables variables,
      String modelName,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext) {
    String metricsRunId = UUID.randomUUID().toString();
    DvUpdateMetricsCollector.markRunStarted(metricsRunId);
    if (variables != null) {
      variables.setVariable(DvUpdateMetricsConstants.VAR_RUN_ID, metricsRunId);
      variables.setVariable(DvUpdateMetricsConstants.VAR_MODEL_NAME, modelName);
      if (metricsPublishContext != null) {
        setVariableIfPresent(
            variables, DvUpdateMetricsConstants.VAR_MODEL_TYPE, metricsPublishContext.modelType());
        setVariableIfPresent(
            variables,
            DvUpdateMetricsConstants.VAR_WORKFLOW_NAME,
            metricsPublishContext.workflowName());
        setVariableIfPresent(
            variables,
            DvUpdateMetricsConstants.VAR_METRICS_DATABASE,
            metricsPublishContext.targetDatabaseName());
        setVariableIfPresent(
            variables,
            DvUpdateMetricsConstants.VAR_METRICS_CATALOG_CONNECTION,
            metricsPublishContext.catalogConnectionName());
      }
      setVariableIfPresent(
          variables,
          DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID,
          VaultUpdateExecutionSupport.resolveExecutionId(
              variables, VaultUpdateExecutionSupport.defaultExecutionIdVariableName()));
    }
    return metricsRunId;
  }

  /** Publishes collected metrics and applies aggregated totals to a Hop result. */
  public static LoadRunPublishSummary finalizeMetricsRun(
      org.apache.hop.core.logging.ILogChannel log,
      String metricsRunId,
      String modelName,
      LogLevel logLevel,
      String metricsOutputFolder,
      String logChannelId,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext,
      Result result)
      throws HopException {
    LoadRunPublishSummary metricsSummary =
        DvUpdateMetricsCollector.publishRunSummary(
            log,
            metricsRunId,
            modelName,
            logLevel,
            metricsOutputFolder,
            logChannelId,
            variables,
            metadataProvider,
            metricsPublishContext);
    if (result != null) {
      DvUpdateMetricsCollector.applyToResult(result, metricsSummary);
    }
    return metricsSummary;
  }

  /**
   * Pipeline Executor does not increment parent transform errors when a child pipeline fails.
   * Metrics collection covers staged update pipelines; this supplements any remaining active
   * sub-pipeline errors (for example when metrics were not recorded).
   */
  static void applyChildPipelineFailures(
      IPipelineEngine<PipelineMeta> engine, Result orchestratorResult) {
    if (engine == null || orchestratorResult == null || orchestratorResult.getNrErrors() > 0) {
      if (orchestratorResult != null && orchestratorResult.getNrErrors() > 0) {
        orchestratorResult.setResult(false);
      }
      return;
    }

    long childErrors = sumActiveSubPipelineErrors(engine);
    if (childErrors > 0) {
      orchestratorResult.setNrErrors(orchestratorResult.getNrErrors() + childErrors);
      orchestratorResult.setResult(false);
    }
  }

  private static long sumActiveSubPipelineErrors(IPipelineEngine<PipelineMeta> engine) {
    if (!(engine instanceof Pipeline pipeline)) {
      return 0;
    }
    Map<String, IPipelineEngine> activeSubPipelines = pipeline.getActiveSubPipelines();
    if (activeSubPipelines == null || activeSubPipelines.isEmpty()) {
      return 0;
    }
    long total = 0;
    for (IPipelineEngine subPipeline : activeSubPipelines.values()) {
      if (subPipeline == null) {
        continue;
      }
      total += Math.max(subPipeline.getErrors(), 0);
      Result subResult = subPipeline.getResult();
      if (subResult != null) {
        total += Math.max(subResult.getNrErrors(), 0);
      }
    }
    return total;
  }

  private static void setVariableIfPresent(IVariables variables, String name, String value) {
    if (variables != null && !Utils.isEmpty(value)) {
      variables.setVariable(name, value);
    }
  }

  private static String resolveOrchestratorModelName(PipelineMeta orchestrator) {
    if (orchestrator == null || Utils.isEmpty(orchestrator.getName())) {
      return "";
    }
    String name = orchestrator.getName();
    if (name.startsWith(DvUpdateMetricsConstants.ORCHESTRATOR_NAME_PREFIX)) {
      return name.substring(DvUpdateMetricsConstants.ORCHESTRATOR_NAME_PREFIX.length());
    }
    return name;
  }

  /** Deletes the staging folder and all files and sub-folders below it. */
  public static void cleanupStagingFolder(String folder, IVariables variables) throws HopException {
    if (Utils.isEmpty(folder)) {
      return;
    }
    try {
      FileObject stagingFolder = HopVfs.getFileObject(folder, variables);
      if (stagingFolder.exists()) {
        stagingFolder.deleteAll();
        if (stagingFolder.exists()) {
          stagingFolder.delete();
        }
      }
    } catch (Exception e) {
      throw new HopException("Unable to clean up pipeline staging folder " + folder, e);
    }
  }

  /** Merges pipeline counters from {@code source} into {@code target}. */
  public static void mergeResult(Result target, Result source) {
    if (target == null || source == null) {
      return;
    }
    target.setNrLinesRead(target.getNrLinesRead() + source.getNrLinesRead());
    target.setNrLinesWritten(target.getNrLinesWritten() + source.getNrLinesWritten());
    target.setNrLinesInput(target.getNrLinesInput() + source.getNrLinesInput());
    target.setNrLinesOutput(target.getNrLinesOutput() + source.getNrLinesOutput());
    target.setNrLinesUpdated(target.getNrLinesUpdated() + source.getNrLinesUpdated());
    target.setNrLinesDeleted(target.getNrLinesDeleted() + source.getNrLinesDeleted());
    target.setNrErrors(target.getNrErrors() + source.getNrErrors());
    if (source.getNrErrors() > 0 || !source.getResult()) {
      target.setResult(false);
    }
  }

  /**
   * Stages pipelines using stable basenames ({@code name.hpl}) without sequence prefixes. Used for
   * multi-source nested pipelines referenced by serial update workflows.
   */
  public static void stageNamedPipelines(
      String folder, IVariables variables, List<PipelineMeta> pipelines) throws HopException {
    stagePipelines(folder, variables, pipelines, false);
  }

  /** Writes a workflow meta to the staging folder as {@code name.hwf}. */
  public static String stageWorkflow(String folder, IVariables variables, WorkflowMeta workflowMeta)
      throws HopException {
    if (workflowMeta == null || Utils.isEmpty(workflowMeta.getName())) {
      return null;
    }
    String workflowFilename =
        appendPath(folder, workflowMeta.getName() + WorkflowMeta.WORKFLOW_EXTENSION);
    workflowMeta.setFilename(workflowFilename);
    try {
      FileObject file = HopVfs.getFileObject(workflowFilename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      String xml = workflowMeta.getXml(variables);
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(xml);
      }
      return workflowFilename;
    } catch (Exception e) {
      throw new HopException(
          "Unable to stage generated workflow '"
              + workflowMeta.getName()
              + "' to "
              + workflowFilename,
          e);
    }
  }

  private static void writePipeline(
      IVariables variables, PipelineMeta pipelineMeta, String pipelineFilename)
      throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(pipelineFilename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      String xml = pipelineMeta.getXml(variables);
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(xml);
      }
    } catch (Exception e) {
      throw new HopException(
          "Unable to stage generated pipeline '"
              + pipelineMeta.getName()
              + "' to "
              + pipelineFilename,
          e);
    }
  }

  /** Builds the staged pipeline base filename (including {@code .hpl} extension). */
  static String buildStagedPipelineFilename(
      String pipelineName, int sequence, boolean sequenceFilenames) {
    String stagedBaseName = pipelineName;
    if (sequenceFilenames) {
      stagedBaseName = String.format("%04d-%s", sequence, pipelineName);
    }
    return stagedBaseName + PipelineMeta.PIPELINE_EXTENSION;
  }

  private static String sanitizeModelName(String modelName) {
    if (Utils.isEmpty(modelName)) {
      return "model";
    }
    return modelName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String ensureTrailingSlash(String folder) {
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder;
    }
    return folder + "/";
  }

  private static String appendPath(String folder, String filename) {
    return ensureTrailingSlash(folder) + filename;
  }
}
