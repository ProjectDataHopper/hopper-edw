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
import java.util.Locale;
import java.util.Map;
import org.hopper.edw.quality.engine.IDataQualityRuleEvaluator;
import org.hopper.edw.quality.engine.QualityEvaluationContext;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualitySeverity;
import org.hopper.edw.quality.profile.DataProfileSnapshot;
import org.hopper.edw.quality.profile.FieldProfile;

/** Fails when {@code nullCount / rowCount > maxRatio} (maxRatio in 0.0–1.0). */
public final class NullRatioMaxEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.NULL_RATIO_MAX;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    Double maxRatio = EvaluatorSupport.parseDouble(rule.parameter(DataQualityRule.PARAM_MAX_RATIO));
    if (maxRatio == null || !Double.isFinite(maxRatio) || maxRatio < 0.0 || maxRatio > 1.0) {
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              EvaluatorSupport.resolveField(rule),
              "NULL_RATIO_MAX maxRatio must be between 0.0 and 1.0 inclusive (got "
                  + rule.parameter(DataQualityRule.PARAM_MAX_RATIO)
                  + ")",
              "maxRatio=" + rule.parameter(DataQualityRule.PARAM_MAX_RATIO),
              "0.0 <= maxRatio <= 1.0",
              EvaluatorSupport.metric(
                  "maxRatio", String.valueOf(rule.parameter(DataQualityRule.PARAM_MAX_RATIO))),
              QualitySeverity.WARNING));
    }

    String fieldName = EvaluatorSupport.resolveField(rule);
    DataProfileSnapshot profile = context.getProfile();
    FieldProfile field = profile.findField(fieldName);
    if (field == null) {
      return EvaluatorSupport.fieldNotInProfile(rule, context, fieldName);
    }

    long rowCount = profile.getRowCount();
    long nullCount = field.getNullCount();
    double ratio = rowCount <= 0 ? 0.0 : (double) nullCount / (double) rowCount;
    if (ratio <= maxRatio) {
      return List.of();
    }

    Map<String, String> metrics =
        EvaluatorSupport.metrics(
            "ratio",
            formatRatio(ratio),
            "nullCount",
            String.valueOf(nullCount),
            "rowCount",
            String.valueOf(rowCount));
    metrics.put("maxRatio", formatRatio(maxRatio));
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '"
                + fieldName
                + "' null ratio "
                + formatRatio(ratio)
                + " exceeds maxRatio "
                + formatRatio(maxRatio),
            "ratio=" + formatRatio(ratio) + " nullCount=" + nullCount + " rowCount=" + rowCount,
            "maxRatio=" + formatRatio(maxRatio),
            metrics));
  }

  private static String formatRatio(double value) {
    if (value == (long) value) {
      return String.format(Locale.ROOT, "%.1f", value);
    }
    String s = String.format(Locale.ROOT, "%.6f", value);
    // trim trailing zeros after decimal
    if (s.indexOf('.') >= 0) {
      s = s.replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }
    return s;
  }
}
