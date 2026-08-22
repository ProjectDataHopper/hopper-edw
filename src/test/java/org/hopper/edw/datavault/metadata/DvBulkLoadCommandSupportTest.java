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

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvBulkLoadCommandSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesMssqlStagingAction() {
    DatabaseMeta mssql = databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID);
    assertEquals(
        DvBulkLoadCommandSupport.MSSQL_BULK_LOAD_ACTION_ID,
        DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(mssql));
  }

  @Test
  void snowflakeHasNoStagingFileAction() {
    DatabaseMeta snowflake =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID);
    assertEquals(null, DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(snowflake));
  }

  @Test
  void snowflakeTargetSchemaDefaultsToPublic() {
    DatabaseMeta snowflake =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID;
          }

          @Override
          public String getPreferredSchemaName() {
            return "";
          }
        };
    assertEquals(
        "PUBLIC", DvBulkLoadTransformSupport.resolveSnowflakeTargetSchema(snowflake, null));
    DatabaseMeta withSchema =
        new DatabaseMeta() {
          @Override
          public String getPreferredSchemaName() {
            return "MARTS";
          }
        };
    assertEquals(
        "MARTS", DvBulkLoadTransformSupport.resolveSnowflakeTargetSchema(withSchema, null));
  }

  @Test
  void resolvesPostgresStagingActionAsPipelineWhenPgBulkLoaderInstalled() {
    DatabaseMeta postgres =
        new DatabaseMeta(
            "postgres-test", "PostgreSQL", "Native", "", "localhost", "test", "postgres", "");
    if (!DvBulkLoadPluginSupport.isTransformPluginAvailable(
        DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)) {
      return;
    }
    assertEquals(
        DvBulkLoadCommandSupport.PIPELINE_ACTION_ID,
        DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(postgres));
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
