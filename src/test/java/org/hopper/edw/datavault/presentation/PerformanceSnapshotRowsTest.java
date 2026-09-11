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
package org.hopper.edw.datavault.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.pipeline.performance.PerformanceSnapShot;
import org.hopper.edw.datavault.presentation.PerformanceSnapshotRows.Metric;
import org.junit.jupiter.api.Test;

class PerformanceSnapshotRowsTest {

  @Test
  void keyedByTransformCopySuffix() throws Exception {
    Date t0 = new Date(1_000L);
    Date t1 = new Date(2_000L);
    PerformanceSnapShot first = snap("Generator", t0, 0);
    first.setTimeDifference(0);
    PerformanceSnapShot second = snap("Generator", t1, 1);
    second.setTimeDifference(1000);
    second.setLinesRead(20);
    Map<String, List<PerformanceSnapShot>> engineMap = new LinkedHashMap<>();
    engineMap.put("Generator.0", List.of(first, second));
    List<RowMetaAndData> rows =
        PerformanceSnapshotRows.from(
            PerformanceSnapshotRows.keyedByTransformLabel(engineMap), Metric.LINES_READ);
    assertEquals(2, rows.size());
    assertEquals("Generator", rows.get(0).getString(PerformanceSnapshotRows.COL_TRANSFORM, ""));
  }

  @Test
  void emptyInput() {
    assertTrue(
        PerformanceSnapshotRows.from((Map<String, List<PerformanceSnapShot>>) null, null)
            .isEmpty());
    assertTrue(PerformanceSnapshotRows.from(Map.of(), Metric.ROWS_PER_SECOND).isEmpty());
    assertEquals("0", PerformanceSnapshotRows.fingerprint(List.of()));
  }

  @Test
  void throughputUsesIntervalDiff() throws Exception {
    Date t0 = new Date(1_000L);
    Date t1 = new Date(2_000L);
    PerformanceSnapShot first = snap("gen", t0, 0);
    first.setTimeDifference(0);
    first.setLinesRead(0);
    PerformanceSnapShot second = snap("gen", t1, 1);
    second.setTimeDifference(1000);
    second.setLinesRead(50);
    second.setLinesInput(10);

    Map<String, List<PerformanceSnapShot>> data = new LinkedHashMap<>();
    data.put("Generator", List.of(first, second));
    List<RowMetaAndData> rows = PerformanceSnapshotRows.from(data, Metric.ROWS_PER_SECOND);
    assertEquals(2, rows.size());
    assertEquals(0L, rows.get(0).getInteger(PerformanceSnapshotRows.COL_ELAPSED_S, -1));
    assertEquals("Generator", rows.get(0).getString(PerformanceSnapshotRows.COL_TRANSFORM, ""));
    assertEquals(0.0, rows.get(0).getNumber(PerformanceSnapshotRows.COL_VALUE, -1), 0.01);
    assertEquals(1L, rows.get(1).getInteger(PerformanceSnapshotRows.COL_ELAPSED_S, -1));
    assertEquals(50.0, rows.get(1).getNumber(PerformanceSnapshotRows.COL_VALUE, -1), 0.01);
  }

  @Test
  void throughputUsesWrittenForGenerators() {
    PerformanceSnapShot generator = snap("15b rows", new Date(1_000L), 1);
    generator.setTimeDifference(1000);
    generator.setLinesWritten(15_000);
    generator.setLinesRead(0);
    generator.setLinesInput(0);
    assertEquals(
        15_000.0, PerformanceSnapshotRows.metricValue(generator, Metric.ROWS_PER_SECOND), 0.01);

    PerformanceSnapShot dummy = snap("Dummy", new Date(1_000L), 1);
    dummy.setTimeDifference(1000);
    dummy.setLinesRead(15_000);
    dummy.setLinesWritten(0);
    assertEquals(
        15_000.0, PerformanceSnapshotRows.metricValue(dummy, Metric.ROWS_PER_SECOND), 0.01);
  }

  @Test
  void throughputDoesNotSumReadAndInput() {
    PerformanceSnapShot snap = snap("seq", new Date(1_000L), 1);
    snap.setTimeDifference(1000);
    snap.setLinesRead(100);
    snap.setLinesInput(100);
    snap.setLinesWritten(100);
    assertEquals(100.0, PerformanceSnapshotRows.metricValue(snap, Metric.ROWS_PER_SECOND), 0.01);
  }

  @Test
  void capsSeriesAndFoldsOther() throws Exception {
    Map<String, List<PerformanceSnapShot>> data = new LinkedHashMap<>();
    Date t0 = new Date(0);
    for (int i = 0; i < 15; i++) {
      PerformanceSnapShot snap = snap("t" + i, t0, i);
      snap.setTimeDifference(1000);
      snap.setLinesRead(100 - i);
      data.put("T" + i, List.of(snap));
    }
    List<RowMetaAndData> rows = PerformanceSnapshotRows.from(data, Metric.LINES_READ);
    long distinct =
        rows.stream()
            .map(
                r -> {
                  try {
                    return r.getString(PerformanceSnapshotRows.COL_TRANSFORM, "");
                  } catch (Exception e) {
                    return "";
                  }
                })
            .distinct()
            .count();
    assertTrue(distinct <= PerformanceSnapshotRows.MAX_SERIES + 1);
    assertTrue(
        rows.stream()
            .anyMatch(
                r -> {
                  try {
                    return PerformanceSnapshotRows.OTHER_SERIES.equals(
                        r.getString(PerformanceSnapshotRows.COL_TRANSFORM, ""));
                  } catch (Exception e) {
                    return false;
                  }
                }));
  }

  @Test
  void metricSwitch() {
    PerformanceSnapShot snap = snap("x", new Date(0), 0);
    snap.setLinesWritten(7);
    snap.setErrors(2);
    snap.setInputBufferSize(11);
    assertEquals(7.0, PerformanceSnapshotRows.metricValue(snap, Metric.LINES_WRITTEN), 0.01);
    assertEquals(2.0, PerformanceSnapshotRows.metricValue(snap, Metric.ERRORS), 0.01);
    assertEquals(11.0, PerformanceSnapshotRows.metricValue(snap, Metric.INPUT_BUFFER), 0.01);
  }

  private static PerformanceSnapShot snap(String name, Date date, int seq) {
    return new PerformanceSnapShot(seq, date, "pipe", name, 0, 0, 0, 0, 0, 0, 0, 0);
  }
}
