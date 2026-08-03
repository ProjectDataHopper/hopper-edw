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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Shared calendar row generation and field formatting for the date dimension generator. */
public final class DateDimensionGeneratorLogic {

  private static final Class<?> PKG = DateDimensionGeneratorMeta.class;

  public static final String MASK_IS_WEEKEND = "@is_weekend";
  public static final String MASK_DAY_OF_WEEK = "@day_of_week";
  public static final String MASK_DATE_KEY = "yyyyMMdd";

  private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private DateDimensionGeneratorLogic() {}

  public record DateRange(LocalDate startDate, LocalDate endDate) {}

  public record PreparedField(
      IValueMeta valueMeta,
      DateTimeFormatter formatter,
      boolean weekendField,
      boolean dayOfWeekField,
      boolean dateKeyField) {}

  public static DateRange resolveDateRange(
      String startDateValue, String endDateValue, IVariables variables) throws HopException {
    LocalDate startDate = parseDate(resolve(startDateValue, variables), "start date");
    LocalDate endDate = parseDate(resolve(endDateValue, variables), "end date");
    if (startDate.isAfter(endDate)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DateDimensionGeneratorLogic.Error.StartAfterEnd", startDate, endDate));
    }
    return new DateRange(startDate, endDate);
  }

  public static IRowMeta buildOutputRowMeta(
      List<DateDimensionGeneratorField> fields, String origin, IVariables variables)
      throws HopPluginException, HopException {
    RowMeta rowMeta = new RowMeta();
    addFieldsToRowMeta(rowMeta, fields, origin, variables);
    return rowMeta;
  }

  public static void addFieldsToRowMeta(
      IRowMeta rowMeta,
      List<DateDimensionGeneratorField> fields,
      String origin,
      IVariables variables)
      throws HopPluginException, HopException {
    if (fields == null) {
      return;
    }
    for (DateDimensionGeneratorField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      rowMeta.addValueMeta(createValueMeta(field, origin, variables));
    }
  }

  public static List<PreparedField> prepareFields(
      List<DateDimensionGeneratorField> fields, String origin, IVariables variables)
      throws HopPluginException, HopException {
    List<PreparedField> prepared = new ArrayList<>();
    if (fields == null) {
      return prepared;
    }
    for (DateDimensionGeneratorField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      prepared.add(prepareField(field, origin, variables));
    }
    if (prepared.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DateDimensionGeneratorLogic.Error.NoFieldsConfigured"));
    }
    return prepared;
  }

  public static Object[] buildRow(LocalDate date, List<PreparedField> preparedFields)
      throws HopException {
    Object[] row = new Object[preparedFields.size()];
    for (int i = 0; i < preparedFields.size(); i++) {
      row[i] = evaluateField(date, preparedFields.get(i));
    }
    return row;
  }

  public static long dayCountInclusive(LocalDate startDate, LocalDate endDate) {
    return endDate.toEpochDay() - startDate.toEpochDay() + 1;
  }

  private static PreparedField prepareField(
      DateDimensionGeneratorField field, String origin, IVariables variables)
      throws HopPluginException, HopException {
    IValueMeta valueMeta = createValueMeta(field, origin, variables);
    String mask = resolve(field.getFormatMask(), variables);
    boolean weekendField =
        valueMeta.getType() == IValueMeta.TYPE_BOOLEAN
            && (Utils.isEmpty(mask) || MASK_IS_WEEKEND.equalsIgnoreCase(mask));
    boolean dayOfWeekField =
        valueMeta.getType() == IValueMeta.TYPE_INTEGER && MASK_DAY_OF_WEEK.equalsIgnoreCase(mask);
    boolean dateKeyField =
        valueMeta.getType() == IValueMeta.TYPE_INTEGER
            && (MASK_DATE_KEY.equalsIgnoreCase(mask) || "YYYYMMDD".equalsIgnoreCase(mask));
    DateTimeFormatter formatter = null;
    if (!weekendField && !dayOfWeekField && !dateKeyField) {
      if (Utils.isEmpty(mask)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DateDimensionGeneratorLogic.Error.MissingFormatMask", field.getName()));
      }
      formatter =
          DateTimeFormatter.ofPattern(mask).withLocale(parseLocale(field.getLocale(), variables));
    }
    return new PreparedField(valueMeta, formatter, weekendField, dayOfWeekField, dateKeyField);
  }

  private static IValueMeta createValueMeta(
      DateDimensionGeneratorField field, String origin, IVariables variables)
      throws HopPluginException, HopException {
    int hopType = field.getHopType() > 0 ? field.getHopType() : IValueMeta.TYPE_STRING;
    int length = Const.toInt(resolve(field.getLength(), variables), -1);
    int precision = Const.toInt(resolve(field.getPrecision(), variables), -1);
    IValueMeta valueMeta =
        ValueMetaFactory.createValueMeta(
            resolve(field.getName(), variables), hopType, length, precision);
    // Format masks are Java DateTimeFormatter patterns applied during row generation. They must not
    // be copied to Hop conversion masks on non-date types: Hop would prepend them when rendering
    // preview/output (e.g. integer 20000101 shown as "yyyyMMdd20000101").
    String mask = resolve(field.getFormatMask(), variables);
    if ((hopType == IValueMeta.TYPE_DATE || hopType == IValueMeta.TYPE_TIMESTAMP)
        && !Utils.isEmpty(mask)
        && !MASK_IS_WEEKEND.equalsIgnoreCase(mask)
        && !MASK_DAY_OF_WEEK.equalsIgnoreCase(mask)) {
      valueMeta.setConversionMask(mask);
    }
    valueMeta.setOrigin(origin);
    return valueMeta;
  }

  private static Object evaluateField(LocalDate date, PreparedField preparedField)
      throws HopException {
    if (preparedField.weekendField()) {
      DayOfWeek dayOfWeek = date.getDayOfWeek();
      return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
    if (preparedField.dayOfWeekField()) {
      return (long) date.getDayOfWeek().getValue();
    }
    if (preparedField.dateKeyField()) {
      return dateKey(date);
    }
    String formatted = date.format(preparedField.formatter());
    return convertFormattedValue(formatted, date, preparedField.valueMeta());
  }

  private static long dateKey(LocalDate date) {
    return date.getYear() * 10000L + date.getMonthValue() * 100L + date.getDayOfMonth();
  }

  private static Object convertFormattedValue(
      String formatted, LocalDate date, IValueMeta valueMeta) throws HopException {
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_STRING -> formatted;
      case IValueMeta.TYPE_INTEGER -> parseLongValue(formatted, valueMeta.getName());
      case IValueMeta.TYPE_NUMBER -> parseDoubleValue(formatted, valueMeta.getName());
      case IValueMeta.TYPE_BOOLEAN -> parseBooleanValue(formatted, valueMeta.getName());
      case IValueMeta.TYPE_DATE -> toDate(date);
      case IValueMeta.TYPE_TIMESTAMP -> toTimestamp(date);
      default ->
          throw new HopException(
              BaseMessages.getString(
                  PKG,
                  "DateDimensionGeneratorLogic.Error.UnsupportedFieldType",
                  valueMeta.getName(),
                  ValueMetaFactory.getValueMetaName(valueMeta.getType())));
    };
  }

  private static long parseLongValue(String formatted, String fieldName) throws HopException {
    try {
      return Long.parseLong(formatted.trim());
    } catch (NumberFormatException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DateDimensionGeneratorLogic.Error.InvalidInteger", fieldName, formatted),
          e);
    }
  }

  private static double parseDoubleValue(String formatted, String fieldName) throws HopException {
    try {
      return Double.parseDouble(formatted.trim());
    } catch (NumberFormatException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DateDimensionGeneratorLogic.Error.InvalidNumber", fieldName, formatted),
          e);
    }
  }

  private static boolean parseBooleanValue(String formatted, String fieldName) throws HopException {
    String normalized = formatted == null ? "" : formatted.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "y", "yes", "true", "t", "1" -> true;
      case "n", "no", "false", "f", "0" -> false;
      default ->
          throw new HopException(
              BaseMessages.getString(
                  PKG, "DateDimensionGeneratorLogic.Error.InvalidBoolean", fieldName, formatted));
    };
  }

  private static Date toDate(LocalDate date) {
    return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  private static java.sql.Timestamp toTimestamp(LocalDate date) {
    return java.sql.Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  private static LocalDate parseDate(String value, String label) throws HopException {
    if (Utils.isEmpty(value)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DateDimensionGeneratorLogic.Error.MissingDate", label));
    }
    String trimmed = value.trim();
    try {
      if (trimmed.length() >= 10) {
        return LocalDate.parse(trimmed.substring(0, 10), SQL_DATE);
      }
      return LocalDate.parse(trimmed, SQL_DATE);
    } catch (DateTimeParseException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DateDimensionGeneratorLogic.Error.InvalidDate", label, value),
          e);
    }
  }

  static Locale parseLocale(String localeValue, IVariables variables) {
    String resolved = resolve(localeValue, variables);
    if (Utils.isEmpty(resolved)) {
      return Locale.getDefault();
    }
    String normalized = resolved.trim().replace('_', '-');
    Locale locale = Locale.forLanguageTag(normalized);
    return locale.getLanguage().isEmpty() ? Locale.getDefault() : locale;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
