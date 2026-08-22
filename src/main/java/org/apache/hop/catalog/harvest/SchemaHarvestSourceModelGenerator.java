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
package org.apache.hop.catalog.harvest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.importing.DatabaseSchemaImportSupport;

/**
 * Builds or updates a {@link SourceModel} ({@code .hsm}) from harvested table layouts and foreign
 * keys so source-model drift can follow the same inventory as the catalog harvest.
 */
public final class SchemaHarvestSourceModelGenerator {

  private SchemaHarvestSourceModelGenerator() {}

  public record GenerateOptions(
      boolean mergeIntoExisting,
      boolean createStubParentTables,
      String modelName,
      String modelDescription) {
    public static GenerateOptions defaults() {
      return new GenerateOptions(true, true, null, null);
    }
  }

  public record GenerateResult(
      SourceModel model,
      int tablesAdded,
      int tablesUpdated,
      int relationshipsAdded,
      List<String> warnings) {}

  public static GenerateResult generate(
      HarvestResult harvest, SourceModel existingOrNull, GenerateOptions options)
      throws HopException {
    if (harvest == null) {
      throw new HopException("Harvest result is required");
    }
    GenerateOptions opts = options != null ? options : GenerateOptions.defaults();
    SourceModel model =
        existingOrNull != null && opts.mergeIntoExisting() ? existingOrNull : new SourceModel();
    if (Utils.isEmpty(model.getName())) {
      String name =
          !Utils.isEmpty(opts.modelName())
              ? opts.modelName()
              : !Utils.isEmpty(harvest.getResourceGroupName())
                  ? harvest.getResourceGroupName() + "-sources"
                  : "harvested-sources";
      model.setName(sanitizeName(name));
    }
    if (!Utils.isEmpty(opts.modelDescription())) {
      model.setDescription(opts.modelDescription());
    } else if (Utils.isEmpty(model.getDescription())) {
      model.setDescription(
          "Generated from schema harvest "
              + Const.NVL(harvest.getHarvestRunId(), "")
              + (Utils.isEmpty(harvest.getResourceGroupName())
                  ? ""
                  : " (group " + harvest.getResourceGroupName() + ")"));
    }

    SourceModelConfiguration config = model.getConfigurationOrDefault();
    List<String> warnings = new ArrayList<>();
    int tablesAdded = 0;
    int tablesUpdated = 0;

    // physical key -> logical SourceTable name
    Map<String, String> physicalToLogical = new HashMap<>();
    seedPhysicalMapFromModel(model, physicalToLogical);

    int layoutIndex = model.getTables().size();
    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject == null || subject.getDiscoveryStatus() != DiscoveryStatus.OK) {
        continue;
      }
      if (Utils.isEmpty(subject.getTableName()) || Utils.isEmpty(subject.getDatabaseMetaName())) {
        continue;
      }
      List<SourceField> discovered =
          SchemaHarvestModelCheckSupport.toDiscoveredSourceFields(subject.getFields());
      if (discovered.isEmpty()) {
        warnings.add(subject.getSubjectKey() + ": no DISCOVERED fields, skipped table");
        continue;
      }

      String connection = Const.NVL(subject.getDatabaseMetaName(), "");
      String schema = Const.NVL(subject.getSchemaName(), "");
      String tableName = Const.NVL(subject.getTableName(), "");
      String physicalKey = physicalKey(schema, tableName);

      SourceTable existing = findTableByPhysical(model, connection, schema, tableName);
      if (existing != null) {
        existing.setColumns(toSourceColumns(discovered));
        if (Utils.isEmpty(existing.getCatalogSourceName())
            && !Utils.isEmpty(subject.getSubjectKey())) {
          existing.setCatalogSourceName(catalogNameFromSubjectKey(subject.getSubjectKey()));
        }
        physicalToLogical.put(physicalKey, existing.getName());
        physicalToLogical.put(physicalKey(null, tableName), existing.getName());
        tablesUpdated++;
        continue;
      }

      String logicalName =
          uniqueLogicalName(model, sanitizeName(tableName), physicalToLogical.values());
      SourceTable table = new SourceTable(logicalName);
      table.setPhysicalType(DvSourceType.DATABASE);
      table.setDatabaseName(connection);
      table.setSchemaName(schema);
      table.setTableName(tableName);
      table.setColumns(toSourceColumns(discovered));
      table.setCatalogSourceName(catalogNameFromSubjectKey(subject.getSubjectKey()));
      table.setLocation(layoutPoint(layoutIndex++));
      model.getTables().add(table);
      physicalToLogical.put(physicalKey, logicalName);
      physicalToLogical.put(physicalKey(null, tableName), logicalName);
      tablesAdded++;

      if (Utils.isEmpty(config.getDefaultDatabase())) {
        config.setDefaultDatabase(connection);
      }
      if (Utils.isEmpty(config.getDefaultSchema()) && !Utils.isEmpty(schema)) {
        config.setDefaultSchema(schema);
      }
    }

    // Stub parents referenced by FKs but not harvested as subjects.
    if (opts.createStubParentTables()) {
      for (HarvestSubjectResult subject : harvest.subjectsView()) {
        if (subject == null || subject.getForeignKeys() == null) {
          continue;
        }
        for (HarvestedForeignKey fk : subject.getForeignKeys()) {
          if (fk == null || fk.getRole() != FieldRole.DISCOVERED) {
            continue;
          }
          String parentKey = physicalKey(fk.getParentSchema(), fk.getParentTable());
          if (Utils.isEmpty(fk.getParentTable()) || physicalToLogical.containsKey(parentKey)) {
            continue;
          }
          String bare = physicalKey(null, fk.getParentTable());
          if (physicalToLogical.containsKey(bare)) {
            continue;
          }
          String logical =
              uniqueLogicalName(
                  model, sanitizeName(fk.getParentTable()), physicalToLogical.values());
          SourceTable stub = new SourceTable(logical);
          stub.setPhysicalType(DvSourceType.DATABASE);
          stub.setDatabaseName(Const.NVL(subject.getDatabaseMetaName(), ""));
          stub.setSchemaName(Const.NVL(fk.getParentSchema(), ""));
          stub.setTableName(Const.NVL(fk.getParentTable(), ""));
          stub.setColumns(stubColumnsFromParent(fk));
          stub.setLocation(layoutPoint(layoutIndex++));
          stub.setDescription("Stub parent table created from harvested FK");
          model.getTables().add(stub);
          physicalToLogical.put(parentKey, logical);
          physicalToLogical.put(bare, logical);
          tablesAdded++;
          warnings.add("Created stub parent table " + logical + " for FK " + fk.displayLabel());
        }
      }
    }

    int relationshipsAdded =
        mergeRelationshipsFromHarvest(model, harvest, physicalToLogical, warnings);

    return new GenerateResult(model, tablesAdded, tablesUpdated, relationshipsAdded, warnings);
  }

  public static void save(SourceModel model, String filename, IVariables variables)
      throws HopException {
    if (model == null) {
      throw new HopException("Source model is required");
    }
    if (Utils.isEmpty(filename)) {
      throw new HopException("Source model filename is required");
    }
    ModelXmlWriteSupport.writeModelXml(SourceModel.XML_TAG, model, filename, variables);
    model.setFilename(filename);
    model.clearChanged();
  }

  static int mergeRelationshipsFromHarvest(
      SourceModel model,
      HarvestResult harvest,
      Map<String, String> physicalToLogical,
      List<String> warnings) {
    if (model == null || harvest == null) {
      return 0;
    }
    Set<String> existingKeys = existingRelationshipKeys(model);
    Set<String> usedNames = new HashSet<>();
    for (SourceRelationship rel : model.getRelationships()) {
      if (rel != null && !Utils.isEmpty(rel.getName())) {
        usedNames.add(rel.getName());
      }
    }

    int added = 0;
    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject == null || subject.getForeignKeys() == null) {
        continue;
      }
      for (HarvestedForeignKey fk : subject.getForeignKeys()) {
        if (fk == null || fk.getRole() != FieldRole.DISCOVERED) {
          continue;
        }
        String childLogical =
            resolveLogical(physicalToLogical, fk.getChildSchema(), fk.getChildTable());
        if (Utils.isEmpty(childLogical)) {
          childLogical =
              resolveLogical(physicalToLogical, subject.getSchemaName(), subject.getTableName());
        }
        String parentLogical =
            resolveLogical(physicalToLogical, fk.getParentSchema(), fk.getParentTable());
        if (Utils.isEmpty(childLogical) || Utils.isEmpty(parentLogical)) {
          warnings.add(
              "Skipped FK "
                  + fk.displayLabel()
                  + ": child or parent table not in model ("
                  + Const.NVL(fk.getChildTable(), "?")
                  + " → "
                  + Const.NVL(fk.getParentTable(), "?")
                  + ")");
          continue;
        }
        SourceRelationship relationship = new SourceRelationship();
        String baseName =
            !Utils.isEmpty(fk.getConstraintName())
                ? sanitizeName(fk.getConstraintName())
                : "fk_" + childLogical + "_" + parentLogical;
        relationship.setName(uniqueName(usedNames, baseName));
        relationship.setChildTableName(childLogical);
        relationship.setParentTableName(parentLogical);
        relationship.setChildColumns(splitList(fk.getChildColumns()));
        relationship.setParentColumns(splitList(fk.getParentColumns()));
        relationship.setDefaultJoinType(SourceJoinType.LEFT);
        relationship.setChildMultiplicity(SourceRelationshipMultiplicity.ZERO_OR_MANY);
        relationship.setParentMultiplicity(SourceRelationshipMultiplicity.ONE);
        relationship.setCardinality("0..N:1");
        relationship.setDescription(
            "From schema harvest " + Const.NVL(harvest.getHarvestRunId(), ""));

        String dedupe = relationshipDedupeKey(relationship);
        if (existingKeys.contains(dedupe)) {
          continue;
        }
        existingKeys.add(dedupe);
        usedNames.add(relationship.getName());
        model.getRelationships().add(relationship);
        added++;
      }
    }
    return added;
  }

  private static void seedPhysicalMapFromModel(
      SourceModel model, Map<String, String> physicalToLogical) {
    if (model == null) {
      return;
    }
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getTableName())) {
        continue;
      }
      physicalToLogical.putIfAbsent(
          physicalKey(table.getSchemaName(), table.getTableName()), table.getName());
      physicalToLogical.putIfAbsent(physicalKey(null, table.getTableName()), table.getName());
    }
  }

  private static SourceTable findTableByPhysical(
      SourceModel model, String connection, String schema, String tableName) {
    if (model == null) {
      return null;
    }
    for (SourceTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (!tableName.equalsIgnoreCase(Const.NVL(table.getTableName(), ""))) {
        continue;
      }
      if (!Utils.isEmpty(schema)
          && !schema.equalsIgnoreCase(Const.NVL(table.getSchemaName(), ""))) {
        continue;
      }
      if (!Utils.isEmpty(connection)
          && !connection.equalsIgnoreCase(Const.NVL(table.getDatabaseName(), ""))) {
        continue;
      }
      return table;
    }
    return null;
  }

  private static List<SourceColumn> toSourceColumns(List<SourceField> fields) {
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

  private static List<SourceColumn> stubColumnsFromParent(HarvestedForeignKey fk) {
    List<SourceColumn> columns = new ArrayList<>();
    List<String> parentCols = splitList(fk.getParentColumns());
    int pk = 1;
    for (String name : parentCols) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      SourceColumn column = new SourceColumn(name);
      column.setPrimaryKeyPosition(pk++);
      columns.add(column);
    }
    return columns;
  }

  private static Set<String> existingRelationshipKeys(SourceModel model) {
    Set<String> keys = new HashSet<>();
    if (model == null) {
      return keys;
    }
    for (SourceRelationship rel : model.getRelationships()) {
      if (rel != null) {
        keys.add(relationshipDedupeKey(rel));
      }
    }
    return keys;
  }

  private static String relationshipDedupeKey(SourceRelationship rel) {
    return normalize(rel.getChildTableName())
        + "|"
        + String.join(",", rel.getChildColumns()).toLowerCase(Locale.ROOT)
        + "->"
        + normalize(rel.getParentTableName())
        + "|"
        + String.join(",", rel.getParentColumns()).toLowerCase(Locale.ROOT);
  }

  private static String resolveLogical(
      Map<String, String> physicalToLogical, String schema, String table) {
    if (physicalToLogical == null || Utils.isEmpty(table)) {
      return null;
    }
    String withSchema = physicalToLogical.get(physicalKey(schema, table));
    if (!Utils.isEmpty(withSchema)) {
      return withSchema;
    }
    return physicalToLogical.get(physicalKey(null, table));
  }

  private static String physicalKey(String schema, String table) {
    return normalize(schema) + "|" + normalize(table);
  }

  private static String uniqueLogicalName(
      SourceModel model, String base, java.util.Collection<String> reserved) {
    String candidate = base;
    int i = 2;
    while (isLogicalNameTaken(model, candidate, reserved)) {
      candidate = base + "_" + i++;
    }
    return candidate;
  }

  private static boolean isLogicalNameTaken(
      SourceModel model, String candidate, java.util.Collection<String> reserved) {
    if (model.findTable(candidate) != null) {
      return true;
    }
    if (reserved == null) {
      return false;
    }
    for (String reservedName : reserved) {
      if (candidate.equalsIgnoreCase(reservedName)) {
        return true;
      }
    }
    return false;
  }

  private static String uniqueName(Set<String> used, String base) {
    String candidate = base;
    int i = 2;
    while (used.contains(candidate)) {
      candidate = base + "_" + i++;
    }
    return candidate;
  }

  private static String sanitizeName(String raw) {
    if (Utils.isEmpty(raw)) {
      return "table";
    }
    String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_\\-]+", "_");
    if (cleaned.isEmpty()) {
      return "table";
    }
    return cleaned;
  }

  private static String catalogNameFromSubjectKey(String subjectKey) {
    if (Utils.isEmpty(subjectKey)) {
      return null;
    }
    int slash = subjectKey.lastIndexOf('/');
    return slash >= 0 && slash < subjectKey.length() - 1
        ? subjectKey.substring(slash + 1)
        : subjectKey;
  }

  private static List<String> splitList(String columns) {
    List<String> result = new ArrayList<>();
    if (Utils.isEmpty(columns)) {
      return result;
    }
    for (String part : columns.split(",")) {
      if (!Utils.isEmpty(part)) {
        result.add(part.trim());
      }
    }
    return result;
  }

  private static Point layoutPoint(int index) {
    int col = index % DatabaseSchemaImportSupport.LAYOUT_COLUMNS;
    int row = index / DatabaseSchemaImportSupport.LAYOUT_COLUMNS;
    return new Point(
        DatabaseSchemaImportSupport.LAYOUT_ORIGIN_X
            + col * DatabaseSchemaImportSupport.LAYOUT_STEP_X,
        DatabaseSchemaImportSupport.LAYOUT_ORIGIN_Y
            + row * DatabaseSchemaImportSupport.LAYOUT_STEP_Y);
  }

  private static String normalize(String value) {
    return Const.NVL(value, "").trim().toLowerCase(Locale.ROOT);
  }
}
