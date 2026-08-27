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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.Point;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.edw.datavault.metadata.datatypemapping.IDataTypeMappingTarget;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceFieldTypeMapping;
import org.jspecify.annotations.NonNull;

/**
 * Named JSON extraction that becomes a flat feed for hubs, links, and satellites.
 *
 * <p>Points at a JSON string field on another source-model object (table, query, or another JSON
 * source) and projects fields using JsonPath, with at most one array expansion per step (Hop
 * JsonInput rules). Nested arrays are handled by chaining multiple {@link SourceJson} objects.
 *
 * <p>Published to the data catalog as a {@code DV_SOURCE} of type {@code JSON}.
 */
@Getter
@Setter
public class SourceJson extends HopMetadataBase implements IHopMetadata, IDataTypeMappingTarget {

  @HopMetadataProperty private String description;

  /** Parent object kind on the source model canvas. */
  @HopMetadataProperty(storeWithCode = true)
  private SourceJsonParentKind parentSourceKind = SourceJsonParentKind.TABLE;

  /** Name of the parent table, query, or JSON source. */
  @HopMetadataProperty private String parentSourceName;

  /**
   * Field on the parent row that holds the JSON document (or a JSON string produced by a previous
   * extraction step).
   */
  @HopMetadataProperty private String jsonFieldName;

  /**
   * Optional UI hint for the single array path chosen for expansion (also encoded in field paths).
   */
  @HopMetadataProperty private String arrayFocusPath;

  @HopMetadataProperty(key = "field", groupKey = "fields")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceJsonField> fields = new ArrayList<>();

  @HopMetadataProperty(key = "dataTypeMappingName", groupKey = "dataTypeMappingNames")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> dataTypeMappingNames = new ArrayList<>();

  @HopMetadataProperty(key = "fieldTypeMapping", groupKey = "fieldTypeMappings")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceFieldTypeMapping> fieldTypeMappings = new ArrayList<>();

  /** JsonInput: do not fail when a path is missing. */
  @HopMetadataProperty private boolean ignoreMissingPath = true;

  /** JsonInput: default missing path leaf to null. */
  @HopMetadataProperty private boolean defaultPathLeafToNull = true;

  /** Default row limit for sample / preview dialogs (0 = use support default). */
  @HopMetadataProperty private int sampleRowLimit;

  /** Catalog feed name used on last publish (may equal {@link #getName()}). */
  @HopMetadataProperty private String publishedCatalogName;

  /** Optional canvas location when the JSON source is shown as a node. */
  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  private boolean selected;

  public SourceJson() {}

  public SourceJson(String name) {
    setName(name);
  }

  public @NonNull List<SourceJsonField> getFields() {
    if (fields == null) {
      fields = new ArrayList<>();
    }
    return fields;
  }

  public void setFields(List<SourceJsonField> fields) {
    this.fields = fields != null ? fields : new ArrayList<>();
  }

  @Override
  public @NonNull List<String> getDataTypeMappingNames() {
    if (dataTypeMappingNames == null) {
      dataTypeMappingNames = new ArrayList<>();
    }
    return dataTypeMappingNames;
  }

  @Override
  public void setDataTypeMappingNames(List<String> dataTypeMappingNames) {
    this.dataTypeMappingNames =
        dataTypeMappingNames != null ? dataTypeMappingNames : new ArrayList<>();
  }

  @Override
  public @NonNull List<SourceFieldTypeMapping> getFieldTypeMappings() {
    if (fieldTypeMappings == null) {
      fieldTypeMappings = new ArrayList<>();
    }
    return fieldTypeMappings;
  }

  @Override
  public void setFieldTypeMappings(List<SourceFieldTypeMapping> fieldTypeMappings) {
    this.fieldTypeMappings = fieldTypeMappings != null ? fieldTypeMappings : new ArrayList<>();
  }

  public SourceJsonParentKind resolveParentSourceKind() {
    return parentSourceKind != null ? parentSourceKind : SourceJsonParentKind.TABLE;
  }

  public Point getLocation() {
    if (location == null) {
      location = new Point(50, 50);
    }
    return location;
  }

  /** Extracted (non pass-through) fields only. */
  public List<SourceJsonField> extractedFields() {
    List<SourceJsonField> extracted = new ArrayList<>();
    for (SourceJsonField field : getFields()) {
      if (field != null && !field.isPassThrough()) {
        extracted.add(field);
      }
    }
    return extracted;
  }

  /** Pass-through parent fields only. */
  public List<SourceJsonField> passThroughFields() {
    List<SourceJsonField> passThrough = new ArrayList<>();
    for (SourceJsonField field : getFields()) {
      if (field != null && field.isPassThrough()) {
        passThrough.add(field);
      }
    }
    return passThrough;
  }

  /** Count of fields whose path expands an array ({@code [*]}). */
  public int countArrayExpandingFields() {
    int count = 0;
    for (SourceJsonField field : getFields()) {
      if (field != null && field.isArrayExpandingPath()) {
        count++;
      }
    }
    return count;
  }
}
