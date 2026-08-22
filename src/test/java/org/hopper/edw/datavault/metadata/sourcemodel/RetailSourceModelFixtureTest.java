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
package org.hopper.edw.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Loads the retail-example {@code source-tables-crm.hsm} fixture and verifies structural integrity
 * plus SQL generation for the sample multi-table query.
 */
class RetailSourceModelFixtureTest {

  private static final Path RETAIL_HSM =
      Path.of("retail-example/models/source-tables-crm.hsm").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  static boolean retailHsmPresent() {
    return Files.isRegularFile(RETAIL_HSM);
  }

  @Test
  @EnabledIf("retailHsmPresent")
  void retailSourceModelLoadsAndCustomerQueryGeneratesSql() throws Exception {
    String xml = Files.readString(RETAIL_HSM);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);
    assertNotNull(rootNode);

    SourceModel model = new SourceModel();
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, model, metadataProvider);
    ModelConfigurationTestSupport.loadProjectMetadata(
        metadataProvider, Path.of("retail-example").toAbsolutePath());
    ModelConfigurationResolver.attach(model, metadataProvider);
    model.setFilename(RETAIL_HSM.toString());
    // Free SQL check() plans pipelines and needs RDBMS metadata for named connections.
    seedDatabasesFromModel(model, metadataProvider);

    assertFalse(model.getTables().isEmpty());
    assertFalse(model.getRelationships().isEmpty());
    assertFalse(model.getQueries().isEmpty());

    SourceQuery query = model.findQuery("all-customer-info");
    assertNotNull(query, "Expected sample query 'all-customer-info'");
    assertEquals("customer_hub", query.getDrivingTableName());
    assertEquals(4, query.getJoins().size());
    assertFalse(query.getColumns().isEmpty());
    assertEquals("all-customer-info", query.getPublishedCatalogName());

    List<ICheckResult> remarks = model.check(metadataProvider, new Variables());
    assertTrue(
        remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> "Unexpected check errors: " + remarks.stream().map(ICheckResult::getText).toList());

    assertTrue(SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query));
    assertEquals(
        SourceQueryGenerationMode.SQL,
        SourceQueryGenerationSupport.resolveEffectiveMode(model, query));

    String sql = SourceQuerySqlGenerator.generate(model, query, null, new Variables());
    assertTrue(sql.contains("FROM public.customer_hub"), sql);
    assertTrue(sql.contains("LEFT OUTER JOIN public.customer_address"), sql);
    assertTrue(sql.contains("LEFT OUTER JOIN public.customer_contact"), sql);
    assertTrue(sql.contains("LEFT OUTER JOIN public.customer_demo"), sql);
    assertTrue(sql.contains("LEFT OUTER JOIN public.customer_prefs"), sql);
    assertTrue(sql.contains("customer_id"), sql);

    var fields = SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query);
    assertEquals(query.getColumns().size(), fields.size());
    assertTrue(fields.stream().anyMatch(f -> "customer_id".equals(f.getName())));
    assertTrue(fields.stream().anyMatch(f -> "email".equals(f.getName())));
  }

  private static void seedDatabasesFromModel(SourceModel model, IHopMetadataProvider metadata)
      throws Exception {
    for (SourceTable table : model.getTables()) {
      if (table == null || table.getDatabaseName() == null || table.getDatabaseName().isBlank()) {
        continue;
      }
      String name = table.getDatabaseName().trim();
      if (metadata.getSerializer(DatabaseMeta.class).exists(name)) {
        continue;
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
      metadata.getSerializer(DatabaseMeta.class).save(db);
    }
  }
}
