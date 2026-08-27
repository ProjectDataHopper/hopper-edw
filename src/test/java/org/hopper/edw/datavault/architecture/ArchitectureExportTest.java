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
package org.hopper.edw.datavault.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.xp.RegisterResourceDefinitionGroupMetadataExtensionPoint;
import org.hopper.edw.datavault.lineage.LineageLayer;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.lineage.TableLineage;
import org.hopper.edw.datavault.lineage.TableSourceKind;
import org.hopper.edw.datavault.lineage.TableSourceRef;
import org.hopper.edw.datavault.lineage.TableSourceRole;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
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
    org.hopper.edw.datavault.metadata.DataVaultModel core =
        new org.hopper.edw.datavault.metadata.DataVaultModel();
    core.setName("core");
    org.hopper.edw.datavault.metadata.DvHub hubCustomer =
        new org.hopper.edw.datavault.metadata.DvHub("hub_customer");
    org.hopper.edw.datavault.metadata.DvSatellite satCustomer =
        new org.hopper.edw.datavault.metadata.DvSatellite("sat_customer");
    satCustomer.setHubName("hub_customer");
    core.getTables().add(hubCustomer);
    core.getTables().add(satCustomer);

    // File 2: product hub + shared customer hub name (dedupe) + link
    org.hopper.edw.datavault.metadata.DataVaultModel sales =
        new org.hopper.edw.datavault.metadata.DataVaultModel();
    sales.setName("sales");
    org.hopper.edw.datavault.metadata.DvHub hubCustomerAgain =
        new org.hopper.edw.datavault.metadata.DvHub("hub_customer");
    org.hopper.edw.datavault.metadata.DvHub hubProduct =
        new org.hopper.edw.datavault.metadata.DvHub("hub_product");
    org.hopper.edw.datavault.metadata.DvLink link =
        new org.hopper.edw.datavault.metadata.DvLink("lnk_customer_product");
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
        "PROJECT_HOME", "/home/matt/git/ProjectDataHopper/hopper-edw/retail-example");

    assertEquals(
        "models/retail-360.hdv",
        ArchitecturePathSupport.toProjectRelativePath(
            "${PROJECT_HOME}/models/retail-360.hdv", variables));
    assertEquals(
        "models/retail-360.hdv",
        ArchitecturePathSupport.toProjectRelativePath(
            "/home/matt/git/ProjectDataHopper/hopper-edw/retail-example/models/retail-360.hdv",
            variables));
    assertEquals(
        "pipelines/generate-date-dimension-data.hpl",
        ArchitecturePathSupport.toProjectRelativePath(
            "/home/matt/git/ProjectDataHopper/hopper-edw/retail-example/pipelines/generate-date-dimension-data.hpl",
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
    link.setLabel(
        "/home/matt/git/ProjectDataHopper/hopper-edw/retail-example/models/retail-360.hdv");
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

  @Test
  void modelBasenameStripsPathAndExtension() {
    assertEquals("retail-360", ArchitectureExportService.modelBasename("models/retail-360.hdv"));
    assertEquals(
        "retail-360", ArchitectureExportService.modelBasename("models/subdir/retail-360.HBV"));
    assertEquals(
        "retail-f-orders",
        ArchitectureExportService.modelBasename("/data/project/models/retail-f-orders.hdm"));
    assertEquals("plain", ArchitectureExportService.modelBasename("plain"));
    assertEquals("", ArchitectureExportService.modelBasename(null));
    assertEquals("", ArchitectureExportService.modelBasename(""));
  }

  @Test
  void modelPathsFromResourceDefinitionGroupOrdersDvBvDm() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail");
    group.getDimensionalModelFiles().add("models/retail-f-orders.hdm");
    group.getDataVaultModelFiles().add("models/retail-360.hdv");
    group.getBusinessVaultModelFiles().add("models/retail-360.hbv");
    group.getDimensionalModelFiles().add("models/retail-conformed-dims.hdm");

    List<String> paths = ArchitectureExportService.modelPathsFromResourceDefinitionGroup(group);
    assertEquals(
        List.of(
            "models/retail-360.hdv",
            "models/retail-360.hbv",
            "models/retail-f-orders.hdm",
            "models/retail-conformed-dims.hdm"),
        paths);
  }

  @Test
  void resolveModelPathsPrefersGroupOverFallback() throws Exception {
    HopEnvironment.init();
    new RegisterResourceDefinitionGroupMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());

    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("docs");
    group.getDataVaultModelFiles().add("models/from-group.hdv");
    metadata.getSerializer(ResourceDefinitionGroupMeta.class).save(group);

    List<String> paths =
        ArchitectureExportService.resolveModelPaths(
            "docs", List.of("models/from-root.hdv"), metadata);
    assertEquals(List.of("models/from-group.hdv"), paths);

    List<String> fallbackOnly =
        ArchitectureExportService.resolveModelPaths(
            null, List.of("models/from-root.hdv"), metadata);
    assertEquals(List.of("models/from-root.hdv"), fallbackOnly);
  }

  @Test
  void perModelDrawioPathUsesTypeSubfoldersAndBasename() {
    assertEquals(
        "/docs/models/data-vault/retail-360.drawio",
        ArchitectureExportService.perModelDrawioPath(
            "/docs/models", "models/subdir/retail-360.hdv"));
    assertEquals(
        "/docs/models/business-vault/retail-360.drawio",
        ArchitectureExportService.perModelDrawioPath(
            "/docs/models/", "${PROJECT_HOME}/models/retail-360.hbv"));
    assertEquals(
        "work/architecture/models/dimensional/retail-f-orders.drawio",
        ArchitectureExportService.perModelDrawioPath(
            "work/architecture/models", "models/retail-f-orders.hdm"));
    assertNull(
        ArchitectureExportService.perModelDrawioPath("/docs", "models/source-tables-crm.hsm"));
    assertNull(ArchitectureExportService.perModelDrawioPath("/docs", null));
  }

  @Test
  void singleModelGraphExportsFreeformElkDrawio() throws Exception {
    Variables variables = new Variables();
    DataVaultModel core = new DataVaultModel();
    core.setName("retail-360");
    DvHub hub = new DvHub("hub_customer");
    core.getTables().add(hub);
    DvSatellite sat = new DvSatellite("sat_customer");
    sat.setHubName("hub_customer");
    core.getTables().add(sat);

    ArchitectureGraph graph = ArchitectureGraphFromModel.fromDataVault(core, variables);
    assertEquals(ArchitectureViewType.MODEL, graph.getViewType());
    assertTrue(graph.isFreeformLayout());
    assertEquals(2, graph.nodeCount());
    assertTrue(graph.edgeCount() >= 1);

    String drawio = DrawioArchitectureExporter.export(graph);
    assertTrue(drawio.contains("hub_customer"));
    assertTrue(drawio.contains("sat_customer"));
    assertTrue(drawio.contains(" edge=\"1\""));
  }
}
