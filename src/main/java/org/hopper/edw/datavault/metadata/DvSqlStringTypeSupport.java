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
package org.hopper.edw.datavault.metadata;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/**
 * MySQL / SingleStore / MariaDB (and generic) large-text SQL type handling for metadata import and
 * source→target length validation.
 *
 * <p>JDBC drivers for these engines often report {@code LONGTEXT}/{@code TEXT} with a useless
 * display size of {@code 255}, while other sources report multi-megabyte or multi-gigabyte lengths
 * for the same logical LOB type. That produces false "source length exceeds target" validation
 * errors even when both sides are unbounded text.
 */
public final class DvSqlStringTypeSupport {

  /** Hop's conventional marker for CLOB / unbounded text (see {@link DatabaseMeta#CLOB_LENGTH}). */
  public static final int CLOB_LENGTH = DatabaseMeta.CLOB_LENGTH;

  public static final int TINYTEXT_CAPACITY = 255;
  public static final int TEXT_CAPACITY = 65_535;
  public static final int MEDIUMTEXT_CAPACITY = 16_777_215;

  private DvSqlStringTypeSupport() {}

  /** True for MySQL-family LOB/text types and Hop CLOB_LENGTH markers. */
  public static boolean isLargeTextSqlType(String sqlTypeName) {
    String base = DvDataTypeSupport.normalizeSqlTypeBase(sqlTypeName);
    if (Utils.isEmpty(base)) {
      return false;
    }
    return switch (base) {
      case "LONGTEXT",
              "MEDIUMTEXT",
              "TEXT",
              "TINYTEXT",
              "CLOB",
              "NCLOB",
              "LONGVARCHAR",
              "LONGNVARCHAR" ->
          true;
      default -> false;
    };
  }

  public static boolean isLargeTextValueMeta(IValueMeta meta) {
    if (meta == null || meta.getType() != IValueMeta.TYPE_STRING) {
      return false;
    }
    if (meta.getLength() >= CLOB_LENGTH) {
      return true;
    }
    return isLargeTextSqlType(meta.getOriginalColumnTypeName());
  }

  public static boolean isLargeTextSourceField(SourceField field) {
    if (field == null) {
      return false;
    }
    if (isLargeTextSqlType(field.getSourceDataType())) {
      return true;
    }
    try {
      int length = Integer.parseInt(org.apache.hop.core.Const.NVL(field.getLength(), "-1").trim());
      return length >= CLOB_LENGTH;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Canonical storage capacity for a SQL string type given a possibly wrong JDBC display size.
   *
   * <p>For {@code LONGTEXT}/{@code CLOB}, always returns at least {@link #CLOB_LENGTH} so a bogus
   * display size of 255 does not look like {@code VARCHAR(255)}.
   */
  public static int capacityForSqlStringType(String sqlTypeName, int reportedLength) {
    String base = DvDataTypeSupport.normalizeSqlTypeBase(sqlTypeName);
    if (Utils.isEmpty(base)) {
      return reportedLength;
    }
    int typeCap =
        switch (base) {
          case "LONGTEXT", "CLOB", "NCLOB", "LONGVARCHAR", "LONGNVARCHAR" -> CLOB_LENGTH;
          case "MEDIUMTEXT" -> MEDIUMTEXT_CAPACITY;
          case "TEXT" -> TEXT_CAPACITY;
          case "TINYTEXT" -> TINYTEXT_CAPACITY;
          default -> -1;
        };
    if (typeCap < 0) {
      return reportedLength;
    }
    if (reportedLength <= 0) {
      return typeCap;
    }
    // Prefer the larger of type capacity and reported length (handles Integer.MAX_VALUE LOBs).
    return Math.max(typeCap, reportedLength);
  }

  /**
   * Fixes {@link IValueMeta} string length when JDBC reports a misleading size.
   *
   * <ul>
   *   <li>Large text types ({@code LONGTEXT}, …) with display size 255 → type capacity
   *   <li>{@code VARCHAR}/{@code CHAR}: prefer {@link IValueMeta#getOriginalPrecision()} (JDBC
   *       {@code COLUMN_SIZE}) when display size is inflated or a stale default (e.g. 255)
   * </ul>
   */
  public static void normalizeStringLength(IValueMeta meta) {
    if (meta == null || meta.getType() != IValueMeta.TYPE_STRING) {
      return;
    }
    String sqlType = meta.getOriginalColumnTypeName();
    if (isLargeTextSqlType(sqlType)) {
      int adjusted = capacityForSqlStringType(sqlType, meta.getLength());
      if (adjusted > 0 && adjusted != meta.getLength()) {
        meta.setLength(adjusted);
      }
      return;
    }
    // VARCHAR / CHAR / NVARCHAR: COLUMN_SIZE (originalPrecision) is the declared character length.
    // getColumnDisplaySize can be wrong on MySQL/SingleStore (utf8 multipliers or default 255).
    int declared = meta.getOriginalPrecision();
    if (declared <= 0) {
      // TYPE_NAME may carry VARCHAR(150) when ResultSetMetaData left originalPrecision empty.
      declared = DvDataTypeSupport.characterLengthFromSqlTypeName(sqlType);
    }
    int display = meta.getLength();
    if (declared <= 0) {
      return;
    }
    String base = DvDataTypeSupport.normalizeSqlTypeBase(sqlType);
    boolean characterType =
        Utils.isEmpty(base)
            || base.contains("CHAR")
            || "BPCHAR".equals(base)
            || "CHARACTER".equals(base)
            || base.startsWith("CHARACTER ");
    if (!characterType && !Utils.isEmpty(base)) {
      return;
    }
    if (display <= 0
        || display == declared
        || display == declared * 3
        || display == declared * 4
        || (display == TINYTEXT_CAPACITY
            && declared != TINYTEXT_CAPACITY
            && declared < CLOB_LENGTH)) {
      // Prefer declared size when display is missing, equal, utf8-multiplied, or the classic 255
      // default while COLUMN_SIZE holds the real VARCHAR(n).
      if (display != declared) {
        meta.setLength(declared);
      }
    }
  }

  /**
   * Length to use for overflow validation. Large-text types use type capacity so LOB↔LOB never
   * fails on display-size noise.
   */
  public static int lengthForValidation(IValueMeta meta) {
    if (meta == null) {
      return -1;
    }
    if (meta.getType() != IValueMeta.TYPE_STRING && meta.getType() != IValueMeta.TYPE_BINARY) {
      return meta.getLength();
    }
    if (isLargeTextSqlType(meta.getOriginalColumnTypeName())) {
      return capacityForSqlStringType(meta.getOriginalColumnTypeName(), meta.getLength());
    }
    if (meta.getLength() >= CLOB_LENGTH) {
      return meta.getLength();
    }
    return meta.getLength();
  }

  /**
   * True when length overflow must not be raised: both sides are large-text/LOB, or the target is
   * unbounded large text.
   */
  public static boolean skipStringLengthOverflowCheck(
      IValueMeta sourceMeta, IValueMeta targetMeta) {
    if (sourceMeta == null || targetMeta == null) {
      return false;
    }
    boolean sourceLarge =
        isLargeTextValueMeta(sourceMeta) || lengthForValidation(sourceMeta) >= CLOB_LENGTH;
    boolean targetLarge =
        isLargeTextValueMeta(targetMeta) || lengthForValidation(targetMeta) >= CLOB_LENGTH;
    // Target LONGTEXT / CLOB can hold any source string length we model.
    if (targetLarge) {
      return true;
    }
    // Both large (e.g. source CLOB_LENGTH, target TEXT with fixed display bug fixed to capacity).
    return sourceLarge && targetLarge;
  }

  /** Apply large-text length normalization to a {@link SourceField}. */
  public static void normalizeSourceFieldLength(SourceField field) {
    if (field == null) {
      return;
    }
    String sqlType = field.getSourceDataType();
    if (!isLargeTextSqlType(sqlType)) {
      return;
    }
    int reported = -1;
    try {
      if (!Utils.isEmpty(field.getLength())) {
        reported = Integer.parseInt(field.getLength().trim());
      }
    } catch (NumberFormatException ignored) {
      reported = -1;
    }
    int adjusted = capacityForSqlStringType(sqlType, reported);
    if (adjusted > 0) {
      field.setLength(String.valueOf(adjusted));
    }
  }
}
