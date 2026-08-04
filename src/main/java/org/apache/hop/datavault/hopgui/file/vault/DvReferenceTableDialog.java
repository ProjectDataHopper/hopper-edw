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
import java.util.Collections;
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
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvDataTypeSupport;
import org.apache.hop.datavault.metadata.DvIntegrationMode;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.DvReferenceLoadMode;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
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

/**
 * Dialog to edit a {@link DvReferenceTable}: natural keys, attributes, record sources, and load
 * mode.
 */
public class DvReferenceTableDialog {
  private static final Class<?> PKG = DvReferenceTableDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvReferenceTable input;
  private final DataVaultModel model;
  private final int originalTableIndex;
  private Shell shell;

  private CTabFolder wTabFolder;

  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Combo wIntegrationMode;
  private Combo wLoadMode;
  private TableView wNaturalKeys;
  private TableView wAttributes;
  private TableView wSources;
  private DvCustomPipelinesTabSupport customPipelinesTab;

  private boolean ok;

  private int margin;
  private int middle;

  public DvReferenceTableDialog(
      Shell parent, HopGui hopGui, DataVaultModel model, DvReferenceTable referenceTable) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.model = model;
    this.input = referenceTable;
    this.originalTableIndex = model != null ? model.getTables().indexOf(referenceTable) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Title", input.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    margin = PropsUi.getMargin();
    middle = PropsUi.getInstance().getMiddlePct();

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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_REFERENCE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Name.Label"));
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

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Description.Label"));
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
    addNaturalKeysTab();
    addAttributesTab();
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
    wOptionsTab.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Options.Label"));
    wOptionsTab.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Options.ToolTip"));
    Composite wOptionsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wOptionsComp);
    wOptionsComp.setLayout(new FormLayout());

    Label wlIntegrationMode = new Label(wOptionsComp, SWT.RIGHT);
    wlIntegrationMode.setText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.IntegrationMode.Label"));
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
        BaseMessages.getString(PKG, "DvReferenceTableDialog.IntegrationMode.ToolTip"));
    FormData fdIntegrationMode = new FormData();
    fdIntegrationMode.left = new FormAttachment(middle, 0);
    fdIntegrationMode.top = new FormAttachment(0, margin);
    fdIntegrationMode.right = new FormAttachment(100, 0);
    wIntegrationMode.setLayoutData(fdIntegrationMode);

    Label wlTableName = new Label(wOptionsComp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.TableName.Label"));
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

    Label wlLoadMode = new Label(wOptionsComp, SWT.RIGHT);
    wlLoadMode.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.LoadMode.Label"));
    PropsUi.setLook(wlLoadMode);
    FormData fdlLoadMode = new FormData();
    fdlLoadMode.left = new FormAttachment(0, 0);
    fdlLoadMode.top = new FormAttachment(wTableName, margin);
    fdlLoadMode.right = new FormAttachment(middle, -margin);
    wlLoadMode.setLayoutData(fdlLoadMode);

    wLoadMode = new Combo(wOptionsComp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wLoadMode);
    // MERGE is reserved; expose FULL_REPLACE and DELETE_INSERT in the UI for now.
    wLoadMode.setItems(
        new String[] {
          DvReferenceLoadMode.FULL_REPLACE.getDescription(),
          DvReferenceLoadMode.DELETE_INSERT.getDescription()
        });
    wLoadMode.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.LoadMode.ToolTip"));
    FormData fdLoadMode = new FormData();
    fdLoadMode.left = new FormAttachment(middle, 0);
    fdLoadMode.top = new FormAttachment(wlLoadMode, 0, SWT.CENTER);
    fdLoadMode.right = new FormAttachment(100, 0);
    wLoadMode.setLayoutData(fdLoadMode);

    wOptionsComp.layout();
    wOptionsTab.setControl(wOptionsComp);
  }

  private void addSourcesTab() {
    CTabItem wSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSourcesTab.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Sources.Label"));
    wSourcesTab.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Sources.ToolTip"));
    Composite wSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSourcesComp);
    wSourcesComp.setLayout(new FormLayout());

    Button wOpenInCatalog = new Button(wSourcesComp, SWT.PUSH);
    wOpenInCatalog.setText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.OpenInCatalog.Label"));
    wOpenInCatalog.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.OpenInCatalog.ToolTip"));
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
    wlSources.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.RecordSources.Label"));
    PropsUi.setLook(wlSources);
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.right = new FormAttachment(wOpenInCatalog, -margin);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    List<String> sources = new ArrayList<>();
    try {
      sources =
          DvSourceCatalogService.listSourceNames(model, variables, hopGui.getMetadataProvider());
    } catch (Exception e) {
      sources = new ArrayList<>();
    }
    Collections.sort(sources);
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.RecordSource.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              sources.toArray(new String[0])),
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

  private void addNaturalKeysTab() {
    CTabItem wKeysTab = new CTabItem(wTabFolder, SWT.NONE);
    wKeysTab.setFont(GuiResource.getInstance().getFontDefault());
    wKeysTab.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.NaturalKeys.Label"));
    wKeysTab.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.NaturalKeys.ToolTip"));
    Composite wKeysComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wKeysComp);
    wKeysComp.setLayout(new FormLayout());

    Label wlNaturalKeys = new Label(wKeysComp, SWT.LEFT);
    wlNaturalKeys.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.NaturalKeys.Label"));
    PropsUi.setLook(wlNaturalKeys);
    FormData fdlNaturalKeys = new FormData();
    fdlNaturalKeys.left = new FormAttachment(0, 0);
    fdlNaturalKeys.top = new FormAttachment(0, 0);
    wlNaturalKeys.setLayoutData(fdlNaturalKeys);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.NaturalKey.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.NaturalKey.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.NaturalKey.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.NaturalKey.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "DvReferenceTableDialog.NaturalKey.SourceFieldName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "DvReferenceTableDialog.NaturalKey.RecordSourceName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    Button wLoadFromSource = new Button(wKeysComp, SWT.PUSH);
    wLoadFromSource.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.Button"));
    wLoadFromSource.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.ToolTip"));
    PropsUi.setLook(wLoadFromSource);
    wLoadFromSource.setLayoutData(new FormDataBuilder().left().bottom().result());
    wLoadFromSource.addListener(SWT.Selection, e -> getKeys());

    int nrRows = input.getNaturalKeys() != null ? input.getNaturalKeys().size() : 1;
    wNaturalKeys =
        new TableView(
            variables,
            wKeysComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            null,
            PropsUi.getInstance());

    wNaturalKeys.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlNaturalKeys, margin)
            .right()
            .bottom(wLoadFromSource, -margin)
            .result());

    wKeysComp.layout();
    wKeysTab.setControl(wKeysComp);
  }

  private void addAttributesTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Attributes.Label"));
    tab.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.Tab.Attributes.ToolTip"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlAttributes = new Label(comp, SWT.LEFT);
    wlAttributes.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.Attributes.Label"));
    PropsUi.setLook(wlAttributes);
    wlAttributes.setLayoutData(new FormDataBuilder().left().top(0, margin).result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.Attribute.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.Attribute.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.Attribute.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.Attribute.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvReferenceTableDialog.Attribute.Precision.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "DvReferenceTableDialog.Attribute.IncludeInChangeDataCapture.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              BaseMessages.getString(PKG, "System.Combo.Yes"),
              BaseMessages.getString(PKG, "System.Combo.No")),
        };

    Button wLoadFromSource = new Button(comp, SWT.PUSH);
    wLoadFromSource.setText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.Button"));
    wLoadFromSource.setToolTipText(
        BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.ToolTip"));
    PropsUi.setLook(wLoadFromSource);
    wLoadFromSource.setLayoutData(new FormDataBuilder().left().bottom().result());
    wLoadFromSource.addListener(SWT.Selection, e -> getAttributes());

    int nrRows = input.getAttributes() != null ? input.getAttributes().size() : 1;
    wAttributes =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            null,
            PropsUi.getInstance());

    wAttributes.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlAttributes, margin)
            .right()
            .bottom(wLoadFromSource, -margin)
            .result());
  }

  private void addLineageTab() {
    TableLineage tableLineage = null;
    try {
      if (model != null) {
        LineageSnapshot snapshot = DvModelLineageCollector.collect(model, variables);
        tableLineage = LineageTabSupport.findTable(snapshot, input.getName());
      }
    } catch (Exception e) {
      // Keep dialog open; tab shows empty lineage message.
    }
    LineageTabSupport.addTab(wTabFolder, variables, margin, tableLineage);
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

    DvReferenceLoadMode loadMode =
        input.getLoadMode() != null ? input.getLoadMode() : DvReferenceLoadMode.FULL_REPLACE;
    if (loadMode == DvReferenceLoadMode.MERGE) {
      // Not yet offered in the combo; show DELETE_INSERT as the closest operational mode.
      wLoadMode.setText(DvReferenceLoadMode.DELETE_INSERT.getDescription());
    } else {
      wLoadMode.setText(loadMode.getDescription());
    }

    if (input.getNaturalKeys() != null) {
      for (int i = 0; i < input.getNaturalKeys().size(); i++) {
        BusinessKey key = input.getNaturalKeys().get(i);
        TableItem item = wNaturalKeys.table.getItem(i);
        item.setText(1, Const.NVL(key.getName(), ""));
        item.setText(2, Const.NVL(key.getDescription(), ""));
        item.setText(3, Const.NVL(key.getDataType(), ""));
        item.setText(4, Const.NVL(key.getLength(), ""));
        item.setText(5, Const.NVL(key.getSourceFieldName(), ""));
        item.setText(6, Const.NVL(key.getRecordSourceName(), ""));
      }
    }
    wNaturalKeys.optimizeTableView();

    if (input.getAttributes() != null) {
      for (int i = 0; i < input.getAttributes().size(); i++) {
        SatelliteAttribute attr = input.getAttributes().get(i);
        TableItem item = wAttributes.table.getItem(i);
        item.setText(1, Const.NVL(attr.getName(), ""));
        item.setText(2, Const.NVL(attr.getDescription(), ""));
        item.setText(3, Const.NVL(attr.getDataType(), ""));
        item.setText(4, Const.NVL(attr.getLength(), ""));
        item.setText(5, Const.NVL(attr.getPrecision(), ""));
        item.setText(6, attr.isIncludeInChangeDataCapture() ? "Y" : "N");
      }
    }
    wAttributes.optimizeTableView();

    if (input.getRecordSources() != null) {
      for (String recordSource : input.getRecordSources()) {
        TableItem item = new TableItem(wSources.table, SWT.NONE);
        item.setText(1, Const.NVL(recordSource, ""));
      }
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
      DataVaultModel draft =
          ModelDialogValidationSupport.cloneDataVaultModel(model, hopGui.getMetadataProvider());
      DvReferenceTable draftTable = locateDraftTable(draft);
      applyWidgetsToTable(draftTable);
      List<ICheckResult> remarks =
          draft.check(hopGui.getMetadataProvider(), variables, DvModelCheckOptions.defaults());
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

  private DvReferenceTable locateDraftTable(DataVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    IDvTable table = draft.getTables().get(originalTableIndex);
    if (!(table instanceof DvReferenceTable referenceTable)) {
      throw new HopException("Validation model table type mismatch");
    }
    return referenceTable;
  }

  private void applyWidgetsToTable(DvReferenceTable target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    target.setIntegrationMode(DvIntegrationMode.lookupDescription(wIntegrationMode.getText()));
    target.setLoadMode(DvReferenceLoadMode.lookupDescription(wLoadMode.getText()));

    List<BusinessKey> keys = new ArrayList<>();
    for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
      BusinessKey key = new BusinessKey();
      key.setName(item.getText(1));
      key.setDescription(item.getText(2));
      key.setDataType(item.getText(3));
      key.setLength(item.getText(4));
      key.setSourceFieldName(item.getText(5));
      key.setRecordSourceName(item.getText(6));
      keys.add(key);
    }
    target.setNaturalKeys(keys);

    List<SatelliteAttribute> attrs = new ArrayList<>();
    for (TableItem item : wAttributes.getNonEmptyItems()) {
      SatelliteAttribute attr = new SatelliteAttribute();
      attr.setName(item.getText(1));
      attr.setDescription(item.getText(2));
      attr.setDataType(item.getText(3));
      attr.setLength(item.getText(4));
      attr.setPrecision(item.getText(5));
      attr.setIncludeInChangeDataCapture("Y".equalsIgnoreCase(item.getText(6)));
      attrs.add(attr);
    }
    target.setAttributes(attrs);

    List<String> recordSources = new ArrayList<>();
    for (TableItem item : wSources.getNonEmptyItems()) {
      String source = item.getText(1);
      if (!Utils.isEmpty(source)) {
        recordSources.add(source);
      }
    }
    target.setRecordSources(recordSources);
    customPipelinesTab.applyTo(target);
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void getKeys() {
    List<TableItem> sourceItems = wSources.getNonEmptyItems();
    if (sourceItems.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.NoSource.Title"));
      mb.open();
      return;
    }

    Set<String> sourcesInKeys = new HashSet<>();
    for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
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
      mb.setMessage(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.AllSourcesMapped.Message"));
      mb.setText(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.AllSourcesMapped.Title"));
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
      wNaturalKeys.optimizeTableView();
    }
  }

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
          BaseMessages.getString(
              PKG, "DvReferenceTableDialog.GetKeys.ErrorLoadingSource.Message", sourceName),
          e);
      return 0;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvReferenceTableDialog.GetKeys.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.NoFields.Title"));
      mb.open();
      return 0;
    }

    List<SourceField> primaryKeyFields =
        SourceFieldPrimaryKeySupport.primaryKeyFields(sourceFields);
    if (!primaryKeyFields.isEmpty()) {
      for (SourceField sf : primaryKeyFields) {
        TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
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
    for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
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
            BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.Title", sourceName),
            BaseMessages.getString(PKG, "DvReferenceTableDialog.GetKeys.Message", sourceName));
    dialog.setMulti(true);
    dialog.setSelectedNrs(selectedIndexes);
    String result = dialog.open();
    if (result == null) {
      return -1;
    }

    int[] indices = dialog.getSelectionIndeces();
    for (int idx : indices) {
      SourceField sf = sourceFields.get(idx);
      TableItem item = new TableItem(wNaturalKeys.table, SWT.NONE);
      item.setText(1, Const.NVL(sf.getName(), ""));
      item.setText(2, Const.NVL(sf.getDescription(), ""));
      item.setText(3, Const.NVL(DvDataTypeSupport.preferredDataTypeLabel(sf), ""));
      item.setText(4, Const.NVL(sf.getLength(), ""));
      item.setText(5, Const.NVL(sf.getName(), ""));
      item.setText(6, Const.NVL(sourceName, ""));
    }
    return indices.length;
  }

  private void getAttributes() {
    List<TableItem> sourceItems = wSources.getNonEmptyItems();
    if (sourceItems.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.NoSource.Message"));
      mb.setText(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.NoSource.Title"));
      mb.open();
      return;
    }

    String sourceName = sourceItems.get(0).getText(1);
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
          BaseMessages.getString(
              PKG, "DvReferenceTableDialog.GetAttributes.ErrorLoadingSource.Message", sourceName),
          e);
      return;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvReferenceTableDialog.GetAttributes.NoFields.Message", sourceName));
      mb.setText(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.NoFields.Title"));
      mb.open();
      return;
    }

    Set<String> naturalKeySourceFields = new HashSet<>();
    for (TableItem item : wNaturalKeys.getNonEmptyItems()) {
      String sourceField = item.getText(5);
      if (Utils.isEmpty(sourceField)) {
        sourceField = item.getText(1);
      }
      if (!Utils.isEmpty(sourceField)) {
        naturalKeySourceFields.add(sourceField);
      }
    }

    String[] choices =
        sourceFields.stream()
            .map(SourceField::getName)
            .filter(n -> !Utils.isEmpty(n) && !naturalKeySourceFields.contains(n))
            .toArray(String[]::new);
    if (choices.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvReferenceTableDialog.GetAttributes.NoFields.Message", sourceName));
      mb.setText(
          BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.NoFields.Title"));
      mb.open();
      return;
    }

    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            choices,
            BaseMessages.getString(PKG, "DvReferenceTableDialog.GetAttributes.Title"),
            BaseMessages.getString(
                PKG, "DvReferenceTableDialog.GetAttributes.Message", sourceName));
    dialog.setMulti(true);
    String result = dialog.open();
    if (result == null) {
      return;
    }

    Set<String> selected = new HashSet<>();
    int[] indices = dialog.getSelectionIndeces();
    for (int idx : indices) {
      if (idx >= 0 && idx < choices.length) {
        selected.add(choices[idx]);
      }
    }

    for (SourceField sf : sourceFields) {
      if (!selected.contains(sf.getName())) {
        continue;
      }
      TableItem item = new TableItem(wAttributes.table, SWT.NONE);
      item.setText(1, Const.NVL(sf.getName(), ""));
      item.setText(2, Const.NVL(sf.getDescription(), ""));
      item.setText(3, Const.NVL(DvDataTypeSupport.preferredDataTypeLabel(sf), ""));
      item.setText(4, Const.NVL(sf.getLength(), ""));
      item.setText(5, Const.NVL(sf.getPrecision(), ""));
      item.setText(6, "Y");
    }
    wAttributes.optimizeTableView();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
