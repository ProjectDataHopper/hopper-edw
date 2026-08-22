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
package org.hopper.edw.quality.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.hopper.edw.quality.engine.evaluators.AllowedValuesEvaluator;
import org.hopper.edw.quality.engine.evaluators.MaxDistinctEvaluator;
import org.hopper.edw.quality.engine.evaluators.MaxLengthEvaluator;
import org.hopper.edw.quality.engine.evaluators.MaxRowCountEvaluator;
import org.hopper.edw.quality.engine.evaluators.MinDistinctEvaluator;
import org.hopper.edw.quality.engine.evaluators.MinLengthEvaluator;
import org.hopper.edw.quality.engine.evaluators.MinRowCountEvaluator;
import org.hopper.edw.quality.engine.evaluators.NotEmptyStringEvaluator;
import org.hopper.edw.quality.engine.evaluators.NotNullEvaluator;
import org.hopper.edw.quality.engine.evaluators.NullRatioMaxEvaluator;
import org.hopper.edw.quality.engine.evaluators.RangeEvaluator;
import org.hopper.edw.quality.engine.evaluators.RegexEvaluator;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;

/** Registry of built-in rule evaluators. */
public final class DataQualityRuleEvaluatorRegistry {

  private static final DataQualityRuleEvaluatorRegistry INSTANCE =
      new DataQualityRuleEvaluatorRegistry();

  private final Map<DataQualityRuleType, IDataQualityRuleEvaluator> evaluators =
      new EnumMap<>(DataQualityRuleType.class);

  private DataQualityRuleEvaluatorRegistry() {
    register(new MinRowCountEvaluator());
    register(new MaxRowCountEvaluator());
    register(new NotNullEvaluator());
    register(new AllowedValuesEvaluator());
    register(new RangeEvaluator());
    register(new NotEmptyStringEvaluator());
    // Phase 2 profile-based rules
    register(new NullRatioMaxEvaluator());
    register(new MinDistinctEvaluator());
    register(new MaxDistinctEvaluator());
    register(new RegexEvaluator());
    register(new MinLengthEvaluator());
    register(new MaxLengthEvaluator());
  }

  public static DataQualityRuleEvaluatorRegistry getInstance() {
    return INSTANCE;
  }

  private void register(IDataQualityRuleEvaluator evaluator) {
    evaluators.put(evaluator.type(), evaluator);
  }

  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (rule == null || !rule.isEnabled() || rule.getType() == null) {
      return List.of();
    }
    IDataQualityRuleEvaluator evaluator = evaluators.get(rule.getType());
    if (evaluator == null) {
      return List.of();
    }
    List<DataQualityFinding> findings = evaluator.evaluate(rule, context);
    return findings != null ? findings : List.of();
  }
}
