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
package org.hopper.edw.datavault.documentation.render;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.DocumentationSite.PendingPage;

/** Shared HTML chrome for every generated page. */
public final class HtmlPageWriter {

  private HtmlPageWriter() {}

  public static String render(
      DocumentationSite site, String htmlPath, String title, String badge, String bodyHtml) {
    return render(site, htmlPath, title, badge, bodyHtml, List.of());
  }

  public static void flushQueued(DocumentationSite site) throws HopException {
    if (site == null) {
      return;
    }
    List<PendingPage> pages = site.getPendingPages();
    while (!pages.isEmpty()) {
      PendingPage page = pages.remove(0);
      String html =
          render(
              site, page.htmlPath(), page.title(), page.badge(), page.bodyHtml(), page.extraHead());
      DocumentationIo.writeUtf8(DocumentationIo.child(site.getTargetRoot(), page.htmlPath()), html);
      if (site.getResult() != null) {
        site.getResult().setPagesWritten(site.getResult().getPagesWritten() + 1);
      }
    }
  }

  public static String render(
      DocumentationSite site,
      String htmlPath,
      String title,
      String badge,
      String bodyHtml,
      List<String> extraHead) {
    String root = DocPaths.rootPrefix(htmlPath);
    String css = root + "assets/css/hop-doc.css";
    String printCss = root + "assets/css/print.css";
    String js = root + "assets/js/hop-doc.js";
    String searchJs = root + "assets/js/search.js";
    String svgJs = root + "assets/js/svg-viewer.js";
    String indexJs = root + "assets/js/search-index.js";
    String project = HtmlEscaper.escape(site.getProjectName());
    String pageTitle = HtmlEscaper.escape(title);
    String generated =
        site.getGeneratedAt() == null
            ? ""
            : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(site.getGeneratedAt());

    StringBuilder html = new StringBuilder(16_384);
    html.append("<!DOCTYPE html>\n<html lang=\"en\" data-theme=\"system\" data-hop-doc-root=\"")
        .append(HtmlEscaper.escape(root))
        .append("\">\n<head>\n<meta charset=\"utf-8\">\n")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        .append("<title>")
        .append(pageTitle)
        .append(" — ")
        .append(project)
        .append("</title>\n")
        .append("<link rel=\"stylesheet\" href=\"")
        .append(HtmlEscaper.escape(css))
        .append("\">\n")
        .append("<link rel=\"stylesheet\" href=\"")
        .append(HtmlEscaper.escape(printCss))
        .append("\" media=\"print\">\n");
    if (extraHead != null) {
      for (String extra : extraHead) {
        html.append(extra).append('\n');
      }
    }
    html.append("</head>\n<body class=\"hop-doc-page\">\n")
        .append("<header class=\"hop-doc-header\">\n");
    appendBreadcrumb(html, site, htmlPath, title, root, project);
    html.append("<div class=\"hop-doc-header-tools\">\n")
        .append(
            "<form class=\"hop-doc-search\" role=\"search\" action=\"#\" onsubmit=\"return false;\">")
        .append("<label class=\"hop-doc-sr-only\" for=\"hop-doc-search-input\">Search</label>")
        .append(
            "<input id=\"hop-doc-search-input\" type=\"search\" placeholder=\"Search pipelines, workflows, models, tables…\" autocomplete=\"off\">")
        .append("<div class=\"hop-doc-search-results\" id=\"hop-doc-search-results\" hidden></div>")
        .append("</form>\n")
        .append("<label class=\"hop-doc-sr-only\" for=\"hop-doc-theme\">Color theme</label>")
        .append(
            "<select id=\"hop-doc-theme\" class=\"hop-doc-theme-toggle\" aria-label=\"Color theme\">")
        .append("<option value=\"system\">System</option>")
        .append("<option value=\"light\">Light</option>")
        .append("<option value=\"dark\">Dark</option>")
        .append("</select>\n</div>\n</header>\n")
        .append("<div class=\"hop-doc-layout\">\n")
        .append("<nav class=\"hop-doc-sidebar\" aria-label=\"Documentation\">\n")
        .append("<ul class=\"hop-doc-nav\">\n")
        .append(
            navLink(
                htmlPath,
                "index.html",
                "Overview",
                htmlPath.endsWith("index.html") && !htmlPath.contains("/")));
    html.append(navLink(htmlPath, "theming.html", "Theming", htmlPath.endsWith("theming.html")));
    html.append(NavTree.render(site.getNav(), htmlPath));
    html.append("</ul>\n</nav>\n")
        .append("<main class=\"hop-doc-main\">\n")
        .append("<header class=\"hop-doc-page-header\">");
    if (!Utils.isEmpty(badge)) {
      html.append("<span class=\"hop-doc-badge\">")
          .append(HtmlEscaper.escape(badge))
          .append("</span>");
    }
    html.append("<h1>").append(pageTitle).append("</h1></header>\n").append(bodyHtml);
    html.append("</main>\n</div>\n")
        .append("<footer class=\"hop-doc-footer\">Generated ")
        .append(HtmlEscaper.escape(generated));
    if (!Utils.isEmpty(site.getGeneratorVersion())) {
      html.append(" · Data Hopper EDW ").append(HtmlEscaper.escape(site.getGeneratorVersion()));
    }
    html.append(" · static HTML, no server required · <a href=\"")
        .append(HtmlEscaper.escape(root + "theming.html"))
        .append("\">CSS theming</a></footer>\n")
        .append("<script src=\"")
        .append(HtmlEscaper.escape(indexJs))
        .append("\"></script>\n")
        .append("<script src=\"")
        .append(HtmlEscaper.escape(js))
        .append("\"></script>\n")
        .append("<script src=\"")
        .append(HtmlEscaper.escape(searchJs))
        .append("\"></script>\n")
        .append("<script src=\"")
        .append(HtmlEscaper.escape(svgJs))
        .append("\"></script>\n")
        .append("</body>\n</html>\n");
    return html.toString();
  }

  public static String propertyTable(List<String[]> rows) {
    if (rows == null || rows.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<table class=\"hop-doc-meta-table\"><tbody>\n");
    for (String[] row : rows) {
      if (row == null || row.length < 2 || Utils.isEmpty(row[1])) {
        continue;
      }
      html.append("<tr><th>")
          .append(HtmlEscaper.escape(row[0]))
          .append("</th><td>")
          .append(row[2] != null && "html".equals(row[2]) ? row[1] : HtmlEscaper.escape(row[1]))
          .append("</td></tr>\n");
    }
    html.append("</tbody></table>\n");
    return html.toString();
  }

  public static String dataTable(List<String> headers, List<List<String>> rows, boolean htmlCells) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<div class=\"hop-doc-table-wrap\"><table class=\"hop-doc-meta-table\">\n<thead><tr>");
    for (String header : headers) {
      html.append("<th>").append(HtmlEscaper.escape(header)).append("</th>");
    }
    html.append("</tr></thead>\n<tbody>\n");
    for (List<String> row : rows) {
      html.append("<tr>");
      for (int i = 0; i < headers.size(); i++) {
        String cell = i < row.size() ? row.get(i) : "";
        html.append("<td>");
        html.append(htmlCells ? cell : HtmlEscaper.escape(cell));
        html.append("</td>");
      }
      html.append("</tr>\n");
    }
    html.append("</tbody></table></div>\n");
    return html.toString();
  }

  public static String link(String href, String text) {
    return "<a href=\"" + HtmlEscaper.escape(href) + "\">" + HtmlEscaper.escape(text) + "</a>";
  }

  public static String svgViewer(
      String htmlPath, String stableId, boolean hasDark, List<String> extraHead) {
    String light = DocPaths.asset(htmlPath, "assets/images/" + stableId + ".light.svg");
    String dark = DocPaths.asset(htmlPath, "assets/images/" + stableId + ".dark.svg");
    String hits = DocPaths.asset(htmlPath, "assets/js/hits/" + stableId + ".js");
    extraHead.add("<script src=\"" + HtmlEscaper.escape(hits) + "\"></script>");
    StringBuilder html = new StringBuilder();
    html.append("<section class=\"hop-doc-diagram\" id=\"diagram\">")
        .append("<h2>Diagram</h2>")
        .append("<div class=\"hop-doc-svg-toolbar\">")
        .append("<button type=\"button\" data-svg-zoom=\"in\">Zoom in</button>")
        .append("<button type=\"button\" data-svg-zoom=\"out\">Zoom out</button>")
        .append("<button type=\"button\" data-svg-zoom=\"fit\">Fit</button>")
        .append("<button type=\"button\" data-svg-zoom=\"reset\">100%</button>")
        .append("</div>")
        .append("<div class=\"hop-doc-svg-viewport\" data-hop-doc-svg=\"")
        .append(HtmlEscaper.escape(stableId))
        .append("\">")
        .append("<div class=\"hop-doc-svg-stage\">")
        .append("<img class=\"hop-doc-svg hop-doc-svg-light\" src=\"")
        .append(HtmlEscaper.escape(light))
        .append("\" alt=\"Canvas diagram\">");
    if (hasDark) {
      html.append("<img class=\"hop-doc-svg hop-doc-svg-dark\" src=\"")
          .append(HtmlEscaper.escape(dark))
          .append("\" alt=\"Canvas diagram (dark)\">");
    }
    html.append("</div></div></section>\n");
    return html.toString();
  }

  public static List<String[]> rowList() {
    return new ArrayList<>();
  }

  public static void row(List<String[]> rows, String label, String value) {
    if (!Utils.isEmpty(value)) {
      rows.add(new String[] {label, value, "text"});
    }
  }

  public static void rowHtml(List<String[]> rows, String label, String htmlValue) {
    if (!Utils.isEmpty(htmlValue)) {
      rows.add(new String[] {label, htmlValue, "html"});
    }
  }

  private static void appendBreadcrumb(
      StringBuilder html,
      DocumentationSite site,
      String htmlPath,
      String title,
      String root,
      String projectEscaped) {
    boolean overview = htmlPath != null && htmlPath.equals("index.html");
    boolean theming = htmlPath != null && htmlPath.equals("theming.html");
    List<String> rest = new ArrayList<>();
    if (theming) {
      rest.add("Theming");
    } else if (!overview) {
      rest.addAll(NavTree.breadcrumb(site.getNav(), htmlPath));
      if (rest.isEmpty() && !Utils.isEmpty(title)) {
        rest.add(title);
      }
    }
    html.append("<nav class=\"hop-doc-breadcrumb\" aria-label=\"Breadcrumb\"><ol>");
    if (rest.isEmpty()) {
      html.append("<li aria-current=\"page\"><span class=\"hop-doc-brand\">")
          .append(projectEscaped)
          .append("</span></li>");
    } else {
      html.append("<li><a class=\"hop-doc-brand\" href=\"")
          .append(HtmlEscaper.escape(root + "index.html"))
          .append("\">")
          .append(projectEscaped)
          .append("</a></li>");
      for (int i = 0; i < rest.size(); i++) {
        boolean last = i == rest.size() - 1;
        html.append("<li");
        if (last) {
          html.append(" aria-current=\"page\"");
        }
        html.append(">").append(HtmlEscaper.escape(rest.get(i))).append("</li>");
      }
    }
    html.append("</ol></nav>\n");
  }

  private static String navLink(String fromHtml, String to, String title, boolean current) {
    String href = DocPaths.relativize(fromHtml, to);
    return "<li"
        + (current ? " class=\"is-current\"" : "")
        + "><a href=\""
        + HtmlEscaper.escape(href)
        + "\">"
        + HtmlEscaper.escape(title)
        + "</a></li>\n";
  }
}
