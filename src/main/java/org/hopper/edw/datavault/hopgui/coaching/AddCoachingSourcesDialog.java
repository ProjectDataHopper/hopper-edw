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
package org.hopper.edw.datavault.hopgui.coaching;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.hopper.edw.datavault.metadata.coaching.ICoachingModelAdapter;

/** Picks catalog record sources to add to the model coaching configuration. */
public class AddCoachingSourcesDialog {

  private static final Class<?> PKG = AddCoachingSourcesDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final ICoachingModelAdapter adapter;
  private final IVariables variables;

  private Shell shell;
  private MetaSelectionLine<DataCatalogMeta> wCatalogConnection;
  private org.eclipse.swt.widgets.List wSources;
  private boolean cancelled = true;

  public AddCoachingSourcesDialog(
      Shell parent, HopGui hopGui, ICoachingModelAdapter adapter, IVariables variables) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.adapter = adapter;
    this.variables = variables;
  }

  public void open(Runnable onAccepted) {
    try {
      shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
      shell.setText(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Title"));
      PropsUi.setLook(shell);
      FormLayout shellLayout = new FormLayout();
      shellLayout.marginWidth = PropsUi.getFormMargin();
      shellLayout.marginHeight = PropsUi.getFormMargin();
      shell.setLayout(shellLayout);
      int margin = PropsUi.getMargin();

      Button wOk = new Button(shell, SWT.PUSH);
      wOk.setText(
          BaseMessages.getString(
              org.apache.hop.ui.core.dialog.BaseDialog.class, "System.Button.OK"));
      wOk.addListener(SWT.Selection, e -> ok(onAccepted));
      Button wCancel = new Button(shell, SWT.PUSH);
      wCancel.setText(
          BaseMessages.getString(
              org.apache.hop.ui.core.dialog.BaseDialog.class, "System.Button.Cancel"));
      wCancel.addListener(SWT.Selection, e -> cancel());

      Label wlCatalog = new Label(shell, SWT.RIGHT);
      wlCatalog.setText(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Catalog.Label"));
      PropsUi.setLook(wlCatalog);
      FormData fdWlCatalog = new FormData();
      fdWlCatalog.left = new FormAttachment(0, 0);
      fdWlCatalog.top = new FormAttachment(0, margin);
      wlCatalog.setLayoutData(fdWlCatalog);

      wCatalogConnection =
          new MetaSelectionLine<>(
              variables,
              hopGui.getMetadataProvider(),
              DataCatalogMeta.class,
              shell,
              SWT.NONE,
              BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Catalog.Label"),
              BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Catalog.Label"));
      PropsUi.setLook(wCatalogConnection);
      FormData fdCatalog = new FormData();
      fdCatalog.left = new FormAttachment(wlCatalog, margin);
      fdCatalog.top = new FormAttachment(0, margin);
      fdCatalog.right = new FormAttachment(100, -margin);
      wCatalogConnection.setLayoutData(fdCatalog);

      Label wlSources = new Label(shell, SWT.RIGHT);
      wlSources.setText(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Sources.Label"));
      PropsUi.setLook(wlSources);
      FormData fdWlSources = new FormData();
      fdWlSources.left = new FormAttachment(0, 0);
      fdWlSources.top = new FormAttachment(wCatalogConnection, margin);
      wlSources.setLayoutData(fdWlSources);

      Button wImport = new Button(shell, SWT.PUSH);
      wImport.setText(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Import"));
      PropsUi.setLook(wImport);
      FormData fdImport = new FormData();
      fdImport.left = new FormAttachment(0, 0);
      fdImport.bottom = new FormAttachment(wOk, -margin);
      wImport.setLayoutData(fdImport);
      wImport.addListener(
          SWT.Selection,
          e -> {
            try {
              CoachingImportSupport.openImportMenu(hopGui, adapter, variables, this::fillSources);
            } catch (HopException ex) {
              new ErrorDialog(shell, "Import", ex.getMessage(), ex);
            }
          });

      BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

      wSources =
          new org.eclipse.swt.widgets.List(
              shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
      PropsUi.setLook(wSources);
      FormData fdSources = new FormData();
      fdSources.left = new FormAttachment(wlSources, margin);
      fdSources.top = new FormAttachment(wCatalogConnection, margin);
      fdSources.right = new FormAttachment(100, -margin);
      fdSources.bottom = new FormAttachment(wImport, -margin);
      wSources.setLayoutData(fdSources);

      String defaultCatalog = resolveInitialCatalog();
      if (!Utils.isEmpty(defaultCatalog)) {
        wCatalogConnection.setText(defaultCatalog);
      }
      wCatalogConnection.addModifyListener(e -> fillSources());

      fillSources();
      shell.setMinimumSize(480, 360);
      BaseTransformDialog.setSize(shell, 520, 420);
      shell.addListener(
          SWT.Traverse,
          e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
              e.doit = false;
              cancel();
            }
          });
      shell.open();
    } catch (Exception e) {
      new ErrorDialog(parent, "Add coaching sources", e.getMessage(), e);
    }
  }

  private String resolveInitialCatalog() {
    try {
      return adapter.resolveCatalogConnectionName(variables, hopGui.getMetadataProvider());
    } catch (Exception ignored) {
      return null;
    }
  }

  private void fillSources() {
    GuiBusySupport.showWhile(
        shell,
        () -> {
          wSources.removeAll();
          String catalogConnection = wCatalogConnection.getText();
          if (Utils.isEmpty(catalogConnection)) {
            return;
          }
          try {
            java.util.List<String> names =
                DvSourceCatalogService.listSourceNames(
                    catalogConnection, variables, hopGui.getMetadataProvider());
            if (names.isEmpty()) {
              wSources.add(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.NoSources"));
              return;
            }
            for (String name : names) {
              wSources.add(name);
            }
          } catch (HopException e) {
            new ErrorDialog(shell, "Sources", e.getMessage(), e);
          }
        });
  }

  private void ok(Runnable onAccepted) {
    String catalogConnection = wCatalogConnection.getText();
    if (Utils.isEmpty(catalogConnection)) {
      MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
      box.setMessage(BaseMessages.getString(PKG, "AddCoachingSourcesDialog.Catalog.Missing"));
      box.open();
      return;
    }
    adapter.setCatalogConnectionName(catalogConnection);
    String namespace = DvCatalogNamespaces.projectSourcesNamespace(variables);
    String[] selection = wSources.getSelection();
    if (selection != null && selection.length > 0) {
      for (String sourceName : selection) {
        if (Utils.isEmpty(sourceName)
            || sourceName.equals(
                BaseMessages.getString(PKG, "AddCoachingSourcesDialog.NoSources"))) {
          continue;
        }
        adapter
            .getCoachingConfiguration()
            .addCoachingSource(
                CoachingSourceRef.forRecordDefinition(catalogConnection, namespace, sourceName));
      }
    }
    cancelled = false;
    dispose();
    if (onAccepted != null) {
      onAccepted.run();
    }
  }

  private void cancel() {
    cancelled = true;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }
}
