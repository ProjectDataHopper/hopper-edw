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
package org.apache.hop.datavault.metadata.database;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;

/**
 * One foreign-key constraint discovered via JDBC {@code DatabaseMetaData.getImportedKeys}.
 *
 * <p>Child columns (FK) reference parent columns (PK) in parallel order.
 */
@Getter
@Setter
public class DiscoveredForeignKey {

  private String constraintName;
  private String childSchema;
  private String childTable;
  private String parentSchema;
  private String parentTable;
  private final List<String> childColumns = new ArrayList<>();
  private final List<String> parentColumns = new ArrayList<>();

  public boolean isValid() {
    return !Utils.isEmpty(childTable)
        && !Utils.isEmpty(parentTable)
        && !childColumns.isEmpty()
        && childColumns.size() == parentColumns.size();
  }

  public void addColumnPair(String childColumn, String parentColumn) {
    if (Utils.isEmpty(childColumn) || Utils.isEmpty(parentColumn)) {
      return;
    }
    childColumns.add(childColumn.trim());
    parentColumns.add(parentColumn.trim());
  }

  /** Stable key for de-duplication across tables. */
  public String dedupeKey() {
    return (childSchema == null ? "" : childSchema)
        + "."
        + (childTable == null ? "" : childTable)
        + "->"
        + (parentSchema == null ? "" : parentSchema)
        + "."
        + (parentTable == null ? "" : parentTable)
        + ":"
        + String.join(",", childColumns)
        + "=>"
        + String.join(",", parentColumns);
  }
}
