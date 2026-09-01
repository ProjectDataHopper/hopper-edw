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
package org.hopper.edw.datavault.transform.sqlexpression;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.expression.SqlExpressionDraft;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;

public class SqlExpressionDialog extends BaseTransformDialog {

  private static final Class<?> PKG = SqlExpressionMeta.class;

  private final SqlExpressionMeta input;
  private TableView wFields;
  private Button wKeepInputFields;
  private TextVar wBvModelFilename;
  private ComboVar wScd2Table;

  public SqlExpressionDialog(
      Shell parent,
      IVariables variables,
      SqlExpressionMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "SqlExpressionDialog.Title"));

    changed = input.hasChanged();
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.SQL_EXPRESSION);
    ModifyListener lsMod = e -> input.setChanged();

    wKeepInputFields = new Button(shell, SWT.CHECK);
    wKeepInputFields.setText(
        BaseMessages.getString(PKG, "SqlExpressionMeta.KeepInputFields.Label"));
    wKeepInputFields.setToolTipText(
        BaseMessages.getString(PKG, "SqlExpressionMeta.KeepInputFields.Tooltip"));
    PropsUi.setLook(wKeepInputFields);
    FormData fdKeep = new FormData();
    fdKeep.left = new FormAttachment(0, 0);
    fdKeep.top = new FormAttachment(wSpacer, margin);
    fdKeep.right = new FormAttachment(100, 0);
    wKeepInputFields.setLayoutData(fdKeep);
    wKeepInputFields.addListener(SWT.Selection, e -> input.setChanged());

    Label wlBvModel = new Label(shell, SWT.RIGHT);
    wlBvModel.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.BvModel.Label"));
    PropsUi.setLook(wlBvModel);
    FormData fdlBvModel = new FormData();
    fdlBvModel.left = new FormAttachment(0, 0);
    fdlBvModel.top = new FormAttachment(wKeepInputFields, margin);
    fdlBvModel.right = new FormAttachment(middle, -margin);
    wlBvModel.setLayoutData(fdlBvModel);

    Button wBrowse = new Button(shell, SWT.PUSH);
    wBrowse.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
    PropsUi.setLook(wBrowse);
    FormData fdBrowse = new FormData();
    fdBrowse.right = new FormAttachment(100, 0);
    fdBrowse.top = new FormAttachment(wKeepInputFields, margin);
    wBrowse.setLayoutData(fdBrowse);
    wBrowse.addListener(SWT.Selection, e -> browseBusinessVaultModel());

    wBvModelFilename = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wBvModelFilename.setToolTipText(
        BaseMessages.getString(PKG, "SqlExpressionDialog.BvModel.Tooltip"));
    PropsUi.setLook(wBvModelFilename);
    FormData fdBvModel = new FormData();
    fdBvModel.left = new FormAttachment(middle, 0);
    fdBvModel.top = new FormAttachment(wKeepInputFields, margin);
    fdBvModel.right = new FormAttachment(wBrowse, -margin);
    wBvModelFilename.setLayoutData(fdBvModel);
    wBvModelFilename.addModifyListener(lsMod);
    wBvModelFilename.addListener(SWT.FocusOut, e -> refreshScd2TableNames());

    Label wlScd2Table = new Label(shell, SWT.RIGHT);
    wlScd2Table.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Scd2Table.Label"));
    PropsUi.setLook(wlScd2Table);
    FormData fdlScd2 = new FormData();
    fdlScd2.left = new FormAttachment(0, 0);
    fdlScd2.top = new FormAttachment(wBvModelFilename, margin);
    fdlScd2.right = new FormAttachment(middle, -margin);
    wlScd2Table.setLayoutData(fdlScd2);

    wScd2Table = new ComboVar(variables, shell, SWT.BORDER);
    wScd2Table.setToolTipText(BaseMessages.getString(PKG, "SqlExpressionDialog.Scd2Table.Tooltip"));
    PropsUi.setLook(wScd2Table);
    FormData fdScd2 = new FormData();
    fdScd2.left = new FormAttachment(middle, 0);
    fdScd2.top = new FormAttachment(wBvModelFilename, margin);
    fdScd2.right = new FormAttachment(100, 0);
    wScd2Table.setLayoutData(fdScd2);
    wScd2Table.addModifyListener(lsMod);

    Label wlBoundHint = new Label(shell, SWT.WRAP);
    wlBoundHint.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Bound.Hint"));
    PropsUi.setLook(wlBoundHint);
    FormData fdHint = new FormData();
    fdHint.left = new FormAttachment(0, 0);
    fdHint.top = new FormAttachment(wScd2Table, margin);
    fdHint.right = new FormAttachment(100, 0);
    wlBoundHint.setLayoutData(fdHint);

    Label wlFields = new Label(shell, SWT.NONE);
    wlFields.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.top = new FormAttachment(wlBoundHint, margin);
    wlFields.setLayoutData(fdlFields);

    Button wAdd = new Button(shell, SWT.PUSH);
    wAdd.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Add.Label"));
    PropsUi.setLook(wAdd);
    FormData fdAdd = new FormData();
    fdAdd.left = new FormAttachment(0, 0);
    fdAdd.top = new FormAttachment(wlFields, margin);
    wAdd.setLayoutData(fdAdd);
    wAdd.addListener(SWT.Selection, e -> openExpressionEditor(-1));

    Button wEdit = new Button(shell, SWT.PUSH);
    wEdit.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Edit.Label"));
    PropsUi.setLook(wEdit);
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(wAdd, margin);
    fdEdit.top = new FormAttachment(wlFields, margin);
    wEdit.setLayoutData(fdEdit);
    wEdit.addListener(SWT.Selection, e -> openExpressionEditor(wFields.getSelectionIndex()));

    Button wDelete = new Button(shell, SWT.PUSH);
    wDelete.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Delete.Label"));
    PropsUi.setLook(wDelete);
    FormData fdDelete = new FormData();
    fdDelete.left = new FormAttachment(wEdit, margin);
    fdDelete.top = new FormAttachment(wlFields, margin);
    wDelete.setLayoutData(fdDelete);
    wDelete.addListener(
        SWT.Selection,
        e -> {
          int idx = wFields.getSelectionIndex();
          if (idx >= 0) {
            wFields.table.remove(idx);
            wFields.optimizeTableView();
            input.setChanged();
          }
        });

    Button wLoadFromTable = new Button(shell, SWT.PUSH);
    wLoadFromTable.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.LoadFromTable.Label"));
    wLoadFromTable.setToolTipText(
        BaseMessages.getString(PKG, "SqlExpressionDialog.LoadFromTable.Tooltip"));
    PropsUi.setLook(wLoadFromTable);
    FormData fdLoad = new FormData();
    fdLoad.left = new FormAttachment(wDelete, margin);
    fdLoad.top = new FormAttachment(wlFields, margin);
    wLoadFromTable.setLayoutData(fdLoad);
    wLoadFromTable.addListener(SWT.Selection, e -> loadExpressionsFromTable());

    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Validate.Label"));
    PropsUi.setLook(wValidate);
    FormData fdValidate = new FormData();
    fdValidate.right = new FormAttachment(100, 0);
    fdValidate.top = new FormAttachment(wlFields, margin);
    wValidate.setLayoutData(fdValidate);
    wValidate.addListener(SWT.Selection, e -> validateExpressions());

    ColumnInfo expressionCol =
        new ColumnInfo(
            BaseMessages.getString(PKG, "SqlExpressionDialog.Column.Expression"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    expressionCol.setReadOnly(true);
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "SqlExpressionDialog.Column.FieldName"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          expressionCol,
          new ColumnInfo(
              BaseMessages.getString(PKG, "SqlExpressionDialog.Column.HopType"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SqlExpressionDialog.Column.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SqlExpressionDialog.Column.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SqlExpressionDialog.Column.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };

    wFields =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            lsMod,
            props);
    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wAdd, margin);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(wOk, -2 * margin);
    wFields.setLayoutData(fdFields);
    wFields.table.addListener(
        SWT.DefaultSelection, e -> openExpressionEditor(wFields.getSelectionIndex()));

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void getData() {
    wKeepInputFields.setSelection(input.isKeepInputFields());
    wBvModelFilename.setText(Const.NVL(input.getBusinessVaultModelFilename(), ""));
    refreshScd2TableNames();
    wScd2Table.setText(Const.NVL(input.getScd2TableName(), ""));
    wFields.clearAll();
    for (SqlExpressionField field : input.getFields()) {
      if (field == null) {
        continue;
      }
      TableItem item = new TableItem(wFields.table, SWT.NONE);
      item.setText(1, Const.NVL(field.getFieldName(), ""));
      item.setText(2, Const.NVL(field.getExpression(), ""));
      item.setText(3, Const.NVL(field.getHopTypeName(), ""));
      item.setText(4, field.getLength() >= 0 ? String.valueOf(field.getLength()) : "");
      item.setText(5, field.getPrecision() >= 0 ? String.valueOf(field.getPrecision()) : "");
      item.setText(6, Const.NVL(field.getDescription(), ""));
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);
  }

  private void saveFieldsTable() {
    List<SqlExpressionField> fields = new ArrayList<>();
    for (TableItem item : wFields.getNonEmptyItems()) {
      SqlExpressionField field = new SqlExpressionField();
      field.setFieldName(item.getText(1));
      field.setExpression(item.getText(2));
      field.setHopTypeName(item.getText(3));
      field.setLength(Const.toInt(item.getText(4), -1));
      field.setPrecision(Const.toInt(item.getText(5), -1));
      field.setDescription(item.getText(6));
      fields.add(field);
    }
    input.setFields(fields);
  }

  private void browseBusinessVaultModel() {
    BaseDialog.presentFileDialog(
        shell,
        wBvModelFilename,
        variables,
        new String[] {"*.hbv;*.HBV", "*"},
        new String[] {
          BaseMessages.getString(PKG, "SqlExpressionDialog.FileType.Hbv"),
          BaseMessages.getString(PKG, "System.FileType.AllFiles")
        },
        true);
    refreshScd2TableNames();
  }

  private void refreshScd2TableNames() {
    String current = wScd2Table.getText();
    try {
      String filename = wBvModelFilename.getText();
      if (Utils.isEmpty(filename)) {
        wScd2Table.setItems(new String[0]);
        wScd2Table.setText(Const.NVL(current, ""));
        return;
      }
      List<String> names =
          SqlExpressionBvTableSupport.listScd2TableNames(
              SqlExpressionBvTableSupport.loadModel(filename, variables, metadataProvider));
      wScd2Table.setItems(names.toArray(new String[0]));
    } catch (Exception ignored) {
      wScd2Table.setItems(new String[0]);
    }
    wScd2Table.setText(Const.NVL(current, ""));
  }

  private void loadExpressionsFromTable() {
    try {
      BvScd2Table table =
          SqlExpressionBvTableSupport.loadScd2Table(
              wBvModelFilename.getText(), wScd2Table.getText(), variables, metadataProvider);
      List<SqlExpressionSpec> specs = SqlExpressionBvTableSupport.specsFromTable(table);
      if (specs.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.LoadFromTable.Label"));
        box.setMessage(BaseMessages.getString(PKG, "SqlExpressionDialog.LoadFromTable.Empty"));
        box.open();
        return;
      }
      wFields.clearAll();
      for (SqlExpressionSpec spec : specs) {
        TableItem item = new TableItem(wFields.table, SWT.NONE);
        item.setText(1, Const.NVL(spec.getFieldName(), ""));
        item.setText(2, Const.NVL(spec.getExpression(), ""));
        item.setText(3, Const.NVL(spec.getHopTypeName(), ""));
        item.setText(4, spec.getLength() >= 0 ? String.valueOf(spec.getLength()) : "");
        item.setText(5, spec.getPrecision() >= 0 ? String.valueOf(spec.getPrecision()) : "");
      }
      if (table.getCalculations() != null) {
        int row = 0;
        for (var calculation : table.getCalculations()) {
          if (calculation == null || Utils.isEmpty(calculation.getExpression())) {
            continue;
          }
          if (row < wFields.table.getItemCount()) {
            wFields.table.getItem(row).setText(6, Const.NVL(calculation.getDescription(), ""));
          }
          row++;
        }
      }
      wFields.removeEmptyRows();
      wFields.setRowNums();
      wFields.optWidth(true);
      input.setChanged();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SqlExpressionDialog.LoadFromTable.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void validateExpressions() {
    try {
      saveFieldsTable();
      input.setKeepInputFields(wKeepInputFields.getSelection());
      input.setBusinessVaultModelFilename(wBvModelFilename.getText());
      input.setScd2TableName(wScd2Table.getText());
      IRowMeta prev = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (prev == null || prev.isEmpty()) {
        throw new HopException(BaseMessages.getString(PKG, "SqlExpressionDialog.Validate.NoInput"));
      }
      SqlExpressionProgram.compile(
          input.resolveSpecs(variables, metadataProvider),
          prev,
          variables,
          input.isKeepInputFields());
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "SqlExpressionDialog.Validate.Ok.Title"));
      box.setMessage(BaseMessages.getString(PKG, "SqlExpressionDialog.Validate.Ok.Message"));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SqlExpressionDialog.Validate.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void openExpressionEditor(int rowIndex) {
    SqlExpressionDraft draft = new SqlExpressionDraft();
    if (rowIndex >= 0 && rowIndex < wFields.table.getItemCount()) {
      TableItem item = wFields.table.getItem(rowIndex);
      draft.setFieldName(item.getText(1));
      draft.setExpression(item.getText(2));
      draft.setHopTypeName(item.getText(3));
      draft.setLength(Const.toInt(item.getText(4), -1));
      draft.setPrecision(Const.toInt(item.getText(5), -1));
      draft.setDescription(item.getText(6));
    }
    IRowMeta prev = null;
    try {
      prev = pipelineMeta.getPrevTransformFields(variables, transformName);
    } catch (HopException ignored) {
      // Editor still opens with an empty field list.
    }
    String[] names = prev != null ? prev.getFieldNames() : new String[0];
    SqlExpressionEditorDialog editor =
        new SqlExpressionEditorDialog(shell, variables, draft, names, prev, true);
    SqlExpressionDraft result = editor.open();
    if (result == null) {
      return;
    }
    TableItem item;
    if (rowIndex >= 0 && rowIndex < wFields.table.getItemCount()) {
      item = wFields.table.getItem(rowIndex);
    } else {
      item = new TableItem(wFields.table, SWT.NONE);
    }
    item.setText(1, Const.NVL(result.getFieldName(), ""));
    item.setText(2, Const.NVL(result.getExpression(), ""));
    item.setText(3, Const.NVL(result.getHopTypeName(), ""));
    item.setText(4, result.getLength() >= 0 ? String.valueOf(result.getLength()) : "");
    item.setText(5, result.getPrecision() >= 0 ? String.valueOf(result.getPrecision()) : "");
    item.setText(6, Const.NVL(result.getDescription(), ""));
    wFields.optimizeTableView();
    input.setChanged();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    input.setKeepInputFields(wKeepInputFields.getSelection());
    input.setBusinessVaultModelFilename(wBvModelFilename.getText());
    input.setScd2TableName(wScd2Table.getText());
    saveFieldsTable();
    transformName = wTransformName.getText();
    input.setChanged();
    dispose();
  }
}
