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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.metadata.dimensional.DmDateGeneratorConfiguration;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorField;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.DateRange;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.GeneratorContext;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorLogic.PreparedField;
import org.apache.hop.datavault.transform.datedimensiongenerator.DateDimensionGeneratorMetaFactory;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.PreviewTableSettingsDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Source-tab widgets for dimensional tables that use the date generator source type. */
public final class DmSourceDateGeneratorGuiSupport {

  private static final Class<?> PKG = HopGuiDmTableDialog.class;

  private DmSourceDateGeneratorGuiSupport() {}

  public static final class Widgets {
    public Label wlStartDate;
    public Text wStartDate;
    public Label wlEndDate;
    public Text wEndDate;
    public Label wlReferenceDate;
    public Text wReferenceDate;
    public Label wlDayOffset;
    public Text wDayOffset;
    public Label wlWeekOffset;
    public Text wWeekOffset;
    public Label wlMonthOffset;
    public Text wMonthOffset;
    public Button wLoadDefaults;
    public Button wLoadRelativeDefaults;
    public Button wLoadFiscalDefaults;
    public Button wPreviewData;
    public Label wlFields;
    public TableView wFields;

    public Control[] allControls() {
      return new Control[] {
        wlStartDate,
        wStartDate,
        wlEndDate,
        wEndDate,
        wlReferenceDate,
        wReferenceDate,
        wlDayOffset,
        wDayOffset,
        wlWeekOffset,
        wWeekOffset,
        wlMonthOffset,
        wMonthOffset,
        wLoadDefaults,
        wLoadRelativeDefaults,
        wLoadFiscalDefaults,
        wPreviewData,
        wlFields,
        wFields
      };
    }
  }

  public static Widgets create(Composite parent, IVariables variables, Control top, int margin) {
    Widgets w = new Widgets();
    int middle = PropsUi.getInstance().getMiddlePct();
    Shell shell = parent.getShell();

    w.wlStartDate = label(parent, "HopGuiDmTableDialog.DateGenerator.StartDate.Label");
    w.wlStartDate.setLayoutData(
        new FormDataBuilder().left().top(top, margin).right(middle, -margin).result());
    w.wStartDate = text(parent);
    w.wStartDate.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(top, margin).right().result());

    w.wlEndDate = label(parent, "HopGuiDmTableDialog.DateGenerator.EndDate.Label");
    w.wlEndDate.setLayoutData(
        new FormDataBuilder().left().top(w.wStartDate, margin).right(middle, -margin).result());
    w.wEndDate = text(parent);
    w.wEndDate.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(w.wStartDate, margin).right().result());

    w.wlReferenceDate = label(parent, "HopGuiDmTableDialog.DateGenerator.ReferenceDate.Label");
    w.wlReferenceDate.setLayoutData(
        new FormDataBuilder().left().top(w.wEndDate, margin).right(middle, -margin).result());
    w.wReferenceDate = text(parent);
    w.wReferenceDate.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(w.wEndDate, margin).right().result());

    w.wlDayOffset = label(parent, "HopGuiDmTableDialog.DateGenerator.DayOffset.Label");
    w.wlDayOffset.setLayoutData(
        new FormDataBuilder().left().top(w.wReferenceDate, margin).right(middle, -margin).result());
    w.wDayOffset = text(parent);
    w.wDayOffset.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(w.wReferenceDate, margin).right().result());

    w.wlWeekOffset = label(parent, "HopGuiDmTableDialog.DateGenerator.WeekOffset.Label");
    w.wlWeekOffset.setLayoutData(
        new FormDataBuilder().left().top(w.wDayOffset, margin).right(middle, -margin).result());
    w.wWeekOffset = text(parent);
    w.wWeekOffset.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(w.wDayOffset, margin).right().result());

    w.wlMonthOffset = label(parent, "HopGuiDmTableDialog.DateGenerator.MonthOffset.Label");
    w.wlMonthOffset.setLayoutData(
        new FormDataBuilder().left().top(w.wWeekOffset, margin).right(middle, -margin).result());
    w.wMonthOffset = text(parent);
    w.wMonthOffset.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(w.wWeekOffset, margin).right().result());

    w.wLoadFiscalDefaults = button(parent, "HopGuiDmTableDialog.DateGenerator.LoadFiscal.Label");
    w.wLoadFiscalDefaults.setLayoutData(
        new FormDataBuilder().right().top(w.wMonthOffset, margin).result());
    w.wLoadFiscalDefaults.addListener(
        SWT.Selection,
        e -> appendFields(w, DateDimensionGeneratorMetaFactory.fiscalDefaultFields()));

    w.wLoadRelativeDefaults =
        button(parent, "HopGuiDmTableDialog.DateGenerator.LoadRelative.Label");
    w.wLoadRelativeDefaults.setLayoutData(
        new FormDataBuilder()
            .right(w.wLoadFiscalDefaults, -margin)
            .top(w.wMonthOffset, margin)
            .result());
    w.wLoadRelativeDefaults.addListener(
        SWT.Selection,
        e -> appendFields(w, DateDimensionGeneratorMetaFactory.relativeDefaultFields()));

    w.wLoadDefaults = button(parent, "HopGuiDmTableDialog.DateGenerator.LoadDefaults.Label");
    w.wLoadDefaults.setLayoutData(
        new FormDataBuilder()
            .right(w.wLoadRelativeDefaults, -margin)
            .top(w.wMonthOffset, margin)
            .result());
    w.wLoadDefaults.addListener(SWT.Selection, e -> loadDefaults(w));

    w.wPreviewData = button(parent, "HopGuiDmTableDialog.DateGenerator.PreviewData.Label");
    w.wPreviewData.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.PreviewData.ToolTip"));
    w.wPreviewData.setLayoutData(
        new FormDataBuilder().right().top(w.wLoadDefaults, margin).result());
    w.wPreviewData.addListener(SWT.Selection, e -> previewData(shell, variables, w));

    w.wlFields = new Label(parent, SWT.LEFT);
    w.wlFields.setText(
        BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Label"));
    PropsUi.setLook(w.wlFields);
    w.wlFields.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(w.wLoadDefaults, margin)
            .right(w.wPreviewData, -margin)
            .result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Type"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames(),
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Mask"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Fields.Column.Locale"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false)
        };

    w.wFields =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());
    w.wFields.setLayoutData(
        new FormDataBuilder().left().top(w.wPreviewData, margin).right().bottom().result());
    return w;
  }

  /**
   * Generates preview rows with the same logic as the Date Dimension Generator transform and shows
   * them in a read-only dialog. Asks for a row limit first (default Hop preview size).
   */
  public static void previewData(Shell shell, IVariables variables, Widgets widgets) {
    try {
      if (widgets == null) {
        return;
      }
      DmDateGeneratorConfiguration config = readData(widgets);
      if (config.getFieldsOrEmpty().isEmpty()
          || config.getFieldsOrEmpty().stream()
              .noneMatch(field -> field != null && !Utils.isEmpty(field.getName()))) {
        throw new HopException(
            BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.Preview.NoFields"));
      }

      PropsUi props = PropsUi.getInstance();
      int defaultRows = Math.max(1, props.getDefaultPreviewSize());
      PreviewTableSettingsDialog settingsDialog =
          new PreviewTableSettingsDialog(shell, defaultRows, variables, true);
      PreviewTableSettingsDialog.Settings settings = settingsDialog.open();
      if (settings == null) {
        return;
      }
      int previewRows = settings.rowLimit > 0 ? settings.rowLimit : defaultRows;
      IVariables previewVariables = settingsDialog.getPreviewExecutionVariables();
      if (previewVariables == null) {
        previewVariables = variables;
      }

      DateRange range =
          DateDimensionGeneratorLogic.resolveDateRange(
              config.getStartDate(), config.getEndDate(), previewVariables);
      GeneratorContext context =
          DateDimensionGeneratorLogic.resolveContext(
              config.getReferenceDate(),
              config.getDayOffset(),
              config.getWeekOffset(),
              config.getMonthOffset(),
              previewVariables);
      IRowMeta rowMeta =
          DateDimensionGeneratorLogic.buildOutputRowMeta(
              config.getFieldsOrEmpty(), "date_generator_preview", previewVariables);
      List<PreparedField> prepared =
          DateDimensionGeneratorLogic.prepareFields(
              config.getFieldsOrEmpty(), "date_generator_preview", previewVariables);

      List<Object[]> rows = new ArrayList<>();
      LocalDate current = range.startDate();
      while (!current.isAfter(range.endDate()) && rows.size() < previewRows) {
        rows.add(DateDimensionGeneratorLogic.buildRow(current, prepared, context));
        current = current.plusDays(1);
      }

      long totalDays =
          DateDimensionGeneratorLogic.dayCountInclusive(range.startDate(), range.endDate());
      String message =
          BaseMessages.getString(
              PKG,
              "HopGuiDmTableDialog.DateGenerator.PreviewData.Message",
              Long.toString(rows.size()),
              Long.toString(totalDays),
              range.startDate().toString(),
              range.endDate().toString());

      new ShowRowsDialog(
              shell,
              previewVariables,
              BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.PreviewData.Title"),
              message,
              rowMeta,
              rows)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.PreviewData.ErrorTitle"),
          BaseMessages.getString(PKG, "HopGuiDmTableDialog.DateGenerator.PreviewData.ErrorMessage"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  public static void setData(Widgets w, DmDateGeneratorConfiguration config) {
    if (w == null) {
      return;
    }
    DmDateGeneratorConfiguration safe =
        config != null ? config : DmDateGeneratorConfiguration.createDefault();
    setText(w.wStartDate, safe.getStartDate());
    setText(w.wEndDate, safe.getEndDate());
    setText(w.wReferenceDate, safe.getReferenceDate());
    setText(w.wDayOffset, Const.NVL(safe.getDayOffset(), "0"));
    setText(w.wWeekOffset, Const.NVL(safe.getWeekOffset(), "0"));
    setText(w.wMonthOffset, Const.NVL(safe.getMonthOffset(), "0"));
    populateFields(w, safe.getFieldsOrEmpty());
  }

  public static DmDateGeneratorConfiguration readData(Widgets w) {
    DmDateGeneratorConfiguration config = new DmDateGeneratorConfiguration();
    if (w == null) {
      return DmDateGeneratorConfiguration.createDefault();
    }
    config.setStartDate(textValue(w.wStartDate));
    config.setEndDate(textValue(w.wEndDate));
    config.setReferenceDate(textValue(w.wReferenceDate));
    config.setDayOffset(textValue(w.wDayOffset));
    config.setWeekOffset(textValue(w.wWeekOffset));
    config.setMonthOffset(textValue(w.wMonthOffset));
    config.setFields(readFields(w));
    return config;
  }

  private static void loadDefaults(Widgets w) {
    setText(w.wStartDate, DateDimensionGeneratorMetaFactory.DEFAULT_START_DATE);
    setText(w.wEndDate, DateDimensionGeneratorMetaFactory.DEFAULT_END_DATE);
    setText(w.wReferenceDate, "");
    setText(w.wDayOffset, "0");
    setText(w.wWeekOffset, "0");
    setText(w.wMonthOffset, "0");
    populateFields(w, DateDimensionGeneratorMetaFactory.defaultFields());
  }

  private static void appendFields(Widgets w, List<DateDimensionGeneratorField> extra) {
    List<DateDimensionGeneratorField> merged = new ArrayList<>(readFields(w));
    for (DateDimensionGeneratorField field : extra) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      boolean exists =
          merged.stream()
              .anyMatch(existing -> field.getName().equalsIgnoreCase(existing.getName()));
      if (!exists) {
        merged.add(field);
      }
    }
    populateFields(w, merged);
  }

  private static void populateFields(Widgets w, List<DateDimensionGeneratorField> fields) {
    if (w.wFields == null || w.wFields.isDisposed()) {
      return;
    }
    Table table = w.wFields.table;
    table.removeAll();
    int i = 0;
    for (DateDimensionGeneratorField field : fields) {
      if (field == null) {
        continue;
      }
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(0, "" + (++i));
      item.setText(1, Const.NVL(field.getName(), ""));
      item.setText(2, ValueMetaFactory.getValueMetaName(field.getHopType()));
      item.setText(3, Const.NVL(field.getLength(), ""));
      item.setText(4, Const.NVL(field.getPrecision(), ""));
      item.setText(5, Const.NVL(field.getFormatMask(), ""));
      item.setText(6, Const.NVL(field.getLocale(), ""));
    }
    w.wFields.optimizeTableView();
  }

  private static List<DateDimensionGeneratorField> readFields(Widgets w) {
    List<DateDimensionGeneratorField> fields = new ArrayList<>();
    if (w.wFields == null || w.wFields.isDisposed()) {
      return fields;
    }
    for (TableItem item : w.wFields.getNonEmptyItems()) {
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

  private static Label label(Composite parent, String key) {
    Label label = new Label(parent, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, key));
    PropsUi.setLook(label);
    return label;
  }

  private static Text text(Composite parent) {
    Text text = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    return text;
  }

  private static Button button(Composite parent, String key) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(BaseMessages.getString(PKG, key));
    PropsUi.setLook(button);
    return button;
  }

  private static void setText(Text text, String value) {
    if (text != null && !text.isDisposed()) {
      text.setText(Const.NVL(value, ""));
    }
  }

  private static String textValue(Text text) {
    return text != null && !text.isDisposed() ? text.getText() : "";
  }
}
