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
package org.apache.hop.datavault.hopgui;

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

/** Checkbox dialog offering standard project catalog and model-configuration elements. */
public final class StandardProjectElementsOfferDialog {

  private static final Class<?> PKG = StandardProjectElementsOfferSupport.class;

  private StandardProjectElementsOfferDialog() {}

  public record Selection(
      boolean accepted,
      boolean dontShowAgain,
      boolean createCatalog,
      boolean createSourceModel,
      boolean createDataVault,
      boolean createBusinessVault,
      boolean createDimensional) {}

  public static Selection open(
      Shell parent,
      boolean offerCatalog,
      boolean offerSourceModel,
      boolean offerDataVault,
      boolean offerBusinessVault,
      boolean offerDimensional) {
    return open(
        parent,
        false,
        false,
        offerCatalog,
        offerSourceModel,
        offerDataVault,
        offerBusinessVault,
        offerDimensional);
  }

  public static Selection open(
      Shell parent,
      boolean fromMenu,
      boolean dontShowAgainInitial,
      boolean missingCatalog,
      boolean missingSourceModel,
      boolean missingDataVault,
      boolean missingBusinessVault,
      boolean missingDimensional) {
    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            fromMenu
                ? "StandardProjectElementsOffer.Menu.Title"
                : "StandardProjectElementsOffer.Title"));
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    Label wlMessage = new Label(shell, SWT.WRAP);
    PropsUi.setLook(wlMessage);
    wlMessage.setText(
        BaseMessages.getString(
            PKG,
            fromMenu
                ? "StandardProjectElementsOffer.Menu.Message"
                : "StandardProjectElementsOffer.Message"));
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.top = new FormAttachment(0, margin);
    fdMessage.right = new FormAttachment(100, 0);
    wlMessage.setLayoutData(fdMessage);

    Button wCatalog =
        checkbox(shell, missingCatalog, "StandardProjectElementsOffer.Catalog", wlMessage, margin);
    Button wSource =
        checkbox(
            shell,
            missingSourceModel,
            "StandardProjectElementsOffer.SourceModel",
            wCatalog,
            margin);
    Button wDv =
        checkbox(
            shell, missingDataVault, "StandardProjectElementsOffer.DataVault", wSource, margin);
    Button wBv =
        checkbox(
            shell, missingBusinessVault, "StandardProjectElementsOffer.BusinessVault", wDv, margin);
    Button wDm =
        checkbox(
            shell, missingDimensional, "StandardProjectElementsOffer.Dimensional", wBv, margin);

    Button wDontShow = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wDontShow);
    wDontShow.setText(BaseMessages.getString(PKG, "StandardProjectElementsOffer.DontShowAgain"));
    wDontShow.setSelection(dontShowAgainInitial);
    FormData fdDontShow = new FormData();
    fdDontShow.left = new FormAttachment(0, 0);
    fdDontShow.top = new FormAttachment(wDm, margin * 2);
    wDontShow.setLayoutData(fdDontShow);

    final Selection[] result = new Selection[1];
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "StandardProjectElementsOffer.Create"));
    wOk.addListener(
        SWT.Selection,
        e -> {
          result[0] =
              new Selection(
                  true,
                  wDontShow.getSelection(),
                  wCatalog.getSelection(),
                  wSource.getSelection(),
                  wDv.getSelection(),
                  wBv.getSelection(),
                  wDm.getSelection());
          shell.dispose();
        });
    Button wSkip = new Button(shell, SWT.PUSH);
    wSkip.setText(
        BaseMessages.getString(
            PKG,
            fromMenu ? "StandardProjectElementsOffer.Close" : "StandardProjectElementsOffer.Skip"));
    wSkip.addListener(
        SWT.Selection,
        e -> {
          result[0] =
              new Selection(false, wDontShow.getSelection(), false, false, false, false, false);
          shell.dispose();
        });
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wSkip}, margin, wDontShow);
    BaseDialog.defaultShellHandling(
        shell,
        e -> wOk.notifyListeners(SWT.Selection, null),
        e -> wSkip.notifyListeners(SWT.Selection, null));
    return result[0];
  }

  private static Button checkbox(
      Shell shell,
      boolean enabled,
      String messageKey,
      org.eclipse.swt.widgets.Control above,
      int margin) {
    Button button = new Button(shell, SWT.CHECK);
    PropsUi.setLook(button);
    button.setText(BaseMessages.getString(PKG, messageKey));
    button.setEnabled(enabled);
    // Existing elements stay selected so the dialog shows what the project already has.
    button.setSelection(true);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(above, margin);
    button.setLayoutData(fd);
    return button;
  }
}
