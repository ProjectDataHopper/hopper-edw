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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadRunDurationMetricsLoaderTest {

  @Test
  void assembleSnapshotAlignsDurationsWithRuns() {
    Date first = new Date(1_700_000_000_000L);
    Date second = new Date(1_700_086_400_000L);
    List<LoadRunDurationRun> runs =
        List.of(
            LoadRunDurationRun.builder().runId("run-1").finishedAt(first).success(true).build(),
            LoadRunDurationRun.builder().runId("run-2").finishedAt(second).success(false).build());

    List<LoadRunDurationMetricsLoader.DurationMetricRow> metrics =
        List.of(
            new LoadRunDurationMetricsLoader.DurationMetricRow("run-1", "hub_customer", 1_500L),
            new LoadRunDurationMetricsLoader.DurationMetricRow("run-2", "hub_customer", 3_000L),
            new LoadRunDurationMetricsLoader.DurationMetricRow("run-2", "sat_customer", 500L));

    LoadRunDurationSnapshot snapshot =
        LoadRunDurationMetricsLoader.assembleSnapshot(
            List.of("hub_customer", "sat_customer"), runs, metrics);

    assertEquals(LoadRunDurationSnapshot.Status.LOADED, snapshot.getStatus());
    assertEquals(2, snapshot.getRuns().size());
    assertEquals(3_000L, snapshot.getMaxDurationMs());
    assertEquals(3_000L, snapshot.resolveDisplayMaxDurationMs());
    assertEquals(1_500L, snapshot.durationMs("hub_customer", 0));
    assertEquals(3_000L, snapshot.durationMs("hub_customer", 1));
    assertEquals(500L, snapshot.durationMs("sat_customer", 1));
    assertEquals(0L, snapshot.durationMs("sat_customer", 0));
  }

  @Test
  void assembleSnapshotIgnoresMetricsForTablesNotInModel() {
    List<LoadRunDurationRun> runs =
        List.of(
            LoadRunDurationRun.builder()
                .runId("run-1")
                .finishedAt(new Date())
                .success(true)
                .build());
    List<LoadRunDurationMetricsLoader.DurationMetricRow> metrics =
        List.of(
            new LoadRunDurationMetricsLoader.DurationMetricRow("run-1", "hub_customer", 2_000L),
            new LoadRunDurationMetricsLoader.DurationMetricRow("run-1", "orphan_table", 99_000L));

    LoadRunDurationSnapshot snapshot =
        LoadRunDurationMetricsLoader.assembleSnapshot(List.of("hub_customer"), runs, metrics);

    assertEquals(2_000L, snapshot.durationMs("hub_customer", 0));
    assertEquals(0L, snapshot.durationMs("orphan_table", 0));
    assertEquals(2_000L, snapshot.getMaxDurationMs());
  }

  @Test
  void assembleSnapshotReportsNoRuns() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationMetricsLoader.assembleSnapshot(
            List.of("hub_customer"), List.of(), List.of());

    assertEquals(LoadRunDurationSnapshot.Status.NO_RUNS, snapshot.getStatus());
    assertTrue(snapshot.getRuns().isEmpty());
  }

  @Test
  void buildRunIdInClauseQuotesRunIds() {
    List<LoadRunDurationRun> runs =
        List.of(
            LoadRunDurationRun.builder()
                .runId("run'a")
                .finishedAt(new Date())
                .success(true)
                .build());
    assertEquals("'run''a'", LoadRunDurationMetricsLoader.buildRunIdInClause(null, runs));
  }
}
