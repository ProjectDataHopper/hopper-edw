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
package org.hopper.edw.datavault.metadata.sourcemodel;

import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiDataVaultModelDialog;
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

/** Editor for project-level {@link SourceModelConfiguration} metadata. */
@GuiPlugin(description = "Editor for Source model configuration metadata")
public class SourceModelConfigurationEditor extends MetadataEditor<SourceModelConfiguration> {

  private static final Class<?> PKG = SourceModelConfiguration.class;

  private Text wName;
  private Text wDescription;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;

  public SourceModelConfigurationEditor(
      HopGui hopGui,
      MetadataManager<SourceModelConfiguration> manager,
      SourceModelConfiguration metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "SourceModelConfigurationEditor.Name.Label"));
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
        BaseMessages.getString(PKG, "SourceModelConfigurationEditor.Description.Label"));
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
            BaseMessages.getString(PKG, "SourceModelConfigurationEditor.Tab.General.Label"),
            BaseMessages.getString(PKG, "SourceModelConfigurationEditor.Tab.General.ToolTip"));

    widgets = new GuiCompositeWidgets(manager.getVariables());
    widgets.createCompositeWidgets(
        getMetadata(),
        null,
        wGeneralTabComp,
        SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID,
        null);
    tabFolder.setSelection(0);

    setWidgetsContent();
    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            setChanged();
          }
        });
    wName.addModifyListener(e -> setChanged());
    wDescription.addModifyListener(e -> setChanged());
  }

  @Override
  public void setWidgetsContent() {
    SourceModelConfiguration configuration = getMetadata();
    wName.setText(Const.NVL(configuration.getName(), ""));
    wDescription.setText(Const.NVL(configuration.getDescription(), ""));
    widgets.setWidgetsContents(
        configuration, wGeneralTabComp, SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
  }

  @Override
  public void getWidgetsContent(SourceModelConfiguration meta) {
    meta.setName(wName.getText());
    meta.setDescription(wDescription.getText());
    widgets.getWidgetsContents(meta, SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
  }
}
