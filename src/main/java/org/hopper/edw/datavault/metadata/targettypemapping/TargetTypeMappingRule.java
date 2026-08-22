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
package org.hopper.edw.datavault.metadata.targettypemapping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One ordered rule in a {@link TargetTypeMappingMeta}: match a Hop value meta and emit a native SQL
 * type template.
 */
@Getter
@Setter
@NoArgsConstructor
public class TargetTypeMappingRule {

  @HopMetadataProperty private String id;
  @HopMetadataProperty private String name;
  @HopMetadataProperty private String description;
  @HopMetadataProperty private boolean enabled = true;

  /** Hop type name to match (e.g. String). Empty = any type. */
  @HopMetadataProperty private String matchHopType;

  /** Inclusive minimum Hop length. Empty = unbounded. Supports variables. */
  @HopMetadataProperty private String matchMinLength;

  /** Inclusive maximum Hop length. Empty = unbounded. Supports variables. */
  @HopMetadataProperty private String matchMaxLength;

  /** Inclusive minimum Hop precision. Empty = unbounded. Supports variables. */
  @HopMetadataProperty private String matchMinPrecision;

  /** Inclusive maximum Hop precision. Empty = unbounded. Supports variables. */
  @HopMetadataProperty private String matchMaxPrecision;

  /** When true, only fields with missing/empty/-1 length match. */
  @HopMetadataProperty private boolean matchLengthAbsent;

  /** Optional glob/regex against the field name (case-insensitive). */
  @HopMetadataProperty private String matchFieldNamePattern;

  /**
   * Native SQL type template, e.g. {@code CHAR(1)}, {@code NVARCHAR({length})}, {@code timestamp(6)
   * with time zone}. Supports Hop variables and {@code {length}} / {@code {precision}}.
   */
  @HopMetadataProperty private String targetSqlType;

  public boolean hasMatchCriteria() {
    return !Utils.isEmpty(matchHopType)
        || !Utils.isEmpty(matchMinLength)
        || !Utils.isEmpty(matchMaxLength)
        || !Utils.isEmpty(matchMinPrecision)
        || !Utils.isEmpty(matchMaxPrecision)
        || matchLengthAbsent
        || !Utils.isEmpty(matchFieldNamePattern);
  }
}
