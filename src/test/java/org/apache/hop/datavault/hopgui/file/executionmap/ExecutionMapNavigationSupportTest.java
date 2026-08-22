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
package org.apache.hop.datavault.hopgui.file.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.datavault.executionmap.ArtifactSnapshotSupport;
import org.apache.hop.datavault.executionmap.DatasetNodeSupport;
import org.apache.hop.datavault.executionmap.ExecutionMapDatasetCatalogSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapArtifactType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionMapNavigationSupportTest {

  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUpRetailCatalog() throws HopException {
    Variables catalogVariables = new Variables();
    catalogVariables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    // Committed unit fixtures (not gitignored work/edw-catalog) so CI has model + source defs.
    fileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.unitCatalogStorageRootPath());
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void detectsSyntheticPaths() {
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("generated://model/p1"));
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("synthetic://orchestrator"));
    assertTrue(ExecutionMapNavigationSupport.isSyntheticPath("dataset://Vault/d_customer"));
    assertFalse(ExecutionMapNavigationSupport.isSyntheticPath("/tmp/workflow.hwf"));
  }

  @Test
  void resolvesVariablesInNodePath() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/project");
    assertEquals(
        "/project/models/retail.hdv",
        ExecutionMapNavigationSupport.resolvePath(variables, "${PROJECT_HOME}/models/retail.hdv"));
  }

  @Test
  void findsSnapshotAndBuildsTooltip() throws Exception {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapArtifactSnapshot snapshot = new ExecutionMapArtifactSnapshot();
    snapshot.setId("snap-1");
    snapshot.setArtifactType(ExecutionMapArtifactType.PIPELINE);
    snapshot.setSourcePath("generated://model/p1");
    snapshot.setXmlGzipBase64(
        ArtifactSnapshotSupport.encodeXml("<pipeline><name>p1</name></pipeline>"));
    document.getSnapshotsOrEmpty().add(snapshot);

    ExecutionMapNode node = new ExecutionMapNode();
    node.setName("p1");
    node.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    node.setPath("generated://model/p1");
    node.setSnapshotId("snap-1");

    assertNotNull(ExecutionMapNavigationSupport.findSnapshot(node, document));
    assertTrue(ExecutionMapNavigationSupport.canOpenFromSnapshot(node, document));
    assertFalse(ExecutionMapNavigationSupport.canOpenArtifactFile(node));
    assertTrue(ExecutionMapNavigationSupport.canNavigate(node, document));

    String tooltip = ExecutionMapNavigationSupport.buildTooltip(node, document);
    assertNotNull(tooltip);
    assertTrue(tooltip.contains("p1"));
    assertTrue(tooltip.contains("GENERATED_PIPELINE"));
    assertFalse(tooltip.isBlank());
  }

  @Test
  void resolvesDatasetRecordKeyFromProperties() {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "Vault");
    node.setProperty("datasetName", "d_customer");

    RecordDefinitionKey key = ExecutionMapNavigationSupport.resolveDatasetRecordKey(node);

    assertEquals(new RecordDefinitionKey("Vault", "d_customer"), key);
  }

  @Test
  void resolvesDatasetRecordKeyFromPathWhenPropertiesMissing() {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    node.setPath("dataset://CRM::customer");

    RecordDefinitionKey key = ExecutionMapNavigationSupport.resolveDatasetRecordKey(node);

    assertEquals(new RecordDefinitionKey("CRM", "customer"), key);
  }

  @Test
  void previewUnavailableWithoutMetadataProvider() {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "hop/project/dimensional/model");
    node.setProperty("datasetName", "d_customer");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertFalse(
        ExecutionMapNavigationSupport.canPreviewDataset(
            node, new ExecutionMapDocument(), new Variables(), null));
  }

  @Test
  void canNavigateWhenTargetDatasetHasCatalogConnectionProperty() {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "Vault");
    node.setProperty("datasetName", "d_customer");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertTrue(ExecutionMapNavigationSupport.canNavigateToCatalog(node, new Variables(), null));
  }

  @Test
  void canNavigateWhenSourceDatasetHasCatalogConnectionProperty() {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    node.setProperty("datasetNamespace", "hop/retail-example/sources");
    node.setProperty("datasetName", "E2E-customer-hub");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertTrue(ExecutionMapNavigationSupport.canNavigateToCatalog(node, new Variables(), null));
  }

  @Test
  void infersSourcesNamespaceForLegacySourceDatasetUnderDataVaultModel() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setId("dv-model");
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    document.getNodesOrEmpty().add(modelNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    datasetNode.setParentNodeId("dv-model");
    datasetNode.setProperty("datasetNamespace", "CRM");
    datasetNode.setProperty("datasetName", "E2E-customer-hub");
    datasetNode.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");
    document.getNodesOrEmpty().add(datasetNode);

    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(datasetNode, document, variables);

    assertEquals(new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub"), key);
    assertTrue(
        ExecutionMapNavigationSupport.canNavigateToCatalog(datasetNode, document, variables, null));
  }

  @Test
  void canPreviewTargetDatasetFromCatalogRecordDefinition() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "hop/retail-example/models/retail-360");
    node.setProperty("datasetName", "hub_customer");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            node, new ExecutionMapDocument(), variables, metadataProvider));
  }

  @Test
  void canPreviewLegacyTargetDatasetUnderDataVaultModel() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setId("dv-model");
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    document.getNodesOrEmpty().add(modelNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    datasetNode.setParentNodeId("dv-model");
    datasetNode.setProperty("datasetNamespace", "Vault");
    datasetNode.setProperty("datasetName", "hub_customer");
    datasetNode.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");
    document.getNodesOrEmpty().add(datasetNode);

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            datasetNode, document, variables, metadataProvider));
  }

  @Test
  void canPreviewLegacyTargetDatasetWithoutCatalogConnectionProperty() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setId("dv-model");
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    document.getNodesOrEmpty().add(modelNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    datasetNode.setParentNodeId("dv-model");
    datasetNode.setProperty("datasetNamespace", "Vault");
    datasetNode.setProperty("datasetName", "hub_customer");
    document.getNodesOrEmpty().add(datasetNode);

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            datasetNode, document, variables, metadataProvider));
  }

  @Test
  void canPreviewDimensionalTargetDatasetFromCatalogRecordDefinition() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setId("dm-model");
    modelNode.setNodeType(ExecutionMapNodeType.DIMENSIONAL_MODEL);
    modelNode.setName("retail-conformed-dims");
    document.getNodesOrEmpty().add(modelNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    datasetNode.setParentNodeId("dm-model");
    datasetNode.setProperty("datasetNamespace", "Vault");
    datasetNode.setProperty("datasetName", "d_customer");
    datasetNode.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");
    document.getNodesOrEmpty().add(datasetNode);

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            datasetNode, document, variables, metadataProvider));
  }

  @Test
  void canPreviewTargetDatasetFromAlternateCatalogConnection() throws HopException {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    DataCatalogMeta sourceCatalog = new DataCatalogMeta();
    sourceCatalog.setName("local-catalog");
    sourceCatalog.setEnabled(true);
    FileDataCatalog sourceFileCatalog = new FileDataCatalog();
    sourceFileCatalog.setStorageDirectory(
        Path.of("integration-tests/catalog-data").toAbsolutePath().toString().replace('\\', '/'));
    sourceCatalog.setCatalog(sourceFileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(sourceCatalog);

    DataCatalogMeta vaultCatalog = new DataCatalogMeta();
    vaultCatalog.setName("vault-catalog");
    vaultCatalog.setEnabled(true);
    FileDataCatalog vaultFileCatalog = new FileDataCatalog();
    // Alternate catalog that does contain retail model targets (local-catalog is
    // integration-tests).
    vaultFileCatalog.setStorageDirectory(RetailExampleCatalogFixtures.unitCatalogStorageRootPath());
    vaultCatalog.setCatalog(vaultFileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(vaultCatalog);
    RecordDefinitionRegistry.getInstance().invalidate();

    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "hop/retail-example/models/retail-360");
    node.setProperty("datasetName", "hub_customer");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            node, new ExecutionMapDocument(), variables, metadataProvider));
  }

  @Test
  void canPreviewBusinessVaultTargetDatasetFromCatalogRecordDefinition() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setId("bv-model");
    modelNode.setNodeType(ExecutionMapNodeType.BUSINESS_VAULT_MODEL);
    modelNode.setName("retail-360");
    document.getNodesOrEmpty().add(modelNode);

    ExecutionMapNode datasetNode = new ExecutionMapNode();
    datasetNode.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    datasetNode.setParentNodeId("bv-model");
    datasetNode.setProperty("datasetNamespace", "Vault");
    datasetNode.setProperty("datasetName", "customer_360_bv");
    datasetNode.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");
    document.getNodesOrEmpty().add(datasetNode);

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            datasetNode, document, variables, metadataProvider));
  }

  @Test
  void buildTooltipIncludesPreviewHintForTargetDataset() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    node.setProperty("datasetNamespace", "hop/retail-example/models/retail-360");
    node.setProperty("datasetName", "hub_customer");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    String tooltip =
        ExecutionMapNavigationSupport.buildTooltip(
            node, new ExecutionMapDocument(), variables, metadataProvider);

    assertNotNull(tooltip);
    assertTrue(tooltip.contains("Preview data"));
  }

  @Test
  void canPreviewSourceDatasetFromCatalogRecordDefinition() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));

    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    node.setProperty("datasetNamespace", "hop/retail-example/sources");
    node.setProperty("datasetName", "E2E-customer-hub");
    node.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");

    assertTrue(
        ExecutionMapNavigationSupport.canPreviewDataset(
            node, new ExecutionMapDocument(), variables, metadataProvider));
  }
}
