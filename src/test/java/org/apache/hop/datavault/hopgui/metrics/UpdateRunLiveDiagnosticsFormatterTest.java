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
package org.apache.hop.datavault.hopgui.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.apache.hop.datavault.metrics.live.PipelineLiveMetrics;
import org.apache.hop.datavault.metrics.live.TransformLiveMetrics;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveState;
import org.junit.jupiter.api.Test;

class UpdateRunLiveDiagnosticsFormatterTest {

  @Test
  void formatsLiveUpdateAsMarkdownWithPipelines() {
    Date startedAt = new Date(System.currentTimeMillis() - 65_000L);
    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshot.builder()
            .metricsRunId("run-1")
            .modelName("retail-f-orders")
            .stagingFolder("/tmp/dv2/retail-f-orders/")
            .startedAt(startedAt)
            .updatedAt(new Date())
            .overallState(UpdateRunLiveState.RUNNING)
            .currentElementName("f_orders")
            .pipelines(
                List.of(
                    PipelineLiveMetrics.builder()
                        .pipelineName("dm-fact-f_orders")
                        .elementName("f_orders")
                        .state(UpdateRunLiveState.RUNNING)
                        .transforms(
                            List.of(
                                TransformLiveMetrics.builder()
                                    .transformName("Table Input")
                                    .pluginId("TableInput")
                                    .running(true)
                                    .rowsRead(100)
                                    .rowsWritten(95)
                                    .bufferIn(1)
                                    .bufferOut(0)
                                    .secondsSinceLastProgress(3)
                                    .status("Running")
                                    .build()))
                        .build()))
            .build();

    String markdown = UpdateRunLiveDiagnosticsFormatter.formatMarkdown(snapshot);

    assertTrue(markdown.contains("## Current status"));
    assertTrue(markdown.contains("**Model:** retail-f-orders"));
    assertTrue(markdown.contains("**State:** `RUNNING`"));
    assertTrue(markdown.contains("**Current table:** f_orders"));
    assertTrue(markdown.contains("## Pipelines"));
    assertTrue(markdown.contains("### dm-fact-f_orders — f_orders `RUNNING`"));
    assertTrue(markdown.contains("```"));
    assertTrue(markdown.contains("| Transform"));
    assertTrue(markdown.contains("| Table Input"));
    assertTrue(markdown.contains("TableInput"));
    assertTrue(markdown.contains("| yes"));
    assertTrue(markdown.contains("| 100 |") || markdown.contains("|        100 |"));
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);
    assertTrue(!rendered.displayText().isEmpty());
    assertTrue(!rendered.spans().isEmpty());
  }

  @Test
  void largePipelineTableKeepsMarkdownStyling() {
    List<TransformLiveMetrics> transforms = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      transforms.add(
          TransformLiveMetrics.builder()
              .transformName("Transform " + i)
              .pluginId("Plugin" + i)
              .running(i % 2 == 0)
              .rowsRead(i * 100L)
              .rowsWritten(i * 95L)
              .bufferIn(i % 3)
              .bufferOut(i % 2)
              .secondsSinceLastProgress(i % 10)
              .status("Running | with special `chars`")
              .build());
    }
    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshot.builder()
            .modelName("retail-f-orders")
            .startedAt(new Date())
            .updatedAt(new Date())
            .overallState(UpdateRunLiveState.RUNNING)
            .pipelines(
                List.of(
                    PipelineLiveMetrics.builder()
                        .pipelineName("dm-fact-f_orders")
                        .state(UpdateRunLiveState.RUNNING)
                        .transforms(transforms)
                        .build()))
            .build();

    RenderedMarkdown rendered =
        MarkdownStyleRenderer.render(UpdateRunLiveDiagnosticsFormatter.formatMarkdown(snapshot));

    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.HEADING_2));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE_BLOCK));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.BOLD));
    assertTrue(isSortedByStart(rendered.spans()));
    assertEquals(
        1L, rendered.spans().stream().filter(span -> span.kind() == SpanKind.CODE_BLOCK).count());
  }

  @Test
  void retailSnapshotKeepsCurrentStatusHeadingStyle() {
    List<TransformLiveMetrics> transforms = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      transforms.add(
          TransformLiveMetrics.builder()
              .transformName("transform-" + i)
              .pluginId("Plugin" + i)
              .running(i >= 6)
              .rowsRead(i == 0 ? 264_656L : 0L)
              .rowsWritten(0L)
              .status(i < 6 ? "Finished" : "Running")
              .build());
    }
    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshot.builder()
            .modelName("retail-360")
            .stagingFolder("/tmp/dv2/retail-360/")
            .startedAt(new Date())
            .updatedAt(new Date())
            .overallState(UpdateRunLiveState.RUNNING)
            .currentElementName("sat_lnk_order_line")
            .pipelines(
                List.of(
                    PipelineLiveMetrics.builder()
                        .pipelineName("0014-sat-sat_lnk_order_line-E2E-order-line")
                        .elementName("sat_lnk_order_line")
                        .state(UpdateRunLiveState.RUNNING)
                        .transforms(transforms)
                        .build()))
            .build();

    RenderedMarkdown rendered =
        MarkdownStyleRenderer.render(UpdateRunLiveDiagnosticsFormatter.formatMarkdown(snapshot));

    assertTrue(rendered.displayText().startsWith("Current status"));
    assertTrue(
        rendered.spans().stream()
            .anyMatch(
                span ->
                    span.kind() == SpanKind.HEADING_2
                        && span.start() == 0
                        && span.length() == "Current status".length()));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE));
    assertEquals(
        1L, rendered.spans().stream().filter(span -> span.kind() == SpanKind.CODE_BLOCK).count());
  }

  private static boolean isSortedByStart(List<MarkdownStyleRenderer.StyleSpan> spans) {
    int previousStart = -1;
    for (MarkdownStyleRenderer.StyleSpan span : spans) {
      if (span.start() < previousStart) {
        return false;
      }
      previousStart = span.start();
    }
    return true;
  }
}
