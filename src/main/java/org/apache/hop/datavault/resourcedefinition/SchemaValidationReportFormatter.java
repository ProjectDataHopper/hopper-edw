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
import org.apache.hop.datavault.resourcedefinition.ValidationFindingFormatter.StructuredFinding;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;

/**
 * Formats schema impact simulation results for logs, Markdown CI artifacts, and HTML.
 *
 * <p>Human reports lift run-level compare context into a summary header, show field type
 * differences in a compact table, and present narrative findings (baseline gaps, target DDL, …) as
 * readable cards — never jammed into an "Actual Type" cell.
 */
public final class SchemaValidationReportFormatter {

  private static final Class<?> PKG = SchemaValidationReportFormatter.class;

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private static final int IMPACT_PREVIEW_LIMIT = 8;

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
    builder
        .append(
            ValidationFindingFormatter.describeCompareContext(
                result.compareMode(), result.baselineVersionUsed(), result.catalogVersionUsed()))
        .append(Const.CR);
    builder
        .append("What we compared: ")
        .append(
            ValidationFindingFormatter.describeWhatWeCompared(
                result.compareMode(), result.baselineVersionUsed(), result.catalogVersionUsed()))
        .append(Const.CR);
    builder
        .append("Expected (baseline): ")
        .append(
            ValidationFindingFormatter.describeExpectedSide(
                result.compareMode(), result.baselineVersionUsed(), result.catalogVersionUsed()))
        .append(Const.CR);
    builder
        .append("Actual: ")
        .append(
            ValidationFindingFormatter.describeActualSide(
                result.compareMode(), result.baselineVersionUsed(), result.catalogVersionUsed()))
        .append(Const.CR);
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
    List<IssueRow> rows = collectRows(report);
    StringBuilder md = new StringBuilder();
    md.append("# Data Vault Resource Definition Validation Report\n\n");
    appendMarkdownSummary(md, result, report);
    md.append("\n");

    List<IssueRow> critical =
        rows.stream().filter(r -> r.severity == IssueSeverity.BLOCKING).toList();
    List<IssueRow> warnings =
        rows.stream().filter(r -> r.severity == IssueSeverity.WARNING).toList();
    List<IssueRow> infos = rows.stream().filter(r -> r.severity == IssueSeverity.INFO).toList();

    if (!critical.isEmpty()) {
      md.append("## Critical issues\n\n");
      appendMarkdownFindings(md, critical);
    }
    if (!warnings.isEmpty()) {
      md.append("## Warnings\n\n");
      appendMarkdownFindings(md, warnings);
    }
    if (!infos.isEmpty()) {
      md.append("## Informational\n\n");
      appendMarkdownFindings(md, infos);
    }
    if (critical.isEmpty() && warnings.isEmpty() && infos.isEmpty()) {
      md.append("## No schema problems detected\n\n");
      md.append("All checked source contracts are in sync with the comparison baseline.\n\n");
    }

    String impactSummary = buildImpactSummary(rows);
    if (!Utils.isEmpty(impactSummary)) {
      md.append("## Downstream impact summary\n\n");
      md.append(impactSummary).append('\n');
    }

    String lineageMd = LineageDiffReportFormatter.formatMarkdown(result.lineageDiffs());
    if (!Utils.isEmpty(lineageMd)) {
      md.append(lineageMd);
    }

    md.append("## Required action\n\n");
    md.append(buildRequiredAction(result.status(), rows)).append('\n');
    return md.toString();
  }

  public static String formatHtml(SchemaImpactSimulationResult result) {
    if (result == null) {
      return "";
    }
    ValidationReport report = result.validationReport();
    List<IssueRow> rows = collectRows(report);
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>");
    html.append("<title>Data Vault Resource Definition Validation Report</title>");
    html.append(
        "<style>"
            + "body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2933;line-height:1.45}"
            + "h1,h2,h3{color:#102a43}h2{margin-top:1.6em}"
            + "table{border-collapse:collapse;width:100%;margin:12px 0}"
            + "th,td{border:1px solid #d9e2ec;padding:8px;text-align:left;vertical-align:top}"
            + "th{background:#f0f4f8}"
            + ".critical{color:#b91c1c}.warning{color:#b45309}.info{color:#0369a1}.pass{color:#047857}"
            + ".summary{background:#f7fafc;padding:16px 18px;border-radius:8px;margin-bottom:20px;"
            + "border:1px solid #d9e2ec}"
            + ".summary p{margin:0.35em 0}"
            + ".finding{border:1px solid #d9e2ec;border-radius:8px;padding:14px 16px;margin:12px 0;"
            + "background:#fff}"
            + ".finding.blocking{border-left:4px solid #b91c1c}"
            + ".finding.warning{border-left:4px solid #b45309}"
            + ".finding.info{border-left:4px solid #0369a1}"
            + ".finding h3{margin:0 0 8px 0;font-size:1.05rem}"
            + ".finding .meta{color:#627d98;font-size:0.9rem;margin-bottom:8px}"
            + ".finding .label{font-weight:600;color:#334e68;margin-top:10px;margin-bottom:2px}"
            + ".finding p{margin:0.25em 0}"
            + ".impact-list{margin:4px 0 0 1.1em;padding:0}"
            + "</style></head><body>");
    html.append("<h1>Data Vault Resource Definition Validation Report</h1>");
    appendHtmlSummary(html, result, report);

    List<IssueRow> critical =
        rows.stream().filter(r -> r.severity == IssueSeverity.BLOCKING).toList();
    List<IssueRow> warnings =
        rows.stream().filter(r -> r.severity == IssueSeverity.WARNING).toList();
    List<IssueRow> infos = rows.stream().filter(r -> r.severity == IssueSeverity.INFO).toList();

    if (rows.isEmpty()) {
      html.append("<h2>No schema problems detected</h2>");
      html.append("<p>All checked source contracts are in sync with the comparison baseline.</p>");
    } else {
      if (!critical.isEmpty()) {
        html.append("<h2 class=\"critical\">Critical issues</h2>");
        appendHtmlFindings(html, critical);
      }
      if (!warnings.isEmpty()) {
        html.append("<h2 class=\"warning\">Warnings</h2>");
        appendHtmlFindings(html, warnings);
      }
      if (!infos.isEmpty()) {
        html.append("<h2 class=\"info\">Informational</h2>");
        appendHtmlFindings(html, infos);
      }
    }

    String impactSummary = buildImpactSummary(rows);
    if (!Utils.isEmpty(impactSummary)) {
      html.append("<h2>Downstream impact summary</h2><ul>");
      for (String line : impactSummary.split("\n")) {
        String item = line.replaceFirst("^-\\s*", "").trim();
        if (!item.isEmpty()) {
          html.append("<li>").append(escapeHtml(item)).append("</li>");
        }
      }
      html.append("</ul>");
    }

    String lineageHtml = LineageDiffReportFormatter.formatHtmlSection(result.lineageDiffs());
    if (!Utils.isEmpty(lineageHtml)) {
      html.append(lineageHtml);
    }

    html.append("<h2>Required action</h2>");
    for (String paragraph : buildRequiredAction(result.status(), rows).split("\n")) {
      if (!Utils.isEmpty(paragraph.trim())) {
        html.append("<p>").append(escapeHtml(paragraph.trim())).append("</p>");
      }
    }
    html.append("</body></html>");
    return html.toString();
  }

  private static void appendMarkdownSummary(
      StringBuilder md, SchemaImpactSimulationResult result, ValidationReport report) {
    md.append("**Timestamp:** ")
        .append(result.timestamp() != null ? TIMESTAMP.format(result.timestamp()) : "")
        .append("  \n");
    md.append("**Resource group:** ")
        .append(escapeMdInline(report != null ? report.getGroupName() : ""))
        .append("  \n");
    md.append("**Status:** ").append(statusEmoji(result.status())).append("  \n");
    md.append("**What we compared:** ")
        .append(
            escapeMdInline(
                ValidationFindingFormatter.describeWhatWeCompared(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("  \n");
    md.append("**Expected (baseline):** ")
        .append(
            escapeMdInline(
                ValidationFindingFormatter.describeExpectedSide(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("  \n");
    md.append("**Actual:** ")
        .append(
            escapeMdInline(
                ValidationFindingFormatter.describeActualSide(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("  \n");
    if (result.compareMode() != null) {
      md.append("**Compare mode:** `").append(result.compareMode().name()).append("`  \n");
    }
  }

  private static void appendHtmlSummary(
      StringBuilder html, SchemaImpactSimulationResult result, ValidationReport report) {
    html.append("<div class=\"summary\">");
    html.append("<p><strong>Timestamp:</strong> ")
        .append(escapeHtml(result.timestamp() != null ? TIMESTAMP.format(result.timestamp()) : ""))
        .append("</p>");
    html.append("<p><strong>Resource group:</strong> ")
        .append(escapeHtml(report != null ? report.getGroupName() : ""))
        .append("</p>");
    html.append("<p><strong>Status:</strong> <span class=\"")
        .append(statusCss(result.status()))
        .append("\">")
        .append(escapeHtml(statusLabel(result.status())))
        .append("</span></p>");
    html.append("<p><strong>What we compared:</strong> ")
        .append(
            escapeHtml(
                ValidationFindingFormatter.describeWhatWeCompared(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("</p>");
    html.append("<p><strong>Expected (baseline):</strong> ")
        .append(
            escapeHtml(
                ValidationFindingFormatter.describeExpectedSide(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("</p>");
    html.append("<p><strong>Actual:</strong> ")
        .append(
            escapeHtml(
                ValidationFindingFormatter.describeActualSide(
                    result.compareMode(),
                    result.baselineVersionUsed(),
                    result.catalogVersionUsed())))
        .append("</p>");
    if (result.compareMode() != null) {
      html.append("<p><strong>Compare mode:</strong> <code>")
          .append(escapeHtml(result.compareMode().name()))
          .append("</code></p>");
    }
    html.append("</div>");
  }

  private static void appendMarkdownFindings(StringBuilder md, List<IssueRow> rows) {
    List<IssueRow> fieldRows = rows.stream().filter(IssueRow::fieldLevel).toList();
    List<IssueRow> narrativeRows = rows.stream().filter(r -> !r.fieldLevel).toList();
    if (!fieldRows.isEmpty()) {
      md.append("| Source | Column | Expected | Actual | Severity | Impact |\n");
      md.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
      for (IssueRow row : fieldRows) {
        md.append("| `")
            .append(escapeMdInline(row.sourceObject))
            .append("` | `")
            .append(escapeMdInline(row.column))
            .append("` | `")
            .append(escapeMdInline(row.expectedType))
            .append("` | `")
            .append(escapeMdInline(row.actualType))
            .append("` | ")
            .append(severityEmoji(row.severity))
            .append(" | ")
            .append(escapeMdInline(shortImpact(row.downstreamImpact)))
            .append(" |\n");
      }
      md.append('\n');
      for (IssueRow row : fieldRows) {
        if (!Utils.isEmpty(row.whatIsWrong) || !Utils.isEmpty(row.whatToDo)) {
          md.append("- **")
              .append(escapeMdInline(row.sourceObject))
              .append("** / `")
              .append(escapeMdInline(row.column))
              .append("`: ");
          if (!Utils.isEmpty(row.whatIsWrong)) {
            md.append(escapeMdInline(row.whatIsWrong));
          }
          if (!Utils.isEmpty(row.whatToDo)) {
            md.append(" — *What to do:* ").append(escapeMdInline(row.whatToDo));
          }
          md.append('\n');
        }
      }
      if (!fieldRows.isEmpty()) {
        md.append('\n');
      }
    }
    for (IssueRow row : narrativeRows) {
      md.append("### ")
          .append(severityEmoji(row.severity))
          .append(' ')
          .append(escapeMdInline(row.title))
          .append("\n\n");
      md.append("**Source:** `").append(escapeMdInline(row.sourceObject)).append("`  \n");
      if (row.kind != null) {
        md.append("**Code:** `").append(row.kind.name()).append("`  \n");
      }
      md.append('\n');
      if (!Utils.isEmpty(row.whatIsWrong)) {
        md.append("**What is wrong**\n\n");
        md.append(escapeMdProse(row.whatIsWrong)).append("\n\n");
      }
      if (!Utils.isEmpty(row.whatToDo)) {
        md.append("**What to do**\n\n");
        md.append(escapeMdProse(row.whatToDo)).append("\n\n");
      }
      if (!Utils.isEmpty(row.downstreamImpact)) {
        md.append("**Affected models**\n\n");
        md.append(escapeMdProse(formatImpactList(row.downstreamImpact))).append("\n\n");
      }
    }
  }

  private static void appendHtmlFindings(StringBuilder html, List<IssueRow> rows) {
    List<IssueRow> fieldRows = rows.stream().filter(IssueRow::fieldLevel).toList();
    List<IssueRow> narrativeRows = rows.stream().filter(r -> !r.fieldLevel).toList();
    if (!fieldRows.isEmpty()) {
      html.append(
          "<table><thead><tr><th>Source</th><th>Column</th><th>Expected</th>"
              + "<th>Actual</th><th>Severity</th><th>Impact</th></tr></thead><tbody>");
      for (IssueRow row : fieldRows) {
        html.append("<tr>");
        html.append("<td>").append(escapeHtml(row.sourceObject)).append("</td>");
        html.append("<td>").append(escapeHtml(row.column)).append("</td>");
        html.append("<td>").append(escapeHtml(row.expectedType)).append("</td>");
        html.append("<td>").append(escapeHtml(row.actualType)).append("</td>");
        html.append("<td class=\"")
            .append(severityCss(row.severity))
            .append("\">")
            .append(escapeHtml(severityLabel(row.severity)))
            .append("</td>");
        html.append("<td>").append(escapeHtml(shortImpact(row.downstreamImpact))).append("</td>");
        html.append("</tr>");
      }
      html.append("</tbody></table>");
    }
    for (IssueRow row : narrativeRows) {
      html.append("<div class=\"finding ").append(severityCss(row.severity)).append("\">");
      html.append("<h3 class=\"")
          .append(severityCss(row.severity))
          .append("\">")
          .append(escapeHtml(row.title))
          .append("</h3>");
      html.append("<div class=\"meta\">");
      html.append("<strong>Source:</strong> ").append(escapeHtml(row.sourceObject));
      if (row.kind != null) {
        html.append(" &nbsp;·&nbsp; <strong>Code:</strong> ").append(escapeHtml(row.kind.name()));
      }
      html.append(" &nbsp;·&nbsp; <strong>Severity:</strong> ")
          .append(escapeHtml(severityLabel(row.severity)));
      html.append("</div>");
      if (!Utils.isEmpty(row.whatIsWrong)) {
        html.append("<div class=\"label\">What is wrong</div><p>")
            .append(escapeHtml(row.whatIsWrong))
            .append("</p>");
      }
      if (!Utils.isEmpty(row.whatToDo)) {
        html.append("<div class=\"label\">What to do</div><p>")
            .append(escapeHtml(row.whatToDo))
            .append("</p>");
      }
      if (!Utils.isEmpty(row.downstreamImpact)) {
        html.append("<div class=\"label\">Affected models</div>");
        html.append("<ul class=\"impact-list\">");
        for (String item : impactItems(row.downstreamImpact, IMPACT_PREVIEW_LIMIT)) {
          html.append("<li>").append(escapeHtml(item)).append("</li>");
        }
        int total = countImpactItems(row.downstreamImpact);
        if (total > IMPACT_PREVIEW_LIMIT) {
          html.append("<li>")
              .append(escapeHtml("… and " + (total - IMPACT_PREVIEW_LIMIT) + " more"))
              .append("</li>");
        }
        html.append("</ul>");
      }
      html.append("</div>");
    }
  }

  static List<IssueRow> collectRows(ValidationReport report) {
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
        StructuredFinding structured = ValidationFindingFormatter.parseStructured(issue.message());
        boolean fieldLevel =
            !Utils.isEmpty(issue.fieldName())
                && (!Utils.isEmpty(types.expectedType) || !Utils.isEmpty(types.actualType));
        String whatIsWrong =
            !Utils.isEmpty(structured.found())
                ? structured.found()
                : ValidationFindingFormatter.shortTitle(issue.message());
        String whatToDo = Const.NVL(structured.why(), "");
        String title =
            fieldLevel
                ? (!Utils.isEmpty(whatIsWrong)
                    ? whatIsWrong
                    : ValidationFindingFormatter.humanTitle(issue.kind()))
                : ValidationFindingFormatter.humanTitle(issue.kind());
        if (Utils.isEmpty(title)) {
          title = issue.kind() != null ? issue.kind().name() : "Finding";
        }
        rows.add(
            new IssueRow(
                sourceObject,
                Const.NVL(issue.fieldName(), ""),
                types.expectedType,
                types.actualType,
                fieldLevel,
                issue.kind(),
                issue.severity(),
                title,
                whatIsWrong,
                whatToDo,
                Const.NVL(issue.downstreamImpact(), "")));
      }
    }
    return rows;
  }

  private static TypePair extractTypes(
      RecordDefinitionValidation validation, ValidationIssue issue) {
    String expectedType = "";
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
        int arrow = details.indexOf("->");
        if (arrow < 0) {
          arrow = details.indexOf('\u2192'); // →
        }
        if (arrow > 0) {
          expectedType = details.substring(0, arrow).replaceAll("(?i)type\\s*:?", "").trim();
          actualType = details.substring(arrow + (details.charAt(arrow) == '-' ? 2 : 1)).trim();
        } else if (!details.isEmpty()) {
          actualType = details;
        } else if (change.kind() != null) {
          actualType = change.kind().name();
        }
        break;
      }
    }
    // Never dump the full narrative finding into type columns.
    return new TypePair(expectedType, actualType);
  }

  static String buildRequiredAction(SimulationStatus status, List<IssueRow> rows) {
    if (status == SimulationStatus.PASS || rows == null || rows.isEmpty()) {
      return BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.Pass");
    }
    Set<IssueKind> kinds = new LinkedHashSet<>();
    for (IssueRow row : rows) {
      if (row != null && row.kind != null) {
        kinds.add(row.kind);
      }
    }
    List<String> steps = new ArrayList<>();
    if (kinds.contains(IssueKind.BASELINE_CONTRACT_MISSING)) {
      steps.add(
          BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.BaselineMissing"));
    }
    if (kinds.contains(IssueKind.WORKING_CONTRACT_MISSING)) {
      steps.add(
          BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.WorkingMissing"));
    }
    if (kinds.contains(IssueKind.SOURCE_UNAVAILABLE)
        || kinds.contains(IssueKind.SOURCE_UNREADABLE)) {
      steps.add(
          BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.LiveUnavailable"));
    }
    if (kinds.contains(IssueKind.TARGET_DDL_REQUIRED)) {
      steps.add(BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.TargetDdl"));
    }
    if (kinds.contains(IssueKind.FIELD_ADDED)
        || kinds.contains(IssueKind.FIELD_REMOVED)
        || kinds.contains(IssueKind.FIELD_TYPE_CHANGED)
        || kinds.contains(IssueKind.PRIMARY_KEY_CHANGED)
        || kinds.contains(IssueKind.MAPPING_BROKEN)
        || kinds.contains(IssueKind.MODEL_ATTRIBUTE_NARROWER)) {
      steps.add(
          BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.FieldContract"));
    }
    if (steps.isEmpty()) {
      if (status == SimulationStatus.CRITICAL_BLOCKED) {
        return BaseMessages.getString(
            PKG, "SchemaValidationReportFormatter.Action.CriticalGeneric");
      }
      return BaseMessages.getString(PKG, "SchemaValidationReportFormatter.Action.WarningGeneric");
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < steps.size(); i++) {
      if (i > 0) {
        builder.append('\n');
      }
      builder.append(i + 1).append(". ").append(steps.get(i));
    }
    return builder.toString();
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

  private static String shortImpact(String impact) {
    List<String> items = impactItems(impact, IMPACT_PREVIEW_LIMIT);
    if (items.isEmpty()) {
      return "";
    }
    int total = countImpactItems(impact);
    String joined = String.join("; ", items);
    if (total > IMPACT_PREVIEW_LIMIT) {
      return joined + "; … +" + (total - IMPACT_PREVIEW_LIMIT);
    }
    return joined;
  }

  private static String formatImpactList(String impact) {
    List<String> items = impactItems(impact, Integer.MAX_VALUE);
    StringBuilder builder = new StringBuilder();
    for (String item : items) {
      builder.append("- ").append(item).append('\n');
    }
    return builder.toString().trim();
  }

  private static List<String> impactItems(String impact, int limit) {
    List<String> items = new ArrayList<>();
    if (Utils.isEmpty(impact) || limit <= 0) {
      return items;
    }
    for (String part : impact.split(";")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        items.add(trimmed);
        if (items.size() >= limit) {
          break;
        }
      }
    }
    return items;
  }

  private static int countImpactItems(String impact) {
    if (Utils.isEmpty(impact)) {
      return 0;
    }
    int count = 0;
    for (String part : impact.split(";")) {
      if (!part.trim().isEmpty()) {
        count++;
      }
    }
    return count;
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
      return "❌";
    }
    if (severity == IssueSeverity.WARNING) {
      return "⚠️";
    }
    return "ℹ️";
  }

  private static String severityLabel(IssueSeverity severity) {
    if (severity == null) {
      return "";
    }
    return switch (severity) {
      case BLOCKING -> "Critical";
      case WARNING -> "Warning";
      case INFO -> "Info";
    };
  }

  private static String severityCss(IssueSeverity severity) {
    if (severity == IssueSeverity.BLOCKING) {
      return "critical";
    }
    if (severity == IssueSeverity.WARNING) {
      return "warning";
    }
    return "info";
  }

  private static String escapeMdInline(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", " ").replace("\r", "");
  }

  /** Preserve paragraph breaks for Markdown prose (not table cells). */
  private static String escapeMdProse(String value) {
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

  private record TypePair(String expectedType, String actualType) {}

  record IssueRow(
      String sourceObject,
      String column,
      String expectedType,
      String actualType,
      boolean fieldLevel,
      IssueKind kind,
      IssueSeverity severity,
      String title,
      String whatIsWrong,
      String whatToDo,
      String downstreamImpact) {}
}
