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
package org.apache.hop.catalog.transform.recordddl;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class RecordDefinitionDdlDialog extends BaseTransformDialog {

  private static final Class<?> PKG = RecordDefinitionDdlMeta.class;

  private final RecordDefinitionDdlMeta input;

  private CCombo wCatalogConnection;
  private Button wSelectFromInput;
  private CCombo wNamespaceField;
  private CCombo wNameField;
  private TextVar wNamespaceValue;
  private TextVar wNameValue;
  private CCombo wOverrideConnection;
  private TextVar wOverrideSchema;
  private TextVar wOverrideTable;
  private Button wExecuteDdl;
  private Button wDropTableIfExists;
  private Button wSkipIfTableExists;
  private Button wAppendSemicolon;
  private Button wFailIfNoFields;
  private TextVar wOutputDdlField;
  private TextVar wOutputStatusField;

  private final List<String> inputFields = new ArrayList<>();

  public RecordDefinitionDdlDialog(
      Shell parent,
      IVariables variables,
      RecordDefinitionDdlMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.Shell.Title"));
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    addSourceTab(wTabFolder, middle, margin);
    addTargetTab(wTabFolder, middle, margin);
    addDdlTab(wTabFolder, middle, margin);
    wTabFolder.setSelection(0);

    loadConnections();
    loadInputFields();
    getData();
    setFlags();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return transformName;
  }

  private void addSourceTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.SourceTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlCatalog = new Label(comp, SWT.RIGHT);
    wlCatalog.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.ConnectionName.Label"));
    PropsUi.setLook(wlCatalog);
    FormData fdlCatalog = new FormData();
    fdlCatalog.left = new FormAttachment(0, margin);
    fdlCatalog.right = new FormAttachment(middle, -margin);
    fdlCatalog.top = new FormAttachment(0, margin);
    wlCatalog.setLayoutData(fdlCatalog);

    wCatalogConnection = new CCombo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wCatalogConnection);
    FormData fdCatalog = new FormData();
    fdCatalog.left = new FormAttachment(middle, 0);
    fdCatalog.right = new FormAttachment(100, -margin);
    fdCatalog.top = new FormAttachment(0, margin);
    wCatalogConnection.setLayoutData(fdCatalog);

    Label wlSelect = new Label(comp, SWT.RIGHT);
    wlSelect.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.SelectFromInput.Label"));
    PropsUi.setLook(wlSelect);
    FormData fdlSelect = new FormData();
    fdlSelect.left = new FormAttachment(0, margin);
    fdlSelect.right = new FormAttachment(middle, -margin);
    fdlSelect.top = new FormAttachment(wCatalogConnection, margin);
    wlSelect.setLayoutData(fdlSelect);

    wSelectFromInput = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wSelectFromInput);
    wSelectFromInput.addListener(SWT.Selection, e -> setFlags());
    FormData fdSelect = new FormData();
    fdSelect.left = new FormAttachment(middle, 0);
    fdSelect.top = new FormAttachment(wlSelect, 0, SWT.CENTER);
    wSelectFromInput.setLayoutData(fdSelect);

    Label wlNamespaceField = new Label(comp, SWT.RIGHT);
    wlNamespaceField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.NamespaceField.Label"));
    PropsUi.setLook(wlNamespaceField);
    FormData fdlNamespaceField = new FormData();
    fdlNamespaceField.left = new FormAttachment(0, margin);
    fdlNamespaceField.right = new FormAttachment(middle, -margin);
    fdlNamespaceField.top = new FormAttachment(wSelectFromInput, margin);
    wlNamespaceField.setLayoutData(fdlNamespaceField);

    wNamespaceField = new CCombo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wNamespaceField);
    FormData fdNamespaceField = new FormData();
    fdNamespaceField.left = new FormAttachment(middle, 0);
    fdNamespaceField.right = new FormAttachment(100, -margin);
    fdNamespaceField.top = new FormAttachment(wSelectFromInput, margin);
    wNamespaceField.setLayoutData(fdNamespaceField);

    Label wlNameField = new Label(comp, SWT.RIGHT);
    wlNameField.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.NameField.Label"));
    PropsUi.setLook(wlNameField);
    FormData fdlNameField = new FormData();
    fdlNameField.left = new FormAttachment(0, margin);
    fdlNameField.right = new FormAttachment(middle, -margin);
    fdlNameField.top = new FormAttachment(wNamespaceField, margin);
    wlNameField.setLayoutData(fdlNameField);

    wNameField = new CCombo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wNameField);
    FormData fdNameField = new FormData();
    fdNameField.left = new FormAttachment(middle, 0);
    fdNameField.right = new FormAttachment(100, -margin);
    fdNameField.top = new FormAttachment(wNamespaceField, margin);
    wNameField.setLayoutData(fdNameField);

    Label wlNamespaceValue = new Label(comp, SWT.RIGHT);
    wlNamespaceValue.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.NamespaceValue.Label"));
    PropsUi.setLook(wlNamespaceValue);
    FormData fdlNamespaceValue = new FormData();
    fdlNamespaceValue.left = new FormAttachment(0, margin);
    fdlNamespaceValue.right = new FormAttachment(middle, -margin);
    fdlNamespaceValue.top = new FormAttachment(wNameField, margin);
    wlNamespaceValue.setLayoutData(fdlNamespaceValue);

    wNamespaceValue = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNamespaceValue);
    FormData fdNamespaceValue = new FormData();
    fdNamespaceValue.left = new FormAttachment(middle, 0);
    fdNamespaceValue.right = new FormAttachment(100, -margin);
    fdNamespaceValue.top = new FormAttachment(wNameField, margin);
    wNamespaceValue.setLayoutData(fdNamespaceValue);

    Label wlNameValue = new Label(comp, SWT.RIGHT);
    wlNameValue.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.NameValue.Label"));
    PropsUi.setLook(wlNameValue);
    FormData fdlNameValue = new FormData();
    fdlNameValue.left = new FormAttachment(0, margin);
    fdlNameValue.right = new FormAttachment(middle, -margin);
    fdlNameValue.top = new FormAttachment(wNamespaceValue, margin);
    wlNameValue.setLayoutData(fdlNameValue);

    wNameValue = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNameValue);
    FormData fdNameValue = new FormData();
    fdNameValue.left = new FormAttachment(middle, 0);
    fdNameValue.right = new FormAttachment(100, -margin);
    fdNameValue.top = new FormAttachment(wNamespaceValue, margin);
    wNameValue.setLayoutData(fdNameValue);
  }

  private void addTargetTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.TargetTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlOverrideConnection = new Label(comp, SWT.RIGHT);
    wlOverrideConnection.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OverrideConnection.Label"));
    PropsUi.setLook(wlOverrideConnection);
    wlOverrideConnection.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OverrideConnection.ToolTip"));
    FormData fdlOverrideConnection = new FormData();
    fdlOverrideConnection.left = new FormAttachment(0, margin);
    fdlOverrideConnection.right = new FormAttachment(middle, -margin);
    fdlOverrideConnection.top = new FormAttachment(0, margin);
    wlOverrideConnection.setLayoutData(fdlOverrideConnection);

    wOverrideConnection = new CCombo(comp, SWT.BORDER);
    PropsUi.setLook(wOverrideConnection);
    FormData fdOverrideConnection = new FormData();
    fdOverrideConnection.left = new FormAttachment(middle, 0);
    fdOverrideConnection.right = new FormAttachment(100, -margin);
    fdOverrideConnection.top = new FormAttachment(0, margin);
    wOverrideConnection.setLayoutData(fdOverrideConnection);

    Label wlOverrideSchema = new Label(comp, SWT.RIGHT);
    wlOverrideSchema.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OverrideSchema.Label"));
    PropsUi.setLook(wlOverrideSchema);
    FormData fdlOverrideSchema = new FormData();
    fdlOverrideSchema.left = new FormAttachment(0, margin);
    fdlOverrideSchema.right = new FormAttachment(middle, -margin);
    fdlOverrideSchema.top = new FormAttachment(wOverrideConnection, margin);
    wlOverrideSchema.setLayoutData(fdlOverrideSchema);

    wOverrideSchema = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOverrideSchema);
    FormData fdOverrideSchema = new FormData();
    fdOverrideSchema.left = new FormAttachment(middle, 0);
    fdOverrideSchema.right = new FormAttachment(100, -margin);
    fdOverrideSchema.top = new FormAttachment(wOverrideConnection, margin);
    wOverrideSchema.setLayoutData(fdOverrideSchema);

    Label wlOverrideTable = new Label(comp, SWT.RIGHT);
    wlOverrideTable.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OverrideTable.Label"));
    PropsUi.setLook(wlOverrideTable);
    FormData fdlOverrideTable = new FormData();
    fdlOverrideTable.left = new FormAttachment(0, margin);
    fdlOverrideTable.right = new FormAttachment(middle, -margin);
    fdlOverrideTable.top = new FormAttachment(wOverrideSchema, margin);
    wlOverrideTable.setLayoutData(fdlOverrideTable);

    wOverrideTable = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOverrideTable);
    FormData fdOverrideTable = new FormData();
    fdOverrideTable.left = new FormAttachment(middle, 0);
    fdOverrideTable.right = new FormAttachment(100, -margin);
    fdOverrideTable.top = new FormAttachment(wOverrideSchema, margin);
    wOverrideTable.setLayoutData(fdOverrideTable);
  }

  private void addDdlTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.DdlTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlExecuteDdl = new Label(comp, SWT.RIGHT);
    wlExecuteDdl.setText(BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.ExecuteDdl.Label"));
    PropsUi.setLook(wlExecuteDdl);
    FormData fdlExecuteDdl = new FormData();
    fdlExecuteDdl.left = new FormAttachment(0, margin);
    fdlExecuteDdl.right = new FormAttachment(middle, -margin);
    fdlExecuteDdl.top = new FormAttachment(0, margin);
    wlExecuteDdl.setLayoutData(fdlExecuteDdl);
    wExecuteDdl = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wExecuteDdl);
    FormData fdExecuteDdl = new FormData();
    fdExecuteDdl.left = new FormAttachment(middle, 0);
    fdExecuteDdl.top = new FormAttachment(wlExecuteDdl, 0, SWT.CENTER);
    wExecuteDdl.setLayoutData(fdExecuteDdl);

    wDropTableIfExists =
        addCheckbox(
            comp, "RecordDefinitionDdlDialog.DropTableIfExists.Label", middle, margin, wExecuteDdl);
    wSkipIfTableExists =
        addCheckbox(
            comp,
            "RecordDefinitionDdlDialog.SkipIfTableExists.Label",
            middle,
            margin,
            wDropTableIfExists);
    wAppendSemicolon =
        addCheckbox(
            comp,
            "RecordDefinitionDdlDialog.AppendSemicolon.Label",
            middle,
            margin,
            wSkipIfTableExists);
    wFailIfNoFields =
        addCheckbox(
            comp,
            "RecordDefinitionDdlDialog.FailIfNoFields.Label",
            middle,
            margin,
            wAppendSemicolon);
    wFailIfNoFields.setToolTipText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.FailIfNoFields.ToolTip"));

    Label wlOutputDdlField = new Label(comp, SWT.RIGHT);
    wlOutputDdlField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OutputDdlField.Label"));
    PropsUi.setLook(wlOutputDdlField);
    FormData fdlOutputDdlField = new FormData();
    fdlOutputDdlField.left = new FormAttachment(0, margin);
    fdlOutputDdlField.right = new FormAttachment(middle, -margin);
    fdlOutputDdlField.top = new FormAttachment(wFailIfNoFields, margin);
    wlOutputDdlField.setLayoutData(fdlOutputDdlField);

    wOutputDdlField = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOutputDdlField);
    FormData fdOutputDdlField = new FormData();
    fdOutputDdlField.left = new FormAttachment(middle, 0);
    fdOutputDdlField.right = new FormAttachment(100, -margin);
    fdOutputDdlField.top = new FormAttachment(wFailIfNoFields, margin);
    wOutputDdlField.setLayoutData(fdOutputDdlField);

    Label wlOutputStatusField = new Label(comp, SWT.RIGHT);
    wlOutputStatusField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDdlDialog.OutputStatusField.Label"));
    PropsUi.setLook(wlOutputStatusField);
    FormData fdlOutputStatusField = new FormData();
    fdlOutputStatusField.left = new FormAttachment(0, margin);
    fdlOutputStatusField.right = new FormAttachment(middle, -margin);
    fdlOutputStatusField.top = new FormAttachment(wOutputDdlField, margin);
    wlOutputStatusField.setLayoutData(fdlOutputStatusField);

    wOutputStatusField = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOutputStatusField);
    FormData fdOutputStatusField = new FormData();
    fdOutputStatusField.left = new FormAttachment(middle, 0);
    fdOutputStatusField.right = new FormAttachment(100, -margin);
    fdOutputStatusField.top = new FormAttachment(wOutputDdlField, margin);
    wOutputStatusField.setLayoutData(fdOutputStatusField);
  }

  private Button addCheckbox(
      Composite comp, String labelKey, int middle, int margin, Button previous) {
    Label label = new Label(comp, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, margin);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top = new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Button button = new Button(comp, SWT.CHECK);
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    button.setLayoutData(fd);
    return button;
  }

  private void loadConnections() {
    try {
      IHopMetadataSerializer<org.apache.hop.catalog.metadata.DataCatalogMeta> catalogSerializer =
          metadataProvider.getSerializer(org.apache.hop.catalog.metadata.DataCatalogMeta.class);
      wCatalogConnection.setItems(catalogSerializer.listObjectNames().toArray(new String[0]));

      IHopMetadataSerializer<DatabaseMeta> databaseSerializer =
          metadataProvider.getSerializer(DatabaseMeta.class);
      wOverrideConnection.setItems(databaseSerializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading metadata connections", e);
    }
  }

  private void loadInputFields() {
    TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
    if (transformMeta == null) {
      return;
    }
    new Thread(
            () -> {
              try {
                IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
                if (row != null) {
                  for (int i = 0; i < row.size(); i++) {
                    inputFields.add(row.getValueMeta(i).getName());
                  }
                  shell
                      .getDisplay()
                      .asyncExec(
                          () -> {
                            String[] fieldNames =
                                org.apache.hop.ui.core.ConstUi.sortFieldNames(inputFields);
                            wNamespaceField.setItems(fieldNames);
                            wNameField.setItems(fieldNames);
                            wNamespaceField.setText(Const.NVL(input.getNamespaceField(), ""));
                            wNameField.setText(Const.NVL(input.getNameField(), ""));
                          });
                }
              } catch (HopException e) {
                logError("Unable to read input fields", e);
              }
            })
        .start();
  }

  private void setFlags() {
    boolean fromInput = wSelectFromInput.getSelection();
    wNamespaceField.setEnabled(fromInput);
    wNameField.setEnabled(fromInput);
    wNamespaceValue.setEnabled(!fromInput);
    wNameValue.setEnabled(!fromInput);
  }

  private void getData() {
    wCatalogConnection.setText(Const.NVL(input.getCatalogConnectionName(), ""));
    wSelectFromInput.setSelection(input.isSelectFromInput());
    wNamespaceValue.setText(Const.NVL(input.getNamespaceValue(), ""));
    wNameValue.setText(Const.NVL(input.getNameValue(), ""));
    wOverrideConnection.setText(Const.NVL(input.getOverrideConnectionName(), ""));
    wOverrideSchema.setText(Const.NVL(input.getOverrideSchemaName(), ""));
    wOverrideTable.setText(Const.NVL(input.getOverrideTableName(), ""));
    wExecuteDdl.setSelection(input.isExecuteDdl());
    wDropTableIfExists.setSelection(input.isDropTableIfExists());
    wSkipIfTableExists.setSelection(input.isSkipIfTableExists());
    wAppendSemicolon.setSelection(input.isAppendSemicolon());
    wFailIfNoFields.setSelection(input.isFailIfNoFields());
    wOutputDdlField.setText(Const.NVL(input.getOutputDdlField(), "ddl"));
    wOutputStatusField.setText(Const.NVL(input.getOutputStatusField(), "ddl_status"));
  }

  private void cancel() {
    transformName = null;
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setCatalogConnectionName(wCatalogConnection.getText());
    input.setSelectFromInput(wSelectFromInput.getSelection());
    input.setNamespaceField(wNamespaceField.getText());
    input.setNameField(wNameField.getText());
    input.setNamespaceValue(wNamespaceValue.getText());
    input.setNameValue(wNameValue.getText());
    input.setOverrideConnectionName(wOverrideConnection.getText());
    input.setOverrideSchemaName(wOverrideSchema.getText());
    input.setOverrideTableName(wOverrideTable.getText());
    input.setExecuteDdl(wExecuteDdl.getSelection());
    input.setDropTableIfExists(wDropTableIfExists.getSelection());
    input.setSkipIfTableExists(wSkipIfTableExists.getSelection());
    input.setAppendSemicolon(wAppendSemicolon.getSelection());
    input.setFailIfNoFields(wFailIfNoFields.getSelection());
    input.setOutputDdlField(Const.NVL(wOutputDdlField.getText(), "ddl"));
    input.setOutputStatusField(Const.NVL(wOutputStatusField.getText(), "ddl_status"));
    input.setChanged();
    dispose();
  }
}
