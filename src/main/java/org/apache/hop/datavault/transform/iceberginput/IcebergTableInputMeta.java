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
package org.apache.hop.datavault.transform.iceberginput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.iceberg.IcebergConnectionSettings;
import org.apache.hop.datavault.metadata.iceberg.IcebergTableReader;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "IcebergTableInput",
    image = "iceberg-logo.svg",
    name = "i18n::IcebergTableInput.Name",
    description = "i18n::IcebergTableInput.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "i18n::IcebergTableInput.keyword",
    documentationUrl = "/pipeline/transforms/icebergtableinput.html")
public class IcebergTableInputMeta
    extends BaseTransformMeta<IcebergTableInput, IcebergTableInputData> {

  private static final Class<?> PKG = IcebergTableInputMeta.class;

  @HopMetadataProperty private String catalogUri;

  @HopMetadataProperty private String warehouse;

  @HopMetadataProperty private String namespace;

  @HopMetadataProperty private String tableName;

  @HopMetadataProperty private String snapshotId;

  @HopMetadataProperty private String branch;

  @HopMetadataProperty private String s3Endpoint;

  @HopMetadataProperty private String s3AccessKey;

  @HopMetadataProperty private String s3SecretKey;

  @HopMetadataProperty(
      groupKey = "fields",
      key = "field",
      injectionGroupKey = "FIELDS",
      injectionKey = "FIELD")
  private List<IcebergTableInputField> fields = new ArrayList<>();

  @Override
  public IcebergTableInputMeta clone() {
    IcebergTableInputMeta meta = new IcebergTableInputMeta();
    meta.catalogUri = catalogUri;
    meta.warehouse = warehouse;
    meta.namespace = namespace;
    meta.tableName = tableName;
    meta.snapshotId = snapshotId;
    meta.branch = branch;
    meta.s3Endpoint = s3Endpoint;
    meta.s3AccessKey = s3AccessKey;
    meta.s3SecretKey = s3SecretKey;
    for (IcebergTableInputField field : fields) {
      meta.fields.add(new IcebergTableInputField(field));
    }
    return meta;
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
      if (!fields.isEmpty()) {
        for (IcebergTableInputField field : fields) {
          if (field == null || Utils.isEmpty(field.getName())) {
            continue;
          }
          int hopType = resolveHopType(field);
          IValueMeta valueMeta =
              ValueMetaFactory.createValueMeta(variables.resolve(field.getName()), hopType);
          valueMeta.setOrigin(name);
          if (!Utils.isEmpty(field.getLength())) {
            valueMeta.setLength(Integer.parseInt(variables.resolve(field.getLength())));
          }
          if (!Utils.isEmpty(field.getPrecision())) {
            valueMeta.setPrecision(Integer.parseInt(variables.resolve(field.getPrecision())));
          }
          rowMeta.addValueMeta(valueMeta);
        }
        return;
      }

      IcebergConnectionSettings settings = IcebergConnectionSettings.from(this, variables);
      try (IcebergTableReader reader = new IcebergTableReader(settings, List.of())) {
        rowMeta.addRowMeta(reader.getOutputRowMeta());
      }
    } catch (Exception e) {
      throw new HopTransformException(
          BaseMessages.getString(PKG, "IcebergTableInputMeta.Error.GetFields"), e);
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
    try {
      IcebergConnectionSettings.from(this, variables).validate();
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "IcebergTableInputMeta.CheckResult.ConnectionOK"),
              transformMeta));
    } catch (IllegalArgumentException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
    }
  }

  private static int resolveHopType(IcebergTableInputField field) {
    if (field.getHopType() > 0) {
      return field.getHopType();
    }
    return IValueMeta.TYPE_STRING;
  }

  public String getCatalogUri() {
    return catalogUri;
  }

  public void setCatalogUri(String catalogUri) {
    this.catalogUri = catalogUri;
  }

  public String getWarehouse() {
    return warehouse;
  }

  public void setWarehouse(String warehouse) {
    this.warehouse = warehouse;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(String snapshotId) {
    this.snapshotId = snapshotId;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getS3Endpoint() {
    return s3Endpoint;
  }

  public void setS3Endpoint(String s3Endpoint) {
    this.s3Endpoint = s3Endpoint;
  }

  public String getS3AccessKey() {
    return s3AccessKey;
  }

  public void setS3AccessKey(String s3AccessKey) {
    this.s3AccessKey = s3AccessKey;
  }

  public String getS3SecretKey() {
    return s3SecretKey;
  }

  public void setS3SecretKey(String s3SecretKey) {
    this.s3SecretKey = s3SecretKey;
  }

  public List<IcebergTableInputField> getFields() {
    return fields;
  }

  public void setFields(List<IcebergTableInputField> fields) {
    this.fields = fields;
  }
}
