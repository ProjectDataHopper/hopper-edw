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
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/**
 * Dialog to edit a single DvLinkHubSource entry (one record source feeding the link) and its
 * per-hub source key field mappings (and driving key sources). Launches HubSourceKeyFieldDialog for
 * the detailed per-hub editing.
 */
public class DvLinkHubSourceDialog {
  private static final Class<?> PKG = DvLinkHubSourceDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink.DvLinkHubSource input;
  private final List<String> availableHubNames;
  private final DataVaultModel model;
  private final List<String> drivingKeyNames;

  private Shell shell;

  private DvCatalogSourceSelectionLine wSource;
  private TableView wHubMappings;

  private List<DvLink.HubSourceKeyField> currentHubFields;

  private boolean ok;

  public DvLinkHubSourceDialog(
      Shell parent,
      HopGui hopGui,
      DvLink.DvLinkHubSource linkSource,
      List<String> hubNames,
      DataVaultModel model,
      List<String> drivingKeyNames) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = linkSource;
    this.availableHubNames = (hubNames != null) ? new ArrayList<>(hubNames) : new ArrayList<>();
    this.model = model;
    this.drivingKeyNames =
        (drivingKeyNames != null) ? new ArrayList<>(drivingKeyNames) : new ArrayList<>();
    this.currentHubFields = new ArrayList<>();
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        "Edit Link Source"
            + (getSourceNameForTitle().isEmpty() ? "" : " - " + getSourceNameForTitle()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at bottom
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());

    Button wAddHub = new Button(shell, SWT.PUSH);
    wAddHub.setText("Add hub mapping");
    wAddHub.addListener(SWT.Selection, e -> addHubMapping());

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_LINK_HUB_SOURCE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wAddHub, wCancel}, margin, null);

    // Source selection
    wSource =
        new DvCatalogSourceSelectionLine(
            variables,
            hopGui.getMetadataProvider(),
            model,
            shell,
            SWT.BORDER,
            "Data Vault Source",
            "The record source (Data Vault Source) that feeds data into this link");
    FormData fdSource = new FormData();
    fdSource.left = new FormAttachment(0, 0);
    fdSource.top = new FormAttachment(0, margin);
    fdSource.right = new FormAttachment(100, 0);
    wSource.setLayoutData(fdSource);
    wSource.addModifyListener(e -> {});

    // Hub mappings label
    Label wlHubs = new Label(shell, SWT.LEFT);
    wlHubs.setText("Hub key field mappings (for each participating hub)");
    PropsUi.setLook(wlHubs);
    FormData fdlHubs = new FormData();
    fdlHubs.left = new FormAttachment(0, 0);
    fdlHubs.top = new FormAttachment(wSource, margin);
    wlHubs.setLayoutData(fdlHubs);

    // Small buttons row for hub mappings management
    Button wEditHub = new Button(shell, SWT.PUSH);
    wEditHub.setText("Edit mappings");
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(0, 0);
    fdEdit.top = new FormAttachment(wlHubs, margin);
    wEditHub.setLayoutData(fdEdit);
    wEditHub.addListener(SWT.Selection, e -> editHubMapping());

    Button wRemoveHub = new Button(shell, SWT.PUSH);
    wRemoveHub.setText("Remove");
    FormData fdRemove = new FormData();
    fdRemove.left = new FormAttachment(wEditHub, margin);
    fdRemove.top = new FormAttachment(wlHubs, margin);
    wRemoveHub.setLayoutData(fdRemove);
    wRemoveHub.addListener(SWT.Selection, e -> removeHubMapping());

    ColumnInfo[] hubCols =
        new ColumnInfo[] {
          new ColumnInfo("Hub name", ColumnInfo.COLUMN_TYPE_CCOMBO, getHubComboOptions()),
        };

    int nrRows = 3;
    wHubMappings =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            hubCols,
            nrRows,
            e -> {},
            PropsUi.getInstance());

    FormData fdHubs = new FormData();
    fdHubs.left = new FormAttachment(0, 0);
    fdHubs.top = new FormAttachment(wRemoveHub, margin);
    fdHubs.right = new FormAttachment(100, 0);
    fdHubs.bottom = new FormAttachment(wOk, -2 * margin);
    wHubMappings.setLayoutData(fdHubs);

    getData();

    BaseTransformDialog.setSize(shell, 600, 450);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private String[] getHubComboOptions() {
    List<String> options = new ArrayList<>(availableHubNames);
    if (input.getHubSourceKeyFields() != null) {
      for (DvLink.HubSourceKeyField field : input.getHubSourceKeyFields()) {
        if (field != null
            && !Utils.isEmpty(field.getHubName())
            && !options.contains(field.getHubName())) {
          options.add(field.getHubName());
        }
      }
    }
    Collections.sort(options);
    return options.toArray(new String[0]);
  }

  private String getSourceNameForTitle() {
    if (input != null && !Utils.isEmpty(input.getSourceName())) {
      return input.getSourceName();
    }
    return "";
  }

  private void getData() {
    // Source
    try {
      wSource.fillItems();
      if (!Utils.isEmpty(input.getSourceName())) {
        wSource.setText(input.getSourceName());
      }
    } catch (HopException e) {
      // ignore, leave blank
    }

    currentHubFields.clear();
    wHubMappings.clearAll();

    if (input.getHubSourceKeyFields() != null) {
      for (DvLink.HubSourceKeyField f : input.getHubSourceKeyFields()) {
        if (f != null) {
          currentHubFields.add(f);
          TableItem item = new TableItem(wHubMappings.table, SWT.NONE);
          item.setText(1, Const.NVL(f.getHubName(), ""));
        }
      }
    }
    wHubMappings.optimizeTableView();
  }

  private void addHubMapping() {
    String hubToAdd = null;

    if (availableHubNames != null && !availableHubNames.isEmpty()) {
      String[] choices = availableHubNames.toArray(new String[0]);
      EnterSelectionDialog dlg =
          new EnterSelectionDialog(
              shell,
              choices,
              "Select hub",
              "Select the hub for which to provide source key field mappings");
      hubToAdd = dlg.open();
      if (hubToAdd == null) {
        return;
      }
      // prevent dups in table
      for (TableItem ti : wHubMappings.getNonEmptyItems()) {
        if (hubToAdd.equalsIgnoreCase(ti.getText(1))) {
          // already there, just edit it
          editHubForName(hubToAdd);
          return;
        }
      }
    } else {
      // no known hubs, just add empty for user to type
      TableItem item = new TableItem(wHubMappings.table, SWT.NONE);
      wHubMappings.optimizeTableView();
      return;
    }

    TableItem item = new TableItem(wHubMappings.table, SWT.NONE);
    item.setText(1, Const.NVL(hubToAdd, ""));
    // ensure pojo exists in pool
    findOrCreateHubField(hubToAdd);
    wHubMappings.optimizeTableView();
  }

  private void editHubMapping() {
    TableItem[] sels = wHubMappings.table.getSelection();
    if (sels == null || sels.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage("Please select a hub in the table first.");
      mb.setText("Edit hub mappings");
      mb.open();
      return;
    }
    String hubName = sels[0].getText(1);
    if (Utils.isEmpty(hubName)) {
      return;
    }
    editHubForName(hubName);
  }

  private void editHubForName(String hubName) {
    DvLink.HubSourceKeyField field = findOrCreateHubField(hubName);
    HubSourceKeyFieldDialog dlg =
        new HubSourceKeyFieldDialog(
            shell,
            hopGui,
            field,
            availableHubNames,
            model,
            drivingKeyNames,
            getCurrentRecordSource());
    dlg.open();
  }

  private DataVaultSource getCurrentRecordSource() {
    try {
      return wSource.resolveSelectedSource();
    } catch (HopException e) {
      DataVaultSource placeholder = new DataVaultSource();
      placeholder.setName(wSource.getText());
      return placeholder;
    }
  }

  private DvLink.HubSourceKeyField findOrCreateHubField(String hubName) {
    for (DvLink.HubSourceKeyField f : currentHubFields) {
      if (hubName != null && hubName.equals(f.getHubName())) {
        return f;
      }
    }
    DvLink.HubSourceKeyField nf = new DvLink.HubSourceKeyField();
    nf.setHubName(hubName);
    currentHubFields.add(nf);
    return nf;
  }

  private void removeHubMapping() {
    int idx = wHubMappings.getSelectionIndex();
    if (idx >= 0) {
      wHubMappings.table.remove(idx);
      wHubMappings.optimizeTableView();
    }
  }

  private void ok() {
    // Source
    String srcName = wSource.getText();
    if (!Utils.isEmpty(srcName)) {
      input.setSourceName(srcName);
    } else {
      input.setSourceName(null);
    }

    // Rebuild hub key fields list from table + current edited details
    List<DvLink.HubSourceKeyField> result = new ArrayList<>();
    for (TableItem item : wHubMappings.getNonEmptyItems()) {
      String h = item.getText(1);
      if (Utils.isEmpty(h)) {
        continue;
      }
      DvLink.HubSourceKeyField match = null;
      for (DvLink.HubSourceKeyField cf : currentHubFields) {
        if (h.equals(cf.getHubName())) {
          match = cf;
          break;
        }
      }
      if (match == null) {
        match = new DvLink.HubSourceKeyField();
        match.setHubName(h);
      }
      result.add(match);
    }
    input.setHubSourceKeyFields(result);

    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
