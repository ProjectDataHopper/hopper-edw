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
package org.apache.hop.datavault.metadata.datatypemapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IValueMeta;
import org.junit.jupiter.api.Test;

class DataTypeMappingValidationSupportTest {

  @Test
  void profileWithoutMatchCriteriaIsError() {
    DataTypeMappingMeta profile = new DataTypeMappingMeta("p");
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("r1");
    rule.setTargetLength("2000");
    profile.getRules().add(rule);

    List<ICheckResult> remarks = DataTypeMappingValidationSupport.checkProfile(profile);
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void stringToTimestampWithoutMaskIsError() {
    PhysicalSourceField physical = new PhysicalSourceField("created_at");
    physical.setHopType(IValueMeta.TYPE_STRING);

    EffectiveSourceField effective = new EffectiveSourceField();
    effective.setSourceFieldName("created_at");
    effective.setEffectiveFieldName("created_at");
    effective.setHopType(IValueMeta.TYPE_TIMESTAMP);
    effective.setTypeChanged(true);

    List<ICheckResult> remarks =
        DataTypeMappingValidationSupport.checkEffective(
            "orders", List.of(physical), List.of(effective), null);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("without a conversion mask")));
  }

  @Test
  void stringToTimestampWithMaskIsOkOrWarningOnly() {
    PhysicalSourceField physical = new PhysicalSourceField("created_at");
    physical.setHopType(IValueMeta.TYPE_STRING);

    EffectiveSourceField effective = new EffectiveSourceField();
    effective.setSourceFieldName("created_at");
    effective.setEffectiveFieldName("created_at");
    effective.setHopType(IValueMeta.TYPE_TIMESTAMP);
    effective.setTypeChanged(true);
    effective.getConversion().setConversionMask("yyyy-MM-dd HH:mm:ss");
    effective.getConversion().setDateFormatLocale("en_US");
    effective.getConversion().setDateFormatTimeZone("UTC");
    effective.setConversionChanged(true);

    List<ICheckResult> remarks =
        DataTypeMappingValidationSupport.checkEffective(
            "orders", List.of(physical), List.of(effective), null);

    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void unknownOverrideFieldIsError() {
    PhysicalSourceField physical = new PhysicalSourceField("id");
    physical.setHopType(IValueMeta.TYPE_INTEGER);

    SourceFieldTypeMapping override = new SourceFieldTypeMapping("missing");
    override.setLength("10");

    EffectiveSourceField effective = new EffectiveSourceField();
    effective.setSourceFieldName("id");
    effective.setEffectiveFieldName("id");
    effective.setHopType(IValueMeta.TYPE_INTEGER);

    List<ICheckResult> remarks =
        DataTypeMappingValidationSupport.checkEffective(
            "t", List.of(physical), List.of(effective), List.of(override));

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("unknown source field")));
  }

  @Test
  void duplicateTargetNamesAreError() {
    PhysicalSourceField a = new PhysicalSourceField("a");
    a.setHopType(IValueMeta.TYPE_STRING);
    PhysicalSourceField b = new PhysicalSourceField("b");
    b.setHopType(IValueMeta.TYPE_STRING);

    EffectiveSourceField ea = new EffectiveSourceField();
    ea.setSourceFieldName("a");
    ea.setEffectiveFieldName("same");
    ea.setHopType(IValueMeta.TYPE_STRING);
    EffectiveSourceField eb = new EffectiveSourceField();
    eb.setSourceFieldName("b");
    eb.setEffectiveFieldName("same");
    eb.setHopType(IValueMeta.TYPE_STRING);

    List<ICheckResult> remarks =
        DataTypeMappingValidationSupport.checkEffective("t", List.of(a, b), List.of(ea, eb), null);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("multiple fields map")));
  }

  @Test
  void lengthNarrowingIsWarning() {
    PhysicalSourceField physical = new PhysicalSourceField("name");
    physical.setHopType(IValueMeta.TYPE_STRING);
    physical.setLength("4000");

    EffectiveSourceField effective = new EffectiveSourceField();
    effective.setSourceFieldName("name");
    effective.setEffectiveFieldName("name");
    effective.setHopType(IValueMeta.TYPE_STRING);
    effective.setLength("2000");
    effective.setLengthChanged(true);

    List<ICheckResult> remarks =
        DataTypeMappingValidationSupport.checkEffective(
            "t", List.of(physical), List.of(effective), null);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText().contains("narrowed")));
  }
}
