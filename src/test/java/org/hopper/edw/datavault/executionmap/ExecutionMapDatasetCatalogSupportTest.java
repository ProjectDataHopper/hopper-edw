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
package org.hopper.edw.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapDatasetCatalogSupportTest {

  @Test
  void parsesCatalogStyleDatasetPath() {
    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.parseDatasetPath(
            "dataset://hop/retail-example/dimensional/retail-conformed-dims::d_warehouse");

    assertEquals(
        new RecordDefinitionKey(
            "hop/retail-example/dimensional/retail-conformed-dims", "d_warehouse"),
        key);
  }

  @Test
  void parsesLegacyDatasetPath() {
    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.parseDatasetPath("dataset://Vault/d_warehouse");

    assertEquals(new RecordDefinitionKey("Vault", "d_warehouse"), key);
  }

  @Test
  void infersSourcesNamespaceFromParentDataVaultModel() {
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
    document.getNodesOrEmpty().add(datasetNode);

    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(datasetNode, document, variables);

    assertEquals(new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub"), key);
    assertTrue(ExecutionMapDatasetCatalogSupport.isCatalogNamespace(key.getNamespace()));
  }

  @Test
  void infersModelNamespaceForTargetDatasetUnderDataVaultModel() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

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

    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(datasetNode, document, variables);

    assertEquals(
        new RecordDefinitionKey("hop/retail-example/models/retail-360", "hub_customer"), key);
  }

  @Test
  void infersCatalogNamespaceFromParentDimensionalModel() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

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
    datasetNode.setProperty("datasetName", "d_warehouse");
    document.getNodesOrEmpty().add(datasetNode);

    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(datasetNode, document, variables);

    assertEquals(
        new RecordDefinitionKey(
            "hop/retail-example/dimensional/retail-conformed-dims", "d_warehouse"),
        key);
    assertTrue(ExecutionMapDatasetCatalogSupport.isCatalogNamespace(key.getNamespace()));
  }
}
