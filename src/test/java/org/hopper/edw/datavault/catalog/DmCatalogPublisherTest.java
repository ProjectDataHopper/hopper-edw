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
package org.hopper.edw.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.quality.model.RecordQualityRuleBinding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmCatalogPublisherTest {

  private static final String CATALOG_CONNECTION = "dm-test-catalog";

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/integration-tests");

    metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    vault.setPreferredSchemaName("dm");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);

    Path catalogDir = Files.createTempDirectory("dm-catalog-publish-test");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void dimensionRecordDefinitionMapsToDimTable() throws Exception {
    DimensionalModel model = loadDimensionalModel("integration-tests/tests/basic/basic-star.hdm");
    DmDimension dimension = (DmDimension) model.findTable("dim_customer");
    String namespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);

    RecordDefinition definition =
        DmCatalogPublisher.toTableRecordDefinition(
            dimension,
            model,
            namespace,
            variables,
            metadataProvider,
            new Date(),
            "test-workflow",
            variables);

    assertEquals(new RecordDefinitionKey(namespace, "dim_customer"), definition.getKey());
    assertEquals(RecordDefinitionType.DIM_TABLE, definition.getType());
    assertNotNull(definition.getFields());
    assertFalse(definition.getFields().isEmpty());
    assertNotNull(definition.getPhysicalTable());
    assertEquals("Vault", definition.getPhysicalTable().getDatabaseMetaName());
    assertEquals("d_customer", definition.getPhysicalTable().getTableName());
    assertEquals("dm", definition.getPhysicalTable().getSchemaName());
    assertTrue(definition.getTags().contains("DM DIMENSION"));
    assertTrue(definition.getTags().contains(model.getName()));
    assertEquals("DIMENSIONAL_MODEL", definition.getOrigin().getModelType());
    assertEquals("dim_customer", definition.getOrigin().getModelElementName());
  }

  @Test
  void factRecordDefinitionMapsToFactTable() throws Exception {
    DimensionalModel model = loadDimensionalModel("integration-tests/tests/basic/basic-star.hdm");
    DmFact fact = (DmFact) model.findTable("fact_sales");
    String namespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);

    RecordDefinition definition =
        DmCatalogPublisher.toTableRecordDefinition(
            fact,
            model,
            namespace,
            variables,
            metadataProvider,
            new Date(),
            "test-workflow",
            variables);

    assertEquals(RecordDefinitionType.FACT_TABLE, definition.getType());
    assertEquals("f_sales", definition.getPhysicalTable().getTableName());
    assertTrue(definition.getTags().contains("DM FACT"));
  }

  @Test
  void dimensionAliasRecordDefinitionResolvesTargetLayout() throws Exception {
    DimensionalModel model =
        loadDimensionalModel("integration-tests/tests/basic/date-role-playing.hdm");
    IDmTable alias = model.findTable("dim_order_date");
    String namespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);

    RecordDefinition definition =
        DmCatalogPublisher.toTableRecordDefinition(
            alias,
            model,
            namespace,
            variables,
            metadataProvider,
            new Date(),
            "test-workflow",
            variables);

    assertEquals(RecordDefinitionType.DIM_TABLE, definition.getType());
    assertTrue(definition.getTags().contains("DM DIMENSION_ALIAS"));
    assertNotNull(definition.getFields().searchValueMeta("date_key"));
    assertEquals("d_date", definition.getPhysicalTable().getTableName());
  }

  @Test
  void publishUpsertsAllModelTables() throws Exception {
    DimensionalModel model = loadDimensionalModel("integration-tests/tests/basic/basic-star.hdm");
    String namespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);

    DmCatalogPublisher.PublishResult result =
        DmCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "publish-basic-star");

    assertEquals(3, result.getTableCount());
    assertEquals(0, result.getErrorCount());
    assertTrue(result.isSuccess());

    for (String tableName : List.of("dim_customer", "dim_product", "fact_sales")) {
      RecordDefinition stored =
          RecordDefinitionRegistry.getInstance()
              .read(
                  CATALOG_CONNECTION,
                  new RecordDefinitionKey(namespace, tableName),
                  variables,
                  metadataProvider);
      assertNotNull(stored, "Missing published record definition for " + tableName);
      assertFalse(stored.getFields().isEmpty());
    }
  }

  @Test
  void republishPreservesQualityRulesAndValidationAcknowledgements() throws Exception {
    DimensionalModel model = loadDimensionalModel("integration-tests/tests/basic/basic-star.hdm");
    String namespace = DmCatalogNamespaces.projectDimensionalModelsNamespace(variables, model);
    RecordDefinitionKey key = new RecordDefinitionKey(namespace, "dim_customer");
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    DmCatalogPublisher.PublishResult first =
        DmCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "publish-1");
    assertTrue(first.isSuccess());

    RecordDefinition stored = registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(stored);
    assertNotNull(stored.getOrigin());
    Date createdAt = stored.getOrigin().getCreatedAt();
    assertNotNull(createdAt);

    RecordQualityRuleBinding binding = new RecordQualityRuleBinding();
    binding.setRuleSetName("retail-rules");
    binding.setRuleId("null-ratio-max");
    binding.setEnabled(true);
    stored.setQualityRules(new ArrayList<>(List.of(binding)));

    RecordDefinitionValidationAcknowledgement ack = new RecordDefinitionValidationAcknowledgement();
    ack.setIssueId("FIELD_ADDED|loyalty_tier|");
    ack.setComment("known additive column");
    ack.setAcknowledgedAt(new Date());
    ack.setAcknowledgedBy("test");
    stored.setValidationAcknowledgements(new ArrayList<>(List.of(ack)));

    registry.upsert(CATALOG_CONNECTION, stored, variables, metadataProvider);

    DmCatalogPublisher.PublishResult second =
        DmCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "publish-2");
    assertTrue(second.isSuccess());

    RecordDefinition afterRepublish =
        registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(afterRepublish);
    assertNotNull(afterRepublish.getOrigin());
    // Proves the document was rewritten by the second publish (not stale manual upsert).
    assertEquals("publish-2", afterRepublish.getOrigin().getLastWorkflow());
    assertEquals(createdAt, afterRepublish.getOrigin().getCreatedAt());
    assertNotNull(afterRepublish.getQualityRules());
    assertEquals(1, afterRepublish.getQualityRules().size());
    assertEquals("retail-rules", afterRepublish.getQualityRules().get(0).getRuleSetName());
    assertEquals("null-ratio-max", afterRepublish.getQualityRules().get(0).getRuleId());
    assertTrue(afterRepublish.getQualityRules().get(0).isEnabled());
    assertNotNull(afterRepublish.getValidationAcknowledgements());
    assertEquals(1, afterRepublish.getValidationAcknowledgements().size());
    assertEquals(
        "FIELD_ADDED|loyalty_tier|",
        afterRepublish.getValidationAcknowledgements().get(0).getIssueId());
    assertEquals(
        "known additive column",
        afterRepublish.getValidationAcknowledgements().get(0).getComment());
    assertEquals("test", afterRepublish.getValidationAcknowledgements().get(0).getAcknowledgedBy());
  }

  private static DataCatalogMeta buildCatalogMeta(Path catalogDir) {
    DataCatalogMeta meta = new DataCatalogMeta();
    meta.setName(CATALOG_CONNECTION);
    meta.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    meta.setCatalog(fileCatalog);
    return meta;
  }

  private static DimensionalModel loadDimensionalModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    // Filename is runtime-only (not in XML); bind load path like Hop file openers.
    model.setFilename(fixture.toString());
    return model;
  }
}
