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
package org.apache.hop.datavault.resourcedefinition;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/**
 * Lightweight SQL helpers for dimensional source queries: table references, alias binding, and
 * {@code alias.*} star detection. Not a full SQL parser.
 */
public final class SqlSourceLineageSupport {

  private static final Pattern SQL_IDENTIFIER =
      Pattern.compile("(?i)(?<![\\w.])([A-Za-z_][A-Za-z0-9_]*)(?![\\w.])");

  /** FROM/JOIN table [AS] alias */
  private static final Pattern TABLE_ALIAS =
      Pattern.compile(
          "(?i)\\b(?:from|join)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*))?");

  private static final Pattern STAR_ALIAS =
      Pattern.compile("(?i)\\b([A-Za-z_][A-Za-z0-9_]*)\\.\\*");

  private SqlSourceLineageSupport() {}

  /** Lower-case physical/logical table names referenced in SQL (FROM/JOIN tokens). */
  public static Set<String> extractTableNames(String sql) {
    Set<String> names = new LinkedHashSet<>();
    if (Utils.isEmpty(sql)) {
      return names;
    }
    Matcher matcher = TABLE_ALIAS.matcher(sql);
    while (matcher.find()) {
      String table = matcher.group(1);
      if (!Utils.isEmpty(table) && !isSqlKeyword(table)) {
        names.add(table.toLowerCase(Locale.ROOT));
      }
    }
    // Fallback: generic identifiers (may include column names; callers filter against known
    // tables).
    if (names.isEmpty()) {
      Matcher id = SQL_IDENTIFIER.matcher(sql);
      while (id.find()) {
        String token = id.group(1);
        if (!Utils.isEmpty(token) && !isSqlKeyword(token)) {
          names.add(token.toLowerCase(Locale.ROOT));
        }
      }
    }
    return names;
  }

  /**
   * Map of alias (lower) → table name (lower) from {@code FROM table alias} / {@code JOIN table
   * alias} clauses. When alias is omitted, table maps to itself.
   */
  public static Map<String, String> extractTableAliases(String sql) {
    Map<String, String> aliases = new LinkedHashMap<>();
    if (Utils.isEmpty(sql)) {
      return aliases;
    }
    Matcher matcher = TABLE_ALIAS.matcher(sql);
    while (matcher.find()) {
      String table = matcher.group(1);
      String alias = matcher.group(2);
      if (Utils.isEmpty(table) || isSqlKeyword(table)) {
        continue;
      }
      String tableKey = table.toLowerCase(Locale.ROOT);
      if (!Utils.isEmpty(alias) && !isSqlKeyword(alias)) {
        aliases.put(alias.toLowerCase(Locale.ROOT), tableKey);
      }
      aliases.putIfAbsent(tableKey, tableKey);
    }
    return aliases;
  }

  /** Aliases used with {@code alias.*} in the SELECT list. */
  public static Set<String> extractStarAliases(String sql) {
    Set<String> stars = new LinkedHashSet<>();
    if (Utils.isEmpty(sql)) {
      return stars;
    }
    Matcher matcher = STAR_ALIAS.matcher(sql);
    while (matcher.find()) {
      String alias = matcher.group(1);
      if (!Utils.isEmpty(alias)) {
        stars.add(alias.toLowerCase(Locale.ROOT));
      }
    }
    return stars;
  }

  public static boolean referencesTable(String sql, String tableName) {
    if (Utils.isEmpty(sql) || Utils.isEmpty(tableName)) {
      return false;
    }
    String want = tableName.toLowerCase(Locale.ROOT);
    return extractTableNames(sql).contains(want);
  }

  private static boolean isSqlKeyword(String token) {
    if (Utils.isEmpty(token)) {
      return true;
    }
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "select",
              "from",
              "where",
              "join",
              "inner",
              "left",
              "right",
              "outer",
              "full",
              "cross",
              "on",
              "and",
              "or",
              "as",
              "group",
              "order",
              "by",
              "having",
              "union",
              "all",
              "distinct",
              "limit",
              "offset",
              "case",
              "when",
              "then",
              "else",
              "end",
              "with",
              "in",
              "not",
              "null",
              "is",
              "between",
              "like",
              "exists",
              "true",
              "false" ->
          true;
      default -> false;
    };
  }
}
