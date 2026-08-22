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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.junit.jupiter.api.Test;

class DataTypeMappingResolverTest {

  @Test
  void stringWithoutLengthMapsToDefaultLength() {
    PhysicalSourceField physical = stringField("customer_name", null);
    DataTypeMappingMeta profile = profile("premodel-defaults", stringNoLengthRule(2000));

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(profile), null);

    assertEquals(IValueMeta.TYPE_STRING, effective.getHopType());
    assertEquals("2000", effective.getLength());
    assertTrue(effective.isLengthChanged());
    assertFalse(effective.isRenamed());
    assertTrue(effective.getProvenance().stream().anyMatch(p -> p.contains("premodel-defaults")));
  }

  @Test
  void firstMatchingRuleWinsWithinProfile() {
    PhysicalSourceField physical = stringField("notes", null);
    DataTypeMappingRule first = stringNoLengthRule(2000);
    first.setId("default-string");
    DataTypeMappingRule second = stringNoLengthRule(500);
    second.setId("other-string");
    DataTypeMappingMeta profile = profile("p", first, second);

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(profile), null);

    assertEquals("2000", effective.getLength());
  }

  @Test
  void laterProfileOverridesAttributes() {
    PhysicalSourceField physical = stringField("notes", null);
    DataTypeMappingMeta base = profile("base", stringNoLengthRule(2000));
    DataTypeMappingRule crmRule = stringNoLengthRule(4000);
    crmRule.setId("crm-string");
    DataTypeMappingMeta crm = profile("crm", crmRule);

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(base, crm), null);

    assertEquals("4000", effective.getLength());
  }

  @Test
  void fieldOverrideWinsLast() {
    PhysicalSourceField physical = stringField("notes", null);
    DataTypeMappingMeta profile = profile("base", stringNoLengthRule(2000));
    SourceFieldTypeMapping override = new SourceFieldTypeMapping("notes");
    override.setLength("100");
    override.setTargetFieldName("note_text");

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(profile), override);

    assertEquals("100", effective.getLength());
    assertEquals("note_text", effective.getEffectiveFieldName());
    assertTrue(effective.isRenamed());
  }

  @Test
  void fieldNamePatternMatch() {
    PhysicalSourceField physical = stringField("updated_at", "50");
    physical.setHopType(IValueMeta.TYPE_STRING);
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("ts-fields");
    rule.setMatchFieldNamePattern("*_at");
    rule.setMatchHopType("String");
    rule.setTargetHopType(IValueMeta.TYPE_TIMESTAMP);
    rule.getConversion().setConversionMask("yyyy-MM-dd HH:mm:ss");
    DataTypeMappingMeta profile = profile("dates", rule);

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(profile), null);

    assertEquals(IValueMeta.TYPE_TIMESTAMP, effective.getHopType());
    assertEquals("yyyy-MM-dd HH:mm:ss", effective.getConversion().getConversionMask());
    assertTrue(effective.isTypeChanged());
    assertTrue(effective.isConversionChanged());
  }

  @Test
  void sourceDataTypePatternMatch() {
    PhysicalSourceField physical = stringField("body", "255");
    physical.setSourceDataType("LONGTEXT");
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("lob");
    rule.setMatchSourceDataTypePattern("LONGTEXT");
    rule.setTargetLength("2000");
    DataTypeMappingMeta profile = profile("sql", rule);

    EffectiveSourceField effective =
        DataTypeMappingResolver.resolve(physical, List.of(profile), null);

    assertEquals("2000", effective.getLength());
  }

  @Test
  void resolveAllAppliesPerFieldOverrides() {
    PhysicalSourceField a = stringField("a", null);
    PhysicalSourceField b = stringField("b", null);
    DataTypeMappingMeta profile = profile("base", stringNoLengthRule(2000));
    SourceFieldTypeMapping overrideB = new SourceFieldTypeMapping("b");
    overrideB.setLength("50");

    List<EffectiveSourceField> effective =
        DataTypeMappingResolver.resolveAll(List.of(a, b), List.of(profile), List.of(overrideB));

    assertEquals(2, effective.size());
    assertEquals("2000", effective.get(0).getLength());
    assertEquals("50", effective.get(1).getLength());
  }

  @Test
  void unmappedFieldStaysPhysical() {
    PhysicalSourceField physical = new PhysicalSourceField("id");
    physical.setHopType(IValueMeta.TYPE_INTEGER);
    physical.setLength("9");

    EffectiveSourceField effective = DataTypeMappingResolver.resolve(physical, List.of(), null);

    assertEquals(IValueMeta.TYPE_INTEGER, effective.getHopType());
    assertEquals("9", effective.getLength());
    assertFalse(effective.isMapped());
  }

  private static PhysicalSourceField stringField(String name, String length) {
    PhysicalSourceField field = new PhysicalSourceField(name);
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength(length);
    return field;
  }

  private static DataTypeMappingRule stringNoLengthRule(int targetLength) {
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("string-default-" + targetLength);
    rule.setName("String without length");
    rule.setMatchHopType("String");
    rule.setMatchLengthAbsent(true);
    rule.setTargetHopType(IValueMeta.TYPE_STRING);
    rule.setTargetLength(Integer.toString(targetLength));
    return rule;
  }

  private static DataTypeMappingMeta profile(String name, DataTypeMappingRule... rules) {
    DataTypeMappingMeta meta = new DataTypeMappingMeta(name);
    for (DataTypeMappingRule rule : rules) {
      meta.getRules().add(rule);
    }
    return meta;
  }
}
