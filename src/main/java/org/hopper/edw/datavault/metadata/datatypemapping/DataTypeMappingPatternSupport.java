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
package org.hopper.edw.datavault.metadata.datatypemapping;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.hop.core.util.Utils;

/** Glob / simple regex matching for data type mapping rules and scope. */
public final class DataTypeMappingPatternSupport {

  private DataTypeMappingPatternSupport() {}

  /**
   * Case-insensitive match. Empty pattern matches everything. Patterns with {@code *} or {@code ?}
   * are treated as globs; otherwise exact equality, unless the pattern looks like a regex (starts
   * with {@code ^} or contains {@code (} / {@code [}).
   */
  public static boolean matches(String pattern, String value) {
    if (Utils.isEmpty(pattern)) {
      return true;
    }
    String candidate = value == null ? "" : value;
    String p = pattern.trim();
    if (p.isEmpty()) {
      return true;
    }
    try {
      if (p.startsWith("^") || p.contains("(") || p.contains("[")) {
        return Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(candidate).find()
            || Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(candidate).matches();
      }
      if (p.contains("*") || p.contains("?")) {
        String regex = globToRegex(p);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(candidate).matches();
      }
      return p.equalsIgnoreCase(candidate);
    } catch (PatternSyntaxException e) {
      return p.equalsIgnoreCase(candidate);
    }
  }

  public static String globToRegex(String glob) {
    StringBuilder sb = new StringBuilder("^");
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '.', '(', ')', '[', ']', '{', '}', '+', '|', '^', '$', '\\' ->
            sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    sb.append('$');
    return sb.toString();
  }

  public static boolean matchesHopType(String matchHopType, int hopTypeId, String hopTypeName) {
    if (Utils.isEmpty(matchHopType)) {
      return true;
    }
    String expected = matchHopType.trim();
    // Numeric type id
    try {
      if (Integer.parseInt(expected) == hopTypeId) {
        return true;
      }
    } catch (NumberFormatException ignored) {
      // not numeric
    }
    String resolvedName = !Utils.isEmpty(hopTypeName) ? hopTypeName : hopTypeName(hopTypeId);
    if (Utils.isEmpty(resolvedName)) {
      return false;
    }
    if (expected.equalsIgnoreCase(resolvedName)) {
      return true;
    }
    return resolvedName.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT));
  }

  /**
   * Resolve a Hop type display name without requiring the plugin registry (works in unit tests).
   */
  public static String hopTypeName(int hopTypeId) {
    return switch (hopTypeId) {
      case 0 -> "None";
      case 1 -> "Number";
      case 2 -> "String";
      case 3 -> "Date";
      case 4 -> "Boolean";
      case 5 -> "Integer";
      case 6 -> "BigNumber";
      case 7 -> "Serializable";
      case 8 -> "Binary";
      case 9 -> "Timestamp";
      case 10 -> "Internet Address";
      default -> {
        try {
          yield org.apache.hop.core.row.value.ValueMetaFactory.getValueMetaName(hopTypeId);
        } catch (Exception e) {
          yield null;
        }
      }
    };
  }

  /** Resolve a Hop type id from a display name without requiring the plugin registry. */
  public static int hopTypeId(String hopTypeName) {
    if (Utils.isEmpty(hopTypeName)) {
      return 0;
    }
    String n = hopTypeName.trim();
    try {
      return Integer.parseInt(n);
    } catch (NumberFormatException ignored) {
      // continue
    }
    return switch (n.toLowerCase(Locale.ROOT)) {
      case "none" -> 0;
      case "number" -> 1;
      case "string" -> 2;
      case "date" -> 3;
      case "boolean" -> 4;
      case "integer" -> 5;
      case "bignumber", "big number" -> 6;
      case "serializable" -> 7;
      case "binary" -> 8;
      case "timestamp" -> 9;
      case "internet address", "inet" -> 10;
      default -> {
        try {
          int id = org.apache.hop.core.row.value.ValueMetaFactory.getIdForValueMeta(n);
          yield id > 0 ? id : 0;
        } catch (Exception e) {
          yield 0;
        }
      }
    };
  }
}
