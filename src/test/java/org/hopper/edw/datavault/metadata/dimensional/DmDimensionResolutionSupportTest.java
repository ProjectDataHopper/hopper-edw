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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmDimensionResolutionSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesAliasToBaseDimension() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    DmDimension resolved =
        DmDimensionResolutionSupport.resolveDimension(model, "dim_order_date", new Variables());
    assertNotNull(resolved);
    assertEquals("dim_date", resolved.getName());
    assertEquals("d_date", resolved.getTableName());
  }

  @Test
  void resolvesPhysicalTableNameThroughAlias() throws Exception {
    DimensionalModel model = loadDateRolePlayingModel();
    assertEquals(
        "d_date",
        DmDimensionResolutionSupport.resolvePhysicalTableName(
            model, "dim_shipment_date", new Variables()));
  }

  @Test
  void rejectsAliasChainToAnotherAlias() {
    DimensionalModel model = new DimensionalModel();
    DmDimension base = new DmDimension();
    base.setName("dim_date");
    base.setTableName("d_date");
    model.getTables().add(base);

    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("dim_order_date");
    alias.setReferencedDimensionName("dim_shipment_date");
    model.getTables().add(alias);

    DmDimensionAlias nested = new DmDimensionAlias();
    nested.setName("dim_shipment_date");
    nested.setReferencedDimensionName("dim_date");
    model.getTables().add(nested);

    assertNull(DmDimensionResolutionSupport.resolveDimension(model, "dim_order_date"));
  }

  @Test
  void resolvesExternalDimensionAlias() throws Exception {
    DmModelLoadSupport.clearCache();
    DimensionalModel model = loadCrossModelAliasFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-alias.hdm")
            .toAbsolutePath()
            .normalize()
            .toString());

    DmDimension customer =
        DmDimensionResolutionSupport.resolveDimension(model, "dim_customer", new Variables());
    assertNotNull(customer);
    assertEquals("dim_customer", customer.getName());
    assertEquals("d_customer", customer.getTableName());

    DmDimension orderDate =
        DmDimensionResolutionSupport.resolveDimension(model, "dim_order_date", new Variables());
    assertNotNull(orderDate);
    assertEquals("dim_date", orderDate.getName());
    assertEquals("d_date", orderDate.getTableName());

    assertEquals(
        "d_product",
        DmDimensionResolutionSupport.resolvePhysicalTableName(
            model, "dim_product", new Variables()));
  }

  @Test
  void resolvesAliasSourceModelDisplayNameForExternalAndLocalAliases() throws Exception {
    DimensionalModel crossModel = loadCrossModelAliasFixture();
    DmDimensionAlias externalCustomer = (DmDimensionAlias) crossModel.findTable("dim_customer");
    assertEquals(
        "shared-conformed",
        DmDimensionResolutionSupport.resolveAliasSourceModelDisplayName(
            crossModel, externalCustomer, new Variables()));

    DimensionalModel localModel = loadDateRolePlayingModel();
    DmDimensionAlias localOrderDate = (DmDimensionAlias) localModel.findTable("dim_order_date");
    assertEquals(
        "date-role-playing",
        DmDimensionResolutionSupport.resolveAliasSourceModelDisplayName(
            localModel, localOrderDate, new Variables()));
  }

  @Test
  void dateTemplateCreatesExpectedAttributes() {
    DmDimension dateDimension = DmDateDimensionTemplate.createDateDimension(null);
    assertEquals("dim_date", dateDimension.getName());
    assertEquals("d_date", dateDimension.getTableName());
    assertEquals(1, dateDimension.getNaturalKeysOrEmpty().size());
    assertEquals("date_key", dateDimension.getNaturalKeysOrEmpty().get(0).getFieldName());
    assertTrue(dateDimension.getAttributesOrEmpty().size() >= 8);
  }

  private static DimensionalModel loadDateRolePlayingModel() throws Exception {
    return loadFixture("integration-tests/tests/basic/date-role-playing.hdm");
  }

  private static DimensionalModel loadCrossModelAliasFixture() throws Exception {
    return loadFixture("integration-tests/tests/basic/cross-model-alias.hdm");
  }

  private static DimensionalModel loadFixture(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}
