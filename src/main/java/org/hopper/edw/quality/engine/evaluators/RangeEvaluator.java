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

import java.util.List;
import org.hopper.edw.quality.engine.IDataQualityRuleEvaluator;
import org.hopper.edw.quality.engine.QualityEvaluationContext;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.profile.FieldProfile;

/**
 * Checks that observed min/max for a field stay within an inclusive numeric range.
 *
 * <p>Phase 1 uses profile min/max (not per-row violation counts). Adequate for batch gates; refine
 * later for exact out-of-range counts.
 */
public final class RangeEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.RANGE;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null || field.getNonNullCount() == 0) {
      return List.of();
    }

    Double min = EvaluatorSupport.parseDouble(rule.parameter(DataQualityRule.PARAM_MIN));
    Double max = EvaluatorSupport.parseDouble(rule.parameter(DataQualityRule.PARAM_MAX));
    if (min == null && max == null) {
      return List.of();
    }

    Double observedMin = toDouble(field.getMinValue());
    Double observedMax = toDouble(field.getMaxValue());
    if (observedMin == null && observedMax == null) {
      return List.of();
    }

    boolean out = false;
    if (min != null && observedMin != null && observedMin < min) {
      out = true;
    }
    if (max != null && observedMax != null && observedMax > max) {
      out = true;
    }
    if (!out) {
      return List.of();
    }

    String expected = (min != null ? min : "-∞") + " .. " + (max != null ? max : "+∞");
    String actual =
        (observedMin != null ? observedMin : "?")
            + " .. "
            + (observedMax != null ? observedMax : "?");
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '"
                + fieldName
                + "' observed range ["
                + actual
                + "] outside expected ["
                + expected
                + "]",
            actual,
            expected,
            EvaluatorSupport.metric("observedMin", String.valueOf(observedMin))));
  }

  private static Double toDouble(Comparable<?> value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
