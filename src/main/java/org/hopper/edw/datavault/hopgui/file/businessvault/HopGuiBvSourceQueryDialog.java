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
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
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
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.DmSourceSqlGuiSupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultSourceQuerySupport;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQuery;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQueryColumn;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQueryKind;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQuerySqlSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvTargetDatabaseSupport;

/** Dialog to edit a satellite-shaped Business Vault source query (table/view or SQL). */
public class HopGuiBvSourceQueryDialog {
  private static final Class<?> PKG = HopGuiBvSourceQueryDialog.class;

  private final Shell parent;
  private final BvSourceQuery input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private MetaSelectionLine<DatabaseMeta> wConnection;
  private Combo wKind;
  private Text wSchemaName;
  private Text wTableName;
  private Text wHashKeyField;
  private Text wHubHashKeyField;
  private Text wFunctionalTimestamp;
  private Text wLoadDateField;
  private TextComposite wSqlQuery;
  private TableView wColumns;
  private CTabFolder wTabFolder;

  private boolean ok;

  public HopGuiBvSourceQueryDialog(
      Shell parent,
      BvSourceQuery table,
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
            PKG, "HopGuiBvSourceQueryDialog.Title", Const.NVL(input.getName(), "Source query")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label lastLabel = addRightLabel("HopGuiBvSourceQueryDialog.Name.Label", null, middle, margin);
    wName = addText(lastLabel, middle);
    lastLabel = addRightLabel("HopGuiBvSourceQueryDialog.Description.Label", wName, middle, margin);
    wDescription = addText(lastLabel, middle);

    wConnection =
        new MetaSelectionLine<>(
            variables,
            HopGui.getInstance().getMetadataProvider(),
            DatabaseMeta.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Connection.Label"),
            BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Connection.ToolTip"));
    FormData fdConnection = new FormData();
    fdConnection.left = new FormAttachment(0, 0);
    fdConnection.top = new FormAttachment(wDescription, margin);
    fdConnection.right = new FormAttachment(100, 0);
    wConnection.setLayoutData(fdConnection);
    try {
      wConnection.fillItems();
    } catch (HopException e) {
      // best effort
    }

    lastLabel = addRightLabel("HopGuiBvSourceQueryDialog.Kind.Label", wConnection, middle, margin);
    wKind = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wKind);
    wKind.setItems(BvSourceQueryKind.getDescriptions());
    FormData fdKind = new FormData();
    fdKind.left = new FormAttachment(middle, 0);
    fdKind.top = new FormAttachment(lastLabel, 0, SWT.TOP);
    fdKind.right = new FormAttachment(100, 0);
    wKind.setLayoutData(fdKind);
    wKind.addListener(SWT.Selection, e -> refreshKindEnablement());

    lastLabel = addRightLabel("HopGuiBvSourceQueryDialog.SchemaName.Label", wKind, middle, margin);
    wSchemaName = addText(lastLabel, middle);
    lastLabel =
        addRightLabel("HopGuiBvSourceQueryDialog.TableName.Label", wSchemaName, middle, margin);
    wTableName = addText(lastLabel, middle);
    lastLabel =
        addRightLabel("HopGuiBvSourceQueryDialog.HashKeyField.Label", wTableName, middle, margin);
    wHashKeyField = addText(lastLabel, middle);
    wHashKeyField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.HashKeyField.ToolTip"));
    lastLabel =
        addRightLabel(
            "HopGuiBvSourceQueryDialog.HubHashKeyField.Label", wHashKeyField, middle, margin);
    wHubHashKeyField = addText(lastLabel, middle);
    wHubHashKeyField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.HubHashKeyField.ToolTip"));
    lastLabel =
        addRightLabel(
            "HopGuiBvSourceQueryDialog.FunctionalTimestamp.Label",
            wHubHashKeyField,
            middle,
            margin);
    wFunctionalTimestamp = addText(lastLabel, middle);
    lastLabel =
        addRightLabel(
            "HopGuiBvSourceQueryDialog.LoadDateField.Label", wFunctionalTimestamp, middle, margin);
    wLoadDateField = addText(lastLabel, middle);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wLoadDateField, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(100, -50);
    wTabFolder.setLayoutData(fdTabFolder);

    addSqlTab(margin);
    addColumnsTab(margin);
    wTabFolder.setSelection(0);

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
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_SOURCE_QUERY);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    getData();
    refreshKindEnablement();
    BaseTransformDialog.setSize(shell, 720, 680);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addSqlTab(int margin) {
    CTabItem sqlTab = new CTabItem(wTabFolder, SWT.NONE);
    sqlTab.setText(BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Tab.Sql"));
    Composite sqlComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(sqlComp);
    sqlComp.setLayout(new FormLayout());
    sqlTab.setControl(sqlComp);

    Button wGetFields = new Button(sqlComp, SWT.PUSH);
    wGetFields.setText(BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.GetFields.Label"));
    PropsUi.setLook(wGetFields);
    FormData fdGetFields = new FormData();
    fdGetFields.left = new FormAttachment(0, 0);
    fdGetFields.top = new FormAttachment(0, margin);
    wGetFields.setLayoutData(fdGetFields);
    wGetFields.addListener(SWT.Selection, e -> getFields());

    Button wPreview = new Button(sqlComp, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Preview.Label"));
    PropsUi.setLook(wPreview);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(wGetFields, margin);
    fdPreview.top = new FormAttachment(0, margin);
    wPreview.setLayoutData(fdPreview);
    wPreview.addListener(SWT.Selection, e -> preview());

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSqlQuery = new StyledTextComp(variables, sqlComp, sqlStyle);
    } else {
      wSqlQuery = new SQLStyledTextComp(variables, sqlComp, sqlStyle);
    }
    wSqlQuery.addLineStyleListener(getSqlReservedWords());
    PropsUi.setLook(wSqlQuery, Props.WIDGET_STYLE_FIXED);
    FormData fdSqlQuery = new FormData();
    fdSqlQuery.left = new FormAttachment(0, 0);
    fdSqlQuery.top = new FormAttachment(wGetFields, margin);
    fdSqlQuery.right = new FormAttachment(100, 0);
    fdSqlQuery.bottom = new FormAttachment(100, 0);
    wSqlQuery.setLayoutData(fdSqlQuery);
  }

  private void addColumnsTab(int margin) {
    CTabItem columnsTab = new CTabItem(wTabFolder, SWT.NONE);
    columnsTab.setText(BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Tab.Columns"));
    Composite columnsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(columnsComp);
    columnsComp.setLayout(new FormLayout());
    columnsTab.setControl(columnsComp);

    ColumnInfo[] cols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Columns.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Columns.Type"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames(),
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Columns.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Columns.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wColumns =
        new TableView(
            variables,
            columnsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            cols,
            1,
            null,
            PropsUi.getInstance());
    FormData fdColumns = new FormData();
    fdColumns.left = new FormAttachment(0, 0);
    fdColumns.top = new FormAttachment(0, margin);
    fdColumns.right = new FormAttachment(100, 0);
    fdColumns.bottom = new FormAttachment(100, 0);
    wColumns.setLayoutData(fdColumns);
  }

  private Label addRightLabel(
      String key, org.eclipse.swt.widgets.Control top, int middle, int margin) {
    Label label = new Label(shell, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, key));
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = top == null ? new FormAttachment(0, margin) : new FormAttachment(top, margin);
    fd.right = new FormAttachment(middle, -margin);
    label.setLayoutData(fd);
    return label;
  }

  private Text addText(Label label, int middle) {
    Text text = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(label, 0, SWT.TOP);
    fd.right = new FormAttachment(100, 0);
    text.setLayoutData(fd);
    return text;
  }

  private void refreshKindEnablement() {
    boolean sql = BvSourceQueryKind.lookupDescription(wKind.getText()) == BvSourceQueryKind.SQL;
    wSqlQuery.setEnabled(sql);
    wTableName.setEnabled(!sql);
    wSchemaName.setEnabled(!sql);
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wConnection.setText(Const.NVL(input.getConnectionName(), ""));
    EnumDialogSupport.selectCombo(wKind, input.getSourceKindOrDefault());
    wSchemaName.setText(Const.NVL(input.getSchemaName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wHashKeyField.setText(Const.NVL(input.getHashKeyField(), ""));
    wHubHashKeyField.setText(Const.NVL(input.getHubHashKeyField(), ""));
    wFunctionalTimestamp.setText(Const.NVL(input.getFunctionalTimestampField(), ""));
    wLoadDateField.setText(Const.NVL(input.getLoadDateField(), ""));
    wSqlQuery.setText(Const.NVL(input.getSqlQuery(), ""));
    wColumns.clearAll();
    for (BvSourceQueryColumn column : input.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      TableItem item = new TableItem(wColumns.table, SWT.NONE);
      item.setText(1, Const.NVL(column.getName(), ""));
      item.setText(2, Const.NVL(column.getDataType(), ""));
      item.setText(3, Const.NVL(column.getLength(), ""));
      item.setText(4, Const.NVL(column.getPrecision(), ""));
    }
    wColumns.optimizeTableView();
  }

  private void applyWidgetsToTable(BvSourceQuery target) {
    target.setName(Const.trim(wName.getText()));
    target.setDescription(wDescription.getText());
    target.setConnectionName(wConnection.getText());
    target.setSourceKind(
        EnumDialogSupport.readCombo(wKind, BvSourceQueryKind.class, BvSourceQueryKind.TABLE));
    target.setSchemaName(wSchemaName.getText());
    target.setTableName(wTableName.getText());
    target.setHashKeyField(wHashKeyField.getText());
    target.setHubHashKeyField(wHubHashKeyField.getText());
    target.setFunctionalTimestampField(wFunctionalTimestamp.getText());
    target.setLoadDateField(wLoadDateField.getText());
    target.setSqlQuery(wSqlQuery.getText());
    target.getColumns().clear();
    for (TableItem item : wColumns.getNonEmptyItems()) {
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      BvSourceQueryColumn column = new BvSourceQueryColumn(name);
      column.setDataType(item.getText(2));
      column.setLength(item.getText(3));
      column.setPrecision(item.getText(4));
      target.getColumns().add(column);
    }
  }

  private void getFields() {
    try {
      BvSourceQuery draft = new BvSourceQuery();
      applyWidgetsToTable(draft);
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      DatabaseMeta databaseMeta =
          BusinessVaultSourceQuerySupport.loadConnection(
              draft, dataVaultModel, metadataProvider, variables);
      if (databaseMeta == null && businessVaultModel != null) {
        databaseMeta =
            BvTargetDatabaseSupport.loadTargetDatabase(
                metadataProvider, businessVaultModel.getConfigurationOrDefault());
      }
      String sql = BvSourceQuerySqlSupport.previewSql(databaseMeta, variables, draft);
      IRowMeta rowMeta = DmSourceSqlGuiSupport.resolveFieldRowMeta(variables, databaseMeta, sql);
      wColumns.clearAll();
      String firstName = null;
      for (int i = 0; i < rowMeta.size(); i++) {
        IValueMeta valueMeta = rowMeta.getValueMeta(i);
        BvSourceQueryColumn column = BvSourceQuerySqlSupport.fromValueMeta(valueMeta);
        if (column == null) {
          continue;
        }
        if (firstName == null) {
          firstName = column.getName();
        }
        TableItem item = new TableItem(wColumns.table, SWT.NONE);
        item.setText(1, Const.NVL(column.getName(), ""));
        item.setText(2, Const.NVL(column.getDataType(), ""));
        item.setText(3, Const.NVL(column.getLength(), ""));
        item.setText(4, Const.NVL(column.getPrecision(), ""));
      }
      wColumns.optimizeTableView();
      if (Utils.isEmpty(wHashKeyField.getText()) && !Utils.isEmpty(firstName)) {
        wHashKeyField.setText(firstName);
      }
      if (wTabFolder.getItemCount() > 1) {
        wTabFolder.setSelection(1);
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.GetFields.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.GetFields.Error.Message"),
          e);
    }
  }

  private void preview() {
    try {
      BvSourceQuery draft = new BvSourceQuery();
      applyWidgetsToTable(draft);
      IHopMetadataProvider metadataProvider = HopGui.getInstance().getMetadataProvider();
      DatabaseMeta databaseMeta =
          BusinessVaultSourceQuerySupport.loadConnection(
              draft, dataVaultModel, metadataProvider, variables);
      if (databaseMeta == null && businessVaultModel != null) {
        databaseMeta =
            BvTargetDatabaseSupport.loadTargetDatabase(
                metadataProvider, businessVaultModel.getConfigurationOrDefault());
      }
      String sql = BvSourceQuerySqlSupport.previewSql(databaseMeta, variables, draft);
      DmSourceSqlGuiSupport.previewSourceSql(shell, variables, metadataProvider, databaseMeta, sql);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.Preview.Error.Message"),
          e);
    }
  }

  private void ok() {
    String newName = Const.trim(wName.getText());
    if (Utils.isEmpty(newName)) {
      showNameWarning(
          "HopGuiBvSourceQueryDialog.NameRequired.Title",
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.NameRequired.Message"));
      return;
    }
    if (businessVaultModel != null && businessVaultModel.hasOtherTableNamed(input, newName)) {
      showNameWarning(
          "HopGuiBvSourceQueryDialog.DuplicateName.Title",
          BaseMessages.getString(PKG, "HopGuiBvSourceQueryDialog.DuplicateName.Message", newName));
      return;
    }
    applyWidgetsToTable(input);
    ok = true;
    dispose();
  }

  private void showNameWarning(String titleKey, String message) {
    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    box.setText(BaseMessages.getString(PKG, titleKey));
    box.setMessage(Const.NVL(message, ""));
    box.open();
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                BvSourceQuery draft = new BvSourceQuery();
                applyWidgetsToTable(draft);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draft.check(
                    tableRemarks,
                    HopGui.getInstance().getMetadataProvider(),
                    variables,
                    businessVaultModel,
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

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    WindowProperty windowProperty = new WindowProperty(shell);
    PropsUi.getInstance().setScreen(windowProperty);
    shell.dispose();
  }

  /**
   * Database reserved words for {@link SQLStyledTextComp} highlighting. Prefers this source query's
   * connection, then the Data Vault / Business Vault target database.
   */
  private List<String> getSqlReservedWords() {
    try {
      DatabaseMeta databaseMeta = loadSqlHighlightDatabase();
      if (databaseMeta == null) {
        return List.of();
      }
      String[] reserved = databaseMeta.getReservedWords();
      return reserved != null ? List.of(reserved) : List.of();
    } catch (Exception e) {
      return List.of();
    }
  }

  private DatabaseMeta loadSqlHighlightDatabase() throws HopException {
    HopGui hopGui = HopGui.getInstance();
    IHopMetadataProvider metadataProvider = hopGui != null ? hopGui.getMetadataProvider() : null;
    if (metadataProvider == null) {
      return null;
    }
    BvSourceQuery draft = new BvSourceQuery();
    String connectionName = null;
    if (wConnection != null && !wConnection.isDisposed()) {
      connectionName = wConnection.getText();
    }
    if (Utils.isEmpty(connectionName) && input != null) {
      connectionName = input.getConnectionName();
    }
    if (!Utils.isEmpty(connectionName) && variables.resolve(connectionName).startsWith("${")) {
      return null;
    }
    draft.setConnectionName(connectionName);
    DatabaseMeta databaseMeta =
        BusinessVaultSourceQuerySupport.loadConnection(
            draft, dataVaultModel, metadataProvider, variables);
    if (databaseMeta != null) {
      return databaseMeta;
    }
    if (businessVaultModel == null) {
      return null;
    }
    return BvTargetDatabaseSupport.loadTargetDatabase(
        metadataProvider, businessVaultModel.getConfigurationOrDefault());
  }
}
