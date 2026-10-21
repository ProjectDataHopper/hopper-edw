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
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;

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

  /** Cell alignment. Null uses the zone default (dimensions center, facts right). */
  private HHorizontalAlignment horizontalAlignment;

  /** Cell alignment. Null uses middle. */
  private HVerticalAlignment verticalAlignment;

  /**
   * Facts only. When every fact opts out, the generated crosstab omits totals. Default on so
   * existing selections keep totals.
   */
  private boolean showingTotals = true;

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
    FactCrosstabField copy = new FactCrosstabField(tableName, columnName, header, aggregation);
    copy.horizontalAlignment = horizontalAlignment;
    copy.verticalAlignment = verticalAlignment;
    copy.showingTotals = showingTotals;
    return copy;
  }

  /** Dimensions default to center. Facts default to right. */
  public HHorizontalAlignment resolveHorizontalAlignment(boolean fact) {
    if (horizontalAlignment != null) {
      return horizontalAlignment;
    }
    return fact ? HHorizontalAlignment.RIGHT : HHorizontalAlignment.CENTER;
  }

  public HVerticalAlignment resolveVerticalAlignment() {
    return verticalAlignment != null ? verticalAlignment : HVerticalAlignment.MIDDLE;
  }

  public static HHorizontalAlignment parseHorizontalAlignment(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return HHorizontalAlignment.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public static HVerticalAlignment parseVerticalAlignment(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return HVerticalAlignment.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
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
