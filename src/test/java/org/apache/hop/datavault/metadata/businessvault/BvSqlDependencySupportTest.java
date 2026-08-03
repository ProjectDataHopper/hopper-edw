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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.junit.jupiter.api.Test;

class BvSqlDependencySupportTest {

  @Test
  void ordersScd2BeforePitBeforeIndependentBusinessTable() {
    BvPitTable pit = new BvPitTable();
    pit.setName("pit_customer");
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("sat_customer_hb");
    BvBusinessTable business = new BvBusinessTable();
    business.setName("dim_customer");
    business.setSqlQuery("SELECT 1");

    List<IBvTable> ordered =
        BusinessVaultUpdateExecutionSupport.orderTablesForPipelineExecution(
            List.of(pit, scd2, business));

    assertEquals(3, ordered.size());
    assertEquals("sat_customer_hb", ordered.get(0).getName());
    assertEquals("pit_customer", ordered.get(1).getName());
    assertEquals("dim_customer", ordered.get(2).getName());
  }

  @Test
  void ordersBusinessTableAfterReferencedBvTable() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable view = new BvBusinessTable();
    view.setName("product_v");
    view.setTableName("product_v");
    view.setSqlQuery("SELECT * FROM {{ ref('s_product') }}");
    model.getTables().add(view);

    // Intentionally list view first
    List<IBvTable> ordered =
        BusinessVaultUpdateExecutionSupport.orderTablesForPipelineExecution(
            List.of(view, scd2), model, new DataVaultModel());

    assertEquals("s_product", ordered.get(0).getName());
    assertEquals("product_v", ordered.get(1).getName());
  }

  @Test
  void detectsSqlRefCycle() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvBusinessTable a = new BvBusinessTable();
    a.setName("a");
    a.setTableName("a");
    a.setSqlQuery("SELECT * FROM {{ ref('b') }}");
    BvBusinessTable b = new BvBusinessTable();
    b.setName("b");
    b.setTableName("b");
    b.setSqlQuery("SELECT * FROM {{ ref('a') }}");
    model.getTables().add(a);
    model.getTables().add(b);

    BvSqlRefResolver.syncRefsFromSql(a, model, new DataVaultModel());
    BvSqlRefResolver.syncRefsFromSql(b, model, new DataVaultModel());

    String cycle = BvSqlDependencySupport.findCycleDescription(model.getTables());
    assertNotNull(cycle);
    assertTrue(cycle.contains("a") && cycle.contains("b"));
  }

  @Test
  void noCycleForLinearChain() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvBusinessTable a = new BvBusinessTable();
    a.setName("a");
    a.setTableName("a");
    a.setSqlQuery("SELECT 1");
    BvBusinessTable b = new BvBusinessTable();
    b.setName("b");
    b.setTableName("b");
    b.setSqlQuery("SELECT * FROM {{ ref('a') }}");
    model.getTables().add(a);
    model.getTables().add(b);
    BvSqlRefResolver.syncRefsFromSql(a, model, new DataVaultModel());
    BvSqlRefResolver.syncRefsFromSql(b, model, new DataVaultModel());
    assertNull(BvSqlDependencySupport.findCycleDescription(model.getTables()));
  }

  @Test
  void recognizesBusinessTableAsExecutable() {
    assertTrue(
        BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(
            BvTableType.BUSINESS_TABLE));
  }
}
