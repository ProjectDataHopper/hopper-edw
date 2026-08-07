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
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.action.GuiContextAction;
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
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphHit;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.sourcemodel.delegates.HopGuiSourceModelClipboardDelegate;
import org.apache.hop.datavault.hopgui.file.sourcemodel.delegates.HopGuiSourceModelSnapshotUndo;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceJsonPreviewSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceQueryPreviewSupport;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceJsonCatalogPublisher;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
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
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
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
import org.eclipse.swt.widgets.Shell;
import org.jspecify.annotations.Nullable;

/**
 * Hop GUI editor for source system models ({@code .hsm}): tables, relationships, multi-table
 * queries, and JSON extractions.
 */
@GuiPlugin(id = "HopGuiSourceModelGraph", description = "i18n::HopGuiSourceModelGraph.Description")
@Getter
@Setter
public class HopGuiSourceModelGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher {

  private static final Class<?> PKG = HopGuiSourceModelGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiSourceModelGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiSourceModelGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiSourceModelGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiSourceModelGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiSourceModelGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiSourceModelGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_EDIT_MODEL =
      "HopGuiSourceModelGraph-ToolBar-10050-Edit-Model";
  public static final String TOOLBAR_ITEM_IMPORT_SCHEMA =
      "HopGuiSourceModelGraph-ToolBar-10055-Import-Schema";
  public static final String TOOLBAR_ITEM_NEW_QUERY =
      "HopGuiSourceModelGraph-ToolBar-10057-New-Query";
  public static final String TOOLBAR_ITEM_NEW_JSON =
      "HopGuiSourceModelGraph-ToolBar-10059-New-Json";
  public static final String TOOLBAR_ITEM_PUBLISH_QUERIES =
      "HopGuiSourceModelGraph-ToolBar-10058-Publish-Queries";
  public static final String TOOLBAR_ITEM_CHECK_MODEL =
      "HopGuiSourceModelGraph-ToolBar-10060-Check-Model";
  public static final String TOOLBAR_ITEM_TOGGLE_COACH =
      "HopGuiSourceModelGraph-ToolBar-10084-Toggle-Coach";
  public static final String TOOLBAR_ITEM_TOGGLE_DURATIONS =
      "HopGuiSourceModelGraph-ToolBar-10086-Toggle-Durations";
  public static final String TOOLBAR_ITEM_SELECT_ALL =
      "HopGuiSourceModelGraph-ToolBar-20010-Select-All";
  public static final String TOOLBAR_ITEM_UNSELECT_ALL =
      "HopGuiSourceModelGraph-ToolBar-20020-Unselect-All";
  public static final String TOOLBAR_ITEM_COPY = "HopGuiSourceModelGraph-ToolBar-20030-Copy";
  public static final String TOOLBAR_ITEM_CUT = "HopGuiSourceModelGraph-ToolBar-20040-Cut";
  public static final String TOOLBAR_ITEM_PASTE = "HopGuiSourceModelGraph-ToolBar-20050-Paste";
  public static final String TOOLBAR_ITEM_DELETE = "HopGuiSourceModelGraph-ToolBar-20060-Delete";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiSourceModelGraph-ToolBar-20070-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiSourceModelGraph-ToolBar-20080-Redo";

  private final HopSourceModelFileType fileType;
  private final HopGuiSourceModelSnapshotUndo snapshotUndo = new HopGuiSourceModelSnapshotUndo();
  private final HopGuiSourceModelClipboardDelegate clipboardDelegate;
  private SourceModel model;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private boolean changed;
  private String filename;

  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private String mouseOverTableName;
  private SourceTable currentTable;
  private SourceQuery currentQuery;
  private SourceJson currentJsonSource;
  private SourceTable startRelationshipTable;
  private Point relationshipDragEndLocation;
  private SourceTable candidateRelationshipTarget;

  public HopGuiSourceModelGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      SourceModel model,
      HopSourceModelFileType fileType) {
    super(hopGui, parent, perspective);
    this.model = model;
    this.fileType = fileType;
    this.clipboardDelegate = new HopGuiSourceModelClipboardDelegate(hopGui, this);

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
      hopGui.getLog().logError("Error setting up the toolbar for HopGuiSourceModelGraph: ", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || model == null) {
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

    drawSourceModelImage(swtGc, area.x, area.y);

    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawSourceModelImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    maximum = model.getMaximum();
    try {
      areaOwners.clear();
      SourceModelPainter painter = new SourceModelPainter(model, gc, variables, width, height);
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
      painter.drawSourceModel(hopGui.getMetadataProvider());
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

  public static HopGuiSourceModelGraph getInstance() {
    IHopPerspective activePerspective = HopGui.getInstance().getActivePerspective();
    if (activePerspective instanceof ExplorerPerspective explorerPerspective) {
      if (explorerPerspective.getActiveFileTypeHandler() instanceof HopGuiSourceModelGraph graph) {
        return graph;
      }
    }
    return null;
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_LEVEL,
      label = "  Zoom: ",
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Zoom.Tooltip",
      type = GuiToolbarElementType.COMBO,
      alignRight = true,
      comboValuesMethod = "getZoomLevels")
  public void zoomLevel() {
    performZoomLevelChanged();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.ZoomIn.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.ZoomOut.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Zoom100.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.ZoomFit.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/zoom-fit.svg")
  @Override
  public void zoomFitToScreen() {
    performZoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EDIT_MODEL,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.EditModel.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "source-model.svg")
  public void editModelProperties() {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiSourceModelDialog dialog = new HopGuiSourceModelDialog(getShell(), hopGui, model);
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
      id = TOOLBAR_ITEM_IMPORT_SCHEMA,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.ImportSchema.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/schema.svg")
  public void importSchema() {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    HopGuiSourceModelImportSupport.importSchema(
        hopGui,
        model,
        () -> {
          commitDialogUndo(beforeChange);
          setChanged();
          redraw();
        });
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_NEW_QUERY,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.NewQuery.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "source-model.svg")
  public void newQueryFromToolbar() {
    addQueryAt(lastClick != null ? lastClick : new Point(50, 50));
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_NEW_JSON,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.NewJson.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "source-model.svg")
  public void newJsonFromToolbar() {
    addJsonAt(lastClick != null ? lastClick : new Point(50, 50));
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_PUBLISH_QUERIES,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.PublishQueries.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/publish.svg")
  public void publishAllQueriesFromToolbar() {
    publishAllQueries();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CHECK_MODEL,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.CheckModel.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/check.svg")
  public void checkModel() {
    if (model == null) {
      return;
    }
    ModelDialogValidationSupport.ModelCheckProgressResult result =
        ModelDialogValidationSupport.runChecksWithProgress(
            getShell(), monitor -> model.check(hopGui.getMetadataProvider(), variables, monitor));
    if (result.cancelled() && result.remarks().isEmpty()) {
      return;
    }
    CheckResultDialog dialog = new CheckResultDialog(getShell(), new ArrayList<>(result.remarks()));
    dialog.open();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_TOGGLE_COACH,
      toolTip = "i18n:org.apache.hop.datavault.hopgui.coaching:ModelCoachPanel.Toggle.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "coach-panel.svg")
  public void toggleCoachPanelToolbar() {
    toggleCoachPanel();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_TOGGLE_DURATIONS,
      toolTip =
          "i18n:org.apache.hop.datavault.hopgui.file.metrics:ModelLoadDurationPane.Toggle.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/show-results.svg")
  public void toggleLoadDurationPanelToolbar() {
    toggleLoadDurationPanel();
  }

  /** Source models have no coach use-case yet; keep the coach panel closed by default. */
  @Override
  protected boolean defaultCoachPanelVisible() {
    return false;
  }

  /** Source models have no load metrics yet; keep the duration overview closed by default. */
  @Override
  protected boolean defaultLoadDurationPanelVisible() {
    return false;
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNDO,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Undo.Tooltip",
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
      showUndoError(undoApplyErrorTitle(), undoApplyErrorMessage(), e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REDO,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Redo.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/redo.svg")
  @GuiKeyboardShortcut(control = true, shift = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, shift = true, key = 'z')
  @Override
  public void redo() {
    try {
      applySnapshotChange(snapshotUndo.redo(model, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(undoApplyErrorTitle(), undoApplyErrorMessage(), e);
    }
  }

  @GuiKeyboardShortcut(control = true, key = 'a')
  @GuiOsxKeyboardShortcut(command = true, key = 'a')
  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SELECT_ALL,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.SelectAll.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/select-all.svg")
  @Override
  public void selectAll() {
    if (model == null) {
      return;
    }
    for (SourceTable table : model.getTables()) {
      if (table != null) {
        table.setSelected(true);
      }
    }
    for (SourceQuery query : model.getQueries()) {
      if (query != null) {
        query.setSelected(true);
      }
    }
    for (SourceJson jsonSource : model.getJsonSources()) {
      if (jsonSource != null) {
        jsonSource.setSelected(true);
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
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.UnselectAll.Tooltip",
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
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Delete.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  @Override
  public void deleteSelected() {
    if (model == null) {
      return;
    }
    List<SourceTable> selected = getSelectedTables();
    List<SourceQuery> selectedQueries = getSelectedQueries();
    List<SourceJson> selectedJson = getSelectedJsonSources();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selected.isEmpty()
        && selectedQueries.isEmpty()
        && selectedJson.isEmpty()
        && selectedNotes.isEmpty()) {
      return;
    }
    markUndoPoint();
    model.getTables().removeAll(selected);
    model.getQueries().removeAll(selectedQueries);
    model.getJsonSources().removeAll(selectedJson);
    model.getNotes().removeAll(selectedNotes);
    setChanged();
    redraw();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COPY,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Copy.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/copy.svg")
  @GuiKeyboardShortcut(control = true, key = 'c')
  @GuiOsxKeyboardShortcut(command = true, key = 'c')
  @Override
  public void copySelectedToClipboard() {
    clipboardDelegate.copySelected(
        getSelectedTables(), getSelectedRelationshipsForClipboard(), getSelectedNotes());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CUT,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Cut.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/cut.svg")
  @GuiKeyboardShortcut(control = true, key = 'x')
  @GuiOsxKeyboardShortcut(command = true, key = 'x')
  @Override
  public void cutSelectedToClipboard() {
    copySelectedToClipboard();
    deleteSelected();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_PASTE,
      toolTip = "i18n::HopGuiSourceModelGraph.Toolbar.Paste.Tooltip",
      type = GuiToolbarElementType.BUTTON,
      image = "ui/images/paste.svg")
  @GuiKeyboardShortcut(control = true, key = 'v')
  @GuiOsxKeyboardShortcut(command = true, key = 'v')
  @Override
  public void pasteFromClipboard() {
    String content = clipboardDelegate.fromClipboard();
    if (Utils.isEmpty(content) || model == null) {
      return;
    }
    markUndoPoint();
    mouseInteractions().unselectAllOnCanvas();
    Point location = lastClick != null ? lastClick : new Point(50, 50);
    if (clipboardDelegate.pasteXml(model, content, location)) {
      setChanged();
      redraw();
    }
  }

  @Override
  public Object getSubject() {
    return model;
  }

  @Override
  public org.apache.hop.core.search.ISearchable createSearchable(
      String locationDescription, org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
      throws org.apache.hop.core.exception.HopException {
    return new org.apache.hop.datavault.hopgui.search.HopGuiSourceModelSearchable(
        locationDescription, model);
  }

  /**
   * Select and open a source query, JSON source, or table matching {@code componentName} (search
   * navigation). Queries are preferred when both share a name; JSON sources next.
   */
  public void openSearchComponent(String componentName) {
    if (Utils.isEmpty(componentName) || model == null) {
      return;
    }
    SourceQuery query = model.findQuery(componentName);
    if (query != null) {
      unselectAllCanvasObjects();
      query.setSelected(true);
      editQuery(query);
      updateGui();
      return;
    }
    SourceJson jsonSource = model.findJsonSource(componentName);
    if (jsonSource != null) {
      unselectAllCanvasObjects();
      jsonSource.setSelected(true);
      editJsonSource(jsonSource);
      updateGui();
      return;
    }
    SourceTable table = model.findTable(componentName);
    if (table == null) {
      return;
    }
    for (SourceTable t : model.getTables()) {
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
    return model != null ? model.getName() : "Source Model";
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
    // not applicable
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    if (perspective != null) {
      perspective.updateTabItem(this);
      perspective.updateTreeItem(this);
    }
    enableUndoToolbarItems();
    enableClipboardToolbarItems();
    if (canvas != null && !canvas.isDisposed()) {
      canvas.setFocus();
    }
  }

  private void enableClipboardToolbarItems() {
    if (toolBarWidgets == null) {
      return;
    }
    boolean hasSelection =
        !getSelectedTables().isEmpty()
            || !getSelectedQueries().isEmpty()
            || !getSelectedJsonSources().isEmpty()
            || !getSelectedNotes().isEmpty();
    boolean hasClipboard = false;
    try {
      hasClipboard = !Utils.isEmpty(GuiResource.getInstance().fromClipboard());
    } catch (Exception ignored) {
      hasClipboard = false;
    }
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_COPY, IHopFileType.CAPABILITY_COPY, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_CUT, IHopFileType.CAPABILITY_CUT, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_DELETE, IHopFileType.CAPABILITY_DELETE, hasSelection);
    toolBarWidgets.enableToolbarItem(
        fileType, this, TOOLBAR_ITEM_PASTE, IHopFileType.CAPABILITY_PASTE, hasClipboard);
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
            BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiSourceModelGraph.SaveFile.Dialog.Message", buildTabName()));
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
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveFile.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveFile.Error.Message"),
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

  @Override
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
    List<SourceTable> selection = getSelectedTables();
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

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new SourceMouseInteractions();
  }

  @Override
  protected String getMetricsModelName() {
    return model != null ? model.getName() : null;
  }

  @Override
  protected String getMetricsModelType() {
    return "SOURCE_MODEL";
  }

  @Override
  protected List<String> getMetricsTableNames() {
    if (model == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names;
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
    if (restored instanceof SourceModel sourceModel) {
      clearTableDragState();
      clearNoteDragState();
      clearSelectionRegion();
      areaOwners.clear();
      mouseOverTableName = null;
      mouseOverNoteLink = null;
      model = sourceModel;
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
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Undo.Error.Record.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Undo.Error.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Undo.Error.Apply.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Undo.Error.Apply.Message");
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
  protected AreaOwner getVisibleAreaOwner(int x, int y) {
    for (int i = areaOwners.size() - 1; i >= 0; i--) {
      AreaOwner areaOwner = areaOwners.get(i);
      if (areaOwner.contains(x, y)) {
        return areaOwner;
      }
    }
    return null;
  }

  @Override
  protected IGuiContextHandler createNoteContextHandler(DvNote note, Point real) {
    return new HopGuiSourceNoteContext(model, this, note, real);
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.NoteContext.Message");
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.NoteLink.TableTooltip", target);
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.NoteLink.Error.Title");
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return BaseMessages.getString(PKG, "HopGuiSourceModelGraph.NoteLink.UrlError.Message", target);
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return BaseMessages.getString(
        PKG, "HopGuiSourceModelGraph.NoteLink.TableNotFound.Message", tableName);
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return;
    }
    SourceTable table = model.findTable(tableName);
    if (table == null) {
      showNoteLinkTableNotFound(tableName);
      return;
    }
    mouseInteractions().unselectAllOnCanvas();
    table.setSelected(true);
    centerOnTable(table);
    redraw();
  }

  private void centerOnTable(SourceTable table) {
    if (table == null || table.getLocation() == null) {
      return;
    }
    centerOnCanvasLocation(
        table.getLocation(),
        Math.max(140, table.getDrawnBoxWidth()),
        Math.max(70, table.getDrawnBoxHeight()));
  }

  @Override
  protected void onMouseDoubleClick(Event e) {
    Point real = screen2real(e.x, e.y);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    DvNote note = getAreaOwnerNote(areaOwner);
    if (note != null) {
      editNote(note);
      return;
    }
    SourceQuery query = getAreaOwnerQuery(areaOwner);
    if (query != null) {
      editQuery(query);
      return;
    }
    SourceJson jsonSource = getAreaOwnerJsonSource(areaOwner);
    if (jsonSource != null) {
      editJsonSource(jsonSource);
      return;
    }
    SourceTable table = getAreaOwnerTable(areaOwner);
    if (table != null) {
      editTable(table);
      return;
    }
    SourceRelationship relationship = getAreaOwnerRelationship(areaOwner);
    if (relationship != null) {
      editRelationship(relationship);
    }
  }

  private void clearTableDragState() {
    positionChangeUndoMarked = false;
    iconDragStart = null;
    iconDragCommitted = false;
    dragSelection = false;
    iconOffset = null;
    currentTable = null;
    currentQuery = null;
    currentJsonSource = null;
    clearNavigationViewportState();
    clearSelectionRegion();
  }

  private void unselectAllCanvasObjects() {
    if (model == null) {
      return;
    }
    for (SourceTable t : model.getTables()) {
      if (t != null) {
        t.setSelected(false);
      }
    }
    for (SourceQuery q : model.getQueries()) {
      if (q != null) {
        q.setSelected(false);
      }
    }
    for (SourceJson j : model.getJsonSources()) {
      if (j != null) {
        j.setSelected(false);
      }
    }
  }

  private @Nullable SourceTable getAreaOwnerTable(AreaOwner areaOwner) {
    if (areaOwner != null && areaOwner.getParent() instanceof SourceTable table) {
      return table;
    }
    return null;
  }

  private List<SourceTable> getSelectedTables() {
    List<SourceTable> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (SourceTable table : model.getTables()) {
      if (table != null && table.isSelected()) {
        selected.add(table);
      }
    }
    return selected;
  }

  private List<SourceQuery> getSelectedQueries() {
    List<SourceQuery> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (SourceQuery query : model.getQueries()) {
      if (query != null && query.isSelected()) {
        selected.add(query);
      }
    }
    return selected;
  }

  private List<SourceJson> getSelectedJsonSources() {
    List<SourceJson> selected = new ArrayList<>();
    if (model == null) {
      return selected;
    }
    for (SourceJson jsonSource : model.getJsonSources()) {
      if (jsonSource != null && jsonSource.isSelected()) {
        selected.add(jsonSource);
      }
    }
    return selected;
  }

  private boolean isQueryInLassoScreenRect(
      SourceQuery query, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    return isCardInLassoScreenRect(
        query != null ? query.getLocation() : null, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY);
  }

  private boolean isJsonInLassoScreenRect(
      SourceJson jsonSource, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    return isCardInLassoScreenRect(
        jsonSource != null ? jsonSource.getLocation() : null,
        lassoMinX,
        lassoMinY,
        lassoMaxX,
        lassoMaxY);
  }

  private boolean isCardInLassoScreenRect(
      Point loc, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (loc == null) {
      return false;
    }
    int tw = 160;
    int th = 80;
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + tw;
    int tMaxY = tMinY + th;
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private void moveSelectedObjects(int dx, int dy) {
    List<SourceTable> selectedTables = getSelectedTables();
    List<SourceQuery> selectedQueries = getSelectedQueries();
    List<SourceJson> selectedJson = getSelectedJsonSources();
    List<DvNote> selectedNotes = getSelectedNotes();
    if (selectedTables.isEmpty()
        && selectedQueries.isEmpty()
        && selectedJson.isEmpty()
        && selectedNotes.isEmpty()) {
      return;
    }
    for (SourceTable table : selectedTables) {
      Point loc = table.getLocation();
      if (loc.x + dx < 0) {
        dx = -loc.x;
      }
      if (loc.y + dy < 0) {
        dy = -loc.y;
      }
    }
    for (SourceQuery query : selectedQueries) {
      Point loc = query.getLocation();
      if (loc != null) {
        if (loc.x + dx < 0) {
          dx = -loc.x;
        }
        if (loc.y + dy < 0) {
          dy = -loc.y;
        }
      }
    }
    for (SourceJson jsonSource : selectedJson) {
      Point loc = jsonSource.getLocation();
      if (loc != null) {
        if (loc.x + dx < 0) {
          dx = -loc.x;
        }
        if (loc.y + dy < 0) {
          dy = -loc.y;
        }
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
    for (SourceTable table : selectedTables) {
      Point loc = table.getLocation();
      // PropsUi.setLocation snaps to canvas grid when grid size > 1.
      PropsUi.setLocation(table, loc.x + dx, loc.y + dy);
    }
    for (SourceQuery query : selectedQueries) {
      Point loc = query.getLocation();
      if (loc != null) {
        Point snapped = PropsUi.calculateGridPosition(new Point(loc.x + dx, loc.y + dy));
        query.setLocation(snapped);
      }
    }
    for (SourceJson jsonSource : selectedJson) {
      Point loc = jsonSource.getLocation();
      if (loc != null) {
        Point snapped = PropsUi.calculateGridPosition(new Point(loc.x + dx, loc.y + dy));
        jsonSource.setLocation(snapped);
      }
    }
    for (DvNote note : selectedNotes) {
      Point loc = note.getLocation();
      PropsUi.setLocation(note, loc.x + dx, loc.y + dy);
    }
  }

  private boolean isTableInLassoScreenRect(
      SourceTable table, int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
    if (table == null || table.getLocation() == null) {
      return false;
    }
    Point loc = table.getLocation();
    int tw = Math.max(1, table.getDrawnBoxWidth() > 0 ? table.getDrawnBoxWidth() : 140);
    int th = Math.max(1, table.getDrawnBoxHeight() > 0 ? table.getDrawnBoxHeight() : 70);
    int tMinX = (int) (loc.x + offset.x);
    int tMinY = (int) (loc.y + offset.y);
    int tMaxX = tMinX + tw;
    int tMaxY = tMinY + th;
    boolean xOverlap = Math.max(lassoMinX, tMinX) < Math.min(lassoMaxX, tMaxX);
    boolean yOverlap = Math.max(lassoMinY, tMinY) < Math.min(lassoMaxY, tMaxY);
    return xOverlap && yOverlap;
  }

  private String getUniqueTableName(String base) {
    String candidate = base;
    int i = 1;
    while (model.findTable(candidate) != null) {
      candidate = base + i;
      i++;
    }
    return candidate;
  }

  private void addTableAt(Point location) {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    SourceTable table = new SourceTable(getUniqueTableName("table"));
    PropsUi.setLocation(
        table, location != null ? location.x : 50, location != null ? location.y : 50);
    model.getTables().add(table);
    boolean accepted =
        new HopGuiSourceTableDialog(
                getShell(), table, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      mouseInteractions().unselectAllOnCanvas();
      table.setSelected(true);
      setChanged();
    } else {
      model.getTables().remove(table);
    }
    redraw();
  }

  private void editTable(SourceTable table) {
    if (table == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted =
        new HopGuiSourceTableDialog(
                getShell(), table, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private void editRelationship(SourceRelationship relationship) {
    if (relationship == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted =
        new HopGuiSourceRelationshipDialog(
                getShell(), relationship, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private void editQuery(SourceQuery query) {
    if (query == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted =
        new HopGuiSourceQueryDialog(
                getShell(), query, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private void editJsonSource(SourceJson jsonSource) {
    if (jsonSource == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    boolean accepted =
        new HopGuiSourceJsonDialog(
                getShell(), jsonSource, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
      redraw();
    }
  }

  private void addJsonAt(Point location) {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    SourceJson jsonSource = new SourceJson(uniqueJsonName("json"));
    if (!model.getTables().isEmpty() && model.getTables().get(0) != null) {
      jsonSource.setParentSourceName(model.getTables().get(0).getName());
    }
    Point snapped =
        PropsUi.calculateGridPosition(
            new Point(location != null ? location.x : 50, location != null ? location.y : 50));
    jsonSource.setLocation(snapped);
    model.getJsonSources().add(jsonSource);
    boolean accepted =
        new HopGuiSourceJsonDialog(
                getShell(), jsonSource, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
    } else {
      model.getJsonSources().remove(jsonSource);
    }
    redraw();
  }

  private String uniqueJsonName(String base) {
    String name = base;
    int i = 2;
    while (model.findJsonSource(name) != null) {
      name = base + i;
      i++;
    }
    return name;
  }

  private void previewQuery(SourceQuery query) {
    if (query == null) {
      return;
    }
    try {
      List<RowMetaAndData> rows =
          SourceQueryPreviewSupport.preview(
              model, query, variables, hopGui.getMetadataProvider(), 50);
      if (rows.isEmpty()) {
        MessageBox emptyBox = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      List<Object[]> data = new ArrayList<>();
      for (RowMetaAndData row : rows) {
        data.add(row.getData());
      }
      new ShowRowsDialog(
              getShell(),
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Message"),
              rows.get(0).getRowMeta(),
              data)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceQueryDialog.Preview.Error.Message"),
          e);
    }
  }

  private void addQueryAt(Point location) {
    if (model == null) {
      return;
    }
    byte[] beforeChange = captureUndoSnapshot();
    SourceQuery query = new SourceQuery(uniqueQueryName("query"));
    if (!model.getTables().isEmpty() && model.getTables().get(0) != null) {
      query.setDrivingTableName(model.getTables().get(0).getName());
    }
    Point snapped =
        PropsUi.calculateGridPosition(
            new Point(location != null ? location.x : 50, location != null ? location.y : 50));
    query.setLocation(snapped);
    model.getQueries().add(query);
    boolean accepted =
        new HopGuiSourceQueryDialog(
                getShell(), query, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
    } else {
      model.getQueries().remove(query);
    }
    redraw();
  }

  private String uniqueQueryName(String base) {
    String name = base;
    int i = 2;
    while (model.findQuery(name) != null) {
      name = base + i;
      i++;
    }
    return name;
  }

  @FunctionalInterface
  public interface SourceModelChange {
    void run() throws Exception;
  }

  public void runUndoableModelChange(SourceModelChange change) throws HopException {
    byte[] beforeChange = captureUndoSnapshot();
    try {
      change.run();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(e);
    }
    commitDialogUndo(beforeChange);
    setChanged();
    redraw();
  }

  private void createRelationship(SourceTable from, SourceTable to) {
    if (from == null || to == null || from == to || model == null) {
      return;
    }
    // Ensure any leftover drag rubber-band is gone before the dialog paints over the canvas.
    cancelRelationshipDrag();
    redraw();

    byte[] beforeChange = captureUndoSnapshot();
    SourceRelationship relationship = new SourceRelationship();
    relationship.setName(uniqueRelationshipName("fk_" + from.getName() + "_" + to.getName()));
    relationship.setChildTableName(from.getName());
    relationship.setParentTableName(to.getName());
    relationship.setDefaultJoinType(SourceJoinType.LEFT);
    relationship.setCardinality("N:1");
    // Prefer matching PK columns by name as a starter mapping.
    for (var pk : to.primaryKeyColumns()) {
      if (pk == null || Utils.isEmpty(pk.getName())) {
        continue;
      }
      if (from.findColumn(pk.getName()) != null) {
        relationship.getChildColumns().add(pk.getName());
        relationship.getParentColumns().add(pk.getName());
      }
    }
    model.getRelationships().add(relationship);
    boolean accepted =
        new HopGuiSourceRelationshipDialog(
                getShell(), relationship, model, variables, hopGui.getMetadataProvider())
            .open();
    if (accepted) {
      commitDialogUndo(beforeChange);
      setChanged();
    } else {
      model.getRelationships().remove(relationship);
    }
    redraw();
  }

  private String uniqueRelationshipName(String base) {
    String name = base;
    int i = 2;
    while (model.findRelationship(name) != null) {
      name = base + "_" + i;
      i++;
    }
    return name;
  }

  private void cancelRelationshipDrag() {
    startRelationshipTable = null;
    relationshipDragEndLocation = null;
    candidateRelationshipTarget = null;
  }

  private SourceTable findTableAtScreen(int screenX, int screenY) {
    Point real = screen2real(screenX, screenY);
    AreaOwner areaOwner = getVisibleAreaOwner(real.x, real.y);
    return getAreaOwnerTable(areaOwner);
  }

  private SourceRelationship getAreaOwnerRelationship(AreaOwner areaOwner) {
    if (areaOwner != null && areaOwner.getParent() instanceof SourceRelationship relationship) {
      return relationship;
    }
    return null;
  }

  private SourceQuery getAreaOwnerQuery(AreaOwner areaOwner) {
    if (areaOwner != null && areaOwner.getParent() instanceof SourceQuery query) {
      return query;
    }
    return null;
  }

  private SourceJson getAreaOwnerJsonSource(AreaOwner areaOwner) {
    if (areaOwner != null && areaOwner.getParent() instanceof SourceJson jsonSource) {
      return jsonSource;
    }
    return null;
  }

  private List<SourceRelationship> getSelectedRelationshipsForClipboard() {
    // Relationships are not multi-selected on canvas yet; empty list for copy of tables only.
    return List.of();
  }

  private void showTableContextDialog(Event e, SourceTable table) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.Context.Table.Message", table.getName());
      IGuiContextHandler contextHandler =
          new HopGuiSourceTableContext(model, this, table, new Point(p.x, p.y));
      GuiContextUtil.getInstance()
          .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showRelationshipContextDialog(Event e, SourceRelationship relationship) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.Context.Relationship.Message", relationship.getName());
      IGuiContextHandler contextHandler =
          new HopGuiSourceRelationshipContext(model, this, relationship, new Point(p.x, p.y));
      GuiContextUtil.getInstance()
          .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showQueryContextDialog(Event e, SourceQuery query) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.Context.Query.Message", query.getName());
      IGuiContextHandler contextHandler =
          new HopGuiSourceQueryContext(model, this, query, new Point(p.x, p.y));
      GuiContextUtil.getInstance()
          .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private void showJsonContextDialog(Event e, SourceJson jsonSource) {
    try {
      org.eclipse.swt.graphics.Point p = getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.Context.Json.Message", jsonSource.getName());
      IGuiContextHandler contextHandler =
          new HopGuiSourceJsonContext(model, this, jsonSource, new Point(p.x, p.y));
      GuiContextUtil.getInstance()
          .handleActionSelection(getShell(), message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-table",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddTable.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddTable.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "1")
  public void addTable(HopGuiSourceModelContext context) {
    addTableAt(context != null ? context.getClick() : new Point(50, 50));
  }

  @GuiContextAction(
      id = "source-model-graph-import-schema",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.ImportSchema.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.ImportSchema.Tooltip",
      image = "ui/images/schema.svg",
      category = "Basic",
      categoryOrder = "2")
  public void importSchemaFromContext(HopGuiSourceModelContext context) {
    importSchema();
  }

  @GuiContextAction(
      id = "source-model-graph-add-query",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddQuery.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddQuery.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "4")
  public void addQuery(HopGuiSourceModelContext context) {
    addQueryAt(lastClick != null ? lastClick : new Point(50, 50));
  }

  @GuiContextAction(
      id = "source-model-graph-add-json",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddJson.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddJson.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "5")
  public void addJson(HopGuiSourceModelContext context) {
    addJsonAt(lastClick != null ? lastClick : new Point(50, 50));
  }

  @GuiContextAction(
      id = "source-model-json-edit",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editJsonAction(HopGuiSourceJsonContext context) {
    if (context != null) {
      editJsonSource(context.getJsonSource());
    }
  }

  @GuiContextAction(
      id = "source-model-json-preview",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewJsonAction(HopGuiSourceJsonContext context) {
    if (context != null) {
      previewJsonSource(context.getJsonSource());
    }
  }

  @GuiContextAction(
      id = "source-model-json-publish",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishJsonAction(HopGuiSourceJsonContext context) {
    if (context != null && context.getJsonSource() != null) {
      publishJsonSource(context.getJsonSource());
    }
  }

  private void previewJsonSource(SourceJson jsonSource) {
    if (model == null || jsonSource == null) {
      return;
    }
    try {
      SourceJsonPreviewSupport.validateForPreview(jsonSource);
      SourceJsonPreviewSupport.PreviewPipeline built =
          SourceJsonPreviewSupport.buildPreviewPipeline(
              model, jsonSource, variables, hopGui.getMetadataProvider());
      int previewRows = SourceJsonPreviewSupport.DEFAULT_ROW_LIMIT;
      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              getShell(),
              variables,
              built.pipelineMeta(),
              new String[] {built.previewTransformName()},
              new int[] {previewRows});
      progressDialog.open();
      Pipeline pipeline = progressDialog.getPipeline();
      if (progressDialog.isCancelled()) {
        return;
      }
      if (pipeline != null
          && pipeline.getResult() != null
          && pipeline.getResult().getNrErrors() > 0) {
        EnterTextDialog etd =
            new EnterTextDialog(
                getShell(),
                BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Title"),
                BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Message"),
                progressDialog.getLoggingText(),
                true);
        etd.setReadOnly();
        etd.open();
        return;
      }
      List<Object[]> data = progressDialog.getPreviewRows(built.previewTransformName());
      if (data == null || data.isEmpty()) {
        MessageBox emptyBox = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      new ShowRowsDialog(
              getShell(),
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Message"),
              progressDialog.getPreviewRowsMeta(built.previewTransformName()),
              data)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Message"),
          e);
    }
  }

  @GuiContextAction(
      id = "source-model-json-delete",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deleteJsonAction(HopGuiSourceJsonContext context) {
    if (context == null || context.getJsonSource() == null || model == null) {
      return;
    }
    markUndoPoint();
    model.getJsonSources().remove(context.getJsonSource());
    setChanged();
    redraw();
  }

  private void publishJsonSource(SourceJson jsonSource) {
    if (model == null || jsonSource == null) {
      return;
    }
    try {
      ensureModelFilenameForPublish();
      SourceJsonCatalogPublisher.PublishResult result =
          SourceJsonCatalogPublisher.publish(
              model, jsonSource, null, variables, hopGui.getMetadataProvider());
      DvDatabaseSourceImportSupport.refreshCatalogPerspective();
      MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishJson.Success.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.PublishJson.Success.Message", result.catalogName()));
      box.open();
      setChanged();
      redraw();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishJson.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishJson.Error.Message"),
          e);
    }
  }

  @GuiContextAction(
      id = "source-model-query-edit",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editQueryAction(HopGuiSourceQueryContext context) {
    if (context != null) {
      editQuery(context.getQuery());
    }
  }

  @GuiContextAction(
      id = "source-model-query-preview",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewQueryAction(HopGuiSourceQueryContext context) {
    if (context != null) {
      previewQuery(context.getQuery());
    }
  }

  @GuiContextAction(
      id = "source-model-query-publish",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishQueryAction(HopGuiSourceQueryContext context) {
    if (context != null && context.getQuery() != null) {
      publishQuery(context.getQuery());
    }
  }

  @GuiContextAction(
      id = "source-model-query-delete",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deleteQueryAction(HopGuiSourceQueryContext context) {
    if (context == null || context.getQuery() == null || model == null) {
      return;
    }
    markUndoPoint();
    model.getQueries().remove(context.getQuery());
    setChanged();
    redraw();
  }

  private void publishQuery(SourceQuery query) {
    if (model == null || query == null) {
      return;
    }
    try {
      ensureModelFilenameForPublish();
      SourceQueryCatalogPublisher.PublishResult result =
          SourceQueryCatalogPublisher.publish(
              model, query, null, variables, hopGui.getMetadataProvider());
      DvDatabaseSourceImportSupport.refreshCatalogPerspective();
      setChanged();
      MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQuery.Success.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.PublishQuery.Success.Message", result.catalogName()));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQuery.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQuery.Error.Message"),
          e);
    }
  }

  private void publishAllQueries() {
    if (model == null) {
      return;
    }
    try {
      ensureModelFilenameForPublish();
      List<SourceQueryCatalogPublisher.PublishResult> results =
          SourceQueryCatalogPublisher.publishAll(
              model, null, variables, hopGui.getMetadataProvider());
      DvDatabaseSourceImportSupport.refreshCatalogPerspective();
      setChanged();
      MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQueries.Success.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiSourceModelGraph.PublishQueries.Success.Message", results.size()));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQueries.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PublishQueries.Error.Message"),
          e);
    }
  }

  private void ensureModelFilenameForPublish() {
    if (model != null && Utils.isEmpty(model.getFilename()) && !Utils.isEmpty(getFilename())) {
      model.setFilename(getFilename());
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-note",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddNote.Tooltip",
      image = "ui/images/note-add.svg",
      category = "Basic",
      categoryOrder = "3")
  public void addNote(HopGuiSourceModelContext context) {
    if (model == null) {
      return;
    }
    markUndoPoint();
    DvNote note = new DvNote();
    note.setNoteType(DvNoteType.GENERAL);
    note.setText("");
    Point location = lastClick != null ? lastClick : new Point(50, 50);
    PropsUi.setLocation(note, location.x, location.y);
    model.getNotes().add(note);
    setChanged();
    editNote(note);
    redraw();
  }

  @GuiContextAction(
      id = "source-model-table-edit",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Table.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Table.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editTableAction(HopGuiSourceTableContext context) {
    if (context != null) {
      editTable(context.getTable());
    }
  }

  @GuiContextAction(
      id = "source-model-table-delete",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Table.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Table.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "2")
  public void deleteTableAction(HopGuiSourceTableContext context) {
    if (context == null || context.getTable() == null || model == null) {
      return;
    }
    SourceTable table = context.getTable();
    String tableName = table.getName();
    markUndoPoint();
    model.getTables().remove(table);
    model
        .getRelationships()
        .removeIf(
            r ->
                r != null
                    && (tableName.equals(r.getChildTableName())
                        || tableName.equals(r.getParentTableName())));
    setChanged();
    redraw();
  }

  @GuiContextAction(
      id = "source-model-relationship-edit",
      parentId = HopGuiSourceRelationshipContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Relationship.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Relationship.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editRelationshipAction(HopGuiSourceRelationshipContext context) {
    if (context != null) {
      editRelationship(context.getRelationship());
    }
  }

  @GuiContextAction(
      id = "source-model-relationship-delete",
      parentId = HopGuiSourceRelationshipContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Relationship.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Relationship.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "2")
  public void deleteRelationshipAction(HopGuiSourceRelationshipContext context) {
    if (context == null || context.getRelationship() == null || model == null) {
      return;
    }
    markUndoPoint();
    model.getRelationships().remove(context.getRelationship());
    setChanged();
    redraw();
  }

  @GuiContextAction(
      id = "source-model-note-edit",
      parentId = HopGuiSourceNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Note.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Note.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editNoteAction(HopGuiSourceNoteContext context) {
    if (context != null) {
      editNote(context.getNote());
    }
  }

  @GuiContextAction(
      id = "source-model-note-delete",
      parentId = HopGuiSourceNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Note.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Note.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiSourceNoteContext context) {
    if (context == null || context.getNote() == null || model == null) {
      return;
    }
    markUndoPoint();
    model.getNotes().remove(context.getNote());
    setChanged();
    redraw();
  }

  private void showSourceContextDialog(Event e, Point real) {
    try {
      Shell parent = getShell();
      org.eclipse.swt.graphics.Point p = parent.getDisplay().map(canvas, null, e.x, e.y);
      String message = BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Canvas.Message");
      IGuiContextHandler contextHandler = new HopGuiSourceModelContext(model, this, real);
      GuiContextUtil.getInstance()
          .handleActionSelection(parent, message, new Point(p.x, p.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Header"),
          BaseMessages.getString(PKG, "HopGuiSourceModelGraph.Context.Error.Message"),
          ex);
    } finally {
      canvas.setFocus();
    }
  }

  private final class SourceMouseInteractions implements ModelGraphMouseInteractions {

    @Override
    public ModelGraphHit resolveHit(int logicalX, int logicalY) {
      AreaOwner areaOwner = getVisibleAreaOwner(logicalX, logicalY);
      SourceTable table = getAreaOwnerTable(areaOwner);
      SourceQuery query = getAreaOwnerQuery(areaOwner);
      SourceJson jsonSource = getAreaOwnerJsonSource(areaOwner);
      DvNote note = getAreaOwnerNote(areaOwner);
      AreaOwner.AreaType areaType = areaOwner == null ? null : areaOwner.getAreaType();
      Object canvasObject = query != null ? query : (jsonSource != null ? jsonSource : table);
      return new ModelGraphHit(areaOwner, areaType, note, canvasObject);
    }

    @Override
    public boolean handleObjectMouseDown(
        Event e, Point real, ModelGraphHit hit, boolean shift, boolean control) {
      Object obj = hit.canvasObject();
      if (obj instanceof SourceQuery queryHit) {
        AreaOwner.AreaType areaType = hit.areaType();
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
          avoidContextDialog = true;
          editQuery(queryHit);
          clearTableDragState();
          return true;
        }
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
          prepareExclusiveDragSelection(
              control, queryHit.isSelected(), () -> queryHit.setSelected(true));
          currentQuery = queryHit;
          currentJsonSource = null;
          currentTable = null;
          iconDragStart = new Point(real.x, real.y);
          iconDragCommitted = false;
          Point loc = queryHit.getLocation() != null ? queryHit.getLocation() : new Point(0, 0);
          iconOffset = new Point(real.x - loc.x, real.y - loc.y);
          clearNoteDragState();
          clearSelectionRegion();
          redraw();
          return true;
        }
        return false;
      }
      if (obj instanceof SourceJson jsonHit) {
        AreaOwner.AreaType areaType = hit.areaType();
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_NAME) {
          avoidContextDialog = true;
          editJsonSource(jsonHit);
          clearTableDragState();
          return true;
        }
        if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
          prepareExclusiveDragSelection(
              control, jsonHit.isSelected(), () -> jsonHit.setSelected(true));
          currentJsonSource = jsonHit;
          currentQuery = null;
          currentTable = null;
          iconDragStart = new Point(real.x, real.y);
          iconDragCommitted = false;
          Point loc = jsonHit.getLocation() != null ? jsonHit.getLocation() : new Point(0, 0);
          iconOffset = new Point(real.x - loc.x, real.y - loc.y);
          clearNoteDragState();
          clearSelectionRegion();
          redraw();
          return true;
        }
        return false;
      }
      if (!(obj instanceof SourceTable tableHit)) {
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
        editTable(tableHit);
        clearTableDragState();
        return true;
      }

      if (e.button == 1 && areaType == AreaOwner.AreaType.TRANSFORM_ICON) {
        prepareExclusiveDragSelection(
            control, tableHit.isSelected(), () -> tableHit.setSelected(true));
        currentTable = tableHit;
        currentQuery = null;
        currentJsonSource = null;
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
    public void handleRelationshipMouseMove(Event e) {
      relationshipDragEndLocation = new Point(e.x, e.y);
      candidateRelationshipTarget = findTableAtScreen(e.x, e.y);
      if (candidateRelationshipTarget == startRelationshipTable) {
        candidateRelationshipTarget = null;
      }
    }

    @Override
    public boolean handleRelationshipMouseUp(Event e, Point real) {
      SourceTable from = startRelationshipTable;
      SourceTable target = findTableAtScreen(e.x, e.y);
      // Clear the candidate line before the modal dialog so the painter stops drawing it.
      cancelRelationshipDrag();
      clearTableDragState();
      avoidContextDialog = true;
      redraw();
      if (from != null && target != null && target != from) {
        createRelationship(from, target);
      }
      return true;
    }

    @Override
    public boolean handleObjectMouseMove(Point real, boolean leftButtonDown) {
      if (!leftButtonDown
          || (currentTable == null && currentQuery == null && currentJsonSource == null)
          || startRelationshipTable != null
          || resize != null) {
        return false;
      }
      if (currentTable != null) {
        currentTable.setSelected(true);
      }
      if (currentQuery != null) {
        currentQuery.setSelected(true);
      }
      if (currentJsonSource != null) {
        currentJsonSource.setSelected(true);
      }
      if (iconOffset == null) {
        iconOffset = new Point(0, 0);
      }
      Point icon = new Point(real.x - iconOffset.x, real.y - iconOffset.y);
      boolean doRedraw = false;
      if (tryCommitIconDrag(real)) {
        doRedraw = true;
      }
      Point baseLoc;
      boolean selected;
      if (currentJsonSource != null) {
        baseLoc = currentJsonSource.getLocation();
        selected = currentJsonSource.isSelected();
      } else if (currentQuery != null) {
        baseLoc = currentQuery.getLocation();
        selected = currentQuery.isSelected();
      } else {
        baseLoc = currentTable.getLocation();
        selected = currentTable.isSelected();
      }
      if (iconDragCommitted && selected && baseLoc != null) {
        int dx = icon.x - baseLoc.x;
        int dy = icon.y - baseLoc.y;
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
      for (SourceTable table : model.getTables()) {
        if (table != null) {
          table.setSelected(false);
        }
      }
      for (SourceQuery query : model.getQueries()) {
        if (query != null) {
          query.setSelected(false);
        }
      }
      for (SourceJson jsonSource : model.getJsonSources()) {
        if (jsonSource != null) {
          jsonSource.setSelected(false);
        }
      }
      unselectAllNotes();
    }

    @Override
    public void selectInLassoRegion(int lassoMinX, int lassoMinY, int lassoMaxX, int lassoMaxY) {
      if (model == null) {
        return;
      }
      for (SourceTable table : model.getTables()) {
        if (isTableInLassoScreenRect(table, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          table.setSelected(true);
        }
      }
      for (SourceQuery query : model.getQueries()) {
        if (isQueryInLassoScreenRect(query, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          query.setSelected(true);
        }
      }
      for (SourceJson jsonSource : model.getJsonSources()) {
        if (isJsonInLassoScreenRect(jsonSource, lassoMinX, lassoMinY, lassoMaxX, lassoMaxY)) {
          jsonSource.setSelected(true);
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
      // Hop model canvases use left-click for action menus (not right-click).
      if (e.button != 1) {
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
        return true;
      }

      if (lastClick == null || lastClick.x != real.x || lastClick.y != real.y) {
        clearTableDragState();
        clearNoteDragState();
        avoidContextDialog = false;
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

      SourceQuery queryHit = getAreaOwnerQuery(getVisibleAreaOwner(real.x, real.y));
      if (queryHit != null) {
        if (isControlDown(e)) {
          avoidContextDialog = true;
          editQuery(queryHit);
        } else if (!avoidContextDialog) {
          showQueryContextDialog(e, queryHit);
        }
        clearTableDragState();
        avoidContextDialog = false;
        return true;
      }

      SourceJson jsonHit = getAreaOwnerJsonSource(getVisibleAreaOwner(real.x, real.y));
      if (jsonHit != null) {
        if (isControlDown(e)) {
          avoidContextDialog = true;
          editJsonSource(jsonHit);
        } else if (!avoidContextDialog) {
          showJsonContextDialog(e, jsonHit);
        }
        clearTableDragState();
        avoidContextDialog = false;
        return true;
      }

      // Prefer relationship label hit over a leftover table selection from mouse-down.
      SourceRelationship relationshipHit =
          getAreaOwnerRelationship(getVisibleAreaOwner(real.x, real.y));
      if (relationshipHit != null) {
        if (isControlDown(e)) {
          // CTRL-click: open edit dialog directly (no context menu).
          avoidContextDialog = true;
          editRelationship(relationshipHit);
        } else if (!avoidContextDialog) {
          showRelationshipContextDialog(e, relationshipHit);
        }
        clearTableDragState();
        avoidContextDialog = false;
        return true;
      }

      SourceTable hit = currentTable;
      if (hit == null) {
        if (!avoidContextDialog) {
          showSourceContextDialog(e, real);
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
          && !dragSelection
          && selectionRegion == null) {
        if (areaOwner.getParent() instanceof SourceTable sourceTable
            && sourceTable.getName() != null) {
          newOver = sourceTable.getName();
        } else if (areaOwner.getParent() instanceof SourceQuery sourceQuery
            && sourceQuery.getName() != null) {
          // Same underline affordance as table cards (painter matches mouseOverTableName).
          newOver = sourceQuery.getName();
        } else if (areaOwner.getParent() instanceof SourceJson sourceJson
            && sourceJson.getName() != null) {
          newOver = sourceJson.getName();
        }
      }
      if ((mouseOverTableName == null && newOver != null)
          || (mouseOverTableName != null && !mouseOverTableName.equals(newOver))) {
        mouseOverTableName = newOver;
        return true;
      }
      return false;
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
}
