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
package org.apache.hop.catalog.transform.recorddatainput;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
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
 * Reads actual data rows from a catalog record definition (same source path as catalog Preview
 * data). Plugin display name: Record Definition Input.
 */
@Transform(
    id = "RecordDefinitionDataInput",
    image = "data-catalog.svg",
    // Literal name — see RecordDefinitionInputMeta (plugin i18n not always applied to palette).
    name = "Record Definition Input",
    description =
        "Reads actual data rows from a catalog record definition (same path as catalog Preview data)",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "record,definition,catalog,input,data,source,preview")
@Getter
@Setter
public class RecordDefinitionDataInputMeta
    extends BaseTransformMeta<RecordDefinitionDataInput, RecordDefinitionDataInputData> {

  private static final Class<?> PKG = RecordDefinitionDataInputMeta.class;

  @HopMetadataProperty(key = "catalog_connection")
  private String catalogConnectionName;

  @HopMetadataProperty(key = "select_from_input")
  private boolean selectFromInput;

  @HopMetadataProperty(key = "namespace_field")
  private String namespaceField;

  @HopMetadataProperty(key = "name_field")
  private String nameField;

  @HopMetadataProperty(key = "namespace_value")
  private String namespaceValue;

  @HopMetadataProperty(key = "name_value")
  private String nameValue;

  /**
   * Optional row limit for the generated source pipeline (0 = no limit). Useful for design-time
   * sampling; production loads typically leave this at 0.
   */
  @HopMetadataProperty(key = "row_limit")
  private String rowLimit = "0";

  public RecordDefinitionDataInputMeta() {}

  @Override
  public RecordDefinitionDataInputMeta clone() {
    RecordDefinitionDataInputMeta meta = new RecordDefinitionDataInputMeta();
    meta.catalogConnectionName = catalogConnectionName;
    meta.selectFromInput = selectFromInput;
    meta.namespaceField = namespaceField;
    meta.nameField = nameField;
    meta.namespaceValue = namespaceValue;
    meta.nameValue = nameValue;
    meta.rowLimit = rowLimit;
    return meta;
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
    if (selectFromInput) {
      // Output shape is per input row's definition and cannot be fixed at design time.
      return;
    }
    if (metadataProvider == null
        || Utils.isEmpty(catalogConnectionName)
        || Utils.isEmpty(namespaceValue)
        || Utils.isEmpty(nameValue)) {
      return;
    }
    try {
      RecordDefinition definition =
          RecordDefinitionDataInputSupport.loadDefinition(
              catalogConnectionName, namespaceValue, nameValue, variables, metadataProvider);
      IRowMeta fields =
          RecordDefinitionDataInputSupport.resolveOutputRowMeta(definition, variables, name);
      inputRowMeta.addRowMeta(fields);
    } catch (Exception e) {
      // Design-time: leave empty when catalog/definition is unavailable.
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

    if (Utils.isEmpty(catalogConnectionName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.MissingCatalog"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.CatalogOk"),
              transformMeta));
    }

    if (selectFromInput) {
      if (prev == null || prev.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.NoInputFields"),
                transformMeta));
      } else if (Utils.isEmpty(namespaceField) || Utils.isEmpty(nameField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.InputFieldsPartial"),
                transformMeta));
      } else {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.InputFieldsOk"),
                transformMeta));
      }
    } else if (Utils.isEmpty(namespaceValue) || Utils.isEmpty(nameValue)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.MissingKeyValues"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "RecordDefinitionDataInput.Check.KeyValuesOk"),
              transformMeta));
    }
  }
}
