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
package org.apache.hop.quality.engine.evaluators;

import java.util.List;
import java.util.Map;
import org.apache.hop.quality.engine.IDataQualityRuleEvaluator;
import org.apache.hop.quality.engine.QualityEvaluationContext;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.FieldProfile;

/** Fails when exact or observed distinct count is below {@code min}. */
public final class MinDistinctEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.MIN_DISTINCT;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    Long min = EvaluatorSupport.parseLong(rule.parameter(DataQualityRule.PARAM_MIN));
    if (min == null) {
      return List.of();
    }

    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null) {
      return EvaluatorSupport.fieldNotInProfile(rule, context, fieldName);
    }

    Long exact = field.getExactDistinctCount();
    if (exact != null) {
      if (exact >= min) {
        return List.of();
      }
      return fail(rule, context, fieldName, exact, min, "exact");
    }

    // Collector failed to obtain any distinct signal — do not false-fail as size=0.
    if (field.isDistinctUnknown() && field.getDistinctValues().isEmpty()) {
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "MIN_DISTINCT could not be evaluated: distinct count unavailable",
              "distinctUnknown=true",
              "min=" + min,
              EvaluatorSupport.metrics("distinctUnknown", "true", "min", String.valueOf(min)),
              QualitySeverity.WARNING));
    }

    long size = field.getDistinctValues().size();
    // Truncated set is a lower bound: size < min still proves violation.
    if (size < min) {
      return fail(rule, context, fieldName, size, min, "observed");
    }
    // size >= min: pass (even if truncated — more distinct values only helps MIN)
    return List.of();
  }

  private static List<DataQualityFinding> fail(
      DataQualityRule rule,
      QualityEvaluationContext context,
      String fieldName,
      long actual,
      long min,
      String source) {
    Map<String, String> metrics =
        EvaluatorSupport.metrics(
            "distinctCount", String.valueOf(actual), "min", String.valueOf(min));
    metrics.put("source", source);
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' distinct count " + actual + " is below minimum " + min,
            "distinctCount=" + actual,
            "min=" + min,
            metrics));
  }
}
