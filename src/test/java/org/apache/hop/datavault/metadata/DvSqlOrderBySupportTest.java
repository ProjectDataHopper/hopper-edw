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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvSqlOrderBySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void noCollationWithoutSessionEvenOnSqlServer() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setDataType("String");

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[ITMREF_0]", businessKey, databaseMeta, new DataVaultConfiguration(), new Variables());

    assertEquals("[ITMREF_0]", expression);
  }

  @Test
  void skipsCollationForIntegerBusinessKey() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    BusinessKey businessKey = new BusinessKey("customer_id");
    businessKey.setDataType("Integer");

    var source = new DvSqlOrderByCollationSupport.ColumnSqlMeta("customer_id", "int", null);
    var target = new DvSqlOrderByCollationSupport.ColumnSqlMeta("customer_id", "int", null);
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("customer_id", source), Map.of("customer_id", target), null, null);

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[customer_id]",
            businessKey,
            databaseMeta,
            new DataVaultConfiguration(),
            new Variables(),
            session);

    assertEquals("[customer_id]", expression);
    assertFalse(expression.contains("COLLATE"));
  }

  @Test
  void skipsCollationForUnsupportedDatabase() {
    DatabaseMeta databaseMeta =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);
    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setDataType("String");

    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta("ITMREF_0", "nvarchar", "French_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("ITMREF_0", source), Map.of("ITMREF_0", target), null, null);

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "\"ITMREF_0\"",
            businessKey,
            databaseMeta,
            new DataVaultConfiguration(),
            new Variables(),
            session);

    assertEquals("\"ITMREF_0\"", expression);
  }

  @Test
  void autoAppliesQuotedBridgeCollationOnPostgreSql() {
    DatabaseMeta databaseMeta =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    BusinessKey businessKey = new BusinessKey("item_code");
    businessKey.setSourceFieldName("item_code");
    businessKey.setDataType("String");

    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "item_code", "character varying", "fr-FR-x-icu");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "item_code", "character varying", "en_US.utf8");
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("item_code", source), Map.of("item_code", target), null, null);

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "\"item_code\"",
            businessKey,
            databaseMeta,
            new DataVaultConfiguration(),
            new Variables(),
            session);

    assertEquals("\"item_code\" COLLATE \"fr-FR-x-icu\"", expression);
    assertTrue(DvSqlOrderBySupport.isCollationOrderBySupported(databaseMeta));
    assertTrue(DvSqlOrderBySupport.isPostgreSql(databaseMeta));
  }

  @Test
  void autoAppliesBridgeCollationWhenSessionShowsRisk() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setSourceFieldName("ITMREF_0");
    businessKey.setDataType("String");

    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta("ITMREF_0", "nvarchar", "French_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("ITMREF_0", source), Map.of("ITMREF_0", target), null, null);

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[ITMREF_0]",
            businessKey,
            databaseMeta,
            new DataVaultConfiguration(),
            new Variables(),
            session);

    assertEquals("[ITMREF_0] COLLATE French_CI_AS", expression);
  }

  @Test
  void noAutoCollationWhenSessionAligned() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();
    BusinessKey businessKey = new BusinessKey("ITMREF_0");
    businessKey.setSourceFieldName("ITMREF_0");
    businessKey.setDataType("String");

    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "ITMREF_0", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("ITMREF_0", source), Map.of("ITMREF_0", target), null, null);

    String expression =
        DvSqlOrderBySupport.orderExpression(
            "[ITMREF_0]",
            businessKey,
            databaseMeta,
            new DataVaultConfiguration(),
            new Variables(),
            session);

    assertEquals("[ITMREF_0]", expression);
  }

  @Test
  void satelliteOrderByCollatesStringDrivingKeyNotLoadDate() {
    DatabaseMeta databaseMeta = mssqlDatabaseMeta();

    var source =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta("phone_type", "nvarchar", "French_CI_AS");
    var target =
        new DvSqlOrderByCollationSupport.ColumnSqlMeta(
            "phone_type", "varchar", "SQL_Latin1_General_CP1_CI_AS");
    DvSqlOrderByCollationSupport.Session session =
        new DvSqlOrderByCollationSupport.Session(
            Map.of("phone_type", source), Map.of("phone_type", target), null, null);

    List<DvSqlOrderBySupport.OrderByField> fields =
        List.of(
            new DvSqlOrderBySupport.OrderByField("[hk]", null, "hk", true),
            new DvSqlOrderBySupport.OrderByField("[phone_type]", "phone_type", "phone_type", true),
            new DvSqlOrderBySupport.OrderByField("[load_date]", null, null, false));

    StringBuilder sql = new StringBuilder("SELECT 1 FROM sat");
    DvSqlOrderBySupport.appendOrderByFields(
        sql, fields, databaseMeta, new DataVaultConfiguration(), new Variables(), session);

    String out = sql.toString();
    assertTrue(out.contains("[phone_type] COLLATE French_CI_AS"));
    assertTrue(out.contains("[load_date]"));
    assertFalse(out.contains("[load_date] COLLATE"));
    // hash with no session risk stays bare
    assertTrue(out.contains("[hk]"));
    assertFalse(out.contains("[hk] COLLATE"));
  }

  private static DatabaseMeta mssqlDatabaseMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}
