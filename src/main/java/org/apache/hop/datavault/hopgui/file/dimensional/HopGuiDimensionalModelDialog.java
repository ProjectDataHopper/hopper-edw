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
package org.apache.hop.datavault.hopgui.file.dimensional;

import org.apache.hop.core.Props;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
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

/** Dialog to edit the properties of a {@link DimensionalModel}. */
public class HopGuiDimensionalModelDialog {
  private static final Class<?> PKG = HopGuiDimensionalModelDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final DimensionalModel input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;
  private Composite wTargetLoadTabComp;
  private Composite wGeneratedArtifactsTabComp;

  private boolean ok;
  private boolean populatingWidgets;

  public HopGuiDimensionalModelDialog(Shell parent, HopGui hopGui, DimensionalModel model) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.variables = hopGui.getVariables();
    this.input = model;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Title", input.getName()));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Name.Label"));
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
        BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Description.Label"));
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

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DM_MODEL);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wDescription, margin))
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    wGeneralTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Tab.General.ToolTip"));
    wTargetLoadTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Tab.TargetLoad.Label"),
            BaseMessages.getString(PKG, "HopGuiDimensionalModelDialog.Tab.TargetLoad.ToolTip"));
    wGeneratedArtifactsTabComp =
        createTabComposite(
            wTabFolder,
            BaseMessages.getString(
                PKG, "HopGuiDimensionalModelDialog.Tab.GeneratedArtifacts.Label"),
            BaseMessages.getString(
                PKG, "HopGuiDimensionalModelDialog.Tab.GeneratedArtifacts.ToolTip"));

    DimensionalConfiguration configuration = input.getConfigurationOrDefault();
    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wGeneralTabComp,
        DimensionalConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wTargetLoadTabComp,
        DimensionalConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wGeneratedArtifactsTabComp,
        DimensionalConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID,
        null);

    wTabFolder.setSelection(0);
    getData();

    widgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            if (!populatingWidgets) {
              input.setChanged();
            }
          }
        });

    BaseTransformDialog.setSize(shell, 650, 450);
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
      DimensionalConfiguration configuration = input.getConfigurationOrDefault();
      widgets.setWidgetsContents(
          configuration, wGeneralTabComp, DimensionalConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
      widgets.setWidgetsContents(
          configuration,
          wTargetLoadTabComp,
          DimensionalConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
      widgets.setWidgetsContents(
          configuration,
          wGeneratedArtifactsTabComp,
          DimensionalConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID);
    } finally {
      populatingWidgets = false;
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setDescription(wDescription.getText());
    DimensionalConfiguration configuration = input.getConfigurationOrDefault();
    widgets.getWidgetsContents(
        configuration, DimensionalConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
    widgets.getWidgetsContents(
        configuration, DimensionalConfiguration.GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID);
    widgets.getWidgetsContents(
        configuration, DimensionalConfiguration.GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID);
    input.setConfiguration(configuration);
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
