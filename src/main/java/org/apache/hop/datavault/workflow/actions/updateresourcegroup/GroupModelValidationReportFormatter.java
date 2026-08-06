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
    String md = formatMarkdown(report);
    // Lightweight HTML wrapper (no external Markdown dependency).
    String escaped =
        md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>\n");
    return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/><title>Model validation "
        + Const.NVL(report != null ? report.groupName() : "", "")
        + "</title></head><body>\n<pre style=\"white-space:pre-wrap;font-family:system-ui,sans-serif\">"
        + escaped
        + "</pre>\n</body></html>\n";
  }

  private static String escapeMd(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|");
  }
}
