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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvTableBase;

/**
 * Non-unique lookup indexes for generated target tables. A primary key created in the same batch
 * already indexes its leading columns, so that index is not repeated. Engines without secondary
 * indexes (Snowflake, Exasol) are skipped.
 */
public final class DvIndexDdlSupport {

  public static final String SUFFIX_NATURAL_KEY = "nk";
  public static final String SUFFIX_TECHNICAL_KEY = "tk";
  public static final String SUFFIX_HASH_KEY = "hk";
  public static final String SUFFIX_LOOKUP = "lookup";

  private DvIndexDdlSupport() {}

  public static boolean supportsSecondaryIndexes(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || DvDdlSupport.isSnowflake(databaseMeta)) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    if (!Utils.isEmpty(pluginId) && pluginId.toUpperCase(Locale.ROOT).startsWith("EXASOL")) {
      return false;
    }
    return databaseMeta.getIDatabase() == null || !databaseMeta.getIDatabase().isExasolVariant();
  }

  /**
   * True when {@code indexColumns} are the primary-key columns or a leading prefix of them.
   * Comparison ignores case.
   */
  public static boolean primaryKeyCovers(
      List<String> indexColumns, List<String> primaryKeyColumns) {
    if (indexColumns == null || indexColumns.isEmpty()) {
      return false;
    }
    if (primaryKeyColumns == null || primaryKeyColumns.size() < indexColumns.size()) {
      return false;
    }
    for (int i = 0; i < indexColumns.size(); i++) {
      String indexColumn = indexColumns.get(i);
      String primaryKeyColumn = primaryKeyColumns.get(i);
      if (Utils.isEmpty(indexColumn)
          || Utils.isEmpty(primaryKeyColumn)
          || !indexColumn.equalsIgnoreCase(primaryKeyColumn)) {
        return false;
      }
    }
    return true;
  }

  /** {@code idx_<table>_<suffix>}, shortened to the engine identifier limit with a stable hash. */
  public static String indexName(String tableName, String suffix, DatabaseMeta databaseMeta) {
    String bare = bareTableName(tableName);
    String desired = "idx_" + bare + "_" + (Utils.isEmpty(suffix) ? SUFFIX_LOOKUP : suffix);
    int max = DvIdentifierLimitSupport.maxColumnNameLength(databaseMeta);
    if (desired.length() <= max) {
      return desired;
    }
    String hash = Integer.toUnsignedString(desired.hashCode(), 36);
    if (hash.length() > 6) {
      hash = hash.substring(hash.length() - 6);
    }
    int keep = Math.max(1, max - hash.length());
    String base = desired.substring(0, Math.min(keep, desired.length()));
    return base + hash;
  }

  public static void appendIndex(
      List<String> result,
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schema,
      String tableName,
      String suffix,
      List<String> columns,
      List<String> primaryKeyColumns,
      IRowMeta layout)
      throws HopException {
    if (!supportsSecondaryIndexes(databaseMeta) || Utils.isEmpty(tableName)) {
      return;
    }
    List<String> indexColumns = columnsOnLayout(columns, layout);
    if (indexColumns.isEmpty()) {
      return;
    }
    boolean creatingThisTable = createsTable(result, tableName);
    if (creatingThisTable && primaryKeyCovers(indexColumns, primaryKeyColumns)) {
      return;
    }
    if (db == null) {
      throw new HopException("Error getting lookup index DDL for table: " + tableName);
    }
    String[] idxFields = indexColumns.toArray(String[]::new);
    String quotedTable =
        databaseMeta.getQuotedSchemaTableCombination(variables, emptyToNull(schema), tableName);
    try {
      if (db.checkIndexExists(emptyToNull(schema), tableName, idxFields)) {
        return;
      }
      String indexDdl =
          db.getCreateIndexStatement(
              quotedTable,
              indexName(tableName, suffix, databaseMeta),
              idxFields,
              false,
              false,
              false,
              true);
      if (!Utils.isEmpty(indexDdl)) {
        result.add(indexDdl);
      }
    } catch (Exception e) {
      throw new HopException("Error getting lookup index DDL for table: " + tableName, e);
    }
  }

  public static void appendDataVaultIndex(
      List<String> result,
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      DvTableBase table,
      DataVaultModel model,
      DataVaultConfiguration config,
      IHopMetadataProvider metadataProvider,
      IRowMeta layout)
      throws HopException {
    if (table == null || config == null) {
      return;
    }
    List<String> logical =
        DvConstraintDdlSupport.resolveDvLogicalKeyColumns(
            table, model, config, variables, layout);
    List<String> primaryKeyColumns =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            table, model, config, variables, metadataProvider, layout, false);
    appendIndex(
        result,
        db,
        databaseMeta,
        variables,
        null,
        tableName,
        dataVaultSuffix(table, logical),
        logical,
        primaryKeyColumns,
        layout);
  }

  public static void appendBusinessVaultIndex(
      List<String> result,
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schema,
      String tableName,
      BvTableBase table,
      BusinessVaultModel model,
      BusinessVaultConfiguration config,
      DataVaultModel dataVaultModel,
      IRowMeta layout)
      throws HopException {
    if (table == null || config == null) {
      return;
    }
    List<String> logical =
        DvConstraintDdlSupport.resolveBvLogicalKeyColumns(
            table, model, config, dataVaultModel, variables, layout);
    List<String> primaryKeyColumns =
        DvConstraintDdlSupport.resolveBvPrimaryKeyColumns(
            table, model, config, dataVaultModel, variables, layout);
    appendIndex(
        result,
        db,
        databaseMeta,
        variables,
        schema,
        tableName,
        businessVaultSuffix(logical),
        logical,
        primaryKeyColumns,
        layout);
  }

  private static String dataVaultSuffix(DvTableBase table, List<String> logical) {
    if (table instanceof DvReferenceTable) {
      return SUFFIX_NATURAL_KEY;
    }
    if (logical != null && logical.size() > 1) {
      return SUFFIX_LOOKUP;
    }
    return SUFFIX_HASH_KEY;
  }

  private static String businessVaultSuffix(List<String> logical) {
    if (logical != null && logical.size() > 1) {
      return SUFFIX_LOOKUP;
    }
    return SUFFIX_HASH_KEY;
  }

  static List<String> columnsOnLayout(List<String> columns, IRowMeta layout) {
    if (columns == null || columns.isEmpty()) {
      return List.of();
    }
    if (layout == null || layout.isEmpty()) {
      List<String> copy = new ArrayList<>();
      for (String column : columns) {
        if (!Utils.isEmpty(column)) {
          copy.add(column);
        }
      }
      return copy;
    }
    List<String> present = new ArrayList<>();
    for (String column : columns) {
      if (Utils.isEmpty(column)) {
        continue;
      }
      for (int i = 0; i < layout.size(); i++) {
        String name = layout.getValueMeta(i).getName();
        if (column.equalsIgnoreCase(name)) {
          present.add(name);
          break;
        }
      }
    }
    return present;
  }

  private static boolean createsTable(List<String> result, String tableName) {
    if (result == null || Utils.isEmpty(tableName)) {
      return false;
    }
    for (String sql : result) {
      String created = DvDdlSupport.extractCreateTableName(sql);
      if (created != null && created.equalsIgnoreCase(tableName)) {
        return true;
      }
    }
    return false;
  }

  private static String bareTableName(String tableName) {
    String bare = tableName == null ? "" : tableName.trim();
    int dot = bare.lastIndexOf('.');
    if (dot >= 0 && dot < bare.length() - 1) {
      bare = bare.substring(dot + 1);
    }
    if ((bare.startsWith("\"") && bare.endsWith("\""))
        || (bare.startsWith("`") && bare.endsWith("`"))
        || (bare.startsWith("[") && bare.endsWith("]"))) {
      bare = bare.substring(1, bare.length() - 1);
    }
    return bare;
  }

  private static String emptyToNull(String value) {
    return Utils.isEmpty(value) ? null : value;
  }
}
