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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Maps a hub business-key vault field to column(s) on a link (or similar) record-source feed.
 *
 * <p>For a normal single-field hub BK: one {@link #sourceFieldName}. For a <em>composite</em> hub
 * BK (single vault column, N source parts): ordered {@link #sourceFieldNames}. Satellites and links
 * do not store the composed vault BK; these fields only identify hash inputs on the feed.
 */
@Getter
@Setter
public class BusinessKeySource {
  @HopMetadataProperty private String businessKeyField;

  /**
   * Ordered source columns when the hub business key is composite (N parts → one vault BK name).
   * Empty falls back to {@link #sourceFieldName}.
   */
  @HopMetadataProperty(key = "sourceFieldName", groupKey = "sourceFieldNames")
  private List<String> sourceFieldNames = new ArrayList<>();

  @HopMetadataProperty private String sourceFieldName;

  public BusinessKeySource() {}

  public BusinessKeySource(String businessKeyField, String sourceFieldName) {
    this.businessKeyField = businessKeyField;
    this.sourceFieldName = sourceFieldName;
  }

  /**
   * Ordered non-empty source field names for this mapping.
   *
   * <p>Prefers {@link #sourceFieldNames}; if empty, uses legacy {@link #sourceFieldName} as a
   * single-element list.
   */
  public List<String> resolveSourceParts() {
    List<String> parts = new ArrayList<>();
    if (sourceFieldNames != null) {
      for (String part : sourceFieldNames) {
        if (!Utils.isEmpty(part)) {
          parts.add(part);
        }
      }
    }
    if (!parts.isEmpty()) {
      return parts;
    }
    if (!Utils.isEmpty(sourceFieldName)) {
      return List.of(sourceFieldName);
    }
    return List.of();
  }

  public int sourcePartCount() {
    return resolveSourceParts().size();
  }
}
