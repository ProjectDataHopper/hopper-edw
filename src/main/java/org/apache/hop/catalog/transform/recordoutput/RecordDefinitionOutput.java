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

import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.discovery.PhysicalSourceRef;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.catalog.discovery.RecordDefinitionDiscoveryService;
import org.apache.hop.catalog.discovery.RecordDefinitionWriteRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class RecordDefinitionOutput
    extends BaseTransform<RecordDefinitionOutputMeta, RecordDefinitionOutputData> {

  private static final Class<?> PKG = RecordDefinitionOutputMeta.class;

  public RecordDefinitionOutput(
      TransformMeta transformMeta,
      RecordDefinitionOutputMeta meta,
      RecordDefinitionOutputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();

    if (!meta.isSelectFromInput()) {
      if (first) {
        first = false;
        data.outputRowMeta = new RowMeta();
        meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
        data.statusFieldStartIndex = 0;
      }
      if (data.fixedConfigProcessed) {
        setOutputDone();
        return false;
      }
      data.fixedConfigProcessed = true;
      processDiscovery(null, 0);
      return true;
    }

    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      data.outputRowMeta = getInputRowMeta().clone();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
      data.statusFieldStartIndex = getInputRowMeta().size();
      resolveInputFieldIndexes();
    }

    processDiscovery(row, data.statusFieldStartIndex);
    return true;
  }

  private void processDiscovery(Object[] baseRow, int statusStartIdx) throws HopException {
    String namespace =
        resolveDefinitionValue(meta.getNamespaceValue(), meta.getNamespaceField(), baseRow);
    String name = resolveDefinitionValue(meta.getNameValue(), meta.getNameField(), baseRow);
    String description =
        resolveDefinitionValue(meta.getDescriptionValue(), meta.getDescriptionField(), baseRow);

    if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingDefinitionKey"));
    }

    String catalogConnection = resolve(meta.getCatalogConnectionName());
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingCatalogConnection"));
    }

    PhysicalSourceRef physicalRef = buildPhysicalRef(baseRow);
    RecordDefinitionDiscoveryService.DiscoveryResult discovery =
        RecordDefinitionDiscoveryService.discover(
            meta.getSourceType(), physicalRef, this, metadataProvider);

    List<SourceField> fields = discovery.fields();
    int fieldCount = fields != null ? fields.size() : 0;
    if (fieldCount == 0 && meta.isFailIfNoFields()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.NoFieldsDiscovered"));
    }

    boolean written = false;
    if (meta.isWriteToCatalog() && fieldCount > 0) {
      RecordDefinitionWriteRequest request =
          RecordDefinitionWriteRequest.builder()
              .catalogConnectionName(catalogConnection)
              .namespace(namespace)
              .name(name)
              .description(description)
              .recordType(meta.getRecordDefinitionType())
              .sourceType(meta.getSourceType())
              .physicalRef(physicalRef)
              .fields(fields)
              .csvDiscovery(discovery.csvDiscovery())
              .sourceIndicator(meta.getSourceIndicator())
              .sourceIndicatorField(meta.getSourceIndicatorField())
              .group(meta.getGroup())
              .deliveryType(meta.getDeliveryType())
              .updatedAt(new Date())
              .pipelineName(getPipelineMeta() != null ? getPipelineMeta().getName() : null)
              .build();
      RecordDefinitionCatalogWriter.upsert(request, this, metadataProvider);
      written = true;
    }

    Object[] outputRow;
    if (baseRow == null) {
      outputRow = RowDataUtil.allocateRowData(data.outputRowMeta.size());
    } else {
      outputRow = RowDataUtil.createResizedCopy(baseRow, data.outputRowMeta.size());
    }

    outputRow[statusStartIdx] = (long) fieldCount;
    outputRow[statusStartIdx + 1] = written;
    outputRow[statusStartIdx + 2] = namespace;
    outputRow[statusStartIdx + 3] = name;
    putRow(data.outputRowMeta, outputRow);
  }

  private PhysicalSourceRef buildPhysicalRef(Object[] row) throws HopException {
    PhysicalSourceRef.Builder builder = PhysicalSourceRef.builder();
    switch (meta.getSourceType()) {
      case DATABASE -> {
        builder.databaseConnectionName(
            resolvePhysicalValue(
                meta.getDatabaseConnectionName(), meta.getDatabaseConnectionField(), row));
        builder.schemaName(resolvePhysicalValue(meta.getSchemaName(), meta.getSchemaField(), row));
        builder.tableName(resolvePhysicalValue(meta.getTableName(), meta.getTableField(), row));
      }
      case CSV, PARQUET -> {
        builder.filePath(resolvePhysicalValue(meta.getFilePath(), meta.getFilePathField(), row));
        builder.folder(resolvePhysicalValue(meta.getFolder(), meta.getFolderField(), row));
        builder.includeFileMask(
            resolvePhysicalValue(meta.getIncludeFileMask(), meta.getIncludeFileMaskField(), row));
        builder.excludeFileMask(resolve(meta.getExcludeFileMask()));
        builder.includeSubfolders(meta.isIncludeSubfolders());
      }
      case ICEBERG -> {
        builder.catalogUri(
            resolvePhysicalValue(
                meta.getIcebergCatalogUri(), meta.getIcebergCatalogUriField(), row));
        builder.warehouse(
            resolvePhysicalValue(meta.getIcebergWarehouse(), meta.getIcebergWarehouseField(), row));
        builder.icebergNamespace(
            resolvePhysicalValue(meta.getIcebergNamespace(), meta.getIcebergNamespaceField(), row));
        builder.icebergTableName(
            resolvePhysicalValue(meta.getIcebergTableName(), meta.getIcebergTableNameField(), row));
        builder.snapshotId(
            resolvePhysicalValue(
                meta.getIcebergSnapshotId(), meta.getIcebergSnapshotIdField(), row));
        builder.branch(
            resolvePhysicalValue(meta.getIcebergBranch(), meta.getIcebergBranchField(), row));
        builder.s3Endpoint(
            resolvePhysicalValue(
                meta.getIcebergS3Endpoint(), meta.getIcebergS3EndpointField(), row));
        builder.s3AccessKey(
            resolvePhysicalValue(
                meta.getIcebergS3AccessKey(), meta.getIcebergS3AccessKeyField(), row));
        builder.s3SecretKey(
            resolvePhysicalValue(
                meta.getIcebergS3SecretKey(), meta.getIcebergS3SecretKeyField(), row));
      }
      default ->
          throw new HopException(
              BaseMessages.getString(
                  PKG, "RecordDefinitionOutput.Error.UnsupportedSourceType", meta.getSourceType()));
    }
    return builder.build();
  }

  private String resolveDefinitionValue(String fixedValue, String fieldName, Object[] row)
      throws HopException {
    if (meta.isSelectFromInput() && !Utils.isEmpty(fieldName) && row != null) {
      int index = getInputRowMeta().indexOfValue(resolve(fieldName));
      if (index >= 0) {
        return getInputRowMeta().getString(row, index);
      }
    }
    return resolve(fixedValue);
  }

  private String resolvePhysicalValue(String fixedValue, String fieldName, Object[] row)
      throws HopException {
    if (meta.isSelectFromInput() && !Utils.isEmpty(fieldName) && row != null) {
      int index = getInputRowMeta().indexOfValue(resolve(fieldName));
      if (index >= 0) {
        String value = getInputRowMeta().getString(row, index);
        if (!Utils.isEmpty(value)) {
          return resolve(value);
        }
      }
    }
    return resolve(fixedValue);
  }

  private void resolveInputFieldIndexes() throws HopException {
    IRowMeta inputRowMeta = getInputRowMeta();
    data.namespaceFieldIndex = indexOf(inputRowMeta, meta.getNamespaceField());
    data.nameFieldIndex = indexOf(inputRowMeta, meta.getNameField());
    data.descriptionFieldIndex = indexOf(inputRowMeta, meta.getDescriptionField());
    data.databaseConnectionFieldIndex = indexOf(inputRowMeta, meta.getDatabaseConnectionField());
    data.schemaFieldIndex = indexOf(inputRowMeta, meta.getSchemaField());
    data.tableFieldIndex = indexOf(inputRowMeta, meta.getTableField());
    data.filePathFieldIndex = indexOf(inputRowMeta, meta.getFilePathField());
    data.folderFieldIndex = indexOf(inputRowMeta, meta.getFolderField());
    data.includeFileMaskFieldIndex = indexOf(inputRowMeta, meta.getIncludeFileMaskField());
  }

  private int indexOf(IRowMeta rowMeta, String fieldName) {
    if (Utils.isEmpty(fieldName)) {
      return -1;
    }
    return rowMeta.indexOfValue(resolve(fieldName));
  }
}
