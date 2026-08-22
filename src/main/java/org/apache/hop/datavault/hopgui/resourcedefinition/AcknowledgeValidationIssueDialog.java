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
package org.apache.hop.datavault.hopgui.resourcedefinition;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
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

/** Collects a required comment before acknowledging a validation issue. */
public final class AcknowledgeValidationIssueDialog {

  private static final Class<?> PKG = AcknowledgeValidationIssueDialog.class;

  private final Shell parent;
  private final String issueMessage;

  private String comment;
  private boolean confirmed;

  public AcknowledgeValidationIssueDialog(Shell parent, String issueMessage) {
    this.parent = parent;
    this.issueMessage = issueMessage;
  }

  public boolean openConfirmed() {
    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle() | SWT.RESIZE);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "AcknowledgeValidationIssueDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Label wlIssue = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlIssue);
    wlIssue.setText(
        BaseMessages.getString(
            PKG,
            "AcknowledgeValidationIssueDialog.Issue.Label",
            Utils.isEmpty(issueMessage) ? "" : issueMessage));
    FormData fdlIssue = new FormData();
    fdlIssue.left = new FormAttachment(0, margin);
    fdlIssue.right = new FormAttachment(100, -margin);
    fdlIssue.top = new FormAttachment(0, margin);
    wlIssue.setLayoutData(fdlIssue);

    Label wlComment = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlComment);
    wlComment.setText(
        BaseMessages.getString(PKG, "AcknowledgeValidationIssueDialog.Comment.Label"));
    FormData fdlComment = new FormData();
    fdlComment.left = new FormAttachment(0, margin);
    fdlComment.top = new FormAttachment(wlIssue, margin);
    wlComment.setLayoutData(fdlComment);

    Text wComment = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wComment);
    FormData fdComment = new FormData();
    fdComment.left = new FormAttachment(0, margin);
    fdComment.right = new FormAttachment(100, -margin);
    fdComment.top = new FormAttachment(wlComment, margin);
    fdComment.height = 100;
    wComment.setLayoutData(fdComment);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "AcknowledgeValidationIssueDialog.Acknowledge.Label"));
    wOk.addListener(
        SWT.Selection,
        e -> {
          comment = wComment.getText();
          if (!Utils.isEmpty(comment)) {
            confirmed = true;
            shell.dispose();
          }
        });
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    DialogHelpSupport.createHelpButton(shell, HelpTopics.ACKNOWLEDGE_VALIDATION_ISSUE);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, wComment);

    shell.setMinimumSize(480, 280);
    shell.pack();
    BaseDialog.defaultShellHandling(
        shell,
        e -> {
          comment = wComment.getText();
          if (!Utils.isEmpty(comment)) {
            confirmed = true;
            shell.dispose();
          }
        },
        e -> shell.dispose());
    return confirmed;
  }

  public String getComment() {
    return comment;
  }
}
