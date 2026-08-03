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
import org.apache.hop.quality.profile.FieldProfile;

/** Fails when observed maximum string length exceeds {@code max}. */
public final class MaxLengthEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.MAX_LENGTH;
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
    if (field.getMaxStringLength() == null) {
      return List.of();
    }

    int observed = field.getMaxStringLength();
    if (observed <= max) {
      return List.of();
    }

    Map<String, String> metrics =
        EvaluatorSupport.metrics(
            "maxStringLength", String.valueOf(observed), "max", String.valueOf(max));
    if (field.getMinStringLength() != null) {
      metrics.put("minStringLength", String.valueOf(field.getMinStringLength()));
    }
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            fieldName,
            "Field '" + fieldName + "' max string length " + observed + " exceeds maximum " + max,
            "maxStringLength=" + observed,
            "max=" + max,
            metrics));
  }
}
