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
import org.apache.hop.quality.profile.FieldProfile;

/** Fails when observed minimum string length is below {@code min}. */
public final class MinLengthEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.MIN_LENGTH;
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
    if (field.getMinStringLength() == null) {
      return List.of();
    }

    int observed = field.getMinStringLength();
    if (observed >= min) {
      return List.of();
    }

    Map<String, String> metrics =
        EvaluatorSupport.metrics(
            "minStringLength", String.valueOf(observed), "min", String.valueOf(min));
    if (field.getMaxStringLength() != null) {
      metrics.put("maxStringLength", String.valueOf(field.getMaxStringLength()));
    }
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' min string length " + observed + " is below minimum " + min,
            "minStringLength=" + observed,
            "min=" + min,
            metrics));
  }
}
