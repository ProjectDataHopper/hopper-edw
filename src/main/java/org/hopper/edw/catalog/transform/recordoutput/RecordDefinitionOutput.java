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
package org.hopper.edw.catalog.transform.recordoutput;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.discovery.PhysicalSourceRef;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.hopper.edw.catalog.discovery.RecordDefinitionDiscoveryService;
import org.hopper.edw.catalog.discovery.RecordDefinitionWriteRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
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
    if (meta.isFieldsFromInput()) {
      return processFieldsFromInput();
    }

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

  private boolean processFieldsFromInput() throws HopException {
    Object[] row = getRow();

    if (first) {
      first = false;
      if (getInputRowMeta() == null && row == null) {
        setOutputDone();
        return false;
      }
      IRowMeta inputMeta = getInputRowMeta();
      if (inputMeta == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingInputFields"));
      }
      data.outputRowMeta = inputMeta.clone();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
      data.statusFieldStartIndex = inputMeta.size();
      resolveInputFieldIndexes();
      resolveStreamFieldIndexes();
    }

    if (row == null) {
      flushFieldGroup();
      setOutputDone();
      return false;
    }

    String groupValue =
        RecordDefinitionOutputFieldSupport.groupingValue(
            getInputRowMeta(), row, data.fieldGroupingFieldIndex);

    if (data.hasOpenGroup
        && RecordDefinitionOutputFieldSupport.groupChanged(data.currentGroupValue, groupValue)) {
      flushFieldGroup();
    }

    if (!data.hasOpenGroup) {
      data.hasOpenGroup = true;
      data.currentGroupValue = groupValue;
      data.currentGroupBaseRow = row;
      data.currentFields.clear();
    }

    SourceField field =
        RecordDefinitionOutputFieldSupport.sourceFieldFromRow(
            getInputRowMeta(),
            row,
            data.fieldNameFieldIndex,
            data.fieldTypeFieldIndex,
            data.fieldLengthFieldIndex,
            data.fieldPrecisionFieldIndex,
            data.fieldPrimaryKeyPositionFieldIndex,
            data.fieldFormatFieldIndex,
            data.fieldDecimalFieldIndex,
            data.fieldGroupingSymbolFieldIndex);
    data.currentFields.add(field);
    return true;
  }

  private void flushFieldGroup() throws HopException {
    if (!data.hasOpenGroup) {
      return;
    }

    List<SourceField> fields = new ArrayList<>(data.currentFields);
    Object[] baseRow = data.currentGroupBaseRow;
    data.hasOpenGroup = false;
    data.currentGroupBaseRow = null;
    data.currentGroupValue = null;
    data.currentFields.clear();

    writeDefinition(baseRow, fields, data.statusFieldStartIndex, null);
  }

  private void processDiscovery(Object[] baseRow, int statusStartIdx) throws HopException {
    PhysicalSourceRef physicalRef = buildPhysicalRef(baseRow);
    RecordDefinitionDiscoveryService.DiscoveryResult discovery =
        RecordDefinitionDiscoveryService.discover(
            meta.getSourceType(), physicalRef, this, metadataProvider);

    List<SourceField> fields = discovery.fields();
    writeDefinition(baseRow, fields, statusStartIdx, discovery);
  }

  private void writeDefinition(
      Object[] baseRow,
      List<SourceField> fields,
      int statusStartIdx,
      RecordDefinitionDiscoveryService.DiscoveryResult discovery)
      throws HopException {
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

    int fieldCount = fields != null ? fields.size() : 0;
    if (fieldCount == 0 && meta.isFailIfNoFields()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.NoFieldsDiscovered"));
    }

    PhysicalSourceRef physicalRef = buildPhysicalRef(baseRow);
    DvSourceDeliveryType deliveryType = resolveDeliveryType(baseRow);

    boolean written = false;
    if (meta.isWriteToCatalog() && fieldCount > 0) {
      RecordDefinitionWriteRequest.Builder requestBuilder =
          RecordDefinitionWriteRequest.builder()
              .catalogConnectionName(catalogConnection)
              .namespace(namespace)
              .name(name)
              .description(description)
              .recordType(meta.getRecordDefinitionType())
              .sourceType(meta.getSourceType())
              .physicalRef(physicalRef)
              .fields(fields)
              .sourceIndicator(meta.getSourceIndicator())
              .sourceIndicatorField(meta.getSourceIndicatorField())
              .group(meta.getGroup())
              .deliveryType(deliveryType)
              .updatedAt(new Date())
              .pipelineName(getPipelineMeta() != null ? getPipelineMeta().getName() : null);
      if (discovery != null) {
        requestBuilder.csvDiscovery(discovery.csvDiscovery());
      }
      RecordDefinitionCatalogWriter.upsert(requestBuilder.build(), this, metadataProvider);
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

  private DvSourceDeliveryType resolveDeliveryType(Object[] baseRow) throws HopException {
    String streamValue = null;
    if (baseRow != null
        && data.deliveryTypeFieldIndex >= 0
        && getInputRowMeta() != null
        && data.deliveryTypeFieldIndex < getInputRowMeta().size()) {
      streamValue = getInputRowMeta().getString(baseRow, data.deliveryTypeFieldIndex);
    }
    return RecordDefinitionOutputFieldSupport.resolveDeliveryType(
        streamValue, meta.getDeliveryType());
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
    if (!Utils.isEmpty(fieldName) && row != null && getInputRowMeta() != null) {
      int index = getInputRowMeta().indexOfValue(resolve(fieldName));
      if (index >= 0) {
        return getInputRowMeta().getString(row, index);
      }
    }
    return resolve(fixedValue);
  }

  private String resolvePhysicalValue(String fixedValue, String fieldName, Object[] row)
      throws HopException {
    // Prefer stream values when a field is mapped (including first row of a field group).
    if (!Utils.isEmpty(fieldName) && row != null && getInputRowMeta() != null) {
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
    data.deliveryTypeFieldIndex = indexOf(inputRowMeta, meta.getDeliveryTypeField());
  }

  private void resolveStreamFieldIndexes() throws HopException {
    IRowMeta inputRowMeta = getInputRowMeta();
    data.fieldGroupingFieldIndex = indexOf(inputRowMeta, meta.getFieldGroupingField());
    data.fieldNameFieldIndex = indexOf(inputRowMeta, meta.getFieldNameField());
    data.fieldTypeFieldIndex = indexOf(inputRowMeta, meta.getFieldTypeField());
    data.fieldLengthFieldIndex = indexOf(inputRowMeta, meta.getFieldLengthField());
    data.fieldPrecisionFieldIndex = indexOf(inputRowMeta, meta.getFieldPrecisionField());
    data.fieldPrimaryKeyPositionFieldIndex =
        indexOf(inputRowMeta, meta.getFieldPrimaryKeyPositionField());
    data.fieldFormatFieldIndex = indexOf(inputRowMeta, meta.getFieldFormatField());
    data.fieldDecimalFieldIndex = indexOf(inputRowMeta, meta.getFieldDecimalField());
    data.fieldGroupingSymbolFieldIndex = indexOf(inputRowMeta, meta.getFieldGroupingSymbolField());

    if (data.fieldNameFieldIndex < 0) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionOutput.Error.FieldNameFieldNotFound",
              resolve(meta.getFieldNameField())));
    }
    if (data.fieldTypeFieldIndex < 0 && !Utils.isEmpty(meta.getFieldTypeField())) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionOutput.Error.FieldTypeFieldNotFound",
              resolve(meta.getFieldTypeField())));
    }
    if (data.fieldTypeFieldIndex < 0) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingFieldTypeMapping"));
    }
  }

  private int indexOf(IRowMeta rowMeta, String fieldName) {
    if (Utils.isEmpty(fieldName)) {
      return -1;
    }
    return rowMeta.indexOfValue(resolve(fieldName));
  }
}
