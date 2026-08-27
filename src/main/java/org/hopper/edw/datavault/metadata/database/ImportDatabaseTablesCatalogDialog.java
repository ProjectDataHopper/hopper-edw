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
package org.hopper.edw.datavault.metadata.database;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Asks which data catalog connection should receive imported record definitions. */
@Getter
@Setter
public class ImportDatabaseTablesCatalogDialog {

  private static final Class<?> PKG = ImportDatabaseTablesCatalogDialog.class;

  private static final String AUDIT_GROUP = "DataVault";
  private static final String AUDIT_TYPE = "DatabaseTablesImport";
  private static final String AUDIT_STATE_NAME = "catalogOptions";
  private static final String STATE_CATALOG_CONNECTION = "catalogConnectionName";

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final String defaultCatalogConnectionName;

  private Shell shell;
  private MetaSelectionLine<DataCatalogMeta> wCatalogConnection;
  private Button wOk;

  private String catalogConnectionName;
  private boolean cancelled = true;

  public ImportDatabaseTablesCatalogDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String defaultCatalogConnectionName) {
    this.parent = parent;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.defaultCatalogConnectionName = defaultCatalogConnectionName;
  }

  public String open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportDatabaseTablesCatalogDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_CATALOG);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wCatalogConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DataCatalogMeta.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.CatalogConnection.Label"),
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.CatalogConnection.ToolTip"));
    FormData fdCatalogConnection = new FormData();
    fdCatalogConnection.top = new FormAttachment(0, margin);
    fdCatalogConnection.left = new FormAttachment(0, 0);
    fdCatalogConnection.right = new FormAttachment(100, 0);
    wCatalogConnection.setLayoutData(fdCatalogConnection);
    try {
      wCatalogConnection.fillItems();
    } catch (HopException e) {
      // best effort
    }

    String initialCatalog = resolveInitialCatalogConnectionName();
    if (!Utils.isEmpty(initialCatalog)) {
      wCatalogConnection.setText(initialCatalog);
    }

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return cancelled ? null : catalogConnectionName;
  }

  private String resolveInitialCatalogConnectionName() {
    if (!Utils.isEmpty(defaultCatalogConnectionName)) {
      return defaultCatalogConnectionName;
    }
    try {
      AuditState auditState =
          AuditManager.getActive().retrieveState(AUDIT_GROUP, AUDIT_TYPE, AUDIT_STATE_NAME);
      if (auditState != null && auditState.getStateMap() != null) {
        return Const.NVL(auditState.extractString(STATE_CATALOG_CONNECTION, ""), "");
      }
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring import catalog dialog state", e);
    }
    return "";
  }

  private void storeAuditState() {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_CATALOG_CONNECTION, wCatalogConnection.getText());

      AuditState auditState = new AuditState(AUDIT_STATE_NAME, stateMap);
      AuditManager.getActive().storeState(AUDIT_GROUP, AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing import catalog dialog state", e);
    }
  }

  private void ok() {
    String selected = Const.NVL(wCatalogConnection.getText(), "").trim();
    if (Utils.isEmpty(selected)) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ImportDatabaseTablesCatalogDialog.NoCatalog.DialogTitle"),
          BaseMessages.getString(PKG, "ImportDatabaseTablesCatalogDialog.NoCatalog.DialogMessage"),
          null);
      return;
    }

    try {
      IHopMetadataSerializer<DataCatalogMeta> serializer =
          metadataProvider.getSerializer(DataCatalogMeta.class);
      DataCatalogMeta catalogMeta = serializer.load(selected);
      if (catalogMeta == null) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.InvalidCatalog.DialogTitle"),
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.InvalidCatalog.DialogMessage", selected),
            null);
        return;
      }
      if (!catalogMeta.isEnabled()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.DisabledCatalog.DialogTitle"),
            BaseMessages.getString(
                PKG, "ImportDatabaseTablesCatalogDialog.DisabledCatalog.DialogMessage", selected),
            null);
        return;
      }
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "ImportDatabaseTablesCatalogDialog.InvalidCatalog.DialogTitle"),
          BaseMessages.getString(
              PKG, "ImportDatabaseTablesCatalogDialog.InvalidCatalog.DialogMessage", selected),
          e);
      return;
    }

    storeAuditState();
    catalogConnectionName = selected;
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    cancelled = true;
    catalogConnectionName = null;
    shell.dispose();
  }
}
