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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SqlSourceLineageSupportTest {

  private static final String RETAIL_D_CUSTOMER_SQL =
      """
      SELECT hc.customer_id, sc.*
      FROM   hub_customer hc
      JOIN   customer_360_bv sc
        ON   hc.customer_hk = sc.customer_hk
      WHERE  sc.x_to_ts = '9999-12-31 23:59:59'
      """;

  @Test
  void extractTableNamesFromRetailCustomerSql() {
    Set<String> tables = SqlSourceLineageSupport.extractTableNames(RETAIL_D_CUSTOMER_SQL);
    assertTrue(tables.contains("hub_customer"));
    assertTrue(tables.contains("customer_360_bv"));
  }

  @Test
  void extractAliasesAndStars() {
    Map<String, String> aliases =
        SqlSourceLineageSupport.extractTableAliases(RETAIL_D_CUSTOMER_SQL);
    assertEquals("hub_customer", aliases.get("hc"));
    assertEquals("customer_360_bv", aliases.get("sc"));
    Set<String> stars = SqlSourceLineageSupport.extractStarAliases(RETAIL_D_CUSTOMER_SQL);
    assertTrue(stars.contains("sc"));
  }

  @Test
  void referencesTable() {
    assertTrue(SqlSourceLineageSupport.referencesTable(RETAIL_D_CUSTOMER_SQL, "customer_360_bv"));
    assertTrue(SqlSourceLineageSupport.referencesTable(RETAIL_D_CUSTOMER_SQL, "CUSTOMER_360_BV"));
  }
}
