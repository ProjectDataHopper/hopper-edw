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
package org.apache.hop.datavault.workflow.actions.endvaultupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.workflow.WorkflowMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionEndVaultUpdateTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void exposesMarkdownAndHtmlReferencedObjectDescriptions() {
    ActionEndVaultUpdate action = new ActionEndVaultUpdate();

    assertEquals("Markdown report", action.getReferencedObjectDescriptions()[0]);
    assertEquals("HTML report", action.getReferencedObjectDescriptions()[1]);
    assertEquals(
        BaseMessages.getString(
            ActionEndVaultUpdate.class,
            "ActionEndVaultUpdate.ReferencedObject.MarkdownDescription"),
        action.getReferencedObjectDescriptions()[0]);
    assertEquals(
        BaseMessages.getString(
            ActionEndVaultUpdate.class, "ActionEndVaultUpdate.ReferencedObject.HtmlDescription"),
        action.getReferencedObjectDescriptions()[1]);
  }

  @Test
  void enablesReferencedObjectsWhenConfiguredReportsExist(@TempDir Path tempDir) throws Exception {
    Path markdown = tempDir.resolve("retail-dv-update-report.md");
    Path html = tempDir.resolve("retail-dv-update-report.html");
    Files.writeString(markdown, "# Report", StandardCharsets.UTF_8);
    Files.writeString(html, "<html></html>", StandardCharsets.UTF_8);

    ActionEndVaultUpdate action = configuredAction(tempDir, "retail-dv-update-report");

    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN]);
    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_HTML]);
    Variables variables = actionVariables(tempDir);
    assertEquals(
        markdown.toString(),
        action
            .loadReferencedObject(ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN, null, variables)
            .getFilename());
    assertEquals(
        html.toString(),
        action
            .loadReferencedObject(ActionEndVaultUpdate.REFERENCED_OBJECT_HTML, null, variables)
            .getFilename());
  }

  @Test
  void disablesReferencedObjectsWhenReportFilesMissing(@TempDir Path tempDir) {
    ActionEndVaultUpdate action = configuredAction(tempDir, "missing-report");

    assertFalse(
        action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN]);
    assertFalse(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_HTML]);
  }

  @Test
  void disablesMarkdownWhenMarkdownReportNotConfigured(@TempDir Path tempDir) throws Exception {
    Files.writeString(
        tempDir.resolve("retail-dv-update-report.html"), "<html></html>", StandardCharsets.UTF_8);

    ActionEndVaultUpdate action = configuredAction(tempDir, "retail-dv-update-report");
    action.setWriteMarkdownReport(false);

    assertFalse(
        action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN]);
    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_HTML]);
  }

  @Test
  void enablesReferencedObjectsFromWorkflowFilenameWhenProjectHomeMissing(@TempDir Path tempDir)
      throws Exception {
    Path projectHome = tempDir.resolve("retail-example");
    Path reports = projectHome.resolve("reports");
    Path workflows = projectHome.resolve("workflows");
    Files.createDirectories(reports);
    Files.createDirectories(workflows);
    Files.writeString(
        reports.resolve("retail-dv-initial-report.md"), "# Report", StandardCharsets.UTF_8);
    Files.writeString(
        reports.resolve("retail-dv-initial-report.html"), "<html></html>", StandardCharsets.UTF_8);

    ActionEndVaultUpdate action = new ActionEndVaultUpdate();
    action.setWriteMarkdownReport(true);
    action.setWriteHtmlReport(true);
    action.setReportOutputFolder("${PROJECT_HOME}/reports");
    action.setReportFileBaseName("retail-dv-initial-report");

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setFilename(workflows.resolve("run-retail-initial.hwf").toString());
    action.setParentWorkflowMeta(workflowMeta);

    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN]);
    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_HTML]);
  }

  @Test
  void enablesReferencedObjectsForRetailInitialWorkflowReports() throws Exception {
    Path repoRoot = Paths.get("").toAbsolutePath();
    Path projectHome = repoRoot.resolve("retail-example");
    Path workflowFile = projectHome.resolve("workflows").resolve("run-retail-initial.hwf");
    Path markdownReport = projectHome.resolve("reports").resolve("retail-dv-initial-report.md");
    if (!Files.isRegularFile(workflowFile) || !Files.isRegularFile(markdownReport)) {
      return;
    }

    ActionEndVaultUpdate action = new ActionEndVaultUpdate();
    action.setWriteMarkdownReport(true);
    action.setWriteHtmlReport(true);
    action.setReportOutputFolder("${PROJECT_HOME}/reports");
    action.setReportFileBaseName("retail-dv-initial-report");

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setFilename(workflowFile.toString());
    action.setParentWorkflowMeta(workflowMeta);

    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN]);
    assertTrue(action.isReferencedObjectEnabled()[ActionEndVaultUpdate.REFERENCED_OBJECT_HTML]);
    assertEquals(
        markdownReport.toString(),
        action
            .loadReferencedObject(ActionEndVaultUpdate.REFERENCED_OBJECT_MARKDOWN, null, null)
            .getFilename());
  }

  @Test
  void supportsDrillDown() {
    assertTrue(new ActionEndVaultUpdate().supportsDrillDown());
  }

  private static ActionEndVaultUpdate configuredAction(Path tempDir, String baseName) {
    ActionEndVaultUpdate action = new ActionEndVaultUpdate();
    action.copyFrom(actionVariables(tempDir));
    action.setWriteMarkdownReport(true);
    action.setWriteHtmlReport(true);
    action.setReportOutputFolder("${REPORT_HOME}");
    action.setReportFileBaseName(baseName);

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName("run-retail-update");
    action.setParentWorkflowMeta(workflowMeta);
    return action;
  }

  private static Variables actionVariables(Path tempDir) {
    Variables variables = new Variables();
    variables.setVariable("REPORT_HOME", tempDir.toString());
    return variables;
  }
}
