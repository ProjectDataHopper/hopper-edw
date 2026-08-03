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
package org.apache.hop.catalog.transform.recordinput;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "RecordDefinitionInput",
    image = "data-catalog.svg",
    name = "i18n::RecordDefinitionInput.Name",
    description = "i18n::RecordDefinitionInput.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "i18n::RecordDefinitionInput.keyword")
@Getter
@Setter
public class RecordDefinitionInputMeta
    extends BaseTransformMeta<RecordDefinitionInput, RecordDefinitionInputData> {

  private static final Class<?> PKG = RecordDefinitionInputMeta.class;

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

  @HopMetadataProperty(key = "output_fields_metadata")
  private boolean outputFieldsMetadata;

  @HopMetadataProperty(key = "output_namespace_field")
  private String outputNamespaceField = "namespace";

  @HopMetadataProperty(key = "output_name_field")
  private String outputNameField = "name";

  @HopMetadataProperty(key = "output_type_field")
  private String outputTypeField = "type";

  @HopMetadataProperty(key = "output_description_field")
  private String outputDescriptionField = "description";

  @HopMetadataProperty(key = "output_model_type_field")
  private String outputModelTypeField = "origin_model_type";

  @HopMetadataProperty(key = "output_model_name_field")
  private String outputModelNameField = "origin_model_name";

  @HopMetadataProperty(key = "output_model_filename_field")
  private String outputModelFilenameField = "origin_model_filename";

  @HopMetadataProperty(key = "output_model_element_field")
  private String outputModelElementField = "origin_model_element";

  @HopMetadataProperty(key = "output_hop_project_field")
  private String outputHopProjectField = "origin_hop_project";

  @HopMetadataProperty(key = "output_created_at_field")
  private String outputCreatedAtField = "origin_created_at";

  @HopMetadataProperty(key = "output_updated_at_field")
  private String outputUpdatedAtField = "origin_updated_at";

  @HopMetadataProperty(key = "output_updated_by_field")
  private String outputUpdatedByField = "origin_updated_by";

  @HopMetadataProperty(key = "output_last_workflow_field")
  private String outputLastWorkflowField = "origin_last_workflow";

  @HopMetadataProperty(key = "output_last_pipeline_field")
  private String outputLastPipelineField = "origin_last_pipeline";

  @HopMetadataProperty(key = "output_physical_database_field")
  private String outputPhysicalDatabaseField = "physical_database";

  @HopMetadataProperty(key = "output_physical_schema_field")
  private String outputPhysicalSchemaField = "physical_schema";

  @HopMetadataProperty(key = "output_physical_table_field")
  private String outputPhysicalTableField = "physical_table";

  @HopMetadataProperty(key = "output_dv_source_type_field")
  private String outputDvSourceTypeField = "dv_source_type";

  @HopMetadataProperty(key = "output_dv_source_indicator_field")
  private String outputDvSourceIndicatorField = "dv_source_indicator";

  @HopMetadataProperty(key = "output_dv_source_indicator_field_field")
  private String outputDvSourceIndicatorFieldField = "dv_source_indicator_field";

  @HopMetadataProperty(key = "output_dv_source_group_field")
  private String outputDvSourceGroupField = "dv_source_group";

  @HopMetadataProperty(key = "output_dv_delivery_type_field")
  private String outputDvDeliveryTypeField = "dv_source_delivery_type";

  @HopMetadataProperty(key = "output_field_name_field")
  private String outputFieldNameField = "field_name";

  @HopMetadataProperty(key = "output_field_type_field")
  private String outputFieldTypeField = "field_type";

  @HopMetadataProperty(key = "output_field_length_field")
  private String outputFieldLengthField = "field_length";

  @HopMetadataProperty(key = "output_field_precision_field")
  private String outputFieldPrecisionField = "field_precision";

  @HopMetadataProperty(key = "output_field_primary_key_position_field")
  private String outputFieldPrimaryKeyPositionField = "field_primary_key_position";

  /** Number of record-level output columns before denormalized field metadata. */
  public static final int FIELD_METADATA_START_OFFSET = 22;

  public RecordDefinitionInputMeta() {}

  @Override
  public RecordDefinitionInputMeta clone() {
    RecordDefinitionInputMeta meta = new RecordDefinitionInputMeta();
    meta.catalogConnectionName = catalogConnectionName;
    meta.selectFromInput = selectFromInput;
    meta.namespaceField = namespaceField;
    meta.nameField = nameField;
    meta.namespaceValue = namespaceValue;
    meta.nameValue = nameValue;
    meta.outputFieldsMetadata = outputFieldsMetadata;
    meta.outputNamespaceField = outputNamespaceField;
    meta.outputNameField = outputNameField;
    meta.outputTypeField = outputTypeField;
    meta.outputDescriptionField = outputDescriptionField;
    meta.outputModelTypeField = outputModelTypeField;
    meta.outputModelNameField = outputModelNameField;
    meta.outputModelFilenameField = outputModelFilenameField;
    meta.outputModelElementField = outputModelElementField;
    meta.outputHopProjectField = outputHopProjectField;
    meta.outputCreatedAtField = outputCreatedAtField;
    meta.outputUpdatedAtField = outputUpdatedAtField;
    meta.outputUpdatedByField = outputUpdatedByField;
    meta.outputLastWorkflowField = outputLastWorkflowField;
    meta.outputLastPipelineField = outputLastPipelineField;
    meta.outputPhysicalDatabaseField = outputPhysicalDatabaseField;
    meta.outputPhysicalSchemaField = outputPhysicalSchemaField;
    meta.outputPhysicalTableField = outputPhysicalTableField;
    meta.outputDvSourceTypeField = outputDvSourceTypeField;
    meta.outputDvSourceIndicatorField = outputDvSourceIndicatorField;
    meta.outputDvSourceIndicatorFieldField = outputDvSourceIndicatorFieldField;
    meta.outputDvSourceGroupField = outputDvSourceGroupField;
    meta.outputDvDeliveryTypeField = outputDvDeliveryTypeField;
    meta.outputFieldNameField = outputFieldNameField;
    meta.outputFieldTypeField = outputFieldTypeField;
    meta.outputFieldLengthField = outputFieldLengthField;
    meta.outputFieldPrecisionField = outputFieldPrecisionField;
    meta.outputFieldPrimaryKeyPositionField = outputFieldPrimaryKeyPositionField;
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

    addField(inputRowMeta, variables.resolve(outputNamespaceField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputNameField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputTypeField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputDescriptionField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputModelTypeField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputModelNameField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputModelFilenameField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputModelElementField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputHopProjectField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputCreatedAtField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputUpdatedAtField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputUpdatedByField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputLastWorkflowField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputLastPipelineField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputPhysicalDatabaseField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputPhysicalSchemaField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputPhysicalTableField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputDvSourceTypeField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta,
        variables.resolve(outputDvSourceIndicatorField),
        IValueMeta.TYPE_STRING,
        name);
    addField(
        inputRowMeta,
        variables.resolve(outputDvSourceIndicatorFieldField),
        IValueMeta.TYPE_STRING,
        name);
    addField(
        inputRowMeta, variables.resolve(outputDvSourceGroupField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputDvDeliveryTypeField), IValueMeta.TYPE_STRING, name);

    if (outputFieldsMetadata) {
      addField(inputRowMeta, variables.resolve(outputFieldNameField), IValueMeta.TYPE_STRING, name);
      addField(inputRowMeta, variables.resolve(outputFieldTypeField), IValueMeta.TYPE_STRING, name);
      addField(
          inputRowMeta, variables.resolve(outputFieldLengthField), IValueMeta.TYPE_INTEGER, name);
      addField(
          inputRowMeta,
          variables.resolve(outputFieldPrecisionField),
          IValueMeta.TYPE_INTEGER,
          name);
      addField(
          inputRowMeta,
          variables.resolve(outputFieldPrimaryKeyPositionField),
          IValueMeta.TYPE_INTEGER,
          name);
    }
  }

  private void addField(IRowMeta rowMeta, String fieldName, int type, String origin) {
    if (!Utils.isEmpty(fieldName)) {
      try {
        IValueMeta vm = ValueMetaFactory.createValueMeta(fieldName, type);
        vm.setOrigin(origin);
        rowMeta.addValueMeta(vm);
      } catch (Exception e) {
        // Ignore creation error
      }
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
              "Catalog connection name is missing.",
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              "Catalog connection name is configured.",
              transformMeta));
    }

    if (selectFromInput) {
      if (prev == null || prev.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Selection is set to read from input fields, but no input fields were found.",
                transformMeta));
      } else {
        if (Utils.isEmpty(namespaceField) || Utils.isEmpty(nameField)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  "Input field names for namespace or name are not fully configured.",
                  transformMeta));
        } else {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  "Input fields selection configured.",
                  transformMeta));
        }
      }
    }
  }
}
