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

import java.util.List;
import java.util.stream.Collectors;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.resourcedefinition.ResourceDefinitionModelNavigationSupport;
import org.apache.hop.datavault.lineage.ReverseLineageConsumer;
import org.apache.hop.datavault.lineage.ReverseLineageIndex;
import org.apache.hop.datavault.lineage.ReverseLineageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Cross-model reverse lineage browser: filter by source feed/field and list DV/BV/DM consumers with
 * path summaries.
 */
public final class ReverseLineageBrowserDialog {

  private static final Class<?> PKG = ReverseLineageBrowserDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final ResourceDefinitionGroupMeta group;

  private Shell shell;
  private Combo wSourceName;
  private Text wSourceField;
  private TableView wResults;
  private Label wlStatus;
  private ReverseLineageIndex index;
  private List<ReverseLineageConsumer> currentRows = List.of();

  public ReverseLineageBrowserDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public void open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Title"));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);
    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());
    Button wOpen = new Button(shell, SWT.PUSH);
    wOpen.setText(BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.OpenModel.Label"));
    wOpen.setToolTipText(
        BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.OpenModel.ToolTip"));
    wOpen.addListener(SWT.Selection, e -> openSelected());
    Button wRefresh = new Button(shell, SWT.PUSH);
    wRefresh.setText(BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Refresh.Label"));
    wRefresh.addListener(SWT.Selection, e -> rebuildIndexAndFilter());
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOpen, wRefresh, wClose}, margin, null);

    Label wlSourceName = new Label(shell, SWT.RIGHT);
    wlSourceName.setText(
        BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.SourceName.Label"));
    PropsUi.setLook(wlSourceName);
    wlSourceName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wSourceName = new Combo(shell, SWT.BORDER | SWT.DROP_DOWN);
    PropsUi.setLook(wSourceName);
    wSourceName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());
    wSourceName.setToolTipText(
        BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.SourceName.ToolTip"));

    Label wlSourceField = new Label(shell, SWT.RIGHT);
    wlSourceField.setText(
        BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.SourceField.Label"));
    PropsUi.setLook(wlSourceField);
    wlSourceField.setLayoutData(
        new FormDataBuilder().left().top(wSourceName, margin).right(middle, -margin).result());

    wSourceField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceField);
    wSourceField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wSourceName, margin).right().result());
    wSourceField.setToolTipText(
        BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.SourceField.ToolTip"));

    wlStatus = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlStatus);
    wlStatus.setLayoutData(
        new FormDataBuilder().left().top(wSourceField, margin).right().result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("Hop"),
          col("Layer"),
          col("Model"),
          col("Table"),
          col("Type"),
          col("Target field"),
          col("Transform"),
          col("Reasons"),
          col("Path"),
        };
    wResults =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            columns,
            1,
            true,
            null,
            PropsUi.getInstance());
    wResults.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlStatus, margin)
            .right()
            .bottom(wClose, -2 * margin)
            .result());
    wResults.table.addListener(SWT.DefaultSelection, e -> openSelected());

    ModifyListener filterListener = e -> applyFilter();
    wSourceName.addModifyListener(filterListener);
    wSourceField.addModifyListener(filterListener);

    rebuildIndexAndFilter();

    BaseTransformDialog.setSize(shell, 980, 620);
    shell.open();
    while (!shell.isDisposed()) {
      if (!shell.getDisplay().readAndDispatch()) {
        shell.getDisplay().sleep();
      }
    }
  }

  private static ColumnInfo col(String title) {
    return new ColumnInfo(title, ColumnInfo.COLUMN_TYPE_TEXT, false, true);
  }

  private void rebuildIndexAndFilter() {
    try {
      ValidationModels models =
          ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
      index = ReverseLineageIndexBuilder.build(models, variables, metadataProvider);
      List<String> names = index.sourceNames();
      String previous = wSourceName.getText();
      wSourceName.setItems(names.toArray(new String[0]));
      if (!Utils.isEmpty(previous)) {
        wSourceName.setText(previous);
      }
      applyFilter();
      wlStatus.setText(
          BaseMessages.getString(
              PKG,
              "ReverseLineageBrowserDialog.Status.Ready",
              Integer.toString(index.size()),
              Integer.toString(names.size())));
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Error.Title"),
          BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Error.BuildIndex"),
          e);
      index = new ReverseLineageIndex();
      currentRows = List.of();
      populateTable(List.of());
      wlStatus.setText(BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Status.Error"));
    }
  }

  private void applyFilter() {
    if (index == null) {
      return;
    }
    currentRows = index.search(wSourceName.getText(), wSourceField.getText());
    populateTable(currentRows);
    wlStatus.setText(
        BaseMessages.getString(
            PKG,
            "ReverseLineageBrowserDialog.Status.Matches",
            Integer.toString(currentRows.size())));
  }

  private void populateTable(List<ReverseLineageConsumer> rows) {
    wResults.table.removeAll();
    if (rows == null || rows.isEmpty()) {
      wResults.setRowNums();
      return;
    }
    for (int i = 0; i < rows.size(); i++) {
      ReverseLineageConsumer row = rows.get(i);
      TableItem item = new TableItem(wResults.table, SWT.NONE);
      // Bind consumer to the item so open still works after the user sorts columns.
      item.setData(row);
      item.setText(1, Integer.toString(row.getHopCount()));
      item.setText(2, row.getLayer() != null ? row.getLayer().name() : "");
      item.setText(3, Const.NVL(row.getModelName(), ""));
      item.setText(4, Const.NVL(row.getTableName(), ""));
      item.setText(5, Const.NVL(row.getTableType(), ""));
      item.setText(6, Const.NVL(row.getTargetField(), ""));
      item.setText(7, Const.NVL(row.getTransform(), ""));
      item.setText(8, String.join("/", row.getReasonCodes()));
      item.setText(9, Const.NVL(row.getPathSummary(), ""));
    }
    wResults.setRowNums();
    wResults.optWidth(true);
  }

  private void openSelected() {
    TableItem[] selection = wResults.table.getSelection();
    if (selection == null || selection.length == 0) {
      return;
    }
    Object data = selection[0].getData();
    if (!(data instanceof ReverseLineageConsumer consumer)) {
      return;
    }
    try {
      SourceUsage usage =
          SourceUsage.builder()
              .modelType(mapLayerToUsageType(consumer.getLayer()))
              .modelName(consumer.getModelName())
              .modelFilename(consumer.getModelFilename())
              .modelElementName(consumer.getTableName())
              .mappedField(consumer.getTargetField())
              .build();
      ResourceDefinitionModelNavigationSupport.openUsage(hopGui, usage, variables);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Error.Title"),
          BaseMessages.getString(PKG, "ReverseLineageBrowserDialog.Error.OpenModel"),
          e);
    }
  }

  private static String mapLayerToUsageType(org.apache.hop.datavault.lineage.LineageLayer layer) {
    if (layer == null) {
      return SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT;
    }
    return switch (layer) {
      case BV -> SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT;
      case DM -> SourceUsageIndexBuilder.MODEL_TYPE_DIMENSIONAL;
      default -> SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT;
    };
  }
}
