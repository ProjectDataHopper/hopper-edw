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
package org.hopper.edw.datavault.metadata.targettypemapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingPatternSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Design-time checks for target type mapping profiles. */
public final class TargetTypeMappingValidationSupport {

  private static final Class<?> PKG = TargetTypeMappingMeta.class;

  private TargetTypeMappingValidationSupport() {}

  public static List<ICheckResult> checkProfile(TargetTypeMappingMeta mapping) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (mapping == null) {
      return remarks;
    }
    if (Utils.isEmpty(mapping.getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "TargetTypeMapping.Check.ProfileMissingName"),
              null));
    }
    if (mapping.getRules().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "TargetTypeMapping.Check.ProfileNoRules", nvl(mapping.getName())),
              null));
    }
    if (Utils.isEmpty(mapping.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "TargetTypeMapping.Check.NoTargetDatabase", nvl(mapping.getName())),
              null));
    }
    int index = 0;
    for (TargetTypeMappingRule rule : mapping.getRules()) {
      index++;
      if (rule == null || !rule.isEnabled()) {
        continue;
      }
      String label = ruleLabel(rule, index);
      if (!rule.hasMatchCriteria()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "TargetTypeMapping.Check.RuleNoMatchCriteria",
                    nvl(mapping.getName()),
                    label),
                null));
      }
      if (Utils.isEmpty(rule.getTargetSqlType())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "TargetTypeMapping.Check.RuleNoTargetSql", nvl(mapping.getName()), label),
                null));
      }
      if (!Utils.isEmpty(rule.getMatchHopType())
          && DataTypeMappingPatternSupport.hopTypeId(rule.getMatchHopType()) <= 0) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "TargetTypeMapping.Check.RuleUnknownMatchHopType",
                    nvl(mapping.getName()),
                    label,
                    rule.getMatchHopType()),
                null));
      }
      checkBoundOrder(
          remarks, mapping.getName(), label, rule.getMatchMinLength(), rule.getMatchMaxLength());
      checkBoundOrder(
          remarks,
          mapping.getName(),
          label,
          rule.getMatchMinPrecision(),
          rule.getMatchMaxPrecision());
      if (rule.isMatchLengthAbsent()
          && TargetTypeMappingResolver.containsPlaceholder(rule.getTargetSqlType(), "length")) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "TargetTypeMapping.Check.LengthAbsentWithLengthPlaceholder",
                    nvl(mapping.getName()),
                    label),
                null));
      }
      if (isBroadRule(rule)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "TargetTypeMapping.Check.BroadRule", nvl(mapping.getName()), label),
                null));
      }
    }
    return remarks;
  }

  public static List<ICheckResult> checkProject(IHopMetadataProvider metadataProvider)
      throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    if (metadataProvider == null) {
      return remarks;
    }
    List<TargetTypeMappingMeta> mappings = TargetTypeMappingSupport.listAll(metadataProvider);
    Map<String, List<String>> byDatabase = new HashMap<>();
    for (TargetTypeMappingMeta mapping : mappings) {
      remarks.addAll(checkProfile(mapping));
      if (mapping == null || Utils.isEmpty(mapping.getTargetDatabase())) {
        continue;
      }
      String key = mapping.getTargetDatabase().trim().toLowerCase(Locale.ROOT);
      byDatabase.computeIfAbsent(key, k -> new ArrayList<>()).add(nvl(mapping.getName()));
    }
    for (Map.Entry<String, List<String>> entry : byDatabase.entrySet()) {
      if (entry.getValue().size() > 1) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "TargetTypeMapping.Check.DuplicateTargetDatabase",
                    entry.getKey(),
                    String.join(", ", entry.getValue())),
                null));
      }
    }
    return remarks;
  }

  private static void checkBoundOrder(
      List<ICheckResult> remarks, String mappingName, String label, String minRaw, String maxRaw) {
    if (Utils.isEmpty(minRaw) || Utils.isEmpty(maxRaw)) {
      return;
    }
    if (minRaw.contains("${") || maxRaw.contains("${")) {
      return;
    }
    Integer min = TargetTypeMappingResolver.parseBound(minRaw, null);
    Integer max = TargetTypeMappingResolver.parseBound(maxRaw, null);
    if (min != null && max != null && min > max) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "TargetTypeMapping.Check.MinGreaterThanMax",
                  nvl(mappingName),
                  label,
                  minRaw,
                  maxRaw),
              null));
    }
  }

  private static boolean isBroadRule(TargetTypeMappingRule rule) {
    if (!Utils.isEmpty(rule.getMatchFieldNamePattern())
        || !Utils.isEmpty(rule.getMatchMinLength())
        || !Utils.isEmpty(rule.getMatchMaxLength())
        || !Utils.isEmpty(rule.getMatchMinPrecision())
        || !Utils.isEmpty(rule.getMatchMaxPrecision())
        || rule.isMatchLengthAbsent()) {
      return false;
    }
    return !Utils.isEmpty(rule.getMatchHopType());
  }

  private static String ruleLabel(TargetTypeMappingRule rule, int index) {
    if (!Utils.isEmpty(rule.getName())) {
      return rule.getName();
    }
    if (!Utils.isEmpty(rule.getId())) {
      return rule.getId();
    }
    return "#" + index;
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
