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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.util.Utils;

/** Parses {@code key=value;key=value} generator arguments. Values may contain {@code |}. */
final class GeneratorArguments {

  private GeneratorArguments() {}

  static Map<String, String> parse(String raw) {
    if (Utils.isEmpty(raw)) {
      return Collections.emptyMap();
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (String part : splitArgs(raw)) {
      int eq = part.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      values.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
    }
    return values;
  }

  static String get(Map<String, String> args, String key, String defaultValue) {
    if (args == null) {
      return defaultValue;
    }
    String value = args.get(key);
    return value == null || value.isEmpty() ? defaultValue : value;
  }

  private static String[] splitArgs(String raw) {
    java.util.List<String> parts = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\'') {
        quoted = !quoted;
        current.append(c);
      } else if (c == ';' && !quoted) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    parts.add(current.toString());
    return parts.toArray(String[]::new);
  }
}
