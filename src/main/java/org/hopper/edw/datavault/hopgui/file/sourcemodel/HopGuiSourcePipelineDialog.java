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
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.file.dimensional.DmSourcePipelineGuiSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.DmSourcePipelineOpenSupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingSupport;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSourceSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipelineCatalogSource;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipelineValidationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipLifecycleSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourcePipelineCatalogImportSupport;

/**
 * Dialog to edit a {@link SourcePipeline}: pipeline file, output transform, declared fields, and
 * optional catalog sources imported from Record Definition Input transforms in the pipeline.
 */
public class HopGuiSourcePipelineDialog {

  private static final Class<?> PKG = HopGuiSourcePipelineDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final SourceModel model;
  private final SourcePipeline input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private TextVar wPipelineFilename;
  private Button wBrowsePipeline;
  private Button wOpenPipeline;
  private Button wImportCatalogSources;
  private ComboVar wOutputTransform;
  private Button wSelectTransform;
  private MetaSelectionLine<PipelineRunConfiguration> wRunConfiguration;
  private Text wCatalogSourceName;
  private TableView wCatalogSources;
  private TableView wFields;
  private SourceDataTypeMappingTab dataTypeMappingTab;

  private boolean ok;

  public HopGuiSourcePipelineDialog(
      Shell parent,
      SourcePipeline pipelineSource,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = pipelineSource;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Shell.Title"));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Preview.Label"));
    wPreview.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Preview.ToolTip"));
    wPreview.addListener(SWT.Selection, e -> previewData());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.SOURCE_PIPELINE);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wPreview, wCancel}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top()
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    addGeneralTab(wTabFolder, middle, margin);
    addCatalogSourcesTab(wTabFolder, margin);
    addFieldsTab(wTabFolder, margin);
    dataTypeMappingTab =
        new SourceDataTypeMappingTab(
            variables,
            metadataProvider,
            () -> {
              SourcePipeline draft = new SourcePipeline();
              getInfo(draft);
              return SourceDataTypeMappingSupport.physicalFields(draft);
            });
    dataTypeMappingTab.addTab(wTabFolder, margin);

    wTabFolder.setSelection(0);
    getData();

    BaseTransformDialog.setSize(shell, 820, 640);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addGeneralTab(CTabFolder tabFolder, int middle, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Tab.General"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlName = new Label(comp, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(comp, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
    wDescription = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    // Pipeline file: [label] [path....] [Import] [Open] [Browse]
    Label wlFile = new Label(comp, SWT.RIGHT);
    wlFile.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.PipelineFile.Label"));
    PropsUi.setLook(wlFile);
    wlFile.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());

    wBrowsePipeline = new Button(comp, SWT.PUSH);
    wBrowsePipeline.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Browse.Label"));
    PropsUi.setLook(wBrowsePipeline);
    wBrowsePipeline.setLayoutData(new FormDataBuilder().right().top(wDescription, margin).result());
    wBrowsePipeline.addListener(SWT.Selection, e -> browsePipelineFile());

    wOpenPipeline = new Button(comp, SWT.PUSH);
    wOpenPipeline.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Open.Label"));
    PropsUi.setLook(wOpenPipeline);
    wOpenPipeline.setLayoutData(
        new FormDataBuilder().right(wBrowsePipeline, -margin).top(wDescription, margin).result());
    wOpenPipeline.addListener(SWT.Selection, e -> openPipeline());

    wImportCatalogSources = new Button(comp, SWT.PUSH);
    wImportCatalogSources.setText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Import.Label"));
    wImportCatalogSources.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Import.ToolTip"));
    PropsUi.setLook(wImportCatalogSources);
    wImportCatalogSources.setLayoutData(
        new FormDataBuilder().right(wOpenPipeline, -margin).top(wDescription, margin).result());
    wImportCatalogSources.addListener(SWT.Selection, e -> importCatalogSourcesFromPipeline());

    wPipelineFilename = new TextVar(variables, comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPipelineFilename);
    wPipelineFilename.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wDescription, margin)
            .right(wImportCatalogSources, -margin)
            .result());

    // Output transform: [label] [combo....] [Select]
    Label wlTransform = new Label(comp, SWT.RIGHT);
    wlTransform.setText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.OutputTransform.Label"));
    PropsUi.setLook(wlTransform);
    wlTransform.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wPipelineFilename, margin)
            .right(middle, -margin)
            .result());

    wSelectTransform = new Button(comp, SWT.PUSH);
    wSelectTransform.setText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Select.Label"));
    PropsUi.setLook(wSelectTransform);
    wSelectTransform.setLayoutData(
        new FormDataBuilder().right().top(wPipelineFilename, margin).result());
    wSelectTransform.addListener(SWT.Selection, e -> loadTransformNames());

    wOutputTransform = new ComboVar(variables, comp, SWT.BORDER);
    PropsUi.setLook(wOutputTransform);
    wOutputTransform.setLayoutData(
        new FormDataBuilder()
            .left(middle, 0)
            .top(wPipelineFilename, margin)
            .right(wSelectTransform, -margin)
            .result());

    wRunConfiguration =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            PipelineRunConfiguration.class,
            comp,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.RunConfiguration.Label"),
            BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.RunConfiguration.ToolTip"));
    wRunConfiguration.setLayoutData(
        new FormDataBuilder().left().top(wOutputTransform, margin).right().result());
    try {
      wRunConfiguration.fillItems();
    } catch (HopException e) {
      // best effort
    }

    Label wlCatalogSource = new Label(comp, SWT.RIGHT);
    wlCatalogSource.setText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSourceName.Label"));
    PropsUi.setLook(wlCatalogSource);
    wlCatalogSource.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wRunConfiguration, margin)
            .right(middle, -margin)
            .result());
    wCatalogSourceName = new Text(comp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCatalogSourceName);
    wCatalogSourceName.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSourceName.ToolTip"));
    wCatalogSourceName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wRunConfiguration, margin).right().result());
  }

  private void addCatalogSourcesTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Tab.CatalogSources"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Label wlInfo = new Label(comp, SWT.LEFT | SWT.WRAP);
    wlInfo.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.Info"));
    PropsUi.setLook(wlInfo);
    wlInfo.setLayoutData(new FormDataBuilder().left().top(0, margin).right().result());

    Button wImport = new Button(comp, SWT.PUSH);
    wImport.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Import.Label"));
    wImport.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Import.ToolTip"));
    PropsUi.setLook(wImport);
    wImport.setLayoutData(new FormDataBuilder().left().top(wlInfo, margin).result());
    wImport.addListener(SWT.Selection, e -> importCatalogSourcesFromPipeline());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.Transform"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.Connection"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.Namespace"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.RecordName"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiSourcePipelineDialog.CatalogSources.SelectFromInput"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {"Y", "N"}),
          new ColumnInfo(
              BaseMessages.getString(
                  PKG, "HopGuiSourcePipelineDialog.CatalogSources.NamespaceField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.CatalogSources.NameField"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wCatalogSources =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            columns,
            Math.max(input.getCatalogSources().size(), 1),
            null,
            PropsUi.getInstance());
    wCatalogSources.setLayoutData(
        new FormDataBuilder().left().top(wImport, margin).right().bottom().result());
  }

  private void addFieldsTab(CTabFolder tabFolder, int margin) {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Tab.Fields"));

    Composite comp = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(comp);
    comp.setLayout(new FormLayout());
    tab.setControl(comp);

    Button wGetFields = new Button(comp, SWT.PUSH);
    wGetFields.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.GetFields.Label"));
    PropsUi.setLook(wGetFields);
    wGetFields.setLayoutData(new FormDataBuilder().left().top(0, margin).result());
    wGetFields.addListener(SWT.Selection, e -> getFieldsFromTransform());

    Button wValidate = new Button(comp, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Validate.Label"));
    PropsUi.setLook(wValidate);
    wValidate.setLayoutData(new FormDataBuilder().left(wGetFields, margin).top(0, margin).result());
    wValidate.addListener(SWT.Selection, e -> validate());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Column.Type"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames()),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Column.Length"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Column.Precision"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Column.Pk"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    wFields =
        new TableView(
            variables,
            comp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            columns,
            Math.max(input.getFields().size(), 1),
            null,
            PropsUi.getInstance());
    wFields.setLayoutData(
        new FormDataBuilder().left().top(wGetFields, margin).right().bottom().result());
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wPipelineFilename.setText(Const.NVL(input.getPipelineFilename(), ""));
    wOutputTransform.setText(Const.NVL(input.getOutputTransformName(), ""));
    try {
      wRunConfiguration.fillItems();
    } catch (Exception ignored) {
      // best effort
    }
    wRunConfiguration.setText(Const.NVL(input.getPipelineRunConfiguration(), ""));
    wCatalogSourceName.setText(Const.NVL(input.getCatalogSourceName(), ""));
    populateCatalogSourcesTable(input.getCatalogSources());
    populateFieldsTable(input.getFields());
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.loadFrom(input);
    }
  }

  private void populateCatalogSourcesTable(List<SourcePipelineCatalogSource> sources) {
    wCatalogSources.table.removeAll();
    if (sources != null) {
      for (SourcePipelineCatalogSource src : sources) {
        if (src == null) {
          continue;
        }
        TableItem item = new TableItem(wCatalogSources.table, SWT.NONE);
        item.setText(1, Const.NVL(src.getTransformName(), ""));
        item.setText(2, Const.NVL(src.getCatalogConnection(), ""));
        item.setText(3, Const.NVL(src.getNamespace(), ""));
        item.setText(4, Const.NVL(src.getRecordName(), ""));
        item.setText(5, src.isSelectFromInput() ? "Y" : "N");
        item.setText(6, Const.NVL(src.getNamespaceField(), ""));
        item.setText(7, Const.NVL(src.getNameField(), ""));
      }
    }
    wCatalogSources.setRowNums();
    wCatalogSources.optWidth(true);
  }

  private void populateFieldsTable(List<SourceColumn> fields) {
    wFields.table.removeAll();
    if (fields != null) {
      for (SourceColumn field : fields) {
        if (field == null) {
          continue;
        }
        TableItem item = new TableItem(wFields.table, SWT.NONE);
        item.setText(1, Const.NVL(field.getName(), ""));
        String typeName = "";
        try {
          if (field.getHopType() > 0) {
            typeName = ValueMetaFactory.getValueMetaName(field.getHopType());
          }
        } catch (Exception ignored) {
          // leave empty
        }
        item.setText(2, typeName);
        item.setText(3, Const.NVL(field.getLength(), ""));
        item.setText(4, Const.NVL(field.getPrecision(), ""));
        item.setText(
            5,
            field.getPrimaryKeyPosition() > 0
                ? Integer.toString(field.getPrimaryKeyPosition())
                : "");
      }
    }
    wFields.setRowNums();
    wFields.optWidth(true);
  }

  private void browsePipelineFile() {
    String selectedFile =
        BaseDialog.presentFileDialog(
            false,
            shell,
            new String[] {"*.hpl", "*"},
            new String[] {
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Browse.Filter.Hpl"),
              BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Browse.Filter.All")
            },
            false);
    if (Utils.isEmpty(selectedFile)) {
      return;
    }
    wPipelineFilename.setText(selectedFile);
  }

  private void importCatalogSourcesFromPipeline() {
    try {
      String file = wPipelineFilename.getText();
      if (Utils.isEmpty(file)) {
        MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.NeedPipelineFile"));
        box.open();
        return;
      }
      List<SourcePipelineCatalogSource> found =
          SourcePipelineCatalogImportSupport.importFromPipeline(file, variables, metadataProvider);
      populateCatalogSourcesTable(found);
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Import.Done.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG,
              "HopGuiSourcePipelineDialog.Import.Done.Message",
              Integer.toString(found.size())));
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Import"),
          e);
    }
  }

  private void loadTransformNames() {
    try {
      String file = wPipelineFilename.getText();
      if (Utils.isEmpty(file)) {
        return;
      }
      List<String> names =
          DvPipelineSourceSupport.listTransformNames(file, variables, metadataProvider);
      wOutputTransform.setItems(names.toArray(new String[0]));
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.LoadTransforms"),
          e);
    }
  }

  private void getFieldsFromTransform() {
    try {
      String file = wPipelineFilename.getText();
      String transform = wOutputTransform.getText();
      if (Utils.isEmpty(file) || Utils.isEmpty(transform)) {
        MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.NeedFileAndTransform"));
        box.open();
        return;
      }
      var rowMeta =
          DvPipelineSourceSupport.resolveLiveTransformFields(
              file, transform, variables, metadataProvider);
      wFields.table.removeAll();
      for (int i = 0; i < rowMeta.size(); i++) {
        IValueMeta vm = rowMeta.getValueMeta(i);
        if (vm == null || Utils.isEmpty(vm.getName())) {
          continue;
        }
        TableItem item = new TableItem(wFields.table, SWT.NONE);
        item.setText(1, vm.getName());
        item.setText(2, ValueMetaFactory.getValueMetaName(vm.getType()));
        if (vm.getLength() >= 0) {
          item.setText(3, Integer.toString(vm.getLength()));
        }
        if (vm.getPrecision() >= 0) {
          item.setText(4, Integer.toString(vm.getPrecision()));
        }
      }
      wFields.setRowNums();
      wFields.optWidth(true);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourcePipelineDialog.Error.GetFields"),
          e);
    }
  }

  private void openPipeline() {
    DmSourcePipelineOpenSupport.openSourcePipelineFile(
        HopGui.getInstance(), shell, variables, wPipelineFilename.getText());
  }

  private void previewData() {
    DmSourcePipelineGuiSupport.previewSourcePipelineData(
        shell,
        variables,
        metadataProvider,
        wPipelineFilename.getText(),
        wOutputTransform.getText());
  }

  private void validate() {
    SourcePipeline draft = new SourcePipeline();
    getInfo(draft);
    var remarks = new ArrayList<>(SourcePipelineValidationSupport.check(draft, model, null));
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
    ModelDialogValidationSupport.showCheckResults(shell, remarks);
  }

  private void getInfo(SourcePipeline target) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    target.setPipelineFilename(wPipelineFilename.getText());
    target.setOutputTransformName(wOutputTransform.getText());
    target.setPipelineRunConfiguration(wRunConfiguration.getText());
    target.setCatalogSourceName(wCatalogSourceName.getText());

    List<SourcePipelineCatalogSource> catalogSources = new ArrayList<>();
    for (int i = 0; i < wCatalogSources.nrNonEmpty(); i++) {
      TableItem item = wCatalogSources.getNonEmpty(i);
      String connection = item.getText(2);
      String namespace = item.getText(3);
      String recordName = item.getText(4);
      String transformName = item.getText(1);
      boolean selectFromInput = "Y".equalsIgnoreCase(Const.NVL(item.getText(5), "N"));
      if (Utils.isEmpty(transformName)
          && Utils.isEmpty(connection)
          && Utils.isEmpty(namespace)
          && Utils.isEmpty(recordName)) {
        continue;
      }
      SourcePipelineCatalogSource src = new SourcePipelineCatalogSource();
      src.setTransformName(transformName);
      src.setCatalogConnection(connection);
      src.setNamespace(namespace);
      src.setRecordName(recordName);
      src.setSelectFromInput(selectFromInput);
      src.setNamespaceField(item.getText(6));
      src.setNameField(item.getText(7));
      catalogSources.add(src);
    }
    target.setCatalogSources(catalogSources);

    List<SourceColumn> fields = new ArrayList<>();
    for (int i = 0; i < wFields.nrNonEmpty(); i++) {
      TableItem item = wFields.getNonEmpty(i);
      String name = item.getText(1);
      if (Utils.isEmpty(name)) {
        continue;
      }
      SourceColumn column = new SourceColumn(name.trim());
      try {
        column.setHopType(ValueMetaFactory.getIdForValueMeta(item.getText(2)));
      } catch (Exception e) {
        column.setHopType(IValueMeta.TYPE_STRING);
      }
      column.setLength(item.getText(3));
      column.setPrecision(item.getText(4));
      try {
        if (!Utils.isEmpty(item.getText(5))) {
          column.setPrimaryKeyPosition(Integer.parseInt(item.getText(5).trim()));
        }
      } catch (NumberFormatException ignored) {
        // leave 0
      }
      fields.add(column);
    }
    target.setFields(fields);
    if (dataTypeMappingTab != null) {
      dataTypeMappingTab.saveTo(target);
    }
  }

  private void ok() {
    if (Utils.isEmpty(wName.getText())) {
      return;
    }
    String oldName = input.getName();
    getInfo(input);
    if (model != null) {
      SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
          model, SourceEndpointKind.PIPELINE, oldName, input.getName());
    }
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
