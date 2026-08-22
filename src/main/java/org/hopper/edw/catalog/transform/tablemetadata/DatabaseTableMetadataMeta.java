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
package org.hopper.edw.catalog.transform.tablemetadata;

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
    id = "DatabaseTableMetadata",
    image = "data-catalog.svg",
    name = "Database Table Metadata",
    description =
        "Discovers table columns as Hop types with length, precision, primary key and foreign keys",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "database,table,metadata,catalog,fields,primary key,foreign key,hop type")
@Getter
@Setter
public class DatabaseTableMetadataMeta
    extends BaseTransformMeta<DatabaseTableMetadata, DatabaseTableMetadataData> {

  @HopMetadataProperty(key = "connection")
  private String connectionName;

  @HopMetadataProperty(key = "select_from_input")
  private boolean selectFromInput;

  @HopMetadataProperty(key = "connection_field")
  private String connectionField;

  @HopMetadataProperty(key = "schema_field")
  private String schemaField;

  @HopMetadataProperty(key = "table_field")
  private String tableField;

  @HopMetadataProperty(key = "schema_name")
  private String schemaName;

  @HopMetadataProperty(key = "table_name")
  private String tableName;

  @HopMetadataProperty(key = "include_foreign_keys")
  private boolean includeForeignKeys = true;

  @HopMetadataProperty(key = "output_database_connection_field")
  private String outputDatabaseConnectionField = "database_connection";

  @HopMetadataProperty(key = "output_schema_name_field")
  private String outputSchemaNameField = "schema_name";

  @HopMetadataProperty(key = "output_table_name_field")
  private String outputTableNameField = "table_name";

  @HopMetadataProperty(key = "output_field_position_field")
  private String outputFieldPositionField = "field_position";

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

  @HopMetadataProperty(key = "output_source_data_type_field")
  private String outputSourceDataTypeField = "source_data_type";

  @HopMetadataProperty(key = "output_fk_constraint_name_field")
  private String outputFkConstraintNameField = "fk_constraint_name";

  @HopMetadataProperty(key = "output_fk_position_field")
  private String outputFkPositionField = "fk_position";

  @HopMetadataProperty(key = "output_fk_referenced_schema_field")
  private String outputFkReferencedSchemaField = "fk_referenced_schema";

  @HopMetadataProperty(key = "output_fk_referenced_table_field")
  private String outputFkReferencedTableField = "fk_referenced_table";

  @HopMetadataProperty(key = "output_fk_referenced_column_field")
  private String outputFkReferencedColumnField = "fk_referenced_column";

  @Override
  public DatabaseTableMetadataMeta clone() {
    return (DatabaseTableMetadataMeta) super.clone();
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
    addField(
        inputRowMeta,
        variables.resolve(outputDatabaseConnectionField),
        IValueMeta.TYPE_STRING,
        name);
    addField(inputRowMeta, variables.resolve(outputSchemaNameField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputTableNameField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputFieldPositionField), IValueMeta.TYPE_INTEGER, name);
    addField(inputRowMeta, variables.resolve(outputFieldNameField), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, variables.resolve(outputFieldTypeField), IValueMeta.TYPE_STRING, name);
    addField(
        inputRowMeta, variables.resolve(outputFieldLengthField), IValueMeta.TYPE_INTEGER, name);
    addField(
        inputRowMeta, variables.resolve(outputFieldPrecisionField), IValueMeta.TYPE_INTEGER, name);
    addField(
        inputRowMeta,
        variables.resolve(outputFieldPrimaryKeyPositionField),
        IValueMeta.TYPE_INTEGER,
        name);
    addField(
        inputRowMeta, variables.resolve(outputSourceDataTypeField), IValueMeta.TYPE_STRING, name);
    if (includeForeignKeys) {
      addField(
          inputRowMeta,
          variables.resolve(outputFkConstraintNameField),
          IValueMeta.TYPE_STRING,
          name);
      addField(
          inputRowMeta, variables.resolve(outputFkPositionField), IValueMeta.TYPE_INTEGER, name);
      addField(
          inputRowMeta,
          variables.resolve(outputFkReferencedSchemaField),
          IValueMeta.TYPE_STRING,
          name);
      addField(
          inputRowMeta,
          variables.resolve(outputFkReferencedTableField),
          IValueMeta.TYPE_STRING,
          name);
      addField(
          inputRowMeta,
          variables.resolve(outputFkReferencedColumnField),
          IValueMeta.TYPE_STRING,
          name);
    }
  }

  private void addField(IRowMeta rowMeta, String fieldName, int type, String origin) {
    if (Utils.isEmpty(fieldName)) {
      return;
    }
    try {
      IValueMeta vm = ValueMetaFactory.createValueMeta(fieldName, type);
      vm.setOrigin(origin);
      rowMeta.addValueMeta(vm);
    } catch (Exception ignored) {
      // Ignore creation error
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
    if (selectFromInput) {
      if (prev == null || prev.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Table metadata is set to read from input, but no input fields were found.",
                transformMeta));
      }
      if (Utils.isEmpty(tableField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Table field is required when reading tables from input.",
                transformMeta));
      }
    } else if (Utils.isEmpty(tableName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Table name is required when not reading from input.",
              transformMeta));
    }
    if (Utils.isEmpty(connectionName) && Utils.isEmpty(connectionField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Database connection name (or connection field) is required.",
              transformMeta));
    }
  }
}
