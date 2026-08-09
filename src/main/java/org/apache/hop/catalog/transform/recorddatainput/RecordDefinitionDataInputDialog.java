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
package org.apache.hop.catalog.transform.recorddatainput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.dimensional.DmSourceRecordDefinitionGuiSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class RecordDefinitionDataInputDialog extends BaseTransformDialog {

  private static final Class<?> PKG = RecordDefinitionDataInputMeta.class;

  private final RecordDefinitionDataInputMeta input;

  private CCombo wConnectionName;
  private Button wSelectFromInput;
  private CCombo wNamespaceField;
  private CCombo wNameField;
  private Button wSelectRecordDefinition;
  private Text wNamespaceValue;
  private Text wNameValue;
  private Text wRowLimit;

  private final List<String> inputFields = new ArrayList<>();

  public RecordDefinitionDataInputDialog(
      Shell parent,
      IVariables variables,
      RecordDefinitionDataInputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    // Separator below transform name (returned as last control).
    Control afterName = createShell(BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.Shell.Title"));

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    ModifyListener lsMod = e -> input.setChanged();

    // Extra margin under the separator before Catalog connection
    int afterSeparatorMargin = margin * 2;

    // Catalog connection
    Label wlConnectionName = new Label(shell, SWT.RIGHT);
    wlConnectionName.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.ConnectionName.Label"));
    PropsUi.setLook(wlConnectionName);
    FormData fdlConnectionName = new FormData();
    fdlConnectionName.left = new FormAttachment(0, 0);
    fdlConnectionName.right = new FormAttachment(middle, -margin);
    fdlConnectionName.top = new FormAttachment(afterName, afterSeparatorMargin);
    wlConnectionName.setLayoutData(fdlConnectionName);

    wConnectionName = new CCombo(shell, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wConnectionName);
    wConnectionName.addModifyListener(lsMod);
    FormData fdConnectionName = new FormData();
    fdConnectionName.left = new FormAttachment(middle, 0);
    fdConnectionName.right = new FormAttachment(100, 0);
    fdConnectionName.top = new FormAttachment(afterName, afterSeparatorMargin);
    wConnectionName.setLayoutData(fdConnectionName);

    // Select from input
    Label wlSelectFromInput = new Label(shell, SWT.RIGHT);
    wlSelectFromInput.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.SelectFromInput.Label"));
    PropsUi.setLook(wlSelectFromInput);
    FormData fdlSelectFromInput = new FormData();
    fdlSelectFromInput.left = new FormAttachment(0, 0);
    fdlSelectFromInput.right = new FormAttachment(middle, -margin);
    fdlSelectFromInput.top = new FormAttachment(wConnectionName, margin);
    wlSelectFromInput.setLayoutData(fdlSelectFromInput);

    wSelectFromInput = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wSelectFromInput);
    FormData fdSelectFromInput = new FormData();
    fdSelectFromInput.left = new FormAttachment(middle, 0);
    fdSelectFromInput.right = new FormAttachment(100, 0);
    fdSelectFromInput.top = new FormAttachment(wlSelectFromInput, 0, SWT.CENTER);
    wSelectFromInput.setLayoutData(fdSelectFromInput);
    wSelectFromInput.addListener(SWT.Selection, e -> {
      input.setChanged();
      setFlags();
    });

    Control previous = wSelectFromInput;

    Label wlNamespaceField = new Label(shell, SWT.RIGHT);
    wlNamespaceField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.NamespaceField.Label"));
    PropsUi.setLook(wlNamespaceField);
    FormData fdlNamespaceField = new FormData();
    fdlNamespaceField.left = new FormAttachment(0, 0);
    fdlNamespaceField.right = new FormAttachment(middle, -margin);
    fdlNamespaceField.top = new FormAttachment(previous, margin);
    wlNamespaceField.setLayoutData(fdlNamespaceField);

    wNamespaceField = new CCombo(shell, SWT.SINGLE | SWT.BORDER);
    PropsUi.setLook(wNamespaceField);
    wNamespaceField.addModifyListener(lsMod);
    FormData fdNamespaceField = new FormData();
    fdNamespaceField.left = new FormAttachment(middle, 0);
    fdNamespaceField.right = new FormAttachment(100, 0);
    fdNamespaceField.top = new FormAttachment(previous, margin);
    wNamespaceField.setLayoutData(fdNamespaceField);
    previous = wNamespaceField;

    Label wlNameField = new Label(shell, SWT.RIGHT);
    wlNameField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.NameField.Label"));
    PropsUi.setLook(wlNameField);
    FormData fdlNameField = new FormData();
    fdlNameField.left = new FormAttachment(0, 0);
    fdlNameField.right = new FormAttachment(middle, -margin);
    fdlNameField.top = new FormAttachment(previous, margin);
    wlNameField.setLayoutData(fdlNameField);

    wNameField = new CCombo(shell, SWT.SINGLE | SWT.BORDER);
    PropsUi.setLook(wNameField);
    wNameField.addModifyListener(lsMod);
    FormData fdNameField = new FormData();
    fdNameField.left = new FormAttachment(middle, 0);
    fdNameField.right = new FormAttachment(100, 0);
    fdNameField.top = new FormAttachment(previous, margin);
    wNameField.setLayoutData(fdNameField);
    previous = wNameField;

    // Select record definition (fills fixed namespace/name values)
    wSelectRecordDefinition = new Button(shell, SWT.PUSH);
    wSelectRecordDefinition.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.SelectRecordDefinition.Label"));
    PropsUi.setLook(wSelectRecordDefinition);
    FormData fdSelectRecord = new FormData();
    fdSelectRecord.left = new FormAttachment(middle, 0);
    fdSelectRecord.top = new FormAttachment(previous, margin);
    wSelectRecordDefinition.setLayoutData(fdSelectRecord);
    wSelectRecordDefinition.addListener(SWT.Selection, e -> selectRecordDefinition());
    previous = wSelectRecordDefinition;

    Label wlNamespaceValue = new Label(shell, SWT.RIGHT);
    wlNamespaceValue.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.NamespaceValue.Label"));
    PropsUi.setLook(wlNamespaceValue);
    FormData fdlNamespaceValue = new FormData();
    fdlNamespaceValue.left = new FormAttachment(0, 0);
    fdlNamespaceValue.right = new FormAttachment(middle, -margin);
    fdlNamespaceValue.top = new FormAttachment(previous, margin);
    wlNamespaceValue.setLayoutData(fdlNamespaceValue);

    wNamespaceValue = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNamespaceValue);
    wNamespaceValue.addModifyListener(lsMod);
    FormData fdNamespaceValue = new FormData();
    fdNamespaceValue.left = new FormAttachment(middle, 0);
    fdNamespaceValue.right = new FormAttachment(100, 0);
    fdNamespaceValue.top = new FormAttachment(previous, margin);
    wNamespaceValue.setLayoutData(fdNamespaceValue);
    previous = wNamespaceValue;

    Label wlNameValue = new Label(shell, SWT.RIGHT);
    wlNameValue.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.NameValue.Label"));
    PropsUi.setLook(wlNameValue);
    FormData fdlNameValue = new FormData();
    fdlNameValue.left = new FormAttachment(0, 0);
    fdlNameValue.right = new FormAttachment(middle, -margin);
    fdlNameValue.top = new FormAttachment(previous, margin);
    wlNameValue.setLayoutData(fdlNameValue);

    wNameValue = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNameValue);
    wNameValue.addModifyListener(lsMod);
    FormData fdNameValue = new FormData();
    fdNameValue.left = new FormAttachment(middle, 0);
    fdNameValue.right = new FormAttachment(100, 0);
    fdNameValue.top = new FormAttachment(previous, margin);
    wNameValue.setLayoutData(fdNameValue);
    previous = wNameValue;

    Label wlRowLimit = new Label(shell, SWT.RIGHT);
    wlRowLimit.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDataInputDialog.RowLimit.Label"));
    PropsUi.setLook(wlRowLimit);
    FormData fdlRowLimit = new FormData();
    fdlRowLimit.left = new FormAttachment(0, 0);
    fdlRowLimit.right = new FormAttachment(middle, -margin);
    fdlRowLimit.top = new FormAttachment(previous, margin);
    wlRowLimit.setLayoutData(fdlRowLimit);

    wRowLimit = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRowLimit);
    wRowLimit.addModifyListener(lsMod);
    FormData fdRowLimit = new FormData();
    fdRowLimit.left = new FormAttachment(middle, 0);
    fdRowLimit.right = new FormAttachment(100, 0);
    fdRowLimit.top = new FormAttachment(previous, margin);
    wRowLimit.setLayoutData(fdRowLimit);

    // Load catalog connections
    try {
      IHopMetadataSerializer<DataCatalogMeta> serializer =
          metadataProvider.getSerializer(DataCatalogMeta.class);
      wConnectionName.setItems(serializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading catalog connections", e);
    }

    final Runnable runnable =
        () -> {
          TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
          if (transformMeta != null) {
            try {
              IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
              if (row != null) {
                for (int i = 0; i < row.size(); i++) {
                  inputFields.add(row.getValueMeta(i).getName());
                }
                shell
                    .getDisplay()
                    .asyncExec(
                        () -> {
                          String[] fieldNames =
                              org.apache.hop.ui.core.ConstUi.sortFieldNames(inputFields);
                          wNamespaceField.setItems(fieldNames);
                          wNameField.setItems(fieldNames);
                          wNamespaceField.setText(Const.NVL(input.getNamespaceField(), ""));
                          wNameField.setText(Const.NVL(input.getNameField(), ""));
                        });
              }
            } catch (HopException e) {
              logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
            }
          }
        };
    new Thread(runnable).start();

    getData();
    setFlags();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void setFlags() {
    boolean fromInput = wSelectFromInput.getSelection();
    wNamespaceField.setEnabled(fromInput);
    wNameField.setEnabled(fromInput);
    wNamespaceValue.setEnabled(!fromInput);
    wNameValue.setEnabled(!fromInput);
    wSelectRecordDefinition.setEnabled(!fromInput);
  }

  private void selectRecordDefinition() {
    try {
      String catalogConnection = wConnectionName.getText();
      if (Utils.isEmpty(catalogConnection)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "RecordDefinitionDataInputDialog.Error.MissingCatalogConnection"));
      }
      List<DmSourceRecordDefinitionGuiSupport.PreviewableRecordRef> records =
          DmSourceRecordDefinitionGuiSupport.listPreviewableRecordDefinitions(
              catalogConnection, variables, metadataProvider);
      if (records.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "RecordDefinitionDataInputDialog.Error.NoRecordDefinitions"));
      }
      String[] choices = records.stream().map(r -> r.label()).toArray(String[]::new);
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              shell,
              choices,
              BaseMessages.getString(
                  PKG, "RecordDefinitionDataInputDialog.SelectRecordDefinition.Title"),
              BaseMessages.getString(
                  PKG, "RecordDefinitionDataInputDialog.SelectRecordDefinition.Message"));
      if (dialog.open() == null) {
        return;
      }
      int[] indices = dialog.getSelectionIndeces();
      if (indices == null
          || indices.length == 0
          || indices[0] < 0
          || indices[0] >= records.size()) {
        return;
      }
      DmSourceRecordDefinitionGuiSupport.PreviewableRecordRef selected = records.get(indices[0]);
      wNamespaceValue.setText(Const.NVL(selected.namespace(), ""));
      wNameValue.setText(Const.NVL(selected.name(), ""));
      input.setChanged();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "RecordDefinitionDataInputDialog.SelectRecordDefinition.Title"),
          e.getMessage(),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void getData() {
    wTransformName.setText(Const.NVL(transformName, ""));
    wConnectionName.setText(Const.NVL(input.getCatalogConnectionName(), ""));
    wSelectFromInput.setSelection(input.isSelectFromInput());
    wNamespaceField.setText(Const.NVL(input.getNamespaceField(), ""));
    wNameField.setText(Const.NVL(input.getNameField(), ""));
    wNamespaceValue.setText(Const.NVL(input.getNamespaceValue(), ""));
    wNameValue.setText(Const.NVL(input.getNameValue(), ""));
    wRowLimit.setText(Const.NVL(input.getRowLimit(), "0"));
    input.setChanged(changed);
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
    transformName = wTransformName.getText();
    input.setCatalogConnectionName(wConnectionName.getText());
    input.setSelectFromInput(wSelectFromInput.getSelection());
    input.setNamespaceField(wNamespaceField.getText());
    input.setNameField(wNameField.getText());
    input.setNamespaceValue(wNamespaceValue.getText());
    input.setNameValue(wNameValue.getText());
    input.setRowLimit(wRowLimit.getText());
    dispose();
  }
}
