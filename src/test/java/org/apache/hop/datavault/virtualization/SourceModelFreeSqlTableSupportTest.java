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
package org.apache.hop.datavault.virtualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.virtualization.sql.SourceModelFreeSqlTableSupport;
import org.junit.jupiter.api.Test;

class SourceModelFreeSqlTableSupportTest {

  @Test
  void extractsBothJoinTablesFromFreeSql() {
    SourceModel model = modelWith("customer_hub", "customer_address", "order_header");
    List<String> names =
        SourceModelFreeSqlTableSupport.referencedTableNames(
            model,
            """
            SELECT c.customer_id, a.city
            FROM customer_hub c
            INNER JOIN customer_address a ON c.customer_id = a.customer_id
            WHERE c.customer_id > 0
            ORDER BY a.city
            LIMIT 100
            """);
    assertEquals(List.of("customer_hub", "customer_address"), names);
  }

  @Test
  void ignoresUnknownTables() {
    SourceModel model = modelWith("customer_hub");
    List<String> names =
        SourceModelFreeSqlTableSupport.referencedTableNames(
            model, "SELECT * FROM customer_hub h JOIN no_such_table t ON h.id = t.id");
    assertEquals(List.of("customer_hub"), names);
  }

  @Test
  void invalidSqlReturnsEmpty() {
    SourceModel model = modelWith("customer_hub");
    assertTrue(SourceModelFreeSqlTableSupport.referencedTableNames(model, "SELECT FROM").isEmpty());
  }

  private static SourceModel modelWith(String... tableNames) {
    SourceModel model = new SourceModel();
    model.setName("m");
    for (String name : tableNames) {
      SourceTable table = new SourceTable(name);
      table.getColumns().add(new SourceColumn("id"));
      model.getTables().add(table);
    }
    return model;
  }
}
