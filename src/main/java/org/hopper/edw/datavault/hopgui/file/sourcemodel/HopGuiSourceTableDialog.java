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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.dialog.ShowRowsDialog;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.datatypemapping.PhysicalSourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipLifecycleSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTableLiveSchemaSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTableValidationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceTablePreviewSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a {@link SourceTable} (identity, physical location, columns). */
public class HopGuiSourceTableDialog {

  private static final Class<?> PKG = HopGuiSourceTableDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final SourceModel model;
  private final SourceTable input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Text wTableName;
  private Text wCatalogSourceName;
  private TableView wColumns;
  private SourceDataTypeMappingTab dataTypeMappingTab;

  private boolean ok;

  public HopGuiSourceTableDialog(
      Shell parent,
      SourceTable table,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = table;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiSourceTableDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Validate.Button"));
    wValidate.addListener(SWT.Selection, e -> validateDefinition());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Button"));
    wPreview.addListener(SWT.Selection, e -> previewData());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel, wPreview}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top()
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    addGeneralTab(wTabFolder, middle, margin);
    addColumnsTab(wTabFolder, margin);
    dataTypeMappingTab =
        new SourceDataTypeMappingTab(
            variables,
            metadataProvider,
            () -> {
              List<PhysicalSourceField> fields = new ArrayList<>();
              for (SourceColumn column : readColumnsFromTable()) {
                PhysicalSourceField physical = PhysicalSourceField.from(column);
                if (physical != null) {
                  fields.add(physical);
                }
              }
              return fields;
            });
    dataTypeMappingTab.addTab(wTabFolder, margin);

    wTabFolder.setSelection(0);
    getData();

    BaseTransformDialog.setSize(shell, 780, 560);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Tab.General.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    wDatabaseName =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DatabaseName.Label"),
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.DatabaseName.ToolTip"));
    wDatabaseName.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right().result());
    try {
      wDatabaseName.fillItems();
    } catch (Exception ignored) {
      // best effort
    }

    Label wlSchema = new Label(comp, SWT.RIGHT);
    wlSchema.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.SchemaName.Label"));
    PropsUi.setLook(wlSchema);
    wlSchema.setLayoutData(
        new FormDataBuilder().left().top(wDatabaseName, margin).right(middle, -margin).result());
    wSchemaName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaName);
    wSchemaName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDatabaseName, margin).right().result());

    Label wlTable = new Label(comp, SWT.RIGHT);
    wlTable.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.TableName.Label"));
    PropsUi.setLook(wlTable);
    wlTable.setLayoutData(
        new FormDataBuilder().left().top(wSchemaName, margin).right(middle, -margin).result());
    wTableName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSchemaName, margin).right().result());

    Label wlCatalog = new Label(comp, SWT.RIGHT);
    wlCatalog.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.CatalogSourceName.Label"));
    PropsUi.setLook(wlCatalog);
    wlCatalog.setLayoutData(
        new FormDataBuilder().left().top(wTableName, margin).right(middle, -margin).result());
    wCatalogSourceName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCatalogSourceName);
    wCatalogSourceName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.CatalogSourceName.ToolTip"));
    wCatalogSourceName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wTableName, margin).right().result());
  }

  private void addColumnsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Tab.Columns.Label"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wGetColumns = new Button(comp, SWT.PUSH);
    wGetColumns.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Button"));
    wGetColumns.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.ToolTip"));
    PropsUi.setLook(wGetColumns);
    wGetColumns.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wGetColumns.addListener(SWT.Selection, e -> getColumnsFromLiveTable());

    Button wShowDiff = new Button(comp, SWT.PUSH);
    wShowDiff.setText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.ShowDifferences.Button"));
    wShowDiff.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceTableDialog.ShowDifferences.ToolTip"));
    PropsUi.setLook(wShowDiff);
    wShowDiff.setLayoutData(
        new FormDataBuilder().left(wGetColumns, margin).top(0, margin).result());
    wShowDiff.addListener(SWT.Selection, e -> showColumnDifferences());

    String[] hopTypes = ValueMetaFactory.getValueMetaNames();
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.SourceDataType"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.HopType"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              hopTypes),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Columns.PrimaryKeyPosition"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    wColumns =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(input.getColumns().size(), 1),
            null,
            PropsUi.getInstance());
    wColumns.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wGetColumns, margin)
            .right()
            .bottom(100, -margin)
            .result());
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wDatabaseName.setText(Const.NVL(input.getDatabaseName(), ""));
    wSchemaName.setText(Const.NVL(input.getSchemaName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wCatalogSourceName.setText(Const.NVL(input.getCatalogSourceName(), ""));

    wColumns.clearAll(false);
    for (SourceColumn column : input.getColumns()) {
      if (column == null) {
        continue;
      }
      TableItem item = new TableItem(wColumns.table, SWT.NONE);
      item.setText(1, Const.NVL(column.getName(), ""));
      item.setText(2, Const.NVL(column.getDescription(), ""));
      item.setText(3, Const.NVL(column.getSourceDataType(), ""));
      item.setText(4, Const.NVL(column.getLength(), ""));
      item.setText(5, Const.NVL(column.getPrecision(), ""));
      String hopTypeName;
      try {
        hopTypeName = ValueMetaFactory.getValueMetaName(column.getHopType());
      } catch (Exception e) {
        hopTypeName = ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING);
      }
      item.setText(6, Const.NVL(hopTypeName, ""));
      item.setText(
          7,
          column.getPrimaryKeyPosition() > 0 ? String.valueOf(column.getPrimaryKeyPosition()) : "");
    }
    wColumns.optimizeTableView();
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.loadFrom(input);
    }
  }

  private void ok() {
    String name = wName.getText().trim();
    if (Utils.isEmpty(name)) {
      return;
    }
    // Unique name among other tables.
    SourceTable existing = model != null ? model.findTable(name) : null;
    if (existing != null && existing != input) {
      return;
    }

    String oldName = input.getName();
    input.setName(name);
    input.setDescription(wDescription.getText());
    input.setDatabaseName(wDatabaseName.getText());
    input.setSchemaName(wSchemaName.getText());
    input.setTableName(wTableName.getText());
    input.setCatalogSourceName(wCatalogSourceName.getText());
    input.setColumns(readColumnsFromTable());
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.saveTo(input);
    }
    if (model != null) {
      SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
          model, SourceEndpointKind.TABLE, oldName, name);
    }
    ok = true;
    dispose();
  }

  private List<SourceColumn> readColumnsFromTable() {
    List<SourceColumn> columns = new ArrayList<>();
    int rows = wColumns.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wColumns.getNonEmpty(i);
      String colName = item.getText(1);
      if (Utils.isEmpty(colName)) {
        continue;
      }
      SourceColumn column = new SourceColumn(colName.trim());
      column.setDescription(item.getText(2));
      column.setSourceDataType(item.getText(3));
      column.setLength(item.getText(4));
      column.setPrecision(item.getText(5));
      try {
        column.setHopType(ValueMetaFactory.getIdForValueMeta(item.getText(6)));
      } catch (Exception e) {
        column.setHopType(IValueMeta.TYPE_STRING);
      }
      column.setPrimaryKeyPosition(Const.toInt(item.getText(7), 0));
      columns.add(column);
    }
    return columns;
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void previewData() {
    try {
      SourceTable working = workingTableFromDialog();
      List<RowMetaAndData> rows =
          SourceTablePreviewSupport.preview(
              model,
              working,
              variables,
              metadataProvider,
              SourceTablePreviewSupport.DEFAULT_ROW_LIMIT);
      if (rows.isEmpty()) {
        MessageBox emptyBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      ShowRowsDialog dialog =
          new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Message"),
              rows.get(0).getRowMeta(),
              toObjectList(rows));
      dialog.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Preview.Error.Message"),
          e);
    }
  }

  private void validateDefinition() {
    try {
      SourceTable working = workingTableFromDialog();
      working.setColumns(readColumnsFromTable());
      if (dataTypeMappingTab != null) {
        dataTypeMappingTab.saveTo(working);
      }
      List<ICheckResult> remarks =
          new ArrayList<>(SourceTableValidationSupport.check(model, working, variables));
      try {
        remarks.addAll(
            SourceDataTypeMappingSupport.check(
                working.getName(),
                working,
                SourceDataTypeMappingSupport.physicalFields(working),
                metadataProvider));
      } catch (Exception mapEx) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                "Data type mapping validation skipped: " + mapEx.getMessage(),
                null));
      }

      // Always try live layout comparison so add/remove/type drift is visible at design time.
      try {
        List<SourceColumn> live =
            SourceTableLiveSchemaSupport.discoverColumns(
                model, working, variables, metadataProvider);
        List<SourceTableLiveSchemaSupport.ColumnDiff> diffs =
            SourceTableLiveSchemaSupport.compareColumns(working.getColumns(), live);
        if (diffs.isEmpty()) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_OK,
                  BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Validate.LiveMatch.Message"),
                  null));
        } else {
          remarks.addAll(SourceTableLiveSchemaSupport.diffsToRemarks(diffs));
        }
      } catch (Exception liveEx) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "HopGuiSourceTableDialog.Validate.LiveSkipped.Message",
                    liveEx.getMessage() != null
                        ? liveEx.getMessage()
                        : liveEx.getClass().getSimpleName()),
                null));
      }

      if (remarks.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Validate.Ok.Message"),
                null));
      }
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.Validate.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceTableDialog.Validate.Error.Message", ex.getMessage()),
          ex);
    }
  }

  private void getColumnsFromLiveTable() {
    try {
      SourceTable working = workingTableFromDialog();
      List<SourceColumn> live =
          SourceTableLiveSchemaSupport.discoverColumns(model, working, variables, metadataProvider);
      if (live.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Empty.Message"));
        box.open();
        return;
      }
      boolean replace = true;
      if (wColumns.nrNonEmpty() > 0) {
        MessageBox confirm =
            new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        confirm.setText(
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Replace.Title"));
        confirm.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Replace.Message"));
        int answer = confirm.open();
        if (answer == SWT.CANCEL) {
          return;
        }
        replace = answer == SWT.YES;
      }
      if (replace) {
        populateColumnsTable(live);
      } else {
        // Append only names not already present.
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (SourceColumn col : readColumnsFromTable()) {
          if (col != null && !Utils.isEmpty(col.getName())) {
            existing.add(col.getName().trim().toLowerCase());
          }
        }
        List<SourceColumn> merged = new ArrayList<>(readColumnsFromTable());
        for (SourceColumn liveCol : live) {
          if (liveCol != null
              && !Utils.isEmpty(liveCol.getName())
              && existing.add(liveCol.getName().trim().toLowerCase())) {
            merged.add(liveCol);
          }
        }
        populateColumnsTable(merged);
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.GetColumns.Error.Message"),
          e);
    }
  }

  private void showColumnDifferences() {
    try {
      SourceTable working = workingTableFromDialog();
      working.setColumns(readColumnsFromTable());
      List<SourceColumn> live =
          SourceTableLiveSchemaSupport.discoverColumns(model, working, variables, metadataProvider);
      List<SourceTableLiveSchemaSupport.ColumnDiff> diffs =
          SourceTableLiveSchemaSupport.compareColumns(working.getColumns(), live);
      List<ICheckResult> remarks = new ArrayList<>();
      if (diffs.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(PKG, "HopGuiSourceTableDialog.ShowDifferences.None.Message"),
                null));
      } else {
        remarks.addAll(SourceTableLiveSchemaSupport.diffsToRemarks(diffs));
      }
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.ShowDifferences.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceTableDialog.ShowDifferences.Error.Message"),
          e);
    }
  }

  private void populateColumnsTable(List<SourceColumn> columns) {
    wColumns.clearAll(false);
    if (columns != null) {
      for (SourceColumn column : columns) {
        if (column == null || Utils.isEmpty(column.getName())) {
          continue;
        }
        TableItem item = new TableItem(wColumns.table, SWT.NONE);
        item.setText(1, Const.NVL(column.getName(), ""));
        item.setText(2, Const.NVL(column.getDescription(), ""));
        item.setText(3, Const.NVL(column.getSourceDataType(), ""));
        item.setText(4, Const.NVL(column.getLength(), ""));
        item.setText(5, Const.NVL(column.getPrecision(), ""));
        String hopTypeName;
        try {
          hopTypeName =
              column.getHopType() > 0 ? ValueMetaFactory.getValueMetaName(column.getHopType()) : "";
        } catch (Exception e) {
          hopTypeName = "";
        }
        item.setText(6, Const.NVL(hopTypeName, ""));
        item.setText(
            7,
            column.getPrimaryKeyPosition() > 0
                ? String.valueOf(column.getPrimaryKeyPosition())
                : "");
      }
    }
    wColumns.optimizeTableView();
  }

  /**
   * Snapshot of dialog fields for preview / live schema without committing OK. Connection, schema,
   * and physical table name drive discovery; columns are taken from the grid when needed.
   */
  private SourceTable workingTableFromDialog() {
    SourceTable working = new SourceTable();
    working.setName(wName.getText() != null ? wName.getText().trim() : "");
    working.setDatabaseName(wDatabaseName.getText());
    working.setSchemaName(wSchemaName.getText());
    working.setTableName(wTableName.getText());
    if (input.getPhysicalType() != null) {
      working.setPhysicalType(input.getPhysicalType());
    }
    return working;
  }

  private static List<Object[]> toObjectList(List<RowMetaAndData> rows) {
    List<Object[]> list = new ArrayList<>();
    for (RowMetaAndData row : rows) {
      list.add(row.getData());
    }
    return list;
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
