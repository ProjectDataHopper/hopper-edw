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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hopper.core.AggregationMethod;

/** One source column placed in a crosstab editor zone. */
@Getter
@Setter
@NoArgsConstructor
public class FactCrosstabField {

  /** Logical table name (fact, dimension, alias, or junk) as shown in the sources tree. */
  private String tableName;

  /** Physical column on that table. */
  private String columnName;

  /** Crosstab / group header. */
  private String header;

  /** Set only for the facts zone. */
  private AggregationMethod aggregation;

  public FactCrosstabField(String tableName, String columnName, String header) {
    this.tableName = tableName;
    this.columnName = columnName;
    this.header = header;
  }

  public FactCrosstabField(
      String tableName, String columnName, String header, AggregationMethod aggregation) {
    this(tableName, columnName, header);
    this.aggregation = aggregation;
  }

  public FactCrosstabField copy() {
    return new FactCrosstabField(tableName, columnName, header, aggregation);
  }

  public boolean sameSource(FactCrosstabField other) {
    if (other == null) {
      return false;
    }
    return eq(tableName, other.tableName) && eq(columnName, other.columnName);
  }

  public boolean sameSource(String otherTable, String otherColumn) {
    return eq(tableName, otherTable) && eq(columnName, otherColumn);
  }

  private static boolean eq(String a, String b) {
    if (a == null) {
      return b == null;
    }
    return a.equals(b);
  }
}
