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
package org.apache.hop.datavault.transform.sourcemodelsql;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Transform meta for free SQL against a Hop Source Model ({@code .hsm}) using the Calcite
 * virtualisation engine.
 */
@Getter
@Setter
@Transform(
    id = "SourceModelSql",
    image = "source-model-sql.svg",
    name = "i18n::SourceModelSql.Name",
    description = "i18n::SourceModelSql.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "i18n::SourceModelSql.keyword",
    documentationUrl = "/pipeline/transforms/sourcemodelsql.html")
public class SourceModelSqlMeta extends BaseTransformMeta<SourceModelSql, SourceModelSqlData> {

  private static final Class<?> PKG = SourceModelSqlMeta.class;

  @HopMetadataProperty(
      key = "sourceModelFilename",
      injectionKey = "SOURCE_MODEL_FILENAME",
      injectionKeyDescription = "SourceModelSql.Injection.SOURCE_MODEL_FILENAME")
  private String sourceModelFilename;

  @HopMetadataProperty(
      key = "sql",
      injectionKey = "SQL",
      injectionKeyDescription = "SourceModelSql.Injection.SQL")
  private String sql;

  /**
   * Optional max rows for the nested plan (0 = unlimited). Applied as a soft preview/pushdown limit
   * where supported.
   */
  @HopMetadataProperty(
      key = "rowLimit",
      injectionKey = "ROW_LIMIT",
      injectionKeyDescription = "SourceModelSql.Injection.ROW_LIMIT")
  private String rowLimit = "0";

  public SourceModelSqlMeta() {
    super();
  }

  @Override
  public SourceModelSqlMeta clone() {
    SourceModelSqlMeta meta = new SourceModelSqlMeta();
    meta.sourceModelFilename = sourceModelFilename;
    meta.sql = sql;
    meta.rowLimit = rowLimit;
    return meta;
  }

  @Override
  public void setDefault() {
    sourceModelFilename = "";
    sql = "";
    rowLimit = "0";
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    try {
      IRowMeta planned =
          SourceModelSqlSupport.planOutputRowMeta(
              sourceModelFilename, sql, variables, metadataProvider);
      if (planned != null) {
        for (int i = 0; i < planned.size(); i++) {
          var vm = planned.getValueMeta(i).clone();
          vm.setOrigin(name);
          rowMeta.addValueMeta(vm);
        }
      }
    } catch (Exception e) {
      throw new HopTransformException(
          BaseMessages.getString(PKG, "SourceModelSqlMeta.Error.GetFields"), e);
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(sourceModelFilename)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModelSqlMeta.CheckResult.MissingModel"),
              transformMeta));
    }
    if (Utils.isEmpty(sql)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModelSqlMeta.CheckResult.MissingSql"),
              transformMeta));
    }
    if (!Utils.isEmpty(sourceModelFilename) && !Utils.isEmpty(sql)) {
      try {
        SourceModelSqlSupport.validate(sourceModelFilename, sql, variables, metadataProvider);
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(PKG, "SourceModelSqlMeta.CheckResult.SqlOk"),
                transformMeta));
      } catch (Exception e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModelSqlMeta.CheckResult.SqlError",
                    e.getMessage() != null ? e.getMessage() : e.toString()),
                transformMeta));
      }
    }
    if (input != null && input.length > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "SourceModelSqlMeta.CheckResult.ReceivingData"),
              transformMeta));
    }
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }

  /** Referenced object index: Hop Source Model ({@code .hsm}). */
  public static final int REFERENCED_SOURCE_MODEL = 0;

  /** Referenced object index: generated free-SQL pipeline (Calcite plan). */
  public static final int REFERENCED_GENERATED_PIPELINE = 1;

  /**
   * @return descriptions for the source model file and the generated execution pipeline
   */
  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "SourceModelSqlMeta.ReferencedObject.SourceModel"),
      BaseMessages.getString(PKG, "SourceModelSqlMeta.ReferencedObject.GeneratedPipeline"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {
      isSourceModelDefined(), isGeneratedPipelineDefined(),
    };
  }

  private boolean isSourceModelDefined() {
    return !Utils.isEmpty(sourceModelFilename);
  }

  private boolean isGeneratedPipelineDefined() {
    return isSourceModelDefined() && !Utils.isEmpty(sql);
  }

  /**
   * Load a referenced object for drill-down / impact / execution map.
   *
   * <ul>
   *   <li>{@link #REFERENCED_SOURCE_MODEL} — load the {@code .hsm} via VFS
   *   <li>{@link #REFERENCED_GENERATED_PIPELINE} — plan free SQL and return the generated {@link
   *       PipelineMeta}
   * </ul>
   */
  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return switch (index) {
      case REFERENCED_SOURCE_MODEL -> loadSourceModel(metadataProvider, variables);
      case REFERENCED_GENERATED_PIPELINE -> loadGeneratedPipeline(metadataProvider, variables);
      default ->
          throw new HopException(
              BaseMessages.getString(
                  PKG,
                  "SourceModelSqlMeta.ReferencedObject.UnknownIndex",
                  Integer.toString(index)));
    };
  }

  private IHasFilename loadSourceModel(IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    if (!isSourceModelDefined()) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceModelSqlMeta.CheckResult.MissingModel"));
    }
    return SourceModelSqlSupport.loadModel(sourceModelFilename, variables, metadataProvider);
  }

  private IHasFilename loadGeneratedPipeline(
      IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    if (!isGeneratedPipelineDefined()) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceModelSqlMeta.ReferencedObject.PipelineNotDefined"));
    }
    int limit = SourceModelSqlSupport.parseRowLimit(rowLimit, variables);
    var plan =
        SourceModelSqlSupport.plan(sourceModelFilename, sql, variables, metadataProvider, limit);
    if (plan == null || plan.pipelineMeta() == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceModelSqlMeta.ReferencedObject.PipelinePlanEmpty"));
    }
    PipelineMeta pipelineMeta = plan.pipelineMeta();
    // Stable name for the temp file / Explorer tab.
    if (Utils.isEmpty(pipelineMeta.getName())) {
      pipelineMeta.setName("source-model-sql-generated");
    }
    pipelineMeta.setMetadataProvider(metadataProvider);
    // Drill-down opens by VFS filename — persist under ${java.io.tmpdir} first.
    return SourceModelSqlSupport.saveGeneratedPipelineToTemp(
        pipelineMeta, variables, metadataProvider);
  }
}
