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
 * Dialog to edit a single DvLinkSatelliteSource entry and its per-satellite source key field
 * mappings.
 */
public class DvLinkSatelliteSourceDialog {
  private static final Class<?> PKG = DvLinkSatelliteSourceDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink.DvLinkSatelliteSource input;
  private final List<String> availableSatelliteNames;
  private final DataVaultModel model;
  private final List<String> drivingKeyNames;

  private Shell shell;

  private DvCatalogSourceSelectionLine wSource;
  private TableView wSatelliteMappings;

  private List<DvLink.SatelliteSourceKeyField> currentSatelliteFields;

  private boolean ok;

  public DvLinkSatelliteSourceDialog(
      Shell parent,
      HopGui hopGui,
      DvLink.DvLinkSatelliteSource linkSatelliteSource,
      List<String> satelliteNames,
      DataVaultModel model,
      List<String> drivingKeyNames) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = linkSatelliteSource;
    this.availableSatelliteNames =
        (satelliteNames != null) ? new ArrayList<>(satelliteNames) : new ArrayList<>();
    this.model = model;
    this.drivingKeyNames =
        (drivingKeyNames != null) ? new ArrayList<>(drivingKeyNames) : new ArrayList<>();
    this.currentSatelliteFields = new ArrayList<>();
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        "Edit Link Satellite Source"
            + (getSourceNameForTitle().isEmpty() ? "" : " - " + getSourceNameForTitle()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());

    Button wAddSatellite = new Button(shell, SWT.PUSH);
    wAddSatellite.setText("Add satellite mapping");
    wAddSatellite.addListener(SWT.Selection, e -> addSatelliteMapping());

    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_LINK_SATELLITE_SOURCE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wAddSatellite, wCancel}, margin, null);

    wSource =
        new DvCatalogSourceSelectionLine(
            variables,
            hopGui.getMetadataProvider(),
            model,
            shell,
            SWT.BORDER,
            "Data Vault Source",
            "The record source that feeds link satellite attributes");
    FormData fdSource = new FormData();
    fdSource.left = new FormAttachment(0, 0);
    fdSource.top = new FormAttachment(0, margin);
    fdSource.right = new FormAttachment(100, 0);
    wSource.setLayoutData(fdSource);

    Label wlSats = new Label(shell, SWT.LEFT);
    wlSats.setText("Link satellite attribute field mappings");
    PropsUi.setLook(wlSats);
    FormData fdlSats = new FormData();
    fdlSats.left = new FormAttachment(0, 0);
    fdlSats.top = new FormAttachment(wSource, margin);
    wlSats.setLayoutData(fdlSats);

    Button wEditSatellite = new Button(shell, SWT.PUSH);
    wEditSatellite.setText("Edit mappings");
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(0, 0);
    fdEdit.top = new FormAttachment(wlSats, margin);
    wEditSatellite.setLayoutData(fdEdit);
    wEditSatellite.addListener(SWT.Selection, e -> editSatelliteMapping());

    Button wRemoveSatellite = new Button(shell, SWT.PUSH);
    wRemoveSatellite.setText("Remove");
    FormData fdRemove = new FormData();
    fdRemove.left = new FormAttachment(wEditSatellite, margin);
    fdRemove.top = new FormAttachment(wlSats, margin);
    wRemoveSatellite.setLayoutData(fdRemove);
    wRemoveSatellite.addListener(SWT.Selection, e -> removeSatelliteMapping());

    ColumnInfo[] satCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Link satellite name", ColumnInfo.COLUMN_TYPE_CCOMBO, getSatelliteComboOptions()),
        };

    wSatelliteMappings =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            satCols,
            3,
            e -> {},
            PropsUi.getInstance());

    FormData fdSats = new FormData();
    fdSats.left = new FormAttachment(0, 0);
    fdSats.top = new FormAttachment(wRemoveSatellite, margin);
    fdSats.right = new FormAttachment(100, 0);
    fdSats.bottom = new FormAttachment(wOk, -2 * margin);
    wSatelliteMappings.setLayoutData(fdSats);

    getData();

    BaseTransformDialog.setSize(shell, 600, 450);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private String[] getSatelliteComboOptions() {
    List<String> options = new ArrayList<>(availableSatelliteNames);
    if (input.getSatelliteSourceKeyFields() != null) {
      for (DvLink.SatelliteSourceKeyField field : input.getSatelliteSourceKeyFields()) {
        if (field != null
            && !Utils.isEmpty(field.getSatelliteName())
            && !options.contains(field.getSatelliteName())) {
          options.add(field.getSatelliteName());
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
    // Catalog source names load lazily on combo focus (wait cursor).
    wSource.setText(Const.NVL(input.getSourceName(), ""));

    currentSatelliteFields.clear();
    wSatelliteMappings.clearAll();

    if (input.getSatelliteSourceKeyFields() != null) {
      for (DvLink.SatelliteSourceKeyField f : input.getSatelliteSourceKeyFields()) {
        if (f != null) {
          currentSatelliteFields.add(f);
          TableItem item = new TableItem(wSatelliteMappings.table, SWT.NONE);
          item.setText(1, Const.NVL(f.getSatelliteName(), ""));
        }
      }
    }
    wSatelliteMappings.optimizeTableView();
  }

  private void addSatelliteMapping() {
    String satelliteToAdd = null;

    if (availableSatelliteNames != null && !availableSatelliteNames.isEmpty()) {
      String[] choices = availableSatelliteNames.toArray(new String[0]);
      EnterSelectionDialog dlg =
          new EnterSelectionDialog(
              shell,
              choices,
              "Select link satellite",
              "Select the link satellite for which to provide source attribute field mappings");
      satelliteToAdd = dlg.open();
      if (satelliteToAdd == null) {
        return;
      }
      for (TableItem ti : wSatelliteMappings.getNonEmptyItems()) {
        if (satelliteToAdd.equalsIgnoreCase(ti.getText(1))) {
          editSatelliteForName(satelliteToAdd);
          return;
        }
      }
    } else {
      TableItem item = new TableItem(wSatelliteMappings.table, SWT.NONE);
      wSatelliteMappings.optimizeTableView();
      return;
    }

    TableItem item = new TableItem(wSatelliteMappings.table, SWT.NONE);
    item.setText(1, Const.NVL(satelliteToAdd, ""));
    findOrCreateSatelliteField(satelliteToAdd);
    wSatelliteMappings.optimizeTableView();
  }

  private void editSatelliteMapping() {
    TableItem[] sels = wSatelliteMappings.table.getSelection();
    if (sels == null || sels.length == 0) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      mb.setMessage("Please select a link satellite in the table first.");
      mb.setText("Edit satellite mappings");
      mb.open();
      return;
    }
    String satelliteName = sels[0].getText(1);
    if (Utils.isEmpty(satelliteName)) {
      return;
    }
    editSatelliteForName(satelliteName);
  }

  private void editSatelliteForName(String satelliteName) {
    DvLink.SatelliteSourceKeyField field = findOrCreateSatelliteField(satelliteName);
    SatelliteSourceKeyFieldDialog dlg =
        new SatelliteSourceKeyFieldDialog(
            shell, hopGui, field, model, drivingKeyNames, getCurrentRecordSource());
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

  private DvLink.SatelliteSourceKeyField findOrCreateSatelliteField(String satelliteName) {
    for (DvLink.SatelliteSourceKeyField f : currentSatelliteFields) {
      if (satelliteName != null && satelliteName.equals(f.getSatelliteName())) {
        return f;
      }
    }
    DvLink.SatelliteSourceKeyField nf = new DvLink.SatelliteSourceKeyField();
    nf.setSatelliteName(satelliteName);
    currentSatelliteFields.add(nf);
    return nf;
  }

  private void removeSatelliteMapping() {
    int idx = wSatelliteMappings.getSelectionIndex();
    if (idx >= 0) {
      wSatelliteMappings.table.remove(idx);
      wSatelliteMappings.optimizeTableView();
    }
  }

  private void ok() {
    String srcName = wSource.getText();
    if (!Utils.isEmpty(srcName)) {
      input.setSourceName(srcName);
    } else {
      input.setSourceName(null);
    }

    List<DvLink.SatelliteSourceKeyField> result = new ArrayList<>();
    for (TableItem item : wSatelliteMappings.getNonEmptyItems()) {
      String s = item.getText(1);
      if (Utils.isEmpty(s)) {
        continue;
      }
      DvLink.SatelliteSourceKeyField match = null;
      for (DvLink.SatelliteSourceKeyField cf : currentSatelliteFields) {
        if (s.equals(cf.getSatelliteName())) {
          match = cf;
          break;
        }
      }
      if (match == null) {
        match = new DvLink.SatelliteSourceKeyField();
        match.setSatelliteName(s);
      }
      result.add(match);
    }
    input.setSatelliteSourceKeyFields(result);

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
