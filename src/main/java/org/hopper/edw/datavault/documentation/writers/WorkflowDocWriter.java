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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.resource.ResourceEntry;
import org.apache.hop.resource.ResourceReference;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
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

/** Documents a workflow (.hwf). */
public final class WorkflowDocWriter {

  private WorkflowDocWriter() {}

  public static void write(DocumentationSite site, ScannedFile file, WorkflowMeta meta)
      throws HopException {
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hwf");
    String stableId = DocPaths.stableId(file.relativePath());
    String name =
        Utils.isEmpty(meta.getName())
            ? DocPaths.withoutExtension(file.relativePath())
            : meta.getName();
    site.rememberPage(file.relativePath(), htmlPath);
    site.addNav(
        new NavItem(
            DocObjectKind.WORKFLOW,
            name,
            htmlPath,
            meta.getDescription(),
            DocPaths.folderSegments(file.relativePath())));
    SearchEntry search =
        PageSupport.search(
            DocObjectKind.WORKFLOW,
            "workflow:" + file.relativePath(),
            name,
            htmlPath,
            meta.getDescription(),
            file.relativePath());
    search.getAliases().add(file.relativePath());
    site.addSearch(search);

    List<String> extraHead = new ArrayList<>();
    SvgDocument svg =
        PageSupport.trySvg(
            site,
            file.relativePath(),
            () ->
                DocumentSvgExporter.workflow(
                    meta, site.getVariables(), site.getOptions().isDarkSvg()));
    StringBuilder body = new StringBuilder();
    body.append(HtmlPageWriter.propertyTable(details(site, meta, file.relativePath())));
    body.append(PageSupport.svgSection(site, htmlPath, stableId, svg, extraHead));
    body.append(actionsSection(site, meta, htmlPath, name));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.notesHtml(site, file.relativePath(), htmlPath, meta.getNotes()));
    }
    PageSupport.writePage(site, htmlPath, name, "Workflow", body.toString(), extraHead);
  }

  private static List<String[]> details(
      DocumentationSite site, WorkflowMeta meta, String relative) {
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
          String desc = nvl(meta.getParameterDescription(parameter));
          String def = nvl(meta.getParameterDefault(parameter));
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

  private static String actionsSection(
      DocumentationSite site, WorkflowMeta meta, String htmlPath, String workflowName) {
    List<ActionMeta> actions = meta.getActions();
    if (actions == null || actions.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"actions\"><h2>Actions</h2>\n");
    List<String> headers = List.of("Name", "Type", "Description");
    List<List<String>> rows = new ArrayList<>();
    for (ActionMeta actionMeta : actions) {
      if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
        continue;
      }
      IAction action = actionMeta.getAction();
      String pluginId = action != null ? action.getPluginId() : "";
      String type = PageSupport.pluginName(ActionPluginType.class, pluginId);
      String description = action != null ? nvl(action.getDescription()) : "";
      String id = DocPaths.fragmentId("action", actionMeta.getName());
      String nameHtml =
          "<span id=\""
              + HtmlEscaper.escape(id)
              + "\">"
              + HtmlEscaper.escape(actionMeta.getName())
              + "</span>";
      rows.add(List.of(nameHtml, HtmlEscaper.escape(type), HtmlEscaper.escape(description)));
      SearchEntry entry =
          PageSupport.search(
              DocObjectKind.ACTION,
              "action:" + htmlPath + ":" + actionMeta.getName(),
              actionMeta.getName(),
              htmlPath + "#" + id,
              description,
              type);
      entry.setParent(workflowName);
      site.addSearch(entry);
    }
    html.append(HtmlPageWriter.dataTable(headers, rows, true));
    html.append("</section>\n");
    return html.toString();
  }

  private static String usedMetadataHtml(
      DocumentationSite site, WorkflowMeta meta, String relative) {
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
          if (html.length() > 0) {
            html.append(", ");
          }
          html.append(HtmlEscaper.escape(entry.getResource()));
          site.addUsedBy(
              entry.getResource(),
              new NavItem(
                  DocObjectKind.WORKFLOW, meta.getName(), DocPaths.htmlForSource(relative, "hwf")));
        }
      }
    } catch (Exception ignored) {
      // Best-effort.
    }
    return html.toString();
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}
