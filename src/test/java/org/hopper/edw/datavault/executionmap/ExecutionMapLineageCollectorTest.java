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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapLineageCollectorTest {

  @Test
  void collectBuildsJobsFromReadsAndWritesEdges() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setHopProject("retail-example");

    ExecutionMapNode source = datasetNode("src-1", "CRM", "customer");
    ExecutionMapNode target = datasetNode("tgt-1", "Vault", "hub_customer");
    ExecutionMapNode pipeline = jobNode("job-1", "hub_customer-CRM-customer", "generated://hub");

    document.getNodesOrEmpty().add(source);
    document.getNodesOrEmpty().add(target);
    document.getNodesOrEmpty().add(pipeline);
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.READS_FROM, source, pipeline));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.WRITES_TO, pipeline, target));

    var edges = ExecutionMapLineageCollector.collect(document);

    assertEquals(1, edges.size());
    ExecutionMapLineageEdge lineageEdge = edges.get(0);
    assertEquals("hop-data-vault/retail-example", lineageEdge.getJobNamespace());
    assertEquals("hub_customer-CRM-customer", lineageEdge.getJobName());
    assertEquals(1, lineageEdge.getInputs().size());
    assertEquals(1, lineageEdge.getOutputs().size());
    assertEquals("CRM", lineageEdge.getInputs().get(0).getNamespace());
    assertEquals("customer", lineageEdge.getInputs().get(0).getName());
    assertEquals("Vault", lineageEdge.getOutputs().get(0).getNamespace());
    assertEquals("hub_customer", lineageEdge.getOutputs().get(0).getName());
  }

  @Test
  void collectIgnoresJobsWithoutDatasetEdges() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.getNodesOrEmpty().add(jobNode("job-1", "orphan", "/tmp/orphan.hpl"));
    assertTrue(ExecutionMapLineageCollector.collect(document).isEmpty());
  }

  @Test
  void openLineageJsonContainsRunEvents() throws Exception {
    ExecutionMapDocument document = new ExecutionMapDocument();
    ExecutionMapNode source = datasetNode("src-1", "CRM", "customer");
    ExecutionMapNode target = datasetNode("tgt-1", "Vault", "hub_customer");
    ExecutionMapNode pipeline = jobNode("job-1", "hub_customer-CRM-customer", "generated://hub");
    document.getNodesOrEmpty().add(source);
    document.getNodesOrEmpty().add(target);
    document.getNodesOrEmpty().add(pipeline);
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.READS_FROM, source, pipeline));
    document.getEdgesOrEmpty().add(edge(ExecutionMapEdgeType.WRITES_TO, pipeline, target));

    String json = OpenLineageExportSupport.toJson(document);
    assertFalse(json.isBlank());
    assertTrue(json.contains("\"eventType\" : \"COMPLETE\""));
    assertTrue(json.contains("\"hub_customer-CRM-customer\""));
    assertTrue(json.contains("\"customer\""));
    assertTrue(json.contains("\"hub_customer\""));
  }

  private static ExecutionMapNode datasetNode(String id, String namespace, String name) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setId(id);
    node.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    node.setName(namespace + "." + name);
    node.setPath(DatasetNodeSupport.datasetPath(namespace, name));
    node.setProperty("datasetNamespace", namespace);
    node.setProperty("datasetName", name);
    node.setProperty("datasetKind", "DATABASE");
    return node;
  }

  private static ExecutionMapNode jobNode(String id, String name, String path) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setId(id);
    node.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    node.setName(name);
    node.setPath(path);
    return node;
  }

  private static ExecutionMapEdge edge(
      ExecutionMapEdgeType type, ExecutionMapNode from, ExecutionMapNode to) {
    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setEdgeType(type);
    edge.setFromNodeId(from.getId());
    edge.setToNodeId(to.getId());
    return edge;
  }
}
