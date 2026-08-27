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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.dbt.DbtImportConflictPolicy;
import org.hopper.edw.datavault.dbt.DbtImportDestination;
import org.hopper.edw.datavault.dbt.DbtImportOptions;
import org.hopper.edw.datavault.dbt.DbtImportService;
import org.hopper.edw.datavault.dbt.DbtModelDraft;
import org.hopper.edw.datavault.dbt.DbtProjectParser;
import org.hopper.edw.datavault.dbt.DbtProjectScan;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Scan a dbt-core project and choose models to import into Business Vault. */
public class HopGuiDbtImportDialog {

  private static final Class<?> PKG = HopGuiDbtImportDialog.class;

  private static final String[] DESTINATIONS = {
    "Current model", "New .hbv file", "Split by first-level folder"
  };
  private static final String[] CONFLICTS = {"Skip existing", "Replace existing"};

  private final Shell parent;
  private final IVariables variables;
  private final String suggestedLibraryName;
  private Shell shell;
  private TextVar wFolder;
  private Text wFilter;
  private TableView wModels;
  private Combo wDestination;
  private Combo wConflict;
  private Button wImportMacros;
  private Text wLibraryName;
  private DbtProjectScan scan;
  private DbtImportOptions result;
  private boolean ok;

  public HopGuiDbtImportDialog(Shell parent, IVariables variables, String suggestedLibraryName) {
    this.parent = parent;
    this.variables = variables;
    this.suggestedLibraryName = suggestedLibraryName;
  }

  public DbtImportOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Title"));
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();
    int middle = 25;

    Label wlFolder = new Label(shell, SWT.RIGHT);
    wlFolder.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Folder.Label"));
    PropsUi.setLook(wlFolder);
    FormData fdlFolder = new FormData();
    fdlFolder.left = new FormAttachment(0, 0);
    fdlFolder.top = new FormAttachment(0, margin);
    fdlFolder.right = new FormAttachment(middle, -margin);
    wlFolder.setLayoutData(fdlFolder);

    Button wBrowse = new Button(shell, SWT.PUSH);
    wBrowse.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Browse.Label"));
    PropsUi.setLook(wBrowse);
    FormData fdBrowse = new FormData();
    fdBrowse.right = new FormAttachment(100, 0);
    fdBrowse.top = new FormAttachment(0, margin);
    wBrowse.setLayoutData(fdBrowse);
    wBrowse.addListener(SWT.Selection, e -> browseFolder());

    Button wScan = new Button(shell, SWT.PUSH);
    wScan.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Scan.Label"));
    PropsUi.setLook(wScan);
    FormData fdScan = new FormData();
    fdScan.right = new FormAttachment(wBrowse, -margin);
    fdScan.top = new FormAttachment(0, margin);
    wScan.setLayoutData(fdScan);
    wScan.addListener(SWT.Selection, e -> scanProject());

    wFolder = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFolder);
    FormData fdFolder = new FormData();
    fdFolder.left = new FormAttachment(middle, 0);
    fdFolder.top = new FormAttachment(0, margin);
    fdFolder.right = new FormAttachment(wScan, -margin);
    wFolder.setLayoutData(fdFolder);

    Label wlFilter = new Label(shell, SWT.RIGHT);
    wlFilter.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Filter.Label"));
    PropsUi.setLook(wlFilter);
    FormData fdlFilter = new FormData();
    fdlFilter.left = new FormAttachment(0, 0);
    fdlFilter.top = new FormAttachment(wFolder, margin);
    fdlFilter.right = new FormAttachment(middle, -margin);
    wlFilter.setLayoutData(fdlFilter);

    Button wSelectAll = new Button(shell, SWT.PUSH);
    wSelectAll.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.SelectAll.Label"));
    PropsUi.setLook(wSelectAll);
    FormData fdSelectAll = new FormData();
    fdSelectAll.right = new FormAttachment(100, 0);
    fdSelectAll.top = new FormAttachment(wFolder, margin);
    wSelectAll.setLayoutData(fdSelectAll);
    wSelectAll.addListener(SWT.Selection, e -> setAllChecked(true));

    Button wSelectNone = new Button(shell, SWT.PUSH);
    wSelectNone.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.SelectNone.Label"));
    PropsUi.setLook(wSelectNone);
    FormData fdSelectNone = new FormData();
    fdSelectNone.right = new FormAttachment(wSelectAll, -margin);
    fdSelectNone.top = new FormAttachment(wFolder, margin);
    wSelectNone.setLayoutData(fdSelectNone);
    wSelectNone.addListener(SWT.Selection, e -> setAllChecked(false));

    wFilter = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFilter);
    FormData fdFilter = new FormData();
    fdFilter.left = new FormAttachment(middle, 0);
    fdFilter.top = new FormAttachment(wFolder, margin);
    fdFilter.right = new FormAttachment(wSelectNone, -margin);
    wFilter.setLayoutData(fdFilter);
    wFilter.addModifyListener(e -> fillModelTable());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Import"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"}),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Path"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Materialized"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Col.Issues"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };
    wModels =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(0, 0);
    fdModels.top = new FormAttachment(wFilter, margin);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.bottom = new FormAttachment(75, 0);
    wModels.setLayoutData(fdModels);

    Label wlDestination = new Label(shell, SWT.RIGHT);
    wlDestination.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Destination.Label"));
    PropsUi.setLook(wlDestination);
    FormData fdlDestination = new FormData();
    fdlDestination.left = new FormAttachment(0, 0);
    fdlDestination.top = new FormAttachment(wModels, margin);
    fdlDestination.right = new FormAttachment(middle, -margin);
    wlDestination.setLayoutData(fdlDestination);

    wDestination = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wDestination);
    wDestination.setItems(DESTINATIONS);
    wDestination.select(0);
    FormData fdDestination = new FormData();
    fdDestination.left = new FormAttachment(middle, 0);
    fdDestination.top = new FormAttachment(wModels, margin);
    fdDestination.right = new FormAttachment(60, 0);
    wDestination.setLayoutData(fdDestination);

    Label wlConflict = new Label(shell, SWT.RIGHT);
    wlConflict.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Conflict.Label"));
    PropsUi.setLook(wlConflict);
    FormData fdlConflict = new FormData();
    fdlConflict.left = new FormAttachment(wDestination, margin);
    fdlConflict.top = new FormAttachment(wModels, margin);
    fdlConflict.right = new FormAttachment(75, 0);
    wlConflict.setLayoutData(fdlConflict);

    wConflict = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wConflict);
    wConflict.setItems(CONFLICTS);
    wConflict.select(0);
    FormData fdConflict = new FormData();
    fdConflict.left = new FormAttachment(wlConflict, margin);
    fdConflict.top = new FormAttachment(wModels, margin);
    fdConflict.right = new FormAttachment(100, 0);
    wConflict.setLayoutData(fdConflict);

    wImportMacros = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wImportMacros);
    wImportMacros.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Macros.Label"));
    wImportMacros.setSelection(true);
    FormData fdMacros = new FormData();
    fdMacros.left = new FormAttachment(middle, 0);
    fdMacros.top = new FormAttachment(wDestination, margin);
    wImportMacros.setLayoutData(fdMacros);

    wLibraryName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLibraryName);
    wLibraryName.setText(Const.NVL(suggestedLibraryName, "dbt-macros"));
    FormData fdLibrary = new FormData();
    fdLibrary.left = new FormAttachment(wImportMacros, margin);
    fdLibrary.top = new FormAttachment(wDestination, margin);
    fdLibrary.right = new FormAttachment(100, 0);
    wLibraryName.setLayoutData(fdLibrary);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Import.Label"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_DBT_IMPORT);
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    BaseTransformDialog.setSize(shell, 960, 640);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok ? result : null;
  }

  private void browseFolder() {
    String folder = BaseDialog.presentDirectoryDialog(shell, wFolder.getText(), null, variables);
    if (!Utils.isEmpty(folder)) {
      wFolder.setText(folder);
      scanProject();
    }
  }

  private void scanProject() {
    try {
      String folder = variables.resolve(wFolder.getText());
      scan = DbtProjectParser.scan(folder);
      if (Utils.isEmpty(wLibraryName.getText()) || "dbt-macros".equals(wLibraryName.getText())) {
        wLibraryName.setText(DbtImportService.defaultLibraryName(scan));
      }
      if (scan.getModels().size() > DbtImportOptions.DEFAULT_SPLIT_THRESHOLD) {
        wDestination.select(2);
      }
      fillModelTable();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private void fillModelTable() {
    wModels.table.removeAll();
    if (scan == null) {
      new TableItem(wModels.table, SWT.NONE);
      wModels.optimizeTableView();
      return;
    }
    String filter = wFilter.getText() != null ? wFilter.getText().toLowerCase(Locale.ROOT) : "";
    for (DbtModelDraft draft : scan.getModels()) {
      if (!matchesFilter(draft, filter)) {
        continue;
      }
      TableItem item = new TableItem(wModels.table, SWT.NONE);
      item.setText(1, draft.isImportable() ? "Y" : "N");
      item.setText(2, Const.NVL(draft.getName(), ""));
      item.setText(3, Const.NVL(draft.getOriginRelativePath(), ""));
      item.setText(4, Const.NVL(draft.getDbtMaterialized(), "view"));
      item.setText(5, Const.NVL(draft.getDescription(), ""));
      item.setText(6, draft.issueSummary());
      item.setData(draft);
    }
    if (wModels.table.getItemCount() == 0) {
      new TableItem(wModels.table, SWT.NONE);
    }
    wModels.optimizeTableView();
  }

  private static boolean matchesFilter(DbtModelDraft draft, String filter) {
    if (Utils.isEmpty(filter)) {
      return true;
    }
    return contains(draft.getName(), filter)
        || contains(draft.getOriginRelativePath(), filter)
        || contains(draft.getDescription(), filter)
        || contains(draft.issueSummary(), filter);
  }

  private static boolean contains(String value, String filter) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(filter);
  }

  private void setAllChecked(boolean checked) {
    String flag = checked ? "Y" : "N";
    for (int i = 0; i < wModels.nrNonEmpty(); i++) {
      wModels.getNonEmpty(i).setText(1, flag);
    }
  }

  private void ok() {
    if (scan == null) {
      scanProject();
      if (scan == null) {
        return;
      }
    }
    result = new DbtImportOptions();
    result.setProjectRoot(scan.getProjectRoot());
    result.setScan(scan);
    result.setImportMacros(wImportMacros.getSelection());
    result.setLibraryName(wLibraryName.getText());
    result.setDestination(destination());
    result.setConflictPolicy(
        wConflict.getSelectionIndex() == 1
            ? DbtImportConflictPolicy.REPLACE
            : DbtImportConflictPolicy.SKIP);
    for (int i = 0; i < wModels.nrNonEmpty(); i++) {
      TableItem item = wModels.getNonEmpty(i);
      if (!"Y".equalsIgnoreCase(item.getText(1))) {
        continue;
      }
      Object data = item.getData();
      if (data instanceof DbtModelDraft draft) {
        result.getSelectedModels().add(draft);
      }
    }
    if (result.getSelectedModels().isEmpty()) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Error.NoSelection"),
          new Exception(BaseMessages.getString(PKG, "HopGuiDbtImportDialog.Error.NoSelection")));
      return;
    }
    ok = true;
    dispose();
  }

  private DbtImportDestination destination() {
    return switch (wDestination.getSelectionIndex()) {
      case 1 -> DbtImportDestination.NEW_MODEL;
      case 2 -> DbtImportDestination.SPLIT_BY_FOLDER;
      default -> DbtImportDestination.CURRENT_MODEL;
    };
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
