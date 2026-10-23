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

import java.util.Locale;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DmTableDisplaySupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DvTableDisplaySupport;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopLocationFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;

/**
 * What a lineage card draws. Dataset table types often live only on the producing job's {@code
 * hop_export} facet, so a dataset with no table type borrows that facet when the names match.
 */
public record LineageViewNodeAppearance(
    String iconPath,
    boolean hopUiIcon,
    String typeLabel,
    String layerLabel,
    EColor fill,
    HopExportFacet resolvedExport) {

  public static final String PIPELINE_ICON = "ui/images/pipeline.svg";
  public static final String SOURCE_ICON = "source-model.svg";
  public static final String CATALOG_ICON = "data-catalog.svg";
  public static final String BUSINESS_VAULT_ICON = "business-vault-model.svg";
  public static final String DATE_DIMENSION_ICON = "date-dimension.svg";

  public static LineageViewNodeAppearance of(LineageGraph graph, LineageNode node) {
    if (node == null) {
      return new LineageViewNodeAppearance(
          CATALOG_ICON, false, "", layerLabel(null, null), EColor.BACKGROUND, null);
    }
    if (node.getKind() == LineageNodeKind.JOB) {
      return new LineageViewNodeAppearance(
          PIPELINE_ICON,
          true,
          msg("LineageViewNodeAppearance.Type.Job"),
          layerLabel(node, node.getHopExport()),
          EColor.LIGHTBLUE,
          node.getHopExport());
    }
    HopExportFacet export = resolvedExport(graph, node);
    if (export != null && !Utils.isEmpty(export.getTableType())) {
      String type = export.getTableType().trim();
      return new LineageViewNodeAppearance(
          iconForTableType(type, export, node),
          false,
          labelForTableType(type),
          layerLabel(node, export),
          EColor.BACKGROUND,
          export);
    }
    boolean catalog = hasCatalogLocation(node.getHopLocation());
    return new LineageViewNodeAppearance(
        catalog ? CATALOG_ICON : SOURCE_ICON,
        false,
        msg("LineageViewNodeAppearance.Type.Source"),
        msg("LineageViewNodeAppearance.Layer.Source"),
        EColor.LIGHTGRAY,
        node.getHopExport());
  }

  /**
   * Node the context menu should read. A dataset copies the producing job's {@code hop_export} when
   * it has no table type of its own. A job copies {@code hop_location} from the dataset it writes.
   * The session graph is left unchanged.
   */
  public static LineageNode withResolvedExport(LineageGraph graph, LineageNode node) {
    if (node == null || node.getKind() == LineageNodeKind.JOB) {
      return node;
    }
    if (hasTableType(node.getHopExport())) {
      return node;
    }
    HopExportFacet borrowed = producingExport(graph, node);
    if (borrowed == null) {
      return node;
    }
    return node.toBuilder().hopExport(borrowed).build();
  }

  /** Export plus location, so a job and its output dataset share the same actions. */
  public static LineageNode forActions(LineageGraph graph, LineageNode node) {
    LineageNode resolved = withResolvedExport(graph, node);
    if (resolved == null || resolved.getKind() != LineageNodeKind.JOB) {
      return resolved;
    }
    if (resolved.getHopLocation() != null) {
      return resolved;
    }
    LineageNode output = outputDataset(graph, resolved);
    if (output == null || output.getHopLocation() == null) {
      return resolved;
    }
    return resolved.toBuilder().hopLocation(output.getHopLocation()).build();
  }

  /** Job that writes this dataset, matched by export name or the last segment of the job name. */
  public static LineageNode producingJob(LineageGraph graph, LineageNode dataset) {
    if (graph == null || dataset == null || dataset.getId() == null) {
      return null;
    }
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (edge == null || !dataset.getId().equals(edge.getToNodeId())) {
        continue;
      }
      LineageNode from = graph.findNode(edge.getFromNodeId());
      if (from != null
          && from.getKind() == LineageNodeKind.JOB
          && jobMatchesDataset(from, dataset)) {
        return from;
      }
    }
    return null;
  }

  /** Dataset this job writes, matched the same way as {@link #producingJob}. */
  public static LineageNode outputDataset(LineageGraph graph, LineageNode job) {
    if (graph == null || job == null || job.getId() == null) {
      return null;
    }
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      if (edge == null || !job.getId().equals(edge.getFromNodeId())) {
        continue;
      }
      LineageNode to = graph.findNode(edge.getToNodeId());
      if (to != null && to.getKind() == LineageNodeKind.DATASET && jobMatchesDataset(job, to)) {
        return to;
      }
    }
    return null;
  }

  public ClassLoader iconClassLoader() {
    if (hopUiIcon) {
      return org.apache.hop.ui.hopgui.HopGui.class.getClassLoader();
    }
    return LineageViewNodeAppearance.class.getClassLoader();
  }

  private static HopExportFacet resolvedExport(LineageGraph graph, LineageNode node) {
    if (hasTableType(node.getHopExport())) {
      return node.getHopExport();
    }
    HopExportFacet borrowed = producingExport(graph, node);
    return borrowed != null ? borrowed : node.getHopExport();
  }

  private static HopExportFacet producingExport(LineageGraph graph, LineageNode dataset) {
    LineageNode job = producingJob(graph, dataset);
    if (job == null || !hasTableType(job.getHopExport())) {
      return null;
    }
    return job.getHopExport();
  }

  private static boolean jobMatchesDataset(LineageNode job, LineageNode dataset) {
    HopExportFacet export = job.getHopExport();
    if (hasTableType(export)) {
      return sameName(dataset.getName(), export.getLogicalName())
          || sameName(dataset.getName(), export.getPhysicalTableName());
    }
    return sameName(dataset.getName(), lastSegment(job.getName()));
  }

  private static String lastSegment(String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    int slash = name.lastIndexOf('/');
    return slash >= 0 ? name.substring(slash + 1) : name;
  }

  private static boolean hasTableType(HopExportFacet export) {
    return export != null && !Utils.isEmpty(export.getTableType());
  }

  private static boolean sameName(String left, String right) {
    if (Utils.isEmpty(left) || Utils.isEmpty(right)) {
      return false;
    }
    return left.trim().equalsIgnoreCase(right.trim());
  }

  private static boolean hasCatalogLocation(HopLocationFacet location) {
    return location != null
        && (!Utils.isEmpty(location.getCatalogKey())
            || !Utils.isEmpty(location.getCatalogConnection()));
  }

  private static String iconForTableType(String type, HopExportFacet export, LineageNode node) {
    String code = type.toUpperCase(Locale.ROOT);
    if ("DIMENSION".equals(code) && isDateDimension(export, node)) {
      return DATE_DIMENSION_ICON;
    }
    if ("HUB".equals(code)
        || "LINK".equals(code)
        || "SATELLITE".equals(code)
        || "REFERENCE".equals(code)
        || "LINKED_TABLE".equals(code)
        || "TABLE_REFERENCE".equals(code)) {
      DvTableType dvType =
          "TABLE_REFERENCE".equals(code) ? DvTableType.LINKED_TABLE : DvTableType.valueOf(code);
      return DvTableDisplaySupport.getImagePath(dvType);
    }
    return switch (code) {
      case "FACT", "PERIODIC_SNAPSHOT_FACT", "ACCUMULATING_SNAPSHOT_FACT", "AGGREGATE_FACT" ->
          DmTableDisplaySupport.FACT_ICON;
      case "FACTLESS_FACT" -> DmTableDisplaySupport.FACTLESS_FACT_ICON;
      case "DIMENSION" -> DmTableDisplaySupport.DIMENSION_ICON;
      case "DIMENSION_ALIAS" -> DmTableDisplaySupport.DIMENSION_ALIAS_ICON;
      case "JUNK_DIMENSION" -> DmTableDisplaySupport.JUNK_DIMENSION_ICON;
      case "RANGE_DIMENSION" -> DmTableDisplaySupport.RANGE_DIMENSION_ICON;
      case "BRIDGE" -> DmTableDisplaySupport.BRIDGE_ICON;
      case "SCD2", "PIT", "IDENTITY_MAP", "BUSINESS_TABLE", "SOURCE_QUERY", "SOURCE_CALENDAR" ->
          BUSINESS_VAULT_ICON;
      default -> CATALOG_ICON;
    };
  }

  private static boolean isDateDimension(HopExportFacet export, LineageNode node) {
    return sameName("d_date", export != null ? export.getLogicalName() : null)
        || sameName("dim_date", export != null ? export.getLogicalName() : null)
        || sameName("d_date", export != null ? export.getPhysicalTableName() : null)
        || sameName("dim_date", export != null ? export.getPhysicalTableName() : null)
        || sameName("d_date", node != null ? node.getName() : null)
        || sameName("dim_date", node != null ? node.getName() : null);
  }

  private static String labelForTableType(String type) {
    String code = type.toUpperCase(Locale.ROOT);
    return switch (code) {
      case "HUB" -> DvTableType.HUB.getDescription();
      case "LINK" -> DvTableType.LINK.getDescription();
      case "SATELLITE" -> DvTableType.SATELLITE.getDescription();
      case "REFERENCE" -> DvTableType.REFERENCE.getDescription();
      case "LINKED_TABLE", "TABLE_REFERENCE" -> DvTableType.LINKED_TABLE.getDescription();
      case "DIMENSION" -> DmTableType.DIMENSION.getDescription();
      case "DIMENSION_ALIAS" -> DmTableType.DIMENSION_ALIAS.getDescription();
      case "JUNK_DIMENSION" -> DmTableType.JUNK_DIMENSION.getDescription();
      case "RANGE_DIMENSION" -> DmTableType.RANGE_DIMENSION.getDescription();
      case "FACT" -> DmTableType.FACT.getDescription();
      case "FACTLESS_FACT" -> DmTableType.FACTLESS_FACT.getDescription();
      case "PERIODIC_SNAPSHOT_FACT" -> DmTableType.PERIODIC_SNAPSHOT_FACT.getDescription();
      case "ACCUMULATING_SNAPSHOT_FACT" -> DmTableType.ACCUMULATING_SNAPSHOT_FACT.getDescription();
      case "AGGREGATE_FACT" -> DmTableType.AGGREGATE_FACT.getDescription();
      case "BRIDGE" -> DmTableType.BRIDGE.getDescription();
      case "SCD2" -> msg("LineageViewNodeAppearance.Type.Scd2");
      case "PIT" -> msg("LineageViewNodeAppearance.Type.Pit");
      case "IDENTITY_MAP" -> msg("LineageViewNodeAppearance.Type.IdentityMap");
      case "BUSINESS_TABLE" -> msg("LineageViewNodeAppearance.Type.BusinessTable");
      case "SOURCE_QUERY" -> msg("LineageViewNodeAppearance.Type.SourceQuery");
      case "SOURCE_CALENDAR" -> msg("LineageViewNodeAppearance.Type.SourceCalendar");
      default -> type;
    };
  }

  private static String layerLabel(LineageNode node, HopExportFacet export) {
    String code = null;
    if (export != null && !Utils.isEmpty(export.getModelLayer())) {
      code = export.getModelLayer().trim();
    } else if (node != null && node.getLayer() != null) {
      code = node.getLayer().getCode();
    }
    if (Utils.isEmpty(code) || LineageGraphLayer.SOURCE.name().equalsIgnoreCase(code)) {
      return msg("LineageViewNodeAppearance.Layer.Source");
    }
    return switch (code.toUpperCase(Locale.ROOT)) {
      case "DV" -> msg("LineageViewNodeAppearance.Layer.DV");
      case "BV" -> msg("LineageViewNodeAppearance.Layer.BV");
      case "DM" -> msg("LineageViewNodeAppearance.Layer.DM");
      default -> code;
    };
  }

  private static String msg(String key) {
    return BaseMessages.getString(LineageViewNodeAppearance.class, key);
  }
}
