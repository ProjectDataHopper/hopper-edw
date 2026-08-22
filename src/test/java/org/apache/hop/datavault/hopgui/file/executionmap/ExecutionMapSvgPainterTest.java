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
package org.apache.hop.datavault.hopgui.file.executionmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.command.svg.ExecutionMapExportScope;
import org.apache.hop.datavault.command.svg.SvgExportService;
import org.apache.hop.datavault.command.svg.SvgRenderOptions;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.executionmap.CrawlOptions;
import org.apache.hop.datavault.executionmap.ExecutionMapCrawler;
import org.apache.hop.datavault.executionmap.ExecutionMapFocusContext;
import org.apache.hop.datavault.executionmap.ExecutionMapLayoutSupport;
import org.apache.hop.datavault.executionmap.ExecutionMapLineStyle;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionMapSvgPainterTest {

  private static final Path ROOT_WORKFLOW =
      Path.of("retail-example/workflows/update-retail-dv-bv-dm.hwf").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generatesSvgForRetailExecutionMap() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

    var document =
        ExecutionMapCrawler.crawl(
                ROOT_WORKFLOW.toString(),
                variables,
                null,
                CrawlOptions.builder().includeGeneratedPipelines(false).build())
            .getDocument();

    String svg =
        SvgExportService.generateExecutionMapSvg(document, SvgRenderOptions.defaults(), variables);

    assertFalse(svg.isBlank());
    assertTrue(svg.contains("<svg"));
    assertTrue(
        svg.contains("line") || svg.contains("polyline"),
        "svg should contain routed edge segments");
  }

  @Test
  void generatesOrthogonalEdgesForHubSpokeMap() throws Exception {
    withLineStyle(
        ExecutionMapLineStyle.ORTHOGONAL,
        () -> {
          try {
            Variables variables = new Variables();
            variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

            var document =
                ExecutionMapCrawler.crawl(
                        ROOT_WORKFLOW.toString(),
                        variables,
                        null,
                        CrawlOptions.builder()
                            .includeGeneratedPipelines(false)
                            .includeWorkflowActions(false)
                            .build())
                    .getDocument();

            SvgRenderOptions options = SvgRenderOptions.defaults();
            options.setExecutionMapExportScope(ExecutionMapExportScope.FULL);
            ExecutionMapLayoutSupport.layout(document);
            String svg = SvgExportService.generateExecutionMapSvg(document, options, variables);

            assertTrue(svg.contains("line") || svg.contains("polyline"));
            assertTrue(
                svg.contains("polygon"), "svg should contain arrowheads for reference edges");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void generatesFocusedSvgForRootView() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

    var document =
        ExecutionMapCrawler.crawl(
                ROOT_WORKFLOW.toString(),
                variables,
                null,
                CrawlOptions.builder()
                    .includeGeneratedPipelines(false)
                    .includeWorkflowActions(false)
                    .build())
            .getDocument();

    SvgRenderOptions options = SvgRenderOptions.defaults();
    options.setExecutionMapExportScope(ExecutionMapExportScope.FOCUSED);

    String svg = SvgExportService.generateExecutionMapSvg(document, options, variables);

    assertFalse(svg.isBlank());
    assertTrue(svg.contains("<svg"));
  }

  @Test
  void generatesFullSvgWithOrthogonalRouting() throws Exception {
    withLineStyle(
        ExecutionMapLineStyle.ORTHOGONAL,
        () -> {
          try {
            Variables variables = new Variables();
            variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

            var document =
                ExecutionMapCrawler.crawl(
                        ROOT_WORKFLOW.toString(),
                        variables,
                        null,
                        CrawlOptions.builder()
                            .includeGeneratedPipelines(false)
                            .includeWorkflowActions(false)
                            .build())
                    .getDocument();
            ExecutionMapLayoutSupport.layout(document);

            SvgRenderOptions options = SvgRenderOptions.defaults();
            options.setExecutionMapExportScope(ExecutionMapExportScope.FULL);

            String svg = SvgExportService.generateExecutionMapSvg(document, options, variables);

            assertTrue(svg.contains("line") || svg.contains("polyline"));
            assertTrue(
                svg.contains("polygon"), "full export should retain orthogonal reference edges");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void generatesDirectCenterEdgesByDefault() throws Exception {
    withLineStyle(
        ExecutionMapLineStyle.DIRECT_CENTER,
        () -> {
          try {
            Variables variables = new Variables();
            variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

            var document =
                ExecutionMapCrawler.crawl(
                        ROOT_WORKFLOW.toString(),
                        variables,
                        null,
                        CrawlOptions.builder()
                            .includeGeneratedPipelines(false)
                            .includeWorkflowActions(false)
                            .build())
                    .getDocument();

            SvgRenderOptions options = SvgRenderOptions.defaults();
            options.setExecutionMapExportScope(ExecutionMapExportScope.FULL);
            ExecutionMapLayoutSupport.layout(document);
            String svg = SvgExportService.generateExecutionMapSvg(document, options, variables);

            assertTrue(svg.contains("line") || svg.contains("polyline"));
            assertFalse(
                svg.contains("polygon"),
                "direct center style should not draw orthogonal arrowheads");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void withLineStyle(ExecutionMapLineStyle style, Runnable action) {
    var config = DataVaultConfigSingleton.getConfig();
    ExecutionMapLineStyle previous = config.getExecutionMapLineStyleOrDefault();
    config.setExecutionMapLineStyle(style);
    try {
      action.run();
    } finally {
      config.setExecutionMapLineStyle(previous);
    }
  }

  @Test
  void generatesFocusedSvgForDrilledModelView() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", ROOT_WORKFLOW.getParent().getParent().toString());

    var document =
        ExecutionMapCrawler.crawl(
                ROOT_WORKFLOW.toString(),
                variables,
                null,
                CrawlOptions.builder()
                    .includeGeneratedPipelines(true)
                    .includeWorkflowActions(false)
                    .build())
            .getDocument();

    String modelNodeId =
        document.getNodesOrEmpty().stream()
            .filter(
                node ->
                    node != null
                        && node.getNodeType() == ExecutionMapNodeType.DATA_VAULT_MODEL
                        && node.getName() != null
                        && node.getName().contains("retail-conformed-dims"))
            .map(node -> node.getId())
            .findFirst()
            .orElse(null);
    if (modelNodeId == null) {
      return;
    }

    ExecutionMapFocusContext focus = new ExecutionMapFocusContext(modelNodeId);
    SvgRenderOptions options = SvgRenderOptions.defaults();
    options.setExecutionMapExportScope(ExecutionMapExportScope.FOCUSED);
    options.setExecutionMapFocus(focus);

    String svg = SvgExportService.generateExecutionMapSvg(document, options, variables);

    assertFalse(svg.isBlank());
    assertTrue(svg.contains("<svg"));
  }
}
