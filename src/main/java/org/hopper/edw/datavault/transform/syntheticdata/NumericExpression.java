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
package org.hopper.edw.datavault.transform.syntheticdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Small numeric expression language for synthetic row counts and field formulas.
 *
 * <p>Hop variables are resolved first. {@code /} is decimal division; {@code div(a,b)} is integer
 * division toward zero. {@code ifEq(left, right, whenEqual, whenNot)} compares stringified field
 * values or single-quoted literals. Remaining {@code ${field}} tokens are row fields, not
 * variables.
 */
public final class NumericExpression {

  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter HOP =
      DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");
  private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private NumericExpression() {}

  public static BigDecimal evaluate(String expression, IVariables variables, Map<String, Object> fields)
      throws HopException {
    if (Utils.isEmpty(expression)) {
      throw new HopException("Empty numeric expression");
    }
    String resolved = variables == null ? expression : variables.resolve(expression);
    Parser parser = new Parser(resolved, fields == null ? Map.of() : fields);
    BigDecimal value = parser.parseExpression();
    parser.skip();
    if (!parser.eof()) {
      throw new HopException(
          "Trailing input in expression '" + expression + "' near '" + parser.rest() + "'");
    }
    return value;
  }

  public static long evaluateLong(String expression, IVariables variables, Map<String, Object> fields)
      throws HopException {
    return evaluate(expression, variables, fields).setScale(0, RoundingMode.DOWN).longValueExact();
  }

  public static double evaluateDouble(
      String expression, IVariables variables, Map<String, Object> fields) throws HopException {
    return evaluate(expression, variables, fields).doubleValue();
  }

  static LocalDate toLocalDate(Object value) throws HopException {
    if (value == null) {
      throw new HopException("Missing date value");
    }
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof Date date) {
      return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    String text = String.valueOf(value).trim();
    if (text.length() >= 10 && text.charAt(4) == '-') {
      text = text.substring(0, 10);
      try {
        return LocalDate.parse(text, ISO);
      } catch (DateTimeParseException e) {
        throw new HopException("Cannot parse date '" + value + "'");
      }
    }
    try {
      if (text.length() > 10) {
        return LocalDate.parse(text, HOP);
      }
      return LocalDate.parse(text, SLASH);
    } catch (DateTimeParseException e) {
      throw new HopException("Cannot parse date '" + value + "'");
    }
  }

  private static final class Parser {
    private final String text;
    private final Map<String, Object> fields;
    private int index;

    private Parser(String text, Map<String, Object> fields) {
      this.text = text;
      this.fields = fields;
    }

    private BigDecimal parseExpression() throws HopException {
      return parseAdd();
    }

    private BigDecimal parseAdd() throws HopException {
      BigDecimal value = parseMul();
      while (true) {
        skip();
        if (match('+')) {
          value = value.add(parseMul());
        } else if (match('-')) {
          value = value.subtract(parseMul());
        } else {
          return value;
        }
      }
    }

    private BigDecimal parseMul() throws HopException {
      BigDecimal value = parseUnary();
      while (true) {
        skip();
        if (match('*')) {
          value = value.multiply(parseUnary());
        } else if (match('/')) {
          BigDecimal divisor = parseUnary();
          if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new HopException("Division by zero in '" + text + "'");
          }
          value = value.divide(divisor, 8, RoundingMode.HALF_UP);
        } else {
          return value;
        }
      }
    }

    private BigDecimal parseUnary() throws HopException {
      skip();
      if (match('+')) {
        return parseUnary();
      }
      if (match('-')) {
        return parseUnary().negate();
      }
      return parsePrimary();
    }

    private BigDecimal parsePrimary() throws HopException {
      skip();
      if (match('(')) {
        BigDecimal value = parseExpression();
        expect(')');
        return value;
      }
      if (isDigit(peek()) || (peek() == '.' && isDigit(peek(1)))) {
        return readNumber();
      }
      String name = readIdent();
      if (name.isEmpty()) {
        throw new HopException("Expected value in '" + text + "' near '" + rest() + "'");
      }
      skip();
      if (match('(')) {
        return call(name);
      }
      return asNumber(lookup(name), name);
    }

    private BigDecimal call(String name) throws HopException {
      if ("ifEq".equals(name)) {
        String left = readCompareOperand();
        expect(',');
        String right = readCompareOperand();
        expect(',');
        BigDecimal whenEqual = parseExpression();
        expect(',');
        BigDecimal whenNot = parseExpression();
        expect(')');
        return left.equals(right) ? whenEqual : whenNot;
      }
      if ("dateDiff".equals(name)) {
        BigDecimal left = parseExpression();
        // dateDiff arguments are fields, not numbers. Re-parse is wrong.
        throw new HopException("Internal dateDiff dispatch");
      }
      if ("max".equals(name) || "min".equals(name)) {
        BigDecimal left = parseExpression();
        expect(',');
        BigDecimal right = parseExpression();
        expect(')');
        int cmp = left.compareTo(right);
        if ("max".equals(name)) {
          return cmp >= 0 ? left : right;
        }
        return cmp <= 0 ? left : right;
      }
      if ("div".equals(name) || "mod".equals(name)) {
        BigDecimal left = parseExpression();
        expect(',');
        BigDecimal right = parseExpression();
        expect(')');
        long a = left.setScale(0, RoundingMode.DOWN).longValue();
        long b = right.setScale(0, RoundingMode.DOWN).longValue();
        if (b == 0) {
          throw new HopException("Division by zero in '" + text + "'");
        }
        long result = "div".equals(name) ? a / b : a % b;
        return BigDecimal.valueOf(result);
      }
      if ("round".equals(name)) {
        BigDecimal value = parseExpression();
        expect(',');
        int scale = parseExpression().intValue();
        expect(')');
        return value.setScale(scale, RoundingMode.HALF_UP);
      }
      throw new HopException("Unknown function '" + name + "' in '" + text + "'");
    }

    private String readCompareOperand() throws HopException {
      skip();
      if (peek() == '\'') {
        return readQuoted();
      }
      if (peek() == '$') {
        String name = readVariableToken();
        Object value = fields.get(name);
        return value == null ? "" : String.valueOf(value);
      }
      String ident = readIdent();
      if (ident.isEmpty()) {
        throw new HopException("Expected string in '" + text + "' near '" + rest() + "'");
      }
      Object value = fields.get(ident);
      return value == null ? ident : String.valueOf(value);
    }

    private String readQuoted() throws HopException {
      expect('\'');
      StringBuilder value = new StringBuilder();
      while (!eof() && peek() != '\'') {
        value.append(text.charAt(index++));
      }
      expect('\'');
      return value.toString();
    }

    private BigDecimal readNumber() {
      int start = index;
      while (isDigit(peek())) {
        index++;
      }
      if (peek() == '.' && isDigit(peek(1))) {
        index++;
        while (isDigit(peek())) {
          index++;
        }
      }
      return new BigDecimal(text.substring(start, index));
    }

    private String readIdent() {
      if (peek() == '$') {
        try {
          return readVariableToken();
        } catch (HopException e) {
          return "";
        }
      }
      int start = index;
      if (!isIdentStart(peek())) {
        return "";
      }
      index++;
      while (isIdentPart(peek())) {
        index++;
      }
      return text.substring(start, index);
    }

    private String readVariableToken() throws HopException {
      expect('$');
      expect('{');
      int start = index;
      while (!eof() && peek() != '}') {
        index++;
      }
      String name = text.substring(start, index);
      expect('}');
      return name;
    }

    private Object lookup(String name) throws HopException {
      if (!fields.containsKey(name)) {
        throw new HopException("Unknown field '" + name + "' in '" + text + "'");
      }
      return fields.get(name);
    }

    private BigDecimal asNumber(Object value, String name) throws HopException {
      if (value instanceof BigDecimal decimal) {
        return decimal;
      }
      if (value instanceof Number number) {
        if (value instanceof Double || value instanceof Float) {
          return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
      }
      if (value == null) {
        throw new HopException("Field '" + name + "' is null in '" + text + "'");
      }
      try {
        return new BigDecimal(String.valueOf(value).trim());
      } catch (NumberFormatException e) {
        throw new HopException("Field '" + name + "' is not numeric in '" + text + "'");
      }
    }

    private void skip() {
      while (!eof() && Character.isWhitespace(text.charAt(index))) {
        index++;
      }
    }

    private boolean match(char c) {
      skip();
      if (peek() == c) {
        index++;
        return true;
      }
      return false;
    }

    private void expect(char c) throws HopException {
      skip();
      if (peek() != c) {
        throw new HopException("Expected '" + c + "' in '" + text + "' near '" + rest() + "'");
      }
      index++;
    }

    private char peek() {
      return peek(0);
    }

    private char peek(int offset) {
      int at = index + offset;
      return at >= 0 && at < text.length() ? text.charAt(at) : '\0';
    }

    private static boolean isDigit(char c) {
      return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
      return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private boolean eof() {
      return index >= text.length();
    }

    private String rest() {
      skip();
      return index >= text.length() ? "" : text.substring(index);
    }
  }

  /**
   * {@code dateDiff} cannot go through the numeric primary parser because its arguments are dates.
   * This entry point rewrites {@code dateDiff(a,b)} to an integer day count before evaluating.
   */
  public static BigDecimal evaluateWithDates(
      String expression, IVariables variables, Map<String, Object> fields) throws HopException {
    String resolved = variables == null ? expression : variables.resolve(expression);
    String rewritten = rewriteDateDiffs(resolved, fields == null ? Map.of() : fields);
    return evaluate(rewritten, null, fields);
  }

  private static String rewriteDateDiffs(String expression, Map<String, Object> fields)
      throws HopException {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < expression.length()) {
      int at = expression.indexOf("dateDiff(", i);
      if (at < 0) {
        out.append(expression.substring(i));
        break;
      }
      out.append(expression, i, at);
      int open = at + "dateDiff(".length();
      int comma = findArgComma(expression, open);
      int close = findClose(expression, open);
      String left = expression.substring(open, comma).trim();
      String right = expression.substring(comma + 1, close).trim();
      long days =
          ChronoUnit.DAYS.between(resolveDate(right, fields), resolveDate(left, fields));
      out.append(days);
      i = close + 1;
    }
    return out.toString();
  }

  private static LocalDate resolveDate(String token, Map<String, Object> fields) throws HopException {
    String name = token;
    if (name.startsWith("${") && name.endsWith("}")) {
      name = name.substring(2, name.length() - 1);
    }
    if (fields.containsKey(name)) {
      return toLocalDate(fields.get(name));
    }
    return toLocalDate(token);
  }

  private static int findArgComma(String expression, int from) throws HopException {
    int depth = 0;
    for (int i = from; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        if (depth == 0) {
          throw new HopException("dateDiff is missing a comma in '" + expression + "'");
        }
        depth--;
      } else if (c == ',' && depth == 0) {
        return i;
      }
    }
    throw new HopException("Unclosed dateDiff in '" + expression + "'");
  }

  private static int findClose(String expression, int from) throws HopException {
    int depth = 0;
    for (int i = from; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        if (depth == 0) {
          return i;
        }
        depth--;
      }
    }
    throw new HopException("Unclosed dateDiff in '" + expression + "'");
  }
}
