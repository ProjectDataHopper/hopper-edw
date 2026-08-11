/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.datatypemapping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One ordered rule in a {@link DataTypeMappingMeta}: match criteria against a physical source field
 * plus target type/length/conversion attributes.
 */
@Getter
@Setter
@NoArgsConstructor
public class DataTypeMappingRule {

  @HopMetadataProperty private String id;
  @HopMetadataProperty private String name;
  @HopMetadataProperty private String description;
  @HopMetadataProperty private boolean enabled = true;

  // --- Match criteria (all optional; empty criteria never matches) ---

  /** Hop type name to match (e.g. String). Empty = any type. */
  @HopMetadataProperty private String matchHopType;

  /** Glob/regex against native/source data type (e.g. TEXT, VARCHAR*). */
  @HopMetadataProperty private String matchSourceDataTypePattern;

  /** Glob/regex against field name (case-insensitive). */
  @HopMetadataProperty private String matchFieldNamePattern;

  /** When true, only fields with missing/empty/-1 length match. */
  @HopMetadataProperty private boolean matchLengthAbsent;

  /** When set (>=0), only fields with numeric length strictly below this value match. */
  @HopMetadataProperty private String matchLengthBelow;

  /** When set (>=0), only fields with numeric length strictly above this value match. */
  @HopMetadataProperty private String matchLengthAbove;

  // --- Target ---

  @HopMetadataProperty(intCodeConverter = ValueMetaBase.ValueTypeCodeConverter.class)
  private int targetHopType = IValueMeta.TYPE_NONE;

  @HopMetadataProperty private String targetLength;
  @HopMetadataProperty private String targetPrecision;

  /** Optional rename; empty keeps the source field name. Supports {@code *} capture later. */
  @HopMetadataProperty private String targetFieldName;

  @HopMetadataProperty private FieldConversionOptions conversion = new FieldConversionOptions();

  public FieldConversionOptions getConversion() {
    if (conversion == null) {
      conversion = new FieldConversionOptions();
    }
    return conversion;
  }

  public boolean hasMatchCriteria() {
    return !Utils.isEmpty(matchHopType)
        || !Utils.isEmpty(matchSourceDataTypePattern)
        || !Utils.isEmpty(matchFieldNamePattern)
        || matchLengthAbsent
        || !Utils.isEmpty(matchLengthBelow)
        || !Utils.isEmpty(matchLengthAbove);
  }

  public boolean hasTargetAttributes() {
    return targetHopType > IValueMeta.TYPE_NONE
        || !Utils.isEmpty(targetLength)
        || !Utils.isEmpty(targetPrecision)
        || !Utils.isEmpty(targetFieldName)
        || getConversion().hasAnyAttribute();
  }
}
