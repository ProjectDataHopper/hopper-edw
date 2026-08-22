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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DmTableDisplaySupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableCardLayout;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.hopper.edw.datavault.hopgui.file.vault.BasePainter;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Paints a dimensional model canvas with dimension/fact tables, relationships, and notes. */
@Getter
@Setter
public class DimensionalModelPainter extends BasePainter {

  private static final Class<?> PKG = DimensionalModelPainter.class;
  private static final int EMPTY_MODEL_HINT_PADDING = 16;
  private static final int EMPTY_MODEL_HINT_LINE_GAP = 6;

  private final DimensionalModel model;
  private String mouseOverTableName;
  private Map<String, IDmTable> tableByName = new HashMap<>();
  private IDmTable startRelationshipTable;
  private Point relationshipDragEndLocation;
  private IDmTable candidateRelationshipTarget;

  /** When false, suppress the first-run empty canvas hint (e.g. SVG export). */
  private boolean showEmptyModelHint = true;

  public DimensionalModelPainter(
      DimensionalModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void setRelationshipDragInfo(
      IDmTable startRelationshipTable,
      Point relationshipDragEndLocation,
      IDmTable candidateRelationshipTarget) {
    this.startRelationshipTable = startRelationshipTable;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void drawDimensionalModel(IHopMetadataProvider metadataProvider) {
    if (model == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }
    buildTableIndex();

    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform((float) offset.x, (float) offset.y, magnification);
    gc.setAntialias(true);
    if (gridSize > 1) {
      drawGrid();
    }

    Map<String, ModelGraphEdgeLayout.EdgeGeometry> connections = drawRelationshipLines();
    drawRelationshipCandidateLine();
    drawNotes(model.getNotes());
    drawTables(metadataProvider);
    gc.setForeground(EColor.BLACK);
    gc.setLineWidth(1);
    ModelGraphConnectionGeometry.drawAnchorSquares(gc, connections.values());
    drawRect(selectionRegion);

    gc.setTransform(0.0f, 0.0f, 1.0f);

    if (showEmptyModelHint && isEmptyModel()) {
      drawEmptyModelHint();
    }

    drawNavigationView();
  }

  private void buildTableIndex() {
    tableByName = new HashMap<>();
    if (model == null) {
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        tableByName.put(table.getName(), table);
      }
    }
  }

  private Map<String, ModelGraphEdgeLayout.EdgeGeometry> drawRelationshipLines() {
    List<ModelGraphEdgeLayout.Edge> edges = collectRelationshipEdges();
    Map<String, ModelGraphEdgeLayout.EdgeGeometry> layout = ModelGraphEdgeLayout.layout(edges);
    gc.setForeground(EColor.BLACK);
    gc.setLineWidth(1);
    for (ModelGraphEdgeLayout.Edge edge : edges) {
      ModelGraphEdgeLayout.EdgeGeometry geometry = layout.get(edge.id());
      if (geometry == null) {
        continue;
      }
      boolean dotted = edge.id().startsWith("alias|");
      if (dotted) {
        gc.setLineStyle(ELineStyle.DOT);
        gc.setForeground(EColor.DARKGRAY);
      } else {
        gc.setLineStyle(ELineStyle.SOLID);
        gc.setForeground(EColor.BLACK);
      }
      ModelGraphConnectionGeometry.drawStraightConnection(
          gc, geometry.fromAnchor(), geometry.toAnchor());
    }
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.BLACK);
    return layout;
  }

  private List<ModelGraphEdgeLayout.Edge> collectRelationshipEdges() {
    List<ModelGraphEdgeLayout.Edge> edges = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (table instanceof IDmFactLikeTable factLike) {
        collectFactDimensionEdges(factLike, edges);
        collectFactJunkDimensionEdges(factLike, edges);
        collectFactRangeDimensionEdges(factLike, edges);
      }
      if (table instanceof DmBridge bridge) {
        collectBridgeDimensionEdges(bridge, edges);
      }
      if (table instanceof DmDimension dimension) {
        collectOutriggerEdges(dimension, edges);
      }
      if (table instanceof DmDimensionAlias alias) {
        collectAliasInheritanceEdge(alias, edges);
      }
    }
    return edges;
  }

  private void collectAliasInheritanceEdge(
      DmDimensionAlias alias, List<ModelGraphEdgeLayout.Edge> edges) {
    IDmTable referenced = resolveLocalInheritanceTarget(alias, tableByName);
    if (referenced == null || referenced.getLocation() == null || alias.getLocation() == null) {
      return;
    }
    Bounds aliasBounds = tableBounds(alias, alias.getLocation());
    Bounds referencedBounds = tableBounds(referenced, referenced.getLocation());
    if (aliasBounds == null || referencedBounds == null) {
      return;
    }
    String aliasKey = tableKey(alias);
    String referencedKey = tableKey(referenced);
    edges.add(
        new ModelGraphEdgeLayout.Edge(
            "alias|" + aliasKey + "|" + referencedKey,
            aliasKey,
            aliasBounds,
            referencedKey,
            referencedBounds));
  }

  private void collectFactDimensionEdges(
      IDmFactLikeTable fact, List<ModelGraphEdgeLayout.Edge> edges) {
    Point factLocation = fact.getLocation();
    if (factLocation == null) {
      return;
    }
    Bounds factBounds = tableBounds(fact, factLocation);
    if (factBounds == null) {
      return;
    }
    String factKey = tableKey(fact);
    int roleIndex = 0;
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      int index = roleIndex++;
      if (role == null || Utils.isEmpty(role.getDimensionTableName())) {
        continue;
      }
      IDmTable dimension = tableByName.get(role.getDimensionTableName());
      if (dimension == null || dimension.getLocation() == null) {
        continue;
      }
      Bounds dimensionBounds = tableBounds(dimension, dimension.getLocation());
      if (dimensionBounds == null) {
        continue;
      }
      String dimKey = tableKey(dimension);
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "fact-dim|" + factKey + "|" + index + "|" + dimKey,
              dimKey,
              dimensionBounds,
              factKey,
              factBounds));
    }
  }

  private void collectFactRangeDimensionEdges(
      IDmFactLikeTable fact, List<ModelGraphEdgeLayout.Edge> edges) {
    Point factLocation = fact.getLocation();
    if (factLocation == null) {
      return;
    }
    Bounds factBounds = tableBounds(fact, factLocation);
    if (factBounds == null) {
      return;
    }
    String factKey = tableKey(fact);
    int roleIndex = 0;
    for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
      int index = roleIndex++;
      if (role == null || Utils.isEmpty(role.getRangeDimensionTableName())) {
        continue;
      }
      IDmTable range = tableByName.get(role.getRangeDimensionTableName());
      if (range == null || range.getLocation() == null) {
        continue;
      }
      Bounds rangeBounds = tableBounds(range, range.getLocation());
      if (rangeBounds == null) {
        continue;
      }
      String rangeKey = tableKey(range);
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "fact-range|" + factKey + "|" + index + "|" + rangeKey,
              rangeKey,
              rangeBounds,
              factKey,
              factBounds));
    }
  }

  private void collectFactJunkDimensionEdges(
      IDmFactLikeTable fact, List<ModelGraphEdgeLayout.Edge> edges) {
    Point factLocation = fact.getLocation();
    if (factLocation == null) {
      return;
    }
    Bounds factBounds = tableBounds(fact, factLocation);
    if (factBounds == null) {
      return;
    }
    String factKey = tableKey(fact);
    int roleIndex = 0;
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      int index = roleIndex++;
      if (role == null || Utils.isEmpty(role.getJunkDimensionTableName())) {
        continue;
      }
      IDmTable junk = tableByName.get(role.getJunkDimensionTableName());
      if (junk == null || junk.getLocation() == null) {
        continue;
      }
      Bounds junkBounds = tableBounds(junk, junk.getLocation());
      if (junkBounds == null) {
        continue;
      }
      String junkKey = tableKey(junk);
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "fact-junk|" + factKey + "|" + index + "|" + junkKey,
              junkKey,
              junkBounds,
              factKey,
              factBounds));
    }
  }

  private void collectBridgeDimensionEdges(DmBridge bridge, List<ModelGraphEdgeLayout.Edge> edges) {
    Point bridgeLocation = bridge.getLocation();
    if (bridgeLocation == null) {
      return;
    }
    Bounds bridgeBounds = tableBounds(bridge, bridgeLocation);
    if (bridgeBounds == null) {
      return;
    }
    String bridgeKey = tableKey(bridge);
    int refIndex = 0;
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      int index = refIndex++;
      if (ref == null || Utils.isEmpty(ref.getDimensionTableName())) {
        continue;
      }
      IDmTable dimension = tableByName.get(ref.getDimensionTableName());
      if (dimension == null || dimension.getLocation() == null) {
        continue;
      }
      Bounds dimensionBounds = tableBounds(dimension, dimension.getLocation());
      if (dimensionBounds == null) {
        continue;
      }
      String dimKey = tableKey(dimension);
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "bridge-dim|" + bridgeKey + "|" + index + "|" + dimKey,
              dimKey,
              dimensionBounds,
              bridgeKey,
              bridgeBounds));
    }
  }

  private void collectOutriggerEdges(DmDimension dimension, List<ModelGraphEdgeLayout.Edge> edges) {
    Point dimensionLocation = dimension.getLocation();
    if (dimensionLocation == null) {
      return;
    }
    Bounds dimensionBounds = tableBounds(dimension, dimensionLocation);
    if (dimensionBounds == null) {
      return;
    }
    String dimensionKey = tableKey(dimension);
    int outriggerIndex = 0;
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      int index = outriggerIndex++;
      if (outrigger == null || Utils.isEmpty(outrigger.getDimensionTableName())) {
        continue;
      }
      IDmTable outriggerTable = tableByName.get(outrigger.getDimensionTableName());
      if (outriggerTable == null || outriggerTable.getLocation() == null) {
        continue;
      }
      Bounds outriggerBounds = tableBounds(outriggerTable, outriggerTable.getLocation());
      if (outriggerBounds == null) {
        continue;
      }
      String outriggerKey = tableKey(outriggerTable);
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "outrigger|" + dimensionKey + "|" + index + "|" + outriggerKey,
              dimensionKey,
              dimensionBounds,
              outriggerKey,
              outriggerBounds));
    }
  }

  private static String tableKey(IDmTable table) {
    return "table|" + (table != null && table.getName() != null ? table.getName() : "?");
  }

  /**
   * Local canvas target for a dotted alias-inheritance line. External conformed aliases often share
   * the referenced dimension's name ({@code d_product} → {@code d_product} in another .hdm); that
   * lookup hits the alias itself and must not become a self-loop.
   */
  static IDmTable resolveLocalInheritanceTarget(
      DmDimensionAlias alias, Map<String, IDmTable> tableByName) {
    if (alias == null || tableByName == null || Utils.isEmpty(alias.getReferencedDimensionName())) {
      return null;
    }
    IDmTable referenced = tableByName.get(alias.getReferencedDimensionName());
    if (referenced == null || referenced == alias) {
      return null;
    }
    return referenced;
  }

  private void drawRelationshipCandidateLine() {
    if (startRelationshipTable == null || relationshipDragEndLocation == null) {
      return;
    }
    Bounds sourceBounds = tableBounds(startRelationshipTable, startRelationshipTable.getLocation());
    if (sourceBounds == null) {
      return;
    }
    Point logEnd = screenDragEndToLogical(relationshipDragEndLocation);
    if (logEnd == null) {
      return;
    }

    boolean validTarget =
        isValidRelationshipPair(startRelationshipTable, candidateRelationshipTarget);
    try {
      gc.setForeground(validTarget ? EColor.BLUE : EColor.DARKGRAY);
      gc.setLineWidth(2);
      gc.setLineStyle(ELineStyle.DASH);
      if (candidateRelationshipTarget != null
          && candidateRelationshipTarget.getLocation() != null
          && validTarget) {
        Bounds targetBounds =
            tableBounds(candidateRelationshipTarget, candidateRelationshipTarget.getLocation());
        ModelGraphConnectionGeometry.drawStraightConnection(gc, sourceBounds, targetBounds);
      } else {
        Bounds cursorBounds = ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y);
        Point lineStart = ModelGraphConnectionGeometry.anchorToward(sourceBounds, cursorBounds);
        ModelGraphConnectionGeometry.drawStraightConnection(gc, lineStart, logEnd);
      }
      gc.setLineStyle(ELineStyle.SOLID);
      int marker = 4;
      gc.fillRoundRectangle(
          logEnd.x - marker, logEnd.y - marker, marker * 2, marker * 2, marker * 2, marker * 2);
    } finally {
      gc.setLineWidth(1);
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setForeground(EColor.BLACK);
    }
  }

  private static boolean isRegularDimensionLike(IDmTable table) {
    return table instanceof DmDimension;
  }

  private static boolean isJunkDimension(IDmTable table) {
    return table instanceof DmJunkDimension;
  }

  private static boolean isRangeDimension(IDmTable table) {
    return table instanceof DmRangeDimension;
  }

  private static boolean isFactLikeTable(IDmTable table) {
    return table instanceof IDmFactLikeTable;
  }

  private static boolean isBridgeTable(IDmTable table) {
    return table instanceof DmBridge;
  }

  private static boolean isValidRelationshipPair(IDmTable a, IDmTable b) {
    if (a == null || b == null || a == b) {
      return false;
    }
    if (isFactLikeTable(a) && isJunkDimension(b)) {
      return true;
    }
    if (isFactLikeTable(b) && isJunkDimension(a)) {
      return true;
    }
    if (isFactLikeTable(a) && isRangeDimension(b)) {
      return true;
    }
    if (isFactLikeTable(b) && isRangeDimension(a)) {
      return true;
    }
    if (isFactLikeTable(a) && isRegularDimensionLike(b)) {
      return true;
    }
    if (isFactLikeTable(b) && isRegularDimensionLike(a)) {
      return true;
    }
    if (isBridgeTable(a) && isRegularDimensionLike(b)) {
      return true;
    }
    if (isBridgeTable(b) && isRegularDimensionLike(a)) {
      return true;
    }
    return a instanceof DmDimension && b instanceof DmDimension;
  }

  private Bounds tableBounds(IDmTable table, Point location) {
    if (location == null) {
      return null;
    }
    int boxWidth = 140;
    int boxHeight = 70;
    if (table instanceof DmTableBase base) {
      boxWidth = Math.max(140, base.getDrawnBoxWidth());
      boxHeight = Math.max(70, base.getDrawnBoxHeight());
    }
    Point screenLoc = real2screen(location.x, location.y);
    return new Bounds(screenLoc.x, screenLoc.y, boxWidth, boxHeight);
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null) {
      return;
    }
    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmTableBase base) || base.getLocation() == null) {
        continue;
      }
      int w = Math.max(4, (int) Math.round(Math.max(140, base.getDrawnBoxWidth()) * scaleX));
      int h = Math.max(4, (int) Math.round(Math.max(70, base.getDrawnBoxHeight()) * scaleY));
      int x = (int) Math.round(graphX + base.getLocation().x * scaleX);
      int y = (int) Math.round(graphY + base.getLocation().y * scaleY);
      gc.fillRectangle(x, y, w, h);
    }
  }

  private void drawTables(IHopMetadataProvider metadataProvider) {
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmTableBase base)) {
        continue;
      }
      Point loc = base.getLocation();
      if (loc == null) {
        continue;
      }

      String label = DmTableDisplaySupport.resolveDisplayName(base);
      String typeLabel = DmTableDisplaySupport.resolveTypeLabel(base);
      String secondaryField =
          DmTableDisplaySupport.resolveSecondaryFieldName(base, model, variables, metadataProvider);
      String aliasSourceModel = null;
      if (base instanceof DmDimensionAlias alias) {
        aliasSourceModel =
            DmTableDisplaySupport.resolveAliasSourceModelDisplayName(alias, model, variables);
      }

      ModelGraphTableCardLayout.BoxSize boxSize =
          ModelGraphTableCardLayout.computeBoxSize(
              gc, label, secondaryField, typeLabel, aliasSourceModel);
      int boxWidth = Math.max(140, boxSize.width());
      int boxHeight = Math.max(70, boxSize.height());
      base.setDrawnBoxWidth(boxWidth);
      base.setDrawnBoxHeight(boxHeight);

      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;
      int[] color = tableTypeColor(base.getTableType());

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(base.isSelected() ? 2 : 1);
      gc.setForeground(color[0], color[1], color[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc,
          getClass().getClassLoader(),
          DmTableDisplaySupport.resolveTableIconPath(base.getTableType()),
          x,
          y,
          magnification);

      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, label, x, y, label.equals(mouseOverTableName));
      ModelGraphTableCardLayout.drawSecondaryLine(gc, secondaryField, x, y, nameExtent);
      Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);
      ModelGraphTableCardLayout.drawExtraLineBelowType(
          gc, aliasSourceModel, x, y, typeExtent, color);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, table, label));
        int nameX = ModelGraphTableCardLayout.nameX(x);
        int nameY = ModelGraphTableCardLayout.nameY(y);
        ModelGraphTableNameHitArea.Bounds nameHit =
            ModelGraphTableNameHitArea.bounds(nameX, nameY, nameExtent);
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_NAME,
                nameHit.x(),
                nameHit.y(),
                nameHit.width(),
                nameHit.height(),
                offset,
                table,
                label));
      }
    }
  }

  private static int[] tableTypeColor(DmTableType tableType) {
    return switch (tableType) {
      case FACT, PERIODIC_SNAPSHOT_FACT, ACCUMULATING_SNAPSHOT_FACT, AGGREGATE_FACT ->
          new int[] {180, 100, 40};
      case FACTLESS_FACT -> new int[] {200, 130, 60};
      case JUNK_DIMENSION -> new int[] {120, 90, 160};
      case RANGE_DIMENSION -> new int[] {140, 110, 170};
      case BRIDGE -> new int[] {90, 150, 90};
      case DIMENSION -> new int[] {60, 120, 180};
      case DIMENSION_ALIAS -> new int[] {100, 140, 180};
    };
  }

  private boolean isEmptyModel() {
    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    return model.getTables().isEmpty() && notesEmpty;
  }

  private List<String> getEmptyModelHintLines() {
    List<String> lines = new ArrayList<>();
    lines.add(BaseMessages.getString(PKG, "DimensionalModelPainter.EmptyModel.Intro"));
    lines.add(BaseMessages.getString(PKG, "DimensionalModelPainter.EmptyModel.AddTables"));
    return lines;
  }

  /** Onboarding hint in the top-left when the model has no tables or notes. */
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
}
