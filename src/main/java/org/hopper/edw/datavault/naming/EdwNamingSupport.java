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
package org.hopper.edw.datavault.naming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.naming.engine.NamingEngine;
import org.apache.hop.naming.engine.NamingSchemeValidator;
import org.apache.hop.naming.engine.NamingSchemeValidator.Finding;
import org.apache.hop.naming.engine.NamingSchemeWalker;
import org.apache.hop.naming.metadata.NamingScheme;
import org.apache.hop.naming.metadata.NamingSchemeSelector;

/**
 * Facade over Hop's naming plugin. Missing plugin or empty metadata is a no-op so hopper-edw still
 * loads when {@code hop-misc-naming} is not installed.
 */
public final class EdwNamingSupport {

  private EdwNamingSupport() {}

  public static List<NamingScheme> loadSchemes(IHopMetadataProvider provider) {
    if (provider == null) {
      return List.of();
    }
    try {
      List<NamingScheme> schemes = provider.getSerializer(NamingScheme.class).loadAll();
      return schemes != null ? schemes : List.of();
    } catch (Throwable t) {
      return List.of();
    }
  }

  /**
   * True when the project has at least one scheme whose type code equals {@code typeCode} (not
   * General). Generate-to-vault uses this so a lone General scheme does not drop {@code hub_}
   * prefixes.
   */
  public static boolean hasTypeSpecificScheme(IHopMetadataProvider provider, String typeCode) {
    if (StringUtils.isEmpty(typeCode)) {
      return false;
    }
    for (NamingScheme scheme : loadSchemes(provider)) {
      if (scheme != null && typeCode.equalsIgnoreCase(StringUtils.trimToEmpty(scheme.getType()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Apply the unique matching scheme. Returns {@code value} unchanged when skipped, when no unique
   * scheme exists, or when the naming plugin is unavailable.
   */
  public static String apply(IHopMetadataProvider provider, String typeCode, String value) {
    if (NamingEngine.shouldSkip(value)) {
      return value;
    }
    NamingScheme scheme = NamingSchemeSelector.resolve(loadSchemes(provider), typeCode, null);
    if (scheme == null) {
      return value;
    }
    return apply(scheme, value, typeCode);
  }

  /**
   * Apply a type-specific scheme to {@code value}, or return {@code fallback} when none exists.
   * Used by generate-to-vault so {@code hub_}/{@code sat_} stay the default.
   */
  public static String applyTypeSpecificOrFallback(
      IHopMetadataProvider provider, String typeCode, String value, String fallback) {
    if (!hasTypeSpecificScheme(provider, typeCode)) {
      return fallback;
    }
    String applied = apply(provider, typeCode, value);
    return StringUtils.isEmpty(applied) ? fallback : applied;
  }

  public static String apply(NamingScheme scheme, String value, String typeCode) {
    if (scheme == null || NamingEngine.shouldSkip(value)) {
      return value;
    }
    try {
      String rewritten = NamingEngine.apply(scheme, value, typeCode);
      return rewritten != null ? rewritten : value;
    } catch (Throwable t) {
      return value;
    }
  }

  public static List<Finding> walk(
      Object root, String location, IHopMetadataProvider provider, Set<String> typeFilter) {
    List<NamingScheme> schemes = loadSchemes(provider);
    if (schemes.isEmpty() || root == null) {
      return List.of();
    }
    try {
      List<Finding> findings = NamingSchemeWalker.walk(root, location, schemes, typeFilter);
      if (findings == null) {
        return List.of();
      }
      return filterAffixFixedPoints(findings, schemes);
    } catch (Throwable t) {
      return List.of();
    }
  }

  /**
   * Hop's engine always prepends {@code prefix} after rewriting the whole string, so {@code
   * hub_customer} is not a fixed point of a {@code hub_} scheme ({@code hub_hub_customer}). Treat
   * the name as conforming when applying the scheme to the prefix-stripped core reproduces it.
   */
  static List<Finding> filterAffixFixedPoints(List<Finding> findings, List<NamingScheme> schemes) {
    List<Finding> kept = new ArrayList<>();
    for (Finding finding : findings) {
      if (finding == null) {
        continue;
      }
      NamingScheme scheme = findScheme(schemes, finding.getSchemeName());
      if (scheme == null) {
        scheme = findSchemeByType(schemes, finding.getTypeCode());
      }
      if (scheme != null && isAffixFixedPoint(scheme, finding.getActual(), finding.getTypeCode())) {
        continue;
      }
      kept.add(finding);
    }
    return kept;
  }

  static boolean isAffixFixedPoint(NamingScheme scheme, String actual, String typeCode) {
    if (scheme == null || NamingEngine.shouldSkip(actual)) {
      return true;
    }
    String applied = apply(scheme, actual, typeCode);
    if (actual.equals(applied)) {
      return true;
    }
    String prefix = StringUtils.defaultString(scheme.getPrefix());
    String suffix = StringUtils.defaultString(scheme.getSuffix());
    String core = actual;
    if (!prefix.isEmpty() && core.startsWith(prefix)) {
      core = core.substring(prefix.length());
    }
    if (!suffix.isEmpty() && core.endsWith(suffix)) {
      core = core.substring(0, core.length() - suffix.length());
    }
    if (core.equals(actual) || core.isEmpty()) {
      return false;
    }
    return actual.equals(apply(scheme, core, typeCode));
  }

  private static NamingScheme findScheme(List<NamingScheme> schemes, String name) {
    if (schemes == null || StringUtils.isEmpty(name)) {
      return null;
    }
    for (NamingScheme scheme : schemes) {
      if (scheme != null && name.equals(scheme.getName())) {
        return scheme;
      }
    }
    return null;
  }

  private static NamingScheme findSchemeByType(List<NamingScheme> schemes, String typeCode) {
    if (schemes == null || StringUtils.isEmpty(typeCode)) {
      return null;
    }
    NamingScheme match = null;
    for (NamingScheme scheme : schemes) {
      if (scheme != null && typeCode.equalsIgnoreCase(StringUtils.trimToEmpty(scheme.getType()))) {
        if (match != null) {
          return null;
        }
        match = scheme;
      }
    }
    return match;
  }

  public static List<Finding> validate(
      String value, String typeCode, IHopMetadataProvider provider) {
    List<NamingScheme> schemes = loadSchemes(provider);
    if (schemes.isEmpty()) {
      return List.of();
    }
    try {
      List<Finding> findings = NamingSchemeValidator.validate(value, typeCode, schemes);
      return findings != null ? findings : List.of();
    } catch (Throwable t) {
      return Collections.emptyList();
    }
  }
}
