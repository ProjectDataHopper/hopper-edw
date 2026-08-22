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
package org.hopper.edw.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Resolves physical source fields through ordered data type mapping profiles and per-source field
 * overrides into an {@link EffectiveSourceField} layout.
 *
 * <p>Merge semantics (confirmed for #113):
 *
 * <ul>
 *   <li>Within a profile: first matching enabled rule establishes the base target for that field.
 *   <li>Across profiles (list order): attribute-level merge — later non-empty attributes override.
 *   <li>Per-source field overrides win last.
 * </ul>
 */
public final class DataTypeMappingResolver {

  private DataTypeMappingResolver() {}

  public static List<EffectiveSourceField> resolveAll(
      List<PhysicalSourceField> physicalFields,
      List<DataTypeMappingMeta> profiles,
      List<SourceFieldTypeMapping> overrides) {
    List<EffectiveSourceField> result = new ArrayList<>();
    if (physicalFields == null) {
      return result;
    }
    Map<String, SourceFieldTypeMapping> overrideByName = indexOverrides(overrides);
    for (PhysicalSourceField physical : physicalFields) {
      if (physical == null || Utils.isEmpty(physical.getName())) {
        continue;
      }
      SourceFieldTypeMapping override = overrideByName.get(normalizeKey(physical.getName()));
      result.add(resolve(physical, profiles, override));
    }
    return result;
  }

  public static EffectiveSourceField resolve(
      PhysicalSourceField physical,
      List<DataTypeMappingMeta> profiles,
      SourceFieldTypeMapping override) {
    EffectiveSourceField effective = baseline(physical);

    if (profiles != null) {
      for (DataTypeMappingMeta profile : profiles) {
        if (profile == null) {
          continue;
        }
        DataTypeMappingRule matched = firstMatchingRule(physical, profile);
        if (matched != null) {
          applyRule(effective, physical, profile, matched);
        }
      }
    }

    if (override != null && !override.isDisabled()) {
      applyOverride(effective, physical, override);
    }

    recomputeChangeFlags(effective, physical);
    return effective;
  }

  public static List<DataTypeMappingMeta> loadProfiles(
      IHopMetadataProvider metadataProvider, List<String> profileNames) throws HopException {
    List<DataTypeMappingMeta> loaded = new ArrayList<>();
    if (metadataProvider == null || profileNames == null) {
      return loaded;
    }
    var serializer = metadataProvider.getSerializer(DataTypeMappingMeta.class);
    for (String name : profileNames) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      DataTypeMappingMeta meta = serializer.load(name.trim());
      if (meta != null) {
        loaded.add(meta);
      }
    }
    return loaded;
  }

  private static EffectiveSourceField baseline(PhysicalSourceField physical) {
    EffectiveSourceField effective = new EffectiveSourceField();
    effective.setSourceFieldName(physical.getName());
    effective.setEffectiveFieldName(physical.getName());
    effective.setDescription(physical.getDescription());
    effective.setSourceDataType(physical.getSourceDataType());
    effective.setHopType(physical.effectiveHopType());
    effective.setLength(physical.getLength());
    effective.setPrecision(physical.getPrecision());
    effective.setPrimaryKeyPosition(physical.getPrimaryKeyPosition());
    effective.setConversion(new FieldConversionOptions(physical.getParseConversion()));
    return effective;
  }

  private static DataTypeMappingRule firstMatchingRule(
      PhysicalSourceField physical, DataTypeMappingMeta profile) {
    for (DataTypeMappingRule rule : profile.getRules()) {
      if (rule == null || !rule.isEnabled()) {
        continue;
      }
      if (!rule.hasMatchCriteria()) {
        continue;
      }
      if (matches(physical, rule)) {
        return rule;
      }
    }
    return null;
  }

  static boolean matches(PhysicalSourceField physical, DataTypeMappingRule rule) {
    if (physical == null || rule == null || !rule.hasMatchCriteria()) {
      return false;
    }
    String hopTypeName = DataTypeMappingPatternSupport.hopTypeName(physical.effectiveHopType());
    if (!DataTypeMappingPatternSupport.matchesHopType(
        rule.getMatchHopType(), physical.effectiveHopType(), hopTypeName)) {
      return false;
    }
    if (!DataTypeMappingPatternSupport.matches(
        rule.getMatchSourceDataTypePattern(), physical.getSourceDataType())) {
      return false;
    }
    if (!DataTypeMappingPatternSupport.matches(
        rule.getMatchFieldNamePattern(), physical.getName())) {
      return false;
    }
    if (rule.isMatchLengthAbsent() && !physical.isLengthAbsent()) {
      return false;
    }
    int length = physical.parseLengthOr(-1);
    if (!Utils.isEmpty(rule.getMatchLengthBelow())) {
      int below = parseInt(rule.getMatchLengthBelow(), Integer.MIN_VALUE);
      if (below != Integer.MIN_VALUE && (length < 0 || length >= below)) {
        return false;
      }
    }
    if (!Utils.isEmpty(rule.getMatchLengthAbove())) {
      int above = parseInt(rule.getMatchLengthAbove(), Integer.MIN_VALUE);
      if (above != Integer.MIN_VALUE && (length < 0 || length <= above)) {
        return false;
      }
    }
    return true;
  }

  private static void applyRule(
      EffectiveSourceField effective,
      PhysicalSourceField physical,
      DataTypeMappingMeta profile,
      DataTypeMappingRule rule) {
    String profileName = !Utils.isEmpty(profile.getName()) ? profile.getName() : "profile";
    String ruleLabel =
        !Utils.isEmpty(rule.getId())
            ? rule.getId()
            : (!Utils.isEmpty(rule.getName()) ? rule.getName() : "rule");
    effective.addProvenance(profileName + ":" + ruleLabel);

    if (rule.getTargetHopType() > IValueMeta.TYPE_NONE) {
      effective.setHopType(rule.getTargetHopType());
    }
    if (!Utils.isEmpty(rule.getTargetLength())) {
      effective.setLength(rule.getTargetLength().trim());
    }
    if (!Utils.isEmpty(rule.getTargetPrecision())) {
      effective.setPrecision(rule.getTargetPrecision().trim());
    }
    if (!Utils.isEmpty(rule.getTargetFieldName())) {
      effective.setEffectiveFieldName(rule.getTargetFieldName().trim());
    }
    effective.getConversion().mergeFrom(rule.getConversion());
  }

  private static void applyOverride(
      EffectiveSourceField effective,
      PhysicalSourceField physical,
      SourceFieldTypeMapping override) {
    effective.addProvenance("override:" + ConstNvl(override.getSourceFieldName()));
    if (override.getTargetHopType() > IValueMeta.TYPE_NONE) {
      effective.setHopType(override.getTargetHopType());
    }
    if (!Utils.isEmpty(override.getLength())) {
      effective.setLength(override.getLength().trim());
    }
    if (!Utils.isEmpty(override.getPrecision())) {
      effective.setPrecision(override.getPrecision().trim());
    }
    if (!Utils.isEmpty(override.getTargetFieldName())) {
      effective.setEffectiveFieldName(override.getTargetFieldName().trim());
    }
    effective.getConversion().mergeFrom(override.getConversion());
  }

  private static void recomputeChangeFlags(
      EffectiveSourceField effective, PhysicalSourceField physical) {
    int physicalType = physical.effectiveHopType();
    effective.setTypeChanged(effective.effectiveHopType() != physicalType);
    effective.setLengthChanged(!lengthEquals(physical.getLength(), effective.getLength()));
    effective.setRenamed(
        !normalizeKey(physical.getName()).equals(normalizeKey(effective.getEffectiveFieldName())));
    boolean conversionChanged =
        !conversionEquals(physical.getParseConversion(), effective.getConversion());
    // Also treat pure type/length-driven conversion as changed when conversion attrs exist
    // beyond parse baseline.
    if (effective.getConversion().hasAnyAttribute()
        && !physical.getParseConversion().hasAnyAttribute()) {
      conversionChanged = true;
    }
    effective.setConversionChanged(conversionChanged);
  }

  private static boolean lengthEquals(String a, String b) {
    int la = parseInt(a, -1);
    int lb = parseInt(b, -1);
    if (Utils.isEmpty(a) && Utils.isEmpty(b)) {
      return true;
    }
    if (Utils.isEmpty(a) != Utils.isEmpty(b)) {
      // empty vs -1 are both "absent"
      return la < 0 && lb < 0;
    }
    return la == lb;
  }

  private static boolean conversionEquals(FieldConversionOptions a, FieldConversionOptions b) {
    FieldConversionOptions left = a != null ? a : new FieldConversionOptions();
    FieldConversionOptions right = b != null ? b : new FieldConversionOptions();
    return ConstNvl(left.getConversionMask()).equals(ConstNvl(right.getConversionMask()))
        && ConstNvl(left.getDecimalSymbol()).equals(ConstNvl(right.getDecimalSymbol()))
        && ConstNvl(left.getGroupingSymbol()).equals(ConstNvl(right.getGroupingSymbol()))
        && ConstNvl(left.getCurrencySymbol()).equals(ConstNvl(right.getCurrencySymbol()))
        && ConstNvl(left.getDateFormatLocale()).equals(ConstNvl(right.getDateFormatLocale()))
        && ConstNvl(left.getDateFormatTimeZone()).equals(ConstNvl(right.getDateFormatTimeZone()))
        && left.isDateFormatLenient() == right.isDateFormatLenient()
        && left.isLenientStringToNumber() == right.isLenientStringToNumber()
        && ConstNvl(left.getEncoding()).equals(ConstNvl(right.getEncoding()))
        && ConstNvl(left.getRoundingType()).equals(ConstNvl(right.getRoundingType()))
        && ConstNvl(left.getStorageType()).equals(ConstNvl(right.getStorageType()))
        && ConstNvl(left.getTrimType()).equals(ConstNvl(right.getTrimType()));
  }

  private static Map<String, SourceFieldTypeMapping> indexOverrides(
      List<SourceFieldTypeMapping> overrides) {
    Map<String, SourceFieldTypeMapping> map = new LinkedHashMap<>();
    if (overrides == null) {
      return map;
    }
    for (SourceFieldTypeMapping override : overrides) {
      if (override == null || Utils.isEmpty(override.getSourceFieldName())) {
        continue;
      }
      map.put(normalizeKey(override.getSourceFieldName()), override);
    }
    return map;
  }

  private static String normalizeKey(String name) {
    return name == null ? "" : name.trim().toLowerCase();
  }

  private static int parseInt(String value, int defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
