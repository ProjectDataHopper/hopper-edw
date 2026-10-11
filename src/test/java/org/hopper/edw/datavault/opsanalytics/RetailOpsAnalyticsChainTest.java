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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Retail initial and update workflows call operational analytics without failing the retail load. */
class RetailOpsAnalyticsChainTest {

  private static final Path RETAIL = Path.of("retail-example");
  private static final String ANALYTICS =
      "${PROJECT_HOME}/models/ops-analytics/workflows/ops-analytics-incremental.hwf";
  private static final String RUN = "Run operational analytics";
  private static final String CONTINUE = "Continue after analytics";

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generateRetailProjectOnce() throws Exception {
    Path workflow = RETAIL.resolve("models/ops-analytics/workflows/ops-analytics-incremental.hwf");
    if (Files.isRegularFile(workflow)) {
      return;
    }
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", RETAIL.toAbsolutePath().toString());
    OpsAnalyticsRequest request = OpsAnalyticsRequest.defaults();
    request.setConnectionName("OPS");
    request.setSchemaName("dv_ops");
    request.setOutputFolder(RETAIL.resolve("models/ops-analytics").toString());
    request.setDvTargetConnection("Vault");
    request.setBvTargetConnection("Vault");
    request.setDmTargetConnection("Vault");
    request.setGroupName("ops-analytics");
    JsonMetadataProvider provider =
        new JsonMetadataProvider(
            org.apache.hop.core.encryption.Encr.getEncoder(),
            RETAIL.resolve("metadata").toString(),
            variables);
    OpsAnalyticsProjectBuilder.write(request, variables, provider);
    assertTrue(Files.isRegularFile(workflow));
  }

  @Test
  void initialWorkflowContinuesWhenAnalyticsFails() throws Exception {
    assertChain(
        RETAIL.resolve("workflows/run-retail-initial.hwf"),
        "run-retail-update-models",
        List.of("Generate initial execution map", "Export architecture"));
  }

  @Test
  void updateWorkflowContinuesWhenAnalyticsFails() throws Exception {
    assertChain(
        RETAIL.resolve("workflows/run-retail-update.hwf"),
        "run-retail-update-models",
        List.of("Record retail load", "Export architecture"));
  }

  private static void assertChain(Path workflow, String modelsAction, List<String> next)
      throws Exception {
    Document document = XmlHandler.loadXmlFile(workflow.toFile());
    Node root = XmlHandler.getSubNode(document, "workflow");
    assertNotNull(actionNamed(root, RUN));
    Node analytics = actionNamed(root, RUN);
    assertEquals(ANALYTICS, XmlHandler.getTagValue(analytics, "filename"));
    assertNotNull(actionNamed(root, CONTINUE));
    assertEquals("SUCCESS", XmlHandler.getTagValue(actionNamed(root, CONTINUE), "type"));

    assertTrue(hop(root, modelsAction, RUN, "Y"), modelsAction + " -> " + RUN);
    assertTrue(hop(root, RUN, CONTINUE, "N"), RUN + " error hop");
    for (String target : next) {
      assertTrue(hop(root, RUN, target, "Y"), RUN + " -> " + target);
      assertTrue(hop(root, CONTINUE, target, "Y"), CONTINUE + " -> " + target);
      assertTrue(!hop(root, modelsAction, target, "Y"), modelsAction + " still hops to " + target);
    }
  }

  private static Node actionNamed(Node workflow, String name) {
    Node actions = XmlHandler.getSubNode(workflow, "actions");
    NodeList children = actions.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child != null && "action".equals(child.getNodeName()) && name.equals(XmlHandler.getTagValue(child, "name"))) {
        return child;
      }
    }
    return null;
  }

  private static boolean hop(Node workflow, String from, String to, String evaluation) {
    Node hops = XmlHandler.getSubNode(workflow, "hops");
    NodeList children = hops.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child == null || !"hop".equals(child.getNodeName())) {
        continue;
      }
      if (from.equals(XmlHandler.getTagValue(child, "from"))
          && to.equals(XmlHandler.getTagValue(child, "to"))
          && evaluation.equals(XmlHandler.getTagValue(child, "evaluation"))) {
        return true;
      }
    }
    return false;
  }
}
