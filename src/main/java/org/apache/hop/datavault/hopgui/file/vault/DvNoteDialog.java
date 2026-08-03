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
package org.apache.hop.datavault.hopgui.file.vault;

import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DvNote;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
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

/** Dialog to edit a Data Vault canvas note (type + text). */
public class DvNoteDialog {
  private static final Class<?> PKG = DvNoteDialog.class;

  private final Shell parent;
  private final DvNote input;
  private Shell shell;
  private Combo wType;
  private Text wText;
  private boolean ok;

  public DvNoteDialog(Shell parent, DvNote note) {
    this.parent = parent;
    this.input = note;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DvNoteDialog.Title"));
    shell.setImage(GuiResource.getInstance().getImageNote());

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_NOTE);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Label wlType = new Label(shell, SWT.RIGHT);
    wlType.setText(BaseMessages.getString(PKG, "DvNoteDialog.Type.Label"));
    PropsUi.setLook(wlType);
    FormData fdlType = new FormData();
    fdlType.left = new FormAttachment(0, 0);
    fdlType.right = new FormAttachment(middle, 0);
    fdlType.top = new FormAttachment(0, margin);
    wlType.setLayoutData(fdlType);

    wType = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wType);
    EnumDialogSupport.populateCombo(wType, DvNoteType.class);
    FormData fdType = new FormData();
    fdType.left = new FormAttachment(middle, margin);
    fdType.top = new FormAttachment(0, margin);
    fdType.right = new FormAttachment(100, 0);
    wType.setLayoutData(fdType);

    Label wlText = new Label(shell, SWT.RIGHT | SWT.TOP);
    wlText.setText(BaseMessages.getString(PKG, "DvNoteDialog.Text.Label"));
    PropsUi.setLook(wlText);
    FormData fdlText = new FormData();
    fdlText.left = new FormAttachment(0, 0);
    fdlText.right = new FormAttachment(middle, 0);
    fdlText.top = new FormAttachment(wType, margin);
    wlText.setLayoutData(fdlText);

    Label wlHelp = new Label(shell, SWT.LEFT);
    wlHelp.setText(BaseMessages.getString(PKG, "DvNoteDialog.LinkSyntax.Help"));
    PropsUi.setLook(wlHelp);
    FormData fdlHelp = new FormData();
    fdlHelp.left = new FormAttachment(middle, margin);
    fdlHelp.right = new FormAttachment(100, 0);
    fdlHelp.bottom = new FormAttachment(wOk, -margin);
    wlHelp.setLayoutData(fdlHelp);

    wText = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    PropsUi.setLook(wText);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(middle, margin);
    fdText.top = new FormAttachment(wType, margin);
    fdText.right = new FormAttachment(100, 0);
    fdText.bottom = new FormAttachment(wlHelp, -margin);
    fdText.height = 120;
    wText.setLayoutData(fdText);

    getData();
    BaseTransformDialog.setSize(shell, 480, 280);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private void getData() {
    DvNoteType type = input.getNoteType() != null ? input.getNoteType() : DvNoteType.GENERAL;
    EnumDialogSupport.selectCombo(wType, type);
    wText.setText(input.getText() != null ? input.getText() : "");
  }

  private void ok() {
    input.setNoteType(EnumDialogSupport.readCombo(wType, DvNoteType.class, DvNoteType.GENERAL));
    input.setText(wText.getText());
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
