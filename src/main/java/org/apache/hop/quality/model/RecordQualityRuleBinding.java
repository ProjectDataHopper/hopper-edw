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
package org.apache.hop.quality.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Applies a library rule or an inline rule to a catalog record definition.
 *
 * <p>Either {@link #ruleSetName}+{@link #ruleId} or {@link #inlineRule} should be set.
 */
@Getter
@Setter
@NoArgsConstructor
public class RecordQualityRuleBinding {

  /** Hop metadata name of a {@code DataQualityRuleSetMeta}. */
  @HopMetadataProperty private String ruleSetName;

  /** Rule id within the referenced rule set. */
  @HopMetadataProperty private String ruleId;

  /** Fully embedded rule when not using a library reference. */
  @HopMetadataProperty private DataQualityRule inlineRule;

  @HopMetadataProperty private QualitySeverity severityOverride;

  @HopMetadataProperty private String fieldNameOverride;

  @HopMetadataProperty private boolean enabled = true;
}
