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

import lombok.Value;

/** Aggregated load-run metrics plus publish metadata for workflow action results. */
@Value
public class LoadRunPublishSummary {

  public static final LoadRunPublishSummary EMPTY =
      new LoadRunPublishSummary(DvUpdateRunTotals.EMPTY, null, 0, 0);

  DvUpdateRunTotals totals;
  String runId;
  int pipelineCount;
  int insightCount;

  public boolean hasMetrics() {
    return pipelineCount > 0;
  }
}
