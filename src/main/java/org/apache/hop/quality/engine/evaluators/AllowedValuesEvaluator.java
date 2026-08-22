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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.util.Utils;
import org.apache.hop.quality.engine.IDataQualityRuleEvaluator;
import org.apache.hop.quality.engine.QualityEvaluationContext;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.profile.FieldProfile;

public final class AllowedValuesEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.ALLOWED_VALUES;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null) {
      return List.of();
    }

    Set<String> allowed = parseAllowed(rule.parameter(DataQualityRule.PARAM_VALUES));
    boolean nullAllowed = rule.parameterBoolean(DataQualityRule.PARAM_NULL_ALLOWED, true);

    long invalid = 0;
    if (!nullAllowed && field.getNullCount() > 0) {
      invalid += field.getNullCount();
    }
    for (Map.Entry<String, Long> entry : field.getValueCounts().entrySet()) {
      if (!allowed.contains(entry.getKey())) {
        invalid += entry.getValue();
      }
    }

    if (invalid <= 0) {
      return List.of();
    }
    String expected =
        "in ["
            + String.join(", ", allowed)
            + "]"
            + (nullAllowed ? " or null" : " (null not allowed)");
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' has " + invalid + " value(s) outside the allowed set",
            "invalidCount=" + invalid,
            expected,
            EvaluatorSupport.metric("invalidCount", String.valueOf(invalid))));
  }

  private static Set<String> parseAllowed(String raw) {
    if (Utils.isEmpty(raw)) {
      return Set.of();
    }
    return Arrays.stream(raw.split("[,;|]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
