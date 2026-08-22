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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.core.gui.IRedrawable;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.markdown.NoteLinkHit;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.ModelCoachPanelAuditSupport;
import org.hopper.edw.datavault.hopgui.ModelLoadDurationPaneAuditSupport;
import org.hopper.edw.datavault.hopgui.coaching.CoachingCanvasDropSupport;
import org.hopper.edw.datavault.hopgui.coaching.ICoachableModelGraph;
import org.hopper.edw.datavault.hopgui.coaching.ModelCoachPanel;
import org.hopper.edw.datavault.hopgui.file.metrics.ModelLoadDurationPane;
import org.hopper.edw.datavault.hopgui.file.vault.BasePainter;
import org.hopper.edw.datavault.hopgui.file.vault.DvNotePadSupport;
import org.hopper.edw.datavault.metadata.DvNote;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.hopgui.CanvasFacade;
import org.apache.hop.ui.hopgui.CanvasListener;
import org.apache.hop.ui.hopgui.CanvasSvgFacade;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.dialog.NotePadDialog;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.delegates.HopGuiNoteLinkSupport;
import org.apache.hop.ui.hopgui.file.shared.HopGuiAbstractGraph;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.CanvasZoomHelper;
import org.apache.hop.ui.hopgui.shared.IWebCanvasGraph;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.jspecify.annotations.Nullable;

/**
 * Shared graph shell for warehouse model file types (Data Vault, Business Vault, and future
 * dimensional models). Subclasses supply domain-specific painting, table CRUD, and relationship
 * rules.
 *
 * <p>On Hop Web, graphs render via SVG ({@link CanvasSvgFacade#publishSnapshot}) instead of
 * painting on the RAP canvas GC.
 */
public abstract class HopGuiModelGraphBase extends HopGuiAbstractGraph
    implements IRedrawable, IWebCanvasGraph {

  /** Optional RAP zoom remote object; set when {@link #setupWebCanvas()} runs. */
  protected Object canvasZoomHandler;

  /** Used by explorer tab-switch to rebind the shared Hop Web zoom remote. */
  public Object getCanvasZoomHandler() {
    return canvasZoomHandler;
  }

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

  /**
   * Last mouse button from mouse-down (pipeline graphs use the same pattern). RAP often omits
   * {@code SWT.BUTTON1} from {@code stateMask} on mouse-move and mouse-up, so drag commits must not
   * rely on stateMask alone.
   */
  protected int lastButton;

  private ModelGraphMouseInteractions mouseInteractions;

  protected SashForm outerModelSash;
  protected SashForm innerModelSash;
  protected ModelCoachPanel coachPanel;
  protected ModelLoadDurationPane loadDurationPane;
  protected boolean coachPanelVisible;
  protected boolean loadDurationPanelVisible;

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
            },
            coachableGraph);

    innerModelSash = new SashForm(outerModelSash, SWT.HORIZONTAL);
    PropsUi.setLook(innerModelSash);

    Composite canvasHolder = new Composite(innerModelSash, SWT.NONE);
    canvasHolder.setLayout(new FillLayout());
    PropsUi.setLook(canvasHolder);

    canvas = new Canvas(canvasHolder, SWT.NO_BACKGROUND);
    setupWebCanvas();
    registerPaintListener.run();
    registerCanvasMouseListeners();
    // SWT DropTarget is fragile under RAP; coach DnD is desktop-only for now.
    if (coachableGraph != null && !EnvironmentUtils.getInstance().isWeb()) {
      CoachingCanvasDropSupport.register(canvas, hopGui, coachableGraph, this, variables);
    }

    // ModelLoadDurationPane uses ScrolledComposite + paint listeners that RAP does not support
    // (NoSuchMethodError on ScrolledComposite.addPaintListener). Skip construction under Hop Web.
    if (!EnvironmentUtils.getInstance().isWeb()) {
      loadDurationPane =
          new ModelLoadDurationPane(
              innerModelSash,
              hopGui,
              variables,
              this::getMetricsModelName,
              this::getMetricsModelType,
              this::getMetricsTableNames);
      innerModelSash.setWeights(new int[] {70, 30});
    } else {
      loadDurationPane = null;
    }

    outerModelSash.setWeights(new int[] {25, 75});
    restoreCoachPanelVisibility();
    restoreLoadDurationPanelVisibility();
  }

  /**
   * Registers this graph with the Hop Web SVG canvas stack when running under RAP. No-op on
   * desktop.
   */
  protected void setupWebCanvas() {
    if (canvas == null || canvas.isDisposed() || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    // ClientListener for canvas.js (hop rubber-band, note resize handles, drag/pan overlays).
    Listener canvasListener = CanvasListener.getInstance();
    canvas.addListener(SWT.MouseDown, canvasListener);
    canvas.addListener(SWT.MouseMove, canvasListener);
    canvas.addListener(SWT.MouseUp, canvasListener);
    canvas.addListener(SWT.Paint, canvasListener);
    canvas.addListener(SWT.MouseWheel, canvasListener);
    canvas.addListener(SWT.MouseVerticalWheel, canvasListener);

    canvasZoomHandler = CanvasZoomHelper.createZoomHandler(this, canvas, this);
    CanvasSvgFacade.registerCanvas(canvas, this);
    CanvasSvgFacade.ensureInteractionHandler(this, canvas);
    // Before first paint: empty nodes/hops/notes so canvas.js Paint does not NPE.
    ModelGraphWebCanvasData.ensureEmptyCollections(canvas);
    if (canvasZoomHandler != null) {
      getDisplay().asyncExec(() -> CanvasZoomHelper.notifyCanvasReady(canvasZoomHandler));
    }
  }

  @Override
  public void dispose() {
    if (EnvironmentUtils.getInstance().isWeb() && canvas != null && !canvas.isDisposed()) {
      CanvasSvgFacade.unregisterCanvas(canvas);
    }
    super.dispose();
  }

  /**
   * Applies a model canvas SVG render on Hop Web: area owners, minimap geometry, snapshot publish,
   * and widget data for the client overlay.
   *
   * @param result interactive SVG render result
   * @param width canvas width in pixels
   * @param height canvas height in pixels
   * @param canvasDataMeta object passed to {@link CanvasFacade#setData} (model/document; not cast
   *     to pipeline/workflow meta under the Hop Web SPI)
   */
  protected void applyWebCanvasRender(
      ModelGraphCanvasSvgResult result, int width, int height, Object canvasDataMeta) {
    if (result == null || result.getCanvasResult() == null) {
      return;
    }
    CanvasSvgRenderResult canvasResult = result.getCanvasResult();
    replaceAreaOwners(canvasResult.getAreaOwners());
    setViewPort(canvasResult.getViewPort());
    setGraphPort(canvasResult.getGraphPort());
    lastNavigationScale = result.getNavigationScale();
    lastNavigationGraphOriginX = result.getNavigationGraphOriginX();
    lastNavigationGraphOriginY = result.getNavigationGraphOriginY();

    canvas.setData("viewPort", getViewPort());
    canvas.setData("graphPort", getGraphPort());
    ModelGraphWebCanvasData.setNodes(canvas, collectWebCanvasNodes());
    ModelGraphWebCanvasData.setNotes(canvas, collectWebCanvasNotes());
    // canvas.js Paint assumes hops is a non-null array (pipelines always set it).
    ModelGraphWebCanvasData.setHops(canvas, List.of());
    CanvasSvgFacade.publishSnapshot(
        canvas, canvasResult, magnification, offset, new Point(width, height));
    CanvasFacade.setData(canvas, magnification, offset, canvasDataMeta);
  }

  /**
   * Card/table positions for Hop Web multi-select drag previews ({@code canvas-svg.js} {@code
   * nodes}). Subclasses return name → location; default empty (single-icon drag still works from
   * area map once label owners are recognized client-side).
   */
  protected Map<String, ModelGraphWebCanvasData.NodePos> collectWebCanvasNodes() {
    return Map.of();
  }

  /**
   * Note pad positions for Hop Web note outlines and resize handles ({@code canvas.js} {@code
   * notes}).
   */
  protected List<ModelGraphWebCanvasData.NotePos> collectWebCanvasNotes() {
    List<DvNote> notes = getModelNotes();
    if (notes == null || notes.isEmpty()) {
      return List.of();
    }
    List<ModelGraphWebCanvasData.NotePos> result = new ArrayList<>();
    for (DvNote note : notes) {
      if (note == null || note.getLocation() == null) {
        continue;
      }
      result.add(
          new ModelGraphWebCanvasData.NotePos(
              note.getLocation().x,
              note.getLocation().y,
              Math.max(note.getWidth(), note.getMinimumWidth()),
              Math.max(note.getHeight(), note.getMinimumHeight()),
              note.isSelected(),
              note.getText() != null ? note.getText() : ""));
    }
    return result;
  }

  /** Fills the RAP canvas background after publishing SVG (same pattern as pipeline graphs). */
  protected void fillWebCanvasBackground(PaintEvent e, int width, int height) {
    e.gc.setBackground(GuiResource.getInstance().getColorBackground());
    e.gc.fillRectangle(0, 0, width, height);
  }

  protected void logWebCanvasRenderError(String message, Exception e) {
    LogChannel.UI.logError(message, e);
  }

  protected String getModelFilename() {
    if (this instanceof IHopFileTypeHandler fileHandler) {
      return fileHandler.getFilename();
    }
    return null;
  }

  /**
   * Default visibility of the coach panel when the user has never toggled it for this file. Shown
   * only for new/unsaved models (no filename yet) so existing and generated files open with a full
   * canvas. Source models override to always hidden until there is a clear coach use-case.
   */
  protected boolean defaultCoachPanelVisible() {
    return Utils.isEmpty(getModelFilename());
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
   * Default visibility of the load-duration (metrics) overview when the user has never toggled it
   * for this file. Off by default so opening many models stays uncluttered; users can open via the
   * toolbar.
   */
  protected boolean defaultLoadDurationPanelVisible() {
    return false;
  }

  protected void restoreLoadDurationPanelVisibility() {
    if (innerModelSash == null || canvas == null || loadDurationPane == null) {
      return;
    }
    // Second SWT chart canvas is not on the Hop Web SVG path yet — keep hidden under RAP.
    if (EnvironmentUtils.getInstance().isWeb()) {
      loadDurationPanelVisible = false;
    } else {
      loadDurationPanelVisible =
          ModelLoadDurationPaneAuditSupport.retrievePanelVisible(
              getModelFilename(), defaultLoadDurationPanelVisible());
    }
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
    if (innerModelSash == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    Composite canvasHolder = canvas.getParent();
    if (loadDurationPane == null) {
      // Web (or other builds without the metrics pane): canvas fills the inner sash.
      innerModelSash.setMaximizedControl(canvasHolder);
      innerModelSash.layout(true, true);
      return;
    }
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
   *
   * <p>On Hop Web, continuous mouse-move / wheel events are not registered (client SVG overlay +
   * zoom handler own those); hover arrives via {@link #handleWebCanvasHover}.
   */
  protected void registerCanvasMouseListeners() {
    canvas.addListener(SWT.MouseDown, this::onMouseDown);
    canvas.addListener(SWT.MouseUp, this::onMouseUp);
    canvas.addListener(SWT.MouseDoubleClick, this::onMouseDoubleClick);
    if (!EnvironmentUtils.getInstance().isWeb()) {
      canvas.addListener(SWT.MouseMove, this::onMouseMove);
      canvas.addMouseWheelListener(this::mouseScrolled);
      canvas.addListener(SWT.MouseExit, this::onMouseExit);
    }
  }

  @Override
  public void handleWebCanvasHover(int graphX, int graphY, int screenX, int screenY) {
    if (!EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    AreaOwner areaOwner = getVisibleAreaOwner(graphX, graphY);
    Point real = new Point(graphX, graphY);
    boolean interactionInProgress =
        mouseInteractions().isRelationshipDragActive()
            || selectionRegion != null
            || dragSelection
            || iconDragCommitted;
    if (interactionInProgress) {
      return;
    }
    if (mouseInteractions().updateHoverState(areaOwner, real)) {
      redraw();
    }
  }

  protected void onMouseDown(Event e) {
    if (EnvironmentUtils.getInstance().isWeb()) {
      // RAP does not stream hover/move; update underline/tooltip from click position.
      Point hover = screen2real(e.x, e.y);
      mouseInteractions().updateHoverState(getVisibleAreaOwner(hover.x, hover.y), hover);
    }
    mouseDownEvent(e);
  }

  protected void onMouseUp(Event e) {
    if (EnvironmentUtils.getInstance().isWeb()) {
      Point released = screen2real(e.x, e.y);
      // RAP does not deliver move-while-button-down. Apply the final drag/lasso geometry only when
      // the pointer actually moved; a still click must not be treated as a completed drag (that
      // would swallow the table context dialog). Keep lastButton so mouseMoveEvent still treats
      // the gesture as button-held (stateMask on mouse-up no longer has BUTTON1).
      if (!isUnmovedClick(released)) {
        if (iconDragCommitted || dragSelection) {
          markPositionUndoPoint();
        }
        mouseMoveEvent(e);
      }
    }
    mouseUpEvent(e);
    lastButton = 0;
    if (EnvironmentUtils.getInstance().isWeb()
        && canvas != null
        && !canvas.isDisposed()
        && !mouseInteractions().isRelationshipDragActive()) {
      ModelGraphWebCanvasData.clearMode(canvas);
    }
  }

  protected void onMouseMove(Event e) {
    mouseMoveEvent(e);
  }

  protected void mouseDownEvent(Event e) {
    lastButton = e.button;
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
      armWebObjectDragModes(e, shift, control);
      return;
    }

    if (handleNoteMouseDown(e, real, hit.note(), hit.areaOwner(), control)) {
      armWebNoteDragModes(e);
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

  /**
   * Hop Web: arm icon drag / relationship hop mode on mouse-down (no move-while-held). Mirrors
   * {@code HopGuiPipelineGraph} web behaviour.
   */
  protected void armWebObjectDragModes(Event e, boolean shift, boolean control) {
    if (!EnvironmentUtils.getInstance().isWeb() || canvas == null || canvas.isDisposed()) {
      return;
    }
    if (mouseInteractions().isRelationshipDragActive()) {
      String startName = mouseInteractions().webRelationshipStartNodeName();
      canvas.setData("mode", "hop");
      canvas.setData("startHopNode", startName);
      ModelGraphWebCanvasData.setNodes(canvas, collectWebCanvasNodes());
      // Sync mode/nodes to client before mouse moves (canvas.js hop rubber-band).
      redraw();
      return;
    }
    if (e.button == 1 && !shift && !control && iconDragStart != null && !iconDragCommitted) {
      iconDragCommitted = true;
      dragSelection = true;
      canvas.setData("mode", "drag");
      // Undo is recorded on mouse-up only if the pointer actually moved.
      ModelGraphWebCanvasData.setNodes(canvas, collectWebCanvasNodes());
      ModelGraphWebCanvasData.setNotes(canvas, collectWebCanvasNotes());
      redraw();
    }
  }

  /** Hop Web: arm note drag or resize mode for client feedback. */
  protected void armWebNoteDragModes(Event e) {
    if (!EnvironmentUtils.getInstance().isWeb() || canvas == null || canvas.isDisposed()) {
      return;
    }
    if (e.button != 1) {
      return;
    }
    ModelGraphWebCanvasData.setNotes(canvas, collectWebCanvasNotes());
    if (resize != null) {
      canvas.setData("mode", "resize");
      canvas.setData("resizeDirection", resize.name());
    } else if (noteDragStart != null) {
      canvas.setData("mode", "drag");
      canvas.setData("resizeDirection", null);
    }
    // Force immediate client sync of mode / notes / resizeDirection.
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

    // RAP: stateMask often lacks BUTTON1 during move/up; lastButton tracks the gesture.
    boolean leftButtonDown = (e.stateMask & SWT.BUTTON1) != 0 || lastButton == 1;

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

      // Hop Web arms iconDragCommitted on mouse-down. A still click is not a completed drag.
      if (!isUnmovedClick(real) && mouseInteractions().handleCommittedDragMouseUp(e)) {
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

  /**
   * True when mouse-up landed on the same graph point as mouse-down (within {@link
   * #ICON_DRAG_THRESHOLD_PX}). Distinguishes a left-click (context dialog) from a drag. Required on
   * Hop Web because {@link #armWebObjectDragModes} sets {@code iconDragCommitted} on mouse-down.
   */
  protected boolean isUnmovedClick(Point real) {
    return ModelGraphClickSupport.isUnmovedClick(lastClick, real, ICON_DRAG_THRESHOLD_PX);
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
      if (EnvironmentUtils.getInstance().isWeb() && canvas != null && !canvas.isDisposed()) {
        ModelGraphWebCanvasData.clearMode(canvas);
        canvas.setData("panStartOffset", null);
        canvas.setData("panCurrentOffset", null);
        canvas.setData("panBoundaries", null);
        // Full SVG repaint at normal opacity (client wireframe + dim ends with mode=null).
        redraw();
      }
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
      if (canvas != null && !canvas.isDisposed()) {
        canvas.setData("mode", "drag");
      }
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
    // Hop Web draws 8px resize handles on the note border; use a wider hit margin than desktop's
    // 4px
    // so edge/handle clicks resize instead of starting a background lasso.
    resize = getNoteResize(areaOwner.getArea(), real);
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

  /**
   * Note resize hit-test. On Hop Web, canvas.js paints handles around the border; a larger margin
   * keeps those clicks from falling through as background lasso.
   */
  protected Resize getNoteResize(Rectangle rectangle, Point point) {
    if (rectangle == null || point == null) {
      return null;
    }
    int margin = EnvironmentUtils.getInstance().isWeb() ? 10 : 4;
    if (point.x <= rectangle.x + margin) {
      if (point.y <= rectangle.y + margin) {
        return Resize.NORTH_WEST;
      }
      if (point.y >= rectangle.y + rectangle.height - margin) {
        return Resize.SOUTH_WEST;
      }
      return Resize.WEST;
    }
    if (point.x >= rectangle.x + rectangle.width - margin) {
      if (point.y <= rectangle.y + margin) {
        return Resize.NORTH_EAST;
      }
      if (point.y >= rectangle.y + rectangle.height - margin) {
        return Resize.SOUTH_EAST;
      }
      return Resize.EAST;
    }
    if (point.y <= rectangle.y + margin) {
      return Resize.NORTH;
    }
    if (point.y >= rectangle.y + rectangle.height - margin) {
      return Resize.SOUTH;
    }
    return null;
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
            org.hopper.edw.datavault.hopgui.file.vault.DvNoteDialog.class, "DvNoteDialog.Title");
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
