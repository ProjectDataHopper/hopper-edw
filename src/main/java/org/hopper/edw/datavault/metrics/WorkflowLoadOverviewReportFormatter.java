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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Formats workflow load overview reports for logs, Markdown, and HTML. */
public final class WorkflowLoadOverviewReportFormatter {

  private static final Class<?> PKG = WorkflowLoadOverviewReportFormatter.class;
  private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";

  private WorkflowLoadOverviewReportFormatter() {}

  public static String formatLog(
      WorkflowLoadOverviewReport report, boolean includePipelineDetail, boolean includeInsights) {
    if (report == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder
        .append(
            BaseMessages.getString(
                PKG,
                "WorkflowLoadOverviewReportFormatter.Log.Header",
                safe(report.getRootWorkflowName()),
                safe(report.getWorkflowExecutionId())))
        .append(Const.CR);
    appendWorkflowTimingLog(builder, report);
    builder
        .append(
            BaseMessages.getString(
                PKG,
                "WorkflowLoadOverviewReportFormatter.Log.Summary",
                Integer.toString(report.getModelCount()),
                Integer.toString(report.getPipelineCount()),
                formatDuration(report.getDurationMs()),
                Long.toString(report.getTotalTargetRowsInserted()),
                report.isSuccess()
                    ? BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Success")
                    : BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Failed")))
        .append(Const.CR);

    for (WorkflowLoadOverviewReport.ModelEntry model : safeModels(report)) {
      builder.append(Const.CR);
      builder
          .append(
              BaseMessages.getString(
                  PKG,
                  "WorkflowLoadOverviewReportFormatter.Log.ModelHeader",
                  safe(model.getModelType()).toUpperCase(Locale.ROOT),
                  safe(model.getModelName()),
                  formatTimestamp(model.getStartedAt()),
                  formatTimestamp(model.getFinishedAt()),
                  formatDuration(model.getDurationMs()),
                  Long.toString(model.getTargetRowsInserted()),
                  Long.toString(model.getErrors()),
                  model.isSuccess()
                      ? BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Success")
                      : BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Failed")))
          .append(Const.CR);
      if (includePipelineDetail && !model.getPipelines().isEmpty()) {
        for (String line : formatPipelineTable(model.getPipelines())) {
          builder.append(line).append(Const.CR);
        }
      }
      if (includeInsights && !model.getInsights().isEmpty()) {
        builder
            .append(BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Insights"))
            .append(Const.CR);
        for (WorkflowLoadOverviewReport.InsightEntry insight : model.getInsights()) {
          builder
              .append("  [")
              .append(safe(insight.getCode()))
              .append("] ")
              .append(safe(insight.getMessage()))
              .append(Const.CR);
        }
      }
    }
    return builder.toString();
  }

  public static String formatMarkdown(
      WorkflowLoadOverviewReport report, boolean includePipelineDetail, boolean includeInsights) {
    if (report == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder
        .append("# Workflow Load Overview — ")
        .append(escapeMarkdown(safe(report.getRootWorkflowName())))
        .append('\n')
        .append('\n');
    appendWorkflowTimingMarkdown(builder, report);
    builder
        .append("**Execution:** `")
        .append(escapeMarkdown(safe(report.getWorkflowExecutionId())))
        .append("`\n\n");
    appendMarkdownSummaryTable(builder, report);
    appendModelsSummaryTableMarkdown(builder, report);

    for (WorkflowLoadOverviewReport.ModelEntry model : safeModels(report)) {
      builder
          .append("## ")
          .append(escapeMarkdown(safe(model.getModelType()).toUpperCase(Locale.ROOT)))
          .append(" — ")
          .append(escapeMarkdown(safe(model.getModelName())))
          .append('\n')
          .append('\n');
      builder
          .append("- ")
          .append(escapeMarkdown(formatModelTypeLabel(model.getModelType())))
          .append('\n');
      appendModelTimingMarkdown(builder, model);
      builder
          .append("- Run id: `")
          .append(escapeMarkdown(safe(model.getLoadRunId())))
          .append("`\n\n");

      if (includePipelineDetail && !model.getPipelines().isEmpty()) {
        builder.append("### Pipelines\n\n");
        builder.append(
            "| Type | Table | Source | Read | Inserted | Errors | Duration |\n| --- | --- | --- | ---: | ---: | ---: | ---: |\n");
        for (WorkflowLoadOverviewReport.PipelineEntry pipeline : model.getPipelines()) {
          builder
              .append("| ")
              .append(escapeMarkdown(safe(pipeline.getElementType())))
              .append(" | ")
              .append(escapeMarkdown(safe(pipeline.getElementName())))
              .append(" | ")
              .append(escapeMarkdown(safe(pipeline.getSourceName())))
              .append(" | ")
              .append(pipeline.getSourceRowsRead())
              .append(" | ")
              .append(pipeline.getTargetRowsInserted())
              .append(" | ")
              .append(pipeline.getErrors())
              .append(" | ")
              .append(formatDuration(pipeline.getDurationMs()))
              .append(" |\n");
        }
        builder.append('\n');
      }

      if (includeInsights && !model.getInsights().isEmpty()) {
        builder.append("### Insights\n\n");
        for (WorkflowLoadOverviewReport.InsightEntry insight : model.getInsights()) {
          builder
              .append("- **")
              .append(escapeMarkdown(safe(insight.getCode())))
              .append("**: ")
              .append(escapeMarkdown(safe(insight.getMessage())))
              .append('\n');
        }
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  public static String formatHtml(
      WorkflowLoadOverviewReport report, boolean includePipelineDetail, boolean includeInsights) {
    if (report == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>");
    builder
        .append("<title>Workflow Load Overview — ")
        .append(escapeHtml(safe(report.getRootWorkflowName())))
        .append("</title>");
    builder.append(
        "<style>body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2933}"
            + "h1,h2,h3{color:#102a43}table{border-collapse:collapse;width:100%;margin:12px 0}"
            + "th,td{border:1px solid #d9e2ec;padding:8px;text-align:left}"
            + "th{background:#f0f4f8}.summary{background:#f7fafc;padding:12px;border-radius:8px}"
            + ".failed{color:#b91c1c}.success{color:#047857}</style></head><body>");
    builder
        .append("<h1>Workflow Load Overview — ")
        .append(escapeHtml(safe(report.getRootWorkflowName())))
        .append("</h1>");
    appendWorkflowTimingHtml(builder, report);
    builder
        .append("<p><strong>Execution:</strong> <code>")
        .append(escapeHtml(safe(report.getWorkflowExecutionId())))
        .append("</code></p>");
    builder.append("<div class=\"summary\">");
    appendHtmlSummaryTable(builder, report);
    appendModelsSummaryTableHtml(builder, report);
    builder.append("</div>");

    for (WorkflowLoadOverviewReport.ModelEntry model : safeModels(report)) {
      builder
          .append("<h2>")
          .append(escapeHtml(safe(model.getModelType()).toUpperCase(Locale.ROOT)))
          .append(" — ")
          .append(escapeHtml(safe(model.getModelName())))
          .append("</h2>");
      builder.append("<p>").append(escapeHtml(formatModelTypeLabel(model.getModelType())));
      appendModelTimingHtml(builder, model);
      builder
          .append("<br/>Run id: <code>")
          .append(escapeHtml(safe(model.getLoadRunId())))
          .append("</code></p>");

      if (includePipelineDetail && !model.getPipelines().isEmpty()) {
        builder
            .append("<h3>Pipelines</h3><table><thead><tr>")
            .append(
                "<th>Type</th><th>Table</th><th>Source</th><th>Read</th><th>Inserted</th><th>Errors</th><th>Duration</th>")
            .append("</tr></thead><tbody>");
        for (WorkflowLoadOverviewReport.PipelineEntry pipeline : model.getPipelines()) {
          builder
              .append("<tr><td>")
              .append(escapeHtml(safe(pipeline.getElementType())))
              .append("</td><td>")
              .append(escapeHtml(safe(pipeline.getElementName())))
              .append("</td><td>")
              .append(escapeHtml(safe(pipeline.getSourceName())))
              .append("</td><td>")
              .append(pipeline.getSourceRowsRead())
              .append("</td><td>")
              .append(pipeline.getTargetRowsInserted())
              .append("</td><td>")
              .append(pipeline.getErrors())
              .append("</td><td>")
              .append(escapeHtml(formatDuration(pipeline.getDurationMs())))
              .append("</td></tr>");
        }
        builder.append("</tbody></table>");
      }

      if (includeInsights && !model.getInsights().isEmpty()) {
        builder.append("<h3>Insights</h3><ul>");
        for (WorkflowLoadOverviewReport.InsightEntry insight : model.getInsights()) {
          builder
              .append("<li><strong>")
              .append(escapeHtml(safe(insight.getCode())))
              .append("</strong>: ")
              .append(escapeHtml(safe(insight.getMessage())))
              .append("</li>");
        }
        builder.append("</ul>");
      }
    }
    builder.append("</body></html>");
    return builder.toString();
  }

  private static List<String> formatPipelineTable(
      List<WorkflowLoadOverviewReport.PipelineEntry> pipelines) {
    String[] headers = {"Type", "Table", "Source", "Read", "Inserted", "Errors", "Duration"};
    List<String[]> rows = new ArrayList<>();
    for (WorkflowLoadOverviewReport.PipelineEntry pipeline : pipelines) {
      rows.add(
          new String[] {
            safe(pipeline.getElementType()),
            safe(pipeline.getElementName()),
            safe(pipeline.getSourceName()),
            Long.toString(pipeline.getSourceRowsRead()),
            Long.toString(pipeline.getTargetRowsInserted()),
            Long.toString(pipeline.getErrors()),
            formatDuration(pipeline.getDurationMs())
          });
    }
    int[] widths = new int[headers.length];
    for (int i = 0; i < headers.length; i++) {
      widths[i] = headers[i].length();
    }
    for (String[] row : rows) {
      for (int i = 0; i < row.length; i++) {
        widths[i] = Math.max(widths[i], row[i].length());
      }
    }
    List<String> lines = new ArrayList<>();
    lines.add(formatRow(headers, widths));
    for (String[] row : rows) {
      lines.add(formatRow(row, widths));
    }
    return lines;
  }

  private static String formatRow(String[] values, int[] widths) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        builder.append("  ");
      }
      builder.append(pad(values[i], widths[i]));
    }
    return builder.toString();
  }

  private static String pad(String value, int width) {
    if (value.length() >= width) {
      return value;
    }
    return value + " ".repeat(width - value.length());
  }

  static String formatDuration(long durationMs) {
    if (durationMs < 0) {
      durationMs = 0;
    }
    double totalSeconds = durationMs / 1000.0;
    long hours = (long) (totalSeconds / 3600.0);
    long minutes = (long) ((totalSeconds % 3600.0) / 60.0);
    double seconds = totalSeconds % 60.0;
    StringBuilder builder = new StringBuilder();
    if (hours > 0) {
      builder.append(hours).append('h').append(' ');
    }
    if (minutes > 0) {
      builder.append(minutes).append('m').append(' ');
    }
    builder.append(String.format(Locale.ROOT, "%.1fs", seconds));
    return builder.toString().trim();
  }

  private static void appendMarkdownSummaryTable(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    builder.append("## Summary\n\n");
    builder
        .append(
            "| Models | Pipelines | Duration | Rows inserted | Status |\n| ---: | ---: | ---: | ---: | --- |\n| ")
        .append(report.getModelCount())
        .append(" | ")
        .append(report.getPipelineCount())
        .append(" | ")
        .append(formatDuration(report.getDurationMs()))
        .append(" | ")
        .append(report.getTotalTargetRowsInserted())
        .append(" | ")
        .append(report.isSuccess() ? "success" : "failed")
        .append(" |\n\n");
  }

  private static void appendHtmlSummaryTable(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    builder
        .append("<h2>Summary</h2><table><thead><tr>")
        .append(
            "<th>Models</th><th>Pipelines</th><th>Duration</th><th>Rows inserted</th><th>Status</th>")
        .append("</tr></thead><tbody><tr><td>")
        .append(report.getModelCount())
        .append("</td><td>")
        .append(report.getPipelineCount())
        .append("</td><td>")
        .append(escapeHtml(formatDuration(report.getDurationMs())))
        .append("</td><td>")
        .append(report.getTotalTargetRowsInserted())
        .append("</td><td class=\"")
        .append(report.isSuccess() ? "success" : "failed")
        .append("\">")
        .append(report.isSuccess() ? "success" : "failed")
        .append("</td></tr></tbody></table>");
  }

  private static void appendModelsSummaryTableMarkdown(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    List<WorkflowLoadOverviewReport.ModelEntry> models = safeModels(report);
    if (models.isEmpty()) {
      return;
    }
    builder.append("## Models\n\n");
    builder.append(
        "| Layer | Model | Duration | Inserted | Errors | Success |\n| --- | --- | ---: | ---: | ---: | ---: |\n");
    for (WorkflowLoadOverviewReport.ModelEntry model : models) {
      builder
          .append("| ")
          .append(escapeMarkdown(safe(model.getModelType()).toUpperCase(Locale.ROOT)))
          .append(" | ")
          .append(escapeMarkdown(safe(model.getModelName())))
          .append(" | ")
          .append(formatDuration(model.getDurationMs()))
          .append(" | ")
          .append(model.getTargetRowsInserted())
          .append(" | ")
          .append(model.getErrors())
          .append(" | ")
          .append(model.isSuccess())
          .append(" |\n");
    }
    builder.append('\n');
  }

  private static void appendModelsSummaryTableHtml(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    List<WorkflowLoadOverviewReport.ModelEntry> models = safeModels(report);
    if (models.isEmpty()) {
      return;
    }
    builder
        .append("<h2>Models</h2><table><thead><tr>")
        .append(
            "<th>Layer</th><th>Model</th><th>Duration</th><th>Inserted</th><th>Errors</th><th>Success</th>")
        .append("</tr></thead><tbody>");
    for (WorkflowLoadOverviewReport.ModelEntry model : models) {
      builder
          .append("<tr><td>")
          .append(escapeHtml(safe(model.getModelType()).toUpperCase(Locale.ROOT)))
          .append("</td><td>")
          .append(escapeHtml(safe(model.getModelName())))
          .append("</td><td>")
          .append(escapeHtml(formatDuration(model.getDurationMs())))
          .append("</td><td>")
          .append(model.getTargetRowsInserted())
          .append("</td><td>")
          .append(model.getErrors())
          .append("</td><td>")
          .append(model.isSuccess() ? "true" : "false")
          .append("</td></tr>");
    }
    builder.append("</tbody></table>");
  }

  private static List<WorkflowLoadOverviewReport.ModelEntry> safeModels(
      WorkflowLoadOverviewReport report) {
    return report.getModels() != null ? report.getModels() : List.of();
  }

  static String formatTimestamp(Date value) {
    if (value == null) {
      return "";
    }
    return new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT).format(value);
  }

  static String formatModelTypeLabel(String modelType) {
    if (Utils.isEmpty(modelType)) {
      return "";
    }
    return switch (modelType.toLowerCase(Locale.ROOT)) {
      case "dv" -> BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.ModelType.Dv");
      case "bv" -> BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.ModelType.Bv");
      case "dm" -> BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.ModelType.Dm");
      default -> modelType;
    };
  }

  private static void appendWorkflowTimingLog(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    appendTimingLabelLine(
        builder, "WorkflowLoadOverviewReportFormatter.Started", report.getStartedAt());
    appendTimingLabelLine(
        builder, "WorkflowLoadOverviewReportFormatter.Finished", report.getFinishedAt());
  }

  private static void appendWorkflowTimingMarkdown(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    appendTimingLabelLineMarkdown(
        builder, "WorkflowLoadOverviewReportFormatter.Started", report.getStartedAt());
    appendTimingLabelLineMarkdown(
        builder, "WorkflowLoadOverviewReportFormatter.Finished", report.getFinishedAt());
    if (!Utils.isEmpty(formatTimestamp(report.getStartedAt()))
        || !Utils.isEmpty(formatTimestamp(report.getFinishedAt()))) {
      builder.append('\n');
    }
  }

  private static void appendWorkflowTimingHtml(
      StringBuilder builder, WorkflowLoadOverviewReport report) {
    String started = formatTimestamp(report.getStartedAt());
    String finished = formatTimestamp(report.getFinishedAt());
    if (Utils.isEmpty(started) && Utils.isEmpty(finished)) {
      return;
    }
    builder.append("<p>");
    if (!Utils.isEmpty(started)) {
      builder.append(
          escapeHtml(
              BaseMessages.getString(PKG, "WorkflowLoadOverviewReportFormatter.Started", started)));
    }
    if (!Utils.isEmpty(finished)) {
      if (!Utils.isEmpty(started)) {
        builder.append("<br/>");
      }
      builder.append(
          escapeHtml(
              BaseMessages.getString(
                  PKG, "WorkflowLoadOverviewReportFormatter.Finished", finished)));
    }
    builder.append("</p>");
  }

  private static void appendModelTimingMarkdown(
      StringBuilder builder, WorkflowLoadOverviewReport.ModelEntry model) {
    appendTimingBulletMarkdown(
        builder, "WorkflowLoadOverviewReportFormatter.Started", model.getStartedAt());
    appendTimingBulletMarkdown(
        builder, "WorkflowLoadOverviewReportFormatter.Finished", model.getFinishedAt());
  }

  private static void appendModelTimingHtml(
      StringBuilder builder, WorkflowLoadOverviewReport.ModelEntry model) {
    String started = formatTimestamp(model.getStartedAt());
    String finished = formatTimestamp(model.getFinishedAt());
    if (!Utils.isEmpty(started)) {
      builder
          .append("<br/>")
          .append(
              escapeHtml(
                  BaseMessages.getString(
                      PKG, "WorkflowLoadOverviewReportFormatter.Started", started)));
    }
    if (!Utils.isEmpty(finished)) {
      builder
          .append("<br/>")
          .append(
              escapeHtml(
                  BaseMessages.getString(
                      PKG, "WorkflowLoadOverviewReportFormatter.Finished", finished)));
    }
  }

  private static void appendTimingLabelLine(StringBuilder builder, String messageKey, Date value) {
    String timestamp = formatTimestamp(value);
    if (Utils.isEmpty(timestamp)) {
      return;
    }
    builder.append(BaseMessages.getString(PKG, messageKey, timestamp)).append(Const.CR);
  }

  private static void appendTimingLabelLineMarkdown(
      StringBuilder builder, String messageKey, Date value) {
    String timestamp = formatTimestamp(value);
    if (Utils.isEmpty(timestamp)) {
      return;
    }
    builder.append(BaseMessages.getString(PKG, messageKey, timestamp)).append('\n');
  }

  private static void appendTimingBulletMarkdown(
      StringBuilder builder, String messageKey, Date value) {
    String timestamp = formatTimestamp(value);
    if (Utils.isEmpty(timestamp)) {
      return;
    }
    builder.append("- ").append(BaseMessages.getString(PKG, messageKey, timestamp)).append('\n');
  }

  private static String safe(String value) {
    return value != null ? value : "";
  }

  private static String escapeMarkdown(String value) {
    return value.replace("|", "\\|");
  }

  private static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
