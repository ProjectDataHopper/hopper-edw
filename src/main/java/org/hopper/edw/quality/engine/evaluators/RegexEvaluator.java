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
package org.hopper.edw.quality.engine.evaluators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.quality.RegexSupport;
import org.hopper.edw.quality.engine.IDataQualityRuleEvaluator;
import org.hopper.edw.quality.engine.QualityEvaluationContext;
import org.hopper.edw.quality.model.DataQualityFinding;
import org.hopper.edw.quality.model.DataQualityRule;
import org.hopper.edw.quality.model.DataQualityRuleType;
import org.hopper.edw.quality.model.QualitySeverity;
import org.hopper.edw.quality.profile.FieldProfile;

/**
 * Evaluates REGEX rules using SQL pushdown metrics when present, otherwise client-side matching
 * against profiled value distributions (Java regex — sample/fall-through path only).
 */
public final class RegexEvaluator implements IDataQualityRuleEvaluator {

  /**
   * @deprecated use {@link RegexSupport#MATCH_MODE_FULL}
   */
  public static final String MATCH_MODE_FULL = RegexSupport.MATCH_MODE_FULL;

  /**
   * @deprecated use {@link RegexSupport#MATCH_MODE_FIND}
   */
  public static final String MATCH_MODE_FIND = RegexSupport.MATCH_MODE_FIND;

  /**
   * @deprecated use {@link RegexSupport#MATCH_MODE_PARTIAL}
   */
  public static final String MATCH_MODE_PARTIAL = RegexSupport.MATCH_MODE_PARTIAL;

  /**
   * @deprecated use {@link RegexSupport#PATH_PUSHDOWN}
   */
  public static final String PATH_PUSHDOWN = RegexSupport.PATH_PUSHDOWN;

  /**
   * @deprecated use {@link RegexSupport#PATH_SAMPLE}
   */
  public static final String PATH_SAMPLE = RegexSupport.PATH_SAMPLE;

  @Override
  public DataQualityRuleType type() {
    return DataQualityRuleType.REGEX;
  }

  @Override
  public List<DataQualityFinding> evaluate(DataQualityRule rule, QualityEvaluationContext context) {
    if (EvaluatorSupport.missingField(rule) || context.getProfile() == null) {
      return List.of();
    }
    String patternText = rule.parameter(DataQualityRule.PARAM_PATTERN);
    if (Utils.isEmpty(patternText)) {
      return List.of(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              EvaluatorSupport.resolveField(rule),
              "REGEX pattern is required",
              "pattern missing",
              "non-empty pattern",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
    }

    String fieldName = EvaluatorSupport.resolveField(rule);
    FieldProfile field = context.getProfile().findField(fieldName);
    if (field == null) {
      return EvaluatorSupport.fieldNotInProfile(rule, context, fieldName);
    }

    boolean nullAllowed = rule.parameterBoolean(DataQualityRule.PARAM_NULL_ALLOWED, true);
    List<DataQualityFinding> findings = new ArrayList<>();

    if (!nullAllowed && field.getNullCount() > 0) {
      findings.add(
          EvaluatorSupport.finding(
              rule,
              context,
              fieldName,
              "Field '"
                  + fieldName
                  + "' has "
                  + field.getNullCount()
                  + " null value(s) but nullAllowed=false",
              "nullCount=" + field.getNullCount(),
              "nullCount=0",
              EvaluatorSupport.metrics(
                  "nullCount", String.valueOf(field.getNullCount()), "nullAllowed", "false")));
    }

    FieldProfile.RegexRuleProfile regexProfile = field.findRegexProfile(RegexSupport.ruleKey(rule));

    if (regexProfile != null
        && RegexSupport.PATH_PUSHDOWN.equalsIgnoreCase(regexProfile.getPath())) {
      Long mismatch = regexProfile.getMismatchCount();
      if (mismatch != null && mismatch > 0) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "mismatchCount", String.valueOf(mismatch), "path", RegexSupport.PATH_PUSHDOWN);
        findings.add(
            EvaluatorSupport.finding(
                rule,
                context,
                fieldName,
                "Field '" + fieldName + "' has " + mismatch + " value(s) not matching regex",
                "mismatchCount=" + mismatch,
                "pattern=" + patternText,
                metrics));
      }
      return findings;
    }

    if (regexProfile != null && regexProfile.isSkipped()) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX evaluation skipped (no pushdown or sample available); use SQL_ASSERTION for"
                  + " high-cardinality free-text",
              "skipped",
              "evaluated",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    // Sample / client path (collector metrics)
    if (regexProfile != null && RegexSupport.PATH_SAMPLE.equalsIgnoreCase(regexProfile.getPath())) {
      long mismatchDistinct = regexProfile.getSampleMismatchCount();
      long mismatchRows = regexProfile.getSampleMismatchRows();
      if (mismatchDistinct > 0 || mismatchRows > 0) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "path",
                RegexSupport.PATH_SAMPLE,
                "sampleSize",
                String.valueOf(regexProfile.getSampleSize()));
        // Distinct-sample path: sampleMismatchCount = failing distinct values
        metrics.put("mismatchDistinct", String.valueOf(mismatchDistinct));
        if (mismatchRows > 0) {
          // ValueCounts-weighted path
          metrics.put("mismatchRows", String.valueOf(mismatchRows));
          metrics.put("mismatchCount", String.valueOf(mismatchRows));
        } else {
          metrics.put("mismatchCount", String.valueOf(mismatchDistinct));
        }
        findings.add(
            EvaluatorSupport.finding(
                rule,
                context,
                fieldName,
                "Field '" + fieldName + "' has sample value(s) not matching regex",
                "mismatchDistinct="
                    + mismatchDistinct
                    + (mismatchRows > 0 ? " mismatchRows=" + mismatchRows : ""),
                "pattern=" + patternText,
                metrics));
      } else if (regexProfile.isCoverageIncomplete()) {
        Map<String, String> metrics =
            EvaluatorSupport.metrics(
                "coverageIncomplete",
                "true",
                "sampleLimit",
                String.valueOf(regexProfile.getSampleLimit()));
        metrics.put("path", RegexSupport.PATH_SAMPLE);
        findings.add(
            EvaluatorSupport.findingWithSeverity(
                rule,
                context,
                fieldName,
                "REGEX coverage incomplete (sample limit " + regexProfile.getSampleLimit() + ")",
                "sampleSize=" + regexProfile.getSampleSize(),
                "full coverage",
                metrics,
                QualitySeverity.INFO));
      }
      return findings;
    }

    // Fall-through: evaluate against collected value distribution with Java regex
    if (field.getValueCounts().isEmpty() && field.getDistinctValues().isEmpty()) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX evaluation skipped (no value distribution); use SQL_ASSERTION or ensure"
                  + " profiling collected values",
              "skipped",
              "evaluated",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    Pattern pattern;
    try {
      pattern = RegexSupport.compile(rule, patternText);
    } catch (PatternSyntaxException e) {
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "Invalid Java regex pattern: " + e.getDescription(),
              "pattern=" + patternText,
              "valid regex",
              EvaluatorSupport.metric("regexSkipped", "true"),
              QualitySeverity.WARNING));
      return findings;
    }

    boolean fullMatch = RegexSupport.isFullMatch(rule);
    long invalidRows = 0;
    long invalidDistinct = 0;
    long sampleSize = 0;
    for (Map.Entry<String, Long> entry : field.getValueCounts().entrySet()) {
      String value = entry.getKey();
      if (value == null) {
        continue;
      }
      sampleSize++;
      if (!RegexSupport.matches(pattern, value, fullMatch)) {
        invalidDistinct++;
        invalidRows += entry.getValue() != null ? entry.getValue() : 1L;
      }
    }
    // If only distinct set without counts
    if (field.getValueCounts().isEmpty()) {
      for (String value : field.getDistinctValues()) {
        if (value == null) {
          continue;
        }
        sampleSize++;
        if (!RegexSupport.matches(pattern, value, fullMatch)) {
          invalidDistinct++;
          invalidRows++;
        }
      }
    }

    if (invalidDistinct > 0 || invalidRows > 0) {
      Map<String, String> metrics =
          EvaluatorSupport.metrics(
              "path", RegexSupport.PATH_SAMPLE, "sampleSize", String.valueOf(sampleSize));
      metrics.put("mismatchDistinct", String.valueOf(invalidDistinct));
      metrics.put("mismatchRows", String.valueOf(invalidRows));
      metrics.put("mismatchCount", String.valueOf(invalidRows));
      findings.add(
          EvaluatorSupport.finding(
              rule,
              context,
              fieldName,
              "Field '" + fieldName + "' has " + invalidRows + " value(s) not matching regex",
              "mismatchCount=" + invalidRows,
              "pattern=" + patternText,
              metrics));
    } else if (field.isDistinctTruncated()) {
      int limit =
          field.getDistinctValues().size() > 0
              ? field.getDistinctValues().size()
              : (int) sampleSize;
      Map<String, String> metrics =
          EvaluatorSupport.metrics(
              "coverageIncomplete", "true", "sampleLimit", String.valueOf(limit));
      metrics.put("path", RegexSupport.PATH_SAMPLE);
      findings.add(
          EvaluatorSupport.findingWithSeverity(
              rule,
              context,
              fieldName,
              "REGEX coverage incomplete (sample limit " + limit + ")",
              "sampleSize=" + sampleSize,
              "full coverage",
              metrics,
              QualitySeverity.INFO));
    }

    return findings;
  }

  /**
   * @deprecated use {@link RegexSupport#ruleKey(DataQualityRule)}
   */
  public static String ruleKey(DataQualityRule rule) {
    return RegexSupport.ruleKey(rule);
  }

  /**
   * @deprecated use {@link RegexSupport#isFullMatch(DataQualityRule)}
   */
  public static boolean isFullMatch(DataQualityRule rule) {
    return RegexSupport.isFullMatch(rule);
  }

  /**
   * @deprecated use {@link RegexSupport#compile(DataQualityRule, String)}
   */
  public static Pattern compile(DataQualityRule rule, String patternText) {
    return RegexSupport.compile(rule, patternText);
  }

  /**
   * @deprecated use {@link RegexSupport#matches(Pattern, String, boolean)}
   */
  public static boolean matches(Pattern pattern, String value, boolean fullMatch) {
    return RegexSupport.matches(pattern, value, fullMatch);
  }
}
