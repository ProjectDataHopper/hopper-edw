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

import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.model.SvgHit;
import org.hopper.edw.datavault.documentation.render.DocumentationIo;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;
import org.hopper.edw.datavault.documentation.render.JsonStrings;
import org.hopper.edw.datavault.documentation.render.MarkdownNotesRenderer;
import org.hopper.edw.datavault.metadata.DvNote;

final class PageSupport {

  private PageSupport() {}

  static void writePage(
      DocumentationSite site, String htmlPath, String title, String badge, String bodyHtml)
      throws HopException {
    writePage(site, htmlPath, title, badge, bodyHtml, List.of());
  }

  static void writePage(
      DocumentationSite site,
      String htmlPath,
      String title,
      String badge,
      String bodyHtml,
      List<String> extraHead)
      throws HopException {
    site.enqueuePage(htmlPath, title, badge, bodyHtml, extraHead);
  }

  static void writeSvg(DocumentationSite site, String stableId, SvgDocument svg)
      throws HopException {
    if (svg == null || Utils.isEmpty(svg.getLightSvg())) {
      return;
    }
    FileObject images = DocumentationIo.child(site.getTargetRoot(), "assets/images");
    DocumentationIo.writeUtf8(
        DocumentationIo.child(images, stableId + ".light.svg"), svg.getLightSvg());
    if (!Utils.isEmpty(svg.getDarkSvg())) {
      DocumentationIo.writeUtf8(
          DocumentationIo.child(images, stableId + ".dark.svg"), svg.getDarkSvg());
    }
    StringBuilder js = new StringBuilder();
    js.append("window.HOP_DOC_HITS = [\n");
    List<SvgHit> hits = svg.getHits();
    for (int i = 0; i < hits.size(); i++) {
      SvgHit hit = hits.get(i);
      if (i > 0) {
        js.append(",\n");
      }
      js.append("  {\"type\":")
          .append(JsonStrings.quote(hit.type()))
          .append(",\"name\":")
          .append(JsonStrings.quote(hit.name()))
          .append(",\"x\":")
          .append(hit.x())
          .append(",\"y\":")
          .append(hit.y())
          .append(",\"w\":")
          .append(hit.w())
          .append(",\"h\":")
          .append(hit.h())
          .append(",\"href\":")
          .append(JsonStrings.quote(hit.href()))
          .append("}");
    }
    js.append("\n];\n");
    DocumentationIo.writeUtf8(
        DocumentationIo.child(site.getTargetRoot(), "assets/js/hits/" + stableId + ".js"),
        js.toString());
  }

  static SearchEntry search(
      DocObjectKind kind,
      String id,
      String name,
      String path,
      String description,
      String keywords) {
    SearchEntry entry = new SearchEntry();
    entry.setKind(kind);
    entry.setId(id);
    entry.setName(name);
    entry.setPath(path);
    entry.setDescription(description);
    entry.setKeywords(keywords);
    if (!Utils.isEmpty(name)) {
      entry.getAliases().add(name);
    }
    return entry;
  }

  static String notesHtml(
      DocumentationSite site, String sourceRelative, String htmlPath, List<NotePadMeta> notes) {
    if (notes == null || notes.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"notes\"><h2>Notes</h2>\n");
    int i = 1;
    for (NotePadMeta note : notes) {
      if (note == null || Utils.isEmpty(note.getNote())) {
        continue;
      }
      html.append(
          MarkdownNotesRenderer.noteBlock(
              "Note " + i, note.getNote(), site, sourceRelative, htmlPath));
      i++;
    }
    html.append("</section>\n");
    return html.toString();
  }

  static String dvNotesHtml(
      DocumentationSite site, String sourceRelative, String htmlPath, List<DvNote> notes) {
    if (notes == null || notes.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"notes\"><h2>Notes</h2>\n");
    int i = 1;
    for (DvNote note : notes) {
      if (note == null || Utils.isEmpty(note.getText())) {
        continue;
      }
      String title = note.getNoteType() != null ? note.getNoteType().name() : "Note " + i;
      html.append(
          MarkdownNotesRenderer.noteBlock(title, note.getText(), site, sourceRelative, htmlPath));
      i++;
    }
    html.append("</section>\n");
    return html.toString();
  }

  static SvgDocument trySvg(DocumentationSite site, String relativePath, SvgProducer producer) {
    try {
      return producer.produce();
    } catch (Exception e) {
      site.warn("SVG skipped for " + relativePath + ": " + e.getMessage());
      return null;
    }
  }

  @FunctionalInterface
  interface SvgProducer {
    SvgDocument produce() throws Exception;
  }

  static String pluginName(Class<? extends IPluginType> pluginType, String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return "";
    }
    try {
      IPlugin plugin = PluginRegistry.getInstance().getPlugin(pluginType, pluginId);
      if (plugin != null && !Utils.isEmpty(plugin.getName())) {
        return plugin.getName();
      }
    } catch (Exception ignored) {
      // Fall back to the raw plugin id.
    }
    return pluginId;
  }

  static String svgSection(
      DocumentationSite site,
      String htmlPath,
      String stableId,
      SvgDocument svg,
      List<String> extraHead)
      throws HopException {
    if (svg == null || Utils.isEmpty(svg.getLightSvg())) {
      return "";
    }
    writeSvg(site, stableId, svg);
    return HtmlPageWriter.svgViewer(
        htmlPath, stableId, !Utils.isEmpty(svg.getDarkSvg()), extraHead);
  }
}
