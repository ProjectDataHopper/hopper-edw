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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Design-time checks for data type mapping profiles and resolved effective layouts. */
public final class DataTypeMappingValidationSupport {

  private static final Class<?> PKG = DataTypeMappingMeta.class;

  private DataTypeMappingValidationSupport() {}

  public static List<ICheckResult> checkProfile(DataTypeMappingMeta profile) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (profile == null) {
      return remarks;
    }
    if (Utils.isEmpty(profile.getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DataTypeMapping.Check.ProfileMissingName"),
              null));
    }
    if (profile.getRules().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "DataTypeMapping.Check.ProfileNoRules", ConstNvl(profile.getName())),
              null));
    }
    int index = 0;
    for (DataTypeMappingRule rule : profile.getRules()) {
      index++;
      if (rule == null) {
        continue;
      }
      String label = ruleLabel(rule, index);
      if (!rule.isEnabled()) {
        continue;
      }
      if (!rule.hasMatchCriteria()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DataTypeMapping.Check.RuleNoMatchCriteria",
                    ConstNvl(profile.getName()),
                    label),
                null));
      }
      if (!rule.hasTargetAttributes()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG, "DataTypeMapping.Check.RuleNoTarget", ConstNvl(profile.getName()), label),
                null));
      }
      if (rule.getTargetHopType() > IValueMeta.TYPE_NONE) {
        try {
          ValueMetaFactory.getValueMetaName(rule.getTargetHopType());
        } catch (Exception e) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DataTypeMapping.Check.RuleInvalidTargetType",
                      ConstNvl(profile.getName()),
                      label,
                      Integer.toString(rule.getTargetHopType())),
                  null));
        }
      }
      if (!Utils.isEmpty(rule.getMatchHopType())) {
        int id = ValueMetaFactory.getIdForValueMeta(rule.getMatchHopType().trim());
        if (id <= 0) {
          try {
            Integer.parseInt(rule.getMatchHopType().trim());
          } catch (NumberFormatException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_WARNING,
                    BaseMessages.getString(
                        PKG,
                        "DataTypeMapping.Check.RuleUnknownMatchHopType",
                        ConstNvl(profile.getName()),
                        label,
                        rule.getMatchHopType()),
                    null));
          }
        }
      }
    }
    return remarks;
  }

  /**
   * Validate resolved effective fields against physical baseline (dangerous conversions, rename
   * collisions, unknown overrides).
   */
  public static List<ICheckResult> checkEffective(
      String sourceLabel,
      List<PhysicalSourceField> physicalFields,
      List<EffectiveSourceField> effectiveFields,
      List<SourceFieldTypeMapping> overrides) {
    List<ICheckResult> remarks = new ArrayList<>();
    String label = ConstNvl(sourceLabel);

    Set<String> physicalNames = new HashSet<>();
    if (physicalFields != null) {
      for (PhysicalSourceField physical : physicalFields) {
        if (physical != null && !Utils.isEmpty(physical.getName())) {
          physicalNames.add(physical.getName().trim().toLowerCase());
        }
      }
    }

    if (overrides != null) {
      for (SourceFieldTypeMapping override : overrides) {
        if (override == null
            || override.isDisabled()
            || Utils.isEmpty(override.getSourceFieldName())) {
          continue;
        }
        if (!physicalNames.contains(override.getSourceFieldName().trim().toLowerCase())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DataTypeMapping.Check.OverrideUnknownField",
                      label,
                      override.getSourceFieldName()),
                  null));
        }
      }
    }

    Set<String> targetNames = new HashSet<>();
    if (effectiveFields != null) {
      for (EffectiveSourceField effective : effectiveFields) {
        if (effective == null) {
          continue;
        }
        String target =
            !Utils.isEmpty(effective.getEffectiveFieldName())
                ? effective.getEffectiveFieldName().trim()
                : ConstNvl(effective.getSourceFieldName());
        String key = target.toLowerCase();
        if (!targetNames.add(key)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG, "DataTypeMapping.Check.DuplicateTargetName", label, target),
                  null));
        }
        checkConversionSafety(remarks, label, physicalFields, effective);
      }
    }
    return remarks;
  }

  private static void checkConversionSafety(
      List<ICheckResult> remarks,
      String sourceLabel,
      List<PhysicalSourceField> physicalFields,
      EffectiveSourceField effective) {
    PhysicalSourceField physical = findPhysical(physicalFields, effective.getSourceFieldName());
    int fromType = physical != null ? physical.effectiveHopType() : IValueMeta.TYPE_STRING;
    int toType = effective.effectiveHopType();
    String fieldName =
        !Utils.isEmpty(effective.getEffectiveFieldName())
            ? effective.getEffectiveFieldName()
            : ConstNvl(effective.getSourceFieldName());

    boolean typeChanged = fromType != toType;
    if (!typeChanged && !effective.isConversionChanged()) {
      // Still check missing length improvement? skip.
      if (effective.isPrimaryKey() && effective.isTypeChanged()) {
        // unreachable
      }
      checkNarrowing(remarks, sourceLabel, fieldName, physical, effective);
      return;
    }

    FieldConversionOptions conv = effective.getConversion();
    String mask = conv != null ? conv.getConversionMask() : null;
    boolean hasMask = !Utils.isEmpty(mask);

    if (fromType == IValueMeta.TYPE_STRING
        && (toType == IValueMeta.TYPE_DATE || toType == IValueMeta.TYPE_TIMESTAMP)) {
      if (!hasMask) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DataTypeMapping.Check.StringToTemporalWithoutMask",
                    sourceLabel,
                    fieldName,
                    typeName(toType)),
                null));
      } else if (Utils.isEmpty(conv.getDateFormatLocale())
          || Utils.isEmpty(conv.getDateFormatTimeZone())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DataTypeMapping.Check.StringToTemporalMissingLocaleTz",
                    sourceLabel,
                    fieldName),
                null));
      }
    }

    if (fromType == IValueMeta.TYPE_STRING
        && (toType == IValueMeta.TYPE_INTEGER
            || toType == IValueMeta.TYPE_NUMBER
            || toType == IValueMeta.TYPE_BIGNUMBER)) {
      if (!hasMask && conv != null && !conv.isLenientStringToNumber()) {
        int severity =
            toType == IValueMeta.TYPE_INTEGER
                ? ICheckResult.TYPE_RESULT_WARNING
                : ICheckResult.TYPE_RESULT_WARNING;
        remarks.add(
            new CheckResult(
                severity,
                BaseMessages.getString(
                    PKG,
                    "DataTypeMapping.Check.StringToNumericWithoutMask",
                    sourceLabel,
                    fieldName,
                    typeName(toType)),
                null));
      }
      if (toType != IValueMeta.TYPE_INTEGER
          && hasMask
          && Utils.isEmpty(conv.getDecimalSymbol())
          && Utils.isEmpty(conv.getGroupingSymbol())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DataTypeMapping.Check.StringToNumberMissingSymbols",
                    sourceLabel,
                    fieldName),
                null));
      }
    }

    if (typeChanged
        && (fromType == IValueMeta.TYPE_BOOLEAN
            || toType == IValueMeta.TYPE_BOOLEAN
            || fromType == IValueMeta.TYPE_INTEGER && toType == IValueMeta.TYPE_STRING
            || fromType == IValueMeta.TYPE_STRING && toType == IValueMeta.TYPE_BOOLEAN)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DataTypeMapping.Check.BooleanishConversion",
                  sourceLabel,
                  fieldName,
                  typeName(fromType),
                  typeName(toType)),
              null));
    }

    if (effective.isPrimaryKey() && typeChanged) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "DataTypeMapping.Check.PkTypeChange", sourceLabel, fieldName),
              null));
    }

    checkNarrowing(remarks, sourceLabel, fieldName, physical, effective);
  }

  private static void checkNarrowing(
      List<ICheckResult> remarks,
      String sourceLabel,
      String fieldName,
      PhysicalSourceField physical,
      EffectiveSourceField effective) {
    if (physical == null) {
      return;
    }
    int fromLen = physical.parseLengthOr(-1);
    int toLen = parseInt(effective.getLength(), -1);
    if (fromLen > 0 && toLen > 0 && toLen < fromLen) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DataTypeMapping.Check.LengthNarrowing",
                  sourceLabel,
                  fieldName,
                  Integer.toString(fromLen),
                  Integer.toString(toLen)),
              null));
    }
  }

  private static PhysicalSourceField findPhysical(
      List<PhysicalSourceField> physicalFields, String name) {
    if (physicalFields == null || Utils.isEmpty(name)) {
      return null;
    }
    for (PhysicalSourceField physical : physicalFields) {
      if (physical != null && name.equalsIgnoreCase(physical.getName())) {
        return physical;
      }
    }
    return null;
  }

  private static String ruleLabel(DataTypeMappingRule rule, int index) {
    if (!Utils.isEmpty(rule.getName())) {
      return rule.getName();
    }
    if (!Utils.isEmpty(rule.getId())) {
      return rule.getId();
    }
    return "#" + index;
  }

  private static String typeName(int hopType) {
    try {
      return ValueMetaFactory.getValueMetaName(hopType);
    } catch (Exception e) {
      return Integer.toString(hopType);
    }
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
