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
package org.apache.hop.datavault.metadata.database;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvDataTypeSupport;
import org.apache.hop.datavault.metadata.DvSqlStringTypeSupport;

/**
 * Corrects {@link IRowMeta} from {@link Database#getTableFieldsMeta} using JDBC {@link
 * DatabaseMetaData#getColumns}, which carries declared {@code COLUMN_SIZE} / {@code TYPE_NAME}.
 *
 * <p>{@code getTableFieldsMeta} relies on {@link java.sql.ResultSetMetaData#getColumnDisplaySize},
 * which SingleStore/MySQL often report incorrectly (e.g. {@code VARCHAR(150)} or {@code LONGTEXT}
 * as display size {@code 255}). That produces false source↔target length validation errors when the
 * physical types are identical.
 */
public final class DatabaseJdbcColumnEnrichmentSupport {

  private DatabaseJdbcColumnEnrichmentSupport() {}

  public static void enrichRowMeta(
      Database db, DatabaseMeta databaseMeta, String schemaName, String tableName, IRowMeta rowMeta)
      throws HopDatabaseException {
    if (db == null || rowMeta == null || rowMeta.isEmpty() || Utils.isEmpty(tableName)) {
      return;
    }
    Map<String, JdbcColumn> byName = readColumns(db, databaseMeta, schemaName, tableName);
    if (byName.isEmpty()) {
      return;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      IValueMeta vm = rowMeta.getValueMeta(i);
      if (vm == null || Utils.isEmpty(vm.getName())) {
        continue;
      }
      JdbcColumn col = byName.get(normalize(vm.getName()));
      if (col == null) {
        continue;
      }
      try {
        IValueMeta enriched = enrichValueMeta(vm, col);
        if (enriched != null && enriched != vm) {
          rowMeta.setValueMeta(i, enriched);
        }
      } catch (HopPluginException e) {
        // Keep original meta if we cannot rebuild it.
      }
    }
  }

  static IValueMeta enrichValueMeta(IValueMeta original, JdbcColumn col) throws HopPluginException {
    if (original == null || col == null) {
      return original;
    }

    String typeName =
        !Utils.isEmpty(col.typeName()) ? col.typeName() : original.getOriginalColumnTypeName();
    int hopType = original.getType();
    int fromSql = DvDataTypeSupport.hopTypeIdFromSqlTypeName(typeName);
    // Prefer SQL TYPE_NAME when ResultSetMetaData mapped poorly (DATETIME→String, etc.).
    if (fromSql > 0
        && (hopType <= 0 || (hopType == IValueMeta.TYPE_STRING && isNonStringSql(typeName)))) {
      hopType = fromSql;
    }

    int length = original.getLength();
    int precision = original.getPrecision();

    if (hopType == IValueMeta.TYPE_STRING || isStringSql(typeName)) {
      hopType = IValueMeta.TYPE_STRING;
      int size = col.columnSize();
      if (DvSqlStringTypeSupport.isLargeTextSqlType(typeName)) {
        size = DvSqlStringTypeSupport.capacityForSqlStringType(typeName, size);
      } else if (size > 0) {
        // Prefer declared COLUMN_SIZE over ResultSet display size.
        length = size;
      }
      if (size > 0) {
        length = size;
      }
    } else if (hopType == IValueMeta.TYPE_TIMESTAMP || hopType == IValueMeta.TYPE_DATE) {
      // Prefer DECIMAL_DIGITS, then TYPE_NAME fsp (DATETIME(6)), over ResultSet scale noise.
      int fsp = col.decimalDigits();
      if (fsp < 0 || fsp > 9) {
        fsp = DvDataTypeSupport.fractionalSecondsFromSqlTypeName(typeName);
      }
      if (fsp >= 0 && fsp <= 9) {
        length = fsp;
        precision = fsp;
      }
    } else if (hopType == IValueMeta.TYPE_INTEGER
        || hopType == IValueMeta.TYPE_NUMBER
        || hopType == IValueMeta.TYPE_BIGNUMBER) {
      if (col.columnSize() > 0) {
        length = col.columnSize();
      }
      if (col.decimalDigits() >= 0) {
        precision = col.decimalDigits();
      }
    }

    IValueMeta enriched =
        ValueMetaFactory.createValueMeta(original.getName(), hopType, length, precision);
    if (!Utils.isEmpty(typeName)) {
      enriched.setOriginalColumnTypeName(typeName);
    }
    if (col.columnSize() > 0) {
      enriched.setOriginalPrecision(col.columnSize());
    }
    if (col.decimalDigits() >= 0) {
      enriched.setOriginalScale(col.decimalDigits());
    }
    DvSqlStringTypeSupport.normalizeStringLength(enriched);
    return enriched;
  }

  private static Map<String, JdbcColumn> readColumns(
      Database db, DatabaseMeta databaseMeta, String schemaName, String tableName)
      throws HopDatabaseException {
    Map<String, JdbcColumn> byName = new LinkedHashMap<>();
    String catalog = DatabaseJdbcCatalogSupport.resolveCatalog(db, databaseMeta);
    String schema = Utils.isEmpty(schemaName) ? null : schemaName.trim();
    String table = DvDatabaseSourceImportSupport.stripTableNameQuotes(tableName).trim();

    readColumnsInto(db, catalog, schema, table, byName);
    if (byName.isEmpty() && !Utils.isEmpty(schema)) {
      readColumnsInto(db, catalog, null, table, byName);
    }
    if (byName.isEmpty() && !Utils.isEmpty(schema)) {
      readColumnsInto(db, catalog, null, schema + "." + table, byName);
    }
    if (byName.isEmpty() && !Utils.isEmpty(catalog)) {
      readColumnsInto(db, null, schema, table, byName);
    }
    return byName;
  }

  private static void readColumnsInto(
      Database db, String catalog, String schema, String table, Map<String, JdbcColumn> byName)
      throws HopDatabaseException {
    ResultSet columns = null;
    try {
      DatabaseMetaData metaData = db.getDatabaseMetaData();
      columns = metaData.getColumns(catalog, schema, table, null);
      while (columns.next()) {
        String name = columns.getString("COLUMN_NAME");
        if (Utils.isEmpty(name)) {
          continue;
        }
        String typeName = columns.getString("TYPE_NAME");
        int columnSize = columns.getInt("COLUMN_SIZE");
        if (columns.wasNull()) {
          columnSize = -1;
        }
        int decimalDigits = columns.getInt("DECIMAL_DIGITS");
        if (columns.wasNull()) {
          decimalDigits = -1;
        }
        byName.putIfAbsent(
            normalize(name),
            new JdbcColumn(
                name.trim(), typeName != null ? typeName.trim() : null, columnSize, decimalDigits));
      }
    } catch (SQLException e) {
      throw new HopDatabaseException("Error reading JDBC column metadata for table " + table, e);
    } finally {
      if (columns != null) {
        try {
          columns.close();
        } catch (SQLException ignored) {
          // ignore
        }
      }
    }
  }

  private static boolean isNonStringSql(String typeName) {
    int hop = DvDataTypeSupport.hopTypeIdFromSqlTypeName(typeName);
    return hop > 0 && hop != IValueMeta.TYPE_STRING;
  }

  private static boolean isStringSql(String typeName) {
    return DvDataTypeSupport.hopTypeIdFromSqlTypeName(typeName) == IValueMeta.TYPE_STRING
        || DvSqlStringTypeSupport.isLargeTextSqlType(typeName);
  }

  private static String normalize(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }

  record JdbcColumn(String name, String typeName, int columnSize, int decimalDigits) {}
}
