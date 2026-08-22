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
package org.apache.hop.datavault.metrics.live;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateRunLiveClassifierTest {

  @Test
  void classifiesSlowTableInput() {
    TransformLiveMetrics transform =
        TransformLiveMetrics.builder()
            .transformName("source customer")
            .pluginId("TableInput")
            .rowsRead(0L)
            .rowsWritten(0L)
            .bufferIn(0L)
            .bufferOut(0L)
            .running(true)
            .secondsSinceLastProgress(75L)
            .build();

    String message = UpdateRunLiveClassifier.classifyMessage(transform);
    assertTrue(message.contains("Table Input"));
  }

  @Test
  void classifiesMergeWaitingForInput() {
    TransformLiveMetrics transform =
        TransformLiveMetrics.builder()
            .transformName("merge rows")
            .pluginId("MergeRowsPlus")
            .rowsRead(0L)
            .rowsWritten(0L)
            .bufferIn(500L)
            .bufferOut(0L)
            .running(true)
            .secondsSinceLastProgress(90L)
            .build();

    String message = UpdateRunLiveClassifier.classifyMessage(transform);
    assertTrue(message.toLowerCase().contains("input stream"));
  }
}
