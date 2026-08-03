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
package org.apache.hop.quality.model;

/** Built-in data quality rule kinds (Phase 1 + Phase 2 types). */
public enum DataQualityRuleType {
  MIN_ROW_COUNT,
  MAX_ROW_COUNT,
  NOT_NULL,
  ALLOWED_VALUES,
  RANGE,
  NOT_EMPTY_STRING,
  /** Fail when nullCount/rowCount exceeds maxRatio (0.0–1.0). */
  NULL_RATIO_MAX,
  /** Fail when observed distinct count is below min. */
  MIN_DISTINCT,
  /** Fail when observed distinct count exceeds max. */
  MAX_DISTINCT,
  /** Non-null values must match a regex (DB pushdown preferred). */
  REGEX,
  /** Fail when any non-null string length is below min. */
  MIN_LENGTH,
  /** Fail when any non-null string length exceeds max. */
  MAX_LENGTH,
  /**
   * Author-supplied SELECT/WITH assertion (escape hatch). Evaluated by {@code SqlAssertionRunner},
   * not the profile evaluator registry.
   */
  SQL_ASSERTION
}
