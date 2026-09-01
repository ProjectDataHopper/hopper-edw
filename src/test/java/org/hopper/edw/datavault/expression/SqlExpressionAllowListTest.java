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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlExpressionAllowListTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void rejectsCurrentTimestamp() {
    assertForbidden("CURRENT_TIMESTAMP");
  }

  @Test
  void rejectsRandom() {
    assertForbidden("RAND()");
  }

  @Test
  void rejectsSubquery() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    assertThrows(
        SqlExpressionException.class,
        () ->
            SqlExpressionCompiler.compile(
                new SqlExpressionSpec("x", "(SELECT fieldA FROM hop_row)"),
                rowMeta,
                new Variables()));
  }

  private static void assertForbidden(String expression) {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("fieldA"));
    SqlExpressionException error =
        assertThrows(
            SqlExpressionException.class,
            () ->
                SqlExpressionCompiler.compile(
                    new SqlExpressionSpec("x", expression), rowMeta, new Variables()));
    assertTrue(
        error.getMessage().toLowerCase().contains("not allowed")
            || error.getMessage().toLowerCase().contains("error"),
        error.getMessage());
  }
}
