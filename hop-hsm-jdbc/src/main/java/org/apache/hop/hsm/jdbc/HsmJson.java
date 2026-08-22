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
package org.apache.hop.hsm.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON subset parser/encoder for the hop-hsm wire protocol. Supports objects, arrays, strings,
 * numbers, booleans, and null — enough for query results without third-party libraries.
 */
public final class HsmJson {

  private HsmJson() {}

  public static Object parse(String json) {
    return new Parser(json).parseValue();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asObject(Object o) {
    return o instanceof Map ? (Map<String, Object>) o : null;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asArray(Object o) {
    return o instanceof List ? (List<Object>) o : null;
  }

  public static String str(Map<String, Object> map, String key) {
    if (map == null) {
      return null;
    }
    Object v = map.get(key);
    return v == null ? null : String.valueOf(v);
  }

  public static boolean bool(Map<String, Object> map, String key, boolean def) {
    if (map == null || !map.containsKey(key)) {
      return def;
    }
    Object v = map.get(key);
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  public static int integer(Map<String, Object> map, String key, int def) {
    if (map == null || !map.containsKey(key) || map.get(key) == null) {
      return def;
    }
    Object v = map.get(key);
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException e) {
      return def;
    }
  }

  public static String encodeForm(Map<String, String> fields) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : fields.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('&');
      }
      sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
    }
    return sb.toString();
  }

  private static String urlEncode(String s) {
    try {
      return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return s;
    }
  }

  private static final class Parser {
    private final String s;
    private int i;

    Parser(String s) {
      this.s = s != null ? s.trim() : "";
    }

    Object parseValue() {
      skipWs();
      if (i >= s.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON");
      }
      char c = s.charAt(i);
      if (c == '{') {
        return parseObject();
      }
      if (c == '[') {
        return parseArray();
      }
      if (c == '"') {
        return parseString();
      }
      if (c == 't' || c == 'f') {
        return parseBoolean();
      }
      if (c == 'n') {
        return parseNull();
      }
      return parseNumber();
    }

    private Map<String, Object> parseObject() {
      expect('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWs();
      if (peek('}')) {
        i++;
        return map;
      }
      while (true) {
        skipWs();
        String key = parseString();
        skipWs();
        expect(':');
        Object value = parseValue();
        map.put(key, value);
        skipWs();
        if (peek('}')) {
          i++;
          break;
        }
        expect(',');
      }
      return map;
    }

    private List<Object> parseArray() {
      expect('[');
      List<Object> list = new ArrayList<>();
      skipWs();
      if (peek(']')) {
        i++;
        return list;
      }
      while (true) {
        list.add(parseValue());
        skipWs();
        if (peek(']')) {
          i++;
          break;
        }
        expect(',');
      }
      return list;
    }

    private String parseString() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') {
          return sb.toString();
        }
        if (c == '\\') {
          if (i >= s.length()) {
            throw new IllegalArgumentException("Bad escape");
          }
          char e = s.charAt(i++);
          switch (e) {
            case '"', '\\', '/' -> sb.append(e);
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
              if (i + 4 > s.length()) {
                throw new IllegalArgumentException("Bad unicode escape");
              }
              sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
              i += 4;
            }
            default -> throw new IllegalArgumentException("Bad escape \\" + e);
          }
        } else {
          sb.append(c);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    private Object parseNumber() {
      int start = i;
      if (peek('-')) {
        i++;
      }
      while (i < s.length()
          && (Character.isDigit(s.charAt(i)) || ".eE+-".indexOf(s.charAt(i)) >= 0)) {
        i++;
      }
      String num = s.substring(start, i);
      if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
        return Double.parseDouble(num);
      }
      try {
        return Long.parseLong(num);
      } catch (NumberFormatException e) {
        return Double.parseDouble(num);
      }
    }

    private Boolean parseBoolean() {
      if (s.startsWith("true", i)) {
        i += 4;
        return Boolean.TRUE;
      }
      if (s.startsWith("false", i)) {
        i += 5;
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Expected boolean at " + i);
    }

    private Object parseNull() {
      if (s.startsWith("null", i)) {
        i += 4;
        return null;
      }
      throw new IllegalArgumentException("Expected null at " + i);
    }

    private void skipWs() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
        i++;
      }
    }

    private boolean peek(char c) {
      return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
      skipWs();
      if (i >= s.length() || s.charAt(i) != c) {
        throw new IllegalArgumentException("Expected '" + c + "' at " + i);
      }
      i++;
    }
  }
}
