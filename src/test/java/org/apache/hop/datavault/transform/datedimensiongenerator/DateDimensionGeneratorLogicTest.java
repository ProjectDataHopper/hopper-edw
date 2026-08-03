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
package org.apache.hop.datavault.transform.datedimensiongenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.DateRange;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.PreparedField;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DateDimensionGeneratorLogicTest {

  private static final Variables VARIABLES = new Variables();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveDateRangeAcceptsInclusiveBounds() throws HopException {
    DateRange range =
        DateDimensionGeneratorLogic.resolveDateRange("2024-01-01", "2024-01-03", VARIABLES);

    assertEquals(LocalDate.of(2024, 1, 1), range.startDate());
    assertEquals(LocalDate.of(2024, 1, 3), range.endDate());
    assertEquals(
        3, DateDimensionGeneratorLogic.dayCountInclusive(range.startDate(), range.endDate()));
  }

  @Test
  void resolveDateRangeRejectsStartAfterEnd() {
    assertThrows(
        HopException.class,
        () -> DateDimensionGeneratorLogic.resolveDateRange("2024-02-01", "2024-01-01", VARIABLES));
  }

  @Test
  void getFieldsAddsConfiguredMetadata() throws HopPluginException, HopTransformException {
    DateDimensionGeneratorMeta meta = DateDimensionGeneratorMetaFactory.createDefault();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "generator", null, null, VARIABLES, (IHopMetadataProvider) null);

    assertEquals(meta.getFields().size(), rowMeta.size());
    assertTrue(rowMeta.indexOfValue("date_key") >= 0);
    assertEquals(
        IValueMeta.TYPE_INTEGER, rowMeta.getValueMeta(rowMeta.indexOfValue("date_key")).getType());
    assertEquals(
        IValueMeta.TYPE_DATE, rowMeta.getValueMeta(rowMeta.indexOfValue("full_date")).getType());
  }

  @Test
  void buildRowUsesLocaleSpecificMonthName() throws Exception {
    List<PreparedField> prepared =
        DateDimensionGeneratorLogic.prepareFields(
            List.of(
                new DateDimensionGeneratorField(
                    "month_name_en", IValueMeta.TYPE_STRING, "20", "0", "MMMM", "en_US"),
                new DateDimensionGeneratorField(
                    "month_name_nl", IValueMeta.TYPE_STRING, "20", "0", "MMMM", "nl_NL")),
            "generator",
            VARIABLES);

    Object[] row = DateDimensionGeneratorLogic.buildRow(LocalDate.of(2024, 3, 15), prepared);

    assertEquals("March", row[0]);
    assertEquals("maart", row[1]);
  }

  @Test
  void buildRowComputesDateKeyAndWeekendFlag() throws Exception {
    List<PreparedField> prepared =
        DateDimensionGeneratorLogic.prepareFields(
            List.of(
                new DateDimensionGeneratorField(
                    "date_key", IValueMeta.TYPE_INTEGER, "8", "0", "yyyyMMdd", ""),
                new DateDimensionGeneratorField(
                    "is_weekend", IValueMeta.TYPE_BOOLEAN, "", "", "@is_weekend", "")),
            "generator",
            VARIABLES);

    Object[] weekday = DateDimensionGeneratorLogic.buildRow(LocalDate.of(2024, 1, 1), prepared);
    Object[] weekend = DateDimensionGeneratorLogic.buildRow(LocalDate.of(2024, 1, 6), prepared);

    assertEquals(20240101L, weekday[0]);
    assertFalse((Boolean) weekday[1]);
    assertEquals(20240106L, weekend[0]);
    assertTrue((Boolean) weekend[1]);
  }

  @Test
  void buildRowProducesDateValueForFullDateField() throws Exception {
    List<PreparedField> prepared =
        DateDimensionGeneratorLogic.prepareFields(
            List.of(
                new DateDimensionGeneratorField(
                    "full_date", IValueMeta.TYPE_DATE, "", "", "yyyy-MM-dd", "")),
            "generator",
            VARIABLES);

    Object[] row = DateDimensionGeneratorLogic.buildRow(LocalDate.of(2024, 6, 30), prepared);

    assertTrue(row[0] instanceof Date);
    IRowMeta rowMeta =
        DateDimensionGeneratorLogic.buildOutputRowMeta(
            List.of(
                new DateDimensionGeneratorField(
                    "full_date", IValueMeta.TYPE_DATE, "", "", "yyyy-MM-dd", "")),
            "generator",
            VARIABLES);
    assertEquals("2024-06-30", rowMeta.getString(row, 0));
  }

  @Test
  void parseLocaleFallsBackToDefaultForBlankLocale() {
    assertEquals(Locale.getDefault(), DateDimensionGeneratorLogic.parseLocale("", VARIABLES));
    assertEquals(Locale.US, DateDimensionGeneratorLogic.parseLocale("en_US", VARIABLES));
  }

  @Test
  void outputValueMetaDoesNotUseFormatterMaskForIntegerFields()
      throws HopPluginException, HopTransformException {
    DateDimensionGeneratorMeta meta = DateDimensionGeneratorMetaFactory.createDefault();
    RowMeta rowMeta = new RowMeta();
    meta.getFields(rowMeta, "generator", null, null, VARIABLES, (IHopMetadataProvider) null);

    IValueMeta dateKey = rowMeta.getValueMeta(rowMeta.indexOfValue("date_key"));
    IValueMeta dayOfWeek = rowMeta.getValueMeta(rowMeta.indexOfValue("day_of_week"));

    assertNotEquals("yyyyMMdd", dateKey.getConversionMask());
    assertNotEquals("@day_of_week", dayOfWeek.getConversionMask());
  }

  @Test
  void dayOfWeekUsesIsoDayNumber() throws Exception {
    List<PreparedField> prepared =
        DateDimensionGeneratorLogic.prepareFields(
            List.of(
                new DateDimensionGeneratorField(
                    "day_of_week", IValueMeta.TYPE_INTEGER, "1", "0", "@day_of_week", "")),
            "generator",
            VARIABLES);

    // 2000-01-01 was a Saturday (ISO day 6).
    Object[] row = DateDimensionGeneratorLogic.buildRow(LocalDate.of(2000, 1, 1), prepared);
    assertEquals(6L, row[0]);
  }
}
