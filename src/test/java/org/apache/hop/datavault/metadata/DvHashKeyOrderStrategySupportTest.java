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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvHashKeyOrderStrategySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void javaInvariantTenBeforeHundred() {
    assertTrue(DvHashKeyOrderStrategySupport.javaPlacesTenBeforeHundred());
    assertTrue(
        DvHashKeyOrderStrategySupport.PROBE_TEN.compareTo(
                DvHashKeyOrderStrategySupport.PROBE_HUNDRED)
            < 0);
  }

  @Test
  void binaryHashUsesPlainSqlOrderBy() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashKeyDataType(HashKeyDataType.BINARY.name());
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertFalse(plan.useHopSortRows());
    assertEquals(" ORDER BY \"order_lhk\"", plan.orderBySqlSuffix());
    assertFalse(plan.orderBySqlSuffix().contains("COLLATE"));
    assertTrue(plan.rationale().toLowerCase().contains("binary"));
  }

  @Test
  void stringHashPostgresOfflineUsesStaticTrustCollateC() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertNotNull(plan.orderBySqlSuffix());
    assertTrue(plan.orderBySqlSuffix().contains("ORDER BY \"order_lhk\""));
    assertTrue(plan.orderBySqlSuffix().contains("COLLATE \"C\""));
    // COLLATE is not the bare select-list column — DISTINCT must be wrapped (PostgreSQL error
    // otherwise: "for SELECT DISTINCT, ORDER BY expressions must appear in select list").
    assertTrue(plan.wrapDistinctSubquery());
    assertTrue(plan.rationale().toLowerCase().contains("static trust"));
  }

  @Test
  void stringHashSqlServerOfflineUsesBin2AndDistinctSubquery() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "[order_lhk]", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertTrue(plan.orderBySqlSuffix().contains("ORDER BY [order_lhk]"));
    assertTrue(plan.orderBySqlSuffix().contains("COLLATE Latin1_General_100_BIN2"));
    assertTrue(plan.wrapDistinctSubquery());
  }

  @Test
  void stringHashMysqlOfflineUsesUtf8mb4Bin() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "`order_lhk`", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertTrue(plan.orderBySqlSuffix().contains("COLLATE utf8mb4_bin"));
  }

  @Test
  void stringHashSingleStoreOfflineUsesUtf8mb4Bin() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "`order_lhk`", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertTrue(plan.orderBySqlSuffix().contains("COLLATE utf8mb4_bin"));
  }

  @Test
  void unknownEngineFallsBackToHopSortRows() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId("SNOWFLAKE");

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", null, false);

    assertTrue(plan.useHopSortRows());
    assertNull(plan.orderBySqlSuffix());
  }

  @Test
  void nullDatabaseFallsBackToHopSortRowsForString() {
    DataVaultConfiguration config = stringHashConfig();

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            null, config, new Variables(), "\"order_lhk\"", null, false);

    assertTrue(plan.useHopSortRows());
  }

  @Test
  void hexHashSameStrategyAsString() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashKeyDataType(HashKeyDataType.HEX.name());
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", null, false);

    assertTrue(plan.useSqlOrderBy());
    assertTrue(plan.orderBySqlSuffix().contains("COLLATE \"C\""));
  }

  @Test
  void applyToDistinctSelectWrapsSqlServer() {
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID);
    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, stringHashConfig(), new Variables(), "[hk]", null, false);

    String body = "SELECT DISTINCT [hk], [a] FROM [lnk]";
    String applied = DvHashKeyOrderStrategySupport.applyToDistinctSelect(body, plan);

    assertTrue(
        applied.startsWith("SELECT * FROM (SELECT DISTINCT [hk], [a] FROM [lnk]) hop_lhk_ord"));
    assertTrue(applied.contains("ORDER BY [hk] COLLATE Latin1_General_100_BIN2"));
  }

  @Test
  void applyToDistinctSelectWrapsPostgresWhenCollate() {
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, stringHashConfig(), new Variables(), "\"hk\"", null, false);

    String body = "SELECT DISTINCT \"hk\" FROM \"lnk\"";
    String applied = DvHashKeyOrderStrategySupport.applyToDistinctSelect(body, plan);

    assertEquals(
        "SELECT * FROM (SELECT DISTINCT \"hk\" FROM \"lnk\") hop_lhk_ord"
            + " ORDER BY \"hk\" COLLATE \"C\"",
        applied);
  }

  @Test
  void binaryHashDoesNotWrapDistinctOnPostgres() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashKeyDataType(HashKeyDataType.BINARY.name());
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);

    var plan =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", null, false);

    assertFalse(plan.wrapDistinctSubquery());
    String body = "SELECT DISTINCT \"order_lhk\" FROM \"lnk\"";
    assertEquals(
        "SELECT DISTINCT \"order_lhk\" FROM \"lnk\" ORDER BY \"order_lhk\"",
        DvHashKeyOrderStrategySupport.applyToDistinctSelect(body, plan));
  }

  @Test
  void applyToDistinctSelectNoChangeWhenHopSort() {
    var plan = DvHashKeyOrderStrategySupport.hopSortFallback();
    String body = "SELECT DISTINCT \"hk\" FROM \"lnk\"";
    assertEquals(body, DvHashKeyOrderStrategySupport.applyToDistinctSelect(body, plan));
  }

  @Test
  void sessionCacheRebindsColumnExpression() {
    DataVaultConfiguration config = stringHashConfig();
    DatabaseMeta db = databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    var session = new DvHashKeyOrderStrategySupport.Session();

    var first =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"order_lhk\"", session, false);
    var second =
        DvHashKeyOrderStrategySupport.resolve(
            db, config, new Variables(), "\"other_lhk\"", session, false);

    assertTrue(first.orderBySqlSuffix().contains("\"order_lhk\""));
    assertTrue(second.orderBySqlSuffix().contains("\"other_lhk\""));
    assertTrue(second.orderBySqlSuffix().contains("COLLATE \"C\""));
  }

  @Test
  void probeSqlBuiltForSupportedEngines() {
    assertNotNull(
        DvHashKeyOrderStrategySupport.buildProbeOrderSql(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID), "C"));
    assertNotNull(
        DvHashKeyOrderStrategySupport.buildProbeOrderSql(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID),
            "Latin1_General_100_BIN2"));
    assertNotNull(
        DvHashKeyOrderStrategySupport.buildProbeOrderSql(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID), "utf8mb4_bin"));
    assertNotNull(
        DvHashKeyOrderStrategySupport.buildProbeOrderSql(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.ORACLE_DB_PLUGIN_ID), "BINARY"));
    assertNull(
        DvHashKeyOrderStrategySupport.buildProbeOrderSql(
            databaseMetaWithPluginId("SNOWFLAKE"), "BINARY"));
  }

  @Test
  void probeRowsMatchHopOrder() {
    List<Object[]> good =
        List.of(
            new Object[] {DvHashKeyOrderStrategySupport.PROBE_TEN},
            new Object[] {DvHashKeyOrderStrategySupport.PROBE_HUNDRED});
    List<Object[]> bad =
        List.of(
            new Object[] {DvHashKeyOrderStrategySupport.PROBE_HUNDRED},
            new Object[] {DvHashKeyOrderStrategySupport.PROBE_TEN});

    assertTrue(DvHashKeyOrderStrategySupport.probeRowsMatchHopOrder(good));
    assertFalse(DvHashKeyOrderStrategySupport.probeRowsMatchHopOrder(bad));
    assertFalse(DvHashKeyOrderStrategySupport.probeRowsMatchHopOrder(List.of()));
  }

  @Test
  void candidatesAndStaticTrustRegistry() {
    assertEquals(
        List.of("C", "POSIX"),
        DvHashKeyOrderStrategySupport.candidateCollations(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID)));
    assertTrue(
        DvHashKeyOrderStrategySupport.hasStaticTrust(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID)));
    assertFalse(
        DvHashKeyOrderStrategySupport.hasStaticTrust(databaseMetaWithPluginId("SNOWFLAKE")));
    assertTrue(
        DvHashKeyOrderStrategySupport.staticTrustPluginIds()
            .contains(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID));
  }

  private static DataVaultConfiguration stringHashConfig() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashKeyDataType(HashKeyDataType.STRING.name());
    return config;
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }

      @Override
      public String getName() {
        return "test-" + pluginId;
      }
    };
  }
}
