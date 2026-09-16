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
import java.util.Locale;
import lombok.Getter;
import org.apache.hop.core.util.Utils;

/** Kimball bus matrix: fact-like rows × conformed dimension columns. */
@Getter
public final class BusMatrix {

  private final String groupName;
  private final List<BusMatrixRow> rows;
  private final List<BusMatrixColumn> columns;
  private final List<String> warnings;

  public BusMatrix(
      String groupName,
      List<BusMatrixRow> rows,
      List<BusMatrixColumn> columns,
      List<String> warnings) {
    this.groupName = groupName;
    this.rows = rows != null ? List.copyOf(rows) : List.of();
    this.columns = columns != null ? List.copyOf(columns) : List.of();
    this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
  }

  public BusMatrixCell cell(int rowIndex, int columnIndex) {
    if (rowIndex < 0
        || rowIndex >= rows.size()
        || columnIndex < 0
        || columnIndex >= columns.size()) {
      return BusMatrixCell.EMPTY;
    }
    return rows.get(rowIndex).cell(columns.get(columnIndex).key());
  }

  public BusMatrix filtered(
      String search, String business, String level1, String level2, boolean hideUnused) {
    String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    List<BusMatrixRow> filteredRows = new ArrayList<>();
    for (BusMatrixRow row : rows) {
      if (!Utils.isEmpty(business) && !business.equals(row.business())) {
        continue;
      }
      if (!Utils.isEmpty(level1) && !level1.equals(row.level1())) {
        continue;
      }
      if (!Utils.isEmpty(level2) && !level2.equals(row.level2())) {
        continue;
      }
      if (!needle.isEmpty() && !row.matches(needle)) {
        continue;
      }
      filteredRows.add(row);
    }
    List<BusMatrixColumn> filteredColumns = new ArrayList<>();
    for (BusMatrixColumn column : columns) {
      boolean used = false;
      for (BusMatrixRow row : filteredRows) {
        if (row.cell(column.key()).used()) {
          used = true;
          break;
        }
      }
      if (hideUnused && !used) {
        continue;
      }
      if (!needle.isEmpty() && !used && !column.matches(needle)) {
        continue;
      }
      filteredColumns.add(column);
    }
    return new BusMatrix(groupName, filteredRows, filteredColumns, warnings);
  }

  public List<String> businesses() {
    return distinct(rows.stream().map(BusMatrixRow::business).toList());
  }

  public List<String> level1Values() {
    return distinct(rows.stream().map(BusMatrixRow::level1).toList());
  }

  public List<String> level2Values() {
    return distinct(rows.stream().map(BusMatrixRow::level2).toList());
  }

  private static List<String> distinct(List<String> values) {
    List<String> out = new ArrayList<>();
    for (String value : values) {
      if (!Utils.isEmpty(value) && !out.contains(value)) {
        out.add(value);
      }
    }
    return out;
  }
}
