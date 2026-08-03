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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metrics.LoadRunDurationRun;
import org.apache.hop.datavault.metrics.LoadRunDurationSnapshot;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;

/** Airflow-style duration bars for recent load runs, one row per model table. */
public class LoadRunDurationOverviewPainter extends BasePainter {

  private static final Class<?> PKG = LoadRunDurationOverviewPainter.class;

  public static final int LABEL_COLUMN_MIN_WIDTH = 140;
  public static final int LABEL_TEXT_PADDING = 12;
  public static final int RUN_COLUMN_WIDTH = 18;
  public static final int BAR_GAP = 4;
  public static final int ROW_HEIGHT = 66;
  public static final int ROW_BAR_MARGIN = 6;
  public static final int HEADER_HEIGHT = 28;
  public static final int PADDING = 8;
  public static final int MIN_BAR_HEIGHT = 2;

  private final LoadRunDurationSnapshot snapshot;
  private final List<DurationBarHit> barHits = new ArrayList<>();
  private int labelColumnWidth = LABEL_COLUMN_MIN_WIDTH;

  public LoadRunDurationOverviewPainter(
      LoadRunDurationSnapshot snapshot, IGc gc, IVariables variables, int width, int height) {
    this(snapshot, gc, variables, width, height, PropsUi.getNativeZoomFactor());
  }

  public LoadRunDurationOverviewPainter(
      LoadRunDurationSnapshot snapshot,
      IGc gc,
      IVariables variables,
      int width,
      int height,
      double nativeZoomFactor) {
    super(gc, variables, snapshot, new Point(width, height));
    this.snapshot = snapshot != null ? snapshot : LoadRunDurationSnapshot.builder().build();
    setMagnification((float) (nativeZoomFactor > 0 ? nativeZoomFactor : 1.0));
  }

  public List<DurationBarHit> getBarHits() {
    return barHits;
  }

  public static Point computePreferredSize(LoadRunDurationSnapshot snapshot) {
    return computePreferredSize(snapshot, null, PropsUi.getNativeZoomFactor());
  }

  public static Point computePreferredSize(
      LoadRunDurationSnapshot snapshot, IGc gc, double nativeZoomFactor) {
    int labelColumnWidth =
        gc != null ? resolveLabelColumnWidth(snapshot, gc) : LABEL_COLUMN_MIN_WIDTH;
    Point logical = computeLogicalPreferredSize(snapshot, labelColumnWidth);
    return new Point(scale(logical.x, nativeZoomFactor), scale(logical.y, nativeZoomFactor));
  }

  public static Point computePreferredSize(
      LoadRunDurationSnapshot snapshot, double nativeZoomFactor) {
    return computePreferredSize(snapshot, null, nativeZoomFactor);
  }

  public static Point computeLogicalPreferredSize(LoadRunDurationSnapshot snapshot) {
    return computeLogicalPreferredSize(snapshot, LABEL_COLUMN_MIN_WIDTH);
  }

  public static Point computeLogicalPreferredSize(
      LoadRunDurationSnapshot snapshot, int labelColumnWidth) {
    LoadRunDurationSnapshot data =
        snapshot != null ? snapshot : LoadRunDurationSnapshot.builder().build();
    int runCount = Math.max(1, data.getRuns().size());
    int rowCount = Math.max(1, data.getTableNames().size());
    int resolvedLabelWidth = Math.max(LABEL_COLUMN_MIN_WIDTH, labelColumnWidth);
    int width = PADDING + resolvedLabelWidth + (runCount * (RUN_COLUMN_WIDTH + BAR_GAP)) + PADDING;
    int height = PADDING + HEADER_HEIGHT + (rowCount * ROW_HEIGHT) + PADDING;
    return new Point(width, height);
  }

  public static int resolveLabelColumnWidth(LoadRunDurationSnapshot snapshot, IGc gc) {
    if (gc == null) {
      return LABEL_COLUMN_MIN_WIDTH;
    }
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setFont(EFont.GRAPH);
    LoadRunDurationSnapshot data =
        snapshot != null ? snapshot : LoadRunDurationSnapshot.builder().build();
    int maxWidth = LABEL_COLUMN_MIN_WIDTH;
    for (String tableName : data.getTableNames()) {
      if (Utils.isEmpty(tableName)) {
        continue;
      }
      maxWidth = Math.max(maxWidth, gc.textExtent(tableName).x + LABEL_TEXT_PADDING);
    }
    return maxWidth;
  }

  public static int scale(int value, double nativeZoomFactor) {
    float zoom = (float) (nativeZoomFactor > 0 ? nativeZoomFactor : 1.0);
    return Math.max(1, Math.round(value * zoom));
  }

  public static int rowBarAreaHeight(int rowHeight, int rowBarMargin) {
    return Math.max(1, rowHeight - (2 * rowBarMargin));
  }

  public static int computeBarHeight(long durationMs, long maxDurationMs, int barAreaHeight) {
    if (durationMs <= 0L || barAreaHeight <= 0) {
      return 0;
    }
    long scaleMax = Math.max(maxDurationMs, 1L);
    int scaled = (int) Math.round((durationMs * (double) barAreaHeight) / scaleMax);
    return Math.max(minBarHeight(barAreaHeight), scaled);
  }

  private int rowBarAreaHeight() {
    return rowBarAreaHeight(ROW_HEIGHT, ROW_BAR_MARGIN);
  }

  private long displayMaxDurationMs() {
    long displayMax = snapshot.resolveDisplayMaxDurationMs();
    return displayMax > 0L ? displayMax : snapshot.getMaxDurationMs();
  }

  private int computeBarHeight(long durationMs) {
    return computeBarHeight(durationMs, displayMaxDurationMs(), rowBarAreaHeight());
  }

  private static int minBarHeight(int barAreaHeight) {
    if (barAreaHeight <= 0) {
      return MIN_BAR_HEIGHT;
    }
    return Math.max(
        1,
        Math.round(
            MIN_BAR_HEIGHT * barAreaHeight / (float) rowBarAreaHeight(ROW_HEIGHT, ROW_BAR_MARGIN)));
  }

  public void drawOverview() {
    if (gc == null) {
      return;
    }
    barHits.clear();
    fillBackground(area.x, area.y);
    labelColumnWidth = resolveLabelColumnWidth(snapshot, gc);

    beginContentDraw();
    try {
      if (snapshot.getStatus() != LoadRunDurationSnapshot.Status.LOADED) {
        drawStatusMessage(resolveStatusMessage(snapshot));
        return;
      }
      if (snapshot.getRuns().isEmpty()) {
        drawStatusMessage(BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns"));
        return;
      }

      drawHeader();
      drawRows();
    } finally {
      endContentDraw();
    }
  }

  public void drawLoadingMessage(String message) {
    if (gc == null || Utils.isEmpty(message)) {
      return;
    }
    fillBackground(area.x, area.y);
    beginContentDraw();
    try {
      gc.setFont(EFont.GRAPH);
      gc.setForeground(EColor.DARKGRAY);
      gc.drawText(message, PADDING, PADDING + 8, true);
    } finally {
      endContentDraw();
    }
  }

  public DurationBarHit findBarHit(int screenX, int screenY) {
    float mag = magnification > 0 ? magnification : 1.0f;
    int x = Math.round(screenX / mag);
    int y = Math.round(screenY / mag);
    for (DurationBarHit hit : barHits) {
      if (hit.contains(x, y)) {
        return hit;
      }
    }
    return null;
  }

  public static String formatDuration(long durationMs) {
    if (durationMs <= 0L) {
      return "0s";
    }
    long totalSeconds = durationMs / 1000L;
    long hours = totalSeconds / 3600L;
    long minutes = (totalSeconds % 3600L) / 60L;
    long seconds = totalSeconds % 60L;
    if (hours > 0L) {
      return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
    }
    if (minutes > 0L) {
      return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
    }
    return String.format(Locale.ROOT, "%ds", seconds);
  }

  public static String formatRunLabel(Date finishedAt) {
    if (finishedAt == null) {
      return "";
    }
    return new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT).format(finishedAt);
  }

  private void beginContentDraw() {
    gc.setTransform(0.0f, 0.0f, magnification > 0 ? magnification : 1.0f);
  }

  private void endContentDraw() {
    gc.setTransform(0.0f, 0.0f, 1.0f);
  }

  private void fillBackground(int width, int height) {
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(IGc.EColor.BACKGROUND);
    gc.fillRectangle(0, 0, width, height);
  }

  private int chartLeft() {
    return PADDING + labelColumnWidth;
  }

  private void drawHeader() {
    long displayMax = displayMaxDurationMs();
    if (displayMax <= 0L) {
      return;
    }
    String label =
        BaseMessages.getString(
            PKG, "LoadRunDurationOverviewPainter.MaxDuration", formatDuration(displayMax));
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(label, chartLeft(), PADDING, true);
  }

  private void drawRows() {
    int chartLeft = chartLeft();
    int rowTop = PADDING + HEADER_HEIGHT;
    List<String> tableNames = snapshot.getTableNames();
    List<LoadRunDurationRun> runs = snapshot.getRuns();

    int runPitch = RUN_COLUMN_WIDTH + BAR_GAP;
    for (int rowIndex = 0; rowIndex < tableNames.size(); rowIndex++) {
      String tableName = tableNames.get(rowIndex);
      int y = rowTop + rowIndex * ROW_HEIGHT;
      drawTableLabel(tableName, y);

      for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
        int x = chartLeft + runIndex * runPitch;
        long durationMs = snapshot.durationMs(tableName, runIndex);
        LoadRunDurationRun run = runs.get(runIndex);
        drawDurationBar(tableName, run, runIndex, x, y, durationMs);
      }
    }
  }

  private void drawTableLabel(String tableName, int y) {
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.BLACK);
    int textY = y + Math.max(ROW_BAR_MARGIN, (ROW_HEIGHT - 12) / 2);
    gc.drawText(Utils.isEmpty(tableName) ? "" : tableName, PADDING, textY, true);
  }

  private void drawDurationBar(
      String tableName, LoadRunDurationRun run, int runIndex, int x, int y, long durationMs) {
    int baselineY = y + ROW_HEIGHT - ROW_BAR_MARGIN;

    if (durationMs <= 0L) {
      gc.setForeground(EColor.LIGHTGRAY);
      gc.drawLine(x, baselineY, x + RUN_COLUMN_WIDTH, baselineY);
      return;
    }

    int barHeight = computeBarHeight(durationMs);
    int barTop = baselineY - barHeight;
    Rectangle barRect = new Rectangle(x, barTop, RUN_COLUMN_WIDTH, barHeight);
    barHits.add(new DurationBarHit(barRect, tableName, run, runIndex, durationMs));

    if (run.isSuccess()) {
      gc.setBackground(EColor.GREEN);
    } else {
      gc.setBackground(EColor.RED);
    }
    gc.fillRectangle(barRect.x, barRect.y, barRect.width, barRect.height);
  }

  private void drawStatusMessage(String message) {
    if (Utils.isEmpty(message)) {
      return;
    }
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(message, PADDING, PADDING + 8, true);
  }

  private String resolveStatusMessage(LoadRunDurationSnapshot data) {
    return switch (data.getStatus()) {
      case NO_DATABASE -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoDatabase");
      case NO_TABLES -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoTables");
      case NO_RUNS -> BaseMessages.getString(PKG, "LoadRunDurationOverviewPainter.NoRuns");
      case ERROR ->
          BaseMessages.getString(
              PKG,
              "LoadRunDurationOverviewPainter.Error",
              Utils.isEmpty(data.getMessage()) ? "unknown" : data.getMessage());
      default -> "";
    };
  }

  /** Logical-coordinate hit target for tooltip display. */
  public record DurationBarHit(
      Rectangle bounds, String tableName, LoadRunDurationRun run, int runIndex, long durationMs) {

    public boolean contains(int x, int y) {
      return bounds != null && bounds.contains(x, y);
    }
  }
}
