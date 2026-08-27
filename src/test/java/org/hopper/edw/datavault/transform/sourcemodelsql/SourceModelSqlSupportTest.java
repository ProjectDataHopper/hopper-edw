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
package org.hopper.edw.datavault.transform.sourcemodelsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SourceModelSqlSupportTest {

  private static final Path RETAIL_HSM =
      Path.of("retail-example/models/source-tables-crm.hsm").toAbsolutePath().normalize();

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  static boolean retailHsmPresent() {
    return Files.isRegularFile(RETAIL_HSM);
  }

  @Test
  void parseRowLimit() {
    assertEquals(0, SourceModelSqlSupport.parseRowLimit(null, new Variables()));
    assertEquals(0, SourceModelSqlSupport.parseRowLimit("0", new Variables()));
    assertEquals(50, SourceModelSqlSupport.parseRowLimit("50", new Variables()));
    Variables vars = new Variables();
    vars.setVariable("LIMIT", "25");
    assertEquals(25, SourceModelSqlSupport.parseRowLimit("${LIMIT}", vars));
  }

  @Test
  void saveGeneratedPipelineToTempSetsFilename() throws Exception {
    Path hsm = writeSampleModel(tempDir.resolve("sample.hsm"));
    IHopMetadataProvider metadata = memoryWithDb("CRM");
    Variables variables = new Variables();
    // Isolate temp under junit tempDir
    variables.setVariable("java.io.tmpdir", tempDir.toString());

    SourceModelSqlPlan plan =
        SourceModelSqlSupport.plan(
            hsm.toString(), "SELECT order_id FROM orders", variables, metadata, 0);
    assertNotNull(plan.pipelineMeta());

    org.apache.hop.pipeline.PipelineMeta saved =
        SourceModelSqlSupport.saveGeneratedPipelineToTemp(plan.pipelineMeta(), variables, metadata);

    assertNotNull(saved.getFilename());
    assertTrue(saved.getFilename().contains("hop-source-model-sql"));
    assertTrue(saved.getFilename().endsWith(".hpl"));
    assertTrue(Files.isRegularFile(Path.of(saved.getFilename())));
  }

  @Test
  void planAgainstTempModel() throws Exception {
    Path hsm = writeSampleModel(tempDir.resolve("sample.hsm"));
    IHopMetadataProvider metadata = memoryWithDb("CRM");

    SourceModelSqlPlan plan =
        SourceModelSqlSupport.plan(
            hsm.toString(),
            "SELECT order_id, city FROM orders WHERE order_id > 0",
            new Variables(),
            metadata,
            10);

    assertNotNull(plan);
    assertTrue(plan.fullPushdown(), plan.explainText());
    assertNotNull(plan.outputRowMeta());
    assertTrue(plan.outputRowMeta().size() >= 2);

    IRowMeta fields =
        SourceModelSqlSupport.planOutputRowMeta(
            hsm.toString(), "SELECT order_id, city FROM orders", new Variables(), metadata);
    assertNotNull(fields);
    assertTrue(fields.size() >= 2);
  }

  @Test
  @EnabledIf("retailHsmPresent")
  void validateRetailModel() throws Exception {
    IHopMetadataProvider metadata = new MemoryMetadataProvider();
    // May fail validation only if SQL tables missing — use a table from fixture if present.
    String xml = Files.readString(RETAIL_HSM);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);
    SourceModel model = new SourceModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, model, metadata);
    assertTrue(model.getTables().size() > 0);
    SourceTable table = model.getTables().get(0);
    if (!UtilsEmpty(table.getDatabaseName())) {
      ensureDb(metadata, table.getDatabaseName());
    }
    String col = table.getColumns().isEmpty() ? "*" : table.getColumns().get(0).getName();
    String sql =
        "*".equals(col)
            ? "SELECT * FROM " + table.getName() + " LIMIT 1"
            : "SELECT " + col + " FROM " + table.getName() + " LIMIT 1";
    SourceModelSqlSupport.validate(RETAIL_HSM.toString(), sql, new Variables(), metadata);
  }

  private static boolean UtilsEmpty(String s) {
    return s == null || s.isBlank();
  }

  private static Path writeSampleModel(Path path) throws Exception {
    SourceModel model = new SourceModel();
    model.setName("sample");
    SourceTable orders = new SourceTable("orders");
    orders.setDatabaseName("CRM");
    orders.setSchemaName("public");
    orders.setTableName("orders");
    SourceColumn id = new SourceColumn("order_id");
    id.setHopType(5);
    SourceColumn city = new SourceColumn("city");
    city.setHopType(2);
    orders.getColumns().add(id);
    orders.getColumns().add(city);
    model.getTables().add(orders);

    String body = XmlMetadataUtil.serializeObjectToXml(model);
    String xml = XmlHandler.getXmlHeader() + XmlHandler.aroundTag(SourceModel.XML_TAG, body);
    Files.writeString(path, xml);
    return path;
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
