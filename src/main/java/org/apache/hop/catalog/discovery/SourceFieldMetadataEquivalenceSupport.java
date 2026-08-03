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
package org.apache.hop.catalog.discovery;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;

/**
 * Compares {@link SourceField} length/precision using Hop value-meta semantics.
 *
 * <p>JDBC and Hop interpret dimensions differently for several types. Rules mirror {@code
 * Database.getDataTypeFromKnownSqlType} in Apache Hop: database precision is total significant
 * digits while Hop length is digits before the decimal; floating types often normalize both
 * dimensions to {@code -1}.
 */
public final class SourceFieldMetadataEquivalenceSupport {

  private static final Class<?> PKG = RecordDefinitionSchemaDiffSupport.class;

  private SourceFieldMetadataEquivalenceSupport() {}

  public static boolean dimensionsEquivalent(SourceField stored, SourceField discovered) {
    return Utils.isEmpty(describeDimensionDifference(stored, discovered));
  }

  public static String describeDimensionDifference(SourceField stored, SourceField discovered) {
    if (stored == null || discovered == null) {
      return null;
    }
    if (stored.getHopType() != discovered.getHopType()) {
      return null;
    }

    List<String> parts = new ArrayList<>();
    switch (stored.getHopType()) {
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
    if (!stringLengthEquivalent(stored.getLength(), discovered.getLength())) {
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

  private static boolean stringLengthEquivalent(String left, String right) {
    return parseDimension(left) == parseDimension(right);
  }

  /**
   * Compares integer lengths using Hop JDBC canonical sizes.
   *
   * <p>Catalog lengths often reflect display width (for example {@code 3} for a 0-100 score) while
   * JDBC discovery normalizes physical types to fixed lengths ({@code SMALLINT -> 4}, {@code
   * INTEGER -> 9}, signed {@code BIGINT -> 15}). PostgreSQL DDL generation in Hop also maps lengths
   * below 5 to {@code SMALLINT}, so a stored length of 3 round-trips as 4.
   */
  private static boolean integerLengthEquivalent(String left, String right) {
    int leftLength = parseDimension(left);
    int rightLength = parseDimension(right);
    if (leftLength == rightLength) {
      return true;
    }
    return canonicalHopIntegerLength(leftLength) == canonicalHopIntegerLength(rightLength);
  }

  private static int canonicalHopIntegerLength(int length) {
    if (length <= 0) {
      return length;
    }
    if (length < 5) {
      return 4;
    }
    if (length <= 9) {
      return 9;
    }
    if (length <= 15) {
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
