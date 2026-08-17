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
package org.apache.hop.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.ModelConfigurationTestSupport;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DvModelLineageCollectorTest {

  private Variables variables;
  private DataVaultModel model;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    model = loadModel("retail-example/models/retail-360.hdv");
  }

  @Test
  void collectRetail360ProducesTableAndFieldLineage() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);

    assertEquals(LineageLayer.DV, snapshot.getModelLayer());
    assertFalse(snapshot.getTables().isEmpty(), "expected tables from retail-360");
    assertTrue(
        snapshot.getTables().stream().allMatch(t -> !t.getReasons().isEmpty()),
        "every table must have at least one reason");
  }

  @Test
  void hubCustomerHasMultiSourceBusinessKeyAndStandardColumns() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    TableLineage hub =
        snapshot
            .findTableByLogicalName("hub_customer")
            .orElseThrow(() -> new AssertionError("hub_customer missing"));

    assertEquals("hub_customer", hub.getPhysicalTableName());
    assertEquals("HUB", hub.getTableType());
    assertTrue(hub.getSources().size() >= 5, "multi-source hub feeds");

    FieldLineage customerId =
        hub.findField("customer_id").orElseThrow(() -> new AssertionError("customer_id missing"));
    assertFalse(customerId.isTechnical());
    assertTrue(
        customerId.getContributions().size() >= 5,
        "one contribution per record source for customer_id");
    assertTrue(
        customerId.getContributions().stream()
            .anyMatch(
                c ->
                    "E2E-customer-demo".equals(c.getSourceName())
                        && "customer_id".equals(c.getSourceFieldName())));
    assertTrue(
        customerId.getContributions().stream()
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(r -> r.getCode() == LineageReasonCode.USER_EXPLICIT_MAPPING));
    assertTrue(
        customerId.getContributions().stream()
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(r -> r.getCode() == LineageReasonCode.MULTI_SOURCE_HUB));

    FieldLineage hash =
        hub.findField("customer_hk").orElseThrow(() -> new AssertionError("customer_hk missing"));
    assertTrue(hash.isTechnical());
    assertTrue(
        hash.getContributions().stream()
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(r -> r.getCode() == LineageReasonCode.HASH_FROM_BUSINESS_KEYS));

    FieldLineage loadTs =
        hub.findField("x_load_ts").orElseThrow(() -> new AssertionError("x_load_ts missing"));
    assertTrue(loadTs.isTechnical());
    assertTrue(
        loadTs.getContributions().stream()
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(r -> r.getCode() == LineageReasonCode.STANDARD_COLUMN));

    FieldLineage recordSource =
        hub.findField("x_record_source")
            .orElseThrow(() -> new AssertionError("x_record_source missing"));
    assertTrue(recordSource.isTechnical());
  }

  @Test
  void satCustomerDemoMapsAttributesFromFeed() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    TableLineage sat =
        snapshot
            .findTableByLogicalName("sat_customer_demo")
            .orElseThrow(() -> new AssertionError("sat_customer_demo missing"));

    assertEquals("sat_customer_demo", sat.getPhysicalTableName());
    assertTrue(
        sat.getSources().stream()
            .anyMatch(
                s ->
                    "E2E-customer-demo".equals(s.getName())
                        && s.getRole() == TableSourceRole.RECORD_SOURCE));
    assertTrue(
        sat.getSources().stream()
            .anyMatch(
                s ->
                    "hub_customer".equals(s.getName())
                        && s.getRole() == TableSourceRole.PARENT_HUB));

    FieldLineage segment =
        sat.findField("segment").orElseThrow(() -> new AssertionError("segment missing"));
    assertEquals(1, segment.getContributions().size());
    FieldContribution contribution = segment.getContributions().get(0);
    assertEquals("E2E-customer-demo", contribution.getSourceName());
    assertEquals("segment", contribution.getSourceFieldName());
    assertEquals(FieldTransform.IDENTITY, contribution.getTransform());
    assertTrue(
        contribution.getReasons().stream()
            .anyMatch(r -> r.getCode() == LineageReasonCode.DEFAULT_SAME_AS_SOURCE));

    assertTrue(sat.findField("customer_hk").isPresent());
    assertTrue(sat.findField("x_load_ts").isPresent());
  }

  @Test
  void lnkOrderLineHasHubKeyMappingsAndLinkHash() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    TableLineage link =
        snapshot
            .findTableByLogicalName("lnk_order_line")
            .orElseThrow(() -> new AssertionError("lnk_order_line missing"));

    assertEquals("LINK", link.getTableType());
    assertTrue(link.getSources().stream().anyMatch(s -> "E2E-order-line".equals(s.getName())));
    assertTrue(link.getSources().stream().anyMatch(s -> "hub_order".equals(s.getName())));
    assertTrue(link.getSources().stream().anyMatch(s -> "hub_product".equals(s.getName())));

    FieldLineage orderId =
        link.findField("order_id")
            .orElseThrow(() -> new AssertionError("order_id mapping missing"));
    assertTrue(
        orderId.getContributions().stream()
            .anyMatch(
                c ->
                    "E2E-order-line".equals(c.getSourceName())
                        && "order_id".equals(c.getSourceFieldName())
                        && c.getReasons().stream()
                            .anyMatch(r -> r.getCode() == LineageReasonCode.LINK_HUB_KEY_MAPPING)));

    FieldLineage linkHash =
        link.findField("lnk_order_line_hk")
            .orElseThrow(() -> new AssertionError("lnk_order_line_hk missing"));
    assertTrue(linkHash.isTechnical());
    assertTrue(
        linkHash.getContributions().stream()
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(r -> r.getCode() == LineageReasonCode.HASH_FROM_BUSINESS_KEYS));
  }

  @Test
  void satLnkOrderLineUsesLinkSatelliteAttributeMappings() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    TableLineage sat =
        snapshot
            .findTableByLogicalName("sat_lnk_order_line")
            .orElseThrow(() -> new AssertionError("sat_lnk_order_line missing"));

    FieldLineage quantity =
        sat.findField("quantity").orElseThrow(() -> new AssertionError("quantity missing"));
    assertEquals("E2E-order-line", quantity.getContributions().get(0).getSourceName());
    assertEquals("quantity", quantity.getContributions().get(0).getSourceFieldName());

    assertTrue(sat.getSources().stream().anyMatch(s -> s.getRole() == TableSourceRole.PARENT_LINK));
  }

  @Test
  void everyFieldHasAtLeastOneReason() {
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
    for (TableLineage table : snapshot.getTables()) {
      for (FieldLineage field : table.getFields()) {
        assertFalse(
            field.getContributions().isEmpty(),
            () ->
                table.getLogicalName()
                    + "."
                    + field.getTargetFieldName()
                    + " has no contributions");
        for (FieldContribution contribution : field.getContributions()) {
          assertFalse(
              contribution.getReasons().isEmpty(),
              () ->
                  table.getLogicalName()
                      + "."
                      + field.getTargetFieldName()
                      + " contribution missing reasons");
        }
      }
    }
  }

  @Test
  void programmaticHubCollectsExplicitMapping() {
    DataVaultModel small = new DataVaultModel();
    small.setName("unit-hub");
    org.apache.hop.datavault.metadata.DvHub hub =
        new org.apache.hop.datavault.metadata.DvHub("hub_x");
    hub.setTableName("hub_x");
    hub.setHashKeyFieldName("x_hk");
    hub.getRecordSources().add("SRC-a");
    org.apache.hop.datavault.metadata.BusinessKey bk =
        new org.apache.hop.datavault.metadata.BusinessKey("x_id");
    bk.setSourceFieldName("src_x_id");
    bk.setRecordSourceName("SRC-a");
    bk.setDataType("Integer");
    hub.getBusinessKeys().add(bk);
    small.getTables().add(hub);
    small.getConfigurationOrDefault().setLoadDateField("LOAD_DATE");
    small.getConfigurationOrDefault().setRecordSourceField("RECORD_SOURCE");

    LineageSnapshot snapshot = DvModelLineageCollector.collect(small, variables);
    TableLineage table = snapshot.findTableByLogicalName("hub_x").orElseThrow();
    FieldLineage field = table.findField("x_id").orElseThrow();
    assertEquals(1, field.getContributions().size());
    assertEquals("src_x_id", field.getContributions().get(0).getSourceFieldName());
    assertEquals(FieldTransform.RENAME, field.getContributions().get(0).getTransform());
    assertEquals(
        LineageReasonCode.USER_EXPLICIT_MAPPING,
        field.getContributions().get(0).getReasons().get(0).getCode());
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    ModelConfigurationTestSupport.attachRetailExample(model, null);
    return model;
  }
}
