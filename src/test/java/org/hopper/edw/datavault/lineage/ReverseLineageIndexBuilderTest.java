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
package org.hopper.edw.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class ReverseLineageIndexBuilderTest {

  private Variables variables;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
  }

  @Test
  void retailSourceFieldFindsDvAndBvConsumers() throws Exception {
    DataVaultModel dv = loadDv("retail-example/models/retail-360.hdv");
    BusinessVaultModel bv = loadBv("retail-example/models/retail-360.hbv");
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources");
    ValidationModels models =
        new ValidationModels(
            group,
            List.of(new ValidationModels.LoadedDataVaultModel(dv, "local-catalog")),
            List.of(new ValidationModels.LoadedBusinessVaultModel(bv, dv, "local-catalog")),
            List.of());

    ReverseLineageIndex index = ReverseLineageIndexBuilder.build(models, variables, null);
    assertFalse(index.isEmpty());

    List<ReverseLineageConsumer> segmentConsumers = index.find("E2E-customer-demo", "segment");
    assertFalse(segmentConsumers.isEmpty(), "expected consumers of E2E-customer-demo.segment");
    assertTrue(
        segmentConsumers.stream()
            .anyMatch(
                c ->
                    "sat_customer_demo".equalsIgnoreCase(c.getTableName())
                        && "segment".equalsIgnoreCase(c.getTargetField())
                        && c.getHopCount() == 1),
        "direct DV sat consumer");
    assertTrue(
        segmentConsumers.stream()
            .anyMatch(
                c ->
                    "customer_360_bv".equalsIgnoreCase(c.getTableName())
                        && "cust_segment".equalsIgnoreCase(c.getTargetField())
                        && c.getHopCount() >= 2),
        "multi-hop BV consumer via sat");

    List<ReverseLineageConsumer> search = index.search("customer-demo", "segment");
    assertFalse(search.isEmpty());
    assertTrue(
        search.stream()
            .anyMatch(c -> c.getPathSummary() != null && c.getPathSummary().contains("segment")));
  }

  private static DataVaultModel loadDv(String path) throws Exception {
    Path fixture = Path.of(path).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    model.setFilename(fixture.toString());
    return model;
  }

  private static BusinessVaultModel loadBv(String path) throws Exception {
    Path fixture = Path.of(path).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, null);
    model.setFilename(fixture.toString());
    return model;
  }
}
