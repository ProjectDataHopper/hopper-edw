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
package org.apache.hop.quality;

import java.util.regex.Pattern;
import org.apache.hop.quality.model.DataQualityRule;

/**
 * Shared REGEX helpers for collectors and evaluators (leaf utility — no engine/profile deps beyond
 * the rule model).
 */
public final class RegexSupport {

  public static final String MATCH_MODE_FULL = "FULL";
  public static final String MATCH_MODE_FIND = "FIND";
  public static final String MATCH_MODE_PARTIAL = "PARTIAL";

  public static final String PATH_PUSHDOWN = "pushdown";
  public static final String PATH_SAMPLE = "sample";
  public static final String PATH_NONE = "none";

  private RegexSupport() {}

  public static String ruleKey(DataQualityRule rule) {
    if (rule == null) {
      return "";
    }
    if (rule.getId() != null && !rule.getId().isBlank()) {
      return rule.getId();
    }
    if (rule.getName() != null && !rule.getName().isBlank()) {
      return rule.getName();
    }
    return String.valueOf(System.identityHashCode(rule));
  }

  public static boolean isFullMatch(DataQualityRule rule) {
    String mode =
        rule != null
            ? rule.parameter(DataQualityRule.PARAM_MATCH_MODE, MATCH_MODE_FULL)
            : MATCH_MODE_FULL;
    if (mode == null) {
      return true;
    }
    String normalized = mode.trim();
    return MATCH_MODE_FULL.equalsIgnoreCase(normalized)
        || (!MATCH_MODE_FIND.equalsIgnoreCase(normalized)
            && !MATCH_MODE_PARTIAL.equalsIgnoreCase(normalized));
  }

  public static Pattern compile(DataQualityRule rule, String patternText) {
    boolean caseSensitive =
        rule == null || rule.parameterBoolean(DataQualityRule.PARAM_CASE_SENSITIVE, true);
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return Pattern.compile(patternText, flags);
  }

  public static boolean matches(Pattern pattern, String value, boolean fullMatch) {
    if (fullMatch) {
      return pattern.matcher(value).matches();
    }
    return pattern.matcher(value).find();
  }
}
