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
package org.hopper.edw.semantic.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Named consumption query against a semantic model (crosstab / presentation). */
@Getter
@Setter
@NoArgsConstructor
public class SemanticSelection {

  @HopMetadataProperty private String name;

  /** Anchor fact (or fact-like) entity. */
  @HopMetadataProperty private String entityName;

  @HopMetadataProperty(key = "group", groupKey = "groups")
  private List<SemanticSelectionField> groups = new ArrayList<>();

  @HopMetadataProperty(key = "row", groupKey = "rows")
  private List<SemanticSelectionField> rows = new ArrayList<>();

  @HopMetadataProperty(key = "column", groupKey = "columns")
  private List<SemanticSelectionField> columns = new ArrayList<>();

  @HopMetadataProperty(key = "measure", groupKey = "measures")
  private List<SemanticSelectionField> measures = new ArrayList<>();

  @HopMetadataProperty private boolean showingHorizontalTotals = true;

  @HopMetadataProperty private boolean showingVerticalTotals = true;

  public SemanticSelection copy() {
    SemanticSelection copy = new SemanticSelection();
    copy.name = name;
    copy.entityName = entityName;
    copy.showingHorizontalTotals = showingHorizontalTotals;
    copy.showingVerticalTotals = showingVerticalTotals;
    copy.groups = copyFields(groups);
    copy.rows = copyFields(rows);
    copy.columns = copyFields(columns);
    copy.measures = copyFields(measures);
    return copy;
  }

  public boolean isEmpty() {
    return isEmpty(groups) && isEmpty(rows) && isEmpty(columns) && isEmpty(measures);
  }

  public List<SemanticSelectionField> allFields() {
    List<SemanticSelectionField> all = new ArrayList<>();
    addAll(all, groups);
    addAll(all, rows);
    addAll(all, columns);
    addAll(all, measures);
    return all;
  }

  private static void addAll(
      List<SemanticSelectionField> target, List<SemanticSelectionField> src) {
    if (src != null) {
      target.addAll(src);
    }
  }

  private static boolean isEmpty(List<SemanticSelectionField> fields) {
    return fields == null || fields.isEmpty();
  }

  private static List<SemanticSelectionField> copyFields(List<SemanticSelectionField> fields) {
    List<SemanticSelectionField> copy = new ArrayList<>();
    if (fields == null) {
      return copy;
    }
    for (SemanticSelectionField field : fields) {
      if (field != null) {
        copy.add(field.copy());
      }
    }
    return copy;
  }
}
