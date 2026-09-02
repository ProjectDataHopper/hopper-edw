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
package org.hopper.edw.datavault.metadata.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvDatabaseSourcePreviewSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void applyRowLimitAppendsPostgresLimit() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql = "SELECT id, name FROM public.customers";
    String limited = DvDatabaseSourcePreviewSupport.applyRowLimit(postgres, sql, 1000);
    assertTrue(limited.toLowerCase().contains("limit 1000"), limited);
    assertTrue(limited.startsWith("SELECT id, name FROM"), limited);
  }

  @Test
  void applyRowLimitDoesNotDoubleApply() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql = "SELECT * FROM customers LIMIT 10";
    assertEquals(sql, DvDatabaseSourcePreviewSupport.applyRowLimit(postgres, sql, 1000));
  }

  @Test
  void applyRowLimitNoOpForNonPositiveLimit() {
    DatabaseMeta postgres =
        new DatabaseMeta("pg", "PostgreSQL", "Native", "", "localhost", "test", "user", "");
    String sql = "SELECT * FROM customers";
    assertEquals(sql, DvDatabaseSourcePreviewSupport.applyRowLimit(postgres, sql, 0));
  }
}
