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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileField;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;

/** Shared helpers for the target write transform at the end of DV update pipelines. */
public final class DvTargetLoadSupport {

  public static final String TEXT_FILE_OUTPUT_TRANSFORM_ID = "TextFileOutput";
  public static final String STAGING_TRANSFORM_PREFIX = "stage_to_";
  public static final String STAGING_FILE_EXTENSION = "csv";
  public static final String STAGING_FILE_COPY_VARIABLE = Const.INTERNAL_VARIABLE_TRANSFORM_COPYNR;
  public static final String STAGING_FILE_COPY_VARIABLE_PATTERN =
      "${" + STAGING_FILE_COPY_VARIABLE + "}";

  private DvTargetLoadSupport() {}

  /** Inputs required to append a target load transform to a generated pipeline. */
  public static final class TargetLoadContext {
    public final IDvTargetLoadConfiguration config;
    public final IVariables variables;
    public final DatabaseMeta targetDatabaseMeta;
    public final String targetDbName;
    public final String targetTableName;
    public final String pipelineName;
    public final String modelName;
    public final int locationX;
    public final int locationY;

    public TargetLoadContext(
        IDvTargetLoadConfiguration config,
        IVariables variables,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName,
        String modelName,
        int locationX,
        int locationY) {
      this.config = config;
      this.variables = variables;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
      this.modelName = modelName;
      this.locationX = locationX;
      this.locationY = locationY;
    }
  }

  /** Result of wiring a target load transform (and optional staging metadata for later PRs). */
  public static final class TargetLoadResult {
    public final TransformMeta transformMeta;
    public final DvTargetLoadMode mode;
    public final String stagingFilePattern;

    public TargetLoadResult(
        TransformMeta transformMeta, DvTargetLoadMode mode, String stagingFilePattern) {
      this.transformMeta = transformMeta;
      this.mode = mode;
      this.stagingFilePattern = stagingFilePattern;
    }
  }

  /**
   * Appends the configured target load transform after {@code predecessor}.
   *
   * <p>Supports {@link DvTargetLoadMode#TABLE_OUTPUT}, {@link DvTargetLoadMode#NATIVE_BULK}, and
   * {@link DvTargetLoadMode#STAGING_FILE}.
   */
  public static TargetLoadResult addTargetLoad(
      TargetLoadContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      Set<String> excludeFields)
      throws HopException {
    return addTargetLoad(ctx, pipelineMeta, targetLayout, predecessor, excludeFields, false);
  }

  public static TargetLoadResult addTargetLoad(
      TargetLoadContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      Set<String> excludeFields,
      boolean truncateTable)
      throws HopException {
    if (ctx == null || ctx.config == null) {
      throw new HopException("Target load context is not configured");
    }
    DvTargetLoadMode mode = ctx.config.resolveTargetLoadMode();
    if (truncateTable && mode != DvTargetLoadMode.TABLE_OUTPUT) {
      mode = DvTargetLoadMode.TABLE_OUTPUT;
    }
    return switch (mode) {
      case TABLE_OUTPUT ->
          addTableOutput(
              ctx, pipelineMeta, targetLayout, predecessor, excludeFields, truncateTable);
      case NATIVE_BULK ->
          addNativeBulkLoader(ctx, pipelineMeta, targetLayout, predecessor, excludeFields);
      case STAGING_FILE ->
          addStagingFileOutput(ctx, pipelineMeta, targetLayout, predecessor, excludeFields);
    };
  }

  /**
   * Builds the staged CSV filename base used by Text File Output and bulk-load workflow actions.
   */
  public static String buildStagingFileBase(
      String stagingFolder, String pipelineName, boolean includeCopyVariable) {
    String base =
        ensureTrailingSlash(stagingFolder) + stripStagedPipelineSequencePrefix(pipelineName);
    if (includeCopyVariable) {
      base = base + "-" + STAGING_FILE_COPY_VARIABLE_PATTERN;
    }
    return base;
  }

  /**
   * Removes the zero-padded sequence prefix ({@code 0001-}) added when staging pipelines for
   * ordered execution. The Text File Output path is generated before that prefix is applied.
   */
  public static String stripStagedPipelineSequencePrefix(String pipelineName) {
    if (Utils.isEmpty(pipelineName) || pipelineName.length() <= 5) {
      return pipelineName;
    }
    if (pipelineName.charAt(4) == '-') {
      String sequence = pipelineName.substring(0, 4);
      for (int i = 0; i < sequence.length(); i++) {
        if (!Character.isDigit(sequence.charAt(i))) {
          return pipelineName;
        }
      }
      return pipelineName.substring(5);
    }
    return pipelineName;
  }

  /** Resolves the staged CSV path for a parallel copy from the Text File Output file base. */
  public static String resolveStagedCsvFilePath(String stagingFileBase, int copyIndex) {
    if (Utils.isEmpty(stagingFileBase)) {
      return stagingFileBase;
    }
    String base = stagingFileBase;
    if (base.contains(STAGING_FILE_COPY_VARIABLE_PATTERN)) {
      base = base.replace(STAGING_FILE_COPY_VARIABLE_PATTERN, Integer.toString(copyIndex));
    } else if (base.endsWith("-" + STAGING_FILE_COPY_VARIABLE)) {
      base =
          base.substring(0, base.length() - STAGING_FILE_COPY_VARIABLE.length()) + "-" + copyIndex;
    } else {
      base = base + "-" + copyIndex;
    }
    return base + "." + STAGING_FILE_EXTENSION;
  }

  private static TargetLoadResult addStagingFileOutput(
      TargetLoadContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      Set<String> excludeFields)
      throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return new TargetLoadResult(null, DvTargetLoadMode.STAGING_FILE, null);
    }
    if (predecessor == null) {
      return new TargetLoadResult(null, DvTargetLoadMode.STAGING_FILE, null);
    }

    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      throw new HopException("Target table name is required for staging-file bulk loading");
    }

    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(TEXT_FILE_OUTPUT_TRANSFORM_ID)) {
      throw new HopException(
          "Text File Output transform is not installed; required for staging-file bulk loading");
    }

    try {
      String stagingFolder = ctx.config.resolveBulkLoadStagingFolder(ctx.variables, ctx.modelName);
      String fileBase = buildStagingFileBase(stagingFolder, ctx.pipelineName, true);
      String stagingFilePattern = fileBase + "." + STAGING_FILE_EXTENSION;

      TextFileOutputMeta textFileOutputMeta = new TextFileOutputMeta();
      textFileOutputMeta.setSeparator(ctx.config.resolveBulkLoadDelimiter(ctx.variables));
      textFileOutputMeta.setEnclosure(ctx.config.resolveBulkLoadEnclosure(ctx.variables));
      textFileOutputMeta.setEncoding(ctx.config.resolveBulkLoadEncoding(ctx.variables));
      textFileOutputMeta.setHeaderEnabled(true);
      textFileOutputMeta.setFooterEnabled(false);
      textFileOutputMeta.setFileFormat("UNIX");
      textFileOutputMeta.setCreateParentFolder(true);
      textFileOutputMeta.getFileSettings().setFileName(fileBase);
      textFileOutputMeta.getFileSettings().setExtension(STAGING_FILE_EXTENSION);
      textFileOutputMeta.getFileSettings().setDoNotOpenNewFileInit(true);
      textFileOutputMeta.getFileSettings().setTransformNrInFilename(false);
      textFileOutputMeta.getFileSettings().setAddToResultFiles(false);

      List<TextFileField> outputFields = new ArrayList<>();
      if (targetLayout != null) {
        for (IValueMeta vm : targetLayout.getValueMetaList()) {
          String name = vm.getName();
          if (shouldExcludeField(name, excludeFields)) {
            continue;
          }
          TextFileField field = new TextFileField();
          field.setName(name);
          field.setType(vm.getType());
          field.setFormat(vm.getConversionMask());
          field.setLength(-1);
          field.setPrecision(-1);
          outputFields.add(field);
        }
      }
      textFileOutputMeta.setOutputFields(outputFields);

      TransformMeta tm =
          new TransformMeta(
              TEXT_FILE_OUTPUT_TRANSFORM_ID,
              STAGING_TRANSFORM_PREFIX + tableName,
              textFileOutputMeta);
      tm.setCopiesString(ctx.config.resolveTargetTableParallelCopies(ctx.variables));
      tm.setLocation(ctx.locationX, ctx.locationY);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return new TargetLoadResult(tm, DvTargetLoadMode.STAGING_FILE, stagingFilePattern);
    } catch (Exception e) {
      throw new HopException("Error creating Text File Output staging transform", e);
    }
  }

  private static TargetLoadResult addNativeBulkLoader(
      TargetLoadContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      Set<String> excludeFields)
      throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return new TargetLoadResult(null, DvTargetLoadMode.NATIVE_BULK, null);
    }
    if (predecessor == null) {
      return new TargetLoadResult(null, DvTargetLoadMode.NATIVE_BULK, null);
    }

    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      throw new HopException("Target table name is required for native bulk loading");
    }

    String transformPluginId =
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(ctx.targetDatabaseMeta);
    if (Utils.isEmpty(transformPluginId)
        || !DvBulkLoadPluginSupport.isTransformPluginAvailable(transformPluginId)) {
      throw new HopException(
          "Native bulk loading is not available for target database type "
              + ctx.targetDatabaseMeta.getPluginId()
              + "; install the matching Hop database bulk loader plugin or use Table Output");
    }

    try {
      ITransformMeta bulkLoaderMeta =
          DvBulkLoadTransformSupport.loadTransformMeta(transformPluginId);
      DvBulkLoadTransformSupport.configureNativeBulkLoader(
          transformPluginId, bulkLoaderMeta, ctx, targetLayout, excludeFields);

      TransformMeta tm =
          DvBulkLoadTransformSupport.newBulkLoaderTransform(
              transformPluginId, "bulk_load_to_" + tableName, bulkLoaderMeta);
      tm.setCopiesString("1");
      tm.setLocation(ctx.locationX, ctx.locationY);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return new TargetLoadResult(tm, DvTargetLoadMode.NATIVE_BULK, null);
    } catch (Exception e) {
      throw new HopException("Error creating native bulk loader transform", e);
    }
  }

  private static TargetLoadResult addTableOutput(
      TargetLoadContext ctx,
      PipelineMeta pipelineMeta,
      IRowMeta targetLayout,
      TransformMeta predecessor,
      Set<String> excludeFields,
      boolean truncateTable)
      throws HopException {
    if (ctx.targetDatabaseMeta == null || Utils.isEmpty(ctx.targetDbName)) {
      return new TargetLoadResult(null, DvTargetLoadMode.TABLE_OUTPUT, null);
    }
    if (predecessor == null) {
      return new TargetLoadResult(null, DvTargetLoadMode.TABLE_OUTPUT, null);
    }

    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      throw new HopException("Target table name is required for Table Output");
    }

    try {
      TableOutputMeta tableOutputMeta = new TableOutputMeta();
      tableOutputMeta.setConnection(ctx.targetDbName);
      tableOutputMeta.setTableName(tableName);
      tableOutputMeta.setSpecifyFields(true);
      tableOutputMeta.setTruncateTable(truncateTable);
      tableOutputMeta.setCommitSize(ctx.config.resolveTargetTableCommitSize(ctx.variables));

      if (targetLayout != null) {
        for (IValueMeta vm : targetLayout.getValueMetaList()) {
          String name = vm.getName();
          if (shouldExcludeField(name, excludeFields)) {
            continue;
          }
          tableOutputMeta.getFields().add(new TableOutputField(name, name));
        }
      }

      TransformMeta tm = new TransformMeta("TableOutput", "write_to_" + tableName, tableOutputMeta);
      tm.setCopiesString(ctx.config.resolveTargetTableParallelCopies(ctx.variables));
      tm.setLocation(ctx.locationX, ctx.locationY);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
      return new TargetLoadResult(tm, DvTargetLoadMode.TABLE_OUTPUT, null);
    } catch (Exception e) {
      throw new HopException("Error creating Table Output transform", e);
    }
  }

  private static String ensureTrailingSlash(String folder) {
    if (Utils.isEmpty(folder)) {
      return "";
    }
    if (folder.endsWith("/") || folder.endsWith("\\")) {
      return folder;
    }
    return folder + "/";
  }

  private static boolean shouldExcludeField(String fieldName, Set<String> excludeFields) {
    if (Utils.isEmpty(fieldName) || excludeFields == null || excludeFields.isEmpty()) {
      return false;
    }
    for (String exclude : excludeFields) {
      if (exclude != null && exclude.equalsIgnoreCase(fieldName)) {
        return true;
      }
    }
    return false;
  }
}
