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

package org.apache.hop.datavault.metadata.composite;

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
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryPreviewSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Preview rows for a {@link DvCompositeSource} (SQL mode only). */
public final class DvCompositeSourcePreviewSupport {

  private DvCompositeSourcePreviewSupport() {}

  public static List<RowMetaAndData> previewRecords(
      DvCompositeSource composite,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    DvCompositeSourceResolver.ResolvedComposite resolved =
        DvCompositeSourceResolver.resolveForSql(composite, variables, metadataProvider);
    DatabaseMeta databaseMeta = resolved.databaseMeta();
    if (databaseMeta == null) {
      // Cached SQL without connection — cannot preview.
      throw new HopException(
          "Cannot preview composite source: database connection unknown (cached SQL only)");
    }
    int limit = rowLimit > 0 ? rowLimit : SourceQueryPreviewSupport.DEFAULT_ROW_LIMIT;
    SimpleLoggingObject logging =
        new SimpleLoggingObject("DvCompositeSourcePreview", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(logging, variables, databaseMeta)) {
      db.connect();
      if (queryTimeoutSeconds > 0) {
        db.setQueryLimit(limit);
      } else if (limit > 0) {
        db.setQueryLimit(limit);
      }
      List<Object[]> rawRows = db.getRows(resolved.sql(), limit);
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
      throw new HopException("Error previewing composite source", e);
    }
  }
}
