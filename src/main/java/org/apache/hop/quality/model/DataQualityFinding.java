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

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** One rule violation or observation from a measure pass (not an infrastructure error). */
@Getter
@Builder
public class DataQualityFinding {

  private final String subjectKey;
  private final String ruleId;
  private final String ruleName;
  private final DataQualityRuleType type;
  private final QualitySeverity severity;
  private final String fieldName;
  private final String message;
  private final String actualSummary;
  private final String expectedSummary;
  @Builder.Default private final Map<String, String> metrics = new LinkedHashMap<>();
}
