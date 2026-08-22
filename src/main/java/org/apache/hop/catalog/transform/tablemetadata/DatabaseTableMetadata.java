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
package org.apache.hop.catalog.transform.tablemetadata;

import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class DatabaseTableMetadata
    extends BaseTransform<DatabaseTableMetadataMeta, DatabaseTableMetadataData> {

  private static final Class<?> PKG = DatabaseTableMetadataMeta.class;

  public DatabaseTableMetadata(
      TransformMeta transformMeta,
      DatabaseTableMetadataMeta meta,
      DatabaseTableMetadataData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.pendingColumns.isEmpty()) {
      emitNextPendingColumn();
      return true;
    }

    if (!meta.isSelectFromInput()) {
      if (first) {
        first = false;
        data.outputRowMeta = new RowMeta();
        meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
        data.statusFieldStartIndex = 0;
      }
      if (data.fixedConfigProcessed) {
        closeDatabaseQuietly();
        setOutputDone();
        return false;
      }
      data.fixedConfigProcessed = true;
      String connectionName = resolve(meta.getConnectionName());
      String schema = resolve(meta.getSchemaName());
      String table = resolve(meta.getTableName());
      discoverAndQueue(null, connectionName, schema, table);
      return true;
    }

    Object[] row = getRow();
    if (row == null) {
      closeDatabaseQuietly();
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

    String connectionName = resolveConnectionFromRow(row);
    String schema = resolveSchemaFromRow(row);
    String table = resolveTableFromRow(row);
    discoverAndQueue(row, connectionName, schema, table);
    return true;
  }

  private void discoverAndQueue(
      Object[] baseRow, String connectionName, String schema, String table) throws HopException {
    if (Utils.isEmpty(table)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseTableMetadata.Error.MissingTableName"));
    }
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseTableMetadata.Error.MissingConnection"));
    }

    ensureDatabase(connectionName);
    List<DatabaseTableMetadataColumn> columns =
        DatabaseTableMetadataSupport.discoverColumns(
            data.database, connectionName, schema, table, this, meta.isIncludeForeignKeys());
    if (columns.isEmpty()) {
      logBasic(
          BaseMessages.getString(
              PKG, "DatabaseTableMetadata.Log.NoFields", connectionName, schema, table));
      return;
    }
    data.pendingBaseRow = baseRow;
    data.pendingColumns.clear();
    data.pendingColumns.addAll(columns);
    emitNextPendingColumn();
  }

  private void emitNextPendingColumn() throws HopException {
    if (data.pendingColumns.isEmpty()) {
      return;
    }
    DatabaseTableMetadataColumn column = data.pendingColumns.remove(0);
    Object[] outputRow;
    if (data.pendingBaseRow == null) {
      outputRow = RowDataUtil.allocateRowData(data.outputRowMeta.size());
    } else {
      outputRow = RowDataUtil.createResizedCopy(data.pendingBaseRow, data.outputRowMeta.size());
    }
    int i = data.statusFieldStartIndex;
    outputRow[i++] = column.getDatabaseConnection();
    outputRow[i++] = column.getSchemaName();
    outputRow[i++] = column.getTableName();
    outputRow[i++] = (long) column.getFieldPosition();
    outputRow[i++] = column.getFieldName();
    outputRow[i++] = column.getFieldType();
    outputRow[i++] = column.getFieldLength();
    outputRow[i++] = column.getFieldPrecision();
    outputRow[i++] = column.getPrimaryKeyPosition();
    outputRow[i++] = column.getSourceDataType();
    if (meta.isIncludeForeignKeys()) {
      outputRow[i++] = column.getFkConstraintName();
      outputRow[i++] = column.getFkPosition();
      outputRow[i++] = column.getFkReferencedSchema();
      outputRow[i++] = column.getFkReferencedTable();
      outputRow[i] = column.getFkReferencedColumn();
    }
    putRow(data.outputRowMeta, outputRow);
    incrementLinesOutput();
  }

  private void ensureDatabase(String connectionName) throws HopException {
    if (data.database != null && connectionName.equals(data.openConnectionName)) {
      return;
    }
    closeDatabaseQuietly();
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DatabaseTableMetadata.Error.ConnectionNotFound", connectionName));
    }
    data.database = new Database(this, this, databaseMeta);
    data.database.connect();
    data.openConnectionName = connectionName;
  }

  private void closeDatabaseQuietly() {
    if (data.database != null) {
      try {
        data.database.disconnect();
      } catch (Exception ignored) {
        // Best effort
      }
      data.database = null;
      data.openConnectionName = null;
    }
  }

  private void resolveInputFieldIndexes() {
    IRowMeta input = getInputRowMeta();
    data.connectionFieldIndex = indexOf(input, meta.getConnectionField());
    data.schemaFieldIndex = indexOf(input, meta.getSchemaField());
    data.tableFieldIndex = indexOf(input, meta.getTableField());
  }

  private int indexOf(IRowMeta rowMeta, String fieldName) {
    if (Utils.isEmpty(fieldName) || rowMeta == null) {
      return -1;
    }
    return rowMeta.indexOfValue(resolve(fieldName));
  }

  private String resolveConnectionFromRow(Object[] row) throws HopException {
    if (data.connectionFieldIndex >= 0) {
      String value = getInputRowMeta().getString(row, data.connectionFieldIndex);
      if (!Utils.isEmpty(value)) {
        return resolve(value);
      }
    }
    return resolve(meta.getConnectionName());
  }

  private String resolveSchemaFromRow(Object[] row) throws HopException {
    if (data.schemaFieldIndex >= 0) {
      String value = getInputRowMeta().getString(row, data.schemaFieldIndex);
      if (value != null) {
        return resolve(value);
      }
    }
    return resolve(meta.getSchemaName());
  }

  private String resolveTableFromRow(Object[] row) throws HopException {
    if (data.tableFieldIndex >= 0) {
      String value = getInputRowMeta().getString(row, data.tableFieldIndex);
      if (!Utils.isEmpty(value)) {
        return resolve(value);
      }
    }
    return resolve(meta.getTableName());
  }

  @Override
  public void dispose() {
    closeDatabaseQuietly();
    super.dispose();
  }
}
