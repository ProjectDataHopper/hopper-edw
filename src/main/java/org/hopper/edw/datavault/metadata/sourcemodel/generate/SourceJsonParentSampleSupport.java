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
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Samples JSON document strings from a {@link SourceJson} parent (table / query / chained JSON).
 *
 * <p>Used by the Source JSON dialog for field discovery without requiring a full vault load.
 */
public final class SourceJsonParentSampleSupport {

  public static final int DEFAULT_SAMPLE_ROWS = 20;

  private SourceJsonParentSampleSupport() {}

  /**
   * Sample up to {@code rowLimit} non-empty JSON document strings from the parent of {@code
   * jsonSource}.
   */
  public static List<String> sampleJsonDocuments(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (model == null || jsonSource == null) {
      throw new HopException("Source model and JSON source are required for sampling");
    }
    if (Utils.isEmpty(jsonSource.getJsonFieldName())) {
      throw new HopException("JSON field name is required for sampling");
    }
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_SAMPLE_ROWS;
    SourceJsonParentKind kind = jsonSource.resolveParentSourceKind();
    String parentName = jsonSource.getParentSourceName();
    String jsonField = jsonSource.getJsonFieldName().trim();

    return switch (kind) {
      case TABLE ->
          sampleFromTable(model, parentName, jsonField, variables, metadataProvider, limit);
      case QUERY ->
          sampleFromQuery(model, parentName, jsonField, variables, metadataProvider, limit);
      case JSON ->
          sampleFromParentJson(model, parentName, jsonField, variables, metadataProvider, limit);
    };
  }

  private static List<String> sampleFromTable(
      SourceModel model,
      String tableName,
      String jsonField,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int limit)
      throws HopException {
    SourceTable table = model.findTable(tableName);
    if (table == null) {
      throw new HopException("Parent table '" + tableName + "' not found");
    }
    DvSourceType physical =
        table.getPhysicalType() != null ? table.getPhysicalType() : DvSourceType.DATABASE;
    if (physical != DvSourceType.DATABASE) {
      throw new HopException(
          "Parent table '"
              + tableName
              + "' has physical type "
              + physical
              + ". Source model tables currently support database parents for JSON sampling; "
              + "stage file/Kafka payloads into a database table or use a Source Query parent.");
    }
    String connectionName =
        !Utils.isEmpty(table.getDatabaseName())
            ? table.getDatabaseName()
            : model.getConfigurationOrDefault().getDefaultDatabase();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("No database connection for parent table '" + tableName + "'");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }
    String schema = table.getSchemaName() != null ? table.getSchemaName() : "";
    String physicalName = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : tableName;
    String qualified =
        Utils.isEmpty(schema)
            ? databaseMeta.quoteField(physicalName)
            : databaseMeta.getQuotedSchemaTableCombination(variables, schema, physicalName);
    String quotedField = databaseMeta.quoteField(jsonField);
    String sql =
        "SELECT " + quotedField + " FROM " + qualified + " WHERE " + quotedField + " IS NOT NULL";

    SimpleLoggingObject logging =
        new SimpleLoggingObject("SourceJsonParentSample", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(logging, variables, databaseMeta)) {
      db.connect();
      if (limit > 0) {
        db.setQueryLimit(limit);
      }
      List<Object[]> rawRows = db.getRows(sql, limit);
      List<String> docs = new ArrayList<>();
      if (rawRows != null) {
        for (Object[] raw : rawRows) {
          if (raw != null && raw.length > 0 && raw[0] != null) {
            String text = String.valueOf(raw[0]).trim();
            if (!text.isEmpty()) {
              docs.add(text);
              if (docs.size() >= limit) {
                break;
              }
            }
          }
        }
      }
      return docs;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error sampling JSON field from parent table '" + tableName + "'", e);
    }
  }

  private static List<String> sampleFromQuery(
      SourceModel model,
      String queryName,
      String jsonField,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int limit)
      throws HopException {
    SourceQuery query = model.findQuery(queryName);
    if (query == null) {
      throw new HopException("Parent query '" + queryName + "' not found");
    }
    SourceQueryGenerationMode mode =
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query);
    if (mode == SourceQueryGenerationMode.SQL) {
      List<RowMetaAndData> rows =
          SourceQueryPreviewSupport.preview(model, query, variables, metadataProvider, limit);
      return extractField(rows, jsonField, limit);
    }
    // Pipeline-mode query: generate parent + read field via limited pipeline preview is heavier;
    // fall back to generating the query pipeline and extracting via row meta after a limited run
    // is not available headless here — surface a clear message.
    throw new HopException(
        "Parent query '"
            + queryName
            + "' resolves to pipeline generation mode. Preview/sample for pipeline-mode query"
            + " parents is not supported yet; force SQL mode when all tables share one connection,"
            + " or paste sample JSON in the dialog.");
  }

  private static List<String> sampleFromParentJson(
      SourceModel model,
      String parentJsonName,
      String jsonField,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int limit)
      throws HopException {
    SourceJson parent = model.findJsonSource(parentJsonName);
    if (parent == null) {
      throw new HopException("Parent JSON source '" + parentJsonName + "' not found");
    }
    // Recursively sample the grandparent field that produced the nested JSON string, if possible.
    // Prefer direct sample of the parent's parent when the field is a projected JSON string.
    List<RowMetaAndData> rows =
        SourceJsonPreviewSupport.preview(model, parent, variables, metadataProvider, limit);
    return extractField(rows, jsonField, limit);
  }

  private static List<String> extractField(List<RowMetaAndData> rows, String fieldName, int limit)
      throws HopException {
    List<String> docs = new ArrayList<>();
    if (rows == null || rows.isEmpty()) {
      return docs;
    }
    int index = rows.get(0).getRowMeta().indexOfValue(fieldName);
    if (index < 0) {
      throw new HopException(
          "Field '"
              + fieldName
              + "' not found in parent sample rows. Available: "
              + rows.get(0).getRowMeta().toStringMeta());
    }
    for (RowMetaAndData row : rows) {
      if (row == null || row.getData() == null) {
        continue;
      }
      Object value = row.getData()[index];
      if (value == null) {
        continue;
      }
      IValueMeta meta = row.getRowMeta().getValueMeta(index);
      String text;
      try {
        text = meta != null ? meta.getString(value) : String.valueOf(value);
      } catch (Exception e) {
        text = String.valueOf(value);
      }
      if (text != null) {
        text = text.trim();
        if (!text.isEmpty()) {
          docs.add(text);
          if (docs.size() >= limit) {
            break;
          }
        }
      }
    }
    return docs;
  }
}
