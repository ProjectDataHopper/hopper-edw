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
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.RecordSourceIndicatorDatabaseImportSection;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Collects database/schema and naming options before bulk-importing tables. */
@Getter
@Setter
public class ImportDatabaseTablesOptionsDialog {

  private static final Class<?> PKG = ImportDatabaseTablesOptionsDialog.class;

  private static final String AUDIT_GROUP = "DataVault";
  private static final String AUDIT_TYPE = "DatabaseTablesImport";
  private static final String AUDIT_STATE_NAME = "options";

  private static final String STATE_DATABASE_NAME = "databaseName";
  private static final String STATE_SCHEMA_NAME = "schemaName";
  private static final String STATE_DATA_VAULT_SOURCE_PREFIX = "dataVaultSourcePrefix";

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private Shell shell;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Text wDataVaultSourcePrefix;
  private RecordSourceIndicatorDatabaseImportSection recordSourceSection;
  private Button wOk;

  private ImportDatabaseTablesOptions options;
  private boolean cancelled = true;

  public ImportDatabaseTablesOptionsDialog(
      Shell parent, IVariables variables, IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public ImportDatabaseTablesOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportDatabaseTablesOptionsDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    PropsUi props = PropsUi.getInstance();
    int margin = PropsUi.getMargin();
    int middle = props.getMiddlePct();

    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Control lastControl = null;

    wDatabaseName =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "ImportDatabaseTablesOptionsDialog.DatabaseName.Label"),
            BaseMessages.getString(PKG, "ImportDatabaseTablesOptionsDialog.DatabaseName.ToolTip"));
    FormData fdDatabaseName = new FormData();
    fdDatabaseName.top = new FormAttachment(0, margin);
    fdDatabaseName.left = new FormAttachment(0, 0);
    fdDatabaseName.right = new FormAttachment(100, 0);
    wDatabaseName.setLayoutData(fdDatabaseName);
    try {
      wDatabaseName.fillItems();
    } catch (HopException e) {
      // best effort
    }
    lastControl = wDatabaseName;

    Label wlSchemaName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlSchemaName);
    wlSchemaName.setText(
        BaseMessages.getString(PKG, "ImportDatabaseTablesOptionsDialog.SchemaName.Label"));
    FormData fdlSchemaName = new FormData();
    fdlSchemaName.top = new FormAttachment(lastControl, margin);
    fdlSchemaName.left = new FormAttachment(0, 0);
    fdlSchemaName.right = new FormAttachment(middle, -margin);
    wlSchemaName.setLayoutData(fdlSchemaName);

    wSchemaName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaName);
    FormData fdSchemaName = new FormData();
    fdSchemaName.top = new FormAttachment(wlSchemaName, 0, SWT.CENTER);
    fdSchemaName.left = new FormAttachment(middle, 0);
    fdSchemaName.right = new FormAttachment(100, 0);
    wSchemaName.setLayoutData(fdSchemaName);
    lastControl = wSchemaName;

    Label wlDataVaultSourcePrefix = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlDataVaultSourcePrefix);
    wlDataVaultSourcePrefix.setText(
        BaseMessages.getString(
            PKG, "ImportDatabaseTablesOptionsDialog.DataVaultSourcePrefix.Label"));
    FormData fdlDataVaultSourcePrefix = new FormData();
    fdlDataVaultSourcePrefix.top = new FormAttachment(lastControl, margin);
    fdlDataVaultSourcePrefix.left = new FormAttachment(0, 0);
    fdlDataVaultSourcePrefix.right = new FormAttachment(middle, -margin);
    wlDataVaultSourcePrefix.setLayoutData(fdlDataVaultSourcePrefix);

    wDataVaultSourcePrefix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDataVaultSourcePrefix);
    FormData fdDataVaultSourcePrefix = new FormData();
    fdDataVaultSourcePrefix.top = new FormAttachment(wlDataVaultSourcePrefix, 0, SWT.CENTER);
    fdDataVaultSourcePrefix.left = new FormAttachment(middle, 0);
    fdDataVaultSourcePrefix.right = new FormAttachment(100, 0);
    wDataVaultSourcePrefix.setLayoutData(fdDataVaultSourcePrefix);
    lastControl = wDataVaultSourcePrefix;

    recordSourceSection =
        new RecordSourceIndicatorDatabaseImportSection(shell, middle, margin, lastControl);

    restoreAuditState();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return cancelled ? null : options;
  }

  private void restoreAuditState() {
    try {
      AuditState auditState =
          AuditManager.getActive().retrieveState(AUDIT_GROUP, AUDIT_TYPE, AUDIT_STATE_NAME);
      if (auditState == null || auditState.getStateMap() == null) {
        return;
      }

      wDatabaseName.setText(Const.NVL(auditState.extractString(STATE_DATABASE_NAME, ""), ""));
      wSchemaName.setText(Const.NVL(auditState.extractString(STATE_SCHEMA_NAME, ""), ""));
      wDataVaultSourcePrefix.setText(
          Const.NVL(auditState.extractString(STATE_DATA_VAULT_SOURCE_PREFIX, ""), ""));
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring import database tables dialog state", e);
    }
  }

  private void storeAuditState() {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_DATABASE_NAME, wDatabaseName.getText());
      stateMap.put(STATE_SCHEMA_NAME, wSchemaName.getText());
      stateMap.put(STATE_DATA_VAULT_SOURCE_PREFIX, wDataVaultSourcePrefix.getText());

      AuditState auditState = new AuditState(AUDIT_STATE_NAME, stateMap);
      AuditManager.getActive().storeState(AUDIT_GROUP, AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing import database tables dialog state", e);
    }
  }

  private void ok() {
    storeAuditState();
    options = new ImportDatabaseTablesOptions();
    options.setDatabaseName(wDatabaseName.getText());
    options.setSchemaName(wSchemaName.getText());
    options.setDataVaultSourcePrefix(wDataVaultSourcePrefix.getText());
    options.setRecordSourceOptions(recordSourceSection.collectOptions());
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    storeAuditState();
    cancelled = true;
    options = null;
    shell.dispose();
  }

  @Getter
  @Setter
  public static final class ImportDatabaseTablesOptions {
    private String databaseName;
    private String schemaName;
    private String dataVaultSourcePrefix;
    private RecordSourceIndicatorOptions recordSourceOptions;

    public ImportDatabaseTablesOptions() {
      // Empty by design
    }
  }
}
