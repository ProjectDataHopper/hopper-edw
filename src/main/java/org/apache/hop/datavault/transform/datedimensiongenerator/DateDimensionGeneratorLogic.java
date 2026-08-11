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
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
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

  public static final String MASK_FISCAL_YEAR = "@fiscal_year";
  public static final String MASK_FISCAL_QUARTER = "@fiscal_quarter";
  public static final String MASK_FISCAL_MONTH = "@fiscal_month";
  public static final String MASK_FISCAL_WEEK = "@fiscal_week";
  public static final String MASK_FISCAL_DAY_OF_YEAR = "@fiscal_day_of_year";

  public static final String MASK_REL_DAY = "@rel_day";
  public static final String MASK_REL_WEEK = "@rel_week";
  public static final String MASK_REL_MONTH = "@rel_month";
  public static final String MASK_REL_QUARTER = "@rel_quarter";
  public static final String MASK_REL_YEAR = "@rel_year";
  public static final String MASK_REL_LABEL_DAY = "@rel_label_day";
  public static final String MASK_REL_LABEL_WEEK = "@rel_label_week";
  public static final String MASK_REL_LABEL_MONTH = "@rel_label_month";
  public static final String MASK_REL_LABEL_QUARTER = "@rel_label_quarter";
  public static final String MASK_REL_LABEL_YEAR = "@rel_label_year";
  public static final String MASK_YTD = "@ytd";
  public static final String MASK_YTG = "@ytg";
  public static final String MASK_ROLLING12 = "@rolling12";

  private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private DateDimensionGeneratorLogic() {}

  public record DateRange(LocalDate startDate, LocalDate endDate) {}

  /**
   * Runtime context for fiscal offsets and relative-to-reference attributes.
   *
   * <p>Fiscal attributes are computed on {@code rowDate + day/week/month offsets}. Relative
   * attributes compare the civil {@code rowDate} to {@code referenceDate}. Empty reference resolves
   * to the JVM local date at prepare time.
   */
  public record GeneratorContext(
      LocalDate referenceDate, int dayOffset, int weekOffset, int monthOffset) {

    public static final GeneratorContext DEFAULT = new GeneratorContext(LocalDate.now(), 0, 0, 0);

    public LocalDate fiscalDate(LocalDate rowDate) {
      return rowDate.plusDays(dayOffset).plusWeeks(weekOffset).plusMonths(monthOffset);
    }
  }

  public enum FieldKind {
    FORMATTER,
    WEEKEND,
    DAY_OF_WEEK,
    DATE_KEY,
    FISCAL_YEAR,
    FISCAL_QUARTER,
    FISCAL_MONTH,
    FISCAL_WEEK,
    FISCAL_DAY_OF_YEAR,
    REL_DAY,
    REL_WEEK,
    REL_MONTH,
    REL_QUARTER,
    REL_YEAR,
    REL_LABEL_DAY,
    REL_LABEL_WEEK,
    REL_LABEL_MONTH,
    REL_LABEL_QUARTER,
    REL_LABEL_YEAR,
    YTD,
    YTG,
    ROLLING12
  }

  public record PreparedField(IValueMeta valueMeta, DateTimeFormatter formatter, FieldKind kind) {}

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

  public static GeneratorContext resolveContext(
      String referenceDateValue,
      String dayOffsetValue,
      String weekOffsetValue,
      String monthOffsetValue,
      IVariables variables)
      throws HopException {
    LocalDate referenceDate;
    String resolvedReference = resolve(referenceDateValue, variables);
    if (Utils.isEmpty(resolvedReference)) {
      referenceDate = LocalDate.now();
    } else {
      referenceDate = parseDate(resolvedReference, "reference date");
    }
    int dayOffset = parseOffset(dayOffsetValue, variables, "day offset");
    int weekOffset = parseOffset(weekOffsetValue, variables, "week offset");
    int monthOffset = parseOffset(monthOffsetValue, variables, "month offset");
    return new GeneratorContext(referenceDate, dayOffset, weekOffset, monthOffset);
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
    return buildRow(date, preparedFields, GeneratorContext.DEFAULT);
  }

  public static Object[] buildRow(
      LocalDate date, List<PreparedField> preparedFields, GeneratorContext context)
      throws HopException {
    GeneratorContext ctx = context != null ? context : GeneratorContext.DEFAULT;
    Object[] row = new Object[preparedFields.size()];
    for (int i = 0; i < preparedFields.size(); i++) {
      row[i] = evaluateField(date, preparedFields.get(i), ctx);
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
    FieldKind kind = resolveFieldKind(valueMeta.getType(), mask);
    DateTimeFormatter formatter = null;
    if (kind == FieldKind.FORMATTER) {
      if (Utils.isEmpty(mask)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DateDimensionGeneratorLogic.Error.MissingFormatMask", field.getName()));
      }
      formatter =
          DateTimeFormatter.ofPattern(mask).withLocale(parseLocale(field.getLocale(), variables));
    }
    return new PreparedField(valueMeta, formatter, kind);
  }

  static FieldKind resolveFieldKind(int hopType, String mask) {
    if (hopType == IValueMeta.TYPE_BOOLEAN
        && (Utils.isEmpty(mask) || MASK_IS_WEEKEND.equalsIgnoreCase(mask))) {
      return FieldKind.WEEKEND;
    }
    if (Utils.isEmpty(mask)) {
      return FieldKind.FORMATTER;
    }
    String normalized = mask.trim();
    if (hopType == IValueMeta.TYPE_INTEGER
        && (MASK_DATE_KEY.equalsIgnoreCase(normalized)
            || "YYYYMMDD".equalsIgnoreCase(normalized))) {
      return FieldKind.DATE_KEY;
    }
    return switch (normalized.toLowerCase(Locale.ROOT)) {
      case MASK_DAY_OF_WEEK -> FieldKind.DAY_OF_WEEK;
      case MASK_IS_WEEKEND -> FieldKind.WEEKEND;
      case MASK_FISCAL_YEAR -> FieldKind.FISCAL_YEAR;
      case MASK_FISCAL_QUARTER -> FieldKind.FISCAL_QUARTER;
      case MASK_FISCAL_MONTH -> FieldKind.FISCAL_MONTH;
      case MASK_FISCAL_WEEK -> FieldKind.FISCAL_WEEK;
      case MASK_FISCAL_DAY_OF_YEAR -> FieldKind.FISCAL_DAY_OF_YEAR;
      case MASK_REL_DAY -> FieldKind.REL_DAY;
      case MASK_REL_WEEK -> FieldKind.REL_WEEK;
      case MASK_REL_MONTH -> FieldKind.REL_MONTH;
      case MASK_REL_QUARTER -> FieldKind.REL_QUARTER;
      case MASK_REL_YEAR -> FieldKind.REL_YEAR;
      case MASK_REL_LABEL_DAY -> FieldKind.REL_LABEL_DAY;
      case MASK_REL_LABEL_WEEK -> FieldKind.REL_LABEL_WEEK;
      case MASK_REL_LABEL_MONTH -> FieldKind.REL_LABEL_MONTH;
      case MASK_REL_LABEL_QUARTER -> FieldKind.REL_LABEL_QUARTER;
      case MASK_REL_LABEL_YEAR -> FieldKind.REL_LABEL_YEAR;
      case MASK_YTD -> FieldKind.YTD;
      case MASK_YTG -> FieldKind.YTG;
      case MASK_ROLLING12 -> FieldKind.ROLLING12;
      default -> FieldKind.FORMATTER;
    };
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
    FieldKind kind = resolveFieldKind(hopType, mask);
    if ((hopType == IValueMeta.TYPE_DATE || hopType == IValueMeta.TYPE_TIMESTAMP)
        && kind == FieldKind.FORMATTER
        && !Utils.isEmpty(mask)) {
      valueMeta.setConversionMask(mask);
    }
    valueMeta.setOrigin(origin);
    return valueMeta;
  }

  private static Object evaluateField(
      LocalDate date, PreparedField preparedField, GeneratorContext context) throws HopException {
    return switch (preparedField.kind()) {
      case WEEKEND -> {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        yield dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
      }
      case DAY_OF_WEEK -> (long) date.getDayOfWeek().getValue();
      case DATE_KEY -> dateKey(date);
      case FISCAL_YEAR -> (long) context.fiscalDate(date).getYear();
      case FISCAL_QUARTER -> (long) context.fiscalDate(date).get(IsoFields.QUARTER_OF_YEAR);
      case FISCAL_MONTH -> (long) context.fiscalDate(date).getMonthValue();
      case FISCAL_WEEK -> (long) context.fiscalDate(date).get(WeekFields.ISO.weekOfWeekBasedYear());
      case FISCAL_DAY_OF_YEAR -> (long) context.fiscalDate(date).getDayOfYear();
      case REL_DAY -> (long) ChronoUnit.DAYS.between(context.referenceDate(), date);
      case REL_WEEK -> (long) weeksBetween(context.referenceDate(), date);
      case REL_MONTH ->
          (long)
              ChronoUnit.MONTHS.between(
                  context.referenceDate().withDayOfMonth(1), date.withDayOfMonth(1));
      case REL_QUARTER -> (long) quartersBetween(context.referenceDate(), date);
      case REL_YEAR ->
          (long)
              ChronoUnit.YEARS.between(
                  context.referenceDate().withDayOfYear(1), date.withDayOfYear(1));
      case REL_LABEL_DAY ->
          relativeLabel("D", ChronoUnit.DAYS.between(context.referenceDate(), date));
      case REL_LABEL_WEEK -> relativeLabel("W", weeksBetween(context.referenceDate(), date));
      case REL_LABEL_MONTH ->
          relativeLabel(
              "M",
              ChronoUnit.MONTHS.between(
                  context.referenceDate().withDayOfMonth(1), date.withDayOfMonth(1)));
      case REL_LABEL_QUARTER -> relativeLabel("Q", quartersBetween(context.referenceDate(), date));
      case REL_LABEL_YEAR ->
          relativeLabel(
              "Y",
              ChronoUnit.YEARS.between(
                  context.referenceDate().withDayOfYear(1), date.withDayOfYear(1)));
      case YTD -> isYearToDate(date, context.referenceDate());
      case YTG -> isYearToGo(date, context.referenceDate());
      case ROLLING12 -> isRolling12(date, context.referenceDate());
      case FORMATTER -> {
        String formatted = date.format(preparedField.formatter());
        yield convertFormattedValue(formatted, date, preparedField.valueMeta());
      }
    };
  }

  static String relativeLabel(String prefix, long offset) {
    if (offset == 0) {
      return prefix;
    }
    if (offset > 0) {
      return prefix + "+" + offset;
    }
    return prefix + offset;
  }

  static long weeksBetween(LocalDate reference, LocalDate date) {
    LocalDate refWeek = reference.with(WeekFields.ISO.dayOfWeek(), 1);
    LocalDate dateWeek = date.with(WeekFields.ISO.dayOfWeek(), 1);
    return ChronoUnit.WEEKS.between(refWeek, dateWeek);
  }

  static long quartersBetween(LocalDate reference, LocalDate date) {
    long refQuarter = reference.getYear() * 4L + reference.get(IsoFields.QUARTER_OF_YEAR);
    long dateQuarter = date.getYear() * 4L + date.get(IsoFields.QUARTER_OF_YEAR);
    return dateQuarter - refQuarter;
  }

  static boolean isYearToDate(LocalDate date, LocalDate reference) {
    return date.getYear() == reference.getYear() && !date.isAfter(reference);
  }

  static boolean isYearToGo(LocalDate date, LocalDate reference) {
    return date.getYear() == reference.getYear() && date.isAfter(reference);
  }

  static boolean isRolling12(LocalDate date, LocalDate reference) {
    // Inclusive window: [reference - 1 year, reference]
    LocalDate windowStart = reference.minusYears(1);
    return !date.isBefore(windowStart) && !date.isAfter(reference);
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

  private static int parseOffset(String value, IVariables variables, String label)
      throws HopException {
    String resolved = resolve(value, variables);
    if (Utils.isEmpty(resolved)) {
      return 0;
    }
    try {
      return Integer.parseInt(resolved.trim());
    } catch (NumberFormatException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DateDimensionGeneratorLogic.Error.InvalidOffset", label, resolved),
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
