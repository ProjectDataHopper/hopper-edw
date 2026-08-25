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
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.catalog.ddl.CatalogTableDdlSupport;
import org.hopper.edw.catalog.ddl.CatalogTableDdlSupport.DdlResult;
import org.hopper.edw.catalog.ddl.CatalogTableDdlSupport.ResolvedTarget;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogLoadSupport;
import org.hopper.edw.catalog.discovery.RecordDefinitionLoadResult;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingContext;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingSupport;

public class RecordDefinitionDdl
    extends BaseTransform<RecordDefinitionDdlMeta, RecordDefinitionDdlData> {

  public RecordDefinitionDdl(
      TransformMeta transformMeta,
      RecordDefinitionDdlMeta meta,
      RecordDefinitionDdlData data,
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
      initializeProcessing();
    }

    if (meta.isSelectFromInput()) {
      String namespace = getInputRowMeta().getString(row, data.namespaceFieldIndex);
      String name = getInputRowMeta().getString(row, data.nameFieldIndex);
      emitResult(row, namespace, name);
      return true;
    }

    if (data.definitionsToProcess == null
        || data.currentDefinitionIndex >= data.definitionsToProcess.size()) {
      setOutputDone();
      return false;
    }

    RecordDefinition definition = data.definitionsToProcess.get(data.currentDefinitionIndex++);
    String namespace = definition.getKey() != null ? definition.getKey().getNamespace() : null;
    String recordName = definition.getKey() != null ? definition.getKey().getName() : null;
    emitResult(null, namespace, recordName);
    return true;
  }

  private void initializeProcessing() throws HopException {
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
    }

    String ddlField = meta.resolveOutputDdlField(this);
    String statusField = meta.resolveOutputStatusField(this);
    data.ddlFieldIndex = data.outputRowMeta.indexOfValue(ddlField);
    data.statusFieldIndex = data.outputRowMeta.indexOfValue(statusField);
    if (data.ddlFieldIndex < 0) {
      throw new HopException("Cannot find output DDL field: " + ddlField);
    }
    if (data.statusFieldIndex < 0) {
      throw new HopException("Cannot find output status field: " + statusField);
    }

    if (meta.isSelectFromInput()) {
      return;
    }

    RecordDefinitionLoadResult loadResult =
        RecordDefinitionCatalogLoadSupport.loadDefinitions(
            resolve(meta.getCatalogConnectionName()),
            resolve(meta.getNamespaceValue()),
            resolve(meta.getNameValue()),
            this,
            metadataProvider);
    RecordDefinitionCatalogLoadSupport.emitLogs(getLogChannel(), loadResult);
    data.definitionsToProcess = loadResult.getDefinitions();
  }

  private void emitResult(Object[] inputRow, String namespace, String recordName)
      throws HopException {
    Object[] outputRow = buildOutputRow(inputRow);
    int ddlIndex = data.ddlFieldIndex;
    int statusIndex = data.statusFieldIndex;

    if (Utils.isEmpty(namespace) || Utils.isEmpty(recordName)) {
      outputRow[ddlIndex] = null;
      outputRow[statusIndex] = CatalogTableDdlSupport.DdlStatus.ERROR.name();
      putRow(data.outputRowMeta, outputRow);
      return;
    }

    String catalogConnectionName = resolve(meta.getCatalogConnectionName());
    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(
                catalogConnectionName,
                new RecordDefinitionKey(namespace, recordName),
                this,
                metadataProvider);
    if (definition == null) {
      outputRow[ddlIndex] = null;
      outputRow[statusIndex] = CatalogTableDdlSupport.DdlStatus.ERROR.name();
      putRow(data.outputRowMeta, outputRow);
      logError("Record definition not found: " + namespace + "/" + recordName);
      return;
    }

    try {
      ResolvedTarget target =
          CatalogTableDdlSupport.resolveTarget(
              definition,
              resolve(meta.getOverrideConnectionName()),
              resolve(meta.getOverrideSchemaName()),
              resolve(meta.getOverrideTableName()),
              this);
      if (Utils.isEmpty(target.connectionName())) {
        throw new HopException("No database connection could be resolved for " + recordName);
      }
      if (Utils.isEmpty(target.tableName())) {
        throw new HopException("No table name could be resolved for " + recordName);
      }

      DatabaseMeta databaseMeta =
          metadataProvider.getSerializer(DatabaseMeta.class).load(target.connectionName());
      if (databaseMeta == null) {
        throw new HopException(
            "Database connection '" + target.connectionName() + "' was not found.");
      }

      List<SourceField> sourceFields =
          CatalogTableDdlSupport.sourceFieldsFromDefinition(definition, this);
      if ((sourceFields == null || sourceFields.isEmpty()) && meta.isFailIfNoFields()) {
        throw new HopException(
            "No fields received for record definition "
                + namespace
                + "/"
                + recordName
                + " (table "
                + target.tableName()
                + "). Enable 'Output fields metadata' on Record Definition Input "
                + "or ensure the catalog record has fields. "
                + "Disable 'Fail if no fields received' on Record Definition DDL only if "
                + "empty-field definitions are intentional.");
      }

      TargetTypeMappingContext mappingContext =
          TargetTypeMappingSupport.resolve(
              resolve(meta.getTargetTypeMappingName()), databaseMeta, metadataProvider, this);
      DdlResult result =
          CatalogTableDdlSupport.applyTableDdl(
              databaseMeta,
              this,
              target.schemaName(),
              target.tableName(),
              sourceFields,
              meta.isDropTableIfExists(),
              meta.isExecuteDdl(),
              meta.isSkipIfTableExists(),
              meta.isAppendSemicolon(),
              this,
              mappingContext);

      outputRow[ddlIndex] = result.ddl();
      outputRow[statusIndex] = result.status().name();
      if (result.status() == CatalogTableDdlSupport.DdlStatus.ERROR) {
        logError(
            "DDL failed for "
                + target.tableName()
                + (result.message() != null ? ": " + result.message() : ""));
      }
    } catch (HopException e) {
      if (meta.isFailIfNoFields() && isNoFieldsError(e)) {
        throw e;
      }
      outputRow[ddlIndex] = null;
      outputRow[statusIndex] = CatalogTableDdlSupport.DdlStatus.ERROR.name();
      logError("DDL failed for " + recordName, e);
    } catch (Exception e) {
      outputRow[ddlIndex] = null;
      outputRow[statusIndex] = CatalogTableDdlSupport.DdlStatus.ERROR.name();
      logError("DDL failed for " + recordName, e);
    }

    putRow(data.outputRowMeta, outputRow);
  }

  private static boolean isNoFieldsError(HopException e) {
    if (e == null || e.getMessage() == null) {
      return false;
    }
    String message = e.getMessage();
    return message.contains("No fields received for record definition")
        || message.contains("At least one source field is required to generate DDL");
  }

  private Object[] buildOutputRow(Object[] inputRow) throws HopException {
    if (meta.isSelectFromInput()) {
      return RowDataUtil.createResizedCopy(inputRow, data.outputRowMeta.size());
    }
    return RowDataUtil.allocateRowData(data.outputRowMeta.size());
  }
}
