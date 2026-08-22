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
package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.Result;
import org.junit.jupiter.api.Test;

class DvUpdateMetricsCollectorTest {

  @Test
  void applyToResultMarksFailureWhenMetricsReportErrors() {
    Result result = new Result();
    result.setResult(true);

    DvUpdateMetricsCollector.applyToResult(result, new DvUpdateRunTotals(10, 5, 2));

    assertEquals(2, result.getNrErrors());
    assertFalse(result.getResult());
    assertEquals(10, result.getNrLinesInput());
    assertEquals(5, result.getNrLinesOutput());
  }

  @Test
  void applyToResultLeavesSuccessfulResultUntouched() {
    Result result = new Result();
    result.setResult(true);

    DvUpdateMetricsCollector.applyToResult(result, DvUpdateRunTotals.EMPTY);

    assertEquals(0, result.getNrErrors());
    assertTrue(result.getResult());
  }

  @Test
  void applyToResultSummarySetsLogText() {
    Result result = new Result();
    result.setResult(true);

    DvUpdateMetricsCollector.applyToResult(
        result, new LoadRunPublishSummary(new DvUpdateRunTotals(1000, 250, 0), "run-123", 4, 2));

    assertEquals(1000, result.getNrLinesInput());
    assertEquals(250, result.getNrLinesOutput());
    assertTrue(result.getLogText().contains("run-123"));
    assertTrue(result.getLogText().contains("pipelines=4"));
    assertTrue(result.getLogText().contains("insights=2"));
  }
}
