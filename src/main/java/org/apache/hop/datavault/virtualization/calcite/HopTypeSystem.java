/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.virtualization.calcite;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;

/** Maps Hop {@link IValueMeta} type codes to Calcite types and back. */
public final class HopTypeSystem {

  private HopTypeSystem() {}

  public static RelDataType toRelType(RelDataTypeFactory typeFactory, SourceColumn column) {
    int hopType = column != null ? column.getHopType() : IValueMeta.TYPE_STRING;
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
      int len = parsePositiveInt(column != null ? column.getLength() : null, 1024);
      type = typeFactory.createSqlType(sqlType, len);
    } else if (sqlType == SqlTypeName.DECIMAL) {
      int precision = parsePositiveInt(column != null ? column.getLength() : null, 38);
      int scale = parseNonNegativeInt(column != null ? column.getPrecision() : null, 10);
      type = typeFactory.createSqlType(sqlType, precision, scale);
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
