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
import java.util.function.Function;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.export.DocumentSvgExporter;
import org.hopper.edw.datavault.documentation.load.DocumentationArtifactLoader;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.model.TableDoc;
import org.hopper.edw.datavault.documentation.model.TableDoc.ColumnDoc;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;
import org.hopper.edw.datavault.documentation.scan.ScannedFile;
import org.hopper.edw.datavault.lineage.BvModelLineageCollector;
import org.hopper.edw.datavault.lineage.DmModelLineageCollector;
import org.hopper.edw.datavault.lineage.DvModelLineageCollector;
import org.hopper.edw.datavault.lineage.FieldLineage;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.lineage.TableLineage;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/** Documents .hsm / .hdv / .hbv / .hdm / .hem files and collects table pages. */
public final class ModelDocWriter {

  private ModelDocWriter() {}

  public static void write(DocumentationSite site, ScannedFile file) throws HopException {
    String path = file.file().getName().getPath();
    switch (file.extension()) {
      case "hsm" -> writeSource(site, file, path);
      case "hdv" -> writeDv(site, file, path);
      case "hbv" -> writeBv(site, file, path);
      case "hdm" -> writeDm(site, file, path);
      case "hem" -> writeHem(site, file, path);
      default -> {
        // Ignore
      }
    }
  }

  private static void writeSource(DocumentationSite site, ScannedFile file, String path)
      throws HopException {
    SourceModel model =
        DocumentationArtifactLoader.loadSourceModel(
            path, site.getVariables(), site.getMetadataProvider());
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hsm");
    String name = nvl(model.getName(), DocPaths.withoutExtension(file.relativePath()));
    remember(site, file, htmlPath, DocObjectKind.SOURCE_MODEL, name, model.getDescription());
    Function<String, String> href =
        tableName -> DocPaths.relativize(htmlPath, DocPaths.tableHref("source", tableName));
    SvgDocument svg =
        DocumentSvgExporter.sourceModel(
            model,
            site.getVariables(),
            site.getMetadataProvider(),
            site.getOptions().isIncludingNotes(),
            site.getOptions().isDarkSvg(),
            href);
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    body.append(
        HtmlPageWriter.propertyTable(
            modelDetails(name, file.relativePath(), model.getDescription())));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, DocPaths.stableId(file.relativePath()), svg, extraHead));
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      TableDoc doc = new TableDoc();
      doc.setLayer("source");
      doc.setLogicalName(table.getName());
      doc.setPhysicalName(nvl(table.getTableName(), table.getName()));
      doc.setTableType(table.getPhysicalType() != null ? table.getPhysicalType().name() : "TABLE");
      doc.setDescription(table.getDescription());
      doc.setModelName(name);
      doc.setModelPageHref(htmlPath);
      if (!Utils.isEmpty(table.getCatalogSourceName())) {
        doc.setCatalogHref(DocPaths.catalogHref("source", table.getCatalogSourceName()));
      }
      for (SourceColumn column : table.getColumns()) {
        if (column == null) {
          continue;
        }
        ColumnDoc col = new ColumnDoc();
        col.setName(column.getName());
        col.setDataType(column.getSourceDataType());
        col.setDescription(column.getDescription());
        doc.getColumns().add(col);
      }
      site.addTable(doc);
      addTableSearch(site, doc);
    }
    body.append(tableIndex(site, htmlPath, "source", names(model), "Tables"));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.dvNotesHtml(site, file.relativePath(), htmlPath, model.getNotes()));
    }
    PageSupport.writePage(site, htmlPath, name, "Source model", body.toString(), extraHead);
  }

  private static void writeDv(DocumentationSite site, ScannedFile file, String path)
      throws HopException {
    DataVaultModel model =
        DocumentationArtifactLoader.loadDataVaultModel(path, site.getMetadataProvider());
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hdv");
    String name = nvl(model.getName(), DocPaths.withoutExtension(file.relativePath()));
    remember(site, file, htmlPath, DocObjectKind.DATA_VAULT_MODEL, name, model.getDescription());
    Function<String, String> href =
        tableName -> DocPaths.relativize(htmlPath, DocPaths.tableHref("dv", tableName));
    SvgDocument svg =
        DocumentSvgExporter.dataVault(
            model,
            site.getVariables(),
            site.getOptions().isIncludingNotes(),
            site.getOptions().isDarkSvg(),
            href);
    LineageSnapshot lineage = null;
    if (site.getOptions().isIncludingLineage()) {
      try {
        lineage =
            DvModelLineageCollector.collect(
                model, site.getVariables(), site.getMetadataProvider(), null);
      } catch (Exception e) {
        site.warn("Lineage collection failed for " + file.relativePath() + ": " + e.getMessage());
      }
    }
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    body.append(
        HtmlPageWriter.propertyTable(
            modelDetails(name, file.relativePath(), model.getDescription())));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, DocPaths.stableId(file.relativePath()), svg, extraHead));
    List<String> names = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      names.add(table.getName());
      TableDoc doc = new TableDoc();
      doc.setLayer("dv");
      doc.setLogicalName(table.getName());
      doc.setPhysicalName(nvl(table.getTableName(), table.getName()));
      doc.setTableType(table.getTableType() != null ? table.getTableType().name() : "TABLE");
      doc.setDescription(table.getDescription());
      doc.setModelName(name);
      doc.setModelPageHref(htmlPath);
      if (lineage != null) {
        lineage.findTableByLogicalName(table.getName()).ifPresent(doc::setLineage);
        if (doc.getLineage() != null) {
          copyLineageColumns(doc);
        }
      }
      site.addTable(doc);
      addTableSearch(site, doc);
    }
    body.append(tableIndex(site, htmlPath, "dv", names, "Tables"));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.dvNotesHtml(site, file.relativePath(), htmlPath, model.getNotes()));
    }
    PageSupport.writePage(site, htmlPath, name, "Data Vault", body.toString(), extraHead);
  }

  private static void writeBv(DocumentationSite site, ScannedFile file, String path)
      throws HopException {
    BusinessVaultModel model =
        DocumentationArtifactLoader.loadBusinessVaultModel(path, site.getMetadataProvider());
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hbv");
    String name = nvl(model.getName(), DocPaths.withoutExtension(file.relativePath()));
    remember(
        site, file, htmlPath, DocObjectKind.BUSINESS_VAULT_MODEL, name, model.getDescription());
    Function<String, String> href =
        tableName -> DocPaths.relativize(htmlPath, DocPaths.tableHref("bv", tableName));
    SvgDocument svg =
        DocumentSvgExporter.businessVault(
            model,
            site.getVariables(),
            site.getMetadataProvider(),
            site.getOptions().isIncludingNotes(),
            site.getOptions().isDarkSvg(),
            href);
    LineageSnapshot lineage = null;
    if (site.getOptions().isIncludingLineage()) {
      try {
        lineage = BvModelLineageCollector.collect(model, site.getVariables());
      } catch (Exception e) {
        site.warn("Lineage collection failed for " + file.relativePath() + ": " + e.getMessage());
      }
    }
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    body.append(
        HtmlPageWriter.propertyTable(
            modelDetails(name, file.relativePath(), model.getDescription())));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, DocPaths.stableId(file.relativePath()), svg, extraHead));
    List<String> names = new ArrayList<>();
    for (IBvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      names.add(table.getName());
      TableDoc doc = new TableDoc();
      doc.setLayer("bv");
      doc.setLogicalName(table.getName());
      doc.setPhysicalName(nvl(table.getTableName(), table.getName()));
      doc.setTableType(table.getTableType() != null ? table.getTableType().name() : "TABLE");
      doc.setDescription(table.getDescription());
      doc.setModelName(name);
      doc.setModelPageHref(htmlPath);
      if (lineage != null) {
        lineage.findTableByLogicalName(table.getName()).ifPresent(doc::setLineage);
        if (doc.getLineage() != null) {
          copyLineageColumns(doc);
        }
      }
      site.addTable(doc);
      addTableSearch(site, doc);
    }
    body.append(tableIndex(site, htmlPath, "bv", names, "Tables"));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.dvNotesHtml(site, file.relativePath(), htmlPath, notesOf(model)));
    }
    PageSupport.writePage(site, htmlPath, name, "Business Vault", body.toString(), extraHead);
  }

  private static void writeDm(DocumentationSite site, ScannedFile file, String path)
      throws HopException {
    DimensionalModel model =
        DocumentationArtifactLoader.loadDimensionalModel(path, site.getMetadataProvider());
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hdm");
    String name = nvl(model.getName(), DocPaths.withoutExtension(file.relativePath()));
    remember(site, file, htmlPath, DocObjectKind.DIMENSIONAL_MODEL, name, model.getDescription());
    Function<String, String> href =
        tableName -> DocPaths.relativize(htmlPath, DocPaths.tableHref("dm", tableName));
    SvgDocument svg =
        DocumentSvgExporter.dimensional(
            model,
            site.getVariables(),
            site.getMetadataProvider(),
            site.getOptions().isIncludingNotes(),
            site.getOptions().isDarkSvg(),
            href);
    LineageSnapshot lineage = null;
    if (site.getOptions().isIncludingLineage()) {
      try {
        lineage =
            DmModelLineageCollector.collect(model, site.getVariables(), site.getMetadataProvider());
      } catch (Exception e) {
        site.warn("Lineage collection failed for " + file.relativePath() + ": " + e.getMessage());
      }
    }
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    body.append(
        HtmlPageWriter.propertyTable(
            modelDetails(name, file.relativePath(), model.getDescription())));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, DocPaths.stableId(file.relativePath()), svg, extraHead));
    List<String> names = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      names.add(table.getName());
      TableDoc doc = new TableDoc();
      doc.setLayer("dm");
      doc.setLogicalName(table.getName());
      doc.setPhysicalName(nvl(table.getTableName(), table.getName()));
      doc.setTableType(table.getTableType() != null ? table.getTableType().name() : "TABLE");
      doc.setDescription(table.getDescription());
      doc.setModelName(name);
      doc.setModelPageHref(htmlPath);
      if (lineage != null) {
        lineage.findTableByLogicalName(table.getName()).ifPresent(doc::setLineage);
        if (doc.getLineage() != null) {
          copyLineageColumns(doc);
        }
      }
      site.addTable(doc);
      addTableSearch(site, doc);
    }
    body.append(tableIndex(site, htmlPath, "dm", names, "Tables"));
    if (site.getOptions().isIncludingNotes()) {
      body.append(PageSupport.dvNotesHtml(site, file.relativePath(), htmlPath, notesOf(model)));
    }
    PageSupport.writePage(site, htmlPath, name, "Dimensional", body.toString(), extraHead);
  }

  private static void writeHem(DocumentationSite site, ScannedFile file, String path)
      throws HopException {
    ExecutionMapDocument document =
        DocumentationArtifactLoader.loadExecutionMap(
            path, site.getMetadataProvider(), site.getVariables());
    String htmlPath = DocPaths.htmlForSource(file.relativePath(), "hem");
    String name = nvl(document.getName(), DocPaths.withoutExtension(file.relativePath()));
    remember(site, file, htmlPath, DocObjectKind.EXECUTION_MAP, name, null);
    SvgDocument svg =
        DocumentSvgExporter.executionMap(
            document,
            site.getVariables(),
            site.getOptions().isDarkSvg(),
            node -> {
              String target = site.getPageBySource().get(node);
              return target == null ? "#" : DocPaths.relativize(htmlPath, target);
            });
    List<String> extraHead = new ArrayList<>();
    StringBuilder body = new StringBuilder();
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Name", name);
    HtmlPageWriter.row(rows, "Filename", file.relativePath());
    HtmlPageWriter.row(rows, "Root artifact", document.getRootArtifactPath());
    body.append(HtmlPageWriter.propertyTable(rows));
    body.append(
        PageSupport.svgSection(
            site, htmlPath, DocPaths.stableId(file.relativePath()), svg, extraHead));
    body.append("<section id=\"nodes\"><h2>Nodes</h2>\n");
    List<String> headers = List.of("Name", "Type", "Path");
    List<List<String>> nodeRows = new ArrayList<>();
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getName())) {
        continue;
      }
      String id = DocPaths.fragmentId("node", node.getName());
      String pathCell = nvl(node.getPath(), "");
      String pathHtml = HtmlEscaper.escape(pathCell);
      String mapped = site.getPageBySource().get(pathCell);
      if (mapped != null) {
        pathHtml = HtmlPageWriter.link(DocPaths.relativize(htmlPath, mapped), pathCell);
      }
      nodeRows.add(
          List.of(
              "<span id=\""
                  + HtmlEscaper.escape(id)
                  + "\">"
                  + HtmlEscaper.escape(node.getName())
                  + "</span>",
              HtmlEscaper.escape(node.getNodeType() != null ? node.getNodeType().name() : ""),
              pathHtml));
    }
    body.append(HtmlPageWriter.dataTable(headers, nodeRows, true));
    body.append("</section>\n");
    PageSupport.writePage(site, htmlPath, name, "Execution map", body.toString(), extraHead);
  }

  private static void remember(
      DocumentationSite site,
      ScannedFile file,
      String htmlPath,
      DocObjectKind kind,
      String name,
      String description) {
    site.rememberPage(file.relativePath(), htmlPath);
    site.addNav(
        new NavItem(
            kind, name, htmlPath, description, DocPaths.folderSegments(file.relativePath())));
    SearchEntry entry =
        PageSupport.search(
            kind,
            kind.indexKey() + ":" + file.relativePath(),
            name,
            htmlPath,
            description,
            file.relativePath());
    entry.getAliases().add(file.relativePath());
    site.addSearch(entry);
  }

  private static List<String[]> modelDetails(String name, String relative, String description) {
    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Name", name);
    HtmlPageWriter.row(rows, "Filename", relative);
    HtmlPageWriter.row(rows, "Description", description);
    return rows;
  }

  private static String tableIndex(
      DocumentationSite site, String htmlPath, String layer, List<String> names, String heading) {
    if (names.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<section id=\"tables\"><h2>")
        .append(HtmlEscaper.escape(heading))
        .append("</h2><ul>\n");
    for (String name : names) {
      String href = DocPaths.relativize(htmlPath, DocPaths.tableHref(layer, name));
      html.append("<li id=\"")
          .append(HtmlEscaper.escape(DocPaths.fragmentId("table", name)))
          .append("\">")
          .append(HtmlPageWriter.link(href, name))
          .append("</li>\n");
    }
    html.append("</ul></section>\n");
    return html.toString();
  }

  private static List<String> names(SourceModel model) {
    List<String> names = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names;
  }

  private static void copyLineageColumns(TableDoc doc) {
    TableLineage lineage = doc.getLineage();
    if (lineage == null) {
      return;
    }
    for (FieldLineage field : lineage.getFields()) {
      if (field == null || Utils.isEmpty(field.getTargetFieldName())) {
        continue;
      }
      ColumnDoc col = new ColumnDoc();
      col.setName(field.getTargetFieldName());
      col.setDataType(field.getDataType());
      col.setTechnical(field.isTechnical());
      doc.getColumns().add(col);
    }
  }

  private static void addTableSearch(DocumentationSite site, TableDoc doc) {
    String href = DocPaths.tableHref(doc.getLayer(), doc.getLogicalName());
    List<String> path = new ArrayList<>();
    String layerLabel = DocPaths.tableLayerNavLabel(doc.getLayer());
    if (!Utils.isEmpty(layerLabel)) {
      path.add(layerLabel);
    }
    if (!Utils.isEmpty(doc.getModelName())) {
      path.add(doc.getModelName());
    }
    site.addNav(
        new NavItem(DocObjectKind.TABLE, doc.getLogicalName(), href, doc.getDescription(), path));
    SearchEntry entry =
        PageSupport.search(
            DocObjectKind.TABLE,
            "table:" + doc.getLayer() + ":" + doc.getLogicalName(),
            doc.getLogicalName(),
            href,
            doc.getDescription(),
            nvl(doc.getPhysicalName(), "") + " " + nvl(doc.getTableType(), ""));
    if (!Utils.isEmpty(doc.getPhysicalName())) {
      entry.getAliases().add(doc.getPhysicalName());
    }
    site.addSearch(entry);
  }

  private static List<DvNote> notesOf(BusinessVaultModel model) {
    return model.getNotes();
  }

  private static List<DvNote> notesOf(DimensionalModel model) {
    return model.getNotes();
  }

  private static String nvl(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }
}
