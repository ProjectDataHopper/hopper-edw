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
package org.hopper.edw.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelTargetTablePreviewSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void previewSelectSql_postgresWithSchema() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            postgres, new Variables(), "public", "hub_customer", 1000);
    assertTrue(sql.startsWith("SELECT * FROM "), sql);
    assertTrue(sql.contains("\"public\".hub_customer"), sql);
    assertTrue(sql.toLowerCase().contains("limit 1000"), sql);
  }

  @Test
  void previewSelectSql_postgresWithDottedTableName() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            postgres, new Variables(), null, "public.hub_customer", 1000);
    assertTrue(sql.startsWith("SELECT * FROM "), sql);
    assertTrue(sql.contains("\"public\".hub_customer"), sql);
    assertTrue(sql.toLowerCase().contains("limit 1000"), sql);
  }

  @Test
  void previewSelectSql_postgresNoSchema() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            postgres, new Variables(), null, "hub_customer", 500);
    assertTrue(sql.startsWith("SELECT * FROM "), sql);
    assertTrue(sql.contains("hub_customer"), sql);
    assertTrue(sql.toLowerCase().contains("limit 500"), sql);
  }

  @Test
  void previewSelectSql_prefixLimit() {
    DatabaseMeta prefixMeta =
        new DatabaseMeta() {
          @Override
          public String getLimitClausePrefix(int limit) {
            return " TOP " + limit;
          }

          @Override
          public String getLimitClause(int limit) {
            return "";
          }

          @Override
          public String getQuotedSchemaTableCombination(
              org.apache.hop.core.variables.IVariables variables, String schema, String table) {
            return "[" + schema + "].[" + table + "]";
          }
        };
    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            prefixMeta, new Variables(), "dbo", "dim_customer", 1000);
    assertTrue(sql.startsWith("SELECT TOP 1000 * FROM "), sql);
    assertTrue(sql.contains("[dbo].[dim_customer]"), sql);
  }

  @Test
  void previewSelectSql_resolvesVariables() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    Variables variables = new Variables();
    variables.setVariable("MY_SCHEMA", "analytics");
    variables.setVariable("MY_TABLE", "fct_sales");

    String sql =
        ModelTargetTablePreviewSupport.previewSelectSql(
            postgres, variables, "${MY_SCHEMA}", "${MY_TABLE}", 100);
    assertTrue(sql.contains("analytics.fct_sales"), sql);
    assertTrue(sql.toLowerCase().contains("limit 100"), sql);
  }

  @Test
  void previewSelectSql_emptyWhenMetaOrTableNull() {
    assertEquals("", ModelTargetTablePreviewSupport.previewSelectSql(null, new Variables(), "public", "table", 1000));
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    assertEquals("", ModelTargetTablePreviewSupport.previewSelectSql(postgres, new Variables(), "public", null, 1000));
    assertEquals("", ModelTargetTablePreviewSupport.previewSelectSql(postgres, new Variables(), "public", "", 1000));
  }
}
