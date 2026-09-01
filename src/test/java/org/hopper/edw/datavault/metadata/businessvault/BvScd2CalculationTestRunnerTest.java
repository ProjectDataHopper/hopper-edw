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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvScd2CalculationTestRunnerTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void columnTestPassesAndFails() throws Exception {
    RowMeta collapse = new RowMeta();
    collapse.addValueMeta(new ValueMetaString("deleted_flag"));
    collapse.addValueMeta(new ValueMetaString("event_date"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(
                new SqlExpressionSpec(
                    "x_date", "CASE WHEN deleted_flag = 'Y' THEN NULL ELSE event_date END")),
            collapse,
            new Variables());

    BvScd2CalculationTestCase pass = new BvScd2CalculationTestCase("keep");
    pass.getInputs().add(new BvScd2NamedValue("deleted_flag", "N"));
    pass.getInputs().add(new BvScd2NamedValue("event_date", "2024-01-01"));
    pass.getExpected().add(new BvScd2NamedValue("x_date", "2024-01-01"));
    BvScd2CalculationTestRunner.runColumnTest(program, collapse, pass);

    BvScd2CalculationTestCase fail = new BvScd2CalculationTestCase("wrong");
    fail.getInputs().add(new BvScd2NamedValue("deleted_flag", "Y"));
    fail.getInputs().add(new BvScd2NamedValue("event_date", "2024-01-01"));
    fail.getExpected().add(new BvScd2NamedValue("x_date", "2024-01-01"));
    assertThrows(
        IllegalStateException.class,
        () -> BvScd2CalculationTestRunner.runColumnTest(program, collapse, fail));
  }

  @Test
  void columnTestConvertsTimestampInputs() throws Exception {
    RowMeta collapse = new RowMeta();
    collapse.addValueMeta(new ValueMetaString("deleted_flag"));
    collapse.addValueMeta(new ValueMetaTimestamp("event_date"));
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            List.of(
                new SqlExpressionSpec(
                    "x_date",
                    "CASE WHEN deleted_flag = 'Y' THEN NULL ELSE event_date END",
                    "Timestamp",
                    -1,
                    -1)),
            collapse,
            new Variables());

    BvScd2CalculationTestCase deleted = new BvScd2CalculationTestCase("deleted-nulls-date");
    deleted.getInputs().add(new BvScd2NamedValue("deleted_flag", "Y"));
    deleted.getInputs().add(new BvScd2NamedValue("event_date", "2023-07-01 00:00:00"));
    deleted.getExpected().add(new BvScd2NamedValue("x_date", ""));
    BvScd2CalculationTestRunner.runColumnTest(program, collapse, deleted);

    Timestamp kept = Timestamp.valueOf("2023-07-01 00:00:00");
    ValueMetaTimestamp tsMeta = new ValueMetaTimestamp("x_date");
    BvScd2CalculationTestCase keep = new BvScd2CalculationTestCase("keep-date");
    keep.getInputs().add(new BvScd2NamedValue("deleted_flag", "N"));
    keep.getInputs().add(new BvScd2NamedValue("event_date", "2023-07-01 00:00:00"));
    keep.getExpected().add(new BvScd2NamedValue("x_date", tsMeta.getString(kept)));
    BvScd2CalculationTestRunner.runColumnTest(program, collapse, keep);
  }

  @Test
  void checkModelReportsFailedTest() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_bv");
    table.getCalculations().add(new BvScd2Calculation("upper_name", "UPPER(name)"));
    BvScd2CalculationTestCase testCase = new BvScd2CalculationTestCase("case");
    testCase.getInputs().add(new BvScd2NamedValue("name", "abc"));
    testCase.getExpected().add(new BvScd2NamedValue("upper_name", "nope"));
    table.getCalculationTests().add(testCase);

    RowMeta collapse = new RowMeta();
    collapse.addValueMeta(new ValueMetaString("name"));
    List<ICheckResult> remarks = new ArrayList<>();
    BvScd2CalculationTestRunner.runAll(remarks, table, collapse, new Variables());
    assertEquals(1, remarks.size());
    assertTrue(remarks.get(0).getText().contains("case"));
  }
}
