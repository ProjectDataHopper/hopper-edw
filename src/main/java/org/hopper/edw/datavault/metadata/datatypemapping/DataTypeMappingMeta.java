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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Project-level reusable data type mapping profile for pre-modeling sources (issue #113).
 *
 * <p>Profiles are attached to source model entities (and later catalog sources). Rules improve weak
 * source types (e.g. String without length → String(2000)) before catalog publish and vault loads.
 */
@HopMetadata(
    key = "data-type-mapping",
    name = "i18n::DataTypeMappingMeta.name",
    description = "i18n::DataTypeMappingMeta.description",
    image = "data-type-mapping.svg",
    documentationUrl = "/metadata-types/data-type-mapping.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class DataTypeMappingMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty private DataTypeMappingScope scope = new DataTypeMappingScope();

  @HopMetadataProperty(key = "rule", groupKey = "rules")
  private List<DataTypeMappingRule> rules = new ArrayList<>();

  public DataTypeMappingMeta() {
    super();
  }

  public DataTypeMappingMeta(String name) {
    super(name);
  }

  public DataTypeMappingScope getScope() {
    if (scope == null) {
      scope = new DataTypeMappingScope();
    }
    return scope;
  }

  public List<DataTypeMappingRule> getRules() {
    if (rules == null) {
      rules = new ArrayList<>();
    }
    return rules;
  }

  public DataTypeMappingRule findRule(String ruleIdOrName) {
    if (Utils.isEmpty(ruleIdOrName)) {
      return null;
    }
    for (DataTypeMappingRule rule : getRules()) {
      if (rule != null && ruleIdOrName.equals(rule.getId())) {
        return rule;
      }
    }
    for (DataTypeMappingRule rule : getRules()) {
      if (rule != null && ruleIdOrName.equals(rule.getName())) {
        return rule;
      }
    }
    return null;
  }
}
