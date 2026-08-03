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
package org.apache.hop.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapRootArtifactType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionMapCrawlerTest {

  private static final Path RETAIL_HOME = Path.of("retail-example").toAbsolutePath().normalize();
  private static final Path ROOT_WORKFLOW =
      RETAIL_HOME.resolve("workflows/update-retail-dv-bv-dm.hwf");

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void crawlRetailUpdateWorkflowDiscoversDvBvDmActions() throws Exception {
    Variables variables = retailVariables();
    CrawlOptions options =
        CrawlOptions.builder()
            .includeGeneratedPipelines(false)
            .includeWorkflowActions(true)
            .captureSnapshots(true)
            .build();

    ExecutionMapCrawler.CrawlResult result =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);

    ExecutionMapDocument document = result.getDocument();
    assertNotNull(document);
    assertTrue(document.getNodesOrEmpty().size() >= 4);
    assertFalse(document.getEdgesOrEmpty().isEmpty());
    assertEqualsRootWorkflow(document);
    assertContainsNodeType(document, ExecutionMapNodeType.DV_UPDATE);
    assertContainsNodeType(document, ExecutionMapNodeType.BV_UPDATE);
    assertContainsNodeType(document, ExecutionMapNodeType.DM_UPDATE);
    assertFalse(document.getSnapshotsOrEmpty().isEmpty());
  }

  @Test
  void crawlRetailWorkflowWithoutActionsSkipsActionNodes() throws Exception {
    Variables variables = retailVariables();
    CrawlOptions options =
        CrawlOptions.builder()
            .includeGeneratedPipelines(false)
            .includeWorkflowActions(false)
            .captureSnapshots(true)
            .build();

    ExecutionMapCrawler.CrawlResult result =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);

    ExecutionMapDocument document = result.getDocument();
    assertEqualsRootWorkflow(document);
    assertFalse(containsWorkflowActionLayerNode(document));
    assertTrue(document.getNodesOrEmpty().size() >= 2);
  }

  @Test
  void crawlRetailWorkflowMinimalViewLayoutsWithoutError() throws Exception {
    Variables variables = retailVariables();
    CrawlOptions options =
        CrawlOptions.builder()
            .includeWorkflowActions(false)
            .includePipelineTransforms(false)
            .includeDatasetNodes(false)
            .includeGeneratedPipelines(true)
            .build();

    ExecutionMapCrawler.CrawlResult result =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);

    ExecutionMapDocument document = result.getDocument();
    assertNotNull(document);
    assertFalse(document.getNodesOrEmpty().isEmpty());
    assertTrue(ExecutionMapHubSpokeLayout.canUseHubSpokeLayout(document));
  }

  @Test
  void crawlRetailWorkflowParentsReferencedModelsUnderRoot() throws Exception {
    Variables variables = retailVariables();
    CrawlOptions options =
        CrawlOptions.builder()
            .includeGeneratedPipelines(false)
            .includeWorkflowActions(false)
            .build();

    ExecutionMapCrawler.CrawlResult result =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);

    ExecutionMapDocument document = result.getDocument();
    ExecutionMapNode root =
        document.getNodesOrEmpty().stream()
            .filter(
                node -> node != null && node.getNodeType() == ExecutionMapNodeType.ROOT_WORKFLOW)
            .findFirst()
            .orElse(null);
    assertNotNull(root);

    boolean modelParentedUnderRoot =
        document.getNodesOrEmpty().stream()
            .anyMatch(
                node ->
                    node != null
                        && node.getNodeType() == ExecutionMapNodeType.DATA_VAULT_MODEL
                        && root.getId().equals(node.getParentNodeId()));
    assertTrue(
        modelParentedUnderRoot, "Referenced DV models should be parented under the root workflow");
  }

  @Test
  void crawlRetailWorkflowCanIncludeDatasetNodesFromGeneratedPipelines() throws Exception {
    Variables variables = retailVariables();
    CrawlOptions options =
        CrawlOptions.builder().includeGeneratedPipelines(true).includeDatasetNodes(true).build();

    ExecutionMapCrawler.CrawlResult result =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);

    ExecutionMapDocument document = result.getDocument();
    boolean hasDatasetNode =
        document.getNodesOrEmpty().stream()
            .anyMatch(
                node ->
                    node != null
                        && (node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET
                            || node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET));
    assertTrue(hasDatasetNode, "Expected dataset nodes when generated pipelines are included");

    boolean hasConnectionBackedDataset =
        document.getNodesOrEmpty().stream()
            .anyMatch(
                node ->
                    node != null
                        && (node.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET
                            || node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET)
                        && "Vault".equals(node.getProperty("datasetNamespace")));
    assertFalse(
        hasConnectionBackedDataset,
        "Generated pipeline datasets should use catalog namespaces, not connection names");

    ExecutionMapNode hubCustomer =
        document.getNodesOrEmpty().stream()
            .filter(
                node ->
                    node != null
                        && node.getNodeType() == ExecutionMapNodeType.TARGET_DATASET
                        && "hub_customer".equals(node.getProperty("datasetName")))
            .findFirst()
            .orElse(null);
    assertNotNull(hubCustomer, "Expected catalog-backed hub_customer target dataset");
    assertEquals(
        "hop/retail-example/models/retail-360", hubCustomer.getProperty("datasetNamespace"));
    assertEquals("DV_HUB", hubCustomer.getProperty("datasetKind"));
  }

  private static Variables retailVariables() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", RETAIL_HOME.toString());
    return variables;
  }

  private static boolean containsWorkflowActionLayerNode(ExecutionMapDocument document) {
    return document.getNodesOrEmpty().stream()
        .anyMatch(
            node ->
                node != null
                    && ExecutionMapContext.isWorkflowActionLayerNodeType(node.getNodeType()));
  }

  private static void assertEqualsRootWorkflow(ExecutionMapDocument document) {
    assertTrue(
        document.getRootArtifactType() == ExecutionMapRootArtifactType.WORKFLOW,
        "Root artifact should be a workflow");
    assertTrue(
        document.getRootArtifactPath().endsWith("update-retail-dv-bv-dm.hwf"),
        "Root path should point at the retail update workflow");
    assertContainsNodeType(document, ExecutionMapNodeType.ROOT_WORKFLOW);
  }

  private static void assertContainsNodeType(
      ExecutionMapDocument document, ExecutionMapNodeType nodeType) {
    boolean found =
        document.getNodesOrEmpty().stream()
            .anyMatch(node -> node != null && node.getNodeType() == nodeType);
    assertTrue(found, () -> "Expected node type " + nodeType + " in execution map");
  }
}
