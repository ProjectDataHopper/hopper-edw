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
package org.apache.hop.datavault.metadata.targettypemapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;
import org.apache.hop.datavault.metadata.DvDdlSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TargetTypeMappingDdlTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void createTableUsesMappedTypesBeforeDialectDefaults() {
    TargetTypeMappingContext context = new TargetTypeMappingContext(sampleMapping(), null);
    RowMeta fields = new RowMeta();
    ValueMetaString flag = new ValueMetaString("flag");
    flag.setLength(1);
    fields.addValueMeta(flag);
    ValueMetaString name = new ValueMetaString("customer_name");
    name.setLength(80);
    fields.addValueMeta(name);
    ValueMetaInteger tiny = new ValueMetaInteger("tiny_code");
    tiny.setLength(2);
    fields.addValueMeta(tiny);
    fields.addValueMeta(new ValueMetaTimestamp("load_dts"));

    String ddl =
        DvDdlSupport.buildCreateTableStatement(
            postgres(),
            new Variables(),
            "hub_sample",
            fields,
            null,
            java.util.List.of(),
            java.util.List.of(),
            true,
            true,
            context);

    assertTrue(ddl.contains("CHAR(1)"));
    assertTrue(ddl.contains("NVARCHAR(80)"));
    assertTrue(ddl.contains("BYTE"));
    assertTrue(ddl.toLowerCase().contains("timestamp(6) with time zone"));
    assertFalse(ddl.toUpperCase().contains("VARCHAR(1)"));
  }

  @Test
  void unmatchedColumnsKeepHopDialectTypes() {
    TargetTypeMappingContext context = new TargetTypeMappingContext(sampleMapping(), null);
    RowMeta fields = new RowMeta();
    ValueMetaString notes = new ValueMetaString("notes");
    notes.setLength(8000);
    fields.addValueMeta(notes);

    String mapped = DvDdlSupport.getFieldDefinition(postgres(), notes, true, context);
    String unmapped = DvDdlSupport.getFieldDefinition(postgres(), notes, true, null);
    assertTrue(mapped.contains(unmapped.trim()) || mapped.equals(unmapped));
  }

  @Test
  void sqlServerUtf8IsSkippedWhenRuleMatches() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("ss");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("String");
    rule.setMatchMaxLength("2000");
    rule.setTargetSqlType("NVARCHAR({length})");
    mapping.getRules().add(rule);
    TargetTypeMappingContext context = new TargetTypeMappingContext(mapping, null);

    ValueMetaString name = new ValueMetaString("customer_name");
    name.setLength(50);
    String definition = DvDdlSupport.getFieldDefinition(postgres(), name, true, context);
    assertTrue(definition.toUpperCase().contains("NVARCHAR(50)"));
    assertFalse(definition.toUpperCase().contains("COLLATE"));

    DatabaseMeta sqlServer =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID;
          }
        };
    assertEquals(
        "customer_name NVARCHAR(50)",
        DvDdlSupport.enrichSqlServerFieldDefinition(sqlServer, "customer_name NVARCHAR(50)"));
    assertTrue(
        DvDdlSupport.enrichSqlServerFieldDefinition(sqlServer, "customer_name VARCHAR(50)")
            .contains("VARCHAR(150)"));
  }

  @Test
  void noContextLeavesHopDefinitionUnchanged() {
    ValueMetaString flag = new ValueMetaString("flag");
    flag.setLength(1);
    String hop = DvDdlSupport.getFieldDefinition(postgres(), flag, true, null);
    assertTrue(hop.toUpperCase().contains("VARCHAR"));
    assertFalse(hop.matches("(?is).*\\bCHAR\\(1\\).*"));
  }

  private static TargetTypeMappingMeta sampleMapping() {
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("postgres-target-type-rules");
    mapping.setTargetDatabase("Vault");
    TargetTypeMappingRule char1 = new TargetTypeMappingRule();
    char1.setMatchHopType("String");
    char1.setMatchMinLength("1");
    char1.setMatchMaxLength("1");
    char1.setTargetSqlType("CHAR(1)");
    mapping.getRules().add(char1);
    TargetTypeMappingRule nvarchar = new TargetTypeMappingRule();
    nvarchar.setMatchHopType("String");
    nvarchar.setMatchMaxLength("2000");
    nvarchar.setTargetSqlType("NVARCHAR({length})");
    mapping.getRules().add(nvarchar);
    TargetTypeMappingRule byt = new TargetTypeMappingRule();
    byt.setMatchHopType("Integer");
    byt.setMatchMaxLength("2");
    byt.setTargetSqlType("BYTE");
    mapping.getRules().add(byt);
    TargetTypeMappingRule ts = new TargetTypeMappingRule();
    ts.setMatchHopType("Timestamp");
    ts.setTargetSqlType("timestamp(6) with time zone");
    mapping.getRules().add(ts);
    return mapping;
  }

  private static DatabaseMeta postgres() {
    return new DatabaseMeta(
        "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
  }
}
