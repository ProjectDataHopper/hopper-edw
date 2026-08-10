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
package org.apache.hop.datavault.hopgui.file.businessvault;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.action.GuiContextAction;
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
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.command.svg.SvgExportService;
import org.apache.hop.datavault.command.svg.SvgRenderOptions;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.hopgui.ModelGeneratedArtifactOpenSupport;
import org.apache.hop.datavault.hopgui.ModelTableLayoutPreviewSupport;
import org.apache.hop.datavault.hopgui.ModelUpdateActionAuditSupport;
import org.apache.hop.datavault.hopgui.ModelUpdateWorkflowClipboardSupport;
import org.apache.hop.datavault.hopgui.ai.BvAiAdvisorDialog;
import org.apache.hop.datavault.hopgui.coaching.ICoachableModelGraph;
import org.apache.hop.datavault.hopgui.file.businessvault.delegates.HopGuiBusinessVaultClipboardDelegate;
import org.apache.hop.datavault.hopgui.file.businessvault.delegates.HopGuiBusinessVaultSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvDdlSupport;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.DvUpdateWorkflowSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultBvReferenceSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvReferenceSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.businessvault.BvBusinessTable;
import org.apache.hop.datavault.metadata.businessvault.BvBvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvDvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvPitTable;
import org.apache.hop.datavault.metadata.businessvault.BvScd2Table;
import org.apache.hop.datavault.metadata.businessvault.BvTableBase;
import org.apache.hop.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.coaching.BvCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.datavault.workflow.actions.businessvaultupdate.ActionBusinessVaultUpdate;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
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
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.w3c.dom.Node;

/** Hop GUI editor for Business Vault models with references to a Data Vault model. */
@GuiPlugin(
    id = "HopGuiBusinessVaultGraph",
    description = "i18n::HopGuiBusinessVaultGraph.Description")
@Getter
@Setter
public class HopGuiBusinessVaultGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher, ICoachableModelGraph {

  private static final Class<?> PKG = HopGuiBusinessVaultGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiBusinessVaultGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiBusinessVaultGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN =
      "HopGuiBusinessVaultGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiBusinessVaultGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiBusinessVaultGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiBusinessVaultGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_EDIT_MODEL =
      "HopGuiBusinessVaultGraph-ToolBar-10050-Edit-Model";
  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiBusinessVaultGraph-ToolBar-10060-Check-Model";
  public static final String TOOLBAR_ITEM_AI_HELP =
      "HopGuiBusinessVaultGraph-ToolBar-10065-AI-Help";
  public static final String TOOLBAR_ITEM_EXPORT_SVG =
      "HopGuiBusinessVaultGraph-ToolBar-10065-Export-Svg";
  public static final String TOOLBAR_ITEM_RELOAD_DV =
      "HopGuiBusinessVaultGraph-ToolBar-10070-Reload-Dv";
  public static final String TOOLBAR_ITEM_DEBUG = "HopGuiBusinessVaultGraph-ToolBar-10080-Debug";
  public static final String TOOLBAR_ITEM_GENERATE_DDL =
      "HopGuiBusinessVaultGraph-ToolBar-10085-Generate-Ddl";
  public static final String TOOLBAR_ITEM_TOGGLE_COACH =
      "HopGuiBusinessVaultGraph-ToolBar-10084-Toggle-Coach";
  public static final String TOOLBAR_ITEM_TOGGLE_DURATIONS =
      "HopGuiBusinessVaultGraph-ToolBar-10086-Toggle-Durations";
  public static final String TOOLBAR_ITEM_SELECT_ALL =
      "HopGuiBusinessVaultGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiBusinessVaultGraph-ToolBar-20020-Unselect-All";
  public static final String TOOLBAR_ITEM_COPY = "HopGuiBusinessVaultGraph-ToolBar-20030-Copy";
  public static final String TOOLBAR_ITEM_CUT = "HopGuiBusinessVaultGraph-ToolBar-20040-Cut";
  public static final String TOOLBAR_ITEM_PASTE = "HopGuiBusinessVaultGraph-ToolBar-20050-Paste";
  public static final String TOOLBAR_ITEM_DELETE = "HopGuiBusinessVaultGraph-ToolBar-20060-Delete";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiBusinessVaultGraph-ToolBar-20070-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiBusinessVaultGraph-ToolBar-20080-Redo";

  private static final String BUSINESS_VAULT_UPDATE_AUDIT_GROUP = "BusinessVault";
  private static final String BUSINESS_VAULT_UPDATE_AUDIT_TYPE = "BusinessVaultUpdateAction";

  private final HopBusinessVaultFileType fileType;
  private final HopGuiBusinessVaultClipboardDelegate clipboardDelegate;
  private final HopGuiBusinessVaultSnapshotUndo snapshotUndo =
      new HopGuiBusinessVaultSnapshotUndo();
  private BusinessVaultModel model;
  private DataVaultModel dataVaultModel;
  private String dataVaultLoadError;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private boolean changed = false;
  private String filename;

  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverBvTableName;
  private String mouseOverDvReferenceName;
  private String mouseOverBvReferenceName;

  private IBvTable currentBvTable;
  private BvDvTableReference currentDvReference;
  private BvBvTableReference currentBvReference;
  private Object dragAnchor;
  private Map<Object, Point> dragStartLocations;

  // Derivative relationship drag: middle-click or shift+left-click from a BV table or DV reference.
  private IBvTable startRelationshipBvTable;
  private BvDvTableReference startRelationshipDvReference;
  private Point relationshipDragEndLocation;
  private IBvTable candidateRelationshipBvTable;
  private BvDvTableReference candidateRelationshipDvReference;

  public HopGuiBusinessVaultGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      BusinessVaultModel model,
      HopBusinessVaultFileType fileType) {
    super(hopGui, parent, perspective);
    this.model = model;
    this.fileType = fileType;
    this.clipboardDelegate = new HopGuiBusinessVaultClipboardDelegate(hopGui, this);

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
    reloadDataVaultModel();
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
      hopGui.getLog().logError("Error setting up the toolbar for HopGuiBusinessVaultGraph: ", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
      return;
    }

    if (EnvironmentUtils.getInstance().isWeb()) {
      drawBusinessVaultModelImageWeb(area.x, area.y);
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

    drawBusinessVaultModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawBusinessVaultModelImageWeb(int width, int height) {
    try {
      ModelGraphCanvasSvgResult result =
          BusinessVaultModelCanvasSvgRenderer.render(buildBusinessVaultSvgContext(width, height));
      applyWebCanvasRender(result, width, height, model);
    } catch (Exception ex) {
      logWebCanvasRenderError("Failed to render Business Vault model SVG for Hop Web", ex);
    }
  }

  private BusinessVaultModelCanvasSvgRenderer.Context buildBusinessVaultSvgContext(
      int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    maximum = model.getMaximum();
    BusinessVaultModelCanvasSvgRenderer.Context ctx =
        new BusinessVaultModelCanvasSvgRenderer.Context();
    ctx.variables = variables;
    ctx.model = model;
    ctx.dataVaultModel = dataVaultModel;
    ctx.canvasSize = new Point(width, height);
    ctx.offset = offset;
    ctx.selectionRegion = selectionRegion;
    ctx.iconSize = propsUi.getIconSize();
    ctx.gridSize = propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1;
    ctx.magnification = (float) (magnification * PropsUi.getNativeZoomFactor());
    ctx.screenMagnification = magnification;
    ctx.zoomFactor = propsUi.getZoomFactor();
    ctx.maximum = maximum;
    ctx.mouseOverBvTableName = mouseOverBvTableName;
    ctx.mouseOverDvReferenceName = mouseOverDvReferenceName;
    ctx.mouseOverBvReferenceName = mouseOverBvReferenceName;
    ctx.mouseOverNoteLink = mouseOverNoteLink;
    ctx.noteImageBaseFilename = getFilename();
    ctx.showingNavigationView = !propsUi.isHideViewportEnabled();
    ctx.showHashKeyFieldNames = DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel();
    ctx.metadataProvider = hopGui.getMetadataProvider();
    ctx.startRelationshipBvTable = startRelationshipBvTable;
    ctx.startRelationshipDvReference = startRelationshipDvReference;
    ctx.relationshipDragEndLocation = relationshipDragEndLocation;
    ctx.candidateRelationshipBvTable = candidateRelationshipBvTable;
    ctx.candidateRelationshipDvReference = candidateRelationshipDvReference;
    return ctx;
  }

  private void drawBusinessVaultModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    maximum = model.getMaximum();

    try {
      areaOwners.clear();
      BusinessVaultModelPainter painter =
          new BusinessVaultModelPainter(model, gc, variables, width, height);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification((float) (magnification * PropsUi.getNativeZoomFactor()));
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setMetadataProvider(hopGui.getMetadataProvider());
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverBvTableName(mouseOverBvTableName);
      painter.setMouseOverDvReferenceName(mouseOverDvReferenceName);
      painter.setMouseOverBvReferenceName(mouseOverBvReferenceName);
      painter.setMouseOverNoteLink(mouseOverNoteLink);
      painter.setNoteImageBaseFilename(getFilename());
      painter.setSelectionRegion(selectionRegion);
      painter.setRelationshipDragInfo(
          startRelationshipBvTable,
          startRelationshipDvReference,
          relationshipDragEndLocation,
          candidateRelationshipBvTable,
          candidateRelationshipDvReference);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.setDataVaultModel(dataVaultModel);
      painter.setShowHashKeyFieldNames(
          DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel());
      painter.drawBusinessVaultModel(hopGui.getMetadataProvider());

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
    if (model == null) {
      return nodes;
    }
    if (model.getTables() != null) {
      for (IBvTable table : model.getTables()) {
        if (table == null || Utils.isEmpty(table.getName()) || table.getLocation() == null) {
          continue;
        }
        int w = ModelGraphWebCanvasData.DEFAULT_CARD_WIDTH;
        int h = ModelGraphWebCanvasData.DEFAULT_CARD_HEIGHT;
        if (table instanceof BvTableBase base) {
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
    }
    if (model.getDvReferences() != null) {
      for (BvDvTableReference ref : model.getDvReferences()) {
        if (ref == null || Utils.isEmpty(ref.getDvTableName()) || ref.getLocation() == null) {
          continue;
        }
        nodes.put(
            ref.getDvTableName(),
            ModelGraphWebCanvasData.NodePos.of(
                ref.getLocation().x,
                ref.getLocation().y,
                ref.isSelected(),
                ref.getDrawnBoxWidth() > 0
                    ? ref.getDrawnBoxWidth()
                    : ModelGraphWebCanvasData.DEFAULT_CARD_WIDTH,
                ref.getDrawnBoxHeight() > 0
                    ? ref.getDrawnBoxHeight()
                    : ModelGraphWebCanvasData.DEFAULT_CARD_HEIGHT));
      }
    }
    if (model.getBvReferences() != null) {
      for (BvBvTableReference ref : model.getBvReferences()) {
        if (ref == null || Utils.isEmpty(ref.getBvTableName()) || ref.getLocation() == null) {
          continue;
        }
        nodes.put(
            ref.getBvTableName(),
            ModelGraphWebCanvasData.NodePos.of(
                ref.getLocation().x,
                ref.getLocation().y,
                ref.isSelected(),
                ref.getDrawnBoxWidth() > 0
                    ? ref.getDrawnBoxWidth()
                    : ModelGraphWebCanvasData.DEFAULT_CARD_WIDTH,
                ref.getDrawnBoxHeight() > 0
                    ? ref.getDrawnBoxHeight()
                    : ModelGraphWebCanvasData.DEFAULT_CARD_HEIGHT));
      }
    }
    return nodes;
  }

  @Override
  public void redraw() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  public void reloadDataVaultModel() {
    dataVaultModel = null;
    dataVaultLoadError = null;
    if (model == null) {
      redraw();
      return;
    }
    try {
      // Union of canvas DV aliases (multi-model) + optional legacy linked path.
      dataVaultModel =
          BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
              model, getVariables(), hopGui.getMetadataProvider());
      if (dataVaultModel.getTables().isEmpty()
          && model.getDvReferences().isEmpty()
          && Utils.isEmpty(model.getDataVaultModelPath())) {
        dataVaultModel = null;
      }
    } catch (HopException e) {
      dataVaultLoadError = e.getMessage();
    }
    redraw();
  }

  public static HopGuiBusinessVaultGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective explorerPerspective) {
      if (explorerPerspective.getActiveFileTypeHandler()
          instanceof HopGuiBusinessVaultGraph graph) {
        return graph;
      }
    }
    return null;
  }

  public void runUndoableModelChange(BvModelChange change) throws HopException {
    byte[] beforeChange = captureUndoSnapshot();
    change.run();
    commitDialogUndo(beforeChange);
    setChanged();
    redraw();
  }

  @FunctionalInterface
  public interface BvModelChange {
    void run() throws HopException;
  }

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new BusinessVaultMouseInteractions();
  }

  @Override
  protected String getMetricsModelName() {
    return model != null ? model.getName() : null;
  }

  @Override
  protected String getMetricsModelType() {
    return GeneratedPipelineMetadataConstants.MODEL_TYPE_BV;
  }

  @Override
  protected List<String> getMetricsTableNames() {
    if (model == null || model.getTables() == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IBvTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names;
  }

  private boolean isBvTableInLassoScreenRect(
      IBvTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null) {
      return false;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = 140;
    int th = 70;
    if (table instanceof BvTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        tw = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        th = base.getDrawnBoxHeight();
      }
    }
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int sw = Math.max(1, tw);
    int sh = Math.max(1, th);
    int tMaxX = tMinX + sw;
    int tMaxY = tMinY + sh;
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private void showBusinessVaultContextDialog(Event e, Point real) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Background.Message");
      IGuiContextHandler contextHandler = new HopGuiBusinessVaultContext(model, this, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showDvReferenceContextDialog(Event e, BvDvTableReference reference) {
    if (reference == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG,
              "HopGuiBusinessVaultGraph.Context.DvReference.Message",
              reference.getDvTableName());
      IGuiContextHandler contextHandler =
          new HopGuiBusinessVaultDvReferenceContext(model, this, reference, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showBvReferenceContextDialog(Event e, BvBvTableReference reference) {
    if (reference == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG,
              "HopGuiBusinessVaultGraph.Context.BvReference.Message",
              reference.getBvTableName());
      IGuiContextHandler contextHandler =
          new HopGuiBusinessVaultBvReferenceContext(model, this, reference, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showBvTableContextDialog(Event e, IBvTable table) {
    if (table == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiBusinessVaultGraph.Context.Table.Message", table.getName());
      IGuiContextHandler contextHandler =
          new HopGuiBusinessVaultTableContext(model, this, table, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void editBvTable(IBvTable table) {
    if (table == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted;
    if (table instanceof BvScd2Table scd2Table) {
      accepted =
          new HopGuiBvScd2TableDialog(getShell(), scd2Table, model, dataVaultModel, variables)
              .open();
    } else if (table instanceof BvBusinessTable businessTable) {
      accepted =
          new HopGuiBvBusinessTableDialog(
                  getShell(), businessTable, model, dataVaultModel, variables)
              .open();
    } else {
      accepted =
          new HopGuiBvTableDialog(getShell(), table, model, dataVaultModel, variables).open();
    }
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private boolean isRelationshipDragActive() {
    return startRelationshipBvTable != null || startRelationshipDvReference != null;
  }

  private void startRelationshipDragFromBvTable(IBvTable bvTable, int screenX, int screenY) {
    if (bvTable == null) {
      return;
    }
    startRelationshipBvTable = bvTable;
    startRelationshipDvReference = null;
    relationshipDragEndLocation = new Point(screenX, screenY);
    updateRelationshipCandidates(screenX, screenY);
    mouseOverBvTableName = null;
    mouseOverDvReferenceName = null;
    mouseOverBvReferenceName = null;
    clearCanvasDragState();
    clearNoteDragState();
    clearSelectionRegion();
    avoidContextDialog = true;
    redraw();
  }

  private void startRelationshipDragFromDvReference(
      BvDvTableReference reference, int screenX, int screenY) {
    if (reference == null) {
      return;
    }
    startRelationshipDvReference = reference;
    startRelationshipBvTable = null;
    relationshipDragEndLocation = new Point(screenX, screenY);
    updateRelationshipCandidates(screenX, screenY);
    mouseOverBvTableName = null;
    mouseOverDvReferenceName = null;
    mouseOverBvReferenceName = null;
    clearCanvasDragState();
    clearNoteDragState();
    clearSelectionRegion();
    avoidContextDialog = true;
    redraw();
  }

  private void updateRelationshipCandidates(int screenX, int screenY) {
    candidateRelationshipBvTable = findBvTableAtScreen(screenX, screenY);
    candidateRelationshipDvReference = findDvReferenceAtScreen(screenX, screenY);
    if (startRelationshipBvTable != null
        && candidateRelationshipBvTable == startRelationshipBvTable) {
      candidateRelationshipBvTable = null;
    }
    if (startRelationshipDvReference != null
        && candidateRelationshipDvReference == startRelationshipDvReference) {
      candidateRelationshipDvReference = null;
    }
  }

  private void mouseUpCreateRelationship(Event e) {
    IBvTable targetBvTable = findBvTableAtScreen(e.x, e.y);
    BvDvTableReference targetDvReference = findDvReferenceAtScreen(e.x, e.y);

    if (startRelationshipBvTable != null && targetDvReference != null) {
      createDerivativeRelationship(startRelationshipBvTable, targetDvReference);
    } else if (startRelationshipDvReference != null && targetBvTable != null) {
      createDerivativeRelationship(targetBvTable, startRelationshipDvReference);
    }

    cancelRelationshipDrag();
    clearCanvasDragState();
    avoidContextDialog = true;
    redraw();
  }

  private void createDerivativeRelationship(IBvTable bvTable, BvDvTableReference dvReference) {
    if (bvTable == null || dvReference == null) {
      return;
    }
    if (!BusinessVaultDerivativeSupport.canAddDerivative(bvTable, dvReference)) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    if (BusinessVaultDerivativeSupport.addDerivative(bvTable, dvReference)) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private void cancelRelationshipDrag() {
    startRelationshipBvTable = null;
    startRelationshipDvReference = null;
    relationshipDragEndLocation = null;
    candidateRelationshipBvTable = null;
    candidateRelationshipDvReference = null;
  }

  private IBvTable findBvTableAtScreen(int screenX, int screenY) {
    Point real = screen2real(screenX, screenY);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    if (areaOwner == null || areaOwner.getAreaType() != AreaOwner.AreaType.TRANSFORM_ICON) {
      return null;
    }
    return getAreaOwnerBvTable(areaOwner);
  }

  private BvDvTableReference findDvReferenceAtScreen(int screenX, int screenY) {
    Point real = screen2real(screenX, screenY);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    if (areaOwner == null || areaOwner.getAreaType() != AreaOwner.AreaType.TRANSFORM_ICON) {
      return null;
    }
    return getAreaOwnerDvReference(areaOwner);
  }

  private void clearCanvasDragState() {
    currentBvTable = null;
    currentDvReference = null;
    currentBvReference = null;
    iconDragStart = null;
    iconOffset = null;
    iconDragCommitted = false;
    dragSelection = false;
    positionChangeUndoMarked = false;
    clearDragStartLocations();
  }

  @Override
  protected void updateGraphAfterNavigationPan() {
    updateGui();
  }

  @Override
  protected void onClearNoteDragState() {
    clearDragStartLocations();
  }

  private void clearDragStartLocations() {
    dragAnchor = null;
    dragStartLocations = null;
  }

  private void captureDragStartLocations(Object anchor) {
    dragAnchor = anchor;
    dragStartLocations = new IdentityHashMap<>();
    for (IBvTable table : getSelectedBvTables()) {
      Point loc = table.getLocation();
      if (loc != null) {
        dragStartLocations.put(table, new Point(loc.x, loc.y));
      }
    }
    for (BvDvTableReference reference : getSelectedDvReferences()) {
      Point loc = reference.getLocation();
      if (loc != null) {
        dragStartLocations.put(reference, new Point(loc.x, loc.y));
      }
    }
    for (BvBvTableReference reference : getSelectedBvReferences()) {
      Point loc = reference.getLocation();
      if (loc != null) {
        dragStartLocations.put(reference, new Point(loc.x, loc.y));
      }
    }
    for (DvNote note : getSelectedNotes()) {
      Point loc = note.getLocation();
      if (loc != null) {
        dragStartLocations.put(note, new Point(loc.x, loc.y));
      }
    }
  }

  private void applyDragPositions(Point anchorTarget) {
    if (dragStartLocations == null || dragAnchor == null || anchorTarget == null) {
      return;
    }
    Point anchorStart = dragStartLocations.get(dragAnchor);
    if (anchorStart == null) {
      return;
    }

    int dx = anchorTarget.x - anchorStart.x;
    int dy = anchorTarget.y - anchorStart.y;
    if (dx == 0 && dy == 0) {
      return;
    }

    for (Point start : dragStartLocations.values()) {
      if (start.x + dx < 0) {
        dx = -start.x;
      }
      if (start.y + dy < 0) {
        dy = -start.y;
      }
    }

    for (IBvTable table : getSelectedBvTables()) {
      Point start = dragStartLocations.get(table);
      if (start != null) {
        setCanvasObjectLocation(table, start.x + dx, start.y + dy);
      }
    }
    for (BvDvTableReference reference : getSelectedDvReferences()) {
      Point start = dragStartLocations.get(reference);
      if (start != null) {
        setCanvasObjectLocation(reference, start.x + dx, start.y + dy);
      }
    }
    for (BvBvTableReference reference : getSelectedBvReferences()) {
      Point start = dragStartLocations.get(reference);
      if (start != null) {
        setCanvasObjectLocation(reference, start.x + dx, start.y + dy);
      }
    }
    for (DvNote note : getSelectedNotes()) {
      Point start = dragStartLocations.get(note);
      if (start != null) {
        setCanvasObjectLocation(note, start.x + dx, start.y + dy);
      }
    }
  }

  private void setCanvasObjectLocation(IBvTable table, int x, int y) {
    int clampedX = Math.max(0, x);
    int clampedY = Math.max(0, y);
    PropsUi.setLocation(table, clampedX, clampedY);
  }

  private void setCanvasObjectLocation(BvDvTableReference reference, int x, int y) {
    int clampedX = Math.max(0, x);
    int clampedY = Math.max(0, y);
    Point snapped = PropsUi.calculateGridPosition(new Point(clampedX, clampedY));
    reference.setLocation(snapped);
  }

  private void setCanvasObjectLocation(BvBvTableReference reference, int x, int y) {
    int clampedX = Math.max(0, x);
    int clampedY = Math.max(0, y);
    Point snapped = PropsUi.calculateGridPosition(new Point(clampedX, clampedY));
    reference.setLocation(snapped);
  }

  private void setCanvasObjectLocation(DvNote note, int x, int y) {
    int clampedX = Math.max(0, x);
    int clampedY = Math.max(0, y);
    PropsUi.setLocation(note, clampedX, clampedY);
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

  private IBvTable getAreaOwnerBvTable(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof IBvTable bvTable) {
      return bvTable;
    }
    return null;
  }

  private BvDvTableReference getAreaOwnerDvReference(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof BvDvTableReference reference) {
      return reference;
    }
    return null;
  }

  private BvBvTableReference getAreaOwnerBvReference(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof BvBvTableReference reference) {
      return reference;
    }
    return null;
  }

  private boolean hasSelectedCanvasObjects() {
    return !getSelectedBvTables().isEmpty()
        || !getSelectedDvReferences().isEmpty()
        || !getSelectedBvReferences().isEmpty()
        || !getSelectedNotes().isEmpty();
  }

  private boolean isDvReferenceInLassoScreenRect(
      BvDvTableReference reference, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (reference == null) {
      return false;
    }
    Point loc = reference.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = Math.max(140, reference.getDrawnBoxWidth());
    int th = Math.max(70, reference.getDrawnBoxHeight());
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + tw;
    int tMaxY = tMinY + th;
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private List<IBvTable> getSelectedBvTables() {
    List<IBvTable> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (IBvTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        selected.add(table);
      }
    }
    return selected;
  }

  private List<BvDvTableReference> getSelectedDvReferences() {
    List<BvDvTableReference> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference != null && reference.isSelected()) {
        selected.add(reference);
      }
    }
    return selected;
  }

  private List<BvBvTableReference> getSelectedBvReferences() {
    List<BvBvTableReference> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference != null && reference.isSelected()) {
        selected.add(reference);
      }
    }
    return selected;
  }

  private boolean isBvReferenceInLassoScreenRect(
      BvBvTableReference reference, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (reference == null) {
      return false;
    }
    Point loc = reference.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = Math.max(140, reference.getDrawnBoxWidth());
    int th = Math.max(70, reference.getDrawnBoxHeight());
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + tw;
    int tMaxY = tMinY + th;
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private String getUniqueBvTableName(String base) {
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

  private void addBvTableAtClick(IBvTable table, Point click) {
    if (model == null || table == null) {
      return;
    }
    String name = getUniqueBvTableName(table.getTableType().name());
    table.setName(name);
    table.setTableName(name.toLowerCase().replace(' ', '_'));
    PropsUi.setLocation(table, click != null ? click.x : 50, click != null ? click.y : 50);
    model.getTables().add(table);
    editBvTable(table);
  }

  private void addDvReferenceAtClick(DvTableType tableType, Point click) {
    if (model == null || tableType == null) {
      return;
    }

    List<BusinessVaultDvReferenceSupport.DvModelSource> sources =
        BusinessVaultDvReferenceSupport.listDvModelSources(
            model, dataVaultModel, getVariables(), hopGui.getMetadataProvider());
    // Always at least "Browse .hdv…"; if only browse and no catalog/linked, still allow pick.
    boolean onlyBrowse = sources.size() == 1 && sources.get(0).browseFile();
    if (onlyBrowse && dataVaultModel == null && Utils.isEmpty(model.getDataVaultModelPath())) {
      // Still proceed — user can browse. Soft tip is in the browse dialog title/message.
    }

    BusinessVaultDvReferenceSupport.DvModelSource chosenSource;
    if (sources.size() == 1 && !sources.get(0).browseFile()) {
      chosenSource = sources.get(0);
    } else {
      String[] sourceLabels =
          sources.stream()
              .map(BusinessVaultDvReferenceSupport.DvModelSource::label)
              .toArray(String[]::new);
      EnterSelectionDialog sourceDialog =
          new EnterSelectionDialog(
              getShell(),
              sourceLabels,
              BaseMessages.getString(
                  PKG, "HopGuiBusinessVaultGraph.AddDvReference.SelectModel.Title"),
              BaseMessages.getString(
                  PKG, "HopGuiBusinessVaultGraph.AddDvReference.SelectModel.Message"));
      String selectedSource = sourceDialog.open();
      if (Utils.isEmpty(selectedSource)) {
        return;
      }
      chosenSource =
          sources.stream().filter(s -> selectedSource.equals(s.label())).findFirst().orElse(null);
      if (chosenSource == null) {
        return;
      }
    }

    DataVaultModel sourceModel;
    String modelFilenameForAlias = null;
    try {
      if (chosenSource.browseFile()) {
        String browsed =
            BaseDialog.presentFileDialog(
                hopGui.getShell(),
                new String[] {"*.hdv", "*"},
                new String[] {
                  BaseMessages.getString(
                      PKG, "HopGuiBusinessVaultGraph.AddDvReference.Browse.FilterHdv"),
                  BaseMessages.getString(
                      PKG, "HopGuiBusinessVaultGraph.AddDvReference.Browse.FilterAll")
                },
                true);
        if (Utils.isEmpty(browsed)) {
          return;
        }
        modelFilenameForAlias = browsed;
        sourceModel =
            BusinessVaultDvReferenceSupport.loadDvModel(
                browsed, model.getFilename(), getVariables(), hopGui.getMetadataProvider());
      } else if (chosenSource.linkedModel()) {
        sourceModel = dataVaultModel;
        if (sourceModel == null) {
          // Optional legacy default path on older models.
          sourceModel =
              BusinessVaultDvReferenceSupport.loadDvModel(
                  chosenSource.modelFilename(),
                  model.getFilename(),
                  getVariables(),
                  hopGui.getMetadataProvider());
        }
        // Persist path on the alias so multi-model resolution does not need a global default.
        modelFilenameForAlias = chosenSource.modelFilename();
      } else {
        sourceModel =
            BusinessVaultDvReferenceSupport.loadDvModel(
                chosenSource.modelFilename(),
                model.getFilename(),
                getVariables(),
                hopGui.getMetadataProvider());
        modelFilenameForAlias = chosenSource.modelFilename();
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddDvReference.Error.Title"),
          e.getMessage(),
          e);
      return;
    }

    if (sourceModel == null) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddDvReference.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiBusinessVaultGraph.AddDvReference.Error.MissingDvModel"),
          null);
      return;
    }

    List<String> choices =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(
            sourceModel, model, tableType, modelFilenameForAlias);
    if (choices.isEmpty()) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddDvReference.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiBusinessVaultGraph.AddDvReference.Error.NoTables", tableType.name()),
          null);
      return;
    }

    String title =
        BaseMessages.getString(
            PKG, "HopGuiBusinessVaultGraph.AddDvReference.Dialog.Title", tableType.name());
    String message =
        BaseMessages.getString(
            PKG, "HopGuiBusinessVaultGraph.AddDvReference.Dialog.Message", tableType.name());
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(getShell(), choices.toArray(new String[0]), title, message);
    String selectedName = dialog.open();
    if (Utils.isEmpty(selectedName)) {
      return;
    }

    IDvTable dvTable = sourceModel.findTable(selectedName);
    if (dvTable == null) {
      return;
    }

    int x = click != null ? click.x : 50;
    int y = click != null ? click.y : 50;
    BvDvTableReference reference =
        BusinessVaultDvReferenceSupport.createReference(
            dvTable, new Point(x, y), modelFilenameForAlias);
    if (reference == null) {
      return;
    }
    model.getDvReferences().add(reference);
    setChanged();
    redraw();
  }

  private void addBvReferenceAtClick(Point click) {
    if (model == null) {
      return;
    }

    List<BusinessVaultBvReferenceSupport.BvModelSource> sources =
        BusinessVaultBvReferenceSupport.listBvModelSources(
            model, getVariables(), hopGui.getMetadataProvider());

    BusinessVaultBvReferenceSupport.BvModelSource chosenSource;
    if (sources.size() == 1 && !sources.get(0).browseFile()) {
      chosenSource = sources.get(0);
    } else {
      String[] sourceLabels =
          sources.stream()
              .map(BusinessVaultBvReferenceSupport.BvModelSource::label)
              .toArray(String[]::new);
      EnterSelectionDialog sourceDialog =
          new EnterSelectionDialog(
              getShell(),
              sourceLabels,
              BaseMessages.getString(
                  PKG, "HopGuiBusinessVaultGraph.AddBvReference.SelectModel.Title"),
              BaseMessages.getString(
                  PKG, "HopGuiBusinessVaultGraph.AddBvReference.SelectModel.Message"));
      String selectedSource = sourceDialog.open();
      if (Utils.isEmpty(selectedSource)) {
        return;
      }
      chosenSource =
          sources.stream().filter(s -> selectedSource.equals(s.label())).findFirst().orElse(null);
      if (chosenSource == null) {
        return;
      }
    }

    BusinessVaultModel sourceModel;
    String modelFilenameForAlias;
    try {
      if (chosenSource.browseFile()) {
        String browsed =
            BaseDialog.presentFileDialog(
                hopGui.getShell(),
                new String[] {"*.hbv", "*"},
                new String[] {
                  BaseMessages.getString(
                      PKG, "HopGuiBusinessVaultGraph.AddBvReference.Browse.FilterHbv"),
                  BaseMessages.getString(
                      PKG, "HopGuiBusinessVaultGraph.AddBvReference.Browse.FilterAll")
                },
                true);
        if (Utils.isEmpty(browsed)) {
          return;
        }
        modelFilenameForAlias = browsed;
        sourceModel =
            BusinessVaultBvReferenceSupport.loadBvModel(
                browsed, model.getFilename(), getVariables(), hopGui.getMetadataProvider());
      } else {
        modelFilenameForAlias = chosenSource.modelFilename();
        sourceModel =
            BusinessVaultBvReferenceSupport.loadBvModel(
                chosenSource.modelFilename(),
                model.getFilename(),
                getVariables(),
                hopGui.getMetadataProvider());
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Error.Title"),
          e.getMessage(),
          e);
      return;
    }

    if (sourceModel == null) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiBusinessVaultGraph.AddBvReference.Error.MissingBvModel"),
          null);
      return;
    }

    List<String> choices =
        BusinessVaultBvReferenceSupport.listAvailableBvTableNames(
            sourceModel, model, modelFilenameForAlias);
    if (choices.isEmpty()) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Error.NoTables"),
          null);
      return;
    }

    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            getShell(),
            choices.toArray(new String[0]),
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Dialog.Title"),
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.AddBvReference.Dialog.Message"));
    String selectedName = dialog.open();
    if (Utils.isEmpty(selectedName)) {
      return;
    }

    IBvTable bvTable = sourceModel.findTable(selectedName);
    if (bvTable == null) {
      return;
    }

    int x = click != null ? click.x : 50;
    int y = click != null ? click.y : 50;
    BvBvTableReference reference =
        BusinessVaultBvReferenceSupport.createReference(
            bvTable, new Point(x, y), modelFilenameForAlias);
    if (reference == null) {
      return;
    }
    model.getBvReferences().add(reference);
    setChanged();
    redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-hub-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddHubReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddHubReference.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void addHubReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addDvReferenceAtClick(DvTableType.HUB, context.getClick());
  }

  @GuiContextAction(
      id = "bv-graph-add-satellite-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddSatelliteReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddSatelliteReference.Tooltip",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void addSatelliteReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addDvReferenceAtClick(DvTableType.SATELLITE, context.getClick());
  }

  @GuiContextAction(
      id = "bv-graph-add-link-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddLinkReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddLinkReference.Tooltip",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void addLinkReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addDvReferenceAtClick(DvTableType.LINK, context.getClick());
  }

  @GuiContextAction(
      id = "bv-graph-delete-dv-reference",
      parentId = HopGuiBusinessVaultDvReferenceContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteDvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteDvReference.Tooltip",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void deleteDvReference(HopGuiBusinessVaultDvReferenceContext context) {
    BvDvTableReference reference = context.getReference();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    BusinessVaultModel bvModel = context.getModel();
    if (reference == null || graph == null || bvModel == null) {
      return;
    }
    graph.markUndoPoint();
    bvModel
        .getDvReferences()
        .removeIf(
            ref ->
                ref != null
                    && reference.getDvTableName() != null
                    && reference.getDvTableName().equalsIgnoreCase(ref.getDvTableName()));
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-bv-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddBvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddBvReference.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "0")
  public void addBvTableReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addBvReferenceAtClick(context.getClick());
  }

  @GuiContextAction(
      id = "bv-graph-delete-bv-reference",
      parentId = HopGuiBusinessVaultBvReferenceContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteBvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteBvReference.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void deleteBvReference(HopGuiBusinessVaultBvReferenceContext context) {
    BvBvTableReference reference = context.getReference();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    BusinessVaultModel bvModel = context.getModel();
    if (reference == null || graph == null || bvModel == null) {
      return;
    }
    graph.markUndoPoint();
    bvModel
        .getBvReferences()
        .removeIf(
            ref ->
                ref != null
                    && reference.getBvTableName() != null
                    && reference.getBvTableName().equalsIgnoreCase(ref.getBvTableName())
                    && Objects.equals(
                        reference.getReferencedModelFilename(), ref.getReferencedModelFilename()));
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-scd2",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddScd2.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddScd2.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void addScd2Table(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addBvTableAtClick(new BvScd2Table(), context.getClick());
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-pit",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddPit.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddPit.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void addPitTable(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addBvTableAtClick(new BvPitTable(), context.getClick());
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-business-table",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddBusinessTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddBusinessTable.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addBusinessTable(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addBvTableAtClick(new BvBusinessTable(), context.getClick());
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-add-note",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Business Vault",
      categoryOrder = "4")
  public void addNote(HopGuiBusinessVaultContext context) {
    Point click = context.getClick();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    BusinessVaultModel bvModel = context.getModel();
    if (bvModel == null || graph == null) {
      return;
    }
    graph.markUndoPoint();
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    PropsUi.setLocation(note, click != null ? click.x : 50, click != null ? click.y : 50);
    PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
    bvModel.getNotes().add(note);
    graph.editNote(note, false);
    graph.setChanged();
  }

  @GuiContextAction(
      id = "bv-graph-edit-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.EditNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void editNoteAction(HopGuiBusinessVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (note != null && graph != null) {
      graph.editNote(note);
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiBusinessVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    BusinessVaultModel bvModel = context.getModel();
    if (note != null && bvModel != null && graph != null) {
      graph.markUndoPoint();
      if (bvModel.getNotes().remove(note)) {
        graph.setChanged();
        graph.redraw();
      }
    }
  }

  @GuiContextAction(
      id = "bv-graph-ai-help",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.AiHelp.Tooltip",
      image = "datavault-ai-help.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorContext(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.openAiAdvisor();
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void pasteFromClipboard(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void pasteFromClipboardOnTable(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void pasteFromClipboardOnNote(HopGuiBusinessVaultNoteContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "bv-graph-copy-update-action",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "1")
  public void copyUpdateActionToClipboard(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.copyBusinessVaultUpdateActionToClipboard();
    }
  }

  @GuiContextAction(
      id = "bv-graph-edit-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.Context.EditTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.EditTable.Tooltip",
      image = "ui/images/edit.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void editBvTableAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if (graph != null) {
      graph.editBvTable(context.getTable());
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteTable.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void deleteBvTable(HopGuiBusinessVaultTableContext context) {
    IBvTable table = context.getTable();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    BusinessVaultModel bvModel = context.getModel();
    if (table == null || graph == null || bvModel == null) {
      return;
    }
    graph.markUndoPoint();
    bvModel.getTables().removeIf(t -> t != null && table.getName().equals(t.getName()));
    graph.setChanged();
    graph.redraw();
  }

  @GuiContextAction(
      id = "bv-graph-show-build-pipeline",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiBusinessVaultGraph.Context.ShowBuildPipeline.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.ShowBuildPipeline.Tooltip",
      image = "ui/images/debug.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void showBuildPipelineAction(HopGuiBusinessVaultTableContext context) {
    IBvTable table = context.getTable();
    HopGuiBusinessVaultGraph graph = context.getBusinessVaultGraph();
    if ((table instanceof BvScd2Table || table instanceof BvPitTable) && graph != null) {
      graph.openBuildPipeline(table);
    }
  }

  @GuiContextAction(
      id = "bv-graph-preview-target-layout",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Business Vault",
      categoryOrder = "4")
  public void previewTargetLayoutAction(HopGuiBusinessVaultTableContext context) {
    IBvTable table = context.getTable();
    BusinessVaultModel bvModel = context.getModel();
    if (table == null) {
      return;
    }
    ModelTableLayoutPreviewSupport.previewBvTableLayout(
        hopGui.getShell(), getVariables(), hopGui.getMetadataProvider(), bvModel, table);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.EditModel.Tooltip",
      image = "business-vault-model.svg")
  public void editModelProperties() {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiBusinessVaultModelDialog dialog =
        new HopGuiBusinessVaultModelDialog(getShell(), hopGui, model);
    if (dialog.open()) {
      commitDialogUndo(beforeChange);
      setChanged();
      reloadDataVaultModel();
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
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.CheckModel.Tooltip",
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
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.AiHelp.Tooltip",
      image = "datavault-ai-help.svg")
  public void openAiAdvisor() {
    if (model == null) {
      return;
    }
    new BvAiAdvisorDialog(
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
      id = TOOLBAR_ITEM_EXPORT_SVG,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.ExportSvg.Tooltip",
      image = "ui/images/image.svg")
  public void exportModelToSvg() {
    if (model == null) {
      return;
    }
    try {
      SvgRenderOptions options = SvgRenderOptions.defaults();
      String svgXml =
          SvgExportService.generateBusinessVaultModelSvg(
              model, options, variables, hopGui.getMetadataProvider());

      String proposedName = Const.NVL(model.getName(), "business-vault-model") + ".svg";
      String proposedFilename =
          variables.getVariable("user.home") + java.io.File.separator + proposedName;

      String filenameFromUser =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getShell(),
              null,
              variables,
              HopVfs.getFileObject(proposedFilename),
              new String[] {"*.svg"},
              new String[] {"SVG Files"},
              true);
      if (filenameFromUser == null) {
        return;
      }

      String realFilename = variables.resolve(filenameFromUser);
      var file = HopVfs.getFileObject(realFilename);
      if (file.exists()) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        box.setText(BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.ExportSvg.Exists.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.ExportSvg.Exists.Message"));
        if ((box.open() & SWT.YES) == 0) {
          return;
        }
      }

      try (OutputStream outputStream = HopVfs.getOutputStream(file, false)) {
        outputStream.write(svgXml.getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.ExportSvg.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.ExportSvg.Error.Message"),
          e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_RELOAD_DV,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.ReloadDv.Tooltip",
      image = "datavault-model.svg")
  public void reloadDvModel() {
    reloadDataVaultModel();
    if (!Utils.isEmpty(dataVaultLoadError)) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.ReloadDv.Error.Title"),
          dataVaultLoadError,
          null);
    }
  }

  private static boolean hasCheckErrors(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  /**
   * Returns false when the model has check errors (dialog already shown) or check was cancelled.
   */
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

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_GENERATE_DDL,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.GenerateDdl.Tooltip",
      image = "ui/images/database.svg")
  public void generateModelDdl() {
    if (model == null) {
      return;
    }
    if (!ensureDataVaultModelLoaded()) {
      return;
    }

    List<String> ddlStatements = new ArrayList<>();
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      try {
        for (String ddl :
            table.generateBuildDdl(
                hopGui.getMetadataProvider(), hopGui.getVariables(), model, dataVaultModel)) {
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
            BvTargetDatabaseSupport.loadTargetDatabase(
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
      box.setText(BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NoDdlNeeded.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NoDdlNeeded.Message"));
      box.open();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DEBUG,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Debug.Tooltip",
      image = "ui/images/debug.svg")
  public void debugPipelines() {
    if (model == null) {
      return;
    }
    if (!validateModelForDebug()) {
      return;
    }
    if (!ensureDataVaultModelLoaded()) {
      return;
    }
    IVariables debugVariables = getVariables();
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    if (config.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE) {
      debugStagingWorkflow(debugVariables);
      return;
    }
    for (IBvTable table : model.getTables()) {
      if (table == null || !(table instanceof BvScd2Table || table instanceof BvPitTable)) {
        continue;
      }
      if (!table.isSelected() && nrSelectedBvTables() > 0) {
        continue;
      }
      openBuildPipeline(table, debugVariables);
    }
  }

  public void openBuildPipeline(IBvTable table) {
    if (!validateModelForDebug()) {
      return;
    }
    if (!ensureDataVaultModelLoaded()) {
      return;
    }
    openBuildPipeline(table, getVariables());
  }

  private boolean ensureDataVaultModelLoaded() {
    reloadDataVaultModel();
    if (dataVaultModel != null) {
      return true;
    }
    // SQL-only BV models need no DV universe; SCD2/PIT need canvas DV aliases.
    boolean needsDv =
        model != null
            && model.getTables().stream()
                .anyMatch(t -> t instanceof BvScd2Table || t instanceof BvPitTable);
    if (!needsDv) {
      dataVaultModel = new org.apache.hop.datavault.metadata.DataVaultModel();
      return true;
    }
    new ErrorDialog(
        hopGui.getShell(),
        BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.Title"),
        !Utils.isEmpty(dataVaultLoadError)
            ? dataVaultLoadError
            : BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.MissingDvModel"),
        null);
    return false;
  }

  private void debugStagingWorkflow(IVariables debugVariables) {
    try {
      List<PipelineMeta> pipelineMetas = new ArrayList<>();
      for (IBvTable table :
          BusinessVaultUpdateExecutionSupport.orderTablesForPipelineExecution(
              model.getTables(), model, dataVaultModel)) {
        if (!(table instanceof BvScd2Table
            || table instanceof BvPitTable
            || table instanceof BvBusinessTable)) {
          continue;
        }
        if (!table.isSelected() && nrSelectedBvTables() > 0) {
          continue;
        }
        List<PipelineMeta> generated =
            table.generateBuildPipelines(
                hopGui.getMetadataProvider(), debugVariables, model, dataVaultModel);
        if (generated != null) {
          pipelineMetas.addAll(generated);
        }
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta != null) {
          ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(
              hopGui, pipelineMeta, debugVariables);
        }
      }

      if (pipelineMetas.isEmpty()) {
        return;
      }

      BusinessVaultConfiguration config = model.getConfigurationOrDefault();
      org.apache.hop.core.database.DatabaseMeta targetDatabase =
          BvTargetDatabaseSupport.loadTargetDatabase(hopGui.getMetadataProvider(), config);
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
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.StagingWorkflow"),
          e);
    }
  }

  private void openBuildPipeline(IBvTable table, IVariables debugVariables) {
    if (!(table instanceof BvScd2Table || table instanceof BvPitTable)) {
      return;
    }
    String tableName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    try {
      List<PipelineMeta> pipelineMetas =
          table.generateBuildPipelines(
              hopGui.getMetadataProvider(), debugVariables, model, dataVaultModel);
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
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Debug.Error.Pipeline", tableName),
          e);
    }
  }

  private int nrSelectedBvTables() {
    if (model == null) {
      return 0;
    }
    int count = 0;
    for (IBvTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        count++;
      }
    }
    return count;
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
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.ZoomFit.Tooltip",
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
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Message"),
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
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  private void showCheckResultsDialog(List<ICheckResult> remarks) {
    CheckResultDialog dialog = new CheckResultDialog(hopGui.getShell(), remarks);
    dialog.open();
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
    if (restored instanceof BusinessVaultModel businessVaultModel) {
      model = businessVaultModel;
      reloadDataVaultModel();
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
    mouseOverBvTableName = null;
    mouseOverDvReferenceName = null;
    mouseOverBvReferenceName = null;
    mouseOverNoteLink = null;
  }

  @Override
  protected String undoRecordErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Record.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.Undo.Error.Apply.Message");
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
    return new HopGuiBusinessVaultNoteContext(model, this, note, real);
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NoteContext.Message");
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NoteLink.TableTooltip", target);
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NoteLink.Error.Title");
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return BaseMessages.getString(
        PKG, "HopGuiBusinessVaultGraph.NoteLink.UrlError.Message", target);
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return BaseMessages.getString(
        PKG, "HopGuiBusinessVaultGraph.NoteLink.TableNotFound.Message", tableName);
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {
    navigateToTable(tableName);
  }

  /** Selects and centers a business vault table or DV/BV reference on this graph canvas. */
  public void navigateToTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    IBvTable bvTable = model.findTable(tableName);
    if (bvTable != null) {
      mouseInteractions().unselectAllOnCanvas();
      bvTable.setSelected(true);
      centerOnBvTable(bvTable);
      redraw();
      updateGui();
      return;
    }
    BvDvTableReference reference = model.findDvReference(tableName);
    if (reference != null) {
      mouseInteractions().unselectAllOnCanvas();
      reference.setSelected(true);
      centerOnDvReference(reference);
      redraw();
      updateGui();
      return;
    }
    BvBvTableReference bvReference = model.findBvReference(tableName);
    if (bvReference != null) {
      mouseInteractions().unselectAllOnCanvas();
      bvReference.setSelected(true);
      centerOnBvReference(bvReference);
      redraw();
      updateGui();
      return;
    }
    showNoteLinkTableNotFound(tableName);
  }

  private void centerOnBvTable(IBvTable table) {
    if (table == null) {
      return;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = 140;
    int boxH = 70;
    if (table instanceof BvTableBase base) {
      if (base.getDrawnBoxWidth() > 0) {
        boxW = base.getDrawnBoxWidth();
      }
      if (base.getDrawnBoxHeight() > 0) {
        boxH = base.getDrawnBoxHeight();
      }
    }
    centerOnCanvasLocation(loc, boxW, boxH);
  }

  private void navigateToReferencedDvTable(BvDvTableReference reference) {
    if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
      return;
    }
    try {
      BusinessVaultDvNavigationSupport.navigateToDvTable(hopGui, model, reference, getVariables());
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NavigateDvReference.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void navigateToReferencedBvTable(BvBvTableReference reference) {
    if (reference == null || Utils.isEmpty(reference.getBvTableName())) {
      return;
    }
    try {
      BusinessVaultBvNavigationSupport.navigateToBvTable(hopGui, model, reference, getVariables());
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.NavigateBvReference.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void centerOnDvReference(BvDvTableReference reference) {
    if (reference == null) {
      return;
    }
    Point loc = reference.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = Math.max(140, reference.getDrawnBoxWidth());
    int boxH = Math.max(70, reference.getDrawnBoxHeight());
    centerOnCanvasLocation(loc, boxW, boxH);
  }

  private void centerOnBvReference(BvBvTableReference reference) {
    if (reference == null) {
      return;
    }
    Point loc = reference.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = Math.max(140, reference.getDrawnBoxWidth());
    int boxH = Math.max(70, reference.getDrawnBoxHeight());
    centerOnCanvasLocation(loc, boxW, boxH);
  }

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public org.apache.hop.core.search.ISearchable createSearchable(
      String locationDescription, org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws org.apache.hop.core.exception.HopException {
    return new org.apache.hop.datavault.hopgui.search.HopGuiBusinessVaultModelSearchable(
        locationDescription, model);
  }

  /** Select and open the BV table matching {@code componentName} (search navigation). */
  public void openSearchComponent(String componentName) {
    if (Utils.isEmpty(componentName) || model == null) {
      return;
    }
    IBvTable table = model.findTable(componentName);
    if (table == null) {
      return;
    }
    for (IBvTable t : model.getTables()) {
      if (t != null) {
        t.setSelected(false);
      }
    }
    table.setSelected(true);
    editBvTable(table);
    updateGui();
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : "Business Vault Model";
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

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model == null) {
      return;
    }
    for (IBvTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(true);
      }
    }
    for (DvNote note : model.getNotes()) {
      if (note != null) {
        note.setSelected(true);
      }
    }
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference != null) {
        reference.setSelected(true);
      }
    }
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference != null) {
        reference.setSelected(true);
      }
    }
    redraw();
  }

  @GuiKeyboardShortcut(key = SWT.ESC)
  @GuiOsxKeyboardShortcut(key = SWT.ESC)
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNSELECT_ALL,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.UnselectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/unselect-all.svg")
  @Override
  public void unselectAll() {
    mouseInteractions().unselectAllOnCanvas();
    clearSelectionRegion();
    redraw();
  }

  public void copyBusinessVaultUpdateActionToClipboard() {
    if (model == null) {
      return;
    }
    if (!ensureModelSavedBeforeClipboard()) {
      return;
    }
    try {
      ActionBusinessVaultUpdate updateAction = loadStoredBusinessVaultUpdateAction();
      updateAction.setBusinessVaultModelFile(getFilename());
      ModelUpdateWorkflowClipboardSupport.copyUpdateWorkflowToClipboard(
          hopGui, updateAction, getVariables(), getFilename());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CopyUpdateAction.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CopyUpdateAction.Error.Message"),
          e);
    }
  }

  private ActionBusinessVaultUpdate loadStoredBusinessVaultUpdateAction() throws HopException {
    String actionXml =
        ModelUpdateActionAuditSupport.retrieveActionXml(
            BUSINESS_VAULT_UPDATE_AUDIT_GROUP, BUSINESS_VAULT_UPDATE_AUDIT_TYPE, getFilename());
    if (!Utils.isEmpty(actionXml)) {
      Node actionNode = XmlHandler.loadXmlString(actionXml, ActionMeta.XML_TAG);
      ActionMeta actionMeta =
          new ActionMeta(actionNode, hopGui.getMetadataProvider(), getVariables());
      if (actionMeta.getAction() instanceof ActionBusinessVaultUpdate storedAction) {
        return storedAction;
      }
    }
    return new ActionBusinessVaultUpdate();
  }

  private boolean ensureModelSavedBeforeClipboard() {
    if (!hasChanged()) {
      return true;
    }

    MessageBox messageDialog =
        new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
    messageDialog.setText(
        BaseMessages.getString(
            PKG, "HopGuiBusinessVaultGraph.CopyUpdateAction.Save.Dialog.Header"));
    messageDialog.setMessage(
        BaseMessages.getString(
            PKG, "HopGuiBusinessVaultGraph.CopyUpdateAction.Save.Dialog.Message", buildTabName()));
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
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.SaveFile.Error.Header"),
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.SaveFile.Error.Message"),
            e);
        return false;
      }
    }
    return (answer & SWT.NO) != 0;
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COPY,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Copy.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/copy.svg",
      separator = true)
  @GuiKeyboardShortcut(control = true, key = 'c')
  @GuiOsxKeyboardShortcut(command = true, key = 'c')
  @Override
  public void copySelectedToClipboard() {
    if (clipboardDelegate == null || model == null) {
      return;
    }
    clipboardDelegate.copySelected(getSelectedBvTables(), getSelectedNotes());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CUT,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Cut.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/cut.svg")
  @GuiKeyboardShortcut(control = true, key = 'x')
  @GuiOsxKeyboardShortcut(command = true, key = 'x')
  @Override
  public void cutSelectedToClipboard() {
    if (clipboardDelegate == null || model == null) {
      return;
    }
    clipboardDelegate.copySelected(getSelectedBvTables(), getSelectedNotes());
    deleteSelected();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Delete.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  @Override
  public void deleteSelected() {
    if (model == null) {
      return;
    }
    List<IBvTable> tablesToDelete = getSelectedBvTables();
    List<DvNote> notesToDelete = getSelectedNotes();
    List<BvDvTableReference> referencesToDelete = getSelectedDvReferences();
    List<BvBvTableReference> bvReferencesToDelete = getSelectedBvReferences();
    if (tablesToDelete.isEmpty()
        && notesToDelete.isEmpty()
        && referencesToDelete.isEmpty()
        && bvReferencesToDelete.isEmpty()) {
      return;
    }

    markUndoPoint();
    boolean modelChanged = false;
    if (model.getTables().removeAll(tablesToDelete)) {
      modelChanged = true;
    }
    if (model.getNotes().removeAll(notesToDelete)) {
      modelChanged = true;
    }
    if (model.getDvReferences().removeAll(referencesToDelete)) {
      modelChanged = true;
    }
    if (model.getBvReferences().removeAll(bvReferencesToDelete)) {
      modelChanged = true;
    }
    if (modelChanged) {
      setChanged();
      redraw();
    }
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
      toolTip = "i18n::HopGuiBusinessVaultGraph.Toolbar.Paste.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/paste.svg")
  @GuiKeyboardShortcut(control = true, key = 'v')
  @GuiOsxKeyboardShortcut(command = true, key = 'v')
  @Override
  public void pasteFromClipboard() {
    Point location = lastClick != null ? new Point(lastClick.x, lastClick.y) : null;
    pasteFromClipboard(location);
  }

  public void applyPasteResult(List<IBvTable> tables, List<DvNote> notes) {
    if (model == null) {
      return;
    }
    boolean modelChanged = false;
    mouseInteractions().unselectAllOnCanvas();

    if (tables != null) {
      for (IBvTable table : tables) {
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
    return !getSelectedBvTables().isEmpty()
        || !getSelectedNotes().isEmpty()
        || !getSelectedDvReferences().isEmpty();
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
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiBusinessVaultGraph.SaveFile.Dialog.Message", buildTabName()));
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
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.SaveFile.Error.Message"),
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
    List<IBvTable> selection = getSelectedBvTables();
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

  private final class BusinessVaultMouseInteractions implements ModelGraphMouseInteractions {

    @Override
    public ModelGraphHit resolveHit(int logicalX, int logicalY) {
      AreaOwner areaOwner = getVisibleAreaOwner(logicalX, logicalY);
      IBvTable bvTable = getAreaOwnerBvTable(areaOwner);
      BvDvTableReference dvReference = getAreaOwnerDvReference(areaOwner);
      BvBvTableReference bvReference = getAreaOwnerBvReference(areaOwner);
      DvNote note = getAreaOwnerNote(areaOwner);
      AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
      Object canvasObject =
          bvTable != null ? bvTable : dvReference != null ? dvReference : bvReference;
      return new ModelGraphHit(areaOwner, areaType, note, canvasObject);
    }

    @Override
    public boolean handleObjectMouseDown(
        Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
      AreaOwner.AreaType areaType = hit.areaType();
      Object obj = hit.canvasObject();

      if (obj instanceof IBvTable bvHit) {
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
          avoidContextDialog = true;
          editBvTable(bvHit);
          return true;
        }
        if (areaType == AreaOwner.AreaType.TRANSFORM_ICON
            && (e.button == 2 || (e.button == 1 && shift))) {
          startRelationshipDragFromBvTable(bvHit, e.x, e.y);
          return true;
        }
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
          prepareExclusiveDragSelection(control, bvHit.isSelected(), () -> bvHit.setSelected(true));
          currentBvTable = bvHit;
          currentDvReference = null;
          currentBvReference = null;
          iconDragStart = new Point(real.x, real.y);
          iconDragCommitted = false;
          Point loc = bvHit.getLocation() != null ? bvHit.getLocation() : new Point(0, 0);
          iconOffset = new Point(real.x - loc.x, real.y - loc.y);
          clearNoteDragState();
          clearSelectionRegion();
          redraw();
          return true;
        }
      }

      if (obj instanceof BvDvTableReference dvRefHit) {
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
          avoidContextDialog = true;
          navigateToReferencedDvTable(dvRefHit);
          return true;
        }
        if (areaType == AreaOwner.AreaType.TRANSFORM_ICON
            && (e.button == 2 || (e.button == 1 && shift))) {
          startRelationshipDragFromDvReference(dvRefHit, e.x, e.y);
          return true;
        }
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
          prepareExclusiveDragSelection(
              control, dvRefHit.isSelected(), () -> dvRefHit.setSelected(true));
          currentDvReference = dvRefHit;
          currentBvTable = null;
          currentBvReference = null;
          iconDragStart = new Point(real.x, real.y);
          iconDragCommitted = false;
          Point loc = dvRefHit.getLocation() != null ? dvRefHit.getLocation() : new Point(0, 0);
          iconOffset = new Point(real.x - loc.x, real.y - loc.y);
          clearNoteDragState();
          clearSelectionRegion();
          redraw();
          return true;
        }
      }

      if (obj instanceof BvBvTableReference bvRefHit) {
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
          avoidContextDialog = true;
          navigateToReferencedBvTable(bvRefHit);
          return true;
        }
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
          prepareExclusiveDragSelection(
              control, bvRefHit.isSelected(), () -> bvRefHit.setSelected(true));
          currentBvReference = bvRefHit;
          currentBvTable = null;
          currentDvReference = null;
          iconDragStart = new Point(real.x, real.y);
          iconDragCommitted = false;
          Point loc = bvRefHit.getLocation() != null ? bvRefHit.getLocation() : new Point(0, 0);
          iconOffset = new Point(real.x - loc.x, real.y - loc.y);
          clearNoteDragState();
          clearSelectionRegion();
          redraw();
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean isRelationshipDragActive() {
      return HopGuiBusinessVaultGraph.this.isRelationshipDragActive();
    }

    @Override
    public String webRelationshipStartNodeName() {
      if (startRelationshipBvTable != null) {
        return startRelationshipBvTable.getName();
      }
      if (startRelationshipDvReference != null) {
        return startRelationshipDvReference.getDvTableName();
      }
      return null;
    }

    @Override
    public void handleRelationshipMouseMove(Event e) {
      relationshipDragEndLocation = new Point(e.x, e.y);
      updateRelationshipCandidates(e.x, e.y);
    }

    @Override
    public boolean handleRelationshipMouseUp(Event e, Point real) {
      mouseUpCreateRelationship(e);
      return true;
    }

    @Override
    public boolean handleObjectMouseMove(Point real, boolean leftButtonDown) {
      if (!leftButtonDown || resize != null) {
        return false;
      }
      boolean doRedraw = false;
      if (currentBvTable != null) {
        currentBvTable.setSelected(true);
        doRedraw = mouseMoveCanvasObject(real, currentBvTable) || doRedraw;
      }
      if (currentDvReference != null) {
        currentDvReference.setSelected(true);
        doRedraw = mouseMoveCanvasObject(real, currentDvReference) || doRedraw;
      }
      if (currentBvReference != null) {
        currentBvReference.setSelected(true);
        doRedraw = mouseMoveCanvasObject(real, currentBvReference) || doRedraw;
      }
      return doRedraw;
    }

    @Override
    public boolean handleNoteMouseMove(Point real) {
      if (selectedNote == null || noteOffset == null) {
        return false;
      }
      Point notePos = new Point(real.x - noteOffset.x, real.y - noteOffset.y);
      if (!noteWasMoved) {
        int dx = notePos.x - selectedNote.getLocation().x;
        int dy = notePos.y - selectedNote.getLocation().y;
        if (dx != 0 || dy != 0) {
          captureDragStartLocations(selectedNote);
          markPositionUndoPoint();
          noteWasMoved = true;
        }
      }
      if (noteWasMoved) {
        applyDragPositions(notePos);
        avoidContextDialog = true;
        return true;
      }
      return false;
    }

    @Override
    public boolean hasCancellableDragState() {
      return isRelationshipDragActive()
          || currentBvTable != null
          || currentDvReference != null
          || currentBvReference != null
          || iconDragStart != null
          || currentNote != null
          || noteDragStart != null
          || selectionRegion != null;
    }

    @Override
    public void cancelActiveDragsOnBackgroundClick() {
      cancelRelationshipDrag();
      clearCanvasDragState();
    }

    @Override
    public void clearObjectDragState() {
      clearCanvasDragState();
    }

    @Override
    public void unselectAllOnCanvas() {
      if (model == null) {
        return;
      }
      for (IBvTable table : model.getTables()) {
        if (table != null) {
          table.setSelected(false);
        }
      }
      for (DvNote note : model.getNotes()) {
        if (note != null) {
          note.setSelected(false);
        }
      }
      for (BvDvTableReference reference : model.getDvReferences()) {
        if (reference != null) {
          reference.setSelected(false);
        }
      }
      for (BvBvTableReference reference : model.getBvReferences()) {
        if (reference != null) {
          reference.setSelected(false);
        }
      }
    }

    @Override
    public void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
      if (model == null) {
        return;
      }
      for (IBvTable table : model.getTables()) {
        if (isBvTableInLassoScreenRect(table, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          table.setSelected(true);
        }
      }
      for (DvNote note : model.getNotes()) {
        if (isNoteInLassoScreenRect(note, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          note.setSelected(true);
        }
      }
      for (BvDvTableReference reference : model.getDvReferences()) {
        if (isDvReferenceInLassoScreenRect(reference, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          reference.setSelected(true);
        }
      }
      for (BvBvTableReference reference : model.getBvReferences()) {
        if (isBvReferenceInLassoScreenRect(reference, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          reference.setSelected(true);
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
      clearCanvasDragState();
      clearNoteDragState();
      avoidContextDialog = false;
      return true;
    }

    @Override
    public boolean handlePureClickMouseUp(Event e, Point real) {
      if (lastClick == null || lastClick.x != real.x || lastClick.y != real.y) {
        return false;
      }

      if (handleNoteLinkClickAt(real)) {
        clearCanvasDragState();
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

      IBvTable hit = currentBvTable;
      if (hit != null) {
        if (isControlDown(e)) {
          hit.setSelected(!hit.isSelected());
          redraw();
        } else if (!avoidContextDialog) {
          showBvTableContextDialog(e, hit);
        }
        clearCanvasDragState();
        avoidContextDialog = false;
        return true;
      }

      BvDvTableReference dvHit = currentDvReference;
      if (dvHit != null) {
        if (isControlDown(e)) {
          dvHit.setSelected(!dvHit.isSelected());
          redraw();
        } else if (!avoidContextDialog) {
          showDvReferenceContextDialog(e, dvHit);
        }
        clearCanvasDragState();
        avoidContextDialog = false;
        return true;
      }

      BvBvTableReference bvRefHit = currentBvReference;
      if (bvRefHit != null) {
        if (isControlDown(e)) {
          bvRefHit.setSelected(!bvRefHit.isSelected());
          redraw();
        } else if (!avoidContextDialog) {
          showBvReferenceContextDialog(e, bvRefHit);
        }
        clearCanvasDragState();
        avoidContextDialog = false;
        return true;
      }

      if (!avoidContextDialog) {
        showBusinessVaultContextDialog(e, real);
        avoidContextDialog = true;
      }
      return true;
    }

    @Override
    public boolean clearHoverState() {
      if (mouseOverBvTableName == null
          && mouseOverDvReferenceName == null
          && mouseOverBvReferenceName == null
          && mouseOverNoteLink == null) {
        return false;
      }
      mouseOverBvTableName = null;
      mouseOverDvReferenceName = null;
      mouseOverBvReferenceName = null;
      mouseOverNoteLink = null;
      return true;
    }

    @Override
    public boolean updateHoverState(AreaOwner areaOwner, Point real) {
      String newBvOver = null;
      String newDvOver = null;
      String newBvRefOver = null;
      if (areaOwner != null
          && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
          && !isRelationshipDragActive()
          && !dragSelection
          && selectionRegion == null) {
        if (areaOwner.getParent() instanceof IBvTable bvTable && bvTable.getName() != null) {
          newBvOver = bvTable.getName();
        } else if (areaOwner.getParent() instanceof BvDvTableReference dvReference
            && dvReference.getDvTableName() != null) {
          newDvOver = dvReference.getDvTableName();
        } else if (areaOwner.getParent() instanceof BvBvTableReference bvReference
            && bvReference.getBvTableName() != null) {
          newBvRefOver = bvReference.getBvTableName();
        }
      }
      boolean doRedraw = false;
      if ((mouseOverBvTableName == null && newBvOver != null)
          || (mouseOverBvTableName != null && !mouseOverBvTableName.equals(newBvOver))) {
        mouseOverBvTableName = newBvOver;
        doRedraw = true;
      }
      if ((mouseOverDvReferenceName == null && newDvOver != null)
          || (mouseOverDvReferenceName != null && !mouseOverDvReferenceName.equals(newDvOver))) {
        mouseOverDvReferenceName = newDvOver;
        doRedraw = true;
      }
      if ((mouseOverBvReferenceName == null && newBvRefOver != null)
          || (mouseOverBvReferenceName != null && !mouseOverBvReferenceName.equals(newBvRefOver))) {
        mouseOverBvReferenceName = newBvRefOver;
        doRedraw = true;
      }
      Cursor hand = getDisplay().getSystemCursor(SWT.CURSOR_HAND);
      if (newDvOver != null) {
        if (!Objects.equals(getCanvasCursor(), hand)) {
          setCanvasCursor(hand);
          doRedraw = true;
        }
        String tip =
            BaseMessages.getString(
                PKG, "HopGuiBusinessVaultGraph.NavigateDvReference.Tooltip", newDvOver);
        if (!Objects.equals(canvas.getToolTipText(), tip)) {
          canvas.setToolTipText(tip);
        }
      } else if (newBvRefOver != null) {
        if (!Objects.equals(getCanvasCursor(), hand)) {
          setCanvasCursor(hand);
          doRedraw = true;
        }
        String tip =
            BaseMessages.getString(
                PKG, "HopGuiBusinessVaultGraph.NavigateBvReference.Tooltip", newBvRefOver);
        if (!Objects.equals(canvas.getToolTipText(), tip)) {
          canvas.setToolTipText(tip);
        }
      } else if (mouseOverBvTableName == null && getCanvasCursor() == hand) {
        setCanvasCursor(null);
        doRedraw = true;
      }
      return doRedraw;
    }

    @Override
    public void onLassoMouseDownAfter() {
      mouseOverBvTableName = null;
    }

    @Override
    public boolean isNoteMouseDownAllowed() {
      return true;
    }

    @Override
    public boolean isLassoMoveAllowed() {
      return true;
    }

    @Override
    public boolean allowEmptyLassoClearOnMouseUp() {
      return true;
    }

    @Override
    public boolean isNoteResizeHoverBlocked() {
      return isRelationshipDragActive()
          || dragSelection
          || selectionRegion != null
          || noteWasMoved
          || iconDragCommitted;
    }

    @Override
    public void prepareNavigationViewportDrag() {
      cancelRelationshipDrag();
      clearCanvasDragState();
      clearNoteDragState();
      clearSelectionRegion();
    }

    @Override
    public void refreshGui() {
      updateGui();
    }

    private boolean mouseMoveCanvasObject(Point real, Object dragAnchorObject) {
      if (iconOffset == null) {
        iconOffset = new Point(0, 0);
      }
      Point icon = new Point(real.x - iconOffset.x, real.y - iconOffset.y);
      boolean doRedraw = false;
      if (tryCommitIconDrag(real)) {
        captureDragStartLocations(dragAnchorObject);
        doRedraw = true;
      }
      // Hop Web arms iconDragCommitted on mouse-down (armWebObjectDragModes) so tryCommitIconDrag
      // never runs again — capture start locations lazily before applying the final move.
      if (iconDragCommitted && dragAnchorObject != null && hasSelectedCanvasObjects()) {
        if (dragStartLocations == null) {
          captureDragStartLocations(dragAnchorObject);
        }
        applyDragPositions(icon);
        avoidContextDialog = true;
        doRedraw = true;
      }
      return doRedraw;
    }
  }

  @Override
  public ICoachingModelAdapter createCoachingModelAdapter() {
    if (model == null) {
      return null;
    }
    return new BvCoachingModelAdapter(model, this::editBvTableByName, this::highlightBvTableByName);
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
    IBvTable table = null;
    String normalizedType = tableType.trim();
    if ("SCD2".equalsIgnoreCase(normalizedType)) {
      table = new BvScd2Table();
    } else if ("PIT".equalsIgnoreCase(normalizedType)) {
      table = new BvPitTable();
    }
    if (table == null) {
      return null;
    }
    if (!Utils.isEmpty(tableName)) {
      table.setName(tableName);
      table.setTableName(tableName.toLowerCase().replace(' ', '_'));
    } else {
      String generated = getUniqueBvTableName(table.getTableType().name());
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

  private void editBvTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    for (IBvTable table : model.getTables()) {
      if (table != null && tableName.equals(table.getName())) {
        editBvTable(table);
        return;
      }
    }
  }

  private void highlightBvTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    for (IBvTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(tableName.equals(table.getName()));
      }
    }
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference != null) {
        reference.setSelected(tableName.equals(reference.getDvTableName()));
      }
    }
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference != null) {
        reference.setSelected(tableName.equals(reference.getBvTableName()));
      }
    }
    redraw();
  }
}
