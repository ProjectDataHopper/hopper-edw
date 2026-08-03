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
package org.apache.hop.datavault.config;

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
import org.apache.hop.datavault.layout.ElkCrossingMinimization;
import org.apache.hop.datavault.layout.ElkCycleBreaking;
import org.apache.hop.datavault.layout.ElkLayeringStrategy;
import org.apache.hop.datavault.layout.ElkLayout;
import org.apache.hop.datavault.layout.ElkLayoutAlgorithm;
import org.apache.hop.datavault.layout.ElkLayoutDirection;
import org.apache.hop.datavault.layout.ElkLayoutValues;
import org.apache.hop.datavault.layout.ElkNodePlacement;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import picocli.CommandLine;

@ConfigPlugin(
    id = "ElkLayoutConfigOptionPlugin",
    description = "Configuration options for ELK graph layout")
@GuiPlugin(
    description = "i18n::ElkLayoutConfig.Tab.Name" // label in options dialog
    )
@Getter
@Setter
public class ElkLayoutConfigOptionPlugin
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static final Class<?> PKG = ElkLayoutConfigOptionPlugin.class;

  private static final String WIDGET_ID_LAYOUT_ENABLED = "10020-layout-enabled";
  private static final String WIDGET_ID_LAYOUT_ALGORITHM = "10025-layout-algorithm";
  private static final String WIDGET_ID_LAYOUT_TARGET_WIDTH = "10026-layout-target-width";
  private static final String WIDGET_ID_LAYOUT_DIRECTION = "10030-layout-direction";
  private static final String WIDGET_ID_LAYOUT_SPACING_WITHIN_LAYER =
      "10040-layout-spacing-within-layer";
  private static final String WIDGET_ID_LAYOUT_SPACING_BETWEEN_LAYERS =
      "10050-layout-spacing-between-layers";
  private static final String WIDGET_ID_LAYOUT_SPACING_EDGE_NODE = "10060-layout-spacing-edge-node";
  private static final String WIDGET_ID_LAYOUT_CROSSING_MINIMIZATION =
      "10070-layout-crossing-minimization";
  private static final String WIDGET_ID_LAYOUT_NODE_PLACEMENT = "10080-layout-node-placement";
  private static final String WIDGET_ID_LAYOUT_LAYERING_STRATEGY = "10090-layout-layering-strategy";
  private static final String WIDGET_ID_LAYOUT_CYCLE_BREAKING = "10100-layout-cycle-breaking";
  private static final String WIDGET_ID_LAYOUT_ORIGIN_X = "10110-layout-origin-x";
  private static final String WIDGET_ID_LAYOUT_ORIGIN_Y = "10120-layout-origin-y";
  private static final String WIDGET_ID_LAYOUT_GRID_SIZE = "10130-layout-grid-size";
  private static final String WIDGET_ID_LAYOUT_MIN_NODE_WIDTH = "10140-layout-min-node-width";
  private static final String WIDGET_ID_LAYOUT_NODE_HEIGHT = "10150-layout-node-height";

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_ENABLED,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutEnabled.Message")
  @CommandLine.Option(
      names = {"--dv-layout-enabled"},
      description = "Enable or disable ELK layout for generated Data Vault pipelines")
  private Boolean layoutEnabled;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_ALGORITHM,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutAlgorithm.Message")
  @CommandLine.Option(
      names = {"--dv-layout-algorithm"},
      description = "ELK layout algorithm for generated pipelines (LAYERED, RECT_PACKING)")
  private ElkLayoutAlgorithm layoutAlgorithm;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_TARGET_WIDTH,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutTargetWidth.Message")
  @CommandLine.Option(
      names = {"--dv-layout-target-width"},
      description = "Target row width for rectangle packing layout (pixels)")
  private String layoutTargetWidth;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_DIRECTION,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutDirection.Message")
  @CommandLine.Option(
      names = {"--dv-layout-direction"},
      description = "Primary layout direction for generated pipelines (RIGHT, LEFT, DOWN, UP)")
  private ElkLayoutDirection layoutDirection;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_SPACING_WITHIN_LAYER,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutSpacingWithinLayer.Message")
  @CommandLine.Option(
      names = {"--dv-layout-spacing-within-layer"},
      description = "Spacing between transforms in the same layer (pixels)")
  private String layoutSpacingWithinLayer;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_SPACING_BETWEEN_LAYERS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutSpacingBetweenLayers.Message")
  @CommandLine.Option(
      names = {"--dv-layout-spacing-between-layers"},
      description = "Spacing between layers along the layout direction (pixels)")
  private String layoutSpacingBetweenLayers;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_SPACING_EDGE_NODE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutSpacingEdgeNode.Message")
  @CommandLine.Option(
      names = {"--dv-layout-spacing-edge-node"},
      description = "Spacing between edges and transforms (pixels)")
  private String layoutSpacingEdgeNode;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_CROSSING_MINIMIZATION,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutCrossingMinimization.Message")
  @CommandLine.Option(
      names = {"--dv-layout-crossing-minimization"},
      description = "Crossing minimization strategy for generated pipeline layout")
  private ElkCrossingMinimization layoutCrossingMinimization;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_NODE_PLACEMENT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutNodePlacement.Message")
  @CommandLine.Option(
      names = {"--dv-layout-node-placement"},
      description = "Node placement strategy for generated pipeline layout")
  private ElkNodePlacement layoutNodePlacement;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_LAYERING_STRATEGY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutLayeringStrategy.Message")
  @CommandLine.Option(
      names = {"--dv-layout-layering-strategy"},
      description = "Layer assignment strategy for generated pipeline layout")
  private ElkLayeringStrategy layoutLayeringStrategy;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_CYCLE_BREAKING,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutCycleBreaking.Message")
  @CommandLine.Option(
      names = {"--dv-layout-cycle-breaking"},
      description = "Cycle breaking strategy for generated pipeline layout")
  private ElkCycleBreaking layoutCycleBreaking;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_ORIGIN_X,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutOriginX.Message")
  @CommandLine.Option(
      names = {"--dv-layout-origin-x"},
      description = "X origin offset for generated pipeline layout (pixels)")
  private String layoutOriginX;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_ORIGIN_Y,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutOriginY.Message")
  @CommandLine.Option(
      names = {"--dv-layout-origin-y"},
      description = "Y origin offset for generated pipeline layout (pixels)")
  private String layoutOriginY;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_GRID_SIZE,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutGridSize.Message")
  @CommandLine.Option(
      names = {"--dv-layout-grid-size"},
      description = "Grid size used to snap generated pipeline transform positions (pixels)")
  private String layoutGridSize;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_MIN_NODE_WIDTH,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutMinNodeWidth.Message")
  @CommandLine.Option(
      names = {"--dv-layout-min-node-width"},
      description = "Minimum transform width used by generated pipeline layout (pixels)")
  private String layoutMinNodeWidth;

  @GuiWidgetElement(
      id = WIDGET_ID_LAYOUT_NODE_HEIGHT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = false,
      label = "i18n::ElkLayoutConfigOptionPlugin.LayoutNodeHeight.Message")
  @CommandLine.Option(
      names = {"--dv-layout-node-height"},
      description = "Transform height used by generated pipeline layout (pixels)")
  private String layoutNodeHeight;

  public static ElkLayoutConfigOptionPlugin getInstance() {
    ElkLayoutConfigOptionPlugin instance = new ElkLayoutConfigOptionPlugin();
    ElkLayout layout = DataVaultConfigSingleton.getConfig().getElkLayout();
    instance.layoutEnabled = layout.isEnabled();
    instance.layoutAlgorithm = layout.getAlgorithm();
    instance.layoutTargetWidth = Integer.toString(layout.getTargetWidth());
    instance.layoutDirection = layout.getDirection();
    instance.layoutSpacingWithinLayer = Integer.toString(layout.getSpacingWithinLayer());
    instance.layoutSpacingBetweenLayers = Integer.toString(layout.getSpacingBetweenLayers());
    instance.layoutSpacingEdgeNode = Integer.toString(layout.getSpacingEdgeNode());
    instance.layoutCrossingMinimization = layout.getCrossingMinimization();
    instance.layoutNodePlacement = layout.getNodePlacement();
    instance.layoutLayeringStrategy = layout.getLayeringStrategy();
    instance.layoutCycleBreaking = layout.getCycleBreaking();
    instance.layoutOriginX = Integer.toString(layout.getOriginX());
    instance.layoutOriginY = Integer.toString(layout.getOriginY());
    instance.layoutGridSize = Integer.toString(layout.getGridSize());
    instance.layoutMinNodeWidth = Integer.toString(layout.getMinNodeWidth());
    instance.layoutNodeHeight = Integer.toString(layout.getNodeHeight());
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    try {
      boolean changed =
          applyLayoutOptions(log, DataVaultConfigSingleton.getConfig().getElkLayout());
      if (changed) {
        DataVaultConfigSingleton.saveConfig();
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling ELK layout configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    ElkLayout elkLayout = DataVaultConfigSingleton.getConfig().getElkLayout();
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_LAYOUT_ENABLED:
          layoutEnabled = ((Button) control).getSelection();
          elkLayout.setEnabled(layoutEnabled);
          break;
        case WIDGET_ID_LAYOUT_ALGORITHM:
          layoutAlgorithm =
              ElkLayoutValues.parseEnum(getComboText(control), ElkLayoutAlgorithm.class);
          elkLayout.setAlgorithm(layoutAlgorithm);
          break;
        case WIDGET_ID_LAYOUT_TARGET_WIDTH:
          layoutTargetWidth = getTextValue(control);
          elkLayout.setTargetWidth(
              ElkLayoutValues.parsePositiveInt(layoutTargetWidth, ElkLayout.DEFAULT_TARGET_WIDTH));
          break;
        case WIDGET_ID_LAYOUT_DIRECTION:
          layoutDirection =
              ElkLayoutValues.parseEnum(getComboText(control), ElkLayoutDirection.class);
          elkLayout.setDirection(layoutDirection);
          break;
        case WIDGET_ID_LAYOUT_SPACING_WITHIN_LAYER:
          layoutSpacingWithinLayer = getTextValue(control);
          elkLayout.setSpacingWithinLayer(
              ElkLayoutValues.parseNonNegativeInt(
                  layoutSpacingWithinLayer, ElkLayout.DEFAULT_SPACING_WITHIN_LAYER));
          break;
        case WIDGET_ID_LAYOUT_SPACING_BETWEEN_LAYERS:
          layoutSpacingBetweenLayers = getTextValue(control);
          elkLayout.setSpacingBetweenLayers(
              ElkLayoutValues.parseNonNegativeInt(
                  layoutSpacingBetweenLayers, ElkLayout.DEFAULT_SPACING_BETWEEN_LAYERS));
          break;
        case WIDGET_ID_LAYOUT_SPACING_EDGE_NODE:
          layoutSpacingEdgeNode = getTextValue(control);
          elkLayout.setSpacingEdgeNode(
              ElkLayoutValues.parseNonNegativeInt(
                  layoutSpacingEdgeNode, ElkLayout.DEFAULT_SPACING_EDGE_NODE));
          break;
        case WIDGET_ID_LAYOUT_CROSSING_MINIMIZATION:
          layoutCrossingMinimization =
              ElkLayoutValues.parseEnum(getComboText(control), ElkCrossingMinimization.class);
          elkLayout.setCrossingMinimization(layoutCrossingMinimization);
          break;
        case WIDGET_ID_LAYOUT_NODE_PLACEMENT:
          layoutNodePlacement =
              ElkLayoutValues.parseEnum(getComboText(control), ElkNodePlacement.class);
          elkLayout.setNodePlacement(layoutNodePlacement);
          break;
        case WIDGET_ID_LAYOUT_LAYERING_STRATEGY:
          layoutLayeringStrategy =
              ElkLayoutValues.parseEnum(getComboText(control), ElkLayeringStrategy.class);
          elkLayout.setLayeringStrategy(layoutLayeringStrategy);
          break;
        case WIDGET_ID_LAYOUT_CYCLE_BREAKING:
          layoutCycleBreaking =
              ElkLayoutValues.parseEnum(getComboText(control), ElkCycleBreaking.class);
          elkLayout.setCycleBreaking(layoutCycleBreaking);
          break;
        case WIDGET_ID_LAYOUT_ORIGIN_X:
          layoutOriginX = getTextValue(control);
          elkLayout.setOriginX(
              ElkLayoutValues.parseNonNegativeInt(layoutOriginX, ElkLayout.DEFAULT_ORIGIN_X));
          break;
        case WIDGET_ID_LAYOUT_ORIGIN_Y:
          layoutOriginY = getTextValue(control);
          elkLayout.setOriginY(
              ElkLayoutValues.parseNonNegativeInt(layoutOriginY, ElkLayout.DEFAULT_ORIGIN_Y));
          break;
        case WIDGET_ID_LAYOUT_GRID_SIZE:
          layoutGridSize = getTextValue(control);
          elkLayout.setGridSize(
              ElkLayoutValues.parsePositiveInt(layoutGridSize, ElkLayout.DEFAULT_GRID_SIZE));
          break;
        case WIDGET_ID_LAYOUT_MIN_NODE_WIDTH:
          layoutMinNodeWidth = getTextValue(control);
          elkLayout.setMinNodeWidth(
              ElkLayoutValues.parsePositiveInt(
                  layoutMinNodeWidth, ElkLayout.DEFAULT_MIN_NODE_WIDTH));
          break;
        case WIDGET_ID_LAYOUT_NODE_HEIGHT:
          layoutNodeHeight = getTextValue(control);
          elkLayout.setNodeHeight(
              ElkLayoutValues.parsePositiveInt(layoutNodeHeight, ElkLayout.DEFAULT_NODE_HEIGHT));
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
              PKG, "ElkLayoutConfigOptionPlugin.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(
              PKG, "ElkLayoutConfigOptionPlugin.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  private boolean applyLayoutOptions(ILogChannel log, ElkLayout elkLayout) {
    boolean changed = false;
    if (layoutEnabled != null) {
      elkLayout.setEnabled(layoutEnabled);
      log.logBasic(
          layoutEnabled
              ? "Enabled ELK layout for generated Data Vault pipelines"
              : "Disabled ELK layout for generated Data Vault pipelines");
      changed = true;
    }
    if (layoutAlgorithm != null) {
      elkLayout.setAlgorithm(layoutAlgorithm);
      log.logBasic("Set generated pipeline layout algorithm to " + layoutAlgorithm);
      changed = true;
    }
    if (layoutTargetWidth != null) {
      elkLayout.setTargetWidth(
          ElkLayoutValues.parsePositiveInt(layoutTargetWidth, ElkLayout.DEFAULT_TARGET_WIDTH));
      log.logBasic("Set generated pipeline layout target width to " + elkLayout.getTargetWidth());
      changed = true;
    }
    if (layoutDirection != null) {
      elkLayout.setDirection(layoutDirection);
      log.logBasic("Set generated pipeline layout direction to " + layoutDirection);
      changed = true;
    }
    if (layoutSpacingWithinLayer != null) {
      elkLayout.setSpacingWithinLayer(
          ElkLayoutValues.parseNonNegativeInt(
              layoutSpacingWithinLayer, ElkLayout.DEFAULT_SPACING_WITHIN_LAYER));
      log.logBasic(
          "Set generated pipeline within-layer spacing to " + elkLayout.getSpacingWithinLayer());
      changed = true;
    }
    if (layoutSpacingBetweenLayers != null) {
      elkLayout.setSpacingBetweenLayers(
          ElkLayoutValues.parseNonNegativeInt(
              layoutSpacingBetweenLayers, ElkLayout.DEFAULT_SPACING_BETWEEN_LAYERS));
      log.logBasic(
          "Set generated pipeline between-layer spacing to " + elkLayout.getSpacingBetweenLayers());
      changed = true;
    }
    if (layoutSpacingEdgeNode != null) {
      elkLayout.setSpacingEdgeNode(
          ElkLayoutValues.parseNonNegativeInt(
              layoutSpacingEdgeNode, ElkLayout.DEFAULT_SPACING_EDGE_NODE));
      log.logBasic("Set generated pipeline edge spacing to " + elkLayout.getSpacingEdgeNode());
      changed = true;
    }
    if (layoutCrossingMinimization != null) {
      elkLayout.setCrossingMinimization(layoutCrossingMinimization);
      log.logBasic("Set generated pipeline crossing minimization to " + layoutCrossingMinimization);
      changed = true;
    }
    if (layoutNodePlacement != null) {
      elkLayout.setNodePlacement(layoutNodePlacement);
      log.logBasic("Set generated pipeline node placement to " + layoutNodePlacement);
      changed = true;
    }
    if (layoutLayeringStrategy != null) {
      elkLayout.setLayeringStrategy(layoutLayeringStrategy);
      log.logBasic("Set generated pipeline layering strategy to " + layoutLayeringStrategy);
      changed = true;
    }
    if (layoutCycleBreaking != null) {
      elkLayout.setCycleBreaking(layoutCycleBreaking);
      log.logBasic("Set generated pipeline cycle breaking to " + layoutCycleBreaking);
      changed = true;
    }
    if (layoutOriginX != null) {
      elkLayout.setOriginX(
          ElkLayoutValues.parseNonNegativeInt(layoutOriginX, ElkLayout.DEFAULT_ORIGIN_X));
      log.logBasic("Set generated pipeline layout origin X to " + elkLayout.getOriginX());
      changed = true;
    }
    if (layoutOriginY != null) {
      elkLayout.setOriginY(
          ElkLayoutValues.parseNonNegativeInt(layoutOriginY, ElkLayout.DEFAULT_ORIGIN_Y));
      log.logBasic("Set generated pipeline layout origin Y to " + elkLayout.getOriginY());
      changed = true;
    }
    if (layoutGridSize != null) {
      elkLayout.setGridSize(
          ElkLayoutValues.parsePositiveInt(layoutGridSize, ElkLayout.DEFAULT_GRID_SIZE));
      log.logBasic("Set generated pipeline layout grid size to " + elkLayout.getGridSize());
      changed = true;
    }
    if (layoutMinNodeWidth != null) {
      elkLayout.setMinNodeWidth(
          ElkLayoutValues.parsePositiveInt(layoutMinNodeWidth, ElkLayout.DEFAULT_MIN_NODE_WIDTH));
      log.logBasic("Set generated pipeline minimum node width to " + elkLayout.getMinNodeWidth());
      changed = true;
    }
    if (layoutNodeHeight != null) {
      elkLayout.setNodeHeight(
          ElkLayoutValues.parsePositiveInt(layoutNodeHeight, ElkLayout.DEFAULT_NODE_HEIGHT));
      log.logBasic("Set generated pipeline node height to " + elkLayout.getNodeHeight());
      changed = true;
    }
    return changed;
  }

  private static String getTextValue(Control control) {
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
}
