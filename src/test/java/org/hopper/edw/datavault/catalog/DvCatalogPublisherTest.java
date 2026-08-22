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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.quality.model.RecordQualityRuleBinding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DvCatalogPublisherTest {

  private static final String CATALOG_CONNECTION = "dv-test-catalog";

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
    vault.setPreferredSchemaName("dv");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);

    Path catalogDir = Files.createTempDirectory("dv-catalog-publish-test");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void publishedOriginModelFilenameIsPortableUnderProjectHome() throws Exception {
    Path projectHome = Files.createTempDirectory("dv-catalog-portable-origin");
    Path modelsDir = projectHome.resolve("models");
    Files.createDirectories(modelsDir);
    Path modelFile = modelsDir.resolve("vault1.hdv");
    Files.copy(
        Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize(),
        modelFile);

    variables.setVariable("PROJECT_HOME", projectHome.toAbsolutePath().normalize().toString());

    DataVaultModel model = loadDataVaultModel(modelFile.toString());
    model.setFilename(modelFile.toAbsolutePath().normalize().toString());

    String namespace = DvCatalogNamespaces.projectModelsNamespace(variables, model);
    RecordDefinitionKey key = new RecordDefinitionKey(namespace, "hub_customer");
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    DvCatalogPublisher.PublishResult result =
        DvCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "portable-publish");
    assertTrue(result.getTableCount() > 0);

    RecordDefinition stored = registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(stored);
    assertNotNull(stored.getOrigin());
    assertEquals("${PROJECT_HOME}/models/vault1.hdv", stored.getOrigin().getModelFilename());
  }

  @Test
  void republishPreservesQualityRulesAndValidationAcknowledgements() throws Exception {
    DataVaultModel model = loadDataVaultModel("integration-tests/tests/basic/vault1.hdv");
    String namespace = DvCatalogNamespaces.projectModelsNamespace(variables, model);
    RecordDefinitionKey key = new RecordDefinitionKey(namespace, "hub_customer");
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    DvCatalogPublisher.PublishResult first =
        DvCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "publish-1");
    // Hubs publish without source catalog; satellites may error without local-catalog sources.
    assertTrue(first.getTableCount() > 0, "expected at least hubs/links to publish");

    RecordDefinition stored = registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(stored);
    assertNotNull(stored.getOrigin());
    Date createdAt = stored.getOrigin().getCreatedAt();
    assertNotNull(createdAt);
    assertEquals("publish-1", stored.getOrigin().getLastWorkflow());

    RecordQualityRuleBinding binding = new RecordQualityRuleBinding();
    binding.setRuleSetName("vault-rules");
    binding.setRuleId("row-count-min");
    binding.setEnabled(true);
    stored.setQualityRules(new ArrayList<>(List.of(binding)));

    RecordDefinitionValidationAcknowledgement ack = new RecordDefinitionValidationAcknowledgement();
    ack.setIssueId("FIELD_REMOVED|legacy_col|");
    ack.setComment("retired column");
    ack.setAcknowledgedAt(new Date());
    ack.setAcknowledgedBy("test");
    stored.setValidationAcknowledgements(new ArrayList<>(List.of(ack)));

    registry.upsert(CATALOG_CONNECTION, stored, variables, metadataProvider);

    DvCatalogPublisher.PublishResult second =
        DvCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "publish-2");
    assertTrue(second.getTableCount() > 0);

    RecordDefinition afterRepublish =
        registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(afterRepublish);
    assertNotNull(afterRepublish.getOrigin());
    // Proves hub_customer was rewritten by the second publish (merge path exercised).
    assertEquals("publish-2", afterRepublish.getOrigin().getLastWorkflow());
    assertEquals(createdAt, afterRepublish.getOrigin().getCreatedAt());
    assertNotNull(afterRepublish.getQualityRules());
    assertEquals(1, afterRepublish.getQualityRules().size());
    assertEquals("vault-rules", afterRepublish.getQualityRules().get(0).getRuleSetName());
    assertEquals("row-count-min", afterRepublish.getQualityRules().get(0).getRuleId());
    assertTrue(afterRepublish.getQualityRules().get(0).isEnabled());
    assertNotNull(afterRepublish.getValidationAcknowledgements());
    assertEquals(1, afterRepublish.getValidationAcknowledgements().size());
    assertEquals(
        "FIELD_REMOVED|legacy_col|",
        afterRepublish.getValidationAcknowledgements().get(0).getIssueId());
    assertEquals(
        "retired column", afterRepublish.getValidationAcknowledgements().get(0).getComment());
    assertEquals("test", afterRepublish.getValidationAcknowledgements().get(0).getAcknowledgedBy());
  }

  @Test
  void publishWritesModelRegistryEntry() throws Exception {
    DataVaultModel model = loadDataVaultModel("integration-tests/tests/basic/vault1.hdv");
    model.setFilename("/workspace/integration-tests/tests/basic/vault1.hdv");

    DvCatalogPublisher.PublishResult result =
        DvCatalogPublisher.publish(
            CATALOG_CONNECTION, model, variables, metadataProvider, "registry-test");
    assertTrue(result.getTableCount() > 0);

    // Registry entries are type-scoped (models-registry/dv|bv|dm) so DV/BV basenames never collide.
    RecordDefinitionKey modelKey =
        CatalogModelRegistrySupport.modelRegistryKey(
            variables, "vault1", RecordDefinitionType.DV_MODEL);
    RecordDefinition modelEntry =
        RecordDefinitionRegistry.getInstance()
            .read(CATALOG_CONNECTION, modelKey, variables, metadataProvider);
    assertNotNull(
        modelEntry, "expected DV model registry entry for vault1 under " + modelKey.getNamespace());
    assertEquals("hop/integration-tests/models-registry/dv", modelKey.getNamespace());
    assertEquals(RecordDefinitionType.DV_MODEL, modelEntry.getType());
    assertNotNull(modelEntry.getOrigin());
    assertNotNull(modelEntry.getOrigin().getModelFilename());
    assertTrue(modelEntry.getTags().contains("MODEL_REGISTRY"));
    String resolved =
        CatalogModelRegistrySupport.resolveModelFilename(
            "vault1", RecordDefinitionType.DV_MODEL, variables, metadataProvider);
    assertNotNull(resolved);
    assertTrue(
        resolved.contains("vault1.hdv") || resolved.contains("vault1"),
        () -> "unexpected model path: " + resolved);
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

  private static DataVaultModel loadDataVaultModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    // Filename is runtime-only (not in XML); bind load path like Hop file openers.
    model.setFilename(fixture.toString());
    return model;
  }
}
