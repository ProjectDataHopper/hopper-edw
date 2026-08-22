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
package org.apache.hop.catalog.hopgui.perspective;

import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.quality.history.DataQualityHistoryReader;
import org.apache.hop.quality.history.DataQualityHistoryReader.FindingEntry;
import org.apache.hop.quality.history.DataQualityHistoryReader.SubjectHistoryEntry;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/**
 * TableView dialog listing recent quality profile snapshots for a subject. Double-click a row to
 * open findings for that run.
 */
public final class QualityHistoryBrowserDialog {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Shell parentShell;
  private final IVariables variables;
  private final DatabaseMeta databaseMeta;
  private final String operationsSchema;
  private final String subjectKey;
  private final List<SubjectHistoryEntry> entries;

  private Shell shell;
  private TableView tableView;

  public QualityHistoryBrowserDialog(
      Shell parent,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String subjectKey,
      List<SubjectHistoryEntry> entries) {
    this.parentShell = parent;
    this.variables = variables;
    this.databaseMeta = databaseMeta;
    this.operationsSchema = operationsSchema;
    this.subjectKey = subjectKey;
    this.entries = entries != null ? entries : List.of();
  }

  public void open() {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }

    shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Title"));
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, event -> saveWindowProperty());

    int margin = PropsUi.getMargin();

    Label messageLabel = new Label(shell, SWT.LEFT | SWT.WRAP);
    messageLabel.setText(
        BaseMessages.getString(
            PKG,
            "RecordDefinitionDetailsPanel.Quality.History.Message",
            Const.NVL(subjectKey, "")));
    PropsUi.setLook(messageLabel);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.right = new FormAttachment(100, 0);
    fdMessage.top = new FormAttachment(0, margin);
    messageLabel.setLayoutData(fdMessage);

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, e -> close());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wClose}, margin, null);

    tableView = buildTableView(margin, messageLabel, wClose);
    populateRows();
    tableView.addListener(SWT.DefaultSelection, e -> openFindingsForSelection());
    tableView.table.addListener(SWT.MouseDoubleClick, e -> openFindingsForSelection());

    BaseTransformDialog.setSize(shell);
    shell.open();

    Display display = parentShell.getDisplay();
    while (shell != null && !shell.isDisposed()) {
      if (display == null || display.isDisposed()) {
        break;
      }
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private TableView buildTableView(int margin, Label messageLabel, Button wClose) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.CapturedAt"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.Lifecycle"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.RowCount"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              true),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.Findings"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              true),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.Blocking"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              true),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.LoadId"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "RecordDefinitionDetailsPanel.Quality.History.Column.RunId"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    for (ColumnInfo column : columns) {
      column.setReadOnly(true);
    }

    TableView view =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            Math.max(1, entries.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);

    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, 0);
    fdTable.top = new FormAttachment(messageLabel, margin);
    fdTable.right = new FormAttachment(100, 0);
    fdTable.bottom = new FormAttachment(wClose, -margin);
    view.setLayoutData(fdTable);
    return view;
  }

  private void populateRows() {
    tableView.clearAll(false);
    for (int i = 0; i < entries.size(); i++) {
      SubjectHistoryEntry entry = entries.get(i);
      TableItem item;
      if (i == 0 && tableView.table.getItemCount() > 0) {
        item = tableView.table.getItem(0);
      } else {
        item = new TableItem(tableView.table, SWT.NONE);
      }
      item.setText(1, entry.capturedAt() != null ? DATE_FORMAT.format(entry.capturedAt()) : "");
      item.setText(2, Const.NVL(entry.lifecycle(), ""));
      item.setText(3, entry.rowCount() != null ? Long.toString(entry.rowCount()) : "");
      item.setText(4, entry.findingCount() != null ? Long.toString(entry.findingCount()) : "");
      item.setText(5, entry.blockingCount() != null ? Long.toString(entry.blockingCount()) : "");
      item.setText(6, Const.NVL(entry.loadId(), ""));
      item.setText(7, Const.NVL(entry.qualityRunId(), ""));
      item.setData(entry);
    }
    tableView.optimizeTableView();
  }

  private void openFindingsForSelection() {
    int index = tableView.table.getSelectionIndex();
    if (index < 0 || index >= tableView.table.getItemCount()) {
      return;
    }
    TableItem item = tableView.table.getItem(index);
    Object data = item.getData();
    SubjectHistoryEntry entry =
        data instanceof SubjectHistoryEntry subjectHistoryEntry
            ? subjectHistoryEntry
            : findEntryByRunId(item.getText(7));
    if (entry == null || Utils.isEmpty(entry.qualityRunId())) {
      return;
    }
    openFindingsDialog(entry);
  }

  private SubjectHistoryEntry findEntryByRunId(String runId) {
    if (Utils.isEmpty(runId)) {
      return null;
    }
    for (SubjectHistoryEntry entry : entries) {
      if (runId.equals(entry.qualityRunId())) {
        return entry;
      }
    }
    return null;
  }

  private void openFindingsDialog(SubjectHistoryEntry entry) {
    try {
      List<FindingEntry> findings =
          DataQualityHistoryReader.listFindings(
              databaseMeta, operationsSchema, entry.qualityRunId(), subjectKey, variables);
      if (findings.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(
            BaseMessages.getString(
                PKG, "RecordDefinitionDetailsPanel.Quality.History.Findings.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "RecordDefinitionDetailsPanel.Quality.History.Findings.Empty"));
        box.open();
        return;
      }
      new QualityHistoryFindingsDialog(shell, variables, entry.qualityRunId(), findings).open();
    } catch (DataQualityHistoryReader.QualityHistoryTablesMissingException e) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
      box.setText(
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"));
      box.setMessage(DataQualityHistoryReader.MSG_TABLES_MISSING);
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Title"),
          BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Error.Message"),
          e);
    }
  }

  private void close() {
    saveWindowProperty();
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  private void saveWindowProperty() {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
  }
}
