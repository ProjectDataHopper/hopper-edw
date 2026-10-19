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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

import java.util.List;
import java.util.Map;

/**
 * In-memory dashboard description. {@code link} and {@code edit_link} are row fields, not columns.
 */
public record EdwJourneyPanelSpec(
    EdwJourneyPanelId id,
    String title,
    String note,
    List<ChartSpec> charts,
    List<TableSpec> tables) {

  public EdwJourneyPanelSpec {
    charts = charts == null ? List.of() : List.copyOf(charts);
    tables = tables == null ? List.of() : List.copyOf(tables);
  }

  public record ChartSpec(
      String title,
      String category,
      String series,
      String value,
      String linkTemplate,
      List<Map<String, String>> rows,
      Integer labelAngle) {
    public ChartSpec {
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }

  public record TableSpec(String title, List<String> columns, List<Map<String, String>> rows) {
    public TableSpec {
      columns = columns == null ? List.of() : List.copyOf(columns);
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }
}
