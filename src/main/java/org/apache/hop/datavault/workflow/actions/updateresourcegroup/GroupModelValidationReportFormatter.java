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

import org.apache.hop.core.Const;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.AggregatedFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.FindingIssue;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.ModelFinding;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.GroupModelValidationReport.Severity;

/** Formats {@link GroupModelValidationReport} as Markdown, HTML, or compact log lines. */
public final class GroupModelValidationReportFormatter {

  private GroupModelValidationReportFormatter() {}

  public static String formatLog(GroupModelValidationReport report, boolean includeWarnings) {
    if (report == null) {
      return "";
    }
    StringBuilder log = new StringBuilder();
    log.append("Group model validation status: ")
        .append(report.status())
        .append(" | models=")
        .append(report.modelsChecked())
        .append(" | errors=")
        .append(report.totalErrors())
        .append(" | sharedWarnings=")
        .append(report.uniqueSharedWarnings())
        .append(" | sharedErrors=")
        .append(report.uniqueSharedErrors())
        .append(" | parallelism=")
        .append(report.parallelism())
        .append('\n');

    for (AggregatedFinding finding : report.sharedFindings()) {
      if (finding.severity() == Severity.WARNING && !includeWarnings) {
        continue;
      }
      log.append(finding.severity())
          .append(" [shared, ")
          .append(finding.modelCount())
          .append(" models]: ")
          .append(finding.message())
          .append('\n');
    }
    for (ModelFinding model : report.modelFindings()) {
      String label = model.layer() + " " + model.modelFile();
      if (model.loadFailure() != null && !model.loadFailure().isBlank()) {
        log.append("ERROR [")
            .append(label)
            .append("]: load/check failed: ")
            .append(model.loadFailure())
            .append('\n');
      }
      for (FindingIssue issue : model.issues()) {
        if (issue.severity() == Severity.WARNING && !includeWarnings) {
          continue;
        }
        log.append(issue.severity())
            .append(" [")
            .append(label)
            .append("]: ")
            .append(issue.message())
            .append('\n');
      }
    }
    if (!includeWarnings && report.uniqueSharedWarnings() > 0) {
      log.append("Suppressed ")
          .append(report.uniqueSharedWarnings())
          .append(" unique shared warning(s) (ignore warnings enabled)\n");
    }
    return log.toString().trim();
  }

  public static String formatMarkdown(GroupModelValidationReport report) {
    if (report == null) {
      return "";
    }
    StringBuilder md = new StringBuilder();
    md.append("# Model validation: ")
        .append(escapeMd(Const.NVL(report.groupName(), "")))
        .append("\n\n");
    md.append("**Status:** ").append(report.status()).append("  \n");
    md.append("**Models checked:** ").append(report.modelsChecked()).append("  \n");
    md.append("**Models with errors:** ").append(report.modelsWithErrors()).append("  \n");
    md.append("**Total errors:** ").append(report.totalErrors()).append("  \n");
    md.append("**Unique shared warnings:** ").append(report.uniqueSharedWarnings()).append("  \n");
    md.append("**Unique shared errors:** ").append(report.uniqueSharedErrors()).append("  \n");
    md.append("**Parallelism:** ").append(report.parallelism()).append("  \n");
    if (report.startedAt() != null && report.finishedAt() != null) {
      long ms = Math.max(0, report.finishedAt().toEpochMilli() - report.startedAt().toEpochMilli());
      md.append("**Duration:** ").append(ms).append(" ms\n");
    }
    md.append('\n');

    md.append("## Shared environment findings\n\n");
    if (report.sharedFindings().isEmpty()) {
      md.append("_None_\n\n");
    } else {
      md.append("| Severity | Models | Message |\n");
      md.append("|----------|--------|--------|\n");
      for (AggregatedFinding finding : report.sharedFindings()) {
        md.append("| ")
            .append(finding.severity())
            .append(" | ")
            .append(finding.modelCount())
            .append(" | ")
            .append(escapeMd(finding.message()).replace("\n", " "))
            .append(" |\n");
      }
      md.append('\n');
    }

    md.append("## Model-specific findings\n\n");
    if (report.modelFindings().isEmpty()) {
      md.append("_None_\n\n");
    } else {
      for (ModelFinding model : report.modelFindings()) {
        md.append("### ")
            .append(escapeMd(model.layer()))
            .append(" ")
            .append(escapeMd(model.modelFile()))
            .append("\n\n");
        if (model.loadFailure() != null && !model.loadFailure().isBlank()) {
          md.append("- **ERROR** load/check failed: ")
              .append(escapeMd(model.loadFailure()))
              .append('\n');
        }
        for (FindingIssue issue : model.issues()) {
          md.append("- **")
              .append(issue.severity())
              .append("** ")
              .append(escapeMd(issue.message()))
              .append('\n');
        }
        md.append('\n');
      }
    }

    int clean = report.modelsChecked() - report.modelFindings().size();
    if (clean < 0) {
      clean = 0;
    }
    md.append("## Models without model-specific findings\n\n");
    md.append(clean).append(" of ").append(report.modelsChecked()).append(" model(s)\n");
    return md.toString();
  }

  public static String formatHtml(GroupModelValidationReport report) {
    if (report == null) {
      return "";
    }
    String group = Const.NVL(report.groupName(), "");
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"/>");
    html.append("<title>Model validation: ").append(escapeHtml(group)).append("</title>");
    html.append(
        "<style>"
            + "body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2933;line-height:1.45}"
            + "h1,h2,h3{color:#102a43}h2{margin-top:1.6em}"
            + "table{border-collapse:collapse;width:100%;margin:12px 0}"
            + "th,td{border:1px solid #d9e2ec;padding:8px;text-align:left;vertical-align:top}"
            + "th{background:#f0f4f8}"
            + ".summary{background:#f7fafc;padding:16px 18px;border-radius:8px;margin-bottom:20px;"
            + "border:1px solid #d9e2ec}"
            + ".summary p{margin:0.35em 0}"
            + ".error{color:#b91c1c}.warning{color:#b45309}.pass{color:#047857}"
            + ".finding{border:1px solid #d9e2ec;border-radius:8px;padding:12px 14px;margin:10px 0;"
            + "background:#fff}"
            + ".finding.error{border-left:4px solid #b91c1c}"
            + ".finding.warning{border-left:4px solid #b45309}"
            + "ul{margin:6px 0 0 1.2em}"
            + "</style></head><body>\n");
    html.append("<h1>Model validation: ").append(escapeHtml(group)).append("</h1>\n");
    html.append("<div class=\"summary\">\n");
    html.append("<p><strong>Status:</strong> ")
        .append(statusClassSpan(report.status().name()))
        .append("</p>\n");
    html.append("<p><strong>Models checked:</strong> ")
        .append(report.modelsChecked())
        .append("</p>\n");
    html.append("<p><strong>Models with errors:</strong> ")
        .append(report.modelsWithErrors())
        .append("</p>\n");
    html.append("<p><strong>Total errors:</strong> ")
        .append(report.totalErrors())
        .append("</p>\n");
    html.append("<p><strong>Unique shared warnings:</strong> ")
        .append(report.uniqueSharedWarnings())
        .append("</p>\n");
    html.append("<p><strong>Unique shared errors:</strong> ")
        .append(report.uniqueSharedErrors())
        .append("</p>\n");
    html.append("<p><strong>Parallelism:</strong> ")
        .append(report.parallelism())
        .append("</p>\n");
    if (report.startedAt() != null && report.finishedAt() != null) {
      long ms =
          Math.max(0, report.finishedAt().toEpochMilli() - report.startedAt().toEpochMilli());
      html.append("<p><strong>Duration:</strong> ").append(ms).append(" ms</p>\n");
    }
    html.append("</div>\n");

    html.append("<h2>Shared environment findings</h2>\n");
    if (report.sharedFindings().isEmpty()) {
      html.append("<p><em>None</em></p>\n");
    } else {
      html.append("<table>\n<thead><tr><th>Severity</th><th>Models</th><th>Message</th></tr></thead>\n<tbody>\n");
      for (AggregatedFinding finding : report.sharedFindings()) {
        html.append("<tr><td class=\"")
            .append(severityCss(finding.severity()))
            .append("\">")
            .append(escapeHtml(finding.severity().name()))
            .append("</td><td>")
            .append(finding.modelCount())
            .append("</td><td>")
            .append(escapeHtml(finding.message()))
            .append("</td></tr>\n");
      }
      html.append("</tbody></table>\n");
    }

    html.append("<h2>Model-specific findings</h2>\n");
    if (report.modelFindings().isEmpty()) {
      html.append("<p><em>None</em></p>\n");
    } else {
      for (ModelFinding model : report.modelFindings()) {
        boolean modelError = model.hasError();
        html.append("<div class=\"finding ")
            .append(modelError ? "error" : "warning")
            .append("\">\n");
        html.append("<h3>")
            .append(escapeHtml(model.layer()))
            .append(" ")
            .append(escapeHtml(model.modelFile()))
            .append("</h3>\n<ul>\n");
        if (model.loadFailure() != null && !model.loadFailure().isBlank()) {
          html.append("<li class=\"error\"><strong>ERROR</strong> load/check failed: ")
              .append(escapeHtml(model.loadFailure()))
              .append("</li>\n");
        }
        for (FindingIssue issue : model.issues()) {
          html.append("<li class=\"")
              .append(severityCss(issue.severity()))
              .append("\"><strong>")
              .append(escapeHtml(issue.severity().name()))
              .append("</strong> ")
              .append(escapeHtml(issue.message()))
              .append("</li>\n");
        }
        html.append("</ul>\n</div>\n");
      }
    }

    int clean = Math.max(0, report.modelsChecked() - report.modelFindings().size());
    html.append("<h2>Models without model-specific findings</h2>\n");
    html.append("<p>")
        .append(clean)
        .append(" of ")
        .append(report.modelsChecked())
        .append(" model(s)</p>\n");
    html.append("</body></html>\n");
    return html.toString();
  }

  private static String severityCss(Severity severity) {
    if (severity == Severity.ERROR) {
      return "error";
    }
    if (severity == Severity.WARNING) {
      return "warning";
    }
    return "";
  }

  private static String statusClassSpan(String status) {
    String css =
        switch (status) {
          case "FAILED" -> "error";
          case "WARNINGS" -> "warning";
          case "PASS" -> "pass";
          default -> "";
        };
    return "<span class=\"" + css + "\">" + escapeHtml(status) + "</span>";
  }

  private static String escapeMd(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|");
  }

  private static String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
