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
package org.hopper.edw.datavault.virtualization.calcite;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;

/** Maps Hop {@link IValueMeta} type codes to Calcite types and back. */
public final class HopTypeSystem {

  private HopTypeSystem() {}

  public static RelDataType toRelType(RelDataTypeFactory typeFactory, SourceColumn column) {
    int hopType = column != null ? column.getHopType() : IValueMeta.TYPE_STRING;
    int varcharLen = parsePositiveInt(column != null ? column.getLength() : null, 1024);
    int decimalPrecision = parsePositiveInt(column != null ? column.getLength() : null, 38);
    int decimalScale = parseNonNegativeInt(column != null ? column.getPrecision() : null, 10);
    return toRelType(typeFactory, hopType, varcharLen, decimalPrecision, decimalScale);
  }

  public static RelDataType toRelType(RelDataTypeFactory typeFactory, IValueMeta valueMeta) {
    int hopType = valueMeta != null ? valueMeta.getType() : IValueMeta.TYPE_STRING;
    int length = valueMeta != null ? valueMeta.getLength() : -1;
    int scale = valueMeta != null ? valueMeta.getPrecision() : -1;
    int varcharLen = length > 0 ? length : 1024;
    int decimalPrecision = length > 0 ? length : 38;
    int decimalScale = scale >= 0 ? scale : 10;
    return toRelType(typeFactory, hopType, varcharLen, decimalPrecision, decimalScale);
  }

  private static RelDataType toRelType(
      RelDataTypeFactory typeFactory,
      int hopType,
      int varcharLen,
      int decimalPrecision,
      int decimalScale) {
    if (hopType <= 0) {
      hopType = IValueMeta.TYPE_STRING;
    }
    SqlTypeName sqlType =
        switch (hopType) {
          case IValueMeta.TYPE_INTEGER -> SqlTypeName.BIGINT;
          case IValueMeta.TYPE_NUMBER -> SqlTypeName.DOUBLE;
          case IValueMeta.TYPE_BIGNUMBER -> SqlTypeName.DECIMAL;
          case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> SqlTypeName.TIMESTAMP;
          case IValueMeta.TYPE_BOOLEAN -> SqlTypeName.BOOLEAN;
          case IValueMeta.TYPE_BINARY -> SqlTypeName.VARBINARY;
          default -> SqlTypeName.VARCHAR;
        };
    RelDataType type;
    if (sqlType == SqlTypeName.VARCHAR) {
      type = typeFactory.createSqlType(sqlType, varcharLen);
    } else if (sqlType == SqlTypeName.DECIMAL) {
      type = typeFactory.createSqlType(sqlType, decimalPrecision, decimalScale);
    } else {
      type = typeFactory.createSqlType(sqlType);
    }
    return typeFactory.createTypeWithNullability(type, true);
  }

  public static int toHopType(RelDataType type) {
    if (type == null) {
      return IValueMeta.TYPE_STRING;
    }
    SqlTypeName name = type.getSqlTypeName();
    if (name == null) {
      return IValueMeta.TYPE_STRING;
    }
    return switch (name) {
      case BOOLEAN -> IValueMeta.TYPE_BOOLEAN;
      case TINYINT, SMALLINT, INTEGER, BIGINT -> IValueMeta.TYPE_INTEGER;
      case FLOAT, REAL, DOUBLE -> IValueMeta.TYPE_NUMBER;
      case DECIMAL -> IValueMeta.TYPE_BIGNUMBER;
      case DATE, TIME, TIMESTAMP, TIMESTAMP_WITH_LOCAL_TIME_ZONE -> IValueMeta.TYPE_DATE;
      case BINARY, VARBINARY -> IValueMeta.TYPE_BINARY;
      default -> IValueMeta.TYPE_STRING;
    };
  }

  public static int toHopLength(RelDataType type) {
    if (type == null) {
      return -1;
    }
    SqlTypeName name = type.getSqlTypeName();
    if (name == SqlTypeName.VARCHAR
        || name == SqlTypeName.CHAR
        || name == SqlTypeName.DECIMAL
        || name == SqlTypeName.BINARY
        || name == SqlTypeName.VARBINARY) {
      int precision = type.getPrecision();
      return precision > 0 ? precision : -1;
    }
    return -1;
  }

  public static int toHopScale(RelDataType type) {
    if (type == null || type.getSqlTypeName() != SqlTypeName.DECIMAL) {
      return -1;
    }
    return type.getScale();
  }

  public static org.apache.hop.core.row.IRowMeta toRowMeta(RelDataType rowType) {
    RowMeta rowMeta = new RowMeta();
    if (rowType == null) {
      return rowMeta;
    }
    for (org.apache.calcite.rel.type.RelDataTypeField field : rowType.getFieldList()) {
      try {
        IValueMeta valueMeta =
            ValueMetaFactory.createValueMeta(field.getName(), toHopType(field.getType()));
        rowMeta.addValueMeta(valueMeta);
      } catch (Exception e) {
        // Fall back to string if factory fails for an exotic type.
        try {
          rowMeta.addValueMeta(
              ValueMetaFactory.createValueMeta(field.getName(), IValueMeta.TYPE_STRING));
        } catch (Exception ignored) {
          // ignore
        }
      }
    }
    return rowMeta;
  }

  private static int parsePositiveInt(String text, int defaultValue) {
    if (Utils.isEmpty(text)) {
      return defaultValue;
    }
    try {
      int v = Integer.parseInt(text.trim());
      return v > 0 ? v : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static int parseNonNegativeInt(String text, int defaultValue) {
    if (Utils.isEmpty(text)) {
      return defaultValue;
    }
    try {
      int v = Integer.parseInt(text.trim());
      return v >= 0 ? v : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
