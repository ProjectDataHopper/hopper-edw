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

package org.apache.hop.datavault.resourcedefinition;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.lineage.LineageDiffReportFormatter;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;

/**
 * Formats schema impact simulation results for logs, Markdown CI artifacts, and HTML.
 *
 * <p>Markdown layout matches the issue #76 report template.
 */
public final class SchemaValidationReportFormatter {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private SchemaValidationReportFormatter() {}

  public static String formatLog(SchemaImpactSimulationResult result) {
    if (result == null || result.validationReport() == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder
        .append("Schema validation status: ")
        .append(statusLabel(result.status()))
        .append(Const.CR);
    if (!Utils.isEmpty(result.catalogVersionUsed())) {
      builder
          .append("Target catalog version: ")
          .append(result.catalogVersionUsed())
          .append(Const.CR);
    }
    if (!Utils.isEmpty(result.baselineVersionUsed())
        && !result.baselineVersionUsed().equals(result.catalogVersionUsed())) {
      builder
          .append("Baseline catalog version: ")
          .append(result.baselineVersionUsed())
          .append(Const.CR);
    }
    builder.append(ValidationReportFormatter.format(result.validationReport()));
    String lineageLog = LineageDiffReportFormatter.formatLog(result.lineageDiffs());
    if (!Utils.isEmpty(lineageLog)) {
      builder.append(Const.CR).append(lineageLog);
    }
    return builder.toString();
  }

  public static String formatMarkdown(SchemaImpactSimulationResult result) {
    if (result == null) {
      return "";
    }
    ValidationReport report = result.validationReport();
    StringBuilder md = new StringBuilder();
    md.append("# Data Vault DDL Validation Report\n");
    md.append("**Timestamp:** ")
        .append(result.timestamp() != null ? TIMESTAMP.format(result.timestamp()) : "")
        .append("  \n");
    md.append("**Resource Group:** ")
        .append(escapeMd(report != null ? report.getGroupName() : ""))
        .append("  \n");
    md.append("**Target Catalog Version:** ")
        .append(
            Utils.isEmpty(result.catalogVersionUsed())
                    && Utils.isEmpty(result.baselineVersionUsed())
                ? "(working tree)"
                : escapeMd(
                    firstNonEmpty(result.catalogVersionUsed(), result.baselineVersionUsed())))
        .append("  \n");
    if (result.compareMode() != null) {
      md.append("**Compare Mode:** `").append(result.compareMode().name()).append("`  \n");
    }
    md.append("**Status:** ").append(statusEmoji(result.status())).append("  \n\n");

    List<IssueRow> rows = collectRows(report);
    List<IssueRow> critical =
        rows.stream().filter(r -> r.severity == IssueSeverity.BLOCKING).toList();
    List<IssueRow> warnings =
        rows.stream().filter(r -> r.severity == IssueSeverity.WARNING).toList();

    if (!critical.isEmpty()) {
      md.append("## 🚨 Critical Schema Drift Detected\n");
      md.append(
          "The incoming source data schema does not match the expected Data Catalog anchor point.\n\n");
      appendMarkdownTable(md, critical);
      md.append('\n');
    }

    if (!warnings.isEmpty()) {
      md.append("## ⚠️ Warnings\n\n");
      appendMarkdownTable(md, warnings);
      md.append('\n');
    }

    if (critical.isEmpty() && warnings.isEmpty()) {
      md.append("## ✅ No Schema Drift Detected\n\n");
      md.append("All checked source contracts are in sync with the comparison baseline.\n\n");
    }

    String impactSummary = buildImpactSummary(rows);
    if (!Utils.isEmpty(impactSummary)) {
      md.append("## Downstream Impact Summary\n\n");
      md.append(impactSummary).append('\n');
    }

    String lineageMd = LineageDiffReportFormatter.formatMarkdown(result.lineageDiffs());
    if (!Utils.isEmpty(lineageMd)) {
      md.append(lineageMd);
    }

    md.append("## 🛠️ Required Action\n");
    if (result.status() == SimulationStatus.CRITICAL_BLOCKED) {
      md.append(
          "Deploy the missing DDL migrations to the target database before restarting this workflow, ");
      md.append("or update the Data Catalog mapping / model lineage to match the source system.\n");
    } else if (result.status() == SimulationStatus.WARNING) {
      md.append(
          "Review warning-level drift, lineage renames, and acknowledgements. Loads may continue if the gate is configured to fail only on critical issues.\n");
    } else {
      md.append("No action required; schema validation passed.\n");
    }
    return md.toString();
  }

  public static String formatHtml(SchemaImpactSimulationResult result) {
    if (result == null) {
      return "";
    }
    ValidationReport report = result.validationReport();
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>");
    html.append("<title>Data Vault DDL Validation Report</title>");
    html.append(
        "<style>body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2933}"
            + "h1,h2{color:#102a43}table{border-collapse:collapse;width:100%;margin:12px 0}"
            + "th,td{border:1px solid #d9e2ec;padding:8px;text-align:left;vertical-align:top}"
            + "th{background:#f0f4f8}.critical{color:#b91c1c}.warning{color:#b45309}"
            + ".pass{color:#047857}.summary{background:#f7fafc;padding:12px;border-radius:8px;margin-bottom:16px}"
            + "</style></head><body>");
    html.append("<h1>Data Vault DDL Validation Report</h1>");
    html.append("<div class=\"summary\">");
    html.append("<p><strong>Timestamp:</strong> ")
        .append(
            escapeHtml(
                result.timestamp() != null ? TIMESTAMP.format(result.timestamp()) : ""))
        .append("</p>");
    html.append("<p><strong>Resource Group:</strong> ")
        .append(escapeHtml(report != null ? report.getGroupName() : ""))
        .append("</p>");
    html.append("<p><strong>Target Catalog Version:</strong> ")
        .append(
            escapeHtml(
                Utils.isEmpty(result.catalogVersionUsed())
                        && Utils.isEmpty(result.baselineVersionUsed())
                    ? "(working tree)"
                    : firstNonEmpty(result.catalogVersionUsed(), result.baselineVersionUsed())))
        .append("</p>");
    html.append("<p><strong>Status:</strong> <span class=\"")
        .append(statusCss(result.status()))
        .append("\">")
        .append(escapeHtml(statusLabel(result.status())))
        .append("</span></p></div>");

    List<IssueRow> rows = collectRows(report);
    if (rows.isEmpty()) {
      html.append("<h2>No Schema Drift Detected</h2>");
      html.append("<p>All checked source contracts are in sync.</p>");
    } else {
      html.append("<h2>Schema Drift</h2>");
      html.append(
          "<table><thead><tr><th>Source Object</th><th>Column</th><th>Catalog Type</th>"
              + "<th>Actual Type</th><th>Severity</th><th>Downstream Impact</th></tr></thead><tbody>");
      for (IssueRow row : rows) {
        html.append("<tr>");
        html.append("<td>").append(escapeHtml(row.sourceObject)).append("</td>");
        html.append("<td>").append(escapeHtml(row.column)).append("</td>");
        html.append("<td>").append(escapeHtml(row.catalogType)).append("</td>");
        html.append("<td>").append(escapeHtml(row.actualType)).append("</td>");
        html.append("<td class=\"")
            .append(row.severity == IssueSeverity.BLOCKING ? "critical" : "warning")
            .append("\">")
            .append(escapeHtml(severityLabel(row.severity)))
            .append("</td>");
        html.append("<td>").append(escapeHtml(row.downstreamImpact)).append("</td>");
        html.append("</tr>");
      }
      html.append("</tbody></table>");
    }

    String impactSummary = buildImpactSummary(rows);
    if (!Utils.isEmpty(impactSummary)) {
      html.append("<h2>Downstream Impact Summary</h2><pre>")
          .append(escapeHtml(impactSummary.trim()))
          .append("</pre>");
    }

    String lineageHtml = LineageDiffReportFormatter.formatHtmlSection(result.lineageDiffs());
    if (!Utils.isEmpty(lineageHtml)) {
      html.append(lineageHtml);
    }

    html.append("<h2>Required Action</h2><p>");
    if (result.status() == SimulationStatus.CRITICAL_BLOCKED) {
      html.append(
          "Deploy the missing DDL migrations or update the Data Catalog mapping / model lineage before restarting this workflow.");
    } else if (result.status() == SimulationStatus.WARNING) {
      html.append("Review warning-level drift and lineage renames before promoting.");
    } else {
      html.append("No action required; schema validation passed.");
    }
    html.append("</p></body></html>");
    return html.toString();
  }

  private static void appendMarkdownTable(StringBuilder md, List<IssueRow> rows) {
    md.append(
        "| Source Object | Column | Catalog Type | Actual Type | Severity | Downstream Impact |\n");
    md.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
    for (IssueRow row : rows) {
      md.append("| `")
          .append(escapeMd(row.sourceObject))
          .append("` | `")
          .append(escapeMd(row.column))
          .append("` | `")
          .append(escapeMd(row.catalogType))
          .append("` | `")
          .append(escapeMd(row.actualType))
          .append("` | ")
          .append(severityEmoji(row.severity))
          .append(" | ")
          .append(escapeMd(row.downstreamImpact))
          .append(" |\n");
    }
  }

  private static List<IssueRow> collectRows(ValidationReport report) {
    List<IssueRow> rows = new ArrayList<>();
    if (report == null) {
      return rows;
    }
    for (RecordDefinitionValidation validation : report.getRecordValidations()) {
      if (validation == null) {
        continue;
      }
      String sourceObject =
          validation.key() != null
              ? validation.key().getNamespace() + "/" + validation.key().getName()
              : "?";
      for (ValidationIssue issue : validation.issues()) {
        if (issue == null) {
          continue;
        }
        TypePair types = extractTypes(validation, issue);
        rows.add(
            new IssueRow(
                sourceObject,
                Const.NVL(issue.fieldName(), ""),
                types.catalogType,
                types.actualType,
                issue.severity(),
                Const.NVL(issue.downstreamImpact(), "")));
      }
    }
    return rows;
  }

  private static TypePair extractTypes(
      RecordDefinitionValidation validation, ValidationIssue issue) {
    String catalogType = "";
    String actualType = "";
    if (validation.schemaDiff() != null
        && validation.schemaDiff().changes() != null
        && !Utils.isEmpty(issue.fieldName())) {
      for (RecordDefinitionSchemaDiffSupport.FieldChange change :
          validation.schemaDiff().changes()) {
        if (change == null || !issue.fieldName().equals(change.fieldName())) {
          continue;
        }
        String details = Const.NVL(change.details(), "");
        // Details often look like "Type: VARCHAR -> INTEGER" or similar i18n text.
        int arrow = details.indexOf("->");
        if (arrow < 0) {
          arrow = details.indexOf('\u2192'); // →
        }
        if (arrow > 0) {
          catalogType = details.substring(0, arrow).replaceAll("(?i)type\\s*:?", "").trim();
          actualType = details.substring(arrow + (details.charAt(arrow) == '-' ? 2 : 1)).trim();
        } else if (!details.isEmpty()) {
          actualType = details;
        } else {
          actualType = change.kind() != null ? change.kind().name() : "";
        }
        break;
      }
    }
    if (Utils.isEmpty(catalogType) && Utils.isEmpty(actualType) && !Utils.isEmpty(issue.message())) {
      actualType = issue.message();
    }
    return new TypePair(catalogType, actualType);
  }

  private static String buildImpactSummary(List<IssueRow> rows) {
    Set<String> labels = new LinkedHashSet<>();
    for (IssueRow row : rows) {
      if (!Utils.isEmpty(row.downstreamImpact)) {
        for (String part : row.downstreamImpact.split(";")) {
          String trimmed = part.trim();
          if (!trimmed.isEmpty()) {
            labels.add(trimmed);
          }
        }
      }
    }
    if (labels.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (String label : labels) {
      builder.append("- ").append(label).append('\n');
    }
    return builder.toString();
  }

  private static String statusEmoji(SimulationStatus status) {
    return switch (status != null ? status : SimulationStatus.PASS) {
      case CRITICAL_BLOCKED -> "❌ CRITICAL BLOCKED";
      case WARNING -> "⚠️ WARNINGS";
      case PASS -> "✅ PASS";
    };
  }

  private static String statusLabel(SimulationStatus status) {
    return switch (status != null ? status : SimulationStatus.PASS) {
      case CRITICAL_BLOCKED -> "CRITICAL BLOCKED";
      case WARNING -> "WARNINGS";
      case PASS -> "PASS";
    };
  }

  private static String statusCss(SimulationStatus status) {
    return switch (status != null ? status : SimulationStatus.PASS) {
      case CRITICAL_BLOCKED -> "critical";
      case WARNING -> "warning";
      case PASS -> "pass";
    };
  }

  private static String severityEmoji(IssueSeverity severity) {
    if (severity == IssueSeverity.BLOCKING) {
      return "❌ CRITICAL";
    }
    if (severity == IssueSeverity.WARNING) {
      return "⚠️ WARNING";
    }
    return "ℹ️ INFO";
  }

  private static String severityLabel(IssueSeverity severity) {
    if (severity == null) {
      return "";
    }
    return severity.name();
  }

  private static String escapeMd(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", " ");
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

  private static String firstNonEmpty(String a, String b) {
    if (!Utils.isEmpty(a)) {
      return a;
    }
    return b != null ? b : "";
  }

  private record TypePair(String catalogType, String actualType) {}

  private record IssueRow(
      String sourceObject,
      String column,
      String catalogType,
      String actualType,
      IssueSeverity severity,
      String downstreamImpact) {}
}
