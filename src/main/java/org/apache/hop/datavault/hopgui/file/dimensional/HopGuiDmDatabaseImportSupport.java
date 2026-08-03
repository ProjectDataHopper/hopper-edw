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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.datavault.metadata.dimensional.dbimport.DmDatabaseImportOptions;
import org.apache.hop.datavault.metadata.dimensional.dbimport.DmDatabaseImportResult;
import org.apache.hop.datavault.metadata.dimensional.dbimport.DmDatabaseTableImportSupport;
import org.apache.hop.datavault.metadata.dimensional.dbimport.ImportDmDatabaseTablesOptionsDialog;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EditRowsDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/** GUI helper for importing database tables into a dimensional model. */
public final class HopGuiDmDatabaseImportSupport {

  private static final Class<?> PKG = ImportDmDatabaseTablesOptionsDialog.class;

  private HopGuiDmDatabaseImportSupport() {}

  public static void importDatabaseTables(
      HopGui hopGui, DimensionalModel model, Runnable onChanged) {
    if (hopGui == null || model == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();

    ImportDmDatabaseTablesOptionsDialog optionsDialog =
        new ImportDmDatabaseTablesOptionsDialog(shell, variables, metadataProvider);
    DmDatabaseImportOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String connectionName = Const.NVL(options.getDatabaseName(), "");
    if (Utils.isEmpty(connectionName)) {
      return;
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "ImportDmDatabaseTablesOptionsDialog.ErrorListingTables.Title"),
          BaseMessages.getString(
              PKG, "ImportDmDatabaseTablesOptionsDialog.ErrorListingTables.Message"),
          e);
      return;
    }

    List<String> selectedTables;
    try {
      selectedTables = promptForTableSelection(shell, variables, databaseMeta, options);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Error.Message"),
          e);
      return;
    }
    if (selectedTables == null || selectedTables.isEmpty()) {
      return;
    }

    try {
      DmDatabaseImportResult result =
          DmDatabaseTableImportSupport.importTables(
              model, databaseMeta, options, selectedTables, variables, metadataProvider);
      for (IDmTable table : result.getImportedTablesOrEmpty()) {
        model.getTables().add(table);
      }
      if (onChanged != null) {
        onChanged.run();
      }

      StringBuilder message =
          new StringBuilder(
              BaseMessages.getString(
                  PKG,
                  "HopGuiDmDatabaseImportSupport.Success.Message",
                  result.getImportedTablesOrEmpty().size()));

      if (!result.getWarningsOrEmpty().isEmpty()) {
        message.append(Const.CR).append(Const.CR);
        message.append(
            BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Success.WarningsHeader"));
        for (String warning : result.getWarningsOrEmpty()) {
          message.append(Const.CR).append("- ").append(warning);
        }
      }
      if (!result.getErrorsOrEmpty().isEmpty()) {
        message.append(Const.CR).append(Const.CR);
        message.append(
            BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Success.ErrorsHeader"));
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
      MessageBox success = new MessageBox(shell, icon);
      success.setText(BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Success.Title"));
      success.setMessage(message.toString());
      success.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiDmDatabaseImportSupport.Error.Message"),
          e);
    }
  }

  private static List<String> promptForTableSelection(
      Shell shell, IVariables variables, DatabaseMeta databaseMeta, DmDatabaseImportOptions options)
      throws HopException {
    String schemaName = variables.resolve(options.getSchemaName());
    String[] tableNames;
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DmDatabaseTableImport", LoggingObjectType.GENERAL, null);
    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      tableNames = database.getTablenames(schemaName, false);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "ImportDmDatabaseTablesOptionsDialog.ErrorListingTables.Title"),
          BaseMessages.getString(
              PKG, "ImportDmDatabaseTablesOptionsDialog.ErrorListingTables.Message"),
          e);
      return null;
    }

    if (tableNames == null || tableNames.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(
          BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.NoTablesFound.Title"));
      mb.setMessage(
          BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.NoTablesFound.Message"));
      mb.open();
      return null;
    }

    IRowMeta selectionRowMeta = new RowMeta();
    selectionRowMeta.addValueMeta(
        ValueMetaFactory.createValueMeta(
            BaseMessages.getString(
                PKG, "ImportDmDatabaseTablesOptionsDialog.Selection.Column.TableName"),
            IValueMeta.TYPE_STRING));

    List<Object[]> selectionRows = new ArrayList<>();
    for (String tableName : tableNames) {
      selectionRows.add(new Object[] {tableName});
    }

    EditRowsDialog tableSelectionDialog =
        new EditRowsDialog(
            shell,
            SWT.NONE,
            BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Selection.Title"),
            BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Selection.Message"),
            selectionRowMeta,
            selectionRows);
    List<Object[]> selectedRows = tableSelectionDialog.open();
    if (selectedRows == null) {
      return null;
    }

    List<String> selectedTables = new ArrayList<>();
    for (Object[] row : selectedRows) {
      if (row != null && row.length > 0 && row[0] != null) {
        selectedTables.add(row[0].toString());
      }
    }
    return selectedTables;
  }
}
