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
package org.hopper.edw.datavault.transform.sqlexpression;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.expression.SqlExpressionException;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;

@Getter
@Setter
@Transform(
    id = "SqlExpression",
    image = "sql-expression.svg",
    name = "i18n::SqlExpression.Name",
    description = "i18n::SqlExpression.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Scripting",
    keywords = "i18n::SqlExpression.keyword",
    documentationUrl = "/pipeline/transforms/sqlexpression.html")
public class SqlExpressionMeta extends BaseTransformMeta<SqlExpression, SqlExpressionData> {

  private static final Class<?> PKG = SqlExpressionMeta.class;

  @HopMetadataProperty(
      key = "keep_input_fields",
      injectionKey = "KEEP_INPUT_FIELDS",
      injectionKeyDescription = "SqlExpressionMeta.Injection.KEEP_INPUT_FIELDS")
  private boolean keepInputFields = true;

  @HopMetadataProperty(
      key = "business_vault_model_filename",
      injectionKey = "BUSINESS_VAULT_MODEL_FILENAME",
      injectionKeyDescription = "SqlExpressionMeta.Injection.BUSINESS_VAULT_MODEL_FILENAME")
  private String businessVaultModelFilename;

  @HopMetadataProperty(
      key = "scd2_table_name",
      injectionKey = "SCD2_TABLE_NAME",
      injectionKeyDescription = "SqlExpressionMeta.Injection.SCD2_TABLE_NAME")
  private String scd2TableName;

  @HopMetadataProperty(
      key = "field",
      groupKey = "fields",
      injectionKey = "FIELD",
      injectionGroupKey = "FIELDS",
      injectionKeyDescription = "SqlExpressionMeta.Injection.FIELD")
  private List<SqlExpressionField> fields = new ArrayList<>();

  public SqlExpressionMeta() {}

  public SqlExpressionMeta(SqlExpressionMeta other) {
    if (other == null) {
      return;
    }
    this.keepInputFields = other.keepInputFields;
    this.businessVaultModelFilename = other.businessVaultModelFilename;
    this.scd2TableName = other.scd2TableName;
    for (SqlExpressionField field : other.getFields()) {
      this.fields.add(new SqlExpressionField(field));
    }
  }

  @Override
  public SqlExpressionMeta clone() {
    return new SqlExpressionMeta(this);
  }

  public List<SqlExpressionField> getFields() {
    if (fields == null) {
      fields = new ArrayList<>();
    }
    return fields;
  }

  public List<SqlExpressionSpec> toSpecs() {
    List<SqlExpressionSpec> specs = new ArrayList<>();
    for (SqlExpressionField field : getFields()) {
      if (field == null) {
        continue;
      }
      specs.add(field.toSpec());
    }
    return specs;
  }

  /**
   * When a Business Vault model file and SCD2 table are both set, load calculations from that
   * table. Otherwise use the inline expression list.
   */
  public List<SqlExpressionSpec> resolveSpecs(
      IVariables variables, IHopMetadataProvider metadataProvider) throws HopException {
    return SqlExpressionBvTableSupport.resolveSpecs(this, variables, metadataProvider);
  }

  public boolean usesBusinessVaultTable(IVariables variables) {
    return SqlExpressionBvTableSupport.isBound(this, variables);
  }

  @Override
  public void getFields(
      IRowMeta inputRowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    if (inputRowMeta == null) {
      return;
    }
    List<SqlExpressionSpec> specs;
    try {
      specs = resolveSpecs(variables, metadataProvider);
    } catch (HopException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
    try {
      SqlExpressionProgram program =
          SqlExpressionProgram.compile(specs, inputRowMeta, variables, keepInputFields);
      IRowMeta output = program.getOutputRowMeta();
      inputRowMeta.clear();
      for (int i = 0; i < output.size(); i++) {
        IValueMeta valueMeta = output.getValueMeta(i).clone();
        valueMeta.setOrigin(name);
        inputRowMeta.addValueMeta(valueMeta);
      }
    } catch (SqlExpressionException e) {
      applyFallbackFields(inputRowMeta, specs, name);
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
    List<SqlExpressionSpec> specs;
    try {
      specs = resolveSpecs(variables, metadataProvider);
    } catch (HopException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
      specs = List.of();
    }
    if (specs.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "SqlExpressionMeta.CheckResult.NoExpressions"),
              transformMeta));
    } else if (prev != null && !prev.isEmpty()) {
      try {
        SqlExpressionProgram.compile(specs, prev, variables, keepInputFields);
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                usesBusinessVaultTable(variables)
                    ? BaseMessages.getString(
                        PKG,
                        "SqlExpressionMeta.CheckResult.BoundOk",
                        variables != null ? variables.resolve(scd2TableName) : scd2TableName)
                    : BaseMessages.getString(PKG, "SqlExpressionMeta.CheckResult.ExpressionsOk"),
                transformMeta));
      } catch (SqlExpressionException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
      }
    }
    if (input == null || input.length == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SqlExpressionMeta.CheckResult.NoInput"),
              transformMeta));
    }
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  private void applyFallbackFields(
      IRowMeta inputRowMeta, List<SqlExpressionSpec> specs, String origin) {
    if (!keepInputFields) {
      inputRowMeta.clear();
    }
    if (specs == null || specs.isEmpty()) {
      for (SqlExpressionField field : getFields()) {
        if (field != null) {
          addFallbackField(
              inputRowMeta,
              field.getFieldName(),
              field.getHopTypeName(),
              field.getLength(),
              field.getPrecision(),
              origin);
        }
      }
      return;
    }
    for (SqlExpressionSpec spec : specs) {
      if (spec != null) {
        addFallbackField(
            inputRowMeta,
            spec.getFieldName(),
            spec.getHopTypeName(),
            spec.getLength(),
            spec.getPrecision(),
            origin);
      }
    }
  }

  private static void addFallbackField(
      IRowMeta inputRowMeta,
      String fieldName,
      String hopTypeName,
      int length,
      int precision,
      String origin) {
    if (Utils.isEmpty(fieldName)) {
      return;
    }
    try {
      int hopType =
          Utils.isEmpty(hopTypeName)
              ? IValueMeta.TYPE_STRING
              : ValueMetaFactory.getIdForValueMeta(hopTypeName);
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(fieldName, hopType);
      if (length > 0) {
        valueMeta.setLength(length, precision);
      }
      valueMeta.setOrigin(origin);
      int existing = inputRowMeta.indexOfValue(fieldName);
      if (existing >= 0) {
        inputRowMeta.setValueMeta(existing, valueMeta);
      } else {
        inputRowMeta.addValueMeta(valueMeta);
      }
    } catch (Exception ignored) {
      // Leave the input layout when type construction fails.
    }
  }
}
