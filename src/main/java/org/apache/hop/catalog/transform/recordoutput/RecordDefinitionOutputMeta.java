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
package org.apache.hop.catalog.transform.recordoutput;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "RecordDefinitionOutput",
    image = "data-catalog.svg",
    // Literal name — see RecordDefinitionInputMeta (plugin i18n not always applied to palette).
    name = "Catalog Record Definition Metadata Output",
    description =
        "Writes record definition metadata to a data catalog from physical discovery or stream fields",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Output",
    keywords = "record,definition,catalog,output,discover,migration,fields,metadata")
@Getter
@Setter
public class RecordDefinitionOutputMeta
    extends BaseTransformMeta<RecordDefinitionOutput, RecordDefinitionOutputData> {

  @HopMetadataProperty(key = "catalog_connection")
  private String catalogConnectionName;

  @HopMetadataProperty(key = "record_definition_type")
  private RecordDefinitionType recordDefinitionType = RecordDefinitionType.DV_SOURCE;

  @HopMetadataProperty(key = "select_from_input")
  private boolean selectFromInput;

  @HopMetadataProperty(key = "namespace_field")
  private String namespaceField;

  @HopMetadataProperty(key = "name_field")
  private String nameField;

  @HopMetadataProperty(key = "description_field")
  private String descriptionField;

  @HopMetadataProperty(key = "namespace_value")
  private String namespaceValue;

  @HopMetadataProperty(key = "name_value")
  private String nameValue;

  @HopMetadataProperty(key = "description_value")
  private String descriptionValue;

  @HopMetadataProperty(key = "write_to_catalog")
  private boolean writeToCatalog = true;

  @HopMetadataProperty(key = "fail_if_no_fields")
  private boolean failIfNoFields = true;

  @HopMetadataProperty(key = "source_type", storeWithCode = true)
  private DvSourceType sourceType = DvSourceType.CSV;

  @HopMetadataProperty(key = "database_connection")
  private String databaseConnectionName;

  @HopMetadataProperty(key = "schema_name")
  private String schemaName;

  @HopMetadataProperty(key = "table_name")
  private String tableName;

  @HopMetadataProperty(key = "database_connection_field")
  private String databaseConnectionField;

  @HopMetadataProperty(key = "schema_field")
  private String schemaField;

  @HopMetadataProperty(key = "table_field")
  private String tableField;

  @HopMetadataProperty(key = "file_path")
  private String filePath;

  @HopMetadataProperty(key = "folder")
  private String folder;

  @HopMetadataProperty(key = "include_file_mask")
  private String includeFileMask;

  @HopMetadataProperty(key = "exclude_file_mask")
  private String excludeFileMask;

  @HopMetadataProperty(key = "include_subfolders")
  private boolean includeSubfolders;

  @HopMetadataProperty(key = "file_path_field")
  private String filePathField;

  @HopMetadataProperty(key = "folder_field")
  private String folderField;

  @HopMetadataProperty(key = "include_file_mask_field")
  private String includeFileMaskField;

  @HopMetadataProperty(key = "iceberg_catalog_uri")
  private String icebergCatalogUri;

  @HopMetadataProperty(key = "iceberg_warehouse")
  private String icebergWarehouse;

  @HopMetadataProperty(key = "iceberg_namespace")
  private String icebergNamespace;

  @HopMetadataProperty(key = "iceberg_table_name")
  private String icebergTableName;

  @HopMetadataProperty(key = "iceberg_snapshot_id")
  private String icebergSnapshotId;

  @HopMetadataProperty(key = "iceberg_branch")
  private String icebergBranch;

  @HopMetadataProperty(key = "iceberg_s3_endpoint")
  private String icebergS3Endpoint;

  @HopMetadataProperty(key = "iceberg_s3_access_key")
  private String icebergS3AccessKey;

  @HopMetadataProperty(key = "iceberg_s3_secret_key")
  private String icebergS3SecretKey;

  @HopMetadataProperty(key = "iceberg_catalog_uri_field")
  private String icebergCatalogUriField;

  @HopMetadataProperty(key = "iceberg_warehouse_field")
  private String icebergWarehouseField;

  @HopMetadataProperty(key = "iceberg_namespace_field")
  private String icebergNamespaceField;

  @HopMetadataProperty(key = "iceberg_table_name_field")
  private String icebergTableNameField;

  @HopMetadataProperty(key = "iceberg_snapshot_id_field")
  private String icebergSnapshotIdField;

  @HopMetadataProperty(key = "iceberg_branch_field")
  private String icebergBranchField;

  @HopMetadataProperty(key = "iceberg_s3_endpoint_field")
  private String icebergS3EndpointField;

  @HopMetadataProperty(key = "iceberg_s3_access_key_field")
  private String icebergS3AccessKeyField;

  @HopMetadataProperty(key = "iceberg_s3_secret_key_field")
  private String icebergS3SecretKeyField;

  @HopMetadataProperty(key = "source_indicator")
  private String sourceIndicator;

  @HopMetadataProperty(key = "source_indicator_field")
  private String sourceIndicatorField;

  @HopMetadataProperty(key = "group")
  private String group;

  @HopMetadataProperty(key = "delivery_type", storeWithCode = true)
  private DvSourceDeliveryType deliveryType = DvSourceDeliveryType.CHANGES_ONLY;

  @HopMetadataProperty(key = "delivery_type_field")
  private String deliveryTypeField;

  /** When true, collect field definitions from the input stream instead of discovering them. */
  @HopMetadataProperty(key = "fields_from_input")
  private boolean fieldsFromInput;

  /**
   * Input field used as the consecutive-group break key for stream field rows. Empty means the
   * entire input stream is one record definition.
   */
  @HopMetadataProperty(key = "field_grouping_field")
  private String fieldGroupingField;

  @HopMetadataProperty(key = "field_name_field")
  private String fieldNameField;

  @HopMetadataProperty(key = "field_type_field")
  private String fieldTypeField;

  @HopMetadataProperty(key = "field_length_field")
  private String fieldLengthField;

  @HopMetadataProperty(key = "field_precision_field")
  private String fieldPrecisionField;

  @HopMetadataProperty(key = "field_primary_key_position_field")
  private String fieldPrimaryKeyPositionField;

  @HopMetadataProperty(key = "field_format_field")
  private String fieldFormatField;

  @HopMetadataProperty(key = "field_decimal_field")
  private String fieldDecimalField;

  @HopMetadataProperty(key = "field_grouping_symbol_field")
  private String fieldGroupingSymbolField;

  @HopMetadataProperty(key = "field_count_field")
  private String fieldCountField = "field_count";

  @HopMetadataProperty(key = "written_to_catalog_field")
  private String writtenToCatalogField = "written_to_catalog";

  @HopMetadataProperty(key = "catalog_namespace_field")
  private String catalogNamespaceField = "catalog_namespace";

  @HopMetadataProperty(key = "catalog_name_field")
  private String catalogNameField = "catalog_name";

  @Override
  public RecordDefinitionOutputMeta clone() {
    return (RecordDefinitionOutputMeta) super.clone();
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
    addField(inputRowMeta, variables.resolve(fieldCountField), IValueMeta.TYPE_INTEGER, name);
    addField(inputRowMeta, variables.resolve(writtenToCatalogField), IValueMeta.TYPE_BOOLEAN, name);
    addField(inputRowMeta, variables.resolve(catalogNamespaceField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(catalogNameField), IValueMeta.TYPE_STRING, name);
  }

  private void addField(IRowMeta rowMeta, String fieldName, int type, String origin) {
    if (!Utils.isEmpty(fieldName)) {
      try {
        IValueMeta vm = ValueMetaFactory.createValueMeta(fieldName, type);
        vm.setOrigin(origin);
        rowMeta.addValueMeta(vm);
      } catch (Exception ignored) {
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

    if (fieldsFromInput || selectFromInput) {
      if (prev == null || prev.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Selection is set to read from input fields, but no input fields were found.",
                transformMeta));
      }
    } else if (Utils.isEmpty(namespaceValue) || Utils.isEmpty(nameValue)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              "Namespace and name values are not fully configured.",
              transformMeta));
    }

    if (fieldsFromInput && Utils.isEmpty(fieldNameField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Field name field is required when using field definitions from the input stream.",
              transformMeta));
    }
  }
}
