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
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvBusinessKeyPartSupport;
import org.apache.hop.datavault.metadata.DvDataTypeSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvIntegrationMode;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSatelliteParentKeySupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit the properties of a DvSatellite, including attributes list using TableView. */
public class DvSatelliteDialog {
  private static final Class<?> PKG = DvSatelliteDialog.class;

  private Shell parent;
  private IVariables variables;
  private DvSatellite input;
  private DataVaultModel model;
  private final int originalTableIndex;
  private HopGui hopGui;
  private Shell shell;

  private CTabFolder wTabFolder;

  // Widgets
  private Text wName;
  private Text wDescription;
  private Combo wIntegrationMode;
  private Text wTableName;
  private DvCatalogSourceSelectionLine wRecordSource;
  private Combo wHubName;
  private Combo wLinkName;
  private Text wDrivingKey;
  private Combo wDrivingKeySourceField;
  private Button wStoreRecordSource;
  private Combo wOrphanPolicy;
  private Button wSeedParentHub;
  private TableView wAttributes;
  private TableView wParentKeys;
  private Label wlParentKeysHint;

  private Button wStatusTrackingEnabled;
  private Text wStatusTableName;
  private Text wStatusFieldName;
  private Text wActiveStatusValue;
  private Text wDeletedStatusValue;
  private Control[] stsDetailControls;
  private DvCustomPipelinesTabSupport customPipelinesTab;

  private int margin;
  private int middle;

  private boolean ok;

  public DvSatelliteDialog(
      Shell parent, HopGui hopGui, DvSatellite satellite, DataVaultModel model) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = satellite;
    this.model = model;
    this.originalTableIndex = model != null ? model.getTables().indexOf(satellite) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Title", input.getName()));

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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_SATELLITE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDescription, margin)
            .right()
            .bottom(wOk, -2 * margin)
            .result());

    customPipelinesTab = new DvCustomPipelinesTabSupport(shell, hopGui, variables, margin);
    addGeneralTab();
    addParentKeysTab();
    addAttributesTab();
    addStatusTrackingTab();
    customPipelinesTab.addTab(wTabFolder);
    addLineageTab();

    wTabFolder.setSelection(0);

    getData();
    updateStsFieldsEnabled();
    customPipelinesTab.bindIntegrationMode(wIntegrationMode);

    BaseTransformDialog.setSize(shell, 700, 550);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
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

  private void addGeneralTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.General.Label"));
    tab.setToolTipText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.General.ToolTip"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlIntegrationMode = new Label(comp, SWT.RIGHT);
    wlIntegrationMode.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.IntegrationMode.Label"));
    PropsUi.setLook(wlIntegrationMode);
    wlIntegrationMode.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wIntegrationMode = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wIntegrationMode);
    wIntegrationMode.setItems(DvIntegrationMode.getDescriptions());
    wIntegrationMode.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.IntegrationMode.ToolTip"));
    wIntegrationMode.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlTableName = new Label(comp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    wlTableName.setLayoutData(
        new FormDataBuilder().left().top(wIntegrationMode, margin).right(middle, -margin).result());

    wTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wIntegrationMode, margin).right().result());

    wRecordSource =
        new DvCatalogSourceSelectionLine(
            variables,
            hopGui.getMetadataProvider(),
            model,
            comp,
            SWT.BORDER,
            BaseMessages.getString(PKG, "DvSatelliteDialog.RecordSource.Label"),
            BaseMessages.getString(PKG, "DvSatelliteDialog.RecordSource.ToolTip"));
    wRecordSource.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right().result());
    wRecordSource.addModifyListener(e -> refreshDrivingKeySourceFieldItems());

    Button wOpenInCatalog = new Button(comp, SWT.PUSH);
    wOpenInCatalog.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.OpenInCatalog.Label"));
    wOpenInCatalog.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.OpenInCatalog.ToolTip"));
    PropsUi.setLook(wOpenInCatalog);
    wOpenInCatalog.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wRecordSource, margin).result());
    wOpenInCatalog.addListener(
        SWT.Selection,
        e ->
            RecordSourceCatalogNavigationSupport.openSourceInCatalog(
                shell,
                hopGui,
                variables,
                hopGui.getMetadataProvider(),
                model,
                wRecordSource.getText()));

    wStoreRecordSource = new Button(comp, SWT.CHECK);
    wStoreRecordSource.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StoreRecordSource.Label"));
    wStoreRecordSource.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StoreRecordSource.ToolTip"));
    PropsUi.setLook(wStoreRecordSource);
    wStoreRecordSource.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wOpenInCatalog, margin).right().result());

    Label wlHubName = new Label(comp, SWT.RIGHT);
    wlHubName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.HubName.Label"));
    PropsUi.setLook(wlHubName);
    wlHubName.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wStoreRecordSource, margin)
            .right(middle, -margin)
            .result());

    wHubName = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wHubName);
    wHubName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wStoreRecordSource, margin).right().result());
    wHubName.addListener(SWT.Selection, e -> updateParentKeysTabHint());

    Label wlLinkName = new Label(comp, SWT.RIGHT);
    wlLinkName.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.LinkName.Label"));
    PropsUi.setLook(wlLinkName);
    wlLinkName.setLayoutData(
        new FormDataBuilder().left().top(wHubName, margin).right(middle, -margin).result());

    wLinkName = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wLinkName);
    wLinkName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wHubName, margin).right().result());
    wLinkName.addListener(SWT.Selection, e -> updateParentKeysTabHint());

    Label wlDrivingKey = new Label(comp, SWT.RIGHT);
    wlDrivingKey.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.DrivingKey.Label"));
    PropsUi.setLook(wlDrivingKey);
    wlDrivingKey.setLayoutData(
        new FormDataBuilder().left().top(wLinkName, margin).right(middle, -margin).result());

    wDrivingKey = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDrivingKey);
    wDrivingKey.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wLinkName, margin).right().result());

    Label wlDrivingKeySourceField = new Label(comp, SWT.RIGHT);
    wlDrivingKeySourceField.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.DrivingKeySourceField.Label"));
    PropsUi.setLook(wlDrivingKeySourceField);
    wlDrivingKeySourceField.setLayoutData(
        new FormDataBuilder().left().top(wDrivingKey, margin).right(middle, -margin).result());

    wDrivingKeySourceField = new Combo(comp, SWT.BORDER);
    PropsUi.setLook(wDrivingKeySourceField);
    wDrivingKeySourceField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDrivingKey, margin).right().result());

    Label wlOrphanPolicy = new Label(comp, SWT.RIGHT);
    wlOrphanPolicy.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.OrphanPolicy.Label"));
    PropsUi.setLook(wlOrphanPolicy);
    wlOrphanPolicy.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDrivingKeySourceField, margin)
            .right(middle, -margin)
            .result());

    wOrphanPolicy = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wOrphanPolicy);
    wOrphanPolicy.setItems(
        new String[] {
          org.apache.hop.datavault.metadata.DvOrphanPolicy.INHERIT.name(),
          org.apache.hop.datavault.metadata.DvOrphanPolicy.PASS.name(),
          org.apache.hop.datavault.metadata.DvOrphanPolicy.INFER.name(),
          org.apache.hop.datavault.metadata.DvOrphanPolicy.SENTINEL.name(),
          org.apache.hop.datavault.metadata.DvOrphanPolicy.QUARANTINE.name(),
          org.apache.hop.datavault.metadata.DvOrphanPolicy.FAIL.name()
        });
    wOrphanPolicy.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.OrphanPolicy.ToolTip"));
    wOrphanPolicy.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDrivingKeySourceField, margin).right().result());

    wSeedParentHub = new Button(comp, SWT.PUSH);
    wSeedParentHub.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.SeedParentHub.Label"));
    wSeedParentHub.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.SeedParentHub.ToolTip"));
    PropsUi.setLook(wSeedParentHub);
    wSeedParentHub.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wOrphanPolicy, margin).result());
    wSeedParentHub.addListener(SWT.Selection, e -> seedParentHub());
  }

  private void addParentKeysTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.ParentKeys.Label"));
    tab.setToolTipText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.ParentKeys.ToolTip"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    wlParentKeysHint = new Label(comp, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlParentKeysHint);
    wlParentKeysHint.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.ParentKey.SourceField.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              false),
        };
    columns[0].setUsingVariables(true);

    Button wLoadParentKeys = new Button(comp, SWT.PUSH);
    wLoadParentKeys.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.Button"));
    wLoadParentKeys.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.ToolTip"));
    PropsUi.setLook(wLoadParentKeys);
    wLoadParentKeys.setLayoutData(new FormDataBuilder().left().bottom().result());
    wLoadParentKeys.addListener(SWT.Selection, e -> loadParentKeysFromHub());

    int nrRows =
        input.getParentKeySourceFields() != null
            ? Math.max(1, input.getParentKeySourceFields().size())
            : 1;
    wParentKeys =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            nrRows,
            null,
            PropsUi.getInstance());
    wParentKeys.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlParentKeysHint, margin)
            .right()
            .bottom(wLoadParentKeys, -margin)
            .result());

    updateParentKeysTabHint();
  }

  private void updateParentKeysTabHint() {
    if (wlParentKeysHint == null || wlParentKeysHint.isDisposed()) {
      return;
    }
    String linkName = wLinkName != null ? Const.NVL(wLinkName.getText(), "").trim() : "";
    if (!Utils.isEmpty(linkName)) {
      wlParentKeysHint.setText(
          BaseMessages.getString(PKG, "DvSatelliteDialog.ParentKeys.LinkSatellite.Hint"));
      return;
    }
    String hubName = wHubName != null ? Const.NVL(wHubName.getText(), "").trim() : "";
    String preferredSource =
        wRecordSource != null ? Const.NVL(wRecordSource.getText(), "").trim() : "";
    String hubKeyOrder = "";
    boolean hubHasComposite = false;
    if (!Utils.isEmpty(hubName) && model != null) {
      DvHub hub = model.findHub(hubName, variables, hopGui.getMetadataProvider());
      if (hub != null) {
        List<String> defaults =
            DvSatelliteParentKeySupport.defaultSourceFieldsFromHub(hub, preferredSource, variables);
        hubKeyOrder = defaults.isEmpty() ? "" : String.join(", ", defaults);
        hubHasComposite = DvBusinessKeyPartSupport.hubHasCompositeBusinessKey(hub);
      }
    }
    if (Utils.isEmpty(hubKeyOrder)) {
      wlParentKeysHint.setText(
          BaseMessages.getString(PKG, "DvSatelliteDialog.ParentKeys.HubSatellite.Hint"));
    } else if (hubHasComposite) {
      wlParentKeysHint.setText(
          BaseMessages.getString(
              PKG,
              "DvSatelliteDialog.ParentKeys.HubSatellite.HintWithCompositeOrder",
              hubKeyOrder));
    } else {
      wlParentKeysHint.setText(
          BaseMessages.getString(
              PKG, "DvSatelliteDialog.ParentKeys.HubSatellite.HintWithOrder", hubKeyOrder));
    }
  }

  private void loadParentKeysFromHub() {
    String hubName = wHubName != null ? Const.NVL(wHubName.getText(), "").trim() : "";
    String linkName = wLinkName != null ? Const.NVL(wLinkName.getText(), "").trim() : "";
    if (!Utils.isEmpty(linkName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.LinkSatellite.Message"));
      mb.setText(
          BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.LinkSatellite.Title"));
      mb.open();
      return;
    }
    if (Utils.isEmpty(hubName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.NoHub.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.NoHub.Title"));
      mb.open();
      return;
    }
    DvHub hub =
        model != null ? model.findHub(hubName, variables, hopGui.getMetadataProvider()) : null;
    if (hub == null) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvSatelliteDialog.LoadParentKeys.HubNotFound.Message", hubName));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.HubNotFound.Title"));
      mb.open();
      return;
    }
    String preferredSource =
        wRecordSource != null ? Const.NVL(wRecordSource.getText(), "").trim() : "";
    List<String> sourceFields =
        DvSatelliteParentKeySupport.defaultSourceFieldsFromHub(hub, preferredSource, variables);
    if (sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvSatelliteDialog.LoadParentKeys.NoBusinessKeys.Message", hubName));
      mb.setText(
          BaseMessages.getString(PKG, "DvSatelliteDialog.LoadParentKeys.NoBusinessKeys.Title"));
      mb.open();
      return;
    }
    wParentKeys.clearAll(false);
    for (String sourceField : sourceFields) {
      TableItem item = new TableItem(wParentKeys.table, SWT.NONE);
      item.setText(1, Const.NVL(sourceField, ""));
    }
    wParentKeys.optimizeTableView();
    updateParentKeysTabHint();
  }

  private void addAttributesTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.Attributes.Label"));
    tab.setToolTipText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.Attributes.ToolTip"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlAttributes = new Label(comp, SWT.LEFT);
    wlAttributes.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Attributes.Label"));
    PropsUi.setLook(wlAttributes);
    wlAttributes.setLayoutData(new FormDataBuilder().left().top(0, margin).result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Name.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Description.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.DataType.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Length.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvSatelliteDialog.Attribute.Precision.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "DvSatelliteDialog.Attribute.IncludeInChangeDataCapture.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              BaseMessages.getString(PKG, "System.Combo.Yes"),
              BaseMessages.getString(PKG, "System.Combo.No")),
        };

    Button wLoadFromSource = new Button(comp, SWT.PUSH);
    wLoadFromSource.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.Button"));
    wLoadFromSource.setToolTipText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.ToolTip"));
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

  private void addStatusTrackingTab() {
    CTabItem tab = new CTabItem(wTabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.StatusTracking.Label"));
    tab.setToolTipText(BaseMessages.getString(PKG, "DvSatelliteDialog.Tab.StatusTracking.ToolTip"));

    Composite comp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    wStatusTrackingEnabled = new Button(comp, SWT.CHECK);
    wStatusTrackingEnabled.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StatusTracking.Enabled.Label"));
    PropsUi.setLook(wStatusTrackingEnabled);
    wStatusTrackingEnabled.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wStatusTrackingEnabled.addListener(SWT.Selection, e -> updateStsFieldsEnabled());

    Label wlStatusTableName = new Label(comp, SWT.RIGHT);
    wlStatusTableName.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StatusTracking.TableName.Label"));
    PropsUi.setLook(wlStatusTableName);
    wlStatusTableName.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wStatusTrackingEnabled, margin)
            .right(middle, -margin)
            .result());

    wStatusTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wStatusTableName);
    wStatusTableName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wStatusTrackingEnabled, margin).right().result());

    Label wlStatusFieldName = new Label(comp, SWT.RIGHT);
    wlStatusFieldName.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StatusTracking.FieldName.Label"));
    PropsUi.setLook(wlStatusFieldName);
    wlStatusFieldName.setLayoutData(
        new FormDataBuilder().left().top(wStatusTableName, margin).right(middle, -margin).result());

    wStatusFieldName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wStatusFieldName);
    wStatusFieldName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wStatusTableName, margin).right().result());

    Label wlActiveStatusValue = new Label(comp, SWT.RIGHT);
    wlActiveStatusValue.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StatusTracking.ActiveValue.Label"));
    PropsUi.setLook(wlActiveStatusValue);
    wlActiveStatusValue.setLayoutData(
        new FormDataBuilder().left().top(wStatusFieldName, margin).right(middle, -margin).result());

    wActiveStatusValue = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wActiveStatusValue);
    wActiveStatusValue.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wStatusFieldName, margin).right().result());

    Label wlDeletedStatusValue = new Label(comp, SWT.RIGHT);
    wlDeletedStatusValue.setText(
        BaseMessages.getString(PKG, "DvSatelliteDialog.StatusTracking.DeletedValue.Label"));
    PropsUi.setLook(wlDeletedStatusValue);
    wlDeletedStatusValue.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wActiveStatusValue, margin)
            .right(middle, -margin)
            .result());

    wDeletedStatusValue = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDeletedStatusValue);
    wDeletedStatusValue.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wActiveStatusValue, margin).right().result());

    stsDetailControls =
        new Control[] {
          wlStatusTableName,
          wStatusTableName,
          wlStatusFieldName,
          wStatusFieldName,
          wlActiveStatusValue,
          wActiveStatusValue,
          wlDeletedStatusValue,
          wDeletedStatusValue
        };
  }

  private void updateStsFieldsEnabled() {
    boolean enabled = wStatusTrackingEnabled.getSelection();
    if (stsDetailControls != null) {
      for (Control control : stsDetailControls) {
        if (control != null && !control.isDisposed()) {
          control.setEnabled(enabled);
        }
      }
    }
  }

  private void seedParentHub() {
    applyWidgetsToTable(input);
    int added =
        org.apache.hop.datavault.metadata.DvOrphanHandlingSupport.seedParentHubFromSatellite(
            model, input, variables);
    MessageBox done = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
    done.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.SeedParentHub.Title"));
    done.setMessage(
        BaseMessages.getString(PKG, "DvSatelliteDialog.SeedParentHub.Applied.Message", added));
    done.open();
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    DvIntegrationMode integrationMode =
        input.getIntegrationMode() != null
            ? input.getIntegrationMode()
            : DvIntegrationMode.HOP_MANAGED;
    wIntegrationMode.setText(integrationMode.getDescription());
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    // Catalog source names load lazily on combo focus (wait cursor).
    wRecordSource.setText(Const.NVL(input.getRecordSourceName(), ""));
    wStoreRecordSource.setSelection(input.isStoreRecordSource());
    if (wOrphanPolicy != null) {
      wOrphanPolicy.setText(
          Const.NVL(
              input.getOrphanPolicy(),
              org.apache.hop.datavault.metadata.DvOrphanPolicy.INHERIT.name()));
    }
    refreshHubNameItems();
    selectComboValue(wHubName, Const.NVL(input.getHubName(), ""));
    refreshLinkNameItems();
    selectComboValue(wLinkName, Const.NVL(input.getLinkName(), ""));
    wDrivingKey.setText(Const.NVL(input.getDrivingKey(), ""));
    refreshDrivingKeySourceFieldItems();
    wDrivingKeySourceField.setText(Const.NVL(input.getDrivingKeySourceField(), ""));

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

    if (wParentKeys != null && input.getParentKeySourceFields() != null) {
      for (int i = 0; i < input.getParentKeySourceFields().size(); i++) {
        TableItem item = wParentKeys.table.getItem(i);
        item.setText(1, Const.NVL(input.getParentKeySourceFields().get(i), ""));
      }
    }
    if (wParentKeys != null) {
      wParentKeys.optimizeTableView();
    }
    updateParentKeysTabHint();

    wStatusTrackingEnabled.setSelection(input.isStatusTrackingEnabled());
    wStatusTableName.setText(Const.NVL(input.getStatusTableName(), ""));
    wStatusFieldName.setText(
        Const.NVL(input.getStatusFieldName(), DvSatellite.DEFAULT_STATUS_FIELD_NAME));
    wActiveStatusValue.setText(
        Const.NVL(input.getActiveStatusValue(), DvSatellite.DEFAULT_ACTIVE_STATUS_VALUE));
    wDeletedStatusValue.setText(
        Const.NVL(input.getDeletedStatusValue(), DvSatellite.DEFAULT_DELETED_STATUS_VALUE));
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
                DvSatellite draftTable = locateDraftTable(draft);
                applyWidgetsToTable(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                try (DvModelCheckOptions options = DvModelCheckOptions.forCheckRun()) {
                  draftTable.check(
                      tableRemarks, hopGui.getMetadataProvider(), variables, options, draft);
                }
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

  private DvSatellite locateDraftTable(DataVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    IDvTable table = draft.getTables().get(originalTableIndex);
    if (!(table instanceof DvSatellite satellite)) {
      throw new HopException("Validation model table type mismatch");
    }
    return satellite;
  }

  private void applyWidgetsToTable(DvSatellite target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    target.setIntegrationMode(DvIntegrationMode.lookupDescription(wIntegrationMode.getText()));
    target.setRecordSourceName(wRecordSource.getText());
    target.setStoreRecordSource(wStoreRecordSource.getSelection());
    if (wOrphanPolicy != null) {
      target.setOrphanPolicy(wOrphanPolicy.getText());
    }
    target.setHubName(wHubName.getText());
    target.setLinkName(wLinkName.getText());
    target.setDrivingKey(wDrivingKey.getText());
    target.setDrivingKeySourceField(wDrivingKeySourceField.getText());

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

    List<String> parentKeySourceFields = new ArrayList<>();
    if (wParentKeys != null) {
      for (TableItem item : wParentKeys.getNonEmptyItems()) {
        String sourceField = item.getText(1);
        if (!Utils.isEmpty(sourceField)) {
          parentKeySourceFields.add(sourceField.trim());
        }
      }
    }
    target.setParentKeySourceFields(parentKeySourceFields);

    target.setStatusTrackingEnabled(wStatusTrackingEnabled.getSelection());
    target.setStatusTableName(wStatusTableName.getText());
    target.setStatusFieldName(wStatusFieldName.getText());
    target.setActiveStatusValue(wActiveStatusValue.getText());
    target.setDeletedStatusValue(wDeletedStatusValue.getText());
    customPipelinesTab.applyTo(target);
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private List<String> getModelHubNames() {
    List<String> names = new ArrayList<>();
    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table != null
            && table.getTableType() == DvTableType.HUB
            && !Utils.isEmpty(table.getName())) {
          names.add(table.getName());
        }
      }
    }
    if (!Utils.isEmpty(input.getHubName()) && !names.contains(input.getHubName())) {
      names.add(input.getHubName());
    }
    Collections.sort(names);
    return names;
  }

  private List<String> getModelLinkNames() {
    List<String> names = new ArrayList<>();
    if (model != null && model.getTables() != null) {
      for (IDvTable table : model.getTables()) {
        if (table != null
            && table.getTableType() == DvTableType.LINK
            && !Utils.isEmpty(table.getName())) {
          names.add(table.getName());
        }
      }
    }
    if (!Utils.isEmpty(input.getLinkName()) && !names.contains(input.getLinkName())) {
      names.add(input.getLinkName());
    }
    Collections.sort(names);
    return names;
  }

  private void refreshHubNameItems() {
    if (wHubName == null || wHubName.isDisposed()) {
      return;
    }
    String current = wHubName.getText();
    wHubName.removeAll();
    for (String hubName : getModelHubNames()) {
      wHubName.add(hubName);
    }
    selectComboValue(wHubName, current);
  }

  private void refreshLinkNameItems() {
    if (wLinkName == null || wLinkName.isDisposed()) {
      return;
    }
    String current = wLinkName.getText();
    wLinkName.removeAll();
    for (String linkName : getModelLinkNames()) {
      wLinkName.add(linkName);
    }
    selectComboValue(wLinkName, current);
  }

  private static void selectComboValue(Combo combo, String value) {
    if (combo == null || combo.isDisposed()) {
      return;
    }
    if (Utils.isEmpty(value)) {
      combo.deselectAll();
      combo.setText("");
      return;
    }
    int index = combo.indexOf(value);
    if (index < 0) {
      combo.add(value);
      index = combo.indexOf(value);
    }
    if (index >= 0) {
      combo.select(index);
    }
  }

  private void refreshDrivingKeySourceFieldItems() {
    if (wDrivingKeySourceField == null || wDrivingKeySourceField.isDisposed()) {
      return;
    }
    String current = wDrivingKeySourceField.getText();
    wDrivingKeySourceField.removeAll();
    for (String fieldName : loadSortedRecordSourceFieldNames(wRecordSource.getText())) {
      wDrivingKeySourceField.add(fieldName);
    }
    if (!Utils.isEmpty(current)) {
      wDrivingKeySourceField.setText(current);
    }
  }

  private List<String> loadSortedRecordSourceFieldNames(String sourceName) {
    if (Utils.isEmpty(sourceName)) {
      return List.of();
    }
    try {
      DataVaultSource source =
          DvSourceCatalogService.resolveSource(
              sourceName, model, variables, hopGui.getMetadataProvider());
      if (source == null) {
        return List.of();
      }
      List<SourceField> sourceFields = source.getFields(hopGui.getMetadataProvider());
      if (sourceFields == null || sourceFields.isEmpty()) {
        return List.of();
      }
      List<String> names = new ArrayList<>();
      for (SourceField sourceField : sourceFields) {
        if (sourceField != null && !Utils.isEmpty(sourceField.getName())) {
          names.add(sourceField.getName());
        }
      }
      Collections.sort(names);
      return names;
    } catch (HopException e) {
      return List.of();
    }
  }

  private void getAttributes() {
    String sourceName = wRecordSource.getText();
    if (Utils.isEmpty(sourceName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoSource.Message"));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoSource.Title"));
      mb.open();
      return;
    }

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
              PKG, "DvSatelliteDialog.GetAttributes.ErrorLoadingSource.Message", sourceName),
          e);
      return;
    }

    if (source == null || sourceFields == null || sourceFields.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvSatelliteDialog.GetAttributes.NoFields.Message", sourceName));
      mb.setText(BaseMessages.getString(PKG, "DvSatelliteDialog.GetAttributes.NoFields.Title"));
      mb.open();
      return;
    }

    String drivingKey = wDrivingKey.getText() != null ? wDrivingKey.getText().trim() : "";
    String drivingKeySourceField =
        wDrivingKeySourceField.getText() != null ? wDrivingKeySourceField.getText().trim() : "";

    String hubName = wHubName.getText() != null ? wHubName.getText().trim() : "";
    DvHub linkedHub = null;
    if (!Utils.isEmpty(hubName) && model != null) {
      linkedHub = model.findHub(hubName);
    }
    Set<String> excluded =
        linkedHub != null
            ? DvSatellite.excludedHubSatelliteSourceFieldNames(
                linkedHub, variables, drivingKey, drivingKeySourceField)
            : Collections.emptySet();

    for (SourceField sf : sourceFields) {
      String name = variables.resolve(sf.getName());
      if (excluded.contains(name)) {
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
