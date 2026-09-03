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
package org.hopper.edw.datavault.hopgui.file.businessvault;

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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DvTableDisplaySupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableCardLayout;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.hopper.edw.datavault.hopgui.file.vault.BasePainter;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvBvTableReference;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvDvTableReference;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQueryRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlResolvedKind;
import org.hopper.edw.datavault.metadata.businessvault.BvTableBase;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Paints a Business Vault model canvas with DV references, BV tables, and derivative links. */
@Getter
@Setter
public class BusinessVaultModelPainter extends BasePainter {

  private static final Class<?> PKG = BusinessVaultModelPainter.class;
  private static final int REF_LINE_HEIGHT = 16;
  private static final int DERIVATIVES_TOP = 50;
  private static final int DERIVATIVES_BOTTOM_PADDING = 12;
  private static final int DV_ICON_SIZE = ModelGraphTableCardLayout.ICON_SIZE;
  private static final int DV_MARGIN = ModelGraphTableCardLayout.MARGIN;
  private static final int TABLE_TEXT_INSET = 8;
  private static final int LOGO_SIZE = 22;
  private static final int LOGO_INSET = 6;
  private static final int LOGO_TEXT_GAP = 6;

  private final BusinessVaultModel model;
  private DataVaultModel dataVaultModel;
  private boolean showHashKeyFieldNames;
  private String mouseOverBvTableName;
  private String mouseOverDvReferenceName;
  private String mouseOverBvReferenceName;
  private IBvTable startRelationshipBvTable;
  private BvDvTableReference startRelationshipDvReference;
  private Point relationshipDragEndLocation;
  private IBvTable candidateRelationshipBvTable;
  private BvDvTableReference candidateRelationshipDvReference;

  public BusinessVaultModelPainter(
      BusinessVaultModel model, IGc gc, IVariables variables, int width, int height) {
    super(gc, variables, model, new Point(width, height));
    this.model = model;
  }

  public void setRelationshipDragInfo(
      IBvTable startBvTable,
      BvDvTableReference startDvReference,
      Point relationshipDragEndLocation,
      IBvTable candidateBvTable,
      BvDvTableReference candidateDvReference) {
    this.startRelationshipBvTable = startBvTable;
    this.startRelationshipDvReference = startDvReference;
    this.relationshipDragEndLocation = relationshipDragEndLocation;
    this.candidateRelationshipBvTable = candidateBvTable;
    this.candidateRelationshipDvReference = candidateDvReference;
  }

  public void drawBusinessVaultModel(IHopMetadataProvider metadataProvider) {
    if (model == null || gc == null) {
      return;
    }
    if (areaOwners != null) {
      areaOwners.clear();
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform((float) offset.x, (float) offset.y, magnification);
    gc.setAntialias(true);
    if (gridSize > 1) {
      drawGrid();
    }

    Map<String, BvDvTableReference> dvRefByName = indexDvReferences();
    Map<String, BvBvTableReference> bvRefByName = indexBvReferences();
    prepareDvReferenceBoxSizes();
    prepareBvReferenceBoxSizes();
    Map<String, ModelGraphEdgeLayout.EdgeGeometry> connections =
        drawDerivativeConnections(dvRefByName, bvRefByName);
    drawRelationshipCandidateLine();
    drawNotes(model.getNotes());
    drawDvTableReferences();
    drawBvTableReferences();
    drawBusinessVaultTables();
    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    ModelGraphConnectionGeometry.drawAnchorSquares(gc, connections.values());
    drawRect(selectionRegion);

    gc.setTransform(0.0f, 0.0f, 1.0f);

    boolean notesEmpty = !drawNotes || model.getNotes().isEmpty();
    if (model.getTables().isEmpty()
        && model.getDvReferences().isEmpty()
        && model.getBvReferences().isEmpty()
        && notesEmpty) {
      drawEmptyHint();
    }

    drawNavigationView();
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (model == null) {
      return;
    }
    int minSize = 4;
    Map<String, BvDvTableReference> dvRefByName = indexDvReferences();
    Map<String, BvBvTableReference> bvRefByName = indexBvReferences();

    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    for (IBvTable bvTable : model.getTables()) {
      if (!(bvTable instanceof BvTableBase base)) {
        continue;
      }
      Point bvLoc = base.getLocation();
      if (bvLoc == null) {
        continue;
      }
      Bounds bvBounds =
          navigationBounds(
              graphX,
              graphY,
              scaleX,
              scaleY,
              bvLoc,
              base.getDrawnBoxWidth(),
              base.getDrawnBoxHeight(),
              minSize);
      for (BvDerivativeRef derivative : base.getDerivatives()) {
        if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
          continue;
        }
        BvDvTableReference target = dvRefByName.get(derivative.getDvTableName().toLowerCase());
        if (target == null || target.getLocation() == null) {
          continue;
        }
        Bounds dvBounds =
            navigationBounds(
                graphX,
                graphY,
                scaleX,
                scaleY,
                target.getLocation(),
                target.getDrawnBoxWidth(),
                target.getDrawnBoxHeight(),
                minSize);
        ModelGraphConnectionGeometry.drawStraightConnection(gc, bvBounds, dvBounds);
      }
      addNavigationParentHubConnection(
          base, bvBounds, dvRefByName, graphX, graphY, scaleX, scaleY, minSize);
      if (bvTable instanceof BvBusinessTable businessTable) {
        for (BvSqlRef ref : businessTable.getSqlRefs()) {
          if (ref == null
              || ref.getResolvedKind() != BvSqlResolvedKind.BV_TABLE
              || Utils.isEmpty(ref.getObjectName())) {
            continue;
          }
          Bounds targetBounds =
              resolveSqlBvRefNavBounds(
                  ref.getObjectName(), graphX, graphY, scaleX, scaleY, minSize, bvRefByName);
          if (targetBounds != null) {
            ModelGraphConnectionGeometry.drawStraightConnection(gc, bvBounds, targetBounds);
          }
        }
      }
    }

    gc.setForeground(EColor.BLACK);
    for (BvDvTableReference reference : model.getDvReferences()) {
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int w =
          Math.max(minSize, (int) Math.ceil(Math.max(1, reference.getDrawnBoxWidth()) * scaleX));
      int h =
          Math.max(minSize, (int) Math.ceil(Math.max(1, reference.getDrawnBoxHeight()) * scaleY));
      int x = (int) (graphX + loc.x * scaleX);
      int y = (int) (graphY + loc.y * scaleY);
      gc.setBackground(EColor.LIGHTGRAY);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }

    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference == null || reference.getLocation() == null) {
        continue;
      }
      Point loc = reference.getLocation();
      int w =
          Math.max(minSize, (int) Math.ceil(Math.max(1, reference.getDrawnBoxWidth()) * scaleX));
      int h =
          Math.max(minSize, (int) Math.ceil(Math.max(1, reference.getDrawnBoxHeight()) * scaleY));
      int x = (int) (graphX + loc.x * scaleX);
      int y = (int) (graphY + loc.y * scaleY);
      gc.setBackground(EColor.BACKGROUND);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }

    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvTableBase base) || base.getLocation() == null) {
        continue;
      }
      int w = Math.max(minSize, (int) Math.ceil(Math.max(1, base.getDrawnBoxWidth()) * scaleX));
      int h = Math.max(minSize, (int) Math.ceil(Math.max(1, base.getDrawnBoxHeight()) * scaleY));
      int x = (int) (graphX + base.getLocation().x * scaleX);
      int y = (int) (graphY + base.getLocation().y * scaleY);
      int[] color = tableTypeColor(base.getTableType());
      gc.setBackground(color[0], color[1], color[2]);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
    gc.setBackground(EColor.WHITE);
    gc.setLineWidth(1);
  }

  private Map<String, BvDvTableReference> indexDvReferences() {
    Map<String, BvDvTableReference> byName = new HashMap<>();
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference != null && !Utils.isEmpty(reference.getDvTableName())) {
        byName.put(reference.getDvTableName().toLowerCase(), reference);
      }
    }
    return byName;
  }

  private Map<String, BvBvTableReference> indexBvReferences() {
    Map<String, BvBvTableReference> byName = new HashMap<>();
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference == null) {
        continue;
      }
      if (!Utils.isEmpty(reference.getBvTableName())) {
        byName.put(reference.getBvTableName().toLowerCase(), reference);
      }
      if (!Utils.isEmpty(reference.getPhysicalTableName())) {
        byName.putIfAbsent(reference.getPhysicalTableName().toLowerCase(), reference);
      }
    }
    return byName;
  }

  private Map<String, ModelGraphEdgeLayout.EdgeGeometry> drawDerivativeConnections(
      Map<String, BvDvTableReference> dvRefByName, Map<String, BvBvTableReference> bvRefByName) {
    List<ModelGraphEdgeLayout.Edge> edges =
        collectDerivativeConnectionEdges(dvRefByName, bvRefByName);
    Map<String, ModelGraphEdgeLayout.EdgeGeometry> layout = ModelGraphEdgeLayout.layout(edges);
    gc.setLineWidth(1);
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.DARKGRAY);
    for (ModelGraphEdgeLayout.Edge edge : edges) {
      ModelGraphEdgeLayout.EdgeGeometry geometry = layout.get(edge.id());
      if (geometry == null) {
        continue;
      }
      ModelGraphConnectionGeometry.drawStraightConnection(
          gc, geometry.fromAnchor(), geometry.toAnchor());
    }
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.BLACK);
    return layout;
  }

  private List<ModelGraphEdgeLayout.Edge> collectDerivativeConnectionEdges(
      Map<String, BvDvTableReference> dvRefByName, Map<String, BvBvTableReference> bvRefByName) {
    List<ModelGraphEdgeLayout.Edge> edges = new ArrayList<>();
    for (IBvTable bvTable : model.getTables()) {
      if (!(bvTable instanceof BvTableBase base) || base.getLocation() == null) {
        continue;
      }
      Bounds bvBounds = getBvTableBounds(base);
      if (bvBounds == null) {
        continue;
      }
      String bvKey = bvTableKey(base);
      int derivativeIndex = 0;
      for (BvDerivativeRef derivative : base.getDerivatives()) {
        int index = derivativeIndex++;
        if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
          continue;
        }
        BvDvTableReference target = dvRefByName.get(derivative.getDvTableName().toLowerCase());
        if (target == null || target.getLocation() == null) {
          continue;
        }
        Bounds dvBounds = getDvReferenceBounds(target);
        if (dvBounds == null) {
          continue;
        }
        String dvKey = dvReferenceKey(target);
        edges.add(
            new ModelGraphEdgeLayout.Edge(
                "der|" + bvKey + "|" + index + "|" + dvKey, bvKey, bvBounds, dvKey, dvBounds));
      }
      addScd2ParentHubConnection(base, bvKey, bvBounds, dvRefByName, edges);
      int sourceQueryIndex = 0;
      for (BvSourceQueryRef sourceQueryRef : base.getSourceQueryRefs()) {
        int index = sourceQueryIndex++;
        if (sourceQueryRef == null || Utils.isEmpty(sourceQueryRef.getSourceQueryName())) {
          continue;
        }
        IBvTable sourceTable = model.findTable(sourceQueryRef.getSourceQueryName());
        if (!(sourceTable instanceof BvTableBase sourceBase) || sourceBase.getLocation() == null) {
          continue;
        }
        Bounds sourceBounds = getBvTableBounds(sourceBase);
        if (sourceBounds == null) {
          continue;
        }
        String sourceKey = bvTableKey(sourceBase);
        edges.add(
            new ModelGraphEdgeLayout.Edge(
                "sq|" + bvKey + "|" + index + "|" + sourceKey,
                bvKey,
                bvBounds,
                sourceKey,
                sourceBounds));
      }
      if (bvTable instanceof BvBusinessTable businessTable) {
        collectSqlRefConnectionEdges(
            businessTable, bvKey, bvBounds, dvRefByName, bvRefByName, edges);
      }
    }
    return edges;
  }

  /**
   * Collects edges from a SQL business table to its {@code ref()} targets: same-model BV tables,
   * canvas BV references (external .hbv), and canvas DV references when SQL resolved a DV table.
   */
  private void collectSqlRefConnectionEdges(
      BvBusinessTable businessTable,
      String sourceKey,
      Bounds sourceBounds,
      Map<String, BvDvTableReference> dvRefByName,
      Map<String, BvBvTableReference> bvRefByName,
      List<ModelGraphEdgeLayout.Edge> edges) {
    if (businessTable == null || sourceBounds == null) {
      return;
    }
    int sqlIndex = 0;
    for (BvSqlRef ref : businessTable.getSqlRefs()) {
      int index = sqlIndex++;
      if (ref == null || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      Bounds targetBounds = null;
      String targetKey = null;
      if (ref.getResolvedKind() == BvSqlResolvedKind.BV_TABLE) {
        targetBounds = resolveSqlBvRefBounds(ref.getObjectName(), bvRefByName);
        targetKey = sqlBvRefKey(ref.getObjectName(), bvRefByName);
      } else if (ref.getResolvedKind() == BvSqlResolvedKind.DV_TABLE) {
        BvDvTableReference dvRef = dvRefByName.get(ref.getObjectName().toLowerCase());
        if (dvRef != null && dvRef.getLocation() != null) {
          targetBounds = getDvReferenceBounds(dvRef);
          targetKey = dvReferenceKey(dvRef);
        }
      }
      if (targetBounds == null || Utils.isEmpty(targetKey)) {
        continue;
      }
      edges.add(
          new ModelGraphEdgeLayout.Edge(
              "sql|" + sourceKey + "|" + index + "|" + targetKey,
              sourceKey,
              sourceBounds,
              targetKey,
              targetBounds));
    }
  }

  private String sqlBvRefKey(String objectName, Map<String, BvBvTableReference> bvRefByName) {
    IBvTable local = findBvTableByName(objectName);
    if (local instanceof BvTableBase) {
      return bvTableKey(local);
    }
    if (bvRefByName != null && !Utils.isEmpty(objectName)) {
      BvBvTableReference alias = bvRefByName.get(objectName.toLowerCase());
      if (alias != null) {
        return bvReferenceKey(alias);
      }
    }
    return "bv|" + objectName;
  }

  private static String bvTableKey(IBvTable table) {
    return "bv|" + (table != null && table.getName() != null ? table.getName() : "?");
  }

  private static String dvReferenceKey(BvDvTableReference reference) {
    return "dvref|"
        + (reference != null && reference.getDvTableName() != null
            ? reference.getDvTableName()
            : "?");
  }

  private static String bvReferenceKey(BvBvTableReference reference) {
    if (reference == null) {
      return "bvref|?";
    }
    if (!Utils.isEmpty(reference.getBvTableName())) {
      return "bvref|" + reference.getBvTableName();
    }
    if (!Utils.isEmpty(reference.getPhysicalTableName())) {
      return "bvref|" + reference.getPhysicalTableName();
    }
    return "bvref|?";
  }

  private Bounds resolveSqlBvRefBounds(
      String objectName, Map<String, BvBvTableReference> bvRefByName) {
    // Same-model BV tables first.
    IBvTable local = findBvTableByName(objectName);
    if (local instanceof BvTableBase targetBase && targetBase.getLocation() != null) {
      return getBvTableBounds(targetBase);
    }
    // External canvas BV reference alias.
    if (bvRefByName != null) {
      BvBvTableReference alias = bvRefByName.get(objectName.toLowerCase());
      if (alias != null && alias.getLocation() != null) {
        return getBvReferenceBounds(alias);
      }
    }
    return null;
  }

  private Bounds resolveSqlBvRefNavBounds(
      String objectName,
      double graphX,
      double graphY,
      double scaleX,
      double scaleY,
      int minSize,
      Map<String, BvBvTableReference> bvRefByName) {
    IBvTable local = findBvTableByName(objectName);
    if (local instanceof BvTableBase base && base.getLocation() != null) {
      return navigationBounds(
          graphX,
          graphY,
          scaleX,
          scaleY,
          base.getLocation(),
          base.getDrawnBoxWidth(),
          base.getDrawnBoxHeight(),
          minSize);
    }
    if (bvRefByName != null) {
      BvBvTableReference alias = bvRefByName.get(objectName.toLowerCase());
      if (alias != null && alias.getLocation() != null) {
        return navigationBounds(
            graphX,
            graphY,
            scaleX,
            scaleY,
            alias.getLocation(),
            alias.getDrawnBoxWidth(),
            alias.getDrawnBoxHeight(),
            minSize);
      }
    }
    return null;
  }

  private IBvTable findBvTableByName(String name) {
    if (Utils.isEmpty(name) || model == null) {
      return null;
    }
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (name.equalsIgnoreCase(table.getName()) || name.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private void drawRelationshipCandidateLine() {
    if (relationshipDragEndLocation == null
        || (startRelationshipBvTable == null && startRelationshipDvReference == null)) {
      return;
    }
    Bounds sourceBounds = getRelationshipStartBounds();
    if (sourceBounds == null) {
      return;
    }
    Point logEnd = screenDragEndToLogical(relationshipDragEndLocation);
    if (logEnd == null) {
      return;
    }

    boolean validTarget = isCandidateRelationshipValid();
    gc.setForeground(validTarget ? EColor.BLUE : EColor.DARKGRAY);
    gc.setLineWidth(2);
    gc.setLineStyle(ELineStyle.DASH);
    Bounds targetBounds = validTarget ? getCandidateTargetBounds() : null;
    if (targetBounds != null) {
      ModelGraphConnectionGeometry.drawStraightConnection(gc, sourceBounds, targetBounds);
    } else {
      Bounds cursorBounds = ModelGraphConnectionGeometry.pointBounds(logEnd.x, logEnd.y);
      Point lineStart = ModelGraphConnectionGeometry.anchorToward(sourceBounds, cursorBounds);
      ModelGraphConnectionGeometry.drawStraightConnection(gc, lineStart, logEnd);
    }
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setLineWidth(1);
    gc.setForeground(EColor.BLACK);
  }

  private Bounds getRelationshipStartBounds() {
    if (startRelationshipBvTable instanceof BvTableBase bvTable) {
      return getBvTableBounds(bvTable);
    }
    if (startRelationshipDvReference != null) {
      return getDvReferenceBounds(startRelationshipDvReference);
    }
    return null;
  }

  private Bounds getCandidateTargetBounds() {
    if (candidateRelationshipBvTable instanceof BvTableBase bvTable) {
      return getBvTableBounds(bvTable);
    }
    if (candidateRelationshipDvReference != null) {
      return getDvReferenceBounds(candidateRelationshipDvReference);
    }
    return null;
  }

  private Bounds getBvReferenceBounds(BvBvTableReference reference) {
    if (reference == null || reference.getLocation() == null) {
      return null;
    }
    Point loc = reference.getLocation();
    Point screenLoc = real2screen(loc.x, loc.y);
    int w = Math.max(1, reference.getDrawnBoxWidth());
    int h = Math.max(1, reference.getDrawnBoxHeight());
    return new Bounds(screenLoc.x, screenLoc.y, w, h);
  }

  private void addScd2ParentHubConnection(
      BvTableBase base,
      String bvKey,
      Bounds bvBounds,
      Map<String, BvDvTableReference> dvRefByName,
      List<ModelGraphEdgeLayout.Edge> edges) {
    BvDvTableReference hubRef = findCanvasParentHubReference(base, dvRefByName);
    if (hubRef == null || bvBounds == null) {
      return;
    }
    Bounds dvBounds = getDvReferenceBounds(hubRef);
    if (dvBounds == null) {
      return;
    }
    String dvKey = dvReferenceKey(hubRef);
    edges.add(
        new ModelGraphEdgeLayout.Edge(
            "hub|" + bvKey + "|" + dvKey, bvKey, bvBounds, dvKey, dvBounds));
  }

  private void addNavigationParentHubConnection(
      BvTableBase base,
      Bounds bvBounds,
      Map<String, BvDvTableReference> dvRefByName,
      double graphX,
      double graphY,
      double scaleX,
      double scaleY,
      int minSize) {
    BvDvTableReference hubRef = findCanvasParentHubReference(base, dvRefByName);
    if (hubRef == null || hubRef.getLocation() == null || bvBounds == null) {
      return;
    }
    Bounds dvBounds =
        navigationBounds(
            graphX,
            graphY,
            scaleX,
            scaleY,
            hubRef.getLocation(),
            hubRef.getDrawnBoxWidth(),
            hubRef.getDrawnBoxHeight(),
            minSize);
    ModelGraphConnectionGeometry.drawStraightConnection(gc, bvBounds, dvBounds);
  }

  /**
   * Extra canvas line from the SCD2 parent hub when that hub is on the canvas but not already a
   * derivative (older models that only set Parent hub, or inferred shared satellite parents).
   */
  private BvDvTableReference findCanvasParentHubReference(
      BvTableBase base, Map<String, BvDvTableReference> dvRefByName) {
    if (!(base instanceof BvScd2Table scd2) || dvRefByName == null) {
      return null;
    }
    String hubName =
        BusinessVaultDerivativeSupport.resolveCanvasParentHubName(scd2, dataVaultModel);
    if (Utils.isEmpty(hubName) || BusinessVaultDerivativeSupport.hasDerivative(scd2, hubName)) {
      return null;
    }
    BvDvTableReference target = dvRefByName.get(hubName.toLowerCase());
    if (target == null || target.getLocation() == null) {
      return null;
    }
    if (target.getDvTableType() != null && target.getDvTableType() != DvTableType.HUB) {
      return null;
    }
    return target;
  }

  private Bounds getBvTableBounds(BvTableBase table) {
    Point loc = table.getLocation();
    if (loc == null) {
      return null;
    }
    Point screenLoc = real2screen(loc.x, loc.y);
    return new Bounds(
        screenLoc.x, screenLoc.y, table.getDrawnBoxWidth(), table.getDrawnBoxHeight());
  }

  private Bounds getDvReferenceBounds(BvDvTableReference reference) {
    Point loc = reference.getLocation();
    if (loc == null) {
      return null;
    }
    Point screenLoc = real2screen(loc.x, loc.y);
    return new Bounds(
        screenLoc.x, screenLoc.y, reference.getDrawnBoxWidth(), reference.getDrawnBoxHeight());
  }

  private static Bounds navigationBounds(
      double graphX,
      double graphY,
      double scaleX,
      double scaleY,
      Point loc,
      int boxWidth,
      int boxHeight,
      int minSize) {
    int w = Math.max(minSize, (int) Math.ceil(Math.max(1, boxWidth) * scaleX));
    int h = Math.max(minSize, (int) Math.ceil(Math.max(1, boxHeight) * scaleY));
    int x = (int) (graphX + loc.x * scaleX);
    int y = (int) (graphY + loc.y * scaleY);
    return new Bounds(x, y, w, h);
  }

  private boolean isCandidateRelationshipValid() {
    if (startRelationshipBvTable != null) {
      return candidateRelationshipDvReference != null
          && candidateRelationshipDvReference != startRelationshipDvReference
          && BusinessVaultDerivativeSupport.canAddDerivative(
              startRelationshipBvTable, candidateRelationshipDvReference);
    }
    if (startRelationshipDvReference != null) {
      return candidateRelationshipBvTable != null
          && BusinessVaultDerivativeSupport.canAddDerivative(
              candidateRelationshipBvTable, startRelationshipDvReference);
    }
    return false;
  }

  private void prepareDvReferenceBoxSizes() {
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      if (reference.getLocation() == null) {
        continue;
      }
      calculateDvReferenceBoxSize(reference);
    }
  }

  private Point calculateDvReferenceBoxSize(BvDvTableReference reference) {
    String name = reference.getDvTableName() != null ? reference.getDvTableName() : "?";
    String typeLabel = reference.getDvTableType() != null ? reference.getDvTableType().name() : "?";
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(
            gc, name, resolveDvReferenceSecondaryLine(reference), typeLabel, null);
    reference.setDrawnBoxWidth(boxSize.width());
    reference.setDrawnBoxHeight(boxSize.height());
    return new Point(boxSize.width(), boxSize.height());
  }

  private String resolveDvReferenceSecondaryLine(BvDvTableReference reference) {
    if (reference == null) {
      return null;
    }
    if (!Utils.isEmpty(reference.getReferencedModelFilename())) {
      return modelBasename(reference.getReferencedModelFilename()) + " (linked)";
    }
    if (!showHashKeyFieldNames
        || reference.getDvTableType() != DvTableType.SATELLITE
        || dataVaultModel == null
        || Utils.isEmpty(reference.getDvTableName())) {
      return null;
    }
    IDvTable table = dataVaultModel.findTable(reference.getDvTableName());
    return DvTableDisplaySupport.getHashKeyFieldNameForDisplay(table, dataVaultModel, variables);
  }

  private static String modelBasename(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String name = path.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    if (name.endsWith(".hdv") || name.endsWith(".hbv")) {
      name = name.substring(0, name.length() - 4);
    }
    return name;
  }

  private void drawDvTableReferences() {
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      Point box = calculateDvReferenceBoxSize(reference);
      int boxWidth = box.x;
      int boxHeight = box.y;
      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      gc.setBackground(EColor.LIGHTGRAY);
      gc.setForeground(EColor.DARKGRAY);
      gc.setLineStyle(ELineStyle.DOT);
      gc.setLineWidth(reference.isSelected() ? 2 : 1);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setLineWidth(1);

      drawDvReferenceIcon(reference, x, y);

      String typeLabel =
          reference.getDvTableType() != null ? reference.getDvTableType().name() : "?";
      ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);

      String name = reference.getDvTableName();
      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, name, x, y, name.equals(mouseOverDvReferenceName));
      ModelGraphTableCardLayout.drawSecondaryLine(
          gc, resolveDvReferenceSecondaryLine(reference), x, y, nameExtent);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, reference, name));
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
                reference,
                name));
      }
    }
  }

  private void drawDvReferenceIcon(BvDvTableReference reference, int x, int y) {
    DvTableType tableType = reference.getDvTableType();
    if (tableType == null) {
      return;
    }
    ModelGraphTableCardLayout.drawSvgIcon(
        gc,
        getClass().getClassLoader(),
        DvTableDisplaySupport.getImagePath(tableType),
        x,
        y,
        magnification);
  }

  private void prepareBvReferenceBoxSizes() {
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getBvTableName())) {
        continue;
      }
      if (reference.getLocation() == null) {
        continue;
      }
      calculateBvReferenceBoxSize(reference);
    }
  }

  private Point calculateBvReferenceBoxSize(BvBvTableReference reference) {
    String name = reference.getBvTableName() != null ? reference.getBvTableName() : "?";
    String typeLabel =
        reference.getBvTableType() != null ? reference.getBvTableType().name() : "BV";
    ModelGraphTableCardLayout.BoxSize boxSize =
        ModelGraphTableCardLayout.computeBoxSize(
            gc, name, resolveBvReferenceSecondaryLine(reference), typeLabel, null);
    reference.setDrawnBoxWidth(boxSize.width());
    reference.setDrawnBoxHeight(boxSize.height());
    return new Point(boxSize.width(), boxSize.height());
  }

  private String resolveBvReferenceSecondaryLine(BvBvTableReference reference) {
    if (reference == null || Utils.isEmpty(reference.getReferencedModelFilename())) {
      return null;
    }
    return modelBasename(reference.getReferencedModelFilename()) + " (bv)";
  }

  private void drawBvTableReferences() {
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getBvTableName())) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      Point box = calculateBvReferenceBoxSize(reference);
      int boxWidth = box.x;
      int boxHeight = box.y;
      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;

      // Distinct from DV aliases: solid border, soft fill.
      gc.setBackground(EColor.BACKGROUND);
      gc.setForeground(EColor.BLUE);
      gc.setLineStyle(ELineStyle.DOT);
      gc.setLineWidth(reference.isSelected() ? 2 : 1);
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, CORNER_RADIUS_5, CORNER_RADIUS_5);
      gc.setLineStyle(ELineStyle.SOLID);
      gc.setLineWidth(1);

      ModelGraphTableCardLayout.drawSvgIcon(
          gc, getClass().getClassLoader(), "business-vault-model.svg", x, y, magnification);

      String typeLabel =
          reference.getBvTableType() != null ? reference.getBvTableType().name() : "BV";
      ModelGraphTableCardLayout.drawTypeBelowIcon(gc, typeLabel, x, y);

      String name = reference.getBvTableName();
      Point nameExtent =
          ModelGraphTableCardLayout.drawName(gc, name, x, y, name.equals(mouseOverBvReferenceName));
      ModelGraphTableCardLayout.drawSecondaryLine(
          gc, resolveBvReferenceSecondaryLine(reference), x, y, nameExtent);

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, reference, name));
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
                reference,
                name));
      }
    }
  }

  private void drawBusinessVaultTables() {
    for (IBvTable table : model.getTables()) {
      if (!(table instanceof BvTableBase base)) {
        continue;
      }
      Point loc = base.getLocation();
      if (loc == null) {
        continue;
      }
      int boxWidth = computeBoxWidth(base);
      int boxHeight = computeBoxHeight(base);
      base.setDrawnBoxWidth(boxWidth);
      base.setDrawnBoxHeight(boxHeight);
      Point screenLoc = real2screen(loc.x, loc.y);
      int x = screenLoc.x;
      int y = screenLoc.y;
      int[] color = tableTypeColor(base.getTableType());
      int[] fill = tableTypeFillColor(base.getTableType());
      String logoLetter = tableTypeLogoLetter(base.getTableType());

      if (fill != null) {
        gc.setBackground(fill[0], fill[1], fill[2]);
      } else {
        gc.setBackground(EColor.WHITE);
      }
      gc.fillRoundRectangle(x, y, boxWidth, boxHeight, 8, 8);
      gc.setLineWidth(base.isSelected() ? 2 : 1);
      gc.setForeground(color[0], color[1], color[2]);
      gc.drawRoundRectangle(x, y, boxWidth, boxHeight, 8, 8);
      gc.setLineWidth(1);

      if (logoLetter != null) {
        drawLetterLogo(tableTypeLogoColor(base.getTableType()), logoLetter, x, y);
      }
      int textX = tableContentX(base, x);

      String label = Const.NVL(base.getName(), base.getTableType().name());
      gc.setFont(EFont.GRAPH);
      gc.setForeground(EColor.BLACK);
      boolean underline = label.equals(mouseOverBvTableName);
      if (underline) {
        gc.setLineWidth(1);
        gc.drawText(label, textX, y + TABLE_TEXT_INSET, true);
        Point extent = gc.textExtent(label);
        gc.drawLine(
            textX,
            y + TABLE_TEXT_INSET + extent.y,
            textX + extent.x,
            y + TABLE_TEXT_INSET + extent.y);
      } else {
        gc.drawText(label, textX, y + TABLE_TEXT_INSET, true);
      }

      gc.setFont(EFont.SMALL);
      String typeLabel = base.getTableType().name();
      if (base instanceof BvBusinessTable businessTable) {
        typeLabel = typeLabel + " / " + businessTable.getMaterializationOrDefault().getCode();
      }
      gc.drawText(typeLabel, textX, y + 28, true);
      drawDerivativeReferences(base, textX, y);
      drawInferredParentHub(base, textX, y);
      if (base instanceof BvBusinessTable businessTable) {
        drawSqlRefSummary(businessTable, textX, y);
      }

      if (areaOwners != null) {
        areaOwners.add(
            new AreaOwner(
                AreaType.TRANSFORM_ICON, x, y, boxWidth, boxHeight, offset, table, label));
        gc.setFont(EFont.GRAPH);
        Point nameExtent = gc.textExtent(label);
        ModelGraphTableNameHitArea.Bounds nameHit =
            ModelGraphTableNameHitArea.bounds(textX, y + TABLE_TEXT_INSET, nameExtent);
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

  private void drawDerivativeReferences(BvTableBase base, int x, int y) {
    List<BvDerivativeRef> derivatives = base.getDerivatives();
    if (derivatives == null || derivatives.isEmpty()) {
      return;
    }
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    int lineHeight = derivativeLineHeight();
    int yRef = y + DERIVATIVES_TOP;
    for (BvDerivativeRef derivative : derivatives) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      String typeLabel =
          derivative.getDvTableType() != null ? derivative.getDvTableType().name() : "?";
      gc.drawText(typeLabel + ": " + derivative.getDvTableName(), x, yRef, true);
      yRef += lineHeight;
    }
    gc.setForeground(EColor.BLACK);
  }

  private void drawInferredParentHub(BvTableBase base, int x, int y) {
    String hubName = inferredParentHubLabel(base);
    if (Utils.isEmpty(hubName)) {
      return;
    }
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    int yRef = y + DERIVATIVES_TOP + countDerivatives(base) * derivativeLineHeight();
    gc.drawText("HUB: " + hubName, x, yRef, true);
    gc.setForeground(EColor.BLACK);
  }

  private String inferredParentHubLabel(BvTableBase base) {
    if (!(base instanceof BvScd2Table scd2)) {
      return null;
    }
    String hubName =
        BusinessVaultDerivativeSupport.resolveCanvasParentHubName(scd2, dataVaultModel);
    if (Utils.isEmpty(hubName) || BusinessVaultDerivativeSupport.hasDerivative(scd2, hubName)) {
      return null;
    }
    return hubName;
  }

  private void drawSqlRefSummary(BvBusinessTable businessTable, int x, int y) {
    if (businessTable == null || businessTable.getSqlRefs().isEmpty()) {
      return;
    }
    int offset = countDerivatives(businessTable);
    if (!Utils.isEmpty(inferredParentHubLabel(businessTable))) {
      offset++;
    }
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    int lineHeight = derivativeLineHeight();
    int yRef = y + DERIVATIVES_TOP + offset * lineHeight;
    for (BvSqlRef ref : businessTable.getSqlRefs()) {
      if (ref == null || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      String kind = ref.getResolvedKind() != null ? ref.getResolvedKind().getCode() : "?";
      String label =
          Utils.isEmpty(ref.getModelName())
              ? "ref: " + ref.getObjectName() + " (" + kind + ")"
              : "ref: " + ref.getModelName() + "." + ref.getObjectName() + " (" + kind + ")";
      gc.drawText(label, x, yRef, true);
      yRef += lineHeight;
    }
    gc.setForeground(EColor.BLACK);
  }

  private int computeBoxWidth(BvTableBase base) {
    int textPad = TABLE_TEXT_INSET * 2 + tableLogoColumnWidth(base);
    int width = 120;
    gc.setFont(EFont.GRAPH);
    String label = Const.NVL(base.getName(), base.getTableType().name());
    width = Math.max(width, gc.textExtent(label).x + textPad);
    gc.setFont(EFont.SMALL);
    String typeLabel = base.getTableType().name();
    if (base instanceof BvBusinessTable businessTable) {
      typeLabel = typeLabel + " / " + businessTable.getMaterializationOrDefault().getCode();
    }
    width = Math.max(width, gc.textExtent(typeLabel).x + textPad);
    for (BvDerivativeRef derivative : base.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      String derLabel =
          derivative.getDvTableType() != null ? derivative.getDvTableType().name() : "?";
      width =
          Math.max(width, gc.textExtent(derLabel + ": " + derivative.getDvTableName()).x + textPad);
    }
    String inferredHub = inferredParentHubLabel(base);
    if (!Utils.isEmpty(inferredHub)) {
      width = Math.max(width, gc.textExtent("HUB: " + inferredHub).x + textPad);
    }
    if (base instanceof BvBusinessTable businessTable) {
      for (BvSqlRef ref : businessTable.getSqlRefs()) {
        if (ref == null || Utils.isEmpty(ref.getObjectName())) {
          continue;
        }
        String kind = ref.getResolvedKind() != null ? ref.getResolvedKind().getCode() : "?";
        String refLabel =
            Utils.isEmpty(ref.getModelName())
                ? "ref: " + ref.getObjectName() + " (" + kind + ")"
                : "ref: " + ref.getModelName() + "." + ref.getObjectName() + " (" + kind + ")";
        width = Math.max(width, gc.textExtent(refLabel).x + textPad);
      }
    }
    return width;
  }

  private int computeBoxHeight(BvTableBase base) {
    int refCount = countDerivatives(base);
    if (!Utils.isEmpty(inferredParentHubLabel(base))) {
      refCount++;
    }
    if (base instanceof BvBusinessTable businessTable) {
      for (BvSqlRef ref : businessTable.getSqlRefs()) {
        if (ref != null && !Utils.isEmpty(ref.getObjectName())) {
          refCount++;
        }
      }
    }
    int lineHeight = derivativeLineHeight();
    return Math.max(60, DERIVATIVES_TOP + refCount * lineHeight + DERIVATIVES_BOTTOM_PADDING);
  }

  private static int countDerivatives(BvTableBase base) {
    int refCount = 0;
    if (base == null) {
      return 0;
    }
    for (BvDerivativeRef derivative : base.getDerivatives()) {
      if (derivative != null && !Utils.isEmpty(derivative.getDvTableName())) {
        refCount++;
      }
    }
    return refCount;
  }

  private int derivativeLineHeight() {
    gc.setFont(EFont.SMALL);
    return Math.max(REF_LINE_HEIGHT, gc.textExtent("Ay").y + 4);
  }

  private int[] tableTypeColor(BvTableType tableType) {
    return switch (tableType) {
      case SCD2 -> new int[] {20, 90, 160};
      case PIT -> new int[] {120, 70, 20};
      case BUSINESS_TABLE -> new int[] {40, 120, 60};
      case SOURCE_QUERY -> new int[] {90, 90, 110};
    };
  }

  /** Card fill: pastel in light mode, dark in dark mode so inverted (white) text stays readable. */
  private int[] tableTypeFillColor(BvTableType tableType) {
    if (tableType == null) {
      return null;
    }
    boolean dark = isDarkMode();
    return switch (tableType) {
      case SCD2 -> dark ? new int[] {16, 36, 54} : new int[] {228, 240, 248};
      case SOURCE_QUERY -> dark ? new int[] {30, 24, 44} : new int[] {236, 234, 246};
      default -> null;
    };
  }

  private static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** Badge fill: steel blue for SCD2, muted violet for source query. */
  private int[] tableTypeLogoColor(BvTableType tableType) {
    if (tableType == null) {
      return null;
    }
    return switch (tableType) {
      case SCD2 -> new int[] {88, 148, 186};
      case SOURCE_QUERY -> new int[] {130, 118, 168};
      default -> null;
    };
  }

  private static String tableTypeLogoLetter(BvTableType tableType) {
    if (tableType == null) {
      return null;
    }
    return switch (tableType) {
      case SCD2 -> "2";
      case SOURCE_QUERY -> "Q";
      default -> null;
    };
  }

  private static int tableLogoColumnWidth(BvTableBase base) {
    if (base == null || tableTypeLogoLetter(base.getTableType()) == null) {
      return 0;
    }
    return LOGO_INSET + LOGO_SIZE + LOGO_TEXT_GAP - TABLE_TEXT_INSET;
  }

  private int tableContentX(BvTableBase base, int boxX) {
    return boxX + TABLE_TEXT_INSET + tableLogoColumnWidth(base);
  }

  private void drawLetterLogo(int[] logoRgb, String letter, int boxX, int boxY) {
    if (logoRgb == null || Utils.isEmpty(letter)) {
      return;
    }
    int x = boxX + LOGO_INSET;
    int y = boxY + LOGO_INSET;
    gc.setBackground(logoRgb[0], logoRgb[1], logoRgb[2]);
    gc.fillRoundRectangle(x, y, LOGO_SIZE, LOGO_SIZE, 6, 6);
    gc.setFont(EFont.GRAPH);
    gc.setForeground(255, 255, 255);
    Point extent = gc.textExtent(letter);
    int tx = x + Math.max(0, (LOGO_SIZE - extent.x) / 2);
    int ty = y + Math.max(0, (LOGO_SIZE - extent.y) / 2);
    gc.drawText(letter, tx, ty, true);
  }

  private void drawEmptyHint() {
    String hint = BaseMessages.getString(PKG, "BusinessVaultModelPainter.EmptyHint");
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(hint, 40, 40, true);
  }
}
