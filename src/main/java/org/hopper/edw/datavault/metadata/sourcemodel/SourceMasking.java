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
import org.apache.hop.core.naming.NamingSchemeKind;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.edw.datavault.metadata.datatypemapping.IDataTypeMappingTarget;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceFieldTypeMapping;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.jspecify.annotations.NonNull;

/**
 * Named masking feed. It reads a parent card and replaces selected fields with a Hop masking
 * pattern. The parent stays available. JDBC, Data Vault, and downstream SQL bind this card when
 * they must not see the original values.
 *
 * <p>Published to the data catalog as a {@code DV_SOURCE} of type {@code MASKING}.
 */
@Getter
@Setter
@NamingSchemeKind(EdwNamingSchemeTypes.SOURCE_MASKING)
public class SourceMasking extends HopMetadataBase implements IHopMetadata, IDataTypeMappingTarget {

  @HopMetadataProperty private String description;

  @HopMetadataProperty(storeWithCode = true)
  private SourceMaskingParentKind parentSourceKind = SourceMaskingParentKind.TABLE;

  @HopMetadataProperty private String parentSourceName;

  @HopMetadataProperty(key = "field", groupKey = "fields")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceMaskingField> fields = new ArrayList<>();

  @HopMetadataProperty(key = "dataTypeMappingName", groupKey = "dataTypeMappingNames")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> dataTypeMappingNames = new ArrayList<>();

  @HopMetadataProperty(key = "fieldTypeMapping", groupKey = "fieldTypeMappings")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceFieldTypeMapping> fieldTypeMappings = new ArrayList<>();

  /** Default row limit for preview (0 = support default). */
  @HopMetadataProperty private int sampleRowLimit;

  /** Catalog feed name used on last publish (may equal {@link #getName()}). */
  @HopMetadataProperty private String publishedCatalogName;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  private boolean selected;

  public SourceMasking() {}

  public SourceMasking(String name) {
    setName(name);
  }

  public @NonNull List<SourceMaskingField> getFields() {
    if (fields == null) {
      fields = new ArrayList<>();
    }
    return fields;
  }

  public void setFields(List<SourceMaskingField> fields) {
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

  public SourceMaskingParentKind resolveParentSourceKind() {
    return parentSourceKind != null ? parentSourceKind : SourceMaskingParentKind.TABLE;
  }

  public Point getLocation() {
    if (location == null) {
      location = new Point(50, 50);
    }
    return location;
  }

  public String resolveCatalogName() {
    if (!Utils.isEmpty(publishedCatalogName)) {
      return publishedCatalogName.trim();
    }
    return getName() != null ? getName().trim() : "";
  }

  /** Fields that name a masking pattern. */
  public List<SourceMaskingField> maskedFields() {
    List<SourceMaskingField> masked = new ArrayList<>();
    for (SourceMaskingField field : getFields()) {
      if (field != null && field.hasPattern()) {
        masked.add(field);
      }
    }
    return masked;
  }
}
