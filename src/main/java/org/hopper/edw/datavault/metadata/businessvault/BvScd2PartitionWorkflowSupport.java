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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.addsequence.AddSequenceMeta;
import org.apache.hop.pipeline.transforms.pipelineexecutor.PipelineExecutorMeta;
import org.apache.hop.pipeline.transforms.pipelineexecutor.PipelineExecutorParameters;
import org.apache.hop.pipeline.transforms.rowgenerator.GeneratorField;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileField;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.sql.ActionSql;
import org.apache.hop.workflow.actions.start.ActionStart;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DvBulkLoadCommandSupport;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.hopper.edw.datavault.metadata.DvIntegerSettingValidationSupport;
import org.hopper.edw.datavault.metadata.DvMultiSourceUpdateWorkflowSupport;
import org.hopper.edw.datavault.metadata.DvMultiSourceUpdateWorkflowSupport.PipelineActionFactory;
import org.hopper.edw.datavault.metadata.DvStagingBulkLoadPipelineSupport;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.DvTargetLoadSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport.Scd2BuildContext;

/**
 * Driver pipeline and wrapper workflow for hash-key partitioned SCD2 full rebuilds: truncate once,
 * then run the parameterized SCD2 pipeline once per partition.
 */
public final class BvScd2PartitionWorkflowSupport {

  public static final String GENERATE_PARTITIONS_TRANSFORM = "generate_partitions";
  public static final String PARTITION_NUMBER_TRANSFORM = "partition_number";
  public static final String EXECUTE_SCD2_TRANSFORM = "execute_scd2";
  public static final String DRIVER_NAME_SUFFIX = "-partitions";
  public static final String WORKFLOW_NAME_SUFFIX = "-partitioned";

  private BvScd2PartitionWorkflowSupport() {}

  public static String driverPipelineName(String scd2PipelineName) {
    String base = Utils.isEmpty(scd2PipelineName) ? "bv-scd2" : scd2PipelineName;
    return base + DRIVER_NAME_SUFFIX;
  }

  public static String workflowName(String scd2PipelineName) {
    String base = Utils.isEmpty(scd2PipelineName) ? "bv-scd2" : scd2PipelineName;
    return base + WORKFLOW_NAME_SUFFIX;
  }

  public static PipelineMeta buildDriverPipeline(Scd2BuildContext ctx, PipelineMeta scd2Pipeline)
      throws HopException {
    if (ctx == null || ctx.scd2Table == null || !ctx.scd2Table.isHashKeyPartitioned()) {
      throw new HopException(
          "Hash-key partitioned SCD2 context is required for the driver pipeline");
    }
    if (scd2Pipeline == null || Utils.isEmpty(scd2Pipeline.getName())) {
      throw new HopException("SCD2 pipeline name is required for the partition driver");
    }

    int partitionCount = ctx.scd2Table.getHashKeyPartitionCountOrDefault().getPartitionCount();
    PipelineMeta driver = new PipelineMeta();
    driver.setName(driverPipelineName(scd2Pipeline.getName()));
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        driver, ctx.bvModel, "scd2-partitions", ctx.scd2Table.getName(), ctx.bvTargetTableName);

    RowGeneratorMeta generateMeta = new RowGeneratorMeta();
    generateMeta.setNeverEnding(false);
    generateMeta.setRowLimit(Integer.toString(partitionCount));
    generateMeta
        .getFields()
        .add(
            new GeneratorField(
                BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE,
                "Integer",
                null,
                -1,
                -1,
                null,
                null,
                null,
                Integer.toString(partitionCount),
                false));
    TransformMeta generate =
        new TransformMeta("RowGenerator", GENERATE_PARTITIONS_TRANSFORM, generateMeta);
    generate.setLocation(new Point(160, 160));
    generate.setDistributes(false);
    driver.addTransform(generate);

    AddSequenceMeta sequenceMeta = new AddSequenceMeta();
    sequenceMeta.setDefault();
    sequenceMeta.setDatabaseUsed(false);
    sequenceMeta.setCounterUsed(true);
    sequenceMeta.setValueName(BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE);
    sequenceMeta.setStartAtByValue(0);
    sequenceMeta.setIncrementByValue(1);
    sequenceMeta.setMaxValueByValue(Math.max(0, partitionCount - 1));
    TransformMeta sequence =
        new TransformMeta("Sequence", PARTITION_NUMBER_TRANSFORM, sequenceMeta);
    sequence.setLocation(new Point(320, 160));
    driver.addTransform(sequence);
    driver.addPipelineHop(new PipelineHopMeta(generate, sequence));

    PipelineExecutorMeta executorMeta = new PipelineExecutorMeta();
    executorMeta.setDefault();
    executorMeta.setFilename(scd2Pipeline.getName() + PipelineMeta.PIPELINE_EXTENSION);
    executorMeta.setFilenameInField(false);
    executorMeta.setGroupSize("1");
    executorMeta.setInheritingAllVariables(true);
    executorMeta
        .getParameters()
        .add(parameter(BvScd2HashPartitionSqlSupport.PARTITION_COUNT_VARIABLE));
    executorMeta
        .getParameters()
        .add(parameter(BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_VARIABLE));
    TransformMeta executor =
        new TransformMeta("PipelineExecutor", EXECUTE_SCD2_TRANSFORM, executorMeta);
    executor.setLocation(new Point(480, 160));
    executor.setCopiesString("1");
    driver.addTransform(executor);
    driver.addPipelineHop(new PipelineHopMeta(sequence, executor));

    return driver;
  }

  public static WorkflowMeta buildWorkflow(Scd2BuildContext ctx, PipelineMeta driverPipeline)
      throws HopException {
    return buildWorkflow(ctx, driverPipeline, null, null);
  }

  public static WorkflowMeta buildWorkflow(
      Scd2BuildContext ctx, PipelineMeta driverPipeline, PipelineMeta scd2Pipeline)
      throws HopException {
    return buildWorkflow(ctx, driverPipeline, scd2Pipeline, null);
  }

  public static WorkflowMeta buildWorkflow(
      Scd2BuildContext ctx, PipelineMeta driverPipeline, PipelineActionFactory actionFactory)
      throws HopException {
    return buildWorkflow(ctx, driverPipeline, null, actionFactory);
  }

  public static WorkflowMeta buildWorkflow(
      Scd2BuildContext ctx,
      PipelineMeta driverPipeline,
      PipelineMeta scd2Pipeline,
      PipelineActionFactory actionFactory)
      throws HopException {
    if (ctx == null || ctx.scd2Table == null || !ctx.scd2Table.isHashKeyPartitioned()) {
      throw new HopException(
          "Hash-key partitioned SCD2 context is required for the wrapper workflow");
    }
    if (driverPipeline == null || Utils.isEmpty(driverPipeline.getName())) {
      throw new HopException("Partition driver pipeline is required");
    }

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(workflowName(ctx.pipelineName));

    ActionStart startAction = new ActionStart("Start");
    ActionMeta startMeta = new ActionMeta(startAction);
    startMeta.setLocation(50, 50);
    workflowMeta.addAction(startMeta);

    ActionSql sqlAction = new ActionSql("truncate_" + sanitize(ctx.bvTargetTableName));
    sqlAction.setConnection(ctx.targetDbName);
    sqlAction.setSqlFromFile(false);
    sqlAction.setSql(
        buildTruncateSql(ctx.targetDatabaseMeta, ctx.variables, ctx.bvTargetTableName));
    sqlAction.setSendOneStatement(true);
    sqlAction.setUseVariableSubstitution(true);
    ActionMeta sqlMeta = new ActionMeta(sqlAction);
    sqlMeta.setLocation(250, 50);
    workflowMeta.addAction(sqlMeta);
    workflowMeta.addWorkflowHop(new WorkflowHopMeta(startMeta, sqlMeta));

    String placeholderFilename = driverPipeline.getName() + PipelineMeta.PIPELINE_EXTENSION;
    ActionMeta pipelineAction =
        DvMultiSourceUpdateWorkflowSupport.newPipelineActionMeta(
            "run_" + sanitize(driverPipeline.getName()), placeholderFilename, null, actionFactory);
    pipelineAction.setLocation(450, 50);
    workflowMeta.addAction(pipelineAction);
    workflowMeta.addWorkflowHop(new WorkflowHopMeta(sqlMeta, pipelineAction));

    appendStagingBulkLoadActions(workflowMeta, pipelineAction, ctx, scd2Pipeline);

    return workflowMeta;
  }

  /**
   * After the partition driver finishes, bulk-load each {@code
   * pipeline-${PARTITION_NUMBER}-${copy}.csv} shard. No-op unless target load mode is Staging file.
   */
  static void appendStagingBulkLoadActions(
      WorkflowMeta workflowMeta,
      ActionMeta previousAction,
      Scd2BuildContext ctx,
      PipelineMeta scd2Pipeline)
      throws HopException {
    if (workflowMeta == null
        || previousAction == null
        || ctx == null
        || ctx.bvConfig == null
        || ctx.bvConfig.resolveTargetLoadMode() != DvTargetLoadMode.STAGING_FILE
        || scd2Pipeline == null) {
      return;
    }
    if (!DvBulkLoadPluginSupport.isModeAvailable(
        ctx.targetDatabaseMeta, DvTargetLoadMode.STAGING_FILE)) {
      throw new HopException(
          "Staging file bulk loading is not available for the Business Vault target database of SCD2 table '"
              + ctx.scd2Table.getName()
              + "'");
    }

    TextFileOutputMeta textFileOutputMeta = findStagingFileOutput(scd2Pipeline);
    if (textFileOutputMeta == null
        || textFileOutputMeta.getFileSettings() == null
        || Utils.isEmpty(textFileOutputMeta.getFileSettings().getFileName())) {
      throw new HopException(
          "Partitioned SCD2 pipeline '"
              + scd2Pipeline.getName()
              + "' is missing a Text File Output staging filename");
    }

    int partitionCount = ctx.scd2Table.getHashKeyPartitionCountOrDefault().getPartitionCount();
    int parallelCopies =
        DvIntegerSettingValidationSupport.requirePositiveInteger(
            ctx.bvConfig.resolveTargetTableParallelCopies(ctx.variables),
            ctx.variables,
            DataVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
            "parallel copies");
    List<String> columnNames = stagingColumnNames(textFileOutputMeta);
    String fileBase = textFileOutputMeta.getFileSettings().getFileName();
    if (ctx.variables != null) {
      fileBase = ctx.variables.resolve(fileBase);
    }

    String bulkStagingFolder =
        ctx.bvConfig.resolveBulkLoadStagingFolder(
            ctx.variables, ctx.bvModel != null ? ctx.bvModel.getName() : "business-vault");
    int x = previousAction.getLocation() != null ? previousAction.getLocation().x + 200 : 650;
    int y = previousAction.getLocation() != null ? previousAction.getLocation().y : 50;
    ActionMeta previous = previousAction;
    for (int partition = 0; partition < partitionCount; partition++) {
      String partitionBase = resolvePartitionedStagingFileBase(fileBase, partition);
      for (int copyIndex = 0; copyIndex < parallelCopies; copyIndex++) {
        String stagedFilePath =
            DvTargetLoadSupport.resolveStagedCsvFilePath(partitionBase, copyIndex);
        ActionMeta bulkActionMeta =
            newStagingBulkLoadAction(
                ctx, bulkStagingFolder, columnNames, stagedFilePath, partition, copyIndex);
        bulkActionMeta.setLocation(x, y);
        workflowMeta.addAction(bulkActionMeta);
        workflowMeta.addWorkflowHop(new WorkflowHopMeta(previous, bulkActionMeta));
        previous = bulkActionMeta;
        x += 200;
      }
    }
  }

  static String resolvePartitionedStagingFileBase(String stagingFileBase, int partitionNumber) {
    if (Utils.isEmpty(stagingFileBase)) {
      return stagingFileBase;
    }
    return stagingFileBase.replace(
        BvScd2HashPartitionSqlSupport.PARTITION_NUMBER_REF, Integer.toString(partitionNumber));
  }

  private static ActionMeta newStagingBulkLoadAction(
      Scd2BuildContext ctx,
      String bulkStagingFolder,
      List<String> columnNames,
      String stagedFilePath,
      int partition,
      int copyIndex)
      throws HopException {
    String actionName =
        "bulk_load_" + sanitize(ctx.bvTargetTableName) + "_p" + partition + "_" + copyIndex;
    if (DvStagingBulkLoadPipelineSupport.usesClientSideBulkLoad(ctx.targetDatabaseMeta)) {
      String bulkPipelinePath =
          DvStagingBulkLoadPipelineSupport.buildAndStagePostgresBulkLoadPipeline(
              bulkStagingFolder,
              ctx.variables,
              ctx.bvConfig,
              ctx.targetDbName,
              ctx.bvTargetTableName,
              columnNames,
              stagedFilePath,
              copyIndex + partition * 100);
      ActionMeta pipelineAction =
          DvMultiSourceUpdateWorkflowSupport.newPipelineActionMeta(
              actionName, bulkPipelinePath, null, null);
      return pipelineAction;
    }
    IAction bulkAction =
        DvBulkLoadCommandSupport.createStagingBulkLoadAction(
            ctx.targetDatabaseMeta,
            ctx.bvConfig,
            ctx.variables,
            ctx.targetDbName,
            ctx.bvTargetTableName,
            columnNames,
            stagedFilePath,
            copyIndex);
    bulkAction.setName(actionName);
    return new ActionMeta(bulkAction);
  }

  private static TextFileOutputMeta findStagingFileOutput(PipelineMeta pipelineMeta) {
    if (pipelineMeta == null || pipelineMeta.getTransforms() == null) {
      return null;
    }
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta != null
          && transformMeta.getTransform() instanceof TextFileOutputMeta textFileOutputMeta) {
        return textFileOutputMeta;
      }
    }
    return null;
  }

  private static List<String> stagingColumnNames(TextFileOutputMeta textFileOutputMeta) {
    List<String> columnNames = new ArrayList<>();
    if (textFileOutputMeta.getOutputFields() == null) {
      return columnNames;
    }
    for (TextFileField field : textFileOutputMeta.getOutputFields()) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        columnNames.add(field.getName());
      }
    }
    return columnNames;
  }

  static String buildTruncateSql(
      DatabaseMeta databaseMeta, IVariables variables, String tableName) {
    String quotedTable =
        databaseMeta != null
            ? databaseMeta.getQuotedSchemaTableCombination(variables, null, tableName)
            : tableName;
    if (databaseMeta != null) {
      try {
        String statement = databaseMeta.getTruncateTableStatement(variables, null, tableName);
        if (!Utils.isEmpty(statement)) {
          return statement;
        }
      } catch (Exception ignored) {
        // Test stubs and unloaded database plugins have no iDatabase.
      }
    }
    return "TRUNCATE TABLE " + quotedTable;
  }

  /**
   * Rewrites Pipeline Executor filenames after nested SCD2 pipelines have been staged to absolute
   * paths.
   */
  public static void applyStagedPipelineExecutorFilenames(
      List<PipelineMeta> pipelines, Map<String, String> stagedPathByPipelineBasename) {
    if (pipelines == null
        || stagedPathByPipelineBasename == null
        || stagedPathByPipelineBasename.isEmpty()) {
      return;
    }
    for (PipelineMeta pipelineMeta : pipelines) {
      if (pipelineMeta == null || pipelineMeta.getTransforms() == null) {
        continue;
      }
      for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
        if (transformMeta == null
            || !(transformMeta.getTransform() instanceof PipelineExecutorMeta executorMeta)) {
          continue;
        }
        String current = executorMeta.getFilename();
        if (Utils.isEmpty(current)) {
          continue;
        }
        String staged = lookupStagedPath(stagedPathByPipelineBasename, basename(current));
        if (!Utils.isEmpty(staged)) {
          executorMeta.setFilename(staged);
        }
      }
    }
  }

  public static void applyPipelineExecutorRunConfiguration(
      List<PipelineMeta> pipelines, String pipelineRunConfiguration) {
    if (pipelines == null || Utils.isEmpty(pipelineRunConfiguration)) {
      return;
    }
    for (PipelineMeta pipelineMeta : pipelines) {
      if (pipelineMeta == null || pipelineMeta.getTransforms() == null) {
        continue;
      }
      for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
        if (transformMeta != null
            && transformMeta.getTransform() instanceof PipelineExecutorMeta executorMeta) {
          executorMeta.setRunConfigurationName(pipelineRunConfiguration);
        }
      }
    }
  }

  private static PipelineExecutorParameters parameter(String name) {
    PipelineExecutorParameters mapping = new PipelineExecutorParameters();
    mapping.setVariable(name);
    mapping.setField(name);
    return mapping;
  }

  private static String lookupStagedPath(Map<String, String> map, String key) {
    if (map.containsKey(key)) {
      return map.get(key);
    }
    String withoutExt = stripPipelineExtension(key);
    if (map.containsKey(withoutExt)) {
      return map.get(withoutExt);
    }
    return map.get(withoutExt + PipelineMeta.PIPELINE_EXTENSION);
  }

  private static String basename(String path) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  private static String stripPipelineExtension(String name) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    String ext = PipelineMeta.PIPELINE_EXTENSION;
    if (name.regionMatches(true, name.length() - ext.length(), ext, 0, ext.length())) {
      return name.substring(0, name.length() - ext.length());
    }
    return name;
  }

  private static String sanitize(String name) {
    if (Utils.isEmpty(name)) {
      return "table";
    }
    return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
  }
}
