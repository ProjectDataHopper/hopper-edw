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
import java.util.List;
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
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DependentChildKey;
import org.apache.hop.datavault.metadata.DvIntegrationMode;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the properties of a DvLink using a TabFolder. Name and description are placed at
 * the top, buttons at the bottom. Tabs: Options (hash key field, record source field,
 * hasDescriptiveAttributes, participating hubs and link satellites), Driving keys, Dependent child
 * keys (transactional links), Hub sources (DvLinkHubSourceDialog), Satellite sources
 * (DvLinkSatelliteSourceDialog).
 */
public class DvLinkDialog {
  private static final Class<?> PKG = DvLinkDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink input;
  private final DataVaultModel model;
  private final int originalTableIndex;
  private Shell shell;

  private CTabFolder wTabFolder;

  // Widgets (top level name/desc + per tab)
  private Text wName;
  private Text wDescription;

  // Options tab
  private Combo wIntegrationMode;
  private Text wTableName;
  private Text wLinkHashKeyFieldName;
  private Text wRecordSourceFieldName;
  private Button wHasDescriptiveAttributes;
  private TableView wHubNames;
  private TableView wLinkSatelliteNames;

  // Driving keys tab
  private TableView wDrivingKeyNames;

  // Dependent child keys tab (transactional links)
  private TableView wDependentChildKeys;

  // Hub sources tab
  private TableView wLinkHubSources;
  private List<DvLink.DvLinkHubSource> currentLinkHubSources = new ArrayList<>();

  // Satellite sources tab
  private TableView wLinkSatelliteSources;
  private List<DvLink.DvLinkSatelliteSource> currentLinkSatelliteSources = new ArrayList<>();
  private DvCustomPipelinesTabSupport customPipelinesTab;

  private boolean ok;

  public DvLinkDialog(Shell parent, HopGui hopGui, DvLink link, DataVaultModel model) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = link;
    this.model = model;
    this.originalTableIndex = model != null ? model.getTables().indexOf(link) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvLinkDialog.Title", input.getName()));

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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_LINK);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    // Name at top (outside tabs)
    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvLinkDialog.Name.Label"));
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

    // Description right under name
    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvLinkDialog.Description.Label"));
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
    fdDescription.top = new FormAttachment(wName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    // TabFolder between description and buttons
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
    addDrivingKeysTab();
    addDependentChildKeysTab();
    addHubSourcesTab();
    addSatelliteSourcesTab();
    customPipelinesTab.addTab(wTabFolder);
    addLineageTab();

    wTabFolder.setSelection(0);

    getData();
    customPipelinesTab.bindIntegrationMode(wIntegrationMode);

    BaseTransformDialog.setSize(shell, 700, 550);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private int margin;
  private int middle;

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

  private void addOptionsTab() {
    CTabItem wOptionsTab = new CTabItem(wTabFolder, SWT.NONE);
    wOptionsTab.setFont(GuiResource.getInstance().getFontDefault());
    wOptionsTab.setText("Options");
    wOptionsTab.setToolTipText(
        "General options for this link (hash key, record source field, descriptive attributes, participating hubs)");
    Composite wOptionsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wOptionsComp);
    wOptionsComp.setLayout(new FormLayout());

    Label wlIntegrationMode = new Label(wOptionsComp, SWT.RIGHT);
    wlIntegrationMode.setText(BaseMessages.getString(PKG, "DvLinkDialog.IntegrationMode.Label"));
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
        BaseMessages.getString(PKG, "DvLinkDialog.IntegrationMode.ToolTip"));
    FormData fdIntegrationMode = new FormData();
    fdIntegrationMode.left = new FormAttachment(middle, 0);
    fdIntegrationMode.top = new FormAttachment(0, margin);
    fdIntegrationMode.right = new FormAttachment(100, 0);
    wIntegrationMode.setLayoutData(fdIntegrationMode);

    // Table name (physical) inside options like hub dialog
    Label wlTableName = new Label(wOptionsComp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvLinkDialog.TableName.Label"));
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

    // Link hash key field name
    Label wlLinkHashKey = new Label(wOptionsComp, SWT.RIGHT);
    wlLinkHashKey.setText(BaseMessages.getString(PKG, "DvLinkDialog.LinkHashKeyFieldName.Label"));
    PropsUi.setLook(wlLinkHashKey);
    FormData fdlLinkHashKey = new FormData();
    fdlLinkHashKey.left = new FormAttachment(0, 0);
    fdlLinkHashKey.top = new FormAttachment(wTableName, margin);
    fdlLinkHashKey.right = new FormAttachment(middle, -margin);
    wlLinkHashKey.setLayoutData(fdlLinkHashKey);

    wLinkHashKeyFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLinkHashKeyFieldName);
    FormData fdLinkHashKey = new FormData();
    fdLinkHashKey.left = new FormAttachment(middle, 0);
    fdLinkHashKey.top = new FormAttachment(wTableName, margin);
    fdLinkHashKey.right = new FormAttachment(100, 0);
    wLinkHashKeyFieldName.setLayoutData(fdLinkHashKey);

    // Record source field name (the per-link override field name)
    Label wlRecordSourceField = new Label(wOptionsComp, SWT.RIGHT);
    wlRecordSourceField.setText("Record source field name");
    PropsUi.setLook(wlRecordSourceField);
    FormData fdlRecordSourceField = new FormData();
    fdlRecordSourceField.left = new FormAttachment(0, 0);
    fdlRecordSourceField.top = new FormAttachment(wLinkHashKeyFieldName, margin);
    fdlRecordSourceField.right = new FormAttachment(middle, -margin);
    wlRecordSourceField.setLayoutData(fdlRecordSourceField);

    wRecordSourceFieldName = new Text(wOptionsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRecordSourceFieldName);
    FormData fdRecordSourceField = new FormData();
    fdRecordSourceField.left = new FormAttachment(middle, 0);
    fdRecordSourceField.top = new FormAttachment(wLinkHashKeyFieldName, margin);
    fdRecordSourceField.right = new FormAttachment(100, 0);
    wRecordSourceFieldName.setLayoutData(fdRecordSourceField);

    // Has descriptive attributes checkbox
    Label wlHasDescriptive = new Label(wOptionsComp, SWT.RIGHT);
    wlHasDescriptive.setText(
        BaseMessages.getString(PKG, "DvLinkDialog.HasDescriptiveAttributes.Label"));
    PropsUi.setLook(wlHasDescriptive);
    FormData fdlHasDescriptive = new FormData();
    fdlHasDescriptive.left = new FormAttachment(0, 0);
    fdlHasDescriptive.top = new FormAttachment(wRecordSourceFieldName, margin);
    fdlHasDescriptive.right = new FormAttachment(middle, -margin);
    wlHasDescriptive.setLayoutData(fdlHasDescriptive);

    wHasDescriptiveAttributes = new Button(wOptionsComp, SWT.CHECK);
    PropsUi.setLook(wHasDescriptiveAttributes);
    FormData fdHasDescriptive = new FormData();
    fdHasDescriptive.left = new FormAttachment(middle, 0);
    fdHasDescriptive.top = new FormAttachment(wlHasDescriptive, 0, SWT.CENTER);
    wHasDescriptiveAttributes.setLayoutData(fdHasDescriptive);

    // Participating hubs (single column table) inside options
    Label wlHubNames = new Label(wOptionsComp, SWT.LEFT);
    wlHubNames.setText(BaseMessages.getString(PKG, "DvLinkDialog.HubNames.Label"));
    PropsUi.setLook(wlHubNames);
    FormData fdlHubNames = new FormData();
    fdlHubNames.left = new FormAttachment(0, 0);
    fdlHubNames.top = new FormAttachment(wHasDescriptiveAttributes, margin);
    wlHubNames.setLayoutData(fdlHubNames);

    List<String> hubNames = getModelHubNames();
    ColumnInfo[] hubColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.HubName.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              hubNames.toArray(new String[0])),
        };

    int nrHubRows =
        (input.getHubNames() != null && !input.getHubNames().isEmpty())
            ? input.getHubNames().size()
            : 2;
    wHubNames =
        new TableView(
            variables,
            wOptionsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            hubColumns,
            nrHubRows,
            null,
            PropsUi.getInstance());

    FormData fdHubNames = new FormData();
    fdHubNames.left = new FormAttachment(0, 0);
    fdHubNames.top = new FormAttachment(wlHubNames, margin);
    fdHubNames.right = new FormAttachment(100, 0);
    fdHubNames.bottom = new FormAttachment(50, -margin);
    wHubNames.setLayoutData(fdHubNames);

    Label wlLinkSatelliteNames = new Label(wOptionsComp, SWT.LEFT);
    wlLinkSatelliteNames.setText(
        BaseMessages.getString(PKG, "DvLinkDialog.LinkSatelliteNames.Label"));
    PropsUi.setLook(wlLinkSatelliteNames);
    FormData fdlLinkSatelliteNames = new FormData();
    fdlLinkSatelliteNames.left = new FormAttachment(0, 0);
    fdlLinkSatelliteNames.top = new FormAttachment(wHubNames, margin);
    wlLinkSatelliteNames.setLayoutData(fdlLinkSatelliteNames);

    List<String> linkSatelliteNames = getModelLinkSatelliteNames();
    ColumnInfo[] linkSatColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.LinkSatelliteName.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              linkSatelliteNames.toArray(new String[0])),
        };

    int nrLinkSatRows =
        (input.getLinkSatelliteNames() != null && !input.getLinkSatelliteNames().isEmpty())
            ? input.getLinkSatelliteNames().size()
            : 2;
    wLinkSatelliteNames =
        new TableView(
            variables,
            wOptionsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            linkSatColumns,
            nrLinkSatRows,
            null,
            PropsUi.getInstance());

    FormData fdLinkSatelliteNames = new FormData();
    fdLinkSatelliteNames.left = new FormAttachment(0, 0);
    fdLinkSatelliteNames.top = new FormAttachment(wlLinkSatelliteNames, margin);
    fdLinkSatelliteNames.right = new FormAttachment(100, 0);
    fdLinkSatelliteNames.bottom = new FormAttachment(100, 0);
    wLinkSatelliteNames.setLayoutData(fdLinkSatelliteNames);

    wOptionsComp.layout();
    wOptionsTab.setControl(wOptionsComp);
  }

  private void addDrivingKeysTab() {
    CTabItem wKeysTab = new CTabItem(wTabFolder, SWT.NONE);
    wKeysTab.setFont(GuiResource.getInstance().getFontDefault());
    wKeysTab.setText("Driving keys");
    wKeysTab.setToolTipText(
        "Driving keys (when the same hub participates multiple times under different roles)");
    Composite wKeysComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wKeysComp);
    wKeysComp.setLayout(new FormLayout());

    Label wlDrivingKeys = new Label(wKeysComp, SWT.LEFT);
    wlDrivingKeys.setText(BaseMessages.getString(PKG, "DvLinkDialog.DrivingKeyNames.Label"));
    PropsUi.setLook(wlDrivingKeys);
    FormData fdlDrivingKeys = new FormData();
    fdlDrivingKeys.left = new FormAttachment(0, 0);
    fdlDrivingKeys.top = new FormAttachment(0, 0);
    wlDrivingKeys.setLayoutData(fdlDrivingKeys);

    ColumnInfo[] drivingColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DrivingKeyName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrDrivingRows =
        (input.getDrivingKeyNames() != null && !input.getDrivingKeyNames().isEmpty())
            ? input.getDrivingKeyNames().size()
            : 2;
    wDrivingKeyNames =
        new TableView(
            variables,
            wKeysComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            drivingColumns,
            nrDrivingRows,
            null,
            PropsUi.getInstance());

    FormData fdDrivingKeys = new FormData();
    fdDrivingKeys.left = new FormAttachment(0, 0);
    fdDrivingKeys.top = new FormAttachment(wlDrivingKeys, margin);
    fdDrivingKeys.right = new FormAttachment(100, 0);
    fdDrivingKeys.bottom = new FormAttachment(100, 0);
    wDrivingKeyNames.setLayoutData(fdDrivingKeys);

    wKeysComp.layout();
    wKeysTab.setControl(wKeysComp);
  }

  private void addDependentChildKeysTab() {
    CTabItem wDepTab = new CTabItem(wTabFolder, SWT.NONE);
    wDepTab.setFont(GuiResource.getInstance().getFontDefault());
    wDepTab.setText(BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKeys.Tab"));
    wDepTab.setToolTipText(BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKeys.ToolTip"));
    Composite wDepComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wDepComp);
    wDepComp.setLayout(new FormLayout());

    Label wlDep = new Label(wDepComp, SWT.LEFT);
    wlDep.setText(BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKeys.Label"));
    PropsUi.setLook(wlDep);
    FormData fdlDep = new FormData();
    fdlDep.left = new FormAttachment(0, 0);
    fdlDep.top = new FormAttachment(0, 0);
    wlDep.setLayoutData(fdlDep);

    ColumnInfo[] depColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.SourceField.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {
                "String", "Integer", "Number", "BigNumber", "Date", "Timestamp", "Boolean", "Binary"
              }),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.Precision.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvLinkDialog.DependentChildKey.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    int nrDepRows =
        (input.getDependentChildKeys() != null && !input.getDependentChildKeys().isEmpty())
            ? input.getDependentChildKeys().size()
            : 1;
    wDependentChildKeys =
        new TableView(
            variables,
            wDepComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            depColumns,
            nrDepRows,
            null,
            PropsUi.getInstance());

    FormData fdDep = new FormData();
    fdDep.left = new FormAttachment(0, 0);
    fdDep.top = new FormAttachment(wlDep, margin);
    fdDep.right = new FormAttachment(100, 0);
    fdDep.bottom = new FormAttachment(100, 0);
    wDependentChildKeys.setLayoutData(fdDep);

    wDepComp.layout();
    wDepTab.setControl(wDepComp);
  }

  private void addHubSourcesTab() {
    CTabItem wSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSourcesTab.setText("Hub sources");
    wSourcesTab.setToolTipText(
        "Record sources for this link and their per-hub business key / driving key field mappings");
    Composite wSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSourcesComp);
    wSourcesComp.setLayout(new FormLayout());

    Label wlSources = new Label(wSourcesComp, SWT.LEFT);
    wlSources.setText("Link hub sources (one entry per record source feeding this link)");
    PropsUi.setLook(wlSources);
    FormData fdlSources = new FormData();
    fdlSources.left = new FormAttachment(0, 0);
    fdlSources.top = new FormAttachment(0, 0);
    wlSources.setLayoutData(fdlSources);

    Button wOpenInCatalog = new Button(wSourcesComp, SWT.PUSH);
    wOpenInCatalog.setText(
        BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.Label"));
    wOpenInCatalog.setToolTipText(
        BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.ToolTip"));
    PropsUi.setLook(wOpenInCatalog);
    FormData fdOpenInCatalog = new FormData();
    fdOpenInCatalog.right = new FormAttachment(100, 0);
    fdOpenInCatalog.top = new FormAttachment(wlSources, margin);
    wOpenInCatalog.setLayoutData(fdOpenInCatalog);
    wOpenInCatalog.addListener(
        SWT.Selection,
        e ->
            RecordSourceCatalogNavigationSupport.openSelectedSourceInCatalog(
                shell, hopGui, variables, hopGui.getMetadataProvider(), model, wLinkHubSources));

    Button wEditMappings = new Button(wSourcesComp, SWT.PUSH);
    wEditMappings.setText("Edit hub source mappings...");
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(0, 0);
    fdEdit.top = new FormAttachment(wlSources, margin);
    wEditMappings.setLayoutData(fdEdit);
    wEditMappings.addListener(SWT.Selection, e -> editSelectedLinkHubSource());

    // Simple table of source names (CCOMBO) - details managed via the sub dialog
    List<String> sourceNames = new ArrayList<>();
    try {
      sourceNames =
          DvSourceCatalogService.listSourceNames(model, variables, hopGui.getMetadataProvider());
    } catch (Exception e) {
      sourceNames = new ArrayList<>();
    }
    Collections.sort(sourceNames);

    ColumnInfo[] srcCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Data Vault Source",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              sourceNames.toArray(new String[0])),
        };

    wLinkHubSources =
        new TableView(
            variables,
            wSourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            srcCols,
            2,
            null,
            PropsUi.getInstance());

    FormData fdSources = new FormData();
    fdSources.left = new FormAttachment(0, 0);
    fdSources.top = new FormAttachment(wEditMappings, margin);
    fdSources.right = new FormAttachment(100, 0);
    fdSources.bottom = new FormAttachment(100, 0);
    wLinkHubSources.setLayoutData(fdSources);

    wSourcesComp.layout();
    wSourcesTab.setControl(wSourcesComp);
  }

  private void addSatelliteSourcesTab() {
    CTabItem wSatSourcesTab = new CTabItem(wTabFolder, SWT.NONE);
    wSatSourcesTab.setFont(GuiResource.getInstance().getFontDefault());
    wSatSourcesTab.setText("Satellite sources");
    wSatSourcesTab.setToolTipText(
        "Record sources for link satellites and their per-satellite attribute / driving key mappings");
    Composite wSatSourcesComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wSatSourcesComp);
    wSatSourcesComp.setLayout(new FormLayout());

    Label wlSatSources = new Label(wSatSourcesComp, SWT.LEFT);
    wlSatSources.setText(
        "Link satellite sources (one entry per record source feeding link satellites)");
    PropsUi.setLook(wlSatSources);
    FormData fdlSatSources = new FormData();
    fdlSatSources.left = new FormAttachment(0, 0);
    fdlSatSources.top = new FormAttachment(0, 0);
    wlSatSources.setLayoutData(fdlSatSources);

    Button wOpenSatInCatalog = new Button(wSatSourcesComp, SWT.PUSH);
    wOpenSatInCatalog.setText(
        BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.Label"));
    wOpenSatInCatalog.setToolTipText(
        BaseMessages.getString(PKG, "DvHubDialog.OpenInCatalog.ToolTip"));
    PropsUi.setLook(wOpenSatInCatalog);
    FormData fdOpenSatInCatalog = new FormData();
    fdOpenSatInCatalog.right = new FormAttachment(100, 0);
    fdOpenSatInCatalog.top = new FormAttachment(wlSatSources, margin);
    wOpenSatInCatalog.setLayoutData(fdOpenSatInCatalog);
    wOpenSatInCatalog.addListener(
        SWT.Selection,
        e ->
            RecordSourceCatalogNavigationSupport.openSelectedSourceInCatalog(
                shell,
                hopGui,
                variables,
                hopGui.getMetadataProvider(),
                model,
                wLinkSatelliteSources));

    Button wEditSatMappings = new Button(wSatSourcesComp, SWT.PUSH);
    wEditSatMappings.setText("Edit satellite source mappings...");
    FormData fdEditSat = new FormData();
    fdEditSat.left = new FormAttachment(0, 0);
    fdEditSat.top = new FormAttachment(wlSatSources, margin);
    wEditSatMappings.setLayoutData(fdEditSat);
    wEditSatMappings.addListener(SWT.Selection, e -> editSelectedLinkSatelliteSource());

    List<String> sourceNames = new ArrayList<>();
    try {
      sourceNames =
          DvSourceCatalogService.listSourceNames(model, variables, hopGui.getMetadataProvider());
    } catch (Exception e) {
      sourceNames = new ArrayList<>();
    }
    Collections.sort(sourceNames);

    ColumnInfo[] srcCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Data Vault Source",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              sourceNames.toArray(new String[0])),
        };

    wLinkSatelliteSources =
        new TableView(
            variables,
            wSatSourcesComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            srcCols,
            2,
            null,
            PropsUi.getInstance());

    FormData fdSatSources = new FormData();
    fdSatSources.left = new FormAttachment(0, 0);
    fdSatSources.top = new FormAttachment(wEditSatMappings, margin);
    fdSatSources.right = new FormAttachment(100, 0);
    fdSatSources.bottom = new FormAttachment(100, 0);
    wLinkSatelliteSources.setLayoutData(fdSatSources);

    wSatSourcesComp.layout();
    wSatSourcesTab.setControl(wSatSourcesComp);
  }

  private List<String> getModelHubNames() {
    List<String> names = new ArrayList<>();
    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (Utils.isEmpty(table.getName())) {
          continue;
        }
        // Physical hubs and hub-like table references / role-playing aliases.
        if (table.getTableType() == DvTableType.HUB) {
          names.add(table.getName());
        } else if (table instanceof DvLinkedTable reference
            && reference.getReferencedTableType() == DvTableType.HUB) {
          names.add(table.getName());
        }
      }
    }
    if (input.getHubNames() != null) {
      for (String hubName : input.getHubNames()) {
        if (!Utils.isEmpty(hubName) && !names.contains(hubName)) {
          names.add(hubName);
        }
      }
    }
    Collections.sort(names);
    return names;
  }

  private List<String> getModelLinkSatelliteNames() {
    List<String> names = new ArrayList<>();
    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table.getTableType() == DvTableType.SATELLITE) {
          DvSatellite satellite = (DvSatellite) table;
          if (!Utils.isEmpty(satellite.getLinkName()) && !Utils.isEmpty(table.getName())) {
            names.add(table.getName());
          }
        }
      }
    }
    if (input.getLinkSatelliteNames() != null) {
      for (String satName : input.getLinkSatelliteNames()) {
        if (!Utils.isEmpty(satName) && !names.contains(satName)) {
          names.add(satName);
        }
      }
    }
    Collections.sort(names);
    return names;
  }

  private List<String> getDrivingKeyNamesFromTable() {
    List<String> drivingKeys = new ArrayList<>();
    if (wDrivingKeyNames == null) {
      return drivingKeys;
    }
    for (TableItem item : wDrivingKeyNames.getNonEmptyItems()) {
      String drivingKey = item.getText(1);
      if (!Utils.isEmpty(drivingKey)) {
        drivingKeys.add(drivingKey);
      }
    }
    return drivingKeys;
  }

  private List<String> getHubNamesFromTable() {
    List<String> hubs = new ArrayList<>();
    if (wHubNames == null) {
      return hubs;
    }
    for (TableItem item : wHubNames.getNonEmptyItems()) {
      String h = item.getText(1);
      if (!Utils.isEmpty(h)) {
        hubs.add(h);
      }
    }
    return hubs;
  }

  private List<String> getLinkSatelliteNamesFromTable() {
    List<String> satellites = new ArrayList<>();
    if (wLinkSatelliteNames == null) {
      return satellites;
    }
    for (TableItem item : wLinkSatelliteNames.getNonEmptyItems()) {
      String s = item.getText(1);
      if (!Utils.isEmpty(s)) {
        satellites.add(s);
      }
    }
    return satellites;
  }

  private void editSelectedLinkHubSource() {
    List<TableItem> items = wLinkHubSources.getNonEmptyItems();
    if (items.isEmpty()) {
      return;
    }
    TableItem sel = null;
    if (wLinkHubSources.table.getSelectionCount() > 0) {
      sel = wLinkHubSources.table.getSelection()[0];
    }
    if (sel == null) {
      sel = items.get(0);
    }
    String sourceName = sel.getText(1);
    if (Utils.isEmpty(sourceName)) {
      return;
    }

    DvLink.DvLinkHubSource detail = null;
    for (DvLink.DvLinkHubSource ls : currentLinkHubSources) {
      if (!Utils.isEmpty(ls.getSourceName()) && sourceName.equals(ls.getSourceName())) {
        detail = ls;
        break;
      }
    }
    if (detail == null) {
      detail = new DvLink.DvLinkHubSource();
      detail.setSourceName(sourceName);
      currentLinkHubSources.add(detail);
    }

    List<String> hubs = getHubNamesFromTable();
    List<String> drivingKeys = getDrivingKeyNamesFromTable();
    DvLinkHubSourceDialog dlg =
        new DvLinkHubSourceDialog(shell, hopGui, detail, hubs, model, drivingKeys);
    dlg.open();
  }

  private void editSelectedLinkSatelliteSource() {
    List<TableItem> items = wLinkSatelliteSources.getNonEmptyItems();
    if (items.isEmpty()) {
      return;
    }
    TableItem sel = null;
    if (wLinkSatelliteSources.table.getSelectionCount() > 0) {
      sel = wLinkSatelliteSources.table.getSelection()[0];
    }
    if (sel == null) {
      sel = items.get(0);
    }
    String sourceName = sel.getText(1);
    if (Utils.isEmpty(sourceName)) {
      return;
    }

    DvLink.DvLinkSatelliteSource detail = null;
    for (DvLink.DvLinkSatelliteSource ls : currentLinkSatelliteSources) {
      if (!Utils.isEmpty(ls.getSourceName()) && sourceName.equals(ls.getSourceName())) {
        detail = ls;
        break;
      }
    }
    if (detail == null) {
      detail = new DvLink.DvLinkSatelliteSource();
      detail.setSourceName(sourceName);
      currentLinkSatelliteSources.add(detail);
    }

    List<String> satellites = getLinkSatelliteNamesFromTable();
    List<String> drivingKeys = getDrivingKeyNamesFromTable();
    DvLinkSatelliteSourceDialog dlg =
        new DvLinkSatelliteSourceDialog(shell, hopGui, detail, satellites, model, drivingKeys);
    dlg.open();
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
    wLinkHashKeyFieldName.setText(Const.NVL(input.getLinkHashKeyFieldName(), ""));
    wRecordSourceFieldName.setText(Const.NVL(input.getRecordSourceFieldName(), ""));
    wHasDescriptiveAttributes.setSelection(input.isHasDescriptiveAttributes());

    // Hubs (single column now)
    wHubNames.clearAll();
    if (input.getHubNames() != null) {
      for (String hubName : input.getHubNames()) {
        TableItem item = new TableItem(wHubNames.table, SWT.NONE);
        item.setText(1, Const.NVL(hubName, ""));
      }
    }
    wHubNames.optimizeTableView();
    wLinkSatelliteNames.clearAll();
    if (input.getLinkSatelliteNames() != null) {
      for (String satName : input.getLinkSatelliteNames()) {
        TableItem item = new TableItem(wLinkSatelliteNames.table, SWT.NONE);
        item.setText(1, Const.NVL(satName, ""));
      }
    }
    wLinkSatelliteNames.optimizeTableView();
    // Driving keys
    wDrivingKeyNames.clearAll();
    if (input.getDrivingKeyNames() != null) {
      for (String dk : input.getDrivingKeyNames()) {
        TableItem item = new TableItem(wDrivingKeyNames.table, SWT.NONE);
        item.setText(1, Const.NVL(dk, ""));
      }
    }
    wDrivingKeyNames.optimizeTableView();
    wDependentChildKeys.clearAll();
    if (input.getDependentChildKeys() != null) {
      for (DependentChildKey dck : input.getDependentChildKeys()) {
        if (dck == null) {
          continue;
        }
        TableItem item = new TableItem(wDependentChildKeys.table, SWT.NONE);
        item.setText(1, Const.NVL(dck.getName(), ""));
        item.setText(2, Const.NVL(dck.getSourceFieldName(), ""));
        item.setText(3, Const.NVL(dck.getDataType(), "String"));
        item.setText(4, Const.NVL(dck.getLength(), ""));
        item.setText(5, Const.NVL(dck.getPrecision(), ""));
        item.setText(6, Const.NVL(dck.getDescription(), ""));
      }
    }
    wDependentChildKeys.optimizeTableView();
    currentLinkHubSources.clear();
    wLinkHubSources.clearAll();
    if (input.getLinkHubSources() != null) {
      for (DvLink.DvLinkHubSource ls : input.getLinkHubSources()) {
        if (ls != null) {
          currentLinkHubSources.add(ls);
          if (!Utils.isEmpty(ls.getSourceName()) && !Utils.isEmpty(ls.getSourceName())) {
            TableItem item = new TableItem(wLinkHubSources.table, SWT.NONE);
            item.setText(1, ls.getSourceName());
          }
        }
      }
    }
    wLinkHubSources.optimizeTableView();
    currentLinkSatelliteSources.clear();
    wLinkSatelliteSources.clearAll();
    if (input.getLinkSatelliteSources() != null) {
      for (DvLink.DvLinkSatelliteSource ls : input.getLinkSatelliteSources()) {
        if (ls != null) {
          currentLinkSatelliteSources.add(ls);
          if (!Utils.isEmpty(ls.getSourceName()) && !Utils.isEmpty(ls.getSourceName())) {
            TableItem item = new TableItem(wLinkSatelliteSources.table, SWT.NONE);
            item.setText(1, ls.getSourceName());
          }
        }
      }
    }
    wLinkSatelliteSources.optimizeTableView();
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
      DvLink draftTable = locateDraftTable(draft);
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

  private DvLink locateDraftTable(DataVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    IDvTable table = draft.getTables().get(originalTableIndex);
    if (!(table instanceof DvLink link)) {
      throw new HopException("Validation model table type mismatch");
    }
    return link;
  }

  private void applyWidgetsToTable(DvLink target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    target.setIntegrationMode(DvIntegrationMode.lookupDescription(wIntegrationMode.getText()));
    target.setLinkHashKeyFieldName(wLinkHashKeyFieldName.getText());
    target.setRecordSourceFieldName(wRecordSourceFieldName.getText());
    target.setHasDescriptiveAttributes(wHasDescriptiveAttributes.getSelection());

    List<String> hubs = new ArrayList<>();
    for (TableItem item : wHubNames.getNonEmptyItems()) {
      String h = item.getText(1);
      if (!Utils.isEmpty(h)) {
        hubs.add(h);
      }
    }
    target.setHubNames(hubs);

    List<String> linkSats = new ArrayList<>();
    for (TableItem item : wLinkSatelliteNames.getNonEmptyItems()) {
      String s = item.getText(1);
      if (!Utils.isEmpty(s)) {
        linkSats.add(s);
      }
    }
    target.setLinkSatelliteNames(linkSats);

    List<String> drives = new ArrayList<>();
    for (TableItem item : wDrivingKeyNames.getNonEmptyItems()) {
      String d = item.getText(1);
      if (!Utils.isEmpty(d)) {
        drives.add(d);
      }
    }
    target.setDrivingKeyNames(drives);

    List<DependentChildKey> depKeys = new ArrayList<>();
    for (TableItem item : wDependentChildKeys.getNonEmptyItems()) {
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      DependentChildKey dck = new DependentChildKey(name);
      dck.setSourceFieldName(item.getText(2));
      String dataType = item.getText(3);
      dck.setDataType(Utils.isEmpty(dataType) ? "String" : dataType);
      dck.setLength(item.getText(4));
      dck.setPrecision(item.getText(5));
      dck.setDescription(item.getText(6));
      depKeys.add(dck);
    }
    target.setDependentChildKeys(depKeys);

    target.getLinkHubSources().clear();
    for (TableItem item : wLinkHubSources.getNonEmptyItems()) {
      String sname = item.getText(1);
      if (Utils.isEmpty(sname)) {
        continue;
      }
      DvLink.DvLinkHubSource match = null;
      for (DvLink.DvLinkHubSource cand : currentLinkHubSources) {
        if (!Utils.isEmpty(cand.getSourceName()) && sname.equals(cand.getSourceName())) {
          match = cand;
          break;
        }
      }
      if (match == null) {
        match = new DvLink.DvLinkHubSource();
        match.setSourceName(sname);
      }
      target.getLinkHubSources().add(match);
    }

    target.getLinkSatelliteSources().clear();
    for (TableItem item : wLinkSatelliteSources.getNonEmptyItems()) {
      String sname = item.getText(1);
      if (Utils.isEmpty(sname)) {
        continue;
      }
      DvLink.DvLinkSatelliteSource match = null;
      for (DvLink.DvLinkSatelliteSource cand : currentLinkSatelliteSources) {
        if (!Utils.isEmpty(cand.getSourceName()) && sname.equals(cand.getSourceName())) {
          match = cand;
          break;
        }
      }
      if (match == null) {
        match = new DvLink.DvLinkSatelliteSource();
        match.setSourceName(sname);
      }
      target.getLinkSatelliteSources().add(match);
    }
    customPipelinesTab.applyTo(target);
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
