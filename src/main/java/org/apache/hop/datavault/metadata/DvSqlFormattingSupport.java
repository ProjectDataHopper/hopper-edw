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
import org.apache.hop.core.util.Utils;

/** Best-effort pretty-printer for generated pipeline SQL (especially CTE-heavy queries). */
public final class DvSqlFormattingSupport {

  private static final int INDENT_SPACES = 2;

  /** Soft wrap width for SELECT lists with many projected columns. */
  static final int SELECT_LIST_WRAP_WIDTH = 100;

  /**
   * Indent for SELECT-list continuation lines (matches length of {@code "SELECT "} so columns align
   * under the first field).
   */
  static final int SELECT_LIST_CONTINUATION_SPACES = 7;

  private static final String[] CLAUSE_KEYWORDS =
      new String[] {
        "UNION ALL",
        "UNION",
        "INTERSECT",
        "EXCEPT",
        "CROSS JOIN",
        // Prefer OUTER forms before shorter JOIN keywords (source-query SQL uses these).
        "LEFT OUTER JOIN",
        "RIGHT OUTER JOIN",
        "FULL OUTER JOIN",
        "INNER JOIN",
        "LEFT JOIN",
        "RIGHT JOIN",
        "FULL JOIN",
        "GROUP BY",
        "ORDER BY",
        "SELECT",
        "FROM",
        "WHERE",
        "HAVING",
        "JOIN"
      };

  private DvSqlFormattingSupport() {}

  /**
   * Formats SQL for readability in Hop transforms. On failure, returns the original SQL unchanged.
   */
  public static String formatForDisplay(String sql) {
    if (Utils.isEmpty(sql)) {
      return sql;
    }
    try {
      String normalized = normalizeWhitespace(sql);
      if (normalized.regionMatches(true, 0, "WITH ", 0, 5)) {
        return formatWithCtes(normalized);
      }
      return formatQueryBlock(normalized, 0);
    } catch (RuntimeException e) {
      return sql;
    }
  }

  private static String formatWithCtes(String sql) {
    String remainder = sql.substring(5).trim();
    StringBuilder out = new StringBuilder("WITH");
    int position = 0;
    int cteCount = 0;

    while (position < remainder.length()) {
      position = skipWhitespaceAndCommas(remainder, position);
      if (position >= remainder.length()) {
        break;
      }

      int asOpen = indexOfAsOpenParen(remainder, position);
      if (asOpen < 0) {
        appendClauseBreaks(out, remainder.substring(position).trim(), 1);
        return out.toString().trim();
      }

      String cteName = remainder.substring(position, asOpen).trim();
      int openParen = asOpen + 4;
      int closeParen = findMatchingParen(remainder, openParen);
      if (closeParen < 0) {
        return sql;
      }

      String cteBody = remainder.substring(openParen + 1, closeParen).trim();
      if (cteCount > 0) {
        out.append(',');
      }
      out.append('\n').append(indent(1)).append(cteName).append(" AS (");
      out.append('\n')
          .append(formatQueryBlock(cteBody, 2))
          .append('\n')
          .append(indent(1))
          .append(')');

      position = closeParen + 1;
      cteCount++;

      position = skipWhitespaceAndCommas(remainder, position);
      if (position < remainder.length()
          && startsWithKeyword(remainder, position, "SELECT", "INSERT", "UPDATE", "DELETE")) {
        out.append('\n');
        appendClauseBreaks(out, remainder.substring(position).trim(), 1);
        return out.toString().trim();
      }
    }

    return out.toString().trim();
  }

  private static String formatQueryBlock(String sql, int indentLevel) {
    String trimmed = sql.trim();
    if (trimmed.isEmpty()) {
      return indent(indentLevel);
    }

    if (trimmed.charAt(0) == '(') {
      int closeParen = findMatchingParen(trimmed, 0);
      if (closeParen == trimmed.length() - 1) {
        String inner = trimmed.substring(1, closeParen).trim();
        StringBuilder out = new StringBuilder();
        out.append(indent(indentLevel)).append('(');
        out.append('\n').append(formatQueryBlock(inner, indentLevel + 1));
        out.append('\n').append(indent(indentLevel)).append(')');
        return out.toString();
      }
    }

    StringBuilder out = new StringBuilder();
    appendClauseBreaks(out, trimmed, indentLevel);
    return out.toString();
  }

  private static void appendClauseBreaks(StringBuilder out, String sql, int indentLevel) {
    List<String> clauses = splitIntoClauses(sql);
    for (int index = 0; index < clauses.size(); index++) {
      if (index > 0) {
        out.append('\n');
      }
      String clause = clauses.get(index);
      if (isSelectClause(clause)) {
        out.append(formatSelectClause(clause, indentLevel));
      } else {
        out.append(indent(indentLevel)).append(formatInlineSubqueries(clause, indentLevel));
      }
    }
  }

  private static boolean isSelectClause(String clause) {
    return clause != null
        && (clause.regionMatches(true, 0, "SELECT ", 0, 7)
            || clause.regionMatches(true, 0, "SELECT\t", 0, 7)
            || "SELECT".equalsIgnoreCase(clause.trim()));
  }

  /**
   * Formats a SELECT list for wide projections: pack fields on a line until about {@link
   * #SELECT_LIST_WRAP_WIDTH} characters, then wrap the next field onto a new line with {@link
   * #SELECT_LIST_CONTINUATION_SPACES} leading spaces (aligned under the first field after {@code
   * SELECT }).
   */
  private static String formatSelectClause(String clause, int indentLevel) {
    String trimmed = clause.trim();
    String rest = trimmed.substring("SELECT".length()).trim();
    boolean distinct = false;
    if (rest.regionMatches(true, 0, "DISTINCT ", 0, 9)
        || rest.regionMatches(true, 0, "DISTINCT\t", 0, 9)) {
      distinct = true;
      rest = rest.substring("DISTINCT".length()).trim();
    }

    List<String> selectItems = splitSelectItems(rest);
    if (selectItems.isEmpty()) {
      return indent(indentLevel) + formatInlineSubqueries(trimmed, indentLevel);
    }

    String baseIndent = indent(indentLevel);
    String continuationIndent = baseIndent + " ".repeat(SELECT_LIST_CONTINUATION_SPACES);
    return packSelectItems(baseIndent, continuationIndent, distinct, selectItems, indentLevel);
  }

  private static String packSelectItems(
      String baseIndent,
      String continuationIndent,
      boolean distinct,
      List<String> selectItems,
      int indentLevel) {
    StringBuilder out = new StringBuilder();
    out.append(baseIndent).append("SELECT");
    if (distinct) {
      out.append(" DISTINCT");
    }
    out.append(' ');

    int lineStart = out.length();
    for (int i = 0; i < selectItems.size(); i++) {
      String item = formatInlineSubqueries(selectItems.get(i), indentLevel);
      boolean firstOnLine = out.length() == lineStart;
      String piece = firstOnLine ? item : ", " + item;

      if (!firstOnLine && (out.length() - lineStart) + piece.length() > SELECT_LIST_WRAP_WIDTH) {
        // End previous line with comma; next field starts on the continuation line.
        out.append(',').append('\n').append(continuationIndent);
        lineStart = out.length();
        piece = item;
      }
      out.append(piece);
    }
    return out.toString();
  }

  private static List<String> splitSelectItems(String selectList) {
    List<String> items = new ArrayList<>();
    if (Utils.isEmpty(selectList)) {
      return items;
    }
    QuoteState quotes = new QuoteState();
    int depth = 0;
    int start = 0;
    for (int index = 0; index < selectList.length(); index++) {
      char current = selectList.charAt(index);
      quotes.advance(selectList, index, current);
      if (!quotes.inLiteral()) {
        if (current == '(') {
          depth++;
        } else if (current == ')') {
          depth--;
        } else if (current == ',' && depth == 0) {
          String item = selectList.substring(start, index).trim();
          if (!item.isEmpty()) {
            items.add(item);
          }
          start = index + 1;
        }
      }
    }
    String tail = selectList.substring(start).trim();
    if (!tail.isEmpty()) {
      items.add(tail);
    }
    return items;
  }

  private static List<String> splitIntoClauses(String sql) {
    List<String> clauses = new ArrayList<>();
    QuoteState quotes = new QuoteState();
    int depth = 0;
    int clauseStart = 0;

    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      quotes.advance(sql, index, current);

      if (!quotes.inLiteral()) {
        if (current == '(') {
          depth++;
        } else if (current == ')') {
          depth--;
        } else if (depth == 0) {
          String keyword = matchClauseKeyword(sql, index);
          if (keyword != null
              && isKeywordBoundary(sql, index, keyword.length())
              && !(clauseStart == 0 && "SELECT".equalsIgnoreCase(keyword))) {
            String previous = sql.substring(clauseStart, index).trim();
            if (!previous.isEmpty()) {
              clauses.add(previous);
            }
            clauseStart = index;
            index = clauseStart + keyword.length() - 1;
          }
        }
      }
    }

    String tail = sql.substring(clauseStart).trim();
    if (!tail.isEmpty()) {
      clauses.add(tail);
    }
    return clauses;
  }

  private static String formatInlineSubqueries(String text, int indentLevel) {
    if (text.isEmpty()) {
      return text;
    }

    StringBuilder out = new StringBuilder();
    int index = 0;
    while (index < text.length()) {
      int open = findOpenSelectParen(text, index);
      if (open < 0) {
        out.append(text.substring(index));
        break;
      }

      out.append(text, index, open);
      int close = findMatchingParen(text, open);
      if (close < 0) {
        out.append(text.substring(open));
        break;
      }

      String inner = text.substring(open + 1, close).trim();
      out.append('(');
      out.append('\n').append(formatQueryBlock(inner, indentLevel + 1));
      out.append('\n').append(indent(indentLevel)).append(')');
      index = close + 1;
    }
    return out.toString();
  }

  private static int findOpenSelectParen(String sql, int fromIndex) {
    QuoteState quotes = new QuoteState();
    for (int index = fromIndex; index < sql.length(); index++) {
      char current = sql.charAt(index);
      quotes.advance(sql, index, current);
      if (!quotes.inLiteral() && current == '(') {
        int next = skipWhitespace(sql, index + 1);
        if (startsWithKeyword(sql, next, "SELECT")) {
          return index;
        }
      }
    }
    return -1;
  }

  private static int skipWhitespace(String sql, int fromIndex) {
    int index = fromIndex;
    while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
      index++;
    }
    return index;
  }

  private static String normalizeWhitespace(String sql) {
    StringBuilder out = new StringBuilder(sql.length());
    QuoteState quotes = new QuoteState();
    boolean pendingSpace = false;

    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      quotes.advance(sql, index, current);

      if (quotes.inLiteral()) {
        if (pendingSpace) {
          out.append(' ');
          pendingSpace = false;
        }
        out.append(current);
        continue;
      }

      if (Character.isWhitespace(current)) {
        pendingSpace = true;
      } else {
        if (pendingSpace && !out.isEmpty()) {
          out.append(' ');
        }
        pendingSpace = false;
        out.append(current);
      }
    }

    return out.toString().trim();
  }

  private static int indexOfAsOpenParen(String sql, int fromIndex) {
    QuoteState quotes = new QuoteState();
    int depth = 0;

    for (int index = fromIndex; index < sql.length(); index++) {
      char current = sql.charAt(index);
      quotes.advance(sql, index, current);

      if (!quotes.inLiteral()) {
        if (current == '(') {
          depth++;
        } else if (current == ')') {
          depth--;
        } else if (depth == 0 && sql.regionMatches(true, index, " AS (", 0, 5)) {
          return index;
        }
      }
    }
    return -1;
  }

  private static int findMatchingParen(String sql, int openIndex) {
    if (openIndex < 0 || openIndex >= sql.length() || sql.charAt(openIndex) != '(') {
      return -1;
    }

    QuoteState quotes = new QuoteState();
    int depth = 0;

    for (int index = openIndex; index < sql.length(); index++) {
      char current = sql.charAt(index);
      quotes.advance(sql, index, current);

      if (!quotes.inLiteral()) {
        if (current == '(') {
          depth++;
        } else if (current == ')') {
          depth--;
          if (depth == 0) {
            return index;
          }
        }
      }
    }
    return -1;
  }

  private static int skipWhitespaceAndCommas(String sql, int fromIndex) {
    int index = fromIndex;
    while (index < sql.length()) {
      char current = sql.charAt(index);
      if (Character.isWhitespace(current) || current == ',') {
        index++;
      } else {
        break;
      }
    }
    return index;
  }

  private static boolean startsWithKeyword(String sql, int index, String... keywords) {
    for (String keyword : keywords) {
      if (sql.regionMatches(true, index, keyword, 0, keyword.length())
          && isKeywordBoundary(sql, index, keyword.length())) {
        return true;
      }
    }
    return false;
  }

  private static String matchClauseKeyword(String sql, int index) {
    for (String keyword : CLAUSE_KEYWORDS) {
      if (sql.regionMatches(true, index, keyword, 0, keyword.length())
          && isKeywordBoundary(sql, index, keyword.length())) {
        return keyword;
      }
    }
    return null;
  }

  private static boolean isKeywordBoundary(String sql, int index, int keywordLength) {
    boolean leading =
        index == 0
            || !Character.isLetterOrDigit(sql.charAt(index - 1)) && sql.charAt(index - 1) != '_';
    int end = index + keywordLength;
    boolean trailing =
        end >= sql.length()
            || !Character.isLetterOrDigit(sql.charAt(end)) && sql.charAt(end) != '_';
    return leading && trailing;
  }

  private static String indent(int level) {
    return " ".repeat(Math.max(0, level * INDENT_SPACES));
  }

  private static final class QuoteState {
    private boolean inSingleQuote;
    private boolean inDoubleQuote;

    private void advance(String sql, int index, char current) {
      if (inDoubleQuote) {
        if (current == '"' && !isEscaped(sql, index)) {
          inDoubleQuote = false;
        }
        return;
      }
      if (inSingleQuote) {
        if (current == '\'' && !isEscaped(sql, index)) {
          inSingleQuote = false;
        }
        return;
      }
      if (current == '\'') {
        inSingleQuote = true;
      } else if (current == '"') {
        inDoubleQuote = true;
      }
    }

    private boolean inLiteral() {
      return inSingleQuote || inDoubleQuote;
    }
  }

  private static boolean isEscaped(String sql, int index) {
    int backslashes = 0;
    for (int i = index - 1; i >= 0 && sql.charAt(i) == '\\'; i--) {
      backslashes++;
    }
    return backslashes % 2 == 1;
  }
}
