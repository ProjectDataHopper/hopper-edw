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
package org.hopper.edw.catalog.harvest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.database.DiscoveredForeignKey;

/** Compares catalog (or empty) FK contracts to live discovered foreign keys. */
public final class SchemaHarvestFkDiffSupport {

  private SchemaHarvestFkDiffSupport() {}

  public static List<HarvestedForeignKey> fromDiscovered(
      List<DiscoveredForeignKey> discovered, FieldRole role) {
    List<HarvestedForeignKey> result = new ArrayList<>();
    if (discovered == null) {
      return result;
    }
    for (DiscoveredForeignKey fk : discovered) {
      if (fk == null || !fk.isValid()) {
        continue;
      }
      result.add(
          HarvestedForeignKey.builder()
              .role(role)
              .constraintName(Const.NVL(fk.getConstraintName(), ""))
              .childSchema(Const.NVL(fk.getChildSchema(), ""))
              .childTable(Const.NVL(fk.getChildTable(), ""))
              .childColumns(String.join(",", fk.getChildColumns()))
              .parentSchema(Const.NVL(fk.getParentSchema(), ""))
              .parentTable(Const.NVL(fk.getParentTable(), ""))
              .parentColumns(String.join(",", fk.getParentColumns()))
              .build());
    }
    return result;
  }

  /**
   * Builds expected FK constraints from optional catalog field FK attributes (grouped by constraint
   * + parent table).
   */
  public static List<HarvestedForeignKey> fromCatalogFields(
      List<CatalogSourceField> fields, String childSchema, String childTable) {
    if (fields == null || fields.isEmpty()) {
      return List.of();
    }
    // key: constraint|parentSchema|parentTable
    Map<String, List<CatalogSourceField>> groups = new LinkedHashMap<>();
    for (CatalogSourceField field : fields) {
      if (field == null
          || field.getFkPosition() <= 0
          || Utils.isEmpty(field.getFkReferencedTable())) {
        continue;
      }
      String key =
          Const.NVL(field.getFkConstraintName(), "")
              + "|"
              + Const.NVL(field.getFkReferencedSchema(), "")
              + "|"
              + Const.NVL(field.getFkReferencedTable(), "");
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(field);
    }
    List<HarvestedForeignKey> result = new ArrayList<>();
    for (Map.Entry<String, List<CatalogSourceField>> entry : groups.entrySet()) {
      List<CatalogSourceField> cols = entry.getValue();
      cols.sort(
          (a, b) ->
              Integer.compare(Math.max(0, a.getFkPosition()), Math.max(0, b.getFkPosition())));
      CatalogSourceField first = cols.get(0);
      List<String> childCols = new ArrayList<>();
      List<String> parentCols = new ArrayList<>();
      for (CatalogSourceField col : cols) {
        childCols.add(Const.NVL(col.getName(), ""));
        parentCols.add(Const.NVL(col.getFkReferencedColumn(), ""));
      }
      result.add(
          HarvestedForeignKey.builder()
              .role(FieldRole.EXPECTED)
              .constraintName(Const.NVL(first.getFkConstraintName(), ""))
              .childSchema(Const.NVL(childSchema, ""))
              .childTable(Const.NVL(childTable, ""))
              .childColumns(String.join(",", childCols))
              .parentSchema(Const.NVL(first.getFkReferencedSchema(), ""))
              .parentTable(Const.NVL(first.getFkReferencedTable(), ""))
              .parentColumns(String.join(",", parentCols))
              .build());
    }
    return result;
  }

  /**
   * Diff expected vs discovered FK sets.
   *
   * <p>FKs are matched on the <em>child side</em> (table + child columns). Parent target or
   * constraint-name differences become {@code FOREIGN_KEY_CHANGED}. When the catalog has no FK
   * contract ({@code expected} empty), discovered-only FKs are {@code INFO} inventory rather than
   * gate failures.
   */
  public static List<HarvestChange> diff(
      List<HarvestedForeignKey> expected, List<HarvestedForeignKey> discovered) {
    List<HarvestChange> changes = new ArrayList<>();
    Map<String, HarvestedForeignKey> expectedByChild = indexByChildSide(expected);
    Map<String, HarvestedForeignKey> discoveredByChild = indexByChildSide(discovered);
    boolean catalogHasFkContract = !expectedByChild.isEmpty();

    for (Map.Entry<String, HarvestedForeignKey> entry : expectedByChild.entrySet()) {
      HarvestedForeignKey exp = entry.getValue();
      HarvestedForeignKey act = discoveredByChild.get(entry.getKey());
      if (act == null) {
        changes.add(
            HarvestChange.foreignKey(
                HarvestChange.KIND_FOREIGN_KEY_REMOVED,
                firstChildColumn(exp),
                exp.displayLabel(),
                "not present on live source",
                "BLOCKING"));
        continue;
      }
      if (!parentSideEqual(exp, act)) {
        changes.add(
            HarvestChange.foreignKey(
                HarvestChange.KIND_FOREIGN_KEY_CHANGED,
                firstChildColumn(exp),
                exp.displayLabel(),
                act.displayLabel(),
                "BLOCKING"));
      } else if (!namesEqual(exp.getConstraintName(), act.getConstraintName())) {
        changes.add(
            HarvestChange.foreignKey(
                HarvestChange.KIND_FOREIGN_KEY_CHANGED,
                firstChildColumn(exp),
                exp.displayLabel(),
                act.displayLabel() + " (constraint name differs)",
                "WARNING"));
      }
    }

    for (Map.Entry<String, HarvestedForeignKey> entry : discoveredByChild.entrySet()) {
      if (expectedByChild.containsKey(entry.getKey())) {
        continue;
      }
      HarvestedForeignKey act = entry.getValue();
      String severity = catalogHasFkContract ? "WARNING" : "INFO";
      changes.add(
          HarvestChange.foreignKey(
              HarvestChange.KIND_FOREIGN_KEY_ADDED,
              firstChildColumn(act),
              catalogHasFkContract ? "not in catalog contract" : "(no catalog FK contract)",
              act.displayLabel(),
              severity));
    }
    return changes;
  }

  /** Match key: child table + ordered child columns (parent side compared separately). */
  private static Map<String, HarvestedForeignKey> indexByChildSide(List<HarvestedForeignKey> keys) {
    Map<String, HarvestedForeignKey> map = new LinkedHashMap<>();
    if (keys == null) {
      return map;
    }
    for (HarvestedForeignKey fk : keys) {
      if (fk == null) {
        continue;
      }
      map.putIfAbsent(childSideKey(fk), fk);
    }
    return map;
  }

  private static String childSideKey(HarvestedForeignKey fk) {
    return normalize(fk.getChildSchema())
        + "."
        + normalize(fk.getChildTable())
        + ":"
        + normalize(fk.getChildColumns());
  }

  private static boolean parentSideEqual(HarvestedForeignKey a, HarvestedForeignKey b) {
    return normalize(a.getParentSchema()).equals(normalize(b.getParentSchema()))
        && normalize(a.getParentTable()).equals(normalize(b.getParentTable()))
        && normalize(a.getParentColumns()).equals(normalize(b.getParentColumns()));
  }

  private static boolean namesEqual(String a, String b) {
    return Const.NVL(a, "").trim().equalsIgnoreCase(Const.NVL(b, "").trim());
  }

  private static String firstChildColumn(HarvestedForeignKey fk) {
    if (fk == null || Utils.isEmpty(fk.getChildColumns())) {
      return null;
    }
    String[] parts = fk.getChildColumns().split(",");
    return parts.length > 0 ? parts[0].trim() : null;
  }

  private static String normalize(String value) {
    if (Utils.isEmpty(value)) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
  }
}
