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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.workflow.action.IAction;

/** Detects Hop bulk-loading transforms and workflow actions available for a target database. */
public final class DvBulkLoadPluginSupport {

  public static final String TABLE_OUTPUT_TRANSFORM_ID = "TableOutput";
  public static final String TEXT_FILE_OUTPUT_TRANSFORM_ID = "TextFileOutput";
  public static final String MYSQL_BULK_LOADER_ID = "MySqlBulkLoader";
  public static final String PG_BULK_LOADER_ID = "PGBulkLoader";
  public static final String ORA_BULK_LOADER_ID = "OraBulkLoader";
  public static final String SNOWFLAKE_BULK_LOADER_ID = "SnowflakeBulkLoader";
  public static final String MONETDB_BULK_LOADER_ID = "MonetDBBulkLoader";
  public static final String VERTICA_BULK_LOADER_ID = "VerticaBulkLoader";
  public static final String DORIS_BULK_LOADER_ID = "DorisBulkLoader";

  public static final String MYSQL_DB_PLUGIN_ID = "MYSQL";
  public static final String SINGLESTORE_DB_PLUGIN_ID = "SINGLESTORE";
  public static final String POSTGRESQL_DB_PLUGIN_ID = "POSTGRESQL";
  public static final String ORACLE_DB_PLUGIN_ID = "ORACLE";
  public static final String MSSQL_DB_PLUGIN_ID = "MSSQL";
  public static final String MSSQLNATIVE_DB_PLUGIN_ID = "MSSQLNATIVE";
  public static final String SNOWFLAKE_DB_PLUGIN_ID = "SNOWFLAKE";
  public static final String MONETDB_DB_PLUGIN_ID = "MONETDB";
  public static final String VERTICA_DB_PLUGIN_ID = "VERTICA5";
  public static final String DORIS_DB_PLUGIN_ID = "DORIS";

  private DvBulkLoadPluginSupport() {}

  /** Describes one target load strategy and the Hop plugin ids that implement it. */
  public record BulkLoadCapability(
      DvTargetLoadMode mode,
      String transformPluginId,
      String actionPluginId,
      boolean requiresLocalFile) {}

  public static boolean isTransformPluginAvailable(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return false;
    }
    try {
      ITransformMeta meta =
          PluginRegistry.getInstance()
              .loadClass(TransformPluginType.class, pluginId, ITransformMeta.class);
      return meta != null;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isActionPluginAvailable(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return false;
    }
    try {
      IAction action =
          PluginRegistry.getInstance().loadClass(ActionPluginType.class, pluginId, IAction.class);
      return action != null;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns the Hop bulk loader transform plugin id for the target database, or {@code null} when
   * inline native bulk loading is not supported for that RDBMS type.
   */
  public static String resolveNativeBulkTransformPluginId(DatabaseMeta targetDatabase) {
    if (targetDatabase == null || Utils.isEmpty(targetDatabase.getPluginId())) {
      return null;
    }
    return switch (targetDatabase.getPluginId()) {
      case MYSQL_DB_PLUGIN_ID, SINGLESTORE_DB_PLUGIN_ID -> MYSQL_BULK_LOADER_ID;
      case POSTGRESQL_DB_PLUGIN_ID -> PG_BULK_LOADER_ID;
      case ORACLE_DB_PLUGIN_ID -> ORA_BULK_LOADER_ID;
      case SNOWFLAKE_DB_PLUGIN_ID -> SNOWFLAKE_BULK_LOADER_ID;
      case MONETDB_DB_PLUGIN_ID -> MONETDB_BULK_LOADER_ID;
      case VERTICA_DB_PLUGIN_ID -> VERTICA_BULK_LOADER_ID;
      case DORIS_DB_PLUGIN_ID -> DORIS_BULK_LOADER_ID;
      default -> null;
    };
  }

  /**
   * Returns load strategies supported for the given target database and installed Hop plugins.
   *
   * <p>Always includes {@link DvTargetLoadMode#TABLE_OUTPUT}. Adds {@link
   * DvTargetLoadMode#NATIVE_BULK} when the matching bulk loader transform plugin is installed, and
   * {@link DvTargetLoadMode#STAGING_FILE} when Text File Output and a staged bulk-load workflow
   * action are available.
   */
  public static List<BulkLoadCapability> resolveCapabilities(DatabaseMeta targetDatabase) {
    List<BulkLoadCapability> capabilities = new ArrayList<>();
    capabilities.add(
        new BulkLoadCapability(
            DvTargetLoadMode.TABLE_OUTPUT, TABLE_OUTPUT_TRANSFORM_ID, null, false));

    String nativeBulkTransformId = resolveNativeBulkTransformPluginId(targetDatabase);
    if (!Utils.isEmpty(nativeBulkTransformId)
        && isTransformPluginAvailable(nativeBulkTransformId)) {
      capabilities.add(
          new BulkLoadCapability(DvTargetLoadMode.NATIVE_BULK, nativeBulkTransformId, null, false));
    }

    String stagingBulkActionId =
        DvBulkLoadCommandSupport.resolveStagingBulkActionPluginId(targetDatabase);
    if (isTransformPluginAvailable(TEXT_FILE_OUTPUT_TRANSFORM_ID)
        && !Utils.isEmpty(stagingBulkActionId)
        && isActionPluginAvailable(stagingBulkActionId)) {
      capabilities.add(
          new BulkLoadCapability(
              DvTargetLoadMode.STAGING_FILE,
              TEXT_FILE_OUTPUT_TRANSFORM_ID,
              stagingBulkActionId,
              true));
    }
    return List.copyOf(capabilities);
  }

  public static List<String> getAvailableModeDescriptions(DatabaseMeta targetDatabase) {
    return resolveCapabilities(targetDatabase).stream()
        .map(capability -> capability.mode().getDescription())
        .toList();
  }

  public static boolean isModeAvailable(DatabaseMeta targetDatabase, DvTargetLoadMode mode) {
    if (mode == null) {
      return false;
    }
    return resolveCapabilities(targetDatabase).stream().anyMatch(cap -> cap.mode() == mode);
  }
}
