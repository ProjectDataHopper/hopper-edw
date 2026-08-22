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
package org.apache.hop.catalog.transform.recordinput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiDataVaultModelDialog;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class RecordDefinitionInputDialog extends BaseTransformDialog {

  private static final Class<?> PKG = RecordDefinitionInputMeta.class;

  private final RecordDefinitionInputMeta input;

  private CCombo wConnectionName;
  private Button wSelectFromInput;
  private CCombo wNamespaceField;
  private CCombo wNameField;
  private Text wNamespaceValue;
  private Text wNameValue;
  private Button wOutputFieldsMetadata;

  // Output fields renaming controls
  private Text wOutNamespace;
  private Text wOutName;
  private Text wOutType;
  private Text wOutDescription;
  private Text wOutModelType;
  private Text wOutModelName;
  private Text wOutModelFilename;
  private Text wOutModelElement;
  private Text wOutHopProject;
  private Text wOutCreatedAt;
  private Text wOutUpdatedAt;
  private Text wOutUpdatedBy;
  private Text wOutLastWorkflow;
  private Text wOutLastPipeline;
  private Text wOutPhysicalDatabase;
  private Text wOutPhysicalSchema;
  private Text wOutPhysicalTable;
  private Text wOutDvSourceType;
  private Text wOutDvSourceIndicator;
  private Text wOutDvSourceIndicatorField;
  private Text wOutDvSourceGroup;
  private Text wOutDvDeliveryType;
  private Text wOutFieldName;
  private Text wOutFieldType;
  private Text wOutFieldLength;
  private Text wOutFieldPrecision;
  private Text wOutFieldPrimaryKeyPosition;

  private final List<String> inputFields = new ArrayList<>();

  public RecordDefinitionInputDialog(
      Shell parent,
      IVariables variables,
      RecordDefinitionInputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "RecordDefinitionInputDialog.Shell.Title"));

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    // General tab
    Composite wGeneralTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "RecordDefinitionInputDialog.GeneralTab.Label"),
            BaseMessages.getString(PKG, "RecordDefinitionInputDialog.GeneralTab.Label"));

    // Connection Name
    Label wlConnectionName = new Label(wGeneralTabComp, SWT.RIGHT);
    wlConnectionName.setText(
        BaseMessages.getString(PKG, "RecordDefinitionInputDialog.ConnectionName.Label"));
    PropsUi.setLook(wlConnectionName);
    FormData fdlConnectionName = new FormData();
    fdlConnectionName.left = new FormAttachment(0, 0);
    fdlConnectionName.right = new FormAttachment(middle, -margin);
    fdlConnectionName.top = new FormAttachment(0, margin);
    wlConnectionName.setLayoutData(fdlConnectionName);

    wConnectionName = new CCombo(wGeneralTabComp, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wConnectionName);
    FormData fdConnectionName = new FormData();
    fdConnectionName.left = new FormAttachment(middle, 0);
    fdConnectionName.right = new FormAttachment(100, 0);
    fdConnectionName.top = new FormAttachment(0, margin);
    wConnectionName.setLayoutData(fdConnectionName);

    // Select from input
    Label wlSelectFromInput = new Label(wGeneralTabComp, SWT.RIGHT);
    wlSelectFromInput.setText(
        BaseMessages.getString(PKG, "RecordDefinitionInputDialog.SelectFromInput.Label"));
    PropsUi.setLook(wlSelectFromInput);
    FormData fdlSelectFromInput = new FormData();
    fdlSelectFromInput.left = new FormAttachment(0, 0);
    fdlSelectFromInput.right = new FormAttachment(middle, -margin);
    fdlSelectFromInput.top = new FormAttachment(wConnectionName, margin);
    wlSelectFromInput.setLayoutData(fdlSelectFromInput);

    wSelectFromInput = new Button(wGeneralTabComp, SWT.CHECK);
    PropsUi.setLook(wSelectFromInput);
    FormData fdSelectFromInput = new FormData();
    fdSelectFromInput.left = new FormAttachment(middle, 0);
    fdSelectFromInput.right = new FormAttachment(100, 0);
    fdSelectFromInput.top = new FormAttachment(wlSelectFromInput, 0, SWT.CENTER);
    wSelectFromInput.setLayoutData(fdSelectFromInput);
    wSelectFromInput.addListener(SWT.Selection, e -> setFlags());

    // Namespace field combo
    Label wlNamespaceField = new Label(wGeneralTabComp, SWT.RIGHT);
    wlNamespaceField.setText(
        BaseMessages.getString(PKG, "RecordDefinitionInputDialog.NamespaceField.Label"));
    PropsUi.setLook(wlNamespaceField);
    FormData fdlNamespaceField = new FormData();
    fdlNamespaceField.left = new FormAttachment(0, 0);
    fdlNamespaceField.right = new FormAttachment(middle, -margin);
    fdlNamespaceField.top = new FormAttachment(wSelectFromInput, margin);
    wlNamespaceField.setLayoutData(fdlNamespaceField);

    wNamespaceField = new CCombo(wGeneralTabComp, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wNamespaceField);
    FormData fdNamespaceField = new FormData();
    fdNamespaceField.left = new FormAttachment(middle, 0);
    fdNamespaceField.right = new FormAttachment(100, 0);
    fdNamespaceField.top = new FormAttachment(wSelectFromInput, margin);
    wNamespaceField.setLayoutData(fdNamespaceField);

    // Name field combo
    Label wlNameField = new Label(wGeneralTabComp, SWT.RIGHT);
    wlNameField.setText(BaseMessages.getString(PKG, "RecordDefinitionInputDialog.NameField.Label"));
    PropsUi.setLook(wlNameField);
    FormData fdlNameField = new FormData();
    fdlNameField.left = new FormAttachment(0, 0);
    fdlNameField.right = new FormAttachment(middle, -margin);
    fdlNameField.top = new FormAttachment(wNamespaceField, margin);
    wlNameField.setLayoutData(fdlNameField);

    wNameField = new CCombo(wGeneralTabComp, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wNameField);
    FormData fdNameField = new FormData();
    fdNameField.left = new FormAttachment(middle, 0);
    fdNameField.right = new FormAttachment(100, 0);
    fdNameField.top = new FormAttachment(wNamespaceField, margin);
    wNameField.setLayoutData(fdNameField);

    // Namespace value text
    Label wlNamespaceValue = new Label(wGeneralTabComp, SWT.RIGHT);
    wlNamespaceValue.setText(
        BaseMessages.getString(PKG, "RecordDefinitionInputDialog.NamespaceValue.Label"));
    PropsUi.setLook(wlNamespaceValue);
    FormData fdlNamespaceValue = new FormData();
    fdlNamespaceValue.left = new FormAttachment(0, 0);
    fdlNamespaceValue.right = new FormAttachment(middle, -margin);
    fdlNamespaceValue.top = new FormAttachment(wNameField, margin);
    wlNamespaceValue.setLayoutData(fdlNamespaceValue);

    wNamespaceValue = new Text(wGeneralTabComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNamespaceValue);
    FormData fdNamespaceValue = new FormData();
    fdNamespaceValue.left = new FormAttachment(middle, 0);
    fdNamespaceValue.right = new FormAttachment(100, 0);
    fdNamespaceValue.top = new FormAttachment(wNameField, margin);
    wNamespaceValue.setLayoutData(fdNamespaceValue);

    // Name value text
    Label wlNameValue = new Label(wGeneralTabComp, SWT.RIGHT);
    wlNameValue.setText(BaseMessages.getString(PKG, "RecordDefinitionInputDialog.NameValue.Label"));
    PropsUi.setLook(wlNameValue);
    FormData fdlNameValue = new FormData();
    fdlNameValue.left = new FormAttachment(0, 0);
    fdlNameValue.right = new FormAttachment(middle, -margin);
    fdlNameValue.top = new FormAttachment(wNamespaceValue, margin);
    wlNameValue.setLayoutData(fdlNameValue);

    wNameValue = new Text(wGeneralTabComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wNameValue);
    FormData fdNameValue = new FormData();
    fdNameValue.left = new FormAttachment(middle, 0);
    fdNameValue.right = new FormAttachment(100, 0);
    fdNameValue.top = new FormAttachment(wNamespaceValue, margin);
    wNameValue.setLayoutData(fdNameValue);

    // Output fields metadata
    Label wlOutputFieldsMetadata = new Label(wGeneralTabComp, SWT.RIGHT);
    wlOutputFieldsMetadata.setText(
        BaseMessages.getString(PKG, "RecordDefinitionInputDialog.OutputFieldsMetadata.Label"));
    PropsUi.setLook(wlOutputFieldsMetadata);
    FormData fdlOutputFieldsMetadata = new FormData();
    fdlOutputFieldsMetadata.left = new FormAttachment(0, 0);
    fdlOutputFieldsMetadata.right = new FormAttachment(middle, -margin);
    fdlOutputFieldsMetadata.top = new FormAttachment(wNameValue, margin);
    wlOutputFieldsMetadata.setLayoutData(fdlOutputFieldsMetadata);

    wOutputFieldsMetadata = new Button(wGeneralTabComp, SWT.CHECK);
    PropsUi.setLook(wOutputFieldsMetadata);
    FormData fdOutputFieldsMetadata = new FormData();
    fdOutputFieldsMetadata.left = new FormAttachment(middle, 0);
    fdOutputFieldsMetadata.right = new FormAttachment(100, 0);
    fdOutputFieldsMetadata.top = new FormAttachment(wlOutputFieldsMetadata, 0, SWT.CENTER);
    wOutputFieldsMetadata.setLayoutData(fdOutputFieldsMetadata);

    // Output Fields Tab (renaming)
    Composite wFieldsTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "RecordDefinitionInputDialog.OutputFieldsTab.Label"),
            BaseMessages.getString(PKG, "RecordDefinitionInputDialog.OutputFieldsTab.Label"));

    ScrolledComposite wScroll = new ScrolledComposite(wFieldsTabComp, SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wScroll);
    FormData fdScroll = new FormData();
    fdScroll.left = new FormAttachment(0, 0);
    fdScroll.right = new FormAttachment(100, 0);
    fdScroll.top = new FormAttachment(0, 0);
    fdScroll.bottom = new FormAttachment(100, 0);
    wScroll.setLayoutData(fdScroll);

    Composite wRenamesComp = new Composite(wScroll, SWT.NONE);
    PropsUi.setLook(wRenamesComp);
    wRenamesComp.setLayout(new FormLayout());

    Control last = null;
    last =
        wOutNamespace =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputNamespaceField.Label",
                last,
                middle,
                margin);
    last =
        wOutName =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputNameField.Label",
                last,
                middle,
                margin);
    last =
        wOutType =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputTypeField.Label",
                last,
                middle,
                margin);
    last =
        wOutDescription =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDescriptionField.Label",
                last,
                middle,
                margin);
    last =
        wOutModelType =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputModelTypeField.Label",
                last,
                middle,
                margin);
    last =
        wOutModelName =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputModelNameField.Label",
                last,
                middle,
                margin);
    last =
        wOutModelFilename =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputModelFilenameField.Label",
                last,
                middle,
                margin);
    last =
        wOutModelElement =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputModelElementField.Label",
                last,
                middle,
                margin);
    last =
        wOutHopProject =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputHopProjectField.Label",
                last,
                middle,
                margin);
    last =
        wOutCreatedAt =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputCreatedAtField.Label",
                last,
                middle,
                margin);
    last =
        wOutUpdatedAt =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputUpdatedAtField.Label",
                last,
                middle,
                margin);
    last =
        wOutUpdatedBy =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputUpdatedByField.Label",
                last,
                middle,
                margin);
    last =
        wOutLastWorkflow =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputLastWorkflowField.Label",
                last,
                middle,
                margin);
    last =
        wOutLastPipeline =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputLastPipelineField.Label",
                last,
                middle,
                margin);
    last =
        wOutPhysicalDatabase =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputPhysicalDatabaseField.Label",
                last,
                middle,
                margin);
    last =
        wOutPhysicalSchema =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputPhysicalSchemaField.Label",
                last,
                middle,
                margin);
    last =
        wOutPhysicalTable =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputPhysicalTableField.Label",
                last,
                middle,
                margin);
    last =
        wOutDvSourceType =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDvSourceTypeField.Label",
                last,
                middle,
                margin);
    last =
        wOutDvSourceIndicator =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDvSourceIndicatorField.Label",
                last,
                middle,
                margin);
    last =
        wOutDvSourceIndicatorField =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDvSourceIndicatorFieldField.Label",
                last,
                middle,
                margin);
    last =
        wOutDvSourceGroup =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDvSourceGroupField.Label",
                last,
                middle,
                margin);
    last =
        wOutDvDeliveryType =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputDvDeliveryTypeField.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldName =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputFieldNameField.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldType =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputFieldTypeField.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldLength =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputFieldLengthField.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldPrecision =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputFieldPrecisionField.Label",
                last,
                middle,
                margin);
    last =
        wOutFieldPrimaryKeyPosition =
            addTextField(
                wRenamesComp,
                "RecordDefinitionInputDialog.OutputFieldPrimaryKeyPositionField.Label",
                last,
                middle,
                margin);

    FormData fdRenames = new FormData();
    fdRenames.left = new FormAttachment(0, 0);
    fdRenames.right = new FormAttachment(100, 0);
    fdRenames.top = new FormAttachment(0, 0);
    fdRenames.bottom = new FormAttachment(last, margin);
    wRenamesComp.setLayoutData(fdRenames);

    wRenamesComp.pack();
    wScroll.setContent(wRenamesComp);
    wScroll.setExpandHorizontal(true);
    wScroll.setExpandVertical(true);
    wScroll.setMinSize(wRenamesComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));

    wTabFolder.setSelection(0);

    // Fetch Connections
    try {
      IHopMetadataSerializer<org.apache.hop.catalog.metadata.DataCatalogMeta> serializer =
          metadataProvider.getSerializer(org.apache.hop.catalog.metadata.DataCatalogMeta.class);
      wConnectionName.setItems(serializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading catalog connections", e);
    }

    // Fetch input fields
    final Runnable runnable =
        () -> {
          TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
          if (transformMeta != null) {
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

                          // restore values
                          wNamespaceField.setText(Const.NVL(input.getNamespaceField(), ""));
                          wNameField.setText(Const.NVL(input.getNameField(), ""));
                        });
              }
            } catch (HopException e) {
              logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
            }
          }
        };
    new Thread(runnable).start();

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  private Text addTextField(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    if (previous == null) {
      fdl.top = new FormAttachment(0, margin);
    } else {
      fdl.top = new FormAttachment(previous, margin);
    }
    label.setLayoutData(fdl);

    Text text = new Text(composite, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    if (previous == null) {
      fd.top = new FormAttachment(0, margin);
    } else {
      fd.top = new FormAttachment(previous, margin);
    }
    text.setLayoutData(fd);
    return text;
  }

  private void setFlags() {
    boolean fromInput = wSelectFromInput.getSelection();
    wNamespaceField.setEnabled(fromInput);
    wNameField.setEnabled(fromInput);
    wNamespaceValue.setEnabled(!fromInput);
    wNameValue.setEnabled(!fromInput);
  }

  public void getData() {
    wConnectionName.setText(Const.NVL(input.getCatalogConnectionName(), ""));
    wSelectFromInput.setSelection(input.isSelectFromInput());
    wNamespaceValue.setText(Const.NVL(input.getNamespaceValue(), ""));
    wNameValue.setText(Const.NVL(input.getNameValue(), ""));
    wOutputFieldsMetadata.setSelection(input.isOutputFieldsMetadata());

    wOutNamespace.setText(Const.NVL(input.getOutputNamespaceField(), ""));
    wOutName.setText(Const.NVL(input.getOutputNameField(), ""));
    wOutType.setText(Const.NVL(input.getOutputTypeField(), ""));
    wOutDescription.setText(Const.NVL(input.getOutputDescriptionField(), ""));
    wOutModelType.setText(Const.NVL(input.getOutputModelTypeField(), ""));
    wOutModelName.setText(Const.NVL(input.getOutputModelNameField(), ""));
    wOutModelFilename.setText(Const.NVL(input.getOutputModelFilenameField(), ""));
    wOutModelElement.setText(Const.NVL(input.getOutputModelElementField(), ""));
    wOutHopProject.setText(Const.NVL(input.getOutputHopProjectField(), ""));
    wOutCreatedAt.setText(Const.NVL(input.getOutputCreatedAtField(), ""));
    wOutUpdatedAt.setText(Const.NVL(input.getOutputUpdatedAtField(), ""));
    wOutUpdatedBy.setText(Const.NVL(input.getOutputUpdatedByField(), ""));
    wOutLastWorkflow.setText(Const.NVL(input.getOutputLastWorkflowField(), ""));
    wOutLastPipeline.setText(Const.NVL(input.getOutputLastPipelineField(), ""));
    wOutPhysicalDatabase.setText(Const.NVL(input.getOutputPhysicalDatabaseField(), ""));
    wOutPhysicalSchema.setText(Const.NVL(input.getOutputPhysicalSchemaField(), ""));
    wOutPhysicalTable.setText(Const.NVL(input.getOutputPhysicalTableField(), ""));
    wOutDvSourceType.setText(Const.NVL(input.getOutputDvSourceTypeField(), ""));
    wOutDvSourceIndicator.setText(Const.NVL(input.getOutputDvSourceIndicatorField(), ""));
    wOutDvSourceIndicatorField.setText(Const.NVL(input.getOutputDvSourceIndicatorFieldField(), ""));
    wOutDvSourceGroup.setText(Const.NVL(input.getOutputDvSourceGroupField(), ""));
    wOutDvDeliveryType.setText(Const.NVL(input.getOutputDvDeliveryTypeField(), ""));
    wOutFieldName.setText(Const.NVL(input.getOutputFieldNameField(), ""));
    wOutFieldType.setText(Const.NVL(input.getOutputFieldTypeField(), ""));
    wOutFieldLength.setText(Const.NVL(input.getOutputFieldLengthField(), ""));
    wOutFieldPrecision.setText(Const.NVL(input.getOutputFieldPrecisionField(), ""));
    wOutFieldPrimaryKeyPosition.setText(
        Const.NVL(input.getOutputFieldPrimaryKeyPositionField(), ""));

    setFlags();
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

    input.setCatalogConnectionName(wConnectionName.getText());
    input.setSelectFromInput(wSelectFromInput.getSelection());
    input.setNamespaceField(wNamespaceField.getText());
    input.setNameField(wNameField.getText());
    input.setNamespaceValue(wNamespaceValue.getText());
    input.setNameValue(wNameValue.getText());
    input.setOutputFieldsMetadata(wOutputFieldsMetadata.getSelection());

    input.setOutputNamespaceField(wOutNamespace.getText());
    input.setOutputNameField(wOutName.getText());
    input.setOutputTypeField(wOutType.getText());
    input.setOutputDescriptionField(wOutDescription.getText());
    input.setOutputModelTypeField(wOutModelType.getText());
    input.setOutputModelNameField(wOutModelName.getText());
    input.setOutputModelFilenameField(wOutModelFilename.getText());
    input.setOutputModelElementField(wOutModelElement.getText());
    input.setOutputHopProjectField(wOutHopProject.getText());
    input.setOutputCreatedAtField(wOutCreatedAt.getText());
    input.setOutputUpdatedAtField(wOutUpdatedAt.getText());
    input.setOutputUpdatedByField(wOutUpdatedBy.getText());
    input.setOutputLastWorkflowField(wOutLastWorkflow.getText());
    input.setOutputLastPipelineField(wOutLastPipeline.getText());
    input.setOutputPhysicalDatabaseField(wOutPhysicalDatabase.getText());
    input.setOutputPhysicalSchemaField(wOutPhysicalSchema.getText());
    input.setOutputPhysicalTableField(wOutPhysicalTable.getText());
    input.setOutputDvSourceTypeField(wOutDvSourceType.getText());
    input.setOutputDvSourceIndicatorField(wOutDvSourceIndicator.getText());
    input.setOutputDvSourceIndicatorFieldField(wOutDvSourceIndicatorField.getText());
    input.setOutputDvSourceGroupField(wOutDvSourceGroup.getText());
    input.setOutputDvDeliveryTypeField(wOutDvDeliveryType.getText());
    input.setOutputFieldNameField(wOutFieldName.getText());
    input.setOutputFieldTypeField(wOutFieldType.getText());
    input.setOutputFieldLengthField(wOutFieldLength.getText());
    input.setOutputFieldPrecisionField(wOutFieldPrecision.getText());
    input.setOutputFieldPrimaryKeyPositionField(wOutFieldPrimaryKeyPosition.getText());

    input.setChanged();
    dispose();
  }
}
