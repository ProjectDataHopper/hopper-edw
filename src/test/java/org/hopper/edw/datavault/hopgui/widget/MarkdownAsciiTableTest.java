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
package org.hopper.edw.datavault.hopgui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.datavault.hopgui.widget.MarkdownAsciiTable.Align;
import org.junit.jupiter.api.Test;

class MarkdownAsciiTableTest {

  @Test
  void padsColumnsForAlignedAsciiTable() {
    String table =
        MarkdownAsciiTable.format(
            List.of("Transform", "Plugin", "In"),
            List.of(Align.LEFT, Align.LEFT, Align.RIGHT),
            List.of(
                List.of("source_f_order_lines", "TableInput", "0"),
                List.of("bulk_load", "PGBulkLoader", "100")));

    assertTrue(table.contains("Transform"));
    assertTrue(table.contains("source_f_order_lines"));
    assertTrue(table.contains("PGBulkLoader"));
    int headerLine = table.indexOf("| Transform");
    int dataLine = table.indexOf("| source_f_order_lines");
    assertTrue(headerLine >= 0 && dataLine > headerLine);
    assertTrue(table.indexOf('|', headerLine + 1) > headerLine);
    String[] lines = table.split("\n");
    assertEquals(4, lines.length);
    assertEquals(lines[0].length(), lines[1].length());
    assertTrue(lines[1].contains("----------"));
  }
}
