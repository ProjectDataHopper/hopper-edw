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
package org.hopper.edw.datavault.metadata.sourcemodel.tovault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SourceToVaultRetailClassificationTest {

  private static final Path RETAIL_HSM =
      Path.of("retail-example/models/source-tables-crm.hsm").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  static boolean retailHsmPresent() {
    return Files.isRegularFile(RETAIL_HSM);
  }

  @Test
  @EnabledIf("retailHsmPresent")
  void retailCrmTablesMatchIssue125Expectations() throws Exception {
    SourceModel model = loadRetail();
    SourceToVaultClassification result = SourceToVaultClassifier.classify(model);

    assertRole(result, "customer_hub", SourceTableRole.HUB, "hub_customer");
    assertSat(result, "customer_demo", "sat_customer_demo", "hub_customer");
    assertSat(result, "customer_contact", "sat_customer_contact", "hub_customer");
    assertSat(result, "customer_address", "sat_customer_address", "hub_customer");
    assertSat(result, "customer_prefs", "sat_customer_prefs", "hub_customer");

    SourceToVaultProposal product = result.findProposal("product");
    assertEquals(SourceTableRole.HUB, product.getRole());
    assertEquals("hub_product", product.firstOfKind(ProposedObjectKind.HUB).getName());
    assertEquals("sat_product", product.firstOfKind(ProposedObjectKind.SATELLITE).getName());

    SourceToVaultProposal warehouse = result.findProposal("warehouse");
    assertEquals(SourceTableRole.HUB, warehouse.getRole());
    assertEquals("hub_warehouse", warehouse.firstOfKind(ProposedObjectKind.HUB).getName());
    assertEquals("sat_warehouse", warehouse.firstOfKind(ProposedObjectKind.SATELLITE).getName());

    SourceToVaultProposal order = result.findProposal("order_header");
    assertEquals(SourceTableRole.HUB, order.getRole());
    assertEquals("hub_order", order.firstOfKind(ProposedObjectKind.HUB).getName());
    assertEquals("sat_order", order.firstOfKind(ProposedObjectKind.SATELLITE).getName());
    assertEquals("lnk_order", order.firstOfKind(ProposedObjectKind.LINK).getName());

    SourceToVaultProposal line = result.findProposal("order_line");
    assertEquals(SourceTableRole.LINK, line.getRole());
    ProposedVaultObject link = line.firstOfKind(ProposedObjectKind.LINK);
    assertEquals("lnk_order_line", link.getName());
    assertEquals(List.of("line_number"), link.getDependentChildKeyColumns());
    assertTrue(link.getParticipatingHubNames().contains("hub_order"));
    assertTrue(link.getParticipatingHubNames().contains("hub_product"));
    assertEquals("sat_lnk_order_line", line.firstOfKind(ProposedObjectKind.SATELLITE).getName());

    SourceToVaultProposal stock = result.findProposal("warehouse_product");
    assertEquals(SourceTableRole.LINK, stock.getRole());
    assertEquals("lnk_warehouse_product", stock.firstOfKind(ProposedObjectKind.LINK).getName());
    assertEquals(
        "sat_lnk_warehouse_product", stock.firstOfKind(ProposedObjectKind.SATELLITE).getName());

    SourceToVaultProposal event = result.findProposal("order_shipment_event");
    assertEquals(SourceTableRole.SKIP, event.getRole());
    assertFalse(event.isIncluded());

    SourceToVaultProposal tracking = result.findProposal("order_shipment_tracking");
    assertNotNull(tracking);
    assertEquals(SourceTableRole.HUB, tracking.getRole());
    assertNotNull(tracking.firstOfKind(ProposedObjectKind.LINK));

    SourceToVaultProposal asn = result.findProposal("asn-package-lines");
    assertNotNull(asn);
    assertEquals(SourceTableRole.HUB, asn.getRole());
    ProposedVaultObject nary = asn.firstOfKind(ProposedObjectKind.LINK);
    assertNotNull(nary);
    assertTrue(nary.getParticipatingHubNames().size() >= 3);
  }

  private static void assertRole(
      SourceToVaultClassification result, String source, SourceTableRole role, String hubName) {
    SourceToVaultProposal proposal = result.findProposal(source);
    assertNotNull(proposal, source);
    assertEquals(role, proposal.getRole(), source);
    if (hubName != null) {
      assertEquals(hubName, proposal.firstOfKind(ProposedObjectKind.HUB).getName(), source);
    }
  }

  private static void assertSat(
      SourceToVaultClassification result, String source, String satName, String parentHub) {
    SourceToVaultProposal proposal = result.findProposal(source);
    assertNotNull(proposal, source);
    assertEquals(SourceTableRole.SATELLITE, proposal.getRole(), source);
    ProposedVaultObject sat = proposal.firstOfKind(ProposedObjectKind.SATELLITE);
    assertEquals(satName, sat.getName(), source);
    assertEquals(parentHub, sat.getParentHubName(), source);
  }

  private static SourceModel loadRetail() throws Exception {
    String xml = Files.readString(RETAIL_HSM);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);
    SourceModel model = new SourceModel();
    XmlMetadataUtil.deSerializeFromXml(
        rootNode, SourceModel.class, model, new MemoryMetadataProvider());
    model.setFilename(RETAIL_HSM.toString());
    return model;
  }
}
