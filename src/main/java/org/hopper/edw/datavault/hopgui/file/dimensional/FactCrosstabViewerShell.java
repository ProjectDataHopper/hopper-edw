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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Props;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;

/** Non-modal presentation viewer with an Edit button back to the crosstab editor. */
final class FactCrosstabViewerShell {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  private final HopGui hopGui;
  private final HGeneratedCatalog catalog;
  private final Runnable onEdit;

  FactCrosstabViewerShell(HopGui hopGui, HGeneratedCatalog catalog, Runnable onEdit) {
    this.hopGui = hopGui;
    this.catalog = catalog;
    this.onEdit = onEdit;
  }

  void open() {
    Shell shell = new Shell(hopGui.getShell(), SWT.SHELL_TRIM);
    String title =
        catalog.getPresentation() != null
                && StringUtils.isNotBlank(catalog.getPresentation().getName())
            ? catalog.getPresentation().getName()
            : BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Shell.Title");
    shell.setText(title);
    shell.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();

    Composite bar = new Composite(shell, SWT.NONE);
    PropsUi.setLook(bar, Props.WIDGET_STYLE_TOOLBAR);
    bar.setLayout(new FormLayout());
    FormData fdBar = new FormData();
    fdBar.left = new FormAttachment(0, 0);
    fdBar.top = new FormAttachment(0, 0);
    fdBar.right = new FormAttachment(100, 0);
    bar.setLayoutData(fdBar);

    Button edit = new Button(bar, SWT.PUSH);
    PropsUi.setLook(edit);
    edit.setText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Edit.Label"));
    edit.setToolTipText(BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Edit.Tooltip"));
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(0, margin);
    fdEdit.top = new FormAttachment(0, margin);
    fdEdit.bottom = new FormAttachment(100, -margin);
    edit.setLayoutData(fdEdit);
    edit.addListener(
        SWT.Selection,
        e -> {
          shell.dispose();
          if (onEdit != null) {
            onEdit.run();
          }
        });

    try {
      PresentationGuiPlugin.ensureEnvironment();
      HPresentationViewer viewer =
          new HPresentationViewer(
              shell,
              FactCrosstabGuiSupport.loggingObject(),
              catalog.getProvider(),
              catalog.getPresentation(),
              HPresentationChrome.FULL,
              null);
      FormData fdViewer = new FormData();
      fdViewer.left = new FormAttachment(0, 0);
      fdViewer.top = new FormAttachment(bar, 0);
      fdViewer.right = new FormAttachment(100, 0);
      fdViewer.bottom = new FormAttachment(100, 0);
      viewer.setLayoutData(fdViewer);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Generate"),
          e);
      shell.dispose();
      return;
    }
    shell.setSize(1100, 800);
    shell.open();
  }
}
