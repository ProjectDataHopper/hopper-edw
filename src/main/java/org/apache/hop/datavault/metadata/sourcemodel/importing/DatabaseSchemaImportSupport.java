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
package org.apache.hop.datavault.metadata.sourcemodel.importing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DatabaseForeignKeyDiscoverySupport;
import org.apache.hop.datavault.metadata.database.DiscoveredForeignKey;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Imports database tables (columns, PKs) and FK relationships into a {@link SourceModel}.
 *
 * <p>Optionally publishes each table as a catalog {@code DV_SOURCE} feed.
 */
public final class DatabaseSchemaImportSupport {

  private static final Class<?> PKG = DatabaseSchemaImportSupport.class;

  public static final int LAYOUT_COLUMNS = 4;
  public static final int LAYOUT_ORIGIN_X = 50;
  public static final int LAYOUT_ORIGIN_Y = 50;
  public static final int LAYOUT_STEP_X = 240;
  public static final int LAYOUT_STEP_Y = 160;

  private DatabaseSchemaImportSupport() {}

  public static SourceSchemaImportResult importTables(
      SourceModel model,
      DatabaseMeta databaseMeta,
      SourceSchemaImportOptions options,
      List<String> tableNames,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseSchemaImportSupport.Error.NoModel"));
    }
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseSchemaImportSupport.Error.NoDatabase"));
    }
    if (tableNames == null || tableNames.isEmpty()) {
      return SourceSchemaImportResult.empty();
    }

    SourceSchemaImportOptions resolved =
        options != null ? options : SourceSchemaImportOptions.defaults();
    String connectionName =
        !Utils.isEmpty(resolved.getDatabaseName())
            ? resolved.getDatabaseName()
            : databaseMeta.getName();
    String schemaName =
        variables != null
            ? Const.NVL(variables.resolve(resolved.getSchemaName()), "")
            : Const.NVL(resolved.getSchemaName(), "");

    List<SourceTable> importedTables = new ArrayList<>();
    List<SourceRelationship> importedRelationships = new ArrayList<>();
    List<String> published = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // physical table name (lower) -> canvas logical name
    Map<String, String> physicalToLogical = new HashMap<>();
    List<String> cleanedTableNames = new ArrayList<>();

    ILoggingObject loggingObject =
        new SimpleLoggingObject("SourceSchemaImport", LoggingObjectType.GENERAL, null);
    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();

      int layoutIndex = 0;
      for (String rawTableName : tableNames) {
        String tableName = DvDatabaseSourceImportSupport.stripTableNameQuotes(rawTableName);
        if (Utils.isEmpty(tableName)) {
          continue;
        }
        cleanedTableNames.add(tableName);
        try {
          List<SourceField> fields =
              DvDatabaseSourceImportSupport.importFieldsFromTable(
                  database, variables, schemaName, tableName);
          if (fields.isEmpty()) {
            errors.add(
                BaseMessages.getString(
                    PKG, "DatabaseSchemaImportSupport.Error.NoColumns", tableName));
            continue;
          }

          String logicalName =
              resolveUniqueLogicalName(
                  model,
                  importedTables,
                  buildLogicalName(
                      resolved.getSourceNamePrefix(), connectionName, schemaName, tableName));

          SourceTable sourceTable =
              buildSourceTable(
                  logicalName, connectionName, schemaName, tableName, fields, layoutIndex);
          if (model.findTable(sourceTable.getName()) != null) {
            // Should not happen after resolveUniqueLogicalName; keep defensive rename.
            sourceTable.setName(uniqueLogicalName(model, importedTables, sourceTable.getName()));
          }

          importedTables.add(sourceTable);
          physicalToLogical.put(normalizePhysicalKey(schemaName, tableName), sourceTable.getName());
          layoutIndex++;

          if (resolved.isPublishToCatalog()) {
            try {
              String catalogName =
                  publishTableToCatalog(
                      sourceTable,
                      fields,
                      connectionName,
                      schemaName,
                      tableName,
                      resolved,
                      model,
                      variables,
                      metadataProvider);
              if (!Utils.isEmpty(catalogName)) {
                sourceTable.setCatalogSourceName(catalogName);
                published.add(catalogName);
              }
            } catch (Exception e) {
              warnings.add(
                  BaseMessages.getString(
                      PKG,
                      "DatabaseSchemaImportSupport.Warning.CatalogPublishFailed",
                      tableName,
                      e.getMessage()));
            }
          }
        } catch (Exception e) {
          errors.add(
              BaseMessages.getString(
                  PKG,
                  "DatabaseSchemaImportSupport.Error.TableImportFailed",
                  tableName,
                  e.getMessage()));
        }
      }

      // Include existing model tables in the name map so FKs to already-modeled parents resolve.
      for (SourceTable existing : model.getTables()) {
        if (existing == null || Utils.isEmpty(existing.getTableName())) {
          continue;
        }
        String key = normalizePhysicalKey(existing.getSchemaName(), existing.getTableName());
        physicalToLogical.putIfAbsent(key, existing.getName());
        // Also map bare table name for drivers that omit schema on parent side.
        physicalToLogical.putIfAbsent(
            normalizePhysicalKey(null, existing.getTableName()), existing.getName());
      }
      for (SourceTable imported : importedTables) {
        physicalToLogical.putIfAbsent(
            normalizePhysicalKey(null, imported.getTableName()), imported.getName());
      }

      try {
        List<DiscoveredForeignKey> foreignKeys =
            DatabaseForeignKeyDiscoverySupport.discoverImportedForeignKeysForTables(
                database, databaseMeta, schemaName, cleanedTableNames);
        List<SourceRelationship> relationships =
            buildRelationshipsFromForeignKeys(
                foreignKeys, physicalToLogical, model, importedRelationships, warnings);
        importedRelationships.addAll(relationships);
      } catch (Exception e) {
        warnings.add(
            BaseMessages.getString(
                PKG, "DatabaseSchemaImportSupport.Warning.FkDiscoveryFailed", e.getMessage()));
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseSchemaImportSupport.Error.DatabaseConnection"), e);
    }

    // Seed model defaults when empty.
    SourceModelConfiguration config = model.getConfigurationOrDefault();
    if (Utils.isEmpty(config.getDefaultDatabase()) && !Utils.isEmpty(connectionName)) {
      config.setDefaultDatabase(connectionName);
    }
    if (Utils.isEmpty(config.getDefaultSchema()) && !Utils.isEmpty(schemaName)) {
      config.setDefaultSchema(schemaName);
    }
    if (resolved.isPublishToCatalog()
        && Utils.isEmpty(config.getCatalogConnection())
        && !Utils.isEmpty(resolved.getCatalogConnectionName())) {
      config.setCatalogConnection(resolved.getCatalogConnectionName());
    }

    return new SourceSchemaImportResult(
        importedTables, importedRelationships, published, warnings, errors);
  }

  /** Applies imported tables/relationships onto the model (mutates {@code model}). */
  public static void applyImportResult(SourceModel model, SourceSchemaImportResult result) {
    if (model == null || result == null) {
      return;
    }
    SourceModelConfiguration config = model.getConfigurationOrDefault();
    for (SourceTable table : result.getImportedTablesOrEmpty()) {
      if (table != null) {
        config.applyDefaultDataTypeMappings(table);
        model.getTables().add(table);
      }
    }
    for (SourceRelationship relationship : result.getImportedRelationshipsOrEmpty()) {
      if (relationship != null) {
        model.getRelationships().add(relationship);
      }
    }
  }

  static SourceTable buildSourceTable(
      String logicalName,
      String connectionName,
      String schemaName,
      String tableName,
      List<SourceField> fields,
      int layoutIndex) {
    SourceTable table = new SourceTable(logicalName);
    table.setPhysicalType(DvSourceType.DATABASE);
    table.setDatabaseName(connectionName);
    table.setSchemaName(schemaName);
    table.setTableName(tableName);
    table.setColumns(toSourceColumns(fields));
    table.setLocation(layoutPoint(layoutIndex));
    return table;
  }

  static List<SourceColumn> toSourceColumns(List<SourceField> fields) {
    List<SourceColumn> columns = new ArrayList<>();
    if (fields == null) {
      return columns;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      SourceColumn column = new SourceColumn(field.getName());
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

  static Point layoutPoint(int layoutIndex) {
    int col = layoutIndex % LAYOUT_COLUMNS;
    int row = layoutIndex / LAYOUT_COLUMNS;
    return new Point(LAYOUT_ORIGIN_X + col * LAYOUT_STEP_X, LAYOUT_ORIGIN_Y + row * LAYOUT_STEP_Y);
  }

  static List<SourceRelationship> buildRelationshipsFromForeignKeys(
      List<DiscoveredForeignKey> foreignKeys,
      Map<String, String> physicalToLogical,
      SourceModel model,
      List<SourceRelationship> pendingImported,
      List<String> warnings) {
    List<SourceRelationship> result = new ArrayList<>();
    if (foreignKeys == null || foreignKeys.isEmpty()) {
      return result;
    }

    Set<String> existingKeys = existingRelationshipKeys(model);
    for (SourceRelationship pending : pendingImported) {
      existingKeys.add(relationshipDedupeKey(pending));
    }

    Set<String> usedNames = new HashSet<>();
    for (SourceRelationship existing : model.getRelationships()) {
      if (existing != null && !Utils.isEmpty(existing.getName())) {
        usedNames.add(existing.getName());
      }
    }
    for (SourceRelationship pending : pendingImported) {
      if (pending != null && !Utils.isEmpty(pending.getName())) {
        usedNames.add(pending.getName());
      }
    }

    for (DiscoveredForeignKey fk : foreignKeys) {
      if (fk == null || !fk.isValid()) {
        continue;
      }
      String childLogical =
          resolveLogicalName(physicalToLogical, fk.getChildSchema(), fk.getChildTable());
      String parentLogical =
          resolveLogicalName(physicalToLogical, fk.getParentSchema(), fk.getParentTable());
      if (Utils.isEmpty(childLogical) || Utils.isEmpty(parentLogical)) {
        warnings.add(
            BaseMessages.getString(
                PKG,
                "DatabaseSchemaImportSupport.Warning.FkEndpointMissing",
                Const.NVL(fk.getConstraintName(), "?"),
                Const.NVL(fk.getChildTable(), "?"),
                Const.NVL(fk.getParentTable(), "?")));
        continue;
      }
      if (childLogical.equals(parentLogical)) {
        // Self-FKs are valid; keep them.
      }

      SourceRelationship relationship = new SourceRelationship();
      relationship.setName(
          uniqueName(
              usedNames,
              !Utils.isEmpty(fk.getConstraintName())
                  ? sanitizeName(fk.getConstraintName())
                  : "fk_" + childLogical + "_" + parentLogical));
      relationship.setChildTableName(childLogical);
      relationship.setParentTableName(parentLogical);
      relationship.setChildColumns(new ArrayList<>(fk.getChildColumns()));
      relationship.setParentColumns(new ArrayList<>(fk.getParentColumns()));
      relationship.setDefaultJoinType(SourceJoinType.LEFT);
      relationship.setChildMultiplicity(
          org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity
              .ZERO_OR_MANY);
      relationship.setParentMultiplicity(
          org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity.ONE);
      relationship.setCardinality("0..N:1");

      String dedupe = relationshipDedupeKey(relationship);
      if (existingKeys.contains(dedupe)) {
        warnings.add(
            BaseMessages.getString(
                PKG,
                "DatabaseSchemaImportSupport.Warning.FkDuplicateSkipped",
                relationship.getName()));
        continue;
      }
      existingKeys.add(dedupe);
      usedNames.add(relationship.getName());
      result.add(relationship);
    }
    return result;
  }

  private static String resolveLogicalName(
      Map<String, String> physicalToLogical, String schema, String table) {
    if (physicalToLogical == null || Utils.isEmpty(table)) {
      return null;
    }
    String withSchema = physicalToLogical.get(normalizePhysicalKey(schema, table));
    if (!Utils.isEmpty(withSchema)) {
      return withSchema;
    }
    return physicalToLogical.get(normalizePhysicalKey(null, table));
  }

  static String normalizePhysicalKey(String schema, String table) {
    String s = Utils.isEmpty(schema) ? "" : schema.trim().toLowerCase(Locale.ROOT);
    String t =
        Utils.isEmpty(table)
            ? ""
            : DvDatabaseSourceImportSupport.stripTableNameQuotes(table)
                .trim()
                .toLowerCase(Locale.ROOT);
    return s + "|" + t;
  }

  static String relationshipDedupeKey(SourceRelationship relationship) {
    if (relationship == null) {
      return "";
    }
    return Const.NVL(relationship.getChildTableName(), "")
        + "->"
        + Const.NVL(relationship.getParentTableName(), "")
        + ":"
        + String.join(",", relationship.getChildColumns())
        + "=>"
        + String.join(",", relationship.getParentColumns());
  }

  private static Set<String> existingRelationshipKeys(SourceModel model) {
    Set<String> keys = new HashSet<>();
    if (model == null) {
      return keys;
    }
    for (SourceRelationship relationship : model.getRelationships()) {
      keys.add(relationshipDedupeKey(relationship));
    }
    return keys;
  }

  static String buildLogicalName(
      String prefix, String connectionName, String schemaName, String tableName) {
    if (!Utils.isEmpty(prefix)) {
      return sanitizeName(prefix + tableName);
    }
    // Prefer bare table name on the canvas; disambiguate later if needed.
    return sanitizeName(tableName);
  }

  static String sanitizeName(String name) {
    if (Utils.isEmpty(name)) {
      return "table";
    }
    String cleaned = name.trim().replaceAll("[^A-Za-z0-9_\\-\\.]+", "_");
    if (cleaned.isEmpty()) {
      return "table";
    }
    return cleaned;
  }

  private static String resolveUniqueLogicalName(
      SourceModel model, List<SourceTable> pending, String baseName) {
    return uniqueLogicalName(model, pending, baseName);
  }

  static String uniqueLogicalName(SourceModel model, List<SourceTable> pending, String baseName) {
    Set<String> used = new HashSet<>();
    if (model != null) {
      for (SourceTable table : model.getTables()) {
        if (table != null && !Utils.isEmpty(table.getName())) {
          used.add(table.getName());
        }
      }
    }
    if (pending != null) {
      for (SourceTable table : pending) {
        if (table != null && !Utils.isEmpty(table.getName())) {
          used.add(table.getName());
        }
      }
    }
    return uniqueName(used, baseName);
  }

  static String uniqueName(Set<String> usedNames, String baseName) {
    String base = Utils.isEmpty(baseName) ? "table" : baseName;
    if (!usedNames.contains(base)) {
      return base;
    }
    int suffix = 2;
    while (usedNames.contains(base + "_" + suffix)) {
      suffix++;
    }
    return base + "_" + suffix;
  }

  private static String publishTableToCatalog(
      SourceTable sourceTable,
      List<SourceField> fields,
      String connectionName,
      String schemaName,
      String tableName,
      SourceSchemaImportOptions options,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String catalogConnection = Const.NVL(options.getCatalogConnectionName(), "");
    if (variables != null) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    if (Utils.isEmpty(catalogConnection)) {
      catalogConnection =
          variables != null
              ? variables.resolve(model.getConfigurationOrDefault().getCatalogConnection())
              : model.getConfigurationOrDefault().getCatalogConnection();
    }
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DatabaseSchemaImportSupport.Error.NoCatalogConnection"));
    }

    String metadataName =
        !Utils.isEmpty(sourceTable.getCatalogSourceName())
            ? sourceTable.getCatalogSourceName()
            : sourceTable.getName();
    RecordSourceIndicatorOptions recordSource =
        RecordSourceIndicatorSupport.resolveForTable(
            options.getRecordSourceOptions(), fields, metadataName);
    DataVaultSource dataVaultSource =
        DvDatabaseSourceImportSupport.createDataVaultSource(
            metadataName, connectionName, schemaName, tableName, fields, recordSource);
    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);
    return metadataName;
  }
}
