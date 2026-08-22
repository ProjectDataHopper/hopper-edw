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
package org.apache.hop.datavault.hopgui.widget;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;

/** Formats fixed-width ASCII tables for markdown code blocks. */
public final class MarkdownAsciiTable {

  public enum Align {
    LEFT,
    RIGHT
  }

  private MarkdownAsciiTable() {}

  public static String format(List<String> headers, List<Align> aligns, List<List<String>> rows) {
    if (headers == null || headers.isEmpty()) {
      return "";
    }
    int columnCount = headers.size();
    List<Align> columnAligns = normalizeAligns(aligns, columnCount);
    int[] widths = new int[columnCount];
    for (int column = 0; column < columnCount; column++) {
      widths[column] = cell(headers.get(column)).length();
    }
    if (rows != null) {
      for (List<String> row : rows) {
        if (row == null) {
          continue;
        }
        for (int column = 0; column < columnCount; column++) {
          String value = column < row.size() ? row.get(column) : "";
          widths[column] = Math.max(widths[column], cell(value).length());
        }
      }
    }

    StringBuilder table = new StringBuilder();
    appendRow(table, headers, columnAligns, widths);
    appendSeparator(table, widths);
    if (rows != null) {
      for (List<String> row : rows) {
        if (row == null) {
          continue;
        }
        appendRow(table, row, columnAligns, widths);
      }
    }
    return table.toString().trim();
  }

  private static List<Align> normalizeAligns(List<Align> aligns, int columnCount) {
    List<Align> columnAligns = new ArrayList<>(columnCount);
    for (int column = 0; column < columnCount; column++) {
      Align align =
          aligns != null && column < aligns.size() && aligns.get(column) != null
              ? aligns.get(column)
              : Align.LEFT;
      columnAligns.add(align);
    }
    return columnAligns;
  }

  private static void appendSeparator(StringBuilder table, int[] widths) {
    table.append('|');
    for (int width : widths) {
      table.append(' ').append("-".repeat(Math.max(1, width))).append(' ');
      table.append('|');
    }
    table.append(System.lineSeparator());
  }

  private static void appendRow(
      StringBuilder table, List<String> values, List<Align> aligns, int[] widths) {
    table.append('|');
    for (int column = 0; column < widths.length; column++) {
      String value = column < values.size() ? values.get(column) : "";
      table.append(' ').append(pad(cell(value), widths[column], aligns.get(column))).append(' ');
      table.append('|');
    }
    table.append(System.lineSeparator());
  }

  private static String pad(String value, int width, Align align) {
    if (value.length() >= width) {
      return value;
    }
    int padding = width - value.length();
    if (align == Align.RIGHT) {
      return " ".repeat(padding) + value;
    }
    return value + " ".repeat(padding);
  }

  private static String cell(String value) {
    return Utils.isEmpty(value) ? "" : value.replace('\n', ' ').replace('\r', ' ');
  }
}
