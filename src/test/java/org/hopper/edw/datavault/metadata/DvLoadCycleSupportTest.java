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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLoadCycleSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultsAndFieldResolution() {
    assertEquals("LOAD_CYCLE_ID", DvLoadCycleSupport.DEFAULT_FIELD_NAME);
    assertEquals("dv_load_cycle", DvLoadCycleSupport.DEFAULT_CONTROL_TABLE);
    assertEquals("LOAD_CYCLE_ID", DvLoadCycleSupport.resolveFieldName(null, new Variables()));
    assertEquals(
        "x_load_cycle_id", DvLoadCycleSupport.resolveFieldName("x_load_cycle_id", new Variables()));
  }

  @Test
  void appendToLayoutOnlyWhenEnabled() {
    IRowMeta rowMeta = new RowMeta();
    DvLoadCycleSupport.appendToLayout(rowMeta, false, "LOAD_CYCLE_ID", new Variables());
    assertEquals(0, rowMeta.size());

    DvLoadCycleSupport.appendToLayout(rowMeta, true, "LOAD_CYCLE_ID", new Variables());
    assertEquals(1, rowMeta.size());
    IValueMeta meta = rowMeta.getValueMeta(0);
    assertEquals("LOAD_CYCLE_ID", meta.getName());
    assertEquals(IValueMeta.TYPE_INTEGER, meta.getType());

    // Idempotent
    DvLoadCycleSupport.appendToLayout(rowMeta, true, "LOAD_CYCLE_ID", new Variables());
    assertEquals(1, rowMeta.size());
  }

  @Test
  void variableResolveAndSet() {
    Variables variables = new Variables();
    assertNull(DvLoadCycleSupport.resolveCycleIdFromVariables(variables));
    DvLoadCycleSupport.setCycleIdVariable(variables, 42L);
    assertEquals(42L, DvLoadCycleSupport.resolveCycleIdFromVariables(variables));
    assertEquals("42", variables.getVariable(DvLoadCycleSupport.VAR_LOAD_CYCLE_ID));
  }

  @Test
  void createSqlIsDialectAware() {
    String pgSql =
        DvLoadCycleSupport.buildCreateControlTableSql(
            databaseMetaWithPluginId("POSTGRESQL"), new Variables(), "dv_load_cycle");
    assertTrue(pgSql.contains("CREATE TABLE IF NOT EXISTS"));
    assertTrue(pgSql.contains("BIGINT"));

    String mySql =
        DvLoadCycleSupport.buildCreateControlTableSql(
            databaseMetaWithPluginId("MYSQL"), new Variables(), "dv_load_cycle");
    assertTrue(mySql.contains("CREATE TABLE IF NOT EXISTS"));

    String msSql =
        DvLoadCycleSupport.buildCreateControlTableSql(
            databaseMetaWithPluginId("MSSQLNATIVE"), new Variables(), "dv_load_cycle");
    assertTrue(msSql.contains("IF OBJECT_ID"));
    assertNotNull(msSql);
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }

      @Override
      public String getQuotedSchemaTableCombination(
          org.apache.hop.core.variables.IVariables variables, String schemaName, String tableName) {
        return tableName;
      }
    };
  }

  @Test
  void configurationDefaultsOff() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    assertFalse(config.isStoreLoadCycleId());
    assertEquals(DvLoadCycleSupport.DEFAULT_FIELD_NAME, config.getLoadCycleIdField());
    assertEquals(DvLoadCycleSupport.DEFAULT_CONTROL_TABLE, config.getLoadCycleControlTable());
  }
}
