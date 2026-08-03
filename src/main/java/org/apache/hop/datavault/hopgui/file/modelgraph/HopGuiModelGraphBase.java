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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.IRedrawable;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.markdown.NoteLinkHit;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.ModelCoachPanelAuditSupport;
import org.apache.hop.datavault.hopgui.ModelLoadDurationPaneAuditSupport;
import org.apache.hop.datavault.hopgui.coaching.CoachingCanvasDropSupport;
import org.apache.hop.datavault.hopgui.coaching.ICoachableModelGraph;
import org.apache.hop.datavault.hopgui.coaching.ModelCoachPanel;
import org.apache.hop.datavault.hopgui.file.metrics.ModelLoadDurationPane;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.hopgui.file.vault.DvNotePadSupport;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.dialog.NotePadDialog;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.delegates.HopGuiNoteLinkSupport;
import org.apache.hop.ui.hopgui.file.shared.HopGuiAbstractGraph;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.jspecify.annotations.Nullable;

/**
 * Shared graph shell for warehouse model file types (Data Vault, Business Vault, and future
 * dimensional models). Subclasses supply domain-specific painting, table CRUD, and relationship
 * rules.
 */
public abstract class HopGuiModelGraphBase extends HopGuiAbstractGraph implements IRedrawable {

  protected final ExplorerPerspective perspective;
  protected boolean positionChangeUndoMarked;
  protected boolean avoidContextDialog;

  // Note interaction state shared across model graph types.
  protected NoteLinkHit mouseOverNoteLink;
  protected DvNote currentNote;
  protected DvNote selectedNote;
  protected Point noteDragStart;
  protected boolean noteWasMoved;

  // Navigation viewport (minimap) pan state shared across model graph types.
  protected double lastNavigationScale;
  protected double lastNavigationGraphOriginX;
  protected double lastNavigationGraphOriginY;
  protected boolean navigatingWithViewport;
  protected Point navigationGrabOffset;

  // Shared canvas interaction state (lasso, object drag threshold, view pan prelude).
  protected static final int ICON_DRAG_THRESHOLD_PX = 3;
  protected Point lastClick;
  protected Point iconDragStart;
  protected boolean iconDragCommitted;
  protected boolean dragSelection;
  protected Rectangle selectionRegion;

  private ModelGraphMouseInteractions mouseInteractions;

  protected SashForm outerModelSash;
  protected SashForm innerModelSash;
  protected ModelCoachPanel coachPanel;
  protected ModelLoadDurationPane loadDurationPane;
  protected boolean coachPanelVisible = true;
  protected boolean loadDurationPanelVisible = true;

  protected HopGuiModelGraphBase(HopGui hopGui, Composite parent, ExplorerPerspective perspective) {
    super(hopGui, parent, SWT.NO_BACKGROUND);
    this.perspective = perspective;
  }

  protected abstract ModelGraphMouseInteractions createMouseInteractions();

  protected abstract String getMetricsModelName();

  protected abstract String getMetricsModelType();

  protected abstract List<String> getMetricsTableNames();

  /**
   * Creates coach panel (left), model canvas (center), and load duration overview (right). Call
   * after the toolbar is created; {@code registerPaintListener} should attach the subclass paint
   * listener to {@link #canvas}.
   */
  protected void createModelGraphBody(Control toolBar, Runnable registerPaintListener) {
    outerModelSash = new SashForm(this, SWT.HORIZONTAL);
    PropsUi.setLook(outerModelSash);
    FormData fdOuterSash = new FormData();
    fdOuterSash.left = new FormAttachment(0, 0);
    fdOuterSash.top = new FormAttachment(toolBar, 0);
    fdOuterSash.right = new FormAttachment(100, 0);
    fdOuterSash.bottom = new FormAttachment(100, 0);
    outerModelSash.setLayoutData(fdOuterSash);

    ICoachableModelGraph coachableGraph = this instanceof ICoachableModelGraph g ? g : null;
    coachPanel =
        new ModelCoachPanel(
            outerModelSash,
            hopGui,
            variables,
            this::getModelFilename,
            () -> coachableGraph == null ? null : coachableGraph.createCoachingModelAdapter(),
            () -> {
              if (coachableGraph != null) {
                coachableGraph.notifyCoachModelChanged();
              }
            });

    innerModelSash = new SashForm(outerModelSash, SWT.HORIZONTAL);
    PropsUi.setLook(innerModelSash);

    Composite canvasHolder = new Composite(innerModelSash, SWT.NONE);
    canvasHolder.setLayout(new FillLayout());
    PropsUi.setLook(canvasHolder);

    canvas = new Canvas(canvasHolder, SWT.NO_BACKGROUND);
    registerPaintListener.run();
    registerCanvasMouseListeners();
    if (coachableGraph != null) {
      CoachingCanvasDropSupport.register(canvas, hopGui, coachableGraph, this, variables);
    }

    loadDurationPane =
        new ModelLoadDurationPane(
            innerModelSash,
            hopGui,
            variables,
            this::getMetricsModelName,
            this::getMetricsModelType,
            this::getMetricsTableNames);

    outerModelSash.setWeights(new int[] {25, 75});
    innerModelSash.setWeights(new int[] {70, 30});
    restoreCoachPanelVisibility();
    restoreLoadDurationPanelVisibility();
  }

  protected String getModelFilename() {
    if (this instanceof IHopFileTypeHandler fileHandler) {
      return fileHandler.getFilename();
    }
    return null;
  }

  /**
   * Default visibility of the coach panel when the user has never toggled it for this file.
   * DV/BV/DM default to visible; source models override to hidden until there is a clear coach
   * use-case.
   */
  protected boolean defaultCoachPanelVisible() {
    return true;
  }

  protected void restoreCoachPanelVisibility() {
    if (outerModelSash == null || coachPanel == null) {
      return;
    }
    coachPanelVisible =
        ModelCoachPanelAuditSupport.retrievePanelVisible(
            getModelFilename(), defaultCoachPanelVisible());
    applyCoachPanelVisibility();
  }

  public void toggleCoachPanel() {
    if (outerModelSash == null || coachPanel == null) {
      return;
    }
    coachPanelVisible = !coachPanelVisible;
    applyCoachPanelVisibility();
    ModelCoachPanelAuditSupport.storePanelVisible(getModelFilename(), coachPanelVisible);
  }

  protected void applyCoachPanelVisibility() {
    if (outerModelSash == null || coachPanel == null || innerModelSash == null) {
      return;
    }
    if (coachPanelVisible) {
      coachPanel.setVisible(true);
      outerModelSash.setMaximizedControl(null);
      outerModelSash.setWeights(new int[] {25, 75});
    } else {
      coachPanel.setVisible(false);
      outerModelSash.setMaximizedControl(innerModelSash);
    }
    outerModelSash.layout(true, true);
  }

  public void refreshCoachPanel() {
    if (coachPanel != null && coachPanelVisible) {
      coachPanel.refresh();
    }
  }

  /**
   * Default visibility of the load-duration overview when the user has never toggled it for this
   * file. DV/BV/DM default to visible; source models override to hidden until loads exist.
   */
  protected boolean defaultLoadDurationPanelVisible() {
    return true;
  }

  protected void restoreLoadDurationPanelVisibility() {
    if (innerModelSash == null || canvas == null || loadDurationPane == null) {
      return;
    }
    loadDurationPanelVisible =
        ModelLoadDurationPaneAuditSupport.retrievePanelVisible(
            getModelFilename(), defaultLoadDurationPanelVisible());
    applyLoadDurationPanelVisibility();
  }

  public void toggleLoadDurationPanel() {
    if (innerModelSash == null || canvas == null || loadDurationPane == null) {
      return;
    }
    loadDurationPanelVisible = !loadDurationPanelVisible;
    applyLoadDurationPanelVisibility();
    ModelLoadDurationPaneAuditSupport.storePanelVisible(
        getModelFilename(), loadDurationPanelVisible);
  }

  protected void applyLoadDurationPanelVisibility() {
    Composite canvasHolder = canvas.getParent();
    if (loadDurationPanelVisible) {
      innerModelSash.setMaximizedControl(null);
      loadDurationPane.setVisible(true);
      innerModelSash.setWeights(new int[] {70, 30});
      refreshLoadDurationOverview();
    } else {
      innerModelSash.setMaximizedControl(canvasHolder);
      loadDurationPane.setVisible(false);
    }
    innerModelSash.layout(true, true);
  }

  public void refreshLoadDurationOverview() {
    if (loadDurationPane != null && loadDurationPanelVisible) {
      loadDurationPane.refresh();
    }
  }

  protected ModelGraphMouseInteractions mouseInteractions() {
    if (mouseInteractions == null) {
      mouseInteractions = createMouseInteractions();
    }
    return mouseInteractions;
  }

  /**
   * Registers standard model-graph canvas mouse listeners. Call after {@code canvas} is created;
   * subclasses still add their paint listener separately.
   */
  protected void registerCanvasMouseListeners() {
    canvas.addListener(SWT.MouseDown, this::onMouseDown);
    canvas.addListener(SWT.MouseUp, this::onMouseUp);
    canvas.addListener(SWT.MouseMove, this::onMouseMove);
    canvas.addMouseWheelListener(this::mouseScrolled);
    canvas.addListener(SWT.MouseDoubleClick, this::onMouseDoubleClick);
    canvas.addListener(SWT.MouseExit, this::onMouseExit);
  }

  protected void onMouseDown(Event e) {
    mouseDownEvent(e);
  }

  protected void onMouseUp(Event e) {
    mouseUpEvent(e);
  }

  protected void onMouseMove(Event e) {
    mouseMoveEvent(e);
  }

  protected void mouseDownEvent(Event e) {
    Point real = beginMouseEvent(e);
    boolean shift = isShiftDown(e);
    boolean control = isControlDown(e);

    if (tryBeginNavigationViewportDrag(e)) {
      return;
    }

    ModelGraphHit hit = mouseInteractions().resolveHit(real.x, real.y);
    if (hit == null) {
      hit = ModelGraphHit.BACKGROUND;
    }

    if (mouseInteractions().handleObjectMouseDown(e, real, hit, shift, control)) {
      return;
    }

    if (handleNoteMouseDown(e, real, hit.note(), hit.areaOwner(), control)) {
      return;
    }

    if (mouseInteractions().hasCancellableDragState()) {
      mouseInteractions().cancelActiveDragsOnBackgroundClick();
      clearNoteDragState();
      clearSelectionRegion();
      avoidContextDialog = true;
      redraw();
      return;
    }

    if (handleLassoMouseDown(e, real, control, hit.isBackground())) {
      return;
    }

    if (trySetupViewDragOnMouseDown(e, control)) {
      return;
    }

    redraw();
  }

  protected void mouseMoveEvent(Event e) {
    if (handleViewDragOnMouseMove(e)) {
      return;
    }

    Point real = screen2real(e.x, e.y);

    if (handleMouseMoveNavigationViewport(e)) {
      return;
    }

    boolean doRedraw = false;

    if (resize != null && selectedNote != null) {
      resizeDvNote(selectedNote, real);
      return;
    }

    if (mouseInteractions().isRelationshipDragActive()) {
      mouseInteractions().handleRelationshipMouseMove(e);
      redraw();
      return;
    }

    boolean leftButtonDown = (e.stateMask & SWT.BUTTON1) != 0;

    if (mouseInteractions().handleObjectMouseMove(real, leftButtonDown)) {
      doRedraw = true;
    }

    if (selectedNote != null
        && leftButtonDown
        && resize == null
        && !mouseInteractions().isRelationshipDragActive()) {
      if (mouseInteractions().handleNoteMouseMove(real)) {
        doRedraw = true;
      }
    }

    if (handleLassoMouseMove(real, leftButtonDown)) {
      doRedraw = true;
    }

    if (clearHoverDuringLasso()) {
      doRedraw = true;
    }

    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    if (!doRedraw) {
      doRedraw = mouseInteractions().updateHoverState(areaOwner, real);
    }
    doRedraw = mouseMoveOverNoteLink(areaOwner, doRedraw);
    doRedraw = mouseMoveOverNoteResize(areaOwner, real, doRedraw);

    if (doRedraw) {
      redraw();
      mouseInteractions().refreshGui();
    }
  }

  protected void mouseUpEvent(Event e) {
    try {
      canvas.setToolTipText(null);
      Point real = screen2real(e.x, e.y);

      if (handleMouseUpNavigationViewport()) {
        return;
      }

      endViewDragOnMouseUp();

      if (handleNoteResizeMouseUp()) {
        return;
      }

      if (e.button == 2) {
        clearLassoRegionDefensive();
        mouseInteractions().clearObjectDragState();
        clearNoteDragState();
        return;
      }

      if (mouseInteractions().isRelationshipDragActive()) {
        if (mouseInteractions().handleRelationshipMouseUp(e, real)) {
          return;
        }
      }

      clearEmptyLassoOnMouseUp(mouseInteractions().allowEmptyLassoClearOnMouseUp());

      LassoMouseUpResult lassoResult = handleLassoMouseUp(real);
      if (lassoResult == LassoMouseUpResult.SELECTED) {
        return;
      }

      if (mouseInteractions().handleCommittedDragMouseUp(e)) {
        return;
      }

      if (mouseInteractions().handlePureClickMouseUp(e, real)) {
        return;
      }

      if (avoidContextDialog) {
        avoidContextDialog = false;
      }

      clearLassoRegionDefensive();
      mouseInteractions().clearObjectDragState();
      clearNoteDragState();
    } finally {
      resetCanvasCursor();
    }
  }

  protected void onMouseExit(Event e) {
    mouseExitEvent(e);
  }

  protected void onMouseDoubleClick(Event e) {
    Point real = screen2real(e.x, e.y);
    DvNote note = getAreaOwnerNote(getVisibleAreaOwner(real.x, real.y));
    if (note != null) {
      editNote(note);
    }
  }

  protected static boolean isControlDown(Event e) {
    return (e.stateMask & SWT.MOD1) != 0;
  }

  protected static boolean isShiftDown(Event e) {
    return (e.stateMask & SWT.SHIFT) != 0;
  }

  /**
   * When starting a drag on an unselected canvas object (without Ctrl), clear all selections and
   * select only the object being dragged.
   */
  protected void prepareExclusiveDragSelection(
      boolean control, boolean currentlySelected, Runnable selectDragged) {
    if (!control && !currentlySelected) {
      mouseInteractions().unselectAllOnCanvas();
      selectDragged.run();
    }
  }

  /** Clears tooltip, converts to logical coords, and stores {@link #lastClick}. */
  protected Point beginMouseEvent(Event e) {
    canvas.setToolTipText(null);
    Point real = screen2real(e.x, e.y);
    lastClick = new Point(real.x, real.y);
    return real;
  }

  /**
   * Middle-button or Ctrl+left background pan from {@link
   * org.apache.hop.ui.hopgui.perspective.execution.DragViewZoomBase}. Returns true when panning
   * started.
   */
  protected boolean trySetupViewDragOnMouseDown(Event e, boolean control) {
    return setupDragView(e.button, control, new Point(e.x, e.y));
  }

  /** Returns true when a view-pan move was handled (caller should return early). */
  protected boolean handleViewDragOnMouseMove(Event e) {
    if (viewDrag && lastClick != null) {
      dragView(viewDragStart, new Point(e.x, e.y));
      return true;
    }
    return false;
  }

  /** Ends an in-progress middle-button / Ctrl+left view pan. */
  protected void endViewDragOnMouseUp() {
    if (viewDrag) {
      viewDrag = false;
      viewDragStart = null;
    }
  }

  /**
   * Commits an icon/body drag once the pointer moves past {@link #ICON_DRAG_THRESHOLD_PX}. Returns
   * true when drag was just committed.
   */
  protected boolean tryCommitIconDrag(Point real) {
    if (iconDragCommitted || iconDragStart == null) {
      return false;
    }
    int dxs = real.x - iconDragStart.x;
    int dys = real.y - iconDragStart.y;
    int threshSq = ICON_DRAG_THRESHOLD_PX * ICON_DRAG_THRESHOLD_PX;
    if (dxs * dxs + dys * dys > threshSq) {
      iconDragCommitted = true;
      dragSelection = true;
      markPositionUndoPoint();
      return true;
    }
    return false;
  }

  /** Result of {@link #handleLassoMouseUp(Point)} for mouse-up orchestration. */
  protected enum LassoMouseUpResult {
    NOT_ACTIVE,
    EMPTY_CLICK,
    SELECTED
  }

  /**
   * Left-click on a note: select, prepare drag/resize. Returns true when the event was consumed.
   */
  protected boolean handleNoteMouseDown(
      Event e, Point real, DvNote noteHit, AreaOwner areaOwner, boolean control) {
    if (noteHit == null
        || areaOwner == null
        || areaOwner.getAreaType() != AreaOwner.AreaType.NOTE
        || e.button != 1
        || !mouseInteractions().isNoteMouseDownAllowed()) {
      return false;
    }
    currentNote = noteHit;
    selectedNote = noteHit;
    noteWasMoved = false;
    prepareExclusiveDragSelection(control, noteHit.isSelected(), () -> noteHit.setSelected(true));

    Point loc = noteHit.getLocation() != null ? noteHit.getLocation() : new Point(0, 0);
    noteOffset = new Point(real.x - loc.x, real.y - loc.y);
    noteDragStart = new Point(real.x, real.y);
    resize = getResize(areaOwner.getArea(), real);
    if (resize != null) {
      markPositionUndoPoint();
      resizeArea =
          new Rectangle(
              loc.x,
              loc.y,
              Math.max(noteHit.getWidth(), noteHit.getMinimumWidth()),
              Math.max(noteHit.getHeight(), noteHit.getMinimumHeight()));
    }
    mouseInteractions().clearObjectDragState();
    clearSelectionRegion();
    redraw();
    return true;
  }

  /** Left-click on empty canvas: start lasso rubber-band selection. Returns true when started. */
  protected boolean handleLassoMouseDown(
      Event e, Point real, boolean control, boolean onBackground) {
    if (e.button != 1 || !onBackground) {
      return false;
    }
    if (!control) {
      mouseInteractions().unselectAllOnCanvas();
    }

    selectionRegion = new Rectangle((int) (real.x + offset.x), (int) (real.y + offset.y), 0, 0);
    mouseInteractions().onLassoMouseDownAfter();
    canvas.setData("mode", "select");
    setCanvasCursor(getDisplay().getSystemCursor(SWT.CURSOR_CROSS));
    avoidContextDialog = true;
    redraw();
    return true;
  }

  /**
   * Updates lasso rubber-band size during drag. Returns true when the region changed (caller should
   * redraw).
   */
  protected boolean handleLassoMouseMove(Point real, boolean leftButtonDown) {
    if (selectionRegion == null || !leftButtonDown || !mouseInteractions().isLassoMoveAllowed()) {
      return false;
    }
    selectionRegion.width = real.x + (int) offset.x - selectionRegion.x;
    selectionRegion.height = real.y + (int) offset.y - selectionRegion.y;
    return true;
  }

  /** Clears hover highlights while lasso is active. Returns true when hover state changed. */
  protected boolean clearHoverDuringLasso() {
    if (selectionRegion == null) {
      return false;
    }
    return mouseInteractions().clearHoverState();
  }

  /** Clears an empty lasso started by click-without-drag so background context menu can appear. */
  protected void clearEmptyLassoOnMouseUp(boolean allow) {
    if (!allow || selectionRegion == null || !selectionRegion.isEmpty()) {
      return;
    }
    avoidContextDialog = false;
    selectionRegion = null;
  }

  /**
   * Finishes lasso on mouse-up. Returns {@link LassoMouseUpResult#SELECTED} when selection was
   * applied (caller should return early).
   */
  protected LassoMouseUpResult handleLassoMouseUp(Point real) {
    if (selectionRegion == null) {
      return LassoMouseUpResult.NOT_ACTIVE;
    }
    selectionRegion.width = real.x - selectionRegion.x;
    selectionRegion.height = real.y - selectionRegion.y;

    int absW = Math.abs(selectionRegion.width);
    int absH = Math.abs(selectionRegion.height);

    if (absW < ICON_DRAG_THRESHOLD_PX && absH < ICON_DRAG_THRESHOLD_PX) {
      mouseUpClearSelectionRegion();
      return LassoMouseUpResult.EMPTY_CLICK;
    }
    mouseUpSelectInLassoRegion();
    return LassoMouseUpResult.SELECTED;
  }

  /** Click without drag: discard lasso and allow background context handling. */
  protected void mouseUpClearSelectionRegion() {
    selectionRegion = null;
    canvas.setData("mode", "null");
    setCanvasCursor(null);
    redraw();
  }

  /** Applies lasso selection to domain objects overlapping the rubber-band rect. */
  protected void mouseUpSelectInLassoRegion() {
    int x1 = selectionRegion.x;
    int y1 = selectionRegion.y;
    int x2 = x1 + selectionRegion.width;
    int y2 = y1 + selectionRegion.height;
    int minX = Math.min(x1, x2);
    int maxX = Math.max(x1, x2);
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);

    mouseInteractions().selectInLassoRegion(minX, minY, maxX, maxY);

    selectionRegion = null;
    canvas.setData("mode", "null");
    setCanvasCursor(null);
    avoidContextDialog = true;
    redraw();
    mouseInteractions().afterLassoSelection();
  }

  /** Defensive cleanup when mouse-up leaves an active lasso region. */
  protected void clearLassoRegionDefensive() {
    if (selectionRegion != null) {
      selectionRegion = null;
      setCanvasCursor(null);
    }
  }

  /** Ends in-progress note resize on mouse-up. Returns true when resize was finalized. */
  protected boolean handleNoteResizeMouseUp() {
    if (resize == null || selectedNote == null) {
      return false;
    }
    setChanged();
    resize = null;
    selectedNote = null;
    resizeArea = null;
    setCanvasCursor(null);
    clearNoteDragState();
    avoidContextDialog = true;
    return true;
  }

  protected void mouseExitEvent(Event e) {
    handleMouseExit();
  }

  protected void handleMouseExit() {
    canvas.setToolTipText(null);
    resetCanvasCursor();
    if (mouseInteractions().clearHoverState()) {
      redraw();
    }
  }

  protected @Nullable Cursor getCanvasCursor() {
    if (canvas != null && !canvas.isDisposed()) {
      return canvas.getCursor();
    }
    return getCursor();
  }

  protected void setCanvasCursor(@Nullable Cursor cursor) {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setCursor(cursor);
    } else {
      setCursor(cursor);
    }
  }

  /** Resets the canvas pointer to the platform default (clears resize, hand, lasso, etc.). */
  protected void resetCanvasCursor() {
    setCanvasCursor(null);
  }

  protected boolean isResizeHoverCursor(@Nullable Cursor cursor) {
    if (cursor == null) {
      return false;
    }
    for (Resize resizeKind : Resize.values()) {
      if (cursor.equals(getDisplay().getSystemCursor(resizeKind.getCursor()))) {
        return true;
      }
    }
    return false;
  }

  protected abstract ModelGraphSnapshotUndo<?> getSnapshotUndo();

  protected abstract Object getModelForUndo();

  protected abstract void restoreModelSnapshot(Object restored) throws Exception;

  protected abstract void clearSelectionRegion();

  protected abstract String undoRecordErrorTitle();

  protected abstract String undoRecordErrorMessage();

  protected abstract String undoApplyErrorTitle();

  protected abstract String undoApplyErrorMessage();

  protected abstract String undoToolbarItemId();

  protected abstract String redoToolbarItemId();

  protected abstract GuiToolbarWidgets getToolBarWidgets();

  protected abstract String getZoomLevelToolbarItemId();

  protected abstract List<DvNote> getModelNotes();

  protected abstract AreaOwner getVisibleAreaOwner(int x, int y);

  protected abstract IGuiContextHandler createNoteContextHandler(DvNote note, Point real);

  protected abstract String getNoteContextDialogMessage();

  protected abstract String getNoteLinkTableTooltip(String target);

  protected abstract String getNoteLinkErrorTitle();

  protected abstract String getNoteLinkUrlErrorMessage(String target);

  protected abstract String getNoteLinkTableNotFoundMessage(String tableName);

  protected abstract void navigateToNoteLinkTable(String tableName);

  protected void onClearNoteDragState() {
    // Optional hook for subclasses with additional drag bookkeeping.
  }

  /** Clears in-progress drags before starting a navigation viewport pan. */
  protected void prepareNavigationViewportDrag() {
    mouseInteractions().prepareNavigationViewportDrag();
  }

  /** Called after each paint to capture minimap geometry for viewport hit-testing and panning. */
  protected void captureNavigationViewGeometry(BasePainter painter) {
    if (painter == null) {
      return;
    }
    setGraphPort(painter.getGraphPort());
    setViewPort(painter.getViewPort());
    lastNavigationScale = painter.getNavigationScale();
    lastNavigationGraphOriginX = painter.getNavigationGraphOriginX();
    lastNavigationGraphOriginY = painter.getNavigationGraphOriginY();
  }

  protected void clearNavigationViewportState() {
    navigatingWithViewport = false;
    navigationGrabOffset = null;
  }

  /**
   * Returns true when the left button went down on the blue viewport rectangle and navigation
   * panning started.
   */
  protected boolean tryBeginNavigationViewportDrag(Event e) {
    if (e.button != 1
        || getViewPort() == null
        || getGraphPort() == null
        || lastNavigationScale <= 0.0
        || !getViewPort().contains(e.x, e.y)) {
      return false;
    }
    prepareNavigationViewportDrag();
    avoidContextDialog = true;
    navigatingWithViewport = true;
    navigationGrabOffset = new Point(e.x - getViewPort().x, e.y - getViewPort().y);
    redraw();
    return true;
  }

  /** Returns true when a navigation viewport drag was handled (caller should return early). */
  protected boolean handleMouseMoveNavigationViewport(Event e) {
    if (!navigatingWithViewport
        || (e.stateMask & SWT.BUTTON1) == 0
        || getViewPort() == null
        || getGraphPort() == null
        || lastNavigationScale <= 0.0) {
      return false;
    }
    mouseMoveNavigationViewport(e);
    return true;
  }

  /** Returns true when a navigation viewport drag ended (caller should return early). */
  protected boolean handleMouseUpNavigationViewport() {
    if (!navigatingWithViewport) {
      return false;
    }
    navigatingWithViewport = false;
    navigationGrabOffset = null;
    avoidContextDialog = true;
    redraw();
    return true;
  }

  protected boolean isNavigationViewportClick(Event e) {
    return e.button == 1
        && getViewPort() != null
        && getGraphPort() != null
        && lastNavigationScale > 0.0
        && getViewPort().contains(e.x, e.y);
  }

  private void mouseMoveNavigationViewport(Event e) {
    int desiredLeft = e.x - navigationGrabOffset.x;
    int desiredTop = e.y - navigationGrabOffset.y;

    Rectangle gp = getGraphPort();
    int vw = getViewPort().width;
    int vh = getViewPort().height;

    int minL = gp.x;
    int minT = gp.y;
    int maxL = gp.x + gp.width - vw;
    int maxT = gp.y + gp.height - vh;
    if (maxL < minL) {
      maxL = minL;
    }
    if (maxT < minT) {
      maxT = minT;
    }

    int clampedLeft = Math.clamp(desiredLeft, minL, maxL);
    int clampedTop = Math.clamp(desiredTop, minT, maxT);

    double newVisLeft = (clampedLeft - lastNavigationGraphOriginX) / lastNavigationScale;
    double newVisTop = (clampedTop - lastNavigationGraphOriginY) / lastNavigationScale;

    int newOx = (int) Math.round(-newVisLeft);
    int newOy = (int) Math.round(-newVisTop);

    if (newOx != offset.x || newOy != offset.y) {
      offset.x = newOx;
      offset.y = newOy;
      redraw();
      updateGraphAfterNavigationPan();
    }
  }

  /** Optional hook after the graph offset changes from minimap panning (e.g. refresh toolbar). */
  protected void updateGraphAfterNavigationPan() {
    // default: redraw only
  }

  public List<String> getZoomLevels() {
    return Arrays.asList(
        "25%",
        "50%", "75%", "100%", "150%", "200%", "300%", "400%", "500%", "600%", "700%", "800%",
        "900%", "1000%");
  }

  protected void performZoomIn() {
    magnification += 0.1f;
    if (magnification > 10f) {
      magnification = 10f;
    }
    clearSelectionRegion();
    setZoomLabel();
    redraw();
  }

  protected void performZoomOut() {
    magnification -= 0.1f;
    if (magnification < 0.1f) {
      magnification = 0.1f;
    }
    clearSelectionRegion();
    setZoomLabel();
    redraw();
  }

  protected void performZoom100Percent() {
    super.zoom100Percent();
  }

  protected void performZoomFitToScreen() {
    super.zoomFitToScreen();
  }

  protected void performZoomLevelChanged() {
    readMagnification();
    redraw();
  }

  protected void readMagnification() {
    GuiToolbarWidgets widgets = getToolBarWidgets();
    if (widgets == null) {
      return;
    }
    Combo zoomLabel = (Combo) widgets.getWidgetsMap().get(getZoomLevelToolbarItemId());
    if (zoomLabel == null || zoomLabel.isDisposed()) {
      return;
    }
    String possibleText = zoomLabel.getText().replace("%", "");

    try {
      magnification = Float.parseFloat(possibleText) / 100;
      if (zoomLabel.getText().indexOf('%') < 0) {
        zoomLabel.setText(zoomLabel.getText().concat("%"));
      }
    } catch (Exception e) {
      // ignore invalid input silently for basic impl (core shows dialog)
    }
    clearSelectionRegion();
  }

  @Override
  public void setZoomLabel() {
    GuiToolbarWidgets widgets = getToolBarWidgets();
    if (widgets == null) {
      return;
    }
    Combo combo = (Combo) widgets.getWidgetsMap().get(getZoomLevelToolbarItemId());
    if (combo == null || combo.isDisposed()) {
      return;
    }
    String newString = Math.round(magnification * 100) + "%";
    String oldString = combo.getText();
    if (!newString.equals(oldString)) {
      combo.setText(newString);
    }
  }

  protected byte[] captureUndoSnapshot() {
    ModelGraphSnapshotUndo<?> snapshotUndo = getSnapshotUndo();
    Object model = getModelForUndo();
    if (model == null || snapshotUndo == null || snapshotUndo.isApplyingSnapshot()) {
      return null;
    }
    try {
      return snapshotUndo.captureSnapshotObject(model, hopGui.getMetadataProvider());
    } catch (HopException e) {
      showUndoError(undoRecordErrorTitle(), undoRecordErrorMessage(), e);
      return null;
    }
  }

  protected void commitDialogUndo(byte[] beforeChange) {
    ModelGraphSnapshotUndo<?> snapshotUndo = getSnapshotUndo();
    if (beforeChange != null && snapshotUndo != null) {
      snapshotUndo.pushSnapshot(beforeChange);
    }
  }

  protected void markPositionUndoPoint() {
    if (!positionChangeUndoMarked) {
      markUndoPoint();
      positionChangeUndoMarked = true;
    }
  }

  protected void markUndoPoint() {
    ModelGraphSnapshotUndo<?> snapshotUndo = getSnapshotUndo();
    Object model = getModelForUndo();
    if (model == null || snapshotUndo == null || snapshotUndo.isApplyingSnapshot()) {
      return;
    }
    try {
      snapshotUndo.markChangeObject(model, hopGui.getMetadataProvider());
      enableUndoToolbarItems();
    } catch (HopException e) {
      showUndoError(undoRecordErrorTitle(), undoRecordErrorMessage(), e);
    }
  }

  protected void applySnapshotChange(Object restored) {
    if (restored == null) {
      return;
    }
    try {
      restoreModelSnapshot(restored);
    } catch (Exception e) {
      showUndoError(undoApplyErrorTitle(), undoApplyErrorMessage(), e);
    }
  }

  protected void enableUndoToolbarItems() {
    GuiToolbarWidgets widgets = getToolBarWidgets();
    ModelGraphSnapshotUndo<?> snapshotUndo = getSnapshotUndo();
    if (widgets == null || snapshotUndo == null) {
      return;
    }
    widgets.enableToolbarItem(undoToolbarItemId(), snapshotUndo.canUndo());
    widgets.enableToolbarItem(redoToolbarItemId(), snapshotUndo.canRedo());
  }

  protected void showUndoError(String title, String message, Exception e) {
    new ErrorDialog(hopGui.getShell(), title, message, e);
  }

  protected Map<String, Object> buildCanvasStateProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put(STATE_MAGNIFICATION, magnification);
    if (offset != null) {
      props.put("offsetX", offset.x);
      props.put("offsetY", offset.y);
    }
    return props;
  }

  protected void applyCanvasStateProperties(Map<String, Object> stateProperties) {
    if (stateProperties == null) {
      return;
    }
    Object mag = stateProperties.get(STATE_MAGNIFICATION);
    if (mag instanceof Number number) {
      magnification = number.floatValue();
    } else if (mag instanceof String string) {
      magnification = Float.parseFloat(string.replace("%", "").trim()) / 100f;
    }
    Object ox = stateProperties.get("offsetX");
    Object oy = stateProperties.get("offsetY");
    if (ox instanceof Number x && oy instanceof Number y) {
      offset.x = x.intValue();
      offset.y = y.intValue();
    }
    setZoomLabel();
  }

  protected @Nullable DvNote getAreaOwnerNote(AreaOwner areaOwner) {
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.NOTE
        && areaOwner.getOwner() instanceof DvNote note) {
      return note;
    }
    return null;
  }

  protected @Nullable NoteLinkHit getAreaOwnerNoteLink(AreaOwner areaOwner) {
    NoteLinkHit hopHit = HopGuiNoteLinkSupport.linkHitFrom(areaOwner);
    if (hopHit != null) {
      return hopHit;
    }
    // Legacy CUSTOM area owners (should not appear after Markdown painter).
    if (areaOwner != null
        && areaOwner.getAreaType() == AreaOwner.AreaType.CUSTOM
        && areaOwner.getOwner() instanceof NoteLinkHit linkHit) {
      return linkHit;
    }
    return null;
  }

  protected boolean mouseMoveOverNoteLink(AreaOwner areaOwner, boolean doRedraw) {
    NoteLinkHit newOver = getAreaOwnerNoteLink(areaOwner);
    if ((mouseOverNoteLink == null && newOver != null)
        || (mouseOverNoteLink != null
            && !HopGuiNoteLinkSupport.noteLinksEqual(mouseOverNoteLink, newOver))) {
      doRedraw = true;
    }
    mouseOverNoteLink = newOver;

    Cursor hand = getDisplay().getSystemCursor(SWT.CURSOR_HAND);
    if (newOver != null) {
      if (!Objects.equals(getCanvasCursor(), hand)) {
        setCanvasCursor(hand);
        doRedraw = true;
      }
      String target = newOver.target() != null ? newOver.target().trim() : "";
      String tip =
          HopGuiNoteLinkSupport.isUrlTarget(target)
                  || DvNotePadSupport.looksLikeFileOrUrlTarget(target)
              ? HopGuiNoteLinkSupport.tooltipFor(newOver)
              : getNoteLinkTableTooltip(target);
      if (!Objects.equals(canvas.getToolTipText(), tip)) {
        canvas.setToolTipText(tip);
      }
    } else if (getCanvasCursor() == hand) {
      setCanvasCursor(null);
      doRedraw = true;
    }
    return doRedraw;
  }

  protected boolean mouseMoveOverNoteResize(AreaOwner areaOwner, Point real, boolean doRedraw) {
    if (mouseInteractions().isNoteResizeHoverBlocked()) {
      return clearStaleHoverCursor(doRedraw);
    }
    Resize resizeOver = null;
    if (areaOwner != null && areaOwner.getAreaType() == AreaOwner.AreaType.NOTE) {
      resizeOver = getResize(areaOwner.getArea(), real);
    }
    if (resizeOver != null) {
      Cursor cursor = getDisplay().getSystemCursor(resizeOver.getCursor());
      if (!Objects.equals(getCanvasCursor(), cursor)) {
        setCanvasCursor(cursor);
        doRedraw = true;
      }
    } else if (isResizeHoverCursor(getCanvasCursor())) {
      setCanvasCursor(null);
      doRedraw = true;
    }
    return doRedraw;
  }

  /**
   * Clears resize (and other non-lasso) hover cursors when drag/lasso state blocks resize hover
   * updates. Preserves the lasso crosshair and active note-resize cursor.
   */
  protected boolean clearStaleHoverCursor(boolean doRedraw) {
    if (selectionRegion != null || resize != null) {
      return doRedraw;
    }
    Cursor hand = getDisplay().getSystemCursor(SWT.CURSOR_HAND);
    Cursor current = getCanvasCursor();
    if (mouseOverNoteLink == null && current != null && current != hand) {
      setCanvasCursor(null);
      doRedraw = true;
    }
    return doRedraw;
  }

  protected void clearNoteDragState() {
    positionChangeUndoMarked = false;
    noteDragStart = null;
    noteWasMoved = false;
    noteOffset = null;
    currentNote = null;
    if (resize == null) {
      selectedNote = null;
      resizeArea = null;
    }
    onClearNoteDragState();
  }

  protected void unselectAllNotes() {
    for (DvNote note : getModelNotes()) {
      if (note != null) {
        note.setSelected(false);
      }
    }
  }

  protected List<DvNote> getSelectedNotes() {
    List<DvNote> list = new ArrayList<>();
    for (DvNote note : getModelNotes()) {
      if (note != null && note.isSelected()) {
        list.add(note);
      }
    }
    return list;
  }

  protected boolean isNoteInLassoScreenRect(
      DvNote note, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (note == null) {
      return false;
    }
    Point loc = note.getLocation();
    if (loc == null) {
      return false;
    }
    int nw = Math.max(1, Math.max(note.getWidth(), note.getMinimumWidth()));
    int nh = Math.max(1, Math.max(note.getHeight(), note.getMinimumHeight()));
    int nMinX = loc.x + (int) offset.x;
    int nMinY = loc.y + (int) offset.y;
    int nMaxX = nMinX + nw;
    int nMaxY = nMinY + nh;
    boolean xOverlap = Math.max(lassoMinX, nMinX) < Math.min(lassoMaxX, nMaxX);
    boolean yOverlap = Math.max(lassoMinY, nMinY) < Math.min(lassoMaxY, nMaxY);
    return xOverlap && yOverlap;
  }

  protected void resizeDvNote(DvNote note, Point real) {
    if (note == null || resize == null || resizeArea == null) {
      return;
    }
    switch (resize) {
      case EAST -> resizeNoteEast(note, real);
      case NORTH -> resizeNoteNorth(note, real);
      case NORTH_EAST -> resizeNoteNorthEast(note, real);
      case NORTH_WEST -> resizeNoteNorthWest(note, real);
      case SOUTH -> resizeNoteSouth(note, real);
      case SOUTH_EAST -> resizeNoteSouthEast(note, real);
      case SOUTH_WEST -> resizeNoteSouthWest(note, real);
      case WEST -> resizeNoteWest(note, real);
    }
    redraw();
  }

  private int clampedEastWidth(int mouseX, DvNote note) {
    return Math.max(mouseX - resizeArea.x, note.getMinimumWidth());
  }

  private int clampedSouthHeight(int mouseY, DvNote note) {
    return Math.max(mouseY - resizeArea.y, note.getMinimumHeight());
  }

  private int clampedWestEdgeX(int mouseX, DvNote note) {
    int x = Math.max(0, mouseX);
    int maxX = resizeArea.x + resizeArea.width - note.getMinimumWidth();
    return Math.min(x, maxX);
  }

  private int clampedNorthEdgeY(int mouseY, DvNote note) {
    int y = Math.max(0, mouseY);
    int maxY = resizeArea.y + resizeArea.height - note.getMinimumHeight();
    return Math.min(y, maxY);
  }

  private int widthFromWestEdge(DvNote note) {
    return resizeArea.x + resizeArea.width - note.getLocation().x;
  }

  private int heightFromNorthEdge(DvNote note) {
    return resizeArea.y + resizeArea.height - note.getLocation().y;
  }

  private void resizeNoteEast(DvNote note, Point real) {
    PropsUi.setSize(note, clampedEastWidth(real.x, note), note.getHeight());
  }

  private void resizeNoteSouth(DvNote note, Point real) {
    PropsUi.setSize(note, note.getWidth(), clampedSouthHeight(real.y, note));
  }

  private void resizeNoteWest(DvNote note, Point real) {
    PropsUi.setLocation(note, clampedWestEdgeX(real.x, note), resizeArea.y);
    PropsUi.setSize(note, widthFromWestEdge(note), note.getHeight());
  }

  private void resizeNoteNorth(DvNote note, Point real) {
    PropsUi.setLocation(note, resizeArea.x, clampedNorthEdgeY(real.y, note));
    PropsUi.setSize(note, note.getWidth(), heightFromNorthEdge(note));
  }

  private void resizeNoteSouthEast(DvNote note, Point real) {
    PropsUi.setSize(note, clampedEastWidth(real.x, note), clampedSouthHeight(real.y, note));
  }

  private void resizeNoteNorthEast(DvNote note, Point real) {
    PropsUi.setLocation(note, resizeArea.x, clampedNorthEdgeY(real.y, note));
    PropsUi.setSize(note, clampedEastWidth(real.x, note), heightFromNorthEdge(note));
  }

  private void resizeNoteSouthWest(DvNote note, Point real) {
    PropsUi.setLocation(note, clampedWestEdgeX(real.x, note), resizeArea.y);
    PropsUi.setSize(note, widthFromWestEdge(note), clampedSouthHeight(real.y, note));
  }

  private void resizeNoteNorthWest(DvNote note, Point real) {
    PropsUi.setLocation(note, clampedWestEdgeX(real.x, note), clampedNorthEdgeY(real.y, note));
    PropsUi.setSize(note, widthFromWestEdge(note), heightFromNorthEdge(note));
  }

  public void editNote(DvNote note) {
    editNote(note, true);
  }

  public void editNote(DvNote note, boolean recordUndo) {
    if (note == null) {
      return;
    }
    byte[] beforeChange = recordUndo ? captureUndoSnapshot() : null;
    NotePadMeta pad = DvNotePadSupport.toNotePadMeta(note);
    String title =
        BaseMessages.getString(
            org.apache.hop.datavault.hopgui.file.vault.DvNoteDialog.class, "DvNoteDialog.Title");
    NotePadDialog dialog = new NotePadDialog(variables, getShell(), title, pad, getModelFilename());
    NotePadMeta result = dialog.open();
    if (result != null) {
      DvNotePadSupport.applyFromNotePadMeta(note, result);
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
      canvas.setFocus();
    }
  }

  protected void showNoteContextDialog(Event e, DvNote note, Point real) {
    if (note == null) {
      return;
    }
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = getNoteContextDialogMessage();
      IGuiContextHandler contextHandler = createNoteContextHandler(note, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(hopGui.getShell(), "Error", "Error showing note context dialog: ", ex);
    } finally {
      canvas.setFocus();
    }
  }

  protected boolean handleNoteLinkClickAt(Point real) {
    NoteLinkHit linkHit = getAreaOwnerNoteLink(getVisibleAreaOwner(real.x, real.y));
    if (linkHit == null) {
      return false;
    }
    followNoteLink(linkHit);
    return true;
  }

  protected void handleNoteBodyClick(Event e, DvNote note, Point real, boolean control) {
    if (note == null) {
      return;
    }
    if (control) {
      editNote(note);
    } else if (!avoidContextDialog) {
      showNoteContextDialog(e, note, real);
    }
  }

  /**
   * Follow a Markdown note link: HTTP(S) and Hop-openable files via Hop support; bare names
   * navigate to a table in the model (legacy DV/BV/DM behavior).
   */
  protected void followNoteLink(NoteLinkHit linkHit) {
    if (linkHit == null || Utils.isEmpty(linkHit.target())) {
      return;
    }
    String target = linkHit.target().trim();
    if (HopGuiNoteLinkSupport.isUrlTarget(target)
        || HopGuiNoteLinkSupport.isUrlTarget(variables.resolve(target))) {
      try {
        EnvironmentUtils.getInstance().openUrl(variables.resolve(target));
      } catch (HopException e) {
        new ErrorDialog(getShell(), getNoteLinkErrorTitle(), getNoteLinkUrlErrorMessage(target), e);
      }
      return;
    }
    // Prefer table navigation for bare identifiers (hub_customer); files when path-like.
    if (!DvNotePadSupport.looksLikeFileOrUrlTarget(target)) {
      navigateToNoteLinkTable(target);
      return;
    }
    // Path-like: open via Hop (pipeline/workflow/etc. relative to model file).
    boolean handled =
        HopGuiNoteLinkSupport.followLink(hopGui, variables, getModelFilename(), linkHit);
    if (!handled) {
      navigateToNoteLinkTable(target);
    }
  }

  protected void showNoteLinkTableNotFound(String tableName) {
    MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_WARNING);
    box.setText(getNoteLinkErrorTitle());
    box.setMessage(getNoteLinkTableNotFoundMessage(tableName));
    box.open();
  }

  protected void centerOnCanvasLocation(Point loc, int boxW, int boxH) {
    if (loc == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    float mag = calculateCorrectedMagnification();
    org.eclipse.swt.graphics.Rectangle bounds = canvas.getBounds();
    double centerX = loc.x + boxW / 2.0;
    double centerY = loc.y + boxH / 2.0;
    offset.x = bounds.width / (2.0 * mag) - centerX;
    offset.y = bounds.height / (2.0 * mag) - centerY;
    validateOffset();
  }
}
