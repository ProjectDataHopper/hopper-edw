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
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.lineage.LineageTabSupport;
import org.hopper.edw.datavault.lineage.BvModelLineageCollector;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.lineage.TableLineage;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBridge;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;

/** Dialog to edit a Business Vault bridge table on the canvas. */
public class HopGuiBvBridgeDialog {
  private static final Class<?> PKG = HopGuiBvBridgeDialog.class;

  private final Shell parent;
  private final BvBridge input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private final int originalTableIndex;
  private Shell shell;

  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Text wWeightField;
  private TextComposite wSqlQuery;
  private Label wlDerivatives;
  private TableView wDerivatives;
  private Button wAddDerivative;
  private Button wDeleteDerivative;

  private boolean ok;

  public HopGuiBvBridgeDialog(
      Shell parent,
      BvBridge table,
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
            PKG, "HopGuiBvBridgeDialog.Title", Const.NVL(input.getName(), "BRIDGE")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Name.Label"));
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
    EdwNamingWidgetSupport.enableAndLayout(
        wName, variables, EdwNamingSchemeTypes.BV_BRIDGE, fdName);

    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wName, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wName, margin);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wTableName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wTableName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    Label wlWeightField = new Label(shell, SWT.RIGHT);
    wlWeightField.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.WeightField.Label"));
    PropsUi.setLook(wlWeightField);
    FormData fdlWeightField = new FormData();
    fdlWeightField.left = new FormAttachment(0, 0);
    fdlWeightField.top = new FormAttachment(wDescription, margin);
    fdlWeightField.right = new FormAttachment(middle, -margin);
    wlWeightField.setLayoutData(fdlWeightField);

    wWeightField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wWeightField);
    FormData fdWeightField = new FormData();
    fdWeightField.left = new FormAttachment(middle, 0);
    fdWeightField.top = new FormAttachment(wDescription, margin);
    fdWeightField.right = new FormAttachment(100, 0);
    wWeightField.setLayoutData(fdWeightField);

    Label wlSqlQuery = new Label(shell, SWT.LEFT);
    wlSqlQuery.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.SqlQuery.Label"));
    PropsUi.setLook(wlSqlQuery);
    FormData fdlSqlQuery = new FormData();
    fdlSqlQuery.left = new FormAttachment(0, 0);
    fdlSqlQuery.top = new FormAttachment(wWeightField, margin);
    fdlSqlQuery.right = new FormAttachment(100, 0);
    wlSqlQuery.setLayoutData(fdlSqlQuery);

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSqlQuery = new StyledTextComp(variables, shell, sqlStyle);
    } else {
      wSqlQuery = new SQLStyledTextComp(variables, shell, sqlStyle);
      wSqlQuery.addLineStyleListener(getSqlReservedWords());
    }
    PropsUi.setLook(wSqlQuery, Props.WIDGET_STYLE_FIXED);
    FormData fdSqlQuery = new FormData();
    fdSqlQuery.left = new FormAttachment(0, 0);
    fdSqlQuery.top = new FormAttachment(wlSqlQuery, margin);
    fdSqlQuery.right = new FormAttachment(100, 0);
    fdSqlQuery.bottom = new FormAttachment(48, 0);
    wSqlQuery.setLayoutData(fdSqlQuery);

    wlDerivatives = new Label(shell, SWT.LEFT);
    wlDerivatives.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.Label"));
    PropsUi.setLook(wlDerivatives);
    FormData fdlDerivatives = new FormData();
    fdlDerivatives.left = new FormAttachment(0, 0);
    fdlDerivatives.top = new FormAttachment(wSqlQuery, margin);
    fdlDerivatives.right = new FormAttachment(100, 0);
    wlDerivatives.setLayoutData(fdlDerivatives);

    wAddDerivative = new Button(shell, SWT.PUSH);
    wAddDerivative.setText(BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.Add"));
    PropsUi.setLook(wAddDerivative);
    FormData fdAddDerivative = new FormData();
    fdAddDerivative.left = new FormAttachment(0, 0);
    fdAddDerivative.top = new FormAttachment(wlDerivatives, margin);
    wAddDerivative.setLayoutData(fdAddDerivative);
    wAddDerivative.addListener(SWT.Selection, e -> addDerivativeRow());

    wDeleteDerivative = new Button(shell, SWT.PUSH);
    wDeleteDerivative.setText(
        BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.Delete"));
    PropsUi.setLook(wDeleteDerivative);
    FormData fdDeleteDerivative = new FormData();
    fdDeleteDerivative.left = new FormAttachment(wAddDerivative, margin);
    fdDeleteDerivative.top = new FormAttachment(wlDerivatives, margin);
    wDeleteDerivative.setLayoutData(fdDeleteDerivative);
    wDeleteDerivative.addListener(SWT.Selection, e -> removeDerivativeRows());

    ColumnInfo[] derivativeCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.Column.Name"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getEligibleDvTableNames(),
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.Column.Type"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    derivativeCols[1].setReadOnly(true);

    wDerivatives =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            derivativeCols,
            1,
            null,
            PropsUi.getInstance());
    FormData fdDerivatives = new FormData();
    fdDerivatives.left = new FormAttachment(0, 0);
    fdDerivatives.top = new FormAttachment(wAddDerivative, margin);
    fdDerivatives.right = new FormAttachment(100, 0);
    fdDerivatives.bottom = new FormAttachment(100, -50);
    wDerivatives.setLayoutData(fdDerivatives);

    boolean dvAvailable = dataVaultModel != null && !dataVaultModel.getTables().isEmpty();
    wAddDerivative.setEnabled(dvAvailable);
    if (!dvAvailable) {
      wlDerivatives.setText(
          BaseMessages.getString(PKG, "HopGuiBvBridgeDialog.Derivatives.MissingDvModel"));
    }

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wLineage = new Button(shell, SWT.PUSH);
    wLineage.setText(BaseMessages.getString(LineageTabSupport.class, "LineageTab.ShowButton"));
    wLineage.setToolTipText(
        BaseMessages.getString(LineageTabSupport.class, "LineageTab.ShowButton.ToolTip"));
    wLineage.addListener(SWT.Selection, e -> showLineage());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_BRIDGE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wLineage, wCancel}, margin, null);

    getData();
    BaseTransformDialog.setSize(shell, 640, 720);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void showLineage() {
    TableLineage tableLineage = null;
    try {
      if (businessVaultModel != null) {
        LineageSnapshot snapshot = BvModelLineageCollector.collect(businessVaultModel, variables);
        tableLineage = LineageTabSupport.findTable(snapshot, input.getName());
      }
    } catch (Exception e) {
      new ErrorDialog(shell, "Error", "Error building lineage for table", e);
      return;
    }
    LineageTabSupport.openViewerDialog(shell, variables, tableLineage);
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

  private void addDerivativeRow() {
    new TableItem(wDerivatives.table, SWT.NONE);
    wDerivatives.optimizeTableView();
  }

  private void removeDerivativeRows() {
    int idx = wDerivatives.getSelectionIndex();
    if (idx >= 0) {
      wDerivatives.table.remove(idx);
      wDerivatives.optimizeTableView();
    }
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
    if (!Utils.isEmpty(input.getWeightField())) {
      wWeightField.setText(input.getWeightField());
    }
    if (!Utils.isEmpty(input.getSqlQuery())) {
      wSqlQuery.setText(input.getSqlQuery());
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
  }

  private void ok() {
    applyWidgetsToTable(input);
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
                IBvTable draftTable = locateDraftTable(draft);
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

  private IBvTable locateDraftTable(BusinessVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    return draft.getTables().get(originalTableIndex);
  }

  private void applyWidgetsToTable(IBvTable target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    if (target instanceof BvBridge bridge) {
      bridge.setWeightField(wWeightField.getText());
      bridge.setSqlQuery(wSqlQuery.getText());
    }

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

  private List<String> getSqlReservedWords() {
    try {
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      if (metadataProvider == null || businessVaultModel == null) {
        return List.of();
      }
      DatabaseMeta targetDatabase =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, businessVaultModel.getConfigurationOrDefault());
      if (targetDatabase == null) {
        return List.of();
      }
      String[] reserved = targetDatabase.getReservedWords();
      return reserved != null ? List.of(reserved) : List.of();
    } catch (Exception e) {
      return List.of();
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
}
