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
package org.apache.hop.catalog.transform.tablemetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DatabaseForeignKeyDiscoverySupport;
import org.apache.hop.datavault.metadata.database.DiscoveredForeignKey;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;

/**
 * Discovers table column metadata as Hop types with primary-key and foreign-key attributes, reusing
 * catalog/source import discovery.
 */
public final class DatabaseTableMetadataSupport {

  private DatabaseTableMetadataSupport() {}

  /**
   * Discovers columns for one table. Works for empty tables (uses {@code getTableFieldsMeta}, not
   * data rows).
   */
  public static List<DatabaseTableMetadataColumn> discoverColumns(
      Database db,
      String connectionName,
      String schemaName,
      String tableName,
      IVariables variables,
      boolean includeForeignKeys)
      throws HopException {
    if (db == null) {
      throw new HopException("Database connection is required");
    }
    if (Utils.isEmpty(tableName)) {
      throw new HopException("Table name is required");
    }

    String schema = variables != null ? variables.resolve(Const.NVL(schemaName, "")) : schemaName;
    String table = variables != null ? variables.resolve(tableName) : tableName;

    List<SourceField> fields =
        DvDatabaseSourceImportSupport.importFieldsFromTable(db, variables, schema, table);

    Map<String, FkAttachment> fkByChildColumn = Map.of();
    if (includeForeignKeys) {
      List<DiscoveredForeignKey> fks =
          DatabaseForeignKeyDiscoverySupport.discoverImportedForeignKeys(
              db, db.getDatabaseMeta(), schema, table);
      fkByChildColumn = indexForeignKeysByChildColumn(fks);
    }

    List<DatabaseTableMetadataColumn> columns = new ArrayList<>();
    int position = 1;
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      DatabaseTableMetadataColumn column = new DatabaseTableMetadataColumn();
      column.setDatabaseConnection(connectionName);
      column.setSchemaName(schema);
      column.setTableName(table);
      column.setFieldPosition(position++);
      column.setFieldName(field.getName());
      column.setFieldType(hopTypeName(field.getHopType()));
      column.setFieldLength(parsePositiveLong(field.getLength()));
      column.setFieldPrecision(parseNonNegativeLong(field.getPrecision()));
      column.setPrimaryKeyPosition(Math.max(0, field.getPrimaryKeyPosition()));
      column.setSourceDataType(Const.NVL(field.getSourceDataType(), ""));

      FkAttachment fk = fkByChildColumn.get(normalize(field.getName()));
      if (fk != null) {
        column.setFkConstraintName(fk.constraintName());
        column.setFkPosition(fk.position());
        column.setFkReferencedSchema(fk.referencedSchema());
        column.setFkReferencedTable(fk.referencedTable());
        column.setFkReferencedColumn(fk.referencedColumn());
      }
      columns.add(column);
    }
    return columns;
  }

  /** Pure helper for unit tests: attach first matching imported FK to each field name. */
  static Map<String, FkAttachment> indexForeignKeysByChildColumn(List<DiscoveredForeignKey> fks) {
    Map<String, FkAttachment> byChild = new LinkedHashMap<>();
    if (fks == null) {
      return byChild;
    }
    for (DiscoveredForeignKey fk : fks) {
      if (fk == null || !fk.isValid()) {
        continue;
      }
      List<String> childColumns = fk.getChildColumns();
      List<String> parentColumns = fk.getParentColumns();
      for (int i = 0; i < childColumns.size(); i++) {
        String child = childColumns.get(i);
        if (Utils.isEmpty(child)) {
          continue;
        }
        String key = normalize(child);
        byChild.putIfAbsent(
            key,
            new FkAttachment(
                Const.NVL(fk.getConstraintName(), ""),
                (long) (i + 1),
                Const.NVL(fk.getParentSchema(), ""),
                Const.NVL(fk.getParentTable(), ""),
                Const.NVL(parentColumns.get(i), "")));
      }
    }
    return byChild;
  }

  static String hopTypeName(int hopType) {
    try {
      String name = ValueMetaFactory.getValueMetaName(hopType);
      return Utils.isEmpty(name) ? "String" : name;
    } catch (Exception e) {
      return "String";
    }
  }

  private static Long parsePositiveLong(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      long value = Long.parseLong(raw.trim());
      return value > 0 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long parseNonNegativeLong(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      long value = Long.parseLong(raw.trim());
      return value >= 0 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String normalize(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }

  record FkAttachment(
      String constraintName,
      long position,
      String referencedSchema,
      String referencedTable,
      String referencedColumn) {}
}
