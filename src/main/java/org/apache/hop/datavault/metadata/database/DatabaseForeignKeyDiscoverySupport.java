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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.util.Utils;

/**
 * Discovers foreign-key constraints from JDBC database metadata ({@code getImportedKeys}).
 *
 * <p>Composite keys are assembled by constraint name and {@code KEY_SEQ} order.
 */
public final class DatabaseForeignKeyDiscoverySupport {

  private DatabaseForeignKeyDiscoverySupport() {}

  /**
   * Returns imported foreign keys for one child table (columns on {@code tableName} that reference
   * other tables).
   */
  public static List<DiscoveredForeignKey> discoverImportedForeignKeys(
      Database db, DatabaseMeta databaseMeta, String schemaName, String tableName)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName)) {
      return List.of();
    }

    String catalog = DatabaseJdbcCatalogSupport.resolveCatalog(db, databaseMeta);
    String schema = Utils.isEmpty(schemaName) ? null : schemaName.trim();
    String table = DvDatabaseSourceImportSupport.stripTableNameQuotes(tableName).trim();

    List<DiscoveredForeignKey> keys = readImportedKeys(db, catalog, schema, table);
    if (!keys.isEmpty()) {
      return keys;
    }

    // Some drivers require schema-qualified table names or null schema.
    if (!Utils.isEmpty(schema)) {
      keys = readImportedKeys(db, catalog, null, schema + "." + table);
      if (!keys.isEmpty()) {
        return keys;
      }
      keys = readImportedKeys(db, catalog, null, table);
    }
    return keys;
  }

  /**
   * Discovers imported FKs for every table in {@code tableNames} and de-duplicates constraints that
   * appear more than once.
   */
  public static List<DiscoveredForeignKey> discoverImportedForeignKeysForTables(
      Database db, DatabaseMeta databaseMeta, String schemaName, Iterable<String> tableNames)
      throws HopDatabaseException {
    if (db == null || tableNames == null) {
      return List.of();
    }
    Map<String, DiscoveredForeignKey> byKey = new LinkedHashMap<>();
    for (String tableName : tableNames) {
      if (Utils.isEmpty(tableName)) {
        continue;
      }
      for (DiscoveredForeignKey fk :
          discoverImportedForeignKeys(db, databaseMeta, schemaName, tableName)) {
        if (fk == null || !fk.isValid()) {
          continue;
        }
        byKey.putIfAbsent(fk.dedupeKey(), fk);
      }
    }
    return new ArrayList<>(byKey.values());
  }

  private static List<DiscoveredForeignKey> readImportedKeys(
      Database db, String catalog, String schema, String tableName) throws HopDatabaseException {
    // constraintKey -> sequence -> (fkCol, pkCol)
    Map<String, Map<Integer, ColumnPair>> byConstraint = new LinkedHashMap<>();
    Map<String, DiscoveredForeignKey> headers = new LinkedHashMap<>();

    ResultSet keys = null;
    try {
      keys = db.getDatabaseMetaData().getImportedKeys(catalog, schema, tableName);
      while (keys.next()) {
        String fkTable = trimToNull(keys.getString("FKTABLE_NAME"));
        String pkTable = trimToNull(keys.getString("PKTABLE_NAME"));
        String fkColumn = trimToNull(keys.getString("FKCOLUMN_NAME"));
        String pkColumn = trimToNull(keys.getString("PKCOLUMN_NAME"));
        if (Utils.isEmpty(fkTable)
            || Utils.isEmpty(pkTable)
            || Utils.isEmpty(fkColumn)
            || Utils.isEmpty(pkColumn)) {
          continue;
        }

        String fkSchema = trimToNull(keys.getString("FKTABLE_SCHEM"));
        String pkSchema = trimToNull(keys.getString("PKTABLE_SCHEM"));
        String constraintName = trimToNull(keys.getString("FK_NAME"));
        int sequence = keys.getInt("KEY_SEQ");
        if (sequence <= 0) {
          sequence = 1;
        }

        String headerKey =
            (constraintName != null ? constraintName : "")
                + "|"
                + nvl(fkSchema)
                + "."
                + fkTable
                + "->"
                + nvl(pkSchema)
                + "."
                + pkTable;

        DiscoveredForeignKey header =
            headers.computeIfAbsent(
                headerKey,
                ignored -> {
                  DiscoveredForeignKey fk = new DiscoveredForeignKey();
                  fk.setConstraintName(constraintName);
                  fk.setChildSchema(fkSchema);
                  fk.setChildTable(DvDatabaseSourceImportSupport.stripTableNameQuotes(fkTable));
                  fk.setParentSchema(pkSchema);
                  fk.setParentTable(DvDatabaseSourceImportSupport.stripTableNameQuotes(pkTable));
                  return fk;
                });

        byConstraint
            .computeIfAbsent(headerKey, ignored -> new LinkedHashMap<>())
            .putIfAbsent(
                sequence,
                new ColumnPair(
                    DvDatabaseSourceImportSupport.stripTableNameQuotes(fkColumn),
                    DvDatabaseSourceImportSupport.stripTableNameQuotes(pkColumn)));
      }
    } catch (SQLException e) {
      throw new HopDatabaseException(
          "Error reading imported foreign keys for table " + tableName, e);
    } finally {
      if (keys != null) {
        try {
          keys.close();
        } catch (SQLException ignored) {
          // Ignore close failures.
        }
      }
    }

    List<DiscoveredForeignKey> result = new ArrayList<>();
    for (Map.Entry<String, Map<Integer, ColumnPair>> entry : byConstraint.entrySet()) {
      DiscoveredForeignKey header = headers.get(entry.getKey());
      if (header == null) {
        continue;
      }
      DiscoveredForeignKey fk = new DiscoveredForeignKey();
      fk.setConstraintName(header.getConstraintName());
      fk.setChildSchema(header.getChildSchema());
      fk.setChildTable(header.getChildTable());
      fk.setParentSchema(header.getParentSchema());
      fk.setParentTable(header.getParentTable());
      entry.getValue().entrySet().stream()
          .sorted(Comparator.comparingInt(Map.Entry::getKey))
          .forEach(e -> fk.addColumnPair(e.getValue().childColumn(), e.getValue().parentColumn()));
      if (fk.isValid()) {
        result.add(fk);
      }
    }
    return result;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }

  private record ColumnPair(String childColumn, String parentColumn) {}
}
