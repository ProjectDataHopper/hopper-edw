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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.Test;

class SourceToVaultFeedAndHeuristicTest {

  @Test
  void querySharingHubGrainBecomesSatellite() {
    SourceModel model = customerModel();
    SourceQuery query = new SourceQuery("all-customer-info");
    query.setPublishedCatalogName("feed_customer_enriched");
    query.getColumns().add(queryCol("customer_hub", "customer_id", 1));
    query.getColumns().add(queryCol("customer_demo", "segment", 0));
    query.getColumns().add(queryCol("customer_address", "city", 0));
    model.getQueries().add(query);

    SourceToVaultProposal proposal =
        SourceToVaultClassifier.classify(model).findProposal("all-customer-info");
    assertEquals(SourceTableRole.SATELLITE, proposal.getRole());
    ProposedVaultObject sat = proposal.firstOfKind(ProposedObjectKind.SATELLITE);
    assertEquals("hub_customer", sat.getParentHubName());
    assertEquals(SourceEndpointKind.QUERY, sat.getSourceKind());
    assertTrue(sat.getSatelliteAttributeColumns().contains("segment"));
  }

  @Test
  void jsonFeedWithOwnKeyAndParentFkBecomesHubSatAndLink() {
    SourceModel model = orderModel();
    SourceJson json = new SourceJson("order_shipment_tracking");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("order_header");
    json.getFields().add(jsonField("message_id", 1));
    json.getFields().add(jsonField("order_id", 0));
    json.getFields().add(jsonField("status", 0));
    model.getJsonSources().add(json);
    model
        .getRelationships()
        .add(
            rel(
                "order_shipment_tracking",
                "order_header",
                "order_id",
                SourceEndpointKind.JSON,
                SourceEndpointKind.TABLE));

    SourceToVaultProposal proposal =
        SourceToVaultClassifier.classify(model).findProposal("order_shipment_tracking");
    assertEquals(SourceTableRole.HUB, proposal.getRole());
    assertNotNull(proposal.firstOfKind(ProposedObjectKind.HUB));
    assertNotNull(proposal.firstOfKind(ProposedObjectKind.SATELLITE));
    assertNotNull(proposal.firstOfKind(ProposedObjectKind.LINK));
    assertEquals(
        SourceEndpointKind.JSON, proposal.firstOfKind(ProposedObjectKind.HUB).getSourceKind());
  }

  @Test
  void pipelineWithManyFksBecomesHubAndNaryLink() {
    SourceModel model = orderModel();
    model.getTables().add(table("product", pk("product_id"), col("product_name")));
    model.getTables().add(table("warehouse", pk("warehouse_id"), col("warehouse_name")));
    model.getRelationships().add(rel("order_line", "product", "product_id"));

    SourcePipeline pipeline = new SourcePipeline("asn-package-lines");
    pipeline.setCatalogSourceName("asn-package-lines");
    pipeline.getFields().add(col("package_id"));
    pipeline.getFields().add(col("line_number"));
    pipeline.getFields().add(col("order_id"));
    pipeline.getFields().add(col("product_id"));
    pipeline.getFields().add(col("warehouse_id"));
    pipeline.getFields().add(col("quantity"));
    model.getPipelineSources().add(pipeline);
    model
        .getRelationships()
        .add(
            rel(
                "asn-package-lines",
                "order_header",
                "order_id",
                SourceEndpointKind.PIPELINE,
                SourceEndpointKind.TABLE));
    model
        .getRelationships()
        .add(
            rel(
                "asn-package-lines",
                "product",
                "product_id",
                SourceEndpointKind.PIPELINE,
                SourceEndpointKind.TABLE));
    model
        .getRelationships()
        .add(
            rel(
                "asn-package-lines",
                "warehouse",
                "warehouse_id",
                SourceEndpointKind.PIPELINE,
                SourceEndpointKind.TABLE));

    SourceToVaultProposal proposal =
        SourceToVaultClassifier.classify(model).findProposal("asn-package-lines");
    assertEquals(SourceTableRole.HUB, proposal.getRole());
    assertEquals("hub_asn_package_lines", proposal.firstOfKind(ProposedObjectKind.HUB).getName());
    ProposedVaultObject link = proposal.firstOfKind(ProposedObjectKind.LINK);
    assertNotNull(link);
    assertEquals("lnk_asn_package_lines", link.getName());
    assertTrue(link.getParticipatingHubNames().size() >= 3);
    assertTrue(link.getParticipatingHubNames().contains("hub_asn_package_lines"));
    assertTrue(link.getParticipatingHubNames().contains("hub_order"));
    assertTrue(link.getParticipatingHubNames().contains("hub_product"));
    assertTrue(link.getParticipatingHubNames().contains("hub_warehouse"));
  }

  @Test
  void lookupTableBecomesReference() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("country", pk("country_code"), col("country_name")));
    model.getTables().add(table("customer_hub", pk("customer_id"), col("country_code")));
    model.getRelationships().add(rel("customer_hub", "country", "country_code"));

    SourceToVaultClassification result = SourceToVaultClassifier.classify(model);
    assertEquals(SourceTableRole.REFERENCE, result.findProposal("country").getRole());
    assertEquals(
        "ref_country",
        result.findProposal("country").firstOfKind(ProposedObjectKind.REFERENCE).getName());
    SourceToVaultProposal customer = result.findProposal("customer_hub");
    assertEquals(SourceTableRole.HUB, customer.getRole());
    assertTrue(
        customer.getObjects().stream().noneMatch(o -> o.getKind() == ProposedObjectKind.LINK));
  }

  @Test
  void selfForeignKeyCreatesHierarchyAliasAndLink() {
    SourceModel model = new SourceModel();
    model
        .getTables()
        .add(table("employee", pk("employee_id"), col("manager_id"), col("full_name")));
    model.getRelationships().add(rel("employee", "employee", "manager_id"));

    SourceToVaultProposal proposal =
        SourceToVaultClassifier.classify(model).findProposal("employee");
    assertEquals(SourceTableRole.HUB, proposal.getRole());
    assertNotNull(proposal.firstOfKind(ProposedObjectKind.LINKED_TABLE));
    assertEquals(
        "hub_employee_parent", proposal.firstOfKind(ProposedObjectKind.LINKED_TABLE).getName());
    ProposedVaultObject link = proposal.firstOfKind(ProposedObjectKind.LINK);
    assertEquals("lnk_employee_hierarchy", link.getName());
    assertTrue(link.getParticipatingHubNames().contains("hub_employee"));
    assertTrue(link.getParticipatingHubNames().contains("hub_employee_parent"));
    ProposedVaultObject sat = proposal.firstOfKind(ProposedObjectKind.SATELLITE);
    assertNotNull(sat);
    assertTrue(sat.getSatelliteAttributeColumns().contains("full_name"));
    assertTrue(
        sat.getSatelliteAttributeColumns().stream()
            .noneMatch(name -> "manager_id".equalsIgnoreCase(name)));
  }

  @Test
  void applyCreatesReferenceAndLinkedTable() throws Exception {
    SourceModel model = new SourceModel();
    model
        .getTables()
        .add(table("employee", pk("employee_id"), col("manager_id"), col("full_name")));
    model.getRelationships().add(rel("employee", "employee", "manager_id"));
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultApplySupport.apply(
        model,
        vault,
        SourceToVaultClassifier.classify(model),
        false,
        new Variables(),
        new MemoryMetadataProvider());
    assertNotNull(vault.findHub("hub_employee"));
    assertTrue(vault.findTable("hub_employee_parent") instanceof DvLinkedTable);
    assertNotNull(vault.findLink("lnk_employee_hierarchy"));
  }

  @Test
  void applyCreatesReferenceTable() throws Exception {
    SourceModel model = new SourceModel();
    model.getTables().add(table("country", pk("country_code"), col("country_name")));
    model.getTables().add(table("customer_hub", pk("customer_id"), col("country_code")));
    model.getRelationships().add(rel("customer_hub", "country", "country_code"));
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultApplySupport.apply(
        model,
        vault,
        SourceToVaultClassifier.classify(model),
        false,
        new Variables(),
        new MemoryMetadataProvider());
    assertTrue(vault.findTable("ref_country") instanceof DvReferenceTable);
  }

  @Test
  void includeNonTableSourcesCanBeDisabled() {
    SourceModel model = customerModel();
    SourceQuery query = new SourceQuery("all-customer-info");
    query.getColumns().add(queryCol("customer_hub", "customer_id", 1));
    query.getColumns().add(queryCol("customer_demo", "segment", 0));
    model.getQueries().add(query);
    SourceToVaultOptions options = SourceToVaultOptions.defaults();
    options.setIncludeNonTableSources(false);
    assertEquals(
        null,
        SourceToVaultClassifier.classify(model, null, null, options)
            .findProposal("all-customer-info"));
  }

  @Test
  void tableWithTwoLeftoverFksKeepsBinaryLinks() {
    SourceModel model = orderModel();
    model.getTables().add(table("product", pk("product_id"), col("product_name")));
    model.getRelationships().add(rel("order_header", "product", "product_id"));
    SourceToVaultProposal order =
        SourceToVaultClassifier.classify(model).findProposal("order_header");
    long links =
        order.getObjects().stream().filter(o -> o.getKind() == ProposedObjectKind.LINK).count();
    assertEquals(2, links);
  }

  private static SourceModel customerModel() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id")));
    model.getTables().add(table("customer_demo", pk("customer_id"), col("segment")));
    model.getTables().add(table("customer_address", pk("customer_id"), col("city")));
    model.getRelationships().add(rel("customer_hub", "customer_demo", "customer_id"));
    model.getRelationships().add(rel("customer_hub", "customer_address", "customer_id"));
    return model;
  }

  private static SourceModel orderModel() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id")));
    model
        .getTables()
        .add(table("order_header", pk("order_id"), col("customer_id"), col("order_date")));
    model.getRelationships().add(rel("order_header", "customer_hub", "customer_id"));
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
    SourceColumn column = new SourceColumn(name);
    column.setPrimaryKeyPosition(1);
    column.setHopType(2);
    return column;
  }

  private static SourceColumn col(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(2);
    return column;
  }

  private static SourceQueryColumn queryCol(String table, String column, int pk) {
    SourceQueryColumn qc = new SourceQueryColumn(table, column);
    qc.setPrimaryKeyPosition(pk);
    return qc;
  }

  private static SourceJsonField jsonField(String name, int pk) {
    SourceJsonField field = new SourceJsonField(name, "");
    field.setPrimaryKeyPosition(pk);
    field.setHopType(2);
    return field;
  }

  private static SourceRelationship rel(String child, String parent, String column) {
    return rel(child, parent, column, SourceEndpointKind.TABLE, SourceEndpointKind.TABLE);
  }

  private static SourceRelationship rel(
      String child,
      String parent,
      String column,
      SourceEndpointKind childKind,
      SourceEndpointKind parentKind) {
    SourceRelationship relationship = new SourceRelationship("fk_" + child + "_" + parent);
    relationship.setChildEndpointKind(childKind);
    relationship.setParentEndpointKind(parentKind);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add(column);
    relationship.getParentColumns().add(column);
    return relationship;
  }
}
