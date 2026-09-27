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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.Test;

class BvIdentityIncrementalSqlSupportTest {

  @Test
  void sharedConnectionReplaysAChangedMapVersionFromTheEarlierBound() throws Exception {
    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(context("Vault", "Vault"));
    assertTrue(sql.contains("x_load_ts > ?"));
    assertTrue(sql.contains("rule_version <> s.map_rule_version"));
    assertTrue(sql.contains("CASE WHEN m.valid_from < ? THEN m.valid_from ELSE ? END"));
    assertTrue(sql.contains("map_person"));
    assertTrue(sql.contains("bv_customer_scd2"));
  }

  @Test
  void differentConnectionKeepsTheWatermarkOnly() throws Exception {
    String sql = BvScd2PipelineSupport.buildSatelliteTableInputSql(context("Vault", "Business"));
    assertTrue(sql.contains("x_load_ts > ?"));
    assertFalse(sql.contains("map_rule_version"));
  }

  private static BvScd2PipelineSupport.Scd2BuildContext context(String source, String target)
      throws Exception {
    DvSatellite satellite = new DvSatellite("sat_customer");
    BvIdentityMap identityMap = new BvIdentityMap();
    identityMap.setName("map_person");
    identityMap.setTableName("map_person");
    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.getTables().add(identityMap);
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("bv_customer_scd2");
    scd2.setTableName("bv_customer_scd2");
    scd2.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    scd2.setIdentityMapName("map_person");
    scd2.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    return new BvScd2PipelineSupport.Scd2BuildContext(
        scd2,
        satellite,
        bvModel,
        null,
        new BusinessVaultConfiguration(),
        new org.hopper.edw.datavault.metadata.DataVaultConfiguration(),
        null,
        new Variables(),
        new TestDatabaseMeta(source),
        source,
        new TestDatabaseMeta(target),
        target,
        "sat_customer",
        "bv_customer_scd2",
        "bv-scd2",
        "customer_hk",
        null,
        java.util.List.of(),
        "x_load_ts",
        "valid_from",
        "valid_to",
        "x_record_source",
        BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
        BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
        true);
  }
}
