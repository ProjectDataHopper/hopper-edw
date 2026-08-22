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
package org.hopper.edw.datavault.metrics;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * In-memory load duration data for one model, consumed by {@code LoadRunDurationOverviewPainter}.
 */
@Value
@Builder
public class LoadRunDurationSnapshot {

  public enum Status {
    LOADED,
    NO_DATABASE,
    NO_TABLES,
    NO_RUNS,
    ERROR
  }

  @Builder.Default Status status = Status.LOADED;

  String message;

  @Builder.Default List<LoadRunDurationRun> runs = Collections.emptyList();

  @Builder.Default List<String> tableNames = Collections.emptyList();

  /** element name -> duration per run index (aligned with {@link #runs}). */
  @Builder.Default Map<String, long[]> durationsByElement = Collections.emptyMap();

  long maxDurationMs;

  public List<LoadRunDurationRun> getRuns() {
    return runs != null ? runs : Collections.emptyList();
  }

  public List<String> getTableNames() {
    return tableNames != null ? tableNames : Collections.emptyList();
  }

  public Map<String, long[]> getDurationsByElement() {
    return durationsByElement != null ? durationsByElement : Collections.emptyMap();
  }

  public long durationMs(String elementName, int runIndex) {
    if (elementName == null || runIndex < 0) {
      return 0L;
    }
    long[] durations = getDurationsByElement().get(elementName);
    if (durations == null || runIndex >= durations.length) {
      return 0L;
    }
    return durations[runIndex];
  }

  /** Maximum duration among the model tables and runs shown in the overview chart. */
  public long resolveDisplayMaxDurationMs() {
    long max = 0L;
    int runCount = getRuns().size();
    for (String tableName : getTableNames()) {
      for (int runIndex = 0; runIndex < runCount; runIndex++) {
        max = Math.max(max, durationMs(tableName, runIndex));
      }
    }
    return max;
  }
}
