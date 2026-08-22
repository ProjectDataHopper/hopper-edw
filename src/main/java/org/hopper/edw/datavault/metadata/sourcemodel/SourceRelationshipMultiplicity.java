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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.Locale;
import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Participation / cardinality at one end of a {@link SourceRelationship} (crow's foot min..max).
 */
@Getter
public enum SourceRelationshipMultiplicity implements IEnumHasCodeAndDescription {
  UNKNOWN(
      "UNKNOWN",
      BaseMessages.getString(
          SourceRelationshipMultiplicity.class, "SourceRelationshipMultiplicity.Unknown")),
  ONE(
      "ONE",
      BaseMessages.getString(
          SourceRelationshipMultiplicity.class, "SourceRelationshipMultiplicity.One")),
  ZERO_OR_ONE(
      "ZERO_OR_ONE",
      BaseMessages.getString(
          SourceRelationshipMultiplicity.class, "SourceRelationshipMultiplicity.ZeroOrOne")),
  ONE_OR_MANY(
      "ONE_OR_MANY",
      BaseMessages.getString(
          SourceRelationshipMultiplicity.class, "SourceRelationshipMultiplicity.OneOrMany")),
  ZERO_OR_MANY(
      "ZERO_OR_MANY",
      BaseMessages.getString(
          SourceRelationshipMultiplicity.class, "SourceRelationshipMultiplicity.ZeroOrMany"));

  private final String code;
  private final String description;

  SourceRelationshipMultiplicity(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceRelationshipMultiplicity.class);
  }

  public static SourceRelationshipMultiplicity lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        SourceRelationshipMultiplicity.class, description, UNKNOWN);
  }

  public static SourceRelationshipMultiplicity lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceRelationshipMultiplicity.class, code, UNKNOWN);
  }

  /** Compact canvas label: {@code 1}, {@code 0..1}, {@code N}, {@code 0..N}, {@code ?}. */
  public String compactLabel() {
    return switch (this) {
      case ONE -> "1";
      case ZERO_OR_ONE -> "0..1";
      case ONE_OR_MANY -> "N";
      case ZERO_OR_MANY -> "0..N";
      case UNKNOWN -> "?";
    };
  }

  public boolean isMandatory() {
    return this == ONE || this == ONE_OR_MANY;
  }

  public boolean isMany() {
    return this == ONE_OR_MANY || this == ZERO_OR_MANY;
  }

  /**
   * Parses legacy free-text cardinality (e.g. {@code N:1}, {@code 1:1}, {@code 0..N}) into
   * child/parent multiplicities. Returns {@code null} when unrecognised.
   */
  public static MultiplicityPair parseLegacyCardinality(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('×', ':').replace('*', 'N');
    normalized = normalized.replace("..", "..");
    // Forms: N:1, 1:N, 1:1, 0..N:1, child/parent
    String[] parts = normalized.split("\\s*[:/]\\s*");
    if (parts.length != 2) {
      // Single token like 0..1 or N
      SourceRelationshipMultiplicity one = parseToken(normalized);
      if (one != null) {
        return new MultiplicityPair(one, UNKNOWN);
      }
      return null;
    }
    SourceRelationshipMultiplicity child = parseToken(parts[0]);
    SourceRelationshipMultiplicity parent = parseToken(parts[1]);
    if (child == null && parent == null) {
      return null;
    }
    return new MultiplicityPair(child != null ? child : UNKNOWN, parent != null ? parent : UNKNOWN);
  }

  private static SourceRelationshipMultiplicity parseToken(String token) {
    if (Utils.isEmpty(token)) {
      return null;
    }
    String t = token.trim().toUpperCase(Locale.ROOT);
    return switch (t) {
      case "1", "1..1", "1.1", "ONE" -> ONE;
      case "0..1", "0.1", "0:1", "ZERO_OR_ONE" -> ZERO_OR_ONE;
      case "N", "1..N", "1.N", "1..*", "ONE_OR_MANY", "M" -> ONE_OR_MANY;
      case "0..N", "0.N", "0..*", "ZERO_OR_MANY", "0:N" -> ZERO_OR_MANY;
      case "?", "UNKNOWN" -> UNKNOWN;
      default -> null;
    };
  }

  public record MultiplicityPair(
      SourceRelationshipMultiplicity child, SourceRelationshipMultiplicity parent) {}
}
