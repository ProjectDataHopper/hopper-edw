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
package org.apache.hop.datavault.metadata.iceberg;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/** Maps Iceberg schema values to Hop value metadata and row values. */
public final class IcebergTypeMapping {

  public static final int DEFAULT_INTEGER_LENGTH = 15;

  private IcebergTypeMapping() {}

  public static int hopTypeForIcebergType(Type icebergType) {
    return switch (icebergType.typeId()) {
      case BOOLEAN -> IValueMeta.TYPE_BOOLEAN;
      case INTEGER -> IValueMeta.TYPE_INTEGER;
      case LONG -> IValueMeta.TYPE_INTEGER;
      case FLOAT -> IValueMeta.TYPE_NUMBER;
      case DOUBLE -> IValueMeta.TYPE_NUMBER;
      case STRING, UUID -> IValueMeta.TYPE_STRING;
      case DATE -> IValueMeta.TYPE_DATE;
      case TIME -> IValueMeta.TYPE_STRING;
      case TIMESTAMP -> IValueMeta.TYPE_TIMESTAMP;
      case FIXED, BINARY -> IValueMeta.TYPE_BINARY;
      case DECIMAL -> IValueMeta.TYPE_BIGNUMBER;
      case LIST, MAP, STRUCT -> IValueMeta.TYPE_STRING;
      default -> IValueMeta.TYPE_STRING;
    };
  }

  public static IValueMeta valueMetaForField(
      String fieldName, Type icebergType, String transformName) throws HopException {
    int hopType = hopTypeForIcebergType(icebergType);
    IValueMeta valueMeta = ValueMetaFactory.createValueMeta(fieldName, hopType);
    valueMeta.setOrigin(transformName);
    if (icebergType instanceof Types.DecimalType decimalType) {
      valueMeta.setLength(decimalType.precision());
      valueMeta.setPrecision(decimalType.scale());
    } else if (icebergType instanceof Types.FixedType fixedType) {
      valueMeta.setLength(fixedType.length());
    } else if (hopType == IValueMeta.TYPE_INTEGER) {
      valueMeta.setLength(DEFAULT_INTEGER_LENGTH);
      valueMeta.setPrecision(-1);
    }
    return valueMeta;
  }

  public static Object toHopValue(Object icebergValue, Type icebergType) {
    if (icebergValue == null) {
      return null;
    }
    return switch (icebergType.typeId()) {
      case BOOLEAN -> {
        if (icebergValue instanceof Boolean bool) {
          yield bool;
        }
        yield Boolean.valueOf(icebergValue.toString());
      }
      case INTEGER, LONG -> {
        if (icebergValue instanceof Number number) {
          yield number.longValue();
        }
        yield Long.valueOf(icebergValue.toString());
      }
      case FLOAT, DOUBLE -> {
        if (icebergValue instanceof Number number) {
          yield number.doubleValue();
        }
        yield Double.valueOf(icebergValue.toString());
      }
      case DATE -> {
        if (icebergValue instanceof LocalDate localDate) {
          yield Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (icebergValue instanceof Date date) {
          yield date;
        }
        yield icebergValue;
      }
      case TIMESTAMP -> {
        if (icebergValue instanceof OffsetDateTime offsetDateTime) {
          yield java.sql.Timestamp.from(offsetDateTime.toInstant());
        }
        if (icebergValue instanceof LocalDateTime localDateTime) {
          yield java.sql.Timestamp.valueOf(localDateTime);
        }
        if (icebergValue instanceof Date date && !(icebergValue instanceof java.sql.Timestamp)) {
          yield new java.sql.Timestamp(date.getTime());
        }
        yield icebergValue;
      }
      case DECIMAL -> {
        if (icebergValue instanceof BigDecimal bigDecimal) {
          yield bigDecimal;
        }
        yield new BigDecimal(icebergValue.toString());
      }
      case BINARY, FIXED -> {
        if (icebergValue instanceof ByteBuffer buffer) {
          byte[] bytes = new byte[buffer.remaining()];
          buffer.duplicate().get(bytes);
          yield bytes;
        }
        if (icebergValue instanceof byte[] bytes) {
          yield bytes;
        }
        yield icebergValue;
      }
      case LIST, MAP, STRUCT -> icebergValue.toString();
      default -> icebergValue;
    };
  }

  public static Object[] toHopRow(Record record, Schema schema, String[] fieldNames) {
    Object[] row = new Object[fieldNames.length];
    for (int i = 0; i < fieldNames.length; i++) {
      String fieldName = fieldNames[i];
      Type fieldType = schema.findType(fieldName);
      row[i] = toHopValue(record.getField(fieldName), fieldType);
    }
    return row;
  }
}
