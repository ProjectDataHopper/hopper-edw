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

package org.apache.hop.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
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

class LineageCatalogBaselineLoaderTest {

  private static final String CATALOG = "lineage-baseline-catalog";

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
        "PROJECT_HOME",
        Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    metadataProvider = new MemoryMetadataProvider();
    Path dir = Files.createTempDirectory("lineage-baseline");
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName(CATALOG);
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(dir.toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void roundTripPublishAndLoadBaseline() throws Exception {
    DataVaultModel model = loadModel("retail-example/models/retail-360.hdv");
    model.setName("retail-360");
    LineageSnapshot published = DvModelLineageCollector.collect(model, variables);
    LineageCatalogPublisher.publish(CATALOG, published, variables, metadataProvider, "pub");

    LineageSnapshot baseline =
        LineageCatalogBaselineLoader.load(
            CATALOG, LineageLayer.DV, "retail-360", variables, metadataProvider);
    assertNotNull(baseline);
    assertTrue(baseline.findTableByLogicalName("hub_customer").isPresent());
    assertTrue(baseline.findTableByLogicalName("sat_customer_demo").isPresent());

    TableLineage hub = baseline.findTableByLogicalName("hub_customer").orElseThrow();
    assertEquals("hub_customer", hub.getPhysicalTableName());
    assertTrue(hub.findField("customer_id").isPresent());

    // Identical snapshot → no drift
    LineageDiffResult diff =
        LineageSnapshotDiffSupport.compare(baseline, published, "catalog-baseline");
    assertFalse(diff.hasBlocking());
    // Reasons may differ slightly; allow only INFO/add noise — mapping signatures should match
    assertTrue(
        diff.getEntries().stream()
            .noneMatch(
                e ->
                    e.getType() == LineageDiffType.TABLE_RENAMED
                        || e.getType() == LineageDiffType.MAPPING_CHANGED),
        () -> "unexpected drift: " + diff.getEntries().stream().map(LineageDiffEntry::getMessage).toList());
  }

  @Test
  void loadReturnsNullWhenMissing() throws Exception {
    LineageSnapshot baseline =
        LineageCatalogBaselineLoader.load(
            CATALOG, LineageLayer.DV, "does-not-exist", variables, metadataProvider);
    assertNull(baseline);
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
