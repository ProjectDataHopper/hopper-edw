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

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/** Parses the thin-client {@code params} and {@code paramTypes} JSON arrays. */
public final class DimensionalQueryParameters {

  private DimensionalQueryParameters() {}

  public static List<DimensionalQueryParameter> parse(String paramsJson, String typesJson)
      throws HopException {
    if (Utils.isEmpty(paramsJson)) {
      return List.of();
    }
    List<JsonValue> values = parseArray(paramsJson);
    List<JsonValue> types = Utils.isEmpty(typesJson) ? List.of() : parseArray(typesJson);
    if (!types.isEmpty() && types.size() != values.size()) {
      throw new HopException("paramTypes must have one entry for each bound parameter");
    }
    List<DimensionalQueryParameter> parameters = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      JsonValue value = values.get(i);
      int jdbcType = Types.VARCHAR;
      if (i < types.size() && types.get(i).text != null) {
        try {
          jdbcType = Integer.parseInt(types.get(i).text.trim());
        } catch (NumberFormatException e) {
          throw new HopException("paramTypes entry " + (i + 1) + " is not a JDBC type code");
        }
      }
      parameters.add(new DimensionalQueryParameter(jdbcType, value.text, value.sqlNull));
    }
    return parameters;
  }

  private static List<JsonValue> parseArray(String json) throws HopException {
    String text = json.trim();
    if (!text.startsWith("[") || !text.endsWith("]")) {
      throw new HopException("Expected a JSON array of query parameters");
    }
    List<JsonValue> values = new ArrayList<>();
    int i = 1;
    while (i < text.length() - 1) {
      while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      if (i >= text.length() - 1) {
        break;
      }
      if (text.charAt(i) == ',') {
        i++;
        continue;
      }
      if (text.startsWith("null", i)) {
        values.add(new JsonValue(null, true));
        i += 4;
        continue;
      }
      if (text.charAt(i) == '"') {
        StringBuilder value = new StringBuilder();
        i++;
        while (i < text.length()) {
          char c = text.charAt(i);
          if (c == '\\' && i + 1 < text.length()) {
            char next = text.charAt(i + 1);
            value.append(
                switch (next) {
                  case 'n' -> '\n';
                  case 'r' -> '\r';
                  case 't' -> '\t';
                  case '"' -> '"';
                  case '\\' -> '\\';
                  default -> next;
                });
            i += 2;
            continue;
          }
          if (c == '"') {
            i++;
            break;
          }
          value.append(c);
          i++;
        }
        values.add(new JsonValue(value.toString(), false));
        continue;
      }
      int start = i;
      while (i < text.length() && text.charAt(i) != ',' && text.charAt(i) != ']') {
        i++;
      }
      values.add(new JsonValue(text.substring(start, i).trim(), false));
    }
    return values;
  }

  private record JsonValue(String text, boolean sqlNull) {}
}
