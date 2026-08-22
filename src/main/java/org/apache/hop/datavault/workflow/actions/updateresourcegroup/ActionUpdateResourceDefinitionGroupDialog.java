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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiDataVaultModelDialog;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * Dialog for {@link ActionUpdateResourceDefinitionGroup}. Options are grouped into topic tabs
 * (Selection, Run, Operations, Validation, Data catalog, Metrics, Reports) using the same multi-
 * {@code parentId} {@link GuiCompositeWidgets} pattern as Data Vault Update.
 */
public class ActionUpdateResourceDefinitionGroupDialog extends ActionDialog {

  private static final Class<?> PKG = ActionUpdateResourceDefinitionGroup.class;

  private final ActionUpdateResourceDefinitionGroup action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private final Map<String, Composite> tabComposites = new LinkedHashMap<>();

  public ActionUpdateResourceDefinitionGroupDialog(
      Shell parent,
      ActionUpdateResourceDefinitionGroup action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(
        BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Title", action.getName()),
        action);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.ACTION_DATAVAULT_UPDATE);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);
    wTabFolder.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wSpacer, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());

    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Selection.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Selection.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_RUN_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Run.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Run.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Operations.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Operations.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Validation.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Validation.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_CATALOG_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Catalog.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Catalog.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_METRICS_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Metrics.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Metrics.ToolTip");
    addTab(
        wTabFolder,
        ActionUpdateResourceDefinitionGroup.GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID,
        "ActionUpdateResourceDefinitionGroup.Tab.Reports.Label",
        "ActionUpdateResourceDefinitionGroup.Tab.Reports.ToolTip");

    try {
      widgets = new GuiCompositeWidgets(variables);
      for (Map.Entry<String, Composite> entry : tabComposites.entrySet()) {
        widgets.createCompositeWidgets(action, null, entry.getValue(), entry.getKey(), null);
      }
      widgets.setWidgetsListener(
          new GuiCompositeWidgetsAdapter() {
            @Override
            public void widgetModified(
                GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
              action.setChanged();
            }
          });
      setWidgetsContent();
    } catch (Throwable t) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Name"),
          "Unable to create Update Resource Definition Group settings widgets",
          t instanceof Exception ? (Exception) t : new Exception(t));
    }

    wTabFolder.setSelection(0);

    boolean changedBeforeOpen = action.hasChanged();
    focusActionName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    if (cancelled) {
      action.setChanged(changedBeforeOpen);
      return null;
    }
    return action;
  }

  private void addTab(CTabFolder tabFolder, String tabId, String titleKey, String toolTipKey) {
    Composite tabComp =
        HopGuiDataVaultModelDialog.createTabComposite(
            tabFolder,
            BaseMessages.getString(PKG, titleKey),
            BaseMessages.getString(PKG, toolTipKey));
    tabComposites.put(tabId, tabComp);
  }

  @Override
  protected void onActionNameModified() {
    action.setChanged();
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    if (widgets == null) {
      return;
    }
    for (Map.Entry<String, Composite> entry : tabComposites.entrySet()) {
      widgets.setWidgetsContents(action, entry.getValue(), entry.getKey());
    }
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    if (widgets == null) {
      return;
    }
    for (String tabId : tabComposites.keySet()) {
      widgets.getWidgetsContents(action, tabId);
    }
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
