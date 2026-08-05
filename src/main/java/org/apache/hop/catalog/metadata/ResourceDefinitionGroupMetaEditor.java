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
package org.apache.hop.catalog.metadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.versioning.CatalogVersionGuiSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.GuiBusySupport;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.hopgui.resourcedefinition.ResourceDefinitionValidationGuiSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Editor for {@link ResourceDefinitionGroupMeta}.
 *
 * <p>Optimized for large model lists (hundreds of paths): authoritative in-memory lists, lazy tab
 * materialization, view-only path filter, and bulk table fill without full-column optWidth scans.
 */
@GuiPlugin(description = "Editor for Resource Definition Group metadata")
public class ResourceDefinitionGroupMetaEditor extends MetadataEditor<ResourceDefinitionGroupMeta> {

  private static final Class<?> PKG = ResourceDefinitionGroupMetaEditor.class;

  /** Sample this many rows when sizing the path column (avoids O(n) textExtent over all paths). */
  private static final int OPT_WIDTH_SAMPLE_ROWS = 40;

  private Text wName;
  private Text wDescription;
  private Combo wCatalogConnection;
  private Text wPreviewRowLimit;
  private Button wDetailedChecking;
  private CTabFolder tabFolder;

  private ModelTab dvTab;
  private ModelTab bvTab;
  private ModelTab dmTab;

  public ResourceDefinitionGroupMetaEditor(
      HopGui hopGui,
      MetadataManager<ResourceDefinitionGroupMeta> manager,
      ResourceDefinitionGroupMeta metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);
    Control lastControl = wName;

    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    lastControl = addTextField(parent, middle, margin, lastControl, "Description", wDescription);

    Label wlCatalog = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlCatalog);
    wlCatalog.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.CatalogConnection.Label"));
    FormData fdlCatalog = new FormData();
    fdlCatalog.top = new FormAttachment(lastControl, margin);
    fdlCatalog.left = new FormAttachment(0, 0);
    fdlCatalog.right = new FormAttachment(middle, -margin);
    wlCatalog.setLayoutData(fdlCatalog);

    wCatalogConnection = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wCatalogConnection);
    populateCatalogConnections();
    FormData fdCatalog = new FormData();
    fdCatalog.top = new FormAttachment(wlCatalog, 0, SWT.CENTER);
    fdCatalog.left = new FormAttachment(middle, 0);
    fdCatalog.right = new FormAttachment(100, 0);
    wCatalogConnection.setLayoutData(fdCatalog);
    lastControl = wCatalogConnection;

    lastControl =
        addTextField(
            parent,
            middle,
            margin,
            lastControl,
            "PreviewRowLimit",
            wPreviewRowLimit = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    Label wlDetailed = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDetailed);
    wlDetailed.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.DetailedChecking.Label"));
    FormData fdlDetailed = new FormData();
    fdlDetailed.top = new FormAttachment(lastControl, margin);
    fdlDetailed.left = new FormAttachment(0, 0);
    fdlDetailed.right = new FormAttachment(middle, -margin);
    wlDetailed.setLayoutData(fdlDetailed);

    wDetailedChecking = new Button(parent, SWT.CHECK);
    PropsUi.setLook(wDetailedChecking);
    wDetailedChecking.setSelection(true);
    FormData fdDetailed = new FormData();
    fdDetailed.top = new FormAttachment(wlDetailed, 0, SWT.CENTER);
    fdDetailed.left = new FormAttachment(middle, 0);
    wDetailedChecking.setLayoutData(fdDetailed);
    lastControl = wDetailedChecking;

    tabFolder = new CTabFolder(parent, SWT.BORDER);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(lastControl, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, -50);
    tabFolder.setLayoutData(fdTabs);

    dvTab = createModelTab(tabFolder, "DataVaultModels", HopVaultFileType.VAULT_FILE_EXTENSION);
    bvTab =
        createModelTab(
            tabFolder,
            "BusinessVaultModels",
            HopBusinessVaultFileType.BUSINESS_VAULT_FILE_EXTENSION);
    dmTab =
        createModelTab(
            tabFolder, "DimensionalModels", HopDimensionalFileType.DIMENSIONAL_FILE_EXTENSION);
    tabFolder.setSelection(0);
    tabFolder.addListener(
        SWT.Selection,
        e -> {
          ModelTab selected = selectedModelTab();
          if (selected != null) {
            selected.ensureLoaded();
          }
        });

    Button wValidate = new Button(parent, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.ValidateSources.Label"));
    wValidate.addListener(SWT.Selection, e -> validateSources());
    FormData fdValidate = new FormData();
    fdValidate.right = new FormAttachment(100, 0);
    fdValidate.bottom = new FormAttachment(100, 0);
    wValidate.setLayoutData(fdValidate);

    Button wTagVersion = new Button(parent, SWT.PUSH);
    wTagVersion.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.TagCatalogVersion.Label"));
    wTagVersion.addListener(SWT.Selection, e -> tagCatalogVersion());
    FormData fdTagVersion = new FormData();
    fdTagVersion.right = new FormAttachment(wValidate, -margin);
    fdTagVersion.bottom = new FormAttachment(100, 0);
    wTagVersion.setLayoutData(fdTagVersion);

    Button wListVersions = new Button(parent, SWT.PUSH);
    wListVersions.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.ListCatalogVersions.Label"));
    wListVersions.addListener(SWT.Selection, e -> listCatalogVersions());
    FormData fdListVersions = new FormData();
    fdListVersions.right = new FormAttachment(wTagVersion, -margin);
    fdListVersions.bottom = new FormAttachment(100, 0);
    wListVersions.setLayoutData(fdListVersions);

    Button wBrowseLineage = new Button(parent, SWT.PUSH);
    wBrowseLineage.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.BrowseLineage.Label"));
    wBrowseLineage.setToolTipText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.BrowseLineage.ToolTip"));
    wBrowseLineage.addListener(SWT.Selection, e -> browseLineage());
    FormData fdBrowseLineage = new FormData();
    fdBrowseLineage.right = new FormAttachment(wListVersions, -margin);
    fdBrowseLineage.bottom = new FormAttachment(100, 0);
    wBrowseLineage.setLayoutData(fdBrowseLineage);

    setWidgetsContent();
    resetChanged();

    Listener modifyListener = e -> setChanged();
    wName.addListener(SWT.Modify, modifyListener);
    wDescription.addListener(SWT.Modify, modifyListener);
    wCatalogConnection.addListener(SWT.Modify, modifyListener);
    wPreviewRowLimit.addListener(SWT.Modify, modifyListener);
    wDetailedChecking.addListener(SWT.Selection, modifyListener);
  }

  private Control addTextField(
      Composite parent,
      int middle,
      int margin,
      Control lastControl,
      String labelKey,
      Text textWidget) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor." + labelKey + ".Label"));
    FormData fdl = new FormData();
    fdl.top = new FormAttachment(lastControl, margin);
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    label.setLayoutData(fdl);

    PropsUi.setLook(textWidget);
    FormData fd = new FormData();
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    textWidget.setLayoutData(fd);
    return textWidget;
  }

  private ModelTab createModelTab(CTabFolder folder, String labelKey, String modelExtension) {
    CTabItem tab = new CTabItem(folder, SWT.NONE);
    tab.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor." + labelKey + ".Tab"));

    Composite comp = new Composite(folder, SWT.NONE);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int margin = PropsUi.getMargin();

    Label wlFilter = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlFilter);
    wlFilter.setText(BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.Filter.Label"));
    FormData fdlFilter = new FormData();
    fdlFilter.left = new FormAttachment(0, margin);
    fdlFilter.top = new FormAttachment(0, margin);
    wlFilter.setLayoutData(fdlFilter);

    Text wFilter =
        new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
    PropsUi.setLook(wFilter);
    wFilter.setMessage(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.Filter.Placeholder"));
    FormData fdFilter = new FormData();
    fdFilter.left = new FormAttachment(wlFilter, margin);
    fdFilter.top = new FormAttachment(0, margin);
    fdFilter.right = new FormAttachment(100, -margin);
    wFilter.setLayoutData(fdFilter);

    Button wAddModels = new Button(comp, SWT.PUSH);
    PropsUi.setLook(wAddModels);
    wAddModels.setText(
        BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.AddModels.Label"));
    FormData fdAddModels = new FormData();
    fdAddModels.left = new FormAttachment(0, margin);
    fdAddModels.bottom = new FormAttachment(100, -margin);
    wAddModels.setLayoutData(fdAddModels);

    Label wlCount = new Label(comp, SWT.LEFT);
    PropsUi.setLook(wlCount);
    FormData fdCount = new FormData();
    fdCount.left = new FormAttachment(wAddModels, margin * 2);
    fdCount.top = new FormAttachment(wAddModels, 0, SWT.CENTER);
    wlCount.setLayoutData(fdCount);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.ModelFile.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };
    final ModelTab[] tabHolder = new ModelTab[1];
    TableView table =
        new TableView(
            projectVariables(),
            comp,
            SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER,
            columns,
            1,
            e -> {
              if (tabHolder[0] != null) {
                tabHolder[0].onTableModified();
              }
            },
            PropsUi.getInstance());
    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, margin);
    fdTable.right = new FormAttachment(100, -margin);
    fdTable.top = new FormAttachment(wFilter, margin);
    fdTable.bottom = new FormAttachment(wAddModels, -margin);
    table.setLayoutData(fdTable);

    ModelTab modelTab = new ModelTab(labelKey, modelExtension, table, wFilter, wlCount);
    tabHolder[0] = modelTab;
    wAddModels.addListener(SWT.Selection, e -> addModelsFromProject(modelTab));
    wFilter.addListener(SWT.Modify, e -> modelTab.onFilterChanged());
    return modelTab;
  }

  private ModelTab selectedModelTab() {
    if (tabFolder == null || tabFolder.isDisposed()) {
      return null;
    }
    return switch (tabFolder.getSelectionIndex()) {
      case 0 -> dvTab;
      case 1 -> bvTab;
      case 2 -> dmTab;
      default -> null;
    };
  }

  private IVariables projectVariables() {
    return getHopGui().getVariables();
  }

  private void addModelsFromProject(ModelTab modelTab) {
    modelTab.flushTableToList();
    IVariables variables = projectVariables();
    final List<String>[] discoveredHolder = new List[1];
    GuiBusySupport.showWhile(
        getShell(),
        () ->
            discoveredHolder[0] =
                ResourceDefinitionGroupModelDiscoverySupport.findProjectModelFiles(
                    variables, modelTab.modelExtension));
    List<String> discovered = discoveredHolder[0] != null ? discoveredHolder[0] : List.of();

    if (discovered.isEmpty()) {
      MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(
          BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.AddModels.NoFiles.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG,
              "ResourceDefinitionGroupMetaEditor.AddModels.NoFiles.Message",
              modelTab.modelExtension));
      box.open();
      return;
    }

    String[] choices = discovered.toArray(new String[0]);
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            getShell(),
            choices,
            BaseMessages.getString(
                PKG,
                "ResourceDefinitionGroupMetaEditor.AddModels.Dialog.Title",
                modelTab.modelExtension),
            BaseMessages.getString(
                PKG,
                "ResourceDefinitionGroupMetaEditor.AddModels.Dialog.Message",
                BaseMessages.getString(
                    PKG, "ResourceDefinitionGroupMetaEditor." + modelTab.labelKey + ".Tab")));
    dialog.setMulti(true);
    if (dialog.open() == null) {
      return;
    }

    int[] indices = dialog.getSelectionIndeces();
    if (indices == null || indices.length == 0) {
      return;
    }

    Set<String> existing = new LinkedHashSet<>(modelTab.paths);
    for (int index : indices) {
      if (index >= 0 && index < choices.length) {
        existing.add(choices[index]);
      }
    }
    modelTab.paths.clear();
    modelTab.paths.addAll(existing);
    modelTab.loaded = true;
    modelTab.repaintTable();
    setChanged();
  }

  private void populateCatalogConnections() {
    wCatalogConnection.removeAll();
    wCatalogConnection.add("");
    try {
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      for (String name :
          DvSourceCatalogService.listEnabledCatalogConnectionNames(metadataProvider)) {
        wCatalogConnection.add(name);
      }
    } catch (HopException e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "ResourceDefinitionGroupMetaEditor.Error.CatalogTitle"),
          e.getMessage(),
          e);
    }
  }

  private void validateSources() {
    ResourceDefinitionGroupMeta draft = new ResourceDefinitionGroupMeta();
    getWidgetsContent(draft);
    ResourceDefinitionValidationGuiSupport.validateAndShowResults(HopGui.getInstance(), draft);
  }

  private void tagCatalogVersion() {
    ResourceDefinitionGroupMeta draft = new ResourceDefinitionGroupMeta();
    getWidgetsContent(draft);
    CatalogVersionGuiSupport.tagVersionFromGroup(HopGui.getInstance(), draft);
  }

  private void listCatalogVersions() {
    ResourceDefinitionGroupMeta draft = new ResourceDefinitionGroupMeta();
    getWidgetsContent(draft);
    CatalogVersionGuiSupport.listVersionsForGroup(HopGui.getInstance(), draft);
  }

  private void browseLineage() {
    ResourceDefinitionGroupMeta draft = new ResourceDefinitionGroupMeta();
    getWidgetsContent(draft);
    HopGui hopGui = HopGui.getInstance();
    new org.apache.hop.datavault.hopgui.lineage.ReverseLineageBrowserDialog(
            hopGui.getShell(), hopGui, draft, hopGui.getVariables(), hopGui.getMetadataProvider())
        .open();
  }

  @Override
  public void setWidgetsContent() {
    ResourceDefinitionGroupMeta meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wCatalogConnection.setText(Const.NVL(meta.getDataCatalogConnection(), ""));
    wPreviewRowLimit.setText(String.valueOf(Math.max(1, meta.getPreviewRowLimit())));
    wDetailedChecking.setSelection(meta.isDetailedDataTypeChecking());

    if (dvTab != null) {
      dvTab.setPaths(meta.getDataVaultModelFiles());
    }
    if (bvTab != null) {
      bvTab.setPaths(meta.getBusinessVaultModelFiles());
    }
    if (dmTab != null) {
      dmTab.setPaths(meta.getDimensionalModelFiles());
    }

    // Materialize only the visible tab; others fill on first selection.
    ModelTab selected = selectedModelTab();
    if (selected != null) {
      selected.ensureLoaded();
    }
  }

  @Override
  public void getWidgetsContent(ResourceDefinitionGroupMeta meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    meta.setDataCatalogConnection(wCatalogConnection.getText());
    try {
      meta.setPreviewRowLimit(Integer.parseInt(wPreviewRowLimit.getText().trim()));
    } catch (NumberFormatException e) {
      meta.setPreviewRowLimit(10);
    }
    meta.setDetailedDataTypeChecking(wDetailedChecking.getSelection());

    if (dvTab != null) {
      dvTab.flushTableToList();
      meta.setDataVaultModelFiles(new ArrayList<>(dvTab.paths));
    }
    if (bvTab != null) {
      bvTab.flushTableToList();
      meta.setBusinessVaultModelFiles(new ArrayList<>(bvTab.paths));
    }
    if (dmTab != null) {
      dmTab.flushTableToList();
      meta.setDimensionalModelFiles(new ArrayList<>(dmTab.paths));
    }
  }

  /**
   * Bulk-fills a {@link TableView} without {@code optimizeTableView()} (full optWidth over all
   * rows). Uses a bounded sample for column width.
   */
  static void fillModelTableFast(TableView table, List<String> files) {
    if (table == null || table.isDisposed() || table.table == null || table.table.isDisposed()) {
      return;
    }
    table.table.setRedraw(false);
    try {
      table.table.removeAll();
      if (files != null) {
        for (String file : files) {
          if (!Utils.isEmpty(file)) {
            table.add(file);
          }
        }
      }
      if (table.table.getItemCount() == 0) {
        new TableItem(table.table, SWT.NONE);
      }
      table.removeEmptyRows();
      table.setRowNums();
      // Bound text measurement: sample first N rows only (nrLines > 0).
      table.optWidth(true, OPT_WIDTH_SAMPLE_ROWS);
    } finally {
      if (!table.table.isDisposed()) {
        table.table.setRedraw(true);
      }
    }
  }

  private static List<String> readModelTable(TableView table) {
    List<String> files = new ArrayList<>();
    if (table == null) {
      return files;
    }
    for (int i = 0; i < table.nrNonEmpty(); i++) {
      String file = table.getNonEmpty(i).getText(1);
      if (!Utils.isEmpty(file)) {
        files.add(file.trim());
      }
    }
    return files;
  }

  @Override
  public boolean setFocus() {
    if (wName == null || wName.isDisposed()) {
      return false;
    }
    return wName.setFocus();
  }

  /** One model-layer tab: authoritative path list + optional filtered TableView. */
  private final class ModelTab {
    private final String labelKey;
    private final String modelExtension;
    private final TableView table;
    private final Text wFilter;
    private final Label wlCount;
    private final List<String> paths = new ArrayList<>();
    private boolean loaded;
    private boolean suppressingFilter;

    private ModelTab(
        String labelKey, String modelExtension, TableView table, Text wFilter, Label wlCount) {
      this.labelKey = labelKey;
      this.modelExtension = modelExtension;
      this.table = table;
      this.wFilter = wFilter;
      this.wlCount = wlCount;
    }

    private void setPaths(List<String> source) {
      paths.clear();
      if (source != null) {
        for (String path : source) {
          if (!Utils.isEmpty(path)) {
            paths.add(path);
          }
        }
      }
      loaded = false;
      if (wFilter != null && !wFilter.isDisposed()) {
        suppressingFilter = true;
        try {
          wFilter.setText("");
        } finally {
          suppressingFilter = false;
        }
      }
      updateCountLabel();
    }

    private void ensureLoaded() {
      if (loaded || table == null || table.isDisposed()) {
        return;
      }
      repaintTable();
      loaded = true;
    }

    private void onFilterChanged() {
      if (suppressingFilter || table == null || table.isDisposed()) {
        return;
      }
      if (loaded) {
        flushTableToList();
      }
      loaded = true;
      repaintTable();
    }

    private void onTableModified() {
      setChanged();
    }

    private void flushTableToList() {
      if (!loaded || table == null || table.isDisposed()) {
        return;
      }
      String filter = currentFilter();
      List<String> tableContent = readModelTable(table);
      List<String> merged =
          ResourceDefinitionGroupModelListSupport.mergeFilteredTableEdit(
              paths, filter, tableContent);
      paths.clear();
      paths.addAll(merged);
    }

    private void repaintTable() {
      if (table == null || table.isDisposed()) {
        return;
      }
      String filter = currentFilter();
      List<String> visible = ResourceDefinitionGroupModelListSupport.filterPaths(paths, filter);
      fillModelTableFast(table, visible);
      updateCountLabel();
    }

    private String currentFilter() {
      if (wFilter == null || wFilter.isDisposed()) {
        return "";
      }
      return Const.NVL(wFilter.getText(), "").trim();
    }

    private void updateCountLabel() {
      if (wlCount == null || wlCount.isDisposed()) {
        return;
      }
      String filter = currentFilter();
      int total = paths.size();
      if (Utils.isEmpty(filter)) {
        wlCount.setText(
            BaseMessages.getString(
                PKG, "ResourceDefinitionGroupMetaEditor.ModelCount.Label", total));
      } else {
        int shown = ResourceDefinitionGroupModelListSupport.filterPaths(paths, filter).size();
        wlCount.setText(
            BaseMessages.getString(
                PKG, "ResourceDefinitionGroupMetaEditor.ModelCountFiltered.Label", shown, total));
      }
    }
  }
}
