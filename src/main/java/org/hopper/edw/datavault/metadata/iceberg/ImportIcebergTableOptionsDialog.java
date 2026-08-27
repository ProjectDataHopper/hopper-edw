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

import java.util.List;
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
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.RecordSourceIndicatorImportSection;

/** Collects import options before importing an Iceberg table as a catalog record definition. */
@Getter
@Setter
public class ImportIcebergTableOptionsDialog {

  private static final Class<?> PKG = ImportIcebergTableOptionsDialog.class;

  private final Shell parent;
  private final String tableIdentifier;
  private final String defaultSourceName;
  private final List<String> discoveredFieldNames;

  private Shell shell;
  private TextVar wTableIdentifier;
  private TextVar wSourceName;
  private RecordSourceIndicatorImportSection recordSourceSection;
  private Button wOk;

  private ImportIcebergTableOptions options;
  private boolean cancelled = true;

  public ImportIcebergTableOptionsDialog(
      Shell parent,
      String tableIdentifier,
      String defaultSourceName,
      List<String> discoveredFieldNames) {
    this.parent = parent;
    this.tableIdentifier = tableIdentifier;
    this.defaultSourceName = defaultSourceName;
    this.discoveredFieldNames = discoveredFieldNames;
  }

  public ImportIcebergTableOptions open() {
    IVariables variables = HopGui.getInstance().getVariables();
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportIcebergTableOptionsDialog.Shell.Title"));
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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_ICEBERG_TABLE_OPTIONS);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Control lastControl = null;

    Label wlTableIdentifier = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlTableIdentifier);
    wlTableIdentifier.setText(
        BaseMessages.getString(PKG, "ImportIcebergTableOptionsDialog.TableIdentifier.Label"));
    FormData fdlTableIdentifier = new FormData();
    fdlTableIdentifier.top = new FormAttachment(0, margin);
    fdlTableIdentifier.left = new FormAttachment(0, 0);
    fdlTableIdentifier.right = new FormAttachment(middle, -margin);
    wlTableIdentifier.setLayoutData(fdlTableIdentifier);

    wTableIdentifier = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableIdentifier);
    wTableIdentifier.setEditable(false);
    wTableIdentifier.setText(Const.NVL(tableIdentifier, ""));
    FormData fdTableIdentifier = new FormData();
    fdTableIdentifier.top = new FormAttachment(wlTableIdentifier, 0, SWT.CENTER);
    fdTableIdentifier.left = new FormAttachment(middle, 0);
    fdTableIdentifier.right = new FormAttachment(100, 0);
    wTableIdentifier.setLayoutData(fdTableIdentifier);
    lastControl = wTableIdentifier;

    Label wlSourceName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlSourceName);
    wlSourceName.setText(
        BaseMessages.getString(PKG, "ImportIcebergTableOptionsDialog.SourceName.Label"));
    FormData fdlSourceName = new FormData();
    fdlSourceName.top = new FormAttachment(lastControl, margin);
    fdlSourceName.left = new FormAttachment(0, 0);
    fdlSourceName.right = new FormAttachment(middle, -margin);
    wlSourceName.setLayoutData(fdlSourceName);

    wSourceName = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceName);
    wSourceName.setText(Const.NVL(defaultSourceName, ""));
    FormData fdSourceName = new FormData();
    fdSourceName.top = new FormAttachment(wlSourceName, 0, SWT.CENTER);
    fdSourceName.left = new FormAttachment(middle, 0);
    fdSourceName.right = new FormAttachment(100, 0);
    wSourceName.setLayoutData(fdSourceName);
    lastControl = wSourceName;

    recordSourceSection =
        new RecordSourceIndicatorImportSection(
            shell, middle, margin, discoveredFieldNames, defaultSourceName, lastControl);

    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return cancelled ? null : options;
  }

  private void ok() {
    IVariables variables = HopGui.getInstance().getVariables();
    String sourceName = resolve(variables, Const.NVL(wSourceName.getText(), "")).trim();
    if (Utils.isEmpty(sourceName)) {
      return;
    }

    RecordSourceIndicatorOptions recordSourceOptions =
        recordSourceSection.collectOptions(sourceName);
    if (recordSourceOptions == null) {
      return;
    }

    options = new ImportIcebergTableOptions();
    options.setTableIdentifier(resolve(variables, wTableIdentifier.getText()));
    options.setSourceName(sourceName);
    options.setRecordSourceOptions(recordSourceOptions);
    cancelled = false;
    shell.dispose();
  }

  private static String resolve(IVariables variables, String value) {
    if (variables == null || value == null) {
      return value;
    }
    return variables.resolve(value);
  }

  private void cancel() {
    cancelled = true;
    options = null;
    shell.dispose();
  }

  @Getter
  @Setter
  public static final class ImportIcebergTableOptions {
    private String tableIdentifier;
    private String sourceName;
    private RecordSourceIndicatorOptions recordSourceOptions;
  }
}
