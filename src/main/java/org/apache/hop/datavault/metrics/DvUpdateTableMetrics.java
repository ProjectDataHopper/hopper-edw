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
package org.apache.hop.datavault.metrics;

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
}
