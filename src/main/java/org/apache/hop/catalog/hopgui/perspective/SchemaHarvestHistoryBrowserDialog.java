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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestSubjectSummary;
import org.apache.hop.catalog.model.RecordDefinitionKey;
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
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Group-scoped schema harvest browser: harvest runs (master) and subjects (detail) with filters.
 * Double-click a subject to open changes / field snapshots.
 */
public final class SchemaHarvestHistoryBrowserDialog {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Shell parentShell;
  private final IVariables variables;
  private final DatabaseMeta databaseMeta;
  private final String operationsSchema;
  private final String resourceGroupName;
  private final List<HarvestRunSummary> runs;
  private final IHopMetadataProvider metadataProvider;
  private final String catalogConnectionName;

  private Shell shell;
  private TableView runsView;
  private TableView subjectsView;
  private Button wOnlyChanges;
  private Text wConnectionFilter;
  private Text wSourceTypeFilter;
  private List<HarvestSubjectSummary> currentSubjects = new ArrayList<>();

  public SchemaHarvestHistoryBrowserDialog(
      Shell parent,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String resourceGroupName,
      String unusedSubjectFilter,
      List<HarvestRunSummary> runs,
      IHopMetadataProvider metadataProvider,
      String catalogConnectionName) {
    this.parentShell = parent;
    this.variables = variables;
    this.databaseMeta = databaseMeta;
    this.operationsSchema = operationsSchema;
    this.resourceGroupName = resourceGroupName;
    this.runs = runs != null ? runs : List.of();
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
    shell.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Browser.Title"));
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, e -> saveWindowProperty());

    int margin = PropsUi.getMargin();

    Label messageLabel = new Label(shell, SWT.LEFT | SWT.WRAP);
    messageLabel.setText(
        BaseMessages.getString(
            PKG, "SchemaHarvestHistory.Browser.Message", Const.NVL(resourceGroupName, "")));
    PropsUi.setLook(messageLabel);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.right = new FormAttachment(100, 0);
    fdMessage.top = new FormAttachment(0, margin);
    messageLabel.setLayoutData(fdMessage);

    Composite filterBar = new Composite(shell, SWT.NONE);
    PropsUi.setLook(filterBar);
    filterBar.setLayout(new FormLayout());
    FormData fdFilter = new FormData();
    fdFilter.left = new FormAttachment(0, 0);
    fdFilter.top = new FormAttachment(messageLabel, margin);
    fdFilter.right = new FormAttachment(100, 0);
    filterBar.setLayoutData(fdFilter);

    wOnlyChanges = new Button(filterBar, SWT.CHECK);
    wOnlyChanges.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Filter.OnlyChanges"));
    PropsUi.setLook(wOnlyChanges);
    FormData fdOnly = new FormData();
    fdOnly.left = new FormAttachment(0, 0);
    fdOnly.top = new FormAttachment(0, 0);
    wOnlyChanges.setLayoutData(fdOnly);
    wOnlyChanges.addListener(SWT.Selection, e -> reloadSubjectsForSelection());

    Label wlConn = new Label(filterBar, SWT.RIGHT);
    wlConn.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Filter.Connection"));
    PropsUi.setLook(wlConn);
    FormData fdlConn = new FormData();
    fdlConn.left = new FormAttachment(wOnlyChanges, margin * 2);
    fdlConn.top = new FormAttachment(wOnlyChanges, 0, SWT.CENTER);
    wlConn.setLayoutData(fdlConn);

    wConnectionFilter = new Text(filterBar, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wConnectionFilter);
    FormData fdConn = new FormData();
    fdConn.left = new FormAttachment(wlConn, margin);
    fdConn.top = new FormAttachment(wOnlyChanges, 0, SWT.CENTER);
    fdConn.width = 120;
    wConnectionFilter.setLayoutData(fdConn);
    wConnectionFilter.addListener(SWT.DefaultSelection, e -> reloadSubjectsForSelection());

    Label wlType = new Label(filterBar, SWT.RIGHT);
    wlType.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Filter.SourceType"));
    PropsUi.setLook(wlType);
    FormData fdlType = new FormData();
    fdlType.left = new FormAttachment(wConnectionFilter, margin * 2);
    fdlType.top = new FormAttachment(wOnlyChanges, 0, SWT.CENTER);
    wlType.setLayoutData(fdlType);

    wSourceTypeFilter = new Text(filterBar, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceTypeFilter);
    FormData fdType = new FormData();
    fdType.left = new FormAttachment(wlType, margin);
    fdType.top = new FormAttachment(wOnlyChanges, 0, SWT.CENTER);
    fdType.width = 100;
    wSourceTypeFilter.setLayoutData(fdType);
    wSourceTypeFilter.addListener(SWT.DefaultSelection, e -> reloadSubjectsForSelection());

    Button wApply = new Button(filterBar, SWT.PUSH);
    wApply.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Filter.Apply"));
    PropsUi.setLook(wApply);
    FormData fdApply = new FormData();
    fdApply.left = new FormAttachment(wSourceTypeFilter, margin);
    fdApply.top = new FormAttachment(wOnlyChanges, 0, SWT.CENTER);
    wApply.setLayoutData(fdApply);
    wApply.addListener(SWT.Selection, e -> reloadSubjectsForSelection());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, e -> close());

    Button wOpenChanges = new Button(shell, SWT.PUSH);
    wOpenChanges.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.OpenChanges"));
    PropsUi.setLook(wOpenChanges);
    wOpenChanges.addListener(SWT.Selection, e -> openChangesForSelection());

    Button wOpenCatalog = new Button(shell, SWT.PUSH);
    wOpenCatalog.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.OpenInCatalog"));
    PropsUi.setLook(wOpenCatalog);
    wOpenCatalog.addListener(SWT.Selection, e -> openSelectedInCatalog());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOpenChanges, wOpenCatalog, wClose}, margin, null);

    SashForm sash = new SashForm(shell, SWT.VERTICAL);
    PropsUi.setLook(sash);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, 0);
    fdSash.top = new FormAttachment(filterBar, margin);
    fdSash.right = new FormAttachment(100, 0);
    fdSash.bottom = new FormAttachment(wClose, -margin);
    sash.setLayoutData(fdSash);

    runsView = buildRunsTable(sash);
    subjectsView = buildSubjectsTable(sash);
    sash.setWeights(40, 60);

    populateRuns();
    runsView.addListener(SWT.Selection, e -> reloadSubjectsForSelection());
    runsView.table.addListener(SWT.Selection, e -> reloadSubjectsForSelection());
    subjectsView.addListener(SWT.DefaultSelection, e -> openChangesForSelection());
    subjectsView.table.addListener(SWT.MouseDoubleClick, e -> openChangesForSelection());

    if (runsView.table.getItemCount() > 0) {
      runsView.table.setSelection(0);
      reloadSubjectsForSelection();
    }

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

  private TableView buildRunsTable(SashForm sash) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("SchemaHarvestHistory.Column.FinishedAt", false),
          col("SchemaHarvestHistory.Column.Status", false),
          col("SchemaHarvestHistory.Column.Subjects", true),
          col("SchemaHarvestHistory.Column.WithChanges", true),
          col("SchemaHarvestHistory.Column.Changes", true),
          col("SchemaHarvestHistory.Column.Errors", true),
          col("SchemaHarvestHistory.Column.Baseline", false),
          col("SchemaHarvestHistory.Column.RunId", false),
        };
    TableView view =
        new TableView(
            variables,
            sash,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            Math.max(1, runs.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    return view;
  }

  private TableView buildSubjectsTable(SashForm sash) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("SchemaHarvestHistory.Column.Subject", false),
          col("SchemaHarvestHistory.Column.InSync", false),
          col("SchemaHarvestHistory.Column.Changes", true),
          col("SchemaHarvestHistory.Column.Discovery", false),
          col("SchemaHarvestHistory.Column.SourceType", false),
          col("SchemaHarvestHistory.Column.Database", false),
          col("SchemaHarvestHistory.Column.Table", false),
          col("SchemaHarvestHistory.Column.Message", false),
        };
    TableView view =
        new TableView(
            variables,
            sash,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            1,
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    return view;
  }

  private ColumnInfo col(String key, boolean numeric) {
    ColumnInfo c =
        new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, numeric);
    c.setReadOnly(true);
    return c;
  }

  private void populateRuns() {
    runsView.clearAll(false);
    for (int i = 0; i < runs.size(); i++) {
      HarvestRunSummary run = runs.get(i);
      TableItem item =
          i == 0 && runsView.table.getItemCount() > 0
              ? runsView.table.getItem(0)
              : new TableItem(runsView.table, SWT.NONE);
      item.setText(1, run.finishedAt() != null ? DATE_FORMAT.format(run.finishedAt()) : "");
      item.setText(2, Const.NVL(run.status(), ""));
      item.setText(3, run.subjectCount() != null ? Long.toString(run.subjectCount()) : "");
      item.setText(
          4, run.subjectsWithChanges() != null ? Long.toString(run.subjectsWithChanges()) : "");
      item.setText(5, run.changeCount() != null ? Long.toString(run.changeCount()) : "");
      item.setText(6, run.errorCount() != null ? Long.toString(run.errorCount()) : "");
      item.setText(7, Const.NVL(run.expectedBaseline(), ""));
      item.setText(8, Const.NVL(run.harvestRunId(), ""));
      item.setData(run);
    }
    runsView.optimizeTableView();
  }

  private void reloadSubjectsForSelection() {
    HarvestRunSummary run = selectedRun();
    currentSubjects = new ArrayList<>();
    subjectsView.clearAll(false);
    if (run == null || Utils.isEmpty(run.harvestRunId())) {
      subjectsView.optimizeTableView();
      return;
    }
    try {
      currentSubjects =
          SchemaHarvestHistoryReader.listSubjectsForRun(
              databaseMeta,
              operationsSchema,
              run.harvestRunId(),
              emptyToNull(wConnectionFilter.getText()),
              emptyToNull(wSourceTypeFilter.getText()),
              wOnlyChanges.getSelection(),
              variables);
      for (int i = 0; i < currentSubjects.size(); i++) {
        HarvestSubjectSummary subject = currentSubjects.get(i);
        TableItem item =
            i == 0 && subjectsView.table.getItemCount() > 0
                ? subjectsView.table.getItem(0)
                : new TableItem(subjectsView.table, SWT.NONE);
        item.setText(1, Const.NVL(subject.subjectKey(), ""));
        item.setText(2, subject.inSync() ? "Y" : "N");
        item.setText(3, subject.changeCount() != null ? Long.toString(subject.changeCount()) : "0");
        item.setText(4, Const.NVL(subject.discoveryStatus(), ""));
        item.setText(5, Const.NVL(subject.sourceType(), ""));
        item.setText(6, Const.NVL(subject.databaseMetaName(), ""));
        String table = Const.NVL(subject.schemaName(), "");
        if (!Utils.isEmpty(subject.tableName())) {
          table = Utils.isEmpty(table) ? subject.tableName() : table + "." + subject.tableName();
        }
        item.setText(7, table);
        item.setText(8, Const.NVL(subject.message(), ""));
        item.setData(subject);
      }
      subjectsView.optimizeTableView();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
          e);
    }
  }

  private HarvestRunSummary selectedRun() {
    int index = runsView.table.getSelectionIndex();
    if (index < 0 || index >= runsView.table.getItemCount()) {
      return null;
    }
    Object data = runsView.table.getItem(index).getData();
    return data instanceof HarvestRunSummary summary ? summary : null;
  }

  private HarvestSubjectSummary selectedSubject() {
    int index = subjectsView.table.getSelectionIndex();
    if (index < 0 || index >= subjectsView.table.getItemCount()) {
      return null;
    }
    Object data = subjectsView.table.getItem(index).getData();
    return data instanceof HarvestSubjectSummary summary ? summary : null;
  }

  private void openChangesForSelection() {
    HarvestSubjectSummary subject = selectedSubject();
    HarvestRunSummary run = selectedRun();
    if (subject == null || run == null) {
      return;
    }
    try {
      List<HarvestChange> changes =
          SchemaHarvestHistoryReader.listChangesForSubject(
              databaseMeta, operationsSchema, run.harvestRunId(), subject.subjectKey(), variables);
      List<HarvestedField> fields =
          SchemaHarvestHistoryReader.listFieldsForSubject(
              databaseMeta, operationsSchema, run.harvestRunId(), subject.subjectKey(), variables);
      if (changes.isEmpty() && fields.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Title"));
        box.setMessage(BaseMessages.getString(PKG, "SchemaHarvestHistory.Changes.Empty"));
        box.open();
        return;
      }
      new SchemaHarvestChangesDialog(
              shell, variables, run.harvestRunId(), subject.subjectKey(), changes, fields)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
          e);
    }
  }

  private void openSelectedInCatalog() {
    HarvestSubjectSummary subject = selectedSubject();
    if (subject == null
        || Utils.isEmpty(subject.subjectKey())
        || Utils.isEmpty(catalogConnectionName)) {
      return;
    }
    try {
      int slash = subject.subjectKey().lastIndexOf('/');
      String namespace = slash > 0 ? subject.subjectKey().substring(0, slash) : "";
      String name =
          slash >= 0 && slash < subject.subjectKey().length() - 1
              ? subject.subjectKey().substring(slash + 1)
              : subject.subjectKey();
      RecordDefinitionKey key = new RecordDefinitionKey(namespace, name);
      HopGui hopGui = HopGui.getInstance();
      DataCatalogPerspective perspective =
          (DataCatalogPerspective)
              hopGui.getPerspectiveManager().findPerspective(DataCatalogPerspective.class);
      if (perspective == null) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "SchemaHarvestHistory.OpenInCatalog"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "SchemaHarvestHistory.OpenInCatalog.NotFound", subject.subjectKey()));
        box.open();
        return;
      }
      perspective.selectRecordDefinition(catalogConnectionName, key);
      close();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
          e);
    }
  }

  private static String emptyToNull(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    return value.trim();
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
