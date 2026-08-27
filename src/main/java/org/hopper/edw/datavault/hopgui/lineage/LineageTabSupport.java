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
package org.hopper.edw.datavault.hopgui.lineage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ShowMessageDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.lineage.FieldContribution;
import org.hopper.edw.datavault.lineage.FieldLineage;
import org.hopper.edw.datavault.lineage.LineageReason;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.lineage.TableLineage;
import org.hopper.edw.datavault.lineage.TableSourceRef;

/**
 * Read-only Lineage tab for DV/BV/DM table dialogs: table reasons plus a field contribution grid.
 */
public final class LineageTabSupport {

  private static final Class<?> PKG = LineageTabSupport.class;

  private LineageTabSupport() {}

  /**
   * Adds a Lineage tab populated from {@code tableLineage}. When lineage is null, shows an empty
   * placeholder message.
   */
  public static void addTab(
      CTabFolder tabFolder, IVariables variables, int margin, TableLineage tableLineage) {
    LineageTabContent content = createTab(tabFolder, variables, margin);
    content.apply(tableLineage);
  }

  /**
   * Adds a Lineage tab that computes lineage only when the user first selects the tab (with wait
   * cursor). Prefer this in table dialogs so open stays fast.
   */
  public static void addLazyTab(
      CTabFolder tabFolder,
      IVariables variables,
      int margin,
      Supplier<TableLineage> lineageSupplier) {
    LineageTabContent content = createTab(tabFolder, variables, margin);
    content.wReasons.setText(BaseMessages.getString(PKG, "LineageTab.NotLoaded"));

    AtomicBoolean loaded = new AtomicBoolean(false);
    tabFolder.addListener(
        SWT.Selection,
        e -> {
          if (tabFolder.isDisposed() || content.tab.isDisposed()) {
            return;
          }
          if (tabFolder.getSelection() != content.tab) {
            return;
          }
          if (!loaded.compareAndSet(false, true)) {
            return;
          }
          AtomicReference<TableLineage> lineage = new AtomicReference<>();
          GuiBusySupport.showWhile(
              tabFolder,
              () -> {
                try {
                  if (lineageSupplier != null) {
                    lineage.set(lineageSupplier.get());
                  }
                } catch (Exception ignored) {
                  // Keep dialog open; tab shows empty lineage message.
                }
              });
          if (!tabFolder.isDisposed() && !content.tab.isDisposed()) {
            content.apply(lineage.get());
          }
        });
  }

  /** Resolves table lineage for a logical name from a snapshot (by logical or physical name). */
  public static TableLineage findTable(LineageSnapshot snapshot, String logicalOrPhysicalName) {
    if (snapshot == null || Utils.isEmpty(logicalOrPhysicalName)) {
      return null;
    }
    return snapshot
        .findTableByLogicalName(logicalOrPhysicalName)
        .or(() -> snapshot.findTableByPhysicalName(logicalOrPhysicalName))
        .orElse(null);
  }

  /** Opens a modal viewer for dialogs that do not use a CTabFolder (e.g. simple PIT editors). */
  public static void openViewerDialog(
      Shell parent, IVariables variables, TableLineage tableLineage) {
    Shell shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    String title =
        tableLineage != null && !Utils.isEmpty(tableLineage.getLogicalName())
            ? BaseMessages.getString(PKG, "LineageTab.Title")
                + " — "
                + tableLineage.getLogicalName()
            : BaseMessages.getString(PKG, "LineageTab.Title");
    shell.setText(title);

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wClose}, margin, null);

    CTabFolder folder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(folder, Props.WIDGET_STYLE_TAB);
    folder.setLayoutData(
        new FormDataBuilder().left().top().right().bottom(wClose, -2 * margin).result());
    addTab(folder, variables, margin, tableLineage);
    folder.setSelection(0);

    BaseTransformDialog.setSize(shell, 780, 560);
    shell.open();
    while (!shell.isDisposed()) {
      if (!shell.getDisplay().readAndDispatch()) {
        shell.getDisplay().sleep();
      }
    }
  }

  /** Shows a scrollable DDL lineage explanation dialog when explanation text is non-empty. */
  public static void showDdlExplanation(Shell shell, String explanation) {
    if (shell == null || Utils.isEmpty(explanation)) {
      return;
    }
    ShowMessageDialog dialog =
        new ShowMessageDialog(
            shell,
            SWT.OK | SWT.ICON_INFORMATION,
            BaseMessages.getString(PKG, "LineageExplainDialog.Title"),
            explanation,
            true);
    dialog.open();
  }

  /** Plain-text export of table + field lineage (for buttons / reports). */
  public static String formatAsText(TableLineage tableLineage) {
    if (tableLineage == null) {
      return BaseMessages.getString(PKG, "LineageTab.NoLineage");
    }
    StringBuilder sb = new StringBuilder();
    sb.append(formatTableHeader(tableLineage)).append("\n\n");
    sb.append(BaseMessages.getString(PKG, "LineageTab.Fields.Label")).append('\n');
    for (FieldLineage field : tableLineage.getFields()) {
      if (field.getContributions().isEmpty()) {
        sb.append("  - ")
            .append(Const.NVL(field.getTargetFieldName(), ""))
            .append(field.isTechnical() ? " [technical]" : "")
            .append('\n');
        continue;
      }
      for (FieldContribution contribution : field.getContributions()) {
        sb.append("  - ")
            .append(Const.NVL(field.getTargetFieldName(), ""))
            .append(field.isTechnical() ? " [technical]" : "")
            .append(" ← ")
            .append(Const.NVL(contribution.getSourceName(), ""))
            .append(
                Utils.isEmpty(contribution.getSourceFieldName())
                    ? ""
                    : "." + contribution.getSourceFieldName());
        if (!contribution.getReasons().isEmpty()) {
          sb.append(" [")
              .append(
                  contribution.getReasons().stream()
                      .map(r -> r.getCode().name())
                      .collect(Collectors.joining("/")))
              .append("] ")
              .append(Const.NVL(contribution.getReasons().get(0).getMessage(), ""));
        }
        sb.append('\n');
      }
    }
    return sb.toString().trim();
  }

  private static LineageTabContent createTab(
      CTabFolder tabFolder, IVariables variables, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "LineageTab.Title"));
    tab.setToolTipText(BaseMessages.getString(PKG, "LineageTab.ToolTip"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());

    Label wlHeader = new Label(comp, SWT.LEFT);
    wlHeader.setText(BaseMessages.getString(PKG, "LineageTab.TableReasons.Label"));
    PropsUi.setLook(wlHeader);
    FormData fdlHeader = new FormData();
    fdlHeader.left = new FormAttachment(0, margin);
    fdlHeader.top = new FormAttachment(0, margin);
    fdlHeader.right = new FormAttachment(100, 0);
    wlHeader.setLayoutData(fdlHeader);

    Text wReasons =
        new Text(comp, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wReasons, Props.WIDGET_STYLE_FIXED);
    FormData fdReasons = new FormData();
    fdReasons.left = new FormAttachment(0, margin);
    fdReasons.top = new FormAttachment(wlHeader, margin);
    fdReasons.right = new FormAttachment(100, -margin);
    fdReasons.height = (int) (120 * PropsUi.getNativeZoomFactor());
    wReasons.setLayoutData(fdReasons);
    wReasons.setText("");

    Label wlFields = new Label(comp, SWT.LEFT);
    wlFields.setText(BaseMessages.getString(PKG, "LineageTab.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, margin);
    fdlFields.top = new FormAttachment(wReasons, margin);
    fdlFields.right = new FormAttachment(100, -margin);
    wlFields.setLayoutData(fdlFields);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.TargetField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.Technical"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.Source"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.SourceField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.Transform"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.ReasonCode"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "LineageTab.Column.Why"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };

    TableView tableView =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            true, // read-only
            null,
            PropsUi.getInstance());
    tableView.setLayoutData(
        new FormDataBuilder().left().top(wlFields, margin).right().bottom().result());

    comp.layout();
    tab.setControl(comp);

    return new LineageTabContent(tab, wReasons, tableView);
  }

  private static String formatTableHeader(TableLineage tableLineage) {
    if (tableLineage == null) {
      return BaseMessages.getString(PKG, "LineageTab.NoLineage");
    }
    StringBuilder sb = new StringBuilder();
    sb.append(tableLineage.getTableType())
        .append("  ")
        .append(Const.NVL(tableLineage.getLogicalName(), ""))
        .append(" → ")
        .append(Const.NVL(tableLineage.getPhysicalTableName(), ""))
        .append('\n');
    if (!Utils.isEmpty(tableLineage.getDescription())) {
      sb.append(tableLineage.getDescription()).append('\n');
    }
    if (!tableLineage.getSources().isEmpty()) {
      sb.append(BaseMessages.getString(PKG, "LineageTab.SourcesPrefix"))
          .append(
              tableLineage.getSources().stream()
                  .map(LineageTabSupport::formatSource)
                  .collect(Collectors.joining(", ")))
          .append('\n');
    }
    sb.append('\n');
    for (LineageReason reason : tableLineage.getReasons()) {
      sb.append('[')
          .append(reason.getCode())
          .append("] ")
          .append(Const.NVL(reason.getMessage(), ""))
          .append('\n');
    }
    return sb.toString().trim();
  }

  private static String formatSource(TableSourceRef source) {
    if (source == null) {
      return "";
    }
    String name = Const.NVL(source.getName(), "");
    if (source.getRole() != null) {
      return name + " (" + source.getRole() + ")";
    }
    return name;
  }

  private static void ensureRowCount(TableView tableView, int needed) {
    if (tableView == null || tableView.table == null || tableView.table.isDisposed()) {
      return;
    }
    while (tableView.table.getItemCount() < Math.max(needed, 1)) {
      new TableItem(tableView.table, SWT.NONE);
    }
  }

  private static void clearFieldRows(TableView tableView) {
    if (tableView == null || tableView.table == null || tableView.table.isDisposed()) {
      return;
    }
    tableView.table.removeAll();
    new TableItem(tableView.table, SWT.NONE);
  }

  private static int countContributionRows(TableLineage tableLineage) {
    if (tableLineage == null) {
      return 0;
    }
    int count = 0;
    for (FieldLineage field : tableLineage.getFields()) {
      if (field.getContributions().isEmpty()) {
        count++;
      } else {
        count += field.getContributions().size();
      }
    }
    return count;
  }

  private static void populateFields(TableView tableView, TableLineage tableLineage) {
    clearFieldRows(tableView);
    if (tableLineage == null) {
      tableView.optimizeTableView();
      return;
    }
    int needed = countContributionRows(tableLineage);
    ensureRowCount(tableView, needed);

    int row = 0;
    for (FieldLineage field : tableLineage.getFields()) {
      if (field.getContributions().isEmpty()) {
        TableItem item = tableView.table.getItem(row++);
        item.setText(1, Const.NVL(field.getTargetFieldName(), ""));
        item.setText(2, field.isTechnical() ? "Y" : "N");
        continue;
      }
      for (FieldContribution contribution : field.getContributions()) {
        if (row >= tableView.table.getItemCount()) {
          new TableItem(tableView.table, SWT.NONE);
        }
        TableItem item = tableView.table.getItem(row++);
        item.setText(1, Const.NVL(field.getTargetFieldName(), ""));
        item.setText(2, field.isTechnical() ? "Y" : "N");
        item.setText(3, Const.NVL(contribution.getSourceName(), ""));
        item.setText(4, Const.NVL(contribution.getSourceFieldName(), ""));
        item.setText(
            5, contribution.getTransform() != null ? contribution.getTransform().name() : "");
        String codes =
            contribution.getReasons().stream()
                .map(r -> r.getCode().name())
                .collect(Collectors.joining("/"));
        item.setText(6, codes);
        String why =
            contribution.getReasons().isEmpty()
                ? ""
                : Const.NVL(contribution.getReasons().get(0).getMessage(), "");
        item.setText(7, why);
      }
    }
    tableView.optimizeTableView();
  }

  private static final class LineageTabContent {
    private final CTabItem tab;
    private final Text wReasons;
    private final TableView tableView;

    private LineageTabContent(CTabItem tab, Text wReasons, TableView tableView) {
      this.tab = tab;
      this.wReasons = wReasons;
      this.tableView = tableView;
    }

    private void apply(TableLineage tableLineage) {
      if (wReasons == null || wReasons.isDisposed()) {
        return;
      }
      wReasons.setText(formatTableHeader(tableLineage));
      populateFields(tableView, tableLineage);
    }
  }
}
