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
package org.apache.hop.catalog.transform.recordoutput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class RecordDefinitionOutputDialog extends BaseTransformDialog {

  private static final Class<?> PKG = RecordDefinitionOutputMeta.class;

  private final RecordDefinitionOutputMeta input;

  private org.eclipse.swt.custom.CCombo wConnectionName;
  private Combo wRecordType;
  private Button wSelectFromInput;
  private Button wWriteToCatalog;
  private Button wFailIfNoFields;
  private org.eclipse.swt.custom.CCombo wNamespaceField;
  private org.eclipse.swt.custom.CCombo wNameField;
  private org.eclipse.swt.custom.CCombo wDescriptionField;
  private Text wNamespaceValue;
  private Text wNameValue;
  private Text wDescriptionValue;
  private Combo wSourceType;
  private org.eclipse.swt.custom.CCombo wDatabaseConnection;
  private Text wSchemaName;
  private Text wTableName;
  private org.eclipse.swt.custom.CCombo wDatabaseConnectionField;
  private org.eclipse.swt.custom.CCombo wSchemaField;
  private org.eclipse.swt.custom.CCombo wTableField;
  private Text wFilePath;
  private Text wFolder;
  private Text wIncludeFileMask;
  private Text wExcludeFileMask;
  private Button wIncludeSubfolders;
  private org.eclipse.swt.custom.CCombo wFilePathField;
  private org.eclipse.swt.custom.CCombo wFolderField;
  private org.eclipse.swt.custom.CCombo wIncludeFileMaskField;
  private Composite wFileComp;
  private Composite wIcebergComp;
  private Text wIcebergCatalogUri;
  private Text wIcebergWarehouse;
  private Text wIcebergNamespace;
  private Text wIcebergTableName;
  private Text wIcebergSnapshotId;
  private Text wIcebergBranch;
  private Text wIcebergS3Endpoint;
  private Text wIcebergS3AccessKey;
  private Text wIcebergS3SecretKey;
  private org.eclipse.swt.custom.CCombo wIcebergCatalogUriField;
  private org.eclipse.swt.custom.CCombo wIcebergWarehouseField;
  private org.eclipse.swt.custom.CCombo wIcebergNamespaceField;
  private org.eclipse.swt.custom.CCombo wIcebergTableNameField;
  private org.eclipse.swt.custom.CCombo wIcebergSnapshotIdField;
  private org.eclipse.swt.custom.CCombo wIcebergBranchField;
  private org.eclipse.swt.custom.CCombo wIcebergS3EndpointField;
  private org.eclipse.swt.custom.CCombo wIcebergS3AccessKeyField;
  private org.eclipse.swt.custom.CCombo wIcebergS3SecretKeyField;
  private Text wSourceIndicator;
  private Text wSourceIndicatorFieldName;
  private Text wGroup;
  private Combo wDeliveryType;
  private org.eclipse.swt.custom.CCombo wDeliveryTypeField;
  private Button wFieldsFromInput;
  private org.eclipse.swt.custom.CCombo wFieldGroupingField;
  private org.eclipse.swt.custom.CCombo wFieldNameField;
  private org.eclipse.swt.custom.CCombo wFieldTypeField;
  private org.eclipse.swt.custom.CCombo wFieldLengthField;
  private org.eclipse.swt.custom.CCombo wFieldPrecisionField;
  private org.eclipse.swt.custom.CCombo wFieldPrimaryKeyPositionField;
  private org.eclipse.swt.custom.CCombo wFieldFormatField;
  private org.eclipse.swt.custom.CCombo wFieldDecimalField;
  private org.eclipse.swt.custom.CCombo wFieldGroupingSymbolField;
  private Text wFieldCountField;
  private Text wWrittenToCatalogField;
  private Text wCatalogNamespaceField;
  private Text wCatalogNameField;

  private final List<String> inputFields = new ArrayList<>();

  public RecordDefinitionOutputDialog(
      Shell parent,
      IVariables variables,
      RecordDefinitionOutputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.Shell.Title"));
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    buildGeneralTab(wTabFolder);
    buildDefinitionTab(wTabFolder);
    buildFieldsTab(wTabFolder);
    buildPhysicalTableTab(wTabFolder);
    buildSourceTab(wTabFolder);
    buildDvSourceTab(wTabFolder);
    buildOutputTab(wTabFolder);
    wTabFolder.setSelection(0);

    loadCatalogConnections();
    loadDatabaseConnections();
    loadInputFields();

    getData();
    setFlags();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void buildGeneralTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.GeneralTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;

    last =
        wConnectionName =
            addCombo(
                comp,
                "RecordDefinitionOutputDialog.ConnectionName.Label",
                last,
                middle,
                margin,
                new String[0]);
    last =
        wRecordType =
            addEnumCombo(
                comp,
                "RecordDefinitionOutputDialog.RecordType.Label",
                last,
                middle,
                margin,
                RecordDefinitionType.values());
    last =
        wSelectFromInput =
            addCheckbox(
                comp, "RecordDefinitionOutputDialog.SelectFromInput.Label", last, middle, margin);
    wSelectFromInput.addListener(SWT.Selection, e -> setFlags());
    last =
        wWriteToCatalog =
            addCheckbox(
                comp, "RecordDefinitionOutputDialog.WriteToCatalog.Label", last, middle, margin);
    last =
        wFailIfNoFields =
            addCheckbox(
                comp, "RecordDefinitionOutputDialog.FailIfNoFields.Label", last, middle, margin);
  }

  private void buildDefinitionTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.DefinitionTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wNamespaceField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.NamespaceField.Label", last, middle, margin);
    last =
        wNameField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.NameField.Label", last, middle, margin);
    last =
        wDescriptionField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.DescriptionField.Label", last, middle, margin);
    last =
        wNamespaceValue =
            addTextField(
                comp, "RecordDefinitionOutputDialog.NamespaceValue.Label", last, middle, margin);
    last =
        wNameValue =
            addTextField(
                comp, "RecordDefinitionOutputDialog.NameValue.Label", last, middle, margin);
    last =
        wDescriptionValue =
            addTextField(
                comp, "RecordDefinitionOutputDialog.DescriptionValue.Label", last, middle, margin);
  }

  private void buildFieldsTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.FieldsTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wFieldsFromInput =
            addCheckbox(
                comp, "RecordDefinitionOutputDialog.FieldsFromInput.Label", last, middle, margin);
    wFieldsFromInput.addListener(SWT.Selection, e -> setFlags());
    last =
        wFieldGroupingField =
            addFieldCombo(
                comp,
                "RecordDefinitionOutputDialog.FieldGroupingField.Label",
                last,
                middle,
                margin);
    last =
        wFieldNameField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.FieldNameField.Label", last, middle, margin);
    last =
        wFieldTypeField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.FieldTypeField.Label", last, middle, margin);
    last =
        wFieldLengthField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.FieldLengthField.Label", last, middle, margin);
    last =
        wFieldPrecisionField =
            addFieldCombo(
                comp,
                "RecordDefinitionOutputDialog.FieldPrecisionField.Label",
                last,
                middle,
                margin);
    last =
        wFieldPrimaryKeyPositionField =
            addFieldCombo(
                comp,
                "RecordDefinitionOutputDialog.FieldPrimaryKeyPositionField.Label",
                last,
                middle,
                margin);
    last =
        wFieldFormatField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.FieldFormatField.Label", last, middle, margin);
    last =
        wFieldDecimalField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.FieldDecimalField.Label", last, middle, margin);
    last =
        wFieldGroupingSymbolField =
            addFieldCombo(
                comp,
                "RecordDefinitionOutputDialog.FieldGroupingSymbolField.Label",
                last,
                middle,
                margin);
  }

  private void buildPhysicalTableTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.PhysicalTableTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wDatabaseConnection =
            addCombo(
                comp,
                "RecordDefinitionOutputDialog.DatabaseConnection.Label",
                last,
                middle,
                margin,
                new String[0]);
    last =
        wSchemaName =
            addTextField(
                comp, "RecordDefinitionOutputDialog.SchemaName.Label", last, middle, margin);
    last =
        wTableName =
            addTextField(
                comp, "RecordDefinitionOutputDialog.TableName.Label", last, middle, margin);
    last =
        wDatabaseConnectionField =
            addFieldCombo(
                comp,
                "RecordDefinitionOutputDialog.DatabaseConnectionField.Label",
                last,
                middle,
                margin);
    last =
        wSchemaField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.SchemaField.Label", last, middle, margin);
    last =
        wTableField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.TableField.Label", last, middle, margin);
  }

  private void buildSourceTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.SourceTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wSourceType =
            addI18nEnumCombo(
                comp,
                "RecordDefinitionOutputDialog.SourceType.Label",
                last,
                middle,
                margin,
                DvSourceType.class);
    wSourceType.addModifyListener((ModifyListener) e -> updateSourcePanels());

    wFileComp = new Composite(comp, SWT.NONE);
    PropsUi.setLook(wFileComp);
    wFileComp.setLayout(new FormLayout());
    FormData fdFile = new FormData();
    fdFile.left = new FormAttachment(0, 0);
    fdFile.right = new FormAttachment(100, 0);
    fdFile.top = new FormAttachment(last, margin);
    wFileComp.setLayoutData(fdFile);
    Control fileLast = null;
    fileLast =
        wFilePath =
            addTextField(
                wFileComp, "RecordDefinitionOutputDialog.FilePath.Label", fileLast, middle, margin);
    fileLast =
        wFolder =
            addTextField(
                wFileComp, "RecordDefinitionOutputDialog.Folder.Label", fileLast, middle, margin);
    fileLast =
        wIncludeFileMask =
            addTextField(
                wFileComp,
                "RecordDefinitionOutputDialog.IncludeFileMask.Label",
                fileLast,
                middle,
                margin);
    fileLast =
        wExcludeFileMask =
            addTextField(
                wFileComp,
                "RecordDefinitionOutputDialog.ExcludeFileMask.Label",
                fileLast,
                middle,
                margin);
    fileLast =
        wIncludeSubfolders =
            addCheckbox(
                wFileComp,
                "RecordDefinitionOutputDialog.IncludeSubfolders.Label",
                fileLast,
                middle,
                margin);
    fileLast =
        wFilePathField =
            addFieldCombo(
                wFileComp,
                "RecordDefinitionOutputDialog.FilePathField.Label",
                fileLast,
                middle,
                margin);
    fileLast =
        wFolderField =
            addFieldCombo(
                wFileComp,
                "RecordDefinitionOutputDialog.FolderField.Label",
                fileLast,
                middle,
                margin);
    fileLast =
        wIncludeFileMaskField =
            addFieldCombo(
                wFileComp,
                "RecordDefinitionOutputDialog.IncludeFileMaskField.Label",
                fileLast,
                middle,
                margin);
    fdFile.bottom = new FormAttachment(fileLast, margin);

    wIcebergComp = new Composite(comp, SWT.NONE);
    PropsUi.setLook(wIcebergComp);
    wIcebergComp.setLayout(new FormLayout());
    FormData fdIceberg = new FormData();
    fdIceberg.left = new FormAttachment(0, 0);
    fdIceberg.right = new FormAttachment(100, 0);
    fdIceberg.top = new FormAttachment(wFileComp, margin);
    wIcebergComp.setLayoutData(fdIceberg);
    Control icebergLast = null;
    icebergLast =
        wIcebergCatalogUri =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergCatalogUri.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergWarehouse =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergWarehouse.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergNamespace =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergNamespace.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergTableName =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergTableName.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergSnapshotId =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergSnapshotId.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergBranch =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergBranch.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3Endpoint =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3Endpoint.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3AccessKey =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3AccessKey.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3SecretKey =
            addTextField(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3SecretKey.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergCatalogUriField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergCatalogUriField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergWarehouseField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergWarehouseField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergNamespaceField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergNamespaceField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergTableNameField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergTableNameField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergSnapshotIdField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergSnapshotIdField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergBranchField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergBranchField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3EndpointField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3EndpointField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3AccessKeyField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3AccessKeyField.Label",
                icebergLast,
                middle,
                margin);
    icebergLast =
        wIcebergS3SecretKeyField =
            addFieldCombo(
                wIcebergComp,
                "RecordDefinitionOutputDialog.IcebergS3SecretKeyField.Label",
                icebergLast,
                middle,
                margin);
    fdIceberg.bottom = new FormAttachment(icebergLast, margin);
  }

  private void buildDvSourceTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.DvSourceTab.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wSourceIndicator =
            addTextField(
                comp, "RecordDefinitionOutputDialog.SourceIndicator.Label", last, middle, margin);
    last =
        wSourceIndicatorFieldName =
            addTextField(
                comp,
                "RecordDefinitionOutputDialog.SourceIndicatorField.Label",
                last,
                middle,
                margin);
    last =
        wGroup =
            addTextField(comp, "RecordDefinitionOutputDialog.Group.Label", last, middle, margin);
    last =
        wDeliveryType =
            addI18nEnumCombo(
                comp,
                "RecordDefinitionOutputDialog.DeliveryType.Label",
                last,
                middle,
                margin,
                DvSourceDeliveryType.class);
    last =
        wDeliveryTypeField =
            addFieldCombo(
                comp, "RecordDefinitionOutputDialog.DeliveryTypeField.Label", last, middle, margin);
  }

  private void buildOutputTab(CTabFolder tabFolder) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "RecordDefinitionOutputDialog.OutputTab.Label"));
    ScrolledComposite scroll = new ScrolledComposite(tabFolder, SWT.V_SCROLL | SWT.H_SCROLL);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    Composite comp = new Composite(scroll, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    scroll.setContent(comp);

    int middle = props.getMiddlePct();
    Control last = null;
    last =
        wFieldCountField =
            addTextField(
                comp, "RecordDefinitionOutputDialog.FieldCountField.Label", last, middle, margin);
    last =
        wWrittenToCatalogField =
            addTextField(
                comp,
                "RecordDefinitionOutputDialog.WrittenToCatalogField.Label",
                last,
                middle,
                margin);
    last =
        wCatalogNamespaceField =
            addTextField(
                comp,
                "RecordDefinitionOutputDialog.CatalogNamespaceField.Label",
                last,
                middle,
                margin);
    last =
        wCatalogNameField =
            addTextField(
                comp, "RecordDefinitionOutputDialog.CatalogNameField.Label", last, middle, margin);

    FormData fdComp = new FormData();
    fdComp.left = new FormAttachment(0, 0);
    fdComp.right = new FormAttachment(100, 0);
    fdComp.top = new FormAttachment(0, 0);
    fdComp.bottom = new FormAttachment(last, margin);
    comp.setLayoutData(fdComp);
    comp.pack();
    scroll.setMinSize(comp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    tab.setControl(scroll);
  }

  private org.eclipse.swt.custom.CCombo addCombo(
      Composite composite,
      String labelKey,
      Control previous,
      int middle,
      int margin,
      String[] items) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    org.eclipse.swt.custom.CCombo combo = new org.eclipse.swt.custom.CCombo(composite, SWT.BORDER);
    PropsUi.setLook(combo);
    combo.setItems(items);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    combo.setLayoutData(fd);
    return combo;
  }

  private org.eclipse.swt.custom.CCombo addFieldCombo(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    return addCombo(composite, labelKey, previous, middle, margin, new String[0]);
  }

  private <E extends Enum<E> & IEnumHasCodeAndDescription> Combo addI18nEnumCombo(
      Composite composite,
      String labelKey,
      Control previous,
      int middle,
      int margin,
      Class<E> enumClass) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Combo combo = new Combo(composite, SWT.READ_ONLY);
    PropsUi.setLook(combo);
    EnumDialogSupport.populateCombo(combo, enumClass);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    combo.setLayoutData(fd);
    return combo;
  }

  private Combo addEnumCombo(
      Composite composite,
      String labelKey,
      Control previous,
      int middle,
      int margin,
      Enum<?>[] values) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Combo combo = new Combo(composite, SWT.READ_ONLY);
    PropsUi.setLook(combo);
    String[] labels = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      labels[i] = values[i].name();
    }
    combo.setItems(labels);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    combo.setLayoutData(fd);
    return combo;
  }

  private Text addTextField(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    Label label = new Label(composite, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);

    Text text = new Text(composite, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = fdl.top;
    text.setLayoutData(fd);
    return text;
  }

  private Button addCheckbox(
      Composite composite, String labelKey, Control previous, int middle, int margin) {
    Button button = new Button(composite, SWT.CHECK);
    button.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    button.setLayoutData(fd);
    return button;
  }

  private void loadCatalogConnections() {
    try {
      IHopMetadataSerializer<org.apache.hop.catalog.metadata.DataCatalogMeta> serializer =
          metadataProvider.getSerializer(org.apache.hop.catalog.metadata.DataCatalogMeta.class);
      wConnectionName.setItems(serializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading catalog connections", e);
    }
  }

  private void loadDatabaseConnections() {
    try {
      IHopMetadataSerializer<DatabaseMeta> serializer =
          metadataProvider.getSerializer(DatabaseMeta.class);
      wDatabaseConnection.setItems(serializer.listObjectNames().toArray(new String[0]));
    } catch (Exception e) {
      logError("Error loading database connections", e);
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
                  String[] fieldNames = org.apache.hop.ui.core.ConstUi.sortFieldNames(inputFields);
                  shell.getDisplay().asyncExec(() -> populateFieldCombos(fieldNames));
                }
              } catch (HopException e) {
                logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
              }
            })
        .start();
  }

  private void populateFieldCombos(String[] fieldNames) {
    org.eclipse.swt.custom.CCombo[] combos = {
      wNamespaceField,
      wNameField,
      wDescriptionField,
      wDatabaseConnectionField,
      wSchemaField,
      wTableField,
      wFilePathField,
      wFolderField,
      wIncludeFileMaskField,
      wIcebergCatalogUriField,
      wIcebergWarehouseField,
      wIcebergNamespaceField,
      wIcebergTableNameField,
      wIcebergSnapshotIdField,
      wIcebergBranchField,
      wIcebergS3EndpointField,
      wIcebergS3AccessKeyField,
      wIcebergS3SecretKeyField,
      wDeliveryTypeField,
      wFieldGroupingField,
      wFieldNameField,
      wFieldTypeField,
      wFieldLengthField,
      wFieldPrecisionField,
      wFieldPrimaryKeyPositionField,
      wFieldFormatField,
      wFieldDecimalField,
      wFieldGroupingSymbolField
    };
    for (org.eclipse.swt.custom.CCombo combo : combos) {
      combo.setItems(fieldNames);
    }
    getData();
    setFlags();
  }

  private void updateSourcePanels() {
    DvSourceType type =
        EnumDialogSupport.readCombo(wSourceType, DvSourceType.class, DvSourceType.CSV);
    boolean iceberg = type == DvSourceType.ICEBERG;
    boolean file = type == DvSourceType.CSV || type == DvSourceType.PARQUET;
    wFileComp.setVisible(file);
    wIcebergComp.setVisible(iceberg);
    if (wFileComp.getParent() != null) {
      wFileComp.getParent().layout();
    }
  }

  private void setFlags() {
    boolean fieldsFromInput = wFieldsFromInput.getSelection();
    if (fieldsFromInput && !wSelectFromInput.getSelection()) {
      wSelectFromInput.setSelection(true);
    }
    boolean fromInput = wSelectFromInput.getSelection() || fieldsFromInput;

    wNamespaceField.setEnabled(fromInput);
    wNameField.setEnabled(fromInput);
    wDescriptionField.setEnabled(fromInput);
    // Physical table: field mappings always usable; values from the first row of each group win
    // when present. Fixed connection/schema/table remain available as fallbacks.
    wDatabaseConnectionField.setEnabled(true);
    wSchemaField.setEnabled(true);
    wTableField.setEnabled(true);
    wDatabaseConnection.setEnabled(true);
    wSchemaName.setEnabled(true);
    wTableName.setEnabled(true);
    wFilePathField.setEnabled(fromInput);
    wFolderField.setEnabled(fromInput);
    wIncludeFileMaskField.setEnabled(fromInput);
    wIcebergCatalogUriField.setEnabled(fromInput);
    wIcebergWarehouseField.setEnabled(fromInput);
    wIcebergNamespaceField.setEnabled(fromInput);
    wIcebergTableNameField.setEnabled(fromInput);
    wIcebergSnapshotIdField.setEnabled(fromInput);
    wIcebergBranchField.setEnabled(fromInput);
    wIcebergS3EndpointField.setEnabled(fromInput);
    wIcebergS3AccessKeyField.setEnabled(fromInput);
    wIcebergS3SecretKeyField.setEnabled(fromInput);
    wDeliveryTypeField.setEnabled(fromInput);
    wNamespaceValue.setEnabled(!fromInput);
    wNameValue.setEnabled(!fromInput);
    wDescriptionValue.setEnabled(!fromInput);
    wFilePath.setEnabled(!fromInput);
    wFolder.setEnabled(!fromInput);
    wIncludeFileMask.setEnabled(!fromInput);
    wExcludeFileMask.setEnabled(!fromInput);
    wIncludeSubfolders.setEnabled(!fromInput);
    wIcebergCatalogUri.setEnabled(!fromInput);
    wIcebergWarehouse.setEnabled(!fromInput);
    wIcebergNamespace.setEnabled(!fromInput);
    wIcebergTableName.setEnabled(!fromInput);
    wIcebergSnapshotId.setEnabled(!fromInput);
    wIcebergBranch.setEnabled(!fromInput);
    wIcebergS3Endpoint.setEnabled(!fromInput);
    wIcebergS3AccessKey.setEnabled(!fromInput);
    wIcebergS3SecretKey.setEnabled(!fromInput);

    wFieldGroupingField.setEnabled(fieldsFromInput);
    wFieldNameField.setEnabled(fieldsFromInput);
    wFieldTypeField.setEnabled(fieldsFromInput);
    wFieldLengthField.setEnabled(fieldsFromInput);
    wFieldPrecisionField.setEnabled(fieldsFromInput);
    wFieldPrimaryKeyPositionField.setEnabled(fieldsFromInput);
    wFieldFormatField.setEnabled(fieldsFromInput);
    wFieldDecimalField.setEnabled(fieldsFromInput);
    wFieldGroupingSymbolField.setEnabled(fieldsFromInput);

    updateSourcePanels();
  }

  public void getData() {
    if (wConnectionName == null) {
      return;
    }
    wConnectionName.setText(Const.NVL(input.getCatalogConnectionName(), ""));
    selectEnum(wRecordType, input.getRecordDefinitionType());
    wSelectFromInput.setSelection(input.isSelectFromInput());
    wWriteToCatalog.setSelection(input.isWriteToCatalog());
    wFailIfNoFields.setSelection(input.isFailIfNoFields());
    wNamespaceField.setText(Const.NVL(input.getNamespaceField(), ""));
    wNameField.setText(Const.NVL(input.getNameField(), ""));
    wDescriptionField.setText(Const.NVL(input.getDescriptionField(), ""));
    wNamespaceValue.setText(Const.NVL(input.getNamespaceValue(), ""));
    wNameValue.setText(Const.NVL(input.getNameValue(), ""));
    wDescriptionValue.setText(Const.NVL(input.getDescriptionValue(), ""));
    EnumDialogSupport.selectCombo(wSourceType, input.getSourceType());
    wDatabaseConnection.setText(Const.NVL(input.getDatabaseConnectionName(), ""));
    wSchemaName.setText(Const.NVL(input.getSchemaName(), ""));
    wTableName.setText(Const.NVL(input.getTableName(), ""));
    wDatabaseConnectionField.setText(Const.NVL(input.getDatabaseConnectionField(), ""));
    wSchemaField.setText(Const.NVL(input.getSchemaField(), ""));
    wTableField.setText(Const.NVL(input.getTableField(), ""));
    wFilePath.setText(Const.NVL(input.getFilePath(), ""));
    wFolder.setText(Const.NVL(input.getFolder(), ""));
    wIncludeFileMask.setText(Const.NVL(input.getIncludeFileMask(), ""));
    wExcludeFileMask.setText(Const.NVL(input.getExcludeFileMask(), ""));
    wIncludeSubfolders.setSelection(input.isIncludeSubfolders());
    wFilePathField.setText(Const.NVL(input.getFilePathField(), ""));
    wFolderField.setText(Const.NVL(input.getFolderField(), ""));
    wIncludeFileMaskField.setText(Const.NVL(input.getIncludeFileMaskField(), ""));
    wIcebergCatalogUri.setText(Const.NVL(input.getIcebergCatalogUri(), ""));
    wIcebergWarehouse.setText(Const.NVL(input.getIcebergWarehouse(), ""));
    wIcebergNamespace.setText(Const.NVL(input.getIcebergNamespace(), ""));
    wIcebergTableName.setText(Const.NVL(input.getIcebergTableName(), ""));
    wIcebergSnapshotId.setText(Const.NVL(input.getIcebergSnapshotId(), ""));
    wIcebergBranch.setText(Const.NVL(input.getIcebergBranch(), ""));
    wIcebergS3Endpoint.setText(Const.NVL(input.getIcebergS3Endpoint(), ""));
    wIcebergS3AccessKey.setText(Const.NVL(input.getIcebergS3AccessKey(), ""));
    wIcebergS3SecretKey.setText(Const.NVL(input.getIcebergS3SecretKey(), ""));
    wIcebergCatalogUriField.setText(Const.NVL(input.getIcebergCatalogUriField(), ""));
    wIcebergWarehouseField.setText(Const.NVL(input.getIcebergWarehouseField(), ""));
    wIcebergNamespaceField.setText(Const.NVL(input.getIcebergNamespaceField(), ""));
    wIcebergTableNameField.setText(Const.NVL(input.getIcebergTableNameField(), ""));
    wIcebergSnapshotIdField.setText(Const.NVL(input.getIcebergSnapshotIdField(), ""));
    wIcebergBranchField.setText(Const.NVL(input.getIcebergBranchField(), ""));
    wIcebergS3EndpointField.setText(Const.NVL(input.getIcebergS3EndpointField(), ""));
    wIcebergS3AccessKeyField.setText(Const.NVL(input.getIcebergS3AccessKeyField(), ""));
    wIcebergS3SecretKeyField.setText(Const.NVL(input.getIcebergS3SecretKeyField(), ""));
    wSourceIndicator.setText(Const.NVL(input.getSourceIndicator(), ""));
    wSourceIndicatorFieldName.setText(Const.NVL(input.getSourceIndicatorField(), ""));
    wGroup.setText(Const.NVL(input.getGroup(), ""));
    EnumDialogSupport.selectCombo(wDeliveryType, input.getDeliveryType());
    wDeliveryTypeField.setText(Const.NVL(input.getDeliveryTypeField(), ""));
    wFieldsFromInput.setSelection(input.isFieldsFromInput());
    wFieldGroupingField.setText(Const.NVL(input.getFieldGroupingField(), ""));
    wFieldNameField.setText(Const.NVL(input.getFieldNameField(), ""));
    wFieldTypeField.setText(Const.NVL(input.getFieldTypeField(), ""));
    wFieldLengthField.setText(Const.NVL(input.getFieldLengthField(), ""));
    wFieldPrecisionField.setText(Const.NVL(input.getFieldPrecisionField(), ""));
    wFieldPrimaryKeyPositionField.setText(Const.NVL(input.getFieldPrimaryKeyPositionField(), ""));
    wFieldFormatField.setText(Const.NVL(input.getFieldFormatField(), ""));
    wFieldDecimalField.setText(Const.NVL(input.getFieldDecimalField(), ""));
    wFieldGroupingSymbolField.setText(Const.NVL(input.getFieldGroupingSymbolField(), ""));
    wFieldCountField.setText(Const.NVL(input.getFieldCountField(), ""));
    wWrittenToCatalogField.setText(Const.NVL(input.getWrittenToCatalogField(), ""));
    wCatalogNamespaceField.setText(Const.NVL(input.getCatalogNamespaceField(), ""));
    wCatalogNameField.setText(Const.NVL(input.getCatalogNameField(), ""));
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
    input.setRecordDefinitionType(
        parseEnum(
            wRecordType.getText(), RecordDefinitionType.class, RecordDefinitionType.DV_SOURCE));
    input.setSelectFromInput(wSelectFromInput.getSelection());
    input.setWriteToCatalog(wWriteToCatalog.getSelection());
    input.setFailIfNoFields(wFailIfNoFields.getSelection());
    input.setNamespaceField(wNamespaceField.getText());
    input.setNameField(wNameField.getText());
    input.setDescriptionField(wDescriptionField.getText());
    input.setNamespaceValue(wNamespaceValue.getText());
    input.setNameValue(wNameValue.getText());
    input.setDescriptionValue(wDescriptionValue.getText());
    input.setSourceType(
        EnumDialogSupport.readCombo(wSourceType, DvSourceType.class, DvSourceType.CSV));
    input.setDatabaseConnectionName(wDatabaseConnection.getText());
    input.setSchemaName(wSchemaName.getText());
    input.setTableName(wTableName.getText());
    input.setDatabaseConnectionField(wDatabaseConnectionField.getText());
    input.setSchemaField(wSchemaField.getText());
    input.setTableField(wTableField.getText());
    input.setFilePath(wFilePath.getText());
    input.setFolder(wFolder.getText());
    input.setIncludeFileMask(wIncludeFileMask.getText());
    input.setExcludeFileMask(wExcludeFileMask.getText());
    input.setIncludeSubfolders(wIncludeSubfolders.getSelection());
    input.setFilePathField(wFilePathField.getText());
    input.setFolderField(wFolderField.getText());
    input.setIncludeFileMaskField(wIncludeFileMaskField.getText());
    input.setIcebergCatalogUri(wIcebergCatalogUri.getText());
    input.setIcebergWarehouse(wIcebergWarehouse.getText());
    input.setIcebergNamespace(wIcebergNamespace.getText());
    input.setIcebergTableName(wIcebergTableName.getText());
    input.setIcebergSnapshotId(wIcebergSnapshotId.getText());
    input.setIcebergBranch(wIcebergBranch.getText());
    input.setIcebergS3Endpoint(wIcebergS3Endpoint.getText());
    input.setIcebergS3AccessKey(wIcebergS3AccessKey.getText());
    input.setIcebergS3SecretKey(wIcebergS3SecretKey.getText());
    input.setIcebergCatalogUriField(wIcebergCatalogUriField.getText());
    input.setIcebergWarehouseField(wIcebergWarehouseField.getText());
    input.setIcebergNamespaceField(wIcebergNamespaceField.getText());
    input.setIcebergTableNameField(wIcebergTableNameField.getText());
    input.setIcebergSnapshotIdField(wIcebergSnapshotIdField.getText());
    input.setIcebergBranchField(wIcebergBranchField.getText());
    input.setIcebergS3EndpointField(wIcebergS3EndpointField.getText());
    input.setIcebergS3AccessKeyField(wIcebergS3AccessKeyField.getText());
    input.setIcebergS3SecretKeyField(wIcebergS3SecretKeyField.getText());
    input.setSourceIndicator(wSourceIndicator.getText());
    input.setSourceIndicatorField(wSourceIndicatorFieldName.getText());
    input.setGroup(wGroup.getText());
    input.setDeliveryType(
        EnumDialogSupport.readCombo(
            wDeliveryType, DvSourceDeliveryType.class, DvSourceDeliveryType.CHANGES_ONLY));
    input.setDeliveryTypeField(wDeliveryTypeField.getText());
    input.setFieldsFromInput(wFieldsFromInput.getSelection());
    if (wFieldsFromInput.getSelection()) {
      input.setSelectFromInput(true);
    }
    input.setFieldGroupingField(wFieldGroupingField.getText());
    input.setFieldNameField(wFieldNameField.getText());
    input.setFieldTypeField(wFieldTypeField.getText());
    input.setFieldLengthField(wFieldLengthField.getText());
    input.setFieldPrecisionField(wFieldPrecisionField.getText());
    input.setFieldPrimaryKeyPositionField(wFieldPrimaryKeyPositionField.getText());
    input.setFieldFormatField(wFieldFormatField.getText());
    input.setFieldDecimalField(wFieldDecimalField.getText());
    input.setFieldGroupingSymbolField(wFieldGroupingSymbolField.getText());
    input.setFieldCountField(wFieldCountField.getText());
    input.setWrittenToCatalogField(wWrittenToCatalogField.getText());
    input.setCatalogNamespaceField(wCatalogNamespaceField.getText());
    input.setCatalogNameField(wCatalogNameField.getText());
    input.setChanged();
    dispose();
  }

  private static <E extends Enum<E>> void selectEnum(Combo combo, E value) {
    if (combo == null || value == null) {
      return;
    }
    combo.setText(value.name());
  }

  private static <E extends Enum<E>> E parseEnum(String text, Class<E> type, E defaultValue) {
    if (Utils.isEmpty(text)) {
      return defaultValue;
    }
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }
}
