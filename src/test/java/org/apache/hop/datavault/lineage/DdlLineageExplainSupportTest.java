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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DdlLineageExplainSupportTest {

  private Variables variables;
  private DataVaultModel model;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    model = loadModel("retail-example/models/retail-360.hdv");
  }

  @Test
  void explainAddColumnIncludesSourceMapping() {
    String explanation =
        DdlLineageExplainSupport.explain(
            List.of("ALTER TABLE sat_customer_demo ADD segment VARCHAR(50);"),
            model,
            variables,
            null);

    assertTrue(explanation.contains("sat_customer_demo"), explanation);
    assertTrue(explanation.contains("segment"), explanation);
    assertTrue(
        explanation.contains("E2E-customer-demo") || explanation.contains("DEFAULT_SAME_AS_SOURCE"),
        explanation);
    assertTrue(
        explanation.contains("USER_EXPLICIT_NAME") || explanation.contains("model"), explanation);
  }

  @Test
  void explainCreateTableListsFieldsWithReasons() {
    String explanation =
        DdlLineageExplainSupport.explain(
            List.of("CREATE TABLE hub_customer (customer_id INTEGER);"), model, variables, null);

    assertTrue(explanation.contains("CREATE TABLE hub_customer"), explanation);
    assertTrue(explanation.contains("customer_id"), explanation);
    assertTrue(explanation.contains("customer_hk"), explanation);
    assertTrue(explanation.contains("x_load_ts"), explanation);
  }

  private static DataVaultModel loadModel(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
