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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Result;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileField;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.hop.workflow.engine.WorkflowEngineFactory;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRunContext;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveStagingWorkflowMonitor;

/**
 * Builds and runs master workflows that execute staged DV update pipelines and bulk-load the
 * resulting CSV shard files.
 */
public final class DvUpdateWorkflowSupport {

  public static final String PIPELINE_ACTION_ID = "PIPELINE";

  private DvUpdateWorkflowSupport() {}

  /** Describes one staged pipeline and the bulk-load actions that follow it. */
  public record DvStagingLoadDescriptor(
      String pipelineName,
      String stagedPipelinePath,
      String targetTableName,
      String stagingFolder,
      String stagingFileBase,
      int parallelCopies,
      List<String> columnNames,
      DatabaseMeta targetDatabase,
      String targetDbName) {}

  /** Creates the bulk-load data folder and removes any previously staged CSV files. */
  public static void prepareBulkStagingFolder(String folder, IVariables variables)
      throws HopException {
    try {
      FileObject stagingFolder = HopVfs.getFileObject(folder, variables);
      if (stagingFolder.exists()) {
        FileObject[] children = stagingFolder.getChildren();
        if (children != null) {
          for (FileObject child : children) {
            if (child != null && child.getType().hasContent()) {
              String baseName = child.getName().getBaseName().toLowerCase();
              if (baseName.endsWith(".csv") || baseName.endsWith(PipelineMeta.PIPELINE_EXTENSION)) {
                child.delete();
              }
            }
          }
        }
      } else {
        stagingFolder.createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to prepare bulk-load staging folder " + folder, e);
    }
  }

  /** Deletes all files and sub-folders below the bulk-load staging folder. */
  public static void cleanupBulkStagingFolder(String folder, IVariables variables)
      throws HopException {
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
      throw new HopException("Unable to clean up bulk-load staging folder " + folder, e);
    }
  }

  /** Inspects staged pipelines and builds bulk-load descriptors for workflow generation. */
  public static List<DvStagingLoadDescriptor> buildStagingDescriptors(
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String modelName,
      DatabaseMeta targetDatabase,
      String targetDbName,
      List<PipelineMeta> stagedPipelines)
      throws HopException {
    if (stagedPipelines == null || stagedPipelines.isEmpty()) {
      return List.of();
    }
    String stagingFolder = config.resolveBulkLoadStagingFolder(variables, modelName);
    List<DvStagingLoadDescriptor> descriptors = new ArrayList<>();
    for (PipelineMeta pipelineMeta : stagedPipelines) {
      if (pipelineMeta == null) {
        continue;
      }
      DvStagingLoadDescriptor descriptor =
          inspectStagedPipeline(
              pipelineMeta, stagingFolder, targetDatabase, targetDbName, variables);
      if (descriptor != null) {
        descriptors.add(descriptor);
      }
    }
    return List.copyOf(descriptors);
  }

  /**
   * Builds a sequential master workflow: for each descriptor, run the staged pipeline then
   * bulk-load every parallel shard file.
   */
  public static WorkflowMeta buildMasterWorkflow(
      List<DvStagingLoadDescriptor> descriptors,
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String pipelineRunConfiguration,
      String modelName)
      throws HopException {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(config.resolveGeneratedWorkflowName(variables, modelName));

    ActionStart startAction = new ActionStart("Start");
    ActionMeta previousAction = new ActionMeta(startAction);
    previousAction.setLocation(50, 50);
    workflowMeta.addAction(previousAction);

    int x = 250;
    int y = 50;
    for (DvStagingLoadDescriptor descriptor : descriptors) {
      ActionMeta pipelineActionMeta =
          newActionMeta(
              PIPELINE_ACTION_ID,
              "run_" + sanitizeActionName(descriptor.pipelineName()),
              action -> configurePipelineAction(action, descriptor, pipelineRunConfiguration));
      pipelineActionMeta.setLocation(x, y);
      workflowMeta.addAction(pipelineActionMeta);
      workflowMeta.addWorkflowHop(new WorkflowHopMeta(previousAction, pipelineActionMeta));
      previousAction = pipelineActionMeta;
      x += 200;

      for (int copyIndex = 0; copyIndex < descriptor.parallelCopies(); copyIndex++) {
        String stagedFilePath =
            DvTargetLoadSupport.resolveStagedCsvFilePath(descriptor.stagingFileBase(), copyIndex);
        ActionMeta bulkActionMeta;
        if (DvStagingBulkLoadPipelineSupport.usesClientSideBulkLoad(descriptor.targetDatabase())) {
          String bulkPipelinePath =
              DvStagingBulkLoadPipelineSupport.buildAndStagePostgresBulkLoadPipeline(
                  descriptor.stagingFolder(),
                  variables,
                  config,
                  descriptor.targetDbName(),
                  descriptor.targetTableName(),
                  descriptor.columnNames(),
                  stagedFilePath,
                  copyIndex);
          bulkActionMeta =
              newActionMeta(
                  PIPELINE_ACTION_ID,
                  "bulk_load_" + sanitizeActionName(descriptor.targetTableName()) + "_" + copyIndex,
                  action ->
                      configurePipelineAction(action, bulkPipelinePath, pipelineRunConfiguration));
        } else {
          IAction bulkAction =
              DvBulkLoadCommandSupport.createStagingBulkLoadAction(
                  descriptor.targetDatabase(),
                  config,
                  variables,
                  descriptor.targetDbName(),
                  descriptor.targetTableName(),
                  descriptor.columnNames(),
                  stagedFilePath,
                  copyIndex);
          bulkActionMeta = new ActionMeta(bulkAction);
        }
        bulkActionMeta.setLocation(x, y);
        workflowMeta.addAction(bulkActionMeta);
        workflowMeta.addWorkflowHop(new WorkflowHopMeta(previousAction, bulkActionMeta));
        previousAction = bulkActionMeta;
        x += 200;
      }
    }

    return workflowMeta;
  }

  /** Result of a generated bulk-update master workflow execution. */
  public record MasterWorkflowRunOutcome(Result result, String workflowLogChannelId) {}

  /** Runs the master workflow and returns its result. */
  public static Result runMasterWorkflow(
      WorkflowMeta workflowMeta,
      String workflowRunConfiguration,
      LogLevel logLevel,
      ILoggingObject parent,
      IVariables variables,
      org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws HopException {
    return runMasterWorkflowWithLogChannel(
            workflowMeta, workflowRunConfiguration, logLevel, parent, variables, metadataProvider)
        .result();
  }

  /** Runs the master workflow and returns the result plus the workflow engine log channel id. */
  public static MasterWorkflowRunOutcome runMasterWorkflowWithLogChannel(
      WorkflowMeta workflowMeta,
      String workflowRunConfiguration,
      LogLevel logLevel,
      ILoggingObject parent,
      IVariables variables,
      org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws HopException {
    return runMasterWorkflowWithLogChannel(
        workflowMeta,
        workflowRunConfiguration,
        logLevel,
        parent,
        null,
        variables,
        metadataProvider);
  }

  /** Runs the master workflow and returns the result plus the workflow engine log channel id. */
  public static MasterWorkflowRunOutcome runMasterWorkflowWithLogChannel(
      WorkflowMeta workflowMeta,
      String workflowRunConfiguration,
      LogLevel logLevel,
      ILoggingObject parent,
      UpdateRunLiveRunContext liveContext,
      IVariables variables,
      org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws HopException {
    IWorkflowEngine<WorkflowMeta> engine =
        WorkflowEngineFactory.createWorkflowEngine(
            variables, workflowRunConfiguration, metadataProvider, workflowMeta, parent);
    if (logLevel != null) {
      engine.setLogLevel(logLevel);
    }

    UpdateRunLiveStagingWorkflowMonitor monitor =
        UpdateRunLiveStagingWorkflowMonitor.start(liveContext, engine);
    try {
      Result workflowResult = engine.startExecution();
      if (workflowResult == null) {
        workflowResult = new Result();
      }
      return new MasterWorkflowRunOutcome(workflowResult, engine.getLogChannelId());
    } finally {
      if (monitor != null) {
        monitor.close();
      }
    }
  }

  private static DvStagingLoadDescriptor inspectStagedPipeline(
      PipelineMeta pipelineMeta,
      String stagingFolder,
      DatabaseMeta targetDatabase,
      String targetDbName,
      IVariables variables)
      throws HopException {
    TransformMeta stagingTransform = findStagingTransform(pipelineMeta);
    if (stagingTransform == null) {
      // Partition driver pipelines (and similar orchestrators) have no CSV writer.
      return null;
    }

    String targetTableName =
        stagingTransform.getName().substring(DvTargetLoadSupport.STAGING_TRANSFORM_PREFIX.length());
    if (!(stagingTransform.getTransform() instanceof TextFileOutputMeta textFileOutputMeta)) {
      throw new HopException(
          "Staging transform '"
              + stagingTransform.getName()
              + "' is not a Text File Output transform");
    }

    int parallelCopies =
        DvIntegerSettingValidationSupport.requirePositiveInteger(
            stagingTransform.getCopiesString(),
            variables,
            DataVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
            "parallel copies");

    List<String> columnNames = new ArrayList<>();
    if (textFileOutputMeta.getOutputFields() != null) {
      for (TextFileField field : textFileOutputMeta.getOutputFields()) {
        if (field != null && !Utils.isEmpty(field.getName())) {
          columnNames.add(field.getName());
        }
      }
    }

    String stagedPipelinePath = pipelineMeta.getFilename();
    if (Utils.isEmpty(stagedPipelinePath)) {
      throw new HopException(
          "Staged pipeline '" + pipelineMeta.getName() + "' does not have a filename");
    }

    String stagingFileBase = textFileOutputMeta.getFileSettings().getFileName();
    if (variables != null) {
      stagingFileBase = variables.resolve(stagingFileBase);
    }

    return new DvStagingLoadDescriptor(
        pipelineMeta.getName(),
        stagedPipelinePath,
        targetTableName,
        stagingFolder,
        stagingFileBase,
        parallelCopies,
        List.copyOf(columnNames),
        targetDatabase,
        targetDbName);
  }

  private static TransformMeta findStagingTransform(PipelineMeta pipelineMeta) {
    if (pipelineMeta == null || pipelineMeta.getTransforms() == null) {
      return null;
    }
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta == null || Utils.isEmpty(transformMeta.getName())) {
        continue;
      }
      if (transformMeta.getName().startsWith(DvTargetLoadSupport.STAGING_TRANSFORM_PREFIX)
          && DvTargetLoadSupport.TEXT_FILE_OUTPUT_TRANSFORM_ID.equals(
              transformMeta.getPluginId())) {
        return transformMeta;
      }
    }
    return null;
  }

  private static void configurePipelineAction(
      IAction action, DvStagingLoadDescriptor descriptor, String pipelineRunConfiguration)
      throws HopException {
    configurePipelineAction(action, descriptor.stagedPipelinePath(), pipelineRunConfiguration);
  }

  private static void configurePipelineAction(
      IAction action, String pipelinePath, String pipelineRunConfiguration) throws HopException {
    DvBulkLoadActionSupport.invoke(action, "setFilename", String.class, pipelinePath);
    DvBulkLoadActionSupport.invoke(
        action, "setRunConfiguration", String.class, pipelineRunConfiguration);
    DvBulkLoadActionSupport.invoke(action, "setWaitingToFinish", boolean.class, true);
    DvBulkLoadActionSupport.invoke(action, "setClearResultRows", boolean.class, false);
    DvBulkLoadActionSupport.invoke(action, "setClearResultFiles", boolean.class, false);
  }

  private static ActionMeta newActionMeta(
      String actionPluginId, String actionName, ActionConfigurer configurer) throws HopException {
    IAction action = DvBulkLoadActionSupport.newConfiguredAction(actionPluginId, actionName);
    configurer.configure(action);
    return new ActionMeta(action);
  }

  private static String sanitizeActionName(String name) {
    if (Utils.isEmpty(name)) {
      return "pipeline";
    }
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  @FunctionalInterface
  private interface ActionConfigurer {
    void configure(IAction action) throws HopException;
  }
}
