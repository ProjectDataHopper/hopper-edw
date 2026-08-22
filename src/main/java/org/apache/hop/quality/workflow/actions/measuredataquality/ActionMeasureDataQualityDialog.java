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
package org.apache.hop.quality.workflow.actions.measuredataquality;

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

public class ActionMeasureDataQualityDialog extends ActionDialog {

  private static final Class<?> PKG = ActionMeasureDataQuality.class;

  private final ActionMeasureDataQuality action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wSettingsComp;

  public ActionMeasureDataQualityDialog(
      Shell parent,
      ActionMeasureDataQuality action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionMeasureDataQuality.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(
        BaseMessages.getString(PKG, "ActionMeasureDataQuality.Title", action.getName()), action);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    wSettingsComp = new Composite(shell, SWT.NONE);
    PropsUi.setLook(wSettingsComp);
    wSettingsComp.setLayout(new FormLayout());
    wSettingsComp.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wSpacer, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    widgets = new GuiCompositeWidgets(variables);
    widgets.createCompositeWidgets(
        action, null, wSettingsComp, ActionMeasureDataQuality.GUI_PLUGIN_ELEMENT_PARENT_ID, null);

    setWidgetsContent();
    boolean changedBeforeOpen = action.hasChanged();
    focusActionName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    if (cancelled) {
      action.setChanged(changedBeforeOpen);
      return null;
    }
    return action;
  }

  @Override
  protected void onActionNameModified() {
    action.setChanged();
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    widgets.setWidgetsContents(
        action, wSettingsComp, ActionMeasureDataQuality.GUI_PLUGIN_ELEMENT_PARENT_ID);
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    widgets.getWidgetsContents(action, ActionMeasureDataQuality.GUI_PLUGIN_ELEMENT_PARENT_ID);
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
