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
 *
 */

package org.apache.hop.datavault.hopgui.file.vault;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvIntegrationMode;
import org.apache.hop.datavault.metadata.DvTableBase;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Shared "Custom pipelines" tab for hub, satellite, and link table editors. */
public final class DvCustomPipelinesTabSupport {

  private static final Class<?> PKG = DvCustomPipelinesTabSupport.class;

  private final Shell shell;
  private final HopGui hopGui;
  private final IVariables variables;
  private final int margin;

  private Label wlPaths;
  private Button wSelectPipelines;
  private Button wOpenSelected;
  private TableView wCustomPipelines;

  public DvCustomPipelinesTabSupport(
      Shell shell, HopGui hopGui, IVariables variables, int margin) {
    this.shell = shell;
    this.hopGui = hopGui;
    this.variables = variables;
    this.margin = margin;
  }

  public void addTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.Tab.Label"));
    tab.setToolTipText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.Tab.ToolTip"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    wlPaths = new Label(comp, SWT.LEFT);
    wlPaths.setText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.Paths.Label"));
    PropsUi.setLook(wlPaths);
    wlPaths.setLayoutData(new FormDataBuilder().left().top(0, margin).result());

    wOpenSelected = new Button(comp, SWT.PUSH);
    wOpenSelected.setText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.OpenSelected.Button"));
    wOpenSelected.setToolTipText(
        BaseMessages.getString(PKG, "DvCustomPipelinesTab.OpenSelected.ToolTip"));
    PropsUi.setLook(wOpenSelected);
    wOpenSelected.setLayoutData(new FormDataBuilder().right().bottom().result());
    wOpenSelected.addListener(SWT.Selection, e -> openSelectedPipelines());

    wSelectPipelines = new Button(comp, SWT.PUSH);
    wSelectPipelines.setText(
        BaseMessages.getString(PKG, "DvCustomPipelinesTab.SelectPipelines.Button"));
    wSelectPipelines.setToolTipText(
        BaseMessages.getString(PKG, "DvCustomPipelinesTab.SelectPipelines.ToolTip"));
    PropsUi.setLook(wSelectPipelines);
    wSelectPipelines.setLayoutData(
        new FormDataBuilder().right(wOpenSelected, -margin).bottom().result());
    wSelectPipelines.addListener(SWT.Selection, e -> selectPipelines());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "DvCustomPipelinesTab.Path.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    wCustomPipelines =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());

    wCustomPipelines.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlPaths, margin)
            .right()
            .bottom(wSelectPipelines, -margin)
            .result());
  }

  /** Keep tab controls in sync with the integration mode combo on the parent dialog. */
  public void bindIntegrationMode(Combo wIntegrationMode) {
    if (wIntegrationMode == null || wIntegrationMode.isDisposed()) {
      return;
    }
    wIntegrationMode.addListener(SWT.Selection, e -> updateEnabled(wIntegrationMode.getText()));
    updateEnabled(wIntegrationMode.getText());
  }

  public void updateEnabled(String integrationModeDescription) {
    setControlsEnabled(isCustomPipelinesIntegrationMode(integrationModeDescription));
  }

  static boolean isCustomPipelinesIntegrationMode(String integrationModeDescription) {
    return DvIntegrationMode.lookupDescription(integrationModeDescription)
        == DvIntegrationMode.CUSTOM_PIPELINES;
  }

  private void setControlsEnabled(boolean enabled) {
    setControlEnabled(wlPaths, enabled);
    setControlEnabled(wSelectPipelines, enabled);
    setControlEnabled(wOpenSelected, enabled);
    if (wCustomPipelines != null && !wCustomPipelines.isDisposed()) {
      wCustomPipelines.setEnabled(enabled);
    }
  }

  private static void setControlEnabled(Control control, boolean enabled) {
    if (control != null && !control.isDisposed()) {
      control.setEnabled(enabled);
    }
  }

  public void loadFrom(DvTableBase table) {
    if (wCustomPipelines == null || wCustomPipelines.isDisposed() || table == null) {
      return;
    }
    wCustomPipelines.clearAll();
    List<String> paths = table.getCustomUpdatePipelinePaths();
    if (paths != null) {
      for (String path : paths) {
        if (!Utils.isEmpty(path)) {
          TableItem item = new TableItem(wCustomPipelines.table, SWT.NONE);
          item.setText(1, path);
        }
      }
    }
    wCustomPipelines.optimizeTableView();
  }

  public void applyTo(DvTableBase table) {
    if (table == null || wCustomPipelines == null || wCustomPipelines.isDisposed()) {
      return;
    }
    List<String> paths = new ArrayList<>();
    for (TableItem item : wCustomPipelines.getNonEmptyItems()) {
      String path = item.getText(1);
      if (!Utils.isEmpty(path)) {
        paths.add(path.trim());
      }
    }
    table.setCustomUpdatePipelinePaths(paths);
  }

  private void selectPipelines() {
    FileDialog fileDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
    fileDialog.setText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.SelectPipelines.Button"));
    fileDialog.setFilterExtensions(new String[] {"*.hpl"});
    fileDialog.setFilterNames(
        new String[] {BaseMessages.getString(PKG, "DvCustomPipelinesTab.PipelineFile.Filter")});
    if (fileDialog.open() == null) {
      return;
    }
    String filterPath = fileDialog.getFilterPath();
    String[] fileNames = fileDialog.getFileNames();
    if (fileNames == null || fileNames.length == 0) {
      return;
    }
    Set<String> existing = new LinkedHashSet<>();
    for (TableItem item : wCustomPipelines.getNonEmptyItems()) {
      String path = item.getText(1);
      if (!Utils.isEmpty(path)) {
        existing.add(path);
      }
    }
    for (String fileName : fileNames) {
      if (Utils.isEmpty(fileName)) {
        continue;
      }
      String path =
          Utils.isEmpty(filterPath)
              ? fileName
              : new File(filterPath, fileName).getAbsolutePath();
      if (!existing.contains(path)) {
        TableItem item = new TableItem(wCustomPipelines.table, SWT.NONE);
        item.setText(1, path);
        existing.add(path);
      }
    }
    wCustomPipelines.optimizeTableView();
  }

  private void openSelectedPipelines() {
    List<String> selectedPaths = getSelectedPipelinePaths();
    if (selectedPaths.isEmpty()) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setText(BaseMessages.getString(PKG, "DvCustomPipelinesTab.Error.NoSelection.Title"));
      mb.setMessage(BaseMessages.getString(PKG, "DvCustomPipelinesTab.Error.NoSelection.Message"));
      mb.open();
      return;
    }
    DvCustomPipelineOpenSupport.openPipelinePaths(hopGui, shell, variables, selectedPaths);
  }

  private List<String> getSelectedPipelinePaths() {
    List<String> paths = new ArrayList<>();
    if (wCustomPipelines == null || wCustomPipelines.isDisposed()) {
      return paths;
    }
    int[] selection = wCustomPipelines.table.getSelectionIndices();
    if (selection == null || selection.length == 0) {
      return paths;
    }
    for (int index : selection) {
      if (index >= 0 && index < wCustomPipelines.table.getItemCount()) {
        String path = wCustomPipelines.table.getItem(index).getText(1);
        if (!Utils.isEmpty(path)) {
          paths.add(path);
        }
      }
    }
    return paths;
  }
}