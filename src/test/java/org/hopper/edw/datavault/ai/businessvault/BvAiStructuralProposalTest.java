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
package org.hopper.edw.datavault.ai.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBridge;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.junit.jupiter.api.Test;

class BvAiStructuralProposalTest {

  @Test
  void appliesAddScd2AndBindSatellite() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    DvAiProposal add = new DvAiProposal();
    add.setType(DvAiProposal.Type.ADD_SCD2);
    add.setParameters(
        Map.of(
            "name", "customer_360", "parentHubName", "hub_customer", "tableName", "bv_customer"));

    BvAiProposalApplier.apply(model, List.of(add), null, null);

    BvScd2Table table = (BvScd2Table) model.findTable("customer_360");
    assertNotNull(table);
    assertEquals("bv_customer", table.getTableName());
    assertEquals("hub_customer", table.getParentHubName());

    DvAiProposal bind = new DvAiProposal();
    bind.setType(DvAiProposal.Type.BIND_DV_TABLE);
    bind.setParameters(
        Map.of(
            "tableName",
            "customer_360",
            "dvTableName",
            "sat_customer",
            "dvTableType",
            "SATELLITE"));
    BvAiProposalApplier.apply(model, List.of(bind), null, null);

    assertTrue(
        table.getDerivatives().stream()
            .anyMatch(
                ref ->
                    "sat_customer".equals(ref.getDvTableName())
                        && ref.getDvTableType() == DvTableType.SATELLITE));
  }

  @Test
  void appliesSqlOnBusinessTable() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    DvAiProposal add = new DvAiProposal();
    add.setType(DvAiProposal.Type.ADD_BUSINESS_TABLE);
    add.setParameters(Map.of("name", "customer_mart"));
    BvAiProposalApplier.apply(model, List.of(add), null, null);

    DvAiProposal sql = new DvAiProposal();
    sql.setType(DvAiProposal.Type.SET_SQL_QUERY);
    sql.setParameters(
        Map.of(
            "tableName", "customer_mart", "sqlQuery", "select * from {{ ref('hub_customer') }}"));
    BvAiProposalApplier.apply(model, List.of(sql), null, null);

    BvBusinessTable table = (BvBusinessTable) model.findTable("customer_mart");
    assertEquals("select * from {{ ref('hub_customer') }}", table.getSqlQuery());
  }

  @Test
  void blocksDuplicateAddScd2() {
    BusinessVaultModel model = new BusinessVaultModel();
    BvScd2Table existing = new BvScd2Table();
    existing.setName("customer_360");
    model.getTables().add(existing);

    DvAiProposal add = new DvAiProposal();
    add.setType(DvAiProposal.Type.ADD_SCD2);
    add.setParameters(Map.of("name", "customer_360"));

    BvAiProposalValidator.ValidationResult result =
        BvAiProposalValidator.validate(model, List.of(add), null, null).get(0);
    assertEquals(BvAiProposalValidator.Status.BLOCKED, result.getStatus());
  }

  @Test
  void appliesTableLocation() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_360");
    model.getTables().add(table);

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.SET_TABLE_LOCATION);
    proposal.setParameters(
        Map.of("tableName", "customer_360", "locationX", "200", "locationY", "40"));
    BvAiProposalApplier.apply(model, List.of(proposal), null, null);

    assertEquals(200, table.getLocation().x);
    assertEquals(40, table.getLocation().y);
  }

  @Test
  void appliesAddBridgeWithLinkAndWeight() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    DvAiProposal add = new DvAiProposal();
    add.setType(DvAiProposal.Type.ADD_BRIDGE);
    add.setParameters(
        Map.of(
            "name",
            "customer_account_bridge",
            "tableName",
            "br_customer_account",
            "linkName",
            "lnk_customer_account",
            "weightField",
            "allocation_weight"));
    BvAiProposalApplier.apply(model, List.of(add), null, null);

    BvBridge table = (BvBridge) model.findTable("customer_account_bridge");
    assertNotNull(table);
    assertEquals("br_customer_account", table.getTableName());
    assertEquals("allocation_weight", table.getWeightField());
    assertTrue(
        table.getDerivatives().stream()
            .anyMatch(
                ref ->
                    "lnk_customer_account".equals(ref.getDvTableName())
                        && ref.getDvTableType() == DvTableType.LINK));
  }
}
