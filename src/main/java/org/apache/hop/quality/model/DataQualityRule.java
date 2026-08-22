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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Declarative data quality rule (library entry or inline on a catalog record). */
@Getter
@Setter
@NoArgsConstructor
public class DataQualityRule {

  public static final String PARAM_MIN = "min";
  public static final String PARAM_MAX = "max";
  public static final String PARAM_VALUES = "values";
  public static final String PARAM_NULL_ALLOWED = "nullAllowed";
  public static final String PARAM_TRIM = "trim";

  /** Null ratio upper bound for {@link DataQualityRuleType#NULL_RATIO_MAX} (0.0–1.0). */
  public static final String PARAM_MAX_RATIO = "maxRatio";

  /** Regex pattern for {@link DataQualityRuleType#REGEX}. */
  public static final String PARAM_PATTERN = "pattern";

  /** Regex case sensitivity (default true). */
  public static final String PARAM_CASE_SENSITIVE = "caseSensitive";

  /** Regex match mode: FULL (default) or FIND/PARTIAL. */
  public static final String PARAM_MATCH_MODE = "matchMode";

  /**
   * SQL text for {@link DataQualityRuleType#SQL_ASSERTION}. Must be a single SELECT/WITH statement.
   * Multi-statement is rejected via a literal semicolon scan (a single trailing {@code ;} is
   * allowed); semicolons inside string literals are false-rejected under this hard policy.
   */
  public static final String PARAM_SQL = "sql";

  /**
   * Expectation mode for {@link DataQualityRuleType#SQL_ASSERTION}: {@code ZERO_ROWS} (default),
   * {@code ONE_ROW_TRUE}, or {@code SCALAR_EQ}.
   */
  public static final String PARAM_EXPECT = "expect";

  /**
   * Expected scalar value when expect is {@code SCALAR_EQ}. Compared via strict {@code
   * String.valueOf(firstColumn)} equality (no numeric normalization).
   */
  public static final String PARAM_EXPECT_VALUE = "expectValue";

  /** JDBC query timeout seconds for {@link DataQualityRuleType#SQL_ASSERTION} (default 60). */
  public static final String PARAM_QUERY_TIMEOUT = "queryTimeoutSeconds";

  @HopMetadataProperty private String id;

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String description;

  @HopMetadataProperty private DataQualityRuleType type = DataQualityRuleType.NOT_NULL;

  @HopMetadataProperty private QualitySeverity severity = QualitySeverity.BLOCKING;

  /** Optional column scope; null/blank means dataset-level. */
  @HopMetadataProperty private String fieldName;

  @HopMetadataProperty private boolean enabled = true;

  @HopMetadataProperty private QualityEvaluationMode evaluationHint = QualityEvaluationMode.AUTO;

  /** Type-specific parameters (min, max, values CSV, nullAllowed, trim, …). */
  @HopMetadataProperty private Map<String, String> parameters = new LinkedHashMap<>();

  public DataQualityRule copy() {
    DataQualityRule copy = new DataQualityRule();
    copy.id = id;
    copy.name = name;
    copy.description = description;
    copy.type = type;
    copy.severity = severity;
    copy.fieldName = fieldName;
    copy.enabled = enabled;
    copy.evaluationHint = evaluationHint;
    if (parameters != null) {
      copy.parameters = new LinkedHashMap<>(parameters);
    }
    return copy;
  }

  public void ensureId() {
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
    }
  }

  public String parameter(String key) {
    if (parameters == null || key == null) {
      return null;
    }
    return parameters.get(key);
  }

  public String parameter(String key, String defaultValue) {
    String value = parameter(key);
    return value != null && !value.isBlank() ? value : defaultValue;
  }

  public boolean parameterBoolean(String key, boolean defaultValue) {
    String value = parameter(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.trim())
        || "Y".equalsIgnoreCase(value.trim())
        || "yes".equalsIgnoreCase(value.trim());
  }
}
