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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvSqlRefResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesSameModelBvAndLinkedDvRefs() {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename("/project/models/customer.hbv");
    bvModel.setName("customer");

    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("sat_customer_hb");
    scd2.setTableName("sat_customer_hb");
    bvModel.getTables().add(scd2);

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("customer_enriched");
    sqlTable.setTableName("customer_enriched");
    sqlTable.setSqlQuery(
        "SELECT * FROM {{ ref('sat_customer_hb') }} s JOIN {{ ref('hub_customer') }} h ON 1=1");
    bvModel.getTables().add(sqlTable);

    DataVaultModel dvModel = new DataVaultModel();
    dvModel.setFilename("/project/models/customer.hdv");
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableName("hub_customer");
    dvModel.getTables().add(hub);

    List<BvSqlRef> refs = BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, dvModel);
    assertEquals(2, refs.size());
    assertEquals(BvSqlResolvedKind.BV_TABLE, refs.get(0).getResolvedKind());
    assertEquals("sat_customer_hb", refs.get(0).getResolvedTableName());
    assertEquals(BvSqlResolvedKind.DV_TABLE, refs.get(1).getResolvedKind());
    assertEquals(DvTableType.HUB, refs.get(1).getResolvedDvTableType());
    assertTrue(BusinessVaultDerivativeSupport.hasDerivative(sqlTable, "hub_customer"));
  }

  @Test
  void resolveSqlRewritesRefAndSource() throws Exception {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    bvModel.getTables().add(scd2);

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("product_enriched");
    sqlTable.setTableName("product_enriched");
    sqlTable.setSqlQuery(
        "SELECT p.* FROM {{ ref('s_product') }} p LEFT JOIN {{ source('refdata', 'ref_lookup') }} l ON 1=1");
    sqlTable.getSources().add(new BvSqlSource("refdata", null, "ref", "ref_lookup"));

    String resolved =
        BvSqlRefResolver.resolveSql(
            sqlTable, bvModel, new DataVaultModel(), null, new Variables(), null);
    assertTrue(resolved.contains("s_product"));
    assertTrue(resolved.contains("ref.ref_lookup") || resolved.contains("ref_lookup"));
    assertTrue(!resolved.contains("{{"));
  }

  @Test
  void twoArgRefMatchesLinkedDvModelBasename() {
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename("/x/bv.hbv");
    DataVaultModel dvModel = new DataVaultModel();
    dvModel.setFilename("/x/vault1.hdv");
    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer");
    sat.setTableName("sat_customer");
    dvModel.getTables().add(sat);

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('vault1', 'sat_customer') }}");

    List<BvSqlRef> refs = BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, dvModel);
    assertEquals(1, refs.size());
    assertEquals(BvSqlResolvedKind.DV_TABLE, refs.get(0).getResolvedKind());
  }

  @Test
  void resolvedModelFilenameIsPortableWhenProjectHomeMatches() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/project");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename("/project/models/customer.hbv");
    bvModel.setName("customer");
    bvModel.setDataVaultModelPath("${PROJECT_HOME}/models/customer.hdv");

    DataVaultModel dvModel = new DataVaultModel();
    dvModel.setFilename("/project/models/customer.hdv");
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableName("hub_customer");
    dvModel.getTables().add(hub);

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("customer_enriched");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('hub_customer') }}");

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, dvModel, variables, null);
    assertEquals(1, refs.size());
    assertEquals(BvSqlResolvedKind.DV_TABLE, refs.get(0).getResolvedKind());
    assertEquals("${PROJECT_HOME}/models/customer.hdv", refs.get(0).getResolvedModelFilename());
  }
}
