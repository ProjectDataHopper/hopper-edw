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
package org.hopper.edw.datavault.transform.datedimensiongenerator;

import java.util.ArrayList;
import java.util.List;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

public class DateDimensionGeneratorDialog extends BaseTransformDialog {

  private static final Class<?> PKG = DateDimensionGeneratorMeta.class;

  private final DateDimensionGeneratorMeta input;

  private Text wStartDate;
  private Text wEndDate;
  private Text wReferenceDate;
  private Text wDayOffset;
  private Text wWeekOffset;
  private Text wMonthOffset;
  private TableView wFields;
  private Button wLoadDefaults;
  private Button wLoadRelativeDefaults;
  private Button wLoadFiscalDefaults;
  private Button wLoadLoadTimestamp;

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
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.DATE_DIMENSION_GENERATOR);

    wStartDate =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.StartDate.Label"), wSpacer);
    wEndDate =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.EndDate.Label"), wStartDate);
    wReferenceDate =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.ReferenceDate.Label"),
            wEndDate);
    wDayOffset =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.DayOffset.Label"),
            wReferenceDate);
    wWeekOffset =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.WeekOffset.Label"),
            wDayOffset);
    wMonthOffset =
        addLabeledText(
            BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.MonthOffset.Label"),
            wWeekOffset);

    wLoadFiscalDefaults = new Button(shell, SWT.PUSH);
    wLoadFiscalDefaults.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadFiscalDefaults.Label"));
    PropsUi.setLook(wLoadFiscalDefaults);
    FormData fdLoadFiscal = new FormData();
    fdLoadFiscal.top = new FormAttachment(wMonthOffset, margin);
    fdLoadFiscal.right = new FormAttachment(100, 0);
    wLoadFiscalDefaults.setLayoutData(fdLoadFiscal);
    wLoadFiscalDefaults.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            appendFields(DateDimensionGeneratorMetaFactory.fiscalDefaultFields());
          }
        });

    wLoadRelativeDefaults = new Button(shell, SWT.PUSH);
    wLoadRelativeDefaults.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadRelativeDefaults.Label"));
    PropsUi.setLook(wLoadRelativeDefaults);
    FormData fdLoadRelative = new FormData();
    fdLoadRelative.top = new FormAttachment(wMonthOffset, margin);
    fdLoadRelative.right = new FormAttachment(wLoadFiscalDefaults, -margin);
    wLoadRelativeDefaults.setLayoutData(fdLoadRelative);
    wLoadRelativeDefaults.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            appendFields(DateDimensionGeneratorMetaFactory.relativeDefaultFields());
          }
        });

    wLoadDefaults = new Button(shell, SWT.PUSH);
    wLoadDefaults.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadDefaults.Label"));
    PropsUi.setLook(wLoadDefaults);
    FormData fdLoadDefaults = new FormData();
    fdLoadDefaults.top = new FormAttachment(wMonthOffset, margin);
    fdLoadDefaults.right = new FormAttachment(wLoadRelativeDefaults, -margin);
    wLoadDefaults.setLayoutData(fdLoadDefaults);
    wLoadDefaults.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            loadDefaults();
          }
        });

    wLoadLoadTimestamp = new Button(shell, SWT.PUSH);
    wLoadLoadTimestamp.setText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadLoadTimestamp.Label"));
    wLoadLoadTimestamp.setToolTipText(
        BaseMessages.getString(PKG, "DateDimensionGeneratorDialog.LoadLoadTimestamp.ToolTip"));
    PropsUi.setLook(wLoadLoadTimestamp);
    FormData fdLoadTimestamp = new FormData();
    fdLoadTimestamp.top = new FormAttachment(wMonthOffset, margin);
    fdLoadTimestamp.right = new FormAttachment(wLoadDefaults, -margin);
    wLoadLoadTimestamp.setLayoutData(fdLoadTimestamp);
    wLoadLoadTimestamp.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            appendFields(
                List.of(
                    DateDimensionGeneratorMetaFactory.loadTimestampField(
                        DateDimensionGeneratorMetaFactory.DEFAULT_LOAD_TIMESTAMP_FIELD)));
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

  private Text addLabeledText(String labelText, Control top) {
    Label label = new Label(shell, SWT.RIGHT);
    label.setText(labelText);
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top = new FormAttachment(top, margin);
    label.setLayoutData(fdl);

    Text text = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fdt = new FormData();
    fdt.left = new FormAttachment(middle, 0);
    fdt.top = new FormAttachment(top, margin);
    fdt.right = new FormAttachment(100, 0);
    text.setLayoutData(fdt);
    return text;
  }

  public void getData() {
    wStartDate.setText(Const.NVL(input.getStartDate(), ""));
    wEndDate.setText(Const.NVL(input.getEndDate(), ""));
    wReferenceDate.setText(Const.NVL(input.getReferenceDate(), ""));
    wDayOffset.setText(Const.NVL(input.getDayOffset(), "0"));
    wWeekOffset.setText(Const.NVL(input.getWeekOffset(), "0"));
    wMonthOffset.setText(Const.NVL(input.getMonthOffset(), "0"));

    Table table = wFields.table;
    if (!input.getFields().isEmpty()) {
      table.removeAll();
    }
    for (int i = 0; i < input.getFields().size(); i++) {
      addFieldRow(table, i, input.getFields().get(i));
    }

    wFields.optimizeTableView();
  }

  private void loadDefaults() {
    wStartDate.setText(DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE);
    wEndDate.setText(DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE);
    wReferenceDate.setText("");
    wDayOffset.setText("0");
    wWeekOffset.setText("0");
    wMonthOffset.setText("0");
    populateFields(DateDimensionGeneratorMetaFactory.defaultFields());
    input.setChanged();
  }

  private void appendFields(List<DateDimensionGeneratorField> extraFields) {
    List<DateDimensionGeneratorField> merged = new ArrayList<>(readFieldsFromTable());
    for (DateDimensionGeneratorField extra : extraFields) {
      if (extra == null || Utils.isEmpty(extra.getName())) {
        continue;
      }
      boolean exists =
          merged.stream()
              .anyMatch(existing -> extra.getName().equalsIgnoreCase(existing.getName()));
      if (!exists) {
        merged.add(extra);
      }
    }
    populateFields(merged);
    input.setChanged();
  }

  private void populateFields(List<DateDimensionGeneratorField> fields) {
    Table table = wFields.table;
    table.removeAll();
    for (int i = 0; i < fields.size(); i++) {
      addFieldRow(table, i, fields.get(i));
    }
    wFields.optimizeTableView();
  }

  private void addFieldRow(Table table, int index, DateDimensionGeneratorField field) {
    TableItem item = new TableItem(table, SWT.NONE);
    item.setText(0, "" + (index + 1));
    item.setText(1, Const.NVL(field.getName(), ""));
    item.setText(2, ValueMetaFactory.getValueMetaName(field.getHopType()));
    item.setText(3, Const.NVL(field.getLength(), ""));
    item.setText(4, Const.NVL(field.getPrecision(), ""));
    item.setText(5, Const.NVL(field.getFormatMask(), ""));
    item.setText(6, Const.NVL(field.getLocale(), ""));
  }

  private List<DateDimensionGeneratorField> readFieldsFromTable() {
    List<DateDimensionGeneratorField> fields = new ArrayList<>();
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
      fields.add(field);
    }
    return fields;
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
    input.setReferenceDate(wReferenceDate.getText());
    input.setDayOffset(wDayOffset.getText());
    input.setWeekOffset(wWeekOffset.getText());
    input.setMonthOffset(wMonthOffset.getText());
    input.getFields().clear();
    input.getFields().addAll(readFieldsFromTable());
    input.setChanged();
    dispose();
  }
}
