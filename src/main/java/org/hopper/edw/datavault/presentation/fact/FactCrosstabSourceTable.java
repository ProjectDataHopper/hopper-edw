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
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;

/** One fact or related dimension/junk table in the crosstab sources tree. */
@Getter
@Setter
public final class FactCrosstabSourceTable {

  private String logicalName;
  private String physicalTableName;
  private String joinAlias;
  private DmTableType tableType;
  private String factForeignKey;
  private String dimensionKeyColumn;
  private final List<FactCrosstabSourceColumn> columns = new ArrayList<>();

  public boolean isFact() {
    return tableType == DmTableType.FACT
        || tableType == DmTableType.FACTLESS_FACT
        || tableType == DmTableType.PERIODIC_SNAPSHOT_FACT
        || tableType == DmTableType.ACCUMULATING_SNAPSHOT_FACT
        || tableType == DmTableType.AGGREGATE_FACT;
  }

  public FactCrosstabSourceColumn findColumn(String fieldName) {
    if (fieldName == null) {
      return null;
    }
    for (FactCrosstabSourceColumn column : columns) {
      if (column != null && fieldName.equals(column.getFieldName())) {
        return column;
      }
    }
    return null;
  }
}
