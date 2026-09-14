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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvBridgeTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultsToBridgeType() {
    BvBridge table = new BvBridge();
    assertEquals(BvTableType.BRIDGE, table.getTableType());
  }

  @Test
  void xmlRoundTripPreservesWeightAndSql() throws Exception {
    BvBridge original = new BvBridge();
    original.setName("customer_account_bridge");
    original.setTableName("br_customer_account");
    original.setWeightField("allocation_weight");
    original.setSqlQuery("SELECT customer_hk, account_hk FROM lnk_customer_account");
    original.getDerivatives().add(new BvDerivativeRef("lnk_customer_account", DvTableType.LINK));

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");

    BvBridge restored = new BvBridge();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvBridge.class, restored, null);

    assertEquals("customer_account_bridge", restored.getName());
    assertEquals("br_customer_account", restored.getTableName());
    assertEquals("allocation_weight", restored.getWeightField());
    assertEquals(
        "SELECT customer_hk, account_hk FROM lnk_customer_account", restored.getSqlQuery());
    assertEquals(1, restored.getDerivatives().size());
    assertEquals("lnk_customer_account", restored.getDerivatives().get(0).getDvTableName());
    assertEquals(DvTableType.LINK, restored.getDerivatives().get(0).getDvTableType());
  }

  @Test
  void layoutUsesLinkHubHashKeysAndWeight() throws Exception {
    DataVaultModel dv = customerAccountVault();
    BvBridge bridge = new BvBridge();
    bridge.setName("customer_account_bridge");
    bridge.setTableName("br_customer_account");
    bridge.setWeightField("allocation_weight");
    bridge.getDerivatives().add(new BvDerivativeRef("lnk_customer_account", DvTableType.LINK));

    IRowMeta layout =
        BvBridgeLayoutSupport.buildTargetTableLayout(
            bridge, new BusinessVaultModel(), dv, new Variables(), null);

    assertEquals(3, layout.size());
    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("account_hk", layout.getValueMeta(1).getName());
    assertEquals("allocation_weight", layout.getValueMeta(2).getName());
  }

  @Test
  void generatedSqlSelectsHashKeysFromLink() throws Exception {
    DataVaultModel dv = customerAccountVault();
    BvBridge bridge = new BvBridge();
    bridge.setName("customer_account_bridge");
    bridge.getDerivatives().add(new BvDerivativeRef("lnk_customer_account", DvTableType.LINK));

    String sql = BvBridgePipelineSupport.buildSourceSql(bridge, dv, new Variables(), null, null);
    assertEquals("SELECT customer_hk, account_hk FROM lnk_customer_account", sql);
  }

  @Test
  void checkRequiresTwoHashKeysAndLinkOrSql() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    BvBridge bridge = new BvBridge();
    bridge.setName("orphan_bridge");
    bridge.setTableName("orphan_bridge");
    model.getTables().add(bridge);

    List<ICheckResult> remarks = new ArrayList<>();
    bridge.check(remarks, null, new Variables(), model, new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().toLowerCase().contains("two hub hash")),
        () -> remarks.toString());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().toLowerCase().contains("link")),
        () -> remarks.toString());
  }

  @Test
  void checkPassesWhenLinkProvidesTwoHubs() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DataVaultModel dv = customerAccountVault();
    BvBridge bridge = new BvBridge();
    bridge.setName("customer_account_bridge");
    bridge.setTableName("br_customer_account");
    bridge.getDerivatives().add(new BvDerivativeRef("lnk_customer_account", DvTableType.LINK));
    model.getTables().add(bridge);

    List<ICheckResult> remarks = new ArrayList<>();
    bridge.check(remarks, null, new Variables(), model, dv);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.toString());
  }

  private static DataVaultModel customerAccountVault() {
    DataVaultModel dv = new DataVaultModel();
    DvHub customer = new DvHub("hub_customer");
    customer.setTableName("hub_customer");
    customer.setHashKeyFieldName("customer_hk");
    DvHub account = new DvHub("hub_account");
    account.setTableName("hub_account");
    account.setHashKeyFieldName("account_hk");
    DvLink link = new DvLink("lnk_customer_account");
    link.setTableName("lnk_customer_account");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_account");
    dv.getTables().add(customer);
    dv.getTables().add(account);
    dv.getTables().add(link);
    return dv;
  }
}
