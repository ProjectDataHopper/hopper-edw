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
import org.apache.hop.core.row.IValueMeta;

/** A column offered in the crosstab sources tree. */
@Getter
public final class FactCrosstabSourceColumn {

  public enum Kind {
    MEASURE,
    DEGENERATE,
    RANGE,
    NATURAL_KEY,
    ATTRIBUTE
  }

  private final String fieldName;
  private final String header;
  private final Kind kind;
  private final int valueType;
  private final boolean additiveMeasure;

  public FactCrosstabSourceColumn(
      String fieldName, String header, Kind kind, int valueType, boolean additiveMeasure) {
    this.fieldName = fieldName;
    this.header = header;
    this.kind = kind;
    this.valueType = valueType;
    this.additiveMeasure = additiveMeasure;
  }

  public boolean isNumeric() {
    return valueType == IValueMeta.TYPE_INTEGER
        || valueType == IValueMeta.TYPE_NUMBER
        || valueType == IValueMeta.TYPE_BIGNUMBER;
  }
}
