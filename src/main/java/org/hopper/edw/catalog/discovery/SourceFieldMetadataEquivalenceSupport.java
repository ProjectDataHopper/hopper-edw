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
package org.hopper.edw.catalog.discovery;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvDataTypeSupport;
import org.hopper.edw.datavault.metadata.DvSqlStringTypeSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;

/**
 * Compares {@link SourceField} length/precision using Hop value-meta semantics.
 *
 * <p>JDBC and Hop interpret dimensions differently for several types. Rules mirror {@code
 * Database.getDataTypeFromKnownSqlType} in Apache Hop: database precision is total significant
 * digits while Hop length is digits before the decimal; floating types often normalize both
 * dimensions to {@code -1}.
 *
 * <p>SingleStore/MySQL also misreport {@code VARCHAR(n)} display size as {@code 255} and sometimes
 * map {@code DATETIME} to String. Catalog contracts that match the physical table must not be
 * treated as drift against that JDBC noise.
 */
public final class SourceFieldMetadataEquivalenceSupport {

  private static final Class<?> PKG = RecordDefinitionSchemaDiffSupport.class;

  private SourceFieldMetadataEquivalenceSupport() {}

  public static boolean dimensionsEquivalent(SourceField stored, SourceField discovered) {
    return Utils.isEmpty(describeDimensionDifference(stored, discovered));
  }

  /** True when effective Hop types are the same (or Date/Timestamp compatible). */
  public static boolean typesEquivalent(SourceField stored, SourceField discovered) {
    return Utils.isEmpty(describeTypeDifference(stored, discovered));
  }

  public static String describeTypeDifference(SourceField stored, SourceField discovered) {
    if (stored == null || discovered == null) {
      return null;
    }
    int storedType = effectiveHopType(stored);
    int discoveredType = effectiveHopType(discovered);
    if (hopTypesCompatible(storedType, discoveredType)) {
      return null;
    }
    return BaseMessages.getString(
        PKG,
        "RecordDefinitionSchemaDiffSupport.Detail.Type",
        Const.NVL(stored.getSourceDataType(), valueMetaTypeName(storedType)),
        Const.NVL(discovered.getSourceDataType(), valueMetaTypeName(discoveredType)));
  }

  public static String describeDimensionDifference(SourceField stored, SourceField discovered) {
    if (stored == null || discovered == null) {
      return null;
    }
    int storedType = effectiveHopType(stored);
    int discoveredType = effectiveHopType(discovered);
    // Different types: dimension compare is meaningless (type path reports the issue).
    if (!hopTypesCompatible(storedType, discoveredType)) {
      return null;
    }

    List<String> parts = new ArrayList<>();
    switch (storedType) {
      case IValueMeta.TYPE_DATE,
          IValueMeta.TYPE_TIMESTAMP,
          IValueMeta.TYPE_BOOLEAN,
          IValueMeta.TYPE_BINARY -> {}
      case IValueMeta.TYPE_STRING -> addLengthDifference(parts, stored, discovered);
      case IValueMeta.TYPE_INTEGER -> addIntegerDifference(parts, stored, discovered);
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER ->
          addNumberDifference(parts, stored, discovered);
      default -> addGenericDifference(parts, stored, discovered);
    }
    return parts.isEmpty() ? null : String.join("; ", parts);
  }

  private static void addLengthDifference(
      List<String> parts, SourceField stored, SourceField discovered) {
    if (!stringLengthEquivalent(stored, discovered)) {
      parts.add(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionSchemaDiffSupport.Detail.Length",
              displayDimension(stored.getLength()),
              displayDimension(discovered.getLength())));
    }
  }

  private static void addIntegerDifference(
      List<String> parts, SourceField stored, SourceField discovered) {
    if (!integerLengthEquivalent(stored.getLength(), discovered.getLength())) {
      parts.add(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionSchemaDiffSupport.Detail.Length",
              displayDimension(stored.getLength()),
              displayDimension(discovered.getLength())));
    }
    if (!integerPrecisionEquivalent(stored.getPrecision(), discovered.getPrecision())) {
      parts.add(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionSchemaDiffSupport.Detail.Precision",
              displayDimension(stored.getPrecision()),
              displayDimension(discovered.getPrecision())));
    }
  }

  private static void addNumberDifference(
      List<String> parts, SourceField stored, SourceField discovered) {
    if (isFloatingNumber(stored) || isFloatingNumber(discovered)) {
      if (!floatingDimensionsEquivalent(stored, discovered)) {
        addGenericDifference(parts, stored, discovered);
      }
      return;
    }
    if (!decimalDimensionsEquivalent(stored, discovered)) {
      addGenericDifference(parts, stored, discovered);
    }
  }

  private static void addGenericDifference(
      List<String> parts, SourceField stored, SourceField discovered) {
    if (!rawDimensionEquivalent(stored.getLength(), discovered.getLength())) {
      parts.add(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionSchemaDiffSupport.Detail.Length",
              displayDimension(stored.getLength()),
              displayDimension(discovered.getLength())));
    }
    if (!rawDimensionEquivalent(stored.getPrecision(), discovered.getPrecision())) {
      parts.add(
          BaseMessages.getString(
              PKG,
              "RecordDefinitionSchemaDiffSupport.Detail.Precision",
              displayDimension(stored.getPrecision()),
              displayDimension(discovered.getPrecision())));
    }
  }

  private static boolean isFloatingNumber(SourceField field) {
    if (field == null) {
      return false;
    }
    String sourceType = Const.NVL(field.getSourceDataType(), "").toUpperCase();
    if (sourceType.contains("DOUBLE")
        || sourceType.contains("FLOAT")
        || sourceType.contains("REAL")) {
      return true;
    }
    int length = parseDimension(field.getLength());
    int precision = parseDimension(field.getPrecision());
    return precision <= 0 && length <= 0;
  }

  private static boolean floatingDimensionsEquivalent(SourceField stored, SourceField discovered) {
    return isUnsetFloatingDimension(stored.getLength(), stored.getPrecision())
        && isUnsetFloatingDimension(discovered.getLength(), discovered.getPrecision());
  }

  private static boolean isUnsetFloatingDimension(String length, String precision) {
    int parsedLength = parseDimension(length);
    int parsedPrecision = parseDimension(precision);
    return parsedLength <= 0 && parsedPrecision <= 0;
  }

  private static boolean decimalDimensionsEquivalent(SourceField stored, SourceField discovered) {
    int storedLength = parseDimension(stored.getLength());
    int storedPrecision = normalizeDecimalPrecision(parseDimension(stored.getPrecision()));
    int discoveredLength = parseDimension(discovered.getLength());
    int discoveredPrecision = normalizeDecimalPrecision(parseDimension(discovered.getPrecision()));

    if (storedPrecision != discoveredPrecision) {
      return false;
    }
    if (storedLength == discoveredLength) {
      return true;
    }
    if (storedPrecision <= 0) {
      return storedLength == discoveredLength;
    }
    if (storedLength > 0 && storedLength - storedPrecision == discoveredLength) {
      return true;
    }
    return discoveredLength > 0 && discoveredLength - discoveredPrecision == storedLength;
  }

  private static boolean integerPrecisionEquivalent(String left, String right) {
    return normalizeIntegerPrecision(parseDimension(left))
        == normalizeIntegerPrecision(parseDimension(right));
  }

  private static int normalizeIntegerPrecision(int precision) {
    return precision <= 0 ? 0 : precision;
  }

  private static int normalizeDecimalPrecision(int precision) {
    return precision < 0 ? 0 : precision;
  }

  /**
   * String length equivalence including SingleStore/MySQL JDBC display-size noise and LOB capacity
   * markers.
   */
  static boolean stringLengthEquivalent(SourceField stored, SourceField discovered) {
    if (stored == null || discovered == null) {
      return false;
    }
    // Both large-text / LOB: any reported length is capacity noise.
    if (DvSqlStringTypeSupport.isLargeTextSourceField(stored)
        && DvSqlStringTypeSupport.isLargeTextSourceField(discovered)) {
      return true;
    }
    if (DvSqlStringTypeSupport.isLargeTextSourceField(stored)
        || DvSqlStringTypeSupport.isLargeTextSourceField(discovered)) {
      // One side LOB, other side fixed VARCHAR — not equivalent.
      int other =
          DvSqlStringTypeSupport.isLargeTextSourceField(stored)
              ? parseDimension(discovered.getLength())
              : parseDimension(stored.getLength());
      return other >= DvSqlStringTypeSupport.CLOB_LENGTH || other <= 0;
    }
    return stringLengthEquivalent(stored.getLength(), discovered.getLength());
  }

  static boolean stringLengthEquivalent(String left, String right) {
    int leftLength = parseDimension(left);
    int rightLength = parseDimension(right);
    if (leftLength == rightLength) {
      return true;
    }
    // Classic SingleStore/MySQL getColumnDisplaySize noise vs declared COLUMN_SIZE.
    if (isClassicJdbcDisplaySizeNoise(leftLength, rightLength)
        || isClassicJdbcDisplaySizeNoise(rightLength, leftLength)) {
      return true;
    }
    return false;
  }

  /**
   * {@code suspicious} looks like a bogus display size for declared character length {@code
   * declared}.
   */
  static boolean isClassicJdbcDisplaySizeNoise(int suspicious, int declared) {
    if (declared <= 0 || suspicious <= 0 || suspicious == declared) {
      return false;
    }
    if (suspicious == DvSqlStringTypeSupport.TINYTEXT_CAPACITY
        && declared != DvSqlStringTypeSupport.TINYTEXT_CAPACITY
        && declared < DvSqlStringTypeSupport.CLOB_LENGTH) {
      return true;
    }
    return suspicious == declared * 3 || suspicious == declared * 4;
  }

  static int effectiveHopType(SourceField field) {
    if (field == null) {
      return IValueMeta.TYPE_NONE;
    }
    return DvDataTypeSupport.effectiveHopTypeId(field.getHopType(), field.getSourceDataType());
  }

  static boolean hopTypesCompatible(int left, int right) {
    if (left == right) {
      return true;
    }
    if ((left == IValueMeta.TYPE_NUMBER && right == IValueMeta.TYPE_BIGNUMBER)
        || (left == IValueMeta.TYPE_BIGNUMBER && right == IValueMeta.TYPE_NUMBER)) {
      return true;
    }
    return (left == IValueMeta.TYPE_DATE && right == IValueMeta.TYPE_TIMESTAMP)
        || (left == IValueMeta.TYPE_TIMESTAMP && right == IValueMeta.TYPE_DATE);
  }

  private static String valueMetaTypeName(int hopType) {
    try {
      return org.apache.hop.core.row.value.ValueMetaFactory.getValueMetaName(hopType);
    } catch (Exception e) {
      return String.valueOf(hopType);
    }
  }

  /**
   * Compares integer lengths using Hop / JDBC canonical physical families.
   *
   * <p>Catalog lengths often reflect Hop conventions while JDBC {@code COLUMN_SIZE} uses
   * decimal-digit widths for fixed integer types:
   *
   * <ul>
   *   <li>SMALLINT — Hop {@code 4}, JDBC often {@code 5}
   *   <li>INTEGER — Hop {@code 9}, JDBC often {@code 10} (max digits of signed int32)
   *   <li>BIGINT — Hop {@code 15}, JDBC often {@code 19}
   * </ul>
   *
   * Catalog display widths (for example {@code 3} for a 0–100 score) that round-trip as SMALLINT
   * must not flag as drift against JDBC {@code 4}/{@code 5}.
   */
  private static boolean integerLengthEquivalent(String left, String right) {
    int leftLength = parseDimension(left);
    int rightLength = parseDimension(right);
    if (leftLength == rightLength) {
      return true;
    }
    return canonicalHopIntegerLength(leftLength) == canonicalHopIntegerLength(rightLength);
  }

  /**
   * Maps a reported integer length into a Hop physical family token: {@code 4} SMALLINT, {@code 9}
   * INTEGER, {@code 15} BIGINT. Unclassified lengths are returned unchanged.
   */
  private static int canonicalHopIntegerLength(int length) {
    if (length <= 0) {
      return length;
    }
    // SMALLINT family: Hop 4, JDBC 5, and smaller display widths that DDL maps to SMALLINT.
    if (length <= 5) {
      return 4;
    }
    // INTEGER family: Hop 9, JDBC 10 (signed int32 digit width).
    if (length <= 10) {
      return 9;
    }
    // BIGINT family: Hop 15, JDBC 19 (signed int64 digit width).
    if (length <= 19) {
      return 15;
    }
    return length;
  }

  private static boolean rawDimensionEquivalent(String left, String right) {
    return Const.NVL(left, "").trim().equals(Const.NVL(right, "").trim());
  }

  static int parseDimension(String value) {
    if (Utils.isEmpty(value)) {
      return -1;
    }
    String trimmed = value.trim();
    if ("-1".equals(trimmed)) {
      return -1;
    }
    try {
      return Integer.parseInt(trimmed);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static String displayDimension(String value) {
    return Const.NVL(value, "");
  }
}
