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
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.AttributeSource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DrivingKeySource;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
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
 * Dialog to edit the source field mappings for one link satellite within one DvLinkSatelliteSource.
 */
public class SatelliteSourceKeyFieldDialog {
  private static final Class<?> PKG = SatelliteSourceKeyFieldDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DvLink.SatelliteSourceKeyField input;
  private final DataVaultModel model;
  private final List<String> drivingKeyNames;
  private final DataVaultSource recordSource;

  private Shell shell;

  private Text wSatelliteName;
  private TableView wAttributeSources;
  private TableView wDrivingKeySources;

  private boolean ok;

  public SatelliteSourceKeyFieldDialog(
      Shell parent,
      HopGui hopGui,
      DvLink.SatelliteSourceKeyField field,
      DataVaultModel model,
      List<String> drivingKeyNames,
      DataVaultSource recordSource) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = field;
    this.model = model;
    this.drivingKeyNames =
        (drivingKeyNames != null) ? new ArrayList<>(drivingKeyNames) : new ArrayList<>();
    this.recordSource = recordSource;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText("Edit Link Satellite Source Key Fields");

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_SATELLITE_SOURCE_KEY_FIELD);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Label wlSatelliteName = new Label(shell, SWT.RIGHT);
    wlSatelliteName.setText("Link satellite name");
    PropsUi.setLook(wlSatelliteName);
    FormData fdlSatelliteName = new FormData();
    fdlSatelliteName.left = new FormAttachment(0, 0);
    fdlSatelliteName.top = new FormAttachment(0, margin);
    fdlSatelliteName.right = new FormAttachment(middle, -margin);
    wlSatelliteName.setLayoutData(fdlSatelliteName);

    wSatelliteName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSatelliteName);
    FormData fdSatelliteName = new FormData();
    fdSatelliteName.left = new FormAttachment(middle, 0);
    fdSatelliteName.top = new FormAttachment(0, margin);
    fdSatelliteName.right = new FormAttachment(100, 0);
    wSatelliteName.setLayoutData(fdSatelliteName);

    Label wlAttrs = new Label(shell, SWT.LEFT);
    wlAttrs.setText("Attribute source fields (satellite attribute -> source column)");
    PropsUi.setLook(wlAttrs);
    FormData fdlAttrs = new FormData();
    fdlAttrs.left = new FormAttachment(0, 0);
    fdlAttrs.top = new FormAttachment(wSatelliteName, margin);
    wlAttrs.setLayoutData(fdlAttrs);

    ColumnInfo[] attrCols =
        new ColumnInfo[] {
          new ColumnInfo(
              "Satellite attribute field",
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getAttributeComboOptions()),
          new ColumnInfo(
              "Source field name", ColumnInfo.COLUMN_TYPE_CCOMBO, getSourceFieldComboOptions()),
        };

    int nrAttr =
        (input.getAttributeSources() != null) ? Math.max(1, input.getAttributeSources().size()) : 2;
    wAttributeSources =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            attrCols,
            nrAttr,
            e -> {},
            PropsUi.getInstance());

    FormData fdAttrs = new FormData();
    fdAttrs.left = new FormAttachment(0, 0);
    fdAttrs.top = new FormAttachment(wlAttrs, margin);
    fdAttrs.right = new FormAttachment(100, 0);
    fdAttrs.bottom = new FormAttachment(50, -margin);
    wAttributeSources.setLayoutData(fdAttrs);

    Label wlDks = new Label(shell, SWT.LEFT);
    wlDks.setText("Driving key source fields (driving key -> source column)");
    PropsUi.setLook(wlDks);
    FormData fdlDks = new FormData();
    fdlDks.left = new FormAttachment(0, 0);
    fdlDks.top = new FormAttachment(wAttributeSources, margin);
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

  private String[] getAttributeComboOptions() {
    List<String> options = new ArrayList<>();
    String satelliteName = input != null ? input.getSatelliteName() : null;
    if (model != null && !Utils.isEmpty(satelliteName)) {
      IDvTable table = model.findTable(satelliteName);
      if (table != null && table.getTableType() == DvTableType.SATELLITE) {
        DvSatellite satellite = (DvSatellite) table;
        if (satellite.getAttributes() != null) {
          for (SatelliteAttribute attribute : satellite.getAttributes()) {
            if (attribute != null && !Utils.isEmpty(attribute.getName())) {
              options.add(attribute.getName());
            }
          }
        }
      }
    }
    if (input.getAttributeSources() != null) {
      for (AttributeSource mapping : input.getAttributeSources()) {
        if (mapping != null
            && !Utils.isEmpty(mapping.getAttributeField())
            && !options.contains(mapping.getAttributeField())) {
          options.add(mapping.getAttributeField());
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

  private String[] getSourceFieldComboOptions() {
    List<String> options = new ArrayList<>(loadRecordSourceFieldNames());
    if (input.getAttributeSources() != null) {
      for (AttributeSource mapping : input.getAttributeSources()) {
        if (mapping != null
            && !Utils.isEmpty(mapping.getSourceFieldName())
            && !options.contains(mapping.getSourceFieldName())) {
          options.add(mapping.getSourceFieldName());
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
    if (input.getSatelliteName() != null) {
      wSatelliteName.setText(input.getSatelliteName());
    }

    wAttributeSources.clearAll();
    if (input.getAttributeSources() != null) {
      for (AttributeSource as : input.getAttributeSources()) {
        TableItem item = new TableItem(wAttributeSources.table, SWT.NONE);
        item.setText(1, Const.NVL(as.getAttributeField(), ""));
        item.setText(2, Const.NVL(as.getSourceFieldName(), ""));
      }
    }
    wAttributeSources.optimizeTableView();
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
    input.setSatelliteName(wSatelliteName.getText());

    List<AttributeSource> attrs = new ArrayList<>();
    for (TableItem item : wAttributeSources.getNonEmptyItems()) {
      AttributeSource as = new AttributeSource();
      as.setAttributeField(item.getText(1));
      as.setSourceFieldName(item.getText(2));
      if (!Utils.isEmpty(as.getAttributeField()) || !Utils.isEmpty(as.getSourceFieldName())) {
        attrs.add(as);
      }
    }
    input.setAttributeSources(attrs);

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
