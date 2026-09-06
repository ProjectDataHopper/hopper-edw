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
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.metadata.MetadataPropertyWalker;
import org.hopper.edw.datavault.documentation.metadata.MetadataPropertyWalker.PropertyRow;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;

/** Documents Hop metadata objects with secrets redacted. */
public final class MetadataDocWriter {

  private MetadataDocWriter() {}

  public static void rememberHrefs(DocumentationSite site) {
    IHopMetadataProvider provider = site.getMetadataProvider();
    if (provider == null) {
      return;
    }
    try {
      for (Class<IHopMetadata> type : provider.getMetadataClasses()) {
        IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(type);
        String typeKey = typeKey(type, serializer);
        for (String name : serializer.listObjectNames()) {
          site.rememberMetadata(typeKey, name, DocPaths.metadataHref(typeKey, name));
          site.rememberMetadata(name, name, DocPaths.metadataHref(typeKey, name));
        }
      }
    } catch (Exception e) {
      site.warn("Unable to list metadata: " + e.getMessage());
    }
  }

  public static void writeAll(DocumentationSite site) throws HopException {
    IHopMetadataProvider provider = site.getMetadataProvider();
    if (provider == null) {
      return;
    }
    try {
      for (Class<IHopMetadata> type : provider.getMetadataClasses()) {
        IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(type);
        String typeKey = typeKey(type, serializer);
        String typeLabel = serializer.getDescription();
        for (String name : serializer.listObjectNames()) {
          writeOne(site, serializer, typeKey, typeLabel, name);
        }
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to document metadata", e);
    }
  }

  private static void writeOne(
      DocumentationSite site,
      IHopMetadataSerializer<IHopMetadata> serializer,
      String typeKey,
      String typeLabel,
      String name)
      throws HopException {
    String htmlPath = DocPaths.metadataHref(typeKey, name);
    IHopMetadata object;
    try {
      object = serializer.load(name);
    } catch (Exception e) {
      site.warn("Unable to load metadata " + typeKey + "/" + name + ": " + e.getMessage());
      return;
    }
    List<String> path = new ArrayList<>();
    path.add(Utils.isEmpty(typeLabel) ? typeKey : typeLabel);
    path.addAll(DocPaths.pathSegments(object.getVirtualPath()));
    site.addNav(new NavItem(DocObjectKind.METADATA, name, htmlPath, typeLabel, path));

    List<String[]> rows = HtmlPageWriter.rowList();
    HtmlPageWriter.row(rows, "Name", name);
    HtmlPageWriter.row(rows, "Type", typeLabel);
    StringBuilder body = new StringBuilder();
    body.append(HtmlPageWriter.propertyTable(rows));
    List<PropertyRow> properties = MetadataPropertyWalker.walk(object);
    StringBuilder keywords = new StringBuilder(typeKey);
    for (PropertyRow property : properties) {
      if (!Utils.isEmpty(property.label()) && !property.label().equals(property.name())) {
        keywords.append(' ').append(property.label());
      }
    }
    SearchEntry entry =
        PageSupport.search(
            DocObjectKind.METADATA,
            "metadata:" + typeKey + ":" + name,
            name,
            htmlPath,
            typeLabel,
            keywords.toString());
    site.addSearch(entry);
    if (!properties.isEmpty()) {
      body.append("<section id=\"properties\"><h2>Properties</h2>\n");
      List<List<String>> propRows = new java.util.ArrayList<>();
      boolean anyDescription = false;
      for (PropertyRow property : properties) {
        if (!Utils.isEmpty(property.toolTip())) {
          anyDescription = true;
          break;
        }
      }
      for (PropertyRow property : properties) {
        String label = Utils.isEmpty(property.label()) ? property.name() : property.label();
        String labelHtml =
            "<span title=\""
                + HtmlEscaper.escape(property.name())
                + "\">"
                + HtmlEscaper.escape(label)
                + "</span>";
        if (anyDescription) {
          propRows.add(
              List.of(
                  labelHtml,
                  HtmlEscaper.escape(nvl(property.value(), "")),
                  HtmlEscaper.escape(nvl(property.toolTip(), ""))));
        } else {
          propRows.add(List.of(labelHtml, HtmlEscaper.escape(nvl(property.value(), ""))));
        }
      }
      if (anyDescription) {
        body.append(
            HtmlPageWriter.dataTable(List.of("Property", "Value", "Description"), propRows, true));
      } else {
        body.append(HtmlPageWriter.dataTable(List.of("Property", "Value"), propRows, true));
      }
      body.append("</section>\n");
    }
    List<NavItem> usedBy = site.getUsedBy().get(name);
    if (usedBy != null && !usedBy.isEmpty()) {
      body.append("<section id=\"used-by\"><h2>Used by</h2><ul>");
      for (NavItem item : usedBy) {
        body.append("<li>")
            .append(HtmlPageWriter.link(DocPaths.relativize(htmlPath, item.href()), item.title()))
            .append("</li>");
      }
      body.append("</ul></section>\n");
    }
    PageSupport.writePage(site, htmlPath, name, nvl(typeLabel, "Metadata"), body.toString());
  }

  private static String typeKey(Class<?> type, IHopMetadataSerializer<?> serializer) {
    HopMetadata annotation = type.getAnnotation(HopMetadata.class);
    if (annotation != null && !Utils.isEmpty(annotation.key())) {
      return annotation.key();
    }
    if (serializer != null && !Utils.isEmpty(serializer.getDescription())) {
      return serializer.getDescription();
    }
    return type.getSimpleName();
  }

  private static String nvl(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }
}
