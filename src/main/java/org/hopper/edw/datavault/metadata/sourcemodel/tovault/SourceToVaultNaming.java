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
package org.hopper.edw.datavault.metadata.sourcemodel.tovault;

import java.util.Locale;
import org.apache.hop.core.util.Utils;

/** Default raw-vault object names derived from source table names. */
public final class SourceToVaultNaming {

  private SourceToVaultNaming() {}

  public static String entityName(String tableName) {
    String normalized = normalizeToken(tableName);
    if (normalized.startsWith("hub_") && normalized.length() > 4) {
      normalized = normalized.substring(4);
    }
    normalized = stripSuffix(normalized, "_hub");
    normalized = stripSuffix(normalized, "_header");
    return normalized;
  }

  private static String stripSuffix(String value, String suffix) {
    if (value.endsWith(suffix) && value.length() > suffix.length()) {
      return value.substring(0, value.length() - suffix.length());
    }
    return value;
  }

  public static String hubName(String tableName) {
    return "hub_" + entityName(tableName);
  }

  public static String hubSatelliteName(String tableName) {
    return "sat_" + entityName(tableName);
  }

  public static String extensionSatelliteName(String tableName) {
    return "sat_" + normalizeToken(tableName);
  }

  public static String linkNameFromTable(String tableName) {
    return "lnk_" + normalizeToken(tableName);
  }

  public static String fkLinkName(String childTableName) {
    return "lnk_" + entityName(childTableName);
  }

  public static String linkSatelliteName(String tableName) {
    return "sat_lnk_" + normalizeToken(tableName);
  }

  public static String referenceName(String tableName) {
    return "ref_" + entityName(tableName);
  }

  public static String hierarchyAliasName(String tableName) {
    return hubName(tableName) + "_parent";
  }

  public static String hierarchyLinkName(String tableName) {
    return "lnk_" + entityName(tableName) + "_hierarchy";
  }

  public static String naryLinkName(String tableName) {
    return linkNameFromTable(tableName);
  }

  public static boolean looksLikeHubKernelName(String tableName) {
    String normalized = normalizeToken(tableName);
    return normalized.endsWith("_hub") || normalized.startsWith("hub_");
  }

  static String normalizeToken(String raw) {
    if (Utils.isEmpty(raw)) {
      return "";
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    int start = 0;
    int end = normalized.length();
    while (start < end && normalized.charAt(start) == '_') {
      start++;
    }
    while (end > start && normalized.charAt(end - 1) == '_') {
      end--;
    }
    return start == 0 && end == normalized.length() ? normalized : normalized.substring(start, end);
  }
}
