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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.bus.HopGuiEvents;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.perspective.HopPerspectivePlugin;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.shared.SashFormMemory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.hopper.edw.datavault.hopgui.EdwDocsGuiPlugin;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.hopgui.ai.EdwAiAdvisorOpenSupport;
import org.hopper.edw.datavault.hopgui.opsanalytics.OpsAnalyticsDialog;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyGuiNavigator;
import org.hopper.edw.datavault.hopgui.perspective.journey.link.EdwJourneyPresentationHost;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyConsumptionProbe;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyHistory;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyHistoryQuery;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelBuilder;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelContent;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelContent.Inputs;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelContent.JdbcService;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelId;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPoster;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterStatus;
import org.hopper.edw.datavault.hopgui.presentation.EdwPresentationViewerPane;
import org.hopper.presentation.simple.HGeneratedCatalog;

/** Hop GUI perspective for the EDW data journey of a resource definition group. */
@HopPerspectivePlugin(
    id = "360-EdwJourneyPerspective",
    name = "i18n::EdwJourneyPerspective.Name",
    description = "i18n::EdwJourneyPerspective.Description",
    image = "edw-logo.svg")
@GuiPlugin(
    name = "i18n::EdwJourneyPerspective.Name",
    description = "i18n::EdwJourneyPerspective.GuiPlugin.Description")
public class EdwJourneyPerspective implements IHopPerspective {

  public static final Class<?> PKG = EdwJourneyPerspective.class;
  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "EdwJourneyPerspective-Toolbar";
  public static final String TOOLBAR_ITEM_REFRESH = "EdwJourneyPerspective-Toolbar-10000-Refresh";
  public static final String TOOLBAR_ITEM_EXPAND_ALL =
      "EdwJourneyPerspective-Toolbar-10001-ExpandAll";
  public static final String TOOLBAR_ITEM_COLLAPSE_ALL =
      "EdwJourneyPerspective-Toolbar-10003-CollapseAll";
  public static final String TOOLBAR_ITEM_AI_HELP = "EdwJourneyPerspective-Toolbar-10010-AI-Help";
  public static final String TOOLBAR_ITEM_OPS_ANALYTICS =
      "EdwJourneyPerspective-Toolbar-10020-OpsAnalytics";

  @Getter private static EdwJourneyPerspective instance;

  private HopGui hopGui;
  private Composite composite;
  private SashForm sash;
  private Composite treeComposite;
  private Tree tree;
  private Label wlGroup;
  private Label wlTree;
  private MetaSelectionLine<ResourceDefinitionGroupMeta> wGroup;
  private GuiToolbarWidgets toolBarWidgets;
  private EdwJourneyDetailsPanel detailsPanel;
  private EdwJourneyPoster poster;
  private Composite detailsHost;
  private Composite viewerHost;
  private Composite bottomStackComposite;
  private StackLayout bottomStack;
  private EdwPresentationViewerPane presentationPane;
  private EdwJourneyPresentationHost presentationHost;
  private EdwJourneySnapshot snapshot = EdwJourneySnapshot.empty();
  private EdwJourneyOpsOverlay opsOverlay = EdwJourneyOpsOverlay.empty();
  private EdwJourneyHistory history = EdwJourneyHistory.empty(null);
  private boolean semanticLayerPresent;
  private List<String> semanticFiles = List.of();
  private List<JdbcService> jdbcServices = List.of();
  private EdwJourneyPanelId activePanel = EdwJourneyPanelId.PROJECT_PULSE;
  private boolean preferPanel;
  private ResourceDefinitionGroupMeta selectedGroup;
  private boolean suppressingGroupEvents;
  private boolean applyingTreeSelection;

  public EdwJourneyPerspective() {
    instance = this;
  }

  public EdwJourneySnapshot getSnapshot() {
    return snapshot != null ? snapshot : EdwJourneySnapshot.empty();
  }

  public EdwJourneyOpsOverlay getOpsOverlay() {
    return opsOverlay != null ? opsOverlay : EdwJourneyOpsOverlay.empty();
  }

  @Override
  public String getId() {
    return "edw-journey-perspective";
  }

  @Override
  public void activate() {
    hopGui.setActivePerspective(this);
  }

  @Override
  public void perspectiveActivated() {
    refresh();
  }

  @Override
  public boolean isActive() {
    return hopGui.isActivePerspective(this);
  }

  @Override
  public void initialize(HopGui hopGui, Composite parent) {
    this.hopGui = hopGui;

    composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);
    composite.setLayoutData(new FormDataBuilder().fullSize().result());

    sash = new SashForm(composite, SWT.HORIZONTAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(0, 0);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(100, 0);
    sash.setLayoutData(fdSash);

    createTreePane(sash);
    createDetailsPane(sash);
    sash.setWeights(new int[] {30, 70});
    SashFormMemory.persist(sash, "edw-journey-perspective-tree-width", 30, 70);

    hopGui
        .getEventsHandler()
        .addEventListener(
            getClass().getName() + "ProjectActivated",
            e ->
                hopGui
                    .getDisplay()
                    .asyncExec(
                        () -> {
                          ResourceDefinitionGroupModelDiscoverySupport.invalidateCache();
                          if (tree != null && !tree.isDisposed()) {
                            recreateGroupLine();
                            refresh();
                          }
                        }),
            HopGuiEvents.ProjectActivated.name());
  }

  private void createTreePane(Composite parent) {
    treeComposite = new Composite(parent, SWT.BORDER);
    treeComposite.setLayout(new FormLayout());
    PropsUi.setLook(treeComposite);

    wlGroup = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlGroup);
    wlGroup.setText(BaseMessages.getString(PKG, "EdwJourneyPerspective.Group.Label"));
    FormData fdlGroup = new FormData();
    fdlGroup.left = new FormAttachment(0, PropsUi.getMargin());
    fdlGroup.top = new FormAttachment(0, PropsUi.getMargin());
    fdlGroup.right = new FormAttachment(100, -PropsUi.getMargin());
    wlGroup.setLayoutData(fdlGroup);

    recreateGroupLine();

    wlTree = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlTree);
    wlTree.setText(BaseMessages.getString(PKG, "EdwJourneyPerspective.Tree.Label"));
    FormData fdlTree = new FormData();
    fdlTree.left = new FormAttachment(0, PropsUi.getMargin());
    fdlTree.top = new FormAttachment(wGroup, PropsUi.getMargin());
    fdlTree.right = new FormAttachment(100, -PropsUi.getMargin());
    wlTree.setLayoutData(fdlTree);

    IToolbarContainer toolBarContainer =
        ToolbarFacade.createToolbarContainer(treeComposite, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
    Control toolBar = toolBarContainer.getControl();
    toolBarWidgets = new GuiToolbarWidgets();
    toolBarWidgets.registerGuiPluginObject(this);
    toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment(0, 0);
    fdToolBar.top = new FormAttachment(wlTree, PropsUi.getMargin());
    fdToolBar.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdToolBar);
    toolBar.pack();
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);

    tree = new Tree(treeComposite, SWT.SINGLE | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(tree);
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(toolBar, 0);
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    tree.setLayoutData(fdTree);
    tree.addListener(
        SWT.Selection,
        e -> {
          if (!applyingTreeSelection) {
            preferPanel = false;
          }
          updateSelection();
        });
    tree.addListener(
        SWT.DefaultSelection,
        e -> {
          TreeItem[] selection = tree.getSelection();
          if (selection.length == 0) {
            return;
          }
          Object data = selection[0].getData();
          if (data instanceof EdwJourneyTreeNode node) {
            try {
              EdwJourneyNavigationSupport.openPrimary(hopGui, selectedGroup, node);
            } catch (HopException ex) {
              new ErrorDialog(
                  hopGui.getShell(),
                  BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Title"),
                  BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Message"),
                  ex);
            }
          }
        });
  }

  private void createDetailsPane(Composite parent) {
    SashForm right = new SashForm(parent, SWT.VERTICAL);
    PropsUi.setLook(right);
    poster = new EdwJourneyPoster(right, hopGui.getVariables(), this::onBlockSelected);

    Composite bottom = new Composite(right, SWT.BORDER);
    bottom.setLayout(new FormLayout());
    PropsUi.setLook(bottom);

    bottomStackComposite = new Composite(bottom, SWT.NONE);
    bottomStack = new StackLayout();
    bottomStackComposite.setLayout(bottomStack);
    PropsUi.setLook(bottomStackComposite);
    FormData fdStack = new FormData();
    fdStack.left = new FormAttachment(0, 0);
    fdStack.top = new FormAttachment(0, 0);
    fdStack.right = new FormAttachment(100, 0);
    fdStack.bottom = new FormAttachment(100, 0);
    bottomStackComposite.setLayoutData(fdStack);

    detailsHost = new Composite(bottomStackComposite, SWT.NONE);
    detailsHost.setLayout(new FormLayout());
    PropsUi.setLook(detailsHost);
    viewerHost = new Composite(bottomStackComposite, SWT.NONE);
    viewerHost.setLayout(new FormLayout());
    org.eclipse.swt.graphics.Color pageColor =
        new org.eclipse.swt.graphics.Color(viewerHost.getDisplay(), 0xFA, 0xFB, 0xFC);
    viewerHost.setBackground(pageColor);
    viewerHost.addDisposeListener(e -> pageColor.dispose());
    presentationHost =
        new EdwJourneyPresentationHost(
            () -> snapshot,
            () ->
                new EdwJourneyGuiNavigator(
                    hopGui, selectedGroup, snapshot, this::showPanelFromLink));
    presentationPane = new EdwPresentationViewerPane(viewerHost, presentationHost, false);
    FormData fdPane = new FormData();
    fdPane.left = new FormAttachment(12, 0);
    fdPane.right = new FormAttachment(88, 0);
    fdPane.top = new FormAttachment(0, 0);
    fdPane.bottom = new FormAttachment(100, 0);
    presentationPane.setLayoutData(fdPane);
    bottomStack.topControl = detailsHost;
    detailsPanel = new EdwJourneyDetailsPanel(detailsHost, hopGui, this::onGroupCreated);

    right.setWeights(new int[] {45, 55});
  }

  /**
   * Rebuilds the metadata picker against the current project's provider. SWT Combo READ_ONLY on GTK
   * often will not drop down; {@link MetaSelectionLine} uses CCombo plus New/Edit.
   */
  private void recreateGroupLine() {
    if (treeComposite == null || treeComposite.isDisposed()) {
      return;
    }
    String previous = currentGroupName();
    if (wGroup != null && !wGroup.isDisposed()) {
      wGroup.dispose();
    }
    wGroup =
        new MetaSelectionLine<>(
            hopGui.getVariables(),
            hopGui.getMetadataProvider(),
            ResourceDefinitionGroupMeta.class,
            treeComposite,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            null,
            BaseMessages.getString(PKG, "EdwJourneyPerspective.Group.Tooltip"),
            true);
    FormData fdGroup = new FormData();
    fdGroup.left = new FormAttachment(0, PropsUi.getMargin());
    if (wlGroup != null && !wlGroup.isDisposed()) {
      fdGroup.top = new FormAttachment(wlGroup, PropsUi.getMargin());
    } else {
      fdGroup.top = new FormAttachment(0, PropsUi.getMargin());
    }
    fdGroup.right = new FormAttachment(100, -PropsUi.getMargin());
    wGroup.setLayoutData(fdGroup);
    wGroup.addModifyListener(
        e -> {
          if (suppressingGroupEvents) {
            return;
          }
          String name = currentGroupName();
          String loaded = selectedGroup != null ? Const.NVL(selectedGroup.getName(), "") : "";
          if (name.equals(loaded)) {
            return;
          }
          if (!Utils.isEmpty(name)) {
            EdwJourneyAuditSupport.storeLastGroupName(name);
          }
          reloadJourney();
        });
    if (wlTree != null && !wlTree.isDisposed()) {
      FormData fdlTree = (FormData) wlTree.getLayoutData();
      fdlTree.top = new FormAttachment(wGroup, PropsUi.getMargin());
    }
    if (!Utils.isEmpty(previous)) {
      suppressingGroupEvents = true;
      try {
        wGroup.setText(previous);
      } finally {
        suppressingGroupEvents = false;
      }
    }
    treeComposite.layout(true, true);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::EdwJourneyPerspective.Toolbar.Refresh.Tooltip",
      image = "ui/images/refresh.svg")
  @GuiKeyboardShortcut(key = SWT.F5)
  @GuiOsxKeyboardShortcut(key = SWT.F5)
  public void toolbarRefresh() {
    refresh();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPAND_ALL,
      toolTip = "i18n::EdwJourneyPerspective.Toolbar.ExpandAll.Tooltip",
      image = "ui/images/expand-all.svg")
  public void expandAll() {
    setExpanded(tree.getItems(), true);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COLLAPSE_ALL,
      toolTip = "i18n::EdwJourneyPerspective.Toolbar.CollapseAll.Tooltip",
      image = "ui/images/collapse-all.svg")
  public void collapseAll() {
    setExpanded(tree.getItems(), false);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_AI_HELP,
      toolTip = "i18n::EdwJourneyPerspective.Toolbar.AiHelp.Tooltip",
      image = "ai-provider.svg")
  public void openAiAdvisor() {
    String focus = null;
    if (tree != null && !tree.isDisposed() && tree.getSelectionCount() > 0) {
      Object data = tree.getSelection()[0].getData();
      if (data instanceof EdwJourneyTreeNode node) {
        focus = !Utils.isEmpty(node.id()) ? node.id() : node.label();
      }
    }
    EdwAiAdvisorOpenSupport.openEdwJourney(hopGui, getSnapshot(), getOpsOverlay(), focus);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_OPS_ANALYTICS,
      toolTip = "i18n::EdwJourneyPerspective.Toolbar.OpsAnalytics.Tooltip",
      image = "datavault-configuration.svg")
  public void createOperationalAnalytics() {
    OpsAnalyticsDialog.open(hopGui);
  }

  /**
   * Activates this perspective, selects a resource definition group, and optionally a tree node id.
   */
  public void select(String groupName, String nodeId) {
    activate();
    if (!Utils.isEmpty(groupName)) {
      EdwJourneyAuditSupport.storeLastGroupName(groupName);
    }
    refresh();
    if (!Utils.isEmpty(nodeId)) {
      TreeItem item = findItem(tree.getItems(), nodeId);
      if (item != null) {
        tree.setSelection(item);
        updateSelection();
      }
    }
  }

  public void refresh() {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    try {
      populateGroupLine();
      reloadJourney();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Refresh.Title"),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Refresh.Message"),
          e instanceof HopException hop ? hop : new HopException(e));
    }
  }

  private void populateGroupLine() throws HopException {
    if (wGroup == null || wGroup.isDisposed()) {
      recreateGroupLine();
    }
    if (wGroup == null || wGroup.isDisposed() || hopGui.getMetadataProvider() == null) {
      return;
    }
    String current = currentGroupName();
    String last = EdwJourneyAuditSupport.retrieveLastGroupName();
    suppressingGroupEvents = true;
    try {
      wGroup.fillItems();
      List<String> names = new ArrayList<>();
      String[] items = wGroup.getItems();
      if (items != null) {
        names.addAll(List.of(items));
      }
      String selected = EdwJourneyGroupSelection.resolve(names, current, last);
      wGroup.setText(Const.NVL(selected, ""));
      if (!Utils.isEmpty(selected)) {
        EdwJourneyAuditSupport.storeLastGroupName(selected);
      }
    } finally {
      suppressingGroupEvents = false;
    }
  }

  private void reloadJourney() {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    String previousId = selectedNodeId();
    String groupName = currentGroupName();
    ResourceDefinitionGroupMeta[] groupHolder = new ResourceDefinitionGroupMeta[1];
    EdwJourneySnapshot[] snapshotHolder = new EdwJourneySnapshot[1];
    EdwJourneyOpsOverlay[] overlayHolder = new EdwJourneyOpsOverlay[1];
    EdwJourneyHistory[] historyHolder = new EdwJourneyHistory[1];
    List<String>[] semanticHolder = new List[1];
    List<JdbcService>[] jdbcHolder = new List[1];
    GuiBusySupport.showWhile(
        composite,
        () -> {
          groupHolder[0] = loadGroup(groupName);
          snapshotHolder[0] =
              EdwJourneySnapshotBuilder.build(
                  groupHolder[0], hopGui.getVariables(), hopGui.getMetadataProvider());
          overlayHolder[0] =
              EdwJourneyOpsOverlayLoader.load(
                  groupHolder[0], hopGui.getVariables(), hopGui.getMetadataProvider());
          historyHolder[0] =
              EdwJourneyHistoryQuery.load(
                  groupHolder[0], hopGui.getVariables(), hopGui.getMetadataProvider());
          EdwJourneySnapshot built =
              snapshotHolder[0] != null ? snapshotHolder[0] : EdwJourneySnapshot.empty();
          semanticHolder[0] =
              EdwJourneyConsumptionProbe.semanticFiles(
                  built.dimensionalModels(), hopGui.getVariables());
          jdbcHolder[0] =
              EdwJourneyConsumptionProbe.jdbcServices(
                  hopGui.getMetadataProvider(), built.sourceModels(), hopGui.getVariables());
        });
    selectedGroup = groupHolder[0];
    snapshot = snapshotHolder[0] != null ? snapshotHolder[0] : EdwJourneySnapshot.empty();
    opsOverlay = overlayHolder[0] != null ? overlayHolder[0] : EdwJourneyOpsOverlay.empty();
    history = historyHolder[0] != null ? historyHolder[0] : EdwJourneyHistory.empty(null);
    semanticFiles = semanticHolder[0] != null ? semanticHolder[0] : List.of();
    semanticLayerPresent = !semanticFiles.isEmpty();
    jdbcServices = jdbcHolder[0] != null ? jdbcHolder[0] : List.of();
    rebuildTree(previousId);
  }

  private String currentGroupName() {
    if (wGroup == null || wGroup.isDisposed()) {
      return "";
    }
    return Const.NVL(wGroup.getText(), "").trim();
  }

  private List<String> listGroupNames() throws HopException {
    IHopMetadataProvider provider = hopGui.getMetadataProvider();
    if (provider == null) {
      return List.of();
    }
    IHopMetadataSerializer<ResourceDefinitionGroupMeta> serializer =
        provider.getSerializer(ResourceDefinitionGroupMeta.class);
    List<String> names = new ArrayList<>(serializer.listObjectNames());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  private ResourceDefinitionGroupMeta loadGroup(String groupName) {
    if (Utils.isEmpty(groupName) || hopGui.getMetadataProvider() == null) {
      return null;
    }
    try {
      return hopGui
          .getMetadataProvider()
          .getSerializer(ResourceDefinitionGroupMeta.class)
          .load(groupName);
    } catch (HopException e) {
      return null;
    }
  }

  private void rebuildTree(String selectId) {
    tree.setRedraw(false);
    try {
      tree.removeAll();
      EdwJourneyTreeNode root = EdwJourneyTreeBuilder.build(snapshot, opsOverlay);
      populateItem(null, root);
      applyDefaultExpansion(tree.getItems());
    } finally {
      tree.setRedraw(true);
    }
    TreeItem toSelect =
        !Utils.isEmpty(selectId) ? findItem(tree.getItems(), selectId) : firstItem();
    if (toSelect != null) {
      tree.setSelection(toSelect);
    }
    updateSelection();
  }

  private void populateItem(TreeItem parent, EdwJourneyTreeNode node) {
    TreeItem item = parent == null ? new TreeItem(tree, SWT.NONE) : new TreeItem(parent, SWT.NONE);
    item.setText(Const.NVL(node.label(), ""));
    item.setData(node);
    for (EdwJourneyTreeNode child : node.children()) {
      populateItem(item, child);
    }
  }

  private void applyDefaultExpansion(TreeItem[] items) {
    if (items == null) {
      return;
    }
    for (TreeItem item : items) {
      if (item == null || item.isDisposed()) {
        continue;
      }
      Object data = item.getData();
      boolean expand = false;
      if (data instanceof EdwJourneyTreeNode node) {
        expand =
            node.kind() == Kind.GROUP
                || node.kind() == Kind.STAGE
                || node.kind() == Kind.CATALOG_FEEDS
                || node.kind() == Kind.OUTPUT_GROUP
                || node.kind() == Kind.CONTROL;
      }
      item.setExpanded(expand);
      applyDefaultExpansion(item.getItems());
    }
  }

  private void setExpanded(TreeItem[] items, boolean expanded) {
    if (items == null) {
      return;
    }
    for (TreeItem item : items) {
      if (item == null || item.isDisposed()) {
        continue;
      }
      item.setExpanded(expanded);
      setExpanded(item.getItems(), expanded);
    }
  }

  private TreeItem findItem(TreeItem[] items, String nodeId) {
    if (items == null || Utils.isEmpty(nodeId)) {
      return null;
    }
    for (TreeItem item : items) {
      if (item == null || item.isDisposed()) {
        continue;
      }
      Object data = item.getData();
      if (data instanceof EdwJourneyTreeNode node && nodeId.equals(node.id())) {
        return item;
      }
      TreeItem nested = findItem(item.getItems(), nodeId);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private TreeItem firstItem() {
    TreeItem[] items = tree.getItems();
    return items != null && items.length > 0 ? items[0] : null;
  }

  private String selectedNodeId() {
    if (tree == null || tree.isDisposed()) {
      return null;
    }
    TreeItem[] selection = tree.getSelection();
    if (selection.length == 0) {
      return null;
    }
    Object data = selection[0].getData();
    if (data instanceof EdwJourneyTreeNode node) {
      return node.id();
    }
    return null;
  }

  private void updateSelection() {
    if (detailsPanel == null) {
      return;
    }
    if (poster != null) {
      poster.setMarks(EdwJourneyPosterStatus.marks(snapshot, opsOverlay));
    }
    if (selectedGroup == null) {
      if (poster != null) {
        poster.setSelected(null);
      }
      showDetails();
      try {
        detailsPanel.showNoGroup(!listGroupNames().isEmpty());
      } catch (HopException e) {
        detailsPanel.showNoGroup(false);
      }
      return;
    }
    TreeItem[] selection = tree.getSelection();
    if (selection.length == 0) {
      showPresentation(activePanel);
      return;
    }
    Object data = selection[0].getData();
    if (!(data instanceof EdwJourneyTreeNode node)) {
      showDetails();
      detailsPanel.clear();
      return;
    }
    if (node.kind() == Kind.GROUP) {
      if (poster != null) {
        poster.setSelected(blockForPanel(activePanel));
      }
      showPresentation(activePanel);
      return;
    }
    String block = blockForNode(node);
    if (poster != null) {
      poster.setSelected(block);
    }
    if (preferPanel && block != null) {
      showPresentation(EdwJourneyPanelId.forBlock(block));
    } else {
      showDetails();
      detailsPanel.setNode(snapshot, opsOverlay, selectedGroup, node);
    }
  }

  private void onBlockSelected(String blockId) {
    if (blockId == null) {
      showMap();
      return;
    }
    if ("documentation".equals(blockId)) {
      EdwDocsGuiPlugin.openHtml(hopGui, "index.html");
      return;
    }
    preferPanel = true;
    activePanel = EdwJourneyPanelId.forBlock(blockId);
    if (poster != null) {
      poster.setSelected(blockId);
    }
    showPresentation(activePanel);
  }

  private void showMap() {
    preferPanel = true;
    activePanel = EdwJourneyPanelId.PROJECT_PULSE;
    if (poster != null) {
      poster.setSelected(null);
    }
    if (tree != null && !tree.isDisposed() && tree.getItemCount() > 0) {
      applyingTreeSelection = true;
      try {
        tree.setSelection(tree.getItem(0));
      } finally {
        applyingTreeSelection = false;
      }
    }
    showPresentation(EdwJourneyPanelId.PROJECT_PULSE);
  }

  private void showPanelFromLink(String panelId) {
    EdwJourneyPanelId panel = EdwJourneyPanelId.fromWire(panelId);
    if (panel == null) {
      return;
    }
    preferPanel = true;
    activePanel = panel;
    showPresentation(panel);
  }

  private void showDetails() {
    if (bottomStack != null && detailsHost != null) {
      bottomStack.topControl = detailsHost;
      bottomStackComposite.layout();
    }
  }

  private void showPresentation(EdwJourneyPanelId panel) {
    EdwJourneyPanelId shown = panel == null ? EdwJourneyPanelId.PROJECT_PULSE : panel;
    activePanel = shown;
    if (viewerHost == null || viewerHost.isDisposed() || presentationPane == null) {
      return;
    }
    try {
      Inputs inputs =
          new Inputs(
              snapshot, opsOverlay, history, semanticLayerPresent, semanticFiles, jdbcServices);
      HGeneratedCatalog catalog =
          EdwJourneyPanelBuilder.build(EdwJourneyPanelContent.build(shown, inputs));
      presentationPane.show(
          catalog, BaseMessages.getString(PKG, "EdwJourneyPanel." + shown.name()));
      bottomStack.topControl = viewerHost;
      bottomStackComposite.layout(true, true);
      viewerHost.layout(true, true);
    } catch (Exception e) {
      showDetails();
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Title"),
          BaseMessages.getString(PKG, "EdwJourneyPerspective.Error.Open.Message"),
          e instanceof HopException hop ? hop : new HopException(e));
    }
  }

  private static String blockForNode(EdwJourneyTreeNode node) {
    if (node == null || node.stage() == null) {
      return null;
    }
    return switch (node.stage()) {
      case SOURCES -> "source-models";
      case CONTROLS, TARGET_QUALITY -> "gates";
      case DATA_VAULT -> "dv-models";
      case BUSINESS_VAULT -> "bv-models";
      case DIMENSIONAL -> "dm-models";
      case ORCHESTRATION -> "hop-engine";
      case OUTPUTS -> "lineage-view";
    };
  }

  private static String blockForPanel(EdwJourneyPanelId panel) {
    if (panel == null) {
      return null;
    }
    return switch (panel) {
      case SOURCE_DRIFT -> "source-models";
      case HARVEST_WATCH -> "harvester";
      case GATE_DESK -> "gates";
      case WAVE_BOARD -> "hop-engine";
      case RAW_VAULT -> "dv-models";
      case BUSINESS_VAULT -> "bv-models";
      case DIMENSIONAL -> "dm-models";
      case CONTRACT -> "catalog";
      case LINEAGE_OPS -> "ops";
      case NEXT_MOVE -> "coach";
      case CONSUMPTION -> "presentations";
      case PROJECT_PULSE -> null;
    };
  }

  private void onGroupCreated(ResourceDefinitionGroupMeta group) {
    if (group != null && !Utils.isEmpty(group.getName())) {
      EdwJourneyAuditSupport.storeLastGroupName(group.getName());
    }
    refresh();
  }

  @Override
  public Control getControl() {
    return composite;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }

  @Override
  public void clearSearchFilters() {
    // No search field in phase 1.
  }
}
