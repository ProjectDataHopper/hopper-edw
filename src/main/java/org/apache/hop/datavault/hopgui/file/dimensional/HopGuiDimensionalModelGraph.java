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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SnapAllignDistribute;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.ModelGeneratedArtifactOpenSupport;
import org.apache.hop.datavault.hopgui.ModelTableLayoutPreviewSupport;
import org.apache.hop.datavault.hopgui.ModelUpdateActionAuditSupport;
import org.apache.hop.datavault.hopgui.ModelUpdateWorkflowClipboardSupport;
import org.apache.hop.datavault.hopgui.ai.DmAiAdvisorDialog;
import org.apache.hop.datavault.hopgui.coaching.ICoachableModelGraph;
import org.apache.hop.datavault.hopgui.file.dimensional.delegates.HopGuiDimensionalClipboardDelegate;
import org.apache.hop.datavault.hopgui.file.dimensional.delegates.HopGuiDimensionalSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.metadata.DvDdlSupport;
import org.apache.hop.datavault.metadata.DvIntegerSettingValidationSupport;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.DvUpdateWorkflowSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.DmCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmAccumulatingSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmAggregateFact;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.datavault.metadata.dimensional.DmDateDimensionTemplate;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.apache.hop.datavault.metadata.dimensional.DmLayoutSupport;
import org.apache.hop.datavault.metadata.dimensional.DmPeriodicSnapshotFact;
import org.apache.hop.datavault.metadata.dimensional.DmRangeDimension;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.apache.hop.datavault.metadata.dimensional.IDmFactLikeTable;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.apache.hop.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.CanvasFacade;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

/** Hop GUI editor for Kimball dimensional models (dimensions, facts, and notes). */
@GuiPlugin(
    id = "HopGuiDimensionalModelGraph",
    description = "i18n::HopGuiDimensionalModelGraph.Description")
@Getter
@Setter
public class HopGuiDimensionalModelGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher, ICoachableModelGraph {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiDimensionalModelGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiDimensionalModelGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN =
      "HopGuiDimensionalModelGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiDimensionalModelGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiDimensionalModelGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiDimensionalModelGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_EDIT_MODEL =
      "HopGuiDimensionalModelGraph-ToolBar-10050-Edit-Model";
  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiDimensionalModelGraph-ToolBar-10060-Check-Model";
  public static final String TOOLBAR_ITEM_AI_HELP =
      "HopGuiDimensionalModelGraph-ToolBar-10065-AI-Help";
  public static final String TOOLBAR_ITEM_DEBUG = "HopGuiDimensionalModelGraph-ToolBar-10070-Debug";
  public static final String TOOLBAR_ITEM_GENERATE_DDL =
      "HopGuiDimensionalModelGraph-ToolBar-10080-Generate-Ddl";
  public static final String TOOLBAR_ITEM_TOGGLE_COACH =
      "HopGuiDimensionalModelGraph-ToolBar-10084-Toggle-Coach";
  public static final String TOOLBAR_ITEM_TOGGLE_DURATIONS =
      "HopGuiDimensionalModelGraph-ToolBar-10086-Toggle-Durations";
  public static final String TOOLBAR_ITEM_SELECT_ALL =
      "HopGuiDimensionalModelGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiDimensionalModelGraph-ToolBar-20020-Unselect-All";
  public static final String TOOLBAR_ITEM_COPY = "HopGuiDimensionalModelGraph-ToolBar-20030-Copy";
  public static final String TOOLBAR_ITEM_CUT = "HopGuiDimensionalModelGraph-ToolBar-20040-Cut";
  public static final String TOOLBAR_ITEM_PASTE = "HopGuiDimensionalModelGraph-ToolBar-20050-Paste";
  public static final String TOOLBAR_ITEM_DELETE =
      "HopGuiDimensionalModelGraph-ToolBar-20060-Delete";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiDimensionalModelGraph-ToolBar-20070-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiDimensionalModelGraph-ToolBar-20080-Redo";

  private static final String DIMENSIONAL_UPDATE_AUDIT_GROUP = "Dimensional";
  private static final String DIMENSIONAL_UPDATE_AUDIT_TYPE = "DimensionalUpdateAction";

  private final HopDimensionalFileType fileType;
  private final HopGuiDimensionalClipboardDelegate clipboardDelegate;
  private final HopGuiDimensionalSnapshotUndo snapshotUndo = new HopGuiDimensionalSnapshotUndo();
  private DimensionalModel model;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private boolean changed = false;
  private String filename;

  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;
  private IDmTable currentTable;
  private IDmTable startRelationshipTable;
  private Point relationshipDragEndLocation;
  private IDmTable candidateRelationshipTarget;

  public HopGuiDimensionalModelGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      DimensionalModel model,
      HopDimensionalFileType fileType) {
    super(hopGui, parent, perspective);
    this.model = model;
    this.fileType = fileType;
    this.clipboardDelegate = new HopGuiDimensionalClipboardDelegate(hopGui, this);

    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());

    if (model == null) {
      return;
    }

    setLayout(new FormLayout());
    addToolBar();

    createModelGraphBody(toolBar, () -> canvas.addPaintListener(this::paintControl));

    hopGui.replaceKeyboardShortcutListeners(this);
    canvas.setFocus();
    setZoomLabel();
    layout(true, true);
  }

  private void addToolBar() {
    try {
      IToolbarContainer toolBarContainer =
          ToolbarFacade.createToolbarContainer(this, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
      toolBar = toolBarContainer.getControl();
      toolBarWidgets = new GuiToolbarWidgets();
      toolBarWidgets.registerGuiPluginObject(this);
      toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
      FormData layoutData = new FormData();
      layoutData.left = new FormAttachment(0, 0);
      layoutData.top = new FormAttachment(0, 0);
      layoutData.right = new FormAttachment(100, 0);
      toolBar.setLayoutData(layoutData);
      toolBar.pack();
      PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);
      updateGui();
    } catch (Exception e) {
      hopGui.getLog().logError("Error setting up the toolbar for HopGuiDimensionalModelGraph: ", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
      return;
    }

    if (EnvironmentUtils.getInstance().isWeb()) {
      drawDimensionalModelImageWeb(area.x, area.y);
      fillWebCanvasBackground(e, area.x, area.y);
      return;
    }

    boolean needsDoubleBuffering =
        Const.isWindows() && "GUI".equalsIgnoreCase(Const.getHopPlatformRuntime());

    Image image = null;
    GC swtGc = e.gc;

    if (needsDoubleBuffering) {
      image = new Image(hopGui.getDisplay(), area.x, area.y);
      swtGc = new GC(image);
    }

    drawDimensionalModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawDimensionalModelImageWeb(int width, int height) {
    try {
      ModelGraphCanvasSvgResult result =
          DimensionalModelCanvasSvgRenderer.render(buildDimensionalSvgContext(width, height));
      applyWebCanvasRender(result, width, height, model);
    } catch (Exception ex) {
      logWebCanvasRenderError("Failed to render dimensional model SVG for Hop Web", ex);
    }
  }

  private DimensionalModelCanvasSvgRenderer.Context buildDimensionalSvgContext(
      int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    maximum = model.getMaximum();
    DimensionalModelCanvasSvgRenderer.Context ctx = new DimensionalModelCanvasSvgRenderer.Context();
    ctx.variables = variables;
    ctx.model = model;
    ctx.canvasSize = new Point(width, height);
    ctx.offset = offset;
    ctx.selectionRegion = selectionRegion;
    ctx.iconSize = propsUi.getIconSize();
    ctx.gridSize = propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1;
    ctx.magnification = (float) (magnification * PropsUi.getNativeZoomFactor());
    ctx.screenMagnification = magnification;
    ctx.zoomFactor = propsUi.getZoomFactor();
    ctx.maximum = maximum;
    ctx.mouseOverTableName = mouseOverTableName;
    ctx.mouseOverNoteLink = mouseOverNoteLink;
    ctx.noteImageBaseFilename = getFilename();
    ctx.showingNavigationView = !propsUi.isHideViewportEnabled();
    ctx.metadataProvider = hopGui.getMetadataProvider();
    ctx.startRelationshipTable = startRelationshipTable;
    ctx.relationshipDragEndLocation = relationshipDragEndLocation;
    ctx.candidateRelationshipTarget = candidateRelationshipTarget;
    return ctx;
  }

  private void drawDimensionalModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    maximum = model.getMaximum();

    try {
      areaOwners.clear();
      DimensionalModelPainter painter =
          new DimensionalModelPainter(model, gc, variables, width, height);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification((float) (magnification * PropsUi.getNativeZoomFactor()));
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setMetadataProvider(hopGui.getMetadataProvider());
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverTableName(mouseOverTableName);
      painter.setMouseOverNoteLink(mouseOverNoteLink);
      painter.setNoteImageBaseFilename(getFilename());
      painter.setSelectionRegion(selectionRegion);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.setRelationshipDragInfo(
          startRelationshipTable, relationshipDragEndLocation, candidateRelationshipTarget);
      painter.drawDimensionalModel(hopGui.getMetadataProvider());

      captureNavigationViewGeometry(painter);

      CanvasFacade.setData(canvas, magnification, offset, model);
    } finally {
      gc.dispose();
    }
  }

  @Override
  public void replaceAreaOwners(List<AreaOwner> owners) {
    areaOwners.clear();
    if (owners != null) {
      areaOwners.addAll(owners);
    }
  }

  @Override
  protected Map<String, ModelGraphWebCanvasData.NodePos> collectWebCanvasNodes() {
    Map<String, ModelGraphWebCanvasData.NodePos> nodes = new HashMap<>();
    if (model == null || model.getTables() == null) {
      return nodes;
    }
    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName()) || table.getLocation() == null) {
        continue;
      }
      int w = ModelGraphWebCanvasData.DEFAULT_CARD_WIDTH;
      int h = ModelGraphWebCanvasData.DEFAULT_CARD_HEIGHT;
      if (table instanceof DmTableBase base) {
        if (base.getDrawnBoxWidth() > 0) {
          w = base.getDrawnBoxWidth();
        }
        if (base.getDrawnBoxHeight() > 0) {
          h = base.getDrawnBoxHeight();
        }
      }
      nodes.put(
          table.getName(),
          ModelGraphWebCanvasData.NodePos.of(
              table.getLocation().x, table.getLocation().y, table.isSelected(), w, h));
    }
    return nodes;
  }

  @Override
  public void redraw() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  public static HopGuiDimensionalModelGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective explorerPerspective) {
      if (explorerPerspective.getActiveFileTypeHandler()
          instanceof HopGuiDimensionalModelGraph graph) {
        return graph;
      }
    }
    return null;
  }

  public void runUndoableModelChange(DmModelChange change) throws HopException {
    byte[] beforeChange = captureUndoSnapshot();
    change.run();
    commitDialogUndo(beforeChange);
    setChanged();
    redraw();
  }

  @FunctionalInterface
  public interface DmModelChange {
    void run() throws HopException;
  }

  private void editDmTable(IDmTable table) {
    if (table == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted =
        table instanceof DmRangeDimension rangeDimension
            ? new HopGuiDmRangeDimensionDialog(
                    getShell(), rangeDimension, model, variables, hopGui.getMetadataProvider())
                .open()
            : new HopGuiDmTableDialog(
                    getShell(), table, model, variables, hopGui.getMetadataProvider())
                .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new DimensionalMouseInteractions();
  }

  @Override
  protected String getMetricsModelName() {
    return model != null ? model.getName() : null;
  }

  @Override
  protected String getMetricsModelType() {
    return GeneratedPipelineMetadataConstants.MODEL_TYPE_DM;
  }

  @Override
  protected List<String> getMetricsTableNames() {
    if (model == null || model.getTables() == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names;
  }

  private @Nullable IDmTable getAreaOwnerTable(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof IDmTable table) {
      return table;
    }
    return null;
  }

  private void centerOnTable(IDmTable table) {
    if (table == null) {
      return;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = 140;
    int boxH = 70;
    if (table instanceof DmTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        boxW = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        boxH = base.getDrawnBoxHeight();
      }
    }
    centerOnCanvasLocation(loc, boxW, boxH);
  }

  private void moveSelectedObjects(int dx, int dy) {
    List<IDmTable> selectedTables = getSelectedTables();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selectedTables.isEmpty() && selectedNotes.isEmpty()) {
      return;
    }
    for (IDmTable table : selectedTables) {
      Point loc = table.getLocation();
      if (loc.x + dx < 0) {
        dx = -loc.x;
      }
      if (loc.y + dy < 0) {
        dy = -loc.y;
      }
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      if (loc.x + dx < 0) {
        dx = -loc.x;
      }
      if (loc.y + dy < 0) {
        dy = -loc.y;
      }
    }
    for (IDmTable table : selectedTables) {
      Point loc = table.getLocation();
      PropsUi.setLocation(table, loc.x + dx, loc.y + dy);
      table.setChanged();
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      PropsUi.setLocation(note, loc.x + dx, loc.y + dy);
    }
  }

  @Override
  protected void updateGraphAfterNavigationPan() {
    updateGui();
  }

  private void clearTableDragState() {
    positionChangeUndoMarked = false;
    iconDragStart = null;
    iconDragCommitted = false;
    dragSelection = false;
    iconOffset = null;
    currentTable = null;
    clearNavigationViewportState();
    clearSelectionRegion();
  }

  private IDmTable findTableAtScreen(int screenX, int screenY) {
    Point real = screen2real(screenX, screenY);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    if (areaOwner == null || areaOwner.getAreaType() != AreaOwner.AreaType.TRANSFORM_ICON) {
      return null;
    }
    return getAreaOwnerTable(areaOwner);
  }

  private void cancelRelationshipDrag() {
    startRelationshipTable = null;
    relationshipDragEndLocation = null;
    candidateRelationshipTarget = null;
  }

  private static boolean isRegularDimensionLike(IDmTable table) {
    return table instanceof DmDimension || table instanceof DmDimensionAlias;
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

  private boolean isValidRelationshipPair(IDmTable a, IDmTable b) {
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
    if (a instanceof DmDimension && b instanceof DmDimension) {
      return true;
    }
    return (a instanceof DmDimensionAlias && b instanceof DmDimension)
        || (b instanceof DmDimensionAlias && a instanceof DmDimension);
  }

  private void createRelationship(IDmTable from, IDmTable to) {
    if (from == null || to == null || from == to || !isValidRelationshipPair(from, to)) {
      return;
    }

    byte[] beforeChange = captureUndoSnapshot();
    boolean modelChanged = false;

    if (isFactLikeTable(from) && isJunkDimension(to)) {
      modelChanged = addFactJunkRelationship((IDmFactLikeTable) from, (DmJunkDimension) to);
    } else if (isFactLikeTable(to) && isJunkDimension(from)) {
      modelChanged = addFactJunkRelationship((IDmFactLikeTable) to, (DmJunkDimension) from);
    } else if (isFactLikeTable(from) && isRangeDimension(to)) {
      modelChanged = addFactRangeRelationship((IDmFactLikeTable) from, (DmRangeDimension) to);
    } else if (isFactLikeTable(to) && isRangeDimension(from)) {
      modelChanged = addFactRangeRelationship((IDmFactLikeTable) to, (DmRangeDimension) from);
    } else if (isFactLikeTable(from) && isRegularDimensionLike(to)) {
      modelChanged = addFactDimensionRole((IDmFactLikeTable) from, to);
    } else if (isFactLikeTable(to) && isRegularDimensionLike(from)) {
      modelChanged = addFactDimensionRole((IDmFactLikeTable) to, from);
    } else if (isBridgeTable(from) && isRegularDimensionLike(to)) {
      modelChanged = addBridgeDimensionRef((DmBridge) from, to);
    } else if (isBridgeTable(to) && isRegularDimensionLike(from)) {
      modelChanged = addBridgeDimensionRef((DmBridge) to, from);
    } else if (from instanceof DmDimension parent && to instanceof DmDimension outrigger) {
      modelChanged = addOutriggerRef(parent, outrigger);
    } else if (from instanceof DmDimensionAlias alias && to instanceof DmDimension dimension) {
      modelChanged = setAliasReferencedDimension(alias, dimension);
    } else if (from instanceof DmDimension dimension && to instanceof DmDimensionAlias alias) {
      modelChanged = setAliasReferencedDimension(alias, dimension);
    }

    if (modelChanged) {
      commitDialogUndo(beforeChange);
      setChanged();
    }
  }

  private boolean addFactRangeRelationship(IDmFactLikeTable fact, DmRangeDimension range) {
    if (fact == null || range == null || Utils.isEmpty(range.getName())) {
      return false;
    }
    if (hasExistingRangeRole(fact, range.getName())) {
      return false;
    }
    DmFactRangeDimensionRole role = new DmFactRangeDimensionRole(range.getName(), "", "");
    return appendFactRangeDimensionRole(fact, role);
  }

  private boolean hasExistingRangeRole(IDmFactLikeTable fact, String rangeDimensionName) {
    for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
      if (role != null && rangeDimensionName.equals(role.getRangeDimensionTableName())) {
        return true;
      }
    }
    return false;
  }

  private boolean appendFactRangeDimensionRole(
      IDmFactLikeTable fact, DmFactRangeDimensionRole role) {
    if (fact instanceof DmFact dmFact) {
      dmFact.getRangeDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmFactlessFact factlessFact) {
      factlessFact.getRangeDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmPeriodicSnapshotFact periodicFact) {
      periodicFact.getRangeDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAccumulatingSnapshotFact accumulatingFact) {
      accumulatingFact.getRangeDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAggregateFact aggregateFact) {
      aggregateFact.getRangeDimensionRoles().add(role);
      return true;
    }
    return false;
  }

  private boolean addFactJunkRelationship(IDmFactLikeTable fact, DmJunkDimension junk) {
    if (fact == null || junk == null || Utils.isEmpty(junk.getName())) {
      return false;
    }
    if (hasExistingJunkRole(fact, junk.getName())) {
      return false;
    }
    String fkColumn = DmJunkDimensionSupport.defaultForeignKeyForJunk(junk);
    DmFactJunkDimensionRole role = new DmFactJunkDimensionRole(junk.getName(), fkColumn);
    if (!appendFactJunkDimensionRole(fact, role)) {
      return false;
    }
    DmJunkDimensionSupport.applyFactTableSource(junk, fact.getName());
    return true;
  }

  private boolean addFactDimensionRole(IDmFactLikeTable fact, IDmTable dimension) {
    if (fact == null || dimension == null || Utils.isEmpty(dimension.getName())) {
      return false;
    }
    if (hasExistingFactRole(fact, dimension.getName(), defaultForeignKeyForDimension(dimension))) {
      return false;
    }
    String fkColumn = defaultForeignKeyForDimension(dimension);
    DmFactDimensionRole role = new DmFactDimensionRole(dimension.getName(), null, fkColumn);
    applySourceHashKeyJoinDefaults(role, dimension);
    return appendFactDimensionRole(fact, role);
  }

  private boolean hasExistingJunkRole(IDmFactLikeTable fact, String junkDimensionName) {
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role != null && junkDimensionName.equals(role.getJunkDimensionTableName())) {
        return true;
      }
    }
    return false;
  }

  private void applySourceHashKeyJoinDefaults(DmFactDimensionRole role, IDmTable dimension) {
    if (role == null || dimension == null || model == null || Utils.isEmpty(dimension.getName())) {
      return;
    }
    DmDimension resolved =
        DmDimensionResolutionSupport.resolveDimension(
            model, dimension.getName(), variables, hopGui.getMetadataProvider());
    if (resolved == null
        || DmSurrogateKeySupport.resolveStrategy(resolved)
            != DmSurrogateKeyStrategy.USE_SOURCE_FIELD) {
      return;
    }
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    String surrogateSource =
        DmSurrogateKeySupport.resolveSurrogateKeySourceField(resolved, config, variables);
    if (!Utils.isEmpty(surrogateSource)) {
      role.setSourceFieldName(surrogateSource);
    }
  }

  private boolean hasExistingFactRole(
      IDmFactLikeTable fact, String dimensionName, String foreignKeyColumn) {
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      if (!dimensionName.equals(role.getDimensionTableName())) {
        continue;
      }
      String existingFk = role.getForeignKeyColumn();
      if (Utils.isEmpty(existingFk) || Utils.isEmpty(foreignKeyColumn)) {
        return true;
      }
      if (existingFk.equals(foreignKeyColumn)) {
        return true;
      }
    }
    return false;
  }

  private boolean setAliasReferencedDimension(DmDimensionAlias alias, DmDimension dimension) {
    if (alias == null || dimension == null || Utils.isEmpty(dimension.getName())) {
      return false;
    }
    if (dimension.getName().equals(alias.getName())) {
      return false;
    }
    alias.setReferencedDimensionName(dimension.getName());
    alias.syncPhysicalTableName(model, variables);
    return true;
  }

  private boolean appendFactDimensionRole(IDmFactLikeTable fact, DmFactDimensionRole role) {
    if (fact instanceof DmFact dmFact) {
      dmFact.getDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmFactlessFact factlessFact) {
      factlessFact.getDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmPeriodicSnapshotFact periodicFact) {
      periodicFact.getDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAccumulatingSnapshotFact accumulatingFact) {
      accumulatingFact.getDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAggregateFact aggregateFact) {
      aggregateFact.getDimensionRoles().add(role);
      return true;
    }
    return false;
  }

  private boolean appendFactJunkDimensionRole(IDmFactLikeTable fact, DmFactJunkDimensionRole role) {
    if (fact instanceof DmFact dmFact) {
      dmFact.getJunkDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmFactlessFact factlessFact) {
      factlessFact.getJunkDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmPeriodicSnapshotFact periodicFact) {
      periodicFact.getJunkDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAccumulatingSnapshotFact accumulatingFact) {
      accumulatingFact.getJunkDimensionRoles().add(role);
      return true;
    }
    if (fact instanceof DmAggregateFact aggregateFact) {
      aggregateFact.getJunkDimensionRoles().add(role);
      return true;
    }
    return false;
  }

  private boolean addBridgeDimensionRef(DmBridge bridge, IDmTable dimension) {
    if (bridge == null || dimension == null || Utils.isEmpty(dimension.getName())) {
      return false;
    }
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref != null && dimension.getName().equals(ref.getDimensionTableName())) {
        return false;
      }
    }
    bridge
        .getDimensionRefs()
        .add(
            new DmBridgeDimensionRef(
                dimension.getName(), defaultForeignKeyForDimension(dimension)));
    return true;
  }

  private boolean addOutriggerRef(DmDimension parent, DmDimension outrigger) {
    if (parent == null || outrigger == null || Utils.isEmpty(outrigger.getName())) {
      return false;
    }
    for (DmDimensionOutriggerRef ref : parent.getOutriggersOrEmpty()) {
      if (ref != null && outrigger.getName().equals(ref.getDimensionTableName())) {
        return false;
      }
    }
    parent
        .getOutriggers()
        .add(
            new DmDimensionOutriggerRef(
                outrigger.getName(), defaultForeignKeyForDimension(outrigger)));
    return true;
  }

  private String defaultForeignKeyForDimension(IDmTable dimension) {
    if (dimension == null || Utils.isEmpty(dimension.getName())) {
      return null;
    }
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    if (dimension instanceof DmDimension dmDimension) {
      DmFactDimensionRole tempRole = new DmFactDimensionRole(dimension.getName(), null, null);
      return DmLayoutSupport.defaultFactForeignKeyColumn(dmDimension, tempRole, config, variables);
    }
    if (dimension instanceof DmDimensionAlias alias) {
      DmDimension resolved =
          DmDimensionResolutionSupport.resolveDimension(model, alias.getName(), variables);
      if (resolved != null) {
        DmFactDimensionRole tempRole = new DmFactDimensionRole(alias.getName(), null, null);
        return DmLayoutSupport.defaultFactForeignKeyColumn(resolved, tempRole, config, variables);
      }
    }
    String base = dimension.getName();
    if (base.startsWith("dim_")) {
      base = base.substring(4);
    }
    return base + "_key";
  }

  @Override
  protected AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  private boolean isTableInLassoScreenRect(
      IDmTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null) {
      return false;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = 140;
    int th = 70;
    if (table instanceof DmTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        tw = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        th = base.getDrawnBoxHeight();
      }
    }
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + Math.max(1, tw);
    int tMaxY = tMinY + Math.max(1, th);
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private List<IDmTable> getSelectedTables() {
    List<IDmTable> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        selected.add(table);
      }
    }
    return selected;
  }

  private String getUniqueTableName(String base) {
    if (model == null) {
      return base;
    }
    int num = 1;
    String candidate;
    do {
      candidate = base + " " + num;
      num++;
    } while (model.findTable(candidate) != null);
    return candidate;
  }

  private void addTableAtClick(IDmTable table, Point click) {
    if (model == null || table == null) {
      return;
    }
    String name = getUniqueTableName(table.getTableType().name());
    table.setName(name);
    table.setTableName(name.toLowerCase().replace(' ', '_'));
    PropsUi.setLocation(table, click != null ? click.x : 50, click != null ? click.y : 50);
    model.getTables().add(table);
    editDmTable(table);
  }

  private void showDimensionalContextDialog(Event e, Point real) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Background.Message");
      IGuiContextHandler contextHandler = new HopGuiDimensionalContext(model, this, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showTableContextDialog(Event e, IDmTable table) {
    if (table == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.Context.Table.Message", table.getName());
      IGuiContextHandler contextHandler =
          new HopGuiDimensionalTableContext(model, this, table, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  @GuiContextAction(
      id = "dm-graph-ai-help",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.AiHelp.Tooltip",
      image = "datavault-ai-help.svg",
      category = "Help",
      categoryOrder = "2")
  public void openAiAdvisorContext(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.openAiAdvisor();
    }
  }

  @GuiContextAction(
      id = "dm-graph-import-database-tables",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.ImportDatabaseTables.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.ImportDatabaseTables.Tooltip",
      image = "ui/images/database.svg",
      category = "Import",
      categoryOrder = "1")
  public void importDatabaseTables(HopGuiDimensionalContext context) {
    DimensionalModel dmModel = context.getModel();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (dmModel == null || graph == null) {
      return;
    }
    graph.markUndoPoint();
    HopGuiDmDatabaseImportSupport.importDatabaseTables(
        hopGui,
        dmModel,
        () -> {
          graph.setChanged();
          graph.redraw();
        });
  }

  @GuiContextAction(
      id = "dm-graph-add-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Tooltip",
      image = "dimension.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmDimension(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-dimension-alias",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDimensionAlias.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDimensionAlias.Tooltip",
      image = "dimension-alias.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDimensionAlias(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("dim_alias");
    graph.addTableAtClick(alias, context.getClick());
    graph.editDmTable(alias);
  }

  @GuiContextAction(
      id = "dm-graph-add-date-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDateDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDateDimension.Tooltip",
      image = "dimension.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDateDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (graph == null || dmModel == null) {
      return;
    }
    if (DmDateDimensionTemplate.isDateDimensionPresent(dmModel)) {
      MessageBox box = new MessageBox(graph.getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.DateDimension.Exists.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.DateDimension.Exists.Message"));
      box.open();
      return;
    }
    graph.markUndoPoint();
    Point click = context.getClick();
    String loadDateField =
        dmModel.getConfigurationOrDefault().resolveLoadDateField(graph.getVariables());
    DmDimension dateDimension =
        DmDateDimensionTemplate.createDateDimension(
            click != null ? click : new Point(50, 50), loadDateField);
    graph.addTableAtClick(dateDimension, click);
    MessageBox box = new MessageBox(graph.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
    box.setText(
        BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.DateDimension.Aliases.Title"));
    box.setMessage(
        BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.DateDimension.Aliases.Message"));
    if (box.open() == SWT.YES) {
      int baseX = dateDimension.getLocation().x + 220;
      int baseY = dateDimension.getLocation().y;
      for (DmDimensionAlias alias :
          DmDateDimensionTemplate.createCommonDateAliases(
              dateDimension.getName(), new Point(baseX, baseY))) {
        dmModel.getTables().add(alias);
      }
      graph.setChanged();
      graph.redraw();
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-fact",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Tooltip",
      image = "fact.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void addFact(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmFact(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-junk-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddJunkDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddJunkDimension.Tooltip",
      image = "dimension-junk.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addJunkDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmJunkDimension(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-range-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddRangeDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddRangeDimension.Tooltip",
      image = "dimension-range.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addRangeDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmRangeDimension(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-factless-fact",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddFactlessFact.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddFactlessFact.Tooltip",
      image = "fact.svg",
      category = "Dimensional",
      categoryOrder = "4")
  public void addFactlessFact(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmFactlessFact(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-bridge",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddBridge.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddBridge.Tooltip",
      image = "bridge.svg",
      category = "Dimensional",
      categoryOrder = "5")
  public void addBridge(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableAtClick(new DmBridge(), context.getClick());
  }

  @GuiContextAction(
      id = "dm-graph-add-note",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addNote(HopGuiDimensionalContext context) {
    Point click = context.getClick();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (dmModel == null || graph == null) {
      return;
    }
    graph.markUndoPoint();
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    PropsUi.setLocation(note, click != null ? click.x : 50, click != null ? click.y : 50);
    PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
    dmModel.getNotes().add(note);
    graph.editNote(note, false);
    graph.setChanged();
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "6")
  public void pasteFromClipboard(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "6")
  public void pasteFromClipboardOnTable(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void pasteFromClipboardOnNote(HopGuiDimensionalNoteContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "dm-graph-copy-update-action",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "1")
  public void copyUpdateActionToClipboard(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.copyDimensionalUpdateActionToClipboard();
    }
  }

  @GuiContextAction(
      id = "dm-graph-edit-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.EditNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void editNoteAction(HopGuiDimensionalNoteContext context) {
    DvNote note = context.getNote();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (note != null && graph != null) {
      graph.editNote(note);
    }
  }

  @GuiContextAction(
      id = "dm-graph-delete-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiDimensionalNoteContext context) {
    DvNote note = context.getNote();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (note != null && dmModel != null && graph != null) {
      graph.markUndoPoint();
      if (dmModel.getNotes().remove(note)) {
        graph.setChanged();
        graph.redraw();
      }
    }
  }

  private static final String ACTION_ID_GO_TO_ALIASED_DIMENSION =
      "dm-graph-go-to-aliased-dimension";
  private static final String ACTION_ID_OPEN_SOURCE_PIPELINE = "dm-graph-open-source-pipeline";

  @GuiContextAction(
      id = "dm-graph-edit-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.EditTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.EditTable.Tooltip",
      image = "ui/images/edit.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void editTableAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (graph != null) {
      graph.editDmTable(context.getTable());
    }
  }

  @GuiContextAction(
      id = ACTION_ID_OPEN_SOURCE_PIPELINE,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.OpenSourcePipeline.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.OpenSourcePipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void openSourcePipelineAction(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    if (table != null) {
      DmSourcePipelineOpenSupport.openSourcePipeline(hopGui, table, getVariables());
    }
  }

  @GuiContextAction(
      id = ACTION_ID_GO_TO_ALIASED_DIMENSION,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.GoToAliasedDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.GoToAliasedDimension.Tooltip",
      image = "ui/images/location.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void goToAliasedDimensionAction(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (!(table instanceof DmDimensionAlias alias) || graph == null || dmModel == null) {
      return;
    }
    try {
      DmDimensionAliasNavigationSupport.navigateToSourceDimension(
          hopGui, dmModel, graph, alias, getVariables(), hopGui.getMetadataProvider());
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.GoToAliasedDimension.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  @GuiContextActionFilter(parentId = HopGuiDimensionalTableContext.CONTEXT_ID)
  public boolean filterTableContextActions(
      String contextActionId, HopGuiDimensionalTableContext context) {
    if (ACTION_ID_GO_TO_ALIASED_DIMENSION.equals(contextActionId)) {
      IDmTable table = context.getTable();
      if (!(table instanceof DmDimensionAlias alias)) {
        return false;
      }
      return DmDimensionAliasNavigationSupport.canNavigateToSourceDimension(
          context.getModel(), alias, getVariables(), hopGui.getMetadataProvider());
    }
    if (ACTION_ID_OPEN_SOURCE_PIPELINE.equals(contextActionId)) {
      return DmSourcePipelineOpenSupport.canOpenSourcePipeline(context.getTable(), getVariables());
    }
    return true;
  }

  @GuiContextAction(
      id = "dm-graph-delete-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "4")
  public void deleteTable(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    DimensionalModel dmModel = context.getModel();
    if (table == null || graph == null || dmModel == null) {
      return;
    }
    graph.markUndoPoint();
    String tableName = table.getName();
    dmModel.getTables().removeIf(t -> t != null && tableName.equals(t.getName()));
    dmModel.removeConformedDimensionRefsForTable(tableName);
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "dm-graph-show-update-pipeline",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.ShowUpdatePipeline.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.ShowUpdatePipeline.Tooltip",
      image = "ui/images/debug.svg",
      category = "Dimensional",
      categoryOrder = "5")
  public void showUpdatePipelineAction(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    HopGuiDimensionalModelGraph graph = context.getDimensionalGraph();
    if (table != null && graph != null) {
      graph.openUpdatePipeline(table);
    }
  }

  @GuiContextAction(
      id = "dm-graph-preview-target-layout",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Dimensional",
      categoryOrder = "7")
  public void previewTargetLayoutAction(HopGuiDimensionalTableContext context) {
    IDmTable table = context.getTable();
    DimensionalModel dmModel = context.getModel();
    if (table == null) {
      return;
    }
    ModelTableLayoutPreviewSupport.previewDmTableLayout(
        hopGui.getShell(), getVariables(), hopGui.getMetadataProvider(), dmModel, table);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.EditModel.Tooltip",
      image = "dimensional-model.svg")
  public void editModelProperties() {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiDimensionalModelDialog dialog =
        new HopGuiDimensionalModelDialog(getShell(), hopGui, model);
    if (dialog.open()) {
      commitDialogUndo(beforeChange);
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_TOGGLE_COACH,
      toolTip = "i18n:org.apache.hop.datavault.hopgui.coaching:ModelCoachPanel.Toggle.Tooltip",
      image = "coach-panel.svg")
  public void toggleCoachPanelToolbar() {
    toggleCoachPanel();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_TOGGLE_DURATIONS,
      toolTip =
          "i18n:org.apache.hop.datavault.hopgui.file.metrics:ModelLoadDurationPane.Toggle.Tooltip",
      image = "ui/images/show-results.svg")
  public void toggleLoadDurationPanelToolbar() {
    toggleLoadDurationPanel();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CHECK_MODEL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.CheckModel.Tooltip",
      image = "ui/images/check.svg")
  public void checkModel() {
    if (model == null) {
      return;
    }
    ModelDialogValidationSupport.ModelCheckProgressResult result =
        ModelDialogValidationSupport.runChecksWithProgress(
            hopShell(),
            monitor -> model.check(hopGui.getMetadataProvider(), getVariables(), monitor));
    if (result.cancelled() && result.remarks().isEmpty()) {
      return;
    }
    showCheckResultsDialog(new ArrayList<>(result.remarks()));
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_AI_HELP,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.AiHelp.Tooltip",
      image = "datavault-ai-help.svg")
  public void openAiAdvisor() {
    if (model == null) {
      return;
    }
    new DmAiAdvisorDialog(
            hopShell(),
            hopGui,
            model,
            getVariables(),
            hopGui.getMetadataProvider(),
            this::markUndoPoint,
            () -> {
              setChanged();
              redraw();
              enableUndoToolbarItems();
            })
        .open();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_GENERATE_DDL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.GenerateDdl.Tooltip",
      image = "ui/images/database.svg")
  public void generateModelDdl() {
    if (model == null) {
      return;
    }

    List<String> ddlStatements = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      try {
        for (String ddl :
            table.generateBuildDdl(hopGui.getMetadataProvider(), hopGui.getVariables(), model)) {
          if (!Utils.isEmpty(ddl)) {
            ddlStatements.add(ddl);
          }
        }
      } catch (Exception e) {
        String tableName =
            !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
        new ErrorDialog(
            hopGui.getShell(), "Error", "Error generating DDL for table '" + tableName + "'", e);
      }
    }

    ddlStatements = DvDdlSupport.deduplicateCreateTableDdl(ddlStatements);
    if (!ddlStatements.isEmpty()) {
      try {
        try {
          String explanation =
              org.apache.hop.datavault.lineage.DdlLineageExplainSupport.explain(
                  ddlStatements, model, hopGui.getVariables());
          org.apache.hop.datavault.hopgui.lineage.LineageTabSupport.showDdlExplanation(
              hopGui.getShell(), explanation);
        } catch (Exception lineageEx) {
          // Non-fatal: still open SQL editor.
        }
        DatabaseMeta dbMeta =
            DmTargetDatabaseSupport.loadTargetDatabase(
                hopGui.getMetadataProvider(), model.getConfigurationOrDefault());
        if (dbMeta != null) {
          String sql = String.join("\n", ddlStatements);
          SqlEditor sqlEditor =
              new SqlEditor(
                  hopGui.getShell(),
                  SWT.NONE,
                  hopGui.getVariables(),
                  dbMeta,
                  DbCache.getInstance(),
                  sql);
          sqlEditor.open();
        }
      } catch (Exception e) {
        new ErrorDialog(hopGui.getShell(), "Error", "Error opening DDL SQL editor", e);
      }
    } else {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoDdlNeeded.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoDdlNeeded.Message"));
      box.open();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DEBUG,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Debug.Tooltip",
      image = "ui/images/debug.svg")
  public void debugPipelines() {
    if (model == null) {
      return;
    }
    if (!validateModelForDebug()) {
      return;
    }
    IVariables debugVariables = getVariables();
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    if (config.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE) {
      debugStagingWorkflow(debugVariables);
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (!table.isSelected() && nrSelectedTables() > 0) {
        continue;
      }
      openUpdatePipeline(table, debugVariables);
    }
  }

  public void openUpdatePipeline(IDmTable table) {
    if (table instanceof DmDimensionAlias alias) {
      try {
        openUpdatePipelineForAlias(alias);
      } catch (HopException e) {
        new ErrorDialog(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Debug.Error.Title"),
            BaseMessages.getString(
                PKG,
                "HopGuiDimensionalModelGraph.Debug.Error.Pipeline",
                Const.NVL(alias.getName(), "alias")),
            e);
      }
      return;
    }
    if (!validateModelForDebug()) {
      return;
    }
    openUpdatePipeline(table, getVariables());
  }

  /**
   * Dimension aliases have no load pipeline. Follow the referenced dimension (possibly in another
   * {@code .hdm}) and generate that update pipeline instead.
   */
  private void openUpdatePipelineForAlias(DmDimensionAlias alias) throws HopException {
    DmDimensionAliasNavigationSupport.DimensionPipelineSource source =
        DmDimensionAliasNavigationSupport.resolvePipelineSource(
            model, alias, getVariables(), hopGui.getMetadataProvider());
    if (source.sameModel()) {
      IDmTable target = model != null ? model.findTable(source.dimensionName()) : null;
      if (target == null || target instanceof DmDimensionAlias) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "HopGuiDimensionalModelGraph.Debug.Error.Pipeline", source.dimensionName()));
      }
      openUpdatePipeline(target);
      return;
    }
    HopDimensionalFileType fileType = new HopDimensionalFileType();
    IHopFileTypeHandler handler = fileType.openFile(hopGui, source.modelPath(), getVariables());
    if (!(handler instanceof HopGuiDimensionalModelGraph targetGraph)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.Debug.Error.Pipeline", source.dimensionName()));
    }
    IDmTable target =
        targetGraph.getModel() != null
            ? targetGraph.getModel().findTable(source.dimensionName())
            : null;
    if (target == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.Debug.Error.Pipeline", source.dimensionName()));
    }
    org.eclipse.swt.widgets.Display display = hopGui.getDisplay();
    if (display == null || display.isDisposed()) {
      targetGraph.openUpdatePipeline(target);
      return;
    }
    IDmTable pipelineTable = target;
    display.asyncExec(() -> targetGraph.openUpdatePipeline(pipelineTable));
  }

  private static boolean hasCheckErrors(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private boolean validateModelForDebug() {
    if (model == null) {
      return false;
    }
    ModelDialogValidationSupport.ModelCheckProgressResult result =
        ModelDialogValidationSupport.runChecksWithProgress(
            hopShell(),
            monitor -> model.check(hopGui.getMetadataProvider(), getVariables(), monitor));
    if (result.cancelled()) {
      return false;
    }
    List<ICheckResult> remarks = new ArrayList<>(result.remarks());
    if (!hasCheckErrors(remarks)) {
      return true;
    }
    showCheckResultsDialog(remarks);
    return false;
  }

  private int nrSelectedTables() {
    if (model == null) {
      return 0;
    }
    int count = 0;
    for (IDmTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        count++;
      }
    }
    return count;
  }

  private void openUpdatePipeline(IDmTable table, IVariables debugVariables) {
    if (table == null || model == null) {
      return;
    }
    String tableName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    try {
      DimensionalConfiguration config = model.getConfigurationOrDefault();
      DvIntegerSettingValidationSupport.requirePositiveInteger(
          config.getTargetTableBatchSize(),
          debugVariables,
          DimensionalConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
          BaseMessages.getString(
              DvIntegerSettingValidationSupport.class,
              "DvIntegerSettingValidation.Label.TargetTableBatchSize"));
      DvIntegerSettingValidationSupport.requirePositiveInteger(
          config.getTargetTableParallelCopies(),
          debugVariables,
          DimensionalConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
          BaseMessages.getString(
              DvIntegerSettingValidationSupport.class,
              "DvIntegerSettingValidation.Label.TargetTableParallelCopies"));

      List<PipelineMeta> pipelineMetas =
          table.generateUpdatePipelines(
              hopGui.getMetadataProvider(), debugVariables, model, new Date());
      if (pipelineMetas == null || pipelineMetas.isEmpty()) {
        return;
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta == null) {
          continue;
        }
        ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(
            hopGui, pipelineMeta, debugVariables);
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Debug.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiDimensionalModelGraph.Debug.Error.Pipeline", tableName),
          e);
    }
  }

  private void debugStagingWorkflow(IVariables debugVariables) {
    try {
      List<PipelineMeta> pipelineMetas = new ArrayList<>();
      Date loadTimestamp = new Date();
      for (IDmTable table :
          DmUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables())) {
        if (!table.isSelected() && nrSelectedTables() > 0) {
          continue;
        }
        List<PipelineMeta> generated =
            table.generateUpdatePipelines(
                hopGui.getMetadataProvider(), debugVariables, model, loadTimestamp);
        if (generated != null) {
          pipelineMetas.addAll(generated);
        }
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta != null) {
          openReloadedPipeline(pipelineMeta, debugVariables);
        }
      }

      if (pipelineMetas.isEmpty()) {
        return;
      }

      DimensionalConfiguration config = model.getConfigurationOrDefault();
      org.apache.hop.core.database.DatabaseMeta targetDatabase =
          DmTargetDatabaseSupport.loadTargetDatabase(hopGui.getMetadataProvider(), config);
      if (targetDatabase == null || Utils.isEmpty(config.getTargetDatabase())) {
        return;
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta != null) {
          pipelineMeta.setFilename(
              config.resolveBulkLoadStagingFolder(debugVariables, model.getName())
                  + pipelineMeta.getName()
                  + PipelineMeta.PIPELINE_EXTENSION);
        }
      }

      List<DvUpdateWorkflowSupport.DvStagingLoadDescriptor> descriptors =
          DvUpdateWorkflowSupport.buildStagingDescriptors(
              config,
              debugVariables,
              model.getName(),
              targetDatabase,
              config.getTargetDatabase(),
              pipelineMetas);
      WorkflowMeta masterWorkflow =
          DvUpdateWorkflowSupport.buildMasterWorkflow(
              descriptors, config, debugVariables, "local", model.getName());
      ModelGeneratedArtifactOpenSupport.openGeneratedWorkflow(masterWorkflow);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Debug.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Debug.Error.StagingWorkflow"),
          e);
    }
  }

  private void openReloadedPipeline(PipelineMeta pipelineMeta, IVariables debugVariables)
      throws HopException {
    ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(hopGui, pipelineMeta, debugVariables);
  }

  private void showCheckResultsDialog(List<ICheckResult> remarks) {
    CheckResultDialog dialog = new CheckResultDialog(hopGui.getShell(), remarks);
    dialog.open();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_LEVEL,
      label = "  Zoom: ",
      toolTip = "Zoom in or out",
      type = GuiToolbarElementType.COMBO,
      alignRight = true,
      comboValuesMethod = "getZoomLevels")
  public void zoomLevel() {
    performZoomLevelChanged();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.ZoomFit.Tooltip",
      image = "ui/images/zoom-fit.svg")
  @Override
  public void zoomFitToScreen() {
    performZoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Undo.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/undo.svg",
      separator = true)
  @GuiKeyboardShortcut(control = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, key = 'z')
  @Override
  public void undo() {
    try {
      applySnapshotChange(snapshotUndo.undo(model, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Redo.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/redo.svg")
  @GuiKeyboardShortcut(control = true, shift = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, shift = true, key = 'z')
  @Override
  public void redo() {
    try {
      applySnapshotChange(snapshotUndo.redo(model, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model == null) {
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(true);
      }
    }
    for (DvNote note : model.getNotes()) {
      if (note != null) {
        note.setSelected(true);
      }
    }
    redraw();
  }

  @GuiKeyboardShortcut(key = SWT.ESC)
  @GuiOsxKeyboardShortcut(key = SWT.ESC)
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNSELECT_ALL,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.UnselectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/unselect-all.svg")
  @Override
  public void unselectAll() {
    mouseInteractions().unselectAllOnCanvas();
    clearSelectionRegion();
    redraw();
  }

  @Override
  protected ModelGraphSnapshotUndo<?> getSnapshotUndo() {
    return snapshotUndo.getDelegate();
  }

  @Override
  protected Object getModelForUndo() {
    return model;
  }

  @Override
  protected void restoreModelSnapshot(Object restored) throws Exception {
    if (restored instanceof DimensionalModel dimensionalModel) {
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
      areaOwners.clear();
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      model = dimensionalModel;
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
      canvas.setFocus();
    }
  }

  @Override
  protected void clearSelectionRegion() {
    selectionRegion = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
      canvas.setData("mode", "null");
      setCursor(null);
    }
    mouseOverTableName = null;
    mouseOverNoteLink = null;
  }

  @Override
  protected String undoRecordErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Record.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.Undo.Error.Apply.Message");
  }

  @Override
  protected String undoToolbarItemId() {
    return TOOLBAR_ITEM_UNDO;
  }

  @Override
  protected String redoToolbarItemId() {
    return TOOLBAR_ITEM_REDO;
  }

  @Override
  protected GuiToolbarWidgets getToolBarWidgets() {
    return toolBarWidgets;
  }

  @Override
  protected String getZoomLevelToolbarItemId() {
    return TOOLBAR_ITEM_ZOOM_LEVEL;
  }

  @Override
  protected List<DvNote> getModelNotes() {
    return model != null ? model.getNotes() : List.of();
  }

  @Override
  protected IGuiContextHandler createNoteContextHandler(DvNote note, Point real) {
    return new HopGuiDimensionalNoteContext(model, this, note, real);
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoteContext.Message");
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoteLink.TableTooltip", target);
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.NoteLink.Error.Title");
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return BaseMessages.getString(
        PKG, "HopGuiDimensionalModelGraph.NoteLink.UrlError.Message", target);
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return BaseMessages.getString(
        PKG, "HopGuiDimensionalModelGraph.NoteLink.TableNotFound.Message", tableName);
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {
    navigateToTable(tableName);
  }

  /** Selects and centers a table on this graph canvas. */
  public void navigateToTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    IDmTable table = model.findTable(tableName);
    if (table == null) {
      showNoteLinkTableNotFound(tableName);
      return;
    }
    mouseInteractions().unselectAllOnCanvas();
    table.setSelected(true);
    centerOnTable(table);
    redraw();
    updateGui();
  }

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public org.apache.hop.core.search.ISearchable createSearchable(
      String locationDescription, org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws org.apache.hop.core.exception.HopException {
    return new org.apache.hop.datavault.hopgui.search.HopGuiDimensionalModelSearchable(
        locationDescription, model);
  }

  /** Select and open the dimensional table matching {@code componentName} (search navigation). */
  public void openSearchComponent(String componentName) {
    if (Utils.isEmpty(componentName) || model == null) {
      return;
    }
    IDmTable table = model.findTable(componentName);
    if (table == null) {
      return;
    }
    for (IDmTable t : model.getTables()) {
      if (t != null) {
        t.setSelected(false);
      }
    }
    table.setSelected(true);
    editDmTable(table);
    updateGui();
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : "Dimensional Model";
  }

  @Override
  public void setName(String name) {
    if (model != null) {
      markUndoPoint();
      model.setName(name);
      setChanged();
    }
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @Override
  public IHopFileType getFileType() {
    return fileType;
  }

  @Override
  public String getFilename() {
    return model != null ? model.getFilename() : filename;
  }

  @Override
  public void setFilename(String filename) {
    if (model != null) {
      model.setFilename(filename);
    } else {
      this.filename = filename;
    }
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @Override
  public void save() throws HopException {
    if (fileType != null) {
      fileType.saveFile(hopGui, this);
    }
  }

  @Override
  public void saveAs(String filename) throws HopException {
    if (fileType != null) {
      fileType.saveFileAs(hopGui, this, filename);
    }
  }

  @Override
  public void start() {
    // not applicable
  }

  @Override
  public void stop() {
    // not applicable
  }

  @Override
  public void pause() {
    // not applicable
  }

  @Override
  public void resume() {
    // not applicable
  }

  @Override
  public void preview() {
    // not applicable
  }

  @Override
  public void debug() {
    debugPipelines();
  }

  public void copyDimensionalUpdateActionToClipboard() {
    if (model == null) {
      return;
    }
    if (!ensureModelSavedBeforeClipboard()) {
      return;
    }
    try {
      ActionDimensionalUpdate updateAction = loadStoredDimensionalUpdateAction();
      updateAction.setDimensionalModelFile(getFilename());
      ModelUpdateWorkflowClipboardSupport.copyUpdateWorkflowToClipboard(
          hopGui, updateAction, getVariables(), getFilename());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.CopyUpdateAction.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.CopyUpdateAction.Error.Message"),
          e);
    }
  }

  private ActionDimensionalUpdate loadStoredDimensionalUpdateAction() throws HopException {
    String actionXml =
        ModelUpdateActionAuditSupport.retrieveActionXml(
            DIMENSIONAL_UPDATE_AUDIT_GROUP, DIMENSIONAL_UPDATE_AUDIT_TYPE, getFilename());
    if (!Utils.isEmpty(actionXml)) {
      Node actionNode = XmlHandler.loadXmlString(actionXml, ActionMeta.XML_TAG);
      ActionMeta actionMeta =
          new ActionMeta(actionNode, hopGui.getMetadataProvider(), getVariables());
      if (actionMeta.getAction() instanceof ActionDimensionalUpdate storedAction) {
        return storedAction;
      }
    }
    return new ActionDimensionalUpdate();
  }

  private boolean ensureModelSavedBeforeClipboard() {
    if (!hasChanged()) {
      return true;
    }

    MessageBox messageDialog =
        new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
    messageDialog.setText(
        BaseMessages.getString(
            PKG, "HopGuiDimensionalModelGraph.CopyUpdateAction.Save.Dialog.Header"));
    messageDialog.setMessage(
        BaseMessages.getString(
            PKG,
            "HopGuiDimensionalModelGraph.CopyUpdateAction.Save.Dialog.Message",
            buildTabName()));
    int answer = messageDialog.open();
    if ((answer & SWT.YES) != 0) {
      try {
        if (Utils.isEmpty(getFilename())) {
          String chosenFilename =
              BaseDialog.presentFileDialog(
                  true,
                  hopGui.getActiveShell(),
                  fileType.getFilterExtensions(),
                  fileType.getFilterNames(),
                  true);
          if (chosenFilename == null) {
            return false;
          }
          saveAs(hopGui.getVariables().resolve(chosenFilename));
        } else {
          save();
        }
        return true;
      } catch (Exception e) {
        new ErrorDialog(
            hopShell(),
            BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Header"),
            BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Message"),
            e);
        return false;
      }
    }
    return (answer & SWT.NO) != 0;
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COPY,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Copy.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/copy.svg",
      alignRight = true)
  @GuiKeyboardShortcut(control = true, key = 'c')
  @GuiOsxKeyboardShortcut(command = true, key = 'c')
  @Override
  public void copySelectedToClipboard() {
    if (clipboardDelegate == null || model == null) {
      return;
    }
    clipboardDelegate.copySelected(getSelectedTables(), getSelectedNotes());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CUT,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Cut.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/cut.svg")
  @GuiKeyboardShortcut(control = true, key = 'x')
  @GuiOsxKeyboardShortcut(command = true, key = 'x')
  @Override
  public void cutSelectedToClipboard() {
    if (clipboardDelegate == null || model == null) {
      return;
    }
    clipboardDelegate.copySelected(getSelectedTables(), getSelectedNotes());
    deleteSelected();
  }

  public void pasteFromClipboard(Point location) {
    if (clipboardDelegate == null || model == null) {
      return;
    }
    markUndoPoint();
    clipboardDelegate.pasteXml(model, clipboardDelegate.fromClipboard(), location);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_PASTE,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Paste.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/paste.svg")
  @GuiKeyboardShortcut(control = true, key = 'v')
  @GuiOsxKeyboardShortcut(command = true, key = 'v')
  @Override
  public void pasteFromClipboard() {
    Point location = lastClick != null ? new Point(lastClick.x, lastClick.y) : null;
    pasteFromClipboard(location);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::HopGuiDimensionalModelGraph.Toolbar.Delete.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  @Override
  public void deleteSelected() {
    if (model == null) {
      return;
    }
    List<IDmTable> tablesToDelete = getSelectedTables();
    List<DvNote> notesToDelete = getSelectedNotes();
    if (tablesToDelete.isEmpty() && notesToDelete.isEmpty()) {
      return;
    }
    markUndoPoint();
    boolean modelChanged = false;
    if (model.getTables().removeAll(tablesToDelete)) {
      model.removeConformedDimensionRefsForTables(tablesToDelete);
      modelChanged = true;
    }
    if (model.getNotes().removeAll(notesToDelete)) {
      modelChanged = true;
    }
    if (modelChanged) {
      setChanged();
      redraw();
    }
  }

  public void applyPasteResult(List<IDmTable> tables, List<DvNote> notes) {
    if (model == null) {
      return;
    }
    boolean modelChanged = false;
    mouseInteractions().unselectAllOnCanvas();

    if (tables != null) {
      for (IDmTable table : tables) {
        if (table == null) {
          continue;
        }
        model.getTables().add(table);
        table.setSelected(true);
        modelChanged = true;
      }
    }
    if (notes != null) {
      for (DvNote note : notes) {
        if (note == null) {
          continue;
        }
        model.getNotes().add(note);
        note.setSelected(true);
        modelChanged = true;
      }
    }
    if (modelChanged) {
      setChanged();
      redraw();
    }
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    if (perspective != null) {
      perspective.updateTabItem(this);
      perspective.updateTreeItem(this);
    }
    enableClipboardToolbarItems();
    enableUndoToolbarItems();
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setFocus();
    }
  }

  private void enableClipboardToolbarItems() {
    if (toolBarWidgets == null) {
      return;
    }
    boolean hasSelection = hasCanvasSelection();
    boolean hasClipboard = hasClipboardContent();

    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_COPY, IHopFileType.CAPABILITY_COPY, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_CUT, IHopFileType.CAPABILITY_CUT, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_DELETE, IHopFileType.CAPABILITY_DELETE, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_PASTE, IHopFileType.CAPABILITY_PASTE, hasClipboard);
  }

  private boolean hasCanvasSelection() {
    return !getSelectedTables().isEmpty() || !getSelectedNotes().isEmpty();
  }

  private boolean hasClipboardContent() {
    try {
      return !Utils.isEmpty(GuiResource.getInstance().fromClipboard());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isCloseable() {
    try {
      if (hopGui.fileDelegate.isClosing()) {
        return true;
      }
      if (hasChanged()) {
        MessageBox messageDialog =
            new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        messageDialog.setText(
            BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiDimensionalModelGraph.SaveFile.Dialog.Message", buildTabName()));
        int answer = messageDialog.open();
        if ((answer & SWT.YES) != 0) {
          if (Utils.isEmpty(getFilename())) {
            String chosenFilename =
                BaseDialog.presentFileDialog(
                    true,
                    hopGui.getActiveShell(),
                    fileType.getFilterExtensions(),
                    fileType.getFilterNames(),
                    true);
            if (chosenFilename == null) {
              return false;
            }
            saveAs(hopGui.getVariables().resolve(chosenFilename));
          } else {
            save();
          }
          return true;
        }
        return (answer & SWT.NO) != 0;
      }
      return true;
    } catch (Exception e) {
      new ErrorDialog(
          hopShell(),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiDimensionalModelGraph.SaveFile.Error.Message"),
          e);
    }
    return false;
  }

  private String buildTabName() {
    String realFilename = variables.resolve(getFilename());
    if (Utils.isEmpty(realFilename)) {
      return getName();
    }
    int lastSlash = Math.max(realFilename.lastIndexOf('/'), realFilename.lastIndexOf('\\'));
    if (lastSlash >= 0 && lastSlash < realFilename.length() - 1) {
      return realFilename.substring(lastSlash + 1);
    }
    return realFilename;
  }

  @Override
  public void close() {
    perspective.remove(this);
  }

  @Override
  public boolean hasChanged() {
    return changed || (model != null && model.hasChanged());
  }

  public void setChanged() {
    this.changed = true;
    if (model != null) {
      model.setChanged();
    }
    updateGui();
    redraw();
  }

  public void clearChanged() {
    this.changed = false;
    if (model != null) {
      model.clearChanged();
    }
    updateGui();
    redraw();
  }

  @Override
  public Map<String, Object> getStateProperties() {
    return buildCanvasStateProperties();
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {
    applyCanvasStateProperties(stateProperties);
    redraw();
  }

  @Override
  public IVariables getVariables() {
    return variables;
  }

  @Override
  public SnapAllignDistribute createSnapAlignDistribute() {
    List<IDmTable> selection = getSelectedTables();
    int[] indices = new int[selection.size()];
    for (int i = 0; i < selection.size(); i++) {
      indices[i] = model.getTables().indexOf(selection.get(i));
    }
    return new SnapAllignDistribute(model, selection, indices, null, this);
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }

  private final class DimensionalMouseInteractions implements ModelGraphMouseInteractions {

    @Override
    public ModelGraphHit resolveHit(int logicalX, int logicalY) {
      AreaOwner areaOwner = getVisibleAreaOwner(logicalX, logicalY);
      IDmTable table = getAreaOwnerTable(areaOwner);
      DvNote note = getAreaOwnerNote(areaOwner);
      AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
      return new ModelGraphHit(areaOwner, areaType, note, table);
    }

    @Override
    public boolean handleObjectMouseDown(
        Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
      Object obj = hit.canvasObject();
      if (!(obj instanceof IDmTable tableHit)) {
        return false;
      }
      AreaOwner.AreaType areaType = hit.areaType();

      if (areaType == AreaOwner.AreaType.TRANSFORM_ICON
          && (e.button == 2 || (e.button == 1 && shift))) {
        startRelationshipTable = tableHit;
        relationshipDragEndLocation = new Point(e.x, e.y);
        candidateRelationshipTarget = tableHit;
        mouseOverTableName = null;
        clearTableDragState();
        clearSelectionRegion();
        avoidContextDialog = true;
        redraw();
        return true;
      }

      if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
        avoidContextDialog = true;
        editDmTable(tableHit);
        clearTableDragState();
        return true;
      }

      if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
        prepareExclusiveDragSelection(
            control, tableHit.isSelected(), () -> tableHit.setSelected(true));
        currentTable = tableHit;
        iconDragStart = new Point(real.x, real.y);
        iconDragCommitted = false;
        Point loc = tableHit.getLocation() != null ? tableHit.getLocation() : new Point(0, 0);
        iconOffset = new Point(real.x - loc.x, real.y - loc.y);
        clearNoteDragState();
        clearSelectionRegion();
        redraw();
        return true;
      }

      return false;
    }

    @Override
    public boolean isRelationshipDragActive() {
      return startRelationshipTable != null;
    }

    @Override
    public String webRelationshipStartNodeName() {
      return startRelationshipTable != null ? startRelationshipTable.getName() : null;
    }

    @Override
    public void handleRelationshipMouseMove(Event e) {
      relationshipDragEndLocation = new Point(e.x, e.y);
      candidateRelationshipTarget = findTableAtScreen(e.x, e.y);
      if (candidateRelationshipTarget == startRelationshipTable) {
        candidateRelationshipTarget = null;
      }
    }

    @Override
    public boolean handleRelationshipMouseUp(Event e, Point real) {
      IDmTable target = findTableAtScreen(e.x, e.y);
      if (target != null && target != startRelationshipTable) {
        createRelationship(startRelationshipTable, target);
      }
      cancelRelationshipDrag();
      clearTableDragState();
      avoidContextDialog = true;
      redraw();
      return true;
    }

    @Override
    public boolean handleObjectMouseMove(Point real, boolean leftButtonDown) {
      if (!leftButtonDown
          || currentTable == null
          || startRelationshipTable != null
          || resize != null) {
        return false;
      }
      currentTable.setSelected(true);
      if (iconOffset == null) {
        iconOffset = new Point(0, 0);
      }
      Point icon = new Point(real.x - iconOffset.x, real.y - iconOffset.y);
      boolean doRedraw = false;
      if (tryCommitIconDrag(real)) {
        doRedraw = true;
      }
      if (iconDragCommitted && currentTable.isSelected()) {
        int dx = icon.x - currentTable.getLocation().x;
        int dy = icon.y - currentTable.getLocation().y;
        moveSelectedObjects(dx, dy);
        avoidContextDialog = true;
        doRedraw = true;
      }
      return doRedraw;
    }

    @Override
    public boolean handleNoteMouseMove(Point real) {
      if (selectedNote == null || noteOffset == null) {
        return false;
      }
      Point notePos = new Point(real.x - noteOffset.x, real.y - noteOffset.y);
      int dx = notePos.x - selectedNote.getLocation().x;
      int dy = notePos.y - selectedNote.getLocation().y;
      if (dx == 0 && dy == 0) {
        return false;
      }
      if (!noteWasMoved) {
        markPositionUndoPoint();
      }
      moveSelectedObjects(dx, dy);
      noteWasMoved = true;
      avoidContextDialog = true;
      return true;
    }

    @Override
    public boolean hasCancellableDragState() {
      return startRelationshipTable != null
          || currentTable != null
          || iconDragStart != null
          || currentNote != null
          || noteDragStart != null
          || selectionRegion != null;
    }

    @Override
    public void cancelActiveDragsOnBackgroundClick() {
      cancelRelationshipDrag();
      clearTableDragState();
    }

    @Override
    public void clearObjectDragState() {
      clearTableDragState();
    }

    @Override
    public void unselectAllOnCanvas() {
      if (model == null) {
        return;
      }
      for (IDmTable table : model.getTables()) {
        if (table != null) {
          table.setSelected(false);
        }
      }
      unselectAllNotes();
    }

    @Override
    public void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
      if (model == null) {
        return;
      }
      for (IDmTable table : model.getTables()) {
        if (isTableInLassoScreenRect(table, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          table.setSelected(true);
        }
      }
      for (DvNote note : model.getNotes()) {
        if (isNoteInLassoScreenRect(note, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          note.setSelected(true);
        }
      }
    }

    @Override
    public void afterLassoSelection() {
      updateGui();
    }

    @Override
    public boolean handleCommittedDragMouseUp(Event e) {
      if (e.button != 1) {
        return false;
      }
      if (!iconDragCommitted && !dragSelection && !noteWasMoved) {
        return false;
      }
      setChanged();
      clearTableDragState();
      clearNoteDragState();
      avoidContextDialog = false;
      return true;
    }

    @Override
    public boolean handlePureClickMouseUp(Event e, Point real) {
      if (!isUnmovedClick(real)) {
        if (e.button == 1) {
          clearTableDragState();
          clearNoteDragState();
          avoidContextDialog = false;
        }
        return false;
      }

      if (handleNoteLinkClickAt(real)) {
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
        return true;
      }

      DvNote noteHit = currentNote;
      if (noteHit != null) {
        handleNoteBodyClick(e, noteHit, real, isControlDown(e));
        clearNoteDragState();
        avoidContextDialog = false;
        return true;
      }

      IDmTable hit = currentTable;
      if (hit == null) {
        if (!avoidContextDialog) {
          showDimensionalContextDialog(e, real);
          avoidContextDialog = true;
        } else {
          avoidContextDialog = false;
        }
        return true;
      }

      if (isControlDown(e)) {
        hit.setSelected(!hit.isSelected());
        redraw();
      } else if (!avoidContextDialog) {
        showTableContextDialog(e, hit);
      }
      clearTableDragState();
      avoidContextDialog = false;
      return true;
    }

    @Override
    public boolean clearHoverState() {
      if (mouseOverTableName == null && mouseOverNoteLink == null) {
        return false;
      }
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      return true;
    }

    @Override
    public boolean updateHoverState(AreaOwner areaOwner, Point real) {
      String newOver = null;
      if (areaOwner != null
          && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
          && startRelationshipTable == null
          && !dragSelection
          && selectionRegion == null) {
        if (areaOwner.getParent() instanceof IDmTable dmTable && dmTable.getName() != null) {
          newOver = dmTable.getName();
        }
      }
      boolean doRedraw = false;
      if ((mouseOverTableName == null && newOver != null)
          || (mouseOverTableName != null && !mouseOverTableName.equals(newOver))) {
        mouseOverTableName = newOver;
        doRedraw = true;
      }
      return doRedraw;
    }

    @Override
    public void onLassoMouseDownAfter() {
      mouseOverTableName = null;
    }

    @Override
    public boolean isNoteMouseDownAllowed() {
      return startRelationshipTable == null;
    }

    @Override
    public boolean isLassoMoveAllowed() {
      return startRelationshipTable == null;
    }

    @Override
    public boolean allowEmptyLassoClearOnMouseUp() {
      return startRelationshipTable == null;
    }

    @Override
    public boolean isNoteResizeHoverBlocked() {
      return startRelationshipTable != null
          || dragSelection
          || selectionRegion != null
          || noteWasMoved
          || iconDragCommitted;
    }

    @Override
    public void prepareNavigationViewportDrag() {
      cancelRelationshipDrag();
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
    }

    @Override
    public void refreshGui() {
      updateGui();
    }
  }

  @Override
  public ICoachingModelAdapter createCoachingModelAdapter() {
    if (model == null) {
      return null;
    }
    return new DmCoachingModelAdapter(model, this::editDmTableByName, this::highlightDmTableByName);
  }

  @Override
  public void notifyCoachModelChanged() {
    setChanged();
    refreshCoachPanel();
  }

  @Override
  public String createTableFromCoachSource(
      CoachingSourceRef sourceRef, String tableType, String tableName, Point location) {
    if (model == null || Utils.isEmpty(tableType)) {
      return null;
    }
    markUndoPoint();
    IDmTable table = null;
    String normalizedType = tableType.trim();
    if ("DIMENSION".equalsIgnoreCase(normalizedType)) {
      table = new DmDimension();
    } else if ("FACT".equalsIgnoreCase(normalizedType)) {
      table = new DmFact();
    } else if ("BRIDGE".equalsIgnoreCase(normalizedType)) {
      table = new DmBridge();
    } else if ("JUNK_DIMENSION".equalsIgnoreCase(normalizedType)) {
      table = new DmJunkDimension();
    }
    if (table == null) {
      return null;
    }
    if (!Utils.isEmpty(tableName)) {
      table.setName(tableName);
      table.setTableName(tableName.toLowerCase().replace(' ', '_'));
    } else {
      String generated = getUniqueTableName(table.getTableType().name());
      table.setName(generated);
      table.setTableName(generated.toLowerCase().replace(' ', '_'));
    }
    PropsUi.setLocation(
        table, location != null ? location.x : 50, location != null ? location.y : 50);
    model.getTables().add(table);
    setChanged();
    redraw();
    return table.getName();
  }

  private void editDmTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    IDmTable table = model.findTable(tableName);
    if (table != null) {
      editDmTable(table);
    }
  }

  private void highlightDmTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(tableName.equals(table.getName()));
      }
    }
    redraw();
  }
}
