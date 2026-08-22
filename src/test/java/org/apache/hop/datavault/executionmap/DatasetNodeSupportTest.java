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
package org.apache.hop.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class DatasetNodeSupportTest {

  @Test
  void deduplicatesDatasetNodesByPath() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, new Variables(), null, CrawlOptions.builder().build());

    String first =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.SOURCE_DATASET,
            "CRM",
            "customer",
            "DATABASE",
            "parent-1");
    String second =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.SOURCE_DATASET,
            "CRM",
            "customer",
            "DATABASE",
            "parent-2");

    assertEquals(first, second);
    assertEquals(1, document.getNodesOrEmpty().size());
    assertEquals("dataset://CRM::customer", document.getNodesOrEmpty().get(0).getPath());
  }

  @Test
  void storesCatalogConnectionOnDatasetNode() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, new Variables(), null, CrawlOptions.builder().build());

    String nodeId =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.TARGET_DATASET,
            "Vault",
            "d_customer",
            "DATABASE",
            "parent-1",
            "local-catalog");

    assertEquals(
        "local-catalog",
        document.findNodeById(nodeId).getProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION));
  }

  @Test
  void resolvesOriginModelFromGeneratedPipelineParentChain() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, new Variables(), null, CrawlOptions.builder().build());

    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    context.addNode(modelNode);

    ExecutionMapNode pipelineNode = new ExecutionMapNode();
    pipelineNode.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    pipelineNode.setName("hub-hub_customer");
    pipelineNode.setParentNodeId(modelNode.getId());
    context.addNode(pipelineNode);

    assertEquals(
        modelNode.getId(),
        DatasetNodeSupport.resolveOriginModelNodeId(context, pipelineNode.getId()));
  }

  @Test
  void backfillsCatalogConnectionOnDeduplicatedDatasetNode() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, new Variables(), null, CrawlOptions.builder().build());

    String first =
        DatasetNodeSupport.getOrCreateDatasetNode(
            context,
            ExecutionMapNodeType.TARGET_DATASET,
            "Vault",
            "d_customer",
            "DATABASE",
            "parent-1");
    DatasetNodeSupport.getOrCreateDatasetNode(
        context,
        ExecutionMapNodeType.TARGET_DATASET,
        "Vault",
        "d_customer",
        "DATABASE",
        "parent-2",
        "local-catalog");

    assertEquals(first, document.getNodesOrEmpty().get(0).getId());
    assertEquals(
        "local-catalog",
        document.findNodeById(first).getProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION));
  }

  @Test
  void buildsQualifiedLabelsAndPaths() {
    assertEquals("dataset://default::orders", DatasetNodeSupport.datasetPath(null, "orders"));
    assertEquals("Vault::d_customer", DatasetNodeSupport.qualifiedLabel("Vault", "d_customer"));
    Variables variables = new Variables();
    variables.setVariable("DB", "Warehouse");
    assertEquals("Warehouse", DatasetNodeSupport.resolveValue(variables, "${DB}"));
  }
}
