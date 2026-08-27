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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.lineage.LineageTabSupport;
import org.hopper.edw.datavault.lineage.BvModelLineageCollector;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2BuildMode;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMappingDialogSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2SatelliteConfig;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Tabbed dialog to edit a Business Vault SCD2 table, including multi-satellite field mappings. */
public class HopGuiBvScd2TableDialog {
  private static final Class<?> PKG = HopGuiBvScd2TableDialog.class;

  private final Shell parent;
  private final BvScd2Table input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private final int originalTableIndex;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private Text wTableName;
  private Combo wIncludeHashKey;
  private Combo wBuildMode;
  private Text wFunctionalTimestamp;
  private Text wIncrementalWatermark;
  private Text wValidFromField;
  private Text wValidToField;
  private TableView wDerivatives;
  private Button wAddDerivative;
  private Button wDeleteDerivative;
  private Label wlMappingsHint;
  private TableView wMappings;
  private Button wAddMapping;
  private Button wDeleteMapping;
  private Button wSuggestMappings;
  private TableView wSatelliteConfigs;
  private CTabFolder wTabFolder;

  private ColumnInfo mappingsSatelliteColumn;
  private ColumnInfo mappingsSourceColumn;
  private int margin;
  private int middle;
  private boolean ok;

  public HopGuiBvScd2TableDialog(
      Shell parent,
      BvScd2Table table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = table;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
    this.originalTableIndex =
        businessVaultModel != null ? businessVaultModel.getTables().indexOf(table) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiBvScd2TableDialog.Title", Const.NVL(input.getName(), input.getName())));
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
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_SCD2_TABLE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Description.Label"));
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
    wTabFolder.addListener(SWT.Selection, e -> refreshDynamicTabs());

    addGeneralTab();
    addDerivativesTab();
    addFieldMappingsTab();
    addSatelliteSettingsTab();
    addLineageTab();
    wTabFolder.setSelection(0);
    shell.layout(true, true);

    applyScd2FieldTooltips();
    getData();
    updateMappingsHint();

    BaseTransformDialog.setSize(shell, 720, 620);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addLineageTab() {
    LineageTabSupport.addLazyTab(
        wTabFolder,
        variables,
        margin,
        () -> {
          if (businessVaultModel == null) {
            return null;
          }
          return LineageTabSupport.findTable(
              BvModelLineageCollector.collect(businessVaultModel, variables), input.getName());
        });
  }

  private void addGeneralTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.General.ToolTip"));

    Label wlTableName = new Label(comp, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    wlTableName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlIncludeHashKey = new Label(comp, SWT.RIGHT);
    wlIncludeHashKey.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHashKey.Label"));
    PropsUi.setLook(wlIncludeHashKey);
    wlIncludeHashKey.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right(middle, -margin).result());

    wIncludeHashKey = new Combo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wIncludeHashKey);
    wIncludeHashKey.setItems(
        new String[] {
          BaseMessages.getString(PKG, "System.Combo.Yes"),
          BaseMessages.getString(PKG, "System.Combo.No")
        });
    wIncludeHashKey.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wTableName, margin).right().result());

    Label wlBuildMode = new Label(comp, SWT.RIGHT);
    wlBuildMode.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.BuildMode.Label"));
    PropsUi.setLook(wlBuildMode);
    wlBuildMode.setLayoutData(
        new FormDataBuilder().left().top(wIncludeHashKey, margin).right(middle, -margin).result());

    wBuildMode = new Combo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wBuildMode);
    EnumDialogSupport.populateCombo(wBuildMode, BvScd2BuildMode.class);
    wBuildMode.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wIncludeHashKey, margin).right().result());
    wBuildMode.addListener(SWT.Selection, e -> updateIncrementalFieldState());

    Label wlFunctionalTimestamp = new Label(comp, SWT.RIGHT);
    wlFunctionalTimestamp.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.FunctionalTimestamp.Label"));
    PropsUi.setLook(wlFunctionalTimestamp);
    wlFunctionalTimestamp.setLayoutData(
        new FormDataBuilder().left().top(wBuildMode, margin).right(middle, -margin).result());

    wFunctionalTimestamp = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFunctionalTimestamp);
    wFunctionalTimestamp.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wBuildMode, margin).right().result());

    Label wlIncrementalWatermark = new Label(comp, SWT.RIGHT);
    wlIncrementalWatermark.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncrementalWatermark.Label"));
    PropsUi.setLook(wlIncrementalWatermark);
    wlIncrementalWatermark.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wFunctionalTimestamp, margin)
            .right(middle, -margin)
            .result());

    wIncrementalWatermark = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wIncrementalWatermark);
    wIncrementalWatermark.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wFunctionalTimestamp, margin).right().result());

    Label wlValidFromField = new Label(comp, SWT.RIGHT);
    wlValidFromField.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ValidFromField.Label"));
    PropsUi.setLook(wlValidFromField);
    wlValidFromField.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wIncrementalWatermark, margin)
            .right(middle, -margin)
            .result());

    wValidFromField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wValidFromField);
    wValidFromField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wIncrementalWatermark, margin).right().result());

    Label wlValidToField = new Label(comp, SWT.RIGHT);
    wlValidToField.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ValidToField.Label"));
    PropsUi.setLook(wlValidToField);
    wlValidToField.setLayoutData(
        new FormDataBuilder().left().top(wValidFromField, margin).right(middle, -margin).result());

    wValidToField = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wValidToField);
    wValidToField.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wValidFromField, margin)
            .right()
            .bottom(100, margin)
            .result());
  }

  private void addDerivativesTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Derivatives.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Derivatives.ToolTip"));

    Label wlDerivatives = new Label(comp, SWT.LEFT);
    wlDerivatives.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.Label"));
    PropsUi.setLook(wlDerivatives);
    wlDerivatives.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    wAddDerivative = new Button(comp, SWT.PUSH);
    wAddDerivative.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.Add"));
    PropsUi.setLook(wAddDerivative);
    wAddDerivative.setLayoutData(new FormDataBuilder().left().top(wlDerivatives, margin).result());
    wAddDerivative.addListener(SWT.Selection, e -> addDerivativeRow());

    wDeleteDerivative = new Button(comp, SWT.PUSH);
    wDeleteDerivative.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.Delete"));
    PropsUi.setLook(wDeleteDerivative);
    wDeleteDerivative.setLayoutData(
        new FormDataBuilder().left(wAddDerivative, margin).top(wlDerivatives, margin).result());
    wDeleteDerivative.addListener(SWT.Selection, e -> removeDerivativeRows());

    ColumnInfo[] derivativeCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.Column.Name"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getEligibleDvTableNames(),
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.Column.Type"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    derivativeCols[1].setReadOnly(true);

    wDerivatives =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            derivativeCols,
            1,
            null,
            PropsUi.getInstance());
    wDerivatives.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wAddDerivative, margin)
            .right()
            .bottom(100, margin)
            .result());
    wDerivatives.optimizeTableView();

    boolean dvAvailable = dataVaultModel != null && !dataVaultModel.getTables().isEmpty();
    wAddDerivative.setEnabled(dvAvailable);
    if (!dvAvailable) {
      wlDerivatives.setText(
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.MissingDvModel"));
    }
  }

  private void addFieldMappingsTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.FieldMappings.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.FieldMappings.ToolTip"));

    wlMappingsHint = new Label(comp, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlMappingsHint);
    wlMappingsHint.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    wAddMapping = new Button(comp, SWT.PUSH);
    wAddMapping.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Add"));
    PropsUi.setLook(wAddMapping);
    wAddMapping.setLayoutData(new FormDataBuilder().left().top(wlMappingsHint, margin).result());
    wAddMapping.addListener(SWT.Selection, e -> addMappingRow());

    wDeleteMapping = new Button(comp, SWT.PUSH);
    wDeleteMapping.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Delete"));
    PropsUi.setLook(wDeleteMapping);
    wDeleteMapping.setLayoutData(
        new FormDataBuilder().left(wAddMapping, margin).top(wlMappingsHint, margin).result());
    wDeleteMapping.addListener(SWT.Selection, e -> removeMappingRows());

    wSuggestMappings = new Button(comp, SWT.PUSH);
    wSuggestMappings.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest"));
    PropsUi.setLook(wSuggestMappings);
    wSuggestMappings.setLayoutData(
        new FormDataBuilder().left(wDeleteMapping, margin).top(wlMappingsHint, margin).result());
    wSuggestMappings.addListener(SWT.Selection, e -> suggestMappings());

    mappingsSourceColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Column.SourceField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {},
            false);

    mappingsSatelliteColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Column.Satellite"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            getSatelliteNamesFromDerivativesTable(),
            false);

    ColumnInfo[] mappingCols =
        new ColumnInfo[] {
          mappingsSatelliteColumn,
          mappingsSourceColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Column.TargetField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    wMappings =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            mappingCols,
            1,
            null,
            PropsUi.getInstance());
    wMappings.setLayoutData(
        new FormDataBuilder().left().top(wAddMapping, margin).right().bottom(100, margin).result());
    wMappings.optimizeTableView();
    wMappings.table.addListener(SWT.Modify, e -> refreshMappingSourceCombos());
  }

  private void addSatelliteSettingsTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.SatelliteSettings.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.SatelliteSettings.ToolTip"));

    Label wlSatelliteSettings = new Label(comp, SWT.LEFT | SWT.WRAP);
    wlSatelliteSettings.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SatelliteSettings.Intro"));
    PropsUi.setLook(wlSatelliteSettings);
    wlSatelliteSettings.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    ColumnInfo[] configCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiBvScd2TableDialog.SatelliteSettings.Column.Satellite"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiBvScd2TableDialog.SatelliteSettings.Column.FunctionalTimestamp"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiBvScd2TableDialog.SatelliteSettings.Column.SourceIndicator"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    configCols[0].setReadOnly(true);

    wSatelliteConfigs =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            configCols,
            1,
            null,
            PropsUi.getInstance());
    wSatelliteConfigs.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlSatelliteSettings, margin)
            .right()
            .bottom(100, margin)
            .result());
    wSatelliteConfigs.optimizeTableView();
  }

  private void refreshDynamicTabs() {
    CTabItem selected = wTabFolder.getSelection();
    if (selected == null) {
      return;
    }
    String title = selected.getText();
    if (BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.FieldMappings.Label").equals(title)
        || BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.SatelliteSettings.Label")
            .equals(title)) {
      refreshSatelliteDependentTabs();
    }
  }

  private void refreshSatelliteDependentTabs() {
    String[] satelliteNames = getSatelliteNamesFromDerivativesTable();
    if (mappingsSatelliteColumn != null) {
      mappingsSatelliteColumn.setComboValues(satelliteNames);
      refreshMappingSourceCombos();
    }
    loadSatelliteConfigsTable(satelliteNames);
    updateMappingsHint();
  }

  private void refreshMappingSourceCombos() {
    if (wMappings == null || mappingsSourceColumn == null) {
      return;
    }
    Set<String> union = new LinkedHashSet<>();
    for (TableItem item : wMappings.getNonEmptyItems()) {
      String satelliteName = item.getText(1);
      union.addAll(
          BvScd2FieldMappingDialogSupport.satelliteAttributeNames(satelliteName, dataVaultModel));
    }
    if (union.isEmpty()) {
      for (String satelliteName : getSatelliteNamesFromDerivativesTable()) {
        union.addAll(
            BvScd2FieldMappingDialogSupport.satelliteAttributeNames(satelliteName, dataVaultModel));
      }
    }
    mappingsSourceColumn.setComboValues(union.toArray(new String[0]));
  }

  private void updateMappingsHint() {
    int satelliteCount = getSatelliteNamesFromDerivativesTable().length;
    if (satelliteCount > 1) {
      wlMappingsHint.setText(
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.MultiSatellite"));
    } else {
      wlMappingsHint.setText(
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.SingleSatellite"));
    }
  }

  private String[] getEligibleDvTableNames() {
    List<String> names = new ArrayList<>();
    if (dataVaultModel == null) {
      return names.toArray(new String[0]);
    }
    for (IDvTable table : dataVaultModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || !BusinessVaultDerivativeSupport.isValidDerivativePair(
              input.getTableType(), table.getTableType())) {
        continue;
      }
      names.add(table.getName());
    }
    return names.toArray(new String[0]);
  }

  private String[] getSatelliteNamesFromDerivativesTable() {
    if (wDerivatives == null) {
      return new String[0];
    }
    List<String> names = new ArrayList<>();
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String name = item.getText(1);
      if (!Utils.isEmpty(name)) {
        names.add(name);
      }
    }
    return names.toArray(new String[0]);
  }

  private void addDerivativeRow() {
    new TableItem(wDerivatives.table, SWT.NONE);
    wDerivatives.optimizeTableView();
    refreshSatelliteDependentTabs();
  }

  private void removeDerivativeRows() {
    int idx = wDerivatives.getSelectionIndex();
    if (idx >= 0) {
      wDerivatives.table.remove(idx);
      wDerivatives.optimizeTableView();
      refreshSatelliteDependentTabs();
    }
  }

  private void addMappingRow() {
    new TableItem(wMappings.table, SWT.NONE);
    wMappings.optimizeTableView();
    refreshMappingSourceCombos();
  }

  private void removeMappingRows() {
    int idx = wMappings.getSelectionIndex();
    if (idx >= 0) {
      wMappings.table.remove(idx);
      wMappings.optimizeTableView();
    }
  }

  private void suggestMappings() {
    applyDerivativesToTable(input);
    input.getFieldMappings().clear();
    for (TableItem item : wMappings.getNonEmptyItems()) {
      String satelliteName = item.getText(1);
      String sourceFieldName = item.getText(2);
      String targetFieldName = item.getText(3);
      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)) {
        continue;
      }
      input
          .getFieldMappings()
          .add(new BvScd2FieldMapping(satelliteName, sourceFieldName, targetFieldName));
    }
    input
        .getFieldMappings()
        .addAll(BvScd2FieldMappingDialogSupport.suggestMappings(input, dataVaultModel));
    loadMappingsTable();
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getTableName())) {
      wTableName.setText(input.getTableName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    wIncludeHashKey.select(input.isIncludeHashKey() ? 0 : 1);
    EnumDialogSupport.selectCombo(wBuildMode, input.getBuildModeOrDefault());
    if (!Utils.isEmpty(input.getFunctionalTimestampField())) {
      wFunctionalTimestamp.setText(input.getFunctionalTimestampField());
    }
    if (!Utils.isEmpty(input.getIncrementalWatermarkField())) {
      wIncrementalWatermark.setText(input.getIncrementalWatermarkField());
    }
    updateIncrementalFieldState();
    if (!Utils.isEmpty(input.getValidFromField())) {
      wValidFromField.setText(input.getValidFromField());
    }
    if (!Utils.isEmpty(input.getValidToField())) {
      wValidToField.setText(input.getValidToField());
    }

    wDerivatives.clearAll();
    for (BvDerivativeRef derivative : input.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      TableItem item = new TableItem(wDerivatives.table, SWT.NONE);
      item.setText(1, derivative.getDvTableName());
      if (derivative.getDvTableType() != null) {
        item.setText(2, derivative.getDvTableType().getDescription());
      }
    }
    wDerivatives.optimizeTableView();
    loadMappingsTable();
    refreshSatelliteDependentTabs();
  }

  private void loadMappingsTable() {
    wMappings.clearAll();
    for (BvScd2FieldMapping mapping : input.getFieldMappings()) {
      if (mapping == null
          || Utils.isEmpty(mapping.getSatelliteName())
          || Utils.isEmpty(mapping.getSourceFieldName())) {
        continue;
      }
      TableItem item = new TableItem(wMappings.table, SWT.NONE);
      item.setText(1, mapping.getSatelliteName());
      item.setText(2, Const.NVL(mapping.getSourceFieldName(), ""));
      item.setText(3, Const.NVL(mapping.getTargetFieldName(), ""));
    }
    wMappings.optimizeTableView();
    refreshMappingSourceCombos();
  }

  private void loadSatelliteConfigsTable(String[] satelliteNames) {
    wSatelliteConfigs.clearAll();
    List<BvScd2SatelliteConfig> synced =
        BvScd2FieldMappingDialogSupport.syncSatelliteConfigs(input, List.of(satelliteNames));
    for (BvScd2SatelliteConfig config : synced) {
      TableItem item = new TableItem(wSatelliteConfigs.table, SWT.NONE);
      item.setText(1, config.getSatelliteName());
      item.setText(2, Const.NVL(config.getFunctionalTimestampField(), ""));
      item.setText(3, Const.NVL(config.getSourceIndicatorValue(), ""));
    }
    wSatelliteConfigs.optimizeTableView();
  }

  private void applyDerivativesToTable(BvScd2Table target) {
    target.getDerivatives().clear();
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String dvName = item.getText(1);
      if (Utils.isEmpty(dvName)) {
        continue;
      }
      DvTableType dvType = null;
      if (dataVaultModel != null) {
        IDvTable dvTable = dataVaultModel.findTable(dvName);
        if (dvTable != null) {
          dvType = dvTable.getTableType();
        }
      }
      if (dvType == null && !Utils.isEmpty(item.getText(2))) {
        dvType = DvTableType.lookupDescription(item.getText(2));
        if (dvType == null) {
          dvType = DvTableType.lookupCode(item.getText(2));
        }
      }
      if (dvType != null
          && BusinessVaultDerivativeSupport.isValidDerivativePair(target.getTableType(), dvType)
          && !BusinessVaultDerivativeSupport.hasDerivative(target, dvName)) {
        target.getDerivatives().add(new BvDerivativeRef(dvName, dvType));
      }
    }
  }

  private void ok() {
    applyWidgetsToTable(input);

    List<ICheckResult> remarks =
        BvScd2FieldMappingDialogSupport.validateForDialog(
            input, businessVaultModel, dataVaultModel, variables);
    if (BvScd2FieldMappingDialogSupport.hasValidationErrors(remarks)) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ValidationError.Title"),
          BvScd2FieldMappingDialogSupport.formatValidationErrors(remarks),
          null);
      return;
    }

    ok = true;
    dispose();
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                BusinessVaultModel draft =
                    ModelDialogValidationSupport.cloneBusinessVaultModel(
                        businessVaultModel, HopGui.getInstance().getMetadataProvider());
                BvScd2Table draftTable = locateDraftTable(draft);
                applyWidgetsToTable(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draftTable.check(
                    tableRemarks,
                    HopGui.getInstance().getMetadataProvider(),
                    variables,
                    draft,
                    dataVaultModel);
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

  private BvScd2Table locateDraftTable(BusinessVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    IBvTable table = draft.getTables().get(originalTableIndex);
    if (!(table instanceof BvScd2Table scd2Table)) {
      throw new HopException("Validation model table type mismatch");
    }
    return scd2Table;
  }

  private void applyWidgetsToTable(BvScd2Table target) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    target.setTableName(wTableName.getText());
    target.setIncludeHashKey(wIncludeHashKey.getSelectionIndex() == 0);
    target.setBuildMode(
        EnumDialogSupport.readCombo(
            wBuildMode, BvScd2BuildMode.class, BvScd2BuildMode.FULL_REBUILD));
    target.setFunctionalTimestampField(wFunctionalTimestamp.getText());
    target.setIncrementalWatermarkField(wIncrementalWatermark.getText());
    target.setValidFromField(wValidFromField.getText());
    target.setValidToField(wValidToField.getText());

    applyDerivativesToTable(target);
    Set<String> activeSatellites = new HashSet<>(List.of(getSatelliteNamesFromDerivativesTable()));
    BvScd2FieldMappingDialogSupport.pruneMappingsAndConfigs(target, activeSatellites);

    target.getFieldMappings().clear();
    for (TableItem item : wMappings.getNonEmptyItems()) {
      String satelliteName = item.getText(1);
      String sourceFieldName = item.getText(2);
      String targetFieldName = item.getText(3);
      if (Utils.isEmpty(satelliteName)
          || Utils.isEmpty(sourceFieldName)
          || Utils.isEmpty(targetFieldName)) {
        continue;
      }
      target
          .getFieldMappings()
          .add(new BvScd2FieldMapping(satelliteName, sourceFieldName, targetFieldName));
    }

    target.getSatelliteConfigs().clear();
    for (TableItem item : wSatelliteConfigs.getNonEmptyItems()) {
      String satelliteName = item.getText(1);
      if (Utils.isEmpty(satelliteName)) {
        continue;
      }
      BvScd2SatelliteConfig config = new BvScd2SatelliteConfig(satelliteName);
      config.setFunctionalTimestampField(item.getText(2));
      config.setSourceIndicatorValue(item.getText(3));
      target.getSatelliteConfigs().add(config);
    }
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

  private void updateIncrementalFieldState() {
    boolean incremental =
        EnumDialogSupport.readCombo(wBuildMode, BvScd2BuildMode.class, BvScd2BuildMode.FULL_REBUILD)
            == BvScd2BuildMode.INCREMENTAL;
    wIncrementalWatermark.setEnabled(incremental);
  }

  private void applyScd2FieldTooltips() {
    BusinessVaultConfiguration config =
        businessVaultModel != null
            ? businessVaultModel.getConfigurationOrDefault()
            : new BusinessVaultConfiguration();
    wBuildMode.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.BuildMode.Tooltip"));
    wIncrementalWatermark.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncrementalWatermark.Tooltip"));
    wFunctionalTimestamp.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvScd2TableDialog.FunctionalTimestamp.Tooltip",
            Const.NVL(config.getFunctionalTimestampField(), ""),
            Const.NVL(config.getLoadDateFieldFallback(), "LOAD_DATE")));
    wValidFromField.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvScd2TableDialog.ValidFromField.Tooltip",
            Const.NVL(
                config.getValidFromField(), BusinessVaultConfiguration.DEFAULT_VALID_FROM_FIELD)));
    wValidToField.setToolTipText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvScd2TableDialog.ValidToField.Tooltip",
            Const.NVL(
                config.getValidToField(), BusinessVaultConfiguration.DEFAULT_VALID_TO_FIELD)));
  }
}
