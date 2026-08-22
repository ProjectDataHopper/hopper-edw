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
package org.apache.hop.quality.metadata;

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
import org.apache.hop.quality.model.DataQualityRule;

/** Central library of reusable data quality rules. */
@HopMetadata(
    key = "data-quality-rule-set",
    name = "i18n::DataQualityRuleSetMeta.name",
    description = "i18n::DataQualityRuleSetMeta.description",
    image = "data-catalog.svg",
    documentationUrl = "/metadata-types/data-quality-rule-set.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class DataQualityRuleSetMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty(key = "rule", groupKey = "rules")
  private List<DataQualityRule> rules = new ArrayList<>();

  public DataQualityRuleSetMeta() {
    super();
  }

  public DataQualityRuleSetMeta(String name) {
    super(name);
  }

  public List<DataQualityRule> getRules() {
    if (rules == null) {
      rules = new ArrayList<>();
    }
    return rules;
  }

  public DataQualityRule findRule(String ruleId) {
    if (Utils.isEmpty(ruleId)) {
      return null;
    }
    for (DataQualityRule rule : getRules()) {
      if (rule != null && ruleId.equals(rule.getId())) {
        return rule;
      }
    }
    for (DataQualityRule rule : getRules()) {
      if (rule != null && ruleId.equals(rule.getName())) {
        return rule;
      }
    }
    return null;
  }
}
