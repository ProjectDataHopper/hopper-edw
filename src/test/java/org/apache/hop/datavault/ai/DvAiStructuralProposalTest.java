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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.junit.jupiter.api.Test;

class DvAiStructuralProposalTest {

  @Test
  void appliesAddLinkBetweenExistingHubs() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setTables(new ArrayList<>(List.of(new DvHub("HUB_A"), new DvHub("HUB_B"))));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_LINK);
    proposal.setParameters(
        Map.of("name", "LNK_A_B", "hubNames", "HUB_A,HUB_B", "tableName", "lnk_a_b"));

    DvAiProposalApplier.apply(model, List.of(proposal), null, null);

    DvLink link = model.findLink("LNK_A_B");
    assertNotNull(link);
    assertEquals(List.of("HUB_A", "HUB_B"), link.getHubNames());
    assertEquals("lnk_a_b", link.getTableName());
  }

  @Test
  void appliesAddSatelliteOnHub() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setTables(new ArrayList<>(List.of(new DvHub("HUB_CUSTOMER"))));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_SATELLITE);
    proposal.setParameters(
        Map.of(
            "name",
            "SAT_CUSTOMER",
            "hubName",
            "HUB_CUSTOMER",
            "recordSource",
            "customer",
            "attributeNames",
            "email,name"));

    DvAiProposalApplier.apply(model, List.of(proposal), null, null);

    DvSatellite satellite = (DvSatellite) model.findTable("SAT_CUSTOMER");
    assertNotNull(satellite);
    assertEquals("HUB_CUSTOMER", satellite.getHubName());
    assertNull(satellite.getLinkName());
    assertEquals("customer", satellite.getRecordSource());
    assertEquals(2, satellite.getAttributes().size());
  }

  @Test
  void appliesSetTableLocation() throws Exception {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("HUB_X");
    model.setTables(new ArrayList<>(List.of(hub)));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.SET_TABLE_LOCATION);
    proposal.setParameters(Map.of("tableName", "HUB_X", "locationX", "240", "locationY", "120"));

    DvAiProposalApplier.apply(model, List.of(proposal), null, null);

    assertEquals(240, hub.getLocation().x);
    assertEquals(120, hub.getLocation().y);
  }

  @Test
  void bindsRecordSourceOnHub() throws Exception {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("HUB_CUSTOMER");
    hub.setRecordSources(List.of("customer"));
    model.setTables(new ArrayList<>(List.of(hub)));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.BIND_RECORD_SOURCE);
    proposal.setParameters(Map.of("tableName", "HUB_CUSTOMER", "recordSource", "orders"));

    DvAiProposalApplier.apply(model, List.of(proposal), null, null);

    assertEquals(List.of("customer", "orders"), hub.getRecordSources());
  }

  @Test
  void validatesAddLinkWithoutMetadataProvider() {
    DataVaultModel model = new DataVaultModel();
    model.setTables(new ArrayList<>(List.of(new DvHub("HUB_A"), new DvHub("HUB_B"))));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_LINK);
    proposal.setParameters(Map.of("name", "LNK_A_B", "hubNames", "HUB_A,HUB_B"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal), null, null).get(0);

    assertEquals(DvAiProposalValidator.Status.OK, result.getStatus());
  }

  @Test
  void blocksAddLinkWhenHubMissing() {
    DataVaultModel model = new DataVaultModel();
    model.setTables(new ArrayList<>(List.of(new DvHub("HUB_A"))));

    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_LINK);
    proposal.setParameters(Map.of("name", "LNK_A_B", "hubNames", "HUB_A,HUB_B"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal), null, null).get(0);

    assertEquals(DvAiProposalValidator.Status.BLOCKED, result.getStatus());
  }

  @Test
  void blocksStructuralProposalWithoutMetadataProvider() {
    DataVaultModel model = new DataVaultModel();
    DvAiProposal proposal = new DvAiProposal();
    proposal.setType(DvAiProposal.Type.ADD_HUB);
    proposal.setParameters(Map.of("name", "HUB_CUSTOMER", "recordSource", "customer"));

    DvAiProposalValidator.ValidationResult result =
        DvAiProposalValidator.validate(model, List.of(proposal), null, null).get(0);

    assertEquals(DvAiProposalValidator.Status.BLOCKED, result.getStatus());
  }
}
