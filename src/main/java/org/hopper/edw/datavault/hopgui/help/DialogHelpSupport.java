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
package org.hopper.edw.datavault.hopgui.help;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.EdwDocsGuiPlugin;
import org.hopper.edw.datavault.hopgui.help.HelpTopics.HelpPage;

/** Wires Help buttons on plugin dialogs to plugin-shipped HTML documentation. */
public final class DialogHelpSupport {

  private static final Class<?> PKG = DialogHelpSupport.class;

  private DialogHelpSupport() {}

  /** Replaces any existing Hop URL Help button on the shell, then adds plugin HTML Help. */
  public static Button installLocalHelpButton(Shell shell, String topicId) {
    removeExistingHelpButtons(shell);
    return createHelpButton(shell, topicId);
  }

  public static Button createHelpButton(Composite parent, String topicId) {
    Button button = new Button(parent, SWT.PUSH);
    PropsUi.setLook(button);
    button.setImage(GuiResource.getInstance().getImageHelp());
    button.setText(BaseMessages.getString(PKG, "System.Button.Help"));
    button.setToolTipText(BaseMessages.getString(PKG, "System.Tooltip.Help"));
    FormData fdButton = new FormData();
    fdButton.left = new FormAttachment(0, 0);
    fdButton.bottom = new FormAttachment(100, 0);
    button.setLayoutData(fdButton);
    button.addListener(SWT.Selection, e -> openHelp(parent.getShell(), topicId));
    return button;
  }

  public static void openHelp(Shell parentShell, String topicId) {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }
    try {
      HelpPage page = HelpTopics.requirePage(topicId);
      EdwDocsGuiPlugin.openHtml(parentShell, page.htmlPage(), page.anchor());
    } catch (HopException ex) {
      new ErrorDialog(
          parentShell, BaseMessages.getString(PKG, "DialogHelp.Error.Title"), ex.getMessage(), ex);
    }
  }

  private static void removeExistingHelpButtons(Composite composite) {
    String helpLabel = BaseMessages.getString(PKG, "System.Button.Help");
    for (Control child : composite.getChildren()) {
      if (child instanceof Button button && helpLabel.equals(button.getText())) {
        child.dispose();
      }
    }
  }
}
