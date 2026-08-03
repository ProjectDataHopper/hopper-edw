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
package org.apache.hop.datavault.hopgui.file.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.datavault.metrics.LoadRunDurationRun;
import org.apache.hop.datavault.metrics.LoadRunDurationSnapshot;
import org.junit.jupiter.api.Test;

class LoadRunDurationPreviewSupportTest {

  @Test
  void buildPreviewRowsUsesRequestedColumnsAndSeconds() {
    Date first = new Date(1_700_000_000_000L);
    Date second = new Date(1_700_086_400_000L);
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.LOADED)
            .tableNames(List.of("hub_customer", "sat_customer"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("run-1")
                        .finishedAt(first)
                        .success(true)
                        .build(),
                    LoadRunDurationRun.builder()
                        .runId("run-2")
                        .finishedAt(second)
                        .success(false)
                        .build()))
            .durationsByElement(
                java.util.Map.of(
                    "hub_customer", new long[] {1_500L, 3_000L},
                    "sat_customer", new long[] {0L, 500L}))
            .build();

    List<Object[]> rows =
        LoadRunDurationPreviewSupport.buildPreviewRows("retail-f-orders", snapshot);

    assertEquals(3, rows.size());
    assertEquals("retail-f-orders", rows.get(0)[0]);
    assertEquals("hub_customer", rows.get(0)[1]);
    assertEquals(second, rows.get(0)[2]);
    assertEquals(3.0, rows.get(0)[3]);
    assertEquals("sat_customer", rows.get(1)[1]);
    assertEquals(0.5, rows.get(1)[3]);
    assertEquals(first, rows.get(2)[2]);
    assertEquals(1.5, rows.get(2)[3]);
  }

  @Test
  void hasPreviewRowsIsFalseWhenNoDurationsRecorded() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.NO_RUNS)
            .tableNames(List.of("hub_customer"))
            .runs(List.of())
            .build();

    assertFalse(LoadRunDurationPreviewSupport.hasPreviewRows(snapshot));
    assertTrue(LoadRunDurationPreviewSupport.buildPreviewRows("retail-360", snapshot).isEmpty());
  }

  @Test
  void createPreviewRowMetaDefinesExpectedColumns() {
    IRowMeta rowMeta = LoadRunDurationPreviewSupport.createPreviewRowMeta();

    assertEquals(4, rowMeta.size());
    assertEquals(LoadRunDurationPreviewSupport.COL_MODEL_NAME, rowMeta.getValueMeta(0).getName());
    assertEquals(LoadRunDurationPreviewSupport.COL_TABLE_NAME, rowMeta.getValueMeta(1).getName());
    assertEquals(LoadRunDurationPreviewSupport.COL_TIMESTAMP, rowMeta.getValueMeta(2).getName());
    assertEquals(
        LoadRunDurationPreviewSupport.COL_DURATION_SECONDS, rowMeta.getValueMeta(3).getName());
  }
}
