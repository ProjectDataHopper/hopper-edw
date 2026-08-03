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

/**
 * Fails when exact or observed distinct count exceeds {@code max}. When only a truncated sample is
 * available and size ≤ max, emits a fixed-severity INFO (not a hard fail).
 */
public final class MaxDistinctEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.MAX_DISTINCT;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    Long max = EvaluatorSupport.parseLong(rule.parameter(DataQualityRule.PARAM_MAX));
    if (max == null) {
      return List.of();
    }

    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null) {
      return EvaluatorSupport.fieldNotInProfile(rule, context, fieldName);
    }

    Long exact = field.getExactDistinctCount();
    if (exact != null) {
      if (exact <= max) {
        return List.of();
      }
      return fail(rule, context, fieldName, exact, max, "exact");
    }

    // Collector failed to obtain any distinct signal — do not treat empty as exact zero.
    if (field.isDistinctUnknown() && field.getDistinctValues().isEmpty()) {
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "MAX_DISTINCT could not be evaluated: distinct count unavailable",
              "distinctUnknown=true",
              "max=" + max,
              EvaluatorSupport.metrics("distinctUnknown", "true", "max", String.valueOf(max)),
              QualitySeverity.WARNING));
    }

    long size = field.getDistinctValues().size();
    if (size > max) {
      // Even truncated, size proves violation.
      return fail(rule, context, fieldName, size, max, "observed");
    }
    if (field.isDistinctTruncated()) {
      Map<String, String> metrics =
          EvaluatorSupport.metrics(
              "distinctTruncated", "true", "observedDistinct", String.valueOf(size));
      metrics.put("max", String.valueOf(max));
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "distinct truncated; MAX_DISTINCT not fully verified",
              "observedDistinct=" + size,
              "max=" + max,
              metrics,
              QualitySeverity.INFO));
    }
    // Not truncated: size is exact for the collected set.
    return List.of();
  }

  private static List<DataQualityFinding> fail(
      DataQualityRule rule,
      QualityEvaluationContext context,
      String fieldName,
      long actual,
      long max,
      String source) {
    Map<String, String> metrics =
        EvaluatorSupport.metrics(
            "distinctCount", String.valueOf(actual), "max", String.valueOf(max));
    metrics.put("source", source);
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' distinct count " + actual + " exceeds maximum " + max,
            "distinctCount=" + actual,
            "max=" + max,
            metrics));
  }
}
