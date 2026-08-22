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
package org.apache.hop.datavault.metadata.file;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.RecordSourceIndicatorImportSection;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
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

/** Collects import options before importing a CSV file as a catalog record definition. */
@Getter
@Setter
public class ImportCsvFileOptionsDialog {

  private static final Class<?> PKG = ImportCsvFileOptionsDialog.class;

  private final Shell parent;
  private final String filePath;
  private final String defaultSourceName;
  private final List<String> discoveredFieldNames;

  private Shell shell;
  private Text wFilePath;
  private Text wSourceName;
  private RecordSourceIndicatorImportSection recordSourceSection;
  private Button wOk;

  private ImportCsvFileOptions options;
  private boolean cancelled = true;

  public ImportCsvFileOptionsDialog(
      Shell parent, String filePath, String defaultSourceName, List<String> discoveredFieldNames) {
    this.parent = parent;
    this.filePath = filePath;
    this.defaultSourceName = defaultSourceName;
    this.discoveredFieldNames = discoveredFieldNames;
  }

  public ImportCsvFileOptions open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ImportCsvFileOptionsDialog.Shell.Title"));
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

    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_CSV_FILE_OPTIONS);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Control lastControl = null;

    Label wlFilePath = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlFilePath);
    wlFilePath.setText(BaseMessages.getString(PKG, "ImportCsvFileOptionsDialog.FilePath.Label"));
    FormData fdlFilePath = new FormData();
    fdlFilePath.top = new FormAttachment(0, margin);
    fdlFilePath.left = new FormAttachment(0, 0);
    fdlFilePath.right = new FormAttachment(middle, -margin);
    wlFilePath.setLayoutData(fdlFilePath);

    wFilePath = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wFilePath);
    wFilePath.setText(Const.NVL(filePath, ""));
    FormData fdFilePath = new FormData();
    fdFilePath.top = new FormAttachment(wlFilePath, 0, SWT.CENTER);
    fdFilePath.left = new FormAttachment(middle, 0);
    fdFilePath.right = new FormAttachment(100, 0);
    wFilePath.setLayoutData(fdFilePath);
    lastControl = wFilePath;

    Label wlSourceName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlSourceName);
    wlSourceName.setText(
        BaseMessages.getString(PKG, "ImportCsvFileOptionsDialog.SourceName.Label"));
    FormData fdlSourceName = new FormData();
    fdlSourceName.top = new FormAttachment(lastControl, margin);
    fdlSourceName.left = new FormAttachment(0, 0);
    fdlSourceName.right = new FormAttachment(middle, -margin);
    wlSourceName.setLayoutData(fdlSourceName);

    wSourceName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
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
    String sourceName = Const.NVL(wSourceName.getText(), "").trim();
    RecordSourceIndicatorOptions recordSourceOptions =
        recordSourceSection.collectOptions(sourceName);
    if (recordSourceOptions == null) {
      return;
    }

    options = new ImportCsvFileOptions();
    options.setFilePath(wFilePath.getText());
    options.setSourceName(wSourceName.getText());
    options.setRecordSourceOptions(recordSourceOptions);
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    cancelled = true;
    options = null;
    shell.dispose();
  }

  @Getter
  @Setter
  public static final class ImportCsvFileOptions {
    private String filePath;
    private String sourceName;
    private RecordSourceIndicatorOptions recordSourceOptions;
  }
}
