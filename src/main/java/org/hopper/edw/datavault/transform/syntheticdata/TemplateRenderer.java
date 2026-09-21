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
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;

/** Renders {@code ${field}} and {@code ${pad(expr,width)}} / {@code ${label(field)}} templates. */
final class TemplateRenderer {

  private TemplateRenderer() {}

  static String render(String pattern, IVariables variables, Map<String, Object> fields)
      throws HopException {
    if (pattern == null || pattern.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < pattern.length()) {
      int start = pattern.indexOf("${", i);
      if (start < 0) {
        out.append(pattern.substring(i));
        break;
      }
      out.append(pattern, i, start);
      int end = pattern.indexOf('}', start + 2);
      if (end < 0) {
        throw new HopException("Unclosed ${ in template '" + pattern + "'");
      }
      String inner = pattern.substring(start + 2, end).trim();
      out.append(renderInner(inner, variables, fields));
      i = end + 1;
    }
    return out.toString();
  }

  private static String renderInner(String inner, IVariables variables, Map<String, Object> fields)
      throws HopException {
    if (inner.startsWith("pad(") && inner.endsWith(")")) {
      String args = inner.substring(4, inner.length() - 1);
      int comma = args.lastIndexOf(',');
      if (comma < 0) {
        throw new HopException("pad() needs a width in '" + inner + "'");
      }
      BigDecimal value =
          NumericExpression.evaluateWithDates(args.substring(0, comma).trim(), variables, fields);
      int width = Integer.parseInt(args.substring(comma + 1).trim());
      String digits = value.setScale(0, RoundingMode.DOWN).toPlainString();
      if (digits.startsWith("-")) {
        return "-" + pad(digits.substring(1), Math.max(0, width));
      }
      return pad(digits, width);
    }
    if (inner.startsWith("label(") && inner.endsWith(")")) {
      String name = inner.substring(6, inner.length() - 1).trim();
      return label(stringify(lookupToken(name, fields)));
    }
    if (fields.containsKey(inner)) {
      return stringify(fields.get(inner));
    }
    String resolved = variables == null ? inner : variables.resolve(inner);
    if (fields.containsKey(resolved)) {
      return stringify(fields.get(resolved));
    }
    if (resolved.equals(inner) && !looksNumeric(resolved)) {
      return stringify(lookupToken(inner, fields));
    }
    BigDecimal value = NumericExpression.evaluateWithDates(inner, variables, fields);
    return plain(value);
  }

  private static Object lookupToken(String token, Map<String, Object> fields) {
    String name = token;
    if (name.startsWith("${") && name.endsWith("}")) {
      name = name.substring(2, name.length() - 1);
    }
    if (fields.containsKey(name)) {
      return fields.get(name);
    }
    return token;
  }

  private static boolean looksNumeric(String text) {
    if (text.isEmpty()) {
      return false;
    }
    char c = text.charAt(0);
    return Character.isDigit(c) || c == '-' || c == '(' || c == '$' || text.contains("(");
  }

  static String stringify(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof BigDecimal decimal) {
      return plain(decimal);
    }
    if (value instanceof Double || value instanceof Float) {
      return plain(BigDecimal.valueOf(((Number) value).doubleValue()));
    }
    return String.valueOf(value);
  }

  private static String plain(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return stripped.toPlainString();
  }

  private static String pad(String digits, int width) {
    if (digits.length() >= width) {
      return digits;
    }
    return "0".repeat(width - digits.length()) + digits;
  }

  static String label(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    String[] words = raw.replace('_', ' ').trim().toLowerCase(Locale.ROOT).split("\\s+");
    StringBuilder out = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        out.append(word.substring(1));
      }
    }
    return out.toString();
  }
}
