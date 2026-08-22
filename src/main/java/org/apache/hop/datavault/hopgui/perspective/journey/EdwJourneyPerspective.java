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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.GuiBusySupport;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
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
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

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
  private EdwJourneySnapshot snapshot = EdwJourneySnapshot.empty();
  private EdwJourneyOpsOverlay opsOverlay = EdwJourneyOpsOverlay.empty();
  private ResourceDefinitionGroupMeta selectedGroup;
  private boolean suppressingGroupEvents;

  public EdwJourneyPerspective() {
    instance = this;
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
    tree.addListener(SWT.Selection, e -> updateSelection());
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
    Composite detailsComposite = new Composite(parent, SWT.BORDER);
    detailsComposite.setLayout(new FormLayout());
    PropsUi.setLook(detailsComposite);

    Label wlDetails = new Label(detailsComposite, SWT.LEFT);
    PropsUi.setLook(wlDetails);
    wlDetails.setText(BaseMessages.getString(PKG, "EdwJourneyPerspective.Details.Label"));
    FormData fdlDetails = new FormData();
    fdlDetails.left = new FormAttachment(0, PropsUi.getMargin());
    fdlDetails.top = new FormAttachment(0, PropsUi.getMargin());
    wlDetails.setLayoutData(fdlDetails);

    Composite detailsBody = new Composite(detailsComposite, SWT.NONE);
    detailsBody.setLayout(new FormLayout());
    PropsUi.setLook(detailsBody);
    FormData fdBody = new FormData();
    fdBody.left = new FormAttachment(0, 0);
    fdBody.top = new FormAttachment(wlDetails, PropsUi.getMargin());
    fdBody.right = new FormAttachment(100, 0);
    fdBody.bottom = new FormAttachment(100, 0);
    detailsBody.setLayoutData(fdBody);

    detailsPanel = new EdwJourneyDetailsPanel(detailsBody, hopGui, this::onGroupCreated);
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
        });
    selectedGroup = groupHolder[0];
    snapshot = snapshotHolder[0] != null ? snapshotHolder[0] : EdwJourneySnapshot.empty();
    opsOverlay = overlayHolder[0] != null ? overlayHolder[0] : EdwJourneyOpsOverlay.empty();
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
    if (selectedGroup == null) {
      try {
        detailsPanel.showNoGroup(!listGroupNames().isEmpty());
      } catch (HopException e) {
        detailsPanel.showNoGroup(false);
      }
      return;
    }
    TreeItem[] selection = tree.getSelection();
    if (selection.length == 0) {
      detailsPanel.clear();
      return;
    }
    Object data = selection[0].getData();
    if (data instanceof EdwJourneyTreeNode node) {
      detailsPanel.setNode(snapshot, opsOverlay, selectedGroup, node);
    } else {
      detailsPanel.clear();
    }
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
