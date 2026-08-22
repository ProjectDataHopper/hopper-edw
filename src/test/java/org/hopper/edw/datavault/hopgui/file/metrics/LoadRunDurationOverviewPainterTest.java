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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metrics.LoadRunDurationRun;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;
import org.junit.jupiter.api.Test;

class LoadRunDurationOverviewPainterTest {

  @Test
  void computePreferredSizeIncludesRunsAndTables() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .tableNames(List.of("hub_customer", "sat_customer"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("run-1")
                        .finishedAt(new Date())
                        .success(true)
                        .build(),
                    LoadRunDurationRun.builder()
                        .runId("run-2")
                        .finishedAt(new Date())
                        .success(true)
                        .build()))
            .build();

    Point size = LoadRunDurationOverviewPainter.computeLogicalPreferredSize(snapshot);
    int expectedWidth =
        LoadRunDurationOverviewPainter.PADDING
            + LoadRunDurationOverviewPainter.LABEL_COLUMN_MIN_WIDTH
            + (2
                * (LoadRunDurationOverviewPainter.RUN_COLUMN_WIDTH
                    + LoadRunDurationOverviewPainter.BAR_GAP))
            + LoadRunDurationOverviewPainter.PADDING;
    int expectedHeight =
        LoadRunDurationOverviewPainter.PADDING
            + LoadRunDurationOverviewPainter.HEADER_HEIGHT
            + (2 * LoadRunDurationOverviewPainter.ROW_HEIGHT)
            + LoadRunDurationOverviewPainter.PADDING;

    assertEquals(expectedWidth, size.x);
    assertEquals(expectedHeight, size.y);

    Point scaled = LoadRunDurationOverviewPainter.computePreferredSize(snapshot, 2.0);
    assertEquals(expectedWidth * 2, scaled.x);
    assertEquals(expectedHeight * 2, scaled.y);

    Point wider = LoadRunDurationOverviewPainter.computeLogicalPreferredSize(snapshot, 220);
    assertEquals(
        LoadRunDurationOverviewPainter.PADDING
            + 220
            + (2
                * (LoadRunDurationOverviewPainter.RUN_COLUMN_WIDTH
                    + LoadRunDurationOverviewPainter.BAR_GAP))
            + LoadRunDurationOverviewPainter.PADDING,
        wider.x);
  }

  @Test
  void computeBarHeightScalesAgainstMaxDuration() {
    int barAreaHeight =
        LoadRunDurationOverviewPainter.rowBarAreaHeight(
            LoadRunDurationOverviewPainter.ROW_HEIGHT,
            LoadRunDurationOverviewPainter.ROW_BAR_MARGIN);
    assertEquals(0, LoadRunDurationOverviewPainter.computeBarHeight(0L, 10_000L, barAreaHeight));
    assertEquals(
        barAreaHeight,
        LoadRunDurationOverviewPainter.computeBarHeight(10_000L, 10_000L, barAreaHeight));
    assertEquals(
        barAreaHeight / 2,
        LoadRunDurationOverviewPainter.computeBarHeight(5_000L, 10_000L, barAreaHeight));
  }

  @Test
  void resolveDisplayMaxDurationUsesOnlyDisplayedTables() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .tableNames(List.of("hub_customer"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("run-1")
                        .finishedAt(new Date())
                        .success(true)
                        .build()))
            .durationsByElement(
                Map.of("hub_customer", new long[] {1_000L}, "other_table", new long[] {9_000L}))
            .maxDurationMs(9_000L)
            .build();

    assertEquals(1_000L, snapshot.resolveDisplayMaxDurationMs());
  }

  @Test
  void formatDurationUsesReadableUnits() {
    assertEquals("45s", LoadRunDurationOverviewPainter.formatDuration(45_000L));
    assertEquals("2m 5s", LoadRunDurationOverviewPainter.formatDuration(125_000L));
    assertEquals("1h 0m 1s", LoadRunDurationOverviewPainter.formatDuration(3_601_000L));
  }

  @Test
  void formatRunLabelUsesShortTimestamp() {
    Date finishedAt = new Date(1_704_067_200_000L);
    String label = LoadRunDurationOverviewPainter.formatRunLabel(finishedAt);
    assertTrue(label.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}"));
  }

  @Test
  void snapshotDurationLookupIsRunAligned() {
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .tableNames(List.of("hub_customer"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("run-1")
                        .finishedAt(new Date())
                        .success(true)
                        .build()))
            .durationsByElement(Map.of("hub_customer", new long[] {42L}))
            .maxDurationMs(42L)
            .build();

    assertEquals(42L, snapshot.durationMs("hub_customer", 0));
    assertEquals(0L, snapshot.durationMs("missing", 0));
  }
}
