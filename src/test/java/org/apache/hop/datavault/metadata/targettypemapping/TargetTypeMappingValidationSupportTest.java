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
package org.apache.hop.datavault.metadata.targettypemapping;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TargetTypeMappingValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void ruleWithoutMatchCriteriaIsError() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setTargetSqlType("CHAR(1)");
    mapping.getRules().add(rule);

    List<ICheckResult> remarks = TargetTypeMappingValidationSupport.checkProfile(mapping);
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void ruleWithoutTargetSqlIsError() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("String");
    mapping.getRules().add(rule);

    List<ICheckResult> remarks = TargetTypeMappingValidationSupport.checkProfile(mapping);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("no target SQL")));
  }

  @Test
  void minGreaterThanMaxIsError() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("String");
    rule.setMatchMinLength("10");
    rule.setMatchMaxLength("1");
    rule.setTargetSqlType("CHAR(1)");
    mapping.getRules().add(rule);

    List<ICheckResult> remarks = TargetTypeMappingValidationSupport.checkProfile(mapping);
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void missingTargetDatabaseIsWarning() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("Timestamp");
    rule.setTargetSqlType("timestamp(6) with time zone");
    mapping.getRules().add(rule);

    List<ICheckResult> remarks = TargetTypeMappingValidationSupport.checkProfile(mapping);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText().contains("no target database")));
  }
}
