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
package org.hopper.edw.datavault.workflow.actions.validatedefinitions;

import java.lang.reflect.Field;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiElements;
import org.apache.hop.core.gui.plugin.GuiRegistry;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

public class ActionValidateResourceDefinitionsDialog extends ActionDialog {

  private static final Class<?> PKG = ActionValidateResourceDefinitions.class;

  private final ActionValidateResourceDefinitions action;
  private boolean cancelled = true;
  private GuiCompositeWidgets widgets;
  private Composite wSettingsComp;

  public ActionValidateResourceDefinitionsDialog(
      Shell parent,
      ActionValidateResourceDefinitions action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (Utils.isEmpty(action.getName())) {
      action.setName(BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(
        BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Title", action.getName()),
        action);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    DialogHelpSupport.installLocalHelpButton(
        shell, HelpTopics.ACTION_VALIDATE_RESOURCE_DEFINITIONS);

    // Scrolled settings area so ComboVar rows still fit on smaller screens.
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
          action,
          null,
          wSettingsComp,
          ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID,
          null);

      if (widgets.getWidgetsMap() == null || widgets.getWidgetsMap().isEmpty()) {
        LogChannel.UI.logError(
            "Validate Resource Definitions: createCompositeWidgets produced no controls for parent "
                + ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID
                + " on "
                + action.getClass().getName()
                + " (GuiRegistry missing elements?)");
      }

      setWidgetsContent();

      // Populate version ComboVars after create (do not rely solely on comboValuesMethod).
      refreshCatalogVersionCombos();

      widgets.setWidgetsListener(
          new GuiCompositeWidgetsAdapter() {
            @Override
            public void widgetModified(
                GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
              action.setChanged();
              if (ActionValidateResourceDefinitions.WIDGET_ID_RESOURCE_GROUP.equals(widgetId)) {
                // Keep action.resourceDefinitionGroup in sync so version lists re-resolve.
                compositeWidgets.getWidgetsContents(
                    action, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
                refreshCatalogVersionCombos();
              }
            }
          });

      // Re-read versions.json when the dialog regains focus (e.g. after Tag version elsewhere).
      shell.addListener(SWT.Activate, e -> refreshCatalogVersionCombos());

      wSettingsComp.layout(true, true);
      scrolled.setMinSize(wSettingsComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    } catch (Throwable t) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ActionValidateResourceDefinitions.Name"),
          "Unable to create Validate Resource Definitions settings widgets",
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

  /**
   * Hop registers {@code @GuiWidgetElement} fields during {@code HopGuiEnvironment.initGuiPlugins}.
   * If that scan missed this class (stale plugin jar, classloader order, partial init failure),
   * {@link GuiCompositeWidgets#createCompositeWidgets} finds nothing and the settings area is
   * blank. Re-register from the live action class so the dialog is self-healing.
   */
  private void ensureGuiElementsRegistered() {
    String className = action.getClass().getName();
    String parentId = ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID;
    GuiRegistry registry = GuiRegistry.getInstance();
    GuiElements existing = registry.findGuiElements(className, parentId);
    if (existing != null && existing.getChildren() != null && !existing.getChildren().isEmpty()) {
      return;
    }

    LogChannel.UI.logBasic(
        "Validate Resource Definitions: registering GUI widgets for "
            + className
            + " (parent "
            + parentId
            + ")");

    // Drop empty root if present so we start clean.
    if (existing != null && (existing.getChildren() == null || existing.getChildren().isEmpty())) {
      // putGuiElements overwrites the parent map entry when we re-add below via
      // addGuiWidgetElement.
    }

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
      LogChannel.UI.logBasic(
          "Validate Resource Definitions: registered "
              + root.getChildren().size()
              + " settings widget(s)");
    } else {
      LogChannel.UI.logError(
          "Validate Resource Definitions: GUI widget registration produced no elements for parent "
              + parentId);
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
          action, wSettingsComp, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
    }
  }

  private void getWidgetsContent() {
    action.setName(wName.getText());
    if (widgets != null) {
      widgets.getWidgetsContents(
          action, ActionValidateResourceDefinitions.GUI_PLUGIN_ELEMENT_PARENT_ID);
    }
  }

  /**
   * Reload catalog version tags for ComboVar fields after the resource definition group changes or
   * when the dialog is activated. Reads {@code versions.json} from the FILE catalog (no tag cache).
   * Uses dialog / HopGui variables so {@code ${PROJECT_HOME}} resolves like Data Catalog tagging.
   * Preserves any free-typed value (including variable expressions).
   */
  private void refreshCatalogVersionCombos() {
    if (widgets == null || widgets.getWidgetsMap() == null || widgets.getWidgetsMap().isEmpty()) {
      return;
    }
    if (shell != null && shell.isDisposed()) {
      return;
    }
    try {
      String previousTarget =
          getComboText(ActionValidateResourceDefinitions.WIDGET_ID_TARGET_VERSION);
      String previousBaseline =
          getComboText(ActionValidateResourceDefinitions.WIDGET_ID_BASELINE_VERSION);

      IHopMetadataProvider metadataProvider = null;
      IVariables listVariables = this.variables;
      HopGui hopGui = HopGui.getInstance();
      if (hopGui != null) {
        metadataProvider = hopGui.getMetadataProvider();
        // Prefer HopGui project variables when dialog variables are incomplete.
        if (hopGui.getVariables() != null) {
          listVariables = hopGui.getVariables();
        }
      }
      if (metadataProvider == null) {
        metadataProvider = this.metadataProvider;
      }

      List<String> tags =
          action.listCatalogVersionTags(metadataProvider, LogChannel.UI, listVariables);
      String[] items = tags.toArray(new String[0]);
      widgets.setComboValues(ActionValidateResourceDefinitions.WIDGET_ID_TARGET_VERSION, items);
      widgets.setComboValues(ActionValidateResourceDefinitions.WIDGET_ID_BASELINE_VERSION, items);

      setComboText(ActionValidateResourceDefinitions.WIDGET_ID_TARGET_VERSION, previousTarget);
      setComboText(ActionValidateResourceDefinitions.WIDGET_ID_BASELINE_VERSION, previousBaseline);
    } catch (Throwable t) {
      LogChannel.UI.logError("Unable to refresh catalog version combos", t);
    }
  }

  private String getComboText(String widgetId) {
    Control control = widgets.getWidgetsMap().get(widgetId);
    if (control instanceof ComboVar comboVar) {
      return Const.NVL(comboVar.getText(), "");
    }
    if (control instanceof Combo combo) {
      return Const.NVL(combo.getText(), "");
    }
    return "";
  }

  private void setComboText(String widgetId, String text) {
    Control control = widgets.getWidgetsMap().get(widgetId);
    String value = Const.NVL(text, "");
    if (control instanceof ComboVar comboVar) {
      comboVar.setText(value);
    } else if (control instanceof Combo combo) {
      combo.setText(value);
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
