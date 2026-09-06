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
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.spi.IDataCatalog;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;

/** Documents catalog record definitions (best-effort; catalog errors become warnings). */
public final class CatalogDocWriter {

  private CatalogDocWriter() {}

  public static void writeAll(DocumentationSite site) {
    IHopMetadataProvider provider = site.getMetadataProvider();
    if (provider == null) {
      return;
    }
    try {
      IHopMetadataSerializer<DataCatalogMeta> serializer =
          provider.getSerializer(DataCatalogMeta.class);
      for (String name : serializer.listObjectNames()) {
        DataCatalogMeta meta = serializer.load(name);
        if (meta == null || !meta.isEnabled()) {
          continue;
        }
        documentCatalog(site, meta);
      }
    } catch (Exception e) {
      site.warn("Unable to document catalogs: " + e.getMessage());
    }
  }

  private static void documentCatalog(DocumentationSite site, DataCatalogMeta meta) {
    IDataCatalog catalog = meta.getCatalogOrDefault();
    try {
      catalog.connect(meta, site.getVariables(), site.getMetadataProvider());
      List<RecordDefinitionRef> refs = catalog.list(new RecordDefinitionQuery());
      if (refs == null) {
        return;
      }
      for (RecordDefinitionRef ref : refs) {
        if (ref == null || ref.getKey() == null) {
          continue;
        }
        try {
          RecordDefinition definition = catalog.read(ref.getKey());
          if (definition != null) {
            writeRecord(site, meta.getName(), definition);
          }
        } catch (HopException e) {
          site.warn(
              "Unable to write catalog record "
                  + ref.getKey().getNamespace()
                  + "/"
                  + ref.getKey().getName()
                  + ": "
                  + e.getMessage());
        } catch (Exception e) {
          site.warn(
              "Unable to read catalog record "
                  + ref.getKey().getNamespace()
                  + "/"
                  + ref.getKey().getName()
                  + ": "
                  + e.getMessage());
        }
      }
    } catch (Exception e) {
      site.warn("Unable to open catalog " + meta.getName() + ": " + e.getMessage());
    } finally {
      try {
        catalog.disconnect();
      } catch (HopException ignored) {
        // Ignore disconnect failures.
      }
    }
  }

  private static void writeRecord(
      DocumentationSite site, String catalogName, RecordDefinition definition) throws HopException {
    String namespace = definition.getKey().getNamespace();
    String name = definition.getKey().getName();
    String htmlPath = DocPaths.catalogHref(namespace, name);
    List<String> path = new ArrayList<>();
    if (catalogName != null && !catalogName.isEmpty()) {
      path.add(catalogName);
    }
    if (namespace != null && !namespace.isEmpty()) {
      path.add(namespace);
    }
    site.addNav(
        new NavItem(DocObjectKind.CATALOG, name, htmlPath, definition.getDescription(), path));
    site.addSearch(
        PageSupport.search(
            DocObjectKind.CATALOG,
            "catalog:" + namespace + ":" + name,
            name,
            htmlPath,
            definition.getDescription(),
            namespace + " " + catalogName));

    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Catalog", catalogName);
    HtmlPageWriter.row(rows, "Namespace", namespace);
    HtmlPageWriter.row(rows, "Name", name);
    HtmlPageWriter.row(
        rows, "Type", definition.getType() != null ? definition.getType().name() : "");
    HtmlPageWriter.row(rows, "Description", definition.getDescription());
    if (definition.getTags() != null && !definition.getTags().isEmpty()) {
      HtmlPageWriter.row(rows, "Tags", String.join(", ", definition.getTags()));
    }
    StringBuilder body = new StringBuilder();
    body.append(HtmlPageWriter.propertyTable(rows));
    if (definition.getFields() != null && definition.getFields().size() > 0) {
      List<List<String>> fieldRows = new ArrayList<>();
      for (int i = 0; i < definition.getFields().size(); i++) {
        IValueMeta value = definition.getFields().getValueMeta(i);
        fieldRows.add(
            List.of(
                value.getName(),
                value.getTypeDesc(),
                value.getComments() == null ? "" : value.getComments()));
      }
      body.append("<section id=\"fields\"><h2>Fields</h2>\n");
      body.append(HtmlPageWriter.dataTable(List.of("Name", "Type", "Comments"), fieldRows, false));
      body.append("</section>\n");
    }
    PageSupport.writePage(site, htmlPath, name, "Catalog", body.toString());
  }
}
