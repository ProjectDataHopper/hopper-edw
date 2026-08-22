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

import java.util.List;
import lombok.Getter;

/** Describes a foreign-key constraint to emit in CREATE TABLE DDL. */
@Getter
public final class ForeignKeySpec {

  private final String constraintName;
  private final List<String> childColumns;
  private final String parentTableName;
  private final List<String> parentColumns;

  public ForeignKeySpec(
      String constraintName,
      List<String> childColumns,
      String parentTableName,
      List<String> parentColumns) {
    this.constraintName = constraintName;
    this.childColumns = childColumns == null ? List.of() : List.copyOf(childColumns);
    this.parentTableName = parentTableName;
    this.parentColumns = parentColumns == null ? List.of() : List.copyOf(parentColumns);
  }

  public boolean isValid() {
    return childColumns != null
        && !childColumns.isEmpty()
        && parentColumns != null
        && !parentColumns.isEmpty()
        && parentTableName != null
        && !parentTableName.isBlank()
        && childColumns.size() == parentColumns.size();
  }
}
