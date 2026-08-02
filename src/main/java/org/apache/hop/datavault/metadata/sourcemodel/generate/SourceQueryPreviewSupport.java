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
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Interactive preview of a source query (SQL mode only in this PR). */
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
    if (mode != SourceQueryGenerationMode.SQL) {
      throw new HopException(
          "Preview is currently available for single-connection SQL queries only. "
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
}
