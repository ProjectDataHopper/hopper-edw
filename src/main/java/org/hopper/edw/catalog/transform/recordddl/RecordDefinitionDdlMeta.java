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
package org.hopper.edw.catalog.transform.recordddl;

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
    id = "RecordDefinitionDdl",
    image = "data-catalog.svg",
    name = "Record Definition DDL",
    description =
        "Creates or updates database tables from catalog record definitions, including primary keys",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Utility",
    keywords = "record,definition,catalog,ddl,table,create")
@Getter
@Setter
public class RecordDefinitionDdlMeta
    extends BaseTransformMeta<RecordDefinitionDdl, RecordDefinitionDdlData> {

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

  @HopMetadataProperty(key = "override_connection")
  private String overrideConnectionName;

  @HopMetadataProperty(key = "override_schema")
  private String overrideSchemaName;

  @HopMetadataProperty(key = "override_table")
  private String overrideTableName;

  @HopMetadataProperty(key = "target_type_mapping")
  private String targetTypeMappingName;

  @HopMetadataProperty(key = "execute_ddl")
  private boolean executeDdl = true;

  @HopMetadataProperty(key = "drop_table_if_exists")
  private boolean dropTableIfExists;

  @HopMetadataProperty(key = "skip_if_table_exists")
  private boolean skipIfTableExists;

  @HopMetadataProperty(key = "append_semicolon")
  private boolean appendSemicolon = true;

  /**
   * When true (default), fail the transform if the catalog record definition has no fields to put
   * in CREATE TABLE. Prevents silent empty-table creation when field metadata was not loaded.
   */
  @HopMetadataProperty(key = "fail_if_no_fields")
  private boolean failIfNoFields = true;

  @HopMetadataProperty(key = "output_ddl_field")
  private String outputDdlField = "ddl";

  @HopMetadataProperty(key = "output_status_field")
  private String outputStatusField = "ddl_status";

  private static final String DEFAULT_OUTPUT_DDL_FIELD = "ddl";
  private static final String DEFAULT_OUTPUT_STATUS_FIELD = "ddl_status";

  public RecordDefinitionDdlMeta() {}

  @Override
  public RecordDefinitionDdlMeta clone() {
    RecordDefinitionDdlMeta meta = new RecordDefinitionDdlMeta();
    meta.catalogConnectionName = catalogConnectionName;
    meta.selectFromInput = selectFromInput;
    meta.namespaceField = namespaceField;
    meta.nameField = nameField;
    meta.namespaceValue = namespaceValue;
    meta.nameValue = nameValue;
    meta.overrideConnectionName = overrideConnectionName;
    meta.overrideSchemaName = overrideSchemaName;
    meta.overrideTableName = overrideTableName;
    meta.targetTypeMappingName = targetTypeMappingName;
    meta.executeDdl = executeDdl;
    meta.dropTableIfExists = dropTableIfExists;
    meta.skipIfTableExists = skipIfTableExists;
    meta.appendSemicolon = appendSemicolon;
    meta.failIfNoFields = failIfNoFields;
    meta.outputDdlField = outputDdlField;
    meta.outputStatusField = outputStatusField;
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
    if (inputRowMeta == null) {
      return;
    }
    if (selectFromInput) {
      for (int i = 0; i < inputRowMeta.size(); i++) {
        inputRowMeta.getValueMeta(i).setOrigin(name);
      }
    }
    addField(inputRowMeta, resolveOutputDdlField(variables), IValueMeta.TYPE_STRING, name);
    addField(inputRowMeta, resolveOutputStatusField(variables), IValueMeta.TYPE_STRING, name);
  }

  public String resolveOutputDdlField(IVariables variables) {
    return resolveOutputFieldName(variables, outputDdlField, DEFAULT_OUTPUT_DDL_FIELD);
  }

  public String resolveOutputStatusField(IVariables variables) {
    return resolveOutputFieldName(variables, outputStatusField, DEFAULT_OUTPUT_STATUS_FIELD);
  }

  private static String resolveOutputFieldName(
      IVariables variables, String configuredName, String defaultName) {
    String resolved = variables != null ? variables.resolve(configuredName) : configuredName;
    return Utils.isEmpty(resolved) ? defaultName : resolved;
  }

  private void addField(IRowMeta rowMeta, String fieldName, int type, String origin) {
    if (!Utils.isEmpty(fieldName)) {
      try {
        IValueMeta valueMeta = ValueMetaFactory.createValueMeta(fieldName, type);
        valueMeta.setOrigin(origin);
        rowMeta.addValueMeta(valueMeta);
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
    org.hopper.edw.catalog.discovery.CatalogDiscoverySupport.addCheckRemarks(
        remarks,
        transformMeta,
        variables != null ? variables.resolve(catalogConnectionName) : catalogConnectionName,
        variables != null ? variables.resolve(namespaceValue) : namespaceValue,
        variables != null ? variables.resolve(nameValue) : nameValue,
        selectFromInput,
        false,
        variables,
        metadataProvider);

    if (selectFromInput) {
      if (prev == null || prev.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Selection is set to read from input fields, but no input fields were found.",
                transformMeta));
      } else if (Utils.isEmpty(namespaceField) || Utils.isEmpty(nameField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                "Input field names for namespace or name are not fully configured.",
                transformMeta));
      }
    }
  }
}
