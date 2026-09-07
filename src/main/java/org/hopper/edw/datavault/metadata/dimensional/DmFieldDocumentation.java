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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Business definition, Markdown notes, and Markdown requirements for a dimensional field
 * (attribute, natural key, measure, or degenerate).
 */
@Getter
@Setter
@NoArgsConstructor
public class DmFieldDocumentation {

  /** Short business meaning shown in grids and search. */
  @HopMetadataProperty private String description;

  /** Markdown notes for later documentation (definition, examples, caveats). */
  @HopMetadataProperty private String notes;

  /** Markdown requirements / source-to-target comments for ETL and project-doc. */
  @HopMetadataProperty private String requirements;

  /**
   * When {@code null} or {@code true}, the field is required for gap analysis. Explicit {@code
   * false} marks it optional.
   */
  @HopMetadataProperty private Boolean required;

  public boolean isRequired() {
    return required == null || required;
  }

  public void setRequiredFlag(boolean required) {
    this.required = required;
  }

  public boolean hasContent() {
    return !Utils.isEmpty(description) || !Utils.isEmpty(notes) || !Utils.isEmpty(requirements);
  }

  /** Compact grid label: {@code desc, notes, req} and {@code optional} when not required. */
  public String gridSummary() {
    List<String> parts = new ArrayList<>();
    if (!Utils.isEmpty(description)) {
      parts.add("desc");
    }
    if (!Utils.isEmpty(notes)) {
      parts.add("notes");
    }
    if (!Utils.isEmpty(requirements)) {
      parts.add("req");
    }
    if (!isRequired()) {
      parts.add("optional");
    }
    return String.join(", ", parts);
  }

  public DmFieldDocumentation copy() {
    DmFieldDocumentation copy = new DmFieldDocumentation();
    copy.description = description;
    copy.notes = notes;
    copy.requirements = requirements;
    copy.required = required;
    return copy;
  }

  public static DmFieldDocumentation copyOf(DmFieldDocumentation source) {
    return source == null ? new DmFieldDocumentation() : source.copy();
  }
}
