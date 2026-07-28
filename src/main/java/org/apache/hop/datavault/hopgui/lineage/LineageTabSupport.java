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

package org.apache.hop.datavault.hopgui.lineage;

import java.util.stream.Collectors;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.lineage.FieldContribution;
import org.apache.hop.datavault.lineage.FieldLineage;
import org.apache.hop.datavault.lineage.LineageReason;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.lineage.TableSourceRef;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

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
    fdlHeader.left = new FormAttachment(0, 0);
    fdlHeader.top = new FormAttachment(0, 0);
    fdlHeader.right = new FormAttachment(100, 0);
    wlHeader.setLayoutData(fdlHeader);

    Text wReasons = new Text(comp, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wReasons, Props.WIDGET_STYLE_FIXED);
    FormData fdReasons = new FormData();
    fdReasons.left = new FormAttachment(0, 0);
    fdReasons.top = new FormAttachment(wlHeader, margin);
    fdReasons.right = new FormAttachment(100, 0);
    fdReasons.height = 90;
    wReasons.setLayoutData(fdReasons);
    wReasons.setText(formatTableHeader(tableLineage));

    Label wlFields = new Label(comp, SWT.LEFT);
    wlFields.setText(BaseMessages.getString(PKG, "LineageTab.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.top = new FormAttachment(wReasons, margin);
    fdlFields.right = new FormAttachment(100, 0);
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

    int rowCount = countContributionRows(tableLineage);
    TableView tableView =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(rowCount, 1),
            true, // read-only
            null,
            PropsUi.getInstance());
    tableView.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlFields, margin)
            .right()
            .bottom()
            .result());

    populateFields(tableView, tableLineage);

    comp.layout();
    tab.setControl(comp);
  }

  /**
   * Resolves table lineage for a logical name from a snapshot (by logical or physical name).
   */
  public static TableLineage findTable(LineageSnapshot snapshot, String logicalOrPhysicalName) {
    if (snapshot == null || Utils.isEmpty(logicalOrPhysicalName)) {
      return null;
    }
    return snapshot
        .findTableByLogicalName(logicalOrPhysicalName)
        .or(() -> snapshot.findTableByPhysicalName(logicalOrPhysicalName))
        .orElse(null);
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
    if (tableLineage == null) {
      return;
    }
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
            5,
            contribution.getTransform() != null ? contribution.getTransform().name() : "");
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
    tableView.setRowNums();
    tableView.optWidth(true);
  }
}
