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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.AggregatedFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.Severity;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReportFileWriter.ReportFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupModelValidationReportFileWriterTest {

  @TempDir Path tempDir;

  @Test
  void htmlOnly_writesHtmlNotMarkdown() throws Exception {
    GroupModelValidationReport report =
        new GroupModelValidationReport(
            "g",
            Instant.now(),
            Instant.now(),
            4,
            2,
            0,
            0,
            1,
            0,
            List.of(
                new AggregatedFinding(
                    Severity.WARNING, "encoding issue", 2, List.of("a", "b"), "W|encoding")),
            List.of());

    List<String> written =
        GroupModelValidationReportFileWriter.write(
            tempDir.toString(), "html-only-report", report, ReportFormat.HTML, new Variables());

    assertEquals(1, written.size());
    assertTrue(written.getFirst().endsWith(".html"));
    assertFalse(Files.exists(tempDir.resolve("html-only-report.md")));
    String body = Files.readString(Path.of(written.getFirst()));
    assertTrue(body.contains("<!DOCTYPE html>"));
    assertTrue(body.contains("<table>"));
    assertFalse(body.contains("<pre"));
  }

  @Test
  void markdownOnly_writesMarkdownNotHtml() throws Exception {
    GroupModelValidationReport report =
        new GroupModelValidationReport(
            "g", Instant.now(), Instant.now(), 1, 1, 0, 0, 0, 0, List.of(), List.of());

    List<String> written =
        GroupModelValidationReportFileWriter.write(
            tempDir.toString(), "md-only-report", report, ReportFormat.MARKDOWN, new Variables());

    assertEquals(1, written.size());
    assertTrue(written.getFirst().endsWith(".md"));
    assertFalse(Files.exists(tempDir.resolve("md-only-report.html")));
  }
}
