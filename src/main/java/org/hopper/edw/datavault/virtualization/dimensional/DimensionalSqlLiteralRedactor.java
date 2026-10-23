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
package org.hopper.edw.datavault.virtualization.dimensional;

/** Replaces string and numeric literals in logged SQL so the audit table does not store probes. */
public final class DimensionalSqlLiteralRedactor {

  private DimensionalSqlLiteralRedactor() {}

  public record Redaction(String sql, int literalCount) {}

  public static Redaction redact(String sql) {
    if (sql == null || sql.isEmpty()) {
      return new Redaction(sql == null ? "" : sql, 0);
    }
    StringBuilder out = new StringBuilder(sql.length());
    int count = 0;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        int end = sql.indexOf('\n', i);
        if (end < 0) {
          out.append(sql.substring(i));
          break;
        }
        out.append(sql, i, end);
        i = end - 1;
        continue;
      }
      if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        int end = sql.indexOf("*/", i + 2);
        if (end < 0) {
          out.append(sql.substring(i));
          break;
        }
        out.append(sql, i, end + 2);
        i = end + 1;
        continue;
      }
      if (c == '\'' ) {
        i = skipString(sql, i);
        out.append('?');
        count++;
        continue;
      }
      if (c == '"') {
        int end = skipQuoted(sql, i, '"');
        out.append(sql, i, Math.min(sql.length(), end + 1));
        i = end;
        continue;
      }
      if (c == '`') {
        int end = skipQuoted(sql, i, '`');
        out.append(sql, i, Math.min(sql.length(), end + 1));
        i = end;
        continue;
      }
      if (c == '[') {
        int end = sql.indexOf(']', i + 1);
        if (end < 0) {
          out.append(c);
          continue;
        }
        out.append(sql, i, end + 1);
        i = end;
        continue;
      }
      if (isNumberStart(sql, i)) {
        int end = i + 1;
        while (end < sql.length() && (Character.isDigit(sql.charAt(end)) || sql.charAt(end) == '.')) {
          end++;
        }
        out.append('?');
        count++;
        i = end - 1;
        continue;
      }
      out.append(c);
    }
    return new Redaction(out.toString(), count);
  }

  /** True when a second statement follows a semicolon that is not inside a literal or comment. */
  public static boolean hasExtraStatement(String sql) {
    if (sql == null) {
      return false;
    }
    boolean sawSemicolon = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        int end = sql.indexOf('\n', i);
        if (end < 0) {
          break;
        }
        i = end;
        continue;
      }
      if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        int end = sql.indexOf("*/", i + 2);
        if (end < 0) {
          break;
        }
        i = end + 1;
        continue;
      }
      if (c == '\'') {
        i = skipString(sql, i);
        continue;
      }
      if (c == '"' || c == '`') {
        i = skipQuoted(sql, i, c);
        continue;
      }
      if (c == ';') {
        sawSemicolon = true;
        continue;
      }
      if (sawSemicolon && !Character.isWhitespace(c)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNumberStart(String sql, int i) {
    char c = sql.charAt(i);
    if (!Character.isDigit(c)) {
      return false;
    }
    if (i == 0) {
      return true;
    }
    char previous = sql.charAt(i - 1);
    return !Character.isLetterOrDigit(previous) && previous != '_' && previous != '.';
  }

  private static int skipString(String sql, int open) {
    for (int i = open + 1; i < sql.length(); i++) {
      if (sql.charAt(i) == '\'') {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          i++;
          continue;
        }
        return i;
      }
    }
    return sql.length() - 1;
  }

  private static int skipQuoted(String sql, int open, char quote) {
    for (int i = open + 1; i < sql.length(); i++) {
      if (sql.charAt(i) == quote) {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
          i++;
          continue;
        }
        return i;
      }
    }
    return sql.length() - 1;
  }
}
