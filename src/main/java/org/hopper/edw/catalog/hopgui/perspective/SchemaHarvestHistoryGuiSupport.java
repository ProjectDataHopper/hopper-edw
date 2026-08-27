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

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestRunSummary;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HarvestSubjectSummary;
import org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryReader.HistoryConnection;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.quality.CatalogQualitySubjectSupport;

/** Opens schema harvest history browsers from group editor or catalog detail panel. */
public final class SchemaHarvestHistoryGuiSupport {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;

  private SchemaHarvestHistoryGuiSupport() {}

  public static void openForGroup(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    try {
      String catalogConnection = Const.NVL(group.getDataCatalogConnection(), "");
      if (!Utils.isEmpty(catalogConnection)) {
        catalogConnection = variables.resolve(catalogConnection);
      }
      ResolvedHistory history =
          resolveHistory(shell, catalogConnection, variables, metadataProvider);
      if (history == null) {
        return;
      }
      List<HarvestRunSummary> runs =
          SchemaHarvestHistoryReader.listRuns(
              history.databaseMeta(),
              history.schema(),
              group.getName(),
              variables,
              SchemaHarvestHistoryReader.DEFAULT_HISTORY_LIMIT);
      if (runs.isEmpty()) {
        info(
            shell,
            BaseMessages.getString(PKG, "SchemaHarvestHistory.Title"),
            SchemaHarvestHistoryReader.MSG_NO_HISTORY);
        return;
      }
      new SchemaHarvestHistoryBrowserDialog(
              shell,
              variables,
              history.databaseMeta(),
              history.schema(),
              group.getName(),
              null,
              runs,
              metadataProvider,
              catalogConnection)
          .open();
    } catch (Exception e) {
      if (e.getMessage() != null
          && e.getMessage().contains(SchemaHarvestHistoryReader.MSG_TABLES_MISSING)) {
        info(
            shell,
            BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
            SchemaHarvestHistoryReader.MSG_TABLES_MISSING);
        return;
      }
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
          e);
    }
  }

  public static void openForSubject(
      Shell shell,
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (shell == null || definition == null) {
      return;
    }
    try {
      ResolvedHistory history =
          resolveHistory(shell, catalogConnectionName, variables, metadataProvider);
      if (history == null) {
        return;
      }
      String subjectKey = CatalogQualitySubjectSupport.subjectKey(definition);
      List<HarvestSubjectSummary> entries =
          SchemaHarvestHistoryReader.listSubjectHistory(
              history.databaseMeta(),
              history.schema(),
              subjectKey,
              variables,
              SchemaHarvestHistoryReader.DEFAULT_HISTORY_LIMIT);
      if (entries.isEmpty()) {
        info(
            shell,
            BaseMessages.getString(PKG, "SchemaHarvestHistory.Title"),
            SchemaHarvestHistoryReader.MSG_NO_HISTORY);
        return;
      }
      new SchemaHarvestSubjectHistoryDialog(
              shell,
              variables,
              history.databaseMeta(),
              history.schema(),
              subjectKey,
              entries,
              metadataProvider,
              catalogConnectionName)
          .open();
    } catch (Exception e) {
      if (e.getMessage() != null
          && e.getMessage().contains(SchemaHarvestHistoryReader.MSG_TABLES_MISSING)) {
        info(
            shell,
            BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
            SchemaHarvestHistoryReader.MSG_TABLES_MISSING);
        return;
      }
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Message"),
          e);
    }
  }

  private record ResolvedHistory(DatabaseMeta databaseMeta, String schema) {}

  private static ResolvedHistory resolveHistory(
      Shell shell,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    HistoryConnection connection =
        SchemaHarvestHistoryReader.resolveConnection(
            null, null, catalogConnectionName, variables, metadataProvider);
    if (connection == null) {
      info(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          SchemaHarvestHistoryReader.MSG_NOT_CONFIGURED);
      return null;
    }
    DatabaseMeta databaseMeta =
        SchemaHarvestHistoryReader.loadDatabaseMeta(
            connection.databaseMetaName(), metadataProvider);
    if (databaseMeta == null) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SchemaHarvestHistory.Error.Title"),
          BaseMessages.getString(
              PKG, "SchemaHarvestHistory.Error.ConnectionMissing", connection.databaseMetaName()),
          new HopException(
              "Harvest history database connection not found: " + connection.databaseMetaName()));
      return null;
    }
    return new ResolvedHistory(databaseMeta, connection.schemaName());
  }

  private static void info(Shell shell, String title, String message) {
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
    box.setText(title);
    box.setMessage(message);
    box.open();
  }
}
