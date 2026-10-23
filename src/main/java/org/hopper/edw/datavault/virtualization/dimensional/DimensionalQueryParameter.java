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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Date;

/** One bound JDBC parameter. The value is never written to the audit table. */
public record DimensionalQueryParameter(int jdbcType, String text, boolean sqlNull) {

  public Object hopValue() {
    if (sqlNull || text == null) {
      return null;
    }
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> Long.valueOf(text.trim());
      case Types.FLOAT, Types.REAL, Types.DOUBLE -> Double.valueOf(text.trim());
      case Types.NUMERIC, Types.DECIMAL -> new BigDecimal(text.trim());
      case Types.BIT, Types.BOOLEAN -> Boolean.valueOf(text.trim());
      case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
          new Date(Long.parseLong(text.trim()));
      default -> text;
    };
  }

  public int hopType() {
    return switch (jdbcType) {
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT ->
          org.apache.hop.core.row.IValueMeta.TYPE_INTEGER;
      case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL ->
          org.apache.hop.core.row.IValueMeta.TYPE_BIGNUMBER;
      case Types.BIT, Types.BOOLEAN -> org.apache.hop.core.row.IValueMeta.TYPE_BOOLEAN;
      case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
          org.apache.hop.core.row.IValueMeta.TYPE_TIMESTAMP;
      default -> org.apache.hop.core.row.IValueMeta.TYPE_STRING;
    };
  }
}
