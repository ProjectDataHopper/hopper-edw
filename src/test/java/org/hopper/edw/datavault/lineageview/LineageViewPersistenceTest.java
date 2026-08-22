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
package org.hopper.edw.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopLineageViewFileType;
import org.hopper.edw.datavault.lineage.LineageLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageDirection;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageSeedKind;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class LineageViewPersistenceTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundTripKeepsViewDefinition() throws Exception {
    HopLineageViewDocument document = sampleDocument();

    Variables variables = variablesWithLicenseHeader();
    String xml =
        ModelXmlWriteSupport.formatModelXml(HopLineageViewFileType.XML_TAG, document, variables);
    assertTrue(xml.contains("<hop-lineage-view>"));
    assertTrue(xml.contains("<backendName>local-marquez</backendName>"));
    assertTrue(xml.contains("<logicalTable>f_orders</logicalTable>"));
    assertTrue(xml.contains("Apache License"));
    assertFalse(xml.contains("<filename>"));

    Document parsed = XmlHandler.loadXmlString(xml);
    Node root = XmlHandler.getSubNode(parsed, HopLineageViewFileType.XML_TAG);
    HopLineageViewDocument loaded = new HopLineageViewDocument();
    XmlMetadataUtil.deSerializeFromXml(root, HopLineageViewDocument.class, loaded, null);

    assertEquals("f_orders upstream", loaded.getName());
    assertEquals("local-marquez", loaded.getBackendName());
    assertEquals(LineageSeedKind.MODEL_TABLE, loaded.getSeedKind());
    assertEquals(LineageLayer.DM, loaded.getModelLayer());
    assertEquals("f_orders", loaded.getLogicalTable());
    assertEquals(LineageDirection.UPSTREAM, loaded.getDirection());
    assertEquals(6, loaded.getDepth());
    assertTrue(loaded.isIncludeJobs());
    assertTrue(loaded.isIncludeOpsOverlay());
    assertEquals("retail-sources", loaded.getResourceGroup());
    assertEquals(2, loaded.getLayerFiltersOrEmpty().size());
    assertEquals(LineageGraphLayer.DV, loaded.getLayerFiltersOrEmpty().get(0));
  }

  @Test
  void saveAndLoadViaHopVfs() throws Exception {
    Path dir = Files.createTempDirectory("hlv-persist");
    String filename = dir.resolve("f_orders-upstream.hlv").toString();
    HopLineageViewDocument document = sampleDocument();
    Variables variables = variablesWithLicenseHeader();
    LineageViewPersistence.save(document, filename, variables);

    String xml = Files.readString(dir.resolve("f_orders-upstream.hlv"), StandardCharsets.UTF_8);
    assertTrue(xml.contains("Apache License"));
    assertTrue(xml.contains("<hop-lineage-view>"));
    assertFalse(xml.contains("<filename>"));

    HopLineageViewDocument loaded = LineageViewPersistence.load(filename, null, variables);
    assertEquals("f_orders-upstream", loaded.getName());
    assertEquals(filename, loaded.getFilename());
    assertEquals(LineageLayer.DM, loaded.getModelLayer());
    assertEquals(2, loaded.getLayerFiltersOrEmpty().size());
    assertTrue(loaded.isIncludeOpsOverlay());
  }

  @Test
  void displayNameFollowsFilename() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("New lineage view");
    assertEquals("New lineage view", document.getName());
    document.setFilename("/tmp/models/lineage-f-order-lines.hlv");
    assertEquals("lineage-f-order-lines", document.getName());
  }

  private static Variables variablesWithLicenseHeader() {
    Variables variables = new Variables();
    variables.setVariable(
        org.apache.hop.core.Const.HOP_LICENSE_HEADER_FILE,
        Path.of("license-header.txt").toAbsolutePath().toString());
    return variables;
  }

  private static HopLineageViewDocument sampleDocument() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("f_orders upstream");
    document.setBackendName("local-marquez");
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(LineageLayer.DM);
    document.setModelName("retail-pos");
    document.setLogicalTable("f_orders");
    document.setModelFilename("${PROJECT_HOME}/models/retail-pos.hdm");
    document.setColumnName("order_amount");
    document.setDirection(LineageDirection.UPSTREAM);
    document.setDepth(6);
    document.setIncludeJobs(true);
    document.setIncludeOpsOverlay(true);
    document.setResourceGroup("retail-sources");
    document.getLayerFiltersOrEmpty().addAll(List.of(LineageGraphLayer.DV, LineageGraphLayer.DM));
    return document;
  }
}
