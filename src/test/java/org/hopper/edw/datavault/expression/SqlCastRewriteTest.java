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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlCastRewriteTest {

  @Test
  void rewritesClickHouseCastOnStringLiteral() throws Exception {
    assertEquals(
        "CAST('Default value' AS VARCHAR(720))",
        SqlCastRewrite.rewrite("'Default value' :> VARCHAR(720)"));
  }

  @Test
  void rewritesPostgresCast() throws Exception {
    assertEquals("CAST(fieldA AS VARCHAR(4))", SqlCastRewrite.rewrite("fieldA::VARCHAR(4)"));
  }

  @Test
  void rewritesCastOnCaseEnd() throws Exception {
    String sql = "CASE WHEN field = 0 THEN 'N' WHEN field = 1 THEN 'Y' END :> VARCHAR(4)";
    String rewritten = SqlCastRewrite.rewrite(sql);
    assertTrue(rewritten.startsWith("CAST("));
    assertTrue(rewritten.endsWith(" AS VARCHAR(4))"));
    assertTrue(rewritten.contains("CASE WHEN field = 0 THEN 'N'"));
  }

  @Test
  void doesNotRewriteInsideQuotes() throws Exception {
    String sql = "'value :> VARCHAR(4) and other::INT'";
    assertEquals(sql, SqlCastRewrite.rewrite(sql));
  }

  @Test
  void leavesStandardCastAlone() throws Exception {
    String sql = "CAST(fieldA AS VARCHAR(720))";
    assertEquals(sql, SqlCastRewrite.rewrite(sql));
    assertFalse(sql.contains(":>"));
  }
}
