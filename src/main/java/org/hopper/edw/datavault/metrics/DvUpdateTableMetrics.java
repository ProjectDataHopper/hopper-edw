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
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Row-level metrics for one generated Data Vault update pipeline (one table, one source). */
@Value
@Builder
public class DvUpdateTableMetrics {
  String runId;
  String modelName;
  String modelType;
  String pipelineName;
  String tableType;
  String tableName;
  String sourceName;
  long sourceRowsRead;
  long targetRowsRead;
  long targetRowsInserted;
  long errors;
  boolean success;

  /** Pipeline wall-clock start from the Hop engine (may be null). */
  Date executionStartDate;

  /** Pipeline wall-clock end from the Hop engine (may be null). */
  Date executionEndDate;

  /** Wall-clock duration in ms (end − start when both dates are present). */
  long durationMs;

  @Singular("transform")
  List<TransformRunMetrics> transforms;

  public List<TransformRunMetrics> getTransforms() {
    return transforms != null ? transforms : Collections.emptyList();
  }

  /**
   * Wall-clock duration from pipeline execution start/end dates. Returns 0 when either date is
   * missing or end is before start.
   */
  public static long resolveDurationMs(Date executionStartDate, Date executionEndDate) {
    if (executionStartDate == null || executionEndDate == null) {
      return 0L;
    }
    long duration = executionEndDate.getTime() - executionStartDate.getTime();
    return Math.max(0L, duration);
  }

  /**
   * Wall-clock duration when both timestamps are present and ordered; otherwise {@code fallbackMs}.
   * Used so model/run duration is elapsed time rather than the sum of overlapping transform
   * durations (fact pipelines with parallel dimension lookups).
   */
  public static long resolveDurationMs(
      Date executionStartDate, Date executionEndDate, long fallbackMs) {
    long wallClock = resolveDurationMs(executionStartDate, executionEndDate);
    return wallClock > 0L ? wallClock : Math.max(0L, fallbackMs);
  }

  /**
   * Sum of per-pipeline wall-clock durations. Does not add overlapping transform times: each
   * pipeline already reports end − start (or {@link #getDurationMs()}).
   */
  public static long sumPipelineWallClockMs(List<DvUpdateTableMetrics> pipelines) {
    if (pipelines == null || pipelines.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null) {
        continue;
      }
      long wallClock = pipeline.getDurationMs();
      if (wallClock <= 0L) {
        wallClock =
            resolveDurationMs(pipeline.getExecutionStartDate(), pipeline.getExecutionEndDate());
      }
      total += Math.max(0L, wallClock);
    }
    return total;
  }
}
