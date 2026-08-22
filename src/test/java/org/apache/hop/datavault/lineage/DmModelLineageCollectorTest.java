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
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmModelLineageCollectorTest {

  private Variables variables;
  private DimensionalModel model;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    model = loadModel("retail-example/models/retail-f-orders.hdm");
  }

  @Test
  void retailFOrdersHasTableLineageWithReasons() {
    LineageSnapshot snapshot = DmModelLineageCollector.collect(model, variables);
    assertEquals(LineageLayer.DM, snapshot.getModelLayer());
    assertFalse(snapshot.getTables().isEmpty());
    assertTrue(snapshot.getTables().stream().allMatch(t -> !t.getReasons().isEmpty()));
  }

  @Test
  void factTableHasMeasureOrRoleLineage() {
    LineageSnapshot snapshot = DmModelLineageCollector.collect(model, variables);
    TableLineage fact =
        snapshot
            .findTableByLogicalName("f_orders")
            .or(
                () ->
                    snapshot.getTables().stream()
                        .filter(t -> "FACT".equals(t.getTableType()))
                        .findFirst())
            .orElseThrow(() -> new AssertionError("no fact table in retail-f-orders"));

    assertFalse(fact.getFields().isEmpty(), "fact should expose measures or FK roles");
    assertTrue(
        fact.getFields().stream()
            .flatMap(f -> f.getContributions().stream())
            .flatMap(c -> c.getReasons().stream())
            .anyMatch(
                r ->
                    r.getCode() == LineageReasonCode.DM_ROLE_MAPPING
                        || r.getCode() == LineageReasonCode.DEFAULT_SAME_AS_SOURCE
                        || r.getCode() == LineageReasonCode.USER_EXPLICIT_MAPPING),
        "expected dimensional role mapping reasons");
  }

  @Test
  void dimensionAliasProjectsFieldsFromExternalTarget() {
    LineageSnapshot snapshot = DmModelLineageCollector.collect(model, variables);

    // Role-playing alias of conformed d_date (external model).
    TableLineage alias =
        snapshot
            .findTableByLogicalName("d_order_date")
            .orElseThrow(() -> new AssertionError("d_order_date alias missing in retail-f-orders"));

    assertEquals("DIMENSION_ALIAS", alias.getTableType());
    assertFalse(
        alias.getFields().isEmpty(),
        "dimension alias must project natural keys/attributes from the linked physical dimension");
    assertTrue(
        alias.getSources().stream().anyMatch(s -> "d_date".equalsIgnoreCase(s.getName())),
        "alias should reference physical dimension d_date as a source");
    assertTrue(
        alias.getFields().stream()
            .flatMap(f -> f.getContributions().stream())
            .anyMatch(
                c ->
                    c.getSourceKind() == TableSourceKind.DM_TABLE
                        && "d_date".equalsIgnoreCase(c.getSourceName())),
        "field contributions should come from the parent DM dimension d_date");
    // Physical identity shared with conformed d_date for OpenLineage / ops.
    assertTrue(
        alias.getPhysicalTableName() != null
            && alias.getPhysicalTableName().toLowerCase().contains("date"),
        "physical table name should resolve from the linked dimension");
  }

  @Test
  void dimensionAliasWithBrokenExternalPathKeepsType() {
    Variables broken = new Variables();
    broken.setVariable("PROJECT_HOME", "/nonexistent/path");
    DimensionalModel inventory;
    try {
      inventory = loadModel("retail-example/models/retail-f-inventory.hdm");
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    LineageSnapshot snapshot = DmModelLineageCollector.collect(inventory, broken);
    TableLineage warehouseAlias =
        snapshot
            .findTableByLogicalName("d_warehouse")
            .orElseThrow(() -> new AssertionError("d_warehouse alias missing"));
    assertEquals("DIMENSION_ALIAS", warehouseAlias.getTableType());
    assertTrue(warehouseAlias.getFields().isEmpty(), "unresolved external alias has no fields");
    assertFalse(warehouseAlias.getReasons().isEmpty());
  }

  private static DimensionalModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel loaded = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, loaded, null);
    loaded.setFilename(fixture.toString().replace('\\', '/'));
    return loaded;
  }
}
