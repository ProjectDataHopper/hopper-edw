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
package org.apache.hop.datavault.transform.datedimensiongenerator;

import org.apache.hop.core.Const;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class DateDimensionGeneratorDialog extends BaseTransformDialog {

  private static final Class<?> PKG = DateDimensionGeneratorMeta.class;

  private final DateDimensionGeneratorMeta input;

  private Text wStartDate;
  private Text wEndDate;
  private TableView wFields;
  private Button wLoadDefaults;

  public DateDimensionGeneratorDialog(
      Shell parent,
      IVariables variables,
      DateDimensionGeneratorMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Shell.Title"));

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    Label wlStartDate = new Label(shell, SWT.RIGHT);
    wlStartDate.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.StartDate.Label"));
    PropsUi.setLook(wlStartDate);
    FormData fdlStartDate = new FormData();
    fdlStartDate.left = new FormAttachment(0, 0);
    fdlStartDate.right = new FormAttachment(middle, -margin);
    fdlStartDate.top = new FormAttachment(wSpacer, margin);
    wlStartDate.setLayoutData(fdlStartDate);
    wStartDate = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wStartDate);
    FormData fdStartDate = new FormData();
    fdStartDate.left = new FormAttachment(middle, 0);
    fdStartDate.top = new FormAttachment(wSpacer, margin);
    fdStartDate.right = new FormAttachment(100, 0);
    wStartDate.setLayoutData(fdStartDate);

    Label wlEndDate = new Label(shell, SWT.RIGHT);
    wlEndDate.setText(BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.EndDate.Label"));
    PropsUi.setLook(wlEndDate);
    FormData fdlEndDate = new FormData();
    fdlEndDate.left = new FormAttachment(0, 0);
    fdlEndDate.right = new FormAttachment(middle, -margin);
    fdlEndDate.top = new FormAttachment(wStartDate, margin);
    wlEndDate.setLayoutData(fdlEndDate);
    wEndDate = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wEndDate);
    FormData fdEndDate = new FormData();
    fdEndDate.left = new FormAttachment(middle, 0);
    fdEndDate.top = new FormAttachment(wStartDate, margin);
    fdEndDate.right = new FormAttachment(100, 0);
    wEndDate.setLayoutData(fdEndDate);

    wLoadDefaults = new Button(shell, SWT.PUSH);
    wLoadDefaults.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadDefaults.Label"));
    PropsUi.setLook(wLoadDefaults);
    FormData fdLoadDefaults = new FormData();
    fdLoadDefaults.top = new FormAttachment(wEndDate, margin);
    fdLoadDefaults.right = new FormAttachment(100, 0);
    wLoadDefaults.setLayoutData(fdLoadDefaults);
    wLoadDefaults.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            loadDefaults();
          }
        });

    Label wlFields = new Label(shell, SWT.NONE);
    wlFields.setText(BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.top = new FormAttachment(wLoadDefaults, margin);
    wlFields.setLayoutData(fdlFields);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.Type"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames(),
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.FormatMask"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.Fields.Column.Locale"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };

    wFields =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            input.getFields().size(),
            null,
            props);
    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(wOk, -margin);
    wFields.setLayoutData(fdFields);

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  public void getData() {
    wStartDate.setText(Const.NVL(input.getStartDate(), ""));
    wEndDate.setText(Const.NVL(input.getEndDate(), ""));

    Table table = wFields.table;
    if (!input.getFields().isEmpty()) {
      table.removeAll();
    }
    for (int i = 0; i < input.getFields().size(); i++) {
      DateDimensionGeneratorField field = input.getFields().get(i);
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(0, "" + (i + 1));
      item.setText(1, Const.NVL(field.getName(), ""));
      item.setText(2, ValueMetaFactory.getValueMetaName(field.getHopType()));
      item.setText(3, Const.NVL(field.getLength(), ""));
      item.setText(4, Const.NVL(field.getPrecision(), ""));
      item.setText(5, Const.NVL(field.getFormatMask(), ""));
      item.setText(6, Const.NVL(field.getLocale(), ""));
    }

    wFields.optimizeTableView();
  }

  private void loadDefaults() {
    wStartDate.setText(DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE);
    wEndDate.setText(DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE);
    populateFields(DateDimensionGeneratorMetaFactory.defaultFields());
    input.setChanged();
  }

  private void populateFields(java.util.List<DateDimensionGeneratorField> fields) {
    Table table = wFields.table;
    table.removeAll();
    for (int i = 0; i < fields.size(); i++) {
      DateDimensionGeneratorField field = fields.get(i);
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(0, "" + (i + 1));
      item.setText(1, Const.NVL(field.getName(), ""));
      item.setText(2, ValueMetaFactory.getValueMetaName(field.getHopType()));
      item.setText(3, Const.NVL(field.getLength(), ""));
      item.setText(4, Const.NVL(field.getPrecision(), ""));
      item.setText(5, Const.NVL(field.getFormatMask(), ""));
      item.setText(6, Const.NVL(field.getLocale(), ""));
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
    input.setStartDate(wStartDate.getText());
    input.setEndDate(wEndDate.getText());

    input.getFields().clear();
    for (TableItem item : wFields.getNonEmptyItems()) {
      DateDimensionGeneratorField field = new DateDimensionGeneratorField();
      field.setName(item.getText(1));
      try {
        field.setHopType(ValueMetaFactory.getIdForValueMeta(item.getText(2)));
      } catch (Exception e) {
        field.setHopType(IValueMeta.TYPE_STRING);
      }
      field.setLength(item.getText(3));
      field.setPrecision(item.getText(4));
      field.setFormatMask(item.getText(5));
      field.setLocale(item.getText(6));
      input.getFields().add(field);
    }

    input.setChanged();
    dispose();
  }
}
