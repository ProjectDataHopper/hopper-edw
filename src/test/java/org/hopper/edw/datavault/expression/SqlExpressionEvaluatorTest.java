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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlExpressionEvaluatorTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void deletedFlagNullsDate() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("sourceXDeletedFlag"));
    rowMeta.addValueMeta(new ValueMetaTimestamp("sourceXDate"));
    Timestamp date = Timestamp.valueOf("2024-03-15 10:00:00");

    Object kept =
        eval(
            "CASE WHEN sourceXDeletedFlag = 'Y' THEN NULL ELSE sourceXDate END",
            rowMeta,
            new Object[] {"N", date});
    assertEquals(date, kept);

    Object cleared =
        eval(
            "CASE WHEN sourceXDeletedFlag = 'Y' THEN NULL ELSE sourceXDate END",
            rowMeta,
            new Object[] {"Y", date});
    assertNull(cleared);
  }

  @Test
  void andOrCase() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("conditionA"));
    rowMeta.addValueMeta(new ValueMetaString("conditionB"));
    rowMeta.addValueMeta(new ValueMetaString("conditionC"));
    rowMeta.addValueMeta(new ValueMetaString("field"));

    Object value =
        eval(
            "CASE WHEN (conditionA = 'Y' OR conditionB = 'Y') AND conditionC = 'Y' THEN field END",
            rowMeta,
            new Object[] {"N", "Y", "Y", "kept"});
    assertEquals("kept", value);

    Object skipped =
        eval(
            "CASE WHEN (conditionA = 'Y' OR conditionB = 'Y') AND conditionC = 'Y' THEN field END",
            rowMeta,
            new Object[] {"Y", "N", "N", "kept"});
    assertNull(skipped);
  }

  @Test
  void coalesceWithNestedDeletedCase() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("someCode"));
    rowMeta.addValueMeta(new ValueMetaString("sourceYDeletedFlag"));
    rowMeta.addValueMeta(new ValueMetaString("otherCode"));

    Object fromCoalesce =
        eval(
            "COALESCE(someCode, CASE WHEN sourceYDeletedFlag = 'Y' THEN NULL ELSE otherCode END)",
            rowMeta,
            new Object[] {null, "N", "ALT"});
    assertEquals("ALT", fromCoalesce);

    Object preferSomeCode =
        eval(
            "COALESCE(someCode, CASE WHEN sourceYDeletedFlag = 'Y' THEN NULL ELSE otherCode END)",
            rowMeta,
            new Object[] {"MAIN", "N", "ALT"});
    assertEquals("MAIN", preferSomeCode);

    Object bothNull =
        eval(
            "COALESCE(someCode, CASE WHEN sourceYDeletedFlag = 'Y' THEN NULL ELSE otherCode END)",
            rowMeta,
            new Object[] {null, "Y", "ALT"});
    assertNull(bothNull);
  }

  @Test
  void nestedCaseWithVarcharCast() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("sourceZDeletedFlag"));
    rowMeta.addValueMeta(new ValueMetaInteger("field"));

    Object yes =
        eval(
            "CASE WHEN sourceZDeletedFlag = 'Y' THEN NULL ELSE CASE WHEN field = 0 THEN 'N' WHEN field = 1 THEN 'Y' END END :> VARCHAR(4)",
            rowMeta,
            new Object[] {"N", 1L});
    assertEquals("Y", yes);

    Object no =
        eval(
            "CASE WHEN sourceZDeletedFlag = 'Y' THEN NULL ELSE CASE WHEN field = 0 THEN 'N' WHEN field = 1 THEN 'Y' END END :> VARCHAR(4)",
            rowMeta,
            new Object[] {"N", 0L});
    assertEquals("N", no);

    Object deleted =
        eval(
            "CASE WHEN sourceZDeletedFlag = 'Y' THEN NULL ELSE CASE WHEN field = 0 THEN 'N' WHEN field = 1 THEN 'Y' END END :> VARCHAR(4)",
            rowMeta,
            new Object[] {"Y", 1L});
    assertNull(deleted);
  }

  @Test
  void coalesceDefaultWithVarcharCast() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));

    Object fromField =
        eval("COALESCE(fieldA, 'Default value' :> VARCHAR(720))", rowMeta, new Object[] {"hello"});
    assertEquals("hello", fromField);

    Object fromDefault =
        eval("COALESCE(fieldA, 'Default value' :> VARCHAR(720))", rowMeta, new Object[] {null});
    assertEquals("Default value", fromDefault);
  }

  @Test
  void laterExpressionCanReferenceEarlierOutput() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(
                new SqlExpressionSpec("filled", "COALESCE(fieldA, 'x')"),
                new SqlExpressionSpec("upper_filled", "UPPER(filled)")),
            rowMeta,
            new Variables());
    Object[] out = program.evaluate(new Object[] {null});
    assertEquals("x", out[1]);
    assertEquals("X", out[2]);
  }

  @Test
  void evaluateReplacesExistingFieldByIndex() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(new SqlExpressionSpec("fieldA", "UPPER(fieldA)")), rowMeta, new Variables());
    Object[] out = program.evaluate(new Object[] {"ab"});
    assertEquals(1, program.getOutputRowMeta().size());
    assertEquals("AB", out[0]);
  }

  @Test
  void evaluateDropsInputWhenKeepInputFieldsFalse() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(new SqlExpressionSpec("upper_a", "UPPER(fieldA)")),
            rowMeta,
            new Variables(),
            false);
    Object[] out = program.evaluate(new Object[] {"ab"});
    assertEquals(1, program.getOutputRowMeta().size());
    assertEquals("AB", out[0]);
  }

  @Test
  void evaluateWideRowAppendsCalculatedFieldWithoutNameScan() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    Object[] row = new Object[180];
    for (int i = 0; i < 180; i++) {
      rowMeta.addValueMeta(new ValueMetaString("f" + i));
      row[i] = "v" + i;
    }
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(new SqlExpressionSpec("calc", "CONCAT(f0, '#', f179)")),
            rowMeta,
            new Variables());
    Object[] out = program.evaluate(row);
    assertEquals(181, program.getOutputRowMeta().size());
    assertEquals("v0", out[0]);
    assertEquals("v179", out[179]);
    assertEquals("v0#v179", out[180]);
  }

  @Test
  void evaluateReusesOverAllocatedInputRow() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(new SqlExpressionSpec("calc", "UPPER(fieldA)")), rowMeta, new Variables());
    Object[] row = RowDataUtil.allocateRowData(1);
    row[0] = "ab";
    Object[] out = program.evaluate(row);
    assertSame(row, out);
    assertEquals("ab", out[0]);
    assertEquals("AB", out[1]);
  }

  @Test
  void evaluateCopiesWhenInputHasNoSpareSlots() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(new SqlExpressionSpec("calc", "UPPER(fieldA)")), rowMeta, new Variables());
    Object[] row = new Object[] {"ab"};
    Object[] out = program.evaluate(row);
    assertNotSame(row, out);
    assertEquals("ab", out[0]);
    assertEquals("AB", out[1]);
    assertEquals("ab", row[0]);
  }

  @Test
  void hopVariableIsResolvedAsConstant() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    Variables variables = new Variables();
    variables.setVariable("DEFAULT_DESC", "fallback");
    Object value =
        eval("COALESCE(fieldA, '${DEFAULT_DESC}')", rowMeta, new Object[] {null}, variables);
    assertEquals("fallback", value);
  }

  @Test
  void rejectsNow() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionException error =
        assertThrows(
            SqlExpressionException.class,
            () ->
                SqlExpressionCompiler.compile(
                    new SqlExpressionSpec("x", "NOW()"), rowMeta, new Variables()));
    assertTrue(
        error.getMessage().toLowerCase().contains("not allowed")
            || error.getMessage().contains("NOW"));
  }

  @Test
  void rejectsUnknownColumn() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    assertThrows(
        SqlExpressionException.class,
        () ->
            SqlExpressionCompiler.compile(
                new SqlExpressionSpec("x", "missing_col"), rowMeta, new Variables()));
  }

  private static Object eval(String sql, IRowMeta rowMeta, Object[] row) throws Exception {
    return eval(sql, rowMeta, row, new Variables());
  }

  private static Object eval(String sql, IRowMeta rowMeta, Object[] row, Variables variables)
      throws Exception {
    SqlCompiledExpression compiled =
        SqlExpressionCompiler.compile(new SqlExpressionSpec("out", sql), rowMeta, variables);
    return SqlExpressionEvaluator.evaluate(compiled, row);
  }
}
