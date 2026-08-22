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

public final class NotEmptyStringEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.NOT_EMPTY_STRING;
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
    long bad = field.getEmptyStringCount();
    // blanks counted as empty when trim=true: emptyStringCount already tracks "";
    // for Phase 1 we treat empty string only; trim-aware scan can expand later.
    if (bad <= 0) {
      return List.of();
    }
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' has " + bad + " empty string value(s)",
            "emptyCount=" + bad,
            "emptyCount=0",
            EvaluatorSupport.metric("emptyStringCount", String.valueOf(bad))));
  }
}
