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
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputMeta;

/**
 * Builds one-off pipelines that bulk-load staged CSV shards from the Hop client (PostgreSQL {@code
 * COPY FROM STDIN} via PGBulkLoader).
 */
public final class DvStagingBulkLoadPipelineSupport {

  public static final String CSV_INPUT_TRANSFORM_ID = "CSVInput";

  private DvStagingBulkLoadPipelineSupport() {}

  /**
   * Returns whether staged bulk loading must stream from the Hop client instead of the DB server.
   */
  public static boolean usesClientSideBulkLoad(DatabaseMeta targetDatabase) {
    return targetDatabase != null
        && DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID.equals(targetDatabase.getPluginId());
  }

  /**
   * Builds and writes a PostgreSQL bulk-load pipeline for one staged CSV shard. Returns the staged
   * {@code .hpl} path referenced by the master workflow.
   */
  public static String buildAndStagePostgresBulkLoadPipeline(
      String stagingFolder,
      IVariables variables,
      IDvTargetLoadConfiguration config,
      String targetDbName,
      String targetTableName,
      List<String> columnNames,
      String stagedCsvPath,
      int copyIndex)
      throws HopException {
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)) {
      throw new HopException(
          "PostgreSQL bulk loader transform '"
              + DvBulkLoadPluginSupport.PG_BULK_LOADER_ID
              + "' is not installed; required for staging-file bulk loading");
    }
    if (Utils.isEmpty(stagedCsvPath)) {
      throw new HopException("Staged CSV path is required for PostgreSQL bulk loading");
    }

    PipelineMeta pipelineMeta =
        buildPostgresBulkLoadPipeline(
            config,
            variables,
            targetDbName,
            targetTableName,
            columnNames,
            stagedCsvPath,
            copyIndex);
    DvPipelineOrchestratorSupport.stagePipelines(
        stagingFolder, variables, List.of(pipelineMeta), false);
    return pipelineMeta.getFilename();
  }

  static PipelineMeta buildPostgresBulkLoadPipeline(
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String targetDbName,
      String targetTableName,
      List<String> columnNames,
      String stagedCsvPath,
      int copyIndex)
      throws HopException {
    String pipelineName = "bulk-load-" + targetTableName + "-" + copyIndex;
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(pipelineName);

    CsvInputMeta csvInputMeta = new CsvInputMeta();
    csvInputMeta.setFilename(stagedCsvPath);
    csvInputMeta.setDelimiter(config.resolveBulkLoadDelimiter(variables));
    csvInputMeta.setEnclosure(config.resolveBulkLoadEnclosure(variables));
    csvInputMeta.setEncoding(config.resolveBulkLoadEncoding(variables));
    csvInputMeta.setHeaderPresent(true);
    csvInputMeta.setInputFields(buildCsvInputFields(columnNames));

    TransformMeta csvInputTransform =
        new TransformMeta(CSV_INPUT_TRANSFORM_ID, "read_staged_csv", csvInputMeta);
    csvInputTransform.setLocation(100, 100);
    pipelineMeta.addTransform(csvInputTransform);

    ITransformMeta pgBulkLoaderMeta =
        DvBulkLoadTransformSupport.loadTransformMeta(DvBulkLoadPluginSupport.PG_BULK_LOADER_ID);
    DvBulkLoadTransformSupport.configurePgBulkLoaderFromColumns(
        pgBulkLoaderMeta, config, variables, targetDbName, targetTableName, columnNames);

    TransformMeta pgBulkLoaderTransform =
        DvBulkLoadTransformSupport.newBulkLoaderTransform(
            DvBulkLoadPluginSupport.PG_BULK_LOADER_ID,
            "bulk_load_to_" + targetTableName,
            pgBulkLoaderMeta);
    pgBulkLoaderTransform.setLocation(350, 100);
    pipelineMeta.addTransform(pgBulkLoaderTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(csvInputTransform, pgBulkLoaderTransform));

    return pipelineMeta;
  }

  private static List<CsvInputField> buildCsvInputFields(List<String> columnNames) {
    List<CsvInputField> fields = new ArrayList<>();
    if (columnNames == null) {
      return fields;
    }
    for (String columnName : columnNames) {
      if (!Utils.isEmpty(columnName)) {
        fields.add(new CsvInputField(columnName));
      }
    }
    return fields;
  }
}
