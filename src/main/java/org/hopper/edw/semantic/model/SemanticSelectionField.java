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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.AggregationMethod;

/** One field in a named selection (attribute or measure). */
@Getter
@Setter
@NoArgsConstructor
public class SemanticSelectionField {

  @HopMetadataProperty private String entityName;

  @HopMetadataProperty private String fieldName;

  @HopMetadataProperty private String header;

  /** Measure aggregation override (SUM/COUNT/AVERAGE); ignored for attributes. */
  @HopMetadataProperty private String aggregation;

  public SemanticSelectionField(String entityName, String fieldName) {
    this.entityName = entityName;
    this.fieldName = fieldName;
  }

  public SemanticSelectionField copy() {
    SemanticSelectionField copy = new SemanticSelectionField(entityName, fieldName);
    copy.header = header;
    copy.aggregation = aggregation;
    return copy;
  }

  public AggregationMethod resolveAggregation() {
    if (aggregation == null || aggregation.isBlank()) {
      return null;
    }
    try {
      return AggregationMethod.valueOf(aggregation.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public void setAggregationMethod(AggregationMethod method) {
    this.aggregation = method != null ? method.name() : null;
  }
}
