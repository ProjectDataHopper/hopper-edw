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
      // Explicit Hop type names (String, Integer, Timestamp, …) always win — do not override
      // with source SQL types (user may intentionally store a different layout type).
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
      // Correct JDBC / default mis-maps (DATETIME or Integer stored as hopType String).
      return resolveSourceFieldHopType(sourceField);
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
              "DATETIMEOFFSET" ->
          IValueMeta.TYPE_TIMESTAMP;
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
              "YEAR" ->
          IValueMeta.TYPE_INTEGER;
      case "FLOAT", "FLOAT4", "FLOAT8", "REAL", "DOUBLE", "DOUBLE PRECISION" ->
          IValueMeta.TYPE_NUMBER;
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
              "IMAGE" ->
          IValueMeta.TYPE_BINARY;
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
              "CHARACTER VARYING" ->
          IValueMeta.TYPE_STRING;
      default -> 0;
    };
  }

  /**
   * Effective Hop type id for a catalog/source field used by import-from-source, validation, and
   * row-meta derivation.
   *
   * <p>Corrects a stored {@link IValueMeta#TYPE_STRING} (or unset {@code hopType}) when {@link
   * SourceField#getSourceDataType()} is a known Hop type name or native SQL type (e.g. {@code
   * Integer}, {@code int4}, {@code DATETIME(6)}). This matches how catalog layouts often keep the
   * real type label in {@code sourceDataType} while {@code hopType} was defaulted to String.
   */
  public static int resolveSourceFieldHopType(SourceField sourceField) {
    if (sourceField == null) {
      return IValueMeta.TYPE_STRING;
    }
    int hopType = sourceField.getHopType();
    String label = sourceField.getSourceDataType();

    // Prefer SQL / native type correction over a stale TYPE_STRING hopType (JDBC noise, defaults).
    hopType = effectiveHopTypeId(hopType, label);

    // When still unset or still String, try the label as an explicit Hop type name (Integer, …).
    if ((hopType <= 0 || hopType == IValueMeta.TYPE_STRING) && !Utils.isEmpty(label)) {
      int fromHopName = ValueMetaFactory.getIdForValueMeta(label.trim());
      if (fromHopName > 0 && (hopType <= 0 || fromHopName != IValueMeta.TYPE_STRING)) {
        hopType = fromHopName;
      }
    }

    return hopType > 0 ? hopType : IValueMeta.TYPE_STRING;
  }

  /**
   * Preferred label to store on satellite attributes / hub business keys when copying from a source
   * field: Hop type name when known, otherwise the native SQL type.
   *
   * <p>Never returns empty for a non-null field: unresolved types become {@code String} so hub/sat
   * import-from-source always fills the Data type column (required for vault DDL).
   */
  public static String preferredDataTypeLabel(SourceField sourceField) {
    if (sourceField == null) {
      return "String";
    }
    int hopType = resolveSourceFieldHopType(sourceField);
    try {
      String hopTypeName = ValueMetaFactory.getValueMetaName(hopType);
      if (!Utils.isEmpty(hopTypeName)
          && !"-".equals(hopTypeName)
          && !"None".equalsIgnoreCase(hopTypeName)) {
        // Only trust the Hop name when it is not a String default masking a free-text SQL label.
        if (hopType != IValueMeta.TYPE_STRING) {
          return hopTypeName;
        }
        String sourceDataType = Const.NVL(sourceField.getSourceDataType(), "").trim();
        if (Utils.isEmpty(sourceDataType)
            || "String".equalsIgnoreCase(sourceDataType)
            || hopTypeIdFromSqlTypeName(sourceDataType) == IValueMeta.TYPE_STRING
            || ValueMetaFactory.getIdForValueMeta(sourceDataType) == IValueMeta.TYPE_STRING) {
          return hopTypeName;
        }
        // Unrecognized free-text label with hopType String: keep the source label for DDL/debug.
        return sourceDataType;
      }
    } catch (Exception ignored) {
      // Fall through to source SQL type / String default.
    }
    String sourceDataType = Const.NVL(sourceField.getSourceDataType(), "").trim();
    if (!Utils.isEmpty(sourceDataType)) {
      return sourceDataType;
    }
    return "String";
  }

  /**
   * True when {@code dataType} is a known Hop type name ({@code String}, {@code Integer}, …) or a
   * recognized native SQL type label. Empty/null labels return false.
   */
  public static boolean isKnownDataTypeLabel(String dataType) {
    if (Utils.isEmpty(dataType)) {
      return false;
    }
    String trimmed = dataType.trim();
    if (ValueMetaFactory.getIdForValueMeta(trimmed) > 0) {
      return true;
    }
    return hopTypeIdFromSqlTypeName(trimmed) > 0;
  }

  static String normalizeSqlTypeBase(String sqlTypeName) {
    if (Utils.isEmpty(sqlTypeName)) {
      return "";
    }
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

  /**
   * Corrects a stored Hop type when JDBC previously mapped a non-string SQL type (e.g. {@code
   * DATETIME}) to {@link IValueMeta#TYPE_STRING}. Prefer the SQL type mapping in that case.
   *
   * @param hopType stored hop type id (may be wrong {@code TYPE_STRING})
   * @param sqlTypeName native SQL type name (may include {@code DATETIME(6)})
   * @return effective hop type id
   */
  public static int effectiveHopTypeId(int hopType, String sqlTypeName) {
    int fromSql = hopTypeIdFromSqlTypeName(sqlTypeName);
    if (fromSql <= 0) {
      return hopType > 0 ? hopType : IValueMeta.TYPE_STRING;
    }
    if (hopType <= 0) {
      return fromSql;
    }
    // JDBC ResultSetMetaData on SingleStore/MySQL sometimes maps DATETIME/TIMESTAMP/DATE to String.
    if (hopType == IValueMeta.TYPE_STRING && fromSql != IValueMeta.TYPE_STRING) {
      return fromSql;
    }
    return hopType;
  }

  /**
   * Fractional-second precision from a SQL type label such as {@code DATETIME(6)} or {@code
   * TIMESTAMP(3)}. Returns {@code -1} when not present or not a temporal type.
   */
  public static int fractionalSecondsFromSqlTypeName(String sqlTypeName) {
    if (Utils.isEmpty(sqlTypeName)) {
      return -1;
    }
    int hop = hopTypeIdFromSqlTypeName(sqlTypeName);
    if (hop != IValueMeta.TYPE_TIMESTAMP && hop != IValueMeta.TYPE_DATE) {
      return -1;
    }
    return firstParentheticalInt(sqlTypeName);
  }

  /**
   * Declared character length from labels such as {@code VARCHAR(150)} or {@code NVARCHAR(50)}.
   * Returns {@code -1} when not present.
   */
  public static int characterLengthFromSqlTypeName(String sqlTypeName) {
    String base = normalizeSqlTypeBase(sqlTypeName);
    if (Utils.isEmpty(base)) {
      return -1;
    }
    boolean characterType =
        base.contains("CHAR")
            || "BPCHAR".equals(base)
            || "CHARACTER".equals(base)
            || base.startsWith("CHARACTER ");
    if (!characterType) {
      return -1;
    }
    return firstParentheticalInt(sqlTypeName);
  }

  private static int firstParentheticalInt(String sqlTypeName) {
    if (Utils.isEmpty(sqlTypeName)) {
      return -1;
    }
    int open = sqlTypeName.indexOf('(');
    int close = sqlTypeName.indexOf(')', open + 1);
    if (open < 0 || close <= open + 1) {
      return -1;
    }
    String inside = sqlTypeName.substring(open + 1, close).trim();
    // NUMERIC(18,2) → take first number; DATETIME(6) → 6
    int comma = inside.indexOf(',');
    if (comma > 0) {
      inside = inside.substring(0, comma).trim();
    }
    try {
      int value = Integer.parseInt(inside);
      return value >= 0 ? value : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
