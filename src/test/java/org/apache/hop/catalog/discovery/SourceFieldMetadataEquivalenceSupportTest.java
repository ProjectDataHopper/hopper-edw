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
package org.apache.hop.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class SourceFieldMetadataEquivalenceSupportTest {

  @Test
  void timestampLengthDifferencesAreIgnored() {
    SourceField stored = field("load_date", "Timestamp", IValueMeta.TYPE_TIMESTAMP);
    stored.setLength("");
    SourceField discovered = field("load_date", "Timestamp", IValueMeta.TYPE_TIMESTAMP);
    discovered.setLength("6");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
    assertNull(
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered));
  }

  @Test
  void dateLengthDifferencesAreIgnored() {
    SourceField stored = field("order_date", "Date", IValueMeta.TYPE_DATE);
    stored.setLength("10");
    SourceField discovered = field("order_date", "Date", IValueMeta.TYPE_DATE);
    discovered.setLength("");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void integerPrecisionZeroAndEmptyAreEquivalent() {
    SourceField stored = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("9");
    stored.setPrecision("0");
    SourceField discovered = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("9");
    discovered.setPrecision("");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void decimalNumberDimensionsMatchWhenHopNormalized() {
    SourceField stored = field("unit_price", "Number", IValueMeta.TYPE_NUMBER);
    stored.setLength("9");
    stored.setPrecision("2");
    SourceField discovered = field("unit_price", "Number", IValueMeta.TYPE_NUMBER);
    discovered.setLength("9");
    discovered.setPrecision("2");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void decimalNumberDimensionsMatchWhenOneSideUsesJdbcTotalDigits() {
    SourceField stored = field("unit_price", "Number", IValueMeta.TYPE_NUMBER);
    stored.setLength("11");
    stored.setPrecision("2");
    SourceField discovered = field("unit_price", "Number", IValueMeta.TYPE_NUMBER);
    discovered.setLength("9");
    discovered.setPrecision("2");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void floatingNumberUnsetDimensionsAreEquivalent() {
    SourceField stored = field("ratio", "Double", IValueMeta.TYPE_NUMBER);
    stored.setLength("");
    stored.setPrecision("");
    SourceField discovered = field("ratio", "Double", IValueMeta.TYPE_NUMBER);
    discovered.setLength("-1");
    discovered.setPrecision("0");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void integerLengthsMatchAcrossHopJdbcCanonicalSizes() {
    SourceField stored = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("3");
    stored.setPrecision("0");
    SourceField discovered = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("4");
    discovered.setPrecision("0");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
    assertNull(
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered));
  }

  @Test
  void integerLengthsMatchHopNineVsJdbcTen() {
    // PostgreSQL INTEGER COLUMN_SIZE is 10; Hop catalog contracts use 9.
    SourceField stored = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("9");
    stored.setPrecision("0");
    SourceField discovered = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("10");
    discovered.setPrecision("0");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
    assertNull(
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered));
  }

  @Test
  void smallintLengthsMatchHopFourVsJdbcFive() {
    // PostgreSQL SMALLINT COLUMN_SIZE is 5; Hop catalog often stores 4.
    SourceField stored = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("4");
    stored.setPrecision("0");
    SourceField discovered = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("5");
    discovered.setPrecision("0");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
    assertNull(
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered));
  }

  @Test
  void integerLengthsInDifferentPhysicalFamiliesAreReported() {
    SourceField stored = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("3");
    stored.setPrecision("0");
    SourceField discovered = field("demo_score", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("9");
    discovered.setPrecision("0");

    assertFalse(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
  }

  @Test
  void stringLengthDifferencesAreReported() {
    SourceField stored = field("segment", "String", IValueMeta.TYPE_STRING);
    stored.setLength("50");
    SourceField discovered = field("segment", "String", IValueMeta.TYPE_STRING);
    discovered.setLength("100");

    assertFalse(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(stored, discovered));
    String details =
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered);
    assertTrue(details != null && details.contains("length"), details);
    // Convention: expected (stored/catalog) → actual (discovered/live). Growth 50 → 100.
    assertTrue(
        details.contains("50") && details.contains("100"),
        "details should mention both lengths: " + details);
    int idx50 = details.indexOf("50");
    int idx100 = details.indexOf("100");
    assertTrue(
        idx50 >= 0 && idx100 > idx50,
        "expected length must appear before actual length (50 before 100): " + details);
  }

  @Test
  void singleStoreVarchar150VsJdbcDisplay255IsEquivalent() {
    // Catalog matches physical VARCHAR(150); JDBC rediscovery reports display size 255.
    SourceField catalog = field("customer_name", "VARCHAR", IValueMeta.TYPE_STRING);
    catalog.setLength("150");
    SourceField jdbc = field("customer_name", "VARCHAR", IValueMeta.TYPE_STRING);
    jdbc.setLength("255");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(catalog, jdbc));
    assertTrue(SourceFieldMetadataEquivalenceSupport.typesEquivalent(catalog, jdbc));
  }

  @Test
  void datetimeStoredAsStringIsTypeEquivalentToTimestamp() {
    SourceField catalog = field("load_dts", "DATETIME(6)", IValueMeta.TYPE_TIMESTAMP);
    catalog.setLength("6");
    SourceField jdbc = field("load_dts", "DATETIME", IValueMeta.TYPE_STRING);
    jdbc.setLength("255");

    assertTrue(SourceFieldMetadataEquivalenceSupport.typesEquivalent(catalog, jdbc));
  }

  @Test
  void longtextLengthsAreEquivalentDespiteDisplay255() {
    SourceField catalog = field("notes", "LONGTEXT", IValueMeta.TYPE_STRING);
    catalog.setLength(String.valueOf(org.apache.hop.core.database.DatabaseMeta.CLOB_LENGTH));
    SourceField jdbc = field("notes", "LONGTEXT", IValueMeta.TYPE_STRING);
    jdbc.setLength("255");

    assertTrue(SourceFieldMetadataEquivalenceSupport.dimensionsEquivalent(catalog, jdbc));
  }

  private static SourceField field(String name, String sourceDataType, int hopType) {
    SourceField field = new SourceField(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    return field;
  }
}
