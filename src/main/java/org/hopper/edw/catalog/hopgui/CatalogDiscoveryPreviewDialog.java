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
package org.hopper.edw.catalog.hopgui;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.catalog.discovery.CatalogDiscoverySnapshot;
import org.hopper.edw.catalog.model.RecordDefinitionRef;

/** Shows resolved catalog storage and working-tree record names (not version snapshots). */
public final class CatalogDiscoveryPreviewDialog {

  private static final Class<?> PKG = CatalogDiscoveryPreviewDialog.class;
  private static final int MAX_ROWS = 500;

  private CatalogDiscoveryPreviewDialog() {}

  public static void open(Shell parent, CatalogDiscoverySnapshot snapshot) {
    if (parent == null || snapshot == null) {
      return;
    }
    Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Shell.Title"));
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    Label wSummary = new Label(shell, SWT.WRAP);
    PropsUi.setLook(wSummary);
    wSummary.setText(formatSummary(snapshot));
    FormData fdSummary = new FormData();
    fdSummary.left = new FormAttachment(0, 0);
    fdSummary.top = new FormAttachment(0, margin);
    fdSummary.right = new FormAttachment(100, 0);
    wSummary.setLayoutData(fdSummary);

    Button wClose = new Button(shell, SWT.PUSH);
    PropsUi.setLook(wClose);
    wClose.setText(BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Close"));
    FormData fdClose = new FormData();
    fdClose.right = new FormAttachment(100, 0);
    fdClose.bottom = new FormAttachment(100, 0);
    wClose.setLayoutData(fdClose);
    wClose.addListener(SWT.Selection, e -> shell.dispose());

    Table table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(table);
    table.setHeaderVisible(true);
    table.setLinesVisible(true);
    addColumn(table, BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Column.Namespace"));
    addColumn(table, BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Column.Name"));
    addColumn(table, BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Column.Type"));

    List<RecordDefinitionRef> refs = snapshot.getRefs();
    int limit = refs == null ? 0 : Math.min(refs.size(), MAX_ROWS);
    for (int i = 0; i < limit; i++) {
      RecordDefinitionRef ref = refs.get(i);
      if (ref == null || ref.getKey() == null) {
        continue;
      }
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(0, Const.NVL(ref.getKey().getNamespace(), ""));
      item.setText(1, Const.NVL(ref.getKey().getName(), ""));
      item.setText(2, ref.getType() != null ? ref.getType().name() : "");
    }
    for (TableColumn column : table.getColumns()) {
      column.pack();
    }

    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, 0);
    fdTable.right = new FormAttachment(100, 0);
    fdTable.top = new FormAttachment(wSummary, margin);
    fdTable.bottom = new FormAttachment(wClose, -margin);
    table.setLayoutData(fdTable);

    shell.setMinimumSize(640, 400);
    shell.setSize(760, 520);
    BaseDialog.defaultShellHandling(shell, e -> shell.dispose(), e -> shell.dispose());
  }

  static String formatSummary(CatalogDiscoverySnapshot snapshot) {
    StringBuilder text = new StringBuilder();
    text.append(
        BaseMessages.getString(
            PKG,
            "CatalogDiscoveryPreviewDialog.Summary.Connection",
            Const.NVL(snapshot.getConnectionName(), ""),
            Const.NVL(snapshot.getPluginId(), "")));
    text.append(Const.CR);
    if (!Utils.isEmpty(snapshot.getStorageDirectory())) {
      text.append(
          BaseMessages.getString(
              PKG,
              "CatalogDiscoveryPreviewDialog.Summary.Configured",
              snapshot.getStorageDirectory()));
      text.append(Const.CR);
    }
    if (!Utils.isEmpty(snapshot.getResolvedStorageDirectory())) {
      text.append(
          BaseMessages.getString(
              PKG,
              "CatalogDiscoveryPreviewDialog.Summary.Resolved",
              snapshot.getResolvedStorageDirectory()));
      text.append(Const.CR);
    }
    text.append(
        BaseMessages.getString(
            PKG,
            "CatalogDiscoveryPreviewDialog.Summary.WorkingTree",
            snapshot.getWorkingTreeCount(),
            snapshot.getSkippedUnreadable()));
    text.append(Const.CR);
    if (snapshot.isVersionSnapshotsPresent()) {
      text.append(
          BaseMessages.getString(PKG, "CatalogDiscoveryPreviewDialog.Summary.VersionsPresent"));
      text.append(Const.CR);
    }
    if (!Utils.isEmpty(snapshot.getErrorMessage())) {
      text.append(
          BaseMessages.getString(
              PKG, "CatalogDiscoveryPreviewDialog.Summary.Error", snapshot.getErrorMessage()));
    }
    if (snapshot.getWorkingTreeCount() == 0 && Utils.isEmpty(snapshot.getErrorMessage())) {
      text.append(Const.CR);
      text.append(
          BaseMessages.getString(
              PKG,
              snapshot.isVersionSnapshotsPresent()
                  ? "CatalogDiscoveryPreviewDialog.Summary.EmptyWithVersions"
                  : "CatalogDiscoveryPreviewDialog.Summary.Empty"));
    }
    return text.toString();
  }

  private static void addColumn(Table table, String title) {
    TableColumn column = new TableColumn(table, SWT.LEFT);
    column.setText(title);
  }
}
