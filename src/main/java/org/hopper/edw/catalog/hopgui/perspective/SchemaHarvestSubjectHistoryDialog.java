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
package org.hopper.edw.catalog.hopgui.perspective;

import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
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
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestSubjectSummary;

/** Subject-level timeline of schema harvest results. Double-click opens changes + fields. */
public final class SchemaHarvestSubjectHistoryDialog {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Shell parentShell;
  private final IVariables variables;
  private final DatabaseMeta databaseMeta;
  private final String operationsSchema;
  private final String subjectKey;
  private final List<HarvestSubjectSummary> entries;
  private final IHopMetadataProvider metadataProvider;
  private final String catalogConnectionName;

  private Shell shell;
  private TableView tableView;

  public SchemaHarvestSubjectHistoryDialog(
      Shell parent,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String subjectKey,
      List<HarvestSubjectSummary> entries,
      IHopMetadataProvider metadataProvider,
      String catalogConnectionName) {
    this.parentShell = parent;
    this.variables = variables;
    this.databaseMeta = databaseMeta;
    this.operationsSchema = operationsSchema;
    this.subjectKey = subjectKey;
    this.entries = entries != null ? entries : List.of();
    this.metadataProvider = metadataProvider;
    this.catalogConnectionName = catalogConnectionName;
  }

  public void open() {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }
    shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Subject.Title"));
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, e -> saveWindowProperty());

    int margin = PropsUi.getMargin();

    Label messageLabel = new Label(shell, SWT.LEFT | SWT.WRAP);
    messageLabel.setText(
        BaseMessages.getString(
            PKG, "SchemaHarvestHistory.Subject.Message", Const.NVL(subjectKey, "")));
    PropsUi.setLook(messageLabel);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.right = new FormAttachment(100, 0);
    fdMessage.top = new FormAttachment(0, margin);
    messageLabel.setLayoutData(fdMessage);

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, e -> close());

    Button wOpen = new Button(shell, SWT.PUSH);
    wOpen.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.OpenChanges"));
    PropsUi.setLook(wOpen);
    wOpen.addListener(SWT.Selection, e -> openChangesForSelection());

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOpen, wClose}, margin, null);

    tableView = buildTable(margin, messageLabel, wOpen);
    populateRows();
    tableView.addListener(SWT.DefaultSelection, e -> openChangesForSelection());
    tableView.table.addListener(SWT.MouseDoubleClick, e -> openChangesForSelection());

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

  private TableView buildTable(int margin, Label messageLabel, Button bottom) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("SchemaHarvestHistory.Column.FinishedAt", false),
          col("SchemaHarvestHistory.Column.InSync", false),
          col("SchemaHarvestHistory.Column.Changes", true),
          col("SchemaHarvestHistory.Column.Discovery", false),
          col("SchemaHarvestHistory.Column.Database", false),
          col("SchemaHarvestHistory.Column.RunId", false),
          col("SchemaHarvestHistory.Column.Message", false),
        };
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
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(messageLabel, margin);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(bottom, -margin);
    view.setLayoutData(fd);
    return view;
  }

  private ColumnInfo col(String key, boolean numeric) {
    ColumnInfo c =
        new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, numeric);
    c.setReadOnly(true);
    return c;
  }

  private void populateRows() {
    tableView.clearAll(false);
    for (int i = 0; i < entries.size(); i++) {
      HarvestSubjectSummary entry = entries.get(i);
      TableItem item =
          i == 0 && tableView.table.getItemCount() > 0
              ? tableView.table.getItem(0)
              : new TableItem(tableView.table, SWT.NONE);
      item.setText(1, entry.finishedAt() != null ? DATE_FORMAT.format(entry.finishedAt()) : "");
      item.setText(2, entry.inSync() ? "Y" : "N");
      item.setText(3, entry.changeCount() != null ? Long.toString(entry.changeCount()) : "0");
      item.setText(4, Const.NVL(entry.discoveryStatus(), ""));
      item.setText(5, Const.NVL(entry.databaseMetaName(), ""));
      item.setText(6, Const.NVL(entry.harvestRunId(), ""));
      item.setText(7, Const.NVL(entry.message(), ""));
      item.setData(entry);
    }
    tableView.optimizeTableView();
  }

  private void openChangesForSelection() {
    int index = tableView.table.getSelectionIndex();
    if (index < 0 || index >= tableView.table.getItemCount()) {
      return;
    }
    Object data = tableView.table.getItem(index).getData();
    if (!(data instanceof HarvestSubjectSummary entry) || Utils.isEmpty(entry.harvestRunId())) {
      return;
    }
    try {
      List<HarvestChange> changes =
          SchemaHarvestHistoryReader.listChangesForSubject(
              databaseMeta, operationsSchema, entry.harvestRunId(), subjectKey, variables);
      List<HarvestedField> fields =
          SchemaHarvestHistoryReader.listFieldsForSubject(
              databaseMeta, operationsSchema, entry.harvestRunId(), subjectKey, variables);
      if (changes.isEmpty() && fields.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Title"));
        box.setMessage(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Empty"));
        box.open();
        return;
      }
      new SchemaHarvestChangesDialog(
              shell, variables, entry.harvestRunId(), subjectKey, changes, fields)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
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
