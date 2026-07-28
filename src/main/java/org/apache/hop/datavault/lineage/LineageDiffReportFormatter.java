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

package org.apache.hop.datavault.lineage;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/** Formats lineage drift for CI logs and Markdown validation reports. */
public final class LineageDiffReportFormatter {

  private LineageDiffReportFormatter() {}

  public static String formatLog(List<LineageDiffResult> results) {
    if (results == null || results.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Lineage drift check:").append(Const.CR);
    for (LineageDiffResult result : results) {
      if (result == null) {
        continue;
      }
      sb.append("  Model ")
          .append(nvl(result.getModelName()))
          .append(" (")
          .append(result.getLayer())
          .append(") baseline=")
          .append(nvl(result.getBaselineSource()))
          .append(result.isBaselineMissing() ? " [missing]" : "")
          .append(Const.CR);
      if (result.isEmpty()) {
        sb.append("    no lineage drift").append(Const.CR);
        continue;
      }
      for (LineageDiffEntry entry : result.getEntries()) {
        sb.append("    [")
            .append(entry.getSeverity())
            .append("/")
            .append(entry.getType())
            .append("] ")
            .append(entry.getMessage())
            .append(Const.CR);
      }
    }
    return sb.toString();
  }

  public static String formatMarkdown(List<LineageDiffResult> results) {
    if (results == null || results.isEmpty()) {
      return "";
    }
    StringBuilder md = new StringBuilder();
    md.append("## Lineage Drift\n\n");
    boolean any = false;
    for (LineageDiffResult result : results) {
      if (result == null) {
        continue;
      }
      any = true;
      md.append("### ")
          .append(escape(result.getModelName()))
          .append(" (`")
          .append(result.getLayer())
          .append("`)\n\n");
      md.append("- **Baseline:** `")
          .append(escape(result.getBaselineSource()))
          .append("`")
          .append(result.isBaselineMissing() ? " _(missing — first publish or no prior lineage)_" : "")
          .append("\n");
      if (result.isEmpty()) {
        md.append("- No lineage drift detected.\n\n");
        continue;
      }
      md.append("\n| Severity | Type | Table | Field | Baseline | Current | Message |\n");
      md.append("|----------|------|-------|-------|----------|---------|----------|\n");
      for (LineageDiffEntry entry : result.getEntries()) {
        md.append("| ")
            .append(entry.getSeverity())
            .append(" | ")
            .append(entry.getType())
            .append(" | ")
            .append(escape(entry.getTableName()))
            .append(" | ")
            .append(escape(entry.getFieldName()))
            .append(" | ")
            .append(escape(entry.getBaselineValue()))
            .append(" | ")
            .append(escape(entry.getCurrentValue()))
            .append(" | ")
            .append(escape(entry.getMessage()))
            .append(" |\n");
      }
      md.append('\n');
    }
    return any ? md.toString() : "";
  }

  public static String formatHtmlSection(List<LineageDiffResult> results) {
    String md = formatMarkdown(results);
    if (Utils.isEmpty(md)) {
      return "";
    }
    // Minimal HTML: reuse markdown-like structure as preformatted block for report embedding
    StringBuilder html = new StringBuilder();
    html.append("<h2>Lineage Drift</h2>");
    for (LineageDiffResult result : results) {
      if (result == null) {
        continue;
      }
      html.append("<h3>")
          .append(escHtml(result.getModelName()))
          .append(" (")
          .append(result.getLayer())
          .append(")</h3>");
      if (result.isEmpty()) {
        html.append("<p>No lineage drift detected.</p>");
        continue;
      }
      html.append(
          "<table><thead><tr><th>Severity</th><th>Type</th><th>Table</th><th>Field</th>"
              + "<th>Baseline</th><th>Current</th><th>Message</th></tr></thead><tbody>");
      for (LineageDiffEntry entry : result.getEntries()) {
        html.append("<tr><td>")
            .append(entry.getSeverity())
            .append("</td><td>")
            .append(entry.getType())
            .append("</td><td>")
            .append(escHtml(entry.getTableName()))
            .append("</td><td>")
            .append(escHtml(entry.getFieldName()))
            .append("</td><td>")
            .append(escHtml(entry.getBaselineValue()))
            .append("</td><td>")
            .append(escHtml(entry.getCurrentValue()))
            .append("</td><td>")
            .append(escHtml(entry.getMessage()))
            .append("</td></tr>");
      }
      html.append("</tbody></table>");
    }
    return html.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", " ");
  }

  private static String escHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
