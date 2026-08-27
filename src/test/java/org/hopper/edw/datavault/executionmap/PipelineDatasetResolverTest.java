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

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineDatasetResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesTableInputAndOutputTransforms() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(document, variables, null, CrawlOptions.builder().build());

    ExecutionMapNode modelNode = new ExecutionMapNode();
    modelNode.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    modelNode.setName("retail-360");
    context.addNode(modelNode);

    ExecutionMapNode pipelineNode = new ExecutionMapNode();
    pipelineNode.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    pipelineNode.setName("hub-hub_customer-E2E-customer-hub");
    pipelineNode.setParentNodeId(modelNode.getId());
    context.addNode(pipelineNode);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("load");

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection("CRM");
    tableInputMeta.setSql("SELECT * FROM public.customer");
    TransformMeta inputTransform = new TransformMeta("source customer", tableInputMeta);
    inputTransform.setLocation(100, 100);
    pipelineMeta.addTransform(inputTransform);

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection("Vault");
    tableOutputMeta.setTableName("h_customer");
    TransformMeta outputTransform = new TransformMeta("target h_customer", tableOutputMeta);
    outputTransform.setLocation(300, 100);
    pipelineMeta.addTransform(outputTransform);

    PipelineDatasetResolver.resolvePipeline(context, pipelineNode.getId(), pipelineMeta);

    long sourceDatasets =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET)
            .count();
    long targetDatasets =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
            .count();
    assertEquals(1, sourceDatasets);
    assertEquals(1, targetDatasets);

    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM
                        && pipelineNode.getId().equals(edge.getToNodeId())));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO
                        && pipelineNode.getId().equals(edge.getFromNodeId())));

    ExecutionMapNode sourceDataset =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET)
            .findFirst()
            .orElseThrow();
    assertEquals("dataset://hop/retail-example/sources::E2E-customer-hub", sourceDataset.getPath());
    assertEquals("DV_SOURCE", sourceDataset.getProperty("datasetKind"));
    assertEquals("hop/retail-example/sources", sourceDataset.getProperty("datasetNamespace"));
    assertEquals(modelNode.getId(), sourceDataset.getParentNodeId());

    ExecutionMapNode targetDataset =
        document.getNodesOrEmpty().stream()
            .filter(node -> node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
            .findFirst()
            .orElseThrow();
    assertEquals(
        "dataset://hop/retail-example/models/retail-360::h_customer", targetDataset.getPath());
    assertEquals(modelNode.getId(), targetDataset.getParentNodeId());

    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS
                        && modelNode.getId().equals(edge.getFromNodeId())
                        && sourceDataset.getId().equals(edge.getToNodeId())));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(
                edge ->
                    edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS
                        && modelNode.getId().equals(edge.getFromNodeId())
                        && targetDataset.getId().equals(edge.getToNodeId())));
  }
}
