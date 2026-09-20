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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Logical business table in the semantic layer. */
@Getter
@Setter
@NoArgsConstructor
public class SemanticEntity {

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String description;

  @HopMetadataProperty(storeWithCode = true)
  private SemanticEntityRole role = SemanticEntityRole.DIMENSION;

  @HopMetadataProperty private SemanticPhysicalBinding binding = new SemanticPhysicalBinding();

  @HopMetadataProperty(key = "attribute", groupKey = "attributes")
  private List<SemanticAttribute> attributes = new ArrayList<>();

  @HopMetadataProperty(key = "measure", groupKey = "measures")
  private List<SemanticMeasure> measures = new ArrayList<>();

  public SemanticEntityRole resolveRole() {
    return role != null ? role : SemanticEntityRole.DIMENSION;
  }

  public SemanticAttribute findAttribute(String fieldName) {
    if (fieldName == null || attributes == null) {
      return null;
    }
    for (SemanticAttribute attribute : attributes) {
      if (attribute != null && fieldName.equals(attribute.getName())) {
        return attribute;
      }
    }
    return null;
  }

  public SemanticMeasure findMeasure(String fieldName) {
    if (fieldName == null || measures == null) {
      return null;
    }
    for (SemanticMeasure measure : measures) {
      if (measure != null && fieldName.equals(measure.getName())) {
        return measure;
      }
    }
    return null;
  }

  public String resolvePhysicalTableName() {
    if (binding == null) {
      return name;
    }
    if (binding.getPhysicalTableName() != null && !binding.getPhysicalTableName().isBlank()) {
      return binding.getPhysicalTableName();
    }
    if (binding.getTableName() != null && !binding.getTableName().isBlank()) {
      return binding.getTableName();
    }
    return name;
  }
}
