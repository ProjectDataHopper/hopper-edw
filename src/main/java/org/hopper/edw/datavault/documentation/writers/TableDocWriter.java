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
package org.hopper.edw.datavault.documentation.writers;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.TableDoc;
import org.hopper.edw.datavault.documentation.model.TableDoc.ColumnDoc;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;
import org.hopper.edw.datavault.documentation.render.MarkdownNotesRenderer;
import org.hopper.edw.datavault.lineage.FieldContribution;
import org.hopper.edw.datavault.lineage.FieldLineage;
import org.hopper.edw.datavault.lineage.LineageReason;
import org.hopper.edw.datavault.lineage.TableLineage;
import org.hopper.edw.datavault.lineage.TableSourceRef;

/** Writes one HTML page per collected table (issue #25). */
public final class TableDocWriter {

  private TableDocWriter() {}

  public static void writeAll(DocumentationSite site) throws HopException {
    for (TableDoc table : site.getTables()) {
      write(site, table);
    }
  }

  private static void write(DocumentationSite site, TableDoc table) throws HopException {
    String htmlPath = DocPaths.tableHref(table.getLayer(), table.getLogicalName());
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Logical name", table.getLogicalName());
    HtmlPageWriter.row(rows, "Physical name", table.getPhysicalName());
    HtmlPageWriter.row(rows, "Type", table.getTableType());
    HtmlPageWriter.row(rows, "Layer", table.getLayer());
    HtmlPageWriter.row(rows, "Description", table.getDescription());
    if (!Utils.isEmpty(table.getGrain())) {
      HtmlPageWriter.row(rows, "Grain", table.getGrain());
    }
    if (!Utils.isEmpty(table.getModelPageHref())) {
      HtmlPageWriter.rowHtml(
          rows,
          "Model",
          HtmlPageWriter.link(
              DocPaths.relativize(htmlPath, table.getModelPageHref()),
              nvl(table.getModelName(), table.getModelPageHref())));
    }
    if (!Utils.isEmpty(table.getCatalogHref())) {
      HtmlPageWriter.rowHtml(
          rows,
          "Catalog",
          HtmlPageWriter.link(
              DocPaths.relativize(htmlPath, table.getCatalogHref()), "Record definition"));
    }
    StringBuilder body = new StringBuilder();
    body.append(HtmlPageWriter.propertyTable(rows));
    body.append(columnsSection(table));
    body.append(fieldDocumentationSection(table));
    body.append(lineageSection(htmlPath, table.getLineage()));
    PageSupport.writePage(
        site,
        htmlPath,
        table.getLogicalName(),
        nvl(table.getTableType(), "Table"),
        body.toString());
  }

  private static String columnsSection(TableDoc table) {
    if (table.getColumns().isEmpty()) {
      return "";
    }
    List<String> headers = List.of("Column", "Type", "Technical", "Description");
    List<List<String>> rows = new ArrayList<>();
    for (ColumnDoc column : table.getColumns()) {
      rows.add(
          List.of(
              nvl(column.getName(), ""),
              nvl(column.getDataType(), ""),
              column.isTechnical() ? "yes" : "",
              nvl(column.getDescription(), "")));
    }
    return "<section id=\"columns\"><h2>Columns</h2>\n"
        + HtmlPageWriter.dataTable(headers, rows, false)
        + "</section>\n";
  }

  private static String fieldDocumentationSection(TableDoc table) {
    StringBuilder html = new StringBuilder();
    boolean any = false;
    for (ColumnDoc column : table.getColumns()) {
      if (column == null
          || (Utils.isEmpty(column.getNotes()) && Utils.isEmpty(column.getRequirements()))) {
        continue;
      }
      if (!any) {
        html.append("<section id=\"field-docs\"><h2>Field notes and requirements</h2>\n");
        any = true;
      }
      html.append("<h3>").append(HtmlEscaper.escape(nvl(column.getName(), ""))).append("</h3>\n");
      if (!Utils.isEmpty(column.getNotes())) {
        html.append("<h4>Notes</h4>\n");
        html.append(MarkdownNotesRenderer.toHtml(column.getNotes()));
      }
      if (!Utils.isEmpty(column.getRequirements())) {
        html.append("<h4>Requirements</h4>\n");
        html.append(MarkdownNotesRenderer.toHtml(column.getRequirements()));
      }
    }
    if (any) {
      html.append("</section>\n");
    }
    return html.toString();
  }

  private static String lineageSection(String htmlPath, TableLineage lineage) {
    if (lineage == null) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"lineage\"><h2>Source-to-target lineage</h2>\n");
    if (!lineage.getSources().isEmpty()) {
      html.append("<h3>Sources</h3><ul>");
      for (TableSourceRef source : lineage.getSources()) {
        html.append("<li>")
            .append(HtmlEscaper.escape(nvl(source.getName(), "")))
            .append(" (")
            .append(HtmlEscaper.escape(source.getKind() != null ? source.getKind().name() : ""))
            .append(")</li>");
      }
      html.append("</ul>\n");
    }
    if (!lineage.getReasons().isEmpty()) {
      html.append("<h3>Reasons</h3><ul>");
      for (LineageReason reason : lineage.getReasons()) {
        html.append("<li>");
        if (reason.getCode() != null) {
          html.append("<code>")
              .append(HtmlEscaper.escape(reason.getCode().name()))
              .append("</code> ");
        }
        html.append(HtmlEscaper.escape(nvl(reason.getMessage(), "")));
        html.append("</li>");
      }
      html.append("</ul>\n");
    }
    List<List<String>> rows = new ArrayList<>();
    for (FieldLineage field : lineage.getFields()) {
      if (field == null) {
        continue;
      }
      StringBuilder contrib = new StringBuilder();
      for (FieldContribution contribution : field.getContributions()) {
        if (contrib.length() > 0) {
          contrib.append("; ");
        }
        contrib
            .append(nvl(contribution.getSourceName(), ""))
            .append('.')
            .append(nvl(contribution.getSourceFieldName(), ""))
            .append(" → ")
            .append(contribution.getTransform() != null ? contribution.getTransform().name() : "");
      }
      rows.add(
          List.of(
              nvl(field.getTargetFieldName(), ""),
              nvl(field.getDataType(), ""),
              contrib.toString()));
    }
    if (!rows.isEmpty()) {
      html.append("<h3>Field mappings</h3>\n");
      html.append(
          HtmlPageWriter.dataTable(List.of("Target field", "Type", "Contributions"), rows, false));
    }
    html.append("</section>\n");
    return html.toString();
  }

  private static String nvl(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }
}
