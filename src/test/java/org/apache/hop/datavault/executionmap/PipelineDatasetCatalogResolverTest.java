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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineDatasetCatalogResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void reusesModelDatasetNodesForGeneratedPipelineTransforms() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, variables, null, CrawlOptions.builder().build());

    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    context.addNode(modelNode);

    ExecutionMapNode existingTarget = new ExecutionMapNode();
    existingTarget.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    existingTarget.setName("hop/retail-example/models/retail-360::hub_customer");
    existingTarget.setPath("dataset://hop/retail-example/models/retail-360::hub_customer");
    existingTarget.setParentNodeId(modelNode.getId());
    existingTarget.setProperty("datasetKind", "DV_HUB");
    existingTarget.setProperty("datasetNamespace", "hop/retail-example/models/retail-360");
    existingTarget.setProperty("datasetName", "hub_customer");
    existingTarget.setProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION, "local-catalog");
    context.addNode(existingTarget);

    ExecutionMapNode pipelineNode = new ExecutionMapNode();
    pipelineNode.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    pipelineNode.setName("hub-hub_customer-E2E-customer-hub");
    pipelineNode.setParentNodeId(modelNode.getId());
    context.addNode(pipelineNode);

    PipelineMeta pipelineMeta = new PipelineMeta();
    TableInputMeta targetInput = new TableInputMeta();
    targetInput.setConnection("Vault");
    targetInput.setSql("SELECT hk_customer FROM hub_customer");
    TransformMeta targetTransform = new TransformMeta("target_hub_customer", targetInput);
    pipelineMeta.addTransform(targetTransform);

    TableOutputMeta tableOutput = new TableOutputMeta();
    tableOutput.setConnection("Vault");
    tableOutput.setTableName("hub_customer");
    TransformMeta outputTransform = new TransformMeta("write_to_hub_customer", tableOutput);
    pipelineMeta.addTransform(outputTransform);

    PipelineDatasetResolver.resolvePipeline(context, pipelineNode.getId(), pipelineMeta);

    long vaultBackedNodes =
        document.getNodesOrEmpty().stream()
            .filter(
                node ->
                    node != null
                        && (node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET
                            || node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
                        && "Vault".equals(node.getProperty("datasetNamespace")))
            .count();
    assertEquals(
        0, vaultBackedNodes, "Pipeline crawl should not create connection-backed datasets");

    assertTrue(
        document.getNodesOrEmpty().stream()
            .anyMatch(
                node ->
                    node != null
                        && node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET
                        && "hop/retail-example/models/retail-360::hub_customer"
                            .equals(node.getName())));
  }
}
