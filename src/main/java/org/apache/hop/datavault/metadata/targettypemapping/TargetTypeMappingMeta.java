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
package org.apache.hop.datavault.metadata.targettypemapping;

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
 * Project-level reusable Hop-type to native SQL type preferences for generated DDL (issue #127).
 *
 * <p>Rules are applied before Hop dialect {@code getFieldDefinition}. Unmatched columns keep the
 * database-specific default.
 */
@HopMetadata(
    key = "target-type-mapping",
    name = "i18n::TargetTypeMappingMeta.name",
    description = "i18n::TargetTypeMappingMeta.description",
    image = "target-type-mapping.svg",
    documentationUrl = "/metadata-types/target-type-mapping.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class TargetTypeMappingMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  /**
   * Optional Hop {@code DatabaseMeta} name this mapping is intended for (auto-match and editor
   * preview).
   */
  @HopMetadataProperty private String targetDatabase;

  @HopMetadataProperty(key = "rule", groupKey = "rules")
  private List<TargetTypeMappingRule> rules = new ArrayList<>();

  public TargetTypeMappingMeta() {
    super();
  }

  public TargetTypeMappingMeta(String name) {
    super(name);
  }

  public List<TargetTypeMappingRule> getRules() {
    if (rules == null) {
      rules = new ArrayList<>();
    }
    return rules;
  }

  public TargetTypeMappingRule findRule(String ruleIdOrName) {
    if (Utils.isEmpty(ruleIdOrName)) {
      return null;
    }
    for (TargetTypeMappingRule rule : getRules()) {
      if (rule != null && ruleIdOrName.equals(rule.getId())) {
        return rule;
      }
    }
    for (TargetTypeMappingRule rule : getRules()) {
      if (rule != null && ruleIdOrName.equals(rule.getName())) {
        return rule;
      }
    }
    return null;
  }
}
