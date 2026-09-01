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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * MySQL / SingleStore scalar semantics for SQL Expression ({@code HEX}, {@code UNHEX}, {@code MD5},
 * {@code DATE_FORMAT}).
 */
public final class SqlExpressionMysqlFunctions {

  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
  private static final DateTimeFormatter ISO_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter ISO_DATE_TIME_FRACTION =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

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
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(input);
      return toHex(digest).toLowerCase(Locale.ROOT);
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
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xFF;
      chars[i * 2] = HEX_DIGITS[value >>> 4];
      chars[i * 2 + 1] = HEX_DIGITS[value & 0x0F];
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
    StringBuilder out = new StringBuilder(format.length() * 2);
    for (int i = 0; i < format.length(); i++) {
      char current = format.charAt(i);
      if (current != '%' || i + 1 >= format.length()) {
        out.append(current);
        continue;
      }
      i++;
      out.append(specifier(dateTime, format.charAt(i)));
    }
    return out.toString();
  }

  private static String specifier(LocalDateTime dateTime, char spec) {
    return switch (spec) {
      case '%' -> "%";
      case 'a' -> dateTime.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US);
      case 'b' -> dateTime.getMonth().getDisplayName(TextStyle.SHORT, Locale.US);
      case 'c' -> Integer.toString(dateTime.getMonthValue());
      case 'd' -> pad2(dateTime.getDayOfMonth());
      case 'e' -> Integer.toString(dateTime.getDayOfMonth());
      case 'f' -> pad6(dateTime.getNano() / 1000);
      case 'H' -> pad2(dateTime.getHour());
      case 'h', 'I' -> pad2(hour12(dateTime.getHour()));
      case 'i' -> pad2(dateTime.getMinute());
      case 'j' -> pad3(dateTime.getDayOfYear());
      case 'k' -> Integer.toString(dateTime.getHour());
      case 'l' -> Integer.toString(hour12(dateTime.getHour()));
      case 'M' -> dateTime.getMonth().getDisplayName(TextStyle.FULL, Locale.US);
      case 'm' -> pad2(dateTime.getMonthValue());
      case 'p' -> dateTime.getHour() < 12 ? "AM" : "PM";
      case 'r' ->
          pad2(hour12(dateTime.getHour()))
              + ":"
              + pad2(dateTime.getMinute())
              + ":"
              + pad2(dateTime.getSecond())
              + (dateTime.getHour() < 12 ? " AM" : " PM");
      case 's', 'S' -> pad2(dateTime.getSecond());
      case 'T' ->
          pad2(dateTime.getHour())
              + ":"
              + pad2(dateTime.getMinute())
              + ":"
              + pad2(dateTime.getSecond());
      case 'W' -> dateTime.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US);
      case 'w' -> Integer.toString(dateTime.getDayOfWeek().getValue() % 7);
      case 'Y' -> Integer.toString(dateTime.getYear());
      case 'y' -> pad2(dateTime.getYear() % 100);
      default -> Character.toString(spec);
    };
  }

  private static int hour12(int hour24) {
    int mod = hour24 % 12;
    return mod == 0 ? 12 : mod;
  }

  private static String pad2(int value) {
    return value < 10 ? "0" + value : Integer.toString(value);
  }

  private static String pad3(int value) {
    if (value < 10) {
      return "00" + value;
    }
    if (value < 100) {
      return "0" + value;
    }
    return Integer.toString(value);
  }

  private static String pad6(int value) {
    String text = Integer.toString(Math.max(value, 0));
    if (text.length() >= 6) {
      return text.substring(0, 6);
    }
    return "000000".substring(text.length()) + text;
  }
}
