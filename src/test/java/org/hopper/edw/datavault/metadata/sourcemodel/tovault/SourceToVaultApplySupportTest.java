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

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceToVaultApplySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void applyCreatesWiredHubsLinksAndSatellites() throws Exception {
    SourceModel source = retailLikeModel();
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultClassification classification = SourceToVaultClassifier.classify(source);

    SourceToVaultApplyResult result =
        SourceToVaultApplySupport.apply(
            source, vault, classification, false, new Variables(), new MemoryMetadataProvider());

    assertTrue(result.getCreatedTableNames().contains("hub_customer"));
    assertTrue(result.getCreatedTableNames().contains("sat_customer_demo"));
    assertTrue(result.getCreatedTableNames().contains("hub_order"));
    assertTrue(result.getCreatedTableNames().contains("lnk_order"));
    assertTrue(result.getCreatedTableNames().contains("lnk_order_line"));
    assertTrue(result.getCreatedTableNames().contains("sat_lnk_order_line"));

    DvHub customer = vault.findHub("hub_customer");
    assertNotNull(customer);
    assertEquals("customer_hk", customer.getHashKeyFieldName());
    assertEquals(List.of("customer_hub"), customer.getRecordSources());
    assertEquals("customer_id", customer.getBusinessKeys().get(0).getName());
    assertEquals("customer_id", customer.getBusinessKeys().get(0).getSourceFieldName());

    DvSatellite demo = (DvSatellite) vault.findTable("sat_customer_demo");
    assertEquals("hub_customer", demo.getHubName());
    assertEquals("customer_demo", demo.getRecordSource());
    assertEquals("segment", demo.getAttributes().get(0).getName());

    DvLink orderLine = vault.findLink("lnk_order_line");
    assertNotNull(orderLine);
    assertTrue(orderLine.getHubNames().contains("hub_order"));
    assertTrue(orderLine.getHubNames().contains("hub_product"));
    assertEquals(1, orderLine.getDependentChildKeys().size());
    assertEquals("line_number", orderLine.getDependentChildKeys().get(0).getName());
    assertEquals(List.of("sat_lnk_order_line"), orderLine.getLinkSatelliteNames());
    assertFalse(orderLine.getLinkHubSources().isEmpty());

    DvSatellite lineSat = (DvSatellite) vault.findTable("sat_lnk_order_line");
    assertEquals("lnk_order_line", lineSat.getLinkName());
    assertTrue(lineSat.getAttributes().stream().anyMatch(a -> "quantity".equals(a.getName())));
  }

  @Test
  void seedParentHubsAddsOrderFeedToCustomerHub() throws Exception {
    SourceModel source = retailLikeModel();
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultClassification classification = SourceToVaultClassifier.classify(source);
    SourceToVaultOptions options = SourceToVaultOptions.defaults();
    options.setSeedParentHubsFromChildFeeds(true);

    SourceToVaultApplySupport.apply(
        source,
        vault,
        classification,
        false,
        new Variables(),
        new MemoryMetadataProvider(),
        options);

    DvHub customer = vault.findHub("hub_customer");
    assertNotNull(customer);
    assertTrue(
        customer.getRecordSources().stream().anyMatch(s -> s != null && s.contains("order")),
        "customer hub should also be loaded from an order/link feed");
  }

  @Test
  void secondApplyDoesNotDuplicateExistingHub() throws Exception {
    SourceModel source = retailLikeModel();
    DataVaultModel vault = new DataVaultModel();
    DvHub existing = new DvHub("hub_customer");
    BusinessKey key = new BusinessKey("customer_id");
    key.setSourceFieldName("customer_id");
    existing.getBusinessKeys().add(key);
    existing.getRecordSources().add("customer_hub");
    vault.getTables().add(existing);

    SourceToVaultClassification classification =
        SourceToVaultClassifier.classify(source, null, vault, null);
    SourceToVaultApplyResult result =
        SourceToVaultApplySupport.apply(
            source, vault, classification, false, new Variables(), new MemoryMetadataProvider());

    long customerHubs =
        vault.getTables().stream()
            .filter(t -> "hub_customer".equalsIgnoreCase(t.getName()))
            .count();
    assertEquals(1, customerHubs);
    assertTrue(result.getReusedTableNames().contains("hub_customer"));
    assertNotNull(vault.findTable("sat_customer_demo"));
  }

  private static SourceModel retailLikeModel() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id"), col("load_date")));
    model
        .getTables()
        .add(table("customer_demo", pk("customer_id"), col("segment"), col("load_date")));
    model
        .getTables()
        .add(table("product", pk("product_id"), col("product_name"), col("load_date")));
    model
        .getTables()
        .add(
            table(
                "order_header",
                pk("order_id"),
                col("customer_id"),
                col("order_date"),
                col("load_date")));
    model
        .getTables()
        .add(
            table(
                "order_line",
                pk("order_id", 1),
                pk("product_id", 2),
                pk("line_number", 3),
                col("quantity"),
                col("load_date")));
    model.getRelationships().add(rel("customer_hub", "customer_demo", "customer_id"));
    model.getRelationships().add(rel("order_header", "customer_hub", "customer_id"));
    model.getRelationships().add(rel("order_line", "order_header", "order_id"));
    model.getRelationships().add(rel("order_line", "product", "product_id"));
    return model;
  }

  private static SourceTable table(String name, SourceColumn... columns) {
    SourceTable table = new SourceTable(name);
    table.setCatalogSourceName(name);
    for (SourceColumn column : columns) {
      table.getColumns().add(column);
    }
    return table;
  }

  private static SourceColumn pk(String name) {
    return pk(name, 1);
  }

  private static SourceColumn pk(String name, int position) {
    SourceColumn column = new SourceColumn(name);
    column.setPrimaryKeyPosition(position);
    column.setHopType(2);
    return column;
  }

  private static SourceColumn col(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(2);
    return column;
  }

  private static SourceRelationship rel(String child, String parent, String column) {
    SourceRelationship relationship = new SourceRelationship("fk_" + child + "_" + parent);
    relationship.setChildEndpointKind(SourceEndpointKind.TABLE);
    relationship.setParentEndpointKind(SourceEndpointKind.TABLE);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add(column);
    relationship.getParentColumns().add(column);
    return relationship;
  }
}
