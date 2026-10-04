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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.ShowRowsDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataEditorDialog;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.metadata.MetadataPerspective;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingParentSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingValidationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceMaskingPreviewSupport;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;

/**
 * Dialog for a source masking card. Tabs match the other source cards, including the shared data
 * type mapping tab. The field grid is the contract Mask fields will apply.
 */
public class HopGuiSourceMaskingDialog {

  private static final Class<?> PKG = HopGuiSourceMaskingDialog.class;

  /** TableItem column of the masking pattern. Column 0 is the row number. */
  private static final int PATTERN_COLUMN = 7;

  private final Shell parentShell;
  private final SourceMasking input;
  private final SourceModel model;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private Shell shell;
  private Text wName;
  private Text wDescription;
  private Text wPublishedCatalogName;
  private CCombo wParentKind;
  private CCombo wParentName;
  private TableView wFields;
  private SourceDataTypeMappingTab dataTypeMappingTab;
  private boolean ok;

  public HopGuiSourceMaskingDialog(
      Shell parentShell,
      SourceMasking input,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parentShell = parentShell;
    this.input = input;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiSourceMaskingDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Validate.Button"));
    wValidate.addListener(SWT.Selection, e -> validateDefinition());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Button"));
    wPreview.addListener(SWT.Selection, e -> previewData());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.SOURCE_MASKING);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel, wPreview}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    FormData fdTabs = new FormData();
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.top = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(wOk, -margin);
    wTabFolder.setLayoutData(fdTabs);

    addGeneralTab(wTabFolder, middle, margin);
    addFieldsTab(wTabFolder, margin);
    dataTypeMappingTab =
        new SourceDataTypeMappingTab(
            variables,
            metadataProvider,
            () ->
                org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport
                    .physicalFields(workingCopy()));
    dataTypeMappingTab.addTab(wTabFolder, margin);
    wTabFolder.setSelection(0);

    getData();
    BaseTransformDialog.setSize(shell, 920, 680);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Tab.General.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(form(0, 0, middle, -margin, 0, margin));
    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    EdwNamingWidgetSupport.enableAndLayout(
        wName,
        variables,
        EdwNamingSchemeTypes.SOURCE_MASKING,
        formLeft(middle, 0, 0, margin));

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(belowLabel(wName, middle, margin));
    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(belowField(wName, middle, margin));

    Label wlPublished = new Label(comp, SWT.RIGHT);
    wlPublished.setText(
        BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.PublishedCatalogName.Label"));
    PropsUi.setLook(wlPublished);
    wlPublished.setLayoutData(belowLabel(wDescription, middle, margin));
    wPublishedCatalogName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPublishedCatalogName);
    wPublishedCatalogName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.PublishedCatalogName.ToolTip"));
    wPublishedCatalogName.setLayoutData(belowField(wDescription, middle, margin));

    Label wlKind = new Label(comp, SWT.RIGHT);
    wlKind.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.ParentKind.Label"));
    PropsUi.setLook(wlKind);
    wlKind.setLayoutData(belowLabel(wPublishedCatalogName, middle, margin));
    wParentKind = new CCombo(comp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wParentKind);
    wParentKind.setItems(SourceMaskingParentKind.getDescriptions());
    wParentKind.setLayoutData(belowField(wPublishedCatalogName, middle, margin));
    wParentKind.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            refreshParentNames(wParentName.getText());
          }
        });

    Label wlParent = new Label(comp, SWT.RIGHT);
    wlParent.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.ParentName.Label"));
    PropsUi.setLook(wlParent);
    wlParent.setLayoutData(belowLabel(wParentKind, middle, margin));
    wParentName = new CCombo(comp, SWT.BORDER);
    PropsUi.setLook(wParentName);
    wParentName.setLayoutData(belowField(wParentKind, middle, margin));
  }

  private void addFieldsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Tab.Fields.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wGet = new Button(comp, SWT.PUSH);
    wGet.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.GetFields.Button"));
    PropsUi.setLook(wGet);
    wGet.setLayoutData(new org.apache.hop.ui.core.FormDataBuilder().left().top(0, margin).result());
    wGet.addListener(SWT.Selection, e -> getFieldsFromParent());

    Button wEdit = new Button(comp, SWT.PUSH);
    wEdit.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.Button"));
    PropsUi.setLook(wEdit);
    wEdit.setLayoutData(
        new org.apache.hop.ui.core.FormDataBuilder().left(wGet, margin).top(0, margin).result());
    wEdit.addListener(SWT.Selection, e -> editMaskingPattern());

    String[] hopTypes = ValueMetaFactory.getValueMetaNames();
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.Description"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.HopType"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              hopTypes),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.PrimaryKey"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Columns.Pattern"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              patternNames()),
        };
    // Read the list when the cell opens. A snapshot misses a pattern created while the dialog is up.
    columns[PATTERN_COLUMN - 1].setComboValueSupplier(this::patternNames);
    wFields =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(input.getFields().size(), 1),
            null,
            PropsUi.getInstance());
    wFields.setLayoutData(
        new org.apache.hop.ui.core.FormDataBuilder()
            .left()
            .top(wGet, margin)
            .right()
            .bottom()
            .result());
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wPublishedCatalogName.setText(Const.NVL(input.getPublishedCatalogName(), ""));
    wParentKind.setText(input.resolveParentSourceKind().getDescription());
    refreshParentNames(input.getParentSourceName());
    wFields.clearAll(false);
    for (SourceMaskingField field : input.getFields()) {
      if (field == null) {
        continue;
      }
      TableItem item = new TableItem(wFields.table, SWT.NONE);
      item.setText(1, field.resolveName());
      item.setText(2, Const.NVL(field.getDescription(), ""));
      item.setText(3, hopTypeName(field.getHopType()));
      item.setText(4, field.getLength() >= 0 ? Integer.toString(field.getLength()) : "");
      item.setText(5, field.getPrecision() >= 0 ? Integer.toString(field.getPrecision()) : "");
      item.setText(
          6, field.getPrimaryKeyPosition() > 0 ? Integer.toString(field.getPrimaryKeyPosition()) : "");
      item.setText(7, Const.NVL(field.getPatternName(), ""));
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.loadFrom(input);
    }
  }

  private void ok() {
    String name = wName.getText().trim();
    if (Utils.isEmpty(name)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.MissingName.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.MissingName.Message"));
      box.open();
      return;
    }
    String oldName = input.getName();
    input.setName(name);
    input.setDescription(wDescription.getText());
    input.setPublishedCatalogName(wPublishedCatalogName.getText());
    input.setParentSourceKind(SourceMaskingParentKind.lookupDescription(wParentKind.getText()));
    input.setParentSourceName(wParentName.getText());
    input.setFields(readFields());
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.saveTo(input);
    }
    if (model != null) {
      org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipLifecycleSupport
          .dropRelationshipsOnRename(model, org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind.MASKING, oldName, name);
    }
    ok = true;
    shell.dispose();
  }

  private void cancel() {
    ok = false;
    shell.dispose();
  }

  private List<SourceMaskingField> readFields() {
    List<SourceMaskingField> fields = new ArrayList<>();
    int rows = wFields.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wFields.getNonEmpty(i);
      String fieldName = item.getText(1);
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      SourceMaskingField field = new SourceMaskingField(fieldName.trim());
      field.setDescription(item.getText(2));
      try {
        field.setHopType(ValueMetaFactory.getIdForValueMeta(item.getText(3)));
      } catch (Exception e) {
        field.setHopType(IValueMeta.TYPE_STRING);
      }
      field.setLength(Const.toInt(item.getText(4), -1));
      field.setPrecision(Const.toInt(item.getText(5), -1));
      field.setPrimaryKeyPosition(Const.toInt(item.getText(6), 0));
      field.setPatternName(item.getText(7));
      fields.add(field);
    }
    return fields;
  }

  private void getFieldsFromParent() {
    SourceMasking draft = workingCopy();
    List<SourceMaskingField> copied =
        SourceMaskingParentSupport.copyFields(model, draft, readFields());
    if (copied.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.GetFields.Empty.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.GetFields.Empty.Message"));
      box.open();
      return;
    }
    input.setFields(copied);
    wFields.clearAll(false);
    for (SourceMaskingField field : copied) {
      TableItem item = new TableItem(wFields.table, SWT.NONE);
      item.setText(1, field.resolveName());
      item.setText(2, Const.NVL(field.getDescription(), ""));
      item.setText(3, hopTypeName(field.getHopType()));
      item.setText(4, field.getLength() >= 0 ? Integer.toString(field.getLength()) : "");
      item.setText(5, field.getPrecision() >= 0 ? Integer.toString(field.getPrecision()) : "");
      item.setText(
          6, field.getPrimaryKeyPosition() > 0 ? Integer.toString(field.getPrimaryKeyPosition()) : "");
      item.setText(7, Const.NVL(field.getPatternName(), ""));
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);
  }

  private void validateDefinition() {
    SourceMasking draft = workingCopy();
    var remarks = SourceMaskingValidationSupport.check(draft, model, null, metadataProvider);
    new CheckResultDialog(shell, remarks).open();
  }

  private void previewData() {
    SourceMasking draft = workingCopy();
    try {
      SourceMaskingPreviewSupport.validateForPreview(draft);
      SourceMaskingPreviewSupport.PreviewPipeline built =
          SourceMaskingPreviewSupport.buildPreviewPipeline(
              model, draft, variables, metadataProvider);
      int previewRows =
          draft.getSampleRowLimit() > 0
              ? draft.getSampleRowLimit()
              : SourceMaskingPreviewSupport.DEFAULT_ROW_LIMIT;
      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              shell,
              variables,
              built.pipelineMeta(),
              new String[] {built.previewTransformName()},
              new int[] {previewRows});
      progressDialog.open();
      Pipeline pipeline = progressDialog.getPipeline();
      if (progressDialog.isCancelled()) {
        return;
      }
      if (pipeline != null && pipeline.getResult() != null && pipeline.getResult().getNrErrors() > 0) {
        EnterTextDialog etd =
            new EnterTextDialog(
                shell,
                BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Error.Title"),
                BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Error.Message"),
                progressDialog.getLoggingText(),
                true);
        etd.setReadOnly();
        etd.open();
        return;
      }
      var data = progressDialog.getPreviewRows(built.previewTransformName());
      if (data == null || data.isEmpty()) {
        MessageBox emptyBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(
            BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Message"),
              progressDialog.getPreviewRowsMeta(built.previewTransformName()),
              data)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.Preview.Error.Message"),
          e);
    }
  }

  private void editMaskingPattern() {
    int row = wFields.getSelectionIndex();
    if (row < 0) {
      openPatternType();
      return;
    }
    // The open combo copies its text back onto the row when it loses focus, and its items were
    // captured when the editor opened. Close it before reading or writing the pattern.
    wFields.closeActiveEditors();
    String ruleName = wFields.getItem(row, PATTERN_COLUMN);
    String applied;
    if (Utils.isEmpty(ruleName)) {
      MaskingPattern created = createPattern();
      applied = created == null ? null : created.getName();
    } else {
      applied = editPattern(ruleName.trim());
    }
    wFields.closeActiveEditors();
    if (Utils.isEmpty(applied)) {
      return;
    }
    applyPatternName(row, ruleName, applied.trim());
    wFields.edit(row, PATTERN_COLUMN);
  }

  /** Put {@code appliedName} on the edited row and on every row that still names the old pattern. */
  private void applyPatternName(int row, String previousName, String appliedName) {
    String previous = previousName == null ? "" : previousName.trim();
    int rows = wFields.table.getItemCount();
    for (int i = 0; i < rows; i++) {
      String current = Const.NVL(wFields.getItem(i, PATTERN_COLUMN), "").trim();
      if (i == row || (!previous.isEmpty() && previous.equals(current))) {
        wFields.setText(appliedName, PATTERN_COLUMN, i);
      }
    }
  }

  private void openPatternType() {
    MetadataPerspective perspective = HopGui.getMetadataPerspective();
    if (perspective == null) {
      return;
    }
    perspective.activate();
    perspective.selectType(MaskingPattern.class.getAnnotation(HopMetadata.class).key());
  }

  private MaskingPattern createPattern() {
    try {
      HopGui hopGui = HopGui.getInstance();
      MaskingPattern element = new MaskingPattern();
      if (hopGui != null) {
        ExtensionPointHandler.callExtensionPoint(
            hopGui.getLog(),
            variables,
            HopExtensionPoint.HopGuiMetadataObjectCreateBeforeDialog.id,
            element);
      }
      MetadataEditor<MaskingPattern> editor = patternManager().createEditor(element);
      editor.markAsNew();
      String name = new MetadataEditorDialog(shell, editor).open();
      if (name == null) {
        return null;
      }
      element.setName(name);
      return element;
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.CreateError.Message"),
          e);
      return null;
    }
  }

  private String editPattern(String ruleName) {
    try {
      MaskingPattern element =
          patternMetadataProvider().getSerializer(MaskingPattern.class).load(ruleName);
      if (element == null) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.Error.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "HopGuiSourceMaskingDialog.EditPattern.Missing.Message", ruleName));
        box.open();
        return null;
      }
      MetadataEditor<MaskingPattern> editor = patternManager().createEditor(element);
      return new MetadataEditorDialog(shell, editor).open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceMaskingDialog.EditPattern.EditError.Message"),
          e);
      return null;
    }
  }

  private MetadataManager<MaskingPattern> patternManager() {
    return new MetadataManager<>(variables, patternMetadataProvider(), MaskingPattern.class, shell);
  }

  private IHopMetadataProvider patternMetadataProvider() {
    if (metadataProvider != null) {
      return metadataProvider;
    }
    HopGui hopGui = HopGui.getInstance();
    return hopGui == null ? null : hopGui.getMetadataProvider();
  }

  private String[] patternNames() {
    IHopMetadataProvider provider = patternMetadataProvider();
    if (provider == null) {
      return new String[0];
    }
    try {
      List<String> names =
          new ArrayList<>(provider.getSerializer(MaskingPattern.class).listObjectNames());
      names.sort(String::compareToIgnoreCase);
      return names.toArray(new String[0]);
    } catch (HopException e) {
      return new String[0];
    }
  }

  private SourceMasking workingCopy() {
    SourceMasking draft = new SourceMasking(wName.getText().trim());
    draft.setDescription(wDescription.getText());
    draft.setPublishedCatalogName(wPublishedCatalogName.getText());
    draft.setParentSourceKind(SourceMaskingParentKind.lookupDescription(wParentKind.getText()));
    draft.setParentSourceName(wParentName.getText());
    draft.setFields(readFields());
    draft.setSampleRowLimit(input.getSampleRowLimit());
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.saveTo(draft);
    } else {
      draft.setDataTypeMappingNames(new ArrayList<>(input.getDataTypeMappingNames()));
      draft.setFieldTypeMappings(new ArrayList<>(input.getFieldTypeMappings()));
    }
    return draft;
  }

  private void refreshParentNames(String preferred) {
    SourceMaskingParentKind kind = SourceMaskingParentKind.lookupDescription(wParentKind.getText());
    List<String> names = new ArrayList<>();
    if (model != null) {
      switch (kind) {
        case TABLE ->
            model.getTables().forEach(
                table -> {
                  if (table != null && !Utils.isEmpty(table.getName())) {
                    names.add(table.getName());
                  }
                });
        case QUERY ->
            model.getQueries().forEach(
                query -> {
                  if (query != null && !Utils.isEmpty(query.getName())) {
                    names.add(query.getName());
                  }
                });
        case JSON ->
            model.getJsonSources().forEach(
                json -> {
                  if (json != null && !Utils.isEmpty(json.getName())) {
                    names.add(json.getName());
                  }
                });
        case PIPELINE ->
            model.getPipelineSources().forEach(
                pipeline -> {
                  if (pipeline != null && !Utils.isEmpty(pipeline.getName())) {
                    names.add(pipeline.getName());
                  }
                });
        case MASKING ->
            model.getMaskingSources().forEach(
                masking -> {
                  if (masking != null
                      && !Utils.isEmpty(masking.getName())
                      && !masking.getName().equals(input.getName())) {
                    names.add(masking.getName());
                  }
                });
      }
    }
    wParentName.setItems(names.toArray(new String[0]));
    if (!Utils.isEmpty(preferred)) {
      wParentName.setText(preferred);
    } else if (!names.isEmpty()) {
      wParentName.setText(names.get(0));
    }
  }

  private static String hopTypeName(int hopType) {
    try {
      return ValueMetaFactory.getValueMetaName(hopType);
    } catch (Exception e) {
      return ValueMetaFactory.getValueMetaName(IValueMeta.TYPE_STRING);
    }
  }

  private static FormData form(
      int left, int leftOffset, int right, int rightOffset, int topControl, int topOffset) {
    FormData data = new FormData();
    data.left = new FormAttachment(left, leftOffset);
    data.right = new FormAttachment(right, rightOffset);
    data.top = new FormAttachment(topControl, topOffset);
    return data;
  }

  private static FormData formLeft(int leftPercent, int leftOffset, int topWidget, int topOffset) {
    FormData data = new FormData();
    data.left = new FormAttachment(leftPercent, leftOffset);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(topWidget, topOffset);
    return data;
  }

  private static FormData belowLabel(org.eclipse.swt.widgets.Control above, int middle, int margin) {
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.right = new FormAttachment(middle, -margin);
    data.top = new FormAttachment(above, margin);
    return data;
  }

  private static FormData belowField(org.eclipse.swt.widgets.Control above, int middle, int margin) {
    FormData data = new FormData();
    data.left = new FormAttachment(middle, 0);
    data.right = new FormAttachment(100, 0);
    data.top = new FormAttachment(above, margin);
    return data;
  }
}
