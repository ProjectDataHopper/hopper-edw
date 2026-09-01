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
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import picocli.CommandLine;

@ConfigPlugin(
    id = "DataVaultConfigOptionPlugin",
    description = "Configuration options for the data vault 2.0 plugin")
@GuiPlugin(description = "i18n::DataVaultConfig.Tab.Name")
@Getter
@Setter
public class DataVaultConfigOptionPlugin
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static final Class<?> PKG = DataVaultConfigOptionPlugin.class;

  private static final String WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL = "10000-draw-hash-keys-in-model";
  private static final String WIDGET_ID_ENFORCE_TARGET_UNICODE_CAPABILITY =
      "10005-enforce-target-unicode-capability";
  private static final String WIDGET_ID_WARN_TIMESTAMP_FRACTIONAL_PRECISION_LOSS =
      "10006-warn-timestamp-fractional-precision-loss";
  private static final String WIDGET_ID_MAX_UNDO_OPERATIONS = "10010-max-undo-operations";
  private static final String WIDGET_ID_DM_DEFAULT_SURROGATE_KEY = "10020-dm-default-surrogate-key";
  private static final String WIDGET_ID_DM_DEFAULT_VERSION = "10030-dm-default-version";
  private static final String WIDGET_ID_DM_DEFAULT_EFFECTIVE_FROM =
      "10040-dm-default-effective-from";
  private static final String WIDGET_ID_DM_DEFAULT_EFFECTIVE_TO = "10050-dm-default-effective-to";
  private static final String WIDGET_ID_DM_DEFAULT_LOAD_TIMESTAMP =
      "10060-dm-default-load-timestamp";
  private static final String WIDGET_ID_DM_DEFAULT_CURRENT_FLAG = "10070-dm-default-current-flag";
  private static final String WIDGET_ID_DEFAULT_PIPELINE_RUN_CONFIGURATION =
      "10080-default-pipeline-run-configuration";
  private static final String WIDGET_ID_DEFAULT_WORKFLOW_RUN_CONFIGURATION =
      "10090-default-workflow-run-configuration";
  private static final String WIDGET_ID_LIVE_UPDATE_POLL_INTERVAL_SECONDS =
      "10100-live-update-poll-interval-seconds";
  private static final String WIDGET_ID_SCHEMA_REMEDIATION_FOLDER =
      "10110-schema-remediation-folder";
  private static final String WIDGET_ID_REMIND_UNPUBLISHED_CATALOG_ON_SAVE =
      "10120-remind-unpublished-catalog-on-save";

  @GuiWidgetElement(
      id = WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.DrawHashKeysInModel.Message")
  @CommandLine.Option(
      names = {"--dv-draw-hash-keys"},
      description = "Enable or disable drawing hash keys in Data Vault models")
  private Boolean drawingHashKeysInModel;

  @GuiWidgetElement(
      id = WIDGET_ID_ENFORCE_TARGET_UNICODE_CAPABILITY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.EnforceTargetUnicodeCapability.Message",
      toolTip = "i18n::DataVaultConfigOptionPlugin.EnforceTargetUnicodeCapability.ToolTip")
  @CommandLine.Option(
      names = {"--dv-enforce-target-unicode"},
      description =
          "When true (default), model check errors if the target database is not Unicode-capable for EDW string storage")
  private Boolean enforceTargetUnicodeCapability;

  @GuiWidgetElement(
      id = WIDGET_ID_WARN_TIMESTAMP_FRACTIONAL_PRECISION_LOSS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.WarnTimestampFractionalPrecisionLoss.Message",
      toolTip = "i18n::DataVaultConfigOptionPlugin.WarnTimestampFractionalPrecisionLoss.ToolTip")
  @CommandLine.Option(
      names = {"--dv-warn-timestamp-precision-loss"},
      description =
          "When true (default), model check warns if source timestamp fractional precision exceeds the target engine (e.g. nanoseconds vs SingleStore DATETIME(6))")
  private Boolean warnTimestampFractionalPrecisionLoss;

  @GuiWidgetElement(
      id = WIDGET_ID_MAX_UNDO_OPERATIONS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.MaxUndoOperations.Message")
  @CommandLine.Option(
      names = {"--dv-max-undo-operations"},
      description = "Maximum number of undo/redo snapshots kept in memory for Data Vault models")
  private String maxUndoOperations;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_SURROGATE_KEY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultSurrogateKeyField.Message")
  private String dmDefaultSurrogateKeyField;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_VERSION,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultVersionField.Message")
  private String dmDefaultVersionField;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_EFFECTIVE_FROM,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultEffectiveFromField.Message")
  private String dmDefaultEffectiveFromField;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_EFFECTIVE_TO,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultEffectiveToField.Message")
  private String dmDefaultEffectiveToField;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_LOAD_TIMESTAMP,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultLoadTimestampField.Message")
  private String dmDefaultLoadTimestampField;

  @GuiWidgetElement(
      id = WIDGET_ID_DM_DEFAULT_CURRENT_FLAG,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.DmDefaultCurrentFlagField.Message")
  private String dmDefaultCurrentFlagField;

  @GuiWidgetElement(
      id = WIDGET_ID_DEFAULT_PIPELINE_RUN_CONFIGURATION,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::DataVaultConfigOptionPlugin.DefaultPipelineRunConfiguration.Message")
  private String defaultPipelineRunConfiguration;

  @GuiWidgetElement(
      id = WIDGET_ID_DEFAULT_WORKFLOW_RUN_CONFIGURATION,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::DataVaultConfigOptionPlugin.DefaultWorkflowRunConfiguration.Message")
  private String defaultWorkflowRunConfiguration;

  @GuiWidgetElement(
      id = WIDGET_ID_LIVE_UPDATE_POLL_INTERVAL_SECONDS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::DataVaultConfigOptionPlugin.LiveUpdatePollIntervalSeconds.Message",
      toolTip = "i18n::DataVaultConfigOptionPlugin.LiveUpdatePollIntervalSeconds.ToolTip")
  @CommandLine.Option(
      names = {"--dv-live-update-poll-interval-seconds"},
      description =
          "Seconds between live model-update metric refreshes in the Hop GUI (default 10)")
  private String liveUpdatePollIntervalSeconds;

  @GuiWidgetElement(
      id = WIDGET_ID_SCHEMA_REMEDIATION_FOLDER,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfigOptionPlugin.SchemaRemediationFolder.Message",
      toolTip = "i18n::DataVaultConfigOptionPlugin.SchemaRemediationFolder.ToolTip")
  @CommandLine.Option(
      names = {"--dv-schema-remediation-folder"},
      description = "Root folder for schema-remediation packages (workflow, SQL, HTML/MD report)")
  private String schemaRemediationFolder;

  @GuiWidgetElement(
      id = WIDGET_ID_REMIND_UNPUBLISHED_CATALOG_ON_SAVE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfigOptionPlugin.RemindUnpublishedCatalogOnSave.Message",
      toolTip = "i18n::DataVaultConfigOptionPlugin.RemindUnpublishedCatalogOnSave.ToolTip")
  @CommandLine.Option(
      names = {"--dv-remind-unpublished-catalog-on-save"},
      description =
          "When true (default), saving a source model asks to publish catalog feeds that lag the canvas")
  private Boolean remindUnpublishedCatalogOnSourceModelSave;

  public static DataVaultConfigOptionPlugin getInstance() {
    DataVaultConfigOptionPlugin instance = new DataVaultConfigOptionPlugin();
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    instance.drawingHashKeysInModel = config.isDrawingHashKeysInModel();
    instance.enforceTargetUnicodeCapability = config.isEnforceTargetUnicodeCapability();
    instance.warnTimestampFractionalPrecisionLoss = config.isWarnTimestampFractionalPrecisionLoss();
    instance.maxUndoOperations = Integer.toString(config.getMaxUndoOperations());
    DmDefaultFieldNames defaults = config.getDimensionalDefaultFieldNames();
    instance.dmDefaultSurrogateKeyField = defaults.getSurrogateKeyField();
    instance.dmDefaultVersionField = defaults.getVersionField();
    instance.dmDefaultEffectiveFromField = defaults.getEffectiveFromField();
    instance.dmDefaultEffectiveToField = defaults.getEffectiveToField();
    instance.dmDefaultLoadTimestampField = defaults.getLoadTimestampField();
    instance.dmDefaultCurrentFlagField = defaults.getCurrentFlagField();
    instance.defaultPipelineRunConfiguration = config.getDefaultPipelineRunConfiguration();
    instance.defaultWorkflowRunConfiguration = config.getDefaultWorkflowRunConfiguration();
    instance.liveUpdatePollIntervalSeconds =
        Integer.toString(config.getLiveUpdatePollIntervalSeconds());
    instance.schemaRemediationFolder = config.getSchemaRemediationFolderOrDefault();
    instance.remindUnpublishedCatalogOnSourceModelSave =
        config.isRemindUnpublishedCatalogOnSourceModelSave();
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    try {
      boolean changed = false;
      if (drawingHashKeysInModel != null) {
        config.setDrawingHashKeysInModel(drawingHashKeysInModel);
        if (drawingHashKeysInModel) {
          log.logBasic("Enabled drawing hash keys in model");
        } else {
          log.logBasic("Disabled drawing hash keys in model");
        }
        changed = true;
      }
      if (enforceTargetUnicodeCapability != null) {
        config.setEnforceTargetUnicodeCapability(enforceTargetUnicodeCapability);
        log.logBasic(
            enforceTargetUnicodeCapability
                ? "Enabled hard enforcement of Unicode-capable target databases"
                : "Disabled hard enforcement of Unicode-capable target databases (warnings only)");
        changed = true;
      }
      if (warnTimestampFractionalPrecisionLoss != null) {
        config.setWarnTimestampFractionalPrecisionLoss(warnTimestampFractionalPrecisionLoss);
        log.logBasic(
            warnTimestampFractionalPrecisionLoss
                ? "Enabled warnings for timestamp fractional precision loss"
                : "Disabled warnings for timestamp fractional precision loss");
        changed = true;
      }
      if (maxUndoOperations != null) {
        config.setMaxUndoOperations(parseMaxUndoOperations(maxUndoOperations));
        log.logBasic(
            "Set maximum Data Vault undo/redo operations to " + config.getMaxUndoOperations());
        changed = true;
      }
      if (liveUpdatePollIntervalSeconds != null) {
        config.setLiveUpdatePollIntervalSeconds(
            parseLiveUpdatePollIntervalSeconds(liveUpdatePollIntervalSeconds));
        log.logBasic(
            "Set live model-update poll interval to "
                + config.getLiveUpdatePollIntervalSeconds()
                + " seconds");
        changed = true;
      }
      if (remindUnpublishedCatalogOnSourceModelSave != null) {
        config.setRemindUnpublishedCatalogOnSourceModelSave(
            remindUnpublishedCatalogOnSourceModelSave);
        log.logBasic(
            remindUnpublishedCatalogOnSourceModelSave
                ? "Enabled reminder to publish stale catalog feeds when saving a source model"
                : "Disabled reminder to publish stale catalog feeds when saving a source model");
        changed = true;
      }
      if (changed) {
        DataVaultConfigSingleton.saveConfig();
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling data vault plugin configuration options", e);
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
      switch (widgetId) {
        case WIDGET_ID_DRAW_HASH_KEYS_IN_MODEL:
          drawingHashKeysInModel = ((Button) control).getSelection();
          config.setDrawingHashKeysInModel(drawingHashKeysInModel);
          break;
        case WIDGET_ID_ENFORCE_TARGET_UNICODE_CAPABILITY:
          enforceTargetUnicodeCapability = ((Button) control).getSelection();
          config.setEnforceTargetUnicodeCapability(enforceTargetUnicodeCapability);
          break;
        case WIDGET_ID_WARN_TIMESTAMP_FRACTIONAL_PRECISION_LOSS:
          warnTimestampFractionalPrecisionLoss = ((Button) control).getSelection();
          config.setWarnTimestampFractionalPrecisionLoss(warnTimestampFractionalPrecisionLoss);
          break;
        case WIDGET_ID_MAX_UNDO_OPERATIONS:
          maxUndoOperations = getTextValue(control);
          config.setMaxUndoOperations(parseMaxUndoOperations(maxUndoOperations));
          break;
        case WIDGET_ID_DM_DEFAULT_SURROGATE_KEY:
          dmDefaultSurrogateKeyField = getTextValue(control);
          config.getDimensionalDefaultFieldNames().setSurrogateKeyField(dmDefaultSurrogateKeyField);
          break;
        case WIDGET_ID_DM_DEFAULT_VERSION:
          dmDefaultVersionField = getTextValue(control);
          config.getDimensionalDefaultFieldNames().setVersionField(dmDefaultVersionField);
          break;
        case WIDGET_ID_DM_DEFAULT_EFFECTIVE_FROM:
          dmDefaultEffectiveFromField = getTextValue(control);
          config
              .getDimensionalDefaultFieldNames()
              .setEffectiveFromField(dmDefaultEffectiveFromField);
          break;
        case WIDGET_ID_DM_DEFAULT_EFFECTIVE_TO:
          dmDefaultEffectiveToField = getTextValue(control);
          config.getDimensionalDefaultFieldNames().setEffectiveToField(dmDefaultEffectiveToField);
          break;
        case WIDGET_ID_DM_DEFAULT_LOAD_TIMESTAMP:
          dmDefaultLoadTimestampField = getTextValue(control);
          config
              .getDimensionalDefaultFieldNames()
              .setLoadTimestampField(dmDefaultLoadTimestampField);
          break;
        case WIDGET_ID_DM_DEFAULT_CURRENT_FLAG:
          dmDefaultCurrentFlagField = getTextValue(control);
          config.getDimensionalDefaultFieldNames().setCurrentFlagField(dmDefaultCurrentFlagField);
          break;
        case WIDGET_ID_DEFAULT_PIPELINE_RUN_CONFIGURATION:
          defaultPipelineRunConfiguration = getTextValue(control);
          config.setDefaultPipelineRunConfiguration(defaultPipelineRunConfiguration);
          break;
        case WIDGET_ID_DEFAULT_WORKFLOW_RUN_CONFIGURATION:
          defaultWorkflowRunConfiguration = getTextValue(control);
          config.setDefaultWorkflowRunConfiguration(defaultWorkflowRunConfiguration);
          break;
        case WIDGET_ID_LIVE_UPDATE_POLL_INTERVAL_SECONDS:
          liveUpdatePollIntervalSeconds = getTextValue(control);
          config.setLiveUpdatePollIntervalSeconds(
              parseLiveUpdatePollIntervalSeconds(liveUpdatePollIntervalSeconds));
          break;
        case WIDGET_ID_SCHEMA_REMEDIATION_FOLDER:
          schemaRemediationFolder = getTextValue(control);
          config.setSchemaRemediationFolder(schemaRemediationFolder);
          break;
        case WIDGET_ID_REMIND_UNPUBLISHED_CATALOG_ON_SAVE:
          remindUnpublishedCatalogOnSourceModelSave = ((Button) control).getSelection();
          config.setRemindUnpublishedCatalogOnSourceModelSave(
              remindUnpublishedCatalogOnSourceModelSave);
          break;
        default:
          break;
      }
    }
    try {
      DataVaultConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(
              PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(
              PKG, "DataVaultConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  private static String getTextValue(Control control) {
    if (control instanceof TextVar textVar) {
      return textVar.getText();
    }
    if (control instanceof Text text) {
      return text.getText();
    }
    if (control instanceof MetaSelectionLine<?> metaSelectionLine) {
      return metaSelectionLine.getText();
    }
    throw new IllegalArgumentException(
        "Unsupported text control type: " + control.getClass().getName());
  }

  private static int parseMaxUndoOperations(String value) {
    if (value == null || value.isBlank()) {
      return DataVaultConfig.DEFAULT_MAX_UNDO_OPERATIONS;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(1, parsed);
    } catch (NumberFormatException e) {
      return DataVaultConfig.DEFAULT_MAX_UNDO_OPERATIONS;
    }
  }

  private static int parseLiveUpdatePollIntervalSeconds(String value) {
    if (value == null || value.isBlank()) {
      return DataVaultConfig.DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(1, parsed);
    } catch (NumberFormatException e) {
      return DataVaultConfig.DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS;
    }
  }
}
