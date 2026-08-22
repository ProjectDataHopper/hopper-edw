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
package org.hopper.edw.datavault.workflow.actions.generateexecutionmap;

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
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
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ActionGenerateExecutionMapDialog extends ActionDialog {

  private static final Class<?> PKG = ActionGenerateExecutionMap.class;

  private final ActionGenerateExecutionMap action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wSourceTabComp;
  private Composite wCrawlTabComp;

  public ActionGenerateExecutionMapDialog(
      Shell parent,
      ActionGenerateExecutionMap action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Name"));
    }
  }

  @Override
  public IAction open() {
    Shell parent = getParent();
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Title", action.getName()));

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.ACTION_GENERATE_EXECUTION_MAP);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "ActionGenerateExecutionMap.ActionName.Label"));
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
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    wSourceTabComp =
        createTab(
            wTabFolder, BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Tab.Source.Label"));
    wCrawlTabComp =
        createTab(
            wTabFolder, BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Tab.Crawl.Label"));

    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        action,
        null,
        wSourceTabComp,
        ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID,
        null);
    widgets.createCompositeWidgets(
        action,
        null,
        wCrawlTabComp,
        ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID,
        null);

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

  private static Composite createTab(CTabFolder folder, String label) {
    CTabItem item = new CTabItem(folder, SWT.NONE);
    item.setText(label);
    Composite composite = new Composite(folder, SWT.NONE);
    PropsUi.setLook(composite);
    composite.setLayout(new FormLayout());
    item.setControl(composite);
    return composite;
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    widgets.setWidgetsContents(
        action, wSourceTabComp, ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID);
    widgets.setWidgetsContents(
        action, wCrawlTabComp, ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID);
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    widgets.getWidgetsContents(action, ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID);
    widgets.getWidgetsContents(action, ActionGenerateExecutionMap.GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID);
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
