/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DvSqlFormattingSupportTest {

  @Test
  void formatForDisplayReturnsEmptyInputUnchanged() {
    assertEquals(null, DvSqlFormattingSupport.formatForDisplay(null));
    assertEquals("", DvSqlFormattingSupport.formatForDisplay(""));
    assertEquals("", DvSqlFormattingSupport.formatForDisplay("   "));
  }

  @Test
  void formatForDisplayBreaksSimpleSelectClauses() {
    String sql =
        "SELECT customer_hk, name FROM sat_customer WHERE name = 'Acme' ORDER BY customer_hk";

    String formatted = DvSqlFormattingSupport.formatForDisplay(sql);

    assertTrue(formatted.contains("SELECT customer_hk, name"));
    assertTrue(formatted.contains("\nFROM sat_customer"));
    assertTrue(formatted.contains("\nWHERE name = 'Acme'"));
    assertTrue(formatted.contains("\nORDER BY customer_hk"));
  }

  @Test
  void formatForDisplayFormatsCteChains() {
    String sql =
        "WITH earliest_satellite_load AS (SELECT MIN(x_load_ts)::date AS earliest_load FROM sat_customer), bounds AS (SELECT earliest_load AS start_date, (CURRENT_DATE - INTERVAL '1 day')::date AS end_date FROM earliest_satellite_load) SELECT hk.customer_hk FROM hub_customer hk";

    String formatted = DvSqlFormattingSupport.formatForDisplay(sql);

    assertTrue(formatted.startsWith("WITH"));
    assertTrue(formatted.contains("earliest_satellite_load AS ("));
    assertTrue(formatted.contains("bounds AS ("));
    assertTrue(formatted.contains("\n    SELECT MIN(x_load_ts)::date AS earliest_load"));
    assertTrue(formatted.contains("\n    FROM sat_customer"));
    assertTrue(formatted.contains("\n  SELECT hk.customer_hk"));
    assertTrue(formatted.contains("\n  FROM hub_customer hk"));
  }

  @Test
  void formatForDisplayFormatsNestedSubquery() {
    String sql =
        "SELECT hk.customer_hk, (SELECT MAX(sat.x_load_ts) FROM sat_customer sat WHERE sat.customer_hk = hk.customer_hk) AS sat_ldts FROM hub_customer hk";

    String formatted = DvSqlFormattingSupport.formatForDisplay(sql);

    assertTrue(formatted.contains("SELECT hk.customer_hk,"));
    assertTrue(formatted.contains("(\n  SELECT MAX(sat.x_load_ts)"));
    assertTrue(formatted.contains("\n  FROM sat_customer sat"));
    assertTrue(formatted.contains("\n  WHERE sat.customer_hk = hk.customer_hk"));
    assertTrue(formatted.contains("\nFROM hub_customer hk"));
  }

  @Test
  void formatForDisplayPreservesLiteralWhitespace() {
    String sql = "SELECT 'value with spaces' AS label FROM dual";

    String formatted = DvSqlFormattingSupport.formatForDisplay(sql);

    assertTrue(formatted.contains("'value with spaces'"));
  }
}
