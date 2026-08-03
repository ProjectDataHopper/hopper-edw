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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class RetailDataVaultModelCheckTest {

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws HopException {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    variables.setVariable("OUTPUT_COPIES", "2");

    metadataProvider = new MemoryMetadataProvider();
    // Match retail-example RDBMS plugin ids (Vault/CRM are PostgreSQL).
    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    vault.setDatabaseType("POSTGRESQL");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    crm.setDatabaseType("POSTGRESQL");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);

    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    // Use committed seed fixtures, not gitignored work/edw-catalog (can drift after live refresh).
    fileCatalog.setStorageDirectory(
        RetailExampleCatalogFixtures.seedCatalogStorageRoot().toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void retail360ModelCheckLoadsCatalogSources() throws Exception {
    DataVaultModel model = loadModel("retail-example/models/retail-360.hdv");
    // Offline unit test: no live JDBC and no Hop bulk-loader transform plugins on the classpath.
    // The retail model uses native bulk load at runtime; force Table Output for model-check only.
    model
        .getConfigurationOrDefault()
        .setTargetLoadMode(DvTargetLoadMode.TABLE_OUTPUT.getDescription());

    List<ICheckResult> remarks =
        model.check(metadataProvider, variables, DvModelCheckOptions.fastOnly());

    assertTrue(
        remarks.stream()
            .noneMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .contains("Error loading data vault metadata when model checking")),
        "Model check should load catalog sources for retail-360");
    assertTrue(
        remarks.stream().noneMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () ->
            "Unexpected model-check errors: "
                + remarks.stream()
                    .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)
                    .map(ICheckResult::getText)
                    .toList());
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
