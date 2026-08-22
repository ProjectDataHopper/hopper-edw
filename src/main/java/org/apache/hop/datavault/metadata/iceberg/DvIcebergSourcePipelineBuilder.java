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
package org.apache.hop.datavault.metadata.iceberg;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.file.DvFileSourcePipelineBuilder;
import org.apache.hop.datavault.transform.iceberginput.IcebergTableInputField;
import org.apache.hop.datavault.transform.iceberginput.IcebergTableInputMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Base pipeline builder for Iceberg Data Vault sources. */
public abstract class DvIcebergSourcePipelineBuilder extends DvFileSourcePipelineBuilder {

  protected DvIcebergSource icebergSource;

  protected DvIcebergSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    icebergSource = (DvIcebergSource) dvSource;
  }

  @Override
  protected String calculateTransformName(IDvSource dvSource) {
    String namespace = variables.resolve(icebergSource.getNamespace());
    String tableName = variables.resolve(icebergSource.getTableName());
    if (!Utils.isEmpty(namespace) && !Utils.isEmpty(tableName)) {
      return "source " + namespace + "." + tableName;
    }
    return "source " + recordSource.getName();
  }

  @Override
  protected TransformMeta createFileInput(
      String transformName, Point location, ColumnMapping mapping) throws HopException {
    IcebergTableInputMeta meta = new IcebergTableInputMeta();
    meta.setCatalogUri(icebergSource.getCatalogUri());
    meta.setWarehouse(icebergSource.getWarehouse());
    meta.setNamespace(icebergSource.getNamespace());
    meta.setTableName(icebergSource.getTableName());
    meta.setSnapshotId(icebergSource.getSnapshotId());
    meta.setBranch(icebergSource.getBranch());
    meta.setS3Endpoint(icebergSource.getS3Endpoint());
    meta.setS3AccessKey(icebergSource.getS3AccessKey());
    meta.setS3SecretKey(icebergSource.getS3SecretKey());
    meta.setFields(buildIcebergInputFields(mapping));

    TransformMeta transformMeta = new TransformMeta("IcebergTableInput", transformName, meta);
    transformMeta.setLocation(location.x, location.y);
    return transformMeta;
  }

  private List<IcebergTableInputField> buildIcebergInputFields(ColumnMapping mapping)
      throws HopException {
    List<IcebergTableInputField> fields = new ArrayList<>();
    Set<String> mappedSourceNames = mappedSourceNames(mapping);
    List<SourceField> catalogFields = icebergSource.getFields();
    if (catalogFields != null && !catalogFields.isEmpty()) {
      for (SourceField sourceField : catalogFields) {
        if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
          continue;
        }
        String name = variables.resolve(sourceField.getName());
        if (!mappedSourceNames.contains(name)) {
          continue;
        }
        fields.add(toIcebergField(sourceField, name));
      }
    } else {
      for (ColumnRename rename : mapping.renames()) {
        SourceField sourceField = findSourceField(rename.sourceName());
        if (sourceField != null) {
          fields.add(toIcebergField(sourceField, rename.sourceName()));
        } else {
          fields.add(new IcebergTableInputField(rename.sourceName(), IValueMeta.TYPE_STRING));
        }
      }
    }
    if (fields.isEmpty()) {
      throw new HopException(
          "Please define at least one mapped source column for Iceberg source "
              + recordSource.getName());
    }
    return fields;
  }

  private IcebergTableInputField toIcebergField(SourceField sourceField, String name) {
    int hopType = sourceField.getHopType() > 0 ? sourceField.getHopType() : IValueMeta.TYPE_STRING;
    IcebergTableInputField field = new IcebergTableInputField(name, hopType);
    if (sourceField.getLength() != null) {
      field.setLength(sourceField.getLength());
    }
    if (sourceField.getPrecision() != null) {
      field.setPrecision(sourceField.getPrecision());
    }
    return field;
  }
}
