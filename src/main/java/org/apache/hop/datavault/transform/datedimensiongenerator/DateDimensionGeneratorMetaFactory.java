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
package org.apache.hop.datavault.transform.datedimensiongenerator;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/** Factory helpers for standard calendar date dimension field definitions. */
public final class DateDimensionGeneratorMetaFactory {

  public static final String DEFAULT_START_DATE = "2000-01-01";
  public static final String DEFAULT_END_DATE = "2030-12-31";
  public static final String DEFAULT_LOAD_TIMESTAMP_FIELD = "load_dt";

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

  /** Optional relative-to-reference fields for dynamic reporting filters. */
  public static List<DateDimensionGeneratorField> relativeDefaultFields() {
    List<DateDimensionGeneratorField> fields = new ArrayList<>();
    fields.add(field("day_rel", IValueMeta.TYPE_INTEGER, "6", "0", "@rel_day", ""));
    fields.add(field("week_rel", IValueMeta.TYPE_INTEGER, "6", "0", "@rel_week", ""));
    fields.add(field("month_rel", IValueMeta.TYPE_INTEGER, "6", "0", "@rel_month", ""));
    fields.add(field("quarter_rel", IValueMeta.TYPE_INTEGER, "6", "0", "@rel_quarter", ""));
    fields.add(field("year_rel", IValueMeta.TYPE_INTEGER, "4", "0", "@rel_year", ""));
    fields.add(field("day_rel_label", IValueMeta.TYPE_STRING, "8", "0", "@rel_label_day", ""));
    fields.add(field("week_rel_label", IValueMeta.TYPE_STRING, "8", "0", "@rel_label_week", ""));
    fields.add(field("month_rel_label", IValueMeta.TYPE_STRING, "8", "0", "@rel_label_month", ""));
    fields.add(
        field("quarter_rel_label", IValueMeta.TYPE_STRING, "8", "0", "@rel_label_quarter", ""));
    fields.add(field("year_rel_label", IValueMeta.TYPE_STRING, "8", "0", "@rel_label_year", ""));
    fields.add(field("is_ytd", IValueMeta.TYPE_BOOLEAN, "", "", "@ytd", ""));
    fields.add(field("is_ytg", IValueMeta.TYPE_BOOLEAN, "", "", "@ytg", ""));
    fields.add(field("is_rolling12", IValueMeta.TYPE_BOOLEAN, "", "", "@rolling12", ""));
    return fields;
  }

  /**
   * Load-timestamp field for Type 1 dimension loads and warehouse audit columns. Uses {@link
   * DateDimensionGeneratorLogic#MASK_NOW} so every generated row shares the transform init time.
   */
  public static DateDimensionGeneratorField loadTimestampField(String name) {
    String fieldName = Utils.isEmpty(name) ? DEFAULT_LOAD_TIMESTAMP_FIELD : name;
    return field(
        fieldName, IValueMeta.TYPE_TIMESTAMP, "", "", DateDimensionGeneratorLogic.MASK_NOW, "");
  }

  /**
   * Appends a load-timestamp field when {@code fields} does not already contain {@code fieldName}.
   *
   * @return {@code true} when a field was added
   */
  public static boolean ensureLoadTimestampField(
      List<DateDimensionGeneratorField> fields, String fieldName) {
    if (fields == null || Utils.isEmpty(fieldName)) {
      return false;
    }
    for (DateDimensionGeneratorField field : fields) {
      if (field != null && fieldName.equalsIgnoreCase(field.getName())) {
        return false;
      }
    }
    fields.add(loadTimestampField(fieldName));
    return true;
  }

  /** Optional fiscal calendar fields (use with month/week/day offsets on the transform). */
  public static List<DateDimensionGeneratorField> fiscalDefaultFields() {
    List<DateDimensionGeneratorField> fields = new ArrayList<>();
    fields.add(field("fiscal_year", IValueMeta.TYPE_INTEGER, "4", "0", "@fiscal_year", ""));
    fields.add(field("fiscal_quarter", IValueMeta.TYPE_INTEGER, "1", "0", "@fiscal_quarter", ""));
    fields.add(field("fiscal_month", IValueMeta.TYPE_INTEGER, "2", "0", "@fiscal_month", ""));
    fields.add(field("fiscal_week", IValueMeta.TYPE_INTEGER, "2", "0", "@fiscal_week", ""));
    fields.add(
        field("fiscal_day_of_year", IValueMeta.TYPE_INTEGER, "3", "0", "@fiscal_day_of_year", ""));
    return fields;
  }

  private static DateDimensionGeneratorField field(
      String name, int hopType, String length, String precision, String formatMask, String locale) {
    return new DateDimensionGeneratorField(name, hopType, length, precision, formatMask, locale);
  }
}
