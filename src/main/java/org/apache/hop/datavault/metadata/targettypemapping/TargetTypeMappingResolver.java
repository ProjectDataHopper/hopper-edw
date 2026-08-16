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
package org.apache.hop.datavault.metadata.targettypemapping;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.datatypemapping.DataTypeMappingPatternSupport;

/**
 * First-match resolver: Hop {@link IValueMeta} plus a {@link TargetTypeMappingMeta} become a native
 * SQL type string (or {@code null} when no rule applies).
 */
public final class TargetTypeMappingResolver {

  private static final Pattern PLACEHOLDER =
      Pattern.compile("\\{(length|precision)}", Pattern.CASE_INSENSITIVE);

  private TargetTypeMappingResolver() {}

  /**
   * @return matched SQL type, or {@code null} when no enabled rule applies / a template cannot be
   *     filled
   */
  public static ResolvedTargetType resolve(
      IValueMeta valueMeta, TargetTypeMappingMeta mapping, IVariables variables) {
    if (valueMeta == null || mapping == null) {
      return null;
    }
    for (TargetTypeMappingRule rule : mapping.getRules()) {
      if (rule == null || !rule.isEnabled()) {
        continue;
      }
      if (!matches(rule, valueMeta, variables)) {
        continue;
      }
      String sqlType = applyTemplate(rule.getTargetSqlType(), valueMeta, variables);
      if (Utils.isEmpty(sqlType)) {
        continue;
      }
      return new ResolvedTargetType(sqlType, rule);
    }
    return null;
  }

  public static String resolveSqlType(
      IValueMeta valueMeta, TargetTypeMappingMeta mapping, IVariables variables) {
    ResolvedTargetType resolved = resolve(valueMeta, mapping, variables);
    return resolved != null ? resolved.sqlType() : null;
  }

  static boolean matches(TargetTypeMappingRule rule, IValueMeta valueMeta, IVariables variables) {
    if (rule == null || valueMeta == null || !rule.hasMatchCriteria()) {
      return false;
    }
    if (!DataTypeMappingPatternSupport.matchesHopType(
        rule.getMatchHopType(), valueMeta.getType(), valueMeta.getTypeDesc())) {
      return false;
    }
    if (!DataTypeMappingPatternSupport.matches(
        rule.getMatchFieldNamePattern(), valueMeta.getName())) {
      return false;
    }

    int length = valueMeta.getLength();
    boolean lengthAbsent = length < 0;
    if (rule.isMatchLengthAbsent() && !lengthAbsent) {
      return false;
    }

    Integer minLength = parseBound(rule.getMatchMinLength(), variables);
    Integer maxLength = parseBound(rule.getMatchMaxLength(), variables);
    Integer minPrecision = parseBound(rule.getMatchMinPrecision(), variables);
    Integer maxPrecision = parseBound(rule.getMatchMaxPrecision(), variables);
    if (minLength == null && !Utils.isEmpty(unresolved(rule.getMatchMinLength(), variables))) {
      return false;
    }
    if (maxLength == null && !Utils.isEmpty(unresolved(rule.getMatchMaxLength(), variables))) {
      return false;
    }
    if (minPrecision == null
        && !Utils.isEmpty(unresolved(rule.getMatchMinPrecision(), variables))) {
      return false;
    }
    if (maxPrecision == null
        && !Utils.isEmpty(unresolved(rule.getMatchMaxPrecision(), variables))) {
      return false;
    }

    if (minLength != null || maxLength != null) {
      if (lengthAbsent) {
        return false;
      }
      if (minLength != null && length < minLength) {
        return false;
      }
      if (maxLength != null && length > maxLength) {
        return false;
      }
    }

    int precision = valueMeta.getPrecision();
    if (minPrecision != null || maxPrecision != null) {
      if (precision < 0) {
        return false;
      }
      if (minPrecision != null && precision < minPrecision) {
        return false;
      }
      if (maxPrecision != null && precision > maxPrecision) {
        return false;
      }
    }
    return true;
  }

  static String applyTemplate(String template, IValueMeta valueMeta, IVariables variables) {
    if (Utils.isEmpty(template) || valueMeta == null) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(template) : template;
    if (Utils.isEmpty(resolved)) {
      return null;
    }
    boolean needsLength = containsPlaceholder(resolved, "length");
    boolean needsPrecision = containsPlaceholder(resolved, "precision");
    int length = valueMeta.getLength();
    int precision = valueMeta.getPrecision();
    if (needsLength && length < 0) {
      return null;
    }
    if (needsPrecision && precision < 0) {
      return null;
    }
    Matcher matcher = PLACEHOLDER.matcher(resolved);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String token = matcher.group(1);
      String replacement =
          "precision".equalsIgnoreCase(token)
              ? Integer.toString(precision)
              : Integer.toString(length);
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    String sql = out.toString().trim();
    return sql.isEmpty() ? null : sql;
  }

  static boolean containsPlaceholder(String text, String name) {
    if (Utils.isEmpty(text) || Utils.isEmpty(name)) {
      return false;
    }
    String needle = "{" + name.toLowerCase(Locale.ROOT) + "}";
    return text.toLowerCase(Locale.ROOT).contains(needle);
  }

  /**
   * Parse a numeric bound after variable resolution. Returns {@code null} when the bound is empty.
   * Returns {@code null} and leaves the raw value distinguishable via {@link #unresolved} when the
   * token cannot be parsed (caller then skips the rule).
   */
  static Integer parseBound(String raw, IVariables variables) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    String resolved = unresolved(raw, variables);
    if (Utils.isEmpty(resolved) || resolved.contains("${")) {
      return null;
    }
    try {
      return Integer.valueOf(resolved.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static String unresolved(String raw, IVariables variables) {
    if (Utils.isEmpty(raw)) {
      return "";
    }
    return variables != null ? ConstNvl(variables.resolve(raw)).trim() : raw.trim();
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }

  /** Result of a successful rule application. */
  public record ResolvedTargetType(String sqlType, TargetTypeMappingRule rule) {}
}
