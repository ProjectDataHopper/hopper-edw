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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvBulkLoadPluginSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void tableOutputIsAlwaysAvailable() {
    assertTrue(DvBulkLoadPluginSupport.isTransformPluginAvailable("TableOutput"));
    assertTrue(DvBulkLoadPluginSupport.isModeAvailable(null, DvTargetLoadMode.TABLE_OUTPUT));
    assertTrue(
        DvBulkLoadPluginSupport.resolveCapabilities(null).stream()
            .anyMatch(cap -> cap.mode() == DvTargetLoadMode.TABLE_OUTPUT));
  }

  @Test
  void resolvesMysqlAndPostgresNativeBulkWhenPluginsInstalled() throws Exception {
    DatabaseMeta mysql =
        new DatabaseMeta("mysql-test", "MySQL", "Native", "", "localhost", "test", "root", "");
    DatabaseMeta postgres =
        new DatabaseMeta(
            "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "postgres", "");

    assertEquals(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(mysql));
    assertEquals(
        DvBulkLoadPluginSupport.PG_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(postgres));

    if (DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID)) {
      assertTrue(DvBulkLoadPluginSupport.isModeAvailable(mysql, DvTargetLoadMode.NATIVE_BULK));
    }
    if (DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)) {
      assertTrue(DvBulkLoadPluginSupport.isModeAvailable(postgres, DvTargetLoadMode.NATIVE_BULK));
    }
  }

  @Test
  void singleStoreUsesMysqlBulkLoader() throws Exception {
    DatabaseMeta singleStore = singleStoreDatabaseMeta();
    assertEquals(
        DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(singleStore));
  }

  @Test
  void stagingFileAdvertisedWhenTextFileOutputAndBulkActionInstalled() throws Exception {
    DatabaseMeta mysql =
        new DatabaseMeta("mysql-test", "MySQL", "Native", "", "localhost", "test", "root", "");
    if (DvBulkLoadPluginSupport.isTransformPluginAvailable(
            DvBulkLoadPluginSupport.TEXT_FILE_OUTPUT_TRANSFORM_ID)
        && DvBulkLoadPluginSupport.isActionPluginAvailable(
            DvBulkLoadCommandSupport.MYSQL_BULK_LOAD_ACTION_ID)) {
      assertTrue(DvBulkLoadPluginSupport.isModeAvailable(mysql, DvTargetLoadMode.STAGING_FILE));
      assertTrue(
          DvBulkLoadPluginSupport.resolveCapabilities(mysql).stream()
              .anyMatch(cap -> cap.mode() == DvTargetLoadMode.STAGING_FILE));
    } else {
      assertFalse(DvBulkLoadPluginSupport.isModeAvailable(mysql, DvTargetLoadMode.STAGING_FILE));
    }
  }

  @Test
  void resolvesExtendedNativeBulkTransformIds() {
    assertEquals(
        DvBulkLoadPluginSupport.ORA_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.ORACLE_DB_PLUGIN_ID)));
    assertEquals(
        DvBulkLoadPluginSupport.SNOWFLAKE_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID)));
    assertEquals(
        DvBulkLoadPluginSupport.MONETDB_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.MONETDB_DB_PLUGIN_ID)));
    assertEquals(
        DvBulkLoadPluginSupport.VERTICA_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.VERTICA_DB_PLUGIN_ID)));
    assertEquals(
        DvBulkLoadPluginSupport.DORIS_BULK_LOADER_ID,
        DvBulkLoadPluginSupport.resolveNativeBulkTransformPluginId(
            databaseMetaWithPluginId(DvBulkLoadPluginSupport.DORIS_DB_PLUGIN_ID)));
  }

  @Test
  void resolvesMssqlStagingActionWhenPluginInstalled() {
    DatabaseMeta mssql = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID);
    assertEquals(
        DvBulkLoadCommandSupport.MSSQL_BULK_LOAD_ACTION_ID,
        DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(mssql));

    if (DvBulkLoadPluginSupport.isTransformPluginAvailable(
            DvBulkLoadPluginSupport.TEXT_FILE_OUTPUT_TRANSFORM_ID)
        && DvBulkLoadPluginSupport.isActionPluginAvailable(
            DvBulkLoadCommandSupport.MSSQL_BULK_LOAD_ACTION_ID)) {
      assertTrue(DvBulkLoadPluginSupport.isModeAvailable(mssql, DvTargetLoadMode.STAGING_FILE));
    }
  }

  private static DatabaseMeta singleStoreDatabaseMeta() {
    return new DatabaseMeta(
        "singlestore-test", "SingleStore (MemSQL)", "Native", "", "localhost", "test", "root", "");
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName("test-db");
    return databaseMeta;
  }
}
