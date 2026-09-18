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

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Result of hit-testing coordinates on a bus matrix canvas or SVG. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BusMatrixHit {

  public enum HitType {
    NONE,
    DIMENSION,
    FACT,
    CELL
  }

  private final HitType type;
  private final int rowIndex;
  private final int columnIndex;
  private final BusMatrixRow row;
  private final BusMatrixColumn column;
  private final BusMatrixCell cell;

  public static BusMatrixHit none() {
    return new BusMatrixHit(HitType.NONE, -1, -1, null, null, null);
  }

  public static BusMatrixHit dimension(int columnIndex, BusMatrixColumn column) {
    return new BusMatrixHit(HitType.DIMENSION, -1, columnIndex, null, column, null);
  }

  public static BusMatrixHit fact(int rowIndex, BusMatrixRow row) {
    return new BusMatrixHit(HitType.FACT, rowIndex, -1, row, null, null);
  }

  public static BusMatrixHit cell(
      int rowIndex,
      int columnIndex,
      BusMatrixRow row,
      BusMatrixColumn column,
      BusMatrixCell cell) {
    return new BusMatrixHit(HitType.CELL, rowIndex, columnIndex, row, column, cell);
  }

  public static BusMatrixHit hitAt(int gx, int gy, BusMatrix matrix, BusMatrixLayout layout) {
    if (matrix == null || layout == null || gx < 0 || gy < 0) {
      return none();
    }
    int headerHeight = layout.headerHeight();
    int frozenWidth = layout.frozenWidth();
    int cellWidth = Math.max(1, layout.cellWidth());
    int rowHeight = Math.max(1, layout.rowHeight());
    List<BusMatrixColumn> columns = matrix.getColumns();
    List<BusMatrixRow> rows = matrix.getRows();

    // 1. Tilted dimension header zone
    if (gy < headerHeight) {
      int colIndex = layout.headerColumnAt(gx, gy, 0, columns.size());
      if (colIndex >= 0 && colIndex < columns.size()) {
        return dimension(colIndex, columns.get(colIndex));
      }
      return none();
    }

    // 2. Data rows zone
    int rowIndex = (gy - headerHeight) / rowHeight;
    if (rowIndex < 0 || rowIndex >= rows.size()) {
      return none();
    }
    BusMatrixRow row = rows.get(rowIndex);

    // Left frozen columns -> Fact hit
    if (gx < frozenWidth) {
      return fact(rowIndex, row);
    }

    // Data cells
    int colIndex = (gx - frozenWidth) / cellWidth;
    if (colIndex >= 0 && colIndex < columns.size()) {
      BusMatrixColumn col = columns.get(colIndex);
      BusMatrixCell cell = row.cell(col.key());
      return cell(rowIndex, colIndex, row, col, cell);
    }

    return none();
  }

  public boolean isDimension() {
    return type == HitType.DIMENSION && column != null;
  }

  public boolean isFact() {
    return type == HitType.FACT && row != null;
  }

  public boolean isCell() {
    return type == HitType.CELL && cell != null;
  }

  public boolean hasAction() {
    return type != HitType.NONE;
  }

  public String tooltip() {
    return switch (type) {
      case DIMENSION -> column != null ? column.tooltip() : "";
      case FACT -> row != null ? row.tooltip() : "";
      case CELL -> cell != null && row != null && column != null
          ? cell.richTooltip(row.factName(), row.grain(), column.label())
          : "";
      default -> "";
    };
  }

  public boolean hasTooltip() {
    String t = tooltip();
    return t != null && !t.isBlank();
  }

  public String targetName() {
    return switch (type) {
      case DIMENSION -> column != null ? column.label() : "";
      case FACT -> row != null ? row.factName() : "";
      case CELL -> (row != null ? row.factName() : "")
          + " \u00D7 "
          + (column != null ? column.label() : "");
      default -> "";
    };
  }
}
