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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Interactive preview of a {@link SourceTable} (database tables). */
public final class SourceTablePreviewSupport {

  public static final int DEFAULT_ROW_LIMIT = 50;

  private SourceTablePreviewSupport() {}

  public static List<RowMetaAndData> preview(
      SourceModel model,
      SourceTable table,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (table == null) {
      throw new HopException("Source table is required for preview");
    }
    DvSourceType physical =
        table.getPhysicalType() != null ? table.getPhysicalType() : DvSourceType.DATABASE;
    if (physical != DvSourceType.DATABASE) {
      throw new HopException(
          "Preview is currently available for database source tables only (got " + physical + ")");
    }

    String connectionName = resolveConnectionName(model, table, variables);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          "No database connection is set for table '"
              + ConstNvl(table.getName())
              + "'. Choose a connection on the General tab.");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }

    String schema = table.getSchemaName() != null ? table.getSchemaName().trim() : "";
    String physicalName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName().trim() : table.getName();
    if (Utils.isEmpty(physicalName)) {
      throw new HopException("Physical table name is required for preview");
    }
    String qualified =
        Utils.isEmpty(schema)
            ? databaseMeta.quoteField(physicalName)
            : databaseMeta.getQuotedSchemaTableCombination(variables, schema, physicalName);
    String sql = "SELECT * FROM " + qualified;
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;

    SimpleLoggingObject logging =
        new SimpleLoggingObject("SourceTablePreview", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(logging, variables, databaseMeta)) {
      db.connect();
      if (limit > 0) {
        db.setQueryLimit(limit);
      }
      List<Object[]> rawRows = db.getRows(sql, limit);
      IRowMeta rowMeta = db.getReturnRowMeta();
      List<RowMetaAndData> rows = new ArrayList<>();
      if (rawRows != null && rowMeta != null) {
        for (Object[] raw : rawRows) {
          rows.add(new RowMetaAndData(rowMeta.clone(), raw));
        }
      }
      return rows;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Error previewing source table '" + ConstNvl(table.getName()) + "'", e);
    }
  }

  /**
   * Connection from the table, else model default. Dialog fields can override by mutating a working
   * copy of the table before calling {@link #preview}.
   */
  public static String resolveConnectionName(
      SourceModel model, SourceTable table, IVariables variables) {
    String connectionName = table != null ? ConstNvl(table.getDatabaseName()) : "";
    if (Utils.isEmpty(connectionName) && model != null) {
      connectionName = ConstNvl(model.getConfigurationOrDefault().getDefaultDatabase());
    }
    if (variables != null && !Utils.isEmpty(connectionName)) {
      connectionName = variables.resolve(connectionName);
    }
    return connectionName;
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
