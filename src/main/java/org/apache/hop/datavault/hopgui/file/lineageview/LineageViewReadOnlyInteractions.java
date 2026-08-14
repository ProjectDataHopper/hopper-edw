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
package org.apache.hop.datavault.hopgui.file.lineageview;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.eclipse.swt.widgets.Event;

/** Pan/zoom/select only — no drag or create. */
public class LineageViewReadOnlyInteractions implements ModelGraphMouseInteractions {

  private final HopGuiLineageViewGraph graph;

  public LineageViewReadOnlyInteractions(HopGuiLineageViewGraph graph) {
    this.graph = graph;
  }

  @Override
  public ModelGraphHit resolveHit(int logicalX, int logicalY) {
    AreaOwner areaOwner = graph.getVisibleAreaOwner(logicalX, logicalY);
    LineageNode node = nodeOf(areaOwner);
    AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
    return new ModelGraphHit(areaOwner, areaType, null, node);
  }

  @Override
  public boolean handleObjectMouseDown(
      Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
    if (e.button != 1 || hit == null || !(hit.canvasObject() instanceof LineageNode node)) {
      return false;
    }
    if (hit.areaType() == AreaOwner.AreaType.TRANSFORM_NAME) {
      graph.selectNodeName(node.getId());
    } else {
      graph.selectNode(node.getId(), e);
    }
    return true;
  }

  @Override
  public boolean isRelationshipDragActive() {
    return false;
  }

  @Override
  public void handleRelationshipMouseMove(Event e) {}

  @Override
  public boolean handleRelationshipMouseUp(Event e, Point real) {
    return false;
  }

  @Override
  public boolean handleObjectMouseMove(Point real, boolean leftButtonDown) {
    return false;
  }

  @Override
  public boolean handleNoteMouseMove(Point real) {
    return false;
  }

  @Override
  public boolean hasCancellableDragState() {
    return false;
  }

  @Override
  public void cancelActiveDragsOnBackgroundClick() {}

  @Override
  public void clearObjectDragState() {}

  @Override
  public void unselectAllOnCanvas() {
    graph.selectNode(null);
  }

  @Override
  public void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {}

  @Override
  public void afterLassoSelection() {}

  @Override
  public boolean handleCommittedDragMouseUp(Event e) {
    return false;
  }

  @Override
  public boolean handlePureClickMouseUp(Event e, Point real) {
    return false;
  }

  @Override
  public boolean clearHoverState() {
    if (graph.getMouseOverTableName() == null) {
      return false;
    }
    graph.setMouseOverTableName(null);
    return true;
  }

  @Override
  public boolean updateHoverState(AreaOwner areaOwner, Point real) {
    String newOver = null;
    if (areaOwner != null && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME) {
      LineageNode named = nodeOf(areaOwner);
      if (named != null && named.getId() != null) {
        newOver = named.getId();
      }
    }
    boolean redraw =
        (graph.getMouseOverTableName() == null && newOver != null)
            || (graph.getMouseOverTableName() != null
                && !graph.getMouseOverTableName().equals(newOver));
    if (redraw) {
      graph.setMouseOverTableName(newOver);
    }
    graph.updateNodeHoverTooltip(nodeOf(areaOwner));
    return redraw;
  }

  static LineageNode nodeOf(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getOwner() instanceof LineageNode node) {
      return node;
    }
    if (areaOwner.getParent() instanceof LineageNode node) {
      return node;
    }
    return null;
  }

  @Override
  public void onLassoMouseDownAfter() {}

  @Override
  public boolean isNoteMouseDownAllowed() {
    return false;
  }

  @Override
  public boolean isLassoMoveAllowed() {
    return false;
  }

  @Override
  public boolean allowEmptyLassoClearOnMouseUp() {
    return true;
  }

  @Override
  public boolean isNoteResizeHoverBlocked() {
    return true;
  }

  @Override
  public void prepareNavigationViewportDrag() {}

  @Override
  public void refreshGui() {
    graph.updateGui();
  }
}
