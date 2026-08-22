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
package org.hopper.edw.datavault.metadata.targettypemapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class TargetTypeMappingResolverTest {

  @Test
  void stringLengthOneMapsToChar() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaString field = new ValueMetaString("flag");
    field.setLength(1);

    assertEquals("CHAR(1)", TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void stringMaxLengthUsesPlaceholder() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaString field = new ValueMetaString("name");
    field.setLength(80);

    assertEquals("NVARCHAR(80)", TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void integerMaxLengthTwoMapsToByte() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaInteger field = new ValueMetaInteger("tiny");
    field.setLength(2);

    assertEquals("BYTE", TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void integerMaxLengthThreeMapsToSmallint() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaInteger field = new ValueMetaInteger("small");
    field.setLength(3);

    assertEquals("SMALLINT", TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void timestampMapsWithoutLength() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    IValueMeta field = new ValueMetaTimestamp("load_dts");

    assertEquals(
        "timestamp(6) with time zone",
        TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void firstMatchingRuleWins() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    mapping.getRules().add(stringRule("CHAR(1)", 1, 1));
    TargetTypeMappingRule later = stringRule("VARCHAR({length})", null, 2000);
    mapping.getRules().add(later);
    ValueMetaString field = new ValueMetaString("flag");
    field.setLength(1);

    assertEquals("CHAR(1)", TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void lengthPlaceholderWithAbsentLengthSkipsRule() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    mapping.getRules().add(stringRule("VARCHAR({length})", null, null));
    ValueMetaString field = new ValueMetaString("notes");
    field.setLength(-1);

    assertNull(TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void variablesThenPlaceholders() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = stringRule("${STR_TYPE}({length})", null, 2000);
    mapping.getRules().add(rule);
    ValueMetaString field = new ValueMetaString("name");
    field.setLength(50);
    Variables vars = new Variables();
    vars.setVariable("STR_TYPE", "VARCHAR");

    assertEquals("VARCHAR(50)", TargetTypeMappingResolver.resolveSqlType(field, mapping, vars));
  }

  @Test
  void fieldNamePatternExcludesTechnicalColumn() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = stringRule("CHAR(1)", 1, 1);
    rule.setMatchFieldNamePattern("flag*");
    mapping.getRules().add(rule);
    ValueMetaString flag = new ValueMetaString("flag_yn");
    flag.setLength(1);
    ValueMetaString other = new ValueMetaString("record_source");
    other.setLength(1);

    assertEquals("CHAR(1)", TargetTypeMappingResolver.resolveSqlType(flag, mapping, null));
    assertNull(TargetTypeMappingResolver.resolveSqlType(other, mapping, null));
  }

  @Test
  void disabledRuleIsIgnored() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("p");
    TargetTypeMappingRule rule = stringRule("CHAR(1)", 1, 1);
    rule.setEnabled(false);
    mapping.getRules().add(rule);
    ValueMetaString field = new ValueMetaString("flag");
    field.setLength(1);

    assertNull(TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void unmatchedFallsThrough() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaString field = new ValueMetaString("blob");
    field.setLength(8000);

    assertNull(TargetTypeMappingResolver.resolveSqlType(field, mapping, null));
  }

  @Test
  void resolvedResultExposesMatchedRule() {
    TargetTypeMappingMeta mapping = issueExampleMapping();
    ValueMetaString field = new ValueMetaString("flag");
    field.setLength(1);

    TargetTypeMappingResolver.ResolvedTargetType resolved =
        TargetTypeMappingResolver.resolve(field, mapping, null);
    assertNotNull(resolved);
    assertEquals("char-1", resolved.rule().getId());
  }

  private static TargetTypeMappingMeta issueExampleMapping() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("postgres-target-type-rules");
    mapping.setTargetDatabase("vault");
    mapping.getRules().add(named(stringRule("CHAR(1)", 1, 1), "char-1"));
    mapping.getRules().add(named(stringRule("NVARCHAR({length})", null, 2000), "nvarchar"));
    mapping.getRules().add(named(integerRule("BYTE", null, 2), "byte"));
    mapping.getRules().add(named(integerRule("SMALLINT", null, 3), "smallint"));
    TargetTypeMappingRule ts = new TargetTypeMappingRule();
    ts.setId("timestamptz");
    ts.setMatchHopType("Timestamp");
    ts.setTargetSqlType("timestamp(6) with time zone");
    mapping.getRules().add(ts);
    return mapping;
  }

  private static TargetTypeMappingRule stringRule(String sql, Integer min, Integer max) {
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("String");
    if (min != null) {
      rule.setMatchMinLength(Integer.toString(min));
    }
    if (max != null) {
      rule.setMatchMaxLength(Integer.toString(max));
    }
    rule.setTargetSqlType(sql);
    return rule;
  }

  private static TargetTypeMappingRule integerRule(String sql, Integer min, Integer max) {
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("Integer");
    if (min != null) {
      rule.setMatchMinLength(Integer.toString(min));
    }
    if (max != null) {
      rule.setMatchMaxLength(Integer.toString(max));
    }
    rule.setTargetSqlType(sql);
    return rule;
  }

  private static TargetTypeMappingRule named(TargetTypeMappingRule rule, String id) {
    rule.setId(id);
    return rule;
  }
}
