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
 *
 */

package org.apache.hop.datavault.workflow.actions.exportdatalineage;

import java.lang.reflect.Field;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiElements;
import org.apache.hop.core.gui.plugin.GuiRegistry;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
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
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class ActionExportDataLineageDialog extends ActionDialog {

  private static final Class<?> PKG = ActionExportDataLineage.class;

  private final ActionExportDataLineage action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wSettingsComp;

  public ActionExportDataLineageDialog(
      Shell parent,
      ActionExportDataLineage action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionExportDataLineage.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(
        BaseMessages.getString(PKG, "ActionExportDataLineage.Title", action.getName()), action);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.ACTION_EXPORT_DATA_LINEAGE);

    int margin = PropsUi.getMargin();

    ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(scrolled);
    scrolled.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(new FormAttachment(wSpacer, margin))
            .right()
            .bottom(new FormAttachment(wOk, -2 * margin))
            .result());
    scrolled.setExpandHorizontal(true);
    scrolled.setExpandVertical(true);

    wSettingsComp = new Composite(scrolled, SWT.NONE);
    PropsUi.setLook(wSettingsComp);
    wSettingsComp.setLayout(new FormLayout());
    scrolled.setContent(wSettingsComp);

    try {
      ensureGuiElementsRegistered();

      widgets = new GuiCompositeWidgets(variables);
      widgets.createCompositeWidgets(
          action, null, wSettingsComp, ActionExportDataLineage.GUI_PLUGIN_ELEMENT_PARENT_ID, null);

      setWidgetsContent();

      widgets.setWidgetsListener(
          new GuiCompositeWidgetsAdapter() {
            @Override
            public void widgetModified(
                GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
              action.setChanged();
            }
          });

      wSettingsComp.layout(true, true);
      scrolled.setMinSize(wSettingsComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    } catch (Throwable t) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ActionExportDataLineage.Name"),
          "Unable to create Export Data Lineage settings widgets",
          t instanceof Exception ? (Exception) t : new Exception(t));
    }

    boolean changedBeforeOpen = action.hasChanged();
    focusActionName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());

    if (cancelled) {
      action.setChanged(changedBeforeOpen);
      return null;
    }
    return action;
  }

  private void ensureGuiElementsRegistered() {
    String className = action.getClass().getName();
    String parentId = ActionExportDataLineage.GUI_PLUGIN_ELEMENT_PARENT_ID;
    GuiRegistry registry = GuiRegistry.getInstance();
    GuiElements existing = registry.findGuiElements(className, parentId);
    if (existing != null && existing.getChildren() != null && !existing.getChildren().isEmpty()) {
      return;
    }

    LogChannel.UI.logBasic(
        "Export Data Lineage: registering GUI widgets for " + className + " (parent " + parentId + ")");

    for (Field field : action.getClass().getDeclaredFields()) {
      GuiWidgetElement element = field.getAnnotation(GuiWidgetElement.class);
      if (element == null) {
        continue;
      }
      try {
        registry.addGuiWidgetElement(className, element, field);
      } catch (Throwable t) {
        LogChannel.UI.logError(
            "Unable to register GUI widget for field '" + field.getName() + "'", t);
      }
    }

    GuiElements root = registry.findGuiElements(className, parentId);
    if (root != null) {
      root.sortChildren();
    }
  }

  @Override
  protected void onActionNameModified() {
    action.setChanged();
  }

  private void setWidgetsContent() {
    wName.setText(Const.NVL(action.getName(), ""));
    if (widgets != null) {
      widgets.setWidgetsContents(
          action, wSettingsComp, ActionExportDataLineage.GUI_PLUGIN_ELEMENT_PARENT_ID);
    }
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    if (widgets != null) {
      widgets.getWidgetsContents(action, ActionExportDataLineage.GUI_PLUGIN_ELEMENT_PARENT_ID);
    }
  }

  private void ok() {
    getWidgetsContent();
    action.setChanged();
    cancelled = false;
    dispose();
  }

  private void cancel() {
    cancelled = true;
    dispose();
  }
}
