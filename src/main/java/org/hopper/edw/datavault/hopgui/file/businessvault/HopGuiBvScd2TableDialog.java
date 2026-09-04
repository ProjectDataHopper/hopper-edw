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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
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
import org.hopper.edw.datavault.expression.SqlExpressionDraft;
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.file.vault.TableViewPopulateSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.lineage.LineageTabSupport;
import org.hopper.edw.datavault.lineage.BvModelLineageCollector;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultSourceQuerySupport;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2BuildMode;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Calculation;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2CalculationUnitTestSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMappingDialogSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2HashPartitionCount;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2SatelliteConfig;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQueryRef;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionEditorDialog;

/** Tabbed dialog to edit a Business Vault SCD2 table, including multi-satellite field mappings. */
public class HopGuiBvScd2TableDialog {
  private static final Class<?> PKG = HopGuiBvScd2TableDialog.class;

  private final Shell parent;
  private final BvScd2Table input;
  private final BusinessVaultModel businessVaultModel;
  private DataVaultModel dataVaultModel;
  private String dataVaultLoadError;
  private final IVariables variables;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private Text wTableName;
  private Button wIncludeHashKey;
  private Button wIncludeHubBusinessKeys;
  private Button wLoadHubBusinessKeys;
  private Combo wParentHubName;
  private Label wlParentHubName;
  private Combo wBuildMode;
  private Combo wHashKeyPartitions;
  private TextVar wSqlExpressionCopies;
  private Text wFunctionalTimestamp;
  private Text wIncrementalWatermark;
  private Text wValidFromField;
  private Text wValidToField;
  private TableView wDerivatives;
  private Button wAddDerivative;
  private Button wDeleteDerivative;
  private TableView wSourceQueries;
  private Button wAddSourceQuery;
  private Button wDeleteSourceQuery;
  private Label wlMappingsHint;
  private TableView wMappings;
  private Button wAddMapping;
  private Button wDeleteMapping;
  private Button wSuggestMappings;
  private TableView wSatelliteConfigs;
  private TableView wCalculations;
  private Label wlUnitTestArtifacts;
  private CTabFolder wTabFolder;

  private ColumnInfo mappingsSatelliteColumn;
  private ColumnInfo mappingsSourceColumn;
  private int margin;
  private int middle;
  private boolean ok;

  private record SuggestSourceChoice(String name, boolean sourceQuery) {}

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
    wName.addModifyListener(e -> refreshUnitTestArtifactLabel());

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
    addCalculationsTab();
    addCalculationTestsTab();
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
          BvScd2Table draft = new BvScd2Table();
          applyWidgetsToTable(draft);
          return BvModelLineageCollector.collectScd2Table(
              draft, businessVaultModel, currentDataVaultModel(false), variables);
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
    wlIncludeHashKey.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHashKey.Tooltip"));
    PropsUi.setLook(wlIncludeHashKey);
    wlIncludeHashKey.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right(middle, -margin).result());

    wIncludeHashKey = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wIncludeHashKey);
    wIncludeHashKey.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHashKey.Tooltip"));
    wIncludeHashKey.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wlIncludeHashKey, 0, SWT.CENTER)
            .right()
            .result());
    wIncludeHashKey.addListener(SWT.Selection, e -> updateHubBusinessKeyOptionState());

    wlParentHubName = new Label(comp, SWT.RIGHT);
    wlParentHubName.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ParentHub.Label"));
    wlParentHubName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ParentHub.Tooltip"));
    PropsUi.setLook(wlParentHubName);
    wlParentHubName.setLayoutData(
        new FormDataBuilder().left().top(wlIncludeHashKey, margin).right(middle, -margin).result());

    wParentHubName = new Combo(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wParentHubName);
    wParentHubName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ParentHub.Tooltip"));
    wParentHubName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wlIncludeHashKey, margin).right().result());
    populateParentHubCombo();

    Label wlIncludeHubBusinessKeys = new Label(comp, SWT.RIGHT);
    wlIncludeHubBusinessKeys.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHubBusinessKeys.Label"));
    wlIncludeHubBusinessKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHubBusinessKeys.Tooltip"));
    PropsUi.setLook(wlIncludeHubBusinessKeys);
    wlIncludeHubBusinessKeys.setLayoutData(
        new FormDataBuilder().left().top(wParentHubName, margin).right(middle, -margin).result());

    wIncludeHubBusinessKeys = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wIncludeHubBusinessKeys);
    wIncludeHubBusinessKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.IncludeHubBusinessKeys.Tooltip"));
    wIncludeHubBusinessKeys.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wlIncludeHubBusinessKeys, 0, SWT.CENTER)
            .right()
            .result());
    wIncludeHubBusinessKeys.addListener(SWT.Selection, e -> updateHubBusinessKeyOptionState());

    Label wlLoadHubBusinessKeys = new Label(comp, SWT.RIGHT);
    wlLoadHubBusinessKeys.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.LoadHubBusinessKeys.Label"));
    wlLoadHubBusinessKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.LoadHubBusinessKeys.Tooltip"));
    PropsUi.setLook(wlLoadHubBusinessKeys);
    wlLoadHubBusinessKeys.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlIncludeHubBusinessKeys, margin)
            .right(middle, -margin)
            .result());

    wLoadHubBusinessKeys = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wLoadHubBusinessKeys);
    wLoadHubBusinessKeys.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.LoadHubBusinessKeys.Tooltip"));
    wLoadHubBusinessKeys.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wlLoadHubBusinessKeys, 0, SWT.CENTER)
            .right()
            .result());

    Label wlBuildMode = new Label(comp, SWT.RIGHT);
    wlBuildMode.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.BuildMode.Label"));
    PropsUi.setLook(wlBuildMode);
    wlBuildMode.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlLoadHubBusinessKeys, margin)
            .right(middle, -margin)
            .result());

    wBuildMode = new Combo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wBuildMode);
    EnumDialogSupport.populateCombo(wBuildMode, BvScd2BuildMode.class);
    wBuildMode.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wlLoadHubBusinessKeys, margin).right().result());
    wBuildMode.addListener(SWT.Selection, e -> updateIncrementalFieldState());

    Label wlHashKeyPartitions = new Label(comp, SWT.RIGHT);
    wlHashKeyPartitions.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.HashKeyPartitions.Label"));
    PropsUi.setLook(wlHashKeyPartitions);
    wlHashKeyPartitions.setLayoutData(
        new FormDataBuilder().left().top(wBuildMode, margin).right(middle, -margin).result());

    wHashKeyPartitions = new Combo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wHashKeyPartitions);
    EnumDialogSupport.populateCombo(wHashKeyPartitions, BvScd2HashPartitionCount.class);
    wHashKeyPartitions.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wBuildMode, margin).right().result());

    Label wlSqlExpressionCopies = new Label(comp, SWT.RIGHT);
    wlSqlExpressionCopies.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SqlExpressionCopies.Label"));
    PropsUi.setLook(wlSqlExpressionCopies);
    wlSqlExpressionCopies.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wHashKeyPartitions, margin)
            .right(middle, -margin)
            .result());

    wSqlExpressionCopies = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSqlExpressionCopies);
    wSqlExpressionCopies.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wHashKeyPartitions, margin).right().result());

    Label wlFunctionalTimestamp = new Label(comp, SWT.RIGHT);
    wlFunctionalTimestamp.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.FunctionalTimestamp.Label"));
    PropsUi.setLook(wlFunctionalTimestamp);
    wlFunctionalTimestamp.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wSqlExpressionCopies, margin)
            .right(middle, -margin)
            .result());

    wFunctionalTimestamp = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFunctionalTimestamp);
    wFunctionalTimestamp.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSqlExpressionCopies, margin).right().result());

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
        new FormDataBuilder().left(middle, 0).top(wValidFromField, margin).right().result());
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
            .bottom(55, -margin)
            .result());
    wDerivatives.optimizeTableView();
    wDerivatives.table.addListener(
        SWT.Modify,
        e -> {
          fillDerivativeTypes();
          refreshSatelliteDependentTabs();
        });

    boolean dvAvailable = dataVaultModel != null && !dataVaultModel.getTables().isEmpty();
    wAddDerivative.setEnabled(dvAvailable);
    if (!dvAvailable) {
      wlDerivatives.setText(
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Derivatives.MissingDvModel"));
    }

    Label wlSourceQueries = new Label(comp, SWT.LEFT);
    wlSourceQueries.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SourceQueries.Label"));
    PropsUi.setLook(wlSourceQueries);
    wlSourceQueries.setLayoutData(
        new FormDataBuilder().left().top(wDerivatives, margin).right().result());

    wAddSourceQuery = new Button(comp, SWT.PUSH);
    wAddSourceQuery.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SourceQueries.Add"));
    PropsUi.setLook(wAddSourceQuery);
    wAddSourceQuery.setLayoutData(
        new FormDataBuilder().left().top(wlSourceQueries, margin).result());
    wAddSourceQuery.addListener(SWT.Selection, e -> addSourceQueryRow());

    wDeleteSourceQuery = new Button(comp, SWT.PUSH);
    wDeleteSourceQuery.setText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SourceQueries.Delete"));
    PropsUi.setLook(wDeleteSourceQuery);
    wDeleteSourceQuery.setLayoutData(
        new FormDataBuilder().left(wAddSourceQuery, margin).top(wlSourceQueries, margin).result());
    wDeleteSourceQuery.addListener(SWT.Selection, e -> removeSourceQueryRows());

    ColumnInfo[] sourceQueryCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SourceQueries.Column.Name"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getEligibleSourceQueryNames(),
              false),
        };
    wSourceQueries =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            sourceQueryCols,
            1,
            null,
            PropsUi.getInstance());
    wSourceQueries.setLayoutData(
        new FormDataBuilder().left().top(wAddSourceQuery, margin).right().bottom().result());
    wSourceQueries.optimizeTableView();
    wSourceQueries.table.addListener(SWT.Modify, e -> refreshSatelliteDependentTabs());
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
    wSuggestMappings.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.Tooltip"));
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
    mappingsSourceColumn.setComboValuesSelectionListener(
        (item, rowNr, colNr) -> sourceFieldChoicesForRow(item));

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

  private void addCalculationsTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Calculations.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Calculations.ToolTip"));

    Label hint = new Label(comp, SWT.LEFT | SWT.WRAP);
    hint.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Hint"));
    PropsUi.setLook(hint);
    hint.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    Button wAdd = new Button(comp, SWT.PUSH);
    wAdd.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Add"));
    PropsUi.setLook(wAdd);
    wAdd.setLayoutData(new FormDataBuilder().left().top(hint, margin).result());
    wAdd.addListener(SWT.Selection, e -> openCalculationEditor(-1));

    Button wEdit = new Button(comp, SWT.PUSH);
    wEdit.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Edit"));
    PropsUi.setLook(wEdit);
    wEdit.setLayoutData(new FormDataBuilder().left(wAdd, margin).top(hint, margin).result());
    wEdit.addListener(SWT.Selection, e -> openCalculationEditor(wCalculations.getSelectionIndex()));

    Button wDelete = new Button(comp, SWT.PUSH);
    wDelete.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Delete"));
    PropsUi.setLook(wDelete);
    wDelete.setLayoutData(new FormDataBuilder().left(wEdit, margin).top(hint, margin).result());
    wDelete.addListener(
        SWT.Selection,
        e -> {
          int idx = wCalculations.getSelectionIndex();
          if (idx >= 0) {
            wCalculations.table.remove(idx);
            wCalculations.optimizeTableView();
          }
        });

    ColumnInfo expressionCol =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Expression"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    expressionCol.setReadOnly(true);
    ColumnInfo[] cols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Target"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          expressionCol,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Type"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiBvScd2TableDialog.Calculations.Column.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };
    wCalculations =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            cols,
            1,
            null,
            PropsUi.getInstance());
    wCalculations.setLayoutData(
        new FormDataBuilder().left().top(wAdd, margin).right().bottom(100, margin).result());
    wCalculations.optimizeTableView();
    wCalculations.table.addListener(
        SWT.DefaultSelection, e -> openCalculationEditor(wCalculations.getSelectionIndex()));
  }

  private void openCalculationEditor(int rowIndex) {
    SqlExpressionDraft draft = new SqlExpressionDraft();
    if (rowIndex >= 0 && rowIndex < wCalculations.table.getItemCount()) {
      TableItem item = wCalculations.table.getItem(rowIndex);
      draft.setFieldName(item.getText(1));
      draft.setExpression(item.getText(2));
      draft.setHopTypeName(item.getText(3));
      draft.setLength(Const.toInt(item.getText(4), -1));
      draft.setPrecision(Const.toInt(item.getText(5), -1));
      draft.setDescription(item.getText(6));
    }
    IRowMeta compileMeta = calculationCompileRowMeta();
    SqlExpressionEditorDialog editor =
        new SqlExpressionEditorDialog(
            shell, variables, draft, calculationFieldNames(compileMeta), compileMeta, true);
    SqlExpressionDraft result = editor.open();
    if (result == null) {
      return;
    }
    TableItem item;
    if (rowIndex >= 0 && rowIndex < wCalculations.table.getItemCount()) {
      item = wCalculations.table.getItem(rowIndex);
    } else {
      item = new TableItem(wCalculations.table, SWT.NONE);
    }
    item.setText(1, Const.NVL(result.getFieldName(), ""));
    item.setText(2, Const.NVL(result.getExpression(), ""));
    item.setText(3, Const.NVL(result.getHopTypeName(), ""));
    item.setText(4, result.getLength() >= 0 ? String.valueOf(result.getLength()) : "");
    item.setText(5, result.getPrecision() >= 0 ? String.valueOf(result.getPrecision()) : "");
    item.setText(6, Const.NVL(result.getDescription(), ""));
    wCalculations.optimizeTableView();
  }

  private IRowMeta calculationCompileRowMeta() {
    try {
      BvScd2Table draftTable = new BvScd2Table();
      applyWidgetsToTable(draftTable);
      return BvScd2PipelineSupport.buildCollapseRowLayout(
          draftTable,
          businessVaultModel != null
              ? businessVaultModel.getConfigurationOrDefault()
              : new BusinessVaultConfiguration(),
          currentDataVaultModel(false),
          businessVaultModel,
          variables);
    } catch (Exception e) {
      return null;
    }
  }

  private String[] calculationFieldNames(IRowMeta compileMeta) {
    List<String> names = new ArrayList<>();
    if (compileMeta != null) {
      for (int i = 0; i < compileMeta.size(); i++) {
        String name = compileMeta.getValueMeta(i).getName();
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
    }
    if (wMappings != null) {
      for (TableItem item : wMappings.getNonEmptyItems()) {
        String target = item.getText(3);
        if (!Utils.isEmpty(target) && !names.contains(target)) {
          names.add(target);
        }
      }
    }
    if (wCalculations != null) {
      for (TableItem item : wCalculations.getNonEmptyItems()) {
        String target = item.getText(1);
        if (!Utils.isEmpty(target) && !names.contains(target)) {
          names.add(target);
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private void addCalculationTestsTab() {
    Composite comp =
        HopGuiBusinessVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Tests.Label"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Tests.ToolTip"));

    Label wlHint = new Label(comp, SWT.WRAP);
    wlHint.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tests.Hint"));
    PropsUi.setLook(wlHint);
    wlHint.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    Button wGenerate = new Button(comp, SWT.PUSH);
    wGenerate.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tests.Generate.Label"));
    PropsUi.setLook(wGenerate);
    wGenerate.setLayoutData(new FormDataBuilder().left().top(wlHint, margin).result());
    wGenerate.addListener(SWT.Selection, e -> generateCalculationUnitTest());

    wlUnitTestArtifacts = new Label(comp, SWT.WRAP);
    PropsUi.setLook(wlUnitTestArtifacts);
    wlUnitTestArtifacts.setLayoutData(
        new FormDataBuilder().left().top(wGenerate, margin).right().bottom().result());
    refreshUnitTestArtifactLabel();
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
      currentDataVaultModel(true);
      refreshSatelliteDependentTabs();
    }
    if (BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Tab.Tests.Label").equals(title)) {
      refreshUnitTestArtifactLabel();
    }
  }

  private void refreshUnitTestArtifactLabel() {
    if (wlUnitTestArtifacts == null || wlUnitTestArtifacts.isDisposed()) {
      return;
    }
    String tableName = wName != null ? wName.getText() : input.getName();
    var names = BvScd2CalculationUnitTestSupport.namesFor(tableName);
    wlUnitTestArtifacts.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvScd2TableDialog.Tests.Artifacts",
            names.unitTestName(),
            names.collapseDataSetName(),
            names.calculatedDataSetName(),
            names.unitTestPipelineName() + ".hpl",
            names.capturePipelineName() + ".hpl"));
  }

  private void generateCalculationUnitTest() {
    applyWidgetsToTable(input);
    HopGui hopGui = HopGui.getInstance();
    if (hopGui == null) {
      return;
    }
    BvScd2CalculationUnitTestGuiSupport.generate(
        hopGui, shell, variables, businessVaultModel, currentDataVaultModel(true), input);
  }

  private void refreshSatelliteDependentTabs() {
    String[] satelliteNames = getSatelliteNamesFromDerivativesTable();
    if (mappingsSatelliteColumn != null) {
      mappingsSatelliteColumn.setComboValues(satelliteNames);
      refreshMappingSourceCombos();
    }
    loadSatelliteConfigsTable(satelliteNames);
    updateMappingsHint();
    suggestParentHubFromSatellites();
  }

  private void suggestParentHubFromSatellites() {
    if (wParentHubName == null
        || wParentHubName.isDisposed()
        || !Utils.isEmpty(wParentHubName.getText())) {
      return;
    }
    DataVaultModel dv = currentDataVaultModel(false);
    if (dv == null || wDerivatives == null) {
      return;
    }
    String inferred = null;
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      IDvTable table = dv.findTable(name);
      if (!(table instanceof DvSatellite satellite) || Utils.isEmpty(satellite.getHubName())) {
        continue;
      }
      if (inferred == null) {
        inferred = satellite.getHubName();
      } else if (!inferred.equals(satellite.getHubName())) {
        return;
      }
    }
    if (!Utils.isEmpty(inferred)) {
      wParentHubName.setText(inferred);
    }
  }

  private void refreshMappingSourceCombos() {
    if (wMappings == null || mappingsSourceColumn == null) {
      return;
    }
    mappingsSourceColumn.setComboValues(sourceFieldChoicesForRow(null));
  }

  private String[] sourceFieldChoicesForRow(TableItem item) {
    String satelliteName = item != null ? item.getText(1) : "";
    DataVaultModel dv = currentDataVaultModel(false);
    IHopMetadataProvider provider = metadataProvider();
    if (!Utils.isEmpty(satelliteName)) {
      List<String> names =
          BvScd2FieldMappingDialogSupport.satelliteAttributeNames(
              satelliteName, dv, businessVaultModel, variables, provider);
      return names.toArray(new String[0]);
    }
    Set<String> union = new LinkedHashSet<>();
    for (String name : getSatelliteNamesFromDerivativesTable()) {
      union.addAll(
          BvScd2FieldMappingDialogSupport.satelliteAttributeNames(
              name, dv, businessVaultModel, variables, provider));
    }
    return union.toArray(new String[0]);
  }

  private void updateMappingsHint() {
    if (wlMappingsHint == null || wlMappingsHint.isDisposed()) {
      return;
    }
    boolean hasDvDerivatives = hasNonEmptyTableItems(wDerivatives);
    boolean hasSourceQueries = hasNonEmptyTableItems(wSourceQueries);
    if (!Utils.isEmpty(dataVaultLoadError) && hasDvDerivatives && !hasSourceQueries) {
      wlMappingsHint.setText(
          BaseMessages.getString(
              PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.LoadError", dataVaultLoadError));
      if (wSuggestMappings != null && !wSuggestMappings.isDisposed()) {
        wSuggestMappings.setEnabled(false);
      }
      return;
    }
    BvScd2Table draft = new BvScd2Table();
    applyDerivativesToTable(draft);
    applySourceQueriesToTable(draft);
    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion = analyzeMappings(draft, false);
    int satelliteCount = getSatelliteNamesFromDerivativesTable().length;
    String counts = suggestion.attributeCountSummary();
    if (!suggestion.dvModelPresent() && hasDvDerivatives && !hasSourceQueries) {
      wlMappingsHint.setText(
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.NoDvModel"));
    } else if (satelliteCount > 1) {
      wlMappingsHint.setText(
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Hint.MultiSatellite",
              suggestion.dvTableCount(),
              Utils.isEmpty(counts)
                  ? BaseMessages.getString(
                      PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.NoAttributes")
                  : counts));
    } else {
      wlMappingsHint.setText(
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Hint.SingleSatellite",
              suggestion.dvTableCount(),
              Utils.isEmpty(counts)
                  ? BaseMessages.getString(
                      PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.NoAttributes")
                  : counts));
    }
    if (wSuggestMappings != null && !wSuggestMappings.isDisposed()) {
      wSuggestMappings.setEnabled(satelliteCount > 0);
    }
  }

  private String[] getEligibleDvTableNames() {
    List<String> names = new ArrayList<>();
    DataVaultModel dv = currentDataVaultModel(false);
    if (dv == null) {
      return names.toArray(new String[0]);
    }
    for (IDvTable table : dv.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || table.getTableType() != DvTableType.SATELLITE) {
        continue;
      }
      names.add(table.getName());
    }
    return names.toArray(new String[0]);
  }

  private String[] getSatelliteNamesFromDerivativesTable() {
    List<String> names = new ArrayList<>();
    if (wDerivatives != null) {
      for (TableItem item : wDerivatives.getNonEmptyItems()) {
        String name = item.getText(1);
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
    }
    if (wSourceQueries != null) {
      for (TableItem item : wSourceQueries.getNonEmptyItems()) {
        String name = item.getText(1);
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] getEligibleSourceQueryNames() {
    return BusinessVaultSourceQuerySupport.listSourceQueryNames(businessVaultModel)
        .toArray(new String[0]);
  }

  private void addSourceQueryRow() {
    new TableItem(wSourceQueries.table, SWT.NONE);
    wSourceQueries.optimizeTableView();
    refreshSatelliteDependentTabs();
  }

  private void removeSourceQueryRows() {
    int idx = wSourceQueries.getSelectionIndex();
    if (idx >= 0) {
      wSourceQueries.table.remove(idx);
      wSourceQueries.optimizeTableView();
      refreshSatelliteDependentTabs();
    }
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
    BvScd2Table draft = new BvScd2Table();
    applyWidgetsToTable(draft);
    List<SuggestSourceChoice> choices = listSuggestSourceChoices(draft);
    Collection<String> selectedSources = null;
    if (!choices.isEmpty()) {
      selectedSources = promptSourcesToSuggest(choices);
      if (selectedSources == null) {
        return;
      }
    }
    boolean hasDvDerivatives = !draft.getDerivatives().isEmpty();
    boolean hasSourceQueries = !draft.getSourceQueryRefs().isEmpty();
    BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
        analyzeMappings(draft, true, selectedSources);
    updateMappingsHint();

    if (!Utils.isEmpty(dataVaultLoadError)
        && hasDvDerivatives
        && suggestion.resolvedNames().isEmpty()) {
      showMappingMessage(
          SWT.ICON_ERROR,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(
              PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.LoadError", dataVaultLoadError));
      return;
    }
    if (suggestion.resolvedNames().isEmpty()
        && hasDvDerivatives
        && !hasSourceQueries
        && (!suggestion.dvModelPresent() || suggestion.dvTableCount() == 0)) {
      showMappingMessage(
          SWT.ICON_ERROR,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.NoDvModel"));
      return;
    }
    if (suggestion.resolvedNames().isEmpty()) {
      String missing = String.join(", ", suggestion.missingNames());
      showMappingMessage(
          SWT.ICON_ERROR,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Suggest.Unresolved",
              Utils.isEmpty(missing)
                  ? BaseMessages.getString(
                      PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.NoAttributes")
                  : missing));
      return;
    }
    if (suggestion.suggestedMappings().isEmpty() && !suggestion.emptyAttributeNames().isEmpty()) {
      showMappingMessage(
          SWT.ICON_ERROR,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Suggest.NoAttributes",
              String.join(", ", suggestion.emptyAttributeNames())));
      return;
    }
    if (suggestion.suggestedMappings().isEmpty()) {
      showMappingMessage(
          SWT.ICON_INFORMATION,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Suggest.NoneNew",
              suggestion.alreadyMappedCount()));
      return;
    }

    for (BvScd2FieldMapping mapping : suggestion.suggestedMappings()) {
      TableItem item = new TableItem(wMappings.table, SWT.NONE);
      item.setText(1, Const.NVL(mapping.getSatelliteName(), ""));
      item.setText(2, Const.NVL(mapping.getSourceFieldName(), ""));
      item.setText(3, Const.NVL(mapping.getTargetFieldName(), ""));
    }
    wMappings.optimizeTableView();
    refreshMappingSourceCombos();
    updateMappingsHint();
    String extra = "";
    if (!suggestion.emptyAttributeNames().isEmpty()) {
      extra =
          BaseMessages.getString(
              PKG,
              "HopGuiBvScd2TableDialog.Mappings.Suggest.PartialEmpty",
              String.join(", ", suggestion.emptyAttributeNames()));
    }
    showMappingMessage(
        SWT.ICON_INFORMATION,
        "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
        BaseMessages.getString(
                PKG,
                "HopGuiBvScd2TableDialog.Mappings.Suggest.Added",
                suggestion.suggestedMappings().size(),
                suggestion.attributeCountSummary())
            + extra);
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
    wIncludeHashKey.setSelection(input.isIncludeHashKey());
    wIncludeHubBusinessKeys.setSelection(input.isIncludeHubBusinessKeys());
    wLoadHubBusinessKeys.setSelection(input.isLoadHubBusinessKeys());
    populateParentHubCombo();
    String parentHub = input.getParentHubName();
    if (Utils.isEmpty(parentHub)) {
      parentHub = BusinessVaultDerivativeSupport.findHubDerivativeName(input);
    }
    if (!Utils.isEmpty(parentHub)) {
      wParentHubName.setText(parentHub);
    }
    updateHubBusinessKeyOptionState();
    EnumDialogSupport.selectCombo(wBuildMode, input.getBuildModeOrDefault());
    EnumDialogSupport.selectCombo(wHashKeyPartitions, input.getHashKeyPartitionCountOrDefault());
    wSqlExpressionCopies.setText(Const.NVL(input.getSqlExpressionCopyCount(), ""));
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
      if (derivative == null
          || Utils.isEmpty(derivative.getDvTableName())
          || BusinessVaultDerivativeSupport.isHubDerivative(derivative)) {
        continue;
      }
      TableItem item = new TableItem(wDerivatives.table, SWT.NONE);
      item.setText(1, derivative.getDvTableName());
      if (derivative.getDvTableType() != null) {
        item.setText(2, derivative.getDvTableType().getDescription());
      }
    }
    wDerivatives.optimizeTableView();
    wSourceQueries.clearAll();
    for (BvSourceQueryRef ref : input.getSourceQueryRefs()) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      TableItem item = new TableItem(wSourceQueries.table, SWT.NONE);
      item.setText(1, ref.getSourceQueryName());
    }
    wSourceQueries.optimizeTableView();
    loadMappingsTable();
    loadCalculationsTable();
    refreshUnitTestArtifactLabel();
    refreshSatelliteDependentTabs();
  }

  private void loadMappingsTable() {
    TableViewPopulateSupport.clearRows(wMappings);
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

  private void loadCalculationsTable() {
    wCalculations.clearAll();
    for (BvScd2Calculation calculation : input.getCalculations()) {
      if (calculation == null) {
        continue;
      }
      TableItem item = new TableItem(wCalculations.table, SWT.NONE);
      item.setText(1, Const.NVL(calculation.getTargetFieldName(), ""));
      item.setText(2, Const.NVL(calculation.getExpression(), ""));
      item.setText(3, Const.NVL(calculation.getHopTypeName(), ""));
      item.setText(4, calculation.getLength() >= 0 ? String.valueOf(calculation.getLength()) : "");
      item.setText(
          5, calculation.getPrecision() >= 0 ? String.valueOf(calculation.getPrecision()) : "");
      item.setText(6, Const.NVL(calculation.getDescription(), ""));
    }
    wCalculations.optimizeTableView();
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

  private void applySourceQueriesToTable(BvScd2Table target) {
    target.getSourceQueryRefs().clear();
    if (wSourceQueries == null) {
      return;
    }
    for (TableItem item : wSourceQueries.getNonEmptyItems()) {
      String name = item.getText(1);
      if (Utils.isEmpty(name) || BusinessVaultSourceQuerySupport.hasSourceQuery(target, name)) {
        continue;
      }
      target.getSourceQueryRefs().add(new BvSourceQueryRef(name));
    }
  }

  private void applyDerivativesToTable(BvScd2Table target) {
    target.getDerivatives().clear();
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String dvName = item.getText(1);
      if (Utils.isEmpty(dvName)) {
        continue;
      }
      DvTableType dvType = null;
      DataVaultModel dv = currentDataVaultModel(false);
      if (dv != null) {
        DvSatellite resolved =
            BvScd2FieldMappingDialogSupport.resolveSatellite(
                dv, dvName, variables, metadataProvider());
        if (resolved != null) {
          dvType = DvTableType.SATELLITE;
        } else {
          IDvTable dvTable = dv.findTable(dvName);
          if (dvTable != null) {
            dvType = dvTable.getTableType();
          }
        }
      }
      if (dvType == null && !Utils.isEmpty(item.getText(2))) {
        dvType = DvTableType.lookupDescription(item.getText(2));
        if (dvType == null) {
          dvType = DvTableType.lookupCode(item.getText(2));
        }
      }
      if (dvType == DvTableType.HUB) {
        continue;
      }
      if (dvType != null
          && BusinessVaultDerivativeSupport.isValidDerivativePair(target.getTableType(), dvType)
          && !BusinessVaultDerivativeSupport.hasDerivative(target, dvName)) {
        target.getDerivatives().add(new BvDerivativeRef(dvName, dvType));
      }
    }
  }

  private void ok() {
    BvScd2Table draft = new BvScd2Table();
    applyWidgetsToTable(draft);
    List<ICheckResult> remarks =
        BvScd2FieldMappingDialogSupport.validateForDialog(
            draft, businessVaultModel, currentDataVaultModel(true), variables, metadataProvider());
    if (BvScd2FieldMappingDialogSupport.hasValidationErrors(remarks)
        && !confirmSaveWithValidationErrors(remarks)) {
      return;
    }

    applyWidgetsToTable(input);
    ok = true;
    dispose();
  }

  /**
   * Shows check errors without discarding dialog edits. Some remarks come from model or satellite
   * configuration that cannot be fixed in this shell.
   */
  private boolean confirmSaveWithValidationErrors(List<ICheckResult> remarks) {
    MessageBox box = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_WARNING);
    box.setText(BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.ValidationError.Title"));
    box.setMessage(
        BaseMessages.getString(
            PKG,
            "HopGuiBvScd2TableDialog.ValidationError.SaveAnyway",
            BvScd2FieldMappingDialogSupport.formatValidationErrors(remarks)));
    return box.open() == SWT.YES;
  }

  private void validate() {
    try {
      DataVaultModel dv = currentDataVaultModel(true);
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                BvScd2Table draftTable = new BvScd2Table();
                applyWidgetsToTable(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                BvScd2FieldMappingDialogSupport.MappingSuggestion suggestion =
                    analyzeMappings(draftTable, false);
                tableRemarks.add(
                    new CheckResult(
                        ICheckResult.TYPE_RESULT_OK,
                        BaseMessages.getString(
                            PKG,
                            "HopGuiBvScd2TableDialog.Validate.Resolution",
                            suggestion.dvModelPresent() ? suggestion.dvTableCount() : 0,
                            Utils.isEmpty(suggestion.attributeCountSummary())
                                ? BaseMessages.getString(
                                    PKG, "HopGuiBvScd2TableDialog.Mappings.Hint.NoAttributes")
                                : suggestion.attributeCountSummary()),
                        draftTable));
                draftTable.check(
                    tableRemarks, metadataProvider(), variables, businessVaultModel, dv);
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

  private void applyWidgetsToTable(BvScd2Table target) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    target.setTableName(wTableName.getText());
    target.setIncludeHashKey(wIncludeHashKey.getSelection());
    target.setIncludeHubBusinessKeys(
        wIncludeHashKey.getSelection() && wIncludeHubBusinessKeys.getSelection());
    target.setLoadHubBusinessKeys(wLoadHubBusinessKeys.getSelection());
    target.setParentHubName(wParentHubName.getText());
    target.setBuildMode(
        EnumDialogSupport.readCombo(
            wBuildMode, BvScd2BuildMode.class, BvScd2BuildMode.FULL_REBUILD));
    target.setHashKeyPartitionCount(
        EnumDialogSupport.readCombo(
            wHashKeyPartitions, BvScd2HashPartitionCount.class, BvScd2HashPartitionCount.NONE));
    target.setSqlExpressionCopyCount(Const.trim(wSqlExpressionCopies.getText()));
    target.setFunctionalTimestampField(wFunctionalTimestamp.getText());
    target.setIncrementalWatermarkField(wIncrementalWatermark.getText());
    target.setValidFromField(wValidFromField.getText());
    target.setValidToField(wValidToField.getText());

    applyDerivativesToTable(target);
    BusinessVaultDerivativeSupport.setParentHub(target, wParentHubName.getText());
    applySourceQueriesToTable(target);
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
      BvScd2FieldMapping mapping =
          new BvScd2FieldMapping(satelliteName, sourceFieldName, targetFieldName);
      target.getFieldMappings().add(mapping);
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

    target.getCalculations().clear();
    for (TableItem item : wCalculations.getNonEmptyItems()) {
      String targetField = item.getText(1);
      String expression = item.getText(2);
      if (Utils.isEmpty(targetField) || Utils.isEmpty(expression)) {
        continue;
      }
      BvScd2Calculation calculation = new BvScd2Calculation(targetField, expression);
      calculation.setHopTypeName(item.getText(3));
      calculation.setLength(Const.toInt(item.getText(4), -1));
      calculation.setPrecision(Const.toInt(item.getText(5), -1));
      calculation.setDescription(item.getText(6));
      target.getCalculations().add(calculation);
    }
  }

  /**
   * Asks which linked satellites and source queries to suggest mappings for.
   *
   * @return selected names, or {@code null} if the user cancelled or selected none
   */
  private Collection<String> promptSourcesToSuggest(List<SuggestSourceChoice> choices) {
    String[] labels = new String[choices.size()];
    int[] selectedNrs = new int[choices.size()];
    for (int i = 0; i < choices.size(); i++) {
      SuggestSourceChoice choice = choices.get(i);
      labels[i] =
          BaseMessages.getString(
              PKG,
              choice.sourceQuery()
                  ? "HopGuiBvScd2TableDialog.Mappings.Suggest.Select.SourceQuery"
                  : "HopGuiBvScd2TableDialog.Mappings.Suggest.Select.Satellite",
              choice.name());
      selectedNrs[i] = i;
    }
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            labels,
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.Select.Title"),
            BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.Select.Message"));
    dialog.setMulti(true);
    dialog.setSelectedNrs(selectedNrs);
    if (dialog.open() == null) {
      return null;
    }
    int[] indices = dialog.getSelectionIndeces();
    if (indices == null || indices.length == 0) {
      showMappingMessage(
          SWT.ICON_INFORMATION,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.NoneSelected"));
      return null;
    }
    List<String> selected = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (int index : indices) {
      if (index < 0 || index >= choices.size()) {
        continue;
      }
      String name = choices.get(index).name();
      if (seen.add(name)) {
        selected.add(name);
      }
    }
    if (selected.isEmpty()) {
      showMappingMessage(
          SWT.ICON_INFORMATION,
          "HopGuiBvScd2TableDialog.Mappings.Suggest.Title",
          BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.Mappings.Suggest.NoneSelected"));
      return null;
    }
    return selected;
  }

  private List<SuggestSourceChoice> listSuggestSourceChoices(BvScd2Table draft) {
    List<SuggestSourceChoice> choices = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (draft == null) {
      return choices;
    }
    for (BvDerivativeRef derivative : draft.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      if (!seen.add(derivative.getDvTableName())) {
        continue;
      }
      choices.add(new SuggestSourceChoice(derivative.getDvTableName(), false));
    }
    for (BvSourceQueryRef ref : draft.getSourceQueryRefs()) {
      if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
        continue;
      }
      if (!seen.add(ref.getSourceQueryName())) {
        continue;
      }
      choices.add(new SuggestSourceChoice(ref.getSourceQueryName(), true));
    }
    return choices;
  }

  private BvScd2FieldMappingDialogSupport.MappingSuggestion analyzeMappings(
      BvScd2Table draft, boolean forceReload) {
    return analyzeMappings(draft, forceReload, null);
  }

  private BvScd2FieldMappingDialogSupport.MappingSuggestion analyzeMappings(
      BvScd2Table draft, boolean forceReload, Collection<String> selectedSourceNames) {
    return BvScd2FieldMappingDialogSupport.analyze(
        draft,
        currentDataVaultModel(forceReload),
        businessVaultModel,
        variables,
        metadataProvider(),
        selectedSourceNames);
  }

  private static boolean hasNonEmptyTableItems(TableView tableView) {
    return tableView != null && !tableView.getNonEmptyItems().isEmpty();
  }

  private DataVaultModel currentDataVaultModel(boolean forceReload) {
    if (!forceReload && dataVaultModel != null) {
      return dataVaultModel;
    }
    dataVaultLoadError = null;
    if (businessVaultModel == null) {
      dataVaultModel = null;
      return null;
    }
    try {
      if (forceReload) {
        BusinessVaultDvModelResolver.invalidateReferencedModelCaches(businessVaultModel, variables);
      }
      DataVaultModel loaded =
          BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
              businessVaultModel, variables, metadataProvider());
      if (loaded.getTables().isEmpty()
          && businessVaultModel.getDvReferences().isEmpty()
          && Utils.isEmpty(businessVaultModel.getDataVaultModelPath())) {
        loaded = null;
      }
      dataVaultModel = loaded;
      HopGuiBusinessVaultGraph graph = HopGuiBusinessVaultGraph.getInstance();
      if (graph != null && graph.getModel() == businessVaultModel) {
        graph.replaceDataVaultModel(dataVaultModel, dataVaultLoadError);
      }
      return dataVaultModel;
    } catch (Exception e) {
      dataVaultLoadError = e.getMessage();
      dataVaultModel = null;
      HopGuiBusinessVaultGraph graph = HopGuiBusinessVaultGraph.getInstance();
      if (graph != null && graph.getModel() == businessVaultModel) {
        graph.replaceDataVaultModel(null, dataVaultLoadError);
      }
      return null;
    }
  }

  private IHopMetadataProvider metadataProvider() {
    HopGui hopGui = HopGui.getInstance();
    return hopGui != null ? hopGui.getMetadataProvider() : null;
  }

  private void fillDerivativeTypes() {
    DataVaultModel dv = currentDataVaultModel(false);
    if (wDerivatives == null || dv == null) {
      return;
    }
    for (TableItem item : wDerivatives.table.getItems()) {
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      IDvTable table = dv.findTable(name);
      if (table != null && table.getTableType() != null) {
        String description = table.getTableType().getDescription();
        if (!description.equals(item.getText(2))) {
          item.setText(2, description);
        }
      }
    }
  }

  private void showMappingMessage(int icon, String titleKey, String message) {
    MessageBox box = new MessageBox(shell, SWT.OK | icon);
    box.setText(BaseMessages.getString(PKG, titleKey));
    box.setMessage(Const.NVL(message, ""));
    box.open();
  }

  private void updateHubBusinessKeyOptionState() {
    boolean hashKey = wIncludeHashKey.getSelection();
    boolean hubKeys = hashKey && wIncludeHubBusinessKeys.getSelection();
    wIncludeHubBusinessKeys.setEnabled(hashKey);
    wLoadHubBusinessKeys.setEnabled(hubKeys);
  }

  private void populateParentHubCombo() {
    if (wParentHubName == null || wParentHubName.isDisposed()) {
      return;
    }
    String current = wParentHubName.getText();
    wParentHubName.removeAll();
    wParentHubName.add("");
    for (String hubName :
        BusinessVaultSourceQuerySupport.listParentHubNames(
            businessVaultModel, currentDataVaultModel(false))) {
      wParentHubName.add(hubName);
    }
    if (!Utils.isEmpty(current)) {
      wParentHubName.setText(current);
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
    if (wHashKeyPartitions != null && !wHashKeyPartitions.isDisposed()) {
      wHashKeyPartitions.setEnabled(!incremental);
    }
  }

  private void applyScd2FieldTooltips() {
    BusinessVaultConfiguration config =
        businessVaultModel != null
            ? businessVaultModel.getConfigurationOrDefault()
            : new BusinessVaultConfiguration();
    wBuildMode.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.BuildMode.Tooltip"));
    wHashKeyPartitions.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.HashKeyPartitions.Tooltip"));
    wSqlExpressionCopies.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvScd2TableDialog.SqlExpressionCopies.Tooltip"));
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
