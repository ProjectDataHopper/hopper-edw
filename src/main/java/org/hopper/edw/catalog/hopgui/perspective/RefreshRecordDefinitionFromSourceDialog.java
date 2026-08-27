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

import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogRefreshSupport;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Shows schema drift and asks whether to refresh the catalog contract from the live source. */
public final class RefreshRecordDefinitionFromSourceDialog {

  private static final Class<?> PKG = RefreshRecordDefinitionFromSourceDialog.class;

  private final Shell parent;
  private final RecordDefinitionCatalogRefreshSupport.RefreshPreview preview;

  private boolean confirmed;

  public RefreshRecordDefinitionFromSourceDialog(
      Shell parent, RecordDefinitionCatalogRefreshSupport.RefreshPreview preview) {
    this.parent = parent;
    this.preview = preview;
  }

  public boolean openConfirmed() {
    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(PKG, "RefreshRecordDefinitionFromSourceDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Label wlSummary = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlSummary);
    wlSummary.setText(buildSummaryText());
    FormData fdlSummary = new FormData();
    fdlSummary.left = new FormAttachment(0, margin);
    fdlSummary.right = new FormAttachment(100, -margin);
    fdlSummary.top = new FormAttachment(0, margin);
    wlSummary.setLayoutData(fdlSummary);

    Text wDiff =
        new Text(shell, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wDiff);
    wDiff.setText(RecordDefinitionSchemaDiffSupport.formatDiff(preview.diff()));
    FormData fdDiff = new FormData();
    fdDiff.left = new FormAttachment(0, margin);
    fdDiff.right = new FormAttachment(100, -margin);
    fdDiff.top = new FormAttachment(wlSummary, margin);
    fdDiff.height = 180;
    wDiff.setLayoutData(fdDiff);

    Button wApply = new Button(shell, SWT.PUSH);
    wApply.setText(
        BaseMessages.getString(PKG, "RefreshRecordDefinitionFromSourceDialog.Apply.Label"));
    wApply.addListener(SWT.Selection, e -> confirm(shell));

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel(shell));

    DialogHelpSupport.createHelpButton(shell, HelpTopics.REFRESH_RECORD_DEFINITION_FROM_SOURCE);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wApply, wCancel}, margin, wDiff);
    BaseDialog.defaultShellHandling(shell, e -> confirm(shell), e -> cancel(shell));
    return confirmed;
  }

  private String buildSummaryText() {
    int discoveredCount =
        preview.discoveredFields() != null ? preview.discoveredFields().size() : 0;
    String summary =
        BaseMessages.getString(
            PKG, "RefreshRecordDefinitionFromSourceDialog.Summary", discoveredCount);
    if (preview.physicalSchemaId() != null) {
      summary +=
          "\n"
              + BaseMessages.getString(
                  PKG,
                  "RefreshRecordDefinitionFromSourceDialog.PhysicalSchemaId",
                  preview.physicalSchemaId());
    }
    return summary;
  }

  private void confirm(Shell shell) {
    confirmed = true;
    shell.dispose();
  }

  private void cancel(Shell shell) {
    confirmed = false;
    shell.dispose();
  }
}
