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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TargetTypeMappingSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void explicitNameWinsAndMissingNameFails() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("postgres-target-type-rules");
    mapping.setTargetDatabase("Vault");
    mapping.getRules().add(timestampRule());
    provider.getSerializer(TargetTypeMappingMeta.class).save(mapping);

    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    TargetTypeMappingContext ctx =
        TargetTypeMappingSupport.resolve(
            "postgres-target-type-rules", vault, provider, new Variables());
    assertTrue(ctx.hasMapping());
    assertEquals("postgres-target-type-rules", ctx.getMapping().getName());

    assertThrows(
        HopException.class,
        () -> TargetTypeMappingSupport.resolve("missing", vault, provider, new Variables()));
  }

  @Test
  void uniqueAutoMatchByTargetDatabase() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("postgres-target-type-rules");
    mapping.setTargetDatabase("Vault");
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("Timestamp");
    rule.setTargetSqlType("timestamptz");
    mapping.getRules().add(rule);
    provider.getSerializer(TargetTypeMappingMeta.class).save(mapping);

    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    TargetTypeMappingContext ctx =
        TargetTypeMappingSupport.resolve(null, vault, provider, new Variables());
    assertTrue(ctx.hasMapping());
    assertEquals("postgres-target-type-rules", ctx.getMapping().getName());
  }

  @Test
  void twoMappingsForSameDatabaseSkipAutoMatch() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    TargetTypeMappingMeta one = new TargetTypeMappingMeta("one");
    one.setTargetDatabase("Vault");
    one.getRules().add(timestampRule());
    TargetTypeMappingMeta two = new TargetTypeMappingMeta("two");
    two.setTargetDatabase("vault");
    two.getRules().add(timestampRule());
    provider.getSerializer(TargetTypeMappingMeta.class).save(one);
    provider.getSerializer(TargetTypeMappingMeta.class).save(two);

    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    TargetTypeMappingContext ctx =
        TargetTypeMappingSupport.resolve(null, vault, provider, new Variables());
    assertFalse(ctx.hasMapping());
    assertNull(ctx.getMapping());
  }

  @Test
  void variableNameIsUsedWhenExplicitBlank() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    TargetTypeMappingMeta mapping = new TargetTypeMappingMeta("from-var");
    mapping.setTargetDatabase("Other");
    mapping.getRules().add(timestampRule());
    provider.getSerializer(TargetTypeMappingMeta.class).save(mapping);

    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    Variables vars = new Variables();
    vars.setVariable(TargetTypeMappingSupport.VARIABLE_NAME, "from-var");
    TargetTypeMappingContext ctx = TargetTypeMappingSupport.resolve(null, vault, provider, vars);
    assertTrue(ctx.hasMapping());
    assertEquals("from-var", ctx.getMapping().getName());
  }

  private static TargetTypeMappingRule timestampRule() {
    TargetTypeMappingRule rule = new TargetTypeMappingRule();
    rule.setMatchHopType("Timestamp");
    rule.setTargetSqlType("timestamptz");
    return rule;
  }
}
