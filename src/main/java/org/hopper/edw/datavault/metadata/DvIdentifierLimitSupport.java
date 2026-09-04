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

import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;

/**
 * Maximum unquoted identifier (column / table name) lengths for EDW target engines.
 *
 * <p>PostgreSQL {@code NAMEDATALEN − 1} is 63. MySQL / SingleStore allow 64. SQL Server 128.
 * Snowflake 255. Unknown engines use the PostgreSQL limit so Check model fails closed.
 */
public final class DvIdentifierLimitSupport {

  /** PostgreSQL {@code NAMEDATALEN − 1}. Also the default when the target engine is unknown. */
  public static final int POSTGRES_MAX = 63;

  public static final int MYSQL_MAX = 64;
  public static final int SQLSERVER_MAX = 128;
  public static final int SNOWFLAKE_MAX = 255;
  public static final int ORACLE_MAX = 128;
  public static final int DEFAULT_MAX = POSTGRES_MAX;

  private DvIdentifierLimitSupport() {}

  public static int maxColumnNameLength(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return DEFAULT_MAX;
    }
    return maxColumnNameLength(databaseMeta.getPluginId());
  }

  public static int maxColumnNameLength(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return DEFAULT_MAX;
    }
    return switch (pluginId.toUpperCase(Locale.ROOT)) {
      case DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID, "GREENPLUM", "REDSHIFT" -> POSTGRES_MAX;
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              "MARIADB",
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID ->
          MYSQL_MAX;
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          SQLSERVER_MAX;
      case DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID -> SNOWFLAKE_MAX;
      case DvBulkLoadPluginSupport.ORACLE_DB_PLUGIN_ID -> ORACLE_MAX;
      default -> DEFAULT_MAX;
    };
  }

  /**
   * Returns {@code desired} truncated to {@code maxLength} and unique within {@code used}. A
   * numeric suffix is appended on collision; the base is shortened so the result still fits.
   */
  public static String uniqueIdentifier(String desired, int maxLength, Set<String> used) {
    if (Utils.isEmpty(desired)) {
      return desired;
    }
    int max = maxLength > 0 ? maxLength : DEFAULT_MAX;
    String base = desired.length() <= max ? desired : desired.substring(0, max);
    if (used == null || !used.contains(base)) {
      return base;
    }
    int suffix = 2;
    while (true) {
      String suffixText = Integer.toString(suffix);
      int keep = max - suffixText.length();
      if (keep < 1) {
        keep = 1;
      }
      String truncated = base.length() <= keep ? base : base.substring(0, keep);
      String candidate = truncated + suffixText;
      if (!used.contains(candidate)) {
        return candidate;
      }
      suffix++;
    }
  }
}
