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
package org.apache.hop.datavault.workflow.actions.businessvaultupdate;

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiDataVaultModelDialog;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ActionBusinessVaultUpdateDialog extends ActionDialog {
  private static final Class<?> PKG = ActionBusinessVaultUpdate.class;

  private final ActionBusinessVaultUpdate action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wModelTabComp;
  private Composite wDdlTabComp;

  public ActionBusinessVaultUpdateDialog(
      Shell parent,
      ActionBusinessVaultUpdate action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;

    if (this.action.getName() == null || this.action.getName().isEmpty()) {
      this.action.setName(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Name"));
    }
  }

  @Override
  public IAction open() {
    Shell parent = getParent();
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Title", action.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    Button wOK = new Button(shell, SWT.PUSH);
    wOK.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOK.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());

    DialogHelpSupport.createHelpButton(shell, HelpTopics.ACTION_BUSINESSVAULT_UPDATE);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOK, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Name.Label"));
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

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wName, margin))
            .right()
            .bottom(new FormAttachment(wOK, -2 * margin))
            .result());

    wModelTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Tab.Model.Label"),
            BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Tab.Model.ToolTip"));
    wDdlTabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            wTabFolder,
            BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Tab.Ddl.Label"),
            BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Tab.Ddl.ToolTip"));

    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        action,
        null,
        wModelTabComp,
        ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_MODEL_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        action, null, wDdlTabComp, ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_DDL_TAB_ID, null);

    wTabFolder.setSelection(0);
    setWidgetsContent();

    boolean changedBeforeOpen = action.hasChanged();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    if (cancelled) {
      action.setChanged(changedBeforeOpen);
      return null;
    }
    return action;
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    widgets.setWidgetsContents(
        action, wModelTabComp, ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_MODEL_TAB_ID);
    widgets.setWidgetsContents(
        action, wDdlTabComp, ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_DDL_TAB_ID);
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    widgets.getWidgetsContents(action, ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_MODEL_TAB_ID);
    widgets.getWidgetsContents(action, ActionBusinessVaultUpdate.GUI_PLUGIN_ELEMENT_DDL_TAB_ID);
  }

  private void ok() {
    cancelled = false;
    getWidgetsContent();
    action.setChanged();
    dispose();
  }

  private void cancel() {
    cancelled = true;
    dispose();
  }
}
