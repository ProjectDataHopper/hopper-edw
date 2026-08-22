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
package org.hopper.edw.quality.profile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Per-field metrics collected during a measure pass. */
@Getter
public class FieldProfile {

  private final String fieldName;
  private long nullCount;
  private long emptyStringCount;
  private long nonNullCount;
  private Comparable<?> minValue;
  private Comparable<?> maxValue;
  private final Set<String> distinctValues = new LinkedHashSet<>();
  private final Map<String, Long> valueCounts = new LinkedHashMap<>();
  private boolean distinctTruncated;

  /**
   * Exact distinct count from SQL {@code COUNT(DISTINCT col)} (or complete full-scan observation).
   * {@code null} means unknown — use {@link #distinctValues} + {@link #distinctTruncated} fallback.
   */
  @Setter private Long exactDistinctCount;

  /**
   * True when the collector attempted exact distinct but both COUNT(DISTINCT) and sample fallback
   * failed — evaluators must not treat empty distinctValues as proof of zero cardinality.
   */
  @Setter private boolean distinctUnknown;

  private Integer minStringLength;
  private Integer maxStringLength;

  /**
   * Per-rule REGEX collector results keyed by rule id. Populated by SQL pushdown or bounded sample
   * paths; evaluators fall back to client-side valueCounts when absent.
   */
  private final Map<String, RegexRuleProfile> regexByRuleId = new LinkedHashMap<>();

  public FieldProfile(String fieldName) {
    this.fieldName = fieldName;
  }

  public void observeNull() {
    nullCount++;
  }

  /**
   * Observe an empty string value. Records length 0 and includes {@code ""} in valueCounts /
   * distinctValues so REGEX and distinct rules align with SQL {@code COUNT(DISTINCT)} (which counts
   * empty strings).
   */
  public void observeEmptyString() {
    observeEmptyString(Integer.MAX_VALUE);
  }

  /**
   * Observe an empty string with a distinct-set cap (row-profile path).
   *
   * @param maxDistinct max distinct values to retain in the sample set
   */
  public void observeEmptyString(int maxDistinct) {
    emptyStringCount++;
    nonNullCount++;
    observeStringLength(0);
    recordEmptyInDistribution(1L, maxDistinct);
  }

  public void observeValue(Object raw, String display, int maxDistinct) {
    observeValueCount(raw, display, 1L, maxDistinct);
  }

  /** Bulk observe for SQL GROUP BY counts. */
  public void observeValueCount(Object raw, String display, long count, int maxDistinct) {
    if (count <= 0) {
      return;
    }
    nonNullCount += count;
    if (raw instanceof Comparable<?> comparable) {
      updateMinMax(comparable);
    }
    if (display == null) {
      return;
    }
    valueCounts.merge(display, count, Long::sum);
    if (distinctValues.size() < maxDistinct) {
      distinctValues.add(display);
    } else if (!distinctValues.contains(display)) {
      distinctTruncated = true;
    }
  }

  /** Marks the distinct sample as incomplete (collector hit a row limit). */
  public void markDistinctTruncated() {
    distinctTruncated = true;
  }

  public void observeStringLength(int length) {
    if (minStringLength == null || length < minStringLength) {
      minStringLength = length;
    }
    if (maxStringLength == null || length > maxStringLength) {
      maxStringLength = length;
    }
  }

  public void setStringLengthFromSql(Integer min, Integer max) {
    if (min != null) {
      if (minStringLength == null || min < minStringLength) {
        minStringLength = min;
      }
    }
    if (max != null) {
      if (maxStringLength == null || max > maxStringLength) {
        maxStringLength = max;
      }
    }
  }

  public void setMinMaxFromSql(Comparable<?> min, Comparable<?> max) {
    if (min != null) {
      updateMinMax(min);
    }
    if (max != null) {
      updateMinMax(max);
    }
  }

  public void addNullCount(long count) {
    if (count > 0) {
      nullCount += count;
    }
  }

  public void addEmptyStringCount(long count) {
    if (count > 0) {
      emptyStringCount += count;
      nonNullCount += count;
      // Empty strings have length 0; bulk path does not know exact min/max across empties only.
      observeStringLength(0);
      // Align with SQL COUNT(DISTINCT): empty is a real distinct value.
      recordEmptyInDistribution(count, Integer.MAX_VALUE);
    }
  }

  private void recordEmptyInDistribution(long count, int maxDistinct) {
    valueCounts.merge("", count, Long::sum);
    if (distinctValues.contains("")) {
      return;
    }
    if (distinctValues.size() < maxDistinct) {
      distinctValues.add("");
    } else {
      distinctTruncated = true;
    }
  }

  public RegexRuleProfile regexProfile(String ruleId) {
    return regexByRuleId.computeIfAbsent(ruleId, id -> new RegexRuleProfile());
  }

  public RegexRuleProfile findRegexProfile(String ruleId) {
    return ruleId == null ? null : regexByRuleId.get(ruleId);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void updateMinMax(Comparable value) {
    if (minValue == null || value.compareTo(minValue) < 0) {
      minValue = value;
    }
    if (maxValue == null || value.compareTo(maxValue) > 0) {
      maxValue = value;
    }
  }

  public long getObservedCount() {
    return nullCount + nonNullCount;
  }

  /** Collector/evaluator side-channel for a single REGEX rule on this field. */
  @Getter
  @Setter
  public static class RegexRuleProfile {
    /** Path used: pushdown | sample | none. */
    private String path;

    /**
     * Mismatch row count from SQL pushdown (rows that failed the pattern); null if pushdown not
     * used.
     */
    private Long mismatchCount;

    /** Sample path: number of distinct values inspected. */
    private long sampleSize;

    /**
     * Sample path: number of failing distinct values (not weighted by row frequency). For
     * valueCounts-weighted row mismatches use {@link #sampleMismatchRows}.
     */
    private long sampleMismatchCount;

    /**
     * When sample was evaluated against valueCounts: total row weight of mismatched values. 0 when
     * only distinct sampling was used.
     */
    private long sampleMismatchRows;

    /** Sample path: limit applied (e.g. 500). */
    private int sampleLimit;

    /** True when sample limit was hit and all samples matched (coverage incomplete). */
    private boolean coverageIncomplete;

    /** True when neither pushdown nor sample could run. */
    private boolean skipped;
  }
}
