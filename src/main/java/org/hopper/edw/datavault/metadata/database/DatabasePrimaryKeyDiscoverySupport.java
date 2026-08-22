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
package org.hopper.edw.datavault.metadata.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.util.Utils;

/** Discovers primary-key column names and order from JDBC database metadata. */
public final class DatabasePrimaryKeyDiscoverySupport {

  private DatabasePrimaryKeyDiscoverySupport() {}

  public static List<String> discoverPrimaryKeyColumnNames(
      Database db, DatabaseMeta databaseMeta, String schemaName, String tableName)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName)) {
      return List.of();
    }

    String catalog = DatabaseJdbcCatalogSupport.resolveCatalog(db, databaseMeta);
    String schema = Utils.isEmpty(schemaName) ? null : schemaName.trim();
    String table = DvDatabaseSourceImportSupport.stripTableNameQuotes(tableName).trim();

    List<String> orderedNames = readPrimaryKeys(db, catalog, schema, table);
    if (!orderedNames.isEmpty()) {
      return orderedNames;
    }

    if (!Utils.isEmpty(schema)) {
      orderedNames = readPrimaryKeys(db, catalog, null, schema + "." + table);
      if (!orderedNames.isEmpty()) {
        return orderedNames;
      }
    }

    try {
      String[] fallback = db.getPrimaryKeyColumnNames(table);
      if (fallback != null && fallback.length > 0) {
        return List.of(fallback);
      }
    } catch (HopDatabaseException ignored) {
      // Fall through to empty result.
    }
    return List.of();
  }

  private static List<String> readPrimaryKeys(
      Database db, String catalog, String schema, String tableName) throws HopDatabaseException {
    Map<Integer, String> columnsBySequence = new LinkedHashMap<>();
    ResultSet keys = null;
    try {
      keys = db.getDatabaseMetaData().getPrimaryKeys(catalog, schema, tableName);
      while (keys.next()) {
        String columnName = keys.getString("COLUMN_NAME");
        if (Utils.isEmpty(columnName)) {
          continue;
        }
        int sequence = keys.getInt("KEY_SEQ");
        if (sequence <= 0) {
          sequence = columnsBySequence.size() + 1;
        }
        columnsBySequence.putIfAbsent(sequence, columnName.trim());
      }
    } catch (SQLException e) {
      throw new HopDatabaseException(
          "Error reading primary key metadata for table " + tableName, e);
    } finally {
      if (keys != null) {
        try {
          keys.close();
        } catch (SQLException ignored) {
          // Ignore close failures.
        }
      }
    }

    return columnsBySequence.entrySet().stream()
        .sorted(Comparator.comparingInt(Map.Entry::getKey))
        .map(Map.Entry::getValue)
        .toList();
  }
}
