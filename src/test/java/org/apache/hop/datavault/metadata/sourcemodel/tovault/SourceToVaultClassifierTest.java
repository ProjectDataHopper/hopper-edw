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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceToVaultClassifierTest {

  @Test
  void samePkClusterBecomesOneHubAndSatellites() {
    SourceModel model = new SourceModel();
    model
        .getTables()
        .add(table("customer_hub", pk("customer_id"), col("load_date"), col("record_source")));
    model
        .getTables()
        .add(table("customer_demo", pk("customer_id"), col("segment"), col("load_date")));
    model
        .getTables()
        .add(table("customer_address", pk("customer_id"), col("city"), col("load_date")));
    model.getRelationships().add(rel("customer_hub", "customer_demo", "customer_id"));
    model.getRelationships().add(rel("customer_hub", "customer_address", "customer_id"));

    SourceToVaultClassification result = SourceToVaultClassifier.classify(model);

    SourceToVaultProposal hub = result.findProposal("customer_hub");
    assertNotNull(hub);
    assertEquals(SourceTableRole.HUB, hub.getRole());
    assertEquals("hub_customer", hub.firstOfKind(ProposedObjectKind.HUB).getName());
    assertEquals(
        List.of("customer_id"), hub.firstOfKind(ProposedObjectKind.HUB).getBusinessKeyColumns());

    SourceToVaultProposal demo = result.findProposal("customer_demo");
    assertEquals(SourceTableRole.SATELLITE, demo.getRole());
    ProposedVaultObject sat = demo.firstOfKind(ProposedObjectKind.SATELLITE);
    assertEquals("sat_customer_demo", sat.getName());
    assertEquals("hub_customer", sat.getParentHubName());
    assertEquals(List.of("segment"), sat.getSatelliteAttributeColumns());

    assertEquals(SourceTableRole.SATELLITE, result.findProposal("customer_address").getRole());
  }

  @Test
  void junctionTableBecomesLinkAndLinkSatellite() {
    SourceModel model = warehouseProductModel();

    SourceToVaultClassification result = SourceToVaultClassifier.classify(model);
    SourceToVaultProposal wp = result.findProposal("warehouse_product");
    assertEquals(SourceTableRole.LINK, wp.getRole());
    ProposedVaultObject link = wp.firstOfKind(ProposedObjectKind.LINK);
    assertEquals("lnk_warehouse_product", link.getName());
    assertTrue(link.getParticipatingHubNames().contains("hub_warehouse"));
    assertTrue(link.getParticipatingHubNames().contains("hub_product"));
    assertTrue(link.getDependentChildKeyColumns().isEmpty());

    ProposedVaultObject sat = wp.firstOfKind(ProposedObjectKind.SATELLITE);
    assertNotNull(sat);
    assertEquals("sat_lnk_warehouse_product", sat.getName());
    assertEquals("lnk_warehouse_product", sat.getParentLinkName());
    assertTrue(sat.getSatelliteAttributeColumns().contains("stock_qty"));
    assertFalse(sat.getSatelliteAttributeColumns().contains("warehouse_id"));
  }

  @Test
  void transactionTableKeepsDependentChildKey() {
    SourceModel model = orderLineModel(false);

    SourceToVaultProposal line = SourceToVaultClassifier.classify(model).findProposal("order_line");
    assertEquals(SourceTableRole.LINK, line.getRole());
    ProposedVaultObject link = line.firstOfKind(ProposedObjectKind.LINK);
    assertEquals(List.of("line_number"), link.getDependentChildKeyColumns());
    assertTrue(link.getParticipatingHubNames().contains("hub_order"));
    assertTrue(link.getParticipatingHubNames().contains("hub_product"));
    ProposedVaultObject sat = line.firstOfKind(ProposedObjectKind.SATELLITE);
    assertTrue(sat.getSatelliteAttributeColumns().contains("quantity"));
    assertFalse(sat.getSatelliteAttributeColumns().contains("line_number"));
  }

  @Test
  void invertedRelationshipStillClassifiesOrderLineAsLink() {
    SourceModel model = orderLineModel(true);

    SourceToVaultProposal line = SourceToVaultClassifier.classify(model).findProposal("order_line");
    assertEquals(SourceTableRole.LINK, line.getRole());
    ProposedVaultObject link = line.firstOfKind(ProposedObjectKind.LINK);
    assertEquals(2, link.getParticipatingHubNames().size());
    assertTrue(line.getEvidence().toLowerCase().contains("direction"));
  }

  @Test
  void independentHubWithLeftoverFkCreatesBinaryLink() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id")));
    model
        .getTables()
        .add(
            table(
                "order_header",
                pk("order_id"),
                col("customer_id"),
                col("order_date"),
                col("load_date")));
    model.getRelationships().add(rel("customer_hub", "order_header", "customer_id"));

    SourceToVaultClassification result = SourceToVaultClassifier.classify(model);
    SourceToVaultProposal order = result.findProposal("order_header");
    assertEquals(SourceTableRole.HUB, order.getRole());
    assertEquals("hub_order", order.firstOfKind(ProposedObjectKind.HUB).getName());
    ProposedVaultObject sat = order.firstOfKind(ProposedObjectKind.SATELLITE);
    assertEquals(List.of("order_date"), sat.getSatelliteAttributeColumns());
    ProposedVaultObject link = order.firstOfKind(ProposedObjectKind.LINK);
    assertNotNull(link);
    assertEquals("lnk_order", link.getName());
    assertTrue(link.getParticipatingHubNames().contains("hub_order"));
    assertTrue(link.getParticipatingHubNames().contains("hub_customer"));
  }

  @Test
  void isolatedTableWithoutRelationshipsIsSkipped() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("order_shipment_event", pk("message_id"), col("payload")));

    SourceToVaultProposal event =
        SourceToVaultClassifier.classify(model).findProposal("order_shipment_event");
    assertEquals(SourceTableRole.SKIP, event.getRole());
    assertFalse(event.isIncluded());
  }

  @Test
  void tableWithoutPrimaryKeyIsSkipped() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("notes", col("text")));
    model.getTables().add(table("customer_hub", pk("customer_id")));
    model.getRelationships().add(rel("notes", "customer_hub", "customer_id"));

    SourceToVaultProposal notes = SourceToVaultClassifier.classify(model).findProposal("notes");
    assertEquals(SourceTableRole.SKIP, notes.getRole());
    assertTrue(notes.getSkipReason().toLowerCase().contains("primary key"));
  }

  @Test
  void selectingLinkOnlyImpliesParentHubs() {
    SourceModel model = warehouseProductModel();
    SourceToVaultClassification result =
        SourceToVaultClassifier.classify(model, List.of("warehouse_product"));

    SourceToVaultProposal link = result.findProposal("warehouse_product");
    assertEquals(SourceTableRole.LINK, link.getRole());

    SourceToVaultProposal product = result.findProposal("product");
    assertNotNull(product);
    assertTrue(product.isImplied());
    assertEquals(SourceTableRole.HUB, product.getRole());
    assertEquals("hub_product", product.firstOfKind(ProposedObjectKind.HUB).getName());

    SourceToVaultProposal warehouse = result.findProposal("warehouse");
    assertNotNull(warehouse);
    assertTrue(warehouse.isImplied());
  }

  @Test
  void existingHubIsReusedInsteadOfDuplicated() {
    SourceModel model = warehouseProductModel();
    DataVaultModel vault = new DataVaultModel();
    DvHub existing = new DvHub("hub_product");
    BusinessKey key = new BusinessKey("product_id");
    key.setSourceFieldName("product_id");
    existing.getBusinessKeys().add(key);
    vault.getTables().add(existing);

    SourceToVaultClassification result = SourceToVaultClassifier.classify(model, null, vault, null);
    ProposedVaultObject hub = result.findProposal("product").firstOfKind(ProposedObjectKind.HUB);
    assertTrue(hub.isReuseExisting());
    assertEquals("hub_product", hub.getName());
  }

  @Test
  void fkColumnsCanStayOnSatelliteWhenOptionDisabled() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id")));
    model
        .getTables()
        .add(table("order_header", pk("order_id"), col("customer_id"), col("order_date")));
    model.getRelationships().add(rel("order_header", "customer_hub", "customer_id"));

    SourceToVaultOptions options = SourceToVaultOptions.defaults();
    options.setExcludeFkColumnsFromSatellites(false);
    SourceToVaultProposal order =
        SourceToVaultClassifier.classify(model, null, null, options).findProposal("order_header");
    assertTrue(
        order
            .firstOfKind(ProposedObjectKind.SATELLITE)
            .getSatelliteAttributeColumns()
            .contains("customer_id"));
  }

  private static SourceModel warehouseProductModel() {
    SourceModel model = new SourceModel();
    model
        .getTables()
        .add(table("product", pk("product_id"), col("product_name"), col("load_date")));
    model
        .getTables()
        .add(table("warehouse", pk("warehouse_id"), col("warehouse_name"), col("load_date")));
    model
        .getTables()
        .add(
            table(
                "warehouse_product",
                pk("warehouse_id", 1),
                pk("product_id", 2),
                col("stock_qty"),
                col("reorder_point"),
                col("load_date")));
    model.getRelationships().add(rel("warehouse_product", "product", "product_id"));
    model.getRelationships().add(rel("warehouse_product", "warehouse", "warehouse_id"));
    return model;
  }

  private static SourceModel orderLineModel(boolean invertProductEdge) {
    SourceModel model = new SourceModel();
    model.getTables().add(table("product", pk("product_id"), col("product_name")));
    model.getTables().add(table("order_header", pk("order_id"), col("order_date")));
    model
        .getTables()
        .add(
            table(
                "order_line",
                pk("order_id", 1),
                pk("product_id", 2),
                pk("line_number", 3),
                col("quantity"),
                col("unit_price"),
                col("load_date")));
    if (invertProductEdge) {
      model.getRelationships().add(rel("product", "order_line", "product_id"));
    } else {
      model.getRelationships().add(rel("order_line", "product", "product_id"));
    }
    model.getRelationships().add(rel("order_line", "order_header", "order_id"));
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
