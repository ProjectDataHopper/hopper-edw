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
package org.apache.hop.datavault.hopgui.file.executionmap;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.eclipse.swt.widgets.Event;

/** Read-only mouse interactions for execution map graphs. */
public class ExecutionMapReadOnlyInteractions implements ModelGraphMouseInteractions {

  private final HopGuiExecutionMapGraph graph;

  public ExecutionMapReadOnlyInteractions(HopGuiExecutionMapGraph graph) {
    this.graph = graph;
  }

  @Override
  public ModelGraphHit resolveHit(int logicalX, int logicalY) {
    AreaOwner areaOwner = graph.getVisibleAreaOwner(logicalX, logicalY);
    ExecutionMapNode node = graph.getAreaOwnerNode(areaOwner);
    AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
    if (node != null) {
      return new ModelGraphHit(areaOwner, areaType, null, node);
    }
    return new ModelGraphHit(areaOwner, areaType, null, null);
  }

  @Override
  public boolean handleObjectMouseDown(
      Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
    if (e.button != 1 || hit == null || !(hit.canvasObject() instanceof ExecutionMapNode node)) {
      return false;
    }
    AreaType areaType = hit.areaType();
    if (areaType == AreaType.TRANSFORM_NAME
        && graph.getFocusContext().canDrillInto(node, graph.getDocument())) {
      graph.drillInto(node);
      return true;
    }
    if (areaType == AreaType.TRANSFORM_ICON) {
      graph.showNodeContextDialog(e, node);
      return true;
    }
    return false;
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
  public void unselectAllOnCanvas() {}

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
    boolean changed = false;
    if (graph.mouseOverNode != null) {
      graph.mouseOverNode = null;
      changed = true;
    }
    if (graph.mouseOverNodeName != null) {
      graph.mouseOverNodeName = null;
      changed = true;
    }
    if (changed) {
      graph.updateNodeHoverTooltip(null);
    }
    return changed;
  }

  @Override
  public boolean updateHoverState(AreaOwner areaOwner, Point real) {
    boolean doRedraw = false;
    ExecutionMapNode node = graph.getAreaOwnerNode(areaOwner);
    if (node != graph.mouseOverNode) {
      graph.mouseOverNode = node;
      graph.updateNodeHoverTooltip(node);
      doRedraw = true;
    }

    String newOver = null;
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaType.TRANSFORM_NAME
        && node != null
        && graph.getFocusContext().canDrillInto(node, graph.getDocument())
        && areaOwner.getOwner() instanceof String name) {
      newOver = name;
    }
    if ((graph.mouseOverNodeName == null && newOver != null)
        || (graph.mouseOverNodeName != null && !graph.mouseOverNodeName.equals(newOver))) {
      graph.mouseOverNodeName = newOver;
      doRedraw = true;
    }
    return doRedraw;
  }

  @Override
  public void onLassoMouseDownAfter() {
    graph.mouseOverNodeName = null;
  }

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
