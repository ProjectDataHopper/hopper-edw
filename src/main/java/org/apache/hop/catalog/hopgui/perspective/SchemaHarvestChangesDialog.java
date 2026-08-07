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
package org.apache.hop.catalog.hopgui.perspective;

import java.util.List;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Drill-down: field changes and expected vs discovered field snapshots for one harvest subject. */
public final class SchemaHarvestChangesDialog {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;

  private final Shell parentShell;
  private final IVariables variables;
  private final String harvestRunId;
  private final String subjectKey;
  private final List<HarvestChange> changes;
  private final List<HarvestedField> fields;

  private Shell shell;

  public SchemaHarvestChangesDialog(
      Shell parent,
      IVariables variables,
      String harvestRunId,
      String subjectKey,
      List<HarvestChange> changes,
      List<HarvestedField> fields) {
    this.parentShell = parent;
    this.variables = variables;
    this.harvestRunId = harvestRunId;
    this.subjectKey = subjectKey;
    this.changes = changes != null ? changes : List.of();
    this.fields = fields != null ? fields : List.of();
  }

  public void open() {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }
    shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Title"));
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, e -> saveWindowProperty());

    int margin = PropsUi.getMargin();

    Label messageLabel = new Label(shell, SWT.LEFT | SWT.WRAP);
    messageLabel.setText(
        BaseMessages.getString(
            PKG,
            "SchemaHarvestHistory.Changes.Message",
            Const.NVL(subjectKey, ""),
            Const.NVL(harvestRunId, "")));
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
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wClose}, margin, null);

    CTabFolder folder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(folder);
    FormData fdFolder = new FormData();
    fdFolder.left = new FormAttachment(0, 0);
    fdFolder.top = new FormAttachment(messageLabel, margin);
    fdFolder.right = new FormAttachment(100, 0);
    fdFolder.bottom = new FormAttachment(wClose, -margin);
    folder.setLayoutData(fdFolder);

    CTabItem changesTab = new CTabItem(folder, SWT.NONE);
    changesTab.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Tab.Changes"));
    TableView changesView = buildChangesTable(folder);
    changesTab.setControl(changesView);

    CTabItem fieldsTab = new CTabItem(folder, SWT.NONE);
    fieldsTab.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Tab.Fields"));
    TableView fieldsView = buildFieldsTable(folder);
    fieldsTab.setControl(fieldsView);

    folder.setSelection(0);
    populateChanges(changesView);
    populateFields(fieldsView);

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

  private TableView buildChangesTable(CTabFolder folder) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("SchemaHarvestHistory.Changes.Column.Severity"),
          col("SchemaHarvestHistory.Changes.Column.Kind"),
          col("SchemaHarvestHistory.Changes.Column.Field"),
          col("SchemaHarvestHistory.Changes.Column.Detail"),
        };
    TableView view =
        new TableView(
            variables,
            folder,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(1, changes.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    return view;
  }

  private TableView buildFieldsTable(CTabFolder folder) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("SchemaHarvestHistory.Changes.Column.Role"),
          col("SchemaHarvestHistory.Changes.Column.Field"),
          col("SchemaHarvestHistory.Changes.Column.HopType"),
          col("SchemaHarvestHistory.Changes.Column.Length"),
          col("SchemaHarvestHistory.Changes.Column.Precision"),
          col("SchemaHarvestHistory.Changes.Column.Pk"),
          col("SchemaHarvestHistory.Changes.Column.NativeType"),
        };
    TableView view =
        new TableView(
            variables,
            folder,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(1, fields.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    return view;
  }

  private ColumnInfo col(String key) {
    ColumnInfo c =
        new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, false);
    c.setReadOnly(true);
    return c;
  }

  private void populateChanges(TableView view) {
    view.clearAll(false);
    for (int i = 0; i < changes.size(); i++) {
      HarvestChange change = changes.get(i);
      TableItem item =
          i == 0 && view.table.getItemCount() > 0
              ? view.table.getItem(0)
              : new TableItem(view.table, SWT.NONE);
      item.setText(1, Const.NVL(change.getSeverity(), ""));
      item.setText(2, Const.NVL(change.getChangeKind(), ""));
      item.setText(3, Const.NVL(change.getFieldName(), ""));
      String detail =
          !org.apache.hop.core.util.Utils.isEmpty(change.getActualDetail())
              ? change.getActualDetail()
              : Const.NVL(change.getExpectedDetail(), "");
      item.setText(4, Const.NVL(detail, ""));
    }
    view.optimizeTableView();
  }

  private void populateFields(TableView view) {
    view.clearAll(false);
    for (int i = 0; i < fields.size(); i++) {
      HarvestedField field = fields.get(i);
      TableItem item =
          i == 0 && view.table.getItemCount() > 0
              ? view.table.getItem(0)
              : new TableItem(view.table, SWT.NONE);
      FieldRole role = field.getRole();
      item.setText(1, role != null ? role.name() : "");
      item.setText(2, Const.NVL(field.getFieldName(), ""));
      item.setText(3, Const.NVL(field.getHopType(), ""));
      item.setText(4, Const.NVL(field.getLength(), ""));
      item.setText(5, Const.NVL(field.getPrecision(), ""));
      item.setText(6, Integer.toString(field.getPrimaryKeyPosition()));
      item.setText(7, Const.NVL(field.getSourceDataType(), ""));
    }
    view.optimizeTableView();
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
