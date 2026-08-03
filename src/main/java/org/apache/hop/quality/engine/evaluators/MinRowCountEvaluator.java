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
import org.apache.hop.quality.engine.IDataQualityRuleEvaluator;
import org.apache.hop.quality.engine.QualityEvaluationContext;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.profile.DataProfileSnapshot;

public final class MinRowCountEvaluator implements IDataQualityRuleEvaluator {

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.MIN_ROW_COUNT;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    Long min = EvaluatorSupport.parseLong(rule.parameter(DataQualityRule.PARAM_MIN, "1"));
    if (min == null) {
      min = 1L;
    }
    DataProfileSnapshot profile = context.getProfile();
    long actual = profile != null ? profile.getRowCount() : 0L;
    if (actual >= min) {
      return List.of();
    }
    return List.of(
        EvaluatorSupport.finding(
            rule,
            context,
            null,
            "Row count "
                + actual
                + " is below minimum "
                + min
                + (profile != null && !profile.isRowCountExact() ? " (sample/partial count)" : ""),
            String.valueOf(actual),
            ">= " + min,
            EvaluatorSupport.metric("rowCount", String.valueOf(actual))));
  }
}
