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

import java.util.Arrays;
import java.util.List;
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
import org.hopper.edw.datavault.ai.DvAiProviderPreset;
import org.hopper.edw.datavault.ai.HopAiConfig;
import org.hopper.edw.datavault.ai.HopAiConfigSingleton;
import org.hopper.edw.datavault.ai.HopAiHealthCheckSupport;
import org.hopper.edw.datavault.ai.HopAiProviderSettings;
import org.hopper.edw.datavault.ai.HopAiProviderSettingsSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.PasswordTextVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import picocli.CommandLine;

@ConfigPlugin(
    id = "HopAiConfigOptionPlugin",
    description = "Configuration options for the Hop AI assistant")
@GuiPlugin(description = "i18n::HopAiConfig.Tab.Name")
@Getter
@Setter
public class HopAiConfigOptionPlugin implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static final Class<?> PKG = HopAiConfigOptionPlugin.class;

  private static final String WIDGET_ID_AI_ENABLED = "10200-ai-enabled";
  private static final String WIDGET_ID_AI_PROVIDER_PRESET = "10210-ai-provider-preset";
  private static final String WIDGET_ID_AI_API_KEY = "10220-ai-api-key";
  private static final String WIDGET_ID_AI_BASE_URL = "10230-ai-base-url";
  private static final String WIDGET_ID_AI_MODEL_NAME = "10240-ai-model-name";
  private static final String WIDGET_ID_AI_TEMPERATURE = "10250-ai-temperature";
  private static final String WIDGET_ID_AI_TEST_SERVICE = "10260-ai-test-service";

  private static GuiCompositeWidgets activeWidgets;
  private boolean suppressWidgetEvents;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_ENABLED,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::HopAiConfigOptionPlugin.AiEnabled.Message")
  @CommandLine.Option(
      names = {"--hop-ai-enabled"},
      description = "Enable AI advisory in Hop GUI")
  private Boolean aiEnabled;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_PROVIDER_PRESET,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      label = "i18n::HopAiConfigOptionPlugin.AiProviderPreset.Message",
      comboValuesMethod = "getAiProviderPresetOptions")
  @CommandLine.Option(
      names = {"--hop-ai-provider"},
      description = "AI provider preset for Hop advisory")
  private String aiProviderPreset;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_API_KEY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      password = true,
      label = "i18n::HopAiConfigOptionPlugin.AiApiKey.Message")
  @CommandLine.Option(
      names = {"--hop-ai-api-key"},
      description = "API key for the Hop AI provider")
  private String aiApiKey;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_BASE_URL,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopAiConfigOptionPlugin.AiBaseUrl.Message")
  @CommandLine.Option(
      names = {"--hop-ai-base-url"},
      description = "Optional override of the AI provider base URL")
  private String aiBaseUrl;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_MODEL_NAME,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopAiConfigOptionPlugin.AiModelName.Message")
  @CommandLine.Option(
      names = {"--hop-ai-model-name"},
      description = "Optional override of the AI model name")
  private String aiModelName;

  @GuiWidgetElement(
      id = WIDGET_ID_AI_TEMPERATURE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopAiConfigOptionPlugin.AiTemperature.Message")
  @CommandLine.Option(
      names = {"--hop-ai-temperature"},
      description = "Sampling temperature for Hop AI advisory")
  private String aiTemperature;

  public static HopAiConfigOptionPlugin getInstance() {
    HopAiConfigOptionPlugin instance = new HopAiConfigOptionPlugin();
    HopAiConfig config = HopAiConfigSingleton.getConfig();
    instance.aiEnabled = config.isAiEnabled();
    instance.aiProviderPreset = DvAiProviderPreset.resolve(config.getAiProviderPreset()).getLabel();
    instance.aiApiKey = config.getAiApiKey();
    instance.aiBaseUrl = config.getAiBaseUrl();
    instance.aiModelName = config.getAiModelName();
    instance.aiTemperature = config.getAiTemperature();
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    HopAiConfig config = HopAiConfigSingleton.getConfig();
    try {
      boolean changed = false;
      if (aiEnabled != null) {
        config.setAiEnabled(aiEnabled);
        log.logBasic(aiEnabled ? "Enabled Hop AI advisory" : "Disabled Hop AI advisory");
        changed = true;
      }
      if (aiProviderPreset != null) {
        HopAiProviderSettingsSupport.switchProvider(
            config, DvAiProviderPreset.fromLabel(aiProviderPreset));
        changed = true;
      }
      if (aiApiKey != null) {
        config.setAiApiKey(aiApiKey);
        changed = true;
      }
      if (aiBaseUrl != null) {
        config.setAiBaseUrl(aiBaseUrl);
        changed = true;
      }
      if (aiModelName != null) {
        config.setAiModelName(aiModelName);
        changed = true;
      }
      if (aiTemperature != null) {
        config.setAiTemperature(aiTemperature);
        changed = true;
      }
      if (changed) {
        HopAiProviderSettingsSupport.saveCurrentProvider(config);
        HopAiConfigSingleton.saveConfig();
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling Hop AI configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
    activeWidgets = compositeWidgets;
  }

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {}

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    if (suppressWidgetEvents) {
      return;
    }
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    HopAiConfig config = HopAiConfigSingleton.getConfig();
    HopAiProviderSettingsSupport.ensureMigrated(config);

    DvAiProviderPreset currentPreset = DvAiProviderPreset.resolve(config.getAiProviderPreset());
    Control providerControl = compositeWidgets.getWidgetsMap().get(WIDGET_ID_AI_PROVIDER_PRESET);
    if (providerControl != null) {
      String selectedLabel = getComboText(providerControl);
      DvAiProviderPreset selectedPreset = DvAiProviderPreset.fromLabel(selectedLabel);
      if (selectedPreset != currentPreset) {
        HopAiProviderSettingsSupport.switchProvider(
            config, currentPreset, selectedPreset, readProviderFields(compositeWidgets));
        applyProviderFields(compositeWidgets, config);
        aiProviderPreset = selectedLabel;
      }
    }

    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_AI_ENABLED:
          aiEnabled = ((Button) control).getSelection();
          config.setAiEnabled(aiEnabled);
          break;
        case WIDGET_ID_AI_PROVIDER_PRESET:
          aiProviderPreset = getComboText(control);
          config.setAiProviderPreset(DvAiProviderPreset.fromLabel(aiProviderPreset).name());
          break;
        case WIDGET_ID_AI_API_KEY:
          aiApiKey = getTextValue(control);
          config.setAiApiKey(aiApiKey);
          break;
        case WIDGET_ID_AI_BASE_URL:
          aiBaseUrl = getTextValue(control);
          config.setAiBaseUrl(aiBaseUrl);
          break;
        case WIDGET_ID_AI_MODEL_NAME:
          aiModelName = getTextValue(control);
          config.setAiModelName(aiModelName);
          break;
        case WIDGET_ID_AI_TEMPERATURE:
          aiTemperature = getTextValue(control);
          config.setAiTemperature(aiTemperature);
          break;
        default:
          break;
      }
    }
    HopAiProviderSettingsSupport.saveCurrentProvider(config);
    saveConfig();
  }

  @GuiWidgetElement(
      id = WIDGET_ID_AI_TEST_SERVICE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.BUTTON,
      label = "i18n::HopAiConfigOptionPlugin.TestService.Message",
      order = "10260")
  public void testAiService(HopAiConfigOptionPlugin plugin) {
    GuiCompositeWidgets widgets = activeWidgets;
    if (widgets != null) {
      plugin.persistContents(widgets);
    }
    HopAiConfig config = new HopAiConfig(HopAiConfigSingleton.getConfig());
    Shell shell = HopGui.getInstance().getShell();
    IVariables variables = HopGui.getInstance().getVariables();
    Thread healthCheckThread =
        new Thread(
            () -> {
              try {
                String message = HopAiHealthCheckSupport.check(config, variables);
                shell
                    .getDisplay()
                    .asyncExec(
                        () -> {
                          MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION);
                          box.setText(
                              BaseMessages.getString(
                                  PKG, "HopAiConfigOptionPlugin.TestService.Success.Header"));
                          box.setMessage(message);
                          box.open();
                        });
              } catch (Exception e) {
                shell
                    .getDisplay()
                    .asyncExec(
                        () ->
                            new ErrorDialog(
                                shell,
                                BaseMessages.getString(
                                    PKG, "HopAiConfigOptionPlugin.TestService.Error.Header"),
                                BaseMessages.getString(
                                    PKG, "HopAiConfigOptionPlugin.TestService.Error.Message"),
                                e));
              }
            },
            "HopAiHealthCheck");
    healthCheckThread.setDaemon(true);
    healthCheckThread.start();
  }

  private void saveConfig() {
    try {
      HopAiConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(PKG, "HopAiConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(PKG, "HopAiConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  private HopAiProviderSettings readProviderFields(GuiCompositeWidgets compositeWidgets) {
    HopAiProviderSettings settings = new HopAiProviderSettings();
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_AI_API_KEY:
          settings.setApiKey(getTextValue(control));
          break;
        case WIDGET_ID_AI_BASE_URL:
          settings.setBaseUrl(getTextValue(control));
          break;
        case WIDGET_ID_AI_MODEL_NAME:
          settings.setModelName(getTextValue(control));
          break;
        case WIDGET_ID_AI_TEMPERATURE:
          settings.setTemperature(getTextValue(control));
          break;
        default:
          break;
      }
    }
    return settings;
  }

  private void applyProviderFields(GuiCompositeWidgets compositeWidgets, HopAiConfig config) {
    aiApiKey = config.getAiApiKey();
    aiBaseUrl = config.getAiBaseUrl();
    aiModelName = config.getAiModelName();
    aiTemperature = config.getAiTemperature();
    suppressWidgetEvents = true;
    try {
      for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
        Control control = compositeWidgets.getWidgetsMap().get(widgetId);
        switch (widgetId) {
          case WIDGET_ID_AI_API_KEY:
            setTextValue(control, aiApiKey);
            break;
          case WIDGET_ID_AI_BASE_URL:
            setTextValue(control, aiBaseUrl);
            break;
          case WIDGET_ID_AI_MODEL_NAME:
            setTextValue(control, aiModelName);
            break;
          case WIDGET_ID_AI_TEMPERATURE:
            setTextValue(control, aiTemperature);
            break;
          default:
            break;
        }
      }
    } finally {
      suppressWidgetEvents = false;
    }
  }

  public List<String> getAiProviderPresetOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(DvAiProviderPreset.labels());
  }

  private static String getTextValue(Control control) {
    if (control instanceof PasswordTextVar passwordTextVar) {
      return passwordTextVar.getText();
    }
    if (control instanceof TextVar textVar) {
      return textVar.getText();
    }
    if (control instanceof Text text) {
      return text.getText();
    }
    throw new IllegalArgumentException(
        "Unsupported text control type: " + control.getClass().getName());
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

  private static void setTextValue(Control control, String value) {
    String text = value != null ? value : "";
    if (control instanceof PasswordTextVar passwordTextVar) {
      passwordTextVar.setText(text);
      return;
    }
    if (control instanceof TextVar textVar) {
      textVar.setText(text);
      return;
    }
    if (control instanceof Text textWidget) {
      textWidget.setText(text);
      return;
    }
    throw new IllegalArgumentException(
        "Unsupported text control type: " + control.getClass().getName());
  }
}
