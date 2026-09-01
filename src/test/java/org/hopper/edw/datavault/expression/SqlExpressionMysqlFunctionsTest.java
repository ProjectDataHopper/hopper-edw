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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlExpressionMysqlFunctionsTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void hexStringAndIntegerMatchMysql() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));
    rowMeta.addValueMeta(new ValueMetaInteger("n"));

    assertEquals("616263", eval("HEX(name)", rowMeta, new Object[] {"abc", 0L}));
    assertEquals("C3A9", eval("HEX(name)", rowMeta, new Object[] {"é", 0L}));
    assertEquals("FF", eval("HEX(n)", rowMeta, new Object[] {"x", 255L}));
    assertEquals("A", eval("HEX(10)", rowMeta, new Object[] {"x", 0L}));
    assertNull(eval("HEX(name)", rowMeta, new Object[] {null, 0L}));
  }

  @Test
  void unhexRoundTripAndInvalid() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("hex"));

    assertArrayEquals(
        "MySQL".getBytes(StandardCharsets.US_ASCII),
        (byte[]) eval("UNHEX(hex)", rowMeta, new Object[] {"4D7953514C"}));
    assertEquals("4D7953514C", eval("HEX(UNHEX(hex))", rowMeta, new Object[] {"4D7953514C"}));
    assertArrayEquals(new byte[] {0x0A}, SqlExpressionMysqlFunctions.unhex("A"));
    assertNull(eval("UNHEX(hex)", rowMeta, new Object[] {"GG"}));
    assertNull(eval("UNHEX(hex)", rowMeta, new Object[] {null}));
  }

  @Test
  void md5IsMysqlLowercaseHex() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));

    assertEquals(
        "900150983cd24fb0d6963f7d28e17f72", eval("MD5(name)", rowMeta, new Object[] {"abc"}));
    assertEquals("d41d8cd98f00b204e9800998ecf8427e", eval("MD5('')", rowMeta, new Object[] {"x"}));
    assertNull(eval("MD5(name)", rowMeta, new Object[] {null}));
  }

  @Test
  void concatIsNullInNullOutAndNary() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("a"));
    rowMeta.addValueMeta(new ValueMetaString("b"));
    rowMeta.addValueMeta(new ValueMetaString("c"));

    assertEquals("abcd", eval("CONCAT(a, b, c)", rowMeta, new Object[] {"a", "bc", "d"}));
    assertNull(eval("CONCAT(a, b, c)", rowMeta, new Object[] {"a", null, "d"}));
    assertEquals("a1", eval("CONCAT(a, 1)", rowMeta, new Object[] {"a", "x", "y"}));
  }

  @Test
  void dateFormatMatchesMysqlSpecifiers() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaTimestamp("ts"));
    Timestamp ts = Timestamp.valueOf("2024-03-15 09:05:07");

    assertEquals(
        "2024-03-15 09:05:07",
        eval("DATE_FORMAT(ts, '%Y-%m-%d %H:%i:%s')", rowMeta, new Object[] {ts}));
    assertEquals("15/03/24", eval("DATE_FORMAT(ts, '%d/%m/%y')", rowMeta, new Object[] {ts}));
    assertEquals("09:05:07 AM", eval("DATE_FORMAT(ts, '%r')", rowMeta, new Object[] {ts}));
    assertEquals("Friday", eval("DATE_FORMAT(ts, '%W')", rowMeta, new Object[] {ts}));
    assertNull(eval("DATE_FORMAT(ts, '%Y')", rowMeta, new Object[] {null}));
  }

  @Test
  void dateFormatHelperUsesSundayZeroWeekday() {
    LocalDateTime sunday = LocalDateTime.of(2024, 3, 17, 12, 0, 0);
    assertEquals("0", SqlExpressionMysqlFunctions.formatMysql(sunday, "%w"));
    assertEquals("PM", SqlExpressionMysqlFunctions.formatMysql(sunday, "%p"));
  }

  @Test
  void hexUtf8Bytes() {
    assertEquals("C3A9", SqlExpressionMysqlFunctions.hex("é".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void toDateHelperParsesDatetime() throws Exception {
    assertEquals(
        Timestamp.valueOf("2019-03-01 10:40:27"),
        SqlExpressionMysqlFunctions.toDate("03/01/2019 10:40:27", "MM/DD/YYYY HH:MI:SS"));
    assertEquals(
        Timestamp.valueOf("2019-03-01 14:01:00"),
        SqlExpressionMysqlFunctions.toDate("03/01/2019 14:01:00", "MM/DD/YYYY HH24:MI:SS"));
  }

  @Test
  void toDateMatchesSinglestoreExamples() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("s"));

    assertEquals(
        Timestamp.valueOf("2019-03-01 00:00:00"),
        eval("TO_DATE(s, 'MM/DD/YYYY')", rowMeta, new Object[] {"03/01/2019"}));
    assertEquals(
        Timestamp.valueOf("2019-03-01 10:40:27"),
        eval("TO_DATE(s, 'MM/DD/YYYY HH:MI:SS')", rowMeta, new Object[] {"03/01/2019 10:40:27"}));
    assertEquals(
        Timestamp.valueOf("2019-03-01 00:00:00"),
        eval(
            "TO_DATE(s, 'The day is MONTH DD, YYYY')",
            rowMeta,
            new Object[] {"The day is March 01, 2019"}));
    assertEquals(
        Timestamp.valueOf("2019-03-01 14:01:00"),
        eval("TO_DATE(s, 'MM/DD/YYYY HH:MI PM')", rowMeta, new Object[] {"03/01/2019 02:01 PM"}));
  }

  @Test
  void toDateAllowsMismatchedPunctuationAndFillsMissingParts() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("s"));
    LocalDate today = LocalDate.now();

    assertEquals(
        Timestamp.valueOf("2019-03-01 00:00:00"),
        eval("TO_DATE(s, 'MM/DD/YYYY')", rowMeta, new Object[] {"03-01-2019"}));
    assertEquals(
        Timestamp.valueOf(LocalDate.of(2019, today.getMonth(), 1).atStartOfDay()),
        eval("TO_DATE(s, 'YYYY')", rowMeta, new Object[] {"2019"}));
    assertEquals(
        Timestamp.valueOf(LocalDate.of(today.getYear(), 11, 1).atStartOfDay()),
        eval("TO_DATE(s, 'MM')", rowMeta, new Object[] {"11"}));
    assertEquals(
        Timestamp.valueOf(LocalDate.of(today.getYear(), today.getMonth(), 15).atStartOfDay()),
        eval("TO_DATE(s, 'DD')", rowMeta, new Object[] {"15"}));
  }

  @Test
  void toDateNullAndLiteralMismatch() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("s"));
    assertNull(eval("TO_DATE(s, 'MM/DD/YYYY')", rowMeta, new Object[] {null}));
    assertNull(eval("TO_DATE(s, 'MM/DD/YYYY')", rowMeta, new Object[] {"not-a-date"}));
  }

  @Test
  void toDateRejectsInvalidCalendarDay() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("s"));
    assertThrows(
        SqlExpressionException.class,
        () -> eval("TO_DATE(s, 'MM/DD/YYYY')", rowMeta, new Object[] {"02/30/2019"}));
  }

  private static Object eval(String sql, IRowMeta rowMeta, Object[] row) throws Exception {
    SqlCompiledExpression compiled =
        SqlExpressionCompiler.compile(new SqlExpressionSpec("out", sql), rowMeta, new Variables());
    return SqlExpressionEvaluator.evaluate(compiled, row);
  }
}
