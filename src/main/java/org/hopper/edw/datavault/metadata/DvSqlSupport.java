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

import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/** SQL helpers for generated Data Vault update pipelines. */
public final class DvSqlSupport {

  private DvSqlSupport() {}

  /**
   * Typed NULL for a record-source placeholder column so JDBC drivers report String, not Binary
   * (MySQL maps bare {@code NULL AS col} to Binary in MergeRows).
   */
  public static String typedNullString(
      DatabaseMeta databaseMeta, IVariables variables, DataVaultConfiguration config) {
    String lengthString =
        (config != null && !Utils.isEmpty(config.getRecordSourceFieldLength()))
            ? config.getRecordSourceFieldLength()
            : "100";
    if (variables != null) {
      lengthString = variables.resolve(lengthString);
    }
    return typedNullString(databaseMeta, Const.toInt(lengthString, 100));
  }

  /** Pretty-print generated SQL for display in Hop pipeline transforms. */
  public static String formatForDisplay(String sql) {
    return DvSqlFormattingSupport.formatForDisplay(sql);
  }

  /** Assign SQL to a Table Input transform using {@link #formatForDisplay(String)}. */
  public static void assignDisplaySql(TableInputMeta tableInputMeta, String sql) {
    if (tableInputMeta == null) {
      return;
    }
    tableInputMeta.setSql(formatForDisplay(sql));
  }

  public static String typedNullString(DatabaseMeta databaseMeta, int length) {
    if (databaseMeta != null) {
      String pluginId = databaseMeta.getPluginId();
      if ("MYSQL".equalsIgnoreCase(pluginId) || "SINGLESTORE".equalsIgnoreCase(pluginId)) {
        return "CAST(NULL AS CHAR(" + length + "))";
      }
    }
    return "CAST(NULL AS VARCHAR(" + length + "))";
  }
}
