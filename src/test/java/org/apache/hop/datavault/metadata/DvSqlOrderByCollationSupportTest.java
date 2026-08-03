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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSqlOrderByCollationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void riskWhenCollationsDiffer() {
    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta("ITMREF_0", "nvarchar", "French_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    assertTrue(DvSqlOrderByCollationSupport.isOrderByRisk(source, target));
  }

  @Test
  void riskWhenUnicodeVersusAnsiEvenIfCollationMissing() {
    var source = new DvSqlOrderByCollationSupport.ColumnSqlMeta("name", "NVARCHAR", null);
    var target = new DvSqlOrderByCollationSupport.ColumnSqlMeta("name", "VARCHAR", null);
    assertTrue(DvSqlOrderByCollationSupport.isOrderByRisk(source, target));
  }

  @Test
  void noRiskWhenTypesAndCollationsMatch() {
    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "code", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "code", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    assertFalse(DvSqlOrderByCollationSupport.isOrderByRisk(source, target));
  }

  @Test
  void bridgePrefersSourceCollation() {
    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta("ITMREF_0", "nvarchar", "French_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");

    assertEquals(
        "French_CI_AS",
        DvSqlOrderByCollationSupport.resolveBridgeCollation(
            source, target, "SrcDefault", "TgtDefault"));
  }

  @Test
  void bridgeFallsBackToDefaultsWhenColumnCollationsMissing() {
    var source = new DvSqlOrderByCollationSupport.ColumnSqlMeta("x", "nvarchar", null);
    var target = new DvSqlOrderByCollationSupport.ColumnSqlMeta("x", "varchar", null);
    assertEquals(
        "SrcDefault",
        DvSqlOrderByCollationSupport.resolveBridgeCollation(
            source, target, "SrcDefault", "TgtDefault"));
    assertEquals(
        "TgtDefault",
        DvSqlOrderByCollationSupport.resolveBridgeCollation(source, target, null, "TgtDefault"));
  }

  @Test
  void noBridgeWhenNoRisk() {
    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "code", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "code", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    assertNull(DvSqlOrderByCollationSupport.resolveBridgeCollation(source, target, null, null));
  }

  @Test
  void parsesColumnMetaRows() {
    Map<String, DvSqlOrderByCollationSupport.ColumnSqlMeta> map =
        DvSqlOrderByCollationSupport.parseColumnMetaRows(
            List.of(
                new Object[] {"ITMREF_0", "nvarchar", "French_CI_AS"},
                new Object[] {"qty", "int", null}));
    assertEquals(2, map.size());
    assertEquals("French_CI_AS", map.get("ITMREF_0").collationName());
    assertEquals("nvarchar", map.get("ITMREF_0").typeName());
  }

  @Test
  void buildsSafeColumnMetaQuery() {
    String sql = DvSqlOrderByCollationSupport.buildColumnMetaQuery("dbo", "STA_XBIITM");
    assertTrue(sql.contains("INFORMATION_SCHEMA.COLUMNS"));
    assertTrue(sql.contains("TABLE_NAME = 'STA_XBIITM'"));
    assertTrue(sql.contains("TABLE_SCHEMA = 'dbo'"));

    String injected = DvSqlOrderByCollationSupport.buildColumnMetaQuery("dbo", "t' OR 1=1");
    assertTrue(injected.contains("TABLE_NAME = 't'' OR 1=1'"));
  }

  @Test
  void buildsDatabaseDefaultCollationQueryPerDialect() {
    org.apache.hop.core.database.DatabaseMeta mssql =
        new org.apache.hop.core.database.DatabaseMeta() {
          @Override
          public String getPluginId() {
            return "MSSQLNATIVE";
          }
        };
    org.apache.hop.core.database.DatabaseMeta postgres =
        new org.apache.hop.core.database.DatabaseMeta() {
          @Override
          public String getPluginId() {
            return "POSTGRESQL";
          }
        };
    assertTrue(
        DvSqlOrderByCollationSupport.buildDatabaseDefaultCollationQuery(mssql)
            .contains("DATABASEPROPERTYEX"));
    assertTrue(
        DvSqlOrderByCollationSupport.buildDatabaseDefaultCollationQuery(postgres)
            .contains("pg_database"));
  }
}
