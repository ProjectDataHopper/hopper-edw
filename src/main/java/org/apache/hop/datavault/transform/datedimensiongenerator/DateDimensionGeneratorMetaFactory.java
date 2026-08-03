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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IValueMeta;

/** Factory helpers for standard calendar date dimension field definitions. */
public final class DateDimensionGeneratorMetaFactory {

  public static final String DEFAULT_START_DATE = "2000-01-01";
  public static final String DEFAULT_END_DATE = "2030-12-31";

  private DateDimensionGeneratorMetaFactory() {}

  public static DateDimensionGeneratorMeta createDefault() {
    DateDimensionGeneratorMeta meta = new DateDimensionGeneratorMeta();
    meta.setStartDate(DEFAULT_START_DATE);
    meta.setEndDate(DEFAULT_END_DATE);
    meta.setFields(defaultFields());
    return meta;
  }

  public static List<DateDimensionGeneratorField> defaultFields() {
    List<DateDimensionGeneratorField> fields = new ArrayList<>();
    fields.add(field("date_key", IValueMeta.TYPE_INTEGER, "8", "0", "yyyyMMdd", ""));
    fields.add(field("full_date", IValueMeta.TYPE_DATE, "", "", "yyyy-MM-dd", ""));
    fields.add(field("day_of_week", IValueMeta.TYPE_INTEGER, "1", "0", "@day_of_week", ""));
    fields.add(field("day_of_month", IValueMeta.TYPE_INTEGER, "2", "0", "d", ""));
    fields.add(field("day_of_year", IValueMeta.TYPE_INTEGER, "3", "0", "D", ""));
    fields.add(field("week_of_year", IValueMeta.TYPE_INTEGER, "2", "0", "w", ""));
    fields.add(field("month", IValueMeta.TYPE_INTEGER, "2", "0", "M", ""));
    fields.add(field("month_name", IValueMeta.TYPE_STRING, "20", "0", "MMMM", "en_US"));
    fields.add(field("quarter", IValueMeta.TYPE_INTEGER, "1", "0", "Q", ""));
    fields.add(field("year", IValueMeta.TYPE_INTEGER, "4", "0", "yyyy", ""));
    fields.add(field("is_weekend", IValueMeta.TYPE_BOOLEAN, "", "", "@is_weekend", ""));
    return fields;
  }

  private static DateDimensionGeneratorField field(
      String name, int hopType, String length, String precision, String formatMask, String locale) {
    return new DateDimensionGeneratorField(name, hopType, length, precision, formatMask, locale);
  }
}
