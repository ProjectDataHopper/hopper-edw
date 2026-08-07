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
package org.apache.hop.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SourceModelTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void checkReportsNoTables() {
    SourceModel model = new SourceModel();
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
  }

  @Test
  void checkReportsOkForTwoTableFkModel() {
    SourceModel model = sampleProductLookupModel();
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void xmlRoundTripPreservesTablesRelationshipsAndQuery() throws Exception {
    SourceModel original = sampleProductLookupModel();
    original.setName("crm-source");
    original.setDescription("CRM source system model");
    original.getConfigurationOrDefault().setDefaultDatabase("CRM");
    original.getConfigurationOrDefault().setDefaultSchema("public");

    String xml =
        XmlHandler.aroundTag(SourceModel.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);

    SourceModel restored = new SourceModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, restored, null);

    assertEquals(original.getName(), restored.getName());
    assertEquals(original.getDescription(), restored.getDescription());
    assertEquals(
        original.getConfigurationOrDefault().getDefaultDatabase(),
        restored.getConfigurationOrDefault().getDefaultDatabase());
    assertEquals(
        original.getConfigurationOrDefault().getDefaultSchema(),
        restored.getConfigurationOrDefault().getDefaultSchema());
    assertEquals(2, restored.getTables().size());
    assertEquals(1, restored.getRelationships().size());
    assertEquals(1, restored.getQueries().size());

    SourceTable product = restored.findTable("product");
    assertNotNull(product);
    assertEquals("product", product.getTableName());
    assertEquals(2, product.getColumns().size());
    assertEquals(1, product.primaryKeyColumns().size());
    assertEquals("product_id", product.primaryKeyColumns().get(0).getName());
    assertEquals(50, product.getLocation().x);
    assertEquals(60, product.getLocation().y);

    SourceTable productType = restored.findTable("product_type");
    assertNotNull(productType);
    assertEquals(1, productType.primaryKeyColumns().size());

    SourceRelationship rel = restored.getRelationships().get(0);
    assertEquals("product", rel.getChildTableName());
    assertEquals("product_type", rel.getParentTableName());
    assertEquals(List.of("type_id"), rel.getChildColumns());
    assertEquals(List.of("type_id"), rel.getParentColumns());
    assertEquals(SourceJoinType.LEFT, rel.resolveDefaultJoinType());

    SourceQuery query = restored.findQuery("feed_product_enriched");
    assertNotNull(query);
    assertEquals("product", query.getDrivingTableName());
    assertEquals(1, query.getJoins().size());
    assertEquals("product_type", query.getJoins().get(0).getTableName());
    assertEquals(SourceJoinType.LEFT, query.getJoins().get(0).resolveJoinType());
    assertEquals(4, query.getColumns().size());
    assertEquals("type_name", query.getColumns().get(3).resolveAlias());
    assertEquals(SourceQueryGenerationMode.AUTO, query.resolveGenerationMode());
  }

  @Test
  void checkFlagsOrphanRelationship() {
    SourceModel model = sampleProductLookupModel();
    model.getRelationships().get(0).setParentTableName("missing_parent");
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("missing_parent")));
  }

  @Test
  void xmlRoundTripPreservesJsonSource() throws Exception {
    SourceModel original = sampleJsonLandingModel();
    original.setName("json-landing");

    String xml =
        XmlHandler.aroundTag(SourceModel.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);

    SourceModel restored = new SourceModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, restored, null);

    assertEquals(1, restored.getJsonSources().size());
    SourceJson json = restored.findJsonSource("order_lines");
    assertNotNull(json);
    assertEquals(SourceJsonParentKind.TABLE, json.resolveParentSourceKind());
    assertEquals("events", json.getParentSourceName());
    assertEquals("payload", json.getJsonFieldName());
    assertTrue(json.isIgnoreMissingPath());
    assertEquals(3, json.getFields().size());

    SourceJsonField passThrough = json.getFields().get(0);
    assertTrue(passThrough.isPassThrough());
    assertEquals("event_id", passThrough.getParentFieldName());
    assertEquals(1, passThrough.getPrimaryKeyPosition());

    SourceJsonField sku = json.getFields().get(1);
    assertEquals("sku", sku.getName());
    assertEquals("$.lines.*.sku", sku.getPath());
    assertTrue(sku.isArrayExpandingPath());
  }

  @Test
  void checkOkForJsonSourceWithParentTable() {
    SourceModel model = sampleJsonLandingModel();
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void checkFlagsJsonSourceMissingParent() {
    SourceModel model = sampleJsonLandingModel();
    model.findJsonSource("order_lines").setParentSourceName("missing_events");
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertTrue(
        remarks.stream()
            .anyMatch(r -> r.getText() != null && r.getText().contains("missing_events")));
  }

  @Test
  void checkFlagsJsonSourceMultipleArrayBases() {
    SourceModel model = sampleJsonLandingModel();
    SourceJson json = model.findJsonSource("order_lines");
    SourceJsonField other = new SourceJsonField("tag", "$.tags.*");
    other.setHopType(2);
    json.getFields().add(other);
    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("different arrays")));
  }

  @Test
  void checkFlagsJsonSourceParentCycle() {
    SourceModel model = sampleJsonLandingModel();
    SourceJson first = model.findJsonSource("order_lines");

    SourceJson second = new SourceJson("nested_items");
    second.setParentSourceKind(SourceJsonParentKind.JSON);
    second.setParentSourceName("order_lines");
    second.setJsonFieldName("item_json");
    SourceJsonField f = new SourceJsonField("x", "$.x");
    f.setPrimaryKeyPosition(1);
    second.getFields().add(f);
    model.getJsonSources().add(second);

    // Create a cycle: order_lines → nested_items → order_lines
    first.setParentSourceKind(SourceJsonParentKind.JSON);
    first.setParentSourceName("nested_items");
    first.setJsonFieldName("payload");

    List<ICheckResult> remarks = model.check(null, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("cyclic")));
  }

  @Test
  void arrayBasePathGroupsFieldsUnderSameArray() {
    assertEquals("$.lines[*]", SourceModel.arrayBasePath("$.lines[*].sku"));
    assertEquals("$.lines[*]", SourceModel.arrayBasePath("$.lines[*].qty"));
    assertEquals("$.tags[*]", SourceModel.arrayBasePath("$.tags[*]"));
    assertEquals("$.lines.*", SourceModel.arrayBasePath("$.lines.*.sku"));
    assertEquals("$.tags.*", SourceModel.arrayBasePath("$.tags.*"));
  }

  private static SourceModel sampleJsonLandingModel() {
    SourceModel model = new SourceModel();

    SourceTable events = new SourceTable("events");
    events.setTableName("events");
    events.setDatabaseName("CRM");
    events.setLocation(new Point(40, 40));
    SourceColumn eventId = new SourceColumn("event_id");
    eventId.setPrimaryKeyPosition(1);
    eventId.setHopType(5);
    events.getColumns().add(eventId);
    SourceColumn payload = new SourceColumn("payload");
    payload.setHopType(2);
    events.getColumns().add(payload);
    model.getTables().add(events);

    SourceJson json = new SourceJson("order_lines");
    json.setDescription("Flatten order line items from event payload");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("events");
    json.setJsonFieldName("payload");
    json.setIgnoreMissingPath(true);
    json.setDefaultPathLeafToNull(true);
    json.setLocation(new Point(260, 40));

    SourceJsonField passThrough = SourceJsonField.passThroughField("event_id");
    passThrough.setPrimaryKeyPosition(1);
    passThrough.setHopType(5);
    json.getFields().add(passThrough);

    SourceJsonField sku = new SourceJsonField("sku", "$.lines.*.sku");
    sku.setHopType(2);
    sku.setPrimaryKeyPosition(2);
    json.getFields().add(sku);

    SourceJsonField qty = new SourceJsonField("qty", "$.lines.*.qty");
    qty.setHopType(5);
    json.getFields().add(qty);

    model.getJsonSources().add(json);
    return model;
  }

  private static SourceModel sampleProductLookupModel() {
    SourceModel model = new SourceModel();

    SourceTable product = new SourceTable("product");
    product.setTableName("product");
    product.setDatabaseName("CRM");
    product.setLocation(new Point(50, 60));
    SourceColumn productId = new SourceColumn("product_id");
    productId.setPrimaryKeyPosition(1);
    productId.setHopType(5);
    product.getColumns().add(productId);
    SourceColumn typeId = new SourceColumn("type_id");
    typeId.setHopType(5);
    product.getColumns().add(typeId);
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    productType.setTableName("product_type");
    productType.setDatabaseName("CRM");
    productType.setLocation(new Point(280, 60));
    SourceColumn typePk = new SourceColumn("type_id");
    typePk.setPrimaryKeyPosition(1);
    typePk.setHopType(5);
    productType.getColumns().add(typePk);
    SourceColumn typeName = new SourceColumn("type_name");
    typeName.setHopType(2);
    productType.getColumns().add(typeName);
    model.getTables().add(productType);

    SourceRelationship fk = new SourceRelationship("fk_product_type");
    fk.setChildTableName("product");
    fk.setParentTableName("product_type");
    fk.setChildColumns(List.of("type_id"));
    fk.setParentColumns(List.of("type_id"));
    fk.setDefaultJoinType(SourceJoinType.LEFT);
    model.getRelationships().add(fk);

    SourceQuery query = new SourceQuery("feed_product_enriched");
    query.setDrivingTableName("product");
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName("product_type");
    join.setRelationshipName("fk_product_type");
    join.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(join);
    query.getColumns().add(new SourceQueryColumn("product", "product_id"));
    query.getColumns().add(new SourceQueryColumn("product", "type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_id", "product_type_id"));
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name"));
    model.getQueries().add(query);

    return model;
  }
}
