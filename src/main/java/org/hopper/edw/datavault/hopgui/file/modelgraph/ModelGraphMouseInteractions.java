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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.Point;
import org.eclipse.swt.widgets.Event;

/**
 * Domain-specific mouse behavior for a model graph (Data Vault, Business Vault, dimensional, etc.).
 * Shared event orchestration lives in {@link HopGuiModelGraphBase}; implementations supply hit
 * resolution and model-object actions.
 */
public interface ModelGraphMouseInteractions {

  ModelGraphHit resolveHit(int logicalX, int logicalY);

  /**
   * Table/ref/icon hits: relationship drag, name edit, body drag, info icon. Returns true when the
   * event was consumed.
   */
  boolean handleObjectMouseDown(
      Event e, Point real, ModelGraphHit hit, boolean shift, boolean control);

  boolean isRelationshipDragActive();

  /**
   * When a relationship hop drag is active, the canvas node name used as hop origin for Hop Web
   * client feedback ({@code startHopNode}). Null when not applicable.
   */
  default String webRelationshipStartNodeName() {
    return null;
  }

  void handleRelationshipMouseMove(Event e);

  /** Returns true when the relationship drag was finalized (caller should return). */
  boolean handleRelationshipMouseUp(Event e, Point real);

  /** Object (table/ref) drag during mouse-move. Returns true when a redraw is needed. */
  boolean handleObjectMouseMove(Point real, boolean leftButtonDown);

  /** Note drag during mouse-move. Returns true when a redraw is needed. */
  boolean handleNoteMouseMove(Point real);

  boolean hasCancellableDragState();

  void cancelActiveDragsOnBackgroundClick();

  void clearObjectDragState();

  void unselectAllOnCanvas();

  void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY);

  void afterLassoSelection();

  /** End committed table/note drag on mouse-up. Returns true when handled (caller returns). */
  boolean handleCommittedDragMouseUp(Event e);

  /**
   * Pure click (no drag): context menus, ctrl-toggle, note links. Returns true when handled (caller
   * returns).
   */
  boolean handlePureClickMouseUp(Event e, Point real);

  /** Clears hover highlights. Returns true when state changed (caller should redraw). */
  boolean clearHoverState();

  /** Updates name-underline / info-tooltip hover. Returns true when a redraw is needed. */
  boolean updateHoverState(AreaOwner areaOwner, Point real);

  void onLassoMouseDownAfter();

  boolean isNoteMouseDownAllowed();

  boolean isLassoMoveAllowed();

  boolean allowEmptyLassoClearOnMouseUp();

  boolean isNoteResizeHoverBlocked();

  void prepareNavigationViewportDrag();

  void refreshGui();
}
