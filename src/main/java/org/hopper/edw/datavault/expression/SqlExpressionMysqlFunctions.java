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
package org.hopper.edw.datavault.expression;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MySQL / SingleStore scalar semantics for SQL Expression ({@code HEX}, {@code UNHEX}, {@code MD5},
 * {@code DATE_FORMAT}, {@code TO_DATE}).
 */
public final class SqlExpressionMysqlFunctions {

  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
  private static final char[] HEX_DIGITS_LOWER = "0123456789abcdef".toCharArray();
  private static final DateTimeFormatter ISO_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter ISO_DATE_TIME_FRACTION =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
  private static final ConcurrentHashMap<String, MysqlFormat> DATE_FORMAT_CACHE =
      new ConcurrentHashMap<>();
  private static final ThreadLocal<MessageDigest> MD5 =
      ThreadLocal.withInitial(SqlExpressionMysqlFunctions::newMd5);

  private SqlExpressionMysqlFunctions() {}

  public static String hex(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof byte[] bytes) {
      return toHex(bytes);
    }
    if (value instanceof Number number) {
      return Long.toUnsignedString(number.longValue(), 16).toUpperCase(Locale.ROOT);
    }
    if (value instanceof Boolean bool) {
      return Long.toUnsignedString(bool ? 1L : 0L, 16).toUpperCase(Locale.ROOT);
    }
    return toHex(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
  }

  public static byte[] unhex(Object value) {
    if (value == null) {
      return null;
    }
    String hex = String.valueOf(value);
    if ((hex.length() & 1) == 1) {
      hex = "0" + hex;
    }
    int length = hex.length();
    byte[] out = new byte[length / 2];
    for (int i = 0; i < length; i += 2) {
      int high = Character.digit(hex.charAt(i), 16);
      int low = Character.digit(hex.charAt(i + 1), 16);
      if (high < 0 || low < 0) {
        return null;
      }
      out[i / 2] = (byte) ((high << 4) + low);
    }
    return out;
  }

  public static String md5(Object value) {
    if (value == null) {
      return null;
    }
    byte[] input =
        value instanceof byte[] bytes ? bytes : mysqlString(value).getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = MD5.get();
    digest.reset();
    return toHex(digest.digest(input), HEX_DIGITS_LOWER);
  }

  private static MessageDigest newMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is not available", e);
    }
  }

  public static String dateFormat(Object dateValue, Object formatValue) {
    if (dateValue == null || formatValue == null) {
      return null;
    }
    LocalDateTime dateTime = toLocalDateTime(dateValue);
    if (dateTime == null) {
      return null;
    }
    return formatMysql(dateTime, String.valueOf(formatValue));
  }

  /**
   * SingleStore {@code TO_DATE(value, format)} using TO_CHAR format models ({@code YYYY}, {@code
   * MM}, {@code DD}, {@code HH24}, {@code MI}, {@code SS}, {@code MONTH}, …). Literal mismatch
   * returns null; an invalid calendar date throws.
   */
  public static Timestamp toDate(Object value, Object format) throws SqlExpressionException {
    if (value == null || format == null) {
      return null;
    }
    ParsedDate parsed = parseToDate(String.valueOf(value), String.valueOf(format));
    if (parsed == null) {
      return null;
    }
    LocalDate today = LocalDate.now();
    int year = parsed.year != null ? parsed.year : today.getYear();
    int month = parsed.month != null ? parsed.month : today.getMonthValue();
    int day = parsed.day != null ? parsed.day : 1;
    int hour = parsed.hour24 != null ? parsed.hour24 : 0;
    int minute = parsed.minute != null ? parsed.minute : 0;
    int second = parsed.second != null ? parsed.second : 0;
    if (parsed.hour12 != null) {
      hour = toHour24(parsed.hour12, parsed.afternoon);
    }
    try {
      YearMonth yearMonth = YearMonth.of(year, month);
      if (day < 1 || day > yearMonth.lengthOfMonth()) {
        throw new DateTimeException("day " + day + " is invalid for " + yearMonth);
      }
      return Timestamp.valueOf(LocalDateTime.of(year, month, day, hour, minute, second));
    } catch (DateTimeException e) {
      throw new SqlExpressionException(
          "TO_DATE could not build a valid date: " + e.getMessage(), e);
    }
  }

  static String mysqlString(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    if (value instanceof Double || value instanceof Float) {
      return BigDecimal.valueOf(((Number) value).doubleValue())
          .stripTrailingZeros()
          .toPlainString();
    }
    if (value instanceof Number number) {
      return Long.toString(number.longValue());
    }
    if (value instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    return String.valueOf(value);
  }

  private static String toHex(byte[] bytes) {
    return toHex(bytes, HEX_DIGITS);
  }

  private static String toHex(byte[] bytes, char[] digits) {
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xFF;
      chars[i * 2] = digits[value >>> 4];
      chars[i * 2 + 1] = digits[value & 0x0F];
    }
    return new String(chars);
  }

  static LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate().atStartOfDay();
    }
    if (value instanceof java.sql.Time time) {
      return time.toLocalTime().atDate(LocalDate.of(1970, 1, 1));
    }
    if (value instanceof Calendar calendar) {
      return new Timestamp(calendar.getTimeInMillis()).toLocalDateTime();
    }
    if (value instanceof Date date) {
      return new Timestamp(date.getTime()).toLocalDateTime();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime;
    }
    if (value instanceof LocalDate localDate) {
      return localDate.atStartOfDay();
    }
    String text = String.valueOf(value).trim().replace('T', ' ');
    try {
      return Timestamp.valueOf(text).toLocalDateTime();
    } catch (IllegalArgumentException ignored) {
      // try ISO date / datetime next
    }
    try {
      if (text.length() <= 10) {
        return LocalDate.parse(text).atStartOfDay();
      }
      try {
        return LocalDateTime.parse(text, ISO_DATE_TIME);
      } catch (DateTimeParseException ignored) {
        return LocalDateTime.parse(text, ISO_DATE_TIME_FRACTION);
      }
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  static String formatMysql(LocalDateTime dateTime, String format) {
    if (format == null) {
      return null;
    }
    MysqlFormat compiled =
        DATE_FORMAT_CACHE.computeIfAbsent(format, SqlExpressionMysqlFunctions::compileFormat);
    StringBuilder out = new StringBuilder(compiled.estimatedLength);
    for (FormatPart part : compiled.parts) {
      if (part.specifier != 0) {
        appendSpecifier(out, dateTime, part.specifier);
      } else {
        out.append(part.literal);
      }
    }
    return out.toString();
  }

  private static MysqlFormat compileFormat(String format) {
    List<FormatPart> parts = new ArrayList<>();
    StringBuilder literal = new StringBuilder();
    for (int i = 0; i < format.length(); i++) {
      char current = format.charAt(i);
      if (current == '%' && i + 1 < format.length()) {
        if (literal.length() > 0) {
          parts.add(FormatPart.literal(literal.toString()));
          literal.setLength(0);
        }
        parts.add(FormatPart.specifier(format.charAt(++i)));
      } else {
        literal.append(current);
      }
    }
    if (literal.length() > 0) {
      parts.add(FormatPart.literal(literal.toString()));
    }
    return new MysqlFormat(List.copyOf(parts), format.length() * 2);
  }

  private static void appendSpecifier(StringBuilder out, LocalDateTime dateTime, char spec) {
    switch (spec) {
      case '%' -> out.append('%');
      case 'a' -> out.append(dateTime.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US));
      case 'b' -> out.append(dateTime.getMonth().getDisplayName(TextStyle.SHORT, Locale.US));
      case 'c' -> out.append(dateTime.getMonthValue());
      case 'd' -> appendPad2(out, dateTime.getDayOfMonth());
      case 'e' -> out.append(dateTime.getDayOfMonth());
      case 'f' -> appendPad6(out, dateTime.getNano() / 1000);
      case 'H' -> appendPad2(out, dateTime.getHour());
      case 'h', 'I' -> appendPad2(out, hour12(dateTime.getHour()));
      case 'i' -> appendPad2(out, dateTime.getMinute());
      case 'j' -> appendPad3(out, dateTime.getDayOfYear());
      case 'k' -> out.append(dateTime.getHour());
      case 'l' -> out.append(hour12(dateTime.getHour()));
      case 'M' -> out.append(dateTime.getMonth().getDisplayName(TextStyle.FULL, Locale.US));
      case 'm' -> appendPad2(out, dateTime.getMonthValue());
      case 'p' -> out.append(dateTime.getHour() < 12 ? "AM" : "PM");
      case 'r' -> {
        appendPad2(out, hour12(dateTime.getHour()));
        out.append(':');
        appendPad2(out, dateTime.getMinute());
        out.append(':');
        appendPad2(out, dateTime.getSecond());
        out.append(dateTime.getHour() < 12 ? " AM" : " PM");
      }
      case 's', 'S' -> appendPad2(out, dateTime.getSecond());
      case 'T' -> {
        appendPad2(out, dateTime.getHour());
        out.append(':');
        appendPad2(out, dateTime.getMinute());
        out.append(':');
        appendPad2(out, dateTime.getSecond());
      }
      case 'W' -> out.append(dateTime.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US));
      case 'w' -> out.append(dateTime.getDayOfWeek().getValue() % 7);
      case 'Y' -> out.append(dateTime.getYear());
      case 'y' -> appendPad2(out, dateTime.getYear() % 100);
      default -> out.append(spec);
    }
  }

  private static int hour12(int hour24) {
    int mod = hour24 % 12;
    return mod == 0 ? 12 : mod;
  }

  private static void appendPad2(StringBuilder out, int value) {
    if (value < 10) {
      out.append('0');
    }
    out.append(value);
  }

  private static void appendPad3(StringBuilder out, int value) {
    if (value < 10) {
      out.append("00");
    } else if (value < 100) {
      out.append('0');
    }
    out.append(value);
  }

  private static void appendPad6(StringBuilder out, int value) {
    int micros = Math.max(value, 0);
    if (micros >= 100000) {
      out.append(micros);
      return;
    }
    if (micros >= 10000) {
      out.append('0');
    } else if (micros >= 1000) {
      out.append("00");
    } else if (micros >= 100) {
      out.append("000");
    } else if (micros >= 10) {
      out.append("0000");
    } else {
      out.append("00000");
    }
    out.append(micros);
  }

  private record FormatPart(char specifier, String literal) {
    static FormatPart specifier(char specifier) {
      return new FormatPart(specifier, null);
    }

    static FormatPart literal(String literal) {
      return new FormatPart((char) 0, literal);
    }
  }

  private record MysqlFormat(List<FormatPart> parts, int estimatedLength) {}

  private static int toHour24(int hour12, Boolean afternoon) {
    if (afternoon == null) {
      return hour12;
    }
    int hour = hour12 % 12;
    if (Boolean.TRUE.equals(afternoon)) {
      hour += 12;
    }
    return hour;
  }

  private static ParsedDate parseToDate(String value, String format) {
    List<FormatToken> tokens = tokenizeFormat(format);
    if (tokens.isEmpty()) {
      return null;
    }
    int pos = 0;
    ParsedDate parsed = new ParsedDate();
    for (FormatToken token : tokens) {
      // Literal text (including spaces) must appear in the value. Skip whitespace only
      // before specifiers and punctuation so a format space is not consumed twice.
      if (token.kind != FormatKind.LITERAL) {
        pos = skipWhitespace(value, pos);
      }
      Integer next = token.apply(value, pos, parsed);
      if (next == null) {
        return null;
      }
      pos = next;
    }
    pos = skipWhitespace(value, pos);
    if (pos != value.length()) {
      return null;
    }
    return parsed;
  }

  private static List<FormatToken> tokenizeFormat(String format) {
    List<FormatToken> tokens = new ArrayList<>();
    int i = 0;
    while (i < format.length()) {
      SpecMatch spec = matchSpecifier(format, i);
      if (spec != null) {
        tokens.add(new FormatToken(spec.kind, null));
        i += spec.length;
        continue;
      }
      char current = format.charAt(i);
      if (isPunctuation(current)) {
        tokens.add(new FormatToken(FormatKind.PUNCT, null));
        i++;
        continue;
      }
      int start = i;
      while (i < format.length()
          && matchSpecifier(format, i) == null
          && !isPunctuation(format.charAt(i))) {
        i++;
      }
      tokens.add(new FormatToken(FormatKind.LITERAL, format.substring(start, i)));
    }
    return tokens;
  }

  private static SpecMatch matchSpecifier(String format, int i) {
    if (region(format, i, "A.M.") || region(format, i, "P.M.")) {
      return new SpecMatch(FormatKind.AMPM, 4);
    }
    if (region(format, i, "HH24")) {
      return new SpecMatch(FormatKind.HH24, 4);
    }
    if (region(format, i, "HH12")) {
      return new SpecMatch(FormatKind.HH12, 4);
    }
    if (region(format, i, "MONTH")) {
      return new SpecMatch(FormatKind.MONTH, 5);
    }
    if (region(format, i, "YYYY")) {
      return new SpecMatch(FormatKind.YYYY, 4);
    }
    if (region(format, i, "MON")) {
      return new SpecMatch(FormatKind.MON, 3);
    }
    if (region(format, i, "DD")) {
      return new SpecMatch(FormatKind.DD, 2);
    }
    if (region(format, i, "MM")) {
      return new SpecMatch(FormatKind.MM, 2);
    }
    if (region(format, i, "YY")) {
      return new SpecMatch(FormatKind.YY, 2);
    }
    if (region(format, i, "RR")) {
      return new SpecMatch(FormatKind.RR, 2);
    }
    if (region(format, i, "MI")) {
      return new SpecMatch(FormatKind.MI, 2);
    }
    if (region(format, i, "SS")) {
      return new SpecMatch(FormatKind.SS, 2);
    }
    if (region(format, i, "HH")) {
      return new SpecMatch(FormatKind.HH12, 2);
    }
    if (region(format, i, "DY")) {
      return new SpecMatch(FormatKind.DY, 2);
    }
    if (region(format, i, "AM") || region(format, i, "PM")) {
      return new SpecMatch(FormatKind.AMPM, 2);
    }
    if (region(format, i, "FF")) {
      int length = 2;
      if (i + 2 < format.length() && Character.isDigit(format.charAt(i + 2))) {
        length = 3;
      }
      return new SpecMatch(FormatKind.FF, length);
    }
    if (region(format, i, "D")
        && (i + 1 >= format.length() || !Character.isLetter(format.charAt(i + 1)))) {
      return new SpecMatch(FormatKind.D, 1);
    }
    return null;
  }

  private static boolean region(String format, int i, String token) {
    return format.regionMatches(true, i, token, 0, token.length());
  }

  private static boolean isPunctuation(char c) {
    return "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0;
  }

  private static int skipWhitespace(String value, int pos) {
    while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  private static Integer consumeDigits(String value, int pos, int min, int max) {
    pos = skipWhitespace(value, pos);
    int start = pos;
    while (pos < value.length() && pos - start < max && Character.isDigit(value.charAt(pos))) {
      pos++;
    }
    if (pos - start < min) {
      return null;
    }
    return pos;
  }

  private static Integer parseInt(String value, int start, int end) {
    try {
      return Integer.parseInt(value.substring(start, end));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private enum FormatKind {
    YYYY,
    YY,
    RR,
    MM,
    DD,
    MONTH,
    MON,
    HH24,
    HH12,
    MI,
    SS,
    AMPM,
    DY,
    D,
    FF,
    PUNCT,
    LITERAL
  }

  private static final class FormatToken {
    private final FormatKind kind;
    private final String literal;

    private FormatToken(FormatKind kind, String literal) {
      this.kind = kind;
      this.literal = literal;
    }

    @Override
    public String toString() {
      return literal != null ? kind + "(" + literal + ")" : kind.name();
    }

    Integer apply(String value, int pos, ParsedDate parsed) {
      return switch (kind) {
        case YYYY -> consumeIntField(value, pos, 4, 4, v -> parsed.year = v);
        case YY ->
            consumeIntField(
                value, pos, 2, 2, v -> parsed.year = (LocalDate.now().getYear() / 100) * 100 + v);
        case RR ->
            consumeIntField(value, pos, 2, 2, v -> parsed.year = v <= 49 ? 2000 + v : 1900 + v);
        case MM -> consumeIntField(value, pos, 1, 2, v -> parsed.month = v);
        case DD -> consumeIntField(value, pos, 1, 2, v -> parsed.day = v);
        case MONTH -> consumeMonth(value, pos, parsed, false);
        case MON -> consumeMonth(value, pos, parsed, true);
        case HH24 -> consumeIntField(value, pos, 1, 2, v -> parsed.hour24 = v);
        case HH12 -> consumeIntField(value, pos, 1, 2, v -> parsed.hour12 = v);
        case MI -> consumeIntField(value, pos, 1, 2, v -> parsed.minute = v);
        case SS -> consumeIntField(value, pos, 1, 2, v -> parsed.second = v);
        case AMPM -> consumeAmPm(value, pos, parsed);
        case DY -> {
          int start = skipWhitespace(value, pos);
          yield start + 3 > value.length() ? null : start + 3;
        }
        case D -> consumeIntField(value, pos, 1, 1, v -> {});
        case FF -> {
          Integer end = consumeDigits(value, pos, 1, 9);
          yield end == null ? pos : end;
        }
        case PUNCT -> consumePunct(value, pos);
        case LITERAL -> consumeLiteral(value, pos, literal);
      };
    }
  }

  private static Integer consumeIntField(
      String value, int pos, int min, int max, java.util.function.IntConsumer setter) {
    int start = skipWhitespace(value, pos);
    Integer end = consumeDigits(value, pos, min, max);
    if (end == null) {
      return null;
    }
    Integer number = parseInt(value, start, end);
    if (number == null) {
      return null;
    }
    setter.accept(number);
    return end;
  }

  private static Integer consumeAmPm(String value, int pos, ParsedDate parsed) {
    pos = skipWhitespace(value, pos);
    if (starts(value, pos, "A.M.")) {
      parsed.afternoon = false;
      return pos + 4;
    }
    if (starts(value, pos, "P.M.")) {
      parsed.afternoon = true;
      return pos + 4;
    }
    if (starts(value, pos, "AM")) {
      parsed.afternoon = false;
      return pos + 2;
    }
    if (starts(value, pos, "PM")) {
      parsed.afternoon = true;
      return pos + 2;
    }
    return null;
  }

  private static Integer consumePunct(String value, int pos) {
    pos = skipWhitespace(value, pos);
    if (pos >= value.length() || !isPunctuation(value.charAt(pos))) {
      return null;
    }
    while (pos < value.length() && isPunctuation(value.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  private static Integer consumeLiteral(String value, int pos, String literal) {
    int i = 0;
    while (i < literal.length()) {
      if (Character.isWhitespace(literal.charAt(i))) {
        while (i < literal.length() && Character.isWhitespace(literal.charAt(i))) {
          i++;
        }
        int next = skipWhitespace(value, pos);
        if (next == pos) {
          return null;
        }
        pos = next;
        continue;
      }
      if (pos >= value.length()
          || Character.toUpperCase(value.charAt(pos)) != Character.toUpperCase(literal.charAt(i))) {
        return null;
      }
      pos++;
      i++;
    }
    return pos;
  }

  private static Integer consumeMonth(String value, int pos, ParsedDate parsed, boolean abbrev) {
    pos = skipWhitespace(value, pos);
    Month best = null;
    int bestEnd = -1;
    for (Month month : Month.values()) {
      String name =
          abbrev
              ? month.getDisplayName(TextStyle.SHORT, Locale.US)
              : month.getDisplayName(TextStyle.FULL, Locale.US);
      if (starts(value, pos, name) && pos + name.length() > bestEnd) {
        best = month;
        bestEnd = pos + name.length();
      }
    }
    if (best == null) {
      return null;
    }
    parsed.month = best.getValue();
    return bestEnd;
  }

  private static boolean starts(String value, int pos, String token) {
    return value.regionMatches(true, pos, token, 0, token.length());
  }

  private record SpecMatch(FormatKind kind, int length) {}

  private static final class ParsedDate {
    Integer year;
    Integer month;
    Integer day;
    Integer hour24;
    Integer hour12;
    Integer minute;
    Integer second;
    Boolean afternoon;
  }
}
