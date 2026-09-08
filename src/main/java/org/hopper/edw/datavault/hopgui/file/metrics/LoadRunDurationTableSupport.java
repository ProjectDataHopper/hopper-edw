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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metrics.LoadRunDurationRun;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;

/** Pivot of load durations for the Hop Web metrics panel: one row per table, one column per run. */
public final class LoadRunDurationTableSupport {

  private static final Class<?> PKG = LoadRunDurationTableSupport.class;

  private LoadRunDurationTableSupport() {}

  public record Cell(String text, boolean failed) {}

  public record Row(String tableName, List<Cell> cells) {}

  public record Model(List<String> runHeaders, List<Row> rows, String statusMessage) {

    public boolean hasRows() {
      return rows != null && !rows.isEmpty();
    }
  }

  public static Model build(LoadRunDurationSnapshot snapshot) {
    if (snapshot == null) {
      return new Model(
          List.of(),
          List.of(),
          BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns"));
    }
    if (snapshot.getStatus() != LoadRunDurationSnapshot.Status.LOADED) {
      return new Model(List.of(), List.of(), statusMessage(snapshot));
    }
    List<LoadRunDurationRun> runs = snapshot.getRuns();
    List<String> tableNames = snapshot.getTableNames();
    if (runs.isEmpty()) {
      return new Model(
          List.of(),
          List.of(),
          BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns"));
    }
    if (tableNames.isEmpty()) {
      return new Model(
          List.of(),
          List.of(),
          BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoTables"));
    }

    List<String> headers = new ArrayList<>(runs.size());
    for (int i = 0; i < runs.size(); i++) {
      headers.add(runHeader(runs.get(i), i));
    }

    List<Row> rows = new ArrayList<>(tableNames.size());
    for (String tableName : tableNames) {
      List<Cell> cells = new ArrayList<>(runs.size());
      for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
        long durationMs = snapshot.durationMs(tableName, runIndex);
        LoadRunDurationRun run = runs.get(runIndex);
        cells.add(cell(durationMs, run));
      }
      rows.add(new Row(Const.NVL(tableName, ""), List.copyOf(cells)));
    }
    return new Model(List.copyOf(headers), List.copyOf(rows), null);
  }

  static String statusMessage(LoadRunDurationSnapshot snapshot) {
    if (snapshot == null) {
      return BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns");
    }
    return switch (snapshot.getStatus()) {
      case NO_DATABASE -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoDatabase");
      case NO_TABLES -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoTables");
      case NO_RUNS -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns");
      case ERROR ->
          BaseMessages.getString(
              PKG,
              "LoadRunDurationOverviewPainter.Error",
              Utils.isEmpty(snapshot.getMessage()) ? "unknown" : snapshot.getMessage());
      default -> Const.NVL(snapshot.getMessage(), "");
    };
  }

  static String runHeader(LoadRunDurationRun run, int runIndex) {
    if (run != null) {
      String label = LoadRunDurationOverviewPainter.formatRunLabel(run.getFinishedAt());
      if (!Utils.isEmpty(label)) {
        return label;
      }
      if (!Utils.isEmpty(run.getRunId())) {
        return run.getRunId();
      }
    }
    return BaseMessages.getString(
        PKG, "LoadRunDurationTableSupport.Run", Integer.toString(runIndex + 1));
  }

  static Cell cell(long durationMs, LoadRunDurationRun run) {
    if (durationMs <= 0L) {
      return new Cell("", false);
    }
    String text = LoadRunDurationOverviewPainter.formatDuration(durationMs);
    boolean failed = run != null && !run.isSuccess();
    if (failed) {
      text = BaseMessages.getString(PKG, "LoadRunDurationTableSupport.Failed", text);
    }
    return new Cell(text, failed);
  }
}
