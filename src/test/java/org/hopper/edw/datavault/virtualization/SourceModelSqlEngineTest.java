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
package org.hopper.edw.datavault.virtualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlException;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlOptions;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SourceModelSqlEngineTest {

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
  void simpleSelectParsesAndPlansFullPushdown() throws Exception {
    SourceModel model = sampleTwoTableModel("CRM", "CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT customer_id, name FROM customer WHERE customer_id > 0 ORDER BY name LIMIT 10",
            new Variables(),
            metadata,
            SourceModelSqlOptions.defaults());

    assertTrue(plan.fullPushdown());
    assertEquals(1, plan.pipelineMeta().getTransforms().size());
    assertEquals("TableInput", plan.pipelineMeta().getTransform(0).getTransformPluginId());
    assertFalse(plan.pushdownSqlFragments().isEmpty());
    String sql = plan.pushdownSqlFragments().get(0).toUpperCase();
    assertTrue(sql.contains("SELECT"));
    assertTrue(sql.contains("CUSTOMER") || sql.contains("customer".toUpperCase()));
    assertNotNull(plan.outputRowMeta());
    assertTrue(plan.outputRowMeta().size() >= 2);
  }

  @Test
  void jdbcSchemaQualifiedTableNameResolves() throws Exception {
    // DBeaver writes FROM crm.order_header when schema=crm is active
    SourceModel model = sampleTwoTableModel("CRM", "CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT customer_id, name FROM crm.customer WHERE customer_id > 0",
            new Variables(),
            metadata,
            SourceModelSqlOptions.builder().jdbcSchemaAlias("crm").build());

    assertTrue(plan.fullPushdown(), plan.explainText());
    assertEquals(1, plan.pipelineMeta().nrTransforms());
  }

  @Test
  void sourceSchemaQualifiedTableNameResolves() throws Exception {
    SourceModel model = sampleTwoTableModel("CRM", "CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT customer_id FROM source.customer",
            new Variables(),
            metadata,
            SourceModelSqlOptions.defaults());

    assertTrue(plan.fullPushdown(), plan.explainText());
  }

  @Test
  void joinSameConnectionFullPushdown() throws Exception {
    SourceModel model = sampleTwoTableModel("CRM", "CRM");
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT c.customer_id, a.city FROM customer c "
                + "INNER JOIN address a ON c.customer_id = a.customer_id",
            new Variables(),
            metadata);

    assertTrue(plan.fullPushdown(), plan.explainText());
    assertEquals(1, plan.pipelineMeta().nrTransforms());
    assertTrue(plan.pushdownSqlFragments().get(0).toUpperCase().contains("JOIN"));
  }

  @Test
  void multiConnectionProducesResidualPipeline() throws Exception {
    SourceModel model = sampleTwoTableModel("CRM", "ERP");
    IHopMetadataProvider metadata = memoryWithDb("CRM", "ERP");

    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(
            model,
            "SELECT c.customer_id, a.city FROM customer c "
                + "INNER JOIN address a ON c.customer_id = a.customer_id",
            new Variables(),
            metadata);

    assertFalse(plan.fullPushdown(), plan.explainText());
    assertTrue(plan.pipelineMeta().nrTransforms() > 1);
    assertTrue(
        plan.residualOperators().stream().anyMatch(s -> s.toLowerCase().contains("join")),
        plan.explainText());
  }

  @Test
  void unknownTableFailsValidation() {
    SourceModel model = sampleTwoTableModel("CRM", "CRM");
    assertThrows(
        SourceModelSqlException.class,
        () ->
            SourceModelSqlEngine.validate(model, "SELECT * FROM does_not_exist", new Variables()));
  }

  @Test
  @EnabledIf("retailHsmPresent")
  void retailModelFreeSqlPlans() throws Exception {
    String xml = Files.readString(RETAIL_HSM);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);
    SourceModel model = new SourceModel();
    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, model, metadataProvider);
    // Retail CRM tables share one connection name from the fixture.
    seedRetailDatabases(model, metadataProvider);

    SourceModelSqlEngine.validate(
        model, "SELECT customer_id FROM customer_hub LIMIT 5", new Variables());

    // Use first two tables if present for a simple select
    assertFalse(model.getTables().isEmpty());
    SourceTable t0 = model.getTables().get(0);
    String col = t0.getColumns().isEmpty() ? "*" : t0.getColumns().get(0).getName();
    String sql =
        col.equals("*")
            ? "SELECT * FROM " + t0.getName() + " LIMIT 5"
            : "SELECT " + col + " FROM " + t0.getName() + " LIMIT 5";

    // Only plan when table has a database connection in the model.
    if (t0.getDatabaseName() != null && !t0.getDatabaseName().isBlank()) {
      ensureDb(metadataProvider, t0.getDatabaseName());
      SourceModelSqlPlan plan =
          SourceModelSqlEngine.plan(model, sql, new Variables(), metadataProvider);
      assertNotNull(plan.pipelineMeta());
      assertTrue(plan.pipelineMeta().nrTransforms() >= 1);
    }
  }

  private static void seedRetailDatabases(SourceModel model, IHopMetadataProvider metadata)
      throws Exception {
    for (SourceTable table : model.getTables()) {
      if (table != null && table.getDatabaseName() != null && !table.getDatabaseName().isBlank()) {
        ensureDb(metadata, table.getDatabaseName());
      }
    }
  }

  private static SourceModel sampleTwoTableModel(String customerDb, String addressDb) {
    SourceModel model = new SourceModel();
    model.setName("sample");

    SourceTable customer = new SourceTable("customer");
    customer.setDatabaseName(customerDb);
    customer.setSchemaName("public");
    customer.setTableName("customer");
    customer.getColumns().add(col("customer_id", 5));
    customer.getColumns().add(col("name", 2));
    model.getTables().add(customer);

    SourceTable address = new SourceTable("address");
    address.setDatabaseName(addressDb);
    address.setSchemaName("public");
    address.setTableName("address");
    address.getColumns().add(col("customer_id", 5));
    address.getColumns().add(col("city", 2));
    model.getTables().add(address);
    return model;
  }

  private static SourceColumn col(String name, int hopType) {
    SourceColumn c = new SourceColumn(name);
    c.setHopType(hopType);
    return c;
  }

  private static IHopMetadataProvider memoryWithDb(String... names) throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    for (String name : names) {
      ensureDb(provider, name);
    }
    return provider;
  }

  private static void ensureDb(IHopMetadataProvider provider, String name) throws Exception {
    if (provider.getSerializer(DatabaseMeta.class).exists(name)) {
      return;
    }
    DatabaseMeta db = new DatabaseMeta();
    db.setName(name);
    // Generic / Postgres-like plugin id for dialect mapping tests.
    db.setDatabaseType("POSTGRESQL");
    db.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    db.setHostname("localhost");
    db.setDBName("test");
    db.setPort("5432");
    db.setUsername("test");
    db.setPassword("test");
    provider.getSerializer(DatabaseMeta.class).save(db);
  }
}
