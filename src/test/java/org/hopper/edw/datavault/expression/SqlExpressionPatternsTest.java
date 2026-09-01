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

class SqlExpressionPatternsTest {

  @Test
  void catalogHasCaseAndCoalesceSnippets() {
    assertTrue(
        SqlExpressionPatterns.all().stream()
            .anyMatch(p -> p.snippet().startsWith("CASE WHEN") && p.snippet().contains("END")));
    assertTrue(
        SqlExpressionPatterns.all().stream()
            .anyMatch(p -> p.snippet().startsWith("COALESCE(, , , , , )")));
    assertTrue(SqlExpressionPatterns.all().stream().anyMatch(p -> p.snippet().startsWith("HEX()")));
    assertTrue(
        SqlExpressionPatterns.all().stream().anyMatch(p -> p.snippet().startsWith("DATE_FORMAT(")));
    assertFalse(SqlExpressionPatterns.all().isEmpty());
    for (SqlExpressionPattern pattern : SqlExpressionPatterns.all()) {
      assertFalse(pattern.snippet().isBlank());
      assertFalse(pattern.labelKey().isBlank());
    }
  }

  @Test
  void quoteIdentifierLeavesSimpleNames() {
    assertEquals("cust_email", SqlExpressionPatterns.quoteIdentifier("cust_email"));
    assertEquals("\"valid from\"", SqlExpressionPatterns.quoteIdentifier("valid from"));
  }
}
