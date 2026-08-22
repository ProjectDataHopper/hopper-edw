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
package org.hopper.edw.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowLoadOverviewFileWriterTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesConfiguredReportPathWithVariables() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/workspace/retail-example");

    assertEquals(
        "/workspace/retail-example/reports/retail-dv-initial-report.md",
        WorkflowLoadOverviewFileWriter.resolveConfiguredReportPath(
            "${PROJECT_HOME}/reports",
            "retail-dv-initial-report",
            WorkflowLoadOverviewFileWriter.MARKDOWN_EXTENSION,
            variables));
  }

  @Test
  void resolvesExistingConfiguredReport(@TempDir Path tempDir) throws Exception {
    Path report = tempDir.resolve("overview.md");
    Files.writeString(report, "# Overview", StandardCharsets.UTF_8);

    assertEquals(
        report.toString(),
        WorkflowLoadOverviewFileWriter.resolveExistingReportPath(
            tempDir.toString(),
            "overview",
            WorkflowLoadOverviewFileWriter.MARKDOWN_EXTENSION,
            "run-retail-initial",
            new Variables()));
  }

  @Test
  void findsLatestWorkflowPrefixedReportWhenBaseNameMissing(@TempDir Path tempDir)
      throws Exception {
    Path older = tempDir.resolve("run-retail-initial-11111111-20260101-120000.md");
    Path newer = tempDir.resolve("run-retail-initial-22222222-20260201-120000.md");
    Files.writeString(older, "older", StandardCharsets.UTF_8);
    Thread.sleep(20);
    Files.writeString(newer, "newer", StandardCharsets.UTF_8);

    assertEquals(
        newer.toString(),
        WorkflowLoadOverviewFileWriter.resolveExistingReportPath(
            tempDir.toString(),
            null,
            WorkflowLoadOverviewFileWriter.MARKDOWN_EXTENSION,
            "run-retail-initial",
            new Variables()));
  }

  @Test
  void returnsNullWhenNoMatchingReportExists(@TempDir Path tempDir) throws HopException {
    assertNull(
        WorkflowLoadOverviewFileWriter.resolveExistingReportPath(
            tempDir.toString(),
            "missing-report",
            WorkflowLoadOverviewFileWriter.HTML_EXTENSION,
            "run-retail-initial",
            new Variables()));
  }

  @Test
  void buildReportPathAddsSeparatorWhenMissing() {
    assertTrue(
        WorkflowLoadOverviewFileWriter.buildReportPath("/tmp/reports", "overview", ".html")
            .endsWith("/tmp/reports/overview.html"));
  }

  @Test
  void returnsNullForUnresolvedVariablePaths() {
    assertNull(WorkflowLoadOverviewFileWriter.toLocalPath("${PROJECT_HOME}/reports/overview.md"));
  }
}
