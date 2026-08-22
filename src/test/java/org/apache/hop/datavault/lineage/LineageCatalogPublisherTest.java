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
package org.apache.hop.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class LineageCatalogPublisherTest {

  private static final String CATALOG_CONNECTION = "lineage-test-catalog";

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
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    metadataProvider = new MemoryMetadataProvider();
    Path catalogDir = Files.createTempDirectory("lineage-catalog-publish-test");
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName(CATALOG_CONNECTION);
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void publishWritesSnapshotAndTableSiblingRecords() throws Exception {
    DataVaultModel model = loadModel("retail-example/models/retail-360.hdv");
    model.setName("retail-360");
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);

    LineageCatalogPublisher.PublishResult result =
        LineageCatalogPublisher.publish(
            CATALOG_CONNECTION, snapshot, variables, metadataProvider, "lineage-test");

    assertTrue(result.isSuccess(), "lineage publish should succeed");
    assertTrue(
        result.getLineageRecordCount() > 1,
        "expected snapshot + at least one table (got " + result.getLineageRecordCount() + ")");

    String namespace =
        LineageCatalogNamespaces.projectLineageNamespace(variables, LineageLayer.DV, "retail-360");
    assertEquals("hop/retail-example/lineage/dv/retail-360", namespace);

    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    RecordDefinition snapshotRec =
        registry.read(
            CATALOG_CONNECTION,
            new RecordDefinitionKey(namespace, LineageCatalogNamespaces.SNAPSHOT_RECORD_NAME),
            variables,
            metadataProvider);
    assertNotNull(snapshotRec);
    assertEquals(RecordDefinitionType.UNKNOWN, snapshotRec.getType());
    assertTrue(snapshotRec.getTags().contains(LineageCatalogNamespaces.TAG_LINEAGE));
    assertTrue(snapshotRec.getTags().contains(LineageCatalogNamespaces.TAG_LINEAGE_SNAPSHOT));
    CatalogCustomProperty tableCount =
        snapshotRec.getCustomProperties().get(LineageCatalogNamespaces.PROP_TABLE_COUNT);
    assertNotNull(tableCount);
    assertTrue(Integer.parseInt(tableCount.getValue()) >= 1);

    RecordDefinition hub =
        registry.read(
            CATALOG_CONNECTION,
            new RecordDefinitionKey(namespace, "hub_customer"),
            variables,
            metadataProvider);
    assertNotNull(hub, "hub_customer lineage sibling expected");
    assertTrue(hub.getTags().contains(LineageCatalogNamespaces.TAG_LINEAGE));
    assertTrue(hub.getTags().contains("HUB") || hub.getTags().contains("DV"));
    CatalogCustomProperty fieldsJson =
        hub.getCustomProperties().get(LineageCatalogNamespaces.PROP_FIELDS_JSON);
    assertNotNull(fieldsJson);
    assertTrue(fieldsJson.getValue().contains("customer_id"), fieldsJson.getValue());
    assertTrue(
        fieldsJson.getValue().contains("USER_EXPLICIT_MAPPING")
            || fieldsJson.getValue().contains("customer_id"),
        fieldsJson.getValue());

    CatalogCustomProperty physical =
        hub.getCustomProperties().get(LineageCatalogNamespaces.PROP_PHYSICAL_TABLE);
    assertNotNull(physical);
    assertEquals("hub_customer", physical.getValue());
  }

  @Test
  void republishReplacesDerivedLineage() throws Exception {
    DataVaultModel model = loadModel("retail-example/models/retail-360.hdv");
    model.setName("retail-360");
    LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);

    LineageCatalogPublisher.publish(
        CATALOG_CONNECTION, snapshot, variables, metadataProvider, "run-1");
    LineageCatalogPublisher.PublishResult second =
        LineageCatalogPublisher.publish(
            CATALOG_CONNECTION, snapshot, variables, metadataProvider, "run-2");
    assertTrue(second.isSuccess());

    String namespace =
        LineageCatalogNamespaces.projectLineageNamespace(variables, LineageLayer.DV, "retail-360");
    RecordDefinition sat =
        RecordDefinitionRegistry.getInstance()
            .read(
                CATALOG_CONNECTION,
                new RecordDefinitionKey(namespace, "sat_customer_demo"),
                variables,
                metadataProvider);
    assertNotNull(sat);
    assertEquals("run-2", sat.getOrigin().getLastWorkflow());
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    model.setFilename(fixture.toString());
    return model;
  }
}
