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
package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

class WorkflowLoadOverviewReportFormatterTest {

  @Test
  void formattersIncludeModelPipelineAndInsightSections() {
    Date modelStartedAt = new Date(1_700_000_000_000L);
    Date modelFinishedAt = new Date(1_700_000_065_000L);
    String startedAtText = WorkflowLoadOverviewReportFormatter.formatTimestamp(modelStartedAt);
    String finishedAtText = WorkflowLoadOverviewReportFormatter.formatTimestamp(modelFinishedAt);

    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewReport.builder()
            .overviewId("overview-1")
            .workflowExecutionId("exec-1")
            .rootWorkflowName("run-retail-update")
            .metricsWorkflowName("update-retail-dv-bv-dm")
            .startedAt(modelStartedAt)
            .finishedAt(modelFinishedAt)
            .durationMs(65000L)
            .modelCount(1)
            .pipelineCount(1)
            .insightCount(1)
            .totalSourceRowsRead(10)
            .totalTargetRowsInserted(8)
            .success(true)
            .model(
                WorkflowLoadOverviewReport.ModelEntry.builder()
                    .sequenceNo(1)
                    .loadRunId("dv-run")
                    .modelType("dv")
                    .modelName("retail-360")
                    .pipelineCount(1)
                    .sourceRowsRead(10)
                    .targetRowsInserted(8)
                    .durationMs(65000L)
                    .startedAt(modelStartedAt)
                    .finishedAt(modelFinishedAt)
                    .success(true)
                    .pipeline(
                        WorkflowLoadOverviewReport.PipelineEntry.builder()
                            .elementType("hub")
                            .elementName("hub_customer")
                            .sourceRowsRead(10)
                            .targetRowsInserted(8)
                            .durationMs(4500L)
                            .build())
                    .insight(
                        WorkflowLoadOverviewReport.InsightEntry.builder()
                            .code("HIGH_TARGET_READ_RATIO")
                            .message("Check CDC path")
                            .build())
                    .build())
            .build();

    String log = WorkflowLoadOverviewReportFormatter.formatLog(report, true, true);
    String markdown = WorkflowLoadOverviewReportFormatter.formatMarkdown(report, true, true);
    String html = WorkflowLoadOverviewReportFormatter.formatHtml(report, true, true);

    assertTrue(log.contains("run-retail-update"));
    assertTrue(log.contains("hub_customer"));
    assertTrue(log.contains("HIGH_TARGET_READ_RATIO"));
    int executionIndex = markdown.indexOf("**Execution:**");
    assertTrue(executionIndex > 0);
    assertTrue(markdown.indexOf("Started: " + startedAtText) < executionIndex);
    assertTrue(markdown.indexOf("Finished: " + finishedAtText) < executionIndex);
    assertTrue(markdown.contains("# Workflow Load Overview"));
    assertTrue(markdown.contains("## Summary"));
    assertTrue(markdown.contains("| Models | Pipelines | Duration | Rows inserted | Status |"));
    assertTrue(markdown.contains("1m 5.0s"));
    assertTrue(markdown.contains("## Models"));
    assertTrue(markdown.contains("| Layer | Model | Duration | Inserted | Errors | Success |"));
    assertTrue(markdown.contains("| DV | retail-360 | 1m 5.0s |"));
    assertTrue(markdown.contains("- Data Vault model"));
    assertTrue(markdown.contains("- Started: " + startedAtText));
    assertTrue(markdown.contains("- Finished: " + finishedAtText));
    assertTrue(
        markdown.contains("| Type | Table | Source | Read | Inserted | Errors | Duration |"));
    assertTrue(markdown.contains("| hub | hub_customer |"));
    assertTrue(markdown.contains("4.5s"));
    assertTrue(markdown.contains("### Pipelines"));
    assertTrue(markdown.contains("### Insights"));
    assertTrue(html.contains("<!DOCTYPE html>"));
    assertTrue(html.contains("<h2>Models</h2>"));
    assertTrue(html.contains("Started: " + startedAtText));
    assertTrue(html.contains("Finished: " + finishedAtText));
    assertTrue(
        html.indexOf("Started: " + startedAtText) < html.indexOf("<strong>Execution:</strong>"));
    assertTrue(html.contains("Data Vault model"));
    assertTrue(html.contains("hub_customer"));
    assertTrue(html.contains("HIGH_TARGET_READ_RATIO"));
  }

  @Test
  void formatModelTypeLabelMapsKnownModelTypes() {
    assertEquals(
        "Data Vault model", WorkflowLoadOverviewReportFormatter.formatModelTypeLabel("dv"));
    assertEquals(
        "Business Vault model", WorkflowLoadOverviewReportFormatter.formatModelTypeLabel("bv"));
    assertEquals(
        "Dimensional model", WorkflowLoadOverviewReportFormatter.formatModelTypeLabel("dm"));
  }

  @Test
  void formatDurationRendersHoursMinutesAndSecondsWithOneDecimal() {
    assertEquals("1m 5.0s", WorkflowLoadOverviewReportFormatter.formatDuration(65000L));
    assertEquals("5m 35.0s", WorkflowLoadOverviewReportFormatter.formatDuration(335000L));
    assertEquals("7.0s", WorkflowLoadOverviewReportFormatter.formatDuration(7000L));
    assertEquals("1h 1m 1.5s", WorkflowLoadOverviewReportFormatter.formatDuration(3_661_500L));
    assertEquals("1h 1.5s", WorkflowLoadOverviewReportFormatter.formatDuration(3_601_500L));
    assertEquals("0.0s", WorkflowLoadOverviewReportFormatter.formatDuration(0L));
  }
}
