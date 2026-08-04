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
package org.apache.hop.datavault.hopgui.file.vault;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImportMenu;
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
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
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
import org.apache.hop.datavault.hopgui.ai.DvAiAdvisorDialog;
import org.apache.hop.datavault.hopgui.coaching.ICoachableModelGraph;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.vault.delegates.HopGuiVaultClipboardDelegate;
import org.apache.hop.datavault.hopgui.file.vault.delegates.HopGuiVaultSnapshotUndo;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvIntegerSettingValidationSupport;
import org.apache.hop.datavault.metadata.DvIntegrationSupport;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvLinkedTableSupport;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.DvModelLoadSupport;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.datavault.metadata.DvTableResolutionSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.DvUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.DvUpdateWorkflowSupport;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.DvCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.apache.hop.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdateDialog;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.EditRowsDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
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
import org.apache.hop.ui.hopgui.file.IGraphSnapAlignDistribute;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.start.ActionStart;
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
import org.eclipse.swt.widgets.Shell;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

/**
 * Basic implementation of the vault graph / editor for Data Vault models in Hop GUI. Uses a canvas
 * and DataVaultModelPainter to draw the model. Undo/redo uses gzip-compressed XML snapshots.
 * Implements IHopFileTypeHandler so it can be used as a tab in the explorer perspective.
 */
@GuiPlugin(id = "HopGuiVaultGraph", description = "i18n::HopGuiVaultGraph.Description")
@Getter
@Setter
public class HopGuiVaultGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher, IGraphSnapAlignDistribute, ICoachableModelGraph {
  private static final Class<?> PKG = HopGuiVaultGraph.class;

  private static final String DEBUG_VARIABLES_AUDIT_GROUP = "DataVault";
  private static final String DEBUG_VARIABLES_AUDIT_TYPE = "DebugVariables";
  private static final String DEBUG_VARIABLES_AUDIT_STATE_NAME = "values";

  private static final String DATA_VAULT_UPDATE_AUDIT_GROUP = "DataVault";
  private static final String DATA_VAULT_UPDATE_AUDIT_TYPE = "DataVaultUpdateAction";

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiVaultGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL = "HopGuiVaultGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiVaultGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT = "HopGuiVaultGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 = "HopGuiVaultGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT = "HopGuiVaultGraph-ToolBar-10040-Zoom-Fit";

  public static final String TOOLBAR_ITEM_SELECT_ALL = "HopGuiVaultGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiVaultGraph-ToolBar-20020-Unselect-All";
  public static final String TOOLBAR_ITEM_COPY = "HopGuiVaultGraph-ToolBar-20030-Copy";
  public static final String TOOLBAR_ITEM_CUT = "HopGuiVaultGraph-ToolBar-20040-Cut";
  public static final String TOOLBAR_ITEM_PASTE = "HopGuiVaultGraph-ToolBar-20050-Paste";
  public static final String TOOLBAR_ITEM_DELETE = "HopGuiVaultGraph-ToolBar-20060-Delete";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiVaultGraph-ToolBar-20070-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiVaultGraph-ToolBar-20080-Redo";

  public static final String TOOLBAR_ITEM_EDIT_MODEL = "HopGuiVaultGraph-ToolBar-10050-Edit-Model";

  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiVaultGraph-ToolBar-10060-Check-Model";
  public static final String TOOLBAR_ITEM_AI_HELP = "HopGuiVaultGraph-ToolBar-10065-AI-Help";

  public static final String TOOLBAR_ITEM_RUN_DATA_VAULT_UPDATE =
      "HopGuiVaultGraph-ToolBar-10065-Run-Data-Vault-Update";

  public static final String TOOLBAR_ITEM_DEBUG = "HopGuiVaultGraph-ToolBar-10070-Debug";

  public static final String TOOLBAR_ITEM_GENERATE_DDL =
      "HopGuiVaultGraph-ToolBar-10080-Generate-Ddl";

  public static final String TOOLBAR_ITEM_IMPORT_RECORD_DEFINITIONS =
      "HopGuiVaultGraph-ToolBar-10085-Import-Record-Definitions";
  public static final String TOOLBAR_ITEM_TOGGLE_COACH =
      "HopGuiVaultGraph-ToolBar-10084-Toggle-Coach";
  public static final String TOOLBAR_ITEM_TOGGLE_DURATIONS =
      "HopGuiVaultGraph-ToolBar-10086-Toggle-Durations";

  private final HopVaultFileType fileType;
  private final HopGuiVaultClipboardDelegate clipboardDelegate;
  private final HopGuiVaultSnapshotUndo snapshotUndo = new HopGuiVaultSnapshotUndo();
  private DataVaultModel model;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;

  private boolean changed = false;

  // Area owners for fine-grained hit testing (name vs body of table icon), like pipeline/workflow
  // graphs.
  // Used for mouse-over underline on name, click-name=edit, click-body=context.
  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;

  // Relationship drag state (middle-mouse-button or shift+left-button drag from table to table)
  // to create hub<->satellite or hub<->link relationships (stored as name refs in the tables).
  private IDvTable startRelationshipTable;
  private Point relationshipDragEndLocation; // screen coordinates for temp line
  private IDvTable candidateRelationshipTarget;

  // Drag state for moving tables: left-click + drag on table icon body (not the name part).
  // Moves the clicked table + all currently selected tables. Position relative to initial click.
  private IDvTable currentTable;

  /** No-arg constructor for the Hop GUI menu plugin dispatcher ({@link #menuFileExportToSvg()}). */
  public HopGuiVaultGraph() {
    this(createMenuDispatchParent(), HopGui.getInstance(), null, null, null);
  }

  private static Composite createMenuDispatchParent() {
    Shell shell = HopGui.getInstance().getShell();
    Composite parent = new Composite(shell, SWT.NONE);
    parent.setVisible(false);
    return parent;
  }

  public HopGuiVaultGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      DataVaultModel model,
      HopVaultFileType fileType) {
    super(hopGui, parent, perspective);
    this.model = model;
    this.fileType = fileType;
    this.clipboardDelegate = new HopGuiVaultClipboardDelegate(hopGui, this);

    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());

    if (model == null) {
      return;
    }

    // The layout in the widget is done using a FormLayout to allow toolbar at top + canvas
    setLayout(new FormLayout());

    // Add a tool-bar at the top of the composite (like HopGuiPipelineGraph / HopGuiWorkflowGraph)
    addToolBar();

    createModelGraphBody(toolBar, () -> canvas.addPaintListener(this::paintControl));

    hopGui.replaceKeyboardShortcutListeners(this);

    // Make sure we can receive focus etc.
    canvas.setFocus();

    // Initial zoom label
    setZoomLabel();

    // Ensure layout
    layout(true, true);
  }

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new VaultMouseInteractions();
  }

  @Override
  protected String getMetricsModelName() {
    return model != null ? model.getName() : null;
  }

  @Override
  protected String getMetricsModelType() {
    return GeneratedPipelineMetadataConstants.MODEL_TYPE_DV;
  }

  @Override
  protected List<String> getMetricsTableNames() {
    if (model == null || model.getTables() == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names;
  }

  private @Nullable IDvTable getAreaOwnerTable(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }

    if (areaOwner.getParent() instanceof IDvTable hit) {
      return hit;
    }
    return null;
  }

  private void centerOnTable(IDvTable table) {
    if (table == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return;
    }
    int boxW = 140;
    int boxH = 70;
    if (table instanceof DvTableBase base) {
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
    List<IDvTable> selectedTables = getSelectedTables();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selectedTables.isEmpty() && selectedNotes.isEmpty()) {
      return;
    }
    for (IDvTable table : selectedTables) {
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
    for (IDvTable table : selectedTables) {
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

  public static HopGuiVaultGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective perspective) {
      IHopFileTypeHandler typeHandler = perspective.getActiveFileTypeHandler();
      if (typeHandler instanceof HopGuiVaultGraph vaultGraph) {
        return vaultGraph;
      }
    }
    return null;
  }

  private void addToolBar() {
    try {
      // Create a new toolbar at the top of the main composite...
      // Use ToolbarFacade + IToolbarContainer for compatibility with Hop 2.x (SWT + web)
      //
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

      // enable / disable the icons in the toolbar too.
      //
      updateGui();

    } catch (Exception e) {
      HopGui.getInstance()
          .getLog()
          .logError("Error setting up the toolbar for HopGuiVaultGraph: ", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
      return; // nothing to do!
    }

    // Do double buffering to prevent flickering on Windows
    //
    boolean needsDoubleBuffering =
        Const.isWindows() && "GUI".equalsIgnoreCase(Const.getHopPlatformRuntime());

    Image image = null;
    GC swtGc = e.gc;

    if (needsDoubleBuffering) {
      image = new Image(hopGui.getDisplay(), area.x, area.y);
      swtGc = new GC(image);
    }

    drawVaultModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      // Draw the image onto the canvas and get rid of the resources
      //
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawVaultModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    Point area = new Point(canvas.getBounds().width, canvas.getBounds().height);
    maximum = model.getMaximum();

    try {
      DataVaultModelPainter painter =
          new DataVaultModelPainter(model, gc, variables, area.x, area.y);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification((float) (magnification * PropsUi.getNativeZoomFactor()));
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setSelectionRegion(selectionRegion);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverTableName(mouseOverTableName);
      painter.setMouseOverNoteLink(mouseOverNoteLink);
      painter.setNoteImageBaseFilename(getFilename());
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.setShowHashKeyFieldNames(
          DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel());
      painter.setMetadataProvider(hopGui.getMetadataProvider());
      painter.setMaximum(model.getMaximum());

      // Pass current (if any) relationship drag state so painter can render the candidate line
      // (in logical coords, before tables).
      painter.setRelationshipDragInfo(
          startRelationshipTable, relationshipDragEndLocation, candidateRelationshipTarget);

      painter.drawDataVaultModel();

      captureNavigationViewGeometry(painter);

      CanvasFacade.setData(canvas, magnification, offset, model);
    } finally {
      gc.dispose();
    }
  }

  @Override
  public void redraw() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
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
    if (restored instanceof DataVaultModel dataVaultModel) {
      restoreModelSnapshot(dataVaultModel);
    }
  }

  @Override
  protected String undoRecordErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Record.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Message");
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
    return new HopGuiVaultNoteContext(model, this, note, real);
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteContext.Message");
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.TableTooltip", target);
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.Error.Title");
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return BaseMessages.getString(PKG, "HopGuiVaultGraph.NoteLink.UrlError.Message", target);
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return BaseMessages.getString(
        PKG, "HopGuiVaultGraph.NoteLink.TableNotFound.Message", tableName);
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {
    navigateToTable(tableName);
  }

  private void navigateToReferencedTable(DvLinkedTable reference) {
    if (reference == null) {
      return;
    }
    try {
      DvLinkedTableNavigationSupport.navigateToSourceTable(
          hopGui,
          model,
          this,
          reference,
          getVariables(),
          hopGui != null ? hopGui.getMetadataProvider() : null);
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.GoToReferencedTable.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  /** Selects and centers a table on this graph canvas. */
  public void navigateToTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    IDvTable table = model.findTable(tableName);
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

  // --- Zoom / toolbar actions with @GuiToolbarElement (modeled on HopGuiPipelineGraph) ---

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
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.ZoomFit.Tooltip",
      image = "ui/images/zoom-fit.svg")
  public void fitToScreen() {
    performZoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.EditModel.Tooltip",
      image = "datavault-model.svg")
  public void editModelProperties() {
    editModelProperties(model);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_IMPORT_RECORD_DEFINITIONS,
      toolTip = "i18n::HopGuiVaultGraph.ImportSources.Tooltip",
      image = "data-catalog.svg")
  public void importCatalogRecordDefinitions() {
    DataCatalogPerspective dcp = DataCatalogPerspective.getInstance();
    if (dcp != null) {
      DataCatalogImportMenu.open(hopGui, model, null, () -> dcp.refresh());
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
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.CheckModel.Tooltip",
      image = "ui/images/check.svg")
  public void checkModel() {
    if (model == null) {
      return;
    }
    showCheckResultsDialog(runModelCheck());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_AI_HELP,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.AiHelp.Tooltip",
      image = "datavault-ai-help.svg")
  public void openAiAdvisor() {
    if (model == null) {
      return;
    }
    new DvAiAdvisorDialog(
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
      id = TOOLBAR_ITEM_RUN_DATA_VAULT_UPDATE,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.RunDataVaultUpdate.Tooltip",
      image = "ui/images/run.svg")
  public void runDataVaultUpdate() {
    if (model == null) {
      return;
    }
    if (!ensureModelSavedBeforeRun()) {
      return;
    }

    try {
      ActionDataVaultUpdate updateAction = loadStoredDataVaultUpdateAction();
      updateAction.setDataVaultModelFile(getFilename());

      WorkflowMeta dialogWorkflowMeta = new WorkflowMeta();
      dialogWorkflowMeta.setName(buildDataVaultUpdateWorkflowName());

      ActionDataVaultUpdateDialog dialog =
          new ActionDataVaultUpdateDialog(
              hopGui.getShell(), updateAction, dialogWorkflowMeta, getVariables());
      IAction configuredAction = dialog.open();
      if (configuredAction == null) {
        return;
      }

      ActionDataVaultUpdate confirmedAction = (ActionDataVaultUpdate) configuredAction;
      storeDataVaultUpdateAction(confirmedAction);
      openAndRunDataVaultUpdateWorkflow(confirmedAction);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.RunDataVaultUpdate.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.RunDataVaultUpdate.Error.Message"),
          e);
    }
  }

  private boolean ensureModelSavedBeforeRun() {
    if (!hasChanged()) {
      return true;
    }

    MessageBox messageDialog =
        new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
    messageDialog.setText(
        BaseMessages.getString(PKG, "HopGuiVaultGraph.RunDataVaultUpdate.Save.Dialog.Header"));
    messageDialog.setMessage(
        BaseMessages.getString(
            PKG, "HopGuiVaultGraph.RunDataVaultUpdate.Save.Dialog.Message", buildTabName()));
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
            hopGui.getShell(),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Header"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Message"),
            e);
        return false;
      }
    }
    return (answer & SWT.NO) != 0;
  }

  private String buildDataVaultUpdateWorkflowName() {
    String modelName = model != null ? model.getName() : null;
    if (Utils.isEmpty(modelName)) {
      modelName = buildTabName();
    }
    return "Update " + modelName;
  }

  private ActionDataVaultUpdate loadStoredDataVaultUpdateAction() throws HopException {
    String actionXml = retrieveStoredDataVaultUpdateActionXml();
    if (!Utils.isEmpty(actionXml)) {
      Node actionNode = XmlHandler.loadXmlString(actionXml, ActionMeta.XML_TAG);
      ActionMeta actionMeta =
          new ActionMeta(actionNode, hopGui.getMetadataProvider(), getVariables());
      if (actionMeta.getAction() instanceof ActionDataVaultUpdate storedAction) {
        return storedAction;
      }
    }
    return new ActionDataVaultUpdate();
  }

  private String retrieveStoredDataVaultUpdateActionXml() {
    return ModelUpdateActionAuditSupport.retrieveActionXml(
        DATA_VAULT_UPDATE_AUDIT_GROUP, DATA_VAULT_UPDATE_AUDIT_TYPE, getFilename());
  }

  private void storeDataVaultUpdateAction(ActionDataVaultUpdate updateAction) {
    ModelUpdateActionAuditSupport.storeActionXml(
        DATA_VAULT_UPDATE_AUDIT_GROUP,
        DATA_VAULT_UPDATE_AUDIT_TYPE,
        getFilename(),
        new ActionMeta(updateAction.clone()));
  }

  public void copyDataVaultUpdateActionToClipboard() {
    if (model == null) {
      return;
    }
    if (!ensureModelSavedBeforeClipboard()) {
      return;
    }
    try {
      ActionDataVaultUpdate updateAction = loadStoredDataVaultUpdateAction();
      updateAction.setDataVaultModelFile(getFilename());
      ModelUpdateWorkflowClipboardSupport.copyUpdateWorkflowToClipboard(
          hopGui, updateAction, getVariables(), getFilename());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.CopyUpdateAction.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.CopyUpdateAction.Error.Message"),
          e);
    }
  }

  private boolean ensureModelSavedBeforeClipboard() {
    if (!hasChanged()) {
      return true;
    }

    MessageBox messageDialog =
        new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
    messageDialog.setText(
        BaseMessages.getString(PKG, "HopGuiVaultGraph.CopyUpdateAction.Save.Dialog.Header"));
    messageDialog.setMessage(
        BaseMessages.getString(
            PKG, "HopGuiVaultGraph.CopyUpdateAction.Save.Dialog.Message", buildTabName()));
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
            hopGui.getShell(),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Header"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Message"),
            e);
        return false;
      }
    }
    return (answer & SWT.NO) != 0;
  }

  private WorkflowMeta buildDataVaultUpdateWorkflow(ActionDataVaultUpdate updateAction) {
    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(buildDataVaultUpdateWorkflowName());
    workflowMeta.setMetadataProvider(hopGui.getMetadataProvider());

    ActionStart startAction = new ActionStart("Start");
    ActionMeta startMeta = new ActionMeta(startAction);
    startMeta.setLocation(50, 50);

    ActionMeta updateMeta = new ActionMeta(updateAction.clone());
    updateMeta.setLocation(250, 50);

    workflowMeta.addAction(startMeta);
    workflowMeta.addAction(updateMeta);
    workflowMeta.addWorkflowHop(new WorkflowHopMeta(startMeta, updateMeta));

    return workflowMeta;
  }

  private void openAndRunDataVaultUpdateWorkflow(ActionDataVaultUpdate updateAction)
      throws HopException {
    WorkflowMeta workflowMeta = buildDataVaultUpdateWorkflow(updateAction);
    IHopFileTypeHandler handler =
        ModelGeneratedArtifactOpenSupport.openGeneratedWorkflow(workflowMeta);
    if (handler instanceof HopGuiWorkflowGraph workflowGraph) {
      workflowGraph.workflowRunDelegate.executeWorkflow(
          workflowGraph.getVariables(), workflowMeta, null);
    }
  }

  private List<ICheckResult> runModelCheck() {
    return model.check(
        hopGui.getMetadataProvider(), getVariables(), DvModelCheckOptions.defaults());
  }

  private void showCheckResultsDialog(List<ICheckResult> remarks) {
    CheckResultDialog dialog = new CheckResultDialog(hopGui.getShell(), remarks);
    String tableName = dialog.open();
    if (tableName != null) {
      IDvTable table = model.findTable(tableName);
      if (table != null) {
        editTable(table);
      }
    }
  }

  private static boolean hasCheckErrors(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  /** Returns false when the model has check errors (dialog already shown). */
  private boolean validateModelForDebug() {
    if (model == null) {
      return false;
    }
    List<ICheckResult> remarks = runModelCheck();
    if (!hasCheckErrors(remarks)) {
      return true;
    }
    showCheckResultsDialog(remarks);
    return false;
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_GENERATE_DDL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.GenerateDdl.Tooltip",
      image = "ui/images/database.svg")
  public void generateModelDdl() {
    if (model == null) {
      return;
    }

    List<String> ddlStatements = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      try {
        for (String ddl :
            table.generateUpdateDdl(hopGui.getMetadataProvider(), hopGui.getVariables(), model)) {
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

    if (!ddlStatements.isEmpty()) {
      try {
        // Explain structural changes with source-to-target lineage before opening SQL.
        try {
          String explanation =
              org.apache.hop.datavault.lineage.DdlLineageExplainSupport.explain(
                  ddlStatements, model, hopGui.getVariables(), hopGui.getMetadataProvider());
          org.apache.hop.datavault.hopgui.lineage.LineageTabSupport.showDdlExplanation(
              hopGui.getShell(), explanation);
        } catch (Exception lineageEx) {
          // Non-fatal: still open SQL editor.
        }

        DatabaseMeta dbMeta =
            DvSpecialRecordSupport.loadTargetDatabase(
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
      box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.NoDdlNeeded.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiVaultGraph.NoDdlNeeded.Message"));
      box.open();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DEBUG,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Debug.Tooltip",
      image = "ui/images/debug.svg")
  public void debugPipelines() {
    if (model == null) {
      return;
    }
    if (!validateModelForDebug()) {
      return;
    }
    IVariables debugVariables = resolveVariablesForDebug();
    if (debugVariables == null) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE) {
      debugStagingWorkflow(debugVariables);
      return;
    }
    for (IDvTable table : model.getTables()) {
      // Only show the pipelines of the selected tables if one or more tables are selected.
      //
      if (!table.isSelected() && model.nrSelectedTables() > 0) {
        continue;
      }
      openUpdatePipeline(table, debugVariables);
    }
  }

  private void debugStagingWorkflow(IVariables debugVariables) {
    try {
      Timestamp loadDate = Timestamp.from(Instant.now());
      List<PipelineMeta> pipelineMetas = new ArrayList<>();
      for (IDvTable table :
          DvUpdateExecutionSupport.orderTablesForPipelineExecution(model.getTables())) {
        if (!table.isSelected() && model.nrSelectedTables() > 0) {
          continue;
        }
        if (DvIntegrationSupport.isExternalRead(table)) {
          continue;
        }
        List<PipelineMeta> generated =
            table.generateUpdatePipelines(
                hopGui.getMetadataProvider(), debugVariables, model, loadDate, null);
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

      DataVaultConfiguration config = model.getConfigurationOrDefault();
      org.apache.hop.core.database.DatabaseMeta targetDatabase =
          DvSpecialRecordSupport.loadTargetDatabase(hopGui.getMetadataProvider(), config);
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
          "Error",
          "Error generating bulk-load debug workflow for model '" + model.getName() + "'",
          e);
    }
  }

  public void openUpdatePipeline(IDvTable table) {
    if (!validateModelForDebug()) {
      return;
    }
    IVariables debugVariables = resolveVariablesForDebug();
    if (debugVariables == null) {
      return;
    }
    openUpdatePipeline(table, debugVariables);
  }

  private void openUpdatePipeline(IDvTable table, IVariables debugVariables) {
    String tableName =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    try {
      DvIntegerSettingValidationSupport.requireModelPipelineIntegerSettings(
          model.getConfigurationOrDefault(), debugVariables);

      // Provide a static load date value for this batch (used in Constant transform + stored in
      // target)
      Timestamp loadDate = Timestamp.from(Instant.now());

      if (DvIntegrationSupport.shouldSkipUpdatePipeline(table)) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugPipeline.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG,
                DvIntegrationSupport.isLinkedTable(table)
                    ? "HopGuiVaultGraph.DebugPipeline.LinkedTable"
                    : "HopGuiVaultGraph.DebugPipeline.ExternalTable",
                tableName));
        box.open();
        return;
      }

      List<PipelineMeta> pipelineMetas =
          table.generateUpdatePipelines(
              hopGui.getMetadataProvider(), debugVariables, model, loadDate, null);
      List<org.apache.hop.workflow.WorkflowMeta> workflowMetas =
          table.generateUpdateWorkflows(
              hopGui.getMetadataProvider(), debugVariables, model, loadDate, null);

      // Multi-source hub/link: open the serial orchestration workflow first.
      if (workflowMetas != null) {
        for (org.apache.hop.workflow.WorkflowMeta workflowMeta : workflowMetas) {
          if (workflowMeta != null) {
            ModelGeneratedArtifactOpenSupport.openGeneratedWorkflow(workflowMeta);
          }
        }
      }

      if (pipelineMetas == null || pipelineMetas.isEmpty()) {
        if ((workflowMetas == null || workflowMetas.isEmpty())
            && DvIntegrationSupport.isCustomPipelines(table)) {
          MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_WARNING);
          box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugPipeline.Title"));
          box.setMessage(
              BaseMessages.getString(
                  PKG, "HopGuiVaultGraph.DebugPipeline.CustomPipelinesMissing", tableName));
          box.open();
        }
        return;
      }

      for (PipelineMeta pipelineMeta : pipelineMetas) {
        if (pipelineMeta == null) continue;
        openReloadedPipeline(pipelineMeta, debugVariables);
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(), "Error", "Error generating debug pipeline for '" + tableName + "'", e);
    }
  }

  private void openReloadedPipeline(PipelineMeta pipelineMeta, IVariables debugVariables)
      throws Exception {
    ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(hopGui, pipelineMeta, debugVariables);
  }

  /**
   * Prompts the user for values of variables referenced in the model (for example {@code
   * OUTPUT_COPIES}). Returns a copy of the graph variables with any entered values applied, or
   * {@code null} when the user cancels.
   */
  private @Nullable IVariables resolveVariablesForDebug() {
    if (model == null) {
      return getVariables();
    }

    List<String> usedVariables = new ArrayList<>();
    for (String variableName : model.getUsedVariables()) {
      if (Utils.isEmpty(variableName) || variableName.startsWith(Const.INTERNAL_VARIABLE_PREFIX)) {
        continue;
      }
      usedVariables.add(variableName);
    }

    if (usedVariables.isEmpty()) {
      return getVariables();
    }

    IRowMeta rowMeta;
    try {
      rowMeta = new RowMeta();
      rowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Column.Name"),
              IValueMeta.TYPE_STRING));
      rowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Column.Value"),
              IValueMeta.TYPE_STRING));
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Error.Message"),
          e);
      return null;
    }

    Map<String, String> savedValues = retrieveDebugVariableValues();
    List<Object[]> rows = new ArrayList<>();
    for (String variableName : usedVariables) {
      String value = savedValues.get(variableName);
      if (value == null) {
        value = Const.NVL(getVariables().getVariable(variableName), "");
      }
      rows.add(new Object[] {variableName, value});
    }

    EditRowsDialog dialog =
        new EditRowsDialog(
            hopGui.getShell(),
            SWT.NONE,
            BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Title"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.DebugVariables.Message"),
            rowMeta,
            rows);
    List<Object[]> resultRows = dialog.open();
    if (resultRows == null) {
      return null;
    }

    Map<String, String> valuesToStore = new HashMap<>(savedValues);
    Variables debugVariables = new Variables();
    debugVariables.copyFrom(getVariables());
    for (Object[] row : resultRows) {
      if (row == null || row.length < 2 || row[0] == null) {
        continue;
      }
      String variableName = row[0].toString();
      String variableValue = row[1] != null ? row[1].toString() : "";
      if (!Utils.isEmpty(variableName)) {
        debugVariables.setVariable(variableName, variableValue);
        valuesToStore.put(variableName, variableValue);
      }
    }
    storeDebugVariableValues(valuesToStore);
    return debugVariables;
  }

  private Map<String, String> retrieveDebugVariableValues() {
    try {
      AuditState auditState =
          AuditManager.getActive()
              .retrieveState(
                  DEBUG_VARIABLES_AUDIT_GROUP,
                  DEBUG_VARIABLES_AUDIT_TYPE,
                  DEBUG_VARIABLES_AUDIT_STATE_NAME);
      if (auditState == null || auditState.getStateMap() == null) {
        return new HashMap<>();
      }

      Map<String, String> values = new HashMap<>();
      for (Map.Entry<String, Object> entry : auditState.getStateMap().entrySet()) {
        if (entry.getValue() instanceof String value) {
          values.put(entry.getKey(), value);
        }
      }
      return values;
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring debug variable values", e);
      return new HashMap<>();
    }
  }

  private void storeDebugVariableValues(Map<String, String> values) {
    try {
      Map<String, Object> stateMap = new HashMap<>(values);
      AuditState auditState = new AuditState(DEBUG_VARIABLES_AUDIT_STATE_NAME, stateMap);
      AuditManager.getActive()
          .storeState(DEBUG_VARIABLES_AUDIT_GROUP, DEBUG_VARIABLES_AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing debug variable values", e);
    }
  }

  // --- @GuiContextAction methods for background canvas context (left click) ---
  // The parentId links to HopGuiVaultContext so they appear in the dialog presented on canvas
  // click.
  // Location is taken from the click point passed in the context (de-magnified model coords).

  @GuiContextAction(
      id = "vault-graph-add-hub",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Hub",
      tooltip = "Add a new Hub table at the click location",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void addHub(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    realGraph.markUndoPoint();
    DvHub hub = new DvHub(getUniqueTableNameFromModel("Hub", realModel));
    PropsUi.setLocation(hub, click != null ? click.x : 50, click != null ? click.y : 50);
    realModel.getTables().add(hub);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-satellite",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Satellite",
      tooltip = "Add a new Satellite table at the click location",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void addSatellite(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    realGraph.markUndoPoint();
    DvSatellite sat = new DvSatellite(getUniqueTableNameFromModel("Satellite", realModel));
    PropsUi.setLocation(sat, click != null ? click.x : 50, click != null ? click.y : 50);
    realModel.getTables().add(sat);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-link",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Link",
      tooltip = "Add a new Link table at the click location",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void addLink(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    realGraph.markUndoPoint();
    DvLink link = new DvLink(getUniqueTableNameFromModel("Link", realModel));
    PropsUi.setLocation(link, click.x, click.y);
    realModel.getTables().add(link);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-add-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Reference table",
      tooltip = "Add a natural-key reference/code table (no hub hash key) at the click location",
      image = "datavault-reference.svg",
      keywords = {"reference", "ref", "code", "lookup", "catalog"},
      category = "Data Vault",
      categoryOrder = "4")
  public void addReferenceTable(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null) {
      return;
    }
    realGraph.markUndoPoint();
    DvReferenceTable reference =
        new DvReferenceTable(getUniqueTableNameFromModel("Reference", realModel));
    PropsUi.setLocation(reference, click != null ? click.x : 50, click != null ? click.y : 50);
    realModel.getTables().add(reference);
    realGraph.setChanged();
  }

  private void addTableReferenceAtClick(DvTableType tableType, Point click) {
    if (model == null || tableType == null) {
      return;
    }
    String selectedFile =
        BaseDialog.presentFileDialog(
            false,
            getShell(),
            null,
            getVariables(),
            null,
            new String[] {"*" + HopVaultFileType.VAULT_FILE_EXTENSION},
            new String[] {HopVaultFileType.VAULT_FILE_TYPE_DESCRIPTION},
            false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }
    try {
      String storedPath = selectedFile;
      if (!storedPath.contains("${")) {
        storedPath =
            DvModelLoadSupport.toStoredModelPath(selectedFile, model.getFilename(), getVariables());
      }
      DataVaultModel externalModel =
          DvModelLoadSupport.loadDataVaultModel(
              storedPath,
              model.getFilename(),
              getVariables(),
              hopGui != null ? hopGui.getMetadataProvider() : null);
      List<String> choices =
          DvLinkedTableSupport.listAvailableTableNames(externalModel, model, tableType);
      if (choices.isEmpty()) {
        new ErrorDialog(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddLinkedTable.Error.Title"),
            BaseMessages.getString(
                PKG, "HopGuiVaultGraph.AddLinkedTable.Error.NoTables", tableType.name()),
            null);
        return;
      }
      String title =
          BaseMessages.getString(
              PKG, "HopGuiVaultGraph.AddLinkedTable.Dialog.Title", tableType.name());
      String message =
          BaseMessages.getString(
              PKG, "HopGuiVaultGraph.AddLinkedTable.Dialog.Message", tableType.name());
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(getShell(), choices.toArray(new String[0]), title, message);
      String selectedName = dialog.open();
      if (Utils.isEmpty(selectedName)) {
        return;
      }
      IDvTable externalTable = externalModel.findTable(selectedName);
      if (externalTable == null) {
        return;
      }
      int x = click != null ? click.x : 50;
      int y = click != null ? click.y : 50;
      DvLinkedTable reference =
          DvLinkedTableSupport.createReference(externalTable, storedPath, new Point(x, y));
      if (reference == null) {
        return;
      }
      model.getTables().add(reference);
      setChanged();
      redraw();
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.AddLinkedTable.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-hub-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddHubReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddHubReference.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void addHubReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = context.getVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableReferenceAtClick(DvTableType.HUB, context.getClick());
  }

  @GuiContextAction(
      id = "vault-graph-add-hub-alias",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddHubAlias.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddHubAlias.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void addHubAlias(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = context.getVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addHubAliasAtClick(context.getClick());
  }

  /**
   * Places a same-model hub role-playing alias so a link can reference the same physical hub more
   * than once with distinct source mappings and hash columns.
   */
  private void addHubAliasAtClick(Point click) {
    if (model == null) {
      return;
    }
    List<String> choices =
        DvLinkedTableSupport.listAvailableTableNames(model, model, DvTableType.HUB, true);
    if (choices.isEmpty()) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.Error.NoHubs"),
          null);
      return;
    }
    EnterSelectionDialog targetDialog =
        new EnterSelectionDialog(
            getShell(),
            choices.toArray(new String[0]),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.SelectHub.Title"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.SelectHub.Message"));
    String targetName = targetDialog.open();
    if (Utils.isEmpty(targetName)) {
      return;
    }
    IDvTable target = model.findTable(targetName);
    if (!(target instanceof DvHub)) {
      return;
    }
    String suggestedAlias = "hub_" + targetName.replaceFirst("(?i)^hub_", "") + "_role";
    if (model.findTable(suggestedAlias) != null) {
      suggestedAlias = suggestedAlias + "_2";
    }
    EnterStringDialog nameDialog =
        new EnterStringDialog(
            getShell(),
            suggestedAlias,
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.AliasName.Title"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.AliasName.Message"));
    nameDialog.setMandatory(true);
    String aliasName = nameDialog.open();
    if (Utils.isEmpty(aliasName)) {
      return;
    }
    if (model.findTable(aliasName) != null) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.Error.NameExists", aliasName),
          null);
      return;
    }
    String defaultRoleHash = DvTableResolutionSupport.deriveRoleHashKeyFieldName(aliasName);
    EnterStringDialog hashDialog =
        new EnterStringDialog(
            getShell(),
            Const.NVL(defaultRoleHash, ""),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.RoleHash.Title"),
            BaseMessages.getString(PKG, "HopGuiVaultGraph.AddHubAlias.RoleHash.Message"));
    hashDialog.setMandatory(true);
    String roleHash = hashDialog.open();
    if (Utils.isEmpty(roleHash)) {
      return;
    }
    int x = click != null ? click.x : 50;
    int y = click != null ? click.y : 50;
    DvLinkedTable alias =
        DvLinkedTableSupport.createAlias(aliasName, target, null, roleHash, new Point(x, y));
    if (alias == null) {
      return;
    }
    model.getTables().add(alias);
    setChanged();
    redraw();
  }

  @GuiContextAction(
      id = "vault-graph-add-link-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddLinkReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddLinkReference.Tooltip",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "5")
  public void addLinkReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = context.getVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableReferenceAtClick(DvTableType.LINK, context.getClick());
  }

  @GuiContextAction(
      id = "vault-graph-add-satellite-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddSatelliteReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddSatelliteReference.Tooltip",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void addSatelliteReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = context.getVaultGraph();
    if (graph == null || context.getModel() == null) {
      return;
    }
    graph.markUndoPoint();
    graph.addTableReferenceAtClick(DvTableType.SATELLITE, context.getClick());
  }

  @GuiContextAction(
      id = "vault-graph-paste-clipboard",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void pasteFromClipboard(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (realGraph != null) {
      realGraph.pasteFromClipboard(context.getClick());
    }
  }

  @GuiContextAction(
      id = "vault-graph-copy-update-action",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "1")
  public void copyUpdateActionToClipboard(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (realGraph != null) {
      realGraph.copyDataVaultUpdateActionToClipboard();
    }
  }

  @GuiContextAction(
      id = "vault-graph-compose-multi-table-source",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.ComposeMultiTableSource.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.ComposeMultiTableSource.Tooltip",
      image = "source-model.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void composeMultiTableSource(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context != null ? context.getVaultGraph() : this;
    if (realGraph == null) {
      return;
    }
    org.apache.hop.datavault.hopgui.file.sourcemodel.HopGuiSourceQueryComposeSupport
        .composeAndPublish(realGraph.hopGui, realGraph.getShell());
  }

  @GuiContextAction(
      id = "vault-graph-add-note",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.AddNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Data Vault",
      categoryOrder = "5")
  public void addNote(HopGuiVaultContext context) {
    Point click = context.getClick();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realModel == null || realGraph == null) {
      return;
    }
    realGraph.markUndoPoint();
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    PropsUi.setLocation(note, click != null ? click.x : 50, click != null ? click.y : 50);
    PropsUi.setSize(note, ConstUi.NOTE_MIN_SIZE, ConstUi.NOTE_MIN_SIZE);
    realModel.getNotes().add(note);
    realGraph.editNote(note, false);
    realGraph.setChanged();
  }

  @GuiContextAction(
      id = "vault-graph-edit-model",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit model properties",
      tooltip = "Edit the properties of this Data Vault model",
      image = "datavault-model.svg",
      category = "Data Vault",
      categoryOrder = "10")
  public void editModelProperties(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (realGraph != null && realModel != null) {
      realGraph.editModelProperties(realModel);
    }
  }

  @GuiContextAction(
      id = "vault-graph-publish-dimensional-draft",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.PublishDimensional.Name",
      tooltip = "i18n::HopGuiVaultGraph.PublishDimensional.Tooltip",
      image = "dimensional-model.svg",
      category = "Export",
      categoryOrder = "2")
  public void publishDimensionalDraft(HopGuiVaultContext context) {
    DataVaultModel realModel = context.getModel();
    if (realModel != null) {
      HopGuiDvDimensionalPublishSupport.publishDraftDimensionalModel(hopGui, realModel);
    }
  }

  @GuiContextAction(
      id = "vault-graph-import-record-definitions",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.ImportSources.Name",
      tooltip = "i18n::HopGuiVaultGraph.ImportSources.Tooltip",
      image = "data-catalog.svg",
      category = "Import",
      categoryOrder = "1")
  public void importDatabaseSourceTables(HopGuiVaultContext context) {
    importCatalogRecordDefinitions();
  }

  @GuiContextAction(
      id = "vault-graph-ai-help",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiVaultGraph.AiHelp.Tooltip",
      image = "datavault-ai-help.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorContext(HopGuiVaultContext context) {
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (realGraph != null) {
      realGraph.openAiAdvisor();
    }
  }

  // --- @GuiContextAction methods for table context (left click on icon body, not name) ---
  // The parentId links to HopGuiVaultTableContext. Edit does same as name-click; Delete removes
  // table.

  private static final String ACTION_ID_GO_TO_REFERENCED_TABLE =
      "vault-graph-go-to-referenced-table";

  @GuiContextAction(
      id = ACTION_ID_GO_TO_REFERENCED_TABLE,
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.Context.GoToReferencedTable.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.GoToReferencedTable.Tooltip",
      image = "ui/images/location.svg",
      category = "Data Vault",
      categoryOrder = "0")
  public void goToReferencedTableAction(HopGuiVaultTableContext context) {
    IDvTable table = context.getTable();
    HopGuiVaultGraph graph = context.getVaultGraph();
    DataVaultModel dvModel = context.getModel();
    if (!(table instanceof DvLinkedTable reference) || graph == null || dvModel == null) {
      return;
    }
    graph.navigateToReferencedTable(reference);
  }

  @GuiContextActionFilter(parentId = HopGuiVaultTableContext.CONTEXT_ID)
  public boolean filterTableContextActions(
      String contextActionId, HopGuiVaultTableContext context) {
    if (ACTION_ID_GO_TO_REFERENCED_TABLE.equals(contextActionId)) {
      IDvTable table = context.getTable();
      if (!(table instanceof DvLinkedTable reference)) {
        return false;
      }
      return DvLinkedTableNavigationSupport.canNavigateToSourceTable(
          context.getModel(), reference, getVariables(), hopGui.getMetadataProvider());
    }
    IDvTable table = context.getTable();
    if (table != null && table.getTableType() == DvTableType.LINKED_TABLE) {
      if ("vault-graph-edit-table".equals(contextActionId)
          || "vault-graph-show-table-pipeline".equals(contextActionId)) {
        return false;
      }
    }
    return true;
  }

  @GuiContextAction(
      id = "vault-graph-edit-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit",
      tooltip = "Edit the properties of this table",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editTableAction(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (t != null && realGraph != null) {
      realGraph.editTable(t);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "Delete",
      tooltip = "Delete this table from the model",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteTable(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (t != null && realModel != null && realGraph != null) {
      realGraph.markUndoPoint();
      if (removeTableFromModel(realModel, t)) {
        realGraph.setChanged();
        realGraph.redraw();
      }
    }
  }

  @GuiContextAction(
      id = "vault-graph-show-table-pipeline",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "Show update pipeline",
      tooltip = "Generate and show the pipeline used to update this table",
      image = "ui/images/debug.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void debugTablePipeline(HopGuiVaultTableContext context) {
    IDvTable t = context.getTable();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (t != null && realGraph != null) {
      realGraph.openUpdatePipeline(t);
    }
  }

  @GuiContextAction(
      id = "vault-graph-preview-target-layout",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiVaultGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void previewTargetLayoutAction(HopGuiVaultTableContext context) {
    IDvTable table = context.getTable();
    DataVaultModel dvModel = context.getModel();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (table == null || realGraph == null) {
      return;
    }
    ModelTableLayoutPreviewSupport.previewDvTableLayout(
        realGraph.getShell(),
        realGraph.getVariables(),
        hopGui.getMetadataProvider(),
        dvModel,
        table);
  }

  @GuiContextAction(
      id = "vault-graph-edit-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.EditNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editNoteAction(HopGuiVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    if (note != null && realGraph != null) {
      realGraph.editNote(note);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiVaultGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiVaultNoteContext context) {
    DvNote note = context.getNote();
    HopGuiVaultGraph realGraph = context.getVaultGraph();
    DataVaultModel realModel = context.getModel();
    if (note != null && realModel != null && realGraph != null) {
      realGraph.markUndoPoint();
      if (removeNotesFromModel(realModel, List.of(note))) {
        realGraph.setChanged();
        realGraph.redraw();
      }
    }
  }

  private boolean removeTableFromModel(DataVaultModel targetModel, IDvTable table) {
    if (targetModel == null || table == null) {
      return false;
    }
    if (!targetModel.getTables().remove(table)) {
      return false;
    }
    DataVaultModelReferenceCleanup.cleanupAfterTableRemoved(targetModel, table);
    return true;
  }

  private boolean removeNotesFromModel(DataVaultModel targetModel, List<DvNote> notes) {
    if (targetModel == null || notes == null || notes.isEmpty()) {
      return false;
    }
    return targetModel.getNotes().removeAll(notes);
  }

  // Small helper so the action methods (which may be called on a dummy graph instance via
  // lambda builder) can still compute unique names using the *real* model from context.
  private String getUniqueTableNameFromModel(String base, DataVaultModel m) {
    if (m == null) {
      return base;
    }
    int num = 1;
    String candidate;
    do {
      candidate = base + " " + num;
      num++;
    } while (hasTableWithNameInModel(candidate, m));
    return candidate;
  }

  private boolean hasTableWithNameInModel(String name, DataVaultModel m) {
    for (IDvTable t : m.getTables()) {
      if (t != null && t.getName() != null && t.getName().equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  // --- Context menu / click handling support ---

  private IDvTable findTableAtScreen(int screenX, int screenY) {
    if (model == null) {
      return null;
    }
    float cMag = calculateCorrectedMagnification();
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      // Since setTransform handles mag/pan, visual positions are approx (loc * mag + offset)
      // Box sizes (drawnBox) are now in logical units, so visual size = logical * mag
      int sx = (int) (loc.x * cMag + offset.x);
      int sy = (int) (loc.y * cMag + offset.y);
      int tw = 140;
      int th = 70;
      if (table instanceof DvTableBase base) {
        if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
        if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
      }
      int sw = (int) (tw * cMag);
      int sh = (int) (th * cMag);
      if (screenX >= sx && screenX < sx + sw && screenY >= sy && screenY < sy + sh) {
        return table;
      }
    }
    return null;
  }

  /**
   * Test if the table's current drawn visual rectangle (in screen coords, using drawnBox size +
   * magnification + offset, exactly like findTableAtScreen and AreaOwner registration) overlaps the
   * given lasso rect (in screen pixels). Used to decide which tables to select on lasso up.
   */
  private boolean isTableInLassoScreenRect(
      IDvTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null) {
      return false;
    }
    Point loc = table.getLocation();
    if (loc == null) {
      return false;
    }
    int tw = 140;
    int th = 70;
    if (table instanceof DvTableBase base) {
      if (base.getDrawnBoxWidth() > 0) tw = base.getDrawnBoxWidth();
      if (base.getDrawnBoxHeight() > 0) th = base.getDrawnBoxHeight();
    }
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int sw = Math.max(1, tw);
    int sh = Math.max(1, th);

    int tMaxX = tMinX + sw;
    int tMaxY = tMinY + sh;

    // Overlap test (intersection, not strict containment): select the table if its visual area
    // touches the lasso rect at all. This is practical for lasso selection.
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  /** Lookup area owner at screen mouse coords (for name vs icon body hits, hover etc). */
  @Override
  public AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  private void unselectAllTables() {
    if (model != null) {
      model.getTables();
      for (IDvTable t : model.getTables()) {
        if (t != null) {
          t.setSelected(false);
        }
      }
    }
  }

  private List<IDvTable> getSelectedTables() {
    List<IDvTable> list = new ArrayList<>();
    if (model != null) {
      model.getTables();
      for (IDvTable t : model.getTables()) {
        if (t != null && t.isSelected()) {
          list.add(t);
        }
      }
    }
    return list;
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

  @Override
  protected void clearSelectionRegion() {
    selectionRegion = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
      canvas.setData("mode", "null");
      setCursor(null);
    }
  }

  private void editTable(IDvTable table) {
    if (table == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean modelChanged = false;
    Shell parentShell = getShell();
    if (table.getTableType() == DvTableType.HUB) {
      DvHubDialog dialog = new DvHubDialog(parentShell, hopGui, model, (DvHub) table);
      modelChanged = dialog.open();
    } else if (table.getTableType() == DvTableType.SATELLITE) {
      DvSatelliteDialog dialog =
          new DvSatelliteDialog(parentShell, hopGui, (DvSatellite) table, model);
      modelChanged = dialog.open();
    } else if (table.getTableType() == DvTableType.LINK) {
      DvLinkDialog dialog = new DvLinkDialog(parentShell, hopGui, (DvLink) table, model);
      modelChanged = dialog.open();
    } else if (table.getTableType() == DvTableType.REFERENCE
        && table instanceof DvReferenceTable referenceTable) {
      DvReferenceTableDialog dialog =
          new DvReferenceTableDialog(parentShell, hopGui, model, referenceTable);
      modelChanged = dialog.open();
    }
    if (modelChanged) {
      commitDialogUndo(beforeChange);
      table.setChanged();
      setChanged();
      canvas.setFocus();
    }
  }

  private void editModelProperties(DataVaultModel modelToEdit) {
    if (modelToEdit == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiDataVaultModelDialog dialog =
        new HopGuiDataVaultModelDialog(getShell(), hopGui, modelToEdit);
    if (dialog.open()) {
      commitDialogUndo(beforeChange);
      setChanged();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
    }
  }

  private void cancelRelationshipDrag() {
    startRelationshipTable = null;
    relationshipDragEndLocation = null;
    candidateRelationshipTarget = null;
    clearNavigationViewportState();
    clearTableDragState();
    clearSelectionRegion();
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setData("mode", "null");
    }
  }

  private static boolean isHubSatellitePair(DvTableType first, DvTableType second) {
    return (first == DvTableType.HUB && second == DvTableType.SATELLITE)
        || (first == DvTableType.SATELLITE && second == DvTableType.HUB);
  }

  private static boolean isHubLinkPair(DvTableType first, DvTableType second) {
    return (first == DvTableType.HUB && second == DvTableType.LINK)
        || (first == DvTableType.LINK && second == DvTableType.HUB);
  }

  private static boolean isLinkSatellitePair(DvTableType first, DvTableType second) {
    return (first == DvTableType.LINK && second == DvTableType.SATELLITE)
        || (first == DvTableType.SATELLITE && second == DvTableType.LINK);
  }

  private static DvTableType effectiveRelationshipType(IDvTable table) {
    return DvLinkedTableSupport.effectiveTableType(table);
  }

  private boolean isValidRelationshipPair(IDvTable a, IDvTable b) {
    if (a == null || b == null || a == b) {
      return false;
    }
    DvTableType ta = effectiveRelationshipType(a);
    DvTableType tb = effectiveRelationshipType(b);
    return isHubSatellitePair(ta, tb) || isHubLinkPair(ta, tb) || isLinkSatellitePair(ta, tb);
  }

  /**
   * Create a relationship between the two tables by mutating the appropriate name reference(s)
   * inside the table objects (which live in the model's tables list). Supports hub-satellite,
   * hub-link, and link-satellite relationships. Sets changed on affected table(s) and model.
   */
  private void createRelationship(IDvTable from, IDvTable to) {
    if (from == null || to == null || from == to) {
      return;
    }
    if (!isValidRelationshipPair(from, to)) {
      return;
    }

    byte[] beforeChange = captureUndoSnapshot();

    DvTableType fromType = effectiveRelationshipType(from);
    DvTableType toType = effectiveRelationshipType(to);
    boolean modelChanged;
    if (isHubSatellitePair(fromType, toType)) {
      modelChanged = createHubSatelliteRelationship(from, to);
    } else {
      if (isHubLinkPair(fromType, toType)) {
        modelChanged = createHubLinkRelationship(from, to);
      } else {
        modelChanged = createLinkSatelliteRelationship(from, to);
      }
    }

    if (modelChanged) {
      commitDialogUndo(beforeChange);
      setChanged();
    }
  }

  /**
   * Attach a satellite to a hub: the satellite stores the hub name as its parent. Any previous link
   * parent is cleared so the satellite has a single, unambiguous parent in the model.
   */
  private boolean createHubSatelliteRelationship(IDvTable from, IDvTable to) {
    IDvTable satelliteTable = effectiveRelationshipType(from) == DvTableType.SATELLITE ? from : to;
    IDvTable hubTable = effectiveRelationshipType(from) == DvTableType.HUB ? from : to;
    if (!(satelliteTable instanceof DvSatellite satellite)
        || effectiveRelationshipType(hubTable) != DvTableType.HUB) {
      return false;
    }

    String hubName = hubTable.getName();
    if (hubName == null) {
      return false;
    }

    if (Objects.equals(satellite.getHubName(), hubName)) {
      return false;
    }

    satellite.setHubName(hubName);
    satellite.setLinkName(null);
    return true;
  }

  /**
   * Register a hub as a participant in a link. Hub names are stored on the link; duplicates are
   * ignored.
   */
  private boolean createHubLinkRelationship(IDvTable from, IDvTable to) {
    IDvTable linkTable = effectiveRelationshipType(from) == DvTableType.LINK ? from : to;
    IDvTable hubTable = effectiveRelationshipType(from) == DvTableType.HUB ? from : to;
    if (!(linkTable instanceof DvLink link)
        || effectiveRelationshipType(hubTable) != DvTableType.HUB) {
      return false;
    }

    String hubName = hubTable.getName();
    if (hubName == null) {
      return false;
    }

    List<String> currentHubs = link.getHubNames();
    if (currentHubs != null && currentHubs.contains(hubName)) {
      return false;
    }

    List<String> newHubs = new ArrayList<>(currentHubs != null ? currentHubs : List.of());
    newHubs.add(hubName);
    link.setHubNames(newHubs);
    return true;
  }

  /**
   * Attach a satellite to a link. Updates both sides of the relationship: the satellite stores the
   * link name (and clears any hub parent), and the link records the satellite in its satellite
   * list.
   */
  private boolean createLinkSatelliteRelationship(IDvTable from, IDvTable to) {
    IDvTable satelliteTable = effectiveRelationshipType(from) == DvTableType.SATELLITE ? from : to;
    IDvTable linkTable = effectiveRelationshipType(from) == DvTableType.LINK ? from : to;
    if (!(satelliteTable instanceof DvSatellite satellite) || !(linkTable instanceof DvLink link)) {
      return false;
    }

    String linkName = link.getName();
    String satelliteName = satellite.getName();
    boolean modelChanged = false;

    // Point the satellite at its link parent and drop any hub parent reference.
    if (linkName != null && !Objects.equals(satellite.getLinkName(), linkName)) {
      satellite.setLinkName(linkName);
      satellite.setHubName(null);
      modelChanged = true;
    }

    // Register the satellite on the link's satellite list (skip duplicates).
    if (linkName != null && satelliteName != null) {
      List<String> currentSatellites = link.getLinkSatelliteNames();
      if (currentSatellites == null || !currentSatellites.contains(satelliteName)) {
        List<String> newSatellites =
            new ArrayList<>(currentSatellites != null ? currentSatellites : List.of());
        newSatellites.add(satelliteName);
        link.setLinkSatelliteNames(newSatellites);
        modelChanged = true;
      }
    }

    return modelChanged;
  }

  /**
   * Draw a temporary dashed line from the center of the source table to the current mouse position
   * while dragging a relationship. Uses screen coordinates (overlay after painter).
   */
  private void showVaultContextDialog(Event e, Point real) {
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = "Select the action to execute or the table type to add:";
      IGuiContextHandler contextHandler = new HopGuiVaultContext(model, this, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          HopGui.getInstance().getShell(), "Error", "Error showing vault context dialog: ", ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showTableContextDialog(Event e, IDvTable table) {
    if (table == null) return;
    try {
      Point real = screen2real(e.x, e.y);
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = "Select action for table '" + table.getName() + "':";
      IGuiContextHandler contextHandler = new HopGuiVaultTableContext(model, this, table, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          HopGui.getInstance().getShell(), "Error", "Error showing table context dialog: ", ex);
    } finally {
      canvas.setFocus();
    }
  }

  // --- IHopFileTypeHandler implementation ---

  @Override
  public Object getSubject() {
    return model;
  }

  /** Project / open-tab search: prefer the in-memory model so unsaved edits are searchable. */
  @Override
  public org.apache.hop.core.search.ISearchable createSearchable(
      String locationDescription, org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws org.apache.hop.core.exception.HopException {
    return new org.apache.hop.datavault.hopgui.search.HopGuiDataVaultModelSearchable(
        locationDescription, model);
  }

  /** Select and open the table matching {@code componentName} (search result navigation). */
  public void openSearchComponent(String componentName) {
    if (Utils.isEmpty(componentName) || model == null) {
      return;
    }
    IDvTable table = model.findTable(componentName);
    if (table == null) {
      return;
    }
    for (IDvTable t : model.getTables()) {
      if (t != null) {
        t.setSelected(false);
      }
    }
    table.setSelected(true);
    editTable(table);
    updateGui();
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : "Data Vault Model";
  }

  @Override
  public void setName(String name) {
    if (model != null) {
      markUndoPoint();
      model.setName(name);
      setChanged();
    }
    // Update tab label (text derived from name) and tooltip in explorer perspective, like Hop code
    // does via perspective.updateTabItem
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

  // Note: DataVaultModel may not have filename yet, we can extend or keep in handler
  private String filename;

  @Override
  public void setFilename(String filename) {
    if (model != null) {
      model.setFilename(filename);
    } else {
      this.filename = filename;
    }
    // Update the tab label (name may be derived from filename basename) + tooltip with full path.
    // Exact same pattern as used in HopDataOrchestrationPerspective.updateTabLabel and
    // ExplorerPerspective.updateTabItem + BaseExplorerFileTypeHandler.
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = "10051-menu-data-vault-model-export-to-svg",
      label = "i18n::HopGui.Menu.File.ExportToSVG",
      image = "ui/images/image.svg",
      parentId = HopGui.ID_MAIN_MENU_FILE)
  public void menuFileExportToSvg() {
    IHopFileTypeHandler active = HopGui.getExplorerPerspective().getActiveFileTypeHandler();
    if (active instanceof HopGuiVaultGraph vaultGraph) {
      vaultGraph.exportModelToSvg();
      return;
    }
    if (active
        instanceof
        org.apache.hop.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph
                businessVaultGraph) {
      businessVaultGraph.exportModelToSvg();
      return;
    }
    if (active
        instanceof
        org.apache.hop.datavault.hopgui.file.executionmap.HopGuiExecutionMapGraph
                executionMapGraph) {
      executionMapGraph.exportToSvg();
      return;
    }
    HopGui.getInstance().fileDelegate.exportToSvg();
  }

  public void exportModelToSvg() {
    if (model == null) {
      return;
    }
    try {
      SvgRenderOptions options = SvgRenderOptions.defaults();
      options.setShowHashKeyFieldNames(
          DataVaultConfigSingleton.getConfig().isDrawingHashKeysInModel());
      String svgXml = SvgExportService.generateDataVaultModelSvg(model, options, variables);

      String proposedName = Const.NVL(model.getName(), "data-vault-model") + ".svg";
      String proposedFilename = variables.getVariable("user.home") + File.separator + proposedName;

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
        box.setText(BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Exists.Title"));
        box.setMessage(BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Exists.Message"));
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
          BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.ExportSvg.Error.Message"),
          e);
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
    // Not applicable for model
  }

  @Override
  public void stop() {
    // Not applicable
  }

  @Override
  public void pause() {
    // Not applicable
  }

  @Override
  public void resume() {
    // Not applicable
  }

  @Override
  public void preview() {
    // Not applicable
  }

  @Override
  public void debug() {
    // Not applicable
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);

    // Update the tab text and font
    perspective.updateTabItem(this);
    // Correct the tree if needed
    perspective.updateTreeItem(this);

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

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model != null) {
      for (IDvTable t : model.getTables()) {
        if (t != null) {
          t.setSelected(true);
        }
      }
      for (DvNote note : model.getNotes()) {
        if (note != null) {
          note.setSelected(true);
        }
      }
      redraw();
    }
  }

  @GuiKeyboardShortcut(key = SWT.ESC)
  @GuiOsxKeyboardShortcut(key = SWT.ESC)
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNSELECT_ALL,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.UnselectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/unselect-all.svg")
  @Override
  public void unselectAll() {
    mouseInteractions().unselectAllOnCanvas();
    clearSelectionRegion();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COPY,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Copy.Tooltip",
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
    clipboardDelegate.copySelected(getSelectedTables(), getSelectedNotes());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CUT,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Cut.Tooltip",
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

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Delete.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  @Override
  public void deleteSelected() {
    if (model == null) {
      return;
    }
    List<IDvTable> tablesToDelete = getSelectedTables();
    List<DvNote> notesToDelete = getSelectedNotes();
    if (tablesToDelete.isEmpty() && notesToDelete.isEmpty()) {
      return;
    }

    markUndoPoint();
    boolean modelChanged = false;
    for (IDvTable table : tablesToDelete) {
      modelChanged |= removeTableFromModel(model, table);
    }
    if (removeNotesFromModel(model, notesToDelete)) {
      modelChanged = true;
      selectedNote = null;
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
      toolTip = "i18n::HopGuiVaultGraph.Toolbar.Paste.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/paste.svg")
  @GuiKeyboardShortcut(control = true, key = 'v')
  @GuiOsxKeyboardShortcut(command = true, key = 'v')
  @Override
  public void pasteFromClipboard() {
    Point location = lastClick != null ? new Point(lastClick.x, lastClick.y) : null;
    pasteFromClipboard(location);
  }

  public void applyPasteResult(List<IDvTable> tables, List<DvNote> notes) {
    if (model == null) {
      return;
    }
    boolean modelChanged = false;
    mouseInteractions().unselectAllOnCanvas();

    if (tables != null) {
      for (IDvTable table : tables) {
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
  public boolean isCloseable() {
    try {
      if (hopGui.fileDelegate.isClosing()) {
        return true;
      }

      if (hasChanged()) {
        MessageBox messageDialog =
            new MessageBox(hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        messageDialog.setText(
            BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiVaultGraph.SaveFile.Dialog.Message", buildTabName()));
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
          BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.SaveFile.Error.Message"),
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
          BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Message"),
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
          BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Title"),
          BaseMessages.getString(PKG, "HopGuiVaultGraph.Undo.Error.Apply.Message"),
          e);
    }
  }

  @Override
  protected void markUndoPoint() {
    super.markUndoPoint();
    enableUndoToolbarItems();
  }

  /**
   * Runs a model mutation with undo support using a gzip-compressed XML snapshot taken before the
   * change.
   */
  public void runUndoableModelChange(HopGuiVaultModelChange change) throws HopException {
    byte[] beforeChange = captureUndoSnapshot();
    change.run();
    commitDialogUndo(beforeChange);
    setChanged();
    redraw();
  }

  @FunctionalInterface
  public interface HopGuiVaultModelChange {
    void run() throws HopException;
  }

  private void restoreModelSnapshot(DataVaultModel restored) {
    cancelRelationshipDrag();
    clearTableDragState();
    clearNoteDragState();
    clearSelectionRegion();
    setModel(restored);
    setChanged();
    if (perspective != null) {
      perspective.updateTabItem(this);
    }
    canvas.setFocus();
  }

  @Override
  public Map<String, Object> getStateProperties() {
    return buildCanvasStateProperties();
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {
    Double fMagnification = (Double) stateProperties.get(STATE_MAGNIFICATION);
    magnification = fMagnification == null ? 1.0f : fMagnification.floatValue();
    setZoomLabel();

    // Offsets used to be integers so don't automatically map to Double.
    //
    Object xOffset = stateProperties.get(STATE_SCROLL_X_SELECTION);
    if (xOffset != null) {
      offset.x = Double.parseDouble(xOffset.toString());
    }
    Object yOffset = stateProperties.get(STATE_SCROLL_Y_SELECTION);
    if (yOffset != null) {
      offset.y = Double.parseDouble(yOffset.toString());
    }
    redraw();
  }

  int[] getSelectedTableIndices(List<IDvTable> selection) {
    int[] indices = new int[selection.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = model.getTables().indexOf(selection.get(i));
    }
    return indices;
  }

  @Override
  public SnapAllignDistribute createSnapAlignDistribute() {
    List<IDvTable> selection = getSelectedTables();
    int[] indices = getSelectedTableIndices(selection);

    return new SnapAllignDistribute(model, selection, indices, null, this);
  }

  @Override
  public void snapToGrid() {
    markUndoPoint();
    super.snapToGrid();
  }

  @Override
  public void alignLeft() {
    markUndoPoint();
    super.alignLeft();
  }

  @Override
  public void alignRight() {
    markUndoPoint();
    super.alignRight();
  }

  @Override
  public void alignTop() {
    markUndoPoint();
    super.alignTop();
  }

  @Override
  public void alignBottom() {
    markUndoPoint();
    super.alignBottom();
  }

  @Override
  public void distributeHorizontal() {
    markUndoPoint();
    super.distributeHorizontal();
  }

  @Override
  public void distributeVertical() {
    markUndoPoint();
    super.distributeVertical();
  }

  @Override
  public IVariables getVariables() {
    return variables;
  }

  // --- Other required from abstract or interfaces ---

  // hasChanged and setChanged already overridden above for the interface + abstract

  // IActionContextHandlersProvider
  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>(); // basic, no extra
  }

  public void setModel(DataVaultModel model) {
    cancelRelationshipDrag();
    clearTableDragState();
    clearSelectionRegion();
    areaOwners.clear();
    mouseOverTableName = null;
    mouseOverNoteLink = null;
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setToolTipText(null);
    }
    lastClick = null;
    this.model = model;
    redraw();
  }

  private final class VaultMouseInteractions implements ModelGraphMouseInteractions {

    @Override
    public ModelGraphHit resolveHit(int logicalX, int logicalY) {
      AreaOwner areaOwner = getVisibleAreaOwner(logicalX, logicalY);
      IDvTable table = getAreaOwnerTable(areaOwner);
      DvNote note = getAreaOwnerNote(areaOwner);
      AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
      return new ModelGraphHit(areaOwner, areaType, note, table);
    }

    @Override
    public boolean handleObjectMouseDown(
        Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
      Object obj = hit.canvasObject();
      if (!(obj instanceof IDvTable tableHit)) {
        return false;
      }
      AreaOwner.AreaType areaType = hit.areaType();

      if (AreaOwner.AreaType.TRANSFORM_INFO_ICON == areaType) {
        avoidContextDialog = true;
        clearTableDragState();
        return true;
      }

      if (AreaOwner.AreaType.TRANSFORM_ICON == areaType
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

      if (e.button == 1 && AreaOwner.AreaType.TRANSFORM_NAME == areaType) {
        avoidContextDialog = true;
        if (tableHit instanceof DvLinkedTable reference) {
          navigateToReferencedTable(reference);
        } else {
          editTable(tableHit);
        }
        return true;
      }

      if (e.button == 1 && AreaOwner.AreaType.TRANSFORM_ICON == areaType) {
        prepareExclusiveDragSelection(
            control, tableHit.isSelected(), () -> tableHit.setSelected(true));
        currentTable = tableHit;
        iconDragStart = new Point(real.x, real.y);
        iconDragCommitted = false;
        Point p = tableHit.getLocation() != null ? tableHit.getLocation() : new Point(0, 0);
        iconOffset = new Point(real.x - p.x, real.y - p.y);
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
    public void handleRelationshipMouseMove(Event e) {
      relationshipDragEndLocation = new Point(e.x, e.y);
      candidateRelationshipTarget = findTableAtScreen(e.x, e.y);
    }

    @Override
    public boolean handleRelationshipMouseUp(Event e, Point real) {
      IDvTable target = findTableAtScreen(e.x, e.y);
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
      unselectAllTables();
      unselectAllNotes();
    }

    @Override
    public void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
      if (model == null) {
        return;
      }
      for (IDvTable table : model.getTables()) {
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
      if (lastClick == null || lastClick.x != real.x || lastClick.y != real.y) {
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
        avoidContextDialog = false;
        clearNoteDragState();
        return true;
      }

      IDvTable hit = currentTable;
      if (hit == null) {
        if (!avoidContextDialog) {
          showVaultContextDialog(e, real);
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
      mouseMoveShowInfoTooltip(areaOwner);
      return mouseMoveOverTableName(areaOwner, false);
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

    private boolean mouseMoveOverTableName(AreaOwner areaOwner, boolean doRedraw) {
      String newOver = null;
      DvLinkedTable referenceUnderName = null;
      if (areaOwner != null
          && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
          && startRelationshipTable == null
          && !dragSelection
          && selectionRegion == null) {
        newOver = (String) areaOwner.getOwner();
        if (areaOwner.getParent() instanceof DvLinkedTable reference) {
          referenceUnderName = reference;
        }
      }
      if ((mouseOverTableName == null && newOver != null)
          || (mouseOverTableName != null && !mouseOverTableName.equals(newOver))) {
        doRedraw = true;
      }
      mouseOverTableName = newOver;
      Cursor hand = getDisplay().getSystemCursor(SWT.CURSOR_HAND);
      if (referenceUnderName != null
          && DvLinkedTableNavigationSupport.canNavigateToSourceTable(
              model, referenceUnderName, getVariables(), hopGui.getMetadataProvider())) {
        if (!Objects.equals(getCanvasCursor(), hand)) {
          setCanvasCursor(hand);
          doRedraw = true;
        }
        String tip =
            BaseMessages.getString(
                PKG,
                "HopGuiVaultGraph.NavigateLinkedTable.Tooltip",
                referenceUnderName.getReferencedTableName());
        if (!Objects.equals(canvas.getToolTipText(), tip)) {
          canvas.setToolTipText(tip);
        }
      } else if (getCanvasCursor() == hand) {
        setCanvasCursor(null);
        doRedraw = true;
      }
      return doRedraw;
    }

    private void mouseMoveShowInfoTooltip(AreaOwner areaOwner) {
      if (areaOwner != null && areaOwner.getAreaType() == AreaOwner.AreaType.TRANSFORM_INFO_ICON) {
        IDvTable t = null;
        Object o = areaOwner.getOwner();
        if (o instanceof IDvTable tt) {
          t = tt;
        } else if (areaOwner.getParent() instanceof IDvTable tt) {
          t = tt;
        }
        String tip = (t != null && !Utils.isEmpty(t.getDescription())) ? t.getDescription() : null;
        if (!Objects.equals(canvas.getToolTipText(), tip)) {
          canvas.setToolTipText(tip);
        }
      } else if (canvas.getToolTipText() != null) {
        canvas.setToolTipText(null);
      }
    }
  }

  @Override
  public ICoachingModelAdapter createCoachingModelAdapter() {
    if (model == null) {
      return null;
    }
    return new DvCoachingModelAdapter(model, this::editTableByName, this::highlightTableByName);
  }

  @Override
  public void notifyCoachModelChanged() {
    setChanged();
    refreshCoachPanel();
  }

  @Override
  public String createTableFromCoachSource(
      CoachingSourceRef sourceRef, String tableType, String tableName, Point location) {
    if (model == null || Utils.isEmpty(tableName) || Utils.isEmpty(tableType)) {
      return null;
    }
    markUndoPoint();
    String uniqueName =
        hasTableWithNameInModel(tableName, model)
            ? getUniqueTableNameFromModel(tableName, model)
            : tableName;
    IDvTable table = null;
    String normalizedType = tableType.trim();
    if ("HUB".equalsIgnoreCase(normalizedType)) {
      table = new DvHub(uniqueName);
    } else if ("SATELLITE".equalsIgnoreCase(normalizedType)) {
      table = new DvSatellite(uniqueName);
    } else if ("LINK".equalsIgnoreCase(normalizedType)) {
      table = new DvLink(uniqueName);
    }
    if (table == null) {
      return null;
    }
    int x = location != null ? location.x : 50;
    int y = location != null ? location.y : 50;
    PropsUi.setLocation(table, x, y);
    model.getTables().add(table);
    setChanged();
    redraw();
    return uniqueName;
  }

  private void editTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    for (IDvTable table : model.getTables()) {
      if (table != null && tableName.equals(table.getName())) {
        editTable(table);
        return;
      }
    }
  }

  private void highlightTableByName(String tableName) {
    if (Utils.isEmpty(tableName) || model == null) {
      return;
    }
    for (IDvTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(tableName.equals(table.getName()));
      }
    }
    redraw();
  }
}
