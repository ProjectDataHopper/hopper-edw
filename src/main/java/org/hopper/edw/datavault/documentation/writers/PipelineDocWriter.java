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
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.resource.ResourceEntry;
import org.apache.hop.resource.ResourceReference;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.export.DocumentSvgExporter;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;
import org.hopper.edw.datavault.documentation.scan.ScannedFile;

/** Documents a pipeline (.hpl). */
public final class PipelineDocWriter {

  private PipelineDocWriter() {}

  public static void write(DocumentationSite site, ScannedFile file, PipelineMeta meta)
      throws HopException {
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hpl");
    String stableId = DocPaths.stableId(file.relativePath());
    String name = ConstName(meta.getName(), DocPaths.withoutExtension(file.relativePath()));
    site.rememberPage(file.relativePath(), htmlPath);
    site.addNav(
        new NavItem(
            DocObjectKind.PIPELINE,
            name,
            htmlPath,
            meta.getDescription(),
            DocPaths.folderSegments(file.relativePath())));

    SearchEntry pipelineEntry =
        PageSupport.search(
            DocObjectKind.PIPELINE,
            "pipeline:" + file.relativePath(),
            name,
            htmlPath,
            meta.getDescription(),
            file.relativePath());
    pipelineEntry.getAliases().add(file.relativePath());
    site.addSearch(pipelineEntry);

    List<String> extraHead = new ArrayList<>();
    SvgDocument svg =
        PageSupport.trySvg(
            site,
            file.relativePath(),
            () ->
                DocumentSvgExporter.pipeline(
                    meta, site.getVariables(), site.getOptions().isDarkSvg()));
    StringBuilder body = new StringBuilder();
    body.append(HtmlPageWriter.propertyTable(details(site, meta, file.relativePath())));
    body.append(PageSupport.svgSection(site, htmlPath, stableId, svg, extraHead));
    body.append(transformsSection(site, meta, htmlPath, name));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.notesHtml(site, file.relativePath(), htmlPath, meta.getNotes()));
    }

    PageSupport.writePage(site, htmlPath, name, "Pipeline", body.toString(), extraHead);
  }

  private static List<String[]> details(DocumentationSite site, PipelineMeta meta, String relative)
      throws HopException {
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Name", meta.getName());
    HtmlPageWriter.row(rows, "Filename", relative);
    HtmlPageWriter.row(rows, "Description", meta.getDescription());
    HtmlPageWriter.row(rows, "Extended description", meta.getExtendedDescription());
    if (meta.getModifiedDate() != null) {
      HtmlPageWriter.row(
          rows,
          "Last modified",
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(meta.getModifiedDate()));
    }
    if (site.getOptions().isIncludingParameters()) {
      try {
        for (String parameter : meta.listParameters()) {
          String desc = ConstName(meta.getParameterDescription(parameter), "");
          String def = ConstName(meta.getParameterDefault(parameter), "");
          HtmlPageWriter.row(
              rows,
              "Parameter " + parameter,
              desc + (def.isEmpty() ? "" : " (default: " + def + ")"));
        }
      } catch (Exception ignored) {
        // Parameters are optional.
      }
    }
    HtmlPageWriter.rowHtml(rows, "Used metadata", usedMetadataHtml(site, meta, relative));
    return rows;
  }

  private static String transformsSection(
      DocumentationSite site, PipelineMeta meta, String htmlPath, String pipelineName) {
    List<TransformMeta> transforms = meta.getTransforms();
    if (transforms == null || transforms.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"transforms\"><h2>Transforms</h2>\n");
    List<String> headers = List.of("Name", "Type", "Copies", "Description");
    List<List<String>> rows = new ArrayList<>();
    StringBuilder keywords = new StringBuilder();
    for (TransformMeta transform : transforms) {
      if (transform == null || Utils.isEmpty(transform.getName())) {
        continue;
      }
      String id = DocPaths.fragmentId("transform", transform.getName());
      String type = PageSupport.pluginName(TransformPluginType.class, transform.getPluginId());
      String copies = String.valueOf(transform.getCopies(site.getVariables()));
      String description = transform.getDescription();
      String nameHtml =
          "<span id=\""
              + HtmlEscaper.escape(id)
              + "\">"
              + HtmlEscaper.escape(transform.getName())
              + "</span>";
      rows.add(
          List.of(
              nameHtml,
              HtmlEscaper.escape(type),
              HtmlEscaper.escape(copies),
              HtmlEscaper.escape(ConstName(description, ""))));
      SearchEntry entry =
          PageSupport.search(
              DocObjectKind.TRANSFORM,
              "transform:" + htmlPath + ":" + transform.getName(),
              transform.getName(),
              htmlPath + "#" + id,
              description,
              type);
      entry.setParent(pipelineName);
      site.addSearch(entry);
      if (keywords.length() > 0) {
        keywords.append(' ');
      }
      keywords.append(transform.getName());
    }
    html.append(HtmlPageWriter.dataTable(headers, rows, true));
    html.append("</section>\n");
    return html.toString();
  }

  private static String usedMetadataHtml(
      DocumentationSite site, PipelineMeta meta, String relative) {
    StringBuilder html = new StringBuilder();
    try {
      for (ResourceReference reference : meta.getResourceDependencies(site.getVariables())) {
        if (reference == null || reference.getEntries() == null) {
          continue;
        }
        for (ResourceEntry entry : reference.getEntries()) {
          if (entry == null || Utils.isEmpty(entry.getResource())) {
            continue;
          }
          appendUsed(site, html, entry.getResource(), relative, meta.getName());
        }
      }
    } catch (Exception ignored) {
      // Best-effort.
    }
    for (TransformMeta transform : meta.getTransforms()) {
      if (transform.getTransform() == null) {
        continue;
      }
      DatabaseMeta[] dbs = null;
      if (transform.getTransform() instanceof BaseTransformMeta<?, ?> base) {
        dbs = base.getUsedDatabaseConnections();
      }
      if (dbs == null) {
        continue;
      }
      for (DatabaseMeta db : dbs) {
        if (db != null) {
          appendUsed(site, html, db.getName(), relative, meta.getName());
        }
      }
    }
    return html.toString();
  }

  private static void appendUsed(
      DocumentationSite site,
      StringBuilder html,
      String name,
      String relative,
      String pipelineName) {
    if (Utils.isEmpty(name)) {
      return;
    }
    if (html.length() > 0) {
      html.append(", ");
    }
    String href = site.getMetadataHrefByKey().get("rdbms:" + name);
    if (href == null) {
      href = site.getMetadataHrefByKey().get(name);
    }
    if (href != null) {
      html.append(
          HtmlPageWriter.link(
              DocPaths.relativize(DocPaths.htmlForSource(relative, "hpl"), href), name));
    } else {
      html.append(HtmlEscaper.escape(name));
    }
    site.addUsedBy(
        name,
        new NavItem(DocObjectKind.PIPELINE, pipelineName, DocPaths.htmlForSource(relative, "hpl")));
  }

  private static String ConstName(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }
}
