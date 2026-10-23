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
package org.hopper.edw.datavault.virtualization.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcSqlSchemaResolverTest {

  private static final List<String> SCHEMAS = List.of("crm", "f_orders");

  @Test
  void qualifiedPreviewSelectsTheNamedService() throws Exception {
    assertEquals(
        "f_orders",
        JdbcSqlSchemaResolver.resolve(
            "SELECT * FROM f_orders.d_customer LIMIT 5000", "crm", SCHEMAS));
    assertEquals(
        "f_orders",
        JdbcSqlSchemaResolver.resolve(
            "SELECT * FROM \"f_orders\".\"d_customer\" LIMIT 5000", "crm", SCHEMAS));
  }

  @Test
  void bareTableStaysOnTheConnectionSchema() throws Exception {
    assertEquals(
        "crm",
        JdbcSqlSchemaResolver.resolve("SELECT * FROM d_customer LIMIT 5000", "crm", SCHEMAS));
    assertEquals(
        "crm",
        JdbcSqlSchemaResolver.resolve("SELECT * FROM f_orders LIMIT 5000", "crm", SCHEMAS));
  }

  @Test
  void twoServicesInOneStatementAreRejected() {
    JdbcSqlSchemaResolver.JdbcSqlSchemaException error =
        assertThrows(
            JdbcSqlSchemaResolver.JdbcSqlSchemaException.class,
            () ->
                JdbcSqlSchemaResolver.resolve(
                    "SELECT * FROM f_orders.d_customer JOIN crm.contacts ON 1 = 1",
                    "crm",
                    SCHEMAS));
    assertTrue(error.getMessage().contains("f_orders"));
    assertTrue(error.getMessage().contains("crm"));
  }
}
