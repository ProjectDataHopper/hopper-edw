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
package org.hopper.edw.semantic.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.AggregationMethod;

/** Aggregatable logical field (KPI / fact). */
@Getter
@Setter
@NoArgsConstructor
public class SemanticMeasure {

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String physicalColumn;

  @HopMetadataProperty private String header;

  @HopMetadataProperty private String description;

  @HopMetadataProperty private String formatMask;

  /** SUM, COUNT, or AVERAGE. Stored as a string so XML metadata need not implement IEnumHasCode. */
  @HopMetadataProperty private String aggregation = AggregationMethod.SUM.name();

  @HopMetadataProperty private boolean additive = true;

  @HopMetadataProperty private Map<String, String> properties = new HashMap<>();

  public SemanticMeasure(String name, String physicalColumn) {
    this.name = name;
    this.physicalColumn = physicalColumn;
    this.header = name;
  }

  public String resolvePhysicalColumn() {
    return physicalColumn != null && !physicalColumn.isBlank() ? physicalColumn : name;
  }

  public void setAggregationMethod(AggregationMethod method) {
    this.aggregation = method != null ? method.name() : AggregationMethod.SUM.name();
  }

  public AggregationMethod resolveAggregation() {
    if (aggregation == null || aggregation.isBlank()) {
      return AggregationMethod.SUM;
    }
    try {
      return AggregationMethod.valueOf(aggregation.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return AggregationMethod.SUM;
    }
  }
}
