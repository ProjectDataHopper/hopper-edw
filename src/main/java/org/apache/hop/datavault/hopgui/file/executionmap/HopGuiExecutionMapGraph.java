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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SnapAllignDistribute;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.command.executionmap.ExecutionMapService;
import org.apache.hop.datavault.command.svg.ExecutionMapExportScope;
import org.apache.hop.datavault.command.svg.SvgExportService;
import org.apache.hop.datavault.command.svg.SvgRenderOptions;
import org.apache.hop.datavault.executionmap.ExecutionMapFocusContext;
import org.apache.hop.datavault.executionmap.ExecutionMapViewSupport;
import org.apache.hop.datavault.hopgui.executionmap.ExecutionMapGenerationDialog;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.CanvasFacade;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

/** Read-only Hop GUI viewer for execution map documents. */
@GuiPlugin(id = "HopGuiExecutionMapGraph", description = "Hop execution map graph")
@Getter
@Setter
public class HopGuiExecutionMapGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher {
  private static final Class<?> PKG = HopGuiExecutionMapGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiExecutionMapGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiExecutionMapGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiExecutionMapGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiExecutionMapGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiExecutionMapGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiExecutionMapGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_REFRESH = "HopGuiExecutionMapGraph-ToolBar-10050-Refresh";
  public static final String TOOLBAR_ITEM_EXPORT_SVG =
      "HopGuiExecutionMapGraph-ToolBar-10060-Export-Svg";
  public static final String TOOLBAR_ITEM_EXPORT_LINEAGE =
      "HopGuiExecutionMapGraph-ToolBar-10070-Export-Lineage";
  public static final String TOOLBAR_ITEM_EXPORT_FULL_SVG =
      "HopGuiExecutionMapGraph-ToolBar-10080-Export-Full-Svg";
  private static final String STATE_FOCUS_NODE_ID = "focusNodeId";
  private static final String ACTION_ID_OPEN_ARTIFACT = "execution-map-graph-open-artifact";
  private static final String ACTION_ID_VIEW_SNAPSHOT = "execution-map-graph-view-snapshot";
  private static final String ACTION_ID_OPEN_IN_CATALOG = "execution-map-graph-open-in-catalog";
  private static final String ACTION_ID_PREVIEW_DATASET = "execution-map-graph-preview-dataset";
  private static final String ACTION_ID_VIEW_DATASET = "execution-map-graph-view-dataset";

  private final HopExecutionMapFileType fileType;
  private final ModelGraphSnapshotUndo<ExecutionMapDocument> snapshotUndo =
      new ModelGraphSnapshotUndo<>(
          ExecutionMapDocument.class, HopExecutionMapFileType.XML_TAG, ExecutionMapDocument::new);
  private ExecutionMapDocument document;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private String filename;
  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private final ExecutionMapFocusContext focusContext = new ExecutionMapFocusContext();
  private ExecutionMapBreadcrumbBar breadcrumbBar;
  ExecutionMapNode mouseOverNode;
  String mouseOverNodeName;

  public HopGuiExecutionMapGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      ExecutionMapDocument document,
      HopExecutionMapFileType fileType) {
    super(hopGui, parent, perspective);
    this.document = document;
    this.fileType = fileType;
    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());
    if (document == null) {
      return;
    }
    syncMaximumFromDocument();
    setLayout(new FormLayout());
    addToolBar();
    breadcrumbBar =
        new ExecutionMapBreadcrumbBar(this, this::navigateToFocus, this::navigateToRootFocus);
    FormData fdBreadcrumb = new FormData();
    fdBreadcrumb.left = new FormAttachment(0, 0);
    fdBreadcrumb.top = new FormAttachment(0, toolBar.getBounds().height);
    fdBreadcrumb.right = new FormAttachment(100, 0);
    breadcrumbBar.setLayoutData(fdBreadcrumb);
    breadcrumbBar.pack();
    canvas = new Canvas(this, SWT.NO_BACKGROUND);
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment(0, 0);
    fdCanvas.top = new FormAttachment(breadcrumbBar, 0);
    fdCanvas.right = new FormAttachment(100, 0);
    fdCanvas.bottom = new FormAttachment(100, 0);
    canvas.setLayoutData(fdCanvas);
    canvas.addPaintListener(this::paintControl);
    registerCanvasMouseListeners();
    hopGui.replaceKeyboardShortcutListeners(this);
    canvas.setFocus();
    setZoomLabel();
    layout(true, true);
    if (breadcrumbBar != null) {
      breadcrumbBar.update(document, focusContext);
    }
  }

  public static HopGuiExecutionMapGraph getInstance() {
    IHopFileTypeHandler activeFileTypeHandler =
        HopGui.getExplorerPerspective().getActiveFileTypeHandler();
    if (activeFileTypeHandler instanceof HopGuiExecutionMapGraph graph) {
      return graph;
    }
    return null;
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
      hopGui.getLog().logError("Error setting up execution map toolbar", e);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0 || document == null) {
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
    drawExecutionMapImage(swtGc, area.x, area.y);
    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawExecutionMapImage(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    try {
      areaOwners.clear();
      float paintMagnification = (float) (magnification * PropsUi.getNativeZoomFactor());
      ExecutionMapPainter painter =
          new ExecutionMapPainter(
              document,
              gc,
              variables,
              width,
              height,
              null,
              focusContext,
              ExecutionMapExportScope.FOCUSED);
      var cardMetrics =
          ExecutionMapViewSupport.prepareFocusedView(
              document, focusContext, gc, paintMagnification);
      maximum =
          ExecutionMapViewSupport.computeViewMaximum(
              org.apache.hop.datavault.executionmap.ExecutionMapViewFilter.getVisibleNodes(
                  document, focusContext),
              cardMetrics);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification(paintMagnification);
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverNodeName(mouseOverNodeName);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.drawExecutionMap();
      captureNavigationViewGeometry(painter);
      CanvasFacade.setData(canvas, magnification, offset, document);
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
  protected String getMetricsModelName() {
    return null;
  }

  @Override
  protected String getMetricsModelType() {
    return null;
  }

  @Override
  protected List<String> getMetricsTableNames() {
    return List.of();
  }

  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new ExecutionMapReadOnlyInteractions(this);
  }

  @Override
  protected ModelGraphSnapshotUndo<?> getSnapshotUndo() {
    return snapshotUndo;
  }

  @Override
  protected Object getModelForUndo() {
    return document;
  }

  @Override
  protected void restoreModelSnapshot(Object restored) {
    if (restored instanceof ExecutionMapDocument restoredDocument) {
      this.document = restoredDocument;
    }
  }

  @Override
  protected void clearSelectionRegion() {
    selectionRegion = null;
  }

  @Override
  protected String undoRecordErrorTitle() {
    return "Execution map";
  }

  @Override
  protected String undoRecordErrorMessage() {
    return "Unable to record execution map undo state";
  }

  @Override
  protected String undoApplyErrorTitle() {
    return "Execution map";
  }

  @Override
  protected String undoApplyErrorMessage() {
    return "Unable to apply execution map undo state";
  }

  @Override
  protected String undoToolbarItemId() {
    return "HopGuiExecutionMapGraph-ToolBar-Undo";
  }

  @Override
  protected String redoToolbarItemId() {
    return "HopGuiExecutionMapGraph-ToolBar-Redo";
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
    return List.of();
  }

  @Override
  protected AreaOwner getVisibleAreaOwner(int x, int y) {
    return AreaOwner.getVisibleAreaOwner(areaOwners, x, y);
  }

  @Override
  protected IGuiContextHandler createNoteContextHandler(DvNote note, Point real) {
    return null;
  }

  @Override
  protected String getNoteContextDialogMessage() {
    return "";
  }

  @Override
  protected String getNoteLinkTableTooltip(String target) {
    return target;
  }

  @Override
  protected String getNoteLinkErrorTitle() {
    return "Execution map";
  }

  @Override
  protected String getNoteLinkUrlErrorMessage(String target) {
    return target;
  }

  @Override
  protected String getNoteLinkTableNotFoundMessage(String tableName) {
    return tableName;
  }

  @Override
  protected void navigateToNoteLinkTable(String tableName) {}

  @Override
  protected void onMouseDoubleClick(Event e) {
    Point real = screen2real(e.x, e.y);
    ExecutionMapNode node = getAreaOwnerNode(getVisibleAreaOwner(real.x, real.y));
    if (node != null) {
      ExecutionMapNavigationSupport.openNode(hopGui, variables, document, node);
    }
  }

  @Override
  protected boolean handleLassoMouseDown(
      Event e, Point real, boolean control, boolean onBackground) {
    // Read-only viewer: skip lasso selection so base pan/zoom handlers run instead.
    return false;
  }

  @Override
  protected void updateGraphAfterNavigationPan() {
    setZoomLabel();
  }

  ExecutionMapNode getAreaOwnerNode(AreaOwner areaOwner) {
    if (areaOwner == null) {
      return null;
    }
    if (areaOwner.getParent() instanceof ExecutionMapNode executionMapNode) {
      return executionMapNode;
    }
    return null;
  }

  void showNodeContextDialog(Event e, ExecutionMapNode node) {
    if (node == null || document == null) {
      return;
    }
    try {
      Point real = screen2real(e.x, e.y);
      org.eclipse.swt.graphics.Point screenPoint =
          getShell().getDisplay().map(canvas, null, e.x, e.y);
      String message =
          BaseMessages.getString(
              PKG, "HopGuiExecutionMapGraph.Context.Node.Message", node.getName());
      IGuiContextHandler contextHandler =
          new HopGuiExecutionMapNodeContext(document, this, node, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(
                  getShell(), message, new Point(screenPoint.x, screenPoint.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Context.Error.Message"),
          ex);
    }
  }

  boolean isPureClickAt(Point real) {
    return lastClick != null && lastClick.x == real.x && lastClick.y == real.y;
  }

  void updateNodeHoverTooltip(ExecutionMapNode node) {
    if (canvas == null || canvas.isDisposed()) {
      return;
    }
    String tooltip =
        ExecutionMapNavigationSupport.buildTooltip(
            node, document, variables, hopGui.getMetadataProvider());
    canvas.setToolTipText(tooltip);
  }

  @GuiContextActionFilter(parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID)
  public boolean filterNodeContextActions(
      String contextActionId, HopGuiExecutionMapNodeContext context) {
    ExecutionMapNode node = context.getNode();
    if (ACTION_ID_OPEN_ARTIFACT.equals(contextActionId)) {
      return ExecutionMapNavigationSupport.canNavigate(node, context.getDocument());
    }
    if (ACTION_ID_VIEW_SNAPSHOT.equals(contextActionId)) {
      return ExecutionMapNavigationSupport.canOpenFromSnapshot(node, context.getDocument());
    }
    if (ACTION_ID_OPEN_IN_CATALOG.equals(contextActionId)) {
      return ExecutionMapNavigationSupport.canNavigateToCatalog(
          node, context.getDocument(), variables, hopGui.getMetadataProvider());
    }
    if (ACTION_ID_PREVIEW_DATASET.equals(contextActionId)) {
      return ExecutionMapNavigationSupport.canPreviewDataset(
          node, context.getDocument(), variables, hopGui.getMetadataProvider());
    }
    if (ACTION_ID_VIEW_DATASET.equals(contextActionId)) {
      return ExecutionMapDatasetDetailsViewer.isDatasetNode(node);
    }
    return true;
  }

  @GuiContextAction(
      id = ACTION_ID_OPEN_ARTIFACT,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.OpenArtifact.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.OpenArtifact.Tooltip",
      image = "ui/images/open.svg",
      category = "Execution map",
      categoryOrder = "1")
  public void openArtifactFromContext(HopGuiExecutionMapNodeContext context) {
    if (context.getNode() != null) {
      ExecutionMapNavigationSupport.openNode(
          hopGui, variables, context.getDocument(), context.getNode());
    }
  }

  @GuiContextAction(
      id = ACTION_ID_VIEW_SNAPSHOT,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.ViewSnapshot.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.ViewSnapshot.Tooltip",
      image = "ui/images/view.svg",
      category = "Execution map",
      categoryOrder = "2")
  public void viewSnapshotFromContext(HopGuiExecutionMapNodeContext context) {
    ExecutionMapSnapshotViewer.showSnapshot(
        hopGui.getShell(), context.getDocument(), context.getNode());
  }

  @GuiContextAction(
      id = ACTION_ID_OPEN_IN_CATALOG,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.OpenInCatalog.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.OpenInCatalog.Tooltip",
      image = "data-catalog.svg",
      category = "Execution map",
      categoryOrder = "3")
  public void openInCatalogFromContext(HopGuiExecutionMapNodeContext context) {
    if (context.getNode() != null) {
      try {
        ExecutionMapNavigationSupport.openDatasetInCatalog(
            hopGui,
            variables,
            hopGui.getMetadataProvider(),
            context.getDocument(),
            context.getNode());
      } catch (Exception e) {
        new ErrorDialog(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.OpenTitle"),
            BaseMessages.getString(
                PKG,
                "ExecutionMapNavigationSupport.Error.OpenMessage",
                ExecutionMapNavigationSupport.describeNode(context.getNode())),
            e instanceof HopException ? e : new HopException(e));
      }
    }
  }

  @GuiContextAction(
      id = ACTION_ID_PREVIEW_DATASET,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.PreviewDataset.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.PreviewDataset.Tooltip",
      image = "ui/images/preview.svg",
      category = "Execution map",
      categoryOrder = "4")
  public void previewDatasetFromContext(HopGuiExecutionMapNodeContext context) {
    if (context.getNode() != null) {
      try {
        ExecutionMapNavigationSupport.previewDataset(
            hopGui,
            variables,
            hopGui.getMetadataProvider(),
            context.getDocument(),
            context.getNode());
      } catch (Exception e) {
        new ErrorDialog(
            hopGui.getShell(),
            BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.PreviewTitle"),
            BaseMessages.getString(
                PKG,
                "ExecutionMapNavigationSupport.Error.PreviewMessage",
                ExecutionMapNavigationSupport.describeNode(context.getNode())),
            e instanceof HopException ? e : new HopException(e));
      }
    }
  }

  @GuiContextAction(
      id = ACTION_ID_VIEW_DATASET,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.ViewDataset.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.ViewDataset.Tooltip",
      image = "ui/images/view.svg",
      category = "Execution map",
      categoryOrder = "5")
  public void viewDatasetFromContext(HopGuiExecutionMapNodeContext context) {
    ExecutionMapDatasetDetailsViewer.showDetails(hopGui.getShell(), context.getNode());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPORT_SVG,
      toolTip = "i18n::HopGuiExecutionMapGraph.Toolbar.ExportSvg.Tooltip",
      image = "ui/images/image.svg")
  public void exportExecutionMapToSvg() {
    exportToSvg(ExecutionMapExportScope.FOCUSED);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPORT_FULL_SVG,
      toolTip = "i18n::HopGuiExecutionMapGraph.Toolbar.ExportFullSvg.Tooltip",
      image = "ui/images/image.svg")
  public void exportFullExecutionMapToSvg() {
    exportToSvg(ExecutionMapExportScope.FULL);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPORT_LINEAGE,
      toolTip = "i18n::HopGuiExecutionMapGraph.Toolbar.ExportLineage.Tooltip",
      image = "ui/images/download.svg")
  public void exportExecutionMapLineage() {
    exportLineage();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::HopGuiExecutionMapGraph.Toolbar.Refresh.Tooltip",
      image = "ui/images/refresh.svg",
      separator = true)
  public void refreshExecutionMap() {
    if (document == null
        || org.apache.hop.core.util.Utils.isEmpty(document.getRootArtifactPath())) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Refresh.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Refresh.Error.NoRoot"),
          null);
      return;
    }
    ExecutionMapGenerationDialog.Result generationOptions =
        ExecutionMapGenerationDialog.open(
            hopGui.getShell(), ExecutionMapGenerationDialog.Purpose.REFRESH);
    if (generationOptions == null) {
      return;
    }
    try {
      ExecutionMapService.RefreshResult result =
          ExecutionMapService.refresh(
              document,
              variables,
              hopGui.getMetadataProvider(),
              generationOptions.getCrawlOptions());
      this.document = result.getDocument();
      syncMaximumFromDocument();
      ExecutionMapDiffViewer.showDiff(hopGui.getShell(), result.getDiff());
      updateGui();
      performZoomFitToScreen();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Refresh.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Refresh.Error.Message"),
          e);
    }
  }

  public void exportToSvg() {
    exportToSvg(ExecutionMapExportScope.FOCUSED);
  }

  public void exportToSvg(ExecutionMapExportScope exportScope) {
    if (document == null) {
      return;
    }
    try {
      SvgRenderOptions options = SvgRenderOptions.defaults();
      options.setExecutionMapExportScope(
          exportScope != null ? exportScope : ExecutionMapExportScope.FOCUSED);
      options.setExecutionMapFocus(focusContext);
      String svgXml = SvgExportService.generateExecutionMapSvg(document, options, variables);
      String proposedName = Const.NVL(document.getName(), "execution-map") + ".svg";
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
        box.setText(BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Export.Overwrite.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Export.Overwrite.Message"));
        if (box.open() != SWT.YES) {
          return;
        }
      }
      SvgExportService.writeSvg(realFilename, svgXml);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.ExportSvg.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.ExportSvg.Error.Message"),
          e);
    }
  }

  public void exportLineage() {
    if (document == null) {
      return;
    }
    try {
      String proposedName = Const.NVL(document.getName(), "execution-map") + "-lineage.json";
      String proposedFilename = variables.getVariable("user.home") + File.separator + proposedName;
      String filenameFromUser =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getShell(),
              null,
              variables,
              HopVfs.getFileObject(proposedFilename),
              new String[] {"*.json"},
              new String[] {"JSON Files"},
              true);
      if (filenameFromUser == null) {
        return;
      }
      ExecutionMapService.exportLineage(document, variables.resolve(filenameFromUser), variables);
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.ExportLineage.Success.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiExecutionMapGraph.ExportLineage.Success.Message", filenameFromUser));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.ExportLineage.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.ExportLineage.Error.Message"),
          e);
    }
  }

  @Override
  public boolean hasChanged() {
    return false;
  }

  @Override
  public void setChanged() {
    // read-only viewer
  }

  @Override
  public void undo() {
    // read-only viewer
  }

  @Override
  public void redo() {
    // read-only viewer
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    if (breadcrumbBar != null && !breadcrumbBar.isDisposed()) {
      breadcrumbBar.update(document, focusContext);
    }
    setZoomLabel();
    redraw();
  }

  public void drillInto(ExecutionMapNode node) {
    if (node == null || document == null || !focusContext.canDrillInto(node, document)) {
      return;
    }
    focusContext.drillInto(node.getId());
    performZoomFitToScreen();
    updateGui();
  }

  public void navigateToFocus(String nodeId) {
    focusContext.navigateTo(nodeId, document);
    performZoomFitToScreen();
    updateGui();
  }

  public void navigateToRootFocus() {
    focusContext.navigateToRoot();
    performZoomFitToScreen();
    updateGui();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "Zoom in",
      image = "ui/images/zoom-in.svg")
  @Override
  public void zoomIn() {
    performZoomIn();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "Zoom out",
      image = "ui/images/zoom-out.svg")
  @Override
  public void zoomOut() {
    performZoomOut();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "Zoom 100%",
      image = "ui/images/zoom-100.svg")
  @Override
  public void zoom100Percent() {
    performZoom100Percent();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "Zoom fit",
      image = "ui/images/zoom-fit.svg")
  public void zoomFit() {
    performZoomFitToScreen();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_LEVEL,
      label = "  Zoom: ",
      toolTip = "Zoom in or out",
      type = org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType.COMBO,
      alignRight = true,
      comboValuesMethod = "getZoomLevels")
  public void zoomLevel() {
    performZoomLevelChanged();
  }

  private void syncMaximumFromDocument() {
    if (document != null) {
      maximum = document.getMaximum();
    }
  }

  @Override
  public Object getSubject() {
    return document;
  }

  @Override
  public String getName() {
    return document != null ? document.getName() : "Execution map";
  }

  @Override
  public void setName(String name) {
    if (document != null) {
      document.setName(name);
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
    return filename;
  }

  @Override
  public void setFilename(String filename) {
    this.filename = filename;
    if (document != null) {
      document.setFilename(filename);
    }
  }

  @Override
  public void save() {}

  @Override
  public void saveAs(String filename) {}

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void pause() {}

  @Override
  public void resume() {}

  @Override
  public void preview() {}

  @Override
  public void debug() {}

  @Override
  public void selectAll() {}

  @Override
  public void unselectAll() {}

  @Override
  public void copySelectedToClipboard() {}

  @Override
  public void cutSelectedToClipboard() {}

  @Override
  public void deleteSelected() {}

  @Override
  public void pasteFromClipboard() {}

  @Override
  public boolean isCloseable() {
    return true;
  }

  @Override
  public void close() {
    perspective.remove(this);
  }

  @Override
  public Map<String, Object> getStateProperties() {
    Map<String, Object> props = buildCanvasStateProperties();
    if (!org.apache.hop.core.util.Utils.isEmpty(focusContext.getFocusNodeId())) {
      props.put(STATE_FOCUS_NODE_ID, focusContext.getFocusNodeId());
    }
    return props;
  }

  @Override
  public void applyStateProperties(Map<String, Object> stateProperties) {
    applyCanvasStateProperties(stateProperties);
    if (stateProperties != null) {
      Object focusId = stateProperties.get(STATE_FOCUS_NODE_ID);
      if (focusId != null) {
        focusContext.setFocusNodeId(focusId.toString());
      }
    }
    if (breadcrumbBar != null && !breadcrumbBar.isDisposed()) {
      breadcrumbBar.update(document, focusContext);
    }
    redraw();
  }

  @Override
  public IVariables getVariables() {
    return variables;
  }

  @Override
  public SnapAllignDistribute createSnapAlignDistribute() {
    return new SnapAllignDistribute(document, List.of(), new int[0], null, this);
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return List.of();
  }
}
