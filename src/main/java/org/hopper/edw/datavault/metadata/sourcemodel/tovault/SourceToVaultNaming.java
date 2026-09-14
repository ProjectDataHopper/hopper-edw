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
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingSupport;

/** Default raw-vault object names derived from source table names. */
public final class SourceToVaultNaming {

  private static final ThreadLocal<IHopMetadataProvider> NAMING_PROVIDER = new ThreadLocal<>();

  private SourceToVaultNaming() {}

  /** Apply type-specific naming schemes during classify/apply when a provider is in scope. */
  public static void runWithProvider(IHopMetadataProvider provider, Runnable action) {
    if (action == null) {
      return;
    }
    NAMING_PROVIDER.set(provider);
    try {
      action.run();
    } finally {
      NAMING_PROVIDER.remove();
    }
  }

  private static IHopMetadataProvider provider() {
    return NAMING_PROVIDER.get();
  }

  private static String named(String typeCode, String entity, String fallback) {
    return EdwNamingSupport.applyTypeSpecificOrFallback(provider(), typeCode, entity, fallback);
  }

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
    String entity = entityName(tableName);
    return named(EdwNamingSchemeTypes.DV_HUB, entity, "hub_" + entity);
  }

  public static String hubSatelliteName(String tableName) {
    String entity = entityName(tableName);
    return named(EdwNamingSchemeTypes.DV_SATELLITE, entity, "sat_" + entity);
  }

  public static String extensionSatelliteName(String tableName) {
    String entity = normalizeToken(tableName);
    return named(EdwNamingSchemeTypes.DV_SATELLITE, entity, "sat_" + entity);
  }

  public static String linkNameFromTable(String tableName) {
    String entity = normalizeToken(tableName);
    return named(EdwNamingSchemeTypes.DV_LINK, entity, "lnk_" + entity);
  }

  public static String fkLinkName(String childTableName) {
    String entity = entityName(childTableName);
    return named(EdwNamingSchemeTypes.DV_LINK, entity, "lnk_" + entity);
  }

  public static String linkSatelliteName(String tableName) {
    String entity = "lnk_" + normalizeToken(tableName);
    return named(EdwNamingSchemeTypes.DV_SATELLITE, entity, "sat_" + entity);
  }

  public static String referenceName(String tableName) {
    String entity = entityName(tableName);
    return named(EdwNamingSchemeTypes.DV_REFERENCE, entity, "ref_" + entity);
  }

  public static String hierarchyAliasName(String tableName) {
    return hubName(tableName) + "_parent";
  }

  public static String hierarchyLinkName(String tableName) {
    String entity = entityName(tableName) + "_hierarchy";
    return named(EdwNamingSchemeTypes.DV_LINK, entity, "lnk_" + entity);
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
