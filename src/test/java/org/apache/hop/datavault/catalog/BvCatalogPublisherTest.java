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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.quality.model.RecordQualityRuleBinding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvCatalogPublisherTest {

  private static final String CATALOG_CONNECTION = "bv-test-catalog";

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
    vault.setPreferredSchemaName("bv");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);

    Path catalogDir = Files.createTempDirectory("bv-catalog-publish-test");
    metadataProvider.getSerializer(DataCatalogMeta.class).save(buildCatalogMeta(catalogDir));

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void republishPreservesQualityRulesAndValidationAcknowledgements() throws Exception {
    BusinessVaultModel bvModel = loadBusinessVaultModel("integration-tests/tests/basic/vault1.hbv");
    DataVaultModel dvModel = loadDataVaultModel("integration-tests/tests/basic/vault1.hdv");
    String namespace = BvCatalogNamespaces.projectBusinessVaultModelsNamespace(variables, bvModel);
    RecordDefinitionKey key = new RecordDefinitionKey(namespace, "pit_customer");
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    BvCatalogPublisher.PublishResult first =
        BvCatalogPublisher.publish(
            CATALOG_CONNECTION, bvModel, dvModel, variables, metadataProvider, "publish-1");
    assertTrue(first.isSuccess());
    assertTrue(first.getTableCount() > 0);

    RecordDefinition stored = registry.read(CATALOG_CONNECTION, key, variables, metadataProvider);
    assertNotNull(stored);
    assertNotNull(stored.getOrigin());
    Date createdAt = stored.getOrigin().getCreatedAt();
    assertNotNull(createdAt);

    RecordQualityRuleBinding binding = new RecordQualityRuleBinding();
    binding.setRuleSetName("bv-rules");
    binding.setRuleId("null-ratio-max");
    binding.setEnabled(true);
    stored.setQualityRules(new ArrayList<>(List.of(binding)));

    RecordDefinitionValidationAcknowledgement ack = new RecordDefinitionValidationAcknowledgement();
    ack.setIssueId("FIELD_ADDED|snapshot_date|");
    ack.setComment("known pit column");
    ack.setAcknowledgedAt(new Date());
    ack.setAcknowledgedBy("test");
    stored.setValidationAcknowledgements(new ArrayList<>(List.of(ack)));

    registry.upsert(CATALOG_CONNECTION, stored, variables, metadataProvider);

    BvCatalogPublisher.PublishResult second =
        BvCatalogPublisher.publish(
            CATALOG_CONNECTION, bvModel, dvModel, variables, metadataProvider, "publish-2");
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
    assertEquals("bv-rules", afterRepublish.getQualityRules().get(0).getRuleSetName());
    assertEquals("null-ratio-max", afterRepublish.getQualityRules().get(0).getRuleId());
    assertTrue(afterRepublish.getQualityRules().get(0).isEnabled());
    assertNotNull(afterRepublish.getValidationAcknowledgements());
    assertEquals(1, afterRepublish.getValidationAcknowledgements().size());
    assertEquals(
        "FIELD_ADDED|snapshot_date|",
        afterRepublish.getValidationAcknowledgements().get(0).getIssueId());
    assertEquals(
        "known pit column", afterRepublish.getValidationAcknowledgements().get(0).getComment());
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

  private static BusinessVaultModel loadBusinessVaultModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, null);
    // Filename is runtime-only (not in XML); bind load path like Hop file openers.
    model.setFilename(fixture.toString());
    return model;
  }

  private static DataVaultModel loadDataVaultModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    model.setFilename(fixture.toString());
    return model;
  }
}
