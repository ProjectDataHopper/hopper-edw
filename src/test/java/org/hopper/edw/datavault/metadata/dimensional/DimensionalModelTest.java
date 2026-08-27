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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DimensionalModelTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void checkReportsNoTables() {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    var remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
  }

  @Test
  void checkReportsOkForBasicStarFixture() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/basic-star.hdm").toAbsolutePath().normalize();
    String xml = Files.readString(fixture);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);

    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);

    var remarks = model.check(null, new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
    assertEquals(3, model.getTables().size());
  }

  @Test
  void xmlRoundTripPreservesCoreFields() throws Exception {
    DimensionalModel original = new DimensionalModel();
    original.setName("sales-dm");
    original.setDescription("Sales dimensional model");
    original.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.setTableName("f_sales");
    fact.getDimensionRoles().add(new DmFactDimensionRole("dim_product", "Product", "product_key"));
    fact.getMeasures().add(new DmFactMeasure("amount", true));
    original.getTables().add(fact);
    DmDimension dimension = new DmDimension();
    dimension.setName("dim_product");
    dimension.setTableName("d_product");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("product_id"));
    original.getTables().add(dimension);

    String xml =
        XmlHandler.aroundTag(
            HopDimensionalFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);

    DimensionalModel restored = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, restored, null);

    assertEquals(original.getName(), restored.getName());
    assertEquals(original.getDescription(), restored.getDescription());
    assertEquals(
        original.getConfigurationOrDefault().getTargetDatabase(),
        restored.getConfigurationOrDefault().getTargetDatabase());
    assertEquals(2, restored.getTables().size());
    assertEquals(DmTableType.FACT, restored.getTables().get(0).getTableType());
    assertEquals("fact_sales", restored.getTables().get(0).getName());
    assertEquals(DmTableType.DIMENSION, restored.getTables().get(1).getTableType());
    assertEquals("dim_product", restored.getTables().get(1).getName());
    assertEquals(1, ((DmFact) restored.getTables().get(0)).getDimensionRolesOrEmpty().size());
    assertEquals(1, ((DmDimension) restored.getTables().get(1)).getNaturalKeysOrEmpty().size());
  }
}
