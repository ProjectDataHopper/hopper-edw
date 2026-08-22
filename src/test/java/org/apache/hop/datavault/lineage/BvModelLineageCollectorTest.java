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
package org.apache.hop.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvModelLineageCollectorTest {

  private Variables variables;
  private BusinessVaultModel model;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    model = loadModel("retail-example/models/retail-360.hbv");
  }

  @Test
  void customer360BvHasScd2FieldMappingsWithReasons() {
    LineageSnapshot snapshot = BvModelLineageCollector.collect(model, variables);
    assertEquals(LineageLayer.BV, snapshot.getModelLayer());

    TableLineage table =
        snapshot
            .findTableByLogicalName("customer_360_bv")
            .orElseThrow(() -> new AssertionError("customer_360_bv missing"));

    assertEquals("customer_360_bv", table.getPhysicalTableName());
    assertEquals("SCD2", table.getTableType());
    assertTrue(table.getSources().stream().anyMatch(s -> "sat_customer_demo".equals(s.getName())));
    assertFalse(table.getReasons().isEmpty());

    FieldLineage segment =
        table.findField("cust_segment").orElseThrow(() -> new AssertionError("cust_segment"));
    assertEquals(1, segment.getContributions().size());
    FieldContribution c = segment.getContributions().get(0);
    assertEquals("sat_customer_demo", c.getSourceName());
    assertEquals("segment", c.getSourceFieldName());
    assertEquals(FieldTransform.RENAME, c.getTransform());
    assertTrue(
        c.getReasons().stream().anyMatch(r -> r.getCode() == LineageReasonCode.BV_SCD2_FIELD_MAP));
  }

  @Test
  void explainCreateUsesBvLineage() {
    String explanation =
        DdlLineageExplainSupport.explain(
            java.util.List.of("CREATE TABLE customer_360_bv (cust_segment VARCHAR(20));"),
            model,
            variables);
    assertTrue(explanation.contains("customer_360_bv"), explanation);
    assertTrue(
        explanation.contains("cust_segment") || explanation.contains("BV_SCD2_FIELD_MAP"),
        explanation);
  }

  private static BusinessVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, null);
    return model;
  }
}
