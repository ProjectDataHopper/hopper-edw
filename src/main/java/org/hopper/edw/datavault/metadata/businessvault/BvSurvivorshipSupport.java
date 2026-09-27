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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Rank and null-policy rules that switch one SCD2 table onto {@code SurvivorshipMerge}. */
public final class BvSurvivorshipSupport {

  private static final Class<?> PKG = BvSurvivorshipSupport.class;

  private BvSurvivorshipSupport() {}

  public static boolean usesSurvivorship(BvScd2Table scd2Table) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return false;
    }
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping != null && !Utils.isEmpty(mapping.getRank())) {
        return true;
      }
    }
    return false;
  }

  /** Positive ranks win in ascending order. Blank or invalid text is {@code null}. */
  public static Integer parseRank(String rank) {
    if (Utils.isEmpty(rank)) {
      return null;
    }
    try {
      int value = Integer.parseInt(rank.trim());
      return value >= 1 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Mapping policy, then the leg default, then the calendar. A delta source inherits nulls. Full
   * and seed sources write them. No calendar means apply.
   */
  public static BvNullPolicy resolveNullPolicy(
      BvNullPolicy mappingPolicy,
      BvNullPolicy legDefault,
      BvSourceCalendar calendar,
      String sourceId) {
    if (mappingPolicy != null) {
      return mappingPolicy;
    }
    if (legDefault != null) {
      return legDefault;
    }
    if (dominantMode(calendar, sourceId) == BvSourceCalendarMode.DELTA) {
      return BvNullPolicy.INHERIT;
    }
    return BvNullPolicy.APPLY;
  }

  public static void validateRanks(
      List<ICheckResult> remarks, BvScd2Table scd2Table, IVariables variables) {
    if (scd2Table == null || scd2Table.getFieldMappings() == null) {
      return;
    }
    Map<String, List<BvScd2FieldMapping>> byTarget = new LinkedHashMap<>();
    for (BvScd2FieldMapping mapping : scd2Table.getFieldMappings()) {
      if (mapping == null || Utils.isEmpty(mapping.getTargetFieldName())) {
        continue;
      }
      String target =
          variables == null
              ? mapping.getTargetFieldName().trim()
              : variables.resolve(mapping.getTargetFieldName()).trim();
      if (Utils.isEmpty(target)) {
        continue;
      }
      byTarget.computeIfAbsent(target.toLowerCase(), key -> new ArrayList<>()).add(mapping);
    }
    for (List<BvScd2FieldMapping> group : byTarget.values()) {
      String targetName = group.get(0).getTargetFieldName();
      boolean allRanked = group.stream().allMatch(mapping -> !Utils.isEmpty(mapping.getRank()));
      if (group.size() > 1 && !allRanked) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    BvScd2FieldMappingValidationSupport.class,
                    "BvScd2FieldMappingValidationSupport.Error.DuplicateTargetField",
                    scd2Table.getName(),
                    targetName),
                scd2Table));
        continue;
      }
      Set<Integer> seen = new HashSet<>();
      for (BvScd2FieldMapping mapping : group) {
        if (Utils.isEmpty(mapping.getRank())) {
          continue;
        }
        Integer rank = parseRank(mapping.getRank());
        if (rank == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSurvivorshipSupport.CheckResult.InvalidRank",
                      scd2Table.getName(),
                      targetName,
                      mapping.getRank()),
                  scd2Table));
          continue;
        }
        if (!seen.add(rank)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvSurvivorshipSupport.CheckResult.DuplicateRank",
                      scd2Table.getName(),
                      targetName,
                      mapping.getRank()),
                  scd2Table));
        }
      }
    }
    if (usesSurvivorship(scd2Table) && !scd2Table.isIncludeHashKey()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvSurvivorshipSupport.CheckResult.NeedsHashKey", scd2Table.getName()),
              scd2Table));
    }
    if (usesSurvivorship(scd2Table) && scd2Table.isIncrementalBuild()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "BvSurvivorshipSupport.CheckResult.FullRebuild",
                  scd2Table.getName()),
              scd2Table));
    }
  }

  static BvSourceCalendarMode dominantMode(BvSourceCalendar calendar, String sourceId) {
    if (calendar == null || Utils.isEmpty(sourceId) || calendar.getEntries() == null) {
      return null;
    }
    boolean delta = false;
    boolean fullOrSeed = false;
    for (BvSourceCalendarEntry entry : calendar.getEntries()) {
      if (entry == null || Utils.isEmpty(entry.getSourceId())) {
        continue;
      }
      if (!sourceId.equalsIgnoreCase(entry.getSourceId().trim())) {
        continue;
      }
      BvSourceCalendarMode mode = entry.getModeOrDefault();
      if (mode == BvSourceCalendarMode.DELTA) {
        delta = true;
      } else {
        fullOrSeed = true;
      }
    }
    if (delta && !fullOrSeed) {
      return BvSourceCalendarMode.DELTA;
    }
    if (fullOrSeed) {
      return BvSourceCalendarMode.FULL;
    }
    return null;
  }
}
