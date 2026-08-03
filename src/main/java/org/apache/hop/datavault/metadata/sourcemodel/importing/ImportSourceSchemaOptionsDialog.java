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
package org.apache.hop.datavault.metadata.sourcemodel.importing;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.RecordSourceIndicatorDatabaseImportSection;
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

/** Collects connection/schema options before importing tables into a source model. */
@Getter
public class ImportSourceSchemaOptionsDialog {

  private static final Class<?> PKG = ImportSourceSchemaOptionsDialog.class;

  private static final String AUDIT_GROUP = "DataVault";
  private static final String AUDIT_TYPE = "SourceSchemaImport";
  private static final String AUDIT_STATE_NAME = "options";

  private static final String STATE_DATABASE_NAME = "databaseName";
  private static final String STATE_SCHEMA_NAME = "schemaName";
  private static final String STATE_SOURCE_NAME_PREFIX = "sourceNamePrefix";
  private static final String STATE_PUBLISH_TO_CATALOG = "publishToCatalog";
  private static final String STATE_CATALOG_CONNECTION = "catalogConnectionName";

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final String preferredDatabaseName;
  private final String preferredSchemaName;
  private final String preferredCatalogConnection;

  private Shell shell;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Text wSourceNamePrefix;
  private Button wPublishToCatalog;
  private Text wCatalogConnection;
  private RecordSourceIndicatorDatabaseImportSection recordSourceSection;
  private Button wOk;

  private SourceSchemaImportOptions options;
  private boolean cancelled = true;

  public ImportSourceSchemaOptionsDialog(
      Shell parent, IVariables variables, IHopMetadataProvider metadataProvider) {
    this(parent, variables, metadataProvider, null, null, null);
  }

  public ImportSourceSchemaOptionsDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String preferredDatabaseName,
      String preferredSchemaName,
      String preferredCatalogConnection) {
    this.parent = parent;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.preferredDatabaseName = preferredDatabaseName;
    this.preferredSchemaName = preferredSchemaName;
    this.preferredCatalogConnection = preferredCatalogConnection;
  }

  public SourceSchemaImportOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.Shell.Title"));
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

    // Reuse database import help topic until a dedicated help page exists.
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Control lastControl;

    wDatabaseName =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.DatabaseName.Label"),
            BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.DatabaseName.ToolTip"));
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
        BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.SchemaName.Label"));
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

    Label wlPrefix = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlPrefix);
    wlPrefix.setText(
        BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.SourceNamePrefix.Label"));
    FormData fdlPrefix = new FormData();
    fdlPrefix.top = new FormAttachment(lastControl, margin);
    fdlPrefix.left = new FormAttachment(0, 0);
    fdlPrefix.right = new FormAttachment(middle, -margin);
    wlPrefix.setLayoutData(fdlPrefix);

    wSourceNamePrefix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceNamePrefix);
    FormData fdPrefix = new FormData();
    fdPrefix.top = new FormAttachment(wlPrefix, 0, SWT.CENTER);
    fdPrefix.left = new FormAttachment(middle, 0);
    fdPrefix.right = new FormAttachment(100, 0);
    wSourceNamePrefix.setLayoutData(fdPrefix);
    lastControl = wSourceNamePrefix;

    wPublishToCatalog = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wPublishToCatalog);
    wPublishToCatalog.setText(
        BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.PublishToCatalog.Label"));
    wPublishToCatalog.setToolTipText(
        BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.PublishToCatalog.ToolTip"));
    FormData fdPublish = new FormData();
    fdPublish.top = new FormAttachment(lastControl, margin);
    fdPublish.left = new FormAttachment(middle, 0);
    fdPublish.right = new FormAttachment(100, 0);
    wPublishToCatalog.setLayoutData(fdPublish);
    wPublishToCatalog.setSelection(true);
    wPublishToCatalog.addListener(SWT.Selection, e -> updateCatalogFieldsEnabled());
    lastControl = wPublishToCatalog;

    Label wlCatalog = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlCatalog);
    wlCatalog.setText(
        BaseMessages.getString(PKG, "ImportSourceSchemaOptionsDialog.CatalogConnection.Label"));
    FormData fdlCatalog = new FormData();
    fdlCatalog.top = new FormAttachment(lastControl, margin);
    fdlCatalog.left = new FormAttachment(0, 0);
    fdlCatalog.right = new FormAttachment(middle, -margin);
    wlCatalog.setLayoutData(fdlCatalog);

    wCatalogConnection = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCatalogConnection);
    FormData fdCatalog = new FormData();
    fdCatalog.top = new FormAttachment(wlCatalog, 0, SWT.CENTER);
    fdCatalog.left = new FormAttachment(middle, 0);
    fdCatalog.right = new FormAttachment(100, 0);
    wCatalogConnection.setLayoutData(fdCatalog);
    lastControl = wCatalogConnection;

    recordSourceSection =
        new RecordSourceIndicatorDatabaseImportSection(shell, middle, margin, lastControl);

    restoreAuditState();
    applyPreferredDefaults();
    updateCatalogFieldsEnabled();

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return cancelled ? null : options;
  }

  private void applyPreferredDefaults() {
    if (Const.NVL(wDatabaseName.getText(), "").isEmpty()
        && !Const.NVL(preferredDatabaseName, "").isEmpty()) {
      wDatabaseName.setText(preferredDatabaseName);
    }
    if (Const.NVL(wSchemaName.getText(), "").isEmpty()
        && !Const.NVL(preferredSchemaName, "").isEmpty()) {
      wSchemaName.setText(preferredSchemaName);
    }
    if (Const.NVL(wCatalogConnection.getText(), "").isEmpty()
        && !Const.NVL(preferredCatalogConnection, "").isEmpty()) {
      wCatalogConnection.setText(preferredCatalogConnection);
    }
  }

  private void updateCatalogFieldsEnabled() {
    boolean enabled = wPublishToCatalog.getSelection();
    wCatalogConnection.setEnabled(enabled);
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
      wSourceNamePrefix.setText(
          Const.NVL(auditState.extractString(STATE_SOURCE_NAME_PREFIX, ""), ""));
      wPublishToCatalog.setSelection(
          Boolean.parseBoolean(
              Const.NVL(auditState.extractString(STATE_PUBLISH_TO_CATALOG, "true"), "true")));
      wCatalogConnection.setText(
          Const.NVL(auditState.extractString(STATE_CATALOG_CONNECTION, ""), ""));
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring source schema import dialog state", e);
    }
  }

  private void storeAuditState() {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_DATABASE_NAME, wDatabaseName.getText());
      stateMap.put(STATE_SCHEMA_NAME, wSchemaName.getText());
      stateMap.put(STATE_SOURCE_NAME_PREFIX, wSourceNamePrefix.getText());
      stateMap.put(STATE_PUBLISH_TO_CATALOG, Boolean.toString(wPublishToCatalog.getSelection()));
      stateMap.put(STATE_CATALOG_CONNECTION, wCatalogConnection.getText());
      AuditState auditState = new AuditState(AUDIT_STATE_NAME, stateMap);
      AuditManager.getActive().storeState(AUDIT_GROUP, AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing source schema import dialog state", e);
    }
  }

  private void ok() {
    storeAuditState();
    options = new SourceSchemaImportOptions();
    options.setDatabaseName(wDatabaseName.getText());
    options.setSchemaName(wSchemaName.getText());
    options.setSourceNamePrefix(wSourceNamePrefix.getText());
    options.setPublishToCatalog(wPublishToCatalog.getSelection());
    options.setCatalogConnectionName(wCatalogConnection.getText());
    RecordSourceIndicatorOptions recordSource =
        recordSourceSection != null ? recordSourceSection.collectOptions() : null;
    options.setRecordSourceOptions(recordSource);
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    storeAuditState();
    cancelled = true;
    options = null;
    shell.dispose();
  }
}
