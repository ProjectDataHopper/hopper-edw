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
package org.hopper.edw.datavault.lineageview;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopLocationFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageWarning;

/** Markdown details for a selected lineage node. */
public final class LineageViewDetailsSupport {

  private LineageViewDetailsSupport() {}

  public static String emptySelection() {
    return "_Select a node to see details._";
  }

  public static String format(LineageNode node, LineageGraph graph) {
    return format(node, graph, null);
  }

  public static String format(LineageNode node, LineageGraph graph, LineageViewOpsBadge opsBadge) {
    if (node == null) {
      return emptySelection();
    }
    StringBuilder text = new StringBuilder();
    String title = !Utils.isEmpty(node.getName()) ? node.getName() : node.getId();
    heading(text, 1, title);
    appendSubtitle(text, node, graph);
    heading(text, 2, "Identity");
    bullet(text, "Id", node.getId());
    if (node.getKind() != null) {
      bullet(text, "Kind", node.getKind().getCode());
    }
    if (node.getLayer() != null) {
      bullet(text, "Layer", node.getLayer().getCode());
    }
    bullet(text, "Namespace", node.getNamespace());
    bullet(text, "Name", node.getName());
    // Structure freshness only — never Marquez latestRun.durationMs.
    bullet(text, "Exported", node.getLastExportedAt());

    HopExportFacet export = node.getHopExport();
    if (export != null && hasExport(export)) {
      heading(text, 2, "Hop identity");
      bullet(text, "Model layer", export.getModelLayer());
      bullet(text, "Model name", export.getModelName());
      bullet(text, "Logical name", export.getLogicalName());
      bullet(text, "Physical table", export.getPhysicalTableName());
      bullet(text, "Model file", export.getModelFilename());
      bullet(text, "Table type", export.getTableType());
      bullet(text, "Catalog connection", export.getCatalogConnection());
      bullet(text, "Resource group", export.getResourceGroup());
      bullet(text, "Target database", export.getTargetDatabase());
    }

    HopLocationFacet location = node.getHopLocation();
    if (location != null && hasLocation(location)) {
      heading(text, 2, "Location");
      bullet(text, "Kind", location.getKind());
      bullet(text, "Connection", location.getConnectionName());
      bullet(text, "Schema", location.getSchemaName());
      bullet(text, "Table", location.getTableName());
      bullet(text, "URI", location.getUri());
      bullet(text, "Catalog key", location.getCatalogKey());
      bullet(text, "Catalog connection", location.getCatalogConnection());
    }

    if (opsBadge != null && !Utils.isEmpty(opsBadge.label())) {
      heading(text, 2, "Load time");
      bullet(text, opsBadge.stale() ? "Last load (export)" : "Last load (OPS)", opsBadge.label());
      if (!Utils.isEmpty(opsBadge.tooltip())) {
        text.append('\n').append(opsBadge.tooltip()).append('\n');
      }
    }

    if (node.getSchemaFieldNames() != null && !node.getSchemaFieldNames().isEmpty()) {
      heading(text, 2, "Fields");
      for (String field : node.getSchemaFieldNames()) {
        if (!Utils.isEmpty(field)) {
          text.append("- ").append(code(field)).append('\n');
        }
      }
    }

    if (node.getWarnings() != null && !node.getWarnings().isEmpty()) {
      heading(text, 2, "Warnings");
      for (LineageWarning warning : node.getWarnings()) {
        if (warning == null) {
          continue;
        }
        String code = warning.getCode() != null ? warning.getCode() : "warning";
        String message = warning.getMessage() != null ? warning.getMessage() : "";
        if (Utils.isEmpty(message)) {
          text.append("- ").append(code(code)).append('\n');
        } else {
          text.append("- **")
              .append(escapeInline(code))
              .append(":** ")
              .append(message)
              .append('\n');
        }
      }
    }
    return text.toString().strip();
  }

  private static void appendSubtitle(StringBuilder text, LineageNode node, LineageGraph graph) {
    StringBuilder subtitle = new StringBuilder();
    if (node.getKind() != null) {
      subtitle.append(node.getKind().getCode());
    }
    if (node.getLayer() != null) {
      if (!subtitle.isEmpty()) {
        subtitle.append(" · ");
      }
      subtitle.append(node.getLayer().getCode());
    }
    if (graph != null && node.getId() != null && node.getId().equals(graph.getSeedNodeId())) {
      if (!subtitle.isEmpty()) {
        subtitle.append(" · ");
      }
      subtitle.append("seed");
    }
    if (!subtitle.isEmpty()) {
      text.append(subtitle).append("\n\n");
    }
  }

  private static boolean hasExport(HopExportFacet export) {
    return !Utils.isEmpty(export.getModelLayer())
        || !Utils.isEmpty(export.getModelName())
        || !Utils.isEmpty(export.getLogicalName())
        || !Utils.isEmpty(export.getPhysicalTableName())
        || !Utils.isEmpty(export.getModelFilename())
        || !Utils.isEmpty(export.getTableType())
        || !Utils.isEmpty(export.getCatalogConnection())
        || !Utils.isEmpty(export.getResourceGroup())
        || !Utils.isEmpty(export.getTargetDatabase());
  }

  private static boolean hasLocation(HopLocationFacet location) {
    return !Utils.isEmpty(location.getKind())
        || !Utils.isEmpty(location.getConnectionName())
        || !Utils.isEmpty(location.getSchemaName())
        || !Utils.isEmpty(location.getTableName())
        || !Utils.isEmpty(location.getUri())
        || !Utils.isEmpty(location.getCatalogKey())
        || !Utils.isEmpty(location.getCatalogConnection());
  }

  private static void heading(StringBuilder text, int level, String title) {
    if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
      text.append('\n');
    }
    if (!text.isEmpty()) {
      text.append('\n');
    }
    text.append("#".repeat(Math.max(1, Math.min(level, 6))))
        .append(' ')
        .append(title)
        .append("\n\n");
  }

  private static void bullet(StringBuilder text, String label, String value) {
    if (Utils.isEmpty(value)) {
      return;
    }
    text.append("- **").append(escapeInline(label)).append(":** ").append(code(value)).append('\n');
  }

  private static String code(String value) {
    if (Utils.isEmpty(value)) {
      return "";
    }
    if (value.indexOf('`') >= 0) {
      return escapeInline(value);
    }
    return "`" + value + "`";
  }

  private static String escapeInline(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_");
  }
}
