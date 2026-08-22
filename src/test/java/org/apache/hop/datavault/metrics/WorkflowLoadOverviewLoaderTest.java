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
package org.apache.hop.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowLoadOverviewLoaderTest {

  @Test
  void assembleReportOrdersModelsAndAggregatesTotals() {
    Date startedAt = new Date(1_700_000_000_000L);
    Date dvStarted = new Date(1_700_000_009_000L);
    Date dvFinished = new Date(1_700_000_010_000L);
    Date bvStarted = new Date(1_700_000_018_000L);
    Date bvFinished = new Date(1_700_000_020_000L);

    List<WorkflowLoadOverviewLoader.LoadRunRow> runs =
        List.of(
            new WorkflowLoadOverviewLoader.LoadRunRow(
                "bv-run",
                bvStarted,
                bvFinished,
                "bv",
                "retail-360",
                "update-retail-dv-bv-dm",
                true,
                0L),
            new WorkflowLoadOverviewLoader.LoadRunRow(
                "dv-run",
                dvStarted,
                dvFinished,
                "dv",
                "retail-360",
                "update-retail-dv-bv-dm",
                true,
                0L));

    Map<String, Long> durations = Map.of("dv-run", 1000L, "bv-run", 2000L);
    Map<String, List<WorkflowLoadOverviewReport.PipelineEntry>> pipelines =
        Map.of(
            "dv-run",
            List.of(
                WorkflowLoadOverviewReport.PipelineEntry.builder()
                    .pipelineName("load_hub_customer")
                    .elementType("hub")
                    .elementName("hub_customer")
                    .sourceRowsRead(10)
                    .targetRowsInserted(8)
                    .errors(0)
                    .durationMs(4500L)
                    .build()),
            "bv-run",
            List.of(
                WorkflowLoadOverviewReport.PipelineEntry.builder()
                    .elementType("scd2")
                    .elementName("bv_customer")
                    .sourceRowsRead(5)
                    .targetRowsInserted(4)
                    .errors(0)
                    .build()));
    Map<String, List<WorkflowLoadOverviewReport.InsightEntry>> insights =
        Map.of(
            "dv-run",
            List.of(
                WorkflowLoadOverviewReport.InsightEntry.builder()
                    .code("HIGH_TARGET_READ_RATIO")
                    .message("Check CDC path")
                    .build()));

    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewLoader.assembleReport(
            "exec-1", "run-retail-update", startedAt, runs, durations, pipelines, insights);

    assertEquals("exec-1", report.getWorkflowExecutionId());
    assertEquals("run-retail-update", report.getRootWorkflowName());
    assertEquals("update-retail-dv-bv-dm", report.getMetricsWorkflowName());
    assertEquals(2, report.getModelCount());
    assertEquals(2, report.getPipelineCount());
    assertEquals(1, report.getInsightCount());
    assertEquals(15, report.getTotalSourceRowsRead());
    assertEquals(12, report.getTotalTargetRowsInserted());
    assertEquals(bvFinished.getTime() - startedAt.getTime(), report.getDurationMs());
    assertTrue(report.isSuccess());
    assertEquals("dv", report.getModels().get(0).getModelType());
    assertEquals("bv", report.getModels().get(1).getModelType());
    assertEquals(dvStarted, report.getModels().get(0).getStartedAt());
    assertEquals(dvFinished, report.getModels().get(0).getFinishedAt());
    assertEquals(4500L, report.getModels().get(0).getPipelines().get(0).getDurationMs());
    assertEquals(1, report.getModels().get(0).getInsights().size());
  }

  @Test
  void resolveModelStartedAtFallsBackWhenStartedEqualsFinished() {
    Date finishedAt = new Date(1_700_000_010_000L);
    List<WorkflowLoadOverviewLoader.LoadRunRow> runs =
        List.of(
            new WorkflowLoadOverviewLoader.LoadRunRow(
                "dv-run",
                finishedAt,
                finishedAt,
                "dv",
                "retail-360",
                "update-retail-dv-bv-dm",
                true,
                0L));

    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewLoader.assembleReport(
            "exec-1", "run-retail-update", null, runs, Map.of("dv-run", 1000L), Map.of(), Map.of());

    assertEquals(new Date(finishedAt.getTime() - 1000L), report.getModels().get(0).getStartedAt());
  }

  @Test
  void resolveModelStartedAtFallsBackToFinishedAtMinusDuration() {
    Date finishedAt = new Date(1_700_000_010_000L);
    List<WorkflowLoadOverviewLoader.LoadRunRow> runs =
        List.of(
            new WorkflowLoadOverviewLoader.LoadRunRow(
                "dv-run",
                null,
                finishedAt,
                "dv",
                "retail-360",
                "update-retail-dv-bv-dm",
                true,
                0L));

    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewLoader.assembleReport(
            "exec-1", "run-retail-update", null, runs, Map.of("dv-run", 1000L), Map.of(), Map.of());

    assertEquals(new Date(finishedAt.getTime() - 1000L), report.getModels().get(0).getStartedAt());
  }

  @Test
  void modelTypeOrderPrefersDvBeforeBvBeforeDm() {
    assertEquals(
        0,
        WorkflowLoadOverviewLoader.modelTypeOrder(
            new WorkflowLoadOverviewLoader.LoadRunRow("a", null, null, "dv", "m", null, true, 0L)));
    assertEquals(
        1,
        WorkflowLoadOverviewLoader.modelTypeOrder(
            new WorkflowLoadOverviewLoader.LoadRunRow("a", null, null, "bv", "m", null, true, 0L)));
    assertEquals(
        2,
        WorkflowLoadOverviewLoader.modelTypeOrder(
            new WorkflowLoadOverviewLoader.LoadRunRow("a", null, null, "dm", "m", null, true, 0L)));
  }
}
