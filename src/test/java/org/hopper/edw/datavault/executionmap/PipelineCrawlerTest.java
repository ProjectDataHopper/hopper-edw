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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineCrawlerTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void crawlPipelineWithoutTransformsSkipsTransformNodesAndHops() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(
            document,
            new Variables(),
            null,
            CrawlOptions.builder().includePipelineTransforms(false).build());

    PipelineMeta pipelineMeta = buildSamplePipeline();

    PipelineCrawler.crawlPipeline(context, pipelineMeta.getFilename(), pipelineMeta, true, null);

    assertContainsNodeType(document, ExecutionMapNodeType.ROOT_PIPELINE);
    assertFalse(containsTransformLayerNode(document));
    assertFalse(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.HOP));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO));
  }

  @Test
  void crawlPipelineCreatesRootTransformNodesAndHops() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapContext context =
        new ExecutionMapContext(
            document,
            new Variables(),
            null,
            CrawlOptions.builder().includePipelineTransforms(true).build());

    PipelineMeta pipelineMeta = buildSamplePipeline();

    PipelineCrawler.crawlPipeline(context, pipelineMeta.getFilename(), pipelineMeta, true, null);

    assertContainsNodeType(document, ExecutionMapNodeType.ROOT_PIPELINE);
    assertContainsNodeType(document, ExecutionMapNodeType.PIPELINE_TRANSFORM);
    assertFalse(document.getEdgesOrEmpty().isEmpty());
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.HOP));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM));
    assertTrue(
        document.getEdgesOrEmpty().stream()
            .anyMatch(edge -> edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO));
  }

  private static PipelineMeta buildSamplePipeline() {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("load-customer");
    pipelineMeta.setFilename("/tmp/load-customer.hpl");

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection("CRM");
    tableInputMeta.setSql("SELECT * FROM customer");
    TransformMeta inputTransform = new TransformMeta("source customer", tableInputMeta);
    pipelineMeta.addTransform(inputTransform);

    TableOutputMeta tableOutputMeta = new TableOutputMeta();
    tableOutputMeta.setConnection("Vault");
    tableOutputMeta.setTableName("hub_customer");
    TransformMeta outputTransform = new TransformMeta("target hub_customer", tableOutputMeta);
    pipelineMeta.addTransform(outputTransform);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(inputTransform, outputTransform));
    return pipelineMeta;
  }

  private static boolean containsTransformLayerNode(ExecutionMapDocument document) {
    return document.getNodesOrEmpty().stream()
        .anyMatch(
            node ->
                node != null
                    && ExecutionMapContext.isPipelineTransformLayerNodeType(node.getNodeType()));
  }

  private static void assertContainsNodeType(
      ExecutionMapDocument document, ExecutionMapNodeType nodeType) {
    boolean found =
        document.getNodesOrEmpty().stream()
            .anyMatch(node -> node != null && node.getNodeType() == nodeType);
    assertTrue(found, () -> "Expected node type " + nodeType + " in execution map");
  }
}
