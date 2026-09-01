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
package org.hopper.edw.datavault.expression;

import org.apache.hop.core.util.Utils;

/**
 * Rewrites dialect CAST forms that Calcite's default parser does not accept:
 *
 * <ul>
 *   <li>{@code expr :> TYPE} (ClickHouse-style, used in issue #150 examples)
 *   <li>{@code expr::TYPE} (PostgreSQL)
 * </ul>
 *
 * into standard {@code CAST(expr AS TYPE)}. String literals are left untouched.
 */
public final class SqlCastRewrite {

  private SqlCastRewrite() {}

  public static String rewrite(String sql) throws SqlExpressionException {
    if (Utils.isEmpty(sql)) {
      return sql;
    }
    String current = sql;
    String previous;
    int guard = 0;
    do {
      previous = current;
      current = rewriteOnce(current);
      guard++;
      if (guard > 64) {
        throw new SqlExpressionException("Too many CAST rewrites in expression: " + sql);
      }
    } while (!current.equals(previous));
    return current;
  }

  private static String rewriteOnce(String sql) throws SqlExpressionException {
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"') {
        i = skipQuoted(sql, i);
        continue;
      }
      if (c == ':' && i + 1 < sql.length()) {
        char next = sql.charAt(i + 1);
        if (next == ':') {
          return wrapCast(sql, i, i + 2);
        }
        if (next == '>') {
          return wrapCast(sql, i, i + 2);
        }
      }
      i++;
    }
    return sql;
  }

  private static String wrapCast(String sql, int operatorStart, int operatorEnd)
      throws SqlExpressionException {
    int typeStart = skipWhitespace(sql, operatorEnd);
    TypeSpan type = readType(sql, typeStart);
    if (type == null) {
      throw new SqlExpressionException(
          "CAST operator at position " + operatorStart + " is missing a type: " + sql);
    }
    int exprEnd = skipWhitespaceBack(sql, operatorStart);
    int exprStart = findOperandStart(sql, exprEnd);
    if (exprStart < 0 || exprStart > exprEnd) {
      throw new SqlExpressionException(
          "CAST operator at position " + operatorStart + " is missing an expression: " + sql);
    }
    String expr = sql.substring(exprStart, exprEnd + 1).trim();
    String typeSql = sql.substring(type.start, type.end).trim();
    String rewritten = "CAST(" + expr + " AS " + typeSql + ")";
    return sql.substring(0, exprStart) + rewritten + sql.substring(type.end);
  }

  private static int findOperandStart(String sql, int exprEnd) throws SqlExpressionException {
    if (exprEnd < 0) {
      return -1;
    }
    int i = exprEnd;
    char c = sql.charAt(i);
    if (c == '\'' || c == '"') {
      return findQuoteStart(sql, i);
    }
    if (c == ')') {
      return findMatchingOpenParen(sql, i);
    }
    if (isIdentPart(c) || c == '.') {
      int start = scanIdentBack(sql, i);
      if (isKeywordEnd(sql, start, i + 1)) {
        return findCaseStart(sql, start);
      }
      return start;
    }
    throw new SqlExpressionException(
        "Cannot determine CAST operand ending at position " + exprEnd + ": " + sql);
  }

  private static boolean isKeywordEnd(String sql, int start, int endExclusive) {
    String word = sql.substring(start, endExclusive);
    return word.equalsIgnoreCase("END");
  }

  private static int findCaseStart(String sql, int endKeywordStart) throws SqlExpressionException {
    int depth = 1;
    int i = endKeywordStart - 1;
    while (i >= 0) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"') {
        i = findQuoteStart(sql, i) - 1;
        continue;
      }
      if (isIdentPart(c)) {
        int start = scanIdentBack(sql, i);
        String word = sql.substring(start, i + 1);
        if (word.equalsIgnoreCase("END")) {
          depth++;
        } else if (word.equalsIgnoreCase("CASE")) {
          depth--;
          if (depth == 0) {
            return start;
          }
        }
        i = start - 1;
        continue;
      }
      i--;
    }
    throw new SqlExpressionException("CASE/END mismatch around CAST: " + sql);
  }

  private static int findMatchingOpenParen(String sql, int closeIndex)
      throws SqlExpressionException {
    int depth = 1;
    int i = closeIndex - 1;
    while (i >= 0) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"') {
        i = findQuoteStart(sql, i) - 1;
        continue;
      }
      if (c == ')') {
        depth++;
      } else if (c == '(') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
      i--;
    }
    throw new SqlExpressionException("Unbalanced parentheses around CAST: " + sql);
  }

  private static int findQuoteStart(String sql, int endQuoteIndex) throws SqlExpressionException {
    char quote = sql.charAt(endQuoteIndex);
    int i = endQuoteIndex - 1;
    while (i >= 0) {
      if (sql.charAt(i) == quote) {
        if (i > 0 && sql.charAt(i - 1) == quote) {
          i -= 2;
          continue;
        }
        return i;
      }
      i--;
    }
    throw new SqlExpressionException("Unterminated string around CAST: " + sql);
  }

  private static int scanIdentBack(String sql, int end) {
    int i = end;
    while (i >= 0 && (isIdentPart(sql.charAt(i)) || sql.charAt(i) == '.')) {
      i--;
    }
    return i + 1;
  }

  private static TypeSpan readType(String sql, int start) {
    if (start >= sql.length() || !isIdentStart(sql.charAt(start))) {
      return null;
    }
    int i = start + 1;
    while (i < sql.length() && isIdentPart(sql.charAt(i))) {
      i++;
    }
    int afterName = i;
    i = skipWhitespace(sql, i);
    if (i < sql.length() && sql.charAt(i) == '(') {
      int close = sql.indexOf(')', i);
      if (close < 0) {
        return null;
      }
      return new TypeSpan(start, close + 1);
    }
    return new TypeSpan(start, afterName);
  }

  private static int skipQuoted(String sql, int start) {
    char quote = sql.charAt(start);
    int i = start + 1;
    while (i < sql.length()) {
      if (sql.charAt(i) == quote) {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return sql.length();
  }

  private static int skipWhitespace(String sql, int i) {
    while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int skipWhitespaceBack(String sql, int exclusiveEnd) {
    int i = exclusiveEnd - 1;
    while (i >= 0 && Character.isWhitespace(sql.charAt(i))) {
      i--;
    }
    return i;
  }

  private static boolean isIdentStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private static boolean isIdentPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private record TypeSpan(int start, int end) {}
}
