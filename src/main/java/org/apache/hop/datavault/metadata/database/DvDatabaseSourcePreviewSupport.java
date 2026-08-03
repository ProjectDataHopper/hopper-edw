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
package org.apache.hop.datavault.metadata.database;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Reads sample rows from a {@link DvDatabaseSource} table for interactive preview. */
public final class DvDatabaseSourcePreviewSupport {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvDatabaseSourcePreview", LoggingObjectType.GENERAL, null);

  private DvDatabaseSourcePreviewSupport() {}

  public static List<RowMetaAndData> previewRecords(
      DvDatabaseSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    String connectionName = Const.NVL(source.getDatabaseName(), "").trim();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("Please select a database connection before previewing source data.");
    }

    String tableName = Const.NVL(source.getTableName(), "").trim();
    if (Utils.isEmpty(tableName)) {
      throw new HopException(
          "Please specify a source table or view before previewing source data.");
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + connectionName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' was not found.");
    }

    String schemaName = Const.NVL(source.getSchemaName(), "");
    int effectiveLimit = Math.max(0, rowLimit);
    int effectiveTimeout = Math.max(0, queryTimeoutSeconds);

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      if (effectiveTimeout > 0) {
        db.setStatementQueryTimeoutSeconds(effectiveTimeout);
      }
      if (effectiveLimit > 0) {
        db.setQueryLimit(effectiveLimit);
      }

      List<Object[]> rows;
      IRowMeta rowMeta;
      String previewSql = buildPreviewSql(databaseMeta, variables, source);
      if (previewSql != null) {
        rows = db.getRows(previewSql, effectiveLimit);
        rowMeta = db.getReturnRowMeta();
      } else {
        String qualifiedTable =
            databaseMeta.getQuotedSchemaTableCombination(variables, schemaName, tableName);
        rows = db.getFirstRows(qualifiedTable, effectiveLimit, null);
        rowMeta = db.getReturnRowMeta();
      }

      return toRowMetaAndDataList(rowMeta, rows);
    } catch (HopDatabaseException e) {
      throw new HopException("Error reading preview rows from the source table.", e);
    }
  }

  public static String buildPreviewSql(
      DatabaseMeta databaseMeta, IVariables variables, DvDatabaseSource source) {
    List<SourceField> fields = source.getFields();
    if (fields == null || fields.isEmpty()) {
      return null;
    }

    List<String> quotedFields = new ArrayList<>();
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      quotedFields.add(databaseMeta.quoteField(variables.resolve(field.getName())));
    }
    if (quotedFields.isEmpty()) {
      return null;
    }

    String schemaTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, source.getSchemaName(), source.getTableName());
    return "SELECT " + String.join(", ", quotedFields) + " FROM " + schemaTable;
  }

  private static List<RowMetaAndData> toRowMetaAndDataList(IRowMeta rowMeta, List<Object[]> rows) {
    List<RowMetaAndData> result = new ArrayList<>();
    if (rowMeta == null || rows == null || rows.isEmpty()) {
      return result;
    }
    for (Object[] row : rows) {
      result.add(new RowMetaAndData(rowMeta, row));
    }
    return result;
  }
}
