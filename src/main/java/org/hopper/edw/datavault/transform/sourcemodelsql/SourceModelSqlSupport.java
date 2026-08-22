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
package org.hopper.edw.datavault.transform.sourcemodelsql;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.hopper.edw.datavault.layout.DvPipelineElkLayout;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.virtualization.execute.SourceModelSqlExecutor;
import org.hopper.edw.datavault.virtualization.generate.RelToPipelineGenerator;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlOptions;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Shared planning/execution helpers for the Source Model SQL transform and dialog. */
public final class SourceModelSqlSupport {

  private SourceModelSqlSupport() {}

  public static SourceModel loadModel(
      String sourceModelFilename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceModelFilename)) {
      throw new HopException("Source model filename is empty");
    }
    return SourceModelLoadSupport.load(sourceModelFilename, variables, metadataProvider);
  }

  public static void validate(
      String sourceModelFilename,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceModel model = loadModel(sourceModelFilename, variables, metadataProvider);
    String resolvedSql = variables != null ? variables.resolve(Const.NVL(sql, "")) : sql;
    SourceModelSqlEngine.validate(model, resolvedSql, variables);
  }

  public static SourceModelSqlPlan plan(
      String sourceModelFilename,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    SourceModel model = loadModel(sourceModelFilename, variables, metadataProvider);
    String resolvedSql = variables != null ? variables.resolve(Const.NVL(sql, "")) : sql;
    SourceModelSqlOptions options =
        SourceModelSqlOptions.builder()
            .pipelineName("source-model-sql-transform")
            .previewRowLimit(Math.max(0, rowLimit))
            .build();
    return SourceModelSqlEngine.plan(model, resolvedSql, variables, metadataProvider, options);
  }

  public static IRowMeta planOutputRowMeta(
      String sourceModelFilename,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(sourceModelFilename) || Utils.isEmpty(sql)) {
      return null;
    }
    SourceModelSqlPlan plan = plan(sourceModelFilename, sql, variables, metadataProvider, 0);
    return plan != null ? plan.outputRowMeta() : null;
  }

  public static List<RowMetaAndData> execute(
      String sourceModelFilename,
      String sql,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    SourceModel model = loadModel(sourceModelFilename, variables, metadataProvider);
    String resolvedSql = variables != null ? variables.resolve(Const.NVL(sql, "")) : sql;
    int limit = Math.max(0, rowLimit);
    if (limit > 0) {
      return SourceModelSqlExecutor.preview(model, resolvedSql, variables, metadataProvider, limit);
    }
    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            resolvedSql,
            variables,
            metadataProvider,
            SourceModelSqlOptions.builder().pipelineName("source-model-sql-transform").build());
    // 0 = no artificial cap in executor buffer loop (still subject to nested engine).
    return SourceModelSqlExecutor.execute(plan, variables, Integer.MAX_VALUE);
  }

  public static int parseRowLimit(String rowLimitText, IVariables variables) {
    if (Utils.isEmpty(rowLimitText)) {
      return 0;
    }
    String resolved =
        variables != null ? variables.resolve(rowLimitText.trim()) : rowLimitText.trim();
    try {
      return Math.max(0, Integer.parseInt(resolved));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Applies the Data Vault configuration-perspective ELK settings ({@link
   * org.hopper.edw.datavault.config.DataVaultConfig#getElkLayout()}) to a generated free-SQL
   * pipeline so transforms are positioned for GUI viewing.
   */
  public static void applyConfiguredElkLayout(PipelineMeta pipelineMeta) throws HopException {
    if (pipelineMeta == null) {
      return;
    }
    // Ensure MergeJoin INFO streams / parent refs exist before graph extraction.
    RelToPipelineGenerator.finalizePipelineMeta(pipelineMeta);
    try {
      DvPipelineElkLayout.layout(pipelineMeta);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to apply ELK layout to generated free SQL pipeline", e);
    }
  }

  /**
   * Writes a planned free-SQL pipeline to {@code ${java.io.tmpdir}/hop-source-model-sql/} and
   * reloads it so drill-down / Explorer open have a real VFS filename (not an in-memory-only {@link
   * PipelineMeta}). Applies configured ELK layout before writing.
   *
   * @return pipeline reloaded from the temp {@code .hpl} file
   */
  public static PipelineMeta saveGeneratedPipelineToTemp(
      PipelineMeta pipelineMeta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (pipelineMeta == null) {
      throw new HopException("Generated pipeline is null");
    }
    applyConfiguredElkLayout(pipelineMeta);
    String tmpRoot =
        variables != null
            ? variables.resolve("${java.io.tmpdir}")
            : System.getProperty("java.io.tmpdir");
    if (Utils.isEmpty(tmpRoot)) {
      tmpRoot = System.getProperty("java.io.tmpdir", "/tmp");
    }
    String folder = HopVfs.normalize(tmpRoot + "/hop-source-model-sql");
    try {
      FileObject dir = HopVfs.getFileObject(folder);
      if (!dir.exists()) {
        dir.createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to create temp folder '" + folder + "'", e);
    }

    String baseName = sanitizeTempBaseName(pipelineMeta.getName());
    String xml = pipelineMeta.getXml(variables);
    // Content-stable suffix so re-opens of the same plan overwrite the same file.
    int contentKey = Math.abs(xml.hashCode());
    String path = folder + "/" + baseName + "-" + Integer.toHexString(contentKey) + ".hpl";

    try {
      try (OutputStream out = HopVfs.getOutputStream(path, false)) {
        out.write(xml.getBytes(StandardCharsets.UTF_8));
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write generated pipeline to '" + path + "'", e);
    }

    // Reload from disk so filename is bound and plugins resolve like a normal open.
    PipelineMeta reloaded = new PipelineMeta(path, metadataProvider, variables);
    reloaded.clearChanged();
    return reloaded;
  }

  /** Sanitize for temp filenames (package-visible for tests). */
  static String sanitizeTempBaseName(String name) {
    if (Utils.isEmpty(name)) {
      return "source-model-sql-generated";
    }
    return name.replaceAll("[^a-zA-Z0-9._-]+", "_").toLowerCase(Locale.ROOT);
  }
}
