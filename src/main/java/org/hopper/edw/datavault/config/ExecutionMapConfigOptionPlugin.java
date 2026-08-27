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
package org.hopper.edw.datavault.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.hopper.edw.datavault.executionmap.ExecutionMapLineStyle;
import org.hopper.edw.datavault.layout.ElkLayoutValues;
import picocli.CommandLine;

@ConfigPlugin(
    id = "ExecutionMapConfigOptionPlugin",
    description = "Configuration options for execution map rendering")
@GuiPlugin(description = "i18n::ExecutionMapConfig.Tab.Name")
@Getter
@Setter
public class ExecutionMapConfigOptionPlugin
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static final Class<?> PKG = ExecutionMapConfigOptionPlugin.class;

  private static final String WIDGET_ID_LINE_STYLE = "10200-execution-map-line-style";

  @GuiWidgetElement(
      id = WIDGET_ID_LINE_STYLE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ExecutionMapConfigOptionPlugin.LineStyle.Message")
  @CommandLine.Option(
      names = {"--execution-map-line-style"},
      description = "Execution map edge line style: DIRECT_CENTER, SPLINE, or ORTHOGONAL")
  private ExecutionMapLineStyle executionMapLineStyle;

  public static ExecutionMapConfigOptionPlugin getInstance() {
    ExecutionMapConfigOptionPlugin instance = new ExecutionMapConfigOptionPlugin();
    instance.executionMapLineStyle =
        DataVaultConfigSingleton.getConfig().getExecutionMapLineStyleOrDefault();
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    try {
      if (executionMapLineStyle == null) {
        return false;
      }
      DataVaultConfig config = DataVaultConfigSingleton.getConfig();
      config.setExecutionMapLineStyle(executionMapLineStyle);
      DataVaultConfigSingleton.saveConfig();
      log.logBasic("Set execution map line style to " + executionMapLineStyle.getDescription());
      return true;
    } catch (Exception e) {
      throw new HopException("Error handling execution map configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {}

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {}

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      if (WIDGET_ID_LINE_STYLE.equals(widgetId)) {
        executionMapLineStyle =
            ElkLayoutValues.parseEnum(getComboText(control), ExecutionMapLineStyle.class);
        config.setExecutionMapLineStyle(executionMapLineStyle);
      }
    }
    try {
      DataVaultConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(
              PKG, "ExecutionMapConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(
              PKG, "ExecutionMapConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  private static String getComboText(Control control) {
    if (control instanceof ComboVar comboVar) {
      return comboVar.getText();
    }
    if (control instanceof Combo combo) {
      return combo.getText();
    }
    throw new IllegalArgumentException(
        "Unsupported combo control type: " + control.getClass().getName());
  }
}
