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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class RecordDefinitionInput
    extends BaseTransform<RecordDefinitionInputMeta, RecordDefinitionInputData> {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public RecordDefinitionInput(
      TransformMeta transformMeta,
      RecordDefinitionInputMeta meta,
      RecordDefinitionInputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null && meta.isSelectFromInput()) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;

      if (meta.isSelectFromInput()) {
        data.outputRowMeta = getInputRowMeta().clone();
        meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);

        String namespaceField = resolve(meta.getNamespaceField());
        String nameField = resolve(meta.getNameField());

        data.namespaceFieldIndex = getInputRowMeta().indexOfValue(namespaceField);
        data.nameFieldIndex = getInputRowMeta().indexOfValue(nameField);

        if (data.namespaceFieldIndex < 0) {
          throw new HopException("Cannot find namespace field: " + namespaceField);
        }
        if (data.nameFieldIndex < 0) {
          throw new HopException("Cannot find name field: " + nameField);
        }
      } else {
        data.outputRowMeta = new RowMeta();
        meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);

        String resolvedConnectionName = resolve(meta.getCatalogConnectionName());
        if (Utils.isEmpty(resolvedConnectionName)) {
          throw new HopException("Data catalog connection name is not configured.");
        }

        List<RecordDefinition> definitions = new ArrayList<>();
        String resolvedNamespace = resolve(meta.getNamespaceValue());
        String resolvedName = resolve(meta.getNameValue());

        if (Utils.isEmpty(resolvedNamespace) && Utils.isEmpty(resolvedName)) {
          List<RecordDefinitionRef> allRefs =
              RecordDefinitionRegistry.getInstance()
                  .listAll(new RecordDefinitionQuery(), this, metadataProvider);
          for (RecordDefinitionRef ref : allRefs) {
            if (resolvedConnectionName.equalsIgnoreCase(ref.getCatalogConnectionName())) {
              RecordDefinition def =
                  RecordDefinitionRegistry.getInstance()
                      .read(resolvedConnectionName, ref.getKey(), this, metadataProvider);
              if (def != null) {
                definitions.add(def);
              }
            }
          }
        } else {
          RecordDefinition def =
              RecordDefinitionRegistry.getInstance()
                  .read(
                      resolvedConnectionName,
                      new RecordDefinitionKey(resolvedNamespace, resolvedName),
                      this,
                      metadataProvider);
          if (def != null) {
            definitions.add(def);
          }
        }
        data.definitionsToOutput = definitions;
      }
    }

    if (meta.isSelectFromInput()) {
      String namespace = getInputRowMeta().getString(row, data.namespaceFieldIndex);
      String name = getInputRowMeta().getString(row, data.nameFieldIndex);
      String resolvedConnectionName = resolve(meta.getCatalogConnectionName());

      RecordDefinition definition = null;
      if (!Utils.isEmpty(namespace) && !Utils.isEmpty(name)) {
        definition =
            RecordDefinitionRegistry.getInstance()
                .read(
                    resolvedConnectionName,
                    new RecordDefinitionKey(namespace, name),
                    this,
                    metadataProvider);
      }

      int startIdx = getInputRowMeta().size();
      if (definition != null) {
        emitRecordRows(row, startIdx, definition);
      } else {
        // Output a row with nulls if definition not found
        Object[] outputRow = RowDataUtil.createResizedCopy(row, data.outputRowMeta.size());
        for (int i = startIdx; i < data.outputRowMeta.size(); i++) {
          outputRow[i] = null;
        }
        putRow(data.outputRowMeta, outputRow);
      }
      return true;
    } else {
      if (data.definitionsToOutput == null
          || data.currentDefIndex >= data.definitionsToOutput.size()) {
        setOutputDone();
        return false;
      }

      RecordDefinition definition = data.definitionsToOutput.get(data.currentDefIndex);
      Object[] baseRow = RowDataUtil.allocateRowData(data.outputRowMeta.size());
      emitRecordRows(baseRow, 0, definition);

      data.currentDefIndex++;
      return true;
    }
  }

  private void emitRecordRows(Object[] baseRow, int startIdx, RecordDefinition definition)
      throws HopException {
    if (meta.isOutputFieldsMetadata()) {
      IRowMeta fieldsRowMeta = definition.getFields();
      if (fieldsRowMeta != null && fieldsRowMeta.size() > 0) {
        for (int i = 0; i < fieldsRowMeta.size(); i++) {
          IValueMeta valueMeta = fieldsRowMeta.getValueMeta(i);
          Object[] outputRow = RowDataUtil.createResizedCopy(baseRow, data.outputRowMeta.size());
          populateRecordFields(outputRow, startIdx, definition);

          int fIdx = startIdx + RecordDefinitionInputMeta.FIELD_METADATA_START_OFFSET;
          outputRow[fIdx] = valueMeta.getName();
          outputRow[fIdx + 1] = valueMeta.getTypeDesc();
          outputRow[fIdx + 2] = (long) valueMeta.getLength();
          outputRow[fIdx + 3] = (long) valueMeta.getPrecision();
          outputRow[fIdx + 4] = (long) primaryKeyPositionForField(definition, valueMeta.getName());

          putRow(data.outputRowMeta, outputRow);
        }
        return;
      }
    }

    Object[] outputRow = RowDataUtil.createResizedCopy(baseRow, data.outputRowMeta.size());
    populateRecordFields(outputRow, startIdx, definition);

    if (meta.isOutputFieldsMetadata()) {
      int fIdx = startIdx + RecordDefinitionInputMeta.FIELD_METADATA_START_OFFSET;
      outputRow[fIdx] = null;
      outputRow[fIdx + 1] = null;
      outputRow[fIdx + 2] = null;
      outputRow[fIdx + 3] = null;
      outputRow[fIdx + 4] = null;
    }

    putRow(data.outputRowMeta, outputRow);
  }

  private void populateRecordFields(Object[] outputRow, int startIdx, RecordDefinition definition) {
    outputRow[startIdx] = definition.getKey() != null ? definition.getKey().getNamespace() : null;
    outputRow[startIdx + 1] = definition.getKey() != null ? definition.getKey().getName() : null;
    outputRow[startIdx + 2] = definition.getType() != null ? definition.getType().name() : null;
    outputRow[startIdx + 3] = definition.getDescription();

    RecordOrigin origin = definition.getOrigin();
    if (origin != null) {
      outputRow[startIdx + 4] = origin.getModelType();
      outputRow[startIdx + 5] = origin.getModelName();
      outputRow[startIdx + 6] = origin.getModelFilename();
      outputRow[startIdx + 7] = origin.getModelElementName();
      outputRow[startIdx + 8] = origin.getHopProject();
      outputRow[startIdx + 9] =
          origin.getCreatedAt() != null ? formatDate(origin.getCreatedAt()) : null;
      outputRow[startIdx + 10] =
          origin.getUpdatedAt() != null ? formatDate(origin.getUpdatedAt()) : null;
      outputRow[startIdx + 11] = origin.getUpdatedBy();
      outputRow[startIdx + 12] = origin.getLastWorkflow();
      outputRow[startIdx + 13] = origin.getLastPipeline();
    } else {
      for (int i = 4; i <= 13; i++) {
        outputRow[startIdx + i] = null;
      }
    }

    PhysicalTableRef physicalTable = definition.getPhysicalTable();
    if (physicalTable != null) {
      outputRow[startIdx + 14] = physicalTable.getDatabaseMetaName();
      outputRow[startIdx + 15] = physicalTable.getSchemaName();
      outputRow[startIdx + 16] = physicalTable.getTableName();
    } else {
      outputRow[startIdx + 14] = null;
      outputRow[startIdx + 15] = null;
      outputRow[startIdx + 16] = null;
    }

    DvSourceRecord dvSource = definition.getDvSource();
    if (dvSource != null) {
      outputRow[startIdx + 17] = dvSource.getSourceType();
      outputRow[startIdx + 18] = dvSource.getSourceIndicator();
      outputRow[startIdx + 19] = dvSource.getSourceIndicatorField();
      outputRow[startIdx + 20] = dvSource.getGroup();
      outputRow[startIdx + 21] = dvSource.getDeliveryType();
    } else {
      outputRow[startIdx + 17] = null;
      outputRow[startIdx + 18] = null;
      outputRow[startIdx + 19] = null;
      outputRow[startIdx + 20] = null;
      outputRow[startIdx + 21] = null;
    }
  }

  private int primaryKeyPositionForField(RecordDefinition definition, String fieldName) {
    DvSourceRecord dvSource = definition.getDvSource();
    if (dvSource == null || dvSource.getFields() == null || Utils.isEmpty(fieldName)) {
      return 0;
    }
    for (CatalogSourceField field : dvSource.getFields()) {
      if (field != null && fieldName.equals(field.getName())) {
        return field.getPrimaryKeyPosition();
      }
    }
    return 0;
  }

  private String formatDate(Date date) {
    if (date == null) {
      return null;
    }
    synchronized (DATE_FORMAT) {
      return DATE_FORMAT.format(date);
    }
  }
}
