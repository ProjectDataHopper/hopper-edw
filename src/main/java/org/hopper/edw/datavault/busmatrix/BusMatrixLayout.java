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
package org.hopper.edw.datavault.busmatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.apache.hop.core.Const;

/** Pixel layout for SVG and the GUI canvas. */
public final class BusMatrixLayout {

  public static final String[] FROZEN_HEADERS = {
    "Business", "Level 1", "Level 2", "Level 3", "Fact"
  };
  public static final int FROZEN_COLUMNS = FROZEN_HEADERS.length;
  public static final int MIN_FROZEN_WIDTH = 48;
  public static final int FROZEN_COLUMN_MARGIN = 20;
  public static final int MIN_CELL_WIDTH = 52;
  public static final int MAX_CELL_WIDTH = 64;
  public static final int MIN_HEADER_HEIGHT = 200;
  public static final int MAX_HEADER_HEIGHT = 360;
  public static final int MIN_ROW_HEIGHT = 24;
  public static final int CELL_PAD = 8;
  public static final int HEADER_PAD = 28;

  /** Gap between frozen header titles and the first data row. */
  public static final int FROZEN_HEADER_LIFT = 22;

  public static final int FROZEN_HEADER_BAND = 28;

  /** Counter-clockwise tilt for dimension names (spreadsheet-style). */
  public static final float HEADER_TILT_DEGREES = -45f;

  private static final double HEADER_TILT_RADIANS = Math.toRadians(45);

  /** Fallback used by headless SVG when no font metrics are available. */
  public static final BusMatrixLayout DEFAULT =
      new BusMatrixLayout(new int[] {128, 128, 140, 140, 168}, 52, 220, 26);

  private final int[] frozenWidths;
  private final int cellWidth;
  private final int headerHeight;
  private final int rowHeight;
  private final int frozenWidth;

  public BusMatrixLayout(int[] frozenWidths, int cellWidth, int headerHeight, int rowHeight) {
    this.frozenWidths = frozenWidths.clone();
    this.cellWidth = cellWidth;
    this.headerHeight = headerHeight;
    this.rowHeight = rowHeight;
    int sum = 0;
    for (int width : this.frozenWidths) {
      sum += width;
    }
    this.frozenWidth = sum;
  }

  public static BusMatrixLayout measure(
      BusMatrix matrix, ToIntFunction<String> textWidth, int lineHeight) {
    BusMatrix safe = matrix != null ? matrix : new BusMatrix("", null, null, null);
    int[] widths = new int[FROZEN_COLUMNS];
    for (int i = 0; i < FROZEN_COLUMNS; i++) {
      int width = textWidth.applyAsInt(FROZEN_HEADERS[i]);
      for (BusMatrixRow row : safe.getRows()) {
        width = Math.max(width, textWidth.applyAsInt(frozenValue(row, i)));
      }
      widths[i] = Math.max(MIN_FROZEN_WIDTH, width + FROZEN_COLUMN_MARGIN);
    }
    int maxLabel = 0;
    for (BusMatrixColumn column : safe.getColumns()) {
      maxLabel = Math.max(maxLabel, textWidth.applyAsInt(Const.NVL(column.label(), "")));
    }
    int cell = MIN_CELL_WIDTH;
    int row = Math.max(MIN_ROW_HEIGHT, lineHeight + 10);
    int header = rotatedHeaderHeight(maxLabel, lineHeight);
    return new BusMatrixLayout(widths, cell, header, row);
  }

  /** Header-band height for labels tilted {@link #HEADER_TILT_DEGREES}. */
  public static int rotatedHeaderHeight(int textWidth, int lineHeight) {
    int rise =
        (int)
            Math.ceil(
                Math.max(0, textWidth) * Math.sin(HEADER_TILT_RADIANS)
                    + Math.max(lineHeight, 1) * Math.cos(HEADER_TILT_RADIANS));
    return clamp(rise + HEADER_PAD, MIN_HEADER_HEIGHT, MAX_HEADER_HEIGHT);
  }

  /** Longest unrotated label that still fits in a tilted header band. */
  public static int maxLabelPxForHeader(int headerHeight, int lineHeight) {
    double usable =
        Math.max(0, headerHeight - HEADER_PAD)
            - Math.max(lineHeight, 1) * Math.cos(HEADER_TILT_RADIANS);
    return Math.max(8, (int) Math.floor(usable / Math.sin(HEADER_TILT_RADIANS)));
  }

  /**
   * Horizontal shift of a 45° header edge from the bottom of the band to {@code y} (0 = top).
   * Because the tilt is 45°, the shift equals remaining height.
   */
  public int headerShear(int y) {
    return Math.max(0, headerHeight - y);
  }

  public int headerShear() {
    return headerHeight;
  }

  /**
   * Offset along the 45° edge so the label sits in the parallelogram instead of on the frozen
   * boundary or only at the bottom-left.
   */
  public static int headerLabelInset(int headerHeight, int textWidth) {
    double diagonal = Math.max(1, headerHeight) / Math.sin(HEADER_TILT_RADIANS);
    int inset = (int) Math.round((diagonal - Math.max(0, textWidth)) / 2.0);
    return Math.max(12, inset);
  }

  /** Horizontal center of a 45° header parallelogram whose bottom-left is {@code x}. */
  public static float headerLabelCenterX(int x, int cellWidth, int headerHeight) {
    return x + cellWidth / 2f + headerHeight / 2f;
  }

  /** Vertical center of the header band. */
  public static float headerLabelCenterY(int y, int headerHeight) {
    return y + headerHeight / 2f;
  }

  /**
   * Trapezium (parallelogram) around a tilted dimension name: bottom sits on the data column, left
   * and right edges run 45° counter-clockwise (up and right).
   *
   * @return eight values {@code x1,y1,...,x4,y4} (BL, BR, TR, TL)
   */
  public static int[] headerTrapezium(int x, int y, int cellWidth, int headerHeight) {
    int shear = Math.max(0, headerHeight);
    int w = Math.max(1, cellWidth);
    int bottom = y + headerHeight;
    return new int[] {x, bottom, x + w, bottom, x + w + shear, y, x + shear, y};
  }

  public int[] headerTrapezium(int columnIndex, int offsetX, int offsetY) {
    return headerTrapezium(cellX(columnIndex) + offsetX, offsetY, cellWidth, headerHeight);
  }

  /** Column under a header-band point, accounting for the 45° shear. */
  public int headerColumnAt(int x, int y, int scrollX, int columnCount) {
    if (y < 0 || y >= headerHeight || x < frozenWidth || columnCount <= 0) {
      return -1;
    }
    int local = x + scrollX - frozenWidth - headerShear(y);
    if (local < 0) {
      return -1;
    }
    int col = local / Math.max(1, cellWidth);
    return col < columnCount ? col : -1;
  }

  public int frozenWidth() {
    return frozenWidth;
  }

  public int cellWidth() {
    return cellWidth;
  }

  public int headerHeight() {
    return headerHeight;
  }

  public int rowHeight() {
    return rowHeight;
  }

  public int labelX(int labelIndex) {
    int x = 0;
    int last = Math.min(labelIndex, frozenWidths.length);
    for (int i = 0; i < last; i++) {
      x += frozenWidths[i];
    }
    return x;
  }

  public int labelWidth(int labelIndex) {
    if (labelIndex < 0 || labelIndex >= frozenWidths.length) {
      return MIN_FROZEN_WIDTH;
    }
    return frozenWidths[labelIndex];
  }

  public int cellX(int columnIndex) {
    return frozenWidth + columnIndex * cellWidth;
  }

  public int cellY(int rowIndex) {
    return headerHeight + rowIndex * rowHeight;
  }

  public int width(BusMatrix matrix) {
    int cols = matrix != null ? matrix.getColumns().size() : 0;
    return frozenWidth + cols * cellWidth + headerShear() + 1;
  }

  public int height(BusMatrix matrix) {
    int rows = matrix != null ? matrix.getRows().size() : 0;
    return headerHeight + rows * rowHeight + 1;
  }

  public static String frozenValue(BusMatrixRow row, int index) {
    if (row == null) {
      return "";
    }
    return switch (index) {
      case 0 -> row.business();
      case 1 -> row.level1();
      case 2 -> row.level2();
      case 3 -> row.level3();
      default -> row.factName();
    };
  }

  public static List<String> wrapHeader(String text, int maxPx, ToIntFunction<String> textWidth) {
    String value = Const.NVL(text, "");
    List<String> lines = new ArrayList<>();
    if (value.isEmpty()) {
      return lines;
    }
    if (maxPx <= 0 || textWidth.applyAsInt(value) <= maxPx) {
      lines.add(value);
      return lines;
    }
    StringBuilder current = new StringBuilder();
    String[] parts = value.split("_", -1);
    for (int i = 0; i < parts.length; i++) {
      String piece = i == 0 ? parts[i] : "_" + parts[i];
      if (current.length() > 0 && textWidth.applyAsInt(current + piece) > maxPx) {
        lines.add(ellipsize(current.toString(), maxPx, textWidth));
        current = new StringBuilder(i == 0 ? parts[i] : parts[i]);
      } else {
        current.append(piece);
      }
    }
    if (current.length() > 0) {
      lines.add(ellipsize(current.toString(), maxPx, textWidth));
    }
    if (lines.isEmpty()) {
      lines.add(ellipsize(value, maxPx, textWidth));
    }
    return lines;
  }

  public static boolean startsGroup(BusMatrixRow previous, BusMatrixRow row) {
    if (row == null) {
      return false;
    }
    if (previous == null) {
      return true;
    }
    return !previous.business().equals(row.business()) || !previous.level1().equals(row.level1());
  }

  public static String ellipsize(String text, int maxPx, ToIntFunction<String> textWidth) {
    String value = Const.NVL(text, "");
    if (maxPx <= 0 || textWidth.applyAsInt(value) <= maxPx) {
      return value;
    }
    String ellipsis = "…";
    int ellipsisWidth = textWidth.applyAsInt(ellipsis);
    if (maxPx <= ellipsisWidth) {
      return ellipsis;
    }
    for (int i = value.length() - 1; i >= 0; i--) {
      String candidate = value.substring(0, i) + ellipsis;
      if (textWidth.applyAsInt(candidate) <= maxPx) {
        return candidate;
      }
    }
    return ellipsis;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BusMatrixLayout layout)) {
      return false;
    }
    if (cellWidth != layout.cellWidth
        || headerHeight != layout.headerHeight
        || rowHeight != layout.rowHeight
        || frozenWidths.length != layout.frozenWidths.length) {
      return false;
    }
    for (int i = 0; i < frozenWidths.length; i++) {
      if (frozenWidths[i] != layout.frozenWidths[i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = cellWidth;
    hash = 31 * hash + headerHeight;
    hash = 31 * hash + rowHeight;
    for (int width : frozenWidths) {
      hash = 31 * hash + width;
    }
    return hash;
  }
}
