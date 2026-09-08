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
package org.hopper.edw.datavault.hopgui.file.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hopper.edw.datavault.metrics.LoadRunDurationRun;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;
import org.junit.jupiter.api.Test;

class LoadRunDurationTableSupportTest {

  @Test
  void buildPivotsTablesAsRowsAndRunsAsColumns() {
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
                Map.of(
                    "hub_customer", new long[] {1_500L, 3_000L},
                    "sat_customer", new long[] {0L, 500L}))
            .build();

    LoadRunDurationTableSupport.Model model = LoadRunDurationTableSupport.build(snapshot);

    assertTrue(model.hasRows());
    assertEquals(2, model.runHeaders().size());
    assertEquals(LoadRunDurationOverviewPainter.formatRunLabel(first), model.runHeaders().get(0));
    assertEquals(LoadRunDurationOverviewPainter.formatRunLabel(second), model.runHeaders().get(1));
    assertEquals(2, model.rows().size());
    assertEquals("hub_customer", model.rows().get(0).tableName());
    assertEquals("1s", model.rows().get(0).cells().get(0).text());
    assertFalse(model.rows().get(0).cells().get(0).failed());
    assertTrue(model.rows().get(0).cells().get(1).failed());
    assertTrue(model.rows().get(0).cells().get(1).text().contains("3s"));
    assertEquals("sat_customer", model.rows().get(1).tableName());
    assertEquals("", model.rows().get(1).cells().get(0).text());
  }

  @Test
  void buildReturnsStatusWhenThereAreNoRuns() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.NO_RUNS)
            .tableNames(List.of("hub_customer"))
            .runs(List.of())
            .build();

    LoadRunDurationTableSupport.Model model = LoadRunDurationTableSupport.build(snapshot);

    assertFalse(model.hasRows());
    assertTrue(model.statusMessage().toLowerCase().contains("no load runs"));
  }

  @Test
  void runHeaderFallsBackToRunId() {
    LoadRunDurationRun run = LoadRunDurationRun.builder().runId("abc-123").success(true).build();
    assertEquals("abc-123", LoadRunDurationTableSupport.runHeader(run, 0));
  }
}
