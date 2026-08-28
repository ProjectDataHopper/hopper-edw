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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.GuiProgressSupport;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.DatabaseSchemaImportSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.ImportSourceSchemaOptionsDialog;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.SourceSchemaImportOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.SourceSchemaImportResult;

/** GUI helper for importing database schema (tables + PK/FK) into a source model. */
public final class HopGuiSourceModelImportSupport {

  private static final Class<?> PKG = HopGuiSourceModelImportSupport.class;

  private HopGuiSourceModelImportSupport() {}

  public static void importSchema(HopGui hopGui, SourceModel model, Runnable onChanged) {
    if (hopGui == null || model == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();

    String preferredDb = model.getConfigurationOrDefault().getDefaultDatabase();
    String preferredSchema = model.getConfigurationOrDefault().getDefaultSchema();
    String preferredCatalog = model.getConfigurationOrDefault().getCatalogConnection();

    ImportSourceSchemaOptionsDialog optionsDialog =
        new ImportSourceSchemaOptionsDialog(
            shell, variables, metadataProvider, preferredDb, preferredSchema, preferredCatalog);
    SourceSchemaImportOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String connectionName = Const.NVL(options.getDatabaseName(), "");
    if (Utils.isEmpty(connectionName)) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setText(BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoConnection.Title"));
      mb.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoConnection.Message"));
      mb.open();
      return;
    }

    if (options.isPublishToCatalog() && Utils.isEmpty(options.getCatalogConnectionName())) {
      // Fall back to model config before failing.
      if (!Utils.isEmpty(preferredCatalog)) {
        options.setCatalogConnectionName(preferredCatalog);
      }
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceModelImportSupport.ErrorLoadingConnection.Message", connectionName),
          e);
      return;
    }
    if (databaseMeta == null) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceModelImportSupport.ErrorLoadingConnection.Message", connectionName),
          null);
      return;
    }

    List<String> selectedTables = promptForTables(shell, variables, databaseMeta, options);
    if (selectedTables == null || selectedTables.isEmpty()) {
      return;
    }

    GuiProgressSupport.ProgressResult<SourceSchemaImportResult> progress =
        GuiProgressSupport.run(
            shell,
            true,
            monitor ->
                DatabaseSchemaImportSupport.importTables(
                    model,
                    databaseMeta,
                    options,
                    selectedTables,
                    variables,
                    metadataProvider,
                    monitor));
    if (progress.value() == null) {
      return;
    }
    SourceSchemaImportResult result = progress.value();
    DatabaseSchemaImportSupport.applyImportResult(model, result);
    if (options.isPublishToCatalog()) {
      DvDatabaseSourceImportSupport.refreshCatalogPerspective();
    }
    if (onChanged != null) {
      onChanged.run();
    }
    showResultDialog(shell, result, progress.cancelled());
  }

  private static List<String> promptForTables(
      Shell shell,
      IVariables variables,
      DatabaseMeta databaseMeta,
      SourceSchemaImportOptions options) {
    GuiProgressSupport.ProgressResult<String[]> listed =
        GuiProgressSupport.run(
            shell,
            true,
            monitor ->
                DvDatabaseSourceImportSupport.listTableNames(
                    databaseMeta, variables, options.getSchemaName(), monitor));
    if (listed.cancelled() || listed.value() == null) {
      return null;
    }
    String[] tableNames = listed.value();

    if (tableNames.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoTablesFound.Title"));
      mb.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoTablesFound.Message"));
      mb.open();
      return null;
    }

    String[] sorted = DvDatabaseSourceImportSupport.sortedStrippedTableNames(tableNames);
    EnterSelectionDialog pickDialog =
        new EnterSelectionDialog(
            shell,
            sorted,
            BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.PickTables.Title"),
            BaseMessages.getString(
                PKG, "HopGuiSourceModelImportSupport.PickTables.Message", sorted.length));
    pickDialog.setMulti(true);
    List<Integer> preselected =
        DvDatabaseSourceImportSupport.defaultPreselectedTableIndexes(sorted.length);
    if (!preselected.isEmpty()) {
      pickDialog.setSelectedNrs(preselected);
    }
    if (pickDialog.open() == null) {
      return null;
    }
    int[] indexes = pickDialog.getSelectionIndeces();
    Set<String> picked =
        DvDatabaseSourceImportSupport.tableNamesForSelectionIndexes(sorted, indexes);
    if (picked.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoneSelected.Title"));
      mb.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.NoneSelected.Message"));
      mb.open();
      return List.of();
    }
    return new ArrayList<>(picked);
  }

  private static void showResultDialog(
      Shell shell, SourceSchemaImportResult result, boolean cancelled) {
    StringBuilder message = new StringBuilder();
    if (cancelled) {
      message.append(
          BaseMessages.getString(
              PKG,
              "HopGuiSourceModelImportSupport.Cancelled.Message",
              result.getImportedTablesOrEmpty().size()));
      message.append(Const.CR).append(Const.CR);
    }
    message.append(
        BaseMessages.getString(
            PKG,
            "HopGuiSourceModelImportSupport.Success.Message",
            result.getImportedTablesOrEmpty().size(),
            result.getImportedRelationshipsOrEmpty().size(),
            result.getPublishedCatalogNamesOrEmpty().size()));

    if (!result.getWarningsOrEmpty().isEmpty()) {
      message.append(Const.CR).append(Const.CR);
      message.append(
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.Success.WarningsHeader"));
      for (String warning : result.getWarningsOrEmpty()) {
        message.append(Const.CR).append("- ").append(warning);
      }
    }
    if (!result.getErrorsOrEmpty().isEmpty()) {
      message.append(Const.CR).append(Const.CR);
      message.append(
          BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.Success.ErrorsHeader"));
      for (String error : result.getErrorsOrEmpty()) {
        message.append(Const.CR).append("- ").append(error);
      }
    }

    int icon =
        result.getErrorsOrEmpty().isEmpty()
            ? (result.getWarningsOrEmpty().isEmpty()
                ? SWT.OK | SWT.ICON_INFORMATION
                : SWT.OK | SWT.ICON_WARNING)
            : SWT.OK | SWT.ICON_WARNING;
    MessageBox box = new MessageBox(shell, icon);
    box.setText(BaseMessages.getString(PKG, "HopGuiSourceModelImportSupport.Success.Title"));
    box.setMessage(message.toString());
    box.open();
  }
}
