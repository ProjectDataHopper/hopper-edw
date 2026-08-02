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

package org.apache.hop.datavault.hopgui.file.sourcemodel;

import org.apache.hop.core.Props;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
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

/** Dialog to edit {@link SourceModel} name, description, and configuration. */
public class HopGuiSourceModelDialog {

  private static final Class<?> PKG = HopGuiSourceModelDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final SourceModel input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private GuiCompositeWidgets widgets;
  private Composite wGeneralTabComp;

  private boolean ok;
  private boolean populatingWidgets;

  public HopGuiSourceModelDialog(Shell parent, HopGui hopGui, SourceModel model) {
    this.parent = parent;
    this.variables = hopGui.getVariables();
    this.input = model;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "HopGuiSourceModelDialog.Title", input.getName()));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceModelDialog.Name.Label"));
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
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiSourceModelDialog.Description.Label"));
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
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);

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
            BaseMessages.getString(PKG, "HopGuiSourceModelDialog.Tab.General.Label"),
            BaseMessages.getString(PKG, "HopGuiSourceModelDialog.Tab.General.ToolTip"));

    SourceModelConfiguration configuration = input.getConfigurationOrDefault();
    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        configuration,
        null,
        wGeneralTabComp,
        SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID,
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

    BaseTransformDialog.setSize(shell, 600, 420);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  @NonNull
  private static Composite createTabComposite(CTabFolder tabFolder, String title, String toolTip) {
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
      SourceModelConfiguration configuration = input.getConfigurationOrDefault();
      widgets.setWidgetsContents(
          configuration, wGeneralTabComp, SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
    } finally {
      populatingWidgets = false;
    }
  }

  private void ok() {
    input.setName(wName.getText());
    input.setDescription(wDescription.getText());
    SourceModelConfiguration configuration = input.getConfigurationOrDefault();
    widgets.getWidgetsContents(
        configuration, SourceModelConfiguration.GUI_PLUGIN_ELEMENT_PARENT_ID);
    input.setConfiguration(configuration);
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
