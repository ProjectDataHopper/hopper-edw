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
import org.apache.hop.datavault.metadata.DvTableType;
import org.junit.jupiter.api.Test;

class BusinessVaultUpdateExecutionSupportTest {

  @Test
  void recognizesScd2PitAndBusinessTableAsExecutable() {
    assertTrue(BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(BvTableType.SCD2));
    assertTrue(BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(BvTableType.PIT));
    assertTrue(
        BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(
            BvTableType.BUSINESS_TABLE));
  }

  @Test
  void ordersScd2BeforePitBeforeBusinessTable() {
    BvPitTable pit = new BvPitTable();
    pit.setName("pit_customer");
    pit.getDerivatives().add(new BvDerivativeRef("hub_customer", DvTableType.HUB));

    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("sat_customer_hb");
    scd2.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));

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
}
