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
package org.hopper.edw.quality.engine.evaluators;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.quality.engine.QualityEvaluationContext;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.QualitySeverity;

final class EvaluatorSupport {

  private EvaluatorSupport() {}

  static DataQualityFinding finding(
      DataQualityRule rule,
      QualityEvaluationContext context,
      String fieldName,
      String message,
      String actual,
      String expected,
      Map<String, String> metrics) {
    QualitySeverity severity =
        rule.getSeverity() != null ? rule.getSeverity() : QualitySeverity.BLOCKING;
    return findingWithSeverity(
        rule, context, fieldName, message, actual, expected, metrics, severity);
  }

  /** Finding with a fixed severity (e.g. INFO for truncated / incomplete coverage). */
  static DataQualityFinding findingWithSeverity(
      DataQualityRule rule,
      QualityEvaluationContext context,
      String fieldName,
      String message,
      String actual,
      String expected,
      Map<String, String> metrics,
      QualitySeverity severity) {
    QualitySeverity effective =
        severity != null
            ? severity
            : (rule.getSeverity() != null ? rule.getSeverity() : QualitySeverity.BLOCKING);
    return DataQualityFinding.builder()
        .subjectKey(context.getSubjectKey())
        .ruleId(rule.getId())
        .ruleName(rule.getName())
        .type(rule.getType())
        .severity(effective)
        .fieldName(fieldName)
        .message(message)
        .actualSummary(actual)
        .expectedSummary(expected)
        .metrics(metrics != null ? metrics : Map.of())
        .build();
  }

  static Map<String, String> metrics(Map<String, String> base) {
    return base != null ? new LinkedHashMap<>(base) : new LinkedHashMap<>();
  }

  static Map<String, String> metrics(String k1, String v1, String k2, String v2) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  static Map<String, String> metrics(
      String k1, String v1, String k2, String v2, String k3, String v3) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    return map;
  }

  static String resolveField(DataQualityRule rule) {
    return rule != null ? rule.getFieldName() : null;
  }

  static boolean missingField(DataQualityRule rule) {
    return Utils.isEmpty(resolveField(rule));
  }

  /** Finding when a field-scoped rule's column is absent from the profile. */
  static List<DataQualityFinding> fieldNotInProfile(
      DataQualityRule rule, QualityEvaluationContext context, String fieldName) {
    return java.util.List.of(
        finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' not found in profile",
            "missing",
            "present",
            metric("fieldMissing", "true")));
  }

  static Map<String, String> metric(String key, String value) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  static Double parseDouble(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Long parseLong(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
