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
package org.hopper.edw.datavault.metadata.iceberg;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Collects Iceberg connection settings before importing a table into the data catalog. */
@Getter
@Setter
public class ImportIcebergTableDialog {

  private static final Class<?> PKG = ImportIcebergTableDialog.class;

  private final Shell parent;

  private Shell shell;
  private TextVar wCatalogUri;
  private TextVar wWarehouse;
  private TextVar wNamespace;
  private TextVar wTableName;
  private TextVar wSnapshotId;
  private TextVar wBranch;
  private TextVar wS3Endpoint;
  private TextVar wS3AccessKey;
  private TextVar wS3SecretKey;
  private Button wOk;

  private ImportIcebergTableSettings settings;
  private boolean cancelled = true;

  public ImportIcebergTableDialog(Shell parent) {
    this.parent = parent;
  }

  public ImportIcebergTableSettings open() {
    IVariables variables = HopGui.getInstance().getVariables();
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportIcebergTableDialog.Shell.Title"));
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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_ICEBERG_TABLE);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Control lastControl = null;
    lastControl =
        wCatalogUri =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.CatalogUri.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wWarehouse =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.Warehouse.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wNamespace =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.Namespace.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wTableName =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.TableName.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wSnapshotId =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.SnapshotId.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wBranch =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.Branch.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wS3Endpoint =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.S3Endpoint.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wS3AccessKey =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.S3AccessKey.Label",
                lastControl,
                middle,
                margin);
    lastControl =
        wS3SecretKey =
            addTextVarField(
                variables,
                shell,
                "ImportIcebergTableDialog.S3SecretKey.Label",
                lastControl,
                middle,
                margin);

    wCatalogUri.setText("${ICEBERG_CATALOG_URI}");
    wWarehouse.setText("${ICEBERG_WAREHOUSE}");
    wNamespace.setText("${ICEBERG_NAMESPACE}");
    wTableName.setText("${ICEBERG_TABLE}");
    wS3Endpoint.setText("${S3_ENDPOINT}");
    wS3AccessKey.setText("${S3_ACCESS_KEY}");
    wS3SecretKey.setText("${S3_SECRET_KEY}");

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return cancelled ? null : settings;
  }

  private TextVar addTextVarField(
      IVariables variables,
      Shell parentShell,
      String labelKey,
      Control previous,
      int middle,
      int margin) {
    Label label = new Label(parentShell, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, labelKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    TextVar textVar = new TextVar(variables, parentShell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(textVar);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    textVar.setLayoutData(fd);
    return textVar;
  }

  private void ok() {
    String catalogUri = Const.NVL(wCatalogUri.getText(), "").trim();
    String namespace = Const.NVL(wNamespace.getText(), "").trim();
    String tableName = Const.NVL(wTableName.getText(), "").trim();
    if (Utils.isEmpty(catalogUri) || Utils.isEmpty(namespace) || Utils.isEmpty(tableName)) {
      return;
    }

    settings = new ImportIcebergTableSettings();
    settings.setCatalogUri(catalogUri);
    settings.setWarehouse(wWarehouse.getText());
    settings.setNamespace(namespace);
    settings.setTableName(tableName);
    settings.setSnapshotId(wSnapshotId.getText());
    settings.setBranch(wBranch.getText());
    settings.setS3Endpoint(wS3Endpoint.getText());
    settings.setS3AccessKey(wS3AccessKey.getText());
    settings.setS3SecretKey(wS3SecretKey.getText());
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    cancelled = true;
    settings = null;
    shell.dispose();
  }

  @Getter
  @Setter
  public static final class ImportIcebergTableSettings {
    private String catalogUri;
    private String warehouse;
    private String namespace;
    private String tableName;
    private String snapshotId;
    private String branch;
    private String s3Endpoint;
    private String s3AccessKey;
    private String s3SecretKey;

    public IcebergConnectionSettings toConnectionSettings(
        org.apache.hop.core.variables.IVariables variables) {
      return new IcebergConnectionSettings(
          resolve(variables, catalogUri),
          resolve(variables, warehouse),
          resolve(variables, namespace),
          resolve(variables, tableName),
          resolve(variables, snapshotId),
          resolve(variables, branch),
          resolve(variables, s3Endpoint),
          resolve(variables, s3AccessKey),
          resolve(variables, s3SecretKey));
    }

    private static String resolve(
        org.apache.hop.core.variables.IVariables variables, String value) {
      if (variables == null || value == null) {
        return value;
      }
      return variables.resolve(value);
    }
  }
}
