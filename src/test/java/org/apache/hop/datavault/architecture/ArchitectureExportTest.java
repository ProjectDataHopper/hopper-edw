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
package org.apache.hop.datavault.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.lineage.TableSourceKind;
import org.apache.hop.datavault.lineage.TableSourceRef;
import org.apache.hop.datavault.lineage.TableSourceRole;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ArchitectureExportTest {

  @Test
  void solutionGraphIsArchitectureOnlyWithoutDatasets() {
    ExecutionMapDocument map = new ExecutionMapDocument();
    map.setName("run-retail-update");
    map.setRootArtifactPath("${PROJECT_HOME}/workflows/run-retail-update.hwf");

    ExecutionMapNode root = new ExecutionMapNode();
    root.setId("wf-root");
    root.setName("run-retail-update");
    root.setNodeType(ExecutionMapNodeType.ROOT_WORKFLOW);
    root.setPath("${PROJECT_HOME}/workflows/run-retail-update.hwf");
    map.getNodesOrEmpty().add(root);

    ExecutionMapNode validate = new ExecutionMapNode();
    validate.setId("act-validate");
    validate.setName("Validate sources");
    validate.setNodeType(ExecutionMapNodeType.WORKFLOW_ACTION);
    validate.setPluginId("VALIDATE_RESOURCE_DEFINITIONS");
    map.getNodesOrEmpty().add(validate);

    ExecutionMapNode dvUpdate = new ExecutionMapNode();
    dvUpdate.setId("act-dv");
    dvUpdate.setName("Update DV");
    dvUpdate.setNodeType(ExecutionMapNodeType.DV_UPDATE);
    map.getNodesOrEmpty().add(dvUpdate);

    ExecutionMapNode model = new ExecutionMapNode();
    model.setId("model-dv");
    model.setName("/home/matt/somewhere/retail-360.hdv");
    model.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    model.setPath("${PROJECT_HOME}/models/retail-360.hdv");
    map.getNodesOrEmpty().add(model);

    // Table grain — must not appear on architecture overview
    ExecutionMapNode hub = new ExecutionMapNode();
    hub.setId("ds-hub");
    hub.setName("hub_customer");
    hub.setNodeType(ExecutionMapNodeType.TARGET_DATASET);
    hub.setPath("dataset://vault::hub_customer");
    map.getNodesOrEmpty().add(hub);

    ExecutionMapNode source = new ExecutionMapNode();
    source.setId("ds-src");
    source.setName("E2E-customer");
    source.setNodeType(ExecutionMapNodeType.SOURCE_DATASET);
    map.getNodesOrEmpty().add(source);

    ExecutionMapNode genPipe = new ExecutionMapNode();
    genPipe.setId("gen-pipe");
    genPipe.setName("generated-hub-load");
    genPipe.setNodeType(ExecutionMapNodeType.GENERATED_PIPELINE);
    map.getNodesOrEmpty().add(genPipe);

    ExecutionMapEdge e1 = new ExecutionMapEdge();
    e1.setFromNodeId("wf-root");
    e1.setToNodeId("act-validate");
    e1.setEdgeType(ExecutionMapEdgeType.HOP);
    map.getEdgesOrEmpty().add(e1);

    ExecutionMapEdge e2 = new ExecutionMapEdge();
    e2.setFromNodeId("act-validate");
    e2.setToNodeId("act-dv");
    e2.setEdgeType(ExecutionMapEdgeType.HOP);
    map.getEdgesOrEmpty().add(e2);

    ExecutionMapEdge e3 = new ExecutionMapEdge();
    e3.setFromNodeId("act-dv");
    e3.setToNodeId("model-dv");
    e3.setEdgeType(ExecutionMapEdgeType.REFERENCES);
    e3.setLabel("/home/matt/somewhere/models/retail-360.hdv");
    map.getEdgesOrEmpty().add(e3);

    ExecutionMapEdge e4 = new ExecutionMapEdge();
    e4.setFromNodeId("model-dv");
    e4.setToNodeId("ds-hub");
    e4.setEdgeType(ExecutionMapEdgeType.CONTAINS);
    e4.setLabel("hub_customer");
    map.getEdgesOrEmpty().add(e4);

    ArchitectureGraph graph = ArchitectureGraphFromExecutionMap.build(map);
    org.apache.hop.core.variables.Variables variables =
        new org.apache.hop.core.variables.Variables();
    variables.setVariable("PROJECT_HOME", "/data/retail-example");
    ArchitecturePathSupport.portableizeGraph(graph, variables);

    assertEquals(ArchitectureViewType.SOLUTION, graph.getViewType());
    assertEquals(4, graph.nodeCount(), "only workflow, actions, model — no datasets/pipelines");
    assertNull(graph.findNode("ds-hub"));
    assertNull(graph.findNode("ds-src"));
    assertNull(graph.findNode("gen-pipe"));

    ArchitectureNode control = graph.findNode("act-validate");
    assertEquals(ArchitectureLayer.CONTROL, control.getLayer());
    ArchitectureNode dv = graph.findNode("act-dv");
    assertEquals(ArchitectureLayer.DATA_VAULT, dv.getLayer());
    assertEquals(ArchitectureNodeKind.CAPABILITY, dv.getKind());

    ArchitectureNode modelNode = graph.findNode("model-dv");
    assertEquals(ArchitectureNodeKind.MODEL, modelNode.getKind());
    assertEquals("retail-360.hdv", modelNode.getName());
    assertEquals("models/retail-360.hdv", modelNode.getPath());

    boolean modelEdge = false;
    for (ArchitectureEdge edge : graph.getEdges()) {
      assertFalse(
          edge.getLabel() != null && edge.getLabel().startsWith("/home/"),
          "no absolute edge labels: " + edge.getLabel());
      if ("model-dv".equals(edge.getToNodeId()) || "model-dv".equals(edge.getFromNodeId())) {
        modelEdge = true;
        assertTrue(
            edge.getLabel() == null
                || edge.getLabel().contains("retail-360")
                || edge.getLabel().contains("models/"),
            "model edge should reference the model: " + edge.getLabel());
      }
    }
    assertTrue(modelEdge);

    String drawio = DrawioArchitectureExporter.export(graph);
    assertTrue(drawio.contains("<mxfile"));
    assertTrue(drawio.contains("Data Vault") || drawio.contains("retail-360.hdv"));
    assertTrue(drawio.contains("Validate sources") || drawio.contains("act-validate"));
    assertFalse(drawio.contains("hub_customer"));
    assertFalse(drawio.contains("/home/matt/"));
  }

  @Test
  void dataInventoryFromLineageSnapshotsHasNoRelationshipEdges() {
    LineageSnapshot snap = new LineageSnapshot();
    snap.setModelName("retail-360");
    snap.setModelFilename("models/retail-360.hdv");
    snap.setModelLayer(LineageLayer.DV);

    TableLineage hub = new TableLineage();
    hub.setLogicalName("hub_customer");
    hub.setPhysicalTableName("hub_customer");
    hub.setTableType("HUB");
    hub.setLayer(LineageLayer.DV);
    hub.setModelName("retail-360");
    hub.addSource(
        new TableSourceRef(
            TableSourceKind.DV_SOURCE, "E2E-customer-hub", TableSourceRole.RECORD_SOURCE));
    snap.addTable(hub);

    TableLineage sat = new TableLineage();
    sat.setLogicalName("sat_customer_demo");
    sat.setPhysicalTableName("sat_customer_demo");
    sat.setTableType("SATELLITE");
    sat.setLayer(LineageLayer.DV);
    sat.setModelName("retail-360");
    sat.addSource(
        new TableSourceRef(TableSourceKind.DV_TABLE, "hub_customer", TableSourceRole.PARENT_HUB));
    snap.addTable(sat);

    ArchitectureGraph graph = ArchitectureGraphFromLineage.build(List.of(snap));
    assertEquals(ArchitectureViewType.DATA, graph.getViewType());
    assertEquals("data-inventory", graph.getName());
    assertTrue(graph.isOmitEdges());
    assertTrue(graph.nodeCount() >= 3);
    assertTrue(graph.getEdges().isEmpty(), "inventory must not include ER relationship edges");

    String drawio = DrawioArchitectureExporter.export(graph);
    assertTrue(drawio.contains("hub_customer"));
    assertTrue(drawio.contains("sat_customer_demo"));
    assertTrue(drawio.contains("E2E-customer-hub"));
    assertTrue(drawio.contains("mxCell"));
    assertFalse(
        drawio.contains(" edge=\"1\""), "inventory drawio must not emit relationship edges");
  }

  @Test
  void aggregatedModelGraphUnionsMultipleDvFilesWithElk() throws Exception {
    org.apache.hop.core.HopEnvironment.init();
    org.apache.hop.core.variables.Variables variables =
        new org.apache.hop.core.variables.Variables();

    // File 1: customer hub + sat
    org.apache.hop.datavault.metadata.DataVaultModel core =
        new org.apache.hop.datavault.metadata.DataVaultModel();
    core.setName("core");
    org.apache.hop.datavault.metadata.DvHub hubCustomer =
        new org.apache.hop.datavault.metadata.DvHub("hub_customer");
    org.apache.hop.datavault.metadata.DvSatellite satCustomer =
        new org.apache.hop.datavault.metadata.DvSatellite("sat_customer");
    satCustomer.setHubName("hub_customer");
    core.getTables().add(hubCustomer);
    core.getTables().add(satCustomer);

    // File 2: product hub + shared customer hub name (dedupe) + link
    org.apache.hop.datavault.metadata.DataVaultModel sales =
        new org.apache.hop.datavault.metadata.DataVaultModel();
    sales.setName("sales");
    org.apache.hop.datavault.metadata.DvHub hubCustomerAgain =
        new org.apache.hop.datavault.metadata.DvHub("hub_customer");
    org.apache.hop.datavault.metadata.DvHub hubProduct =
        new org.apache.hop.datavault.metadata.DvHub("hub_product");
    org.apache.hop.datavault.metadata.DvLink link =
        new org.apache.hop.datavault.metadata.DvLink("lnk_customer_product");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_product");
    sales.getTables().add(hubCustomerAgain);
    sales.getTables().add(hubProduct);
    sales.getTables().add(link);

    ArchitectureGraph graph =
        ArchitectureGraphFromModel.fromDataVaultModels(List.of(core, sales), variables);

    assertEquals(ArchitectureViewType.MODEL, graph.getViewType());
    assertTrue(graph.isFreeformLayout());
    // 3 unique tables: hub_customer (once), sat_customer, hub_product, lnk → 4
    assertEquals(4, graph.nodeCount());
    assertNotNull(graph.findNode("table:hub_customer"));
    assertNotNull(graph.findNode("table:hub_product"));
    assertNotNull(graph.findNode("table:lnk_customer_product"));
    assertTrue(graph.edgeCount() >= 2);

    for (ArchitectureNode node : graph.getNodes()) {
      assertTrue(node.hasLayoutCoordinates(), "node " + node.getId() + " needs ELK coordinates");
    }

    String drawio = DrawioArchitectureExporter.export(graph);
    assertTrue(drawio.contains("hub_customer"));
    assertTrue(drawio.contains("hub_product"));
    assertTrue(drawio.contains("lnk_customer_product"));
    assertTrue(drawio.contains(" edge=\"1\""));
    long yCount =
        java.util.regex.Pattern.compile("y=\"(\\d+)\"")
            .matcher(drawio)
            .results()
            .map(m -> m.group(1))
            .distinct()
            .count();
    assertTrue(yCount >= 2, "aggregated freeform drawio should use more than one y coordinate");
  }

  @Test
  void swimlanePacksMultipleRows() {
    List<ArchitectureNode> nodes = new java.util.ArrayList<>();
    for (int i = 0; i < 20; i++) {
      nodes.add(new ArchitectureNode("n" + i, "Node " + i, ArchitectureNodeKind.TABLE));
    }
    List<List<ArchitectureNode>> rows = DrawioArchitectureExporter.packRows(nodes, 1400);
    assertTrue(rows.size() >= 2, "20 nodes should wrap into multiple rows");
  }

  @Test
  void modelPathsFromExecutionMap() {
    ExecutionMapDocument map = new ExecutionMapDocument();
    ExecutionMapNode model = new ExecutionMapNode();
    model.setId("m1");
    model.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    model.setPath("${PROJECT_HOME}/models/retail-360.hdv");
    map.getNodesOrEmpty().add(model);

    List<String> paths = ArchitectureExportService.modelPathsFromExecutionMap(map);
    assertEquals(1, paths.size());
    assertTrue(paths.get(0).contains("retail-360.hdv"));
  }

  @Test
  void resolveProjectRelativePathForWrite() throws Exception {
    org.apache.hop.core.variables.Variables variables =
        new org.apache.hop.core.variables.Variables();
    variables.setVariable("PROJECT_HOME", "/data/retail-example");

    String out =
        ArchitectureExportService.resolveProjectRelativePath(
            "work/architecture/out.drawio", variables, true);
    assertEquals("/data/retail-example/work/architecture/out.drawio", out);

    String abs =
        ArchitectureExportService.resolveProjectRelativePath(
            "/tmp/absolute.drawio", variables, true);
    assertEquals("/tmp/absolute.drawio", abs);
  }

  @Test
  void projectRelativePathsInDrawioExport() {
    org.apache.hop.core.variables.Variables variables =
        new org.apache.hop.core.variables.Variables();
    variables.setVariable(
        "PROJECT_HOME", "/home/matt/git/mattcasters/hop-data-vault/retail-example");

    assertEquals(
        "models/retail-360.hdv",
        ArchitecturePathSupport.toProjectRelativePath(
            "${PROJECT_HOME}/models/retail-360.hdv", variables));
    assertEquals(
        "models/retail-360.hdv",
        ArchitecturePathSupport.toProjectRelativePath(
            "/home/matt/git/mattcasters/hop-data-vault/retail-example/models/retail-360.hdv",
            variables));
    assertEquals(
        "pipelines/generate-date-dimension-data.hpl",
        ArchitecturePathSupport.toProjectRelativePath(
            "/home/matt/git/mattcasters/hop-data-vault/retail-example/pipelines/generate-date-dimension-data.hpl",
            variables));
    // Free-text edge labels stay unchanged
    assertEquals(
        "Open the referenced data vault model",
        ArchitecturePathSupport.toProjectRelativePath(
            "Open the referenced data vault model", variables));

    ExecutionMapDocument map = new ExecutionMapDocument();
    map.setName("run-retail-update");
    map.setRootArtifactPath("${PROJECT_HOME}/workflows/run-retail-update.hwf");

    ExecutionMapNode model = new ExecutionMapNode();
    model.setId("model-dv");
    model.setName("retail-360");
    model.setNodeType(ExecutionMapNodeType.DATA_VAULT_MODEL);
    model.setPath("${PROJECT_HOME}/models/retail-360.hdv");
    map.getNodesOrEmpty().add(model);

    ExecutionMapNode bv = new ExecutionMapNode();
    bv.setId("model-bv");
    bv.setName("retail-360");
    bv.setNodeType(ExecutionMapNodeType.BUSINESS_VAULT_MODEL);
    bv.setPath("${PROJECT_HOME}/models/retail-360.hbv");
    map.getNodesOrEmpty().add(bv);

    ExecutionMapEdge link = new ExecutionMapEdge();
    link.setFromNodeId("model-bv");
    link.setToNodeId("model-dv");
    link.setEdgeType(ExecutionMapEdgeType.MODEL_LINK);
    link.setLabel("/home/matt/git/mattcasters/hop-data-vault/retail-example/models/retail-360.hdv");
    map.getEdgesOrEmpty().add(link);

    ArchitectureGraph graph = ArchitectureGraphFromExecutionMap.build(map);
    ArchitecturePathSupport.portableizeGraph(graph, variables);

    ArchitectureNode modelNode = graph.findNode("model-dv");
    assertEquals("models/retail-360.hdv", modelNode.getPath());

    boolean foundRelativeLabel = false;
    for (ArchitectureEdge edge : graph.getEdges()) {
      if (edge.getLabel() != null && edge.getLabel().contains("retail-360.hdv")) {
        assertEquals("models/retail-360.hdv", edge.getLabel());
        foundRelativeLabel = true;
      }
      assertFalse(
          edge.getLabel() != null && edge.getLabel().startsWith("/home/"),
          "edge label must not be absolute: " + edge.getLabel());
    }
    assertTrue(foundRelativeLabel);

    String drawio = DrawioArchitectureExporter.export(graph);
    assertTrue(drawio.contains("models/retail-360.hdv"));
    assertFalse(drawio.contains("/home/matt/"));
  }
}
