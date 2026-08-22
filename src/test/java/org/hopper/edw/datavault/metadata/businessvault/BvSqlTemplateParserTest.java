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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BvSqlTemplateParserTest {

  @Test
  void parsesOneArgRef() {
    List<BvSqlTemplateParser.MacroOccurrence> macros =
        BvSqlTemplateParser.parse("SELECT * FROM {{ ref('s_product') }} p");
    assertEquals(1, macros.size());
    assertEquals(BvSqlTemplateParser.MacroKind.REF, macros.get(0).kind());
    assertTrue(macros.get(0).isOneArgRef());
    assertEquals("s_product", macros.get(0).refObjectName());
    assertNull(macros.get(0).refModelName());
  }

  @Test
  void parsesTwoArgRef() {
    List<BvSqlTemplateParser.MacroOccurrence> macros =
        BvSqlTemplateParser.parse("FROM {{ ref('customer-360', 's_product') }}");
    assertEquals(1, macros.size());
    assertEquals("customer-360", macros.get(0).refModelName());
    assertEquals("s_product", macros.get(0).refObjectName());
  }

  @Test
  void parsesSource() {
    List<BvSqlTemplateParser.MacroOccurrence> macros =
        BvSqlTemplateParser.parse("JOIN {{ source('refdata', 'ref_lookup_something') }} l ON 1=1");
    assertEquals(1, macros.size());
    assertEquals(BvSqlTemplateParser.MacroKind.SOURCE, macros.get(0).kind());
    assertEquals("refdata", macros.get(0).sourceName());
    assertEquals("ref_lookup_something", macros.get(0).sourceTableName());
  }

  @Test
  void extractsDistinctRefsAndSources() {
    String sql =
        """
        SELECT * FROM {{ ref('a') }} x
        JOIN {{ ref('model', 'b') }} y ON 1=1
        JOIN {{ source('src', 't1') }} s ON 1=1
        JOIN {{ ref('a') }} x2 ON 1=1
        """;
    List<BvSqlRef> refs = BvSqlTemplateParser.extractRefs(sql);
    assertEquals(2, refs.size());
    assertEquals("a", refs.get(0).getObjectName());
    assertEquals("model", refs.get(1).getModelName());
    assertEquals("b", refs.get(1).getObjectName());

    List<BvSqlSource> sources = BvSqlTemplateParser.extractSourceUsages(sql);
    assertEquals(1, sources.size());
    assertEquals("src", sources.get(0).getSourceName());
    assertEquals("t1", sources.get(0).getTableName());
  }

  @Test
  void rewriteReplacesInOrder() {
    String sql = "SELECT * FROM {{ ref('hub_customer') }} h JOIN {{ source('s','t') }} x";
    String rewritten =
        BvSqlTemplateParser.rewrite(
            sql,
            macro ->
                macro.kind() == BvSqlTemplateParser.MacroKind.REF
                    ? "\"hub_customer\""
                    : "ref.lookup");
    assertEquals("SELECT * FROM \"hub_customer\" h JOIN ref.lookup x", rewritten);
  }
}
