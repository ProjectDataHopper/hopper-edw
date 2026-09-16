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
package org.hopper.edw.datavault.busmatrix;

import java.util.function.Function;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;

/** Sticky HTML crosstab for project documentation. */
public final class BusMatrixHtmlRenderer {

  private BusMatrixHtmlRenderer() {}

  public static String table(BusMatrix matrix, Function<String, String> href) {
    BusMatrix safe = matrix != null ? matrix : new BusMatrix("", null, null, null);
    StringBuilder html = new StringBuilder();
    html.append(
        "<div class=\"hop-doc-table-wrap hop-doc-bus-matrix-wrap\"><table class=\"hop-doc-bus-matrix\">\n<thead><tr>");
    header(html, "Business", 1);
    header(html, "Level 1", 2);
    header(html, "Level 2", 3);
    header(html, "Level 3", 4);
    header(html, "Fact", 5);
    for (BusMatrixColumn column : safe.getColumns()) {
      html.append("<th class=\"hop-doc-bus-matrix-dim\" title=\"")
          .append(HtmlEscaper.escape(column.physicalTableName()))
          .append("\">");
      String dimHref = href != null ? href.apply(column.dimensionName()) : null;
      if (!Utils.isEmpty(dimHref)) {
        html.append("<a href=\"").append(HtmlEscaper.escape(dimHref)).append("\">");
      }
      html.append("<span class=\"hop-doc-bus-matrix-dim-label\">")
          .append(HtmlEscaper.escape(column.label()))
          .append("</span>");
      if (!Utils.isEmpty(dimHref)) {
        html.append("</a>");
      }
      html.append("</th>");
    }
    html.append("</tr></thead>\n<tbody>\n");
    BusMatrixRow previous = null;
    for (BusMatrixRow row : safe.getRows()) {
      html.append("<tr");
      if (BusMatrixLayout.startsGroup(previous, row)) {
        html.append(" class=\"hop-doc-bus-matrix-group\"");
      }
      html.append('>');
      previous = row;
      cell(html, row.business(), true, 1);
      cell(html, row.level1(), true, 2);
      cell(html, row.level2(), true, 3);
      cell(html, row.level3(), true, 4);
      html.append("<th class=\"hop-doc-bus-matrix-sticky hop-doc-bus-matrix-c5\">");
      String factHref = href != null ? href.apply(row.factName()) : null;
      if (!Utils.isEmpty(factHref)) {
        html.append("<a href=\"").append(HtmlEscaper.escape(factHref)).append("\">");
      }
      html.append(HtmlEscaper.escape(row.factName()));
      if (!Utils.isEmpty(factHref)) {
        html.append("</a>");
      }
      html.append("</th>");
      for (BusMatrixColumn column : safe.getColumns()) {
        BusMatrixCell cell = row.cell(column.key());
        html.append("<td");
        if (cell.used()) {
          html.append(" class=\"hop-doc-bus-matrix-x\" title=\"")
              .append(HtmlEscaper.escape(cell.tooltip()))
              .append('"');
        }
        html.append('>').append(HtmlEscaper.escape(cell.mark())).append("</td>");
      }
      html.append("</tr>\n");
    }
    html.append("</tbody></table></div>\n");
    return html.toString();
  }

  private static void header(StringBuilder html, String label, int index) {
    html.append("<th class=\"hop-doc-bus-matrix-sticky hop-doc-bus-matrix-c")
        .append(index)
        .append("\">")
        .append(HtmlEscaper.escape(label))
        .append("</th>");
  }

  private static void cell(StringBuilder html, String value, boolean sticky, int index) {
    html.append("<th class=\"hop-doc-bus-matrix-sticky hop-doc-bus-matrix-c")
        .append(index)
        .append("\">")
        .append(HtmlEscaper.escape(value))
        .append("</th>");
  }
}
