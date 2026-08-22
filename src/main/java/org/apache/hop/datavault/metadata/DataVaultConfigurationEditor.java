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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiDataVaultModelDialog;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Editor for project-level {@link DataVaultConfiguration} metadata. */
@GuiPlugin(description = "Editor for Data Vault configuration metadata")
public class DataVaultConfigurationEditor extends MetadataEditor<DataVaultConfiguration> {

  private static final Class<?> PKG = DataVaultConfiguration.class;
  private static final Class<?> DIALOG_PKG = HopGuiDataVaultModelDialog.class;

  private Text wName;
  private Text wDescription;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;
  private Composite wUnknownTabComp;
  private Composite wInvalidTabComp;
  private Composite wOrphanTabComp;
  private Composite wColumnsTabComp;
  private Composite wTargetLoadTabComp;
  private Composite wGeneratedPipelinesTabComp;
  private boolean populatingWidgets;

  public DataVaultConfigurationEditor(
      HopGui hopGui,
      MetadataManager<DataVaultConfiguration> manager,
      DataVaultConfiguration metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Name.Label"));
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    Label wlDescription = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlDescription);
    wlDescription.setText(
        BaseMessages.getString(PKG, "DataVaultConfigurationEditor.Description.Label"));
    FormData fdlDescription = new FormData();
    fdlDescription.top = new FormAttachment(wName, margin);
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.top = new FormAttachment(wlDescription, 0, SWT.CENTER);
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    CTabFolder tabFolder = new CTabFolder(parent, SWT.BORDER);
    FormData fdTabs = new FormData();
    fdTabs.top = new FormAttachment(wDescription, margin);
    fdTabs.left = new FormAttachment(0, 0);
    fdTabs.right = new FormAttachment(100, 0);
    fdTabs.bottom = new FormAttachment(100, 0);
    tabFolder.setLayoutData(fdTabs);

    wGeneralTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.General.Label"),
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.General.ToolTip"));
    wUnknownTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Unknown.Label"),
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Unknown.ToolTip"));
    wInvalidTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Invalid.Label"),
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Invalid.ToolTip"));
    wOrphanTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Orphan.Label"),
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Orphan.ToolTip"));
    wColumnsTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Columns.Label"),
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.Columns.ToolTip"));
    wTargetLoadTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.TargetLoad.Label"),
            BaseMessages.getString(
                DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.TargetLoad.ToolTip"));
    wGeneratedPipelinesTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(
                DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.GeneratedPipelines.Label"),
            BaseMessages.getString(
                DIALOG_PKG, "HopGuiDataVaultModelDialog.Tab.GeneratedPipelines.ToolTip"));

    DataVaultConfiguration configuration = getMetadata();
    widgets = new GuiCompositeWidgets(manager.getVariables());
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
    tabFolder.setSelection(0);

    setWidgetsContent();
    updateSingleStoreShardWidgets(configuration);
    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            if (populatingWidgets) {
              return;
            }
            setChanged();
            if ("targetDatabase".equals(widgetId)) {
              DataVaultConfiguration current = getMetadata();
              widgets.getWidgetsContents(
                  current, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
              updateSingleStoreShardWidgets(current);
            }
          }
        });
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
  }

  @Override
  public void setWidgetsContent() {
    populatingWidgets = true;
    try {
      DataVaultConfiguration configuration = getMetadata();
      wName.setText(Const.NVL(configuration.getName(), ""));
      wDescription.setText(Const.NVL(configuration.getDescription(), ""));
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

  @Override
  public void getWidgetsContent(DataVaultConfiguration meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_INVALID_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID);
    widgets.getWidgetsContents(meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
    widgets.getWidgetsContents(
        meta, DataVaultConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID);
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
      return manager
          .getMetadataProvider()
          .getSerializer(DatabaseMeta.class)
          .load(configuration.getTargetDatabase());
    } catch (Exception e) {
      return null;
    }
  }
}
