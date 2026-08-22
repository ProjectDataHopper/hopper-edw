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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogRefreshSupport;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Applies harvested DISCOVERED field layouts and/or foreign keys onto working-tree catalog source
 * contracts so subsequent harvests can enforce real FK drift (not INFO inventory only).
 */
public final class SchemaHarvestCatalogApplySupport {

  private SchemaHarvestCatalogApplySupport() {}

  public record ApplyOptions(boolean applyForeignKeys, boolean applyDiscoveredFields) {
    public static ApplyOptions fksOnly() {
      return new ApplyOptions(true, false);
    }

    public static ApplyOptions fieldsAndFks() {
      return new ApplyOptions(true, true);
    }
  }

  public record ApplyResult(
      int subjectsUpdated,
      int subjectsSkipped,
      int foreignKeyConstraintsApplied,
      int fieldsUpdated,
      List<String> messages) {}

  public static ApplyResult apply(
      HarvestResult harvest,
      String catalogConnectionOverride,
      ApplyOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (harvest == null) {
      throw new HopException("Harvest result is required");
    }
    ApplyOptions opts = options != null ? options : ApplyOptions.fksOnly();
    if (!opts.applyForeignKeys() && !opts.applyDiscoveredFields()) {
      return new ApplyResult(0, 0, 0, 0, List.of("Nothing to apply (both options off)"));
    }

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    int updated = 0;
    int skipped = 0;
    int fkCount = 0;
    int fieldCount = 0;
    List<String> messages = new ArrayList<>();
    Date now = new Date();

    for (HarvestSubjectResult subject : harvest.subjectsView()) {
      if (subject == null || Utils.isEmpty(subject.getSubjectKey())) {
        continue;
      }
      if (subject.getDiscoveryStatus() != DiscoveryStatus.OK) {
        skipped++;
        continue;
      }
      String catalogConnection =
          !Utils.isEmpty(catalogConnectionOverride)
              ? catalogConnectionOverride
              : subject.getCatalogConnection();
      if (Utils.isEmpty(catalogConnection)) {
        skipped++;
        messages.add(subject.getSubjectKey() + ": no catalog connection");
        continue;
      }
      RecordDefinitionKey key = parseSubjectKey(subject.getSubjectKey());
      RecordDefinition definition =
          registry.read(catalogConnection, key, variables, metadataProvider);
      if (definition == null || definition.getDvSource() == null) {
        skipped++;
        messages.add(subject.getSubjectKey() + ": catalog record not found");
        continue;
      }

      boolean changed = false;
      DvSourceRecord dvSource = definition.getDvSource();
      List<CatalogSourceField> catalogFields =
          dvSource.getFields() != null ? new ArrayList<>(dvSource.getFields()) : new ArrayList<>();

      if (opts.applyDiscoveredFields()) {
        List<SourceField> discovered = discoveredSourceFields(subject.getFields());
        if (!discovered.isEmpty()) {
          // Preserve FK attrs when only fields are refreshed then re-apply FKs below.
          RecordDefinitionCatalogRefreshSupport.applyDiscoveredFields(definition, discovered, now);
          catalogFields =
              definition.getDvSource().getFields() != null
                  ? new ArrayList<>(definition.getDvSource().getFields())
                  : new ArrayList<>();
          fieldCount += discovered.size();
          changed = true;
        }
      }

      int appliedFks = 0;
      if (opts.applyForeignKeys()) {
        List<HarvestedForeignKey> discoveredFks = discoveredForeignKeys(subject.getForeignKeys());
        appliedFks = applyForeignKeysToCatalogFields(catalogFields, discoveredFks);
        if (appliedFks > 0 || !discoveredFks.isEmpty()) {
          // Always write FK clear/set so a re-apply with zero FKs clears stale catalog FKs.
          dvSource.setFields(catalogFields);
          // Keep CatalogSourceField FKs; only rebuild transient IRowMeta for Hop APIs.
          definition.setFields(DvSourceFieldSupport.toRowMetaFromCatalog(catalogFields, variables));
          changed = true;
          fkCount += appliedFks;
        }
      }

      if (!changed) {
        skipped++;
        continue;
      }

      RecordOrigin origin = definition.getOrigin();
      if (origin == null) {
        origin = new RecordOrigin();
        definition.setOrigin(origin);
      }
      origin.setLastDiscoveredAt(now);
      origin.setUpdatedAt(now);

      definition.validate();
      registry.upsert(catalogConnection, definition, variables, metadataProvider);
      updated++;
      messages.add(
          subject.getSubjectKey()
              + ": updated"
              + (opts.applyDiscoveredFields() ? " fields" : "")
              + (opts.applyForeignKeys() ? " fks=" + appliedFks : ""));
    }

    return new ApplyResult(updated, skipped, fkCount, fieldCount, messages);
  }

  /**
   * Clears FK attributes on all fields, then stamps DISCOVERED foreign keys onto matching child
   * columns. Returns number of FK constraints applied.
   */
  static int applyForeignKeysToCatalogFields(
      List<CatalogSourceField> catalogFields, List<HarvestedForeignKey> discoveredFks) {
    if (catalogFields == null) {
      return 0;
    }
    // Clear previous FK contract.
    for (CatalogSourceField field : catalogFields) {
      if (field == null) {
        continue;
      }
      field.setFkConstraintName(null);
      field.setFkPosition(0);
      field.setFkReferencedSchema(null);
      field.setFkReferencedTable(null);
      field.setFkReferencedColumn(null);
    }
    if (discoveredFks == null || discoveredFks.isEmpty()) {
      return 0;
    }

    Map<String, CatalogSourceField> byName = indexCatalogFields(catalogFields);
    int applied = 0;
    for (HarvestedForeignKey fk : discoveredFks) {
      if (fk == null || Utils.isEmpty(fk.getChildColumns())) {
        continue;
      }
      String[] childCols = splitColumns(fk.getChildColumns());
      String[] parentCols = splitColumns(fk.getParentColumns());
      if (childCols.length == 0) {
        continue;
      }
      boolean any = false;
      for (int i = 0; i < childCols.length; i++) {
        CatalogSourceField field = byName.get(normalize(childCols[i]));
        if (field == null) {
          continue;
        }
        field.setFkConstraintName(Const.NVL(fk.getConstraintName(), ""));
        field.setFkPosition(i + 1);
        field.setFkReferencedSchema(Const.NVL(fk.getParentSchema(), ""));
        field.setFkReferencedTable(Const.NVL(fk.getParentTable(), ""));
        field.setFkReferencedColumn(i < parentCols.length ? Const.NVL(parentCols[i], "") : "");
        any = true;
      }
      if (any) {
        applied++;
      }
    }
    return applied;
  }

  static List<SourceField> discoveredSourceFields(List<HarvestedField> fields) {
    return SchemaHarvestModelCheckSupport.toDiscoveredSourceFields(fields);
  }

  static List<HarvestedForeignKey> discoveredForeignKeys(List<HarvestedForeignKey> foreignKeys) {
    List<HarvestedForeignKey> result = new ArrayList<>();
    if (foreignKeys == null) {
      return result;
    }
    for (HarvestedForeignKey fk : foreignKeys) {
      if (fk != null && fk.getRole() == FieldRole.DISCOVERED) {
        result.add(fk);
      }
    }
    return result;
  }

  private static Map<String, CatalogSourceField> indexCatalogFields(
      List<CatalogSourceField> fields) {
    Map<String, CatalogSourceField> map = new LinkedHashMap<>();
    for (CatalogSourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      map.putIfAbsent(normalize(field.getName()), field);
    }
    return map;
  }

  private static String[] splitColumns(String columns) {
    if (Utils.isEmpty(columns)) {
      return new String[0];
    }
    String[] parts = columns.split(",");
    List<String> cleaned = new ArrayList<>();
    for (String part : parts) {
      if (!Utils.isEmpty(part)) {
        cleaned.add(part.trim());
      }
    }
    return cleaned.toArray(new String[0]);
  }

  private static String normalize(String name) {
    return Const.NVL(name, "").trim().toLowerCase(Locale.ROOT);
  }

  private static RecordDefinitionKey parseSubjectKey(String subjectKey) {
    int slash = subjectKey.lastIndexOf('/');
    if (slash <= 0) {
      return new RecordDefinitionKey("", subjectKey);
    }
    return new RecordDefinitionKey(subjectKey.substring(0, slash), subjectKey.substring(slash + 1));
  }
}
