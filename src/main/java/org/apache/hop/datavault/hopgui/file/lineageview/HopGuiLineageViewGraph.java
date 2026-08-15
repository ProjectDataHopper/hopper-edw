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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.command.svg.SvgExportService;
import org.apache.hop.datavault.hopgui.file.modelgraph.HopGuiModelGraphBase;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphMouseInteractions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphSnapshotUndo;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.hopgui.help.MarkdownHelpBrowserSupport;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyledTextComp;
import org.apache.hop.datavault.layout.ElkLayoutBox;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.datavault.lineageview.LineageViewDetailsSupport;
import org.apache.hop.datavault.lineageview.LineageViewElkSupport;
import org.apache.hop.datavault.lineageview.LineageViewNavigationSupport;
import org.apache.hop.datavault.lineageview.LineageViewOpsBadge;
import org.apache.hop.datavault.lineageview.LineageViewOpsOverlay;
import org.apache.hop.datavault.lineageview.LineageViewSeedSupport;
import org.apache.hop.datavault.lineageview.backend.DatasetDetails;
import org.apache.hop.datavault.lineageview.backend.ILineageQueryService;
import org.apache.hop.datavault.lineageview.backend.JobDetails;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageGraphOps;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.apache.hop.datavault.lineageview.backend.LineageQuery;
import org.apache.hop.datavault.lineageview.backend.LineageQueryServiceFactory;
import org.apache.hop.datavault.lineageview.backend.LineageWarning;
import org.apache.hop.datavault.lineageview.backend.OpenLineageRef;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
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
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;

/** Explorer tab for a Hop Lineage View. */
@GuiPlugin(id = "HopGuiLineageViewGraph", description = "Hop lineage view graph")
@Getter
@Setter
public class HopGuiLineageViewGraph extends HopGuiModelGraphBase
    implements IHopFileTypeHandler, IGuiRefresher {

  private static final Class<?> PKG = HopGuiLineageViewGraph.class;

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiLineageViewGraph-Toolbar";
  public static final String TOOLBAR_ITEM_ZOOM_LEVEL =
      "HopGuiLineageViewGraph-ToolBar-10500-Zoom-Level";
  public static final String TOOLBAR_ITEM_ZOOM_IN = "HopGuiLineageViewGraph-ToolBar-10010-Zoom-In";
  public static final String TOOLBAR_ITEM_ZOOM_OUT =
      "HopGuiLineageViewGraph-ToolBar-10020-Zoom-Out";
  public static final String TOOLBAR_ITEM_ZOOM_100 =
      "HopGuiLineageViewGraph-ToolBar-10030-Zoom-100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT =
      "HopGuiLineageViewGraph-ToolBar-10040-Zoom-Fit";
  public static final String TOOLBAR_ITEM_REFRESH = "HopGuiLineageViewGraph-ToolBar-10050-Refresh";
  public static final String TOOLBAR_ITEM_SETTINGS =
      "HopGuiLineageViewGraph-ToolBar-10060-Settings";
  public static final String TOOLBAR_ITEM_EXPORT_SVG =
      "HopGuiLineageViewGraph-ToolBar-10070-Export-Svg";
  public static final String TOOLBAR_ITEM_UNDO = "HopGuiLineageViewGraph-ToolBar-Undo";
  public static final String TOOLBAR_ITEM_REDO = "HopGuiLineageViewGraph-ToolBar-Redo";
  public static final String ACTION_ID_OPEN_MODEL = "lineage-view-open-model";
  public static final String ACTION_ID_OPEN_CATALOG = "lineage-view-open-catalog";
  public static final String ACTION_ID_SHOW_UPDATE_PIPELINE = "lineage-view-show-update-pipeline";
  public static final String ACTION_ID_SHOW_BUILD_PIPELINE = "lineage-view-show-build-pipeline";

  private final HopLineageViewFileType fileType;
  private final ModelGraphSnapshotUndo<HopLineageViewDocument> snapshotUndo =
      new ModelGraphSnapshotUndo<>(
          HopLineageViewDocument.class,
          HopLineageViewFileType.XML_TAG,
          HopLineageViewDocument::new);
  private HopLineageViewDocument document;
  private Control toolBar;
  private GuiToolbarWidgets toolBarWidgets;
  private Label statusLabel;
  private MarkdownStyledTextComp detailsMarkdown;
  private String detailsMarkdownSource = "";
  private String filename;
  private final List<AreaOwner> areaOwners = new ArrayList<>();
  private LineageGraph sessionGraph;
  private Map<String, ElkLayoutBox> layoutBoxes = Map.of();
  private String selectedNodeId;
  private String mouseOverTableName;
  private String statusText = "";
  private String errorBanner;
  private boolean changed;
  private boolean facetsInline = true;
  private LineageViewOpsOverlay opsOverlay = LineageViewOpsOverlay.empty();
  private List<LineageSnapshot> extraSnapshots = List.of();
  private AtomicBoolean refreshCancelled = new AtomicBoolean(false);
  private AtomicBoolean followUpCancelled = new AtomicBoolean(false);
  private boolean refreshInProgress;
  private LineageViewClickPoint pendingContextClick;
  private LineageViewClickPoint followUpContextClick;
  private boolean wantContextAfterFollowUp;

  public HopGuiLineageViewGraph(
      Composite parent,
      HopGui hopGui,
      ExplorerPerspective perspective,
      HopLineageViewDocument document,
      HopLineageViewFileType fileType) {
    super(hopGui, parent, perspective);
    this.document = document;
    this.fileType = fileType;
    this.variables = new Variables();
    this.variables.copyFrom(hopGui.getVariables());
    if (document == null) {
      return;
    }
    setLayout(new FormLayout());
    addToolBar();
    statusLabel = new Label(this, SWT.NONE);
    PropsUi.setLook(statusLabel);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, PropsUi.getMargin());
    fdStatus.top = new FormAttachment(0, toolBar.getBounds().height);
    fdStatus.right = new FormAttachment(100, 0);
    statusLabel.setLayoutData(fdStatus);
    SashForm sash = new SashForm(this, SWT.HORIZONTAL);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(statusLabel, 0);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(100, 0);
    sash.setLayoutData(fdSash);
    canvas = new Canvas(sash, SWT.NO_BACKGROUND);
    Composite detailsPane = new Composite(sash, SWT.NONE);
    PropsUi.setLook(detailsPane);
    detailsPane.setLayout(new FormLayout());
    int detailsMargin = PropsUi.getMargin();
    Button viewHtml = new Button(detailsPane, SWT.PUSH);
    viewHtml.setText(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Details.ViewHtml"));
    PropsUi.setLook(viewHtml);
    FormData fdViewHtml = new FormData();
    fdViewHtml.left = new FormAttachment(0, detailsMargin);
    fdViewHtml.top = new FormAttachment(0, detailsMargin);
    viewHtml.setLayoutData(fdViewHtml);
    viewHtml.addListener(SWT.Selection, event -> openDetailsAsHtml());
    detailsMarkdown = new MarkdownStyledTextComp(detailsPane, SWT.NONE);
    FormData fdDetails = new FormData();
    fdDetails.left = new FormAttachment(0, 0);
    fdDetails.right = new FormAttachment(100, 0);
    fdDetails.top = new FormAttachment(viewHtml, detailsMargin);
    fdDetails.bottom = new FormAttachment(100, 0);
    detailsMarkdown.setLayoutData(fdDetails);
    setDetailsMarkdown(LineageViewDetailsSupport.emptySelection());
    sash.setWeights(78, 22);
    setupWebCanvas();
    canvas.addPaintListener(this::paintControl);
    registerCanvasMouseListeners();
    hopGui.replaceKeyboardShortcutListeners(this);
    canvas.setFocus();
    setStatusText(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Status.Idle"));
    setZoomLabel();
    layout(true, true);
    addDisposeListener(
        e -> {
          refreshCancelled.set(true);
          followUpCancelled.set(true);
        });
  }

  public static HopGuiLineageViewGraph getInstance() {
    IHopFileTypeHandler activeFileTypeHandler =
        HopGui.getExplorerPerspective().getActiveFileTypeHandler();
    if (activeFileTypeHandler instanceof HopGuiLineageViewGraph graph) {
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
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Error", "Unable to create lineage view toolbar", e);
    }
  }

  public void refreshGraph() {
    refreshCancelled.set(true);
    AtomicBoolean cancelled = new AtomicBoolean(false);
    refreshCancelled = cancelled;
    errorBanner = null;
    refreshInProgress = true;
    setRefreshEnabled(false);
    setStatusText(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Status.Loading"));
    redraw();
    Thread worker =
        new Thread(
            () -> {
              try {
                LineageBackendMeta backend = loadBackend();
                LineageViewSeedSupport.refreshOpenLineageIds(document, backend, variables);
                try (ILineageQueryService service =
                    LineageQueryServiceFactory.open(
                        backend, variables, hopGui.getMetadataProvider(), hopGui.getLog())) {
                  LineageQuery query = LineageViewSeedSupport.toQuery(document, extraSnapshots);
                  LineageGraph raw = service.fetchGraph(query);
                  LineageGraph applied = LineageGraphOps.apply(raw, query);
                  Map<String, ElkLayoutBox> boxes = LineageViewElkSupport.layout(applied);
                  LineageViewOpsOverlay overlay = LineageViewOpsOverlay.empty();
                  if (document.isIncludeOpsOverlay()) {
                    overlay =
                        LineageViewOpsOverlay.load(
                            applied, hopGui.getMetadataProvider(), variables);
                  }
                  String structure = formatStatus(backend, applied, overlay);
                  applyRefresh(
                      applied,
                      boxes,
                      structure,
                      service.facetsInlineOnGraph(),
                      overlay,
                      null,
                      cancelled);
                }
              } catch (Exception e) {
                applyRefresh(
                    null, Map.of(), null, true, LineageViewOpsOverlay.empty(), e, cancelled);
              }
            },
            "hlv-refresh");
    worker.setDaemon(true);
    worker.start();
  }

  static String formatStatus(LineageBackendMeta backend, LineageGraph graph) {
    return formatStatus(backend, graph, LineageViewOpsOverlay.empty());
  }

  static String formatStatus(
      LineageBackendMeta backend, LineageGraph graph, LineageViewOpsOverlay overlay) {
    String kind =
        backend != null && backend.getSettingsOrDefault() != null
            ? backend.getSettingsOrDefault().kind().name()
            : "";
    int nodes = graph != null ? graph.getNodesOrEmpty().size() : 0;
    int warnings = graph != null && graph.getWarnings() != null ? graph.getWarnings().size() : 0;
    StringBuilder text = new StringBuilder();
    if (!Utils.isEmpty(kind)) {
      text.append(kind).append(" · ");
    }
    text.append(nodes).append(" nodes");
    if (warnings > 0) {
      text.append(" · ").append(warnings).append(" warnings");
    }
    if (graph != null && graph.getWarnings() != null) {
      for (LineageWarning warning : graph.getWarnings()) {
        if (warning != null && LineageWarning.SEED_ISOLATED.equals(warning.getCode())) {
          text.append(" · seed isolated");
          break;
        }
      }
    }
    if (overlay != null && overlay.isSuppressed() && !Utils.isEmpty(overlay.getStatusNote())) {
      text.append(" · ").append(overlay.getStatusNote());
    } else if (overlay != null
        && !overlay.isSuppressed()
        && overlay.getSnapshots() != null
        && !overlay.getSnapshots().isEmpty()) {
      text.append(" · OPS overlay");
    }
    return text.toString();
  }

  private LineageBackendMeta loadBackend() throws HopException {
    if (document == null || Utils.isEmpty(document.getBackendName())) {
      throw new HopException(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Error.NoBackend"));
    }
    IHopMetadataSerializer<LineageBackendMeta> serializer =
        hopGui.getMetadataProvider().getSerializer(LineageBackendMeta.class);
    LineageBackendMeta backend = serializer.load(document.getBackendName());
    if (backend == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "HopGuiLineageViewGraph.Error.BackendMissing", document.getBackendName()));
    }
    return backend;
  }

  private void applyRefresh(
      LineageGraph graph,
      Map<String, ElkLayoutBox> boxes,
      String structure,
      boolean inlineFacets,
      LineageViewOpsOverlay overlay,
      Exception error,
      AtomicBoolean cancelled) {
    Display display = getDisplay();
    if (display == null || display.isDisposed()) {
      return;
    }
    display.asyncExec(
        () -> {
          if (cancelled.get() || isDisposed()) {
            refreshInProgress = false;
            return;
          }
          if (error != null) {
            errorBanner = error.getMessage();
            sessionGraph = null;
            opsOverlay = LineageViewOpsOverlay.empty();
            layoutBoxes = Map.of();
            setStatusText(
                BaseMessages.getString(
                    PKG, "HopGuiLineageViewGraph.Status.Error", Const.NVL(errorBanner, "")));
          } else {
            errorBanner = null;
            sessionGraph = graph;
            facetsInline = inlineFacets;
            opsOverlay = overlay != null ? overlay : LineageViewOpsOverlay.empty();
            layoutBoxes = boxes != null ? boxes : Map.of();
            maximum = computeMaximum();
            setStatusText(
                BaseMessages.getString(
                    PKG, "HopGuiLineageViewGraph.Status.Ready", Const.NVL(structure, "")));
          }
          updateDetails();
          refreshInProgress = false;
          setRefreshEnabled(true);
          redraw();
        });
  }

  public void selectNode(String nodeId) {
    selectNode(nodeId, false);
  }

  /** Name click: refresh the details pane only — no context dialog. */
  public void selectNodeName(String nodeId) {
    avoidContextDialog = true;
    pendingContextClick = null;
    wantContextAfterFollowUp = false;
    followUpContextClick = null;
    selectNode(nodeId, true);
  }

  /**
   * Card mouse-down: select the node and start facet follow-up. Context waits for an unmoved
   * mouse-up so Hop Web does not treat the click as a drag.
   */
  public void beginCardClick(String nodeId, LineageViewClickPoint click) {
    avoidContextDialog = false;
    pendingContextClick = click;
    wantContextAfterFollowUp = false;
    followUpContextClick = click;
    selectNode(nodeId, false);
  }

  /** Card mouse-up that did not move: open the context dialog, or wait for facet follow-up. */
  public boolean finishCardClick(Point real) {
    if (!isUnmovedClick(real)) {
      cancelPendingContext();
      return false;
    }
    if (avoidContextDialog) {
      avoidContextDialog = false;
      pendingContextClick = null;
      wantContextAfterFollowUp = false;
      return true;
    }
    LineageViewClickPoint click = pendingContextClick;
    pendingContextClick = null;
    if (click == null) {
      return false;
    }
    LineageNode node = findSessionNode(selectedNodeId);
    if (needsFollowUp(node)) {
      wantContextAfterFollowUp = true;
      followUpContextClick = click;
      return true;
    }
    wantContextAfterFollowUp = false;
    showNodeContextDialog(click, node);
    return true;
  }

  public void cancelPendingContext() {
    pendingContextClick = null;
    wantContextAfterFollowUp = false;
    followUpContextClick = null;
  }

  private void selectNode(String nodeId, boolean nameClick) {
    this.selectedNodeId = nodeId;
    updateDetails();
    redraw();
    if (!nameClick) {
      startFollowUp(nodeId);
    }
  }

  boolean needsFollowUp(LineageNode node) {
    if (facetsInline || node == null) {
      return false;
    }
    if (node.getKind() == LineageNodeKind.JOB) {
      return node.getHopExport() == null;
    }
    if (node.getKind() == LineageNodeKind.DATASET) {
      return node.getHopLocation() == null;
    }
    return false;
  }

  private void startFollowUp(String nodeId) {
    followUpCancelled.set(true);
    LineageNode node = findSessionNode(nodeId);
    if (!needsFollowUp(node)) {
      return;
    }
    AtomicBoolean cancelled = new AtomicBoolean(false);
    followUpCancelled = cancelled;
    Thread worker =
        new Thread(
            () -> {
              try {
                LineageBackendMeta backend = loadBackend();
                try (ILineageQueryService service =
                    LineageQueryServiceFactory.open(
                        backend, variables, hopGui.getMetadataProvider(), hopGui.getLog())) {
                  LineageNode updated = enrichNode(service, node);
                  applyFollowUp(nodeId, updated, cancelled);
                }
              } catch (Exception ignored) {
                applyFollowUp(nodeId, node, cancelled);
              }
            },
            "hlv-follow-up");
    worker.setDaemon(true);
    worker.start();
  }

  static LineageNode enrichNode(ILineageQueryService service, LineageNode node)
      throws HopException {
    if (service == null || node == null) {
      return node;
    }
    if (node.getKind() == LineageNodeKind.JOB) {
      JobDetails details =
          service
              .fetchJob(
                  OpenLineageRef.builder()
                      .namespace(node.getNamespace())
                      .name(node.getName())
                      .build())
              .orElse(null);
      if (details == null) {
        return node;
      }
      return node.toBuilder()
          .hopExport(details.getHopExport() != null ? details.getHopExport() : node.getHopExport())
          .hopOps(details.getHopOps() != null ? details.getHopOps() : node.getHopOps())
          .latestRunId(
              !Utils.isEmpty(details.getLatestRunId())
                  ? details.getLatestRunId()
                  : node.getLatestRunId())
          .lastExportedAt(
              !Utils.isEmpty(details.getLastExportedAt())
                  ? details.getLastExportedAt()
                  : node.getLastExportedAt())
          .build();
    }
    if (node.getKind() == LineageNodeKind.DATASET) {
      DatasetDetails details =
          service
              .fetchDataset(
                  OpenLineageRef.builder()
                      .namespace(node.getNamespace())
                      .name(node.getName())
                      .build())
              .orElse(null);
      if (details == null) {
        return node;
      }
      return node.toBuilder()
          .hopLocation(
              details.getHopLocation() != null ? details.getHopLocation() : node.getHopLocation())
          .schemaFieldNames(
              details.getSchemaFieldNames() != null && !details.getSchemaFieldNames().isEmpty()
                  ? details.getSchemaFieldNames()
                  : node.getSchemaFieldNames())
          .build();
    }
    return node;
  }

  private void applyFollowUp(String nodeId, LineageNode updated, AtomicBoolean cancelled) {
    Display display = getDisplay();
    if (display == null || display.isDisposed()) {
      return;
    }
    display.asyncExec(
        () -> {
          if (cancelled.get() || isDisposed() || !nodeId.equals(selectedNodeId)) {
            return;
          }
          replaceSessionNode(updated);
          updateDetails();
          redraw();
          if (wantContextAfterFollowUp && followUpContextClick != null && updated != null) {
            wantContextAfterFollowUp = false;
            LineageViewClickPoint click = followUpContextClick;
            followUpContextClick = null;
            showNodeContextDialog(click, updated);
          }
        });
  }

  private void replaceSessionNode(LineageNode updated) {
    if (sessionGraph == null || updated == null || updated.getId() == null) {
      return;
    }
    List<LineageNode> nodes = new ArrayList<>();
    for (LineageNode node : sessionGraph.getNodesOrEmpty()) {
      nodes.add(updated.getId().equals(node.getId()) ? updated : node);
    }
    sessionGraph = sessionGraph.toBuilder().nodes(List.copyOf(nodes)).build();
  }

  void showNodeContextDialog(LineageViewClickPoint click, LineageNode node) {
    if (node == null || click == null || canvas == null || canvas.isDisposed()) {
      return;
    }
    try {
      Point real = screen2real(click.x(), click.y());
      org.eclipse.swt.graphics.Point screenPoint =
          getShell().getDisplay().map(canvas, null, click.x(), click.y());
      String message =
          BaseMessages.getString(
              PKG,
              "HopGuiLineageViewGraph.Context.Node.Message",
              Const.NVL(node.getName(), node.getId()));
      IGuiContextHandler contextHandler =
          new HopGuiLineageViewNodeContext(document, this, node, real);
      avoidContextDialog =
          GuiContextUtil.getInstance()
              .handleActionSelection(
                  getShell(), message, new Point(screenPoint.x, screenPoint.y), contextHandler);
    } catch (Exception ex) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Context.Error.Message"),
          ex);
    }
  }

  private void updateDetails() {
    LineageNode node = findSessionNode(selectedNodeId);
    setDetailsMarkdown(LineageViewDetailsSupport.format(node, sessionGraph, opsBadge(node)));
  }

  private void setDetailsMarkdown(String markdown) {
    detailsMarkdownSource = markdown != null ? markdown : "";
    if (detailsMarkdown == null || detailsMarkdown.isDisposed()) {
      return;
    }
    detailsMarkdown.setMarkdown(detailsMarkdownSource);
    detailsMarkdown.scrollToTop();
  }

  private void openDetailsAsHtml() {
    MarkdownHelpBrowserSupport.openInBrowser(
        getShell(),
        BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Details.HtmlTitle"),
        detailsMarkdownSource,
        "lineage-view-node");
  }

  public LineageViewOpsBadge opsBadge(LineageNode node) {
    return opsOverlay != null ? opsOverlay.badgeFor(node) : null;
  }

  void updateNodeHoverTooltip(LineageNode node) {
    if (canvas == null || canvas.isDisposed()) {
      return;
    }
    LineageViewOpsBadge badge = opsBadge(node);
    canvas.setToolTipText(badge != null ? badge.tooltip() : null);
  }

  private Point computeMaximum() {
    return LineageViewSvgPainter.maximumOf(layoutBoxes);
  }

  private void setStatusText(String text) {
    this.statusText = Const.NVL(text, "");
    if (statusLabel != null && !statusLabel.isDisposed()) {
      statusLabel.setText(statusText);
    }
  }

  private void setRefreshEnabled(boolean enabled) {
    if (toolBarWidgets != null) {
      toolBarWidgets.enableToolbarItem(TOOLBAR_ITEM_REFRESH, enabled);
    }
  }

  private void paintControl(PaintEvent e) {
    Point area = getArea();
    if (area.x == 0 || area.y == 0) {
      return;
    }
    if (EnvironmentUtils.getInstance().isWeb()) {
      drawLineageViewWeb(area.x, area.y);
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
    drawGraph(swtGc, area.x, area.y);
    if (needsDoubleBuffering) {
      e.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private void drawLineageViewWeb(int width, int height) {
    try {
      LineageViewCanvasSvgRenderer.Context ctx = new LineageViewCanvasSvgRenderer.Context();
      PropsUi propsUi = PropsUi.getInstance();
      ctx.variables = variables;
      ctx.graph = sessionGraph;
      ctx.boxes = layoutBoxes;
      ctx.selectedNodeId = selectedNodeId;
      ctx.mouseOverTableName = mouseOverTableName;
      ctx.canvasSize = new Point(width, height);
      ctx.offset = offset;
      ctx.iconSize = propsUi.getIconSize();
      ctx.gridSize = propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1;
      ctx.magnification = (float) (magnification * PropsUi.getNativeZoomFactor());
      ctx.screenMagnification = (float) propsUi.getZoomFactor();
      ctx.zoomFactor = propsUi.getZoomFactor();
      ctx.maximum = maximum;
      ctx.showingNavigationView = !propsUi.isHideViewportEnabled();
      ctx.opsOverlay = opsOverlay;
      ctx.bannerText = canvasBannerText();
      ctx.bannerError = LineageViewCanvasBanner.error(errorBanner);
      ModelGraphCanvasSvgResult result = LineageViewCanvasSvgRenderer.render(ctx);
      applyWebCanvasRender(result, width, height, document);
    } catch (Exception ex) {
      logWebCanvasRenderError("Failed to render lineage view SVG for Hop Web", ex);
    }
  }

  private void drawGraph(GC swtGc, int width, int height) {
    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    try {
      areaOwners.clear();
      float paintMagnification = (float) (magnification * PropsUi.getNativeZoomFactor());
      LineageViewPainter painter =
          new LineageViewPainter(
              sessionGraph, layoutBoxes, selectedNodeId, gc, variables, width, height, opsOverlay);
      painter.setGridSize(propsUi.isShowCanvasGridEnabled() ? propsUi.getCanvasGridSize() : 1);
      painter.setZoomFactor((float) propsUi.getZoomFactor());
      painter.setMagnification(paintMagnification);
      painter.setOffset(offset);
      painter.setIconSize(propsUi.getIconSize());
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setShowingNavigationView(!propsUi.isHideViewportEnabled());
      painter.setMouseOverTableName(mouseOverTableName);
      painter.setBanner(canvasBannerText(), LineageViewCanvasBanner.error(errorBanner));
      painter.draw();
      captureNavigationViewGeometry(painter);
      CanvasFacade.setData(canvas, magnification, offset, document);
    } finally {
      gc.dispose();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_UNDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Undo.Tooltip",
      image = "ui/images/undo.svg")
  @GuiKeyboardShortcut(control = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, key = 'z')
  @Override
  public void undo() {
    try {
      applySnapshotChange(snapshotUndo.undo(document, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(undoApplyErrorTitle(), undoApplyErrorMessage(), e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REDO,
      toolTip = "i18n:org.apache.hop.ui.hopgui:HopGui.Toolbar.Redo.Tooltip",
      image = "ui/images/redo.svg")
  @GuiKeyboardShortcut(control = true, shift = true, key = 'z')
  @GuiOsxKeyboardShortcut(command = true, shift = true, key = 'z')
  @Override
  public void redo() {
    try {
      applySnapshotChange(snapshotUndo.redo(document, hopGui.getMetadataProvider(), getFilename()));
    } catch (HopException e) {
      showUndoError(undoApplyErrorTitle(), undoApplyErrorMessage(), e);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::HopGuiLineageViewGraph.Toolbar.Refresh",
      image = "ui/images/refresh.svg",
      separator = true)
  public void toolbarRefresh() {
    refreshGraph();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_SETTINGS,
      toolTip = "i18n::HopGuiLineageViewGraph.Toolbar.Settings",
      image = "ui/images/settings.svg")
  public void toolbarSettings() {
    byte[] beforeChange = captureUndoSnapshot();
    LineageViewSettingsDialog dialog =
        new LineageViewSettingsDialog(hopGui.getShell(), hopGui, document, false);
    if (dialog.open()) {
      commitDialogUndo(beforeChange);
      setChanged();
      enableUndoToolbarItems();
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
      refreshGraph();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPORT_SVG,
      toolTip = "i18n::HopGuiLineageViewGraph.Toolbar.ExportSvg",
      image = "ui/images/image.svg")
  public void exportToSvg() {
    if (sessionGraph == null) {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Empty"));
      box.open();
      return;
    }
    try {
      String svgXml =
          LineageViewSvgPainter.generateSvg(sessionGraph, layoutBoxes, variables, opsOverlay);
      String proposedName = Const.NVL(getName(), "lineage") + ".svg";
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
        box.setText(BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Overwrite.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Overwrite.Message"));
        if (box.open() != SWT.YES) {
          return;
        }
      }
      SvgExportService.writeSvg(realFilename, svgXml);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Export.Error.Message"),
          e);
    }
  }

  @GuiContextActionFilter(parentId = HopGuiLineageViewNodeContext.CONTEXT_ID)
  public boolean filterNodeContextActions(
      String contextActionId, HopGuiLineageViewNodeContext context) {
    LineageNode node = context != null ? context.getNode() : null;
    if (ACTION_ID_OPEN_MODEL.equals(contextActionId)) {
      return LineageViewNavigationSupport.canOpenModel(node, variables);
    }
    if (ACTION_ID_OPEN_CATALOG.equals(contextActionId)) {
      return LineageViewNavigationSupport.canOpenCatalog(node);
    }
    if (ACTION_ID_SHOW_UPDATE_PIPELINE.equals(contextActionId)) {
      return LineageViewNavigationSupport.canOpenUpdatePipeline(node, variables);
    }
    if (ACTION_ID_SHOW_BUILD_PIPELINE.equals(contextActionId)) {
      return LineageViewNavigationSupport.canOpenBuildPipeline(node, variables);
    }
    return true;
  }

  @GuiContextAction(
      id = ACTION_ID_OPEN_MODEL,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiLineageViewGraph.Context.OpenModel.Name",
      tooltip = "i18n::HopGuiLineageViewGraph.Context.OpenModel.Tooltip",
      image = "ui/images/open.svg",
      category = "Lineage",
      categoryOrder = "1")
  public void openModelFromContext(HopGuiLineageViewNodeContext context) {
    runNavigation(
        () -> LineageViewNavigationSupport.openModel(hopGui, variables, context.getNode()));
  }

  @GuiContextAction(
      id = ACTION_ID_OPEN_CATALOG,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiLineageViewGraph.Context.OpenCatalog.Name",
      tooltip = "i18n::HopGuiLineageViewGraph.Context.OpenCatalog.Tooltip",
      image = "data-catalog.svg",
      category = "Lineage",
      categoryOrder = "2")
  public void openCatalogFromContext(HopGuiLineageViewNodeContext context) {
    runNavigation(() -> LineageViewNavigationSupport.openCatalog(hopGui, context.getNode()));
  }

  @GuiContextAction(
      id = ACTION_ID_SHOW_UPDATE_PIPELINE,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiLineageViewGraph.Context.ShowUpdatePipeline.Name",
      tooltip = "i18n::HopGuiLineageViewGraph.Context.ShowUpdatePipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Lineage",
      categoryOrder = "3")
  public void showUpdatePipelineFromContext(HopGuiLineageViewNodeContext context) {
    runNavigation(
        () ->
            LineageViewNavigationSupport.openUpdatePipeline(hopGui, variables, context.getNode()));
  }

  @GuiContextAction(
      id = ACTION_ID_SHOW_BUILD_PIPELINE,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiLineageViewGraph.Context.ShowBuildPipeline.Name",
      tooltip = "i18n::HopGuiLineageViewGraph.Context.ShowBuildPipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Lineage",
      categoryOrder = "4")
  public void showBuildPipelineFromContext(HopGuiLineageViewNodeContext context) {
    runNavigation(
        () -> LineageViewNavigationSupport.openBuildPipeline(hopGui, variables, context.getNode()));
  }

  private void runNavigation(NavigationAction action) {
    try {
      action.run();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Context.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Context.Error.Navigate"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  @FunctionalInterface
  private interface NavigationAction {
    void run() throws Exception;
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
      toolTip = "Zoom to fit",
      image = "ui/images/zoom-fit.svg")
  @Override
  public void zoomFitToScreen() {
    performZoomFitToScreen();
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
    for (Map.Entry<String, ElkLayoutBox> entry : layoutBoxes.entrySet()) {
      ElkLayoutBox box = entry.getValue();
      nodes.put(
          entry.getKey(),
          ModelGraphWebCanvasData.NodePos.of(
              box.x(), box.y(), entry.getKey().equals(selectedNodeId), box.width(), box.height()));
    }
    return nodes;
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

  @Override
  protected ModelGraphMouseInteractions createMouseInteractions() {
    return new LineageViewReadOnlyInteractions(this);
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
    if (restored instanceof HopLineageViewDocument restoredDocument) {
      this.document = restoredDocument;
      if (perspective != null) {
        perspective.updateTabItem(this);
      }
      refreshGraph();
    }
  }

  @Override
  protected void clearSelectionRegion() {
    selectionRegion = null;
  }

  @Override
  protected String undoRecordErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Undo.Title");
  }

  @Override
  protected String undoRecordErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Undo.Record.Message");
  }

  @Override
  protected String undoApplyErrorTitle() {
    return BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Undo.Title");
  }

  @Override
  protected String undoApplyErrorMessage() {
    return BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Undo.Apply.Message");
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
    return List.of();
  }

  @Override
  public AreaOwner getVisibleAreaOwner(int x, int y) {
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
    return "Lineage view";
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
  protected boolean handleLassoMouseDown(
      Event e, Point real, boolean control, boolean onBackground) {
    return false;
  }

  @Override
  public boolean hasChanged() {
    return changed;
  }

  @Override
  public void setChanged() {
    this.changed = true;
    updateGui();
  }

  public void clearChanged() {
    this.changed = false;
    updateGui();
  }

  @Override
  public void updateGui() {
    hopGui.handleFileCapabilities(fileType, this, hasChanged(), false, false);
    setZoomLabel();
    enableUndoToolbarItems();
    redraw();
  }

  @Override
  public Object getSubject() {
    return document;
  }

  @Override
  public String getName() {
    return document != null ? document.getName() : "Lineage view";
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
    try {
      if (hasChanged()) {
        MessageBox messageDialog =
            new MessageBox(hopGui.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        messageDialog.setText(
            BaseMessages.getString(PKG, "HopGuiLineageViewGraph.SaveFile.Dialog.Header"));
        messageDialog.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiLineageViewGraph.SaveFile.Dialog.Message", getName()));
        int answer = messageDialog.open();
        if ((answer & SWT.YES) != 0) {
          save();
          return !hasChanged();
        }
        return (answer & SWT.NO) != 0;
      }
      return true;
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Error", "Unable to close lineage view", e);
      return false;
    }
  }

  @Override
  public void close() {
    refreshCancelled.set(true);
    followUpCancelled.set(true);
    perspective.remove(this);
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
    return new SnapAllignDistribute(document, List.of(), new int[0], null, this);
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return List.of();
  }

  public LineageNode findSessionNode(String nodeId) {
    return sessionGraph != null ? sessionGraph.findNode(nodeId) : null;
  }

  String canvasBannerText() {
    return LineageViewCanvasBanner.text(
        errorBanner,
        sessionGraph,
        refreshInProgress,
        BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Status.Loading"),
        BaseMessages.getString(PKG, "HopGuiLineageViewGraph.Empty"));
  }
}
