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
package org.hopper.edw.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvDdlSupport;

/**
 * Parses Hop-generated CREATE/ALTER DDL into structured {@link DdlDelta} entries for lineage
 * explanation.
 */
public final class DdlDeltaClassifier {

  private static final Pattern ALTER_ADD =
      Pattern.compile(
          "(?is)ALTER\\s+TABLE\\s+([\\w.\"`\\[\\]]+)\\s+ADD(?:\\s+COLUMN)?\\s+([\\w.\"`\\[\\]]+)");
  private static final Pattern ALTER_MODIFY =
      Pattern.compile(
          "(?is)ALTER\\s+TABLE\\s+([\\w.\"`\\[\\]]+)\\s+(?:ALTER|MODIFY)(?:\\s+COLUMN)?\\s+([\\w.\"`\\[\\]]+)");
  private static final Pattern ALTER_DROP =
      Pattern.compile(
          "(?is)ALTER\\s+TABLE\\s+([\\w.\"`\\[\\]]+)\\s+DROP(?:\\s+COLUMN)?\\s+([\\w.\"`\\[\\]]+)");
  private static final Pattern ALTER_TABLE_ONLY =
      Pattern.compile("(?is)ALTER\\s+TABLE\\s+([\\w.\"`\\[\\]]+)");

  private DdlDeltaClassifier() {}

  public static List<DdlDelta> classify(List<String> ddlStatements) {
    List<DdlDelta> deltas = new ArrayList<>();
    if (ddlStatements == null) {
      return deltas;
    }
    for (String ddl : ddlStatements) {
      if (Utils.isEmpty(ddl)) {
        continue;
      }
      // One Hop DDL blob may contain multiple statements
      for (String statement : splitStatements(ddl)) {
        DdlDelta delta = classifyOne(statement.trim());
        if (delta != null) {
          deltas.add(delta);
        }
      }
    }
    return deltas;
  }

  static DdlDelta classifyOne(String sql) {
    if (Utils.isEmpty(sql)) {
      return null;
    }
    if (isCreateTable(sql)) {
      String table = unquote(DvDdlSupport.extractCreateTableName(sql));
      DdlDelta delta = new DdlDelta(DdlDeltaType.CREATE_TABLE, table, null, sql);
      delta.setSummary("CREATE TABLE " + nvl(table));
      return delta;
    }

    Matcher add = ALTER_ADD.matcher(sql);
    if (add.find()) {
      DdlDelta delta =
          new DdlDelta(DdlDeltaType.ADD_COLUMN, unquote(add.group(1)), unquote(add.group(2)), sql);
      delta.setSummary("ADD COLUMN " + delta.getTableName() + "." + delta.getColumnName());
      return delta;
    }

    Matcher drop = ALTER_DROP.matcher(sql);
    if (drop.find()) {
      DdlDelta delta =
          new DdlDelta(
              DdlDeltaType.DROP_COLUMN, unquote(drop.group(1)), unquote(drop.group(2)), sql);
      delta.setSummary("DROP COLUMN " + delta.getTableName() + "." + delta.getColumnName());
      return delta;
    }

    Matcher modify = ALTER_MODIFY.matcher(sql);
    if (modify.find()) {
      DdlDelta delta =
          new DdlDelta(
              DdlDeltaType.ALTER_COLUMN, unquote(modify.group(1)), unquote(modify.group(2)), sql);
      delta.setSummary("ALTER COLUMN " + delta.getTableName() + "." + delta.getColumnName());
      return delta;
    }

    Matcher alterOnly = ALTER_TABLE_ONLY.matcher(sql);
    if (alterOnly.find()) {
      DdlDelta delta = new DdlDelta(DdlDeltaType.OTHER, unquote(alterOnly.group(1)), null, sql);
      delta.setSummary("ALTER TABLE " + delta.getTableName());
      return delta;
    }

    DdlDelta other = new DdlDelta(DdlDeltaType.OTHER, null, null, sql);
    other.setSummary("DDL change");
    return other;
  }

  private static List<String> splitStatements(String ddl) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : ddl.split("\\R")) {
      current.append(line).append('\n');
      if (line.trim().endsWith(";")) {
        parts.add(current.toString());
        current.setLength(0);
      }
    }
    if (current.length() > 0 && !current.toString().isBlank()) {
      parts.add(current.toString());
    }
    if (parts.isEmpty()) {
      parts.add(ddl);
    }
    return parts;
  }

  private static String unquote(String identifier) {
    if (Utils.isEmpty(identifier)) {
      return identifier;
    }
    String id = identifier.trim();
    // schema.table → table for lineage matching on physical name
    int dot = id.lastIndexOf('.');
    if (dot >= 0 && dot < id.length() - 1) {
      id = id.substring(dot + 1);
    }
    if ((id.startsWith("\"") && id.endsWith("\""))
        || (id.startsWith("`") && id.endsWith("`"))
        || (id.startsWith("[") && id.endsWith("]"))) {
      id = id.substring(1, id.length() - 1);
    }
    return id;
  }

  private static boolean isCreateTable(String ddl) {
    return ddl.trim().regionMatches(true, 0, "CREATE", 0, 6);
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
