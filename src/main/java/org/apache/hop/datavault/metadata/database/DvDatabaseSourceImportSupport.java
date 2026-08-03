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
package org.apache.hop.datavault.metadata.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
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
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldPrimaryKeySupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EditRowsDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/** Shared helpers for importing database tables as record definitions in the data catalog. */
public final class DvDatabaseSourceImportSupport {

  private static final Class<?> PKG = DvDatabaseSourceImportSupport.class;

  /** Above this count, the table pick dialog starts with no tables pre-selected. */
  static final int LARGE_SCHEMA_TABLE_THRESHOLD = 25;

  private DvDatabaseSourceImportSupport() {}

  /** Bulk-import database tables as catalog-backed record definitions. */
  public static void importDatabaseTables(
      Shell shell, HopGui hopGui, IVariables variables, IHopMetadataProvider metadataProvider) {
    importDatabaseTables(shell, hopGui, variables, metadataProvider, null, null);
  }

  public static void importDatabaseTables(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model) {
    importDatabaseTables(shell, hopGui, variables, metadataProvider, model, null);
  }

  public static void importDatabaseTables(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      String preferredCatalogConnectionName) {
    ImportDatabaseTablesOptionsDialog optionsDialog =
        new ImportDatabaseTablesOptionsDialog(shell, variables, metadataProvider);
    ImportDatabaseTablesOptionsDialog.ImportDatabaseTablesOptions options = optionsDialog.open();
    if (options == null) {
      return;
    }

    String connectionName = Const.NVL(options.getDatabaseName(), "");
    if (Utils.isEmpty(connectionName)) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoConnection.DialogMessage"));
      return;
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName),
          e);
      return;
    }

    if (databaseMeta == null) {
      showError(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogTitle"),
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ErrorLoadingConnection.DialogMessage", connectionName));
      return;
    }

    String schemaName = Const.NVL(options.getSchemaName(), "");
    String[] tableNames;
    ILoggingObject loggingObject =
        new SimpleLoggingObject("DvDatabaseSourceImport", LoggingObjectType.GENERAL, null);
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();
      tableNames = db.getTablenames(schemaName, false);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorListingTables.DialogMessage"),
          e);
      return;
    }

    if (tableNames == null || tableNames.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage(
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.NoTablesFound.DialogTitle"));
      mb.open();
      return;
    }

    String[] sortedTableNames = sortedStrippedTableNames(tableNames);
    Set<String> pickedTables = promptForTablesToImport(shell, sortedTableNames);
    if (pickedTables == null || pickedTables.isEmpty()) {
      return;
    }

    IRowMeta selectionRowMeta = new RowMeta();
    try {
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Column.TableName"),
              IValueMeta.TYPE_STRING));
      selectionRowMeta.addValueMeta(
          ValueMetaFactory.createValueMeta(
              BaseMessages.getString(
                  PKG, "DvDatabaseSourceEditor.ImportTables.Column.DataVaultSourceName"),
              IValueMeta.TYPE_STRING));
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    List<Object[]> selectionRows = new ArrayList<>();
    String prefix = Const.NVL(options.getDataVaultSourcePrefix(), "");
    Set<String> usedSourceNames = new HashSet<>();
    for (String tableName : pickedTables) {
      String dataVaultSourceName =
          uniqueNameInBatch(
              buildDefaultMetadataName(prefix, connectionName, schemaName, tableName),
              usedSourceNames);
      selectionRows.add(new Object[] {tableName, dataVaultSourceName});
    }

    EditRowsDialog tableSelectionDialog =
        new EditRowsDialog(
            shell,
            SWT.NONE,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Title"),
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Selection.Message"),
            selectionRowMeta,
            selectionRows);
    List<Object[]> selectedRows = tableSelectionDialog.open();
    if (selectedRows == null) {
      return;
    }

    String catalogConnectionName =
        new ImportDatabaseTablesCatalogDialog(
                shell,
                variables,
                metadataProvider,
                resolveDefaultCatalogConnectionName(
                    model, variables, preferredCatalogConnectionName))
            .open();
    if (Utils.isEmpty(catalogConnectionName)) {
      return;
    }

    int importedCount = 0;
    List<String> errors = new ArrayList<>();
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();

      for (Object[] row : selectedRows) {
        if (row == null || row.length < 2) {
          continue;
        }
        String tableName = stripTableNameQuotes(row[0] != null ? row[0].toString() : null);
        String dataVaultSourceName = row[1] != null ? row[1].toString() : null;
        if (Utils.isEmpty(tableName) || Utils.isEmpty(dataVaultSourceName)) {
          continue;
        }

        try {
          if (DvSourceCatalogService.exists(
              dataVaultSourceName, catalogConnectionName, variables, metadataProvider)) {
            errors.add(
                BaseMessages.getString(
                    PKG,
                    "DvDatabaseSourceEditor.ImportTables.Exists.Message",
                    dataVaultSourceName,
                    tableName));
            continue;
          }

          List<SourceField> fields = importFieldsFromTable(db, variables, schemaName, tableName);
          RecordSourceIndicatorOptions tableRecordSource =
              RecordSourceIndicatorSupport.resolveForTable(
                  options.getRecordSourceOptions(), fields, dataVaultSourceName);
          DataVaultSource imported =
              createDataVaultSource(
                  dataVaultSourceName,
                  connectionName,
                  schemaName,
                  tableName,
                  fields,
                  tableRecordSource);
          RecordDefinitionCatalogWriter.upsertDataVaultSource(
              imported,
              catalogConnectionName,
              model,
              variables,
              metadataProvider,
              null,
              null,
              null);
          importedCount++;
        } catch (Exception e) {
          errors.add(
              BaseMessages.getString(
                  PKG,
                  "DvDatabaseSourceEditor.ImportTables.TableError.Message",
                  tableName,
                  e.getMessage()));
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogTitle"),
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ErrorImportingTables.DialogMessage"),
          e);
      return;
    }

    refreshCatalogPerspective();

    if (!errors.isEmpty()) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.PartialFailure.Title"),
          BaseMessages.getString(
              PKG,
              "DvDatabaseSourceEditor.ImportTables.PartialFailure.Message",
              importedCount,
              String.join(Const.CR, errors)),
          null);
      return;
    }

    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
    mb.setMessage(
        BaseMessages.getString(
            PKG, "DvDatabaseSourceEditor.ImportTables.Success.Message", importedCount));
    mb.setText(BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Success.Title"));
    mb.open();
  }

  public static boolean shouldPreselectAllTables(int tableCount) {
    return tableCount > 0 && tableCount <= LARGE_SCHEMA_TABLE_THRESHOLD;
  }

  public static List<Integer> defaultPreselectedTableIndexes(int tableCount) {
    if (!shouldPreselectAllTables(tableCount)) {
      return List.of();
    }
    List<Integer> indexes = new ArrayList<>(tableCount);
    for (int i = 0; i < tableCount; i++) {
      indexes.add(i);
    }
    return indexes;
  }

  public static String[] sortedStrippedTableNames(String[] tableNames) {
    if (tableNames == null || tableNames.length == 0) {
      return new String[0];
    }
    String[] sorted = new String[tableNames.length];
    for (int i = 0; i < tableNames.length; i++) {
      sorted[i] = stripTableNameQuotes(tableNames[i]);
    }
    Arrays.sort(sorted, String.CASE_INSENSITIVE_ORDER);
    return sorted;
  }

  public static Set<String> tableNamesForSelectionIndexes(
      String[] choices, int[] selectionIndexes) {
    Set<String> pickedTables = new LinkedHashSet<>();
    if (choices == null || selectionIndexes == null) {
      return pickedTables;
    }
    for (int index : selectionIndexes) {
      if (index >= 0 && index < choices.length) {
        pickedTables.add(choices[index]);
      }
    }
    return pickedTables;
  }

  private static Set<String> promptForTablesToImport(Shell shell, String[] sortedTableNames) {
    EnterSelectionDialog pickDialog =
        new EnterSelectionDialog(
            shell,
            sortedTableNames,
            BaseMessages.getString(PKG, "DvDatabaseSourceEditor.ImportTables.Pick.Title"),
            BaseMessages.getString(
                PKG, "DvDatabaseSourceEditor.ImportTables.Pick.Message", sortedTableNames.length));
    pickDialog.setMulti(true);
    List<Integer> preselected = defaultPreselectedTableIndexes(sortedTableNames.length);
    if (!preselected.isEmpty()) {
      pickDialog.setSelectedNrs(preselected);
    }
    if (pickDialog.open() == null) {
      return null;
    }

    int[] selectionIndexes = pickDialog.getSelectionIndeces();
    Set<String> pickedTables = tableNamesForSelectionIndexes(sortedTableNames, selectionIndexes);
    if (pickedTables.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ImportTables.Pick.NoneSelected.Title"));
      mb.setMessage(
          BaseMessages.getString(
              PKG, "DvDatabaseSourceEditor.ImportTables.Pick.NoneSelected.Message"));
      mb.open();
      return Set.of();
    }
    return pickedTables;
  }

  private static void showError(Shell shell, String title, String message) {
    MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    mb.setMessage(message);
    mb.setText(title);
    mb.open();
  }

  /**
   * Removes wrapping single, double, or back-quote characters from a table name returned by JDBC
   * metadata (e.g. {@code "order"} → {@code order}).
   */
  public static String stripTableNameQuotes(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return tableName;
    }
    String trimmed = tableName.trim();
    if (trimmed.length() < 2) {
      return trimmed;
    }
    char first = trimmed.charAt(0);
    char last = trimmed.charAt(trimmed.length() - 1);
    if ((first == '"' && last == '"')
        || (first == '\'' && last == '\'')
        || (first == '`' && last == '`')) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  public static String buildDefaultMetadataName(
      String prefix, String connectionName, String tableName) {
    return buildDefaultMetadataName(prefix, connectionName, null, tableName);
  }

  public static String buildDefaultMetadataName(
      String prefix, String connectionName, String schemaName, String tableName) {
    String effectivePrefix = Const.NVL(prefix, "");
    if (Utils.isEmpty(effectivePrefix)) {
      return buildSuggestedSourceName(connectionName, schemaName, tableName);
    }
    return effectivePrefix + tableName;
  }

  /** Suggested Data Vault Source name: {@code connection[-schema]-table}. */
  public static String buildSuggestedSourceName(
      String connectionName, String schemaName, String tableName) {
    String connection = Const.NVL(connectionName, "");
    String schema = Const.NVL(schemaName, "").trim();
    String table = stripTableNameQuotes(Const.NVL(tableName, ""));
    if (Utils.isEmpty(schema)) {
      return connection + "-" + table;
    }
    return connection + "-" + schema + "-" + table;
  }

  private static String resolveDefaultCatalogConnectionName(
      DataVaultModel model, IVariables variables, String preferredCatalogConnectionName) {
    if (model != null && model.getConfigurationOrDefault() != null) {
      String configured = model.getConfigurationOrDefault().getDataCatalogConnection();
      if (variables != null) {
        configured = variables.resolve(configured);
      }
      if (!Utils.isEmpty(configured)) {
        return configured;
      }
    }
    if (variables != null && !Utils.isEmpty(preferredCatalogConnectionName)) {
      preferredCatalogConnectionName = variables.resolve(preferredCatalogConnectionName);
    }
    return Utils.isEmpty(preferredCatalogConnectionName) ? null : preferredCatalogConnectionName;
  }

  private static String uniqueNameInBatch(String baseName, Set<String> usedNames) {
    String candidate = Const.NVL(baseName, "");
    if (Utils.isEmpty(candidate)) {
      return candidate;
    }
    if (!usedNames.contains(candidate)) {
      usedNames.add(candidate);
      return candidate;
    }
    int suffix = 2;
    while (usedNames.contains(candidate + "_" + suffix)) {
      suffix++;
    }
    candidate = candidate + "_" + suffix;
    usedNames.add(candidate);
    return candidate;
  }

  public static String uniqueCatalogSourceName(
      String baseName,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String candidate = Const.NVL(baseName, "");
    if (Utils.isEmpty(candidate)) {
      throw new HopException("Record definition name cannot be empty");
    }
    if (!DvSourceCatalogService.exists(
        candidate, catalogConnectionName, variables, metadataProvider)) {
      return candidate;
    }
    int suffix = 2;
    while (DvSourceCatalogService.exists(
        candidate + "_" + suffix, catalogConnectionName, variables, metadataProvider)) {
      suffix++;
    }
    return candidate + "_" + suffix;
  }

  public static List<SourceField> importFieldsFromTable(
      Database db, IVariables variables, String schemaName, String tableName)
      throws HopDatabaseException, HopException {
    String resolvedSchema = variables != null ? variables.resolve(schemaName) : schemaName;
    String resolvedTable = variables != null ? variables.resolve(tableName) : tableName;

    IRowMeta rowMeta = db.getTableFieldsMeta(resolvedSchema, resolvedTable);
    if (rowMeta == null || rowMeta.isEmpty()) {
      return List.of();
    }

    List<SourceField> fields = new ArrayList<>();
    for (IValueMeta vm : rowMeta.getValueMetaList()) {
      SourceField sf = new SourceField(vm.getName());
      sf.setDescription("");
      String nativeType = vm.getOriginalColumnTypeName();
      sf.setSourceDataType(
          !org.apache.hop.core.util.Utils.isEmpty(nativeType) ? nativeType : vm.getTypeDesc());
      sf.setLength(vm.getLength() > 0 ? String.valueOf(vm.getLength()) : "");
      sf.setPrecision(vm.getPrecision() >= 0 ? String.valueOf(vm.getPrecision()) : "");
      sf.setHopType(vm.getType());
      fields.add(sf);
    }

    List<String> primaryKeyColumnNames =
        DatabasePrimaryKeyDiscoverySupport.discoverPrimaryKeyColumnNames(
            db, db.getDatabaseMeta(), resolvedSchema, resolvedTable);
    SourceFieldPrimaryKeySupport.applyPrimaryKeyFieldNames(fields, primaryKeyColumnNames);
    return fields;
  }

  public static DvDatabaseSource createDatabaseSource(
      String connectionName, String schemaName, String tableName, List<SourceField> fields) {
    DvDatabaseSource source = new DvDatabaseSource();
    source.setDatabaseName(connectionName);
    source.setSchemaName(schemaName);
    source.setTableName(tableName);
    source.setFields(fields);
    return source;
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName,
      String connectionName,
      String schemaName,
      String tableName,
      List<SourceField> fields) {
    return createDataVaultSource(metadataName, connectionName, schemaName, tableName, fields, null);
  }

  public static DataVaultSource createDataVaultSource(
      String metadataName,
      String connectionName,
      String schemaName,
      String tableName,
      List<SourceField> fields,
      RecordSourceIndicatorOptions recordSourceOptions) {
    DataVaultSource source = new DataVaultSource(metadataName);
    source.setSource(createDatabaseSource(connectionName, schemaName, tableName, fields));
    if (recordSourceOptions != null) {
      RecordSourceIndicatorSupport.applyRecordSource(source, recordSourceOptions);
    }
    return source;
  }

  public static void refreshCatalogPerspective() {
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective != null) {
      perspective.refresh();
    }
  }
}
