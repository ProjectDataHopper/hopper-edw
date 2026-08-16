/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.file.modelgraph.DvTableDisplaySupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableCardLayout;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvIntegrationSupport;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvLinkedTableSupport;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableResolutionSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;

/**
 * Basic painter for a Data Vault model using IGc. Draws tables (hubs, links, satellites) at their
 * locations with icons and names. Draws straight, side-spread relationship lines from links to
 * their participating hubs and from satellites to their parent hubs (or links), with a square
 * attachment point on each table edge. Supports mouse-over underline on table names (for hyperlink
 * style) and populates AreaOwners for name (TRANSFORM_NAME) vs body (TRANSFORM_ICON) to support
 * differentiated clicks. If a table has a non-empty description, draws a small INFO_DISABLED icon
 * at top-left and registers a TRANSFORM_INFO_ICON area (for tooltip on hover). Optionally shows the
 * resolved hash key field name in small print below the table name ({@link
 * #setShowHashKeyFieldNames(boolean)}).
 */
@Getter
@Setter
public class DataVaultModelPainter extends BasePainter {

  private static final Class<?> PKG = DataVaultModelPainter.class;
  private static final int EMPTY_MODEL_HINT_PADDING = 16;
  private static final int EMPTY_MODEL_HINT_LINE_GAP = 6;

  private final DataVaultModel model;
  private final Map<String, IDvTable> tableByName;

  // Transient drag state for drawing candidate relationship line (middle-btn or shift+left drag)
  // while creating hub<->satellite or hub<->link. These are set by the owning graph before each
  // draw.
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coords from mouse
  private IDvTable candidateRelationshipTarget;

  /** When true, render the resolved hash key field name in small print below the table name. */
  private boolean showHashKeyFieldNames;

  /** When false, suppress the first-run empty canvas hint (e.g. SVG export). */
  private boolean showEmptyModelHint = true;

  private static final int ICON_SIZE = ModelGraphTableCardLayout.ICON_SIZE;
  private static final int MARGIN = ModelGraphTableCardLayout.MARGIN;
  private static final int MINI_ICON_SIZE = 16;

  // Note: TABLE_* removed in favor of per-table dynamic sizes based on textExtent()

  public DataVaultModelPainter(
      DataVaultModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
    this.gc = gc;
    this.tableByName = new HashMap<>();

    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table.getName() != null) {
          tableByName.put(table.getName(), table);
        }
      }
    }
  }

  /**
   * Set (or clear) the transient relationship drag state used to render a candidate line from a
   * source table center to the current mouse position (screen coords). Called by HopGuiVaultGraph
   * before each paint during a drag operation.
   */
  public void setRelationshipDragInfo(
      IDvTable startRelationshipTable,
      Point relationshipDragEndLocation,
      IDvTable candidateRelationshipTarget) {
    this.startRelationshipTable = startRelationshipTable;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void drawDataVaultModel() {
    if (model == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }

    // reset the transform before clearing the background
    gc.setTransform(0.0f, 0.0f, 1.0f);

    // Simple light gray background
    gc.setBackground(IGc.EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    // Set the appropriate transform before drawing the tables and relations
    // IMPORTANT: offset is not used in Hop 2.18 so we'll need to calculate manually
    //
    gc.setTransform((float) (offset.x), (float) (offset.y), magnification);

    drawDataVaultModelImage();
    drawRect(selectionRegion);

    // Draw the navigation view in native pixels to make calculation a bit easier.
    //
    gc.setTransform(0.0f, 0.0f, 1.0f);

    if (showEmptyModelHint && isEmptyModel()) {
      drawEmptyModelHint();
    }

    drawNavigationView();
  }

  public void drawDataVaultModelImage() {
    if (gridSize > 1) {
      drawGrid();
    }

    // Draw connections first (behind tables)
    gc.setForeground(IGc.EColor.BLACK);
    gc.setLineWidth(1);
    Map<String, ModelGraphEdgeLayout.EdgeGeometry> connections = drawTableConnections();

    // Draw the (temporary) candidate relationship line (if dragging), before tables so it renders
    // underneath the table cards, just like permanent connections. Uses logical coords under the
    // active transform.
    drawRelationshipCandidateLine();

    // Draw notes behind tables (like pipeline/workflow graphs).
    drawNotes(model.getNotes());

    // Draw the tables
    for (IDvTable table : model.getTables()) {
      Point loc = real2screen(table.getLocation().x, table.getLocation().y);
      if (loc == null) {
        loc = new Point(50, 50);
      }
      drawTable(table, loc.x, loc.y);
    }

    // Attachment squares sit on table edges, so they are painted after the cards.
    gc.setForeground(IGc.EColor.BLACK);
    gc.setLineWidth(1);
    ModelGraphConnectionGeometry.drawAnchorSquares(gc, connections.values());
  }

  private Map<String, ModelGraphEdgeLayout.EdgeGeometry> drawTableConnections() {
    List<ModelGraphEdgeLayout.Edge> edges = collectTableConnectionEdges();
    Map<String, ModelGraphEdgeLayout.EdgeGeometry> layout = ModelGraphEdgeLayout.layout(edges);
    for (ModelGraphEdgeLayout.Edge edge : edges) {
      ModelGraphEdgeLayout.EdgeGeometry geometry = layout.get(edge.id());
      if (geometry == null) {
        continue;
      }
      ModelGraphConnectionGeometry.drawStraightConnection(
          gc, geometry.fromAnchor(), geometry.toAnchor());
    }
    return layout;
  }

  private List<ModelGraphEdgeLayout.Edge> collectTableConnectionEdges() {
    List<ModelGraphEdgeLayout.Edge> edges = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      if (table.getTableType() == DvTableType.LINK && table instanceof DvLink link) {
        collectLinkConnectionEdges(link, edges);
      }
      if (table.getTableType() == DvTableType.SATELLITE) {
        collectSatelliteConnectionEdges(table, edges);
      }
    }
    return edges;
  }

  private void collectLinkConnectionEdges(DvLink link, List<ModelGraphEdgeLayout.Edge> edges) {
    Bounds linkBounds = tableBounds(link);
    if (linkBounds == null) {
      return;
    }
    String linkKey = tableKey(link);
    for (String hubName : link.getHubNames()) {
      IDvTable hub = tableByName.get(hubName);
      Bounds hubBounds = tableBounds(hub);
      if (hubBounds == null) {
        continue;
      }
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "link-hub|" + linkKey + "|" + tableKey(hub),
              linkKey,
              linkBounds,
              tableKey(hub),
              hubBounds));
    }
  }

  private void collectSatelliteConnectionEdges(
      IDvTable sat, List<ModelGraphEdgeLayout.Edge> edges) {
    if (!(sat instanceof DvSatellite satellite)) {
      return;
    }
    Bounds satBounds = tableBounds(sat);
    if (satBounds == null) {
      return;
    }
    // Prefer hub, as per standard DV2.0; fall back to link if no hub
    String parentName = satellite.getHubName();
    if (parentName == null || parentName.isEmpty()) {
      parentName = satellite.getLinkName();
    }
    if (parentName == null || parentName.isEmpty()) {
      return;
    }
    IDvTable parent = tableByName.get(parentName);
    Bounds parentBounds = tableBounds(parent);
    if (parentBounds == null) {
      return;
    }
    String satKey = tableKey(sat);
    String parentKey = tableKey(parent);
    edges.add(
        new ModelGraphEdgeLayout.Edge(
            "sat-parent|" + satKey + "|" + parentKey, satKey, satBounds, parentKey, parentBounds));
  }

  private Bounds tableBounds(IDvTable table) {
    if (table == null || table.getLocation() == null) {
      return null;
    }
    Point loc = real2screen(table.getLocation().x, table.getLocation().y);
    Point box = calculateTableBoxSize(table);
    return new Bounds(loc.x, loc.y, box.x, box.y);
  }

  private static String tableKey(IDvTable table) {
    return "table|" + (table.getName() != null ? table.getName() : "?");
  }

  private boolean isEmptyModel() {
    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    return model.getTables().isEmpty() && notesEmpty;
  }

  /**
   * Empty-model onboarding only: true when the project sources namespace has at least one DV
   * source. Uses a short-circuit catalog existence check (session-cached) — never lists all source
   * names on paint.
   */
  private boolean hasCatalogRecordDefinitions() {
    if (metadataProvider == null) {
      return false;
    }
    try {
      return DvSourceCatalogService.hasAnySources(model, variables, metadataProvider);
    } catch (HopException ignored) {
      return false;
    }
  }

  private List<String> getEmptyModelHintLines() {
    List<String> lines = new ArrayList<>();
    lines.add(BaseMessages.getString(PKG, "DataVaultModelPainter.EmptyModel.Intro"));
    if (!hasCatalogRecordDefinitions()) {
      lines.add(
          BaseMessages.getString(PKG, "DataVaultModelPainter.EmptyModel.AddRecordDefinitions"));
    }
    lines.add(BaseMessages.getString(PKG, "DataVaultModelPainter.EmptyModel.AddTables"));
    return lines;
  }

  /** Onboarding hint centered in the canvas when the model has no tables or notes. */
  private void drawEmptyModelHint() {
    if (area == null || area.x <= 0 || area.y <= 0) {
      return;
    }

    List<String> lines = getEmptyModelHintLines();
    if (lines.isEmpty()) {
      return;
    }

    gc.setFont(EFont.GRAPH);
    int maxLineWidth = 0;
    int totalTextHeight = 0;
    List<Point> lineExtents = new ArrayList<>(lines.size());
    int lineHeight = gc.textExtent("Ay").y;
    for (String line : lines) {
      Point extent = Utils.isEmpty(line) ? new Point(0, lineHeight / 2) : gc.textExtent(line);
      lineExtents.add(extent);
      maxLineWidth = Math.max(maxLineWidth, extent.x);
      totalTextHeight += extent.y;
      if (lineExtents.size() < lines.size()) {
        totalTextHeight += EMPTY_MODEL_HINT_LINE_GAP;
      }
    }

    int boxWidth = maxLineWidth + (2 * EMPTY_MODEL_HINT_PADDING);
    int boxHeight = totalTextHeight + (2 * EMPTY_MODEL_HINT_PADDING);
    int boxX = 32;
    int boxY = 32;

    int alpha = gc.getAlpha();
    gc.setAlpha(210);
    gc.setTransform(0, 0, 2.5f);
    gc.setBackground(IGc.EColor.WHITE);
    gc.setForeground(IGc.EColor.LIGHTGRAY);
    gc.fillRoundRectangle(boxX, boxY, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.drawRoundRectangle(boxX, boxY, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.setAlpha(alpha);

    gc.setForeground(IGc.EColor.DARKGRAY);
    int textY = boxY + EMPTY_MODEL_HINT_PADDING;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      Point extent = lineExtents.get(i);
      if (!Utils.isEmpty(line)) {
        int textX = boxX + EMPTY_MODEL_HINT_PADDING + Math.max(0, (maxLineWidth - extent.x) / 2);
        gc.drawText(line, textX, textY, true);
      }
      textY += extent.y;
      if (i < lines.size() - 1) {
        textY += EMPTY_MODEL_HINT_LINE_GAP;
      }
    }
    gc.setTransform(0, 0, 1.0f);
  }

  private void drawTable(IDvTable table, int x, int y) {
    Point box = calculateTableBoxSize(table);

    // Draw rounded rectangle background
    drawTableBox(table, x, y, box);

    // Draw icon using SVG  top-left + margin
    drawTableIcon(table, x, y);

    // Type label below the icon, small print (+ load mode badge for reference tables)
    Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, getTypeLabel(table), x, y);
    if (table instanceof org.apache.hop.datavault.metadata.DvReferenceTable referenceTable) {
      String loadBadge = DvTableDisplaySupport.getReferenceLoadModeBadge(referenceTable);
      if (!Utils.isEmpty(loadBadge)) {
        ModelGraphTableCardLayout.drawExtraLineBelowType(gc, loadBadge, x, y, typeExtent, null);
      }
    } else if (table instanceof DvLinkedTable reference) {
      String sourceModel =
          DvTableResolutionSupport.resolveReferenceSourceModelDisplayName(
              model, reference, variables);
      if (!Utils.isEmpty(sourceModel)) {
        ModelGraphTableCardLayout.drawExtraLineBelowType(gc, sourceModel, x, y, typeExtent, null);
      }
    }

    // Name of the table to the right of the icon (and optional hash key / natural keys below it)
    Point nameExtent = drawTableName(table, x, y);
    if (showHashKeyFieldNames || table.getTableType() == DvTableType.REFERENCE) {
      drawTableHashKeyFieldName(table, x, y, nameExtent);
    }

    // If the table has a description, draw a small info icon in the top-left corner.
    //
    drawTableDescriptionInfoIcon(table, x, y);
  }

  private Point drawTableName(IDvTable table, int x, int y) {
    String name = table.getName() != null ? table.getName() : "?";
    Point nameExtent =
        ModelGraphTableCardLayout.drawName(gc, name, x, y, name.equals(mouseOverTableName));

    if (areaOwners != null) {
      int nameLogX = ModelGraphTableCardLayout.nameX(x);
      int nameLogY = ModelGraphTableCardLayout.nameY(y);
      ModelGraphTableNameHitArea.Bounds nameHit =
          ModelGraphTableNameHitArea.bounds(nameLogX, nameLogY, nameExtent);
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_NAME,
              nameHit.x(),
              nameHit.y(),
              nameHit.width(),
              nameHit.height(),
              offset,
              table,
              name));
    }
    return nameExtent;
  }

  private void drawTableHashKeyFieldName(IDvTable table, int x, int y, Point nameExtent) {
    String secondaryLine = getSecondaryFieldLineForDisplay(table);
    ModelGraphTableCardLayout.drawSecondaryLine(gc, secondaryLine, x, y, nameExtent);
  }

  private String getHashKeyFieldNameForDisplay(IDvTable table) {
    return DvTableDisplaySupport.getHashKeyFieldNameForDisplay(
        table, model, tableByName, variables);
  }

  /** Hash key for hubs/links/sats; natural-key summary for reference tables. */
  private String getSecondaryFieldLineForDisplay(IDvTable table) {
    return DvTableDisplaySupport.getSecondaryFieldLineForDisplay(
        table, model, tableByName, variables, showHashKeyFieldNames);
  }

  private void drawTableBox(IDvTable table, int x, int y, Point box) {
    gc.setBackground(IGc.EColor.WHITE);
    boolean nonManaged = !DvIntegrationSupport.isHopManaged(table);
    gc.setForeground(nonManaged ? IGc.EColor.DARKGRAY : IGc.EColor.BLACK);
    gc.fillRoundRectangle(x, y, box.x, box.y, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.setLineWidth(table.isSelected() ? 2 : 1);
    gc.drawRoundRectangle(x, y, box.x, box.y, CORNER_RADIUS_5, CORNER_RADIUS_5);
    gc.setLineWidth(1);

    // The TRANSFORM_ICON area type is used to mean the whole box including the icon and 2 texts.
    //
    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_ICON, x, y, box.x, box.y, offset, table, table.getName()));
    }
  }

  private void drawTableIcon(IDvTable table, int x, int y) {
    ModelGraphTableCardLayout.drawSvgIcon(
        gc,
        getClass().getClassLoader(),
        DvTableDisplaySupport.getImagePathForTable(table),
        x,
        y,
        magnification);
  }

  private void drawTableDescriptionInfoIcon(IDvTable table, int x, int y) {
    if (table == null || StringUtils.isEmpty(table.getDescription())) {
      return;
    }
    int xInfo = x - (MINI_ICON_SIZE / 2) - 1;
    int yInfo = y - (MINI_ICON_SIZE / 2) - 1;
    try {
      gc.drawImage(IGc.EImage.INFO_DISABLED, xInfo, yInfo, magnification);
    } catch (Exception e) {
      // optional decoration; ignore draw failure
    }
    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_INFO_ICON,
              xInfo,
              yInfo,
              MINI_ICON_SIZE,
              MINI_ICON_SIZE,
              offset,
              table,
              table.getDescription()));
    }
  }

  private String getTypeLabel(IDvTable table) {
    if (table == null || table.getTableType() == null) {
      return "";
    }
    DvTableType displayType =
        table instanceof DvLinkedTable reference
            ? reference.getReferencedTableType()
            : table.getTableType();
    String base =
        switch (displayType) {
          case HUB -> "Hub";
          case SATELLITE -> "Satellite";
          case LINK -> "Link";
          case REFERENCE -> "Reference";
          default -> table.getTableType() != null ? table.getTableType().name() : "";
        };
    if (table instanceof DvLinkedTable reference) {
      boolean sameModelAlias =
          Utils.isEmpty(reference.getReferencedModelFilename())
              && !Utils.isEmpty(reference.getName())
              && !reference
                  .getName()
                  .equalsIgnoreCase(Const.NVL(reference.getReferencedTableName(), ""));
      base = base + (sameModelAlias ? " (alias)" : " (linked)");
    }
    String suffix = DvIntegrationSupport.integrationCanvasSuffix(table);
    if (Utils.isEmpty(suffix)) {
      return base;
    }
    return base + " (" + suffix + ")";
  }

  /**
   * Compute the box size (in logical/model units) for the table card. Uses textExtent() for the
   * name width. Height based on icon + type label below. Also caches the size on the table for use
   * in hit tests etc. The transform will apply magnification and panning to these logical
   * sizes/positions.
   */
  private Point calculateTableBoxSize(IDvTable table) {
    String name = table.getName() != null ? table.getName() : "?";
    String typeLabel = getTypeLabel(table);
    String fieldLine = getSecondaryFieldLineForDisplay(table);
    String extraLine = null;
    if (table instanceof DvLinkedTable reference) {
      extraLine =
          DvTableResolutionSupport.resolveReferenceSourceModelDisplayName(
              model, reference, variables);
    } else if (table instanceof org.apache.hop.datavault.metadata.DvReferenceTable referenceTable) {
      extraLine = DvTableDisplaySupport.getReferenceLoadModeBadge(referenceTable);
    }
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(gc, name, fieldLine, typeLabel, extraLine);
    if (table instanceof DvTableBase base) {
      base.setDrawnBoxWidth(boxSize.width());
      base.setDrawnBoxHeight(boxSize.height());
    }
    return new Point(boxSize.width(), boxSize.height());
  }

  private boolean isValidRelationshipPair(IDvTable a, IDvTable b) {
    if (a == null || b == null || a == b) {
      return false;
    }
    DvTableType ta = DvLinkedTableSupport.effectiveTableType(a);
    DvTableType tb = DvLinkedTableSupport.effectiveTableType(b);
    boolean hubSat =
        (ta == DvTableType.HUB && tb == DvTableType.SATELLITE)
            || (ta == DvTableType.SATELLITE && tb == DvTableType.HUB);
    boolean hubLink =
        (ta == DvTableType.HUB && tb == DvTableType.LINK)
            || (ta == DvTableType.LINK && tb == DvTableType.HUB);
    boolean linkSat =
        (ta == DvTableType.LINK && tb == DvTableType.SATELLITE)
            || (ta == DvTableType.SATELLITE && tb == DvTableType.LINK);
    return hubSat || hubLink || linkSat;
  }

  /**
   * Draw the temporary candidate relationship line (dashed) from the center of the source table to
   * the current drag end location (mouse). The end is provided in screen coords and converted to
   * logical here so the line draws correctly under the active gc transform (offset + mag). This is
   * invoked before drawing tables (after connections) so the line appears behind/under the table
   * visuals.
   */
  private void drawRelationshipCandidateLine() {
    if (startRelationshipTable == null || relationshipDragEndLocation == null) {
      return;
    }
    Point startLocation = startRelationshipTable.getLocation();
    Point loc = real2screen(startLocation.x, startLocation.y);
    if (loc == null) {
      return;
    }

    Point box = calculateTableBoxSize(startRelationshipTable);
    Bounds sourceBounds = new Bounds(loc.x, loc.y, box.x, box.y);
    Point logEnd = screenDragEndToLogical(relationshipDragEndLocation);
    if (logEnd == null) {
      return;
    }

    try {
      boolean validTarget =
          isValidRelationshipPair(startRelationshipTable, candidateRelationshipTarget);
      gc.setForeground(validTarget ? IGc.EColor.BLUE : IGc.EColor.DARKGRAY);
      gc.setLineWidth(2);
      gc.setLineStyle(IGc.ELineStyle.DASH);
      if (candidateRelationshipTarget != null
          && candidateRelationshipTarget.getLocation() != null
          && validTarget) {
        Point targetLoc =
            real2screen(
                candidateRelationshipTarget.getLocation().x,
                candidateRelationshipTarget.getLocation().y);
        Point targetBox = calculateTableBoxSize(candidateRelationshipTarget);
        Bounds targetBounds = new Bounds(targetLoc.x, targetLoc.y, targetBox.x, targetBox.y);
        ModelGraphConnectionGeometry.drawStraightConnection(gc, sourceBounds, targetBounds);
      } else {
        Bounds cursorBounds = ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y);
        Point lineStart = ModelGraphConnectionGeometry.anchorToward(sourceBounds, cursorBounds);
        ModelGraphConnectionGeometry.drawStraightConnection(gc, lineStart, logEnd);
      }

      // Small endpoint marker (approx circle using round rect)
      gc.setLineStyle(IGc.ELineStyle.SOLID);
      int m = 4;
      gc.fillRoundRectangle(logEnd.x - m, logEnd.y - m, m * 2, m * 2, m * 2, m * 2);
    } finally {
      gc.setLineWidth(1);
      gc.setLineStyle(IGc.ELineStyle.SOLID);
      gc.setForeground(IGc.EColor.BLACK);
    }
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null || maximum == null) {
      return;
    }
    // Minimum size in viewport pixels so transforms remain visible
    int minSize = 4;
    // Draw hops as lines first (behind transforms)
    gc.setForeground(IGc.EColor.DARKGRAY);
    gc.setLineWidth(1);

    // Draw tables as small rectangles
    gc.setForeground(IGc.EColor.BLACK);
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      int w = Math.max(minSize, (int) Math.ceil(iconSize * scaleX * 2));
      int h = Math.max(minSize, (int) Math.ceil(iconSize * scaleY));
      int x = (int) (graphX + loc.x * scaleX);
      int y = (int) (graphY + loc.y * scaleY);
      switch (table.getTableType()) {
        case HUB:
          gc.setBackground(IGc.EColor.GREEN);
          break;
        case SATELLITE:
          gc.setBackground(IGc.EColor.RED);
          break;
        case LINK:
          gc.setBackground(IGc.EColor.YELLOW);
          break;
        case REFERENCE:
          gc.setBackground(IGc.EColor.BLUE);
          break;
        case LINKED_TABLE, TABLE_REFERENCE:
          gc.setBackground(IGc.EColor.LIGHTGRAY);
          break;
      }
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
    gc.setBackground(IGc.EColor.WHITE);
  }
}
