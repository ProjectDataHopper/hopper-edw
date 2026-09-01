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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;

/** Runs SCD2 calculation tests in-process (no database). */
public final class BvScd2CalculationTestRunner {

  private static final Class<?> PKG = BvScd2CalculationValidationSupport.class;

  private BvScd2CalculationTestRunner() {}

  public static void runAll(
      List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      IRowMeta collapseLayout,
      IVariables variables) {
    if (scd2Table == null || collapseLayout == null) {
      return;
    }
    SqlExpressionProgram program;
    try {
      program =
          SqlExpressionProgram.compile(
              BvScd2CalculationValidationSupport.toSpecs(scd2Table.getCalculations(), variables),
              collapseLayout,
              variables);
    } catch (Exception e) {
      return;
    }

    for (BvScd2CalculationTestCase testCase : scd2Table.getCalculationTests()) {
      if (testCase == null || Utils.isEmpty(testCase.getName())) {
        continue;
      }
      try {
        runColumnTest(program, collapseLayout, testCase);
      } catch (Exception e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2CalculationValidationSupport.Error.TestFailed",
                    scd2Table.getName(),
                    testCase.getName(),
                    e.getMessage()),
                scd2Table));
      }
    }

    for (BvScd2CollapseTestCase testCase : scd2Table.getCollapseTests()) {
      if (testCase == null || Utils.isEmpty(testCase.getName())) {
        continue;
      }
      try {
        runCollapseTest(program, collapseLayout, testCase, variables);
      } catch (Exception e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvScd2CalculationValidationSupport.Error.TestFailed",
                    scd2Table.getName(),
                    testCase.getName(),
                    e.getMessage()),
                scd2Table));
      }
    }
  }

  static void runColumnTest(
      SqlExpressionProgram program, IRowMeta collapseLayout, BvScd2CalculationTestCase testCase)
      throws Exception {
    Object[] row = RowDataUtil.allocateRowData(collapseLayout.size());
    for (BvScd2NamedValue input : testCase.getInputs()) {
      if (input == null || Utils.isEmpty(input.getName())) {
        continue;
      }
      int idx = collapseLayout.indexOfValue(input.getName());
      if (idx < 0) {
        throw new IllegalArgumentException("Unknown input field '" + input.getName() + "'");
      }
      row[idx] = convert(collapseLayout.getValueMeta(idx), input.getValue());
    }
    Object[] output = program.evaluate(row);
    IRowMeta outputMeta = program.getOutputRowMeta();
    for (BvScd2NamedValue expected : testCase.getExpected()) {
      if (expected == null || Utils.isEmpty(expected.getName())) {
        continue;
      }
      int idx = outputMeta.indexOfValue(expected.getName());
      if (idx < 0) {
        throw new IllegalArgumentException("Unknown expected field '" + expected.getName() + "'");
      }
      String actual = stringify(outputMeta.getValueMeta(idx), output[idx]);
      String wanted = expected.getValue() == null ? "" : expected.getValue();
      if (!valuesEqual(wanted, actual)) {
        throw new IllegalStateException(
            expected.getName() + " expected '" + wanted + "' but was '" + actual + "'");
      }
    }
  }

  static void runCollapseTest(
      SqlExpressionProgram program,
      IRowMeta collapseLayout,
      BvScd2CollapseTestCase testCase,
      IVariables variables)
      throws Exception {
    String inputPath = resolvePath(testCase.getInputCsvPath(), variables);
    String expectedPath = resolvePath(testCase.getExpectedCsvPath(), variables);
    if (Utils.isEmpty(inputPath) || Utils.isEmpty(expectedPath)) {
      throw new IllegalArgumentException("Input and expected CSV paths are required");
    }
    List<String[]> inputRows = readCsv(inputPath);
    List<String[]> expectedRows = readCsv(expectedPath);
    if (inputRows.size() != expectedRows.size()) {
      throw new IllegalStateException(
          "Input CSV has "
              + (inputRows.size() - 1)
              + " data rows but expected CSV has "
              + (expectedRows.size() - 1));
    }
    if (inputRows.isEmpty() || expectedRows.isEmpty()) {
      throw new IllegalArgumentException("CSV files must include a header row");
    }
    String[] inputHeader = inputRows.get(0);
    String[] expectedHeader = expectedRows.get(0);
    IRowMeta outputMeta = program.getOutputRowMeta();
    for (int r = 1; r < inputRows.size(); r++) {
      Object[] row = RowDataUtil.allocateRowData(collapseLayout.size());
      String[] input = inputRows.get(r);
      for (int c = 0; c < inputHeader.length && c < input.length; c++) {
        int idx = collapseLayout.indexOfValue(inputHeader[c]);
        if (idx >= 0) {
          row[idx] = convert(collapseLayout.getValueMeta(idx), input[c]);
        }
      }
      Object[] output = program.evaluate(row);
      String[] expected = expectedRows.get(r);
      for (int c = 0; c < expectedHeader.length && c < expected.length; c++) {
        int idx = outputMeta.indexOfValue(expectedHeader[c]);
        if (idx < 0) {
          continue;
        }
        String actual = stringify(outputMeta.getValueMeta(idx), output[idx]);
        if (!valuesEqual(expected[c], actual)) {
          throw new IllegalStateException(
              "Row "
                  + r
                  + " field "
                  + expectedHeader[c]
                  + " expected '"
                  + expected[c]
                  + "' but was '"
                  + actual
                  + "'");
        }
      }
    }
  }

  private static String resolvePath(String path, IVariables variables) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    return variables != null ? variables.resolve(path) : path;
  }

  private static Object convert(IValueMeta valueMeta, String text) throws Exception {
    if (valueMeta == null || Utils.isEmpty(text)) {
      return null;
    }
    // convertMeta describes the incoming text, not the target field. Passing the target
    // Timestamp/Date meta makes Hop treat the String as a native value and ClassCast.
    IValueMeta stringMeta = new ValueMetaString(valueMeta.getName());
    return valueMeta.convertDataFromString(text, stringMeta, null, null, IValueMeta.TRIM_TYPE_BOTH);
  }

  private static String stringify(IValueMeta valueMeta, Object value) throws Exception {
    if (valueMeta == null || value == null || valueMeta.isNull(value)) {
      return "";
    }
    return ConstNvl(valueMeta.getString(value));
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }

  private static boolean valuesEqual(String expected, String actual) {
    String left = expected == null ? "" : expected;
    String right = actual == null ? "" : actual;
    return left.equals(right);
  }

  static List<String[]> readCsv(String vfsPath) throws Exception {
    List<String[]> rows = new ArrayList<>();
    try (InputStream in = HopVfs.getInputStream(vfsPath);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          continue;
        }
        rows.add(parseCsvLine(line));
      }
    }
    return rows;
  }

  static String[] parseCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            current.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          current.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        fields.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString());
    return fields.toArray(new String[0]);
  }
}
