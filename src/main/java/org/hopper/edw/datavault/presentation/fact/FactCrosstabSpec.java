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
package org.hopper.edw.datavault.presentation.fact;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** In-memory crosstab editor contents for one fact-table presentation session. */
@Getter
@Setter
public class FactCrosstabSpec {

  public enum Zone {
    GROUPS,
    HORIZONTAL,
    VERTICAL,
    FACTS
  }

  private String factTableName;

  /** Saved selection name. Empty when this editor session is a new presentation. */
  private String selectionName;
  private List<FactCrosstabField> groups = new ArrayList<>();
  private List<FactCrosstabField> horizontalDimensions = new ArrayList<>();
  private List<FactCrosstabField> verticalDimensions = new ArrayList<>();
  private List<FactCrosstabField> facts = new ArrayList<>();
  private boolean showingHorizontalTotals = true;
  private boolean showingVerticalTotals = true;

  /** A4 portrait when true. The default is landscape, matching earlier presentations. */
  private boolean portrait;

  public FactCrosstabSpec() {}

  public FactCrosstabSpec copy() {
    FactCrosstabSpec copy = new FactCrosstabSpec();
    copy.factTableName = factTableName;
    copy.selectionName = selectionName;
    copy.showingHorizontalTotals = showingHorizontalTotals;
    copy.showingVerticalTotals = showingVerticalTotals;
    copy.portrait = portrait;
    copy.groups = copyFields(groups);
    copy.horizontalDimensions = copyFields(horizontalDimensions);
    copy.verticalDimensions = copyFields(verticalDimensions);
    copy.facts = copyFields(facts);
    return copy;
  }

  public List<FactCrosstabField> fields(Zone zone) {
    return switch (zone) {
      case GROUPS -> groups;
      case HORIZONTAL -> horizontalDimensions;
      case VERTICAL -> verticalDimensions;
      case FACTS -> facts;
    };
  }

  public boolean isEmpty() {
    return groups.isEmpty()
        && horizontalDimensions.isEmpty()
        && verticalDimensions.isEmpty()
        && facts.isEmpty();
  }

  /** A pivot needs both axes. Otherwise the presentation is a flat table. */
  public boolean usesCrosstab() {
    return horizontalDimensions != null
        && !horizontalDimensions.isEmpty()
        && verticalDimensions != null
        && !verticalDimensions.isEmpty();
  }

  /** Exchange the horizontal and vertical dimension lists. */
  public void swapAxes() {
    List<FactCrosstabField> horizontal =
        horizontalDimensions != null ? horizontalDimensions : new ArrayList<>();
    List<FactCrosstabField> vertical =
        verticalDimensions != null ? verticalDimensions : new ArrayList<>();
    horizontalDimensions = vertical;
    verticalDimensions = horizontal;
  }

  public List<FactCrosstabField> allFields() {
    List<FactCrosstabField> all = new ArrayList<>();
    all.addAll(groups);
    all.addAll(horizontalDimensions);
    all.addAll(verticalDimensions);
    all.addAll(facts);
    return all;
  }

  public boolean containsSource(Zone zone, String tableName, String columnName) {
    for (FactCrosstabField field : fields(zone)) {
      if (field != null && field.sameSource(tableName, columnName)) {
        return true;
      }
    }
    return false;
  }

  public void addField(Zone zone, FactCrosstabField field) {
    if (field == null || containsSource(zone, field.getTableName(), field.getColumnName())) {
      return;
    }
    fields(zone).add(field);
  }

  private static List<FactCrosstabField> copyFields(List<FactCrosstabField> fields) {
    List<FactCrosstabField> copy = new ArrayList<>();
    if (fields == null) {
      return copy;
    }
    for (FactCrosstabField field : fields) {
      if (field != null) {
        copy.add(field.copy());
      }
    }
    return copy;
  }
}
