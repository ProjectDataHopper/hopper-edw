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
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Props;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonFieldSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonValidationSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipLifecycleSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceJsonParentSampleSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceJsonPreviewSupport;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceJsonSampleSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to define a {@link SourceJson} extraction (parent source, JSON field, projected fields).
 */
public class HopGuiSourceJsonDialog {

  private static final Class<?> PKG = HopGuiSourceJsonDialog.class;

  private static final int FLD_COL_NAME = 1;
  private static final int FLD_COL_PATH = 2;
  private static final int FLD_COL_PASSTHROUGH = 3;
  private static final int FLD_COL_PARENT_FIELD = 4;
  private static final int FLD_COL_TYPE = 5;
  private static final int FLD_COL_FORMAT = 6;
  private static final int FLD_COL_LENGTH = 7;
  private static final int FLD_COL_PRECISION = 8;
  private static final int FLD_COL_KEY = 9;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final SourceModel model;
  private final SourceJson input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private Text wPublishedCatalogName;
  private Combo wParentKind;
  private Combo wParentName;
  private Combo wJsonField;
  private Button wIgnoreMissingPath;
  private Button wDefaultPathLeafToNull;
  private TableView wFields;
  private ColumnInfo colParentField;
  private SourceDataTypeMappingTab dataTypeMappingTab;

  private boolean ok;

  public HopGuiSourceJsonDialog(
      Shell parent,
      SourceJson jsonSource,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = jsonSource;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiSourceJsonDialog.Title", Const.NVL(input.getName(), "")));
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
    wValidate.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Validate.Button"));
    wValidate.addListener(SWT.Selection, e -> validateDefinition());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Button"));
    wPreview.addListener(SWT.Selection, e -> previewData());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel, wPreview}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder().left().top().right().bottom(wOk, -margin).result());

    addGeneralTab(wTabFolder, middle, margin);
    addFieldsTab(wTabFolder, margin);
    dataTypeMappingTab =
        new SourceDataTypeMappingTab(
            variables,
            metadataProvider,
            () -> SourceDataTypeMappingSupport.physicalFields(workingFromDialog()));
    dataTypeMappingTab.addTab(wTabFolder, margin);

    wTabFolder.setSelection(0);
    getData();
    refreshParentFieldComboValues();

    BaseTransformDialog.setSize(shell, 920, 680);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Tab.General.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlPublished = new Label(comp, SWT.RIGHT);
    wlPublished.setText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.PublishedCatalogName.Label"));
    PropsUi.setLook(wlPublished);
    wlPublished.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());
    wPublishedCatalogName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPublishedCatalogName);
    wPublishedCatalogName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.PublishedCatalogName.ToolTip"));
    wPublishedCatalogName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());

    Label wlParentKind = new Label(comp, SWT.RIGHT);
    wlParentKind.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.ParentKind.Label"));
    PropsUi.setLook(wlParentKind);
    wlParentKind.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wPublishedCatalogName, margin)
            .right(middle, -margin)
            .result());
    wParentKind = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wParentKind);
    wParentKind.setItems(SourceJsonParentKind.getDescriptions());
    wParentKind.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wPublishedCatalogName, margin).right().result());
    wParentKind.addListener(
        SWT.Selection,
        e -> {
          // Kind changed: rebuild name list and pick a valid entry (do not keep a stale name).
          refreshParentNameItems("");
          refreshJsonFieldItems("");
          refreshParentFieldComboValues();
        });

    Label wlParentName = new Label(comp, SWT.RIGHT);
    wlParentName.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.ParentName.Label"));
    PropsUi.setLook(wlParentName);
    wlParentName.setLayoutData(
        new FormDataBuilder().left().top(wParentKind, margin).right(middle, -margin).result());
    wParentName = new Combo(comp, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wParentName);
    wParentName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wParentKind, margin).right().result());
    wParentName.addListener(
        SWT.Selection,
        e -> {
          refreshJsonFieldItems();
          refreshParentFieldComboValues();
        });

    Label wlJsonField = new Label(comp, SWT.RIGHT);
    wlJsonField.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.JsonField.Label"));
    PropsUi.setLook(wlJsonField);
    wlJsonField.setLayoutData(
        new FormDataBuilder().left().top(wParentName, margin).right(middle, -margin).result());
    wJsonField = new Combo(comp, SWT.BORDER);
    PropsUi.setLook(wJsonField);
    wJsonField.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.JsonField.ToolTip"));
    wJsonField.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wParentName, margin).right().result());

    wIgnoreMissingPath = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wIgnoreMissingPath);
    wIgnoreMissingPath.setText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.IgnoreMissingPath.Label"));
    wIgnoreMissingPath.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wJsonField, margin * 2).right().result());

    wDefaultPathLeafToNull = new Button(comp, SWT.CHECK);
    PropsUi.setLook(wDefaultPathLeafToNull);
    wDefaultPathLeafToNull.setText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.DefaultPathLeafToNull.Label"));
    wDefaultPathLeafToNull.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wIgnoreMissingPath, margin).right().result());

    Label wlHint = new Label(comp, SWT.LEFT | SWT.WRAP);
    wlHint.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.General.Hint"));
    PropsUi.setLook(wlHint);
    wlHint.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDefaultPathLeafToNull, margin * 2)
            .right()
            .bottom(100, -margin)
            .result());
  }

  private void addFieldsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Tab.Fields.Label"));
    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wAddParentKeys = new Button(comp, SWT.PUSH);
    wAddParentKeys.setText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.AddParentKeys.Button"));
    wAddParentKeys.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wAddParentKeys.addListener(SWT.Selection, e -> addParentPrimaryKeys());

    Button wSamplePropose = new Button(comp, SWT.PUSH);
    wSamplePropose.setText(
        BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.SamplePropose.Button"));
    wSamplePropose.setLayoutData(
        new FormDataBuilder().left(wAddParentKeys, margin).top(0, margin).result());
    wSamplePropose.addListener(SWT.Selection, e -> sampleAndProposeFields());

    Button wPasteSample = new Button(comp, SWT.PUSH);
    wPasteSample.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.PasteSample.Button"));
    wPasteSample.setLayoutData(
        new FormDataBuilder().left(wSamplePropose, margin).top(0, margin).result());
    wPasteSample.addListener(SWT.Selection, e -> pasteSampleAndPropose());

    Label wlHint = new Label(comp, SWT.LEFT);
    wlHint.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Hint"));
    PropsUi.setLook(wlHint);
    wlHint.setLayoutData(
        new FormDataBuilder().left(wPasteSample, margin * 2).top(0, margin + 4).right().result());

    ColumnInfo colName =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Name"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    ColumnInfo colPath =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Path"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    colPath.setToolTip(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Path.ToolTip"));
    ColumnInfo colPassThrough =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.PassThrough"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {"Y", "N"});
    colParentField =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.ParentField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            parentFieldNames());
    ColumnInfo colType =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Type"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            ValueMetaFactory.getValueMetaNames());
    ColumnInfo colFormat =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Format"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    ColumnInfo colLength =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Length"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    ColumnInfo colPrecision =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Precision"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    ColumnInfo colKey =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Key"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            keyPositionChoices());
    colKey.setToolTip(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Fields.Key.ToolTip"));

    ColumnInfo[] columns =
        new ColumnInfo[] {
          colName,
          colPath,
          colPassThrough,
          colParentField,
          colType,
          colFormat,
          colLength,
          colPrecision,
          colKey
        };
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
        new FormDataBuilder()
            .left()
            .top(wAddParentKeys, margin)
            .right()
            .bottom(100, -margin)
            .result());
  }

  private void sampleAndProposeFields() {
    try {
      SourceJson working = workingFromDialog();
      if (Utils.isEmpty(working.getParentSourceName())
          || Utils.isEmpty(working.getJsonFieldName())) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.NeedParent.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.NeedParent.Message"));
        box.open();
        return;
      }
      int limit =
          working.getSampleRowLimit() > 0
              ? working.getSampleRowLimit()
              : SourceJsonParentSampleSupport.DEFAULT_SAMPLE_ROWS;
      List<String> docs =
          SourceJsonParentSampleSupport.sampleJsonDocuments(
              model, working, variables, metadataProvider, limit);
      if (docs.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Empty.Title"));
        box.setMessage(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Empty.Message"));
        box.open();
        return;
      }
      applyProposedFields(docs);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Error.Message"),
          e);
    }
  }

  private void pasteSampleAndPropose() {
    try {
      EnterTextDialog dialog =
          new EnterTextDialog(
              shell,
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.PasteSample.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.PasteSample.Message"),
              "",
              true);
      String text = dialog.open();
      if (Utils.isEmpty(text)) {
        return;
      }
      applyProposedFields(List.of(text.trim()));
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Error.Message"),
          e);
    }
  }

  private void applyProposedFields(List<String> docs) throws Exception {
    List<String> arrayBases = SourceJsonSampleSupport.discoverArrayBases(docs);
    String focus = null;
    if (arrayBases.size() > 1) {
      EnterSelectionDialog pick =
          new EnterSelectionDialog(
              shell,
              arrayBases.toArray(new String[0]),
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.ArrayFocus.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.ArrayFocus.Message"));
      String selected = pick.open();
      if (selected == null) {
        return;
      }
      focus = selected;
    } else if (arrayBases.size() == 1) {
      focus = arrayBases.get(0);
    }

    List<SourceJsonField> proposed = SourceJsonSampleSupport.proposeFields(docs, focus);
    if (proposed.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.NoPaths.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.NoPaths.Message"));
      box.open();
      return;
    }

    boolean replace = true;
    if (wFields.nrNonEmpty() > 0) {
      MessageBox confirm = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
      confirm.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Replace.Title"));
      confirm.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Sample.Replace.Message"));
      int answer = confirm.open();
      if (answer == SWT.CANCEL) {
        return;
      }
      replace = answer == SWT.YES;
    }

    if (replace) {
      // Keep existing pass-through rows when replacing extracted fields.
      List<SourceJsonField> keepPassThrough = new ArrayList<>();
      int rows = wFields.nrNonEmpty();
      for (int i = 0; i < rows; i++) {
        TableItem item = wFields.getNonEmpty(i);
        if ("Y".equalsIgnoreCase(item.getText(FLD_COL_PASSTHROUGH))) {
          SourceJsonField pt = SourceJsonField.passThroughField(item.getText(FLD_COL_PARENT_FIELD));
          pt.setName(item.getText(FLD_COL_NAME));
          pt.setPrimaryKeyPosition(Const.toInt(item.getText(FLD_COL_KEY), 0));
          int hopType = SourceJsonFieldSupport.hopTypeIdFromLabel(item.getText(FLD_COL_TYPE));
          if (hopType <= 0) {
            hopType =
                SourceJsonFieldSupport.resolveParentFieldHopType(
                    model,
                    SourceJsonParentKind.lookupDescription(wParentKind.getText()),
                    wParentName.getText(),
                    item.getText(FLD_COL_PARENT_FIELD));
          }
          pt.setHopType(hopType);
          keepPassThrough.add(pt);
        }
      }
      wFields.clearAll(false);
      for (SourceJsonField field : keepPassThrough) {
        addFieldToTable(field);
      }
    }
    for (SourceJsonField field : proposed) {
      addFieldToTable(field);
    }
    if (!Utils.isEmpty(focus)) {
      // store array focus on working model for round-trip (applied on OK via getInfo only if we
      // set a field — use input for now so OK persists if user accepts)
      input.setArrayFocusPath(focus);
    }
    wFields.optimizeTableView();
    refreshParentFieldComboValues();
  }

  private void addFieldToTable(SourceJsonField field) {
    if (field == null) {
      return;
    }
    TableItem item = new TableItem(wFields.table, SWT.NONE);
    item.setText(FLD_COL_NAME, Const.NVL(field.getName(), ""));
    item.setText(FLD_COL_PATH, Const.NVL(field.getPath(), ""));
    item.setText(FLD_COL_PASSTHROUGH, field.isPassThrough() ? "Y" : "N");
    item.setText(FLD_COL_PARENT_FIELD, Const.NVL(field.getParentFieldName(), ""));
    int hopType =
        SourceJsonFieldSupport.resolveEffectiveHopType(model, workingParentContext(), field);
    item.setText(FLD_COL_TYPE, SourceJsonFieldSupport.hopTypeLabel(hopType));
    item.setText(FLD_COL_FORMAT, Const.NVL(field.getFormat(), ""));
    item.setText(FLD_COL_LENGTH, field.getLength() >= 0 ? Integer.toString(field.getLength()) : "");
    item.setText(
        FLD_COL_PRECISION, field.getPrecision() >= 0 ? Integer.toString(field.getPrecision()) : "");
    item.setText(
        FLD_COL_KEY, field.isPrimaryKey() ? Integer.toString(field.getPrimaryKeyPosition()) : "");
  }

  /** Minimal SourceJson with parent kind/name for type resolution while the dialog is open. */
  private SourceJson workingParentContext() {
    SourceJson ctx =
        new SourceJson(Const.NVL(wName != null ? wName.getText() : input.getName(), ""));
    if (wParentKind != null && !wParentKind.isDisposed()) {
      ctx.setParentSourceKind(SourceJsonParentKind.lookupDescription(wParentKind.getText()));
    } else {
      ctx.setParentSourceKind(input.resolveParentSourceKind());
    }
    if (wParentName != null && !wParentName.isDisposed()) {
      ctx.setParentSourceName(wParentName.getText());
    } else {
      ctx.setParentSourceName(input.getParentSourceName());
    }
    return ctx;
  }

  private void validateDefinition() {
    try {
      SourceJson draft = workingFromDialog();
      SourceJsonFieldSupport.applyMissingPassThroughTypes(model, draft);
      // Reflect resolved pass-through types back into the grid so the user sees them.
      refreshFieldTypeCellsFromDraft(draft);
      List<ICheckResult> remarks =
          new ArrayList<>(SourceJsonValidationSupport.check(draft, model, null));
      try {
        remarks.addAll(
            SourceDataTypeMappingSupport.check(
                draft.getName(),
                draft,
                SourceDataTypeMappingSupport.physicalFields(draft),
                metadataProvider));
      } catch (Exception mapEx) {
        // best effort
      }
      if (remarks.isEmpty()) {
        remarks =
            List.of(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_OK,
                    BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Validate.Ok.Message"),
                    null));
      }
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Validate.Error.Title"),
          BaseMessages.getString(
              PKG, "HopGuiSourceJsonDialog.Validate.Error.Message", ex.getMessage()),
          ex);
    }
  }

  private void refreshFieldTypeCellsFromDraft(SourceJson draft) {
    if (draft == null || wFields == null || wFields.isDisposed()) {
      return;
    }
    List<SourceJsonField> fields = draft.getFields();
    int rows = wFields.nrNonEmpty();
    for (int i = 0; i < rows && i < fields.size(); i++) {
      SourceJsonField field = fields.get(i);
      if (field == null) {
        continue;
      }
      int hopType = SourceJsonFieldSupport.resolveEffectiveHopType(model, draft, field);
      if (hopType > 0) {
        wFields.getNonEmpty(i).setText(FLD_COL_TYPE, SourceJsonFieldSupport.hopTypeLabel(hopType));
      }
    }
  }

  private void previewData() {
    try {
      SourceJson working = workingFromDialog();
      SourceJsonPreviewSupport.validateForPreview(working);
      SourceJsonPreviewSupport.PreviewPipeline built =
          SourceJsonPreviewSupport.buildPreviewPipeline(
              model, working, variables, metadataProvider);
      int previewRows = SourceJsonPreviewSupport.DEFAULT_ROW_LIMIT;
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
      if (pipeline != null
          && pipeline.getResult() != null
          && pipeline.getResult().getNrErrors() > 0) {
        EnterTextDialog etd =
            new EnterTextDialog(
                shell,
                BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Title"),
                BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Message"),
                progressDialog.getLoggingText(),
                true);
        etd.setReadOnly();
        etd.open();
        return;
      }
      List<Object[]> data = progressDialog.getPreviewRows(built.previewTransformName());
      if (data == null || data.isEmpty()) {
        MessageBox emptyBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        emptyBox.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Empty.Title"));
        emptyBox.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Empty.Message"));
        emptyBox.open();
        return;
      }
      new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Title"),
              BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Message"),
              progressDialog.getPreviewRowsMeta(built.previewTransformName()),
              data)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.Preview.Error.Message"),
          e);
    }
  }

  private SourceJson workingFromDialog() {
    SourceJson working = new SourceJson();
    getInfo(working);
    if (Utils.isEmpty(working.getName())) {
      working.setName(Const.NVL(input.getName(), "json"));
    }
    return working;
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wPublishedCatalogName.setText(Const.NVL(input.getPublishedCatalogName(), ""));
    EnumDialogSupport.selectCombo(wParentKind, input.resolveParentSourceKind());
    // READ_ONLY combos only accept values present in their item list: populate items first, then
    // select the stored parent / JSON field (do not setText before setItems).
    refreshParentNameItems(Const.NVL(input.getParentSourceName(), ""));
    refreshJsonFieldItems(Const.NVL(input.getJsonFieldName(), ""));
    wIgnoreMissingPath.setSelection(input.isIgnoreMissingPath());
    wDefaultPathLeafToNull.setSelection(input.isDefaultPathLeafToNull());

    wFields.clearAll(false);
    SourceJsonFieldSupport.applyMissingPassThroughTypes(model, input);
    for (SourceJsonField field : input.getFields()) {
      if (field == null) {
        continue;
      }
      addFieldToTable(field);
    }
    wFields.optimizeTableView();
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.loadFrom(input);
    }
  }

  private void getInfo(SourceJson target) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    target.setPublishedCatalogName(wPublishedCatalogName.getText());
    target.setParentSourceKind(SourceJsonParentKind.lookupDescription(wParentKind.getText()));
    target.setParentSourceName(wParentName.getText());
    target.setJsonFieldName(wJsonField.getText());
    target.setIgnoreMissingPath(wIgnoreMissingPath.getSelection());
    target.setDefaultPathLeafToNull(wDefaultPathLeafToNull.getSelection());
    if (!Utils.isEmpty(input.getArrayFocusPath())) {
      target.setArrayFocusPath(input.getArrayFocusPath());
    }

    List<SourceJsonField> fields = new ArrayList<>();
    int rows = wFields.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wFields.getNonEmpty(i);
      String name = item.getText(FLD_COL_NAME);
      String path = item.getText(FLD_COL_PATH);
      boolean passThrough = "Y".equalsIgnoreCase(item.getText(FLD_COL_PASSTHROUGH));
      String parentField = item.getText(FLD_COL_PARENT_FIELD);
      if (Utils.isEmpty(name) && Utils.isEmpty(path) && Utils.isEmpty(parentField)) {
        continue;
      }
      SourceJsonField field = new SourceJsonField();
      field.setName(name);
      field.setPath(path);
      field.setPassThrough(passThrough);
      field.setParentFieldName(parentField);
      field.setHopType(SourceJsonFieldSupport.hopTypeIdFromLabel(item.getText(FLD_COL_TYPE)));
      field.setFormat(item.getText(FLD_COL_FORMAT));
      field.setLength(Const.toInt(item.getText(FLD_COL_LENGTH), -1));
      field.setPrecision(Const.toInt(item.getText(FLD_COL_PRECISION), -1));
      field.setPrimaryKeyPosition(Const.toInt(item.getText(FLD_COL_KEY), 0));
      fields.add(field);
    }
    target.setFields(fields);
    // Inherit parent column types for pass-through rows left blank in the grid.
    SourceJsonFieldSupport.applyMissingPassThroughTypes(model, target);
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.saveTo(target);
    }
  }

  private void addParentPrimaryKeys() {
    SourceJsonParentKind kind = SourceJsonParentKind.lookupDescription(wParentKind.getText());
    String parentName = wParentName.getText();
    List<String> keyNames = parentPrimaryKeyNames(kind, parentName);
    if (keyNames.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.AddParentKeys.None.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.AddParentKeys.None.Message"));
      box.open();
      return;
    }
    Set<String> existing = new LinkedHashSet<>();
    int rows = wFields.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wFields.getNonEmpty(i);
      if ("Y".equalsIgnoreCase(item.getText(FLD_COL_PASSTHROUGH))) {
        existing.add(item.getText(FLD_COL_PARENT_FIELD));
      }
    }
    int nextKey = 1;
    for (int i = 0; i < rows; i++) {
      int pos = Const.toInt(wFields.getNonEmpty(i).getText(FLD_COL_KEY), 0);
      if (pos >= nextKey) {
        nextKey = pos + 1;
      }
    }
    for (String keyName : keyNames) {
      if (existing.contains(keyName)) {
        continue;
      }
      int hopType =
          SourceJsonFieldSupport.resolveParentFieldHopType(model, kind, parentName, keyName);
      SourceJsonField pt = SourceJsonField.passThroughField(keyName);
      pt.setHopType(hopType);
      pt.setPrimaryKeyPosition(nextKey++);
      addFieldToTable(pt);
    }
    wFields.optimizeTableView();
    refreshParentFieldComboValues();
  }

  private List<String> parentPrimaryKeyNames(SourceJsonParentKind kind, String parentName) {
    List<String> names = new ArrayList<>();
    if (kind == null || Utils.isEmpty(parentName)) {
      return names;
    }
    switch (kind) {
      case TABLE -> {
        SourceTable table = model.findTable(parentName);
        if (table != null) {
          for (SourceColumn column : table.primaryKeyColumns()) {
            if (column != null && !Utils.isEmpty(column.getName())) {
              names.add(column.getName());
            }
          }
        }
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(parentName);
        if (query != null) {
          for (SourceQueryColumn column : query.getColumns()) {
            if (column != null && column.isPrimaryKey()) {
              names.add(column.resolveAlias());
            }
          }
        }
      }
      case JSON -> {
        SourceJson parentJson = model.findJsonSource(parentName);
        if (parentJson != null) {
          for (SourceJsonField field : parentJson.getFields()) {
            if (field != null && field.isPrimaryKey()) {
              names.add(field.resolveName());
            }
          }
        }
      }
    }
    return names;
  }

  private void refreshParentNameItems() {
    refreshParentNameItems(wParentName.getText());
  }

  private void refreshParentNameItems(String preferred) {
    SourceJsonParentKind kind = SourceJsonParentKind.lookupDescription(wParentKind.getText());
    wParentName.setItems(parentNames(kind));
    selectComboItem(wParentName, preferred);
  }

  private void refreshJsonFieldItems() {
    refreshJsonFieldItems(wJsonField.getText());
  }

  private void refreshJsonFieldItems(String preferred) {
    wJsonField.setItems(parentFieldNames());
    selectComboItem(wJsonField, preferred);
  }

  /**
   * Select {@code preferred} in a combo after {@link Combo#setItems(String[])}. Uses {@link
   * Combo#select(int)} so READ_ONLY combos keep the intended value (unlike {@code setText} before
   * items exist).
   */
  private static void selectComboItem(Combo combo, String preferred) {
    if (combo == null || combo.isDisposed() || combo.getItemCount() == 0) {
      return;
    }
    if (!Utils.isEmpty(preferred)) {
      int index = combo.indexOf(preferred);
      if (index >= 0) {
        combo.select(index);
        return;
      }
    }
    // No preferred match: leave first item selected for usability on new objects / kind change.
    combo.select(0);
  }

  private void refreshParentFieldComboValues() {
    if (colParentField != null) {
      colParentField.setComboValues(parentFieldNames());
    }
  }

  private String[] parentNames(SourceJsonParentKind kind) {
    List<String> names = new ArrayList<>();
    if (kind == null) {
      return new String[0];
    }
    switch (kind) {
      case TABLE -> {
        for (SourceTable table : model.getTables()) {
          if (table != null && !Utils.isEmpty(table.getName())) {
            names.add(table.getName());
          }
        }
      }
      case QUERY -> {
        for (SourceQuery query : model.getQueries()) {
          if (query != null && !Utils.isEmpty(query.getName())) {
            names.add(query.getName());
          }
        }
      }
      case JSON -> {
        for (SourceJson json : model.getJsonSources()) {
          if (json != null
              && !Utils.isEmpty(json.getName())
              && !json.getName().equals(input.getName())) {
            names.add(json.getName());
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private String[] parentFieldNames() {
    SourceJsonParentKind kind = SourceJsonParentKind.lookupDescription(wParentKind.getText());
    String parentName = wParentName.getText();
    LinkedHashSet<String> names = new LinkedHashSet<>();
    if (kind == null || Utils.isEmpty(parentName)) {
      return new String[0];
    }
    switch (kind) {
      case TABLE -> {
        SourceTable table = model.findTable(parentName);
        if (table != null) {
          for (SourceColumn column : table.getColumns()) {
            if (column != null && !Utils.isEmpty(column.getName())) {
              names.add(column.getName());
            }
          }
        }
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(parentName);
        if (query != null) {
          for (SourceQueryColumn column : query.getColumns()) {
            if (column != null) {
              String alias = column.resolveAlias();
              if (!Utils.isEmpty(alias)) {
                names.add(alias);
              }
            }
          }
        }
      }
      case JSON -> {
        SourceJson parentJson = model.findJsonSource(parentName);
        if (parentJson != null) {
          for (SourceJsonField field : parentJson.getFields()) {
            if (field != null) {
              String name = field.resolveName();
              if (!Utils.isEmpty(name)) {
                names.add(name);
              }
            }
          }
        }
      }
    }
    return names.toArray(new String[0]);
  }

  private static String[] keyPositionChoices() {
    String[] choices = new String[9];
    choices[0] = "";
    for (int i = 1; i <= 8; i++) {
      choices[i] = Integer.toString(i);
    }
    return choices;
  }

  private void ok() {
    if (Utils.isEmpty(wName.getText())) {
      MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.NameRequired.Title"));
      box.setMessage(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.NameRequired.Message"));
      box.open();
      return;
    }
    String newName = wName.getText().trim();
    SourceJson existing = model.findJsonSource(newName);
    if (existing != null && existing != input) {
      MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.DuplicateName.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceJsonDialog.DuplicateName.Message", newName));
      box.open();
      return;
    }
    String oldName = input.getName();
    getInfo(input);
    if (model != null) {
      SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
          model, SourceEndpointKind.JSON, oldName, input.getName());
    }
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    WindowProperty winprop = new WindowProperty(shell);
    PropsUi.getInstance().setScreen(winprop);
    shell.dispose();
  }
}
