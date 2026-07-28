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

package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;

/**
 * Compares two {@link LineageSnapshot} instances and classifies renames, mapping changes, and
 * add/remove drift (VaultSpeed-style opacity guard).
 */
public final class LineageSnapshotDiffSupport {

  private LineageSnapshotDiffSupport() {}

  public static LineageDiffResult compare(
      LineageSnapshot baseline, LineageSnapshot current, String baselineSource) {
    String modelName =
        current != null && !Utils.isEmpty(current.getModelName())
            ? current.getModelName()
            : (baseline != null ? baseline.getModelName() : null);
    LineageLayer layer =
        current != null && current.getModelLayer() != null
            ? current.getModelLayer()
            : (baseline != null ? baseline.getModelLayer() : LineageLayer.DV);
    LineageDiffResult result = new LineageDiffResult(modelName, layer, baselineSource);

    if (baseline == null || baseline.getTables().isEmpty()) {
      result.setBaselineMissing(true);
      if (current != null) {
        for (TableLineage table : current.getTables()) {
          if (table == null || Utils.isEmpty(table.getLogicalName())) {
            continue;
          }
          result.add(
              new LineageDiffEntry(
                  LineageDiffType.TABLE_ADDED,
                  LineageDiffSeverity.INFO,
                  table.getLogicalName(),
                  null,
                  null,
                  table.getLogicalName(),
                  "No baseline lineage in catalog; table present in current model only."));
        }
      }
      return result;
    }
    if (current == null) {
      for (TableLineage table : baseline.getTables()) {
        if (table == null || Utils.isEmpty(table.getLogicalName())) {
          continue;
        }
        result.add(
            new LineageDiffEntry(
                LineageDiffType.TABLE_REMOVED,
                LineageDiffSeverity.WARNING,
                table.getLogicalName(),
                null,
                table.getLogicalName(),
                null,
                "Table present in baseline lineage but missing from current model."));
      }
      return result;
    }

    Map<String, TableLineage> baseByLogical = indexByLogical(baseline.getTables());
    Map<String, TableLineage> currByLogical = indexByLogical(current.getTables());
    Map<String, TableLineage> baseByFingerprint = indexByFingerprint(baseline.getTables());
    Map<String, TableLineage> currByFingerprint = indexByFingerprint(current.getTables());

    Set<String> matchedBase = new LinkedHashSet<>();
    Set<String> matchedCurr = new LinkedHashSet<>();

    // Exact logical-name matches
    for (Map.Entry<String, TableLineage> entry : currByLogical.entrySet()) {
      String logical = entry.getKey();
      TableLineage curr = entry.getValue();
      TableLineage base = baseByLogical.get(logical);
      if (base != null) {
        matchedBase.add(logicalKey(base));
        matchedCurr.add(logicalKey(curr));
        compareTables(result, base, curr);
      }
    }

    // Remaining: try fingerprint rename detection
    for (Map.Entry<String, TableLineage> entry : currByLogical.entrySet()) {
      TableLineage curr = entry.getValue();
      if (matchedCurr.contains(logicalKey(curr))) {
        continue;
      }
      String fp = fingerprint(curr);
      if (Utils.isEmpty(fp)) {
        continue;
      }
      TableLineage base = baseByFingerprint.get(fp);
      if (base != null && !matchedBase.contains(logicalKey(base))) {
        matchedBase.add(logicalKey(base));
        matchedCurr.add(logicalKey(curr));
        boolean physicalChanged =
            !equalsIgnoreCase(base.getPhysicalTableName(), curr.getPhysicalTableName());
        boolean logicalChanged =
            !equalsIgnoreCase(base.getLogicalName(), curr.getLogicalName());
        if (logicalChanged || physicalChanged) {
          LineageDiffSeverity severity =
              physicalChanged && !hasExplicitNameReason(curr)
                  ? LineageDiffSeverity.BLOCKING
                  : LineageDiffSeverity.WARNING;
          result.add(
              new LineageDiffEntry(
                  LineageDiffType.TABLE_RENAMED,
                  severity,
                  curr.getLogicalName(),
                  null,
                  describeTable(base),
                  describeTable(curr),
                  "Table appears renamed (same source field fingerprint): "
                      + describeTable(base)
                      + " → "
                      + describeTable(curr)
                      + (severity == LineageDiffSeverity.BLOCKING
                          ? " without USER_EXPLICIT_NAME evidence."
                          : ".")));
        }
        compareTables(result, base, curr);
      }
    }

    // Remaining current = added
    for (TableLineage curr : current.getTables()) {
      if (curr == null || Utils.isEmpty(curr.getLogicalName())) {
        continue;
      }
      if (!matchedCurr.contains(logicalKey(curr))) {
        result.add(
            new LineageDiffEntry(
                LineageDiffType.TABLE_ADDED,
                LineageDiffSeverity.INFO,
                curr.getLogicalName(),
                null,
                null,
                describeTable(curr),
                "New table in current model not present in baseline lineage."));
      }
    }

    // Remaining baseline = removed
    for (TableLineage base : baseline.getTables()) {
      if (base == null || Utils.isEmpty(base.getLogicalName())) {
        continue;
      }
      if (!matchedBase.contains(logicalKey(base))) {
        result.add(
            new LineageDiffEntry(
                LineageDiffType.TABLE_REMOVED,
                LineageDiffSeverity.WARNING,
                base.getLogicalName(),
                null,
                describeTable(base),
                null,
                "Table removed from current model (present in baseline lineage)."));
      }
    }

    return result;
  }

  private static void compareTables(
      LineageDiffResult result, TableLineage base, TableLineage curr) {
    if (!equalsIgnoreCase(base.getPhysicalTableName(), curr.getPhysicalTableName())
        && equalsIgnoreCase(base.getLogicalName(), curr.getLogicalName())) {
      LineageDiffSeverity severity =
          hasExplicitNameReason(curr) ? LineageDiffSeverity.WARNING : LineageDiffSeverity.BLOCKING;
      result.add(
          new LineageDiffEntry(
              LineageDiffType.TABLE_RENAMED,
              severity,
              curr.getLogicalName(),
              null,
              base.getPhysicalTableName(),
              curr.getPhysicalTableName(),
              "Physical table name changed for "
                  + curr.getLogicalName()
                  + ": "
                  + nvl(base.getPhysicalTableName())
                  + " → "
                  + nvl(curr.getPhysicalTableName())));
    }

    Map<String, FieldLineage> baseFields = indexFields(base);
    Map<String, FieldLineage> currFields = indexFields(curr);
    Map<String, String> baseBySource = fieldBySourceKey(base);
    Map<String, String> currBySource = fieldBySourceKey(curr);

    Set<String> matchedBaseFields = new LinkedHashSet<>();
    Set<String> matchedCurrFields = new LinkedHashSet<>();

    for (Map.Entry<String, FieldLineage> entry : currFields.entrySet()) {
      String name = entry.getKey();
      FieldLineage currField = entry.getValue();
      FieldLineage baseField = baseFields.get(name);
      if (baseField != null) {
        matchedBaseFields.add(name);
        matchedCurrFields.add(name);
        compareFieldMappings(result, curr.getLogicalName(), baseField, currField);
      }
    }

    // Field renames via source contribution fingerprint
    for (Map.Entry<String, String> entry : currBySource.entrySet()) {
      String sourceKey = entry.getKey();
      String currTarget = entry.getValue();
      if (matchedCurrFields.contains(norm(currTarget))) {
        continue;
      }
      String baseTarget = baseBySource.get(sourceKey);
      if (baseTarget != null && !matchedBaseFields.contains(norm(baseTarget))) {
        matchedBaseFields.add(norm(baseTarget));
        matchedCurrFields.add(norm(currTarget));
        result.add(
            new LineageDiffEntry(
                LineageDiffType.FIELD_RENAMED,
                LineageDiffSeverity.WARNING,
                curr.getLogicalName(),
                currTarget,
                baseTarget,
                currTarget,
                "Field renamed on "
                    + curr.getLogicalName()
                    + " (same source "
                    + sourceKey
                    + "): "
                    + baseTarget
                    + " → "
                    + currTarget));
      }
    }

    for (String name : currFields.keySet()) {
      if (!matchedCurrFields.contains(name)) {
        result.add(
            new LineageDiffEntry(
                LineageDiffType.FIELD_ADDED,
                LineageDiffSeverity.INFO,
                curr.getLogicalName(),
                name,
                null,
                name,
                "Field added on " + curr.getLogicalName() + ": " + name));
      }
    }
    for (String name : baseFields.keySet()) {
      if (!matchedBaseFields.contains(name)) {
        result.add(
            new LineageDiffEntry(
                LineageDiffType.FIELD_REMOVED,
                LineageDiffSeverity.WARNING,
                curr.getLogicalName(),
                name,
                name,
                null,
                "Field removed from " + curr.getLogicalName() + ": " + name));
      }
    }
  }

  private static void compareFieldMappings(
      LineageDiffResult result, String tableName, FieldLineage base, FieldLineage curr) {
    String baseSources = contributionSignature(base);
    String currSources = contributionSignature(curr);
    if (!Objects.equals(baseSources, currSources)) {
      result.add(
          new LineageDiffEntry(
              LineageDiffType.MAPPING_CHANGED,
              LineageDiffSeverity.WARNING,
              tableName,
              curr.getTargetFieldName(),
              baseSources,
              currSources,
              "Source mapping changed for "
                  + tableName
                  + "."
                  + curr.getTargetFieldName()
                  + ": "
                  + baseSources
                  + " → "
                  + currSources));
    }
  }

  private static Map<String, TableLineage> indexByLogical(List<TableLineage> tables) {
    Map<String, TableLineage> map = new LinkedHashMap<>();
    if (tables == null) {
      return map;
    }
    for (TableLineage table : tables) {
      if (table == null || Utils.isEmpty(table.getLogicalName())) {
        continue;
      }
      map.putIfAbsent(norm(table.getLogicalName()), table);
    }
    return map;
  }

  private static Map<String, TableLineage> indexByFingerprint(List<TableLineage> tables) {
    Map<String, TableLineage> map = new LinkedHashMap<>();
    if (tables == null) {
      return map;
    }
    for (TableLineage table : tables) {
      if (table == null) {
        continue;
      }
      String fp = fingerprint(table);
      if (!Utils.isEmpty(fp)) {
        map.putIfAbsent(fp, table);
      }
    }
    return map;
  }

  private static Map<String, FieldLineage> indexFields(TableLineage table) {
    Map<String, FieldLineage> map = new LinkedHashMap<>();
    if (table == null || table.getFields() == null) {
      return map;
    }
    for (FieldLineage field : table.getFields()) {
      if (field == null || Utils.isEmpty(field.getTargetFieldName())) {
        continue;
      }
      map.putIfAbsent(norm(field.getTargetFieldName()), field);
    }
    return map;
  }

  private static Map<String, String> fieldBySourceKey(TableLineage table) {
    Map<String, String> map = new LinkedHashMap<>();
    if (table == null || table.getFields() == null) {
      return map;
    }
    for (FieldLineage field : table.getFields()) {
      if (field == null || field.isTechnical()) {
        continue;
      }
      for (FieldContribution contribution : field.getContributions()) {
        if (contribution == null || Utils.isEmpty(contribution.getSourceFieldName())) {
          continue;
        }
        String key =
            nvl(contribution.getSourceName()).toLowerCase(Locale.ROOT)
                + "."
                + contribution.getSourceFieldName().toLowerCase(Locale.ROOT);
        map.putIfAbsent(key, field.getTargetFieldName());
      }
    }
    return map;
  }

  /**
   * Stable fingerprint of non-technical source contributions for rename detection across table
   * renames.
   */
  static String fingerprint(TableLineage table) {
    if (table == null || table.getFields() == null) {
      return "";
    }
    Set<String> keys = new LinkedHashSet<>();
    for (FieldLineage field : table.getFields()) {
      if (field == null || field.isTechnical()) {
        continue;
      }
      for (FieldContribution contribution : field.getContributions()) {
        if (contribution == null || Utils.isEmpty(contribution.getSourceFieldName())) {
          continue;
        }
        keys.add(
            nvl(contribution.getSourceName()).toLowerCase(Locale.ROOT)
                + "."
                + contribution.getSourceFieldName().toLowerCase(Locale.ROOT));
      }
    }
    if (keys.isEmpty()) {
      // Fall back to ordered source feed names
      if (table.getSources() != null) {
        for (TableSourceRef source : table.getSources()) {
          if (source != null && !Utils.isEmpty(source.getName())) {
            keys.add("feed:" + source.getName().toLowerCase(Locale.ROOT));
          }
        }
      }
    }
    return keys.stream().sorted().collect(Collectors.joining("|"));
  }

  private static String contributionSignature(FieldLineage field) {
    if (field == null || field.getContributions().isEmpty()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (FieldContribution contribution : field.getContributions()) {
      if (contribution == null) {
        continue;
      }
      parts.add(
          nvl(contribution.getSourceName())
              + "."
              + nvl(contribution.getSourceFieldName())
              + "/"
              + (contribution.getTransform() != null ? contribution.getTransform().name() : ""));
    }
    return String.join(";", parts);
  }

  private static boolean hasExplicitNameReason(TableLineage table) {
    if (table == null || table.getReasons() == null) {
      return false;
    }
    return table.getReasons().stream()
        .anyMatch(r -> r != null && r.getCode() == LineageReasonCode.USER_EXPLICIT_NAME);
  }

  private static String describeTable(TableLineage table) {
    if (table == null) {
      return "";
    }
    String logical = nvl(table.getLogicalName());
    String physical = nvl(table.getPhysicalTableName());
    if (logical.equals(physical) || Utils.isEmpty(physical)) {
      return logical;
    }
    return logical + " [" + physical + "]";
  }

  private static String logicalKey(TableLineage table) {
    return table == null ? "" : norm(table.getLogicalName());
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equalsIgnoreCase(b);
  }

  private static String norm(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
