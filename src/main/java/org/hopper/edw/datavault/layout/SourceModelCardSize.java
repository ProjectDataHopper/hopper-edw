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
package org.hopper.edw.datavault.layout;

import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/**
 * Card size used by source-model layout. Painted {@code drawnBox} sizes win. Before the first
 * paint, a table is estimated from its key rows instead of a flat pipeline-sized box.
 */
public final class SourceModelCardSize {

  public static final int MIN_TABLE_WIDTH = 160;
  public static final int MIN_TABLE_HEIGHT = 80;
  public static final int DEFAULT_TABLE_HEIGHT = 90;
  public static final int MIN_CARD_WIDTH = 160;
  public static final int MIN_CARD_HEIGHT = 80;

  private SourceModelCardSize() {}

  public record Size(int width, int height) {}

  public static Size ofTable(SourceTable table) {
    int drawnWidth = table != null ? table.getDrawnBoxWidth() : 0;
    int drawnHeight = table != null ? table.getDrawnBoxHeight() : 0;
    int estimatedHeight = estimatedTableHeight(table);
    int width = Math.max(MIN_TABLE_WIDTH, drawnWidth > 0 ? drawnWidth : MIN_TABLE_WIDTH);
    int height = drawnHeight > 0 ? drawnHeight : estimatedHeight;
    if (drawnHeight == DEFAULT_TABLE_HEIGHT && estimatedHeight > drawnHeight) {
      height = estimatedHeight;
    }
    return new Size(width, Math.max(MIN_TABLE_HEIGHT, height));
  }

  public static Size ofNamedCard(String name) {
    int chars = name != null ? name.length() : 0;
    return new Size(Math.max(MIN_CARD_WIDTH, 48 + chars * 8), MIN_CARD_HEIGHT);
  }

  private static int estimatedTableHeight(SourceTable table) {
    if (table == null) {
      return MIN_TABLE_HEIGHT;
    }
    int keys = table.primaryKeyColumns().size();
    int columns = table.getColumns().size();
    int rows = keys > 0 ? keys : Math.min(columns, 3);
    rows = Math.max(1, rows);
    return 48 + rows * 18;
  }
}
