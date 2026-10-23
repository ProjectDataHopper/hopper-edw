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
package org.hopper.edw.datavault.metadata.dimensional.service;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalAuditSupport;

/** GUI editor for {@link DimensionalModelService} metadata. */
public class DimensionalModelServiceEditor extends MetadataEditor<DimensionalModelService> {

  private static final Class<?> PKG = DimensionalModelService.class;

  private Text wName;
  private Button wEnabled;
  private Text wDescription;
  private TextVar wModelFilename;
  private Text wDefaultRowLimit;
  private Text wMaxRowLimit;
  private Text wQueryTimeout;
  private Button wAllowSchemaMetadata;
  private Text wAllowedUsers;
  private Combo wMaskingPolicy;
  private Button wAuditEnabled;
  private ComboVar wAuditConnection;
  private TextVar wAuditSchema;
  private TextVar wAuditTable;
  private Button wStoreSqlVerbatim;
  private Button wCreateAuditTable;

  public DimensionalModelServiceEditor(
      HopGui hopGui, MetadataManager<DimensionalModelService> manager, DimensionalModelService metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  protected Button createHelpButton(Shell shell) {
    return DialogHelpSupport.createHelpButton(shell, HelpTopics.DIMENSIONAL_MODEL_SERVICE);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wIcon = new Label(parent, SWT.RIGHT);
    wIcon.setImage(getImage());
    FormData fdlIcon = new FormData();
    fdlIcon.top = new FormAttachment(0, 0);
    fdlIcon.right = new FormAttachment(100, 0);
    wIcon.setLayoutData(fdlIcon);
    PropsUi.setLook(wIcon);

    Control last = addName(parent, wIcon, middle, margin);
    last = addLine(parent, last, middle, margin, "DimensionalModelServiceEditor.Enabled.Label", true);
    last = addDescription(parent, last, middle, margin);
    last = addFilename(parent, last, middle, margin);
    last = addText(parent, last, middle, margin, "DefaultRowLimit");
    last = addText(parent, last, middle, margin, "MaxRowLimit");
    last = addText(parent, last, middle, margin, "QueryTimeout");
    last = addMetadataCheckbox(parent, last, middle, margin);
    last = addAllowedUsers(parent, last, middle, margin);
    last = addPolicy(parent, last, middle, margin);
    last = addAudit(parent, last, middle, margin);

    Label wlHelp = new Label(parent, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlHelp);
    wlHelp.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.Help.Label"));
    FormData fdHelp = new FormData();
    fdHelp.left = new FormAttachment(0, 0);
    fdHelp.right = new FormAttachment(100, 0);
    fdHelp.top = new FormAttachment(last, 3 * margin);
    wlHelp.setLayoutData(fdHelp);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
    wModelFilename.addModifyListener(e -> setChanged());
    wDefaultRowLimit.addModifyListener(e -> setChanged());
    wMaxRowLimit.addModifyListener(e -> setChanged());
    wQueryTimeout.addModifyListener(e -> setChanged());
    wAllowedUsers.addModifyListener(e -> setChanged());
    wAuditSchema.addModifyListener(e -> setChanged());
    wAuditTable.addModifyListener(e -> setChanged());
    wEnabled.addListener(SWT.Selection, e -> setChanged());
    wAllowSchemaMetadata.addListener(SWT.Selection, e -> setChanged());
    wAuditEnabled.addListener(SWT.Selection, e -> setChanged());
    wStoreSqlVerbatim.addListener(SWT.Selection, e -> setChanged());
    wMaskingPolicy.addListener(SWT.Selection, e -> setChanged());
    wAuditConnection.addModifyListener(e -> setChanged());
  }

  private Control addName(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.Name.Label"));
    FormData labelData = new FormData();
    labelData.top = new FormAttachment(previous, margin);
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    label.setLayoutData(labelData);
    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData data = new FormData();
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    EdwNamingWidgetSupport.enableAndLayout(
        wName, hopGui.getVariables(), EdwNamingSchemeTypes.HOP_METADATA, data);
    return label;
  }

  private Control addLine(
      Composite parent, Control previous, int middle, int margin, String key, boolean enabled) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, key));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    Button button = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(button);
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    button.setLayoutData(data);
    if (enabled) {
      wEnabled = button;
    }
    return label;
  }

  private Control addDescription(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.Description.Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    wDescription.setLayoutData(data);
    return label;
  }

  private Control addFilename(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.ModelFilename.Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    Button browse = new Button(parent, SWT.PUSH);
    PropsUi.setLook(browse);
    browse.setText("Browse…");
    FormData browseData = new FormData();
    browseData.right = new FormAttachment(100, 0);
    browseData.top = new FormAttachment(label, 0, SWT.CENTER);
    browse.setLayoutData(browseData);
    wModelFilename = new TextVar(manager.getVariables(), parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelFilename);
    wModelFilename.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.ModelFilename.Tooltip"));
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(browse, -margin);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    wModelFilename.setLayoutData(data);
    browse.addListener(
        SWT.Selection,
        e -> {
          String path =
              BaseDialog.presentFileDialog(
                  parent.getShell(),
                  wModelFilename,
                  manager.getVariables(),
                  new String[] {"*.hdm", "*"},
                  new String[] {"Dimensional model (*.hdm)", "All files"},
                  true);
          if (path != null) {
            setChanged();
          }
        });
    return label;
  }

  private Control addText(Composite parent, Control previous, int middle, int margin, String suffix) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor." + suffix + ".Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    Text text = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    text.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor." + suffix + ".Tooltip"));
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    text.setLayoutData(data);
    switch (suffix) {
      case "DefaultRowLimit" -> wDefaultRowLimit = text;
      case "MaxRowLimit" -> wMaxRowLimit = text;
      default -> wQueryTimeout = text;
    }
    return label;
  }

  private Control addMetadataCheckbox(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AllowSchemaMetadata.Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    wAllowSchemaMetadata = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(wAllowSchemaMetadata);
    wAllowSchemaMetadata.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AllowSchemaMetadata.Tooltip"));
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    wAllowSchemaMetadata.setLayoutData(data);
    return label;
  }

  private Control addAllowedUsers(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AllowedUsers.Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    wAllowedUsers = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wAllowedUsers);
    wAllowedUsers.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AllowedUsers.Tooltip"));
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    data.height = 60;
    wAllowedUsers.setLayoutData(data);
    return wAllowedUsers;
  }

  private Control addPolicy(Composite parent, Control previous, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.MaskingPolicy.Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    wMaskingPolicy = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wMaskingPolicy);
    wMaskingPolicy.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.MaskingPolicy.Tooltip"));
    EnumDialogSupport.populateCombo(wMaskingPolicy, DimensionalMaskingPolicy.class);
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    wMaskingPolicy.setLayoutData(data);
    return label;
  }

  private Control addAudit(Composite parent, Control previous, int middle, int margin) {
    Label enabled = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(enabled);
    enabled.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AuditEnabled.Label"));
    FormData enabledData = new FormData();
    enabledData.left = new FormAttachment(0, 0);
    enabledData.right = new FormAttachment(middle, -margin);
    enabledData.top = new FormAttachment(previous, 2 * margin);
    enabled.setLayoutData(enabledData);
    wAuditEnabled = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(wAuditEnabled);
    wAuditEnabled.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AuditEnabled.Tooltip"));
    FormData checkData = new FormData();
    checkData.left = new FormAttachment(middle, 0);
    checkData.top = new FormAttachment(enabled, 0, SWT.CENTER);
    wAuditEnabled.setLayoutData(checkData);

    Label connection = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(connection);
    connection.setText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AuditConnection.Label"));
    FormData connectionLabel = new FormData();
    connectionLabel.left = new FormAttachment(0, 0);
    connectionLabel.right = new FormAttachment(middle, -margin);
    connectionLabel.top = new FormAttachment(enabled, 2 * margin);
    connection.setLayoutData(connectionLabel);
    wAuditConnection = new ComboVar(manager.getVariables(), parent, SWT.BORDER);
    PropsUi.setLook(wAuditConnection);
    wAuditConnection.setItems(connectionNames());
    FormData connectionData = new FormData();
    connectionData.left = new FormAttachment(middle, 0);
    connectionData.right = new FormAttachment(100, 0);
    connectionData.top = new FormAttachment(connection, 0, SWT.CENTER);
    wAuditConnection.setLayoutData(connectionData);

    Control schema = labeledVar(parent, connection, middle, margin, "AuditSchema");
    Control table = labeledVar(parent, schema, middle, margin, "AuditTable");

    Label verbatim = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(verbatim);
    verbatim.setText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.StoreSqlVerbatim.Label"));
    FormData verbatimLabel = new FormData();
    verbatimLabel.left = new FormAttachment(0, 0);
    verbatimLabel.right = new FormAttachment(middle, -margin);
    verbatimLabel.top = new FormAttachment(table, 2 * margin);
    verbatim.setLayoutData(verbatimLabel);
    wStoreSqlVerbatim = new Button(parent, SWT.CHECK | SWT.LEFT);
    PropsUi.setLook(wStoreSqlVerbatim);
    wStoreSqlVerbatim.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.StoreSqlVerbatim.Tooltip"));
    FormData verbatimData = new FormData();
    verbatimData.left = new FormAttachment(middle, 0);
    verbatimData.top = new FormAttachment(verbatim, 0, SWT.CENTER);
    wStoreSqlVerbatim.setLayoutData(verbatimData);

    wCreateAuditTable = new Button(parent, SWT.PUSH);
    wCreateAuditTable.setText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.CreateAuditTable.Label"));
    wCreateAuditTable.setToolTipText(
        BaseMessages.getString(PKG, "DimensionalModelServiceEditor.CreateAuditTable.Tooltip"));
    PropsUi.setLook(wCreateAuditTable);
    FormData createData = new FormData();
    createData.left = new FormAttachment(middle, 0);
    createData.top = new FormAttachment(verbatim, 2 * margin);
    wCreateAuditTable.setLayoutData(createData);
    wCreateAuditTable.addListener(SWT.Selection, e -> createAuditTable());
    return wCreateAuditTable;
  }

  private Control labeledVar(Composite parent, Control previous, int middle, int margin, String suffix) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor." + suffix + ".Label"));
    FormData labelData = new FormData();
    labelData.left = new FormAttachment(0, 0);
    labelData.right = new FormAttachment(middle, -margin);
    labelData.top = new FormAttachment(previous, 2 * margin);
    label.setLayoutData(labelData);
    TextVar text = new TextVar(manager.getVariables(), parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(label, 0, SWT.CENTER);
    text.setLayoutData(data);
    if ("AuditSchema".equals(suffix)) {
      wAuditSchema = text;
    } else {
      wAuditTable = text;
    }
    return label;
  }

  private String[] connectionNames() {
    try {
      List<String> names =
          hopGui.getMetadataProvider().getSerializer(DatabaseMeta.class).listObjectNames();
      return names != null ? names.toArray(String[]::new) : new String[0];
    } catch (Exception e) {
      return new String[0];
    }
  }

  private void createAuditTable() {
    DimensionalModelService draft = new DimensionalModelService();
    getWidgetsContent(draft);
    try {
      DimensionalAuditSupport.createTable(hopGui.getVariables(), hopGui.getMetadataProvider(), draft);
      MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.CreateAuditTable.Label"));
      box.setMessage(
          BaseMessages.getString(
              PKG,
              "DimensionalModelServiceEditor.CreateAuditTable.Created",
              draft.getAuditTableOrDefault()));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "DimensionalModelServiceEditor.CreateAuditTable.Label"),
          e.getMessage(),
          e);
    }
  }

  @Override
  public void setWidgetsContent() {
    DimensionalModelService meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    wEnabled.setSelection(meta.isEnabled());
    wDescription.setText(Const.NVL(meta.getDescription(), ""));
    wModelFilename.setText(Const.NVL(meta.getModelFilename(), ""));
    wDefaultRowLimit.setText(Integer.toString(meta.getDefaultRowLimit()));
    wMaxRowLimit.setText(Integer.toString(meta.getMaxRowLimit()));
    wQueryTimeout.setText(Integer.toString(meta.getQueryTimeoutSeconds()));
    wAllowSchemaMetadata.setSelection(meta.isAllowSchemaMetadata());
    wAllowedUsers.setText(String.join(System.lineSeparator(), meta.getAllowedUsersOrEmpty()));
    EnumDialogSupport.selectCombo(wMaskingPolicy, meta.getMaskingPolicyOrDefault());
    wAuditEnabled.setSelection(meta.isAuditEnabled());
    wAuditConnection.setText(Const.NVL(meta.getAuditConnection(), ""));
    wAuditSchema.setText(Const.NVL(meta.getAuditSchema(), ""));
    wAuditTable.setText(Const.NVL(meta.getAuditTable(), DimensionalModelService.DEFAULT_AUDIT_TABLE));
    wStoreSqlVerbatim.setSelection(meta.isStoreSqlVerbatim());
  }

  @Override
  public void getWidgetsContent(DimensionalModelService meta) {
    meta.setName(wName.getText());
    meta.setEnabled(wEnabled.getSelection());
    meta.setDescription(wDescription.getText());
    meta.setModelFilename(wModelFilename.getText());
    meta.setDefaultRowLimit(parseInt(wDefaultRowLimit.getText(), 10_000));
    meta.setMaxRowLimit(parseInt(wMaxRowLimit.getText(), 100_000));
    meta.setQueryTimeoutSeconds(parseInt(wQueryTimeout.getText(), 60));
    meta.setAllowSchemaMetadata(wAllowSchemaMetadata.getSelection());
    meta.setAllowedUsers(splitUsers(wAllowedUsers.getText()));
    meta.setMaskingPolicy(
        EnumDialogSupport.readCombo(
            wMaskingPolicy, DimensionalMaskingPolicy.class, DimensionalMaskingPolicy.SAFE));
    meta.setAuditEnabled(wAuditEnabled.getSelection());
    meta.setAuditConnection(wAuditConnection.getText());
    meta.setAuditSchema(wAuditSchema.getText());
    meta.setAuditTable(wAuditTable.getText());
    meta.setStoreSqlVerbatim(wStoreSqlVerbatim.getSelection());
  }

  @Override
  public void save() throws HopException {
    getWidgetsContent(getMetadata());
    String conflict =
        DimensionalModelService.sourceNameConflict(
            getMetadata().getName(), hopGui.getMetadataProvider());
    if (conflict != null) {
      throw new HopException(conflict);
    }
    if (getMetadata().isAuditEnabled()) {
      boolean exists = false;
      try {
        exists =
            DimensionalAuditSupport.tableExists(
                hopGui.getVariables(), hopGui.getMetadataProvider(), getMetadata());
      } catch (Exception ignored) {
        exists = false;
      }
      if (!exists) {
        MessageBox box = new MessageBox(getShell(), SWT.OK | SWT.ICON_WARNING);
        box.setText(BaseMessages.getString(PKG, "DimensionalModelServiceEditor.AuditEnabled.Label"));
        box.setMessage(
            BaseMessages.getString(PKG, "DimensionalModelServiceEditor.CreateAuditTable.Missing"));
        box.open();
      }
    }
    super.save();
  }

  private static List<String> splitUsers(String text) {
    List<String> users = new ArrayList<>();
    if (text == null) {
      return users;
    }
    for (String line : text.split("\\R")) {
      if (line != null && !line.isBlank()) {
        users.add(line.trim());
      }
    }
    return users;
  }

  private static int parseInt(String text, int defaultValue) {
    try {
      return Integer.parseInt(Const.NVL(text, "").trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
