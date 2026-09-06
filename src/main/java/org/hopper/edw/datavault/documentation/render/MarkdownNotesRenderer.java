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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.TableDoc;
import org.hopper.edw.datavault.hopgui.markdown.CommonMarkSupport;

/**
 * Renders canvas Markdown notes to an HTML fragment. Project-relative links to pipelines,
 * workflows, models, and tables are rewritten to the generated documentation pages, resolved from
 * the note's source file folder.
 */
public final class MarkdownNotesRenderer {

  private MarkdownNotesRenderer() {}

  public static String toHtml(String markdown) {
    return toHtml(markdown, null, null, null);
  }

  public static String toHtml(
      String markdown, DocumentationSite site, String sourceRelative, String htmlPath) {
    if (Utils.isEmpty(markdown)) {
      return "";
    }
    Node document = CommonMarkSupport.parse(markdown);
    if (site != null && !Utils.isEmpty(htmlPath)) {
      document.accept(
          new AbstractVisitor() {
            @Override
            public void visit(Link link) {
              if (link != null) {
                link.setDestination(
                    rewriteDestination(link.getDestination(), site, sourceRelative, htmlPath));
              }
              visitChildren(link);
            }
          });
    }
    return CommonMarkSupport.renderHtml(document);
  }

  public static String noteBlock(String title, String markdown) {
    return noteBlock(title, markdown, null, null, null);
  }

  public static String noteBlock(
      String title,
      String markdown,
      DocumentationSite site,
      String sourceRelative,
      String htmlPath) {
    String body = toHtml(markdown, site, sourceRelative, htmlPath);
    if (body.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<article class=\"hop-doc-note\">");
    if (!Utils.isEmpty(title)) {
      html.append("<h3>").append(HtmlEscaper.escape(title)).append("</h3>");
    }
    html.append(body);
    html.append("</article>\n");
    return html.toString();
  }

  static String rewriteDestination(
      String destination, DocumentationSite site, String sourceRelative, String htmlPath) {
    if (Utils.isEmpty(destination) || site == null || Utils.isEmpty(htmlPath)) {
      return destination == null ? "" : destination;
    }
    String href = destination.trim();
    if (href.startsWith("#") || isExternal(href)) {
      return href;
    }
    String fragment = "";
    int hash = href.indexOf('#');
    if (hash >= 0) {
      fragment = href.substring(hash);
      href = href.substring(0, hash);
    }
    if (href.isEmpty()) {
      return destination;
    }

    String targetHtml = null;
    if (isBareName(href)) {
      targetHtml = tablePage(site, href, htmlPath);
    }
    if (targetHtml == null) {
      String projectRel = toProjectRelative(href, site, sourceRelative);
      if (!Utils.isEmpty(projectRel)) {
        targetHtml = sourcePage(site, projectRel);
        if (targetHtml == null && isBareName(href)) {
          targetHtml = tablePage(site, href, htmlPath);
        }
      }
    }
    if (Utils.isEmpty(targetHtml)) {
      return destination;
    }
    return DocPaths.relativize(htmlPath, targetHtml) + fragment;
  }

  private static boolean isExternal(String href) {
    int colon = href.indexOf(':');
    if (colon <= 0) {
      return false;
    }
    String scheme = href.substring(0, colon).toLowerCase(Locale.ROOT);
    return "http".equals(scheme)
        || "https".equals(scheme)
        || "mailto".equals(scheme)
        || "data".equals(scheme)
        || "javascript".equals(scheme)
        || "ftp".equals(scheme);
  }

  private static boolean isBareName(String href) {
    return href.indexOf('/') < 0
        && href.indexOf('\\') < 0
        && href.indexOf(':') < 0
        && href.indexOf('.') < 0;
  }

  private static boolean isAbsolute(String path) {
    if (path.startsWith("/")) {
      return true;
    }
    return path.length() >= 3
        && Character.isLetter(path.charAt(0))
        && path.charAt(1) == ':'
        && (path.charAt(2) == '/' || path.charAt(2) == '\\');
  }

  private static String toProjectRelative(
      String href, DocumentationSite site, String sourceRelative) {
    String working = href;
    if (site.getVariables() != null) {
      working = site.getVariables().resolve(working);
    }
    working = DocPaths.posix(working);
    if (working.startsWith("file:")) {
      working = stripFileScheme(working);
    }
    String withoutHome = stripProjectHomePlaceholder(working);
    boolean projectHome = !withoutHome.equals(working);
    working = withoutHome;
    if (isAbsolute(working)) {
      String underSource = stripSourcePrefix(working, site);
      return underSource == null ? "" : DocPaths.stripDot(underSource);
    }
    if (projectHome) {
      return DocPaths.stripDot(working);
    }
    return DocPaths.resolveRelative(DocPaths.parentPath(sourceRelative), working);
  }

  private static String stripFileScheme(String path) {
    String working = path;
    if (working.startsWith("file://")) {
      working = working.substring("file://".length());
    } else if (working.startsWith("file:")) {
      working = working.substring("file:".length());
    }
    if (working.startsWith("localhost/")) {
      working = working.substring("localhost".length());
    }
    return working;
  }

  private static String stripProjectHomePlaceholder(String path) {
    String marker = "${PROJECT_HOME}";
    if (path.startsWith(marker + "/")) {
      return path.substring(marker.length() + 1);
    }
    if (path.startsWith(marker)) {
      return path.substring(marker.length()).replaceFirst("^/", "");
    }
    return path;
  }

  private static String stripSourcePrefix(String path, DocumentationSite site) {
    if (site.getOptions() == null || Utils.isEmpty(site.getOptions().getSourceFolder())) {
      return isAbsolute(path) ? null : path;
    }
    String source = DocPaths.stripDot(DocPaths.posix(site.getOptions().getSourceFolder()));
    if (source.endsWith("/")) {
      source = source.substring(0, source.length() - 1);
    }
    if (path.equals(source)) {
      return "";
    }
    if (path.startsWith(source + "/")) {
      return path.substring(source.length() + 1);
    }
    return isAbsolute(path) ? null : path;
  }

  private static String sourcePage(DocumentationSite site, String projectRel) {
    Map<String, String> pages = site.getPageBySource();
    String html = pages.get(projectRel);
    if (html != null) {
      return html;
    }
    String stripped = DocPaths.stripDot(projectRel);
    html = pages.get(stripped);
    if (html != null) {
      return html;
    }
    String ext = DocPaths.extensionOf(stripped);
    if (!ext.isEmpty() && !"other".equals(DocPaths.folderForExtension(ext))) {
      return DocPaths.htmlForSource(stripped, ext);
    }
    String base =
        stripped.contains("/") ? stripped.substring(stripped.lastIndexOf('/') + 1) : stripped;
    if (base.isEmpty()) {
      return null;
    }
    String found = null;
    for (Map.Entry<String, String> entry : pages.entrySet()) {
      String key = entry.getKey();
      if (key.equals(base) || key.endsWith("/" + base)) {
        if (found != null && !found.equals(entry.getValue())) {
          return null;
        }
        found = entry.getValue();
      }
    }
    return found;
  }

  private static String tablePage(DocumentationSite site, String name, String htmlPath) {
    TableDoc preferred = null;
    TableDoc any = null;
    for (TableDoc table : site.getTables()) {
      if (table == null || !nameMatches(table, name)) {
        continue;
      }
      any = table;
      if (htmlPath.equals(table.getModelPageHref())) {
        preferred = table;
        break;
      }
    }
    TableDoc match = preferred != null ? preferred : any;
    if (match != null && !Utils.isEmpty(match.getLogicalName())) {
      return DocPaths.tableHref(match.getLayer(), match.getLogicalName());
    }
    for (NavItem item : site.getNav().getOrDefault(DocObjectKind.TABLE, List.of())) {
      if (item != null && name.equalsIgnoreCase(item.title())) {
        return item.href();
      }
    }
    return null;
  }

  private static boolean nameMatches(TableDoc table, String name) {
    return name.equalsIgnoreCase(nvl(table.getLogicalName()))
        || name.equalsIgnoreCase(nvl(table.getPhysicalName()));
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
