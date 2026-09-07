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
package org.hopper.edw.datavault.documentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDocumentationServiceTest {

  @TempDir Path temp;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void generatesIndexAssetsAndSearchIndex() throws Exception {
    Path source = temp.resolve("project");
    Path target = temp.resolve("docs");
    Files.createDirectories(source);

    ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
    options.setSourceFolder(source.toString());
    options.setTargetFolder(target.toString());
    options.setProjectName("Doc Test");
    options.setIncludingCatalog(false);
    options.setIncludingMetadata(false);
    options.setDarkSvg(false);

    Variables variables = new Variables();
    variables.setVariable(ProjectDocumentationService.VAR_PROJECT_HOME, source.toString());

    ProjectDocumentationResult result =
        ProjectDocumentationService.generate(
            options, variables, new MemoryMetadataProvider(), LogChannel.GENERAL);

    assertTrue(Files.isRegularFile(target.resolve("index.html")));
    assertTrue(Files.isRegularFile(target.resolve("theming.html")));
    assertTrue(Files.isRegularFile(target.resolve("assets/css/hop-doc.css")));
    assertTrue(Files.isRegularFile(target.resolve("assets/css/themes/default.css")));
    assertTrue(Files.isRegularFile(target.resolve("assets/css/themes/compact.css")));
    assertTrue(Files.isRegularFile(target.resolve("assets/css/themes/high-contrast.css")));
    assertTrue(Files.isRegularFile(target.resolve("assets/js/search-index.js")));
    assertTrue(Files.isRegularFile(target.resolve("assets/js/search.js")));
    assertTrue(Files.isRegularFile(target.resolve("assets/js/svg-viewer.js")));

    String index = Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8);
    assertTrue(index.contains("Doc Test"));
    assertTrue(index.contains("hop-doc-search"));
    assertTrue(index.contains("hop-doc-breadcrumb"));
    assertTrue(index.contains("id=\"hop-doc-theme\""));
    assertTrue(index.contains("data-theme=\"system\""));

    String searchJs =
        Files.readString(target.resolve("assets/js/search-index.js"), StandardCharsets.UTF_8);
    assertTrue(searchJs.contains("window.HOP_DOC_INDEX"));
    assertTrue(searchJs.contains("Doc Test"));
    assertFalse(result.getOutputFolder().isBlank());
  }

  @Test
  void documentsDataVaultModelFromFixture() throws Exception {
    Path source =
        Path.of("integration-tests/tests/multi-satellite-bv").toAbsolutePath().normalize();
    Path target = temp.resolve("dv-docs");
    assumeTrue(Files.isDirectory(source), "integration-tests fixture missing");

    ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
    options.setSourceFolder(source.toString());
    options.setTargetFolder(target.toString());
    options.setProjectName("Customer 360");
    options.setIncludingCatalog(false);
    options.setIncludingMetadata(false);
    options.setDarkSvg(false);

    Variables variables = new Variables();
    variables.setVariable(ProjectDocumentationService.VAR_PROJECT_HOME, source.toString());

    ProjectDocumentationResult result =
        ProjectDocumentationService.generate(
            options, variables, new MemoryMetadataProvider(), LogChannel.GENERAL);
    assertTrue(result.getErrors() == 0, () -> result.getWarnings().toString());

    assertTrue(Files.isRegularFile(target.resolve("index.html")));
    String searchJs =
        Files.readString(target.resolve("assets/js/search-index.js"), StandardCharsets.UTF_8);
    assertTrue(searchJs.contains("customer-360") || searchJs.contains("hub_customer"), searchJs);
    assertTrue(
        Files.walk(target)
            .anyMatch(
                p ->
                    p.getFileName().toString().endsWith(".html")
                        && p.toString().contains("data-vault")),
        "expected a Data Vault model HTML page");

    String index = Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8);
    assertTrue(index.contains("<summary>"));
    assertTrue(index.contains("hop-doc-nav-group"));
    assertFalse(
        index.contains("class=\"is-current\" open"), "overview should leave type groups collapsed");

    Path tablePage =
        Files.walk(target)
            .filter(
                p ->
                    p.toString().contains("tables") && p.getFileName().toString().endsWith(".html"))
            .findFirst()
            .orElseThrow();
    String tableHtml = Files.readString(tablePage, StandardCharsets.UTF_8);
    assertTrue(tableHtml.contains("class=\"is-current\" open"));
    assertTrue(tableHtml.contains("<summary>Tables</summary>"));
    assertTrue(tableHtml.contains("validate-customer-360-bv") || tableHtml.contains("Pipelines"));
    assertTrue(tableHtml.contains("hop-doc-breadcrumb"));
    assertTrue(tableHtml.contains("id=\"hop-doc-theme\""));

    String pipeline =
        Files.readString(
            target.resolve("pipelines/validate-customer-360-bv.html"), StandardCharsets.UTF_8);
    assertTrue(pipeline.contains("update-customer-360"), "sibling workflows stay in the tree");
    assertTrue(pipeline.contains("<summary>Workflows</summary>"));
    assertTrue(pipeline.contains("<summary>Data Vault models</summary>"));
    assertTrue(pipeline.contains("hop-doc-breadcrumb"));
    assertTrue(pipeline.contains(">Pipelines</li>"));
  }

  @Test
  void documentsNestedPipelineFolder() throws Exception {
    Path source = temp.resolve("project");
    Path nested = source.resolve("pipelines").resolve("load");
    Files.createDirectories(nested);
    Files.writeString(
        nested.resolve("tiny-load.hpl"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <pipeline>
          <info>
            <name>tiny-load</name>
            <description>nested pipeline</description>
          </info>
        </pipeline>
        """);
    Path target = temp.resolve("docs");

    ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
    options.setSourceFolder(source.toString());
    options.setTargetFolder(target.toString());
    options.setProjectName("Nested Files");
    options.setIncludingCatalog(false);
    options.setIncludingMetadata(false);
    options.setDarkSvg(false);

    Variables variables = new Variables();
    variables.setVariable(ProjectDocumentationService.VAR_PROJECT_HOME, source.toString());

    ProjectDocumentationResult result =
        ProjectDocumentationService.generate(
            options, variables, new MemoryMetadataProvider(), LogChannel.GENERAL);
    assertTrue(result.getErrors() == 0, () -> result.getWarnings().toString());

    Path page = target.resolve("pipelines/pipelines/load/tiny-load.html");
    assertTrue(Files.isRegularFile(page), "expected " + page);
    String html = Files.readString(page, StandardCharsets.UTF_8);
    assertTrue(html.contains("tiny-load"));
    assertTrue(html.contains("Pipelines"));
    assertTrue(html.contains("load"), "folder segment should appear in the nav tree");

    String index = Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8);
    assertTrue(index.contains("tiny-load"));
  }

  @Test
  void reportsProgressAndHonorsCancel() throws Exception {
    Path source =
        Path.of("integration-tests/tests/multi-satellite-bv").toAbsolutePath().normalize();
    Path target = temp.resolve("cancel-docs");
    assumeTrue(Files.isDirectory(source), "integration-tests fixture missing");

    ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
    options.setSourceFolder(source.toString());
    options.setTargetFolder(target.toString());
    options.setProjectName("Cancel Test");
    options.setIncludingCatalog(false);
    options.setIncludingMetadata(false);
    options.setDarkSvg(false);

    Variables variables = new Variables();
    variables.setVariable(ProjectDocumentationService.VAR_PROJECT_HOME, source.toString());

    CancelAfterFirstWork monitor = new CancelAfterFirstWork();
    ProjectDocumentationResult result =
        ProjectDocumentationService.generate(
            options, variables, new MemoryMetadataProvider(), LogChannel.GENERAL, monitor);

    assertTrue(result.isCancelled());
    assertTrue(monitor.beginTaskWork > 0);
    assertTrue(monitor.workedTotal >= 1);
    assertTrue(Files.isRegularFile(target.resolve("index.html")));
  }

  @Test
  void relativizeUsedByNestedPages() {
    assertTrue(
        DocPaths.relativize("pipelines/a.html", "assets/js/search-index.js").startsWith("../"));
  }

  private static final class CancelAfterFirstWork implements IProgressMonitor {
    int beginTaskWork;
    int workedTotal;
    private boolean cancel;

    @Override
    public void beginTask(String message, int nrWorks) {
      beginTaskWork = nrWorks;
    }

    @Override
    public void subTask(String message) {}

    @Override
    public boolean isCanceled() {
      return cancel;
    }

    @Override
    public void worked(int nrWorks) {
      workedTotal += nrWorks;
      cancel = true;
    }

    @Override
    public void done() {}

    @Override
    public void setTaskName(String taskName) {}
  }
}
