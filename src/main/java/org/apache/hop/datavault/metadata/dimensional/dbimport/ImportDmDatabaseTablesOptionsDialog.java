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
package org.apache.hop.datavault.metadata.dimensional.dbimport;

import lombok.Getter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Collects connection and default SCD options before importing database tables. */
@Getter
public class ImportDmDatabaseTablesOptionsDialog {

  private static final Class<?> PKG = ImportDmDatabaseTablesOptionsDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private Shell shell;
  private MetaSelectionLine<DatabaseMeta> wDatabaseName;
  private Text wSchemaName;
  private Combo wDefaultScdType;
  private Button wOk;

  private DmDatabaseImportOptions options;
  private boolean cancelled = true;

  public ImportDmDatabaseTablesOptionsDialog(
      Shell parent, IVariables variables, IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public DmDatabaseImportOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DM_DATABASE_TABLES_OPTIONS);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wDatabaseName =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            DatabaseMeta.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Database.Label"),
            BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Database.Tooltip"));
    FormData fdDatabase = new FormData();
    fdDatabase.left = new FormAttachment(0, margin);
    fdDatabase.top = new FormAttachment(0, margin);
    fdDatabase.right = new FormAttachment(100, -margin);
    wDatabaseName.setLayoutData(fdDatabase);
    try {
      wDatabaseName.fillItems();
    } catch (HopException e) {
      // best effort
    }

    Label wlSchema = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlSchema);
    wlSchema.setText(
        BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.Schema.Label"));
    FormData fdlSchema = new FormData();
    fdlSchema.left = new FormAttachment(0, margin);
    fdlSchema.right = new FormAttachment(middle, -margin);
    fdlSchema.top = new FormAttachment(wDatabaseName, margin);
    wlSchema.setLayoutData(fdlSchema);

    wSchemaName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaName);
    FormData fdSchema = new FormData();
    fdSchema.left = new FormAttachment(middle, margin);
    fdSchema.top = new FormAttachment(wDatabaseName, margin);
    fdSchema.right = new FormAttachment(100, -margin);
    wSchemaName.setLayoutData(fdSchema);

    Label wlScdType = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlScdType);
    wlScdType.setText(
        BaseMessages.getString(PKG, "ImportDmDatabaseTablesOptionsDialog.DefaultScdType.Label"));
    FormData fdlScdType = new FormData();
    fdlScdType.left = new FormAttachment(0, margin);
    fdlScdType.right = new FormAttachment(middle, -margin);
    fdlScdType.top = new FormAttachment(wSchemaName, margin);
    wlScdType.setLayoutData(fdlScdType);

    wDefaultScdType = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wDefaultScdType);
    EnumDialogSupport.populateCombo(wDefaultScdType, DmDimensionScdType.class);
    wDefaultScdType.select(0);
    FormData fdScdType = new FormData();
    fdScdType.left = new FormAttachment(middle, margin);
    fdScdType.top = new FormAttachment(wSchemaName, margin);
    fdScdType.right = new FormAttachment(100, -margin);
    wDefaultScdType.setLayoutData(fdScdType);

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return cancelled ? null : options;
  }

  private void ok() {
    cancelled = false;
    options = DmDatabaseImportOptions.defaults();
    options.setDatabaseName(wDatabaseName.getText());
    options.setSchemaName(wSchemaName.getText());
    options.setDefaultDimensionScdType(
        EnumDialogSupport.readCombo(
            wDefaultScdType, DmDimensionScdType.class, DmDimensionScdType.TYPE1));
    dispose();
  }

  private void cancel() {
    cancelled = true;
    dispose();
  }

  private void dispose() {
    shell.dispose();
  }
}
