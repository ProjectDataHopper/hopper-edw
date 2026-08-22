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
package org.hopper.edw.datavault.hopgui.file.vault;

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvDdlSupport;
import org.hopper.edw.datavault.metadata.ModelConfigurationExtractSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jspecify.annotations.NonNull;

/** Dialog to edit the properties of a DataVaultModel (description, configuration, etc). */
public class HopGuiDataVaultModelDialog {
  private static final Class<?> PKG = HopGuiDataVaultModelDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DataVaultModel input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private MetaSelectionLine<DataVaultConfiguration> wConfiguration;
  private Button wExtract;
  private Label wlNamedHint;
  private CTabFolder wTabFolder;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;
  private Composite wUnknownTabComp;
  private Composite wInvalidTabComp;
  private Composite wOrphanTabComp;
  private Composite wColumnsTabComp;
  private Composite wTargetLoadTabComp;
  private Composite wGeneratedPipelinesTabComp;

  private boolean ok;
  private boolean populatingWidgets;

  public HopGuiDataVaultModelDialog(Shell parent, HopGui hopGui, DataVaultModel model) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = model;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Title", input.getName()));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Name.Label"));
    PropsUi.setLook(wlName);
    FormData fdlName = new FormData();
    fdlName.left = new FormAttachment(0, 0);
    fdlName.top = new FormAttachment(0, margin);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(middle, 0);
    fdName.top = new FormAttachment(0, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);
    wName.addModifyListener(e -> input.setChanged());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);
    wDescription.addModifyListener(e -> input.setChanged());

    wConfiguration =
        new MetaSelectionLine<>(
            variables,
            hopGui.getMetadataProvider(),
            DataVaultConfiguration.class,
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Configuration.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Configuration.ToolTip"));
    FormData fdConfiguration = new FormData();
    fdConfiguration.left = new FormAttachment(0, 0);
    fdConfiguration.top = new FormAttachment(wDescription, margin);
    fdConfiguration.right = new FormAttachment(100, 0);
    wConfiguration.setLayoutData(fdConfiguration);
    try {
      wConfiguration.fillItems();
    } catch (HopException e) {
      // best effort
    }
    wConfiguration.addModifyListener(e -> updateConfigurationMode());

    wExtract = new Button(shell, SWT.PUSH);
    wExtract.setText(BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Extract.Label"));
    PropsUi.setLook(wExtract);
    FormData fdExtract = new FormData();
    fdExtract.left = new FormAttachment(middle, 0);
    fdExtract.top = new FormAttachment(wConfiguration, margin);
    wExtract.setLayoutData(fdExtract);
    wExtract.addListener(SWT.Selection, e -> extractConfiguration());

    wlNamedHint = new Label(shell, SWT.WRAP);
    PropsUi.setLook(wlNamedHint);
    wlNamedHint.setText(BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.NamedHint.Label"));
    FormData fdNamedHint = new FormData();
    fdNamedHint.left = new FormAttachment(0, 0);
    fdNamedHint.top = new FormAttachment(wExtract, margin);
    fdNamedHint.right = new FormAttachment(100, 0);
    wlNamedHint.setLayoutData(fdNamedHint);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_MODEL);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wExtract, margin))
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    wGeneralTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.General.ToolTip"));
    wUnknownTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Unknown.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Unknown.ToolTip"));
    wInvalidTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Invalid.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Invalid.ToolTip"));
    wOrphanTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Orphan.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Orphan.ToolTip"));
    wColumnsTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Columns.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.Columns.ToolTip"));
    wTargetLoadTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.TargetLoad.Label"),
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.TargetLoad.ToolTip"));
    wGeneratedPipelinesTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDataVaultModelDialog.Tab.GeneratedPipelines.Label"),
            BaseMessages.getString(
                PKG, "HopGuiDataVaultModelDialog.Tab.GeneratedPipelines.ToolTip"));

    DataVaultConfiguration configuration = input.getConfigurationOrDefault();
    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wGeneralTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wUnknownTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wInvalidTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wOrphanTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wColumnsTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wTargetLoadTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wGeneratedPipelinesTabComp,
        DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID,
        null);

    wTabFolder.setSelection(0);

    getData();
    updateSingleStoreShardWidgets(input.getConfigurationOrDefault());

    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            if (populatingWidgets) {
              return;
            }
            input.setChanged();
            if ("targetDatabase".equals(widgetId)) {
              DataVaultConfiguration configuration = input.getConfigurationOrDefault();
              widgets.getWidgetsContents(
                  configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
              applySingleStoreShardDefaults(configuration);
              updateSingleStoreShardWidgets(configuration);
            }
          }
        });

    BaseTransformDialog.setSize(shell, 700, 600);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    return ok;
  }

  @NonNull
  public static Composite createTabComposite(CTabFolder tabFolder, String title, String toolTip) {
    CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(title);
    tabItem.setToolTipText(toolTip);

    Composite composite = new Composite(tabFolder, SWT.NONE);
    PropsUi.setLook(composite);
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    composite.setLayout(layout);
    tabItem.setControl(composite);
    return composite;
  }

  private void getData() {
    populatingWidgets = true;
    try {
      if (input.getName() != null) {
        wName.setText(input.getName());
      }
      if (input.getDescription() != null) {
        wDescription.setText(input.getDescription());
      }
      wConfiguration.setText(Const.NVL(input.getConfigurationName(), ""));
      updateConfigurationMode();

      DataVaultConfiguration configuration = input.getConfigurationOrDefault();
      widgets.setWidgetsContents(
          configuration, wGeneralTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
      widgets.setWidgetsContents(
          configuration, wUnknownTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID);
      widgets.setWidgetsContents(
          configuration, wInvalidTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID);
      widgets.setWidgetsContents(
          configuration, wOrphanTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID);
      widgets.setWidgetsContents(
          configuration, wColumnsTabComp, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID);
      widgets.setWidgetsContents(
          configuration,
          wTargetLoadTabComp,
          DataVaultConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
      widgets.setWidgetsContents(
          configuration,
          wGeneratedPipelinesTabComp,
          DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID);
    } finally {
      populatingWidgets = false;
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setDescription(wDescription.getText());
    String configurationName = Const.NVL(wConfiguration.getText(), "").trim();
    input.setConfigurationName(configurationName);
    if (Utils.isEmpty(configurationName)) {
      DataVaultConfiguration configuration = input.getConfigurationOrDefault();
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
      widgets.getWidgetsContents(
          configuration, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID);
      input.setConfiguration(configuration);
    } else {
      input.setConfiguration(null);
    }

    ok = true;
    dispose();
  }

  private void updateConfigurationMode() {
    boolean named = !Utils.isEmpty(wConfiguration.getText());
    if (wTabFolder != null && !wTabFolder.isDisposed()) {
      wTabFolder.setVisible(!named);
    }
    if (wlNamedHint != null && !wlNamedHint.isDisposed()) {
      wlNamedHint.setVisible(named);
    }
    if (wExtract != null && !wExtract.isDisposed()) {
      wExtract.setEnabled(!named);
    }
  }

  private void extractConfiguration() {
    if (ModelConfigurationExtractSupport.extract(hopGui, input)) {
      wConfiguration.setText(Const.NVL(input.getConfigurationName(), ""));
      try {
        wConfiguration.fillItems();
      } catch (HopException ignored) {
        // combo already has the new name
      }
      updateConfigurationMode();
    }
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void applySingleStoreShardDefaults(DataVaultConfiguration configuration) {
    if (configuration == null || configuration.isSingleStoreShardKeyOnHashKey()) {
      return;
    }
    DatabaseMeta targetDatabase = loadTargetDatabase(configuration);
    if (DvDdlSupport.isSingleStore(targetDatabase)) {
      configuration.setSingleStoreShardKeyOnHashKey(true);
      populatingWidgets = true;
      try {
        widgets.setWidgetsContents(
            configuration,
            wTargetLoadTabComp,
            DataVaultConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
      } finally {
        populatingWidgets = false;
      }
    }
  }

  private void updateSingleStoreShardWidgets(DataVaultConfiguration configuration) {
    if (widgets == null || configuration == null) {
      return;
    }
    boolean singleStore = DvDdlSupport.isSingleStore(loadTargetDatabase(configuration));
    widgets.enableWidgets(configuration, "singleStoreShardKeyOnHashKey", singleStore);
    widgets.enableWidgets(configuration, "singleStoreShardKeyIncludeDrivingKeys", singleStore);
  }

  private DatabaseMeta loadTargetDatabase(DataVaultConfiguration configuration) {
    if (configuration == null || Utils.isEmpty(configuration.getTargetDatabase())) {
      return null;
    }
    try {
      return hopGui
          .getMetadataProvider()
          .getSerializer(DatabaseMeta.class)
          .load(configuration.getTargetDatabase());
    } catch (Exception ignored) {
      return null;
    }
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
