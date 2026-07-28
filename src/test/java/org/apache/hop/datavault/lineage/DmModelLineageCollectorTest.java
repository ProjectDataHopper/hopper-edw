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
        "PROJECT_HOME",
        Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
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
            .or(() -> snapshot.getTables().stream().filter(t -> "FACT".equals(t.getTableType())).findFirst())
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

  private static DimensionalModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}
