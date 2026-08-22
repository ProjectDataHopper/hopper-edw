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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.datatypemapping.IDataTypeMappingTarget;
import org.apache.hop.datavault.metadata.datatypemapping.SourceFieldTypeMapping;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.jspecify.annotations.NonNull;

/**
 * Physical source entity on a {@link SourceModel} canvas (database table in v1; file types later).
 */
@Getter
@Setter
public class SourceTable extends HopMetadataBase
    implements IHopMetadata, IGuiPosition, IDataTypeMappingTarget {

  @HopMetadataProperty private String description;

  /** Optional link to a catalog {@code DV_SOURCE} record name. */
  @HopMetadataProperty private String catalogSourceName;

  @HopMetadataProperty(storeWithCode = true)
  private DvSourceType physicalType = DvSourceType.DATABASE;

  @HopMetadataProperty private String databaseName;
  @HopMetadataProperty private String schemaName;
  @HopMetadataProperty private String tableName;

  @HopMetadataProperty(key = "column", groupKey = "columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceColumn> columns = new ArrayList<>();

  /** Ordered project {@code DataTypeMappingMeta} profile names applied to this table. */
  @HopMetadataProperty(key = "dataTypeMappingName", groupKey = "dataTypeMappingNames")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> dataTypeMappingNames = new ArrayList<>();

  /** Per-field fine-tunes applied after profiles. */
  @HopMetadataProperty(key = "fieldTypeMapping", groupKey = "fieldTypeMappings")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceFieldTypeMapping> fieldTypeMappings = new ArrayList<>();

  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  private boolean selected;
  private int drawnBoxWidth = 160;
  private int drawnBoxHeight = 90;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  public SourceTable() {}

  public SourceTable(String name) {
    setName(name);
  }

  public @NonNull List<SourceColumn> getColumns() {
    if (columns == null) {
      columns = new ArrayList<>();
    }
    return columns;
  }

  public void setColumns(List<SourceColumn> columns) {
    this.columns = columns != null ? columns : new ArrayList<>();
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

  public SourceColumn findColumn(String columnName) {
    if (Utils.isEmpty(columnName)) {
      return null;
    }
    for (SourceColumn column : getColumns()) {
      if (column != null && columnName.equals(column.getName())) {
        return column;
      }
    }
    return null;
  }

  public List<SourceColumn> primaryKeyColumns() {
    List<SourceColumn> keys = new ArrayList<>();
    for (SourceColumn column : getColumns()) {
      if (column != null && column.isPrimaryKey()) {
        keys.add(column);
      }
    }
    keys.sort((a, b) -> Integer.compare(a.getPrimaryKeyPosition(), b.getPrimaryKeyPosition()));
    return keys;
  }

  public DvSourceType resolvePhysicalType() {
    return physicalType != null ? physicalType : DvSourceType.DATABASE;
  }

  @Override
  public void setLocation(Point p) {
    if (p == null) {
      this.location = new Point(50, 50);
    } else {
      this.location = new Point(p.x, p.y);
    }
  }

  @Override
  public void setLocation(int x, int y) {
    if (location == null) {
      location = new Point(x, y);
    } else {
      location.x = x;
      location.y = y;
    }
  }

  @Override
  public Point getLocation() {
    if (location == null) {
      location = new Point(50, 50);
    }
    return location;
  }

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }
}
