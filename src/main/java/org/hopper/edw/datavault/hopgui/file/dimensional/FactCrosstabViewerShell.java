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
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.hopper.core.exception.HQueryCancelledException;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;

/** Viewer opened on top of the crosstab editor. Closing it returns to that editor. */
final class FactCrosstabViewerShell {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  private final Shell parent;
  private final HGeneratedCatalog catalog;
  private Shell shell;

  FactCrosstabViewerShell(Shell parent, HGeneratedCatalog catalog) {
    this.parent = parent;
    this.catalog = catalog;
  }

  void open() {
    if (parent == null || parent.isDisposed()) {
      return;
    }
    close();
    shell = new Shell(parent, SWT.SHELL_TRIM);
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
    edit.addListener(SWT.Selection, e -> close());

    // Open before the query so the progress dialog has a visible parent. The viewer fills in
    // after the query returns. The editor shell stays open underneath.
    shell.setSize(1100, 800);
    shell.open();
    shell.forceActive();

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
      if (shell.isDisposed()) {
        return;
      }
      FormData fdViewer = new FormData();
      fdViewer.left = new FormAttachment(0, 0);
      fdViewer.top = new FormAttachment(bar, 0);
      fdViewer.right = new FormAttachment(100, 0);
      fdViewer.bottom = new FormAttachment(100, 0);
      viewer.setLayoutData(fdViewer);
      shell.layout(true, true);
    } catch (HQueryCancelledException e) {
      close();
    } catch (Exception e) {
      Shell errorParent = parent != null && !parent.isDisposed() ? parent : shell;
      new ErrorDialog(
          errorParent,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Generate"),
          e);
      close();
    }
  }

  void close() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }
}
