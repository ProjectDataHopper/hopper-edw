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
package org.hopper.edw.datavault.virtualization.execute;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.virtualization.generate.RelToPipelineGenerator;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlException;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlOptions;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;

/** Runs a planned source-model SQL pipeline locally and collects preview rows. */
public final class SourceModelSqlExecutor {

  public static final int DEFAULT_ROW_LIMIT = SourceModelSqlOptions.DEFAULT_PREVIEW_LIMIT;

  private SourceModelSqlExecutor() {}

  public static List<RowMetaAndData> preview(
      SourceModel model,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    return preview(model, sql, variables, metadataProvider, rowLimit, null);
  }

  /**
   * @param jdbcSchemaAlias Source model service / JDBC schema name so {@code schema.table} SQL from
   *     tools like DBeaver validates against Calcite
   */
  public static List<RowMetaAndData> preview(
      SourceModel model,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      String jdbcSchemaAlias)
      throws HopException {
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;
    SourceModelSqlOptions options =
        SourceModelSqlOptions.builder()
            .pipelineName("source-model-sql-preview")
            .previewRowLimit(limit)
            .jdbcSchemaAlias(jdbcSchemaAlias)
            .build();
    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(model, sql, variables, metadataProvider, options);
    return execute(plan, variables, limit);
  }

  public static List<RowMetaAndData> execute(
      SourceModelSqlPlan plan, IVariables variables, int rowLimit) throws HopException {
    if (plan == null || plan.pipelineMeta() == null) {
      throw new SourceModelSqlException("SQL plan has no pipeline");
    }
    PipelineMeta pipelineMeta = plan.pipelineMeta();
    String transformName = plan.outputTransformName();

    // Programmatic pipelines skip XML load path — resolve MergeJoin INFO streams etc.
    RelToPipelineGenerator.finalizePipelineMeta(pipelineMeta);

    Pipeline pipeline = new LocalPipelineEngine(pipelineMeta);
    if (variables != null) {
      pipeline.initializeFrom(variables);
    }
    // BASIC so init failures are written to the log buffer we attach to exceptions.
    pipeline.setLogLevel(LogLevel.BASIC);
    try {
      pipeline.prepareExecution();
    } catch (HopException e) {
      throw new HopException(
          "Source model SQL preview failed to prepare: " + e.getMessage() + logSnippet(pipeline),
          e);
    }

    List<RowMetaAndData> rows = new ArrayList<>();
    ITransform transform = pipeline.findRunThread(transformName);
    if (transform == null) {
      throw new HopException(
          "Preview transform '"
              + transformName
              + "' was not started (a transform likely failed to initialize)."
              + logSnippet(pipeline));
    }
    final int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;
    transform.addRowListener(
        new RowAdapter() {
          @Override
          public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
            if (rows.size() < limit) {
              rows.add(new RowMetaAndData(rowMeta, row));
            }
          }
        });

    pipeline.startThreads();
    pipeline.waitUntilFinished();
    if (pipeline.getErrors() > 0) {
      throw new HopException(
          "Source model SQL preview finished with "
              + pipeline.getErrors()
              + " error(s)."
              + logSnippet(pipeline));
    }
    return rows;
  }

  private static String logSnippet(Pipeline pipeline) {
    try {
      if (pipeline == null || pipeline.getLogChannelId() == null) {
        return "";
      }
      StringBuffer buffer = HopLogStore.getAppender().getBuffer(pipeline.getLogChannelId(), true);
      if (buffer == null || buffer.isEmpty()) {
        return "";
      }
      String text = buffer.toString().trim();
      if (text.length() > 2000) {
        text = text.substring(text.length() - 2000);
      }
      return "\n--- log ---\n" + text;
    } catch (Exception e) {
      return "";
    }
  }
}
