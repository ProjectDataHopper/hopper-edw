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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.metadata.MetadataPropertyWalker;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.DocumentationScope;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;

/** Writes index.html. */
public final class IndexWriter {

  private static final DocObjectKind[] COUNT_ORDER = {
    DocObjectKind.PIPELINE,
    DocObjectKind.WORKFLOW,
    DocObjectKind.SOURCE_MODEL,
    DocObjectKind.DATA_VAULT_MODEL,
    DocObjectKind.BUSINESS_VAULT_MODEL,
    DocObjectKind.DIMENSIONAL_MODEL,
    DocObjectKind.EXECUTION_MAP,
    DocObjectKind.TABLE,
    DocObjectKind.METADATA,
    DocObjectKind.CATALOG
  };

  private IndexWriter() {}

  public static void write(DocumentationSite site) throws HopException {
    StringBuilder body = new StringBuilder();
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Project", site.getProjectName());
    HtmlPageWriter.row(rows, "Source folder", site.getOptions().getSourceFolder());
    HtmlPageWriter.row(
        rows,
        "Generated",
        site.getGeneratedAt() == null
            ? ""
            : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(site.getGeneratedAt()));
    HtmlPageWriter.row(rows, "Scope", site.getOptions().getScope().name());
    body.append(HtmlPageWriter.propertyTable(rows));
    body.append("<div class=\"hop-doc-counts\">");
    for (DocObjectKind kind : COUNT_ORDER) {
      int count = site.getNav().getOrDefault(kind, List.of()).size();
      if (count == 0) {
        continue;
      }
      body.append("<div class=\"hop-doc-count\"><strong>")
          .append(count)
          .append("</strong>")
          .append(HtmlEscaper.escape(kind.navLabel()))
          .append("</div>");
    }
    body.append("</div>\n");
    for (DocObjectKind kind : COUNT_ORDER) {
      List<NavItem> items = site.getNav().getOrDefault(kind, List.of());
      if (items.isEmpty()) {
        continue;
      }
      List<NavItem> sorted = new ArrayList<>(items);
      sorted.sort(
          Comparator.comparing(NavItem::pathTitle, String.CASE_INSENSITIVE_ORDER)
              .thenComparing(NavItem::href, String.CASE_INSENSITIVE_ORDER));
      body.append("<section><h2>").append(HtmlEscaper.escape(kind.navLabel())).append("</h2><ul>");
      for (NavItem item : sorted) {
        body.append("<li>").append(HtmlPageWriter.link(item.href(), item.pathTitle()));
        if (!Utils.isEmpty(item.description())) {
          body.append(" — ").append(HtmlEscaper.escape(item.description()));
        }
        body.append("</li>");
      }
      body.append("</ul></section>\n");
    }
    if (site.getOptions().getScope() == DocumentationScope.ENVIRONMENT) {
      body.append(environmentSection(site));
    }
    PageSupport.writePage(site, "index.html", site.getProjectName(), "Project", body.toString());
    site.getResult().setIndexHtml("index.html");
  }

  private static String environmentSection(DocumentationSite site) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<section id=\"environment\"><h2>Environment</h2><table class=\"hop-doc-meta-table\"><tbody>");
    String envName = site.getVariables().getVariable("HOP_ENVIRONMENT_NAME");
    if (!Utils.isEmpty(envName)) {
      html.append("<tr><th>Name</th><td>").append(HtmlEscaper.escape(envName)).append("</td></tr>");
    }
    for (String name : site.getVariables().getVariableNames()) {
      if (MetadataPropertyWalker.looksSecret(name)) {
        continue;
      }
      String value = site.getVariables().getVariable(name);
      if (MetadataPropertyWalker.looksEncrypted(value)) {
        value = MetadataPropertyWalker.REDACTED;
      }
      html.append("<tr><th>")
          .append(HtmlEscaper.escape(name))
          .append("</th><td>")
          .append(HtmlEscaper.escape(value == null ? "" : value))
          .append("</td></tr>");
    }
    html.append("</tbody></table></section>\n");
    return html.toString();
  }
}
