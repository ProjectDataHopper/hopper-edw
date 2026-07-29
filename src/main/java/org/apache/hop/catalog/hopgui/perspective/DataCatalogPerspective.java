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
 *
 */

package org.apache.hop.catalog.hopgui.perspective;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.key.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.key.GuiOsxKeyboardShortcut;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.bus.HopGuiEvents;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImportMenu;
import org.apache.hop.ui.hopgui.perspective.HopPerspectivePlugin;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/** Hop GUI perspective for browsing data catalog connections and record definitions. */
@HopPerspectivePlugin(
    id = "350-DataCatalogPerspective",
    name = "i18n::DataCatalogPerspective.Name",
    description = "i18n::DataCatalogPerspective.Description",
    image = "data-catalog.svg")
@GuiPlugin(
    name = "i18n::DataCatalogPerspective.Name",
    description = "i18n::DataCatalogPerspective.GuiPlugin.Description")
public class DataCatalogPerspective implements IHopPerspective {

  public static final Class<?> PKG = DataCatalogPerspective.class;
  public static final String ID_PERSPECTIVE_TOOLBAR_ITEM = "20035-perspective-data-catalog";
  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "DataCatalogPerspective-Toolbar";
  public static final String TOOLBAR_ITEM_IMPORT =
      "DataCatalogPerspective-Toolbar-10005-Import";
  public static final String TOOLBAR_ITEM_REFRESH = "DataCatalogPerspective-Toolbar-10000-Refresh";
  public static final String TOOLBAR_ITEM_EXPAND_ALL =
      "DataCatalogPerspective-Toolbar-10001-ExpandAll";
  public static final String TOOLBAR_ITEM_COLLAPSE_ALL =
      "DataCatalogPerspective-Toolbar-10003-CollapseAll";
  public static final String TOOLBAR_ITEM_PREVIEW = "DataCatalogPerspective-Toolbar-10015-Preview";
  public static final String TOOLBAR_ITEM_DELETE = "DataCatalogPerspective-Toolbar-10010-Delete";
  private static final String TREE_KEY = "Data catalog perspective tree";

  @Getter private static DataCatalogPerspective instance;

  private HopGui hopGui;
  private Composite composite;
  private SashForm sash;
  private Tree tree;
  private GuiToolbarWidgets toolBarWidgets;
  private RecordDefinitionDetailsPanel detailsPanel;
  private RecordDefinition selectedRecordDefinition;
  private Combo wRecordFilter;
  private Text searchText;
  private Button wGroupByNamespace;
  private DataCatalogRecordListFilter recordListFilter = DataCatalogRecordListFilter.ALL;
  private String filterText = "";
  private boolean groupByNamespace = DataCatalogPerspectiveAuditSupport.retrieveGroupByNamespace();
  private final Set<String> treeStateSeeded = new HashSet<>();
  private boolean rebuildingTree;
  /** Suppresses search/filter widget listeners while programmatically adjusting them. */
  private boolean suppressingFilterEvents;

  public DataCatalogPerspective() {
    instance = this;
  }

  @Override
  public String getId() {
    return "data-catalog-perspective";
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

    refresh();

    hopGui
        .getEventsHandler()
        .addEventListener(
            getClass().getName(), e -> refresh(), HopGuiEvents.MetadataChanged.name());
  }

  private void createTreePane(Composite parent) {
    Composite treeComposite = new Composite(parent, SWT.BORDER);
    treeComposite.setLayout(new FormLayout());
    PropsUi.setLook(treeComposite);

    Label wlTree = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlTree);
    wlTree.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Tree.Label"));
    FormData fdlTree = new FormData();
    fdlTree.left = new FormAttachment(0, PropsUi.getMargin());
    fdlTree.top = new FormAttachment(0, PropsUi.getMargin());
    wlTree.setLayoutData(fdlTree);

    Label wlFilter = new Label(treeComposite, SWT.LEFT);
    PropsUi.setLook(wlFilter);
    wlFilter.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Filter.Label"));
    FormData fdlFilter = new FormData();
    fdlFilter.left = new FormAttachment(0, PropsUi.getMargin());
    fdlFilter.top = new FormAttachment(wlTree, PropsUi.getMargin());
    wlFilter.setLayoutData(fdlFilter);

    wRecordFilter = new Combo(treeComposite, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wRecordFilter);
    for (DataCatalogRecordListFilter filter : DataCatalogRecordListFilter.values()) {
      wRecordFilter.add(filter.label());
    }
    wRecordFilter.select(0);
    wRecordFilter.addListener(
        SWT.Selection,
        e -> {
          if (suppressingFilterEvents) {
            return;
          }
          recordListFilter = DataCatalogRecordListFilter.fromIndex(wRecordFilter.getSelectionIndex());
          refresh();
        });
    FormData fdFilter = new FormData();
    fdFilter.left = new FormAttachment(wlFilter, PropsUi.getMargin());
    fdFilter.top = new FormAttachment(wlTree, PropsUi.getMargin());
    fdFilter.right = new FormAttachment(100, -PropsUi.getMargin());
    wRecordFilter.setLayoutData(fdFilter);

    searchText = new Text(treeComposite, SWT.SEARCH | SWT.ICON_CANCEL | SWT.ICON_SEARCH);
    searchText.setMessage(
        BaseMessages.getString(PKG, "DataCatalogPerspective.Search.Placeholder"));
    PropsUi.setLook(searchText);
    FormData fdSearch = new FormData();
    fdSearch.left = new FormAttachment(0, 0);
    fdSearch.top = new FormAttachment(wRecordFilter, PropsUi.getMargin());
    fdSearch.right = new FormAttachment(100, 0);
    searchText.setLayoutData(fdSearch);
    searchText.addListener(
        SWT.Modify,
        e -> {
          if (suppressingFilterEvents) {
            return;
          }
          filterText = Const.NVL(searchText.getText(), "").trim();
          refresh();
        });

    wGroupByNamespace = new Button(treeComposite, SWT.CHECK);
    PropsUi.setLook(wGroupByNamespace);
    wGroupByNamespace.setText(
        BaseMessages.getString(PKG, "DataCatalogPerspective.GroupByNamespace.Label"));
    wGroupByNamespace.setToolTipText(
        BaseMessages.getString(PKG, "DataCatalogPerspective.GroupByNamespace.Tooltip"));
    wGroupByNamespace.setSelection(groupByNamespace);
    wGroupByNamespace.addListener(
        SWT.Selection,
        e -> {
          groupByNamespace = wGroupByNamespace.getSelection();
          DataCatalogPerspectiveAuditSupport.storeGroupByNamespace(groupByNamespace);
          refresh();
        });
    FormData fdGroupByNamespace = new FormData();
    fdGroupByNamespace.left = new FormAttachment(0, PropsUi.getMargin());
    fdGroupByNamespace.top = new FormAttachment(searchText, PropsUi.getMargin());
    wGroupByNamespace.setLayoutData(fdGroupByNamespace);

    IToolbarContainer toolBarContainer =
        ToolbarFacade.createToolbarContainer(
            treeComposite, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
    Control toolBar = toolBarContainer.getControl();
    toolBarWidgets = new GuiToolbarWidgets();
    toolBarWidgets.registerGuiPluginObject(this);
    toolBarWidgets.createToolbarWidgets(toolBarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment(0, 0);
    fdToolBar.top = new FormAttachment(wGroupByNamespace, PropsUi.getMargin());
    fdToolBar.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdToolBar);
    toolBar.pack();
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);

    tree = new Tree(treeComposite, SWT.MULTI | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(tree);
    FormData fdTree = new FormData();
    fdTree.left = new FormAttachment(0, 0);
    fdTree.top = new FormAttachment(toolBar, 0);
    fdTree.right = new FormAttachment(100, 0);
    fdTree.bottom = new FormAttachment(100, 0);
    tree.setLayoutData(fdTree);

    tree.addListener(SWT.Selection, e -> updateSelection());
    tree.addListener(
        SWT.Expand,
        e -> {
          if (!rebuildingTree) {
            DataCatalogTreeMemorySupport.recordExpandedState(
                (TreeItem) e.item, TREE_KEY, true, treeStateSeeded);
          }
        });
    tree.addListener(
        SWT.Collapse,
        e -> {
          if (!rebuildingTree) {
            DataCatalogTreeMemorySupport.recordExpandedState(
                (TreeItem) e.item, TREE_KEY, false, treeStateSeeded);
          }
        });
  }

  private void createDetailsPane(Composite parent) {
    Composite detailsComposite = new Composite(parent, SWT.BORDER);
    detailsComposite.setLayout(new FormLayout());
    PropsUi.setLook(detailsComposite);

    Label wlDetails = new Label(detailsComposite, SWT.LEFT);
    PropsUi.setLook(wlDetails);
    wlDetails.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Details.Label"));
    FormData fdlDetails = new FormData();
    fdlDetails.left = new FormAttachment(0, PropsUi.getMargin());
    fdlDetails.top = new FormAttachment(0, PropsUi.getMargin());
    wlDetails.setLayoutData(fdlDetails);

    Composite detailsBody = new Composite(detailsComposite, SWT.NONE);
    PropsUi.setLook(detailsBody);
    FormData fdBody = new FormData();
    fdBody.left = new FormAttachment(0, 0);
    fdBody.top = new FormAttachment(wlDetails, PropsUi.getMargin());
    fdBody.right = new FormAttachment(100, 0);
    fdBody.bottom = new FormAttachment(100, 0);
    detailsBody.setLayoutData(fdBody);

    detailsPanel = new RecordDefinitionDetailsPanel(detailsBody, hopGui.getVariables(), this::refresh);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_IMPORT,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Import.Tooltip",
      image = "ui/images/add.svg")
  public void openImportMenu() {
    DataCatalogImportMenu.open(
        hopGui, null, resolveSelectedCatalogConnectionName(), this::refresh);
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Refresh.Tooltip",
      image = "ui/images/refresh.svg")
  @GuiKeyboardShortcut(key = SWT.F5)
  @GuiOsxKeyboardShortcut(key = SWT.F5)
  public void refreshToolbar() {
    refresh();
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_EXPAND_ALL,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.ExpandAll.Tooltip",
      image = "ui/images/expand-all.svg")
  public void expandAllFolders() {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    tree.setRedraw(false);
    try {
      DataCatalogTreeNavigation.setFolderExpandedState(tree, TREE_KEY, groupByNamespace, true);
    } finally {
      tree.setRedraw(true);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_COLLAPSE_ALL,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.CollapseAll.Tooltip",
      image = "ui/images/collapse-all.svg")
  public void collapseAllFolders() {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    tree.setRedraw(false);
    try {
      DataCatalogTreeNavigation.setFolderExpandedState(tree, TREE_KEY, groupByNamespace, false);
    } finally {
      tree.setRedraw(true);
    }
  }

  /**
   * Activates this perspective and selects a record definition in the tree and details panel.
   *
   * <p>Navigation from other perspectives (e.g. Execution Map "Open in Data Catalog") must succeed
   * even when the catalog tree currently has a type filter or search text that would hide the
   * target. Preview uses the registry directly and is unaffected by tree filters; selection is not.
   */
  public void selectRecordDefinition(String catalogConnectionName, RecordDefinitionKey key)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName) || key == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.SelectRecord.MissingTarget"));
    }
    if (tree == null || tree.isDisposed()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.SelectRecord.TreeUnavailable"));
    }

    // Confirm the definition exists in the catalog backend before relying on the filtered tree.
    RecordDefinition existing =
        RecordDefinitionRegistry.getInstance()
            .read(
                catalogConnectionName,
                key,
                hopGui.getVariables(),
                hopGui.getMetadataProvider());
    if (existing == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DataCatalogPerspective.Error.SelectRecord.NotFound",
              key.toString(),
              catalogConnectionName));
    }

    // Tree filters only affect presentation; clear them so the target is listable.
    boolean filtersCleared = clearTreeFiltersForNavigation();
    activate();
    String recordKey = key.toString();
    if (filtersCleared || !trySelectRecord(catalogConnectionName, recordKey)) {
      refresh();
    }
    if (!trySelectRecord(catalogConnectionName, recordKey)) {
      // Definition exists in the catalog backend; open details even if the tree item is missing
      // (e.g. transient population failure). Avoid blocking navigation from Execution Map.
      selectedRecordDefinition = existing;
      if (detailsPanel != null) {
        detailsPanel.setRecordDefinition(catalogConnectionName, existing);
      }
      updateToolbar();
      return;
    }
    updateSelection();
  }

  /**
   * Resets type filter and search text so programmatic navigation can see every record. Returns
   * true when filters actually changed.
   */
  private boolean clearTreeFiltersForNavigation() {
    boolean changed = false;
    suppressingFilterEvents = true;
    try {
      if (recordListFilter != DataCatalogRecordListFilter.ALL) {
        recordListFilter = DataCatalogRecordListFilter.ALL;
        changed = true;
      }
      if (wRecordFilter != null && !wRecordFilter.isDisposed() && wRecordFilter.getSelectionIndex() != 0) {
        wRecordFilter.select(0);
        changed = true;
      }
      if (!Utils.isEmpty(filterText)) {
        filterText = "";
        changed = true;
      }
      if (searchText != null && !searchText.isDisposed() && !Utils.isEmpty(searchText.getText())) {
        searchText.setText("");
        changed = true;
      }
    } finally {
      suppressingFilterEvents = false;
    }
    return changed;
  }

  public void refresh() {
    if (tree == null || tree.isDisposed()) {
      return;
    }

    RecordDefinitionRegistry.getInstance().invalidate();

    TreeItem[] selection = tree.getSelection();
    String selectedCatalog = DataCatalogSelectionSupport.firstCatalogConnectionName(selection);
    List<String> selectedRecordKeys = DataCatalogSelectionSupport.collectRecordKeys(selection);

    rebuildingTree = true;
    tree.setRedraw(false);
    try {
      tree.removeAll();
      populateTree();
      DataCatalogTreeMemorySupport.applyExpandedState(
          tree, TREE_KEY, groupByNamespace, treeStateSeeded);
    } catch (HopException e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Refresh.Title"),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.Refresh.Message"),
          e);
    } finally {
      rebuildingTree = false;
      tree.setRedraw(true);
    }

    restoreSelection(selectedCatalog, selectedRecordKeys);
    updateSelection();
    updateToolbar();
  }

  private void populateTree() throws HopException {
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    IVariables variables = hopGui.getVariables();
    List<DataCatalogMeta> connections = listEnabledConnections(metadataProvider);
    boolean hideEmptyConnections = shouldHideEmptyConnections();

    Map<String, List<RecordDefinitionRef>> refsByCatalog = new LinkedHashMap<>();
    List<RecordDefinitionRef> allRefs =
        RecordDefinitionRegistry.getInstance()
            .listAll(buildRecordListQuery(), variables, metadataProvider);
    for (RecordDefinitionRef ref : allRefs) {
      refsByCatalog
          .computeIfAbsent(ref.getCatalogConnectionName(), name -> new ArrayList<>())
          .add(ref);
    }

    for (DataCatalogMeta connection : connections) {
      List<RecordDefinitionRef> refs =
          DataCatalogTreeBuilder.sortRefs(
              refsByCatalog.getOrDefault(connection.getName(), List.of()));

      if (hideEmptyConnections && refs.isEmpty()) {
        continue;
      }

      TreeItem catalogItem = new TreeItem(tree, SWT.NONE);
      catalogItem.setText(Const.NVL(connection.getName(), ""));
      catalogItem.setData(DataCatalogTreeNode.catalog(connection.getName()));
      populateConnectionItems(catalogItem, connection.getName(), refs);
      populateVersionItems(catalogItem, connection.getName(), variables, metadataProvider);
    }
  }

  private void populateVersionItems(
      TreeItem catalogItem,
      String connectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      List<org.apache.hop.catalog.versioning.CatalogVersionEntry> versions =
          org.apache.hop.catalog.versioning.CatalogVersionService.listVersions(
              connectionName, variables, metadataProvider);
      if (versions == null || versions.isEmpty()) {
        return;
      }
      TreeItem versionsRoot = new TreeItem(catalogItem, SWT.NONE);
      versionsRoot.setText(
          BaseMessages.getString(PKG, "DataCatalogPerspective.Tree.Versions.Label"));
      versionsRoot.setData(DataCatalogTreeNode.versionsRoot(connectionName));
      for (org.apache.hop.catalog.versioning.CatalogVersionEntry entry : versions) {
        if (entry == null || Utils.isEmpty(entry.getTag())) {
          continue;
        }
        TreeItem tagItem = new TreeItem(versionsRoot, SWT.NONE);
        String label =
            entry.getTag()
                + "  ("
                + Const.NVL(entry.getCreatedAt(), "")
                + ", "
                + entry.getRecordCount()
                + " "
                + BaseMessages.getString(PKG, "DataCatalogPerspective.Tree.Versions.Records")
                + ")";
        tagItem.setText(label);
        tagItem.setData(DataCatalogTreeNode.versionTag(connectionName, entry.getTag()));
        try {
          List<org.apache.hop.catalog.model.RecordDefinition> defs =
              org.apache.hop.catalog.versioning.CatalogVersionService.readAllAtVersion(
                  connectionName, entry.getTag(), variables, metadataProvider);
          for (org.apache.hop.catalog.model.RecordDefinition def : defs) {
            if (def == null || def.getKey() == null) {
              continue;
            }
            TreeItem recordItem = new TreeItem(tagItem, SWT.NONE);
            recordItem.setText(def.getKey().toString());
            recordItem.setData(
                DataCatalogTreeNode.versionRecord(connectionName, entry.getTag(), def.getKey()));
          }
        } catch (HopException ignored) {
          // Tag still shown; expand may be empty if snapshot is corrupt.
        }
      }
    } catch (HopException ignored) {
      // FILE catalogs without versions simply omit the Versions node.
    }
  }

  private RecordDefinitionQuery buildRecordListQuery() {
    RecordDefinitionQuery query = recordListFilter.toQuery();
    if (!Utils.isEmpty(filterText)) {
      query.setNameContains(filterText);
    }
    return query;
  }

  private boolean shouldHideEmptyConnections() {
    return recordListFilter != DataCatalogRecordListFilter.ALL || !Utils.isEmpty(filterText);
  }

  private void populateConnectionItems(
      TreeItem catalogItem, String connectionName, List<RecordDefinitionRef> refs) {
    if (groupByNamespace) {
      for (Map.Entry<String, List<RecordDefinitionRef>> entry :
          DataCatalogTreeBuilder.groupByNamespace(refs).entrySet()) {
        if (entry.getValue().isEmpty()) {
          continue;
        }
        TreeItem namespaceItem = new TreeItem(catalogItem, SWT.NONE);
        namespaceItem.setText(entry.getKey());
        namespaceItem.setData(DataCatalogTreeNode.namespace(connectionName, entry.getKey()));
        for (RecordDefinitionRef ref : entry.getValue()) {
          addRecordItem(namespaceItem, connectionName, ref);
        }
      }
      return;
    }

    for (RecordDefinitionRef ref : refs) {
      addRecordItem(catalogItem, connectionName, ref);
    }
  }

  private void addRecordItem(TreeItem parent, String connectionName, RecordDefinitionRef ref) {
    TreeItem recordItem = new TreeItem(parent, SWT.NONE);
    recordItem.setText(DataCatalogTreeBuilder.displayRecordName(ref, groupByNamespace));
    recordItem.setData(DataCatalogTreeNode.record(connectionName, ref));
  }

  private boolean trySelectRecord(String catalogConnectionName, String recordKey) {
    if (tree == null || tree.isDisposed() || Utils.isEmpty(catalogConnectionName) || Utils.isEmpty(recordKey)) {
      return false;
    }
    TreeItem catalogItem = findCatalogItem(catalogConnectionName);
    if (catalogItem == null) {
      return false;
    }
    List<TreeItem> itemsToSelect =
        DataCatalogTreeNavigation.findRecordItems(catalogItem, List.of(recordKey));
    if (itemsToSelect.isEmpty()) {
      return false;
    }
    TreeItem target = itemsToSelect.get(0);
    DataCatalogTreeNavigation.expandAncestors(target, TREE_KEY, treeStateSeeded);
    tree.setSelection(target);
    tree.showSelection();
    return isRecordSelected(catalogConnectionName, recordKey);
  }

  private boolean isRecordSelected(String catalogConnectionName, String recordKey) {
    if (tree == null || tree.isDisposed() || tree.getSelectionCount() < 1) {
      return false;
    }
    for (TreeItem selected : tree.getSelection()) {
      if (selected == null || selected.isDisposed()) {
        continue;
      }
      DataCatalogTreeNode node = (DataCatalogTreeNode) selected.getData();
      if (node != null
          && node.getType() == DataCatalogTreeNode.Type.RECORD
          && catalogConnectionName.equals(node.getCatalogConnectionName())
          && node.getRecordKey() != null
          && recordKey.equals(node.getRecordKey().toString())) {
        return true;
      }
    }
    return false;
  }

  private TreeItem findCatalogItem(String catalogConnectionName) {
    if (tree == null || tree.isDisposed() || Utils.isEmpty(catalogConnectionName)) {
      return null;
    }
    for (TreeItem catalogItem : tree.getItems()) {
      if (catalogItem == null || catalogItem.isDisposed()) {
        continue;
      }
      DataCatalogTreeNode catalogNode = (DataCatalogTreeNode) catalogItem.getData();
      if (catalogNode != null
          && catalogConnectionName.equals(catalogNode.getCatalogConnectionName())) {
        return catalogItem;
      }
    }
    return null;
  }

  private void restoreSelection(String selectedCatalog, List<String> selectedRecordKeys) {
    if (Utils.isEmpty(selectedCatalog)) {
      return;
    }

    TreeItem catalogItem = findCatalogItem(selectedCatalog);
    if (catalogItem == null) {
      return;
    }

    if (selectedRecordKeys == null || selectedRecordKeys.isEmpty()) {
      tree.setSelection(catalogItem);
      return;
    }

    List<TreeItem> itemsToSelect =
        DataCatalogTreeNavigation.findRecordItems(catalogItem, selectedRecordKeys);

    if (!itemsToSelect.isEmpty()) {
      for (TreeItem item : itemsToSelect) {
        DataCatalogTreeNavigation.expandAncestors(item, TREE_KEY, treeStateSeeded);
      }
      tree.setSelection(itemsToSelect.toArray(new TreeItem[0]));
      tree.showSelection();
    } else {
      tree.setSelection(catalogItem);
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_PREVIEW,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Preview.Tooltip",
      image = "ui/images/preview.svg")
  public void previewSelectedRecord() {
    if (selectedRecordDefinition == null
        || !RecordDefinitionPreviewSupport.supportsPreview(selectedRecordDefinition)) {
      return;
    }
    RecordDefinitionPreviewRunner.run(
        hopGui.getShell(),
        selectedRecordDefinition,
        hopGui.getVariables(),
        hopGui.getMetadataProvider());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_DELETE,
      toolTip = "i18n::DataCatalogPerspective.Toolbar.Delete.Tooltip",
      image = "ui/images/delete.svg")
  @GuiKeyboardShortcut(key = SWT.DEL)
  @GuiOsxKeyboardShortcut(key = SWT.DEL)
  public void onDeleteRecord() {
    if (tree == null || tree.isDisposed()) {
      return;
    }

    List<DataCatalogTreeNode> recordNodes =
        DataCatalogSelectionSupport.collectRecordNodes(tree.getSelection());
    if (recordNodes.isEmpty()) {
      return;
    }

    MessageBox confirm =
        new MessageBox(hopGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
    confirm.setText(BaseMessages.getString(PKG, "DataCatalogPerspective.Delete.Title"));
    if (recordNodes.size() == 1) {
      DataCatalogTreeNode node = recordNodes.get(0);
      confirm.setMessage(
          BaseMessages.getString(
              PKG,
              "DataCatalogPerspective.Delete.Message",
              node.getRecordKey().toString(),
              node.getCatalogConnectionName()));
    } else {
      confirm.setMessage(
          BaseMessages.getString(
              PKG, "DataCatalogPerspective.Delete.Bulk.Message", recordNodes.size()));
    }
    if (confirm.open() != SWT.YES) {
      return;
    }

    int deletedCount = 0;
    List<String> errors = new ArrayList<>();
    for (DataCatalogTreeNode node : recordNodes) {
      try {
        RecordDefinitionRegistry.getInstance()
            .delete(
                node.getCatalogConnectionName(),
                node.getRecordKey(),
                hopGui.getVariables(),
                hopGui.getMetadataProvider());
        deletedCount++;
      } catch (HopException e) {
        errors.add(
            node.getRecordKey().toString()
                + ": "
                + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      }
    }

    refresh();

    if (!errors.isEmpty()) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Delete.PartialFailure.Title"),
          BaseMessages.getString(
              PKG,
              "DataCatalogPerspective.Delete.PartialFailure.Message",
              deletedCount,
              String.join(Const.CR, errors)),
          null);
    }
  }

  private void updateSelection() {
    selectedRecordDefinition = null;

    List<DataCatalogTreeNode> recordNodes =
        DataCatalogSelectionSupport.collectRecordNodes(tree.getSelection());
    if (recordNodes.isEmpty()) {
      detailsPanel.clear();
      updateToolbar();
      return;
    }

    if (recordNodes.size() > 1) {
      detailsPanel.showMultipleSelected(recordNodes.size());
      updateToolbar();
      return;
    }

    DataCatalogTreeNode node = recordNodes.get(0);
    try {
      RecordDefinition definition;
      boolean readOnlyVersion = node.getType() == DataCatalogTreeNode.Type.VERSION_RECORD;
      if (readOnlyVersion) {
        definition =
            org.apache.hop.catalog.versioning.CatalogVersionService.readDefinition(
                    node.getCatalogConnectionName(),
                    node.getVersionTag(),
                    node.getRecordKey(),
                    hopGui.getVariables(),
                    hopGui.getMetadataProvider())
                .orElse(null);
      } else {
        definition =
            RecordDefinitionRegistry.getInstance()
                .read(
                    node.getCatalogConnectionName(),
                    node.getRecordKey(),
                    hopGui.getVariables(),
                    hopGui.getMetadataProvider());
      }
      selectedRecordDefinition = definition;
      detailsPanel.setRecordDefinition(
          node.getCatalogConnectionName(), definition, readOnlyVersion, node.getVersionTag());
    } catch (HopException e) {
      detailsPanel.clear();
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.LoadRecord.Title"),
          BaseMessages.getString(PKG, "DataCatalogPerspective.Error.LoadRecord.Message"),
          e);
    }
    updateToolbar();
  }

  private String resolveSelectedCatalogConnectionName() {
    if (tree == null || tree.isDisposed()) {
      return null;
    }
    return DataCatalogSelectionSupport.firstCatalogConnectionName(tree.getSelection());
  }

  private void updateToolbar() {
    if (toolBarWidgets == null) {
      return;
    }
    List<DataCatalogTreeNode> selectedRecords =
        DataCatalogSelectionSupport.collectRecordNodes(tree.getSelection());
    boolean recordSelected = !selectedRecords.isEmpty();
    boolean anyVersion =
        selectedRecords.stream()
            .anyMatch(n -> n != null && n.getType() == DataCatalogTreeNode.Type.VERSION_RECORD);
    toolBarWidgets.enableToolbarItem(TOOLBAR_ITEM_DELETE, recordSelected && !anyVersion);
    toolBarWidgets.enableToolbarItem(
        TOOLBAR_ITEM_PREVIEW,
        selectedRecordDefinition != null
            && RecordDefinitionPreviewSupport.supportsPreview(selectedRecordDefinition));
  }

  private List<DataCatalogMeta> listEnabledConnections(IHopMetadataProvider metadataProvider)
      throws HopException {
    List<DataCatalogMeta> connections = new ArrayList<>();
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    List<String> names = serializer.listObjectNames();
    names.sort(String.CASE_INSENSITIVE_ORDER);
    for (String name : names) {
      DataCatalogMeta meta = serializer.load(name);
      if (meta != null && meta.isEnabled()) {
        connections.add(meta);
      }
    }
    return connections;
  }

  @Override
  public Control getControl() {
    return composite;
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return new ArrayList<>();
  }
}