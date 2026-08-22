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
package org.hopper.edw.datavault.metadata.targettypemapping;

import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/** Normalize and compare native SQL type strings for DDL CREATE/ALTER. */
public final class TargetTypeSqlSupport {

  private static final Pattern TRAILING_CR = Pattern.compile("[\\r\\n]+$");

  private TargetTypeSqlSupport() {}

  public static String stripTrailingWhitespace(String sql) {
    if (sql == null) {
      return "";
    }
    return TRAILING_CR.matcher(sql).replaceAll("").trim();
  }

  /**
   * Normalize a SQL type token for comparison: uppercase, collapse whitespace, apply common
   * aliases, and attach length/precision when the token has no parentheses.
   */
  public static String normalizeSqlType(String sqlType, int length, int precision) {
    if (Utils.isEmpty(sqlType)) {
      return "";
    }
    String type = sqlType.trim().toUpperCase(Locale.ROOT);
    type = type.replaceAll("\\s+", " ");
    type = type.replace("CHARACTER VARYING", "VARCHAR");
    type = type.replace("NATIONAL CHARACTER VARYING", "NVARCHAR");
    type = type.replace("NATIONAL CHARACTER", "NCHAR");
    if (type.equals("CHARACTER") || type.startsWith("CHARACTER(")) {
      type = type.replaceFirst("CHARACTER", "CHAR");
    }
    type =
        switch (type) {
          case "BPCHAR" -> "CHAR";
          case "INT2" -> "SMALLINT";
          case "INT4", "INT" -> "INTEGER";
          case "INT8" -> "BIGINT";
          case "BOOL" -> "BOOLEAN";
          case "TIMESTAMPTZ" -> "TIMESTAMP WITH TIME ZONE";
          case "TIMESTAMP WITHOUT TIME ZONE" -> "TIMESTAMP";
          default -> type;
        };
    if (!type.contains("(") && length > 0) {
      if (isLengthTyped(type)) {
        if (isNumericType(type) && precision >= 0) {
          type = type + "(" + length + ", " + precision + ")";
        } else if (!isNumericType(type)) {
          type = type + "(" + length + ")";
        }
      }
    }
    return type;
  }

  public static String normalizeSqlType(String sqlType) {
    return normalizeSqlType(sqlType, -1, -1);
  }

  public static String physicalSqlType(IValueMeta valueMeta) {
    if (valueMeta == null) {
      return "";
    }
    String original = valueMeta.getOriginalColumnTypeName();
    if (Utils.isEmpty(original)) {
      return "";
    }
    return normalizeSqlType(original, valueMeta.getLength(), valueMeta.getPrecision());
  }

  public static boolean sameNormalizedType(String left, String right) {
    return normalizeSqlType(left).equals(normalizeSqlType(right));
  }

  /**
   * Replace every occurrence of {@code hopTypeToken} in {@code sql} with {@code desiredType}. No-op
   * when the Hop token is empty or already equals the desired type.
   */
  public static String replaceTypeToken(String sql, String hopTypeToken, String desiredType) {
    if (Utils.isEmpty(sql) || Utils.isEmpty(hopTypeToken) || Utils.isEmpty(desiredType)) {
      return sql;
    }
    String hop = stripTrailingWhitespace(hopTypeToken);
    String desired = stripTrailingWhitespace(desiredType);
    if (hop.isEmpty() || hop.equals(desired)) {
      return sql;
    }
    return sql.replace(hop, desired);
  }

  private static boolean isLengthTyped(String type) {
    return type.equals("CHAR")
        || type.equals("VARCHAR")
        || type.equals("NCHAR")
        || type.equals("NVARCHAR")
        || isNumericType(type);
  }

  private static boolean isNumericType(String type) {
    return type.equals("NUMERIC") || type.equals("DECIMAL") || type.equals("NUMBER");
  }
}
