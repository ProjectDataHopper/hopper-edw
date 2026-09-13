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
package org.hopper.edw.datavault.diagram.exporter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.ExportContext;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EdwRichFormatDiagramExporterTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void dataVaultPlantUmlMermaidAndDrawio() throws Exception {
    DataVaultModel model = dataVault();
    IExportContext context = testContext();

    DiagramExportResult puml =
        new DataVaultPlantUmlDiagramExporter()
            .export(model, new DiagramExportOptions(null, "PLANTUML"), context);
    assertTrue(puml.getContent().contains("@startuml"));
    assertTrue(puml.getContent().contains("<<Hub>>"));
    assertTrue(puml.getContent().contains("<<Link>>"));
    assertTrue(puml.getContent().contains("<<Satellite>>"));

    DiagramExportResult mmd =
        new DataVaultMermaidDiagramExporter()
            .export(model, new DiagramExportOptions(null, "MERMAID"), context);
    assertTrue(mmd.getContent().contains("erDiagram"));
    assertTrue(mmd.getContent().contains("hub_customer"));

    DiagramExportResult drawio =
        new DataVaultDrawioDiagramExporter()
            .export(model, new DiagramExportOptions(null, "DRAWIO"), context);
    assertTrue(drawio.getContent().contains("<mxfile"));
    assertTrue(drawio.getContent().contains("mxCell"));
  }

  @Test
  void businessVaultPlantUmlAndMermaid() throws Exception {
    BusinessVaultModel model = businessVault();
    DiagramExportResult puml =
        new BusinessVaultPlantUmlDiagramExporter()
            .export(model, new DiagramExportOptions(null, "PLANTUML"), testContext());
    assertTrue(puml.getContent().contains("@startuml"));
    assertTrue(puml.getContent().contains("<<SCD2>>"));

    DiagramExportResult mmd =
        new BusinessVaultMermaidDiagramExporter()
            .export(model, new DiagramExportOptions(null, "MERMAID"), testContext());
    assertTrue(mmd.getContent().contains("erDiagram"));
    assertTrue(mmd.getContent().contains("bv_customer"));

    DiagramExportResult drawio =
        new BusinessVaultDrawioDiagramExporter()
            .export(model, new DiagramExportOptions(null, "DRAWIO"), testContext());
    assertTrue(drawio.getContent().contains("<mxfile"));
  }

  @Test
  void sourceModelPlantUmlMermaidAndDrawio() throws Exception {
    SourceModel model = sourceModel();
    IExportContext context = testContext();

    DiagramExportResult puml =
        new SourceModelPlantUmlDiagramExporter()
            .export(model, new DiagramExportOptions(null, "PLANTUML"), context);
    assertTrue(puml.getContent().contains("@startuml"));
    assertTrue(puml.getContent().contains("customer"));
    assertTrue(puml.getContent().contains("<<PK>>"));
    assertTrue(puml.getContent().contains("<<FK>>"));

    DiagramExportResult mmd =
        new SourceModelMermaidDiagramExporter()
            .export(model, new DiagramExportOptions(null, "MERMAID"), context);
    assertTrue(mmd.getContent().contains("erDiagram"));
    assertTrue(mmd.getContent().contains("customer"));

    DiagramExportResult drawio =
        new SourceModelDrawioDiagramExporter()
            .export(model, new DiagramExportOptions(null, "DRAWIO"), context);
    assertTrue(drawio.getContent().contains("<mxfile"));
    assertTrue(drawio.getContent().contains("edge=\"1\""));
  }

  @Test
  void executionMapPlantUmlMermaidAndDrawio() throws Exception {
    ExecutionMapDocument map = executionMap();
    IExportContext context = testContext();

    DiagramExportResult puml =
        new ExecutionMapPlantUmlDiagramExporter()
            .export(map, new DiagramExportOptions(null, "PLANTUML"), context);
    assertTrue(puml.getContent().contains("@startuml"));
    assertTrue(puml.getContent().contains("component"));

    DiagramExportResult mmd =
        new ExecutionMapMermaidDiagramExporter()
            .export(map, new DiagramExportOptions(null, "MERMAID"), context);
    assertTrue(mmd.getContent().contains("flowchart LR"));
    assertTrue(mmd.getContent().contains("wf_root"));

    DiagramExportResult drawio =
        new ExecutionMapDrawioDiagramExporter()
            .export(map, new DiagramExportOptions(null, "DRAWIO"), context);
    assertTrue(drawio.getContent().contains("<mxfile"));
    assertTrue(drawio.getContent().contains("mxCell"));
  }

  private static IExportContext testContext() {
    return new ExportContext(new Variables(), null, null, IExportContext.ExportEnvironment.TEST);
  }

  private static DataVaultModel dataVault() {
    DataVaultModel model = new DataVaultModel();
    model.setName("demo");

    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setLocation(new Point(80, 80));
    BusinessKey key = new BusinessKey();
    key.setName("customer_id");
    hub.getBusinessKeys().add(key);
    model.getTables().add(hub);

    DvLink link = new DvLink();
    link.setName("lnk_customer_order");
    link.setLocation(new Point(280, 80));
    link.getHubNames().add("hub_customer");
    model.getTables().add(link);

    DvSatellite satellite = new DvSatellite();
    satellite.setName("sat_customer");
    satellite.setLocation(new Point(80, 220));
    satellite.setHubName("hub_customer");
    model.getTables().add(satellite);
    return model;
  }

  private static BusinessVaultModel businessVault() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName("bv-demo");
    BvScd2Table table = new BvScd2Table();
    table.setName("bv_customer");
    table.setLocation(new Point(80, 80));
    table.getDerivatives().add(new BvDerivativeRef("hub_customer", DvTableType.HUB));
    model.getTables().add(table);
    return model;
  }

  private static SourceModel sourceModel() {
    SourceModel model = new SourceModel();
    model.setName("crm");

    SourceTable customer = new SourceTable("customer");
    customer.setLocation(new Point(40, 40));
    SourceColumn id = new SourceColumn("id");
    id.setPrimaryKeyPosition(1);
    id.setSourceDataType("integer");
    customer.getColumns().add(id);
    SourceColumn name = new SourceColumn("name");
    name.setSourceDataType("varchar");
    customer.getColumns().add(name);
    model.getTables().add(customer);

    SourceTable address = new SourceTable("address");
    address.setLocation(new Point(280, 40));
    SourceColumn addressId = new SourceColumn("id");
    addressId.setPrimaryKeyPosition(1);
    address.getColumns().add(addressId);
    SourceColumn customerId = new SourceColumn("customer_id");
    address.getColumns().add(customerId);
    model.getTables().add(address);

    SourceRelationship relationship = new SourceRelationship("fk_address_customer");
    relationship.setChildTableName("address");
    relationship.setParentTableName("customer");
    relationship.getChildColumns().add("customer_id");
    relationship.getParentColumns().add("id");
    model.getRelationships().add(relationship);
    return model;
  }

  private static ExecutionMapDocument executionMap() {
    ExecutionMapDocument map = new ExecutionMapDocument();
    map.setName("run-update");

    ExecutionMapNode root = new ExecutionMapNode();
    root.setId("wf-root");
    root.setName("run-update");
    root.setNodeType(ExecutionMapNodeType.ROOT_WORKFLOW);
    map.getNodesOrEmpty().add(root);

    ExecutionMapNode dv = new ExecutionMapNode();
    dv.setId("act-dv");
    dv.setName("Update DV");
    dv.setNodeType(ExecutionMapNodeType.DV_UPDATE);
    map.getNodesOrEmpty().add(dv);

    ExecutionMapEdge edge = new ExecutionMapEdge();
    edge.setFromNodeId("wf-root");
    edge.setToNodeId("act-dv");
    edge.setEdgeType(ExecutionMapEdgeType.HOP);
    map.getEdgesOrEmpty().add(edge);
    return map;
  }
}
