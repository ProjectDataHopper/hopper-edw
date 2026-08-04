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
package org.apache.hop.catalog.transform.tablemetadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class DatabaseTableMetadataDialog extends BaseTransformDialog {

  private static final Class<?> PKG = DatabaseTableMetadataMeta.class;

  private final DatabaseTableMetadataMeta input;

  private org.eclipse.swt.custom.CCombo wConnectionName;
  private Button wSelectFromInput;
  private Button wIncludeForeignKeys;
  private org.eclipse.swt.custom.CCombo wConnectionField;
  private org.eclipse.swt.custom.CCombo wSchemaField;
  private org.eclipse.swt.custom.CCombo wTableField;
  private Text wSchemaName;
  private Text wTableName;
  private Text wOutDatabaseConnection;
  private Text wOutSchemaName;
  private Text wOutTableName;
  private Text wOutFieldPosition;
  private Text wOutFieldName;
  private Text wOutFieldType;
  private Text wOutFieldLength;
  private Text wOutFieldPrecision;
  private Text wOutPkPosition;
  private Text wOutSourceDataType;
  private Text wOutFkConstraint;
  private Text wOutFkPosition;
  private Text wOutFkRefSchema;
  private Text wOutFkRefTable;
  private Text wOutFkRefColumn;

  private final List<String> inputFields = new ArrayList<>();

  public DatabaseTableMetadataDialog(
      Shell parent,
      IVariables variables,
      DatabaseTableMetadataMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "DatabaseTableMetadataDialog.Shell.Title"));
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    buildGeneralTab(wTabFolder);
    buildOutputTab(wTabFolder);
    wTabFolder.setSelection(0);

    loadDatabaseConnections();
    loadInputFields();
    getData();
    setFlags();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void buildGeneralTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "DatabaseTableMetadataDialog.GeneralTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wConnectionName =
            addCombo(
                comp,
                "DatabaseTableMetadataDialog.Connection.Label",
                last,
                middle,
                margin,
                new String[0]);
    last =
        wSelectFromInput =
            addCheckbox(
                comp, "DatabaseTableMetadataDialog.SelectFromInput.Label", last, middle, margin);
    wSelectFromInput.addListener(SWT.Selection, e -> setFlags());
    last =
        wIncludeForeignKeys =
            addCheckbox(
                comp, "DatabaseTableMetadataDialog.IncludeForeignKeys.Label", last, middle, margin);
    last =
        wConnectionField =
            addFieldCombo(
                comp, "DatabaseTableMetadataDialog.ConnectionField.Label", last, middle, margin);
    last =
        wSchemaField =
            addFieldCombo(
                comp, "DatabaseTableMetadataDialog.SchemaField.Label", last, middle, margin);
    last =
        wTableField =
            addFieldCombo(
                comp, "DatabaseTableMetadataDialog.TableField.Label", last, middle, margin);
    last =
        wSchemaName =
            addTextField(
                comp, "DatabaseTableMetadataDialog.SchemaName.Label", last, middle, margin);
    last =
        wTableName =
            addTextField(comp, "DatabaseTableMetadataDialog.TableName.Label", last, middle, margin);
  }

  private void buildOutputTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "DatabaseTableMetadataDialog.OutputTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wOutDatabaseConnection =
            addTextField(
                comp,
                "DatabaseTableMetadataDialog.OutputDatabaseConnection.Label",
                last,
                middle,
                margin);
    last =
        wOutSchemaName =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputSchemaName.Label", last, middle, margin);
    last =
        wOutTableName =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputTableName.Label", last, middle, margin);
    last =
        wOutFieldPosition =
            addTextField(
                comp,
                "DatabaseTableMetadataDialog.OutputFieldPosition.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldName =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFieldName.Label", last, middle, margin);
    last =
        wOutFieldType =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFieldType.Label", last, middle, margin);
    last =
        wOutFieldLength =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFieldLength.Label", last, middle, margin);
    last =
        wOutFieldPrecision =
            addTextField(
                comp,
                "DatabaseTableMetadataDialog.OutputFieldPrecision.Label",
                last,
                middle,
                margin);
    last =
        wOutPkPosition =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputPkPosition.Label", last, middle, margin);
    last =
        wOutSourceDataType =
            addTextField(
                comp,
                "DatabaseTableMetadataDialog.OutputSourceDataType.Label",
                last,
                middle,
                margin);
    last =
        wOutFkConstraint =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFkConstraint.Label", last, middle, margin);
    last =
        wOutFkPosition =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFkPosition.Label", last, middle, margin);
    last =
        wOutFkRefSchema =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFkRefSchema.Label", last, middle, margin);
    last =
        wOutFkRefTable =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFkRefTable.Label", last, middle, margin);
    last =
        wOutFkRefColumn =
            addTextField(
                comp, "DatabaseTableMetadataDialog.OutputFkRefColumn.Label", last, middle, margin);
  }

  private org.eclipse.swt.custom.CCombo addCombo(
      Composite composite,
      String labelKey,
      Control previous,
      int middle,
      int margin,
      String[] items) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    org.eclipse.swt.custom.CCombo combo = new org.eclipse.swt.custom.CCombo(composite, SWT.BORDER);
    PropsUi.setLook(combo);
    combo.setItems(items);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    combo.setLayoutData(fd);
    return combo;
  }

  private org.eclipse.swt.custom.CCombo addFieldCombo(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    return addCombo(composite, labelKey, previous, middle, margin, new String[0]);
  }

  private Text addTextField(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Text text = new Text(composite, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    text.setLayoutData(fd);
    return text;
  }

  private Button addCheckbox(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    Button button = new Button(composite, SWT.CHECK);
    button.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    button.setLayoutData(fd);
    return button;
  }

  private void loadDatabaseConnections() {
    try {
      IHopMetadataSerializer<DatabaseMeta> serializer =
          metadataProvider.getSerializer(DatabaseMeta.class);
      wConnectionName.setItems(serializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading database connections", e);
    }
  }

  private void loadInputFields() {
    TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
    if (transformMeta == null) {
      return;
    }
    new Thread(
            () -> {
              try {
                IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
                if (row != null) {
                  for (int i = 0; i < row.size(); i++) {
                    inputFields.add(row.getValueMeta(i).getName());
                  }
                  String[] fieldNames = org.apache.hop.ui.core.ConstUi.sortFieldNames(inputFields);
                  shell.getDisplay().asyncExec(() -> populateFieldCombos(fieldNames));
                }
              } catch (HopException e) {
                logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
              }
            })
        .start();
  }

  private void populateFieldCombos(String[] fieldNames) {
    wConnectionField.setItems(fieldNames);
    wSchemaField.setItems(fieldNames);
    wTableField.setItems(fieldNames);
    getData();
    setFlags();
  }

  private void setFlags() {
    boolean fromInput = wSelectFromInput.getSelection();
    wConnectionField.setEnabled(fromInput);
    wSchemaField.setEnabled(fromInput);
    wTableField.setEnabled(fromInput);
    wSchemaName.setEnabled(!fromInput);
    wTableName.setEnabled(!fromInput);
  }

  public void getData() {
    if (wConnectionName == null) {
      return;
    }
    wConnectionName.setText(Const.NVL(input.getConnectionName(), ""));
    wSelectFromInput.setSelection(input.isSelectFromInput());
    wIncludeForeignKeys.setSelection(input.isIncludeForeignKeys());
    wConnectionField.setText(Const.NVL(input.getConnectionField(), ""));
    wSchemaField.setText(Const.NVL(input.getSchemaField(), ""));
    wTableField.setText(Const.NVL(input.getTableField(), ""));
    wSchemaName.setText(Const.NVL(input.getSchemaName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wOutDatabaseConnection.setText(Const.NVL(input.getOutputDatabaseConnectionField(), ""));
    wOutSchemaName.setText(Const.NVL(input.getOutputSchemaNameField(), ""));
    wOutTableName.setText(Const.NVL(input.getOutputTableNameField(), ""));
    wOutFieldPosition.setText(Const.NVL(input.getOutputFieldPositionField(), ""));
    wOutFieldName.setText(Const.NVL(input.getOutputFieldNameField(), ""));
    wOutFieldType.setText(Const.NVL(input.getOutputFieldTypeField(), ""));
    wOutFieldLength.setText(Const.NVL(input.getOutputFieldLengthField(), ""));
    wOutFieldPrecision.setText(Const.NVL(input.getOutputFieldPrecisionField(), ""));
    wOutPkPosition.setText(Const.NVL(input.getOutputFieldPrimaryKeyPositionField(), ""));
    wOutSourceDataType.setText(Const.NVL(input.getOutputSourceDataTypeField(), ""));
    wOutFkConstraint.setText(Const.NVL(input.getOutputFkConstraintNameField(), ""));
    wOutFkPosition.setText(Const.NVL(input.getOutputFkPositionField(), ""));
    wOutFkRefSchema.setText(Const.NVL(input.getOutputFkReferencedSchemaField(), ""));
    wOutFkRefTable.setText(Const.NVL(input.getOutputFkReferencedTableField(), ""));
    wOutFkRefColumn.setText(Const.NVL(input.getOutputFkReferencedColumnField(), ""));
  }

  private void cancel() {
    transformName = null;
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setConnectionName(wConnectionName.getText());
    input.setSelectFromInput(wSelectFromInput.getSelection());
    input.setIncludeForeignKeys(wIncludeForeignKeys.getSelection());
    input.setConnectionField(wConnectionField.getText());
    input.setSchemaField(wSchemaField.getText());
    input.setTableField(wTableField.getText());
    input.setSchemaName(wSchemaName.getText());
    input.setTableName(wTableName.getText());
    input.setOutputDatabaseConnectionField(wOutDatabaseConnection.getText());
    input.setOutputSchemaNameField(wOutSchemaName.getText());
    input.setOutputTableNameField(wOutTableName.getText());
    input.setOutputFieldPositionField(wOutFieldPosition.getText());
    input.setOutputFieldNameField(wOutFieldName.getText());
    input.setOutputFieldTypeField(wOutFieldType.getText());
    input.setOutputFieldLengthField(wOutFieldLength.getText());
    input.setOutputFieldPrecisionField(wOutFieldPrecision.getText());
    input.setOutputFieldPrimaryKeyPositionField(wOutPkPosition.getText());
    input.setOutputSourceDataTypeField(wOutSourceDataType.getText());
    input.setOutputFkConstraintNameField(wOutFkConstraint.getText());
    input.setOutputFkPositionField(wOutFkPosition.getText());
    input.setOutputFkReferencedSchemaField(wOutFkRefSchema.getText());
    input.setOutputFkReferencedTableField(wOutFkRefTable.getText());
    input.setOutputFkReferencedColumnField(wOutFkRefColumn.getText());
    input.setChanged();
    dispose();
  }
}
