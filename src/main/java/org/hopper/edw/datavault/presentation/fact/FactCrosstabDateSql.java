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
package org.hopper.edw.datavault.presentation.fact;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;

/**
 * Renders a date format mask in the target SQL dialect.
 *
 * <p>The value must already be text when a group filter compares it. A Java conversion mask on the
 * group column alone formats the label and leaves the source timestamp unchanged, so the nested
 * crosstab receives no rows.
 */
final class FactCrosstabDateSql {

  private FactCrosstabDateSql() {}

  static boolean isDateMask(String mask) {
    if (Utils.isEmpty(mask)) {
      return false;
    }
    for (int i = 0; i < mask.length(); i++) {
      switch (mask.charAt(i)) {
        case 'y', 'Y', 'M', 'd', 'D', 'H', 'h' -> {
          return true;
        }
        default -> {}
      }
    }
    return false;
  }

  /**
   * @return a SQL expression, or {@code null} when this dialect should keep the raw column
   */
  static String expression(DatabaseMeta databaseMeta, String qualifiedColumn, String javaMask) {
    if (databaseMeta == null || Utils.isEmpty(qualifiedColumn) || !isDateMask(javaMask)) {
      return null;
    }
    String pluginId = Const.NVL(databaseMeta.getPluginId(), "");
    String pattern = quoteLiteral(javaMask);
    if (DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return "to_char(" + qualifiedColumn + ", " + quoteLiteral(toPostgres(javaMask)) + ")";
    }
    if (DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return "DATE_FORMAT(" + qualifiedColumn + ", " + quoteLiteral(toMysql(javaMask)) + ")";
    }
    if (DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return "FORMAT(" + qualifiedColumn + ", " + pattern + ")";
    }
    if (DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return "TO_VARCHAR(" + qualifiedColumn + ", " + quoteLiteral(toPostgres(javaMask)) + ")";
    }
    return null;
  }

  private static String quoteLiteral(String text) {
    return "'" + text.replace("'", "''") + "'";
  }

  private static String toPostgres(String javaMask) {
    return translate(
        javaMask,
        new String[][] {
          {"yyyy", "YYYY"},
          {"yy", "YY"},
          {"MMMM", "Month"},
          {"MMM", "Mon"},
          {"MM", "MM"},
          {"dd", "DD"},
          {"HH", "HH24"},
          {"hh", "HH12"},
          {"mm", "MI"},
          {"ss", "SS"},
          {"a", "AM"}
        });
  }

  private static String toMysql(String javaMask) {
    return translate(
        javaMask,
        new String[][] {
          {"yyyy", "%Y"},
          {"yy", "%y"},
          {"MMMM", "%M"},
          {"MMM", "%b"},
          {"MM", "%m"},
          {"dd", "%d"},
          {"HH", "%H"},
          {"hh", "%h"},
          {"mm", "%i"},
          {"ss", "%s"},
          {"a", "%p"}
        });
  }

  private static String translate(String javaMask, String[][] tokens) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < javaMask.length()) {
      boolean matched = false;
      for (String[] token : tokens) {
        if (javaMask.startsWith(token[0], i)) {
          out.append(token[1]);
          i += token[0].length();
          matched = true;
          break;
        }
      }
      if (!matched) {
        out.append(javaMask.charAt(i));
        i++;
      }
    }
    return out.toString();
  }
}
