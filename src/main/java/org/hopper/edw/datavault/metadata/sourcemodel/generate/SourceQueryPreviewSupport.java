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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

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
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;

/** Interactive preview of a source query (SQL mode and FREE_SQL). */
public final class SourceQueryPreviewSupport {

  public static final int DEFAULT_ROW_LIMIT = 50;

  private SourceQueryPreviewSupport() {}

  public static List<RowMetaAndData> preview(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    SourceQueryGenerationMode mode =
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query);
    if (mode == SourceQueryGenerationMode.FREE_SQL) {
      return org.hopper.edw.datavault.virtualization.execute.SourceModelSqlExecutor.preview(
          model, query.getFreeSql(), variables, metadataProvider, rowLimit);
    }
    if (mode != SourceQueryGenerationMode.SQL) {
      throw new HopException(
          "Preview is currently available for single-connection SQL queries and Free SQL. "
              + "This query resolves to pipeline generation mode.");
    }
    String connectionName = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("No database connection available for preview");
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(variables.resolve(connectionName));
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }
    String sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables);
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;

    SimpleLoggingObject logging =
        new SimpleLoggingObject("SourceQueryPreview", LoggingObjectType.GENERAL, null);
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
      throw new HopException("Error previewing source query", e);
    }
  }

  /**
   * Opens the query in the Hop Database Perspective SQL workbench editor with a limit of 1000 rows.
   */
  public static void openInDatabaseWorkbench(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (query == null) {
      throw new HopException("Source query is required for preview");
    }
    SourceQueryGenerationMode mode =
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query);
    if (mode != SourceQueryGenerationMode.SQL && mode != SourceQueryGenerationMode.FREE_SQL) {
      throw new HopException(
          "Preview in Database Workbench is currently available for single-connection SQL queries and Free SQL. "
              + "This query resolves to pipeline generation mode.");
    }
    if (mode == SourceQueryGenerationMode.FREE_SQL
        && !SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)) {
      throw new HopException(
          "Preview in Database Workbench requires all tables in the query to be on the same database connection.");
    }
    String connectionName = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("No database connection available for preview");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }
    String sql;
    if (mode == SourceQueryGenerationMode.FREE_SQL) {
      sql = org.apache.hop.core.Const.NVL(query.getFreeSql(), "").trim();
      if (Utils.isEmpty(sql)) {
        throw new HopException("Free SQL is empty");
      }
    } else {
      sql = SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables);
    }

    String limitedSql =
        org.hopper.edw.datavault.metadata.database.DvDatabaseSourcePreviewSupport.applyRowLimit(
            databaseMeta, sql, 1000);
    org.apache.hop.ui.hopgui.perspective.database.DatabaseWorkbenchDialog.openSql(
        databaseMeta, limitedSql);
  }
}
