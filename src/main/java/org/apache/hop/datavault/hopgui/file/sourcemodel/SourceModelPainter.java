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
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableCardLayout;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;

/**
 * Paints a source model canvas: tables, PK/FK relationship edges, composition dependency lines
 * (query/JSON parents), and notes.
 */
@Getter
@Setter
public class SourceModelPainter extends BasePainter {

  private static final Class<?> PKG = SourceModelPainter.class;
  private static final int[] TABLE_COLOR = new int[] {40, 110, 160};
  private static final int EMPTY_MODEL_HINT_PADDING = 16;
  private static final int EMPTY_MODEL_HINT_LINE_GAP = 6;

  /** Default card size used for query/JSON nodes when measuring dependency anchors. */
  private static final int COMPOSITION_CARD_WIDTH = 160;

  private static final int COMPOSITION_CARD_HEIGHT = 80;

  private final SourceModel model;
  private String mouseOverTableName;
  private Map<String, SourceTable> tableByName = new HashMap<>();
  private boolean showEmptyModelHint = true;

  /** Table, query, JSON, or pipeline node where relationship drag started. */
  private Object startRelationshipNode;

  private Point relationshipDragEndLocation;

  /** Table, query, JSON, or pipeline node under the cursor during relationship drag. */
  private Object candidateRelationshipTarget;

  public SourceModelPainter(
      SourceModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void setRelationshipDragInfo(
      Object startRelationshipNode,
      Point relationshipDragEndLocation,
      Object candidateRelationshipTarget) {
    this.startRelationshipNode = startRelationshipNode;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipTarget = candidateRelationshipTarget;
  }

  public void drawSourceModel(IHopMetadataProvider metadataProvider) {
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

    // Composition deps under solid FK edges so relationships stay visually primary.
    drawCompositionDependencyLines();
    drawRelationshipLines();
    drawRelationshipCandidateLine();
    drawNotes(model.getNotes());
    drawTables();
    drawQueries();
    drawJsonSources();
    drawPipelineSources();
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
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        tableByName.put(table.getName(), table);
      }
    }
  }

  /**
   * Dashed, subdued lines from source tables (and other composition parents) into query and JSON
   * cards so multi-table queries and JSON extractions show their feed dependencies without looking
   * like FK relationships.
   */
  private void drawCompositionDependencyLines() {
    if (model == null || gc == null) {
      return;
    }
    try {
      gc.setLineWidth(1);
      gc.setLineStyle(ELineStyle.DASH);
      gc.setForeground(EColor.GRAY);

      if (model.getQueries() != null) {
        for (SourceQuery query : model.getQueries()) {
          if (query == null || query.getLocation() == null) {
            continue;
          }
          Bounds queryBounds = queryBounds(query);
          for (String feedName : SourceQueryGenerationSupport.participantTableNames(model, query)) {
            // Free SQL may reference tables, Source JSON, or Source Pipeline cards.
            Bounds parentBounds = resolveCompositionFeedBounds(feedName);
            if (parentBounds == null) {
              continue;
            }
            drawCompositionEdge(parentBounds, queryBounds);
          }
        }
      }

      if (model.getJsonSources() != null) {
        for (SourceJson jsonSource : model.getJsonSources()) {
          if (jsonSource == null || jsonSource.getLocation() == null) {
            continue;
          }
          Bounds jsonBounds = jsonBounds(jsonSource);
          Bounds parentBounds =
              resolveCompositionParentBounds(
                  jsonSource.resolveParentSourceKind(), jsonSource.getParentSourceName());
          if (parentBounds == null) {
            continue;
          }
          drawCompositionEdge(parentBounds, jsonBounds);
        }
      }
    } finally {
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setLineWidth(1);
      gc.setForeground(EColor.BLACK);
    }
  }

  private void drawCompositionEdge(Bounds from, Bounds to) {
    Point anchorFrom = ModelGraphConnectionGeometry.anchorToward(from, to);
    Point anchorTo = ModelGraphConnectionGeometry.anchorToward(to, from);
    Point screenFrom = real2screen(anchorFrom.x, anchorFrom.y);
    Point screenTo = real2screen(anchorTo.x, anchorTo.y);
    gc.drawLine(screenFrom.x, screenFrom.y, screenTo.x, screenTo.y);
  }

  private Bounds resolveCompositionParentBounds(SourceJsonParentKind kind, String parentName) {
    if (Utils.isEmpty(parentName)) {
      return null;
    }
    SourceJsonParentKind resolved = kind != null ? kind : SourceJsonParentKind.TABLE;
    return switch (resolved) {
      case TABLE -> resolveTableBounds(parentName);
      case QUERY -> {
        SourceQuery query = model.findQuery(parentName);
        yield query != null ? queryBounds(query) : null;
      }
      case JSON -> {
        SourceJson parentJson = model.findJsonSource(parentName);
        yield parentJson != null ? jsonBounds(parentJson) : null;
      }
    };
  }

  private Bounds resolveTableBounds(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return null;
    }
    SourceTable table = tableByName.get(tableName);
    if (table == null) {
      table = model.findTable(tableName);
    }
    if (table == null || table.getLocation() == null) {
      return null;
    }
    return tableBounds(table);
  }

  /**
   * Bounds for a composition-edge endpoint named in free SQL or a visual query: table, JSON card,
   * or pipeline card (case-insensitive).
   */
  private Bounds resolveCompositionFeedBounds(String feedName) {
    if (Utils.isEmpty(feedName)) {
      return null;
    }
    Bounds tableBounds = resolveTableBounds(feedName);
    if (tableBounds != null) {
      return tableBounds;
    }
    SourceJson json = model.findJsonSource(feedName);
    if (json == null && model.getJsonSources() != null) {
      for (SourceJson candidate : model.getJsonSources()) {
        if (candidate != null
            && !Utils.isEmpty(candidate.getName())
            && candidate.getName().equalsIgnoreCase(feedName.trim())) {
          json = candidate;
          break;
        }
      }
    }
    if (json != null && json.getLocation() != null) {
      return jsonBounds(json);
    }
    SourcePipeline pipeline = model.findPipelineSource(feedName);
    if (pipeline == null && model.getPipelineSources() != null) {
      for (SourcePipeline candidate : model.getPipelineSources()) {
        if (candidate != null
            && !Utils.isEmpty(candidate.getName())
            && candidate.getName().equalsIgnoreCase(feedName.trim())) {
          pipeline = candidate;
          break;
        }
      }
    }
    if (pipeline != null && pipeline.getLocation() != null) {
      return pipelineBounds(pipeline);
    }
    return null;
  }

  private Bounds queryBounds(SourceQuery query) {
    Point loc = query.getLocation();
    int x = loc != null ? loc.x : 0;
    int y = loc != null ? loc.y : 0;
    if (gc == null) {
      return new Bounds(x, y, COMPOSITION_CARD_WIDTH, COMPOSITION_CARD_HEIGHT);
    }
    String label = Utils.isEmpty(query.getName()) ? "?" : query.getName();
    String secondary =
        Utils.isEmpty(query.getDrivingTableName()) ? "" : "from " + query.getDrivingTableName();
    int joins = query.getJoins() != null ? query.getJoins().size() : 0;
    String extra = joins + " join(s), " + query.getColumns().size() + " col(s)";
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, "QUERY", extra);
    return new Bounds(
        x,
        y,
        Math.max(COMPOSITION_CARD_WIDTH, boxSize.width()),
        Math.max(COMPOSITION_CARD_HEIGHT, boxSize.height()));
  }

  private Bounds jsonBounds(SourceJson jsonSource) {
    Point loc = jsonSource.getLocation();
    int x = loc != null ? loc.x : 0;
    int y = loc != null ? loc.y : 0;
    if (gc == null) {
      return new Bounds(x, y, COMPOSITION_CARD_WIDTH, COMPOSITION_CARD_HEIGHT);
    }
    String label = Utils.isEmpty(jsonSource.getName()) ? "?" : jsonSource.getName();
    String secondary =
        Utils.isEmpty(jsonSource.getParentSourceName())
            ? ""
            : "from " + jsonSource.getParentSourceName();
    if (!Utils.isEmpty(jsonSource.getJsonFieldName())) {
      secondary =
          Utils.isEmpty(secondary)
              ? jsonSource.getJsonFieldName()
              : secondary + " · " + jsonSource.getJsonFieldName();
    }
    int fieldCount = jsonSource.getFields() != null ? jsonSource.getFields().size() : 0;
    String extra = fieldCount + " field(s)";
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, "JSON", extra);
    return new Bounds(
        x,
        y,
        Math.max(COMPOSITION_CARD_WIDTH, boxSize.width()),
        Math.max(COMPOSITION_CARD_HEIGHT, boxSize.height()));
  }

  private void drawRelationshipLines() {
    gc.setLineWidth(1);
    gc.setLineStyle(ELineStyle.SOLID);
    Map<SourceRelationship, SourceRelationshipEdgeLayout.EdgeGeometry> edgeLayout =
        SourceRelationshipEdgeLayout.layout(model, tableByName);
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null) {
        continue;
      }
      Bounds childBounds =
          SourceRelationshipEdgeLayout.boundsOfEndpoint(
              model,
              tableByName,
              relationship.resolveChildEndpointKind(),
              relationship.getChildTableName());
      Bounds parentBounds =
          SourceRelationshipEdgeLayout.boundsOfEndpoint(
              model,
              tableByName,
              relationship.resolveParentEndpointKind(),
              relationship.getParentTableName());
      if (childBounds == null || parentBounds == null) {
        continue;
      }
      SourceRelationshipEdgeLayout.EdgeGeometry geometry = edgeLayout.get(relationship);
      Point from;
      Point to;
      if (geometry != null) {
        from = geometry.childAnchor();
        to = geometry.parentAnchor();
      } else {
        from = ModelGraphConnectionGeometry.anchorToward(childBounds, parentBounds);
        to = ModelGraphConnectionGeometry.anchorToward(parentBounds, childBounds);
      }
      Point screenFrom = real2screen(from.x, from.y);
      Point screenTo = real2screen(to.x, to.y);

      gc.setForeground(EColor.DARKGRAY);
      gc.drawLine(screenFrom.x, screenFrom.y, screenTo.x, screenTo.y);

      // Crow's foot symbols at each end (child = FK side, parent = PK side).
      SourceRelationshipCrowFootDrawer.draw(
          gc, screenFrom, screenTo, relationship.resolveChildMultiplicity());
      SourceRelationshipCrowFootDrawer.draw(
          gc, screenTo, screenFrom, relationship.resolveParentMultiplicity());

      // Label + left-click hit target at the midpoint (edit/delete context menu).
      String baseName =
          Utils.isEmpty(relationship.getName())
              ? (SourceEndpointSupport.displayName(
                      relationship.resolveChildEndpointKind(), relationship.getChildTableName())
                  + " \u2192 "
                  + SourceEndpointSupport.displayName(
                      relationship.resolveParentEndpointKind(), relationship.getParentTableName()))
              : relationship.getName();
      String label = baseName + "  " + relationship.compactMultiplicityLabel();
      gc.setFont(EFont.SMALL);
      Point labelExtent = gc.textExtent(label);
      int midX = (screenFrom.x + screenTo.x) / 2;
      int midY = (screenFrom.y + screenTo.y) / 2;
      int labelX = midX - labelExtent.x / 2;
      int labelY = midY - labelExtent.y / 2;
      int pad = 3;
      gc.setBackground(EColor.BACKGROUND);
      gc.fillRectangle(
          labelX - pad, labelY - pad, labelExtent.x + pad * 2, labelExtent.y + pad * 2);
      gc.setForeground(EColor.DARKGRAY);
      gc.drawText(label, labelX, labelY, true);
      gc.setFont(EFont.GRAPH);

      if (areaOwners != null) {
        int hitW = Math.max(labelExtent.x + pad * 2, 28);
        int hitH = Math.max(labelExtent.y + pad * 2, 18);
        areaOwners.add(
            new AreaOwner(
                AreaType.CUSTOM,
                midX - hitW / 2,
                midY - hitH / 2,
                hitW,
                hitH,
                offset,
                relationship,
                label));
      }
    }
    gc.setForeground(EColor.BLACK);
    gc.setFont(EFont.GRAPH);
  }

  /**
   * Temporary relationship drag line.
   *
   * <p>{@code relationshipDragEndLocation} is in raw canvas/mouse coordinates. Drawing happens
   * under a GC scale of {@code magnification} only (SwtGc ignores translation), while table
   * geometry is painted at {@code real2screen(graph) = graph + offset}. The cursor end must
   * therefore be converted to that same draw space: {@code canvas / magnification}. Using raw mouse
   * coords here over-scales the tip by the native zoom factor.
   */
  private void drawRelationshipCandidateLine() {
    if (startRelationshipNode == null || relationshipDragEndLocation == null) {
      return;
    }
    Bounds startBounds = boundsOfCanvasNode(startRelationshipNode);
    if (startBounds == null) {
      return;
    }
    Point drawStart = real2screen(startBounds.centerX(), startBounds.centerY());
    // Convert canvas mouse coords into draw space (pre-GC-scale).
    float mag = magnification > 0f ? magnification : 1f;
    Point drawEnd =
        new Point(
            Math.round(relationshipDragEndLocation.x / mag),
            Math.round(relationshipDragEndLocation.y / mag));

    Bounds targetBounds = boundsOfCanvasNode(candidateRelationshipTarget);
    boolean valid = targetBounds != null;
    try {
      gc.setForeground(valid ? EColor.BLUE : EColor.DARKGRAY);
      gc.setLineWidth(2);
      gc.setLineStyle(ELineStyle.DASH);
      Point tip = drawEnd;
      if (valid) {
        tip = real2screen(targetBounds.centerX(), targetBounds.centerY());
      }
      gc.drawLine(drawStart.x, drawStart.y, tip.x, tip.y);
      int marker = 4;
      gc.fillRoundRectangle(
          tip.x - marker, tip.y - marker, marker * 2, marker * 2, marker * 2, marker * 2);
    } finally {
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setLineWidth(1);
      gc.setForeground(EColor.BLACK);
    }
  }

  private Bounds boundsOfCanvasNode(Object node) {
    if (node == null) {
      return null;
    }
    if (node instanceof SourceTable table) {
      return tableBounds(table);
    }
    if (node instanceof SourceQuery query) {
      return queryBounds(query);
    }
    if (node instanceof SourceJson json) {
      return jsonBounds(json);
    }
    if (node instanceof SourcePipeline pipeline) {
      return pipelineBounds(pipeline);
    }
    return null;
  }

  private Bounds pipelineBounds(SourcePipeline pipelineSource) {
    Point loc = pipelineSource.getLocation();
    int x = loc != null ? loc.x : 0;
    int y = loc != null ? loc.y : 0;
    if (gc == null) {
      return new Bounds(x, y, COMPOSITION_CARD_WIDTH, COMPOSITION_CARD_HEIGHT);
    }
    String label = Utils.isEmpty(pipelineSource.getName()) ? "?" : pipelineSource.getName();
    String secondary =
        Utils.isEmpty(pipelineSource.getOutputTransformName())
            ? Const.NVL(pipelineSource.getPipelineFilename(), "")
            : pipelineSource.getOutputTransformName();
    int fieldCount = pipelineSource.getFields() != null ? pipelineSource.getFields().size() : 0;
    String extra = fieldCount + " field(s)";
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, "PIPELINE", extra);
    return new Bounds(
        x,
        y,
        Math.max(COMPOSITION_CARD_WIDTH, boxSize.width()),
        Math.max(COMPOSITION_CARD_HEIGHT, boxSize.height()));
  }

  private static Bounds tableBounds(SourceTable table) {
    Point loc = table.getLocation();
    return new Bounds(
        loc != null ? loc.x : 0,
        loc != null ? loc.y : 0,
        Math.max(140, table.getDrawnBoxWidth()),
        Math.max(70, table.getDrawnBoxHeight()));
  }

  private void drawTables() {
    for (SourceTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }

      String label = Utils.isEmpty(table.getName()) ? "?" : table.getName();
      String physical =
          Utils.isEmpty(table.getTableName())
              ? ""
              : (Utils.isEmpty(table.getSchemaName())
                  ? table.getTableName()
                  : table.getSchemaName() + "." + table.getTableName());
      String typeLabel = "TABLE";
      String pkSummary = primaryKeySummary(table);
      if (Utils.isEmpty(pkSummary)) {
        pkSummary = columnPreview(table);
      }

      ModelGraphTableCardLayout.BoxSize boxSize =
          ModelGraphTableCardLayout.computeBoxSize(gc, label, physical, typeLabel, pkSummary);
      int boxWidth = Math.max(160, boxSize.width());
      int boxHeight = Math.max(80, boxSize.height());
      table.setDrawnBoxWidth(boxWidth);
      table.setDrawnBoxHeight(boxHeight);

      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(table.isSelected() ? 2 : 1);
      gc.setForeground(TABLE_COLOR[0], TABLE_COLOR[1], TABLE_COLOR[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc, getClass().getClassLoader(), "source-model.svg", x, y, magnification);

      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, label, x, y, label.equals(mouseOverTableName));
      ModelGraphTableCardLayout.drawSecondaryLine(gc, physical, x, y, nameExtent);
      Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);
      ModelGraphTableCardLayout.drawExtraLineBelowType(
          gc, pkSummary, x, y, typeExtent, TABLE_COLOR);

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

  private static String primaryKeySummary(SourceTable table) {
    List<String> keys = new ArrayList<>();
    for (SourceColumn column : table.primaryKeyColumns()) {
      if (column != null && !Utils.isEmpty(column.getName())) {
        keys.add("★ " + column.getName());
      }
    }
    if (keys.isEmpty()) {
      return "";
    }
    return "PK: " + String.join(", ", keys);
  }

  /** Short non-PK column preview when no primary key is defined. */
  private static String columnPreview(SourceTable table) {
    List<String> names = new ArrayList<>();
    for (SourceColumn column : table.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName()) || column.isPrimaryKey()) {
        continue;
      }
      names.add(column.getName());
      if (names.size() >= 3) {
        break;
      }
    }
    if (names.isEmpty()) {
      return "";
    }
    return String.join(", ", names);
  }

  private static final int[] QUERY_COLOR = new int[] {120, 80, 160};

  /** Teal/cyan card for JSON extraction nodes (distinct from purple queries). */
  private static final int[] JSON_COLOR = new int[] {20, 130, 140};

  private static final int[] PIPELINE_COLOR = new int[] {180, 100, 40};

  private void drawQueries() {
    if (model.getQueries() == null) {
      return;
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null) {
        continue;
      }
      Point loc = query.getLocation();
      if (loc == null) {
        continue;
      }
      String label = Utils.isEmpty(query.getName()) ? "?" : query.getName();
      String secondary =
          Utils.isEmpty(query.getDrivingTableName()) ? "" : "from " + query.getDrivingTableName();
      String typeLabel = "QUERY";
      int joins = query.getJoins() != null ? query.getJoins().size() : 0;
      String extra = joins + " join(s), " + query.getColumns().size() + " col(s)";

      ModelGraphTableCardLayout.BoxSize boxSize =
          ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, typeLabel, extra);
      int boxWidth = Math.max(160, boxSize.width());
      int boxHeight = Math.max(80, boxSize.height());

      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(query.isSelected() ? 2 : 1);
      gc.setForeground(QUERY_COLOR[0], QUERY_COLOR[1], QUERY_COLOR[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc, getClass().getClassLoader(), "source-model.svg", x, y, magnification);
      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, label, x, y, label.equals(mouseOverTableName));
      ModelGraphTableCardLayout.drawSecondaryLine(gc, secondary, x, y, nameExtent);
      Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);
      ModelGraphTableCardLayout.drawExtraLineBelowType(gc, extra, x, y, typeExtent, QUERY_COLOR);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, query, label));
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
                query,
                label));
      }
    }
  }

  private void drawJsonSources() {
    if (model.getJsonSources() == null) {
      return;
    }
    for (SourceJson jsonSource : model.getJsonSources()) {
      if (jsonSource == null) {
        continue;
      }
      Point loc = jsonSource.getLocation();
      if (loc == null) {
        continue;
      }
      String label = Utils.isEmpty(jsonSource.getName()) ? "?" : jsonSource.getName();
      String secondary =
          Utils.isEmpty(jsonSource.getParentSourceName())
              ? ""
              : "from " + jsonSource.getParentSourceName();
      if (!Utils.isEmpty(jsonSource.getJsonFieldName())) {
        secondary =
            Utils.isEmpty(secondary)
                ? jsonSource.getJsonFieldName()
                : secondary + " · " + jsonSource.getJsonFieldName();
      }
      String typeLabel = "JSON";
      int fieldCount = jsonSource.getFields() != null ? jsonSource.getFields().size() : 0;
      String extra = fieldCount + " field(s)";

      ModelGraphTableCardLayout.BoxSize boxSize =
          ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, typeLabel, extra);
      int boxWidth = Math.max(160, boxSize.width());
      int boxHeight = Math.max(80, boxSize.height());

      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(jsonSource.isSelected() ? 2 : 1);
      gc.setForeground(JSON_COLOR[0], JSON_COLOR[1], JSON_COLOR[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc, getClass().getClassLoader(), "source-model.svg", x, y, magnification);
      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, label, x, y, label.equals(mouseOverTableName));
      ModelGraphTableCardLayout.drawSecondaryLine(gc, secondary, x, y, nameExtent);
      Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);
      ModelGraphTableCardLayout.drawExtraLineBelowType(gc, extra, x, y, typeExtent, JSON_COLOR);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, jsonSource, label));
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
                jsonSource,
                label));
      }
    }
  }

  private void drawPipelineSources() {
    if (model.getPipelineSources() == null) {
      return;
    }
    for (SourcePipeline pipelineSource : model.getPipelineSources()) {
      if (pipelineSource == null) {
        continue;
      }
      Point loc = pipelineSource.getLocation();
      if (loc == null) {
        continue;
      }
      String label = Utils.isEmpty(pipelineSource.getName()) ? "?" : pipelineSource.getName();
      String secondary =
          Utils.isEmpty(pipelineSource.getOutputTransformName())
              ? Const.NVL(pipelineSource.getPipelineFilename(), "")
              : pipelineSource.getOutputTransformName();
      String typeLabel = "PIPELINE";
      int fieldCount = pipelineSource.getFields() != null ? pipelineSource.getFields().size() : 0;
      String extra = fieldCount + " field(s)";

      ModelGraphTableCardLayout.BoxSize boxSize =
          ModelGraphTableCardLayout.computeBoxSize(gc, label, secondary, typeLabel, extra);
      int boxWidth = Math.max(160, boxSize.width());
      int boxHeight = Math.max(80, boxSize.height());

      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.WHITE);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(pipelineSource.isSelected() ? 2 : 1);
      gc.setForeground(PIPELINE_COLOR[0], PIPELINE_COLOR[1], PIPELINE_COLOR[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc, getClass().getClassLoader(), "source-model.svg", x, y, magnification);
      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, label, x, y, label.equals(mouseOverTableName));
      ModelGraphTableCardLayout.drawSecondaryLine(gc, secondary, x, y, nameExtent);
      Point typeExtent = ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);
      ModelGraphTableCardLayout.drawExtraLineBelowType(gc, extra, x, y, typeExtent, PIPELINE_COLOR);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, pipelineSource, label));
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
                pipelineSource,
                label));
      }
    }
  }

  private boolean isEmptyModel() {
    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    boolean queriesEmpty = model.getQueries() == null || model.getQueries().isEmpty();
    boolean jsonEmpty = model.getJsonSources() == null || model.getJsonSources().isEmpty();
    boolean pipelineEmpty =
        model.getPipelineSources() == null || model.getPipelineSources().isEmpty();
    return model.getTables().isEmpty() && queriesEmpty && jsonEmpty && pipelineEmpty && notesEmpty;
  }

  private List<String> getEmptyModelHintLines() {
    List<String> lines = new ArrayList<>();
    lines.add(BaseMessages.getString(PKG, "SourceModelPainter.EmptyModel.Intro"));
    lines.add(BaseMessages.getString(PKG, "SourceModelPainter.EmptyModel.AddTables"));
    lines.add(BaseMessages.getString(PKG, "SourceModelPainter.EmptyModel.AddQuery"));
    lines.add(BaseMessages.getString(PKG, "SourceModelPainter.EmptyModel.AddJson"));
    return lines;
  }

  /**
   * Onboarding hint in the top-left when the model has no tables or notes.
   *
   * <p>Drawn after the canvas transform is reset to screen pixels. Scaled modestly so it stays
   * readable without dominating the canvas (see {@link #emptyModelHintScale()}).
   */
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

    float emptyHintScale = emptyModelHintScale();
    int alpha = gc.getAlpha();
    gc.setAlpha(210);
    gc.setTransform(0, 0, emptyHintScale);
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

  /**
   * Empty-model onboarding scale: halfway between unscaled (1.0) and the previous oversized 2.5×
   * native-zoom boost → {@code 1.5 × max(1, nativeZoom)}.
   */
  static float emptyModelHintScale() {
    double nativeZoom = PropsUi.getNativeZoomFactor();
    if (nativeZoom < 1.0d) {
      nativeZoom = 1.0d;
    }
    return (float) (1.5d * nativeZoom);
  }
}
