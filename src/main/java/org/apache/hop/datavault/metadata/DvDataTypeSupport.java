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
package org.apache.hop.datavault.metadata;

import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;

/**
 * Resolves Hop value-meta type ids from model field data-type labels.
 *
 * <p>Model attributes and business keys often store either:
 *
 * <ul>
 *   <li>Hop type names ({@code Integer}, {@code String}, …) from manual entry / remediation, or
 *   <li>Native SQL types ({@code int2}, {@code varchar}, {@code datetime(6)}, …) copied by “Get
 *       attributes/keys” from {@link SourceField#getSourceDataType()}.
 * </ul>
 *
 * Only Hop names resolve via {@link ValueMetaFactory#getIdForValueMeta(String)}. When the label is
 * a SQL type, map well-known dialect names (including MySQL/SingleStore {@code DATETIME}) or use
 * the matching source field’s {@link SourceField#getHopType()} instead of defaulting to String
 * (which produced false type-mismatch validation errors).
 */
public final class DvDataTypeSupport {

  private DvDataTypeSupport() {}

  /**
   * Resolve a Hop type id from a free-text data type label, optionally using a source field when
   * the label is not a known Hop type name.
   *
   * @param dataType Hop type name or native SQL type (may be null/empty)
   * @param sourceField optional matching source field (hop type fallback)
   * @return a positive Hop type id, or {@link IValueMeta#TYPE_STRING} when unresolved
   */
  public static int resolveHopTypeId(String dataType, SourceField sourceField) {
    if (!Utils.isEmpty(dataType)) {
      String trimmed = dataType.trim();
      int typeId = ValueMetaFactory.getIdForValueMeta(trimmed);
      if (typeId > 0) {
        return typeId;
      }
      typeId = hopTypeIdFromSqlTypeName(trimmed);
      if (typeId > 0) {
        return typeId;
      }
    }
    if (sourceField != null) {
      if (sourceField.getHopType() > 0) {
        return sourceField.getHopType();
      }
      // Stored hop type may be unset while sourceDataType holds DATETIME / TIMESTAMP, etc.
      if (!Utils.isEmpty(sourceField.getSourceDataType())) {
        int fromStoredSql = hopTypeIdFromSqlTypeName(sourceField.getSourceDataType());
        if (fromStoredSql > 0) {
          return fromStoredSql;
        }
      }
    }
    return IValueMeta.TYPE_STRING;
  }

  /**
   * Maps common native SQL type names (including precision suffixes like {@code DATETIME(6)}) to
   * Hop types. Returns {@code 0} when unknown.
   */
  public static int hopTypeIdFromSqlTypeName(String sqlTypeName) {
    if (Utils.isEmpty(sqlTypeName)) {
      return 0;
    }
    String base = normalizeSqlTypeBase(sqlTypeName);
    if (Utils.isEmpty(base)) {
      return 0;
    }
    return switch (base) {
      case "TIMESTAMP",
          "TIMESTAMPTZ",
          "TIMESTAMP_TZ",
          "TIMESTAMP_LTZ",
          "TIMESTAMP_NTZ",
          "DATETIME",
          "DATETIME2",
          "SMALLDATETIME",
          "DATETIMEOFFSET" -> IValueMeta.TYPE_TIMESTAMP;
      case "DATE", "TIME", "TIME_WITH_TIME_ZONE", "TIMETZ" -> IValueMeta.TYPE_DATE;
      case "BOOL", "BOOLEAN", "BIT" -> IValueMeta.TYPE_BOOLEAN;
      case "TINYINT",
          "SMALLINT",
          "MEDIUMINT",
          "INT",
          "INT2",
          "INT4",
          "INT8",
          "INTEGER",
          "BIGINT",
          "SERIAL",
          "BIGSERIAL",
          "YEAR" -> IValueMeta.TYPE_INTEGER;
      case "FLOAT", "FLOAT4", "FLOAT8", "REAL", "DOUBLE", "DOUBLE PRECISION" -> IValueMeta
          .TYPE_NUMBER;
      case "DECIMAL", "NUMERIC", "NUMBER", "MONEY", "SMALLMONEY" -> IValueMeta.TYPE_BIGNUMBER;
      case "BINARY",
          "VARBINARY",
          "BLOB",
          "LONGBLOB",
          "MEDIUMBLOB",
          "TINYBLOB",
          "BYTEA",
          "RAW",
          "LONG RAW",
          "IMAGE" -> IValueMeta.TYPE_BINARY;
      case "CHAR",
          "NCHAR",
          "VARCHAR",
          "NVARCHAR",
          "VARCHAR2",
          "NVARCHAR2",
          "TEXT",
          "NTEXT",
          "TINYTEXT",
          "MEDIUMTEXT",
          "LONGTEXT",
          "CLOB",
          "NCLOB",
          "LONG",
          "BPCHAR",
          "CHARACTER",
          "CHARACTER VARYING" -> IValueMeta.TYPE_STRING;
      default -> 0;
    };
  }

  /**
   * Preferred label to store on satellite attributes / hub business keys when copying from a source
   * field: Hop type name when known, otherwise the native SQL type.
   */
  public static String preferredDataTypeLabel(SourceField sourceField) {
    if (sourceField == null) {
      return "";
    }
    int hopType = sourceField.getHopType();
    if (hopType <= 0) {
      hopType = hopTypeIdFromSqlTypeName(sourceField.getSourceDataType());
    }
    if (hopType > 0) {
      try {
        String hopTypeName = ValueMetaFactory.getValueMetaName(hopType);
        if (!Utils.isEmpty(hopTypeName) && !"-".equals(hopTypeName)) {
          return hopTypeName;
        }
      } catch (Exception ignored) {
        // Fall through to source SQL type.
      }
    }
    return Const.NVL(sourceField.getSourceDataType(), "");
  }

  static String normalizeSqlTypeBase(String sqlTypeName) {
    String upper = sqlTypeName.trim().toUpperCase(Locale.ROOT);
    // Strip schema qualifiers: "sys.datetime" → "datetime"
    int dot = upper.lastIndexOf('.');
    if (dot >= 0 && dot < upper.length() - 1) {
      upper = upper.substring(dot + 1);
    }
    // Strip precision/scale: DATETIME(6), NUMERIC(18,2), VARCHAR(50)
    int paren = upper.indexOf('(');
    if (paren > 0) {
      upper = upper.substring(0, paren).trim();
    }
    // Collapse multi-word types already handled in switch ("DOUBLE PRECISION")
    return upper.replaceAll("\\s+", " ").trim();
  }
}
