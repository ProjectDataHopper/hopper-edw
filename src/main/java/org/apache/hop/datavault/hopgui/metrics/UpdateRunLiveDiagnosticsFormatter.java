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
package org.apache.hop.datavault.hopgui.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.widget.MarkdownAsciiTable;
import org.apache.hop.datavault.hopgui.widget.MarkdownAsciiTable.Align;
import org.apache.hop.datavault.metrics.live.PipelineLiveMetrics;
import org.apache.hop.datavault.metrics.live.TransformLiveMetrics;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveBottleneck;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.apache.hop.datavault.metrics.live.UpdateRunLiveState;
import org.apache.hop.i18n.BaseMessages;

/** Formats live update snapshots as markdown for the analysis dialog and clipboard export. */
public final class UpdateRunLiveDiagnosticsFormatter {

  private static final Class<?> PKG = UpdateRunLiveDiagnosticsFormatter.class;

  private UpdateRunLiveDiagnosticsFormatter() {}

  public static String formatMarkdown(UpdateRunLiveSnapshot snapshot) {
    if (snapshot == null) {
      return "";
    }
    StringBuilder markdown = new StringBuilder();
    markdown
        .append("## ")
        .append(BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.StatusSection"))
        .append("\n\n");
    appendBulletField(
        markdown,
        BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.Model"),
        snapshot.getModelName(),
        false);
    appendBulletField(
        markdown,
        BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.State"),
        formatState(snapshot.getOverallState()),
        true);
    appendBulletField(
        markdown,
        BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.Duration"),
        formatDuration(snapshot),
        false);
    if (!Utils.isEmpty(snapshot.getCurrentElementName())) {
      appendBulletField(
          markdown,
          BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.CurrentTable"),
          snapshot.getCurrentElementName(),
          false);
    }
    if (!Utils.isEmpty(snapshot.getStagingFolder())) {
      appendBulletField(
          markdown,
          BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.StagingFolder"),
          snapshot.getStagingFolder(),
          true);
    }
    UpdateRunLiveBottleneck bottleneck = snapshot.getPrimaryBottleneck();
    if (bottleneck != null && !Utils.isEmpty(bottleneck.getMessage())) {
      appendBulletField(
          markdown,
          BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.Bottleneck"),
          bottleneck.getMessage(),
          false);
    }
    markdown
        .append("\n_")
        .append(BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Summary.OpenExecutionHint"))
        .append("_\n");
    appendPipelinesSection(markdown, snapshot);
    return markdown.toString().trim();
  }

  public static String formatSummary(UpdateRunLiveSnapshot snapshot) {
    return formatMarkdown(snapshot);
  }

  public static String formatClipboardText(UpdateRunLiveSnapshot snapshot) {
    return formatMarkdown(snapshot);
  }

  private static void appendPipelinesSection(
      StringBuilder markdown, UpdateRunLiveSnapshot snapshot) {
    if (snapshot.getPipelines() == null || snapshot.getPipelines().isEmpty()) {
      return;
    }
    markdown
        .append("\n## ")
        .append(BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.PipelinesSection"))
        .append("\n\n");
    for (PipelineLiveMetrics pipeline : snapshot.getPipelines()) {
      if (pipeline == null) {
        continue;
      }
      markdown.append("### ").append(safe(pipeline.getPipelineName()));
      if (!Utils.isEmpty(pipeline.getElementName())) {
        markdown.append(" — ").append(pipeline.getElementName());
      }
      markdown.append(" ").append(inlineCode(formatState(pipeline.getState()))).append("\n\n");
      if (pipeline.getTransforms() == null || pipeline.getTransforms().isEmpty()) {
        continue;
      }
      appendTransformMetricsTable(markdown, pipeline.getTransforms());
      markdown.append(System.lineSeparator());
    }
  }

  private static void appendTransformMetricsTable(
      StringBuilder markdown, List<TransformLiveMetrics> transforms) {
    List<String> headers =
        List.of(
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Transform"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Plugin"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Running"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.In"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Out"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.BufIn"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.BufOut"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Stall"),
            BaseMessages.getString(PKG, "UpdateRunLiveDiagnostics.Markdown.Table.Status"));
    List<Align> aligns =
        List.of(
            Align.LEFT,
            Align.LEFT,
            Align.LEFT,
            Align.RIGHT,
            Align.RIGHT,
            Align.RIGHT,
            Align.RIGHT,
            Align.RIGHT,
            Align.LEFT);
    List<List<String>> rows = new ArrayList<>();
    for (TransformLiveMetrics transform : transforms) {
      if (transform == null) {
        continue;
      }
      rows.add(
          List.of(
              tableCell(safe(transform.getTransformName())),
              tableCell(safe(transform.getPluginId())),
              tableCell(formatRunning(transform.isRunning())),
              tableCell(Long.toString(transform.getRowsRead())),
              tableCell(Long.toString(transform.getRowsWritten())),
              tableCell(Long.toString(transform.getBufferIn())),
              tableCell(Long.toString(transform.getBufferOut())),
              tableCell(Long.toString(transform.getSecondsSinceLastProgress())),
              tableCell(safe(transform.getStatus()))));
    }
    markdown.append("```").append(System.lineSeparator());
    markdown.append(MarkdownAsciiTable.format(headers, aligns, rows));
    markdown.append(System.lineSeparator()).append("```").append(System.lineSeparator());
  }

  private static String formatRunning(boolean running) {
    return BaseMessages.getString(
        PKG,
        running
            ? "UpdateRunLiveDiagnostics.Markdown.Table.RunningYes"
            : "UpdateRunLiveDiagnostics.Markdown.Table.RunningNo");
  }

  private static String tableCell(String value) {
    return safe(value).replace("|", "\\|").replace('\n', ' ');
  }

  private static void appendBulletField(
      StringBuilder markdown, String label, String value, boolean codeValue) {
    if (Utils.isEmpty(value)) {
      return;
    }
    markdown
        .append("- **")
        .append(label)
        .append(":** ")
        .append(codeValue ? inlineCode(value) : safe(value))
        .append(System.lineSeparator());
  }

  private static String formatState(UpdateRunLiveState state) {
    return state != null ? state.name() : "-";
  }

  private static String inlineCode(String value) {
    return "`" + safe(value).replace("`", "'") + "`";
  }

  private static String formatDuration(UpdateRunLiveSnapshot snapshot) {
    if (snapshot.getStartedAt() == null) {
      return "-";
    }
    long endMillis =
        snapshot.getUpdatedAt() != null
            ? snapshot.getUpdatedAt().getTime()
            : System.currentTimeMillis();
    long seconds = Math.max(0L, (endMillis - snapshot.getStartedAt().getTime()) / 1000L);
    long minutes = TimeUnit.SECONDS.toMinutes(seconds);
    long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes);
    return minutes + "m " + remainingSeconds + "s";
  }

  private static String safe(String value) {
    return value != null ? value : "";
  }
}
