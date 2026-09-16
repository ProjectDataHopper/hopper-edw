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
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixBuilder;
import org.hopper.edw.datavault.busmatrix.BusMatrixCsvWriter;
import org.hopper.edw.datavault.busmatrix.BusMatrixHtmlRenderer;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.DocumentationIo;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;

/** Writes one bus-matrix page per resource definition group. */
public final class BusMatrixDocWriter {

  private BusMatrixDocWriter() {}

  public static void writeAll(DocumentationSite site) throws HopException {
    IHopMetadataProvider provider = site.getMetadataProvider();
    if (provider == null) {
      return;
    }
    IHopMetadataSerializer<ResourceDefinitionGroupMeta> serializer;
    try {
      serializer = provider.getSerializer(ResourceDefinitionGroupMeta.class);
    } catch (Exception e) {
      site.warn("Unable to list resource definition groups for bus matrices: " + e.getMessage());
      return;
    }
    List<String> names;
    try {
      names = serializer.listObjectNames();
    } catch (Exception e) {
      site.warn("Unable to list resource definition groups for bus matrices: " + e.getMessage());
      return;
    }
    if (names == null) {
      return;
    }
    for (String name : names) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      try {
        ResourceDefinitionGroupMeta group = serializer.load(name);
        if (group == null || group.getDimensionalModelFiles().isEmpty()) {
          continue;
        }
        writeOne(site, group);
      } catch (Exception e) {
        site.warn(
            "Bus matrix skipped for group "
                + name
                + ": "
                + Const.NVL(e.getMessage(), e.toString()));
      }
    }
  }

  private static void writeOne(DocumentationSite site, ResourceDefinitionGroupMeta group)
      throws HopException {
    BusMatrix matrix =
        BusMatrixBuilder.build(group, site.getVariables(), site.getMetadataProvider());
    if (matrix.getRows().isEmpty() && matrix.getColumns().isEmpty()) {
      return;
    }
    String htmlPath = DocPaths.busMatrixHref(group.getName());
    String title = "Bus matrix — " + group.getName();
    site.addNav(
        new NavItem(DocObjectKind.BUS_MATRIX, group.getName(), htmlPath, group.getDescription()));
    SearchEntry search =
        PageSupport.search(
            DocObjectKind.BUS_MATRIX,
            "bus-matrix-" + DocPaths.slug(group.getName()),
            group.getName(),
            htmlPath,
            group.getDescription(),
            keywords(matrix));
    site.addSearch(search);

    String csvPath = "bus-matrices/" + DocPaths.slug(group.getName()) + ".csv";
    DocumentationIo.writeUtf8(
        DocumentationIo.child(site.getTargetRoot(), csvPath), BusMatrixCsvWriter.write(matrix));

    SvgDocument svg =
        BusMatrixSvgPainter.paintBoth(
            matrix, name -> DocPaths.relativize(htmlPath, DocPaths.tableHref("dm", name)));
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Resource definition group", group.getName());
    HtmlPageWriter.row(rows, "Processes", Integer.toString(matrix.getRows().size()));
    HtmlPageWriter.row(rows, "Dimensions", Integer.toString(matrix.getColumns().size()));
    if (!matrix.getWarnings().isEmpty()) {
      HtmlPageWriter.row(rows, "Warnings", String.join("; ", matrix.getWarnings()));
    }
    body.append(HtmlPageWriter.propertyTable(rows));
    body.append("<p><a href=\"")
        .append(HtmlEscaper.escape(DocPaths.relativize(htmlPath, csvPath)))
        .append("\">Download CSV</a></p>\n");
    body.append(
        BusMatrixHtmlRenderer.table(
            matrix, name -> DocPaths.relativize(htmlPath, DocPaths.tableHref("dm", name))));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, "bus-matrix-" + DocPaths.slug(group.getName()), svg, extraHead));
    PageSupport.writePage(site, htmlPath, title, "Bus matrix", body.toString(), extraHead);
  }

  private static String keywords(BusMatrix matrix) {
    StringBuilder keys = new StringBuilder();
    matrix.getRows().forEach(row -> keys.append(row.factName()).append(' '));
    matrix.getColumns().forEach(col -> keys.append(col.label()).append(' '));
    return keys.toString();
  }
}
