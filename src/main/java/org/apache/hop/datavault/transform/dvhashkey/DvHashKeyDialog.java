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
 *
 */

package org.apache.hop.datavault.transform.dvhashkey;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashContentCasing;
import org.apache.hop.datavault.metadata.HashKeyDataType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.pipeline.transform.ITableItemInsertListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class DvHashKeyDialog extends BaseTransformDialog {
  private static final Class<?> PKG = DvHashKeyMeta.class;

  private final DvHashKeyMeta input;

  private CCombo wHashAlgorithm;
  private CCombo wHashKeyDataType;
  private CCombo wHashContentCasing;
  private Text wBusinessKeyDelimiter;
  private Text wNullPlaceholder;
  private Text wHashContentPrefix;
  private Text wHashContentSuffix;
  private Button wTrimBusinessKeys;
  private Text wResult;
  private TableView wFields;

  private ColumnInfo[] columnInfo;

  private final List<String> inputFields = new ArrayList<>();

  public DvHashKeyDialog(
      Shell parent, IVariables variables, DvHashKeyMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "DvHashKeyDialog.Shell.Title"));

    buildButtonBar().ok(e -> ok()).get(e -> get()).cancel(e -> cancel()).build();

    Label wlHashAlgorithm = new Label(shell, SWT.RIGHT);
    wlHashAlgorithm.setText(BaseMessages.getString(PKG, "DvHashKeyDialog.HashAlgorithm.Label"));
    PropsUi.setLook(wlHashAlgorithm);
    FormData fdlHashAlgorithm = new FormData();
    fdlHashAlgorithm.left = new FormAttachment(0, 0);
    fdlHashAlgorithm.right = new FormAttachment(middle, -margin);
    fdlHashAlgorithm.top = new FormAttachment(wSpacer, margin);
    wlHashAlgorithm.setLayoutData(fdlHashAlgorithm);
    wHashAlgorithm = new CCombo(shell, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wHashAlgorithm.setItems(HashAlgorithm.getDescriptions());
    PropsUi.setLook(wHashAlgorithm);
    FormData fdHashAlgorithm = new FormData();
    fdHashAlgorithm.left = new FormAttachment(middle, 0);
    fdHashAlgorithm.top = new FormAttachment(wSpacer, margin);
    fdHashAlgorithm.right = new FormAttachment(100, 0);
    wHashAlgorithm.setLayoutData(fdHashAlgorithm);

    Label wlHashKeyDataType = new Label(shell, SWT.RIGHT);
    wlHashKeyDataType.setText(BaseMessages.getString(PKG, "DvHashKeyDialog.HashKeyDataType.Label"));
    PropsUi.setLook(wlHashKeyDataType);
    FormData fdlHashKeyDataType = new FormData();
    fdlHashKeyDataType.left = new FormAttachment(0, 0);
    fdlHashKeyDataType.right = new FormAttachment(middle, -margin);
    fdlHashKeyDataType.top = new FormAttachment(wHashAlgorithm, margin);
    wlHashKeyDataType.setLayoutData(fdlHashKeyDataType);
    wHashKeyDataType = new CCombo(shell, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wHashKeyDataType.setItems(HashKeyDataType.getDescriptions());
    PropsUi.setLook(wHashKeyDataType);
    FormData fdHashKeyDataType = new FormData();
    fdHashKeyDataType.left = new FormAttachment(middle, 0);
    fdHashKeyDataType.top = new FormAttachment(wHashAlgorithm, margin);
    fdHashKeyDataType.right = new FormAttachment(100, 0);
    wHashKeyDataType.setLayoutData(fdHashKeyDataType);

    Label wlHashContentCasing = new Label(shell, SWT.RIGHT);
    wlHashContentCasing.setText(
        BaseMessages.getString(PKG, "DvHashKeyDialog.HashContentCasing.Label"));
    PropsUi.setLook(wlHashContentCasing);
    FormData fdlHashContentCasing = new FormData();
    fdlHashContentCasing.left = new FormAttachment(0, 0);
    fdlHashContentCasing.right = new FormAttachment(middle, -margin);
    fdlHashContentCasing.top = new FormAttachment(wHashKeyDataType, margin);
    wlHashContentCasing.setLayoutData(fdlHashContentCasing);
    wHashContentCasing = new CCombo(shell, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wHashContentCasing.setItems(HashContentCasing.getDescriptions());
    PropsUi.setLook(wHashContentCasing);
    FormData fdHashContentCasing = new FormData();
    fdHashContentCasing.left = new FormAttachment(middle, 0);
    fdHashContentCasing.top = new FormAttachment(wHashKeyDataType, margin);
    fdHashContentCasing.right = new FormAttachment(100, 0);
    wHashContentCasing.setLayoutData(fdHashContentCasing);

    Label wlBusinessKeyDelimiter = new Label(shell, SWT.RIGHT);
    wlBusinessKeyDelimiter.setText(
        BaseMessages.getString(PKG, "DvHashKeyDialog.BusinessKeyDelimiter.Label"));
    PropsUi.setLook(wlBusinessKeyDelimiter);
    FormData fdlBusinessKeyDelimiter = new FormData();
    fdlBusinessKeyDelimiter.left = new FormAttachment(0, 0);
    fdlBusinessKeyDelimiter.right = new FormAttachment(middle, -margin);
    fdlBusinessKeyDelimiter.top = new FormAttachment(wHashContentCasing, margin);
    wlBusinessKeyDelimiter.setLayoutData(fdlBusinessKeyDelimiter);
    wBusinessKeyDelimiter = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wBusinessKeyDelimiter);
    FormData fdBusinessKeyDelimiter = new FormData();
    fdBusinessKeyDelimiter.left = new FormAttachment(middle, 0);
    fdBusinessKeyDelimiter.top = new FormAttachment(wHashContentCasing, margin);
    fdBusinessKeyDelimiter.right = new FormAttachment(100, 0);
    wBusinessKeyDelimiter.setLayoutData(fdBusinessKeyDelimiter);

    Label wlNullPlaceholder = new Label(shell, SWT.RIGHT);
    wlNullPlaceholder.setText(BaseMessages.getString(PKG, "DvHashKeyDialog.NullPlaceholder.Label"));
    PropsUi.setLook(wlNullPlaceholder);
    FormData fdlNullPlaceholder = new FormData();
    fdlNullPlaceholder.left = new FormAttachment(0, 0);
    fdlNullPlaceholder.right = new FormAttachment(middle, -margin);
    fdlNullPlaceholder.top = new FormAttachment(wBusinessKeyDelimiter, margin);
    wlNullPlaceholder.setLayoutData(fdlNullPlaceholder);
    wNullPlaceholder = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNullPlaceholder);
    FormData fdNullPlaceholder = new FormData();
    fdNullPlaceholder.left = new FormAttachment(middle, 0);
    fdNullPlaceholder.top = new FormAttachment(wBusinessKeyDelimiter, margin);
    fdNullPlaceholder.right = new FormAttachment(100, 0);
    wNullPlaceholder.setLayoutData(fdNullPlaceholder);

    Label wlHashContentPrefix = new Label(shell, SWT.RIGHT);
    wlHashContentPrefix.setText(
        BaseMessages.getString(PKG, "DvHashKeyDialog.HashContentPrefix.Label"));
    PropsUi.setLook(wlHashContentPrefix);
    FormData fdlHashContentPrefix = new FormData();
    fdlHashContentPrefix.left = new FormAttachment(0, 0);
    fdlHashContentPrefix.right = new FormAttachment(middle, -margin);
    fdlHashContentPrefix.top = new FormAttachment(wNullPlaceholder, margin);
    wlHashContentPrefix.setLayoutData(fdlHashContentPrefix);
    wHashContentPrefix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashContentPrefix);
    FormData fdHashContentPrefix = new FormData();
    fdHashContentPrefix.left = new FormAttachment(middle, 0);
    fdHashContentPrefix.top = new FormAttachment(wNullPlaceholder, margin);
    fdHashContentPrefix.right = new FormAttachment(100, 0);
    wHashContentPrefix.setLayoutData(fdHashContentPrefix);

    Label wlHashContentSuffix = new Label(shell, SWT.RIGHT);
    wlHashContentSuffix.setText(
        BaseMessages.getString(PKG, "DvHashKeyDialog.HashContentSuffix.Label"));
    PropsUi.setLook(wlHashContentSuffix);
    FormData fdlHashContentSuffix = new FormData();
    fdlHashContentSuffix.left = new FormAttachment(0, 0);
    fdlHashContentSuffix.right = new FormAttachment(middle, -margin);
    fdlHashContentSuffix.top = new FormAttachment(wHashContentPrefix, margin);
    wlHashContentSuffix.setLayoutData(fdlHashContentSuffix);
    wHashContentSuffix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHashContentSuffix);
    FormData fdHashContentSuffix = new FormData();
    fdHashContentSuffix.left = new FormAttachment(middle, 0);
    fdHashContentSuffix.top = new FormAttachment(wHashContentPrefix, margin);
    fdHashContentSuffix.right = new FormAttachment(100, 0);
    wHashContentSuffix.setLayoutData(fdHashContentSuffix);

    Label wlTrimBusinessKeys = new Label(shell, SWT.RIGHT);
    wlTrimBusinessKeys.setText(
        BaseMessages.getString(PKG, "DvHashKeyDialog.TrimBusinessKeys.Label"));
    PropsUi.setLook(wlTrimBusinessKeys);
    FormData fdlTrimBusinessKeys = new FormData();
    fdlTrimBusinessKeys.left = new FormAttachment(0, 0);
    fdlTrimBusinessKeys.right = new FormAttachment(middle, -margin);
    fdlTrimBusinessKeys.top = new FormAttachment(wHashContentSuffix, margin);
    wlTrimBusinessKeys.setLayoutData(fdlTrimBusinessKeys);
    wTrimBusinessKeys = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wTrimBusinessKeys);
    FormData fdTrimBusinessKeys = new FormData();
    fdTrimBusinessKeys.left = new FormAttachment(middle, 0);
    fdTrimBusinessKeys.top = new FormAttachment(wlTrimBusinessKeys, 0, SWT.CENTER);
    wTrimBusinessKeys.setLayoutData(fdTrimBusinessKeys);

    Label wlResult = new Label(shell, SWT.RIGHT);
    wlResult.setText(BaseMessages.getString(PKG, "DvHashKeyDialog.Result.Label"));
    PropsUi.setLook(wlResult);
    FormData fdlResult = new FormData();
    fdlResult.left = new FormAttachment(0, 0);
    fdlResult.right = new FormAttachment(middle, -margin);
    fdlResult.top = new FormAttachment(wTrimBusinessKeys, margin);
    wlResult.setLayoutData(fdlResult);
    wResult = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wResult);
    FormData fdResult = new FormData();
    fdResult.left = new FormAttachment(middle, 0);
    fdResult.top = new FormAttachment(wTrimBusinessKeys, margin);
    fdResult.right = new FormAttachment(100, 0);
    wResult.setLayoutData(fdResult);

    Label wlFields = new Label(shell, SWT.NONE);
    wlFields.setText(BaseMessages.getString(PKG, "DvHashKeyDialog.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.top = new FormAttachment(wResult, margin);
    wlFields.setLayoutData(fdlFields);

    final int nrCols = 1;
    final int nrFields = input.getFields().size();

    columnInfo = new ColumnInfo[nrCols];
    columnInfo[0] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "DvHashKeyDialog.Fieldname.Column"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false);
    wFields =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columnInfo,
            nrFields,
            null,
            props);

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(wOk, -margin);
    wFields.setLayoutData(fdFields);

    final Runnable runnable =
        () -> {
          TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
          if (transformMeta != null) {
            try {
              IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
              for (int i = 0; i < row.size(); i++) {
                inputFields.add(row.getValueMeta(i).getName());
              }
              setComboBoxes();
            } catch (HopException e) {
              logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
            }
          }
        };
    new Thread(runnable).start();

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  private static void selectCCombo(CCombo combo, org.apache.hop.metadata.api.IEnumHasCodeAndDescription value) {
    if (combo == null || value == null) {
      return;
    }
    combo.setText(value.getDescription());
  }

  protected void setComboBoxes() {
    String[] fieldNames = ConstUi.sortFieldNames(inputFields);
    columnInfo[0].setComboValues(fieldNames);
  }

  private void get() {
    try {
      IRowMeta r = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (r != null) {
        ITableItemInsertListener insertListener =
            (tableItem, v) -> {
              tableItem.setText(2, BaseMessages.getString(PKG, "System.Combo.Yes"));
              return true;
            };
        BaseTransformDialog.getFieldsFromPrevious(
            r, wFields, 1, new int[] {1}, new int[] {}, -1, -1, insertListener);
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Title"),
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"),
          ke);
    }
  }

  public void getData() {
    selectCCombo(
        wHashAlgorithm,
        input.getHashAlgorithm() != null ? input.getHashAlgorithm() : HashAlgorithm.MD5);
    selectCCombo(
        wHashKeyDataType,
        input.getHashKeyDataType() != null ? input.getHashKeyDataType() : HashKeyDataType.HEX);
    selectCCombo(
        wHashContentCasing,
        input.getHashContentCasing() != null
            ? input.getHashContentCasing()
            : HashContentCasing.UPPER);
    wBusinessKeyDelimiter.setText(Const.NVL(input.getBusinessKeyDelimiter(), ""));
    wNullPlaceholder.setText(Const.NVL(input.getNullPlaceholder(), ""));
    wHashContentPrefix.setText(Const.NVL(input.getHashContentPrefix(), ""));
    wHashContentSuffix.setText(Const.NVL(input.getHashContentSuffix(), ""));
    wTrimBusinessKeys.setSelection(input.isTrimBusinessKeys());
    wResult.setText(Const.NVL(input.getResultFieldName(), ""));

    Table table = wFields.table;
    if (!input.getFields().isEmpty()) {
      table.removeAll();
    }
    for (int i = 0; i < input.getFields().size(); i++) {
      DvHashKeyField field = input.getFields().get(i);
      TableItem ti = new TableItem(table, SWT.NONE);
      ti.setText(0, "" + (i + 1));
      ti.setText(1, Const.NVL(field.getName(), ""));
    }

    wFields.optimizeTableView();
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

    input.setHashAlgorithm(
        EnumDialogSupport.lookupText(
            wHashAlgorithm.getText(), HashAlgorithm.class, HashAlgorithm.MD5));
    input.setHashKeyDataType(
        EnumDialogSupport.lookupText(
            wHashKeyDataType.getText(), HashKeyDataType.class, HashKeyDataType.HEX));
    input.setHashContentCasing(
        EnumDialogSupport.lookupText(
            wHashContentCasing.getText(), HashContentCasing.class, HashContentCasing.UPPER));
    input.setBusinessKeyDelimiter(wBusinessKeyDelimiter.getText());
    input.setNullPlaceholder(wNullPlaceholder.getText());
    input.setHashContentPrefix(wHashContentPrefix.getText());
    input.setHashContentSuffix(wHashContentSuffix.getText());
    input.setTrimBusinessKeys(wTrimBusinessKeys.getSelection());
    input.setResultFieldName(wResult.getText());

    input.getFields().clear();
    for (TableItem item : wFields.getNonEmptyItems()) {
      input.getFields().add(new DvHashKeyField(item.getText(1)));
    }
    input.setChanged();
    dispose();
  }
}