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
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DrivingKeySource;
import org.apache.hop.datavault.metadata.DvBusinessKeyPartSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to edit the source field mappings for one hub within one DvLinkHubSource. Allows
 * specifying which source columns map to the hub's business keys and which source columns supply
 * any driving keys.
 */
public class HubSourceKeyFieldDialog {
  private static final Class<?> PKG = HubSourceKeyFieldDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink.HubSourceKeyField input;
  private final List<String> availableHubNames;
  private final DataVaultModel model;
  private final List<String> drivingKeyNames;
  private final DataVaultSource recordSource;

  private Shell shell;

  private Text wHubName;
  private TableView wBusinessKeySources;
  private TableView wDrivingKeySources;

  private boolean ok;

  public HubSourceKeyFieldDialog(
      Shell parent,
      HopGui hopGui,
      DvLink.HubSourceKeyField field,
      List<String> hubs,
      DataVaultModel model,
      List<String> drivingKeyNames,
      DataVaultSource recordSource) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = field;
    this.availableHubNames = (hubs != null) ? new ArrayList<>(hubs) : new ArrayList<>();
    this.model = model;
    this.drivingKeyNames =
        (drivingKeyNames != null) ? new ArrayList<>(drivingKeyNames) : new ArrayList<>();
    this.recordSource = recordSource;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText("Edit Hub Source Key Fields");

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    // Buttons at the bottom
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_HUB_SOURCE_KEY_FIELD);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    // Hub name (can be prefilled or chosen)
    Label wlHubName = new Label(shell, SWT.RIGHT);
    wlHubName.setText("Hub name");
    PropsUi.setLook(wlHubName);
    FormData fdlHubName = new FormData();
    fdlHubName.left = new FormAttachment(0, 0);
    fdlHubName.top = new FormAttachment(0, margin);
    fdlHubName.right = new FormAttachment(middle, -margin);
    wlHubName.setLayoutData(fdlHubName);

    wHubName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wHubName);
    FormData fdHubName = new FormData();
    fdHubName.left = new FormAttachment(middle, 0);
    fdHubName.top = new FormAttachment(0, margin);
    fdHubName.right = new FormAttachment(100, 0);
    wHubName.setLayoutData(fdHubName);
    // If we have known hubs and none set yet, leave blank for user; editing context usually
    // prefills

    // Business key sources section
    Label wlBks = new Label(shell, SWT.LEFT | SWT.WRAP);
    wlBks.setText(BaseMessages.getString(PKG, "HubSourceKeyFieldDialog.BusinessKeySources.Label"));
    PropsUi.setLook(wlBks);
    FormData fdlBks = new FormData();
    fdlBks.left = new FormAttachment(0, 0);
    fdlBks.top = new FormAttachment(wHubName, margin);
    fdlBks.right = new FormAttachment(100, 0);
    wlBks.setLayoutData(fdlBks);

    Label wlBksHint = new Label(shell, SWT.LEFT | SWT.WRAP);
    wlBksHint.setText(
        BaseMessages.getString(PKG, "HubSourceKeyFieldDialog.BusinessKeySources.Hint"));
    PropsUi.setLook(wlBksHint);
    FormData fdlBksHint = new FormData();
    fdlBksHint.left = new FormAttachment(0, 0);
    fdlBksHint.top = new FormAttachment(wlBks, margin / 2);
    fdlBksHint.right = new FormAttachment(100, 0);
    wlBksHint.setLayoutData(fdlBksHint);

    ColumnInfo[] bkCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HubSourceKeyFieldDialog.HubBusinessKey.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getBusinessKeyComboOptions()),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HubSourceKeyFieldDialog.SourceField.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getSourceFieldComboOptions()),
        };

    int nrBk = Math.max(1, countBusinessKeySourceRowsForDisplay());
    wBusinessKeySources =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            bkCols,
            nrBk,
            e -> {},
            PropsUi.getInstance());

    FormData fdBks = new FormData();
    fdBks.left = new FormAttachment(0, 0);
    fdBks.top = new FormAttachment(wlBksHint, margin);
    fdBks.right = new FormAttachment(100, 0);
    fdBks.bottom = new FormAttachment(50, -margin);
    wBusinessKeySources.setLayoutData(fdBks);

    // Driving key sources section
    Label wlDks = new Label(shell, SWT.LEFT);
    wlDks.setText("Driving key source fields (driving key -> source column)");
    PropsUi.setLook(wlDks);
    FormData fdlDks = new FormData();
    fdlDks.left = new FormAttachment(0, 0);
    fdlDks.top = new FormAttachment(wBusinessKeySources, margin);
    wlDks.setLayoutData(fdlDks);

    ColumnInfo[] dkCols =
        new ColumnInfo[] {
          new ColumnInfo("Driving key", ColumnInfo.COLUMN_TYPE_CCOMBO, getDrivingKeyComboOptions()),
          new ColumnInfo(
              "Source field name", ColumnInfo.COLUMN_TYPE_CCOMBO, getSourceFieldComboOptions()),
        };

    int nrDk =
        (input.getDrivingKeySources() != null)
            ? Math.max(1, input.getDrivingKeySources().size())
            : 2;
    wDrivingKeySources =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            dkCols,
            nrDk,
            e -> {},
            PropsUi.getInstance());

    FormData fdDks = new FormData();
    fdDks.left = new FormAttachment(0, 0);
    fdDks.top = new FormAttachment(wlDks, margin);
    fdDks.right = new FormAttachment(100, 0);
    fdDks.bottom = new FormAttachment(wOk, -2 * margin);
    wDrivingKeySources.setLayoutData(fdDks);

    getData();

    BaseTransformDialog.setSize(shell, 600, 450);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  private String[] getBusinessKeyComboOptions() {
    List<String> options = new ArrayList<>();
    String hubName = input != null ? input.getHubName() : null;
    if (model != null && !Utils.isEmpty(hubName)) {
      DvHub hub = model.findHub(hubName);
      if (hub != null && hub.getBusinessKeyFieldNames() != null) {
        options.addAll(hub.getBusinessKeyFieldNames());
      }
    }
    if (input.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource mapping : input.getSourceBusinessKeyFields()) {
        if (mapping != null
            && !Utils.isEmpty(mapping.getBusinessKeyField())
            && !options.contains(mapping.getBusinessKeyField())) {
          options.add(mapping.getBusinessKeyField());
        }
      }
    }
    Collections.sort(options);
    return options.toArray(new String[0]);
  }

  private String[] getDrivingKeyComboOptions() {
    List<String> options = new ArrayList<>(drivingKeyNames);
    if (input.getDrivingKeySources() != null) {
      for (DrivingKeySource mapping : input.getDrivingKeySources()) {
        if (mapping != null
            && !Utils.isEmpty(mapping.getDrivingKey())
            && !options.contains(mapping.getDrivingKey())) {
          options.add(mapping.getDrivingKey());
        }
      }
    }
    Collections.sort(options);
    return options.toArray(new String[0]);
  }

  private int countBusinessKeySourceRowsForDisplay() {
    if (input.getSourceBusinessKeyFields() == null) {
      return 2;
    }
    int count = 0;
    for (BusinessKeySource mapping : input.getSourceBusinessKeyFields()) {
      if (mapping == null) {
        continue;
      }
      List<String> parts = mapping.resolveSourceParts();
      count += Math.max(1, parts.size());
    }
    return Math.max(1, count);
  }

  private String[] getSourceFieldComboOptions() {
    List<String> options = new ArrayList<>(loadRecordSourceFieldNames());
    if (input.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource mapping : input.getSourceBusinessKeyFields()) {
        if (mapping == null) {
          continue;
        }
        for (String part : mapping.resolveSourceParts()) {
          if (!Utils.isEmpty(part) && !options.contains(part)) {
            options.add(part);
          }
        }
      }
    }
    if (input.getDrivingKeySources() != null) {
      for (DrivingKeySource mapping : input.getDrivingKeySources()) {
        if (mapping != null
            && !Utils.isEmpty(mapping.getSourceField())
            && !options.contains(mapping.getSourceField())) {
          options.add(mapping.getSourceField());
        }
      }
    }
    Collections.sort(options);
    return options.toArray(new String[0]);
  }

  private List<String> loadRecordSourceFieldNames() {
    if (recordSource == null) {
      return List.of();
    }
    try {
      List<SourceField> sourceFields = recordSource.getFields(hopGui.getMetadataProvider());
      if (sourceFields == null || sourceFields.isEmpty()) {
        return List.of();
      }
      List<String> names = new ArrayList<>();
      for (SourceField sourceField : sourceFields) {
        if (sourceField != null && !Utils.isEmpty(sourceField.getName())) {
          names.add(sourceField.getName());
        }
      }
      Collections.sort(names);
      return names;
    } catch (HopException e) {
      return List.of();
    }
  }

  private void getData() {
    if (input.getHubName() != null) {
      wHubName.setText(input.getHubName());
    }

    // Business key sources: expand composite multi-part mappings to one row per source part
    wBusinessKeySources.clearAll();
    if (input.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource bs : input.getSourceBusinessKeyFields()) {
        if (bs == null) {
          continue;
        }
        List<String> parts = bs.resolveSourceParts();
        if (parts.isEmpty()) {
          TableItem item = new TableItem(wBusinessKeySources.table, SWT.NONE);
          item.setText(1, Const.NVL(bs.getBusinessKeyField(), ""));
          item.setText(2, "");
        } else {
          for (String part : parts) {
            TableItem item = new TableItem(wBusinessKeySources.table, SWT.NONE);
            item.setText(1, Const.NVL(bs.getBusinessKeyField(), ""));
            item.setText(2, Const.NVL(part, ""));
          }
        }
      }
    }
    wBusinessKeySources.optimizeTableView();
    // Driving key sources
    wDrivingKeySources.clearAll();
    if (input.getDrivingKeySources() != null) {
      for (DrivingKeySource ds : input.getDrivingKeySources()) {
        TableItem item = new TableItem(wDrivingKeySources.table, SWT.NONE);
        item.setText(1, Const.NVL(ds.getDrivingKey(), ""));
        item.setText(2, Const.NVL(ds.getSourceField(), ""));
      }
    }
    wDrivingKeySources.optimizeTableView();
  }

  private void ok() {
    input.setHubName(wHubName.getText());

    // Read business key sources table; group rows with the same hub BK (composite parts)
    Map<String, List<String>> partsByBusinessKey = new LinkedHashMap<>();
    for (TableItem item : wBusinessKeySources.getNonEmptyItems()) {
      String businessKeyField = Const.NVL(item.getText(1), "").trim();
      String sourceField = Const.NVL(item.getText(2), "").trim();
      if (Utils.isEmpty(businessKeyField) && Utils.isEmpty(sourceField)) {
        continue;
      }
      // Allow comma-separated multi-part in a single cell as well
      List<String> parts = BusinessKeySourceFieldUiSupport.parseSourceFields(sourceField);
      if (parts.isEmpty() && !Utils.isEmpty(sourceField)) {
        parts = List.of(sourceField);
      }
      List<String> grouped =
          partsByBusinessKey.computeIfAbsent(businessKeyField, k -> new ArrayList<>());
      for (String part : parts) {
        if (!Utils.isEmpty(part)) {
          grouped.add(part);
        }
      }
      if (parts.isEmpty()) {
        partsByBusinessKey.putIfAbsent(businessKeyField, new ArrayList<>());
      }
    }

    List<BusinessKeySource> bks = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : partsByBusinessKey.entrySet()) {
      BusinessKeySource bs = new BusinessKeySource();
      bs.setBusinessKeyField(entry.getKey());
      List<String> parts = entry.getValue();
      boolean compositeOnHub = isCompositeHubBusinessKey(entry.getKey());
      if (parts.size() > 1 || compositeOnHub) {
        bs.setSourceFieldNames(new ArrayList<>(parts));
        bs.setSourceFieldName(parts.isEmpty() ? null : parts.get(0));
      } else if (parts.size() == 1) {
        bs.setSourceFieldNames(new ArrayList<>());
        bs.setSourceFieldName(parts.get(0));
      }
      if (!Utils.isEmpty(bs.getBusinessKeyField()) || !bs.resolveSourceParts().isEmpty()) {
        bks.add(bs);
      }
    }
    input.setSourceBusinessKeyFields(bks);

    // Read driving key sources table
    List<DrivingKeySource> dks = new ArrayList<>();
    for (TableItem item : wDrivingKeySources.getNonEmptyItems()) {
      DrivingKeySource ds = new DrivingKeySource();
      ds.setDrivingKey(item.getText(1));
      ds.setSourceField(item.getText(2));
      if (!Utils.isEmpty(ds.getDrivingKey()) || !Utils.isEmpty(ds.getSourceField())) {
        dks.add(ds);
      }
    }
    input.setDrivingKeySources(dks);

    ok = true;
    dispose();
  }

  private boolean isCompositeHubBusinessKey(String businessKeyField) {
    if (Utils.isEmpty(businessKeyField) || model == null) {
      return false;
    }
    String hubName = Const.NVL(wHubName != null ? wHubName.getText() : input.getHubName(), "");
    if (Utils.isEmpty(hubName)) {
      return false;
    }
    DvHub hub = model.findHub(hubName, variables, hopGui.getMetadataProvider());
    if (hub == null) {
      return false;
    }
    for (BusinessKey bk : hub.getDistinctBusinessKeys()) {
      if (bk != null && businessKeyField.equals(bk.getName()) && bk.isComposite()) {
        return true;
      }
    }
    return DvBusinessKeyPartSupport.hubHasCompositeBusinessKey(hub)
        && hub.getBusinessKeyFieldNames().contains(businessKeyField);
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
