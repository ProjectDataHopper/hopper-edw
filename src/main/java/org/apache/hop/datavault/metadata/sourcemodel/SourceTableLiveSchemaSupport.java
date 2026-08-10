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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceTablePreviewSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Live database column discovery and compare for source-model tables (Get columns / Show
 * differences).
 */
public final class SourceTableLiveSchemaSupport {

  public enum DiffKind {
    ONLY_IN_MODEL,
    ONLY_IN_LIVE,
    TYPE_CHANGED,
    LENGTH_CHANGED,
    PRECISION_CHANGED,
    PK_CHANGED
  }

  public record ColumnDiff(String columnName, DiffKind kind, String detail) {}

  private SourceTableLiveSchemaSupport() {}

  /**
   * Discovers columns (and PK positions) from the live physical table for the given source-model
   * table definition.
   */
  public static List<SourceColumn> discoverColumns(
      SourceModel model,
      SourceTable table,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (table == null) {
      throw new HopException("Source table is required");
    }
    if (metadataProvider == null) {
      throw new HopException("Metadata provider is required to load the database connection");
    }
    String connectionName =
        SourceTablePreviewSupport.resolveConnectionName(model, table, variables);
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          "No database connection is set for table '"
              + Const.NVL(table.getName(), "")
              + "'. Choose a connection on the General tab.");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }
    String schema =
        variables != null
            ? Const.NVL(variables.resolve(table.getSchemaName()), "")
            : Const.NVL(table.getSchemaName(), "");
    String physical =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName().trim() : table.getName();
    if (variables != null) {
      physical = variables.resolve(physical);
    }
    if (Utils.isEmpty(physical)) {
      throw new HopException("Physical table name is required to retrieve columns");
    }

    SimpleLoggingObject logging =
        new SimpleLoggingObject("SourceTableLiveSchema", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(logging, variables, databaseMeta)) {
      db.connect();
      List<SourceField> fields =
          DvDatabaseSourceImportSupport.importFieldsFromTable(db, variables, schema, physical);
      return toSourceColumns(fields);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Unable to discover columns for table '" + Const.NVL(table.getName(), "") + "'", e);
    }
  }

  /** Compares model columns to a live discovery result (order-independent by name). */
  public static List<ColumnDiff> compareColumns(
      List<SourceColumn> modelColumns, List<SourceColumn> liveColumns) {
    Map<String, SourceColumn> modelByName = indexByName(modelColumns);
    Map<String, SourceColumn> liveByName = indexByName(liveColumns);
    List<ColumnDiff> diffs = new ArrayList<>();

    for (Map.Entry<String, SourceColumn> entry : modelByName.entrySet()) {
      SourceColumn live = liveByName.get(entry.getKey());
      if (live == null) {
        diffs.add(
            new ColumnDiff(
                entry.getValue().getName(),
                DiffKind.ONLY_IN_MODEL,
                "Present in model, not found in live table"));
        continue;
      }
      SourceColumn model = entry.getValue();
      int modelType = model.getHopType();
      int liveType = live.getHopType();
      if (modelType > 0 && liveType > 0 && modelType != liveType) {
        diffs.add(
            new ColumnDiff(
                model.getName(),
                DiffKind.TYPE_CHANGED,
                "Hop type model=" + typeLabel(modelType) + " live=" + typeLabel(liveType)));
      }
      // Length is meaningful for character/binary types only. JDBC display sizes for Integer
      // (e.g. 10) and temporal types are noisy and should not be treated as layout drift.
      if (shouldCompareLength(modelType, liveType)
          && !lengthEqual(model.getLength(), live.getLength())) {
        diffs.add(
            new ColumnDiff(
                model.getName(),
                DiffKind.LENGTH_CHANGED,
                "Length model="
                    + Const.NVL(model.getLength(), "")
                    + " live="
                    + Const.NVL(live.getLength(), "")));
      }
      // Precision is meaningful for Number/BigNumber. Integer scale 0 vs empty, and Timestamp
      // fractional-second metadata, are not treated as differences here.
      if (shouldComparePrecision(modelType, liveType)
          && !lengthEqual(model.getPrecision(), live.getPrecision())) {
        diffs.add(
            new ColumnDiff(
                model.getName(),
                DiffKind.PRECISION_CHANGED,
                "Precision model="
                    + Const.NVL(model.getPrecision(), "")
                    + " live="
                    + Const.NVL(live.getPrecision(), "")));
      }
      if (model.getPrimaryKeyPosition() != live.getPrimaryKeyPosition()) {
        diffs.add(
            new ColumnDiff(
                model.getName(),
                DiffKind.PK_CHANGED,
                "PK position model="
                    + model.getPrimaryKeyPosition()
                    + " live="
                    + live.getPrimaryKeyPosition()));
      }
    }
    for (Map.Entry<String, SourceColumn> entry : liveByName.entrySet()) {
      if (!modelByName.containsKey(entry.getKey())) {
        diffs.add(
            new ColumnDiff(
                entry.getValue().getName(),
                DiffKind.ONLY_IN_LIVE,
                "Present in live table, not in model"));
      }
    }
    return diffs;
  }

  /** Turns column diffs into check remarks for Validate / Show differences dialogs. */
  public static List<ICheckResult> diffsToRemarks(List<ColumnDiff> diffs) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (diffs == null) {
      return remarks;
    }
    for (ColumnDiff diff : diffs) {
      if (diff == null) {
        continue;
      }
      int type =
          switch (diff.kind()) {
            case ONLY_IN_MODEL, ONLY_IN_LIVE, TYPE_CHANGED, PK_CHANGED ->
                ICheckResult.TYPE_RESULT_ERROR;
            case LENGTH_CHANGED, PRECISION_CHANGED -> ICheckResult.TYPE_RESULT_WARNING;
          };
      remarks.add(
          new CheckResult(
              type, diff.kind().name() + " [" + diff.columnName() + "]: " + diff.detail(), null));
    }
    return remarks;
  }

  /**
   * Character length is meaningful for String (and Binary). Integer display sizes and temporal
   * metadata are not comparable layout drift.
   */
  static boolean shouldCompareLength(int modelHopType, int liveHopType) {
    int type = modelHopType > 0 ? modelHopType : liveHopType;
    return type == IValueMeta.TYPE_STRING || type == IValueMeta.TYPE_BINARY;
  }

  /** Scale is meaningful for Number / BigNumber only. */
  static boolean shouldComparePrecision(int modelHopType, int liveHopType) {
    int type = modelHopType > 0 ? modelHopType : liveHopType;
    return type == IValueMeta.TYPE_NUMBER || type == IValueMeta.TYPE_BIGNUMBER;
  }

  public static List<SourceColumn> toSourceColumns(List<SourceField> fields) {
    List<SourceColumn> columns = new ArrayList<>();
    if (fields == null) {
      return columns;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      SourceColumn column = new SourceColumn(field.getName().trim());
      column.setDescription(field.getDescription());
      column.setSourceDataType(field.getSourceDataType());
      column.setLength(field.getLength());
      column.setPrecision(field.getPrecision());
      column.setHopType(field.getHopType());
      column.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      columns.add(column);
    }
    return columns;
  }

  private static Map<String, SourceColumn> indexByName(List<SourceColumn> columns) {
    Map<String, SourceColumn> byName = new LinkedHashMap<>();
    if (columns == null) {
      return byName;
    }
    for (SourceColumn column : columns) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      byName.putIfAbsent(column.getName().trim().toLowerCase(Locale.ROOT), column);
    }
    return byName;
  }

  private static boolean lengthEqual(String left, String right) {
    String l = normalizeLength(left);
    String r = normalizeLength(right);
    return Objects.equals(l, r);
  }

  private static String normalizeLength(String value) {
    if (Utils.isEmpty(value) || "-1".equals(value.trim())) {
      return "";
    }
    return value.trim();
  }

  private static String typeLabel(int hopType) {
    try {
      return ValueMetaFactory.getValueMetaName(hopType);
    } catch (Exception e) {
      return String.valueOf(hopType);
    }
  }
}
