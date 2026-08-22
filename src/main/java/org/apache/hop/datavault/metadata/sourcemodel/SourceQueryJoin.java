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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.jspecify.annotations.NonNull;

/** One join step in a {@link SourceQuery} (table added to the driving table). */
@Getter
@Setter
@NoArgsConstructor
public class SourceQueryJoin {

  /** Table being joined in (must exist on the source model). */
  @HopMetadataProperty private String tableName;

  /** Optional reference to a {@link SourceRelationship#getName()} for default join columns. */
  @HopMetadataProperty private String relationshipName;

  @HopMetadataProperty(storeWithCode = true)
  private SourceJoinType joinType = SourceJoinType.LEFT;

  /**
   * Join keys on the left side of this step (already-in-scope tables), used when {@link
   * #relationshipName} is empty or overridden.
   */
  @HopMetadataProperty(key = "left_column", groupKey = "left_columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> leftColumns = new ArrayList<>();

  /** Join keys on {@link #tableName}. */
  @HopMetadataProperty(key = "right_column", groupKey = "right_columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> rightColumns = new ArrayList<>();

  /**
   * Optional qualified left column sources ({@code table.column}) when left keys span multiple
   * prior tables. Parallel to {@link #leftColumns} when used.
   */
  @HopMetadataProperty(key = "left_table", groupKey = "left_tables")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> leftTableNames = new ArrayList<>();

  public @NonNull List<String> getLeftColumns() {
    if (leftColumns == null) {
      leftColumns = new ArrayList<>();
    }
    return leftColumns;
  }

  public void setLeftColumns(List<String> leftColumns) {
    this.leftColumns = leftColumns != null ? new ArrayList<>(leftColumns) : new ArrayList<>();
  }

  public @NonNull List<String> getRightColumns() {
    if (rightColumns == null) {
      rightColumns = new ArrayList<>();
    }
    return rightColumns;
  }

  public void setRightColumns(List<String> rightColumns) {
    this.rightColumns = rightColumns != null ? new ArrayList<>(rightColumns) : new ArrayList<>();
  }

  public @NonNull List<String> getLeftTableNames() {
    if (leftTableNames == null) {
      leftTableNames = new ArrayList<>();
    }
    return leftTableNames;
  }

  public void setLeftTableNames(List<String> leftTableNames) {
    this.leftTableNames =
        leftTableNames != null ? new ArrayList<>(leftTableNames) : new ArrayList<>();
  }

  public SourceJoinType resolveJoinType() {
    return joinType != null ? joinType : SourceJoinType.LEFT;
  }
}
