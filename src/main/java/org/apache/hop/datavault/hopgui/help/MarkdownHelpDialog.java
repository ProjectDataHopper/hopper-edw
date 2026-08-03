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
package org.apache.hop.datavault.hopgui.help;

import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyledTextComp;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;

/** Modal dialog that renders markdown help text with SWT style ranges. */
public final class MarkdownHelpDialog {

  private static final Class<?> PKG = MarkdownHelpDialog.class;

  private MarkdownHelpDialog() {}

  public static void open(Shell parent, String title, String markdown, String topicId) {
    if (parent == null || parent.isDisposed()) {
      return;
    }
    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        Utils.isEmpty(title) ? BaseMessages.getString(PKG, "MarkdownHelpDialog.Title") : title);

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);

    int margin = PropsUi.getMargin();

    Button wOpenInBrowser = new Button(shell, SWT.PUSH);
    wOpenInBrowser.setText(BaseMessages.getString(PKG, "MarkdownHelpDialog.OpenInBrowser"));
    wOpenInBrowser.addListener(
        SWT.Selection,
        e -> MarkdownHelpBrowserSupport.openInBrowser(shell, shell.getText(), markdown, topicId));

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOpenInBrowser, wClose}, margin, null);

    MarkdownStyledTextComp markdownComp = new MarkdownStyledTextComp(shell, SWT.NONE);
    markdownComp.setMarkdown(markdown != null ? markdown : "");
    FormData fdMarkdown = new FormData();
    fdMarkdown.left = new FormAttachment(0, 0);
    fdMarkdown.right = new FormAttachment(100, 0);
    fdMarkdown.top = new FormAttachment(0, margin);
    fdMarkdown.bottom = new FormAttachment(wClose, -margin);
    markdownComp.setLayoutData(fdMarkdown);

    BaseTransformDialog.setSize(shell, 720, 520);
    BaseDialog.defaultShellHandling(shell, e -> shell.dispose(), e -> shell.dispose());
  }
}
