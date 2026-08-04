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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.hopgui.lineage.LineageTabSupport;
import org.apache.hop.datavault.lineage.DvModelLineageCollector;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvDataTypeSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvIntegrationMode;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldPrimaryKeySupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit the properties of a DvHub, including business keys list using TableView. */
public class DvHubDialog {
  private static final Class<?> PKG = DvHubDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvHub input;
  private final DataVaultModel model;
  private final int originalTableIndex;
  private Shell shell;

  private CTabFolder wTabFolder;

  // Widgets
  private Text wName;
  private Text wTableName;
  private Text wDescription;

  private Combo wIntegrationMode;
  private Text wHashKeyFieldName;
  private Text wRecordSourceFieldName;
  private TableView wBusinessKeys;
  private TableView wSources;
  private DvCustomPipelinesTabSupport customPipelinesTab;

  private boolean ok;

  private int margin;
  private int middle;

  public DvHubDialog(Shell parent, HopGui hopGui, DataVaultModel model, DvHub hub) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.model = model;
    this.input = hub;
    this.originalTableIndex = model != null ? model.getTables().indexOf(hub) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvHubDialog.Title", input.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at the bottom (using standard positioning)
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_HUB);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    // Name
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvHubDialog.Name.Label"));
    PropsUi.setLook(wlName);
    FormData fdlName = new FormData();
    fdlName.left = new FormAttachment(0, 0);
    fdlName.top = new FormAttachment(0, margin);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(middle, 0);
    fdName.top = new FormAttachment(0, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    // Description
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvHubDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wDescription, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    customPipelinesTab = new DvCustomPipelinesTabSupport(shell, hopGui, variables, margin);
    addOptionsTab();
    addSourcesTab();
    addKeysTab();
    customPipelinesTab.addTab(wTabFolder);
    addLineageTab();

    wTabFolder.setSelection(0);

    getData();
    customPipelinesTab.bindIntegrationMode(wIntegrationMode);

    BaseTransformDialog.setSize(shell, 700, 550);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void addOptionsTab() {
    CTabItem wOptionsTab = new CTabItem(wTabFolder, SWT.NONE);
    wOptionsTab.setFont(GuiResource.getInstance().getFontDefault());
    wOptionsTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Options.Label"));
    wOptionsTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Options.ToolTip"));
    Composite wOptionsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wOptionsComp);
    wOptionsComp.setLayout(new FormLayout());

    Label wlIntegrationMode = new Label(wOptionsComp, SWT.RIGHT);
    wlIntegrationMode.setText(BaseMessages.getString(PKG, "DvHubDialog.IntegrationMode.Label"));
    PropsUi.setLook(wlIntegrationMode);
    FormData fdlIntegrationMode = new FormData();
    fdlIntegrationMode.left = new FormAttachment(0, 0);
    fdlIntegrationMode.top = new FormAttachment(0, margin);
    fdlIntegrationMode.right = new FormAttachment(middle, -margin);
    wlIntegrationMode.setLayoutData(fdlIntegrationMode);

    wIntegrationMode = new Combo(wOptionsComp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wIntegrationMode);
    wIntegrationMode.setItems(DvIntegrationMode.getDescriptions());
    wIntegrationMode.setToolTipText(
        BaseMessages.getString(PKG, "DvHubDialog.IntegrationMode.ToolTip"));
    FormData fdIntegrationMode = new FormData();
    fdIntegrationMode.left = new FormAttachment(middle, 0);
    fdIntegrationMode.top = new FormAttachment(0, margin);
    fdIntegrationMode.right = new FormAttachment(100, 0);
    wIntegrationMode.setLayoutData(fdIntegrationMode);

    // Table name (physical)
    Label wlTableName = new Label(wOptionsComp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvHubDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wIntegrationMode, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wlTableName, 0, SWT.CENTER);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);

    // Hash key field name (per-hub, replaces global suffix from DataVaultConfiguration)
    Label wlHashKeyFieldName = new Label(wOptionsComp, SWT.RIGHT);
    wlHashKeyFieldName.setText(BaseMessages.getString(PKG, "DvHubDialog.HashKeyFieldName.Label"));
    PropsUi.setLook(wlHashKeyFieldName);
    FormData fdlHashKeyFieldName = new FormData();
    fdlHashKeyFieldName.left = new FormAttachment(0, 0);
    fdlHashKeyFieldName.top = new FormAttachment(wTableName, margin);
    fdlHashKeyFieldName.right = new FormAttachment(middle, -margin);
    wlHashKeyFieldName.setLayoutData(fdlHashKeyFieldName);

    wHashKeyFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashKeyFieldName);
    FormData fdHashKeyFieldName = new FormData();
    fdHashKeyFieldName.left = new FormAttachment(middle, 0);
    fdHashKeyFieldName.top = new FormAttachment(wlHashKeyFieldName, 0, SWT.CENTER);
    fdHashKeyFieldName.right = new FormAttachment(100, 0);
    wHashKeyFieldName.setLayoutData(fdHashKeyFieldName);

    // Record source field name (per-hub override of the one in DataVaultConfiguration)
    Label wlRecordSourceFieldName = new Label(wOptionsComp, SWT.RIGHT);
    wlRecordSourceFieldName.setText(
        BaseMessages.getString(PKG, "DvHubDialog.RecordSourceFieldName.Label"));
    PropsUi.setLook(wlRecordSourceFieldName);
    FormData fdlRecordSourceFieldName = new FormData();
    fdlRecordSourceFieldName.left = new FormAttachment(0, 0);
    fdlRecordSourceFieldName.top = new FormAttachment(wHashKeyFieldName, margin);
    fdlRecordSourceFieldName.right = new FormAttachment(middle, -margin);
    wlRecordSourceFieldName.setLayoutData(fdlRecordSourceFieldName);

    wRecordSourceFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRecordSourceFieldName);
    FormData fdRecordSourceFieldName = new FormData();
    fdRecordSourceFieldName.left = new FormAttachment(middle, 0);
    fdRecordSourceFieldName.top = new FormAttachment(wlRecordSourceFieldName, 0, SWT.CENTER);
    fdRecordSourceFieldName.right = new FormAttachment(100, 0);
    wRecordSourceFieldName.setLayoutData(fdRecordSourceFieldName);

    wOptionsComp.layout();
    wOptionsTab.setControl(wOptionsComp);
  }

  private void addKeysTab() {
    CTabItem wKeysTab = new CTabItem(wTabFolder, SWT.NONE);
    wKeysTab.setFont(GuiResource.getInstance().getFontDefault());
    wKeysTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Keys.Label"));
    wKeysTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Keys.ToolTip"));
    Composite wKeysComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wKeysComp);
    wKeysComp.setLayout(new FormLayout());

    // Business keys - use TableView
    Label wlBusinessKeys = new Label(wKeysComp, SWT.LEFT);
    wlBusinessKeys.setText(BaseMessages.getString(PKG, "DvHubDialog.BusinessKeys.Label"));
    PropsUi.setLook(wlBusinessKeys);
    FormData fdlBusinessKeys = new FormData();
    fdlBusinessKeys.left = new FormAttachment(0, 0);
    fdlBusinessKeys.top = new FormAttachment(0, 0);
    wlBusinessKeys.setLayoutData(fdlBusinessKeys);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.SourceFieldName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvHubDialog.BusinessKey.SourceSystem.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    Button wLoadFromSource = new Button(wKeysComp, SWT.PUSH);
    wLoadFromSource.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Button"));
    wLoadFromSource.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.ToolTip"));
    PropsUi.setLook(wLoadFromSource);
    wLoadFromSource.setLayoutData(new FormDataBuilder().left().bottom().result());
    wLoadFromSource.addListener(SWT.Selection, e -> getKeys());

    int nrRows = input.getBusinessKeys() != null ? input.getBusinessKeys().size() : 1;
    wBusinessKeys =
        new TableView(
            variables,
            wKeysComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            null,
            PropsUi.getInstance());

    wBusinessKeys.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlBusinessKeys, margin)
            .right()
            .bottom(wLoadFromSource, -margin)
            .result());

    wKeysComp.layout();
    wKeysTab.setControl(wKeysComp);
  }

  private void addLineageTab() {
    LineageTabSupport.addLazyTab(
        wTabFolder,
        variables,
        margin,
        () -> {
          if (model == null) {
            return null;
          }
          return LineageTabSupport.findTable(
              DvModelLineageCollector.collect(model, variables), input.getName());
        });
  }

  private void addSourcesTab() {
    CTabItem wSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSourcesTab.setText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Sources.Label"));
    wSourcesTab.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.Tab.Sources.ToolTip"));
    Composite wSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSourcesComp);
    wSourcesComp.setLayout(new FormLayout());

    // Multi-record sources section for Hubs (supports multiple sources feeding one Hub table)
    Button wOpenInCatalog = new Button(wSourcesComp, SWT.PUSH);
    wOpenInCatalog.setText(BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.Label"));
    wOpenInCatalog.setToolTipText(BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.ToolTip"));
    PropsUi.setLook(wOpenInCatalog);
    FormData fdOpenInCatalog = new FormData();
    fdOpenInCatalog.right = new FormAttachment(100, 0);
    fdOpenInCatalog.top = new FormAttachment(0, 0);
    wOpenInCatalog.setLayoutData(fdOpenInCatalog);
    wOpenInCatalog.addListener(
        SWT.Selection,
        e ->
            RecordSourceCatalogNavigationSupport.openSelectedSourceInCatalog(
                shell, hopGui, variables, hopGui.getMetadataProvider(), model, wSources));

    Label wlSources = new Label(wSourcesComp, SWT.LEFT);
    wlSources.setText(BaseMessages.getString(PKG, "DvHubDialog.RecordSources.Label"));
    PropsUi.setLook(wlSources);
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.right = new FormAttachment(wOpenInCatalog, -margin);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          DvSourceComboSupport.createLazySourceColumn(
              "Data Vault Source", shell, model, variables, hopGui.getMetadataProvider()),
        };
    wSources =
        new TableView(
            hopGui.getVariables(),
            wSourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdSources = new FormData();
    fdSources.left = new FormAttachment(0, 0);
    fdSources.top = new FormAttachment(wOpenInCatalog, margin);
    fdSources.right = new FormAttachment(100, 0);
    fdSources.bottom = new FormAttachment(100, 0);
    wSources.setLayoutData(fdSources);

    wSourcesComp.layout();
    wSourcesTab.setControl(wSourcesComp);
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    DvIntegrationMode integrationMode =
        input.getIntegrationMode() != null
            ? input.getIntegrationMode()
            : DvIntegrationMode.HOP_MANAGED;
    wIntegrationMode.setText(integrationMode.getDescription());
    wHashKeyFieldName.setText(Const.NVL(input.getHashKeyFieldName(), ""));
    wRecordSourceFieldName.setText(Const.NVL(input.getRecordSourceFieldName(), ""));

    // The business keys
    //
    for (int i = 0; i < input.getBusinessKeys().size(); i++) {
      BusinessKey bk = input.getBusinessKeys().get(i);
      TableItem item = wBusinessKeys.table.getItem(i);
      item.setText(1, Const.NVL(bk.getName(), ""));
      item.setText(2, Const.NVL(bk.getDescription(), ""));
      item.setText(3, Const.NVL(bk.getDataType(), ""));
      item.setText(4, Const.NVL(bk.getLength(), ""));
      item.setText(5, Const.NVL(bk.getSourceFieldName(), ""));
      item.setText(6, Const.NVL(bk.getRecordSourceName(), ""));
    }
    wBusinessKeys.optimizeTableView();

    // The record sources
    //
    for (String recordSource : input.getRecordSources()) {
      TableItem item = new TableItem(wSources.table, SWT.NONE);
      item.setText(1, Const.NVL(recordSource, ""));
    }
    wSources.optimizeTableView();
    customPipelinesTab.loadFrom(input);
  }

  private void ok() {
    applyWidgetsToTable(input);
    input.setChanged();
    ok = true;
    dispose();
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                DataVaultModel draft =
                    ModelDialogValidationSupport.cloneDataVaultModel(
                        model, hopGui.getMetadataProvider());
                DvHub draftTable = locateDraftTable(draft);
                applyWidgetsToTable(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draftTable.check(
                    tableRemarks,
                    hopGui.getMetadataProvider(),
                    variables,
                    DvModelCheckOptions.defaults(),
                    draft);
                return tableRemarks;
              });
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"),
          BaseMessages.getString(
              ModelDialogValidationSupport.class,
              "ModelTableDialog.Validate.Error",
              ex.getMessage()),
          ex);
    }
  }

  private DvHub locateDraftTable(DataVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    IDvTable table = draft.getTables().get(originalTableIndex);
    if (!(table instanceof DvHub hub)) {
      throw new HopException("Validation model table type mismatch");
    }
    return hub;
  }

  private void applyWidgetsToTable(DvHub target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    target.setIntegrationMode(DvIntegrationMode.lookupDescription(wIntegrationMode.getText()));
    target.setHashKeyFieldName(wHashKeyFieldName.getText());
    target.setRecordSourceFieldName(wRecordSourceFieldName.getText());

    List<BusinessKey> keys = new ArrayList<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      BusinessKey bk = new BusinessKey();
      bk.setName(item.getText(1));
      bk.setDescription(item.getText(2));
      bk.setDataType(item.getText(3));
      bk.setLength(item.getText(4));
      bk.setSourceFieldName(item.getText(5));
      bk.setRecordSourceName(item.getText(6));
      keys.add(bk);
    }
    target.setBusinessKeys(keys);

    target.getRecordSources().clear();
    for (TableItem item : wSources.getNonEmptyItems()) {
      target.getRecordSources().add(item.getText(1));
    }
    customPipelinesTab.applyTo(target);
  }

  private void cancel() {
    // restore backup? simple, no for now
    ok = false;
    dispose();
  }

  private void getKeys() {
    List<TableItem> sourceItems = wSources.getNonEmptyItems();
    if (sourceItems.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoSource.Title"));
      mb.open();
      return;
    }

    Set<String> sourcesInKeys = new HashSet<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      String sourceSystem = item.getText(6);
      if (!Utils.isEmpty(sourceSystem)) {
        sourcesInKeys.add(sourceSystem);
      }
    }

    List<String> missingSources = new ArrayList<>();
    for (TableItem sourceItem : sourceItems) {
      String sourceName = sourceItem.getText(1);
      if (!Utils.isEmpty(sourceName) && !sourcesInKeys.contains(sourceName)) {
        missingSources.add(sourceName);
      }
    }

    if (missingSources.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.AllSourcesMapped.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.AllSourcesMapped.Title"));
      mb.open();
      return;
    }

    boolean changed = false;
    for (String sourceName : missingSources) {
      int added = importKeysFromSource(sourceName);
      if (added < 0) {
        break;
      }
      if (added > 0) {
        changed = true;
      }
    }

    if (changed) {
      wBusinessKeys.optimizeTableView();
    }
  }

  /**
   * Prompts for business key columns from the given record source and appends selected rows.
   *
   * @return the number of keys added, or {@code -1} if the user cancelled
   */
  private int importKeysFromSource(String sourceName) {
    DataVaultSource source = null;
    List<SourceField> sourceFields = null;

    try {
      source =
          DvSourceCatalogService.resolveSource(
              sourceName, model, variables, hopGui.getMetadataProvider());
      if (source != null) {
        sourceFields = source.getFields(hopGui.getMetadataProvider());
      }
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
          BaseMessages.getString(PKG, "DvHubDialog.GetKeys.ErrorLoadingSource.Message", sourceName),
          e);
      return 0;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "DvHubDialog.GetKeys.NoFields.Title"));
      mb.open();
      return 0;
    }

    List<SourceField> primaryKeyFields =
        SourceFieldPrimaryKeySupport.primaryKeyFields(sourceFields);
    if (!primaryKeyFields.isEmpty()) {
      for (SourceField sf : primaryKeyFields) {
        TableItem item = new TableItem(wBusinessKeys.table, SWT.NONE);
        item.setText(1, Const.NVL(sf.getName(), ""));
        item.setText(2, Const.NVL(sf.getDescription(), ""));
        item.setText(3, Const.NVL(DvDataTypeSupport.preferredDataTypeLabel(sf), ""));
        item.setText(4, Const.NVL(sf.getLength(), ""));
        item.setText(5, Const.NVL(sf.getName(), ""));
        item.setText(6, Const.NVL(sourceName, ""));
      }
      return primaryKeyFields.size();
    }

    Set<String> preselectedSourceFields = new HashSet<>();
    for (TableItem item : wBusinessKeys.getNonEmptyItems()) {
      if (!sourceName.equals(item.getText(6))) {
        continue;
      }
      String sourceFieldName = item.getText(5);
      if (!Utils.isEmpty(sourceFieldName)) {
        preselectedSourceFields.add(sourceFieldName);
      }
    }

    String[] choices = new String[sourceFields.size()];
    List<Integer> selectedIndexes = new ArrayList<>();
    for (int i = 0; i < sourceFields.size(); i++) {
      SourceField sf = sourceFields.get(i);
      choices[i] = sf.getName();
      if (preselectedSourceFields.contains(sf.getName())) {
        selectedIndexes.add(i);
      }
    }

    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            choices,
            BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Title", sourceName),
            BaseMessages.getString(PKG, "DvHubDialog.GetKeys.Message", sourceName));
    dialog.setMulti(true);
    dialog.setSelectedNrs(selectedIndexes);
    String result = dialog.open();
    if (result == null) {
      return -1;
    }

    int[] indices = dialog.getSelectionIndeces();
    for (int idx : indices) {
      SourceField sf = sourceFields.get(idx);
      TableItem item = new TableItem(wBusinessKeys.table, SWT.NONE);
      item.setText(1, Const.NVL(sf.getName(), ""));
      item.setText(2, Const.NVL(sf.getDescription(), ""));
      item.setText(3, Const.NVL(DvDataTypeSupport.preferredDataTypeLabel(sf), ""));
      item.setText(4, Const.NVL(sf.getLength(), ""));
      item.setText(5, Const.NVL(sf.getName(), ""));
      item.setText(6, Const.NVL(sourceName, ""));
    }
    return indices.length;
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
